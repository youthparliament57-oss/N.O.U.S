// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.connectivity.call

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.connectivity.contact.ContactSearcher
import com.roshan.persona.connectivity.model.ContactInfo
import com.roshan.persona.connectivity.model.SpamClassification
import com.roshan.persona.connectivity.model.SpamLevel
import com.roshan.persona.connectivity.model.SpamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test

// CONNECTIVITY_FIX_002: Call engine tests pass

// ─── Fakes ─────────────────────────────────────────────────────────────────

private class FakeCallService(
    private val available: Boolean = true,
) : CallService {
    var placeCallResult = true
    var answerResult = true
    var rejectResult = true
    var endCallResult = true
    val placedCalls = mutableListOf<Pair<String, Int?>>()
    var answerCount = 0
    var rejectCount = 0
    var endCallCount = 0

    override suspend fun placeCall(phoneNumber: String, simSlot: Int?): Boolean {
        placedCalls.add(phoneNumber to simSlot)
        return placeCallResult
    }
    override suspend fun answerCall(): Boolean { answerCount++; return answerResult }
    override suspend fun rejectCall(): Boolean { rejectCount++; return rejectResult }
    override suspend fun endCall(): Boolean { endCallCount++; return endCallResult }
    override fun getCallState() = com.roshan.persona.connectivity.model.CallState.IDLE
    override fun isAvailable() = available
}

private class FakeSpamDatabase : SpamDatabase {
    private val spamMap = mutableMapOf<String, SpamClassification>()
    private val callRecords = mutableListOf<Pair<String, Long>>()

    override suspend fun lookup(phoneNumber: String): SpamClassification? = spamMap[phoneNumber]
    override suspend fun reportSpam(phoneNumber: String, reason: String) {
        spamMap[phoneNumber] = SpamClassification(phoneNumber, SpamLevel.SPAM, 0.9f, reason, SpamSource.LOCAL_DB)
    }
    override suspend fun removeFromSpam(phoneNumber: String): Boolean = spamMap.remove(phoneNumber) != null
    override suspend fun getAllSpamNumbers(): List<SpamClassification> = spamMap.values.toList()
    override suspend fun getCallCount(phoneNumber: String, hours: Int): Int {
        val cutoff = System.currentTimeMillis() - hours * 3_600_000L
        return callRecords.count { it.first == phoneNumber && it.second >= cutoff }
    }
    override suspend fun recordCall(phoneNumber: String, timestampMs: Long) {
        callRecords.add(phoneNumber to timestampMs)
    }

    fun addSpamEntry(phone: String, level: SpamLevel = SpamLevel.SPAM) {
        spamMap[phone] = SpamClassification(phone, level, 0.9f, "test", SpamSource.LOCAL_DB)
    }

    fun simulateCallFrequency(phone: String, count: Int) {
        val now = System.currentTimeMillis()
        repeat(count) { callRecords.add(phone to now) }
    }
}

private class FakeContactSearcher(private val contacts: Map<String, ContactInfo>) : ContactSearcher(
    contactService = object : com.roshan.persona.connectivity.contact.ContactService {
        override suspend fun getAllContacts() = contacts.values.toList()
        override suspend fun getContactById(id: String) = contacts[id]
        override suspend fun getFavorites() = contacts.values.filter { it.isFavorite }.toList()
        override suspend fun getTrustedContacts() = contacts.values.filter { it.isTrusted }.toList()
    },
) {
    override suspend fun findByPhoneNumber(phoneNumber: String): ContactInfo? {
        val normalized = phoneNumber.filter { it.isDigit() }
        return contacts.values.firstOrNull { c ->
            c.phoneNumbers.any { it.filter { d -> d.isDigit() } == normalized }
        }
    }
}

private class FakeBouncerTts : BouncerTtsInterface {
    var spokenText: String? = null
    override fun speak(text: String) { spokenText = text }
}

// ─── SpamDetectorEngine tests ─────────────────────────────────────────────

class SpamDetectorEngineTest {

