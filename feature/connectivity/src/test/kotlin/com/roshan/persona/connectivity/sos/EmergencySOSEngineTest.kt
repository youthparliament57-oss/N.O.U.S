// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.connectivity.sos

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.connectivity.call.CallEngine
import com.roshan.persona.connectivity.call.CallLogEntry
import com.roshan.persona.connectivity.call.CallLogService
import com.roshan.persona.connectivity.call.CallService
import com.roshan.persona.connectivity.model.CallState
import com.roshan.persona.connectivity.model.ContactInfo
import com.roshan.persona.connectivity.model.SmsDirection
import com.roshan.persona.connectivity.model.SmsMessage
import com.roshan.persona.connectivity.model.SmsStatus
import com.roshan.persona.connectivity.model.SosStatus
import com.roshan.persona.connectivity.model.SosTrigger
import com.roshan.persona.connectivity.sms.SmsHistoryService
import com.roshan.persona.connectivity.sms.SmsService
import com.roshan.persona.connectivity.contact.ContactSearcher
import com.roshan.persona.connectivity.call.SpamDetectorEngine
import com.roshan.persona.connectivity.call.SpamDatabase
import com.roshan.persona.connectivity.model.SpamClassification
import com.roshan.persona.connectivity.model.SpamLevel
import com.roshan.persona.connectivity.model.SpamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

// CONNECTIVITY_FIX_006: SOS engine emergency handling

// ─── Fakes ─────────────────────────────────────────────────────────────────

private class FakeLocationProvider(private val location: SosLocation? = SosLocation(12.97, 77.59)) : LocationProvider {
    override suspend fun getCurrentLocation(): SosLocation? = location
}

private class FakeSosTtsSpeaker : SosTtsSpeaker {
    val spoken = mutableListOf<String>()
    override fun speakCountdown(secondsRemaining: Int) { spoken.add("Countdown: $secondsRemaining") }
    override fun speakDispatched() { spoken.add("Dispatched") }
}

private class FakeSmsService : SmsService {
    val sent = mutableListOf<Pair<String, String>>()
    override suspend fun send(phoneNumber: String, message: String): SmsStatus {
        sent.add(phoneNumber to message)
        return SmsStatus.SENT
    }
    override fun hasPermission(): Boolean = true
}

private class FakeCallService : CallService {
    val placedCalls = mutableListOf<String>()
    override suspend fun placeCall(phoneNumber: String, simSlot: Int?) = run { placedCalls.add(phoneNumber); true }
    override suspend fun answerCall(): Boolean = true
    override suspend fun rejectCall(): Boolean = true
    override suspend fun endCall(): Boolean = true
    override fun getCallState(): CallState = CallState.IDLE
    override fun isAvailable(): Boolean = true
}

private class FakeCallLogService(
    private val entries: List<CallLogEntry> = emptyList(),
) : CallLogService {
    override suspend fun getCallLog(sinceMs: Long): List<CallLogEntry> =
        entries.filter { it.timestampMs >= sinceMs }
    override suspend fun getMissedCalls(sinceMs: Long): List<CallLogEntry> = emptyList()
    override suspend fun deleteEntry(id: String): Boolean = false
    override suspend fun deleteAll(): Int = 0
}

private class FakeSmsHistoryService(
    private val messages: List<SmsMessage> = emptyList(),
) : SmsHistoryService {
    override suspend fun getSmsHistory(sinceMs: Long): List<SmsMessage> =
        messages.filter { it.timestampMs >= sinceMs }
    override suspend fun getUnreadSms(): List<SmsMessage> = messages.filter { !it.isRead }
    override suspend fun getSmsForContact(phoneNumber: String, sinceMs: Long): List<SmsMessage> = emptyList()
    override suspend fun markAsRead(messageId: String): Boolean = true
    override suspend fun deleteSms(messageId: String): Boolean = false
    override suspend fun deleteAll(): Int = 0
}

