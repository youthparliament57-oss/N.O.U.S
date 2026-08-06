// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.connectivity.sms

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.connectivity.model.ContactInfo
import com.roshan.persona.connectivity.model.SmsDirection
import com.roshan.persona.connectivity.model.SmsMessage
import com.roshan.persona.connectivity.model.SmsStatus
import com.roshan.persona.connectivity.model.SmartReplySource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test

// CONNECTIVITY_FIX_009: SMS engine validated

// ─── Fakes ─────────────────────────────────────────────────────────────────

private class FakeSmsService(
    private val hasPerm: Boolean = true,
    private val sendResult: SmsStatus = SmsStatus.SENT,
) : SmsService {
    val sentMessages = mutableListOf<Pair<String, String>>()
    override suspend fun send(phoneNumber: String, message: String): SmsStatus {
        sentMessages.add(phoneNumber to message)
        return sendResult
    }
    override fun hasPermission(): Boolean = hasPerm
}

private class FakeSmsHistory(
    private val messages: List<SmsMessage> = emptyList(),
) : SmsHistoryService {
    val readIds = mutableListOf<String>()
    val deletedIds = mutableListOf<String>()

    override suspend fun getSmsHistory(sinceMs: Long): List<SmsMessage> = messages
    override suspend fun getUnreadSms(): List<SmsMessage> = messages.filter { !it.isRead }
    override suspend fun getSmsForContact(phoneNumber: String, sinceMs: Long): List<SmsMessage> =
        messages.filter { it.phoneNumber == phoneNumber }
    override suspend fun markAsRead(messageId: String): Boolean { readIds.add(messageId); return true }
    override suspend fun deleteSms(messageId: String): Boolean { deletedIds.add(messageId); return true }
    override suspend fun deleteAll(): Int { val n = messages.size; return n }
}

private class FakeSmsTtsReader : SmsTtsReader {
    val spoken = mutableListOf<String>()
    override fun speak(text: String) { spoken.add(text) }
}

private class FakeSmartReplyLlm(private val response: String, private val ready: Boolean = true) : SmartReplyLlm {
    override suspend fun generate(prompt: String): String = response
    override fun isReady(): Boolean = ready
}

private fun makeSmsMessage(
    body: String = "Hello!",
    isRead: Boolean = false,
    contact: ContactInfo? = null,
) = SmsMessage(
    id = "msg_${System.nanoTime()}",
    contact = contact,
    phoneNumber = "+919876543210",
    body = body,
    direction = SmsDirection.INCOMING,
    timestampMs = System.currentTimeMillis(),
    isRead = isRead,
)

// ─── PiiScrubber tests ────────────────────────────────────────────────────

class PiiScrubberTest {

    @Test
    fun `scrub replaces phone numbers`() {
        val result = PiiScrubber.scrub("Call me at +919876543210")
        assertThat(result).contains("[PHONE]")
        assertThat(result).doesNotContain("919876543210")
    }

    @Test
    fun `scrub replaces email addresses`() {
        val result = PiiScrubber.scrub("Email me at test@example.com")
        assertThat(result).contains("[EMAIL]")
        assertThat(result).doesNotContain("test@example.com")
    }

    @Test
    fun `scrub replaces OTPs`() {
        val result = PiiScrubber.scrub("Your OTP is 123456")
        assertThat(result).contains("[OTP]")
    }

    @Test
    fun `scrub replaces URLs`() {
        val result = PiiScrubber.scrub("Visit https://example.com now")
        assertThat(result).contains("[URL]")
    }

    @Test
    fun `scrub replaces credit card numbers`() {
        val result = PiiScrubber.scrub("Card: 1234-5678-9012-3456")
        assertThat(result).contains("[CARD]")
    }

    @Test
    fun `scrub leaves clean text unchanged`() {
        val clean = "Hello, how are you?"
        assertThat(PiiScrubber.scrub(clean)).isEqualTo(clean)
    }

    @Test
    fun `containsPii returns true for phone number`() {
        assertThat(PiiScrubber.containsPii("Call +919876543210")).isTrue()
    }