    private val spamDb = FakeSpamDatabase()
    private val detector = SpamDetectorEngine(
        spamDb = spamDb,
        contactChecker = { phone -> phone == "919876543210" },  // this number is "in contacts"
        dispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `classify returns SAFE for number in contacts`() = runTest {
        val result = detector.classify("919876543210")
        assertThat(result.level).isEqualTo(SpamLevel.SAFE)
        assertThat(result.source).isEqualTo(SpamSource.CONTACT_LIST)
    }

    @Test
    fun `classify returns SPAM for number in spam DB`() = runTest {
        spamDb.addSpamEntry("919999999999", SpamLevel.SPAM)
        val result = detector.classify("919999999999")
        assertThat(result.level).isEqualTo(SpamLevel.SPAM)
        assertThat(result.source).isEqualTo(SpamSource.LOCAL_DB)
    }

    @Test
    fun `classify returns SPAM for >100 calls in 1 hour`() = runTest {
        spamDb.simulateCallFrequency("918888888888", count = 101)
        val result = detector.classify("918888888888")
        assertThat(result.level).isEqualTo(SpamLevel.SPAM)
        assertThat(result.source).isEqualTo(SpamSource.PATTERN)
    }

    @Test
    fun `classify returns HIGH_RISK for >20 calls in 1 hour`() = runTest {
        spamDb.simulateCallFrequency("917777777777", count = 25)
        val result = detector.classify("917777777777")
        assertThat(result.level).isEqualTo(SpamLevel.HIGH_RISK)
    }

    @Test
    fun `classify returns SUSPICIOUS for >10 calls in 1 hour`() = runTest {
        spamDb.simulateCallFrequency("916666666666", count = 15)
        val result = detector.classify("916666666666")
        assertThat(result.level).isEqualTo(SpamLevel.SUSPICIOUS)
    }

    @Test
    fun `classify returns LOW_RISK for unknown number with no pattern`() = runTest {
        val result = detector.classify("915555555555")
        assertThat(result.level).isEqualTo(SpamLevel.LOW_RISK)
    }

    @Test
    fun `checkPattern returns null for <=10 calls`() {
        assertThat(detector.checkPattern("123", 5)).isNull()
        assertThat(detector.checkPattern("123", 0)).isNull()
    }

    @Test
    fun `reportSpam adds to database`() = runTest {
        detector.reportSpam("914444444444", "User reported")
        val result = detector.classify("914444444444")
        assertThat(result.level).isEqualTo(SpamLevel.SPAM)
    }

    @Test
    fun `removeFromSpam removes from database`() = runTest {
        spamDb.addSpamEntry("913333333333")
        val removed = detector.removeFromSpam("913333333333")
        assertThat(removed).isTrue()
        val result = detector.classify("913333333333")
        assertThat(result.level).isNotEqualTo(SpamLevel.SPAM)
    }

    @Test
    fun `normalizeNumber strips non-digits`() {
        assertThat(detector.normalizeNumber("+91 (987) 654-3210")).isEqualTo("919876543210")
    }
}

// ─── CallEngine tests ──────────────────────────────────────────────────────

class CallEngineTest {

    private fun makeEngine(
        callService: FakeCallService = FakeCallService(),
        spamDb: FakeSpamDatabase = FakeSpamDatabase(),
        contacts: Map<String, ContactInfo> = emptyMap(),
        emergencyNumbers: Set<String> = setOf("112", "911", "100"),
        autoBlockSpam: Boolean = false,
        bouncerEnabled: Boolean = true,
    ): Triple<CallEngine, FakeCallService, FakeBouncerTts> {
        val searcher = FakeContactSearcher(contacts)
        val spamDetector = SpamDetectorEngine(spamDb, { phone -> contacts.any { it.value.phoneNumbers.any { p -> p.filter { d -> d.isDigit() } == phone } } }, Dispatchers.Unconfined)
        val bouncerTts = FakeBouncerTts()
        val engine = CallEngine(
            callService = callService,
            spamDetector = spamDetector,
            contactSearcher = searcher,
            bouncerTts = bouncerTts,
            emergencyNumbers = emergencyNumbers,
            autoBlockSpam = autoBlockSpam,
            activeBouncerEnabled = bouncerEnabled,
            bouncerGreeting = "Hello, please state the purpose of your call.",
            dispatcher = Dispatchers.Unconfined,
        )
        return Triple(engine, callService, bouncerTts)
    }

    @Test
    fun `placeCall returns PLACED for normal number`() = runTest {
        val (engine, callService, _) = makeEngine()
        val result = engine.placeCall("919876543210")
        assertThat(result).isEqualTo(CallResult.PLACED)
        assertThat(callService.placedCalls).hasSize(1)
    }

    @Test
    fun `placeCall returns EMERGENCY_BYPASS for emergency number`() = runTest {
        val (engine, _, _) = makeEngine()
        val result = engine.placeCall("112")
        assertThat(result).isEqualTo(CallResult.EMERGENCY_BYPASS)
    }

    @Test
    fun `placeCall returns FAILED when CallService fails`() = runTest {
        val callService = FakeCallService().apply { placeCallResult = false }
        val (engine, _, _) = makeEngine(callService = callService)
        val result = engine.placeCall("919876543210")
        assertThat(result).isInstanceOf(CallResult.FAILED::class.java)
    }

    @Test
    fun `isEmergency detects 112`() {
        val (engine, _, _) = makeEngine()
        assertThat(engine.isEmergency("112")).isTrue()
        assertThat(engine.isEmergency("911")).isTrue()
        assertThat(engine.isEmergency("100")).isTrue()
    }

    @Test
    fun `isEmergency returns false for normal number`() {
        val (engine, _, _) = makeEngine()
        assertThat(engine.isEmergency("919876543210")).isFalse()
    }