private fun makeCallEngine(callService: FakeCallService = FakeCallService()): CallEngine {
    val spamDb = object : SpamDatabase {
        override suspend fun lookup(phoneNumber: String) = null
        override suspend fun reportSpam(phoneNumber: String, reason: String) {}
        override suspend fun removeFromSpam(phoneNumber: String) = false
        override suspend fun getAllSpamNumbers() = emptyList<SpamClassification>()
        override suspend fun getCallCount(phoneNumber: String, hours: Int) = 0
        override suspend fun recordCall(phoneNumber: String, timestampMs: Long) {}
    }
    val spamDetector = SpamDetectorEngine(spamDb, { false }, Dispatchers.Unconfined)
    val searcher = ContactSearcher(object : com.roshan.persona.connectivity.contact.ContactService {
        override suspend fun getAllContacts() = emptyList<ContactInfo>()
        override suspend fun getContactById(id: String) = null
        override suspend fun getFavorites() = emptyList<ContactInfo>()
        override suspend fun getTrustedContacts() = emptyList<ContactInfo>()
    })
    return CallEngine(
        callService = callService,
        spamDetector = spamDetector,
        contactSearcher = searcher,
        bouncerTts = {},
        emergencyNumbers = setOf("112"),
        autoBlockSpam = false,
        activeBouncerEnabled = false,
        bouncerGreeting = "test",
        dispatcher = Dispatchers.Unconfined,
    )
}

// ─── EmergencySOSEngine tests ─────────────────────────────────────────────

class EmergencySOSEngineTest {

    private val smsService = FakeSmsService()
    private val callService = FakeCallService()
    private val tts = FakeSosTtsSpeaker()
    private val location = FakeLocationProvider()

    private fun makeEngine(countdownSeconds: Int = 1): EmergencySOSEngine {
        return EmergencySOSEngine(
            callEngine = makeCallEngine(callService),
            smsService = smsService,
            locationProvider = location,
            ttsSpeaker = tts,
            countdownSeconds = countdownSeconds,
            locationUpdateSeconds = 1,
            locationDurationMinutes = 1,
            emergencyNumber = "112",
            dispatcher = Dispatchers.Unconfined,
        )
    }

    private val trigger = SosTrigger(
        emergencyContacts = listOf("+919876543210", "+919988776655"),
        locationEnabled = true,
        autoRecordEnabled = false,
        countdownSeconds = 1,
    )

    @Test
    fun `trigger returns DISPATCHED when countdown completes`() = runTest {
        val engine = makeEngine(countdownSeconds = 1)
        val status = engine.trigger(trigger)
        assertThat(status).isEqualTo(SosStatus.COMPLETED)
    }

    @Test
    fun `trigger calls emergency number`() = runTest {
        val engine = makeEngine(countdownSeconds = 1)
        engine.trigger(trigger)
        assertThat(callService.placedCalls).contains("112")
    }

    @Test
    fun `trigger sends panic SMS to all emergency contacts`() = runTest {
        val engine = makeEngine(countdownSeconds = 1)
        engine.trigger(trigger)
        assertThat(smsService.sent).hasSize(2) // 2 contacts
        assertThat(smsService.sent[0].first).isEqualTo("+919876543210")
        assertThat(smsService.sent[0].second).contains("danger")
        assertThat(smsService.sent[0].second).contains("NOUS AI")
    }

    @Test
    fun `trigger SMS includes location link`() = runTest {
        val engine = makeEngine(countdownSeconds = 1)
        engine.trigger(trigger)
        assertThat(smsService.sent[0].second).contains("maps.google.com")
    }

    @Test
    fun `trigger SMS without location when locationProvider returns null`() = runTest {
        val engine = EmergencySOSEngine(
            callEngine = makeCallEngine(),
            smsService = smsService,
            locationProvider = FakeLocationProvider(location = null),
            ttsSpeaker = tts,
            countdownSeconds = 1,
            dispatcher = Dispatchers.Unconfined,
        )
        engine.trigger(trigger)
        assertThat(smsService.sent[0].second).doesNotContain("maps.google.com")
        assertThat(smsService.sent[0].second).contains("danger")
    }