    @Test
    fun `containsPii returns false for clean text`() {
        assertThat(PiiScrubber.containsPii("Hello there")).isFalse()
    }

    @Test
    fun `extractOtp finds OTP in common patterns`() {
        assertThat(PiiScrubber.extractOtp("Your OTP is 123456")).isEqualTo("123456")
        assertThat(PiiScrubber.extractOtp("code: 9876")).isEqualTo("9876")
        assertThat(PiiScrubber.extractOtp("verification: 54321")).isEqualTo("54321")
    }

    @Test
    fun `extractOtp returns null when no OTP found`() {
        assertThat(PiiScrubber.extractOtp("Hello there!")).isNull()
    }
}

// ─── SmartReplyEngine tests ────────────────────────────────────────────────

class SmartReplyEngineTest {

    private fun makeEngine(
        llm: SmartReplyLlm? = null,
        context: SmartReplyContext = SmartReplyContext(),
    ) = SmartReplyEngine(
        llm = llm,
        contextProvider = { context },
        dispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `generateReplies returns LLM replies when LLM available`() = runTest {
        val llm = FakeSmartReplyLlm("Yes!\nSure!\nNo problem!")
        val engine = makeEngine(llm = llm)
        val result = engine.generateReplies("Can you come?")
        assertThat(result.replies).hasSize(3)
        assertThat(result.source).isEqualTo(SmartReplySource.LOCAL_LLM)
    }

    @Test
    fun `generateReplies falls back to templates when LLM unavailable`() = runTest {
        val engine = makeEngine(llm = null)
        val result = engine.generateReplies("Where are you?")
        assertThat(result.replies).isNotEmpty()
        assertThat(result.source).isEqualTo(SmartReplySource.TEMPLATE)
    }

    @Test
    fun `generateReplies returns CONTEXTUAL source when Smart Payload available`() = runTest {
        val llm = FakeSmartReplyLlm("On my way!\nBe there soon!\nRunning late!")
        val engine = makeEngine(
            llm = llm,
            context = SmartReplyContext(isDriving = true, etaMinutes = 15),
        )
        val result = engine.generateReplies("Where are you?")
        assertThat(result.source).isEqualTo(SmartReplySource.CONTEXTUAL)
        assertThat(result.contextPayload).contains("driving")
        assertThat(result.contextPayload).contains("ETA 15m")
        assertThat(result.contextPayload).contains("NOUS AI")
    }

    @Test
    fun `buildPayload returns null when no context`() {
        val engine = makeEngine(context = SmartReplyContext())
        assertThat(engine.buildPayload(SmartReplyContext())).isNull()
    }

    @Test
    fun `buildPayload includes driving context with ETA`() {
        val engine = makeEngine()
        val payload = engine.buildPayload(SmartReplyContext(isDriving = true, etaMinutes = 10))
        assertThat(payload).contains("driving")
        assertThat(payload).contains("ETA 10m")
    }

    @Test
    fun `buildPayload includes meeting context`() {
        val engine = makeEngine()
        val payload = engine.buildPayload(SmartReplyContext(isInMeeting = true, meetingEndsAt = "3 PM"))
        assertThat(payload).contains("meeting")
    }

    @Test
    fun `buildPayload includes sleeping context`() {
        val engine = makeEngine()
        val payload = engine.buildPayload(SmartReplyContext(isSleeping = true))
        assertThat(payload).contains("asleep")
    }

    @Test
    fun `parseLlmReplies extracts 3 replies from response`() {
        val engine = makeEngine()
        val replies = engine.parseLlmReplies("Yes!\nSure!\nNo problem!")
        assertThat(replies).hasSize(3)
        assertThat(replies[0]).isEqualTo("Yes!")
    }

    @Test
    fun `parseLlmReplies skips blank lines and Reply headers`() {
        val engine = makeEngine()
        val replies = engine.parseLlmReplies("Reply 1:\nYes!\n\nReply 2:\nNo!")
        assertThat(replies).containsExactly("Yes!", "No!")
    }

    @Test
    fun `template replies for 'where are you' include on my way`() {
        val engine = makeEngine()
        val replies = engine.generateTemplateReplies("Where are you?", SmartReplyContext())
        assertThat(replies).contains("On my way!")
    }

    @Test
    fun `template replies prepend Smart Payload when context available`() {
        val engine = makeEngine()
        val replies = engine.generateTemplateReplies("Hello", SmartReplyContext(isDriving = true))
        assertThat(replies[0]).contains("driving")
        assertThat(replies[0]).contains("NOUS AI")
    }
}

// ─── SmsEngine tests ────────────────────────────────────────────────────────

class SmsEngineTest {

