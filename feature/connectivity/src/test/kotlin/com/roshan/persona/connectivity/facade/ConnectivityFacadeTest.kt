// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.connectivity.facade

import android.content.Context
import android.content.pm.PackageManager
import com.google.common.truth.Truth.assertThat
import com.roshan.persona.connectivity.call.BouncerTtsInterface
import com.roshan.persona.connectivity.call.CallEngine
import com.roshan.persona.connectivity.call.CallLogEntry
import com.roshan.persona.connectivity.call.CallLogService
import com.roshan.persona.connectivity.call.CallResult
import com.roshan.persona.connectivity.call.CallService
import com.roshan.persona.connectivity.call.SpamDatabase
import com.roshan.persona.connectivity.call.SpamDetectorEngine
import com.roshan.persona.connectivity.contact.ContactManager
import com.roshan.persona.connectivity.contact.ContactSearcher
import com.roshan.persona.connectivity.contact.SimManager
import com.roshan.persona.connectivity.contact.SimService
import com.roshan.persona.connectivity.contact.WritableContactService
import com.roshan.persona.connectivity.messaging.MessagingAccessibilityBridge
import com.roshan.persona.connectivity.messaging.OutboundExecutor
import com.roshan.persona.connectivity.model.CallDirection
import com.roshan.persona.connectivity.model.CallState
import com.roshan.persona.connectivity.model.ContactInfo
import com.roshan.persona.connectivity.model.MessagingApp
import com.roshan.persona.connectivity.model.ShareContent
import com.roshan.persona.connectivity.model.SimInfo
import com.roshan.persona.connectivity.model.SimSlot
import com.roshan.persona.connectivity.model.SmsDirection
import com.roshan.persona.connectivity.model.SmsMessage
import com.roshan.persona.connectivity.model.SmsStatus
import com.roshan.persona.connectivity.model.SosStatus
import com.roshan.persona.connectivity.model.SosTrigger
import com.roshan.persona.connectivity.model.SpamClassification
import com.roshan.persona.connectivity.model.SpamLevel
import com.roshan.persona.connectivity.model.SpamSource
import com.roshan.persona.connectivity.recording.Aes256Encryptor
import com.roshan.persona.connectivity.recording.AudioRecorder
import com.roshan.persona.connectivity.recording.CallRecording
import com.roshan.persona.connectivity.recording.CallRecordingEngine
import com.roshan.persona.connectivity.recording.RecordingStore
import com.roshan.persona.connectivity.recording.TranscriptionLlm
import com.roshan.persona.connectivity.sms.SmsEngine
import com.roshan.persona.connectivity.sms.SmsHistoryService
import com.roshan.persona.connectivity.sms.SmsService
import com.roshan.persona.connectivity.sms.SmsTemplateManager
import com.roshan.persona.connectivity.sms.SmartReplyContext
import com.roshan.persona.connectivity.sms.SmartReplyEngine
import com.roshan.persona.connectivity.sms.SmartReplyLlm
import com.roshan.persona.connectivity.sos.EmergencySOSEngine
import com.roshan.persona.connectivity.sos.LocationProvider
import com.roshan.persona.connectivity.sos.ProactiveCommsEngine
import com.roshan.persona.connectivity.sos.SosLocation
import com.roshan.persona.connectivity.sos.SosTtsSpeaker
import com.roshan.persona.connectivity.unified.CommsHistoryEngine
import com.roshan.persona.connectivity.unified.LiveTranslationBridge
import com.roshan.persona.connectivity.unified.MessagingUnificationEngine
import com.roshan.persona.connectivity.unified.NotificationListenerInterface
import com.roshan.persona.connectivity.unified.ShareEngine
import com.roshan.persona.connectivity.unified.TranslationService
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

// CONNECTIVITY_FIX_003: Connectivity facade safe

/**
 * NOUS — Connectivity Facade E2E Smoke Tests (Module 11 v3 — Step 11.8 FINAL).
 *
 * End-to-end smoke tests covering the [ConnectivityFacade] — the single entry
 * point for all 12 v3 engines. Each test exercises one facade operation with
 * fake services to verify the wiring is correct.
 *
 * ## What this verifies
 *
 *  - Facade correctly delegates to each underlying engine.
 *  - CallResult / SmsStatus / SendResult / SosStatus are translated correctly.
 *  - ContactSearcher + SimManager + CallEngine chain works end-to-end.
 *  - ContactSearcher + SmsEngine chain works end-to-end.
 *  - MessagingUnificationEngine emits UnifiedMessage via Flow.
 *  - CommsHistoryEngine merges calls + SMS into a unified timeline.
 *  - ShareEngine returns SMS target for short text.
 *  - LiveTranslationBridge starts/stops cleanly.
 *  - CallRecordingEngine records + stores + searches.
 *  - EmergencySOSEngine triggers + cancels.
 *  - ProactiveCommsEngine returns suggestions.
 *  - SmsTemplateManager add/get/list/remove round-trip.
 *  - SpamDetectorEngine classifies numbers.
 */
class ConnectivityFacadeTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Fake services (in-memory implementations of all 12 engines' deps)
    // ═══════════════════════════════════════════════════════════════════════════

    private class FakeCallService : CallService {
        val placedCalls = mutableListOf<Pair<String, Int?>>()
        var placeCallResult = true
        override suspend fun placeCall(phoneNumber: String, simSlot: Int?): Boolean {
            placedCalls.add(phoneNumber to simSlot)
            return placeCallResult
        }
        override suspend fun answerCall(): Boolean = true
        override suspend fun rejectCall(): Boolean = true
        override suspend fun endCall(): Boolean = true
        override fun getCallState(): CallState = CallState.IDLE
        override fun isAvailable(): Boolean = true
    }

    private class FakeSmsService : SmsService {
        val sent = mutableListOf<Pair<String, String>>()
        var sendResult = SmsStatus.SENT
        override suspend fun send(phoneNumber: String, message: String): SmsStatus {
            sent.add(phoneNumber to message)
            return sendResult
        }
        override fun hasPermission(): Boolean = true
    }

    private class FakeSmsHistoryService(
        private val messages: List<SmsMessage> = emptyList(),
    ) : SmsHistoryService {
        val read = mutableListOf<String>()
        override suspend fun getSmsHistory(sinceMs: Long): List<SmsMessage> =
            messages.filter { it.timestampMs >= sinceMs }
        override suspend fun getUnreadSms(): List<SmsMessage> = messages.filter { !it.isRead }
        override suspend fun getSmsForContact(phoneNumber: String, sinceMs: Long): List<SmsMessage> =
            messages.filter { it.phoneNumber == phoneNumber && it.timestampMs >= sinceMs }
        override suspend fun markAsRead(messageId: String): Boolean { read.add(messageId); return true }
        override suspend fun deleteSms(messageId: String): Boolean = false
        override suspend fun deleteAll(): Int = 0
    }

    private class FakeCallLogService(
        private val entries: List<CallLogEntry> = emptyList(),
    ) : CallLogService {
        override suspend fun getCallLog(sinceMs: Long): List<CallLogEntry> =
            entries.filter { it.timestampMs >= sinceMs }
        override suspend fun getMissedCalls(sinceMs: Long): List<CallLogEntry> =
            entries.filter { it.direction == CallDirection.MISSED && it.timestampMs >= sinceMs }
        override suspend fun deleteEntry(id: String): Boolean = false
        override suspend fun deleteAll(): Int = 0
    }

    private class FakeContactService(
        private val contacts: List<ContactInfo> = emptyList(),
    ) : WritableContactService {
        val added = mutableListOf<Triple<String, String, String?>>()
        val deleted = mutableListOf<String>()
        val favorites = mutableMapOf<String, Boolean>()
        val trusted = mutableMapOf<String, Boolean>()
        override suspend fun getAllContacts(): List<ContactInfo> = contacts
        override suspend fun getContactById(id: String): ContactInfo? = contacts.firstOrNull { it.id == id }
        override suspend fun getFavorites(): List<ContactInfo> = contacts.filter { it.isFavorite }
        override suspend fun getTrustedContacts(): List<ContactInfo> = contacts.filter { it.isTrusted }
        override suspend fun addContact(name: String, phoneNumber: String, email: String?): ContactInfo? {
            added.add(Triple(name, phoneNumber, email))
            return ContactInfo(
                id = "contact_${added.size}",
                displayName = name,
                phoneNumbers = listOf(phoneNumber),
                email = email,
                isFavorite = false,
                isTrusted = false,
            )
        }
        override suspend fun setFavorite(contactId: String, isFavorite: Boolean): Boolean {
            favorites[contactId] = isFavorite
            return true
        }
        override suspend fun setTrusted(contactId: String, isTrusted: Boolean): Boolean {
            trusted[contactId] = isTrusted
            return true
        }
        override suspend fun setPreferredMessagingApp(contactId: String, app: MessagingApp): Boolean = true
        override suspend fun deleteContact(contactId: String): Boolean {
            deleted.add(contactId)
            return true
        }
    }

    private class FakeSimService(
        private val sims: List<SimInfo> = listOf(
            SimInfo(slot = SimSlot.SIM1, carrierName = "Jio", displayName = "Jio", isActive = true, phoneNumber = "+919876543210"),
            SimInfo(slot = SimSlot.SIM2, carrierName = "Airtel", displayName = "Airtel", isActive = true, phoneNumber = "+919988776655"),
        ),
    ) : SimService {
        override suspend fun getActiveSims(): List<SimInfo> = sims
    }

    private class FakeSpamDatabase : SpamDatabase {
        val spamNumbers = ConcurrentHashMap<String, String>()
        val calls = ConcurrentHashMap<String, MutableList<Long>>()
        override suspend fun lookup(phoneNumber: String): SpamClassification? {
            val reason = spamNumbers[phoneNumber] ?: return null
            return SpamClassification(
                phoneNumber = phoneNumber,
                level = SpamLevel.SPAM,
                confidence = 0.95f,
                reason = reason,
                source = SpamSource.LOCAL_DB,
            )
        }
        override suspend fun reportSpam(phoneNumber: String, reason: String) { spamNumbers[phoneNumber] = reason }
        override suspend fun removeFromSpam(phoneNumber: String): Boolean = spamNumbers.remove(phoneNumber) != null
        override suspend fun getAllSpamNumbers(): List<SpamClassification> = spamNumbers.keys.map {
            SpamClassification(it, SpamLevel.SPAM, 0.95f, spamNumbers[it] ?: "", SpamSource.LOCAL_DB)
        }
        override suspend fun getCallCount(phoneNumber: String, hours: Int): Int {
            val cutoff = System.currentTimeMillis() - hours * 3_600_000L
            return calls[phoneNumber]?.count { it >= cutoff } ?: 0
        }
        override suspend fun recordCall(phoneNumber: String, timestampMs: Long) {
            calls.getOrPut(phoneNumber) { mutableListOf() }.add(timestampMs)
        }
    }

    private class FakeAudioRecorder : AudioRecorder {
        var recording = false
        var audioData: ByteArray? = ByteArray(100) { 0 }
        override fun startRecording(): Boolean { recording = true; return true }
        override fun stopRecording(): ByteArray? { recording = false; return audioData }
        override fun isRecording(): Boolean = recording
        override fun getDurationSeconds(): Long = 5
    }

    private class FakeTranscriptionLlm(
        private val transcript: String? = "Hello world",
    ) : TranscriptionLlm {
        override suspend fun transcribe(audioData: ByteArray): String? = transcript
        override fun isReady(): Boolean = true
    }

    private class FakeRecordingStore : RecordingStore {
        val recordings = ConcurrentHashMap<String, CallRecording>()
        var storeCount = 0
            private set
        override suspend fun store(recording: CallRecording): String {
            recordings[recording.id] = recording
            storeCount++
            return recording.id
        }
        override suspend fun get(recordingId: String): CallRecording? = recordings[recordingId]
        override suspend fun searchByTranscript(query: String, limit: Int): List<CallRecording> {
            val q = query.lowercase()
            return recordings.values.filter { it.transcript?.lowercase()?.contains(q) == true }.take(limit)
        }
        override suspend fun getRecent(limit: Int): List<CallRecording> =
            recordings.values.sortedByDescending { it.timestampMs }.take(limit)
        override suspend fun delete(recordingId: String): Boolean = recordings.remove(recordingId) != null
        override suspend fun deleteAll(): Int { val n = recordings.size; recordings.clear(); return n }
        override suspend fun count(): Int = recordings.size
    }

    private class FakeNotificationListener(private val enabled: Boolean = true) : NotificationListenerInterface {
        override fun isEnabled(): Boolean = enabled
    }

    private class FakeLocationProvider(
        private val location: SosLocation? = SosLocation(12.97, 77.59),
    ) : LocationProvider {
        override suspend fun getCurrentLocation(): SosLocation? = location
    }

    private class FakeSosTtsSpeaker : SosTtsSpeaker {
        val spoken = mutableListOf<String>()
        override fun speakCountdown(secondsRemaining: Int) { spoken.add("Countdown: $secondsRemaining") }
        override fun speakDispatched() { spoken.add("Dispatched") }
    }

    private class FakeTranslationService(
        private val transcription: String? = "Namaste",
        private val translation: String? = "Hello",
    ) : TranslationService {
        var transcribeCount = 0; private set
        var translateCount = 0; private set
        override suspend fun transcribe(audioData: ByteArray): String? {
            transcribeCount++; return transcription
        }
        override suspend fun translate(text: String): String? {
            translateCount++; return translation
        }
    }

    private class FakeSmartReplyLlm(private val reply: String = "Sure, I'll be there.") : SmartReplyLlm {
        override suspend fun generate(prompt: String): String = reply
        override fun isReady(): Boolean = true
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Facade factory — assembles all 12 engines with fakes
    // ═══════════════════════════════════════════════════════════════════════════

    private class FacadeFactory(
        val contacts: List<ContactInfo> = emptyList(),
        val smsMessages: List<SmsMessage> = emptyList(),
        val callLog: List<CallLogEntry> = emptyList(),
        placeCallResult: Boolean = true,
        smsSendResult: SmsStatus = SmsStatus.SENT,
    ) {
        val callService = FakeCallService().also { cs -> cs.placeCallResult = placeCallResult }
        val smsService = FakeSmsService().also { ss -> ss.sendResult = smsSendResult }
        val smsHistory = FakeSmsHistoryService(smsMessages)
        val callLogService = FakeCallLogService(callLog)
        val contactService = FakeContactService(contacts)
        val simService = FakeSimService()
        val spamDb = FakeSpamDatabase()
        val audioRecorder = FakeAudioRecorder()
        val transcriptionLlm = FakeTranscriptionLlm()
        val recordingStore = FakeRecordingStore()
        val notificationListener = FakeNotificationListener(enabled = true)
        val locationProvider = FakeLocationProvider()
        val sosTts = FakeSosTtsSpeaker()
        val translationService = FakeTranslationService()
        val smartReplyLlm = FakeSmartReplyLlm()

        private val mockContext: Context = mockk(relaxed = true)
        private val mockPackageManager: PackageManager = mockk(relaxed = true)

        val contactSearcher = ContactSearcher(contactService, maxFuzzyDistance = 3, maxResults = 10)
        val contactManager = ContactManager(contactService)
        val simManager = SimManager(simService)
        val spamDetector = SpamDetectorEngine(spamDb, { phone ->
            contacts.any { it.phoneNumbers.any { p -> p.filter { d -> d.isDigit() } == phone } }
        }, Dispatchers.Unconfined)
        val callEngine = CallEngine(
            callService = callService,
            spamDetector = spamDetector,
            contactSearcher = contactSearcher,
            bouncerTts = BouncerTtsInterface { },
            emergencyNumbers = setOf("112", "911", "100"),
            autoBlockSpam = false,
            activeBouncerEnabled = false,
            bouncerGreeting = "test greeting",
            dispatcher = Dispatchers.Unconfined,
        )
        val smartReplyEngine = SmartReplyEngine(
            llm = smartReplyLlm,
            contextProvider = {
                SmartReplyContext(isDriving = false, isInMeeting = false, isSleeping = false)
            },
            dispatcher = Dispatchers.Unconfined,
        )
        val smsEngine = SmsEngine(
            smsService = smsService,
            smsHistory = smsHistory,
            smartReplyEngine = smartReplyEngine,
            ttsReader = { },
            dispatcher = Dispatchers.Unconfined,
        )
        val smsTemplateManager = SmsTemplateManager()
        val outboundExecutor: OutboundExecutor by lazy {
            every { mockContext.packageManager } returns mockPackageManager
            every { mockContext.startActivity(any()) } just Runs
            every { mockPackageManager.getPackageInfo(any<String>(), any()) } returns mockk(relaxed = true)
            OutboundExecutor(mockContext, NoopOpBridge(), Dispatchers.Unconfined)
        }
        val messagingUnification = MessagingUnificationEngine(
            notificationListener = notificationListener,
            dispatcher = Dispatchers.Unconfined,
        )
        val commsHistory = CommsHistoryEngine(
            callLogService = callLogService,
            smsHistoryService = smsHistory,
            dispatcher = Dispatchers.Unconfined,
        )
        val shareEngine: ShareEngine by lazy {
            ShareEngine(mockContext, Dispatchers.Unconfined)
        }
        val liveTranslationBridge = LiveTranslationBridge(
            translationService = translationService,
            displayCallback = { },
            dispatcher = Dispatchers.Unconfined,
        )
        val callRecordingEngine = CallRecordingEngine(
            recorder = audioRecorder,
            transcriber = transcriptionLlm,
            store = recordingStore,
            encryptor = Aes256Encryptor(
                keyProvider = { Aes256Encryptor.deriveKey("test-secret") },
                fileWriter = { _, _ -> "/tmp/test.enc" },
            ),
            dispatcher = Dispatchers.Unconfined,
        )
        val emergencySosEngine = EmergencySOSEngine(
            callEngine = callEngine,
            smsService = smsService,
            locationProvider = locationProvider,
            ttsSpeaker = sosTts,
            countdownSeconds = 1,
            locationUpdateSeconds = 30,
            locationDurationMinutes = 1,
            emergencyNumber = "112",
            dispatcher = Dispatchers.Unconfined,
        )
        val proactiveCommsEngine = ProactiveCommsEngine(
            callLogService = callLogService,
            smsHistoryService = smsHistory,
            dispatcher = Dispatchers.Unconfined,
            neglectThresholdDays = 14,
        )

        val facade = ConnectivityFacade(
            callEngine = callEngine,
            spamDetector = spamDetector,
            smsEngine = smsEngine,
            smsTemplateManager = smsTemplateManager,
            contactSearcher = contactSearcher,
            contactManager = contactManager,
            simManager = simManager,
            outboundExecutor = outboundExecutor,
            messagingUnificationEngine = messagingUnification,
            commsHistoryEngine = commsHistory,
            shareEngine = shareEngine,
            liveTranslationBridge = liveTranslationBridge,
            callRecordingEngine = callRecordingEngine,
            emergencySosEngine = emergencySosEngine,
            proactiveCommsEngine = proactiveCommsEngine,
        )

        private class NoopOpBridge : MessagingAccessibilityBridge {
            override fun isServiceEnabled(): Boolean = false
            override suspend fun clickSendButton(app: MessagingApp, timeoutMs: Long): Boolean = false
            override suspend fun verifyMessageSent(app: MessagingApp): Boolean = false
        }
    }

    private fun testContact(
        id: String = "c1",
        name: String = "Mom",
        phone: String = "+919876543210",
    ) = ContactInfo(
        id = id,
        displayName = name,
        phoneNumbers = listOf(phone),
        email = null,
        isFavorite = false,
        isTrusted = false,
    )

    private fun testSms(
        id: String = "sms1",
        body: String = "Hello",
        direction: SmsDirection = SmsDirection.INCOMING,
        isRead: Boolean = false,
        timestampMs: Long = System.currentTimeMillis(),
    ) = SmsMessage(
        id = id,
        contact = null,
        phoneNumber = "+919876543210",
        body = body,
        direction = direction,
        timestampMs = timestampMs,
        status = SmsStatus.SENT,
        isRead = isRead,
    )

    private fun testCallLog(
        id: String = "call1",
        phone: String = "+919876543210",
        name: String? = "Mom",
        direction: CallDirection = CallDirection.OUTGOING,
        durationSeconds: Long = 300,
        timestampMs: Long = System.currentTimeMillis(),
    ) = CallLogEntry(
        id = id,
        phoneNumber = phone,
        displayName = name,
        direction = direction,
        timestampMs = timestampMs,
        durationSeconds = durationSeconds,
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // CALL OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `callContact returns PLACED when contact found`() = runTest {
        val factory = FacadeFactory(contacts = listOf(testContact(name = "Mom")))
        val result = factory.facade.callContact("Mom")
        assertThat(result).isEqualTo(CallResult.PLACED)
        assertThat(factory.callService.placedCalls).hasSize(1)
        assertThat(factory.callService.placedCalls[0].first).isEqualTo("+919876543210")
    }

    @Test
    fun `callContact returns FAILED when contact not found`() = runTest {
        val factory = FacadeFactory(contacts = emptyList())
        val result = factory.facade.callContact("Unknown")
        assertThat(result).isInstanceOf(CallResult.FAILED::class.java)
        assertThat((result as CallResult.FAILED).reason).contains("Contact not found")
        assertThat(factory.callService.placedCalls).isEmpty()
    }

    @Test
    fun `callContact respects SIM preference from SimManager`() = runTest {
        val factory = FacadeFactory(contacts = listOf(testContact(id = "c1", name = "Mom")))
        factory.simManager.setPreferredSim("c1", SimSlot.SIM2, isAutoLearned = true)
        val result = factory.facade.callContact("Mom")
        assertThat(result).isEqualTo(CallResult.PLACED)
        assertThat(factory.callService.placedCalls[0].second).isEqualTo(1)  // SIM2 = slot 1
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SMS OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `sendSmsToContact returns SENT when contact found`() = runTest {
        val factory = FacadeFactory(contacts = listOf(testContact(name = "Mom")))
        val status = factory.facade.sendSmsToContact("Mom", "I'll be late")
        assertThat(status).isEqualTo(SmsStatus.SENT)
        assertThat(factory.smsService.sent).hasSize(1)
        assertThat(factory.smsService.sent[0].first).isEqualTo("+919876543210")
        assertThat(factory.smsService.sent[0].second).isEqualTo("I'll be late")
    }

    @Test
    fun `sendSmsToContact returns FAILED when contact not found`() = runTest {
        val factory = FacadeFactory(contacts = emptyList())
        val status = factory.facade.sendSmsToContact("Ghost", "Hello")
        assertThat(status).isEqualTo(SmsStatus.FAILED)
        assertThat(factory.smsService.sent).isEmpty()
    }

    @Test
    fun `readUnreadSms returns unread messages`() = runTest {
        val factory = FacadeFactory(smsMessages = listOf(
            testSms(id = "s1", body = "Hi", isRead = false),
            testSms(id = "s2", body = "There", isRead = true),
        ))
        val unread = factory.facade.readUnreadSms()
        assertThat(unread).hasSize(1)
        assertThat(unread[0].id).isEqualTo("s1")
    }

    @Test
    fun `getSmartReplies delegates to SmsEngine`() = runTest {
        val factory = FacadeFactory()
        val suggestions = factory.facade.getSmartReplies(testSms(body = "Where are you?"))
        assertThat(suggestions.replies).isNotEmpty()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONTACT OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `searchContacts delegates to ContactSearcher`() = runTest {
        val factory = FacadeFactory(contacts = listOf(
            testContact(id = "c1", name = "Mom"),
            testContact(id = "c2", name = "Rohit", phone = "+919988776655"),
        ))
        val results = factory.facade.searchContacts("mom")
        assertThat(results).hasSize(1)
        assertThat(results[0].contact.displayName).isEqualTo("Mom")
    }

    @Test
    fun `addContact delegates to ContactManager`() = runTest {
        val factory = FacadeFactory()
        val newContact = factory.facade.addContact("Priya", "+919912345678", "priya@test.com")
        assertThat(newContact).isNotNull()
        assertThat(newContact!!.displayName).isEqualTo("Priya")
        assertThat(factory.contactService.added).hasSize(1)
        assertThat(factory.contactService.added[0].first).isEqualTo("Priya")
    }

    @Test
    fun `deleteContact delegates to ContactManager`() = runTest {
        val factory = FacadeFactory()
        val deleted = factory.facade.deleteContact("c1")
        assertThat(deleted).isTrue()
        assertThat(factory.contactService.deleted).contains("c1")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SIM OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `getActiveSims returns dual SIM list`() = runTest {
        val factory = FacadeFactory()
        val sims = factory.facade.getActiveSims()
        assertThat(sims).hasSize(2)
        assertThat(sims[0].carrierName).isEqualTo("Jio")
        assertThat(sims[1].carrierName).isEqualTo("Airtel")
    }

    @Test
    fun `setPreferredSim + getPreferredSim round-trip`() = runTest {
        val factory = FacadeFactory()
        factory.facade.setPreferredSim("c1", SimSlot.SIM2, isAutoLearned = false)
        val preferred = factory.facade.getPreferredSim("c1")
        assertThat(preferred).isEqualTo(SimSlot.SIM2)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UNIFIED INBOX
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `processIncomingNotification emits UnifiedMessage via Flow`() = runTest {
        val factory = FacadeFactory()
        val messageDeferred = kotlinx.coroutines.async {
            factory.facade.incomingMessages().first()
        }
        factory.facade.processIncomingNotification(MessagingApp.WHATSAPP, "Rohit", "Hey there!")
        val message = messageDeferred.await()
        assertThat(message.app).isEqualTo(MessagingApp.WHATSAPP)
        assertThat(message.senderName).isEqualTo("Rohit")
        assertThat(message.body).isEqualTo("Hey there!")
        assertThat(message.isRead).isFalse()
    }

    @Test
    fun `getUnreadMessages returns unread after processIncomingNotification`() = runTest {
        val factory = FacadeFactory()
        factory.facade.processIncomingNotification(MessagingApp.WHATSAPP, "Rohit", "Hi")
        factory.facade.processIncomingNotification(MessagingApp.TELEGRAM, "Priya", "Hello")
        val unread = factory.facade.getUnreadMessages()
        assertThat(unread).hasSize(2)
    }

    @Test
    fun `markAppRead marks message as read and reduces unread count`() = runTest {
        val factory = FacadeFactory()
        factory.facade.processIncomingNotification(MessagingApp.WHATSAPP, "Rohit", "Hi")
        factory.facade.markAppRead(MessagingApp.WHATSAPP)
        val unread = factory.facade.getUnreadMessages()
        assertThat(unread).isEmpty()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COMMS HISTORY
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `getUnifiedTimeline merges calls and SMS sorted by time desc`() = runTest {
        val now = System.currentTimeMillis()
        val factory = FacadeFactory(
            callLog = listOf(
                testCallLog(id = "call1", timestampMs = now - 1000, name = "Mom"),
            ),
            smsMessages = listOf(
                testSms(id = "sms1", timestampMs = now - 500, body = "Hello"),
            ),
        )
        val timeline = factory.facade.getUnifiedTimeline()
        assertThat(timeline).hasSize(2)
        assertThat(timeline[0].id).isEqualTo("sms_sms1")  // newer first
        assertThat(timeline[1].id).isEqualTo("call_call1")
    }

    @Test
    fun `searchComms filters by summary substring`() = runTest {
        val factory = FacadeFactory(
            smsMessages = listOf(
                testSms(id = "s1", body = "Flight confirmed"),
                testSms(id = "s2", body = "Meeting tomorrow"),
            ),
        )
        val results = factory.facade.searchComms("flight")
        assertThat(results).hasSize(1)
        assertThat(results[0].summary).contains("Flight confirmed")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SHARE
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `getShareTargets returns SMS target for short text`() = runTest {
        val factory = FacadeFactory()
        val content = ShareContent(text = "Hi", imageUri = null, fileUri = null, mimeType = "text/plain")
        val targets = factory.facade.getShareTargets(content)
        val smsTarget = targets.firstOrNull { it.appName == "SMS" }
        assertThat(smsTarget).isNotNull()
        assertThat(smsTarget!!.isRecommended).isTrue()  // short text → SMS recommended
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LIVE TRANSLATION
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `startLiveTranslation sets active and stop clears`() = runTest {
        val factory = FacadeFactory()
        assertThat(factory.facade.isLiveTranslationActive()).isFalse()
        factory.facade.startLiveTranslation("hi", "en")
        assertThat(factory.facade.isLiveTranslationActive()).isTrue()
        factory.facade.stopLiveTranslation()
        assertThat(factory.facade.isLiveTranslationActive()).isFalse()
    }

    @Test
    fun `processAudioChunkForTranslation translates and invokes display callback`() = runTest {
        val factory = FacadeFactory()
        factory.facade.startLiveTranslation("hi", "en")
        factory.facade.processAudioChunkForTranslation(ByteArray(64))
        // Verify both transcribe and translate were called.
        assertThat(factory.translationService.transcribeCount).isEqualTo(1)
        assertThat(factory.translationService.translateCount).isEqualTo(1)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CALL RECORDING
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `startCallRecording + stopCallRecording stores recording`() = runTest {
        val factory = FacadeFactory()
        val started = factory.facade.startCallRecording("Mom", "+919876543210")
        assertThat(started).isTrue()
        assertThat(factory.facade.isCallRecording()).isTrue()
        val recordingId = factory.facade.stopCallRecording()
        assertThat(recordingId).isNotNull()
        assertThat(factory.facade.isCallRecording()).isFalse()
        assertThat(factory.recordingStore.storeCount).isAtLeast(1)  // initial + transcript update
    }

    @Test
    fun `searchCallRecordings finds by transcript content`() = runTest {
        val factory = FacadeFactory()
        factory.facade.startCallRecording("Mom", "+919876543210")
        factory.facade.stopCallRecording()
        val results = factory.facade.searchCallRecordings("hello")
        assertThat(results).hasSize(1)
        assertThat(results[0].contactName).isEqualTo("Mom")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EMERGENCY SOS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `triggerSos returns COMPLETED when countdown completes`() = runTest {
        val factory = FacadeFactory()
        val trigger = SosTrigger(
            emergencyContacts = listOf("+919876543210"),
            locationEnabled = true,
            autoRecordEnabled = false,
            countdownSeconds = 1,
        )
        val status = factory.facade.triggerSos(trigger)
        assertThat(status).isEqualTo(SosStatus.COMPLETED)
        // Should have placed emergency call + sent panic SMS.
        assertThat(factory.callService.placedCalls.map { it.first }).contains("112")
        assertThat(factory.smsService.sent).isNotEmpty()
    }

    @Test
    fun `cancelSos during countdown returns CANCELLED`() = runTest {
        val factory = FacadeFactory()
        val trigger = SosTrigger(
            emergencyContacts = listOf("+919876543210"),
            locationEnabled = false,
            autoRecordEnabled = false,
            countdownSeconds = 5,  // longer countdown so we can cancel
        )
        // Launch trigger async — it will enter the countdown loop and suspend at delay(1000).
        val triggerDeferred = async {
            factory.facade.triggerSos(trigger)
        }
        // Give trigger a chance to start (it runs synchronously up to first delay).
        yield()
        // Cancel during countdown — sets cancelFlag = true.
        factory.facade.cancelSos()
        val status = triggerDeferred.await()
        assertThat(status).isEqualTo(SosStatus.CANCELLED)
        // Should NOT have placed emergency call.
        assertThat(factory.callService.placedCalls).isEmpty()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PROACTIVE COMMS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `getProactiveSuggestions returns empty list when no data`() = runTest {
        val factory = FacadeFactory()
        val suggestions = factory.facade.getProactiveSuggestions()
        // Empty data → empty suggestions.
        assertThat(suggestions).isNotNull()
        assertThat(suggestions).isEmpty()
    }

    @Test
    fun `getProactiveSuggestions returns unread suggestion when messages exist`() = runTest {
        val oldTimestamp = System.currentTimeMillis() - 3 * 3600_000L  // 3 hours ago
        val factory = FacadeFactory(
            smsMessages = listOf(
                testSms(id = "s1", body = "Hello", isRead = false, timestampMs = oldTimestamp),
            ),
        )
        val suggestions = factory.facade.getProactiveSuggestions()
        assertThat(suggestions).isNotEmpty()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SMS TEMPLATES
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `addSmsTemplate + getSmsTemplate round-trip`() {
        val factory = FacadeFactory()
        factory.facade.addSmsTemplate("meeting", "I'm in a meeting.")
        val template = factory.facade.getSmsTemplate("meeting")
        assertThat(template).isEqualTo("I'm in a meeting.")
    }

    @Test
    fun `listSmsTemplates returns all templates`() {
        val factory = FacadeFactory()
        factory.facade.addSmsTemplate("meeting", "In a meeting.")
        factory.facade.addSmsTemplate("driving", "Driving now.")
        val templates = factory.facade.listSmsTemplates()
        assertThat(templates).hasSize(2)
        assertThat(templates["meeting"]).isEqualTo("In a meeting.")
        assertThat(templates["driving"]).isEqualTo("Driving now.")
    }

    @Test
    fun `removeSmsTemplate removes by name`() {
        val factory = FacadeFactory()
        factory.facade.addSmsTemplate("meeting", "In a meeting.")
        val removed = factory.facade.removeSmsTemplate("meeting")
        assertThat(removed).isTrue()
        assertThat(factory.facade.getSmsTemplate("meeting")).isNull()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SPAM DETECTION
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `classifySpam returns LOW_RISK for unknown number`() = runTest {
        val factory = FacadeFactory()
        val classification = factory.facade.classifySpam("+919999988888")
        assertThat(classification.level).isEqualTo(SpamLevel.LOW_RISK)
    }

    @Test
    fun `reportSpam adds number to spam DB and classifySpam returns SPAM`() = runTest {
        val factory = FacadeFactory()
        factory.facade.reportSpam("+919999988888", "Telemarketer")
        val classification = factory.facade.classifySpam("+919999988888")
        assertThat(classification.level).isEqualTo(SpamLevel.SPAM)
        assertThat(classification.reason).contains("Telemarketer")
    }

    @Test
    fun `unblockNumber removes from spam DB`() = runTest {
        val factory = FacadeFactory()
        factory.facade.reportSpam("+919999988888", "Telemarketer")
        val removed = factory.facade.unblockNumber("+919999988888")
        assertThat(removed).isTrue()
        // After removal, classifySpam should return LOW_RISK again.
        val classification = factory.facade.classifySpam("+919999988888")
        assertThat(classification.level).isEqualTo(SpamLevel.LOW_RISK)
    }
}