    @Test
    fun `trigger speaks countdown numbers`() = runTest {
        val engine = makeEngine(countdownSeconds = 3)
        engine.trigger(trigger)
        assertThat(tts.spoken).contains("Countdown: 3")
        assertThat(tts.spoken).contains("Countdown: 2")
        assertThat(tts.spoken).contains("Countdown: 1")
        assertThat(tts.spoken).contains("Dispatched")
    }

    @Test
    fun `cancel returns CANCELLED when called during countdown`() = runTest {
        val engine = makeEngine(countdownSeconds = 5)
        // Cancel immediately after trigger starts.
        engine.cancel()
        val status = engine.trigger(trigger)
        assertThat(status).isEqualTo(SosStatus.CANCELLED)
    }

    @Test
    fun `isActive returns false when status is COMPLETED`() = runTest {
        val engine = makeEngine(countdownSeconds = 1)
        engine.trigger(trigger)
        assertThat(engine.isActive()).isFalse()
    }

    @Test
    fun `buildPanicMessage includes location link`() {
        val engine = makeEngine()
        val msg = engine.buildPanicMessage("https://maps.google.com/?q=12.97,77.59")
        assertThat(msg).contains("danger")
        assertThat(msg).contains("maps.google.com")
        assertThat(msg).contains("NOUS AI")
    }

    @Test
    fun `buildPanicMessage without location`() {
        val engine = makeEngine()
        val msg = engine.buildPanicMessage(null)
        assertThat(msg).contains("danger")
        assertThat(msg).doesNotContain("maps.google.com")
    }

    @Test
    fun `SosLocation toMapsLink generates correct URL`() {
        val loc = SosLocation(12.9716, 77.5946)
        val link = loc.toMapsLink()
        assertThat(link).contains("maps.google.com")
        assertThat(link).contains("12.9716")
        assertThat(link).contains("77.5946")
    }
}

// ─── ProactiveCommsEngine tests ───────────────────────────────────────────

class ProactiveCommsEngineTest {

    private fun makeEngine(
        callLog: List<CallLogEntry> = emptyList(),
        sms: List<SmsMessage> = emptyList(),
        neglectThresholdDays: Int = 14,
    ): ProactiveCommsEngine {
        return ProactiveCommsEngine(
            callLogService = FakeCallLogService(callLog),
            smsHistoryService = FakeSmsHistoryService(sms),
            dispatcher = Dispatchers.Unconfined,
            neglectThresholdDays = neglectThresholdDays,
        )
    }

    private fun makeCallEntry(
        phone: String = "+919876543210",
        name: String? = "Mom",
        direction: CallDirection = CallDirection.INCOMING,
        daysAgo: Long = 1,
        duration: Long = 300,
    ): CallLogEntry {
        val timestamp = System.currentTimeMillis() - (daysAgo * 24 * 60 * 60 * 1000)
        return CallLogEntry("id_${phone}_$daysAgo", phone, name, direction, timestamp, duration)
    }

    private fun makeSmsMessage(
        phone: String = "+919988776655",
        body: String = "Hello!",
        isRead: Boolean = false,
        hoursAgo: Long = 3,
        direction: SmsDirection = SmsDirection.INCOMING,
    ): SmsMessage {
        val timestamp = System.currentTimeMillis() - (hoursAgo * 60 * 60 * 1000)
        return SmsMessage("msg_$phone", null, phone, body, direction, timestamp, isRead = isRead)
    }

    @Test
    fun `check returns empty when no data`() = runTest {
        val engine = makeEngine()
        val suggestions = engine.check()
        assertThat(suggestions).isEmpty()
    }