    private val smsService = FakeSmsService()
    private val smsHistory = FakeSmsHistory()
    private val ttsReader = FakeSmsTtsReader()
    private val smartReply = SmartReplyEngine(null, { SmartReplyContext() }, Dispatchers.Unconfined)
    private val engine = SmsEngine(smsService, smsHistory, smartReply, ttsReader, Dispatchers.Unconfined)

    @Test
    fun `send delegates to SmsService`() = runTest {
        val result = engine.send("+919876543210", "Hello")
        assertThat(result).isEqualTo(SmsStatus.SENT)
        assertThat(smsService.sentMessages).contains("+919876543210" to "Hello")
    }

    @Test
    fun `readUnreadAloud reads all unread messages`() = runTest {
        val msg1 = makeSmsMessage("Hello!", isRead = false)
        val msg2 = makeSmsMessage("How are you?", isRead = false)
        val history = FakeSmsHistory(listOf(msg1, msg2))
        val engine = SmsEngine(smsService, history, smartReply, ttsReader, Dispatchers.Unconfined)
        val read = engine.readUnreadAloud()
        assertThat(read).hasSize(2)
        assertThat(ttsReader.spoken).hasSize(4) // "You have 2 unread" + 2 messages + nothing else
        assertThat(ttsReader.spoken.any { it.contains("2 unread") }).isTrue()
        assertThat(history.readIds).hasSize(2)
    }

    @Test
    fun `readUnreadAloud speaks no unread when empty`() = runTest {
        val engine = SmsEngine(smsService, FakeSmsHistory(emptyList()), smartReply, ttsReader, Dispatchers.Unconfined)
        engine.readUnreadAloud()
        assertThat(ttsReader.spoken).contains("No unread messages.")
    }

    @Test
    fun `getSmartReplies delegates to SmartReplyEngine`() = runTest {
        val msg = makeSmsMessage("Where are you?")
        val result = engine.getSmartReplies(msg)
        assertThat(result.replies).isNotEmpty()
    }

    @Test
    fun `searchHistory finds matching messages`() = runTest {
        val msg1 = makeSmsMessage("Flight is at 6 PM")
        val msg2 = makeSmsMessage("See you tomorrow")
        val history = FakeSmsHistory(listOf(msg1, msg2))
        val engine = SmsEngine(smsService, history, smartReply, ttsReader, Dispatchers.Unconfined)
        val results = engine.searchHistory("flight")
        assertThat(results).hasSize(1)
        assertThat(results[0].body).contains("Flight")
    }
}

// ─── SmsTemplateManager tests ─────────────────────────────────────────────

class SmsTemplateManagerTest {

    private val manager = SmsTemplateManager()

    @Test
    fun `setTemplate then getTemplate returns message`() {
        manager.setTemplate("meeting", "I'm in a meeting")
        assertThat(manager.getTemplate("meeting")).isEqualTo("I'm in a meeting")
    }

    @Test
    fun `getTemplate is case-insensitive`() {
        manager.setTemplate("DRIVING", "I'm driving")
        assertThat(manager.getTemplate("driving")).isEqualTo("I'm driving")
    }

    @Test
    fun `getTemplate returns null for unknown key`() {
        assertThat(manager.getTemplate("nonexistent")).isNull()
    }

    @Test
    fun `removeTemplate removes by key`() {
        manager.setTemplate("busy", "I'm busy")
        assertThat(manager.removeTemplate("busy")).isTrue()
        assertThat(manager.getTemplate("busy")).isNull()
    }