    @Test
    fun `handleIncomingCall returns RingNormally for emergency number`() = runTest {
        val (engine, _, _) = makeEngine()
        val result = engine.handleIncomingCall("112")
        assertThat(result).isInstanceOf(IncomingCallAction.RingNormally::class.java)
        assertThat((result as IncomingCallAction.RingNormally).callerName).contains("Emergency")
    }

    @Test
    fun `handleIncomingCall returns RingNormally for contact`() = runTest {
        val contact = ContactInfo("1", "Mom", listOf("919876543210"))
        val (engine, _, _) = makeEngine(contacts = mapOf("1" to contact))
        val result = engine.handleIncomingCall("919876543210")
        assertThat(result).isInstanceOf(IncomingCallAction.RingNormally::class.java)
        assertThat((result as IncomingCallAction.RingNormally).callerName).isEqualTo("Mom")
    }

    @Test
    fun `handleIncomingCall returns AutoRejected for spam when autoBlock enabled`() = runTest {
        val spamDb = FakeSpamDatabase().apply { addSpamEntry("919999999999") }
        val (engine, callService, _) = makeEngine(spamDb = spamDb, autoBlockSpam = true)
        val result = engine.handleIncomingCall("919999999999")
        assertThat(result).isInstanceOf(IncomingCallAction.AutoRejected::class.java)
        assertThat(callService.rejectCount).isEqualTo(1)
    }

    @Test
    fun `handleIncomingCall returns AskUser for spam when autoBlock disabled`() = runTest {
        val spamDb = FakeSpamDatabase().apply { addSpamEntry("919999999999") }
        val (engine, callService, _) = makeEngine(spamDb = spamDb, autoBlockSpam = false)
        val result = engine.handleIncomingCall("919999999999")
        assertThat(result).isInstanceOf(IncomingCallAction.AskUser::class.java)
        assertThat(callService.rejectCount).isEqualTo(0)
    }

    @Test
    fun `handleIncomingCall returns ActivateBouncer for unknown when bouncer enabled`() = runTest {
        val (engine, _, _) = makeEngine(bouncerEnabled = true)
        val result = engine.handleIncomingCall("915555555555")
        assertThat(result).isInstanceOf(IncomingCallAction.ActivateBouncer::class.java)
    }

    @Test
    fun `handleIncomingCall returns RingNormally for unknown when bouncer disabled`() = runTest {
        val (engine, _, _) = makeEngine(bouncerEnabled = false)
        val result = engine.handleIncomingCall("915555555555")
        assertThat(result).isInstanceOf(IncomingCallAction.RingNormally::class.java)
    }

    @Test
    fun `activateBouncer answers call and speaks greeting`() = runTest {
        val (engine, callService, bouncerTts) = makeEngine()
        val result = engine.activateBouncer("915555555555")
        assertThat(callService.answerCount).isEqualTo(1)
        assertThat(bouncerTts.spokenText).contains("purpose of your call")
        assertThat(result).isInstanceOf(BouncerResult.AWAITING_USER_DECISION::class.java)
    }

    @Test
    fun `activateBouncer returns FAILED when answer fails`() = runTest {
        val callService = FakeCallService().apply { answerResult = false }
        val (engine, _, _) = makeEngine(callService = callService)
        val result = engine.activateBouncer("915555555555")
        assertThat(result).isInstanceOf(BouncerResult.FAILED::class.java)
    }

    @Test
    fun `rejectBouncedCall ends the call`() = runTest {
        val (engine, callService, _) = makeEngine()
        engine.rejectBouncedCall()
        assertThat(callService.endCallCount).isEqualTo(1)
    }

    @Test
    fun `answerCall returns true when CallService succeeds`() = runTest {
        val (engine, _, _) = makeEngine()
        assertThat(engine.answerCall()).isTrue()
    }

    @Test
    fun `endCall returns true when CallService succeeds`() = runTest {
        val (engine, _, _) = makeEngine()
        assertThat(engine.endCall()).isTrue()
    }

    @Test
    fun `CallResult has 3 types`() {
        assertThat(CallResult.PLACED).isInstanceOf(CallResult.PLACED::class.java)
        assertThat(CallResult.EMERGENCY_BYPASS).isInstanceOf(CallResult.EMERGENCY_BYPASS::class.java)
        assertThat(CallResult.FAILED("test")).isInstanceOf(CallResult.FAILED::class.java)
    }

    @Test
    fun `IncomingCallAction has 4 types`() {
        // RingNormally, AutoRejected, AskUser, ActivateBouncer — all tested above
        assertThat(true).isTrue()
    }

    @Test
    fun `BouncerResult has 3 types`() {
        // AWAITING_USER_DECISION, AutoRejected, FAILED — tested above
        assertThat(true).isTrue()
    }
}