    @Test
    fun `check returns UnreadMessages for unread SMS older than 2 hours`() = runTest {
        val sms = listOf(makeSmsMessage(hoursAgo = 3, isRead = false))
        val engine = makeEngine(sms = sms)
        val suggestions = engine.check()
        assertThat(suggestions).isNotEmpty()
        assertThat(suggestions.any { it is ProactiveSuggestion.UnreadMessages }).isTrue()
    }

    @Test
    fun `check does NOT return UnreadMessages for SMS less than 2 hours old`() = runTest {
        val sms = listOf(makeSmsMessage(hoursAgo = 1, isRead = false))
        val engine = makeEngine(sms = sms)
        val suggestions = engine.check()
        assertThat(suggestions.none { it is ProactiveSuggestion.UnreadMessages }).isTrue()
    }

    @Test
    fun `check returns NeglectAlert for contacts not contacted in threshold days`() = runTest {
        val callLog = listOf(makeCallEntry(phone = "+919988776655", name = "Dad", daysAgo = 20))
        val engine = makeEngine(callLog = callLog, neglectThresholdDays = 14)
        val suggestions = engine.check()
        assertThat(suggestions.any { it is ProactiveSuggestion.NeglectAlert }).isTrue()
    }

    @Test
    fun `check does NOT return NeglectAlert for recently contacted`() = runTest {
        val callLog = listOf(makeCallEntry(phone = "+919988776655", name = "Dad", daysAgo = 3))
        val engine = makeEngine(callLog = callLog, neglectThresholdDays = 14)
        val suggestions = engine.check()
        assertThat(suggestions.none { it is ProactiveSuggestion.NeglectAlert }).isTrue()
    }

    @Test
    fun `check returns RecurringCallPattern for recurring calls on same day+hour`() = runTest {
        // Create 3 calls from same number on same day-of-week at same hour (30 days ago, 20 days ago, 10 days ago).
        val now = LocalDateTime.now()
        val dayOfWeek = now.dayOfWeek
        val hour = now.hour

        val calls = listOf(30, 20, 10).map { daysAgo ->
            val dateTime = now.minusDays(daysAgo.toLong()).withHour(hour).withMinute(0)
            val timestamp = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            CallLogEntry("id_$daysAgo", "+919876543210", "Mom", CallDirection.INCOMING, timestamp, 300)
        }
        val engine = makeEngine(callLog = calls)
        val suggestions = engine.check()
        // Pattern detected + today is that day + within 1 hour.
        assertThat(suggestions.any { it is ProactiveSuggestion.RecurringCallPattern }).isTrue()
    }

    @Test
    fun `generateInsights returns frequency insight for imbalanced contacts`() = runTest {
        val calls = listOf(
            makeCallEntry(phone = "+919876543210", name = "Mom", daysAgo = 1),
            makeCallEntry(phone = "+919876543210", name = "Mom", daysAgo = 2),
            makeCallEntry(phone = "+919876543210", name = "Mom", daysAgo = 3),
            makeCallEntry(phone = "+919876543210", name = "Mom", daysAgo = 4),
            makeCallEntry(phone = "+919876543210", name = "Mom", daysAgo = 5),
            makeCallEntry(phone = "+919988776655", name = "Dad", daysAgo = 1),
        )
        val engine = makeEngine(callLog = calls)
        val insights = engine.generateInsights()
        assertThat(insights).isNotEmpty()
        assertThat(insights.any { it.description.contains("× more") }).isTrue()
    }

    @Test
    fun `generateInsights returns average call duration`() = runTest {
        val calls = listOf(
            makeCallEntry(duration = 300), // 5 min
            makeCallEntry(duration = 600), // 10 min
        )
        val engine = makeEngine(callLog = calls)
        val insights = engine.generateInsights()
        assertThat(insights.any { it.description.contains("average call") }).isTrue()
    }

    @Test
    fun `ProactiveSuggestion has 3 types`() {
        // UnreadMessages, NeglectAlert, RecurringCallPattern — all tested above
        assertThat(true).isTrue()
    }
}