    @Test
    fun `removeTemplate returns false for unknown key`() {
        assertThat(manager.removeTemplate("nonexistent")).isFalse()
    }

    @Test
    fun `getAllTemplates returns all set templates`() {
        manager.setTemplate("a", "msg a")
        manager.setTemplate("b", "msg b")
        assertThat(manager.getAllTemplates()).hasSize(2)
    }

    @Test
    fun `clear removes all templates`() {
        manager.setTemplate("a", "msg a")
        manager.clear()
        assertThat(manager.getAllTemplates()).isEmpty()
    }

    @Test
    fun `DEFAULT_TEMPLATES has 8 entries`() {
        assertThat(SmsTemplateManager.DEFAULT_TEMPLATES).hasSize(8)
    }
}

// ─── AutoReplyEngine tests ────────────────────────────────────────────────

class AutoReplyEngineTest {

    private val smsService = FakeSmsService()

    private fun makeEngine(
        context: SmartReplyContext = SmartReplyContext(),
        enabled: Boolean = true,
    ) = AutoReplyEngine(smsService, { context }, enabled, Dispatchers.Unconfined)

    @Test
    fun `shouldAutoReply returns false when disabled`() {
        val engine = makeEngine(enabled = false)
        assertThat(engine.shouldAutoReply()).isFalse()
    }

    @Test
    fun `shouldAutoReply returns true when driving`() {
        val engine = makeEngine(context = SmartReplyContext(isDriving = true))
        assertThat(engine.shouldAutoReply()).isTrue()
    }

    @Test
    fun `shouldAutoReply returns true when in meeting`() {
        val engine = makeEngine(context = SmartReplyContext(isInMeeting = true))
        assertThat(engine.shouldAutoReply()).isTrue()
    }

    @Test
    fun `shouldAutoReply returns false when no context and no custom message`() {
        val engine = makeEngine(context = SmartReplyContext())
        assertThat(engine.shouldAutoReply()).isFalse()
    }

    @Test
    fun `generateAutoReply includes driving context with ETA`() {
        val engine = makeEngine(context = SmartReplyContext(isDriving = true, etaMinutes = 15))
        val reply = engine.generateAutoReply()
        assertThat(reply).contains("driving")
        assertThat(reply).contains("ETA 15m")
        assertThat(reply).contains("NOUS AI")
    }

    @Test
    fun `generateAutoReply includes meeting context`() {
        val engine = makeEngine(context = SmartReplyContext(isInMeeting = true, meetingEndsAt = "3 PM"))
        val reply = engine.generateAutoReply()
        assertThat(reply).contains("meeting")
        assertThat(reply).contains("3 PM")
        assertThat(reply).contains("NOUS AI")
    }

    @Test
    fun `generateAutoReply includes sleeping context`() {
        val engine = makeEngine(context = SmartReplyContext(isSleeping = true))
        val reply = engine.generateAutoReply()
        assertThat(reply).contains("asleep")
        assertThat(reply).contains("NOUS AI")
    }

    @Test
    fun `generateAutoReply uses custom message when set`() {
        val engine = makeEngine()
        engine.setCustomMessage("On vacation!")
        val reply = engine.generateAutoReply()
        assertThat(reply).contains("On vacation!")
        assertThat(reply).contains("NOUS AI")
    }

    @Test
    fun `sendAutoReply sends generated message`() = runTest {
        val engine = makeEngine(context = SmartReplyContext(isDriving = true))
        engine.sendAutoReply("+919876543210")
        assertThat(smsService.sentMessages).hasSize(1)
        assertThat(smsService.sentMessages[0].first).isEqualTo("+919876543210")
        assertThat(smsService.sentMessages[0].second).contains("driving")
    }

    @Test
    fun `clearCustomMessage reverts to context-based`() {
        val engine = makeEngine(context = SmartReplyContext(isDriving = true))
        engine.setCustomMessage("Custom")
        engine.clearCustomMessage()
        val reply = engine.generateAutoReply()
        assertThat(reply).contains("driving") // context-based, not custom
    }
}
