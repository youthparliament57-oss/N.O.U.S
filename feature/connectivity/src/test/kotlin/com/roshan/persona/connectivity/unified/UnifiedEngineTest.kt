// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.connectivity.unified

import android.content.Context
import android.content.pm.PackageManager
import com.google.common.truth.Truth.assertThat
import com.roshan.persona.connectivity.call.CallLogEntry
import com.roshan.persona.connectivity.call.CallLogService
import com.roshan.persona.connectivity.model.CallDirection
import com.roshan.persona.connectivity.model.MessagingApp
import com.roshan.persona.connectivity.model.ShareContent
import com.roshan.persona.connectivity.sms.SmsHistoryService
import com.roshan.persona.connectivity.model.SmsMessage
import com.roshan.persona.connectivity.model.SmsDirection
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

// CONNECTIVITY_FIX_001: Unified engine test coverage

// ─── Fakes ─────────────────────────────────────────────────────────────────

private class FakeNotificationListener(private val enabled: Boolean = true) : NotificationListenerInterface {
    override fun isEnabled(): Boolean = enabled
}

private class FakeCallLogService(private val entries: List<CallLogEntry> = emptyList()) : CallLogService {
    override suspend fun getCallLog(sinceMs: Long) = entries.filter { it.timestampMs >= sinceMs }
    override suspend fun getMissedCalls(sinceMs: Long) = entries.filter { it.direction == CallDirection.MISSED }
    override suspend fun deleteEntry(id: String) = false
    override suspend fun deleteAll() = 0
}

private class FakeSmsHistoryService(private val messages: List<SmsMessage> = emptyList()) : SmsHistoryService {
    override suspend fun getSmsHistory(sinceMs: Long) = messages.filter { it.timestampMs >= sinceMs }
    override suspend fun getUnreadSms() = messages.filter { !it.isRead }
    override suspend fun getSmsForContact(phoneNumber: String, sinceMs: Long) = messages.filter { it.phoneNumber == phoneNumber }
    override suspend fun markAsRead(messageId: String) = true
    override suspend fun deleteSms(messageId: String) = false
    override suspend fun deleteAll() = 0
}

private class FakeTranslationService(
    private val transcription: String? = "Namaste",
    private val translation: String? = "Hello",
) : TranslationService {
    var transcribeCount = 0; private set
    var translateCount = 0; private set
    override suspend fun transcribe(audioData: ByteArray): String? { transcribeCount++; return transcription }
    override suspend fun translate(text: String): String? { translateCount++; return translation }
}

// ─── MessagingUnificationEngine tests ─────────────────────────────────────

class MessagingUnificationEngineTest {

    private val listener = FakeNotificationListener(enabled = true)
    private val engine = MessagingUnificationEngine(listener, Dispatchers.Unconfined)

    @Test
    fun `isListenerEnabled returns configured value`() {
        assertThat(engine.isListenerEnabled()).isTrue()
    }

    @Test
    fun `processIncomingNotification emits UnifiedMessage`() = runTest {
        engine.processIncomingNotification(MessagingApp.WHATSAPP, "Mom", "Hello!")
        val message = engine.incomingMessages.first()
        assertThat(message.app).isEqualTo(MessagingApp.WHATSAPP)
        assertThat(message.senderName).isEqualTo("Mom")
        assertThat(message.body).isEqualTo("Hello!")
    }

    @Test
    fun `processIncomingNotification stores latest per app`() = runTest {
        engine.processIncomingNotification(MessagingApp.WHATSAPP, "Mom", "Hi")
        engine.processIncomingNotification(MessagingApp.TELEGRAM, "Rohit", "Hey")
        val unread = engine.getUnread()
        assertThat(unread).hasSize(2)
    }

    @Test
    fun `markAsRead removes from unread`() = runTest {
        engine.processIncomingNotification(MessagingApp.WHATSAPP, "Mom", "Hi")
        engine.markAsRead(MessagingApp.WHATSAPP)
        assertThat(engine.getUnread()).isEmpty()
    }

    @Test
    fun `getUnreadCounts returns count per app`() = runTest {
        engine.processIncomingNotification(MessagingApp.WHATSAPP, "Mom", "Hi")
        val counts = engine.getUnreadCounts()
        assertThat(counts[MessagingApp.WHATSAPP]).isEqualTo(1)
        assertThat(counts[MessagingApp.TELEGRAM]).isEqualTo(0)
    }
}

// ─── CommsHistoryEngine tests ──────────────────────────────────────────────

class CommsHistoryEngineTest {

    private fun makeCallEntry(phone: String, name: String?, daysAgo: Long, duration: Long = 300): CallLogEntry {
        val ts = System.currentTimeMillis() - (daysAgo * 24 * 60 * 60 * 1000)
        return CallLogEntry("call_$phone", phone, name, CallDirection.INCOMING, ts, duration)
    }

    private fun makeSms(phone: String, body: String, hoursAgo: Long, direction: SmsDirection = SmsDirection.INCOMING): SmsMessage {
        val ts = System.currentTimeMillis() - (hoursAgo * 60 * 60 * 1000)
        return SmsMessage("sms_$phone", null, phone, body, direction, ts)
    }

    private fun makeEngine(
        calls: List<CallLogEntry> = emptyList(),
        sms: List<SmsMessage> = emptyList(),
    ) = CommsHistoryEngine(FakeCallLogService(calls), FakeSmsHistoryService(sms), Dispatchers.Unconfined)

    @Test
    fun `getTimeline merges calls and SMS sorted by time`() = runTest {
        val calls = listOf(makeCallEntry("+91", "Mom", 1))
        val sms = listOf(makeSms("+91", "Hello", 2))
        val engine = makeEngine(calls, sms)
        val timeline = engine.getTimeline()
        assertThat(timeline).hasSize(2)
        // Most recent first (call is 1 day ago, SMS is 2 hours ago → SMS first).
        assertThat(timeline[0].type).isEqualTo(com.roshan.persona.connectivity.model.CommsType.SMS)
    }

    @Test
    fun `search finds matching entries by summary`() = runTest {
        val sms = listOf(makeSms("+91", "Flight at 6 PM", 1))
        val engine = makeEngine(sms = sms)
        val results = engine.search("flight")
        assertThat(results).hasSize(1)
        assertThat(results[0].summary).contains("Flight")
    }

    @Test
    fun `search returns empty for no match`() = runTest {
        val engine = makeEngine(sms = listOf(makeSms("+91", "Hello", 1)))
        assertThat(engine.search("nonexistent")).isEmpty()
    }

    @Test
    fun `getCommsForContact returns all comms for phone number`() = runTest {
        val calls = listOf(makeCallEntry("+919876543210", "Mom", 1))
        val sms = listOf(makeSms("+919876543210", "Hi Mom", 2))
        val engine = makeEngine(calls, sms)
        val results = engine.getCommsForContact("+919876543210")
        assertThat(results).hasSize(2)
    }

    @Test
    fun `generateInsights returns frequency insight for imbalanced contacts`() = runTest {
        val calls = listOf(
            makeCallEntry("+91", "Mom", 1),
            makeCallEntry("+91", "Mom", 2),
            makeCallEntry("+91", "Mom", 3),
            makeCallEntry("+91", "Mom", 4),
            makeCallEntry("+91", "Mom", 5),
            makeCallEntry("+92", "Dad", 1),
        )
        val engine = makeEngine(calls = calls)
        val insights = engine.generateInsights()
        assertThat(insights.any { it.description.contains("× more") }).isTrue()
    }

    @Test
    fun `generateInsights returns average call duration`() = runTest {
        val calls = listOf(
            makeCallEntry("+91", "Mom", 1, 300),
            makeCallEntry("+91", "Mom", 2, 600),
        )
        val engine = makeEngine(calls = calls)
        val insights = engine.generateInsights()
        assertThat(insights.any { it.description.contains("average call") }).isTrue()
    }
}

// ─── ShareEngine tests ─────────────────────────────────────────────────────

class ShareEngineTest {

    private val mockContext: Context = mockk()
    private val mockPackageManager: PackageManager = mockk()

    private fun makeEngine(): ShareEngine {
        every { mockContext.packageManager } returns mockPackageManager
        every { mockContext.getSystemService(Context.CLIPBOARD_SERVICE) } returns mockk<android.content.ClipboardManager>(relaxed = true)
        return ShareEngine(mockContext, Dispatchers.Unconfined)
    }

    @Test
    fun `getShareTargets includes SMS always`() = runTest {
        every { mockPackageManager.getPackageInfo(any<String>(), any()) } throws PackageManager.NameNotFoundException()
        val engine = makeEngine()
        val targets = engine.getShareTargets(ShareContent(text = "Hello", imageUri = null, fileUri = null, mimeType = "text/plain"))
        assertThat(targets.any { it.appName == "SMS" }).isTrue()
    }

    @Test
    fun `getShareTargets includes WhatsApp when installed`() = runTest {
        every { mockPackageManager.getPackageInfo(eq("com.whatsapp"), any()) } returns mockk()
        every { mockPackageManager.getPackageInfo(ne("com.whatsapp"), any()) } throws PackageManager.NameNotFoundException()
        val engine = makeEngine()
        val targets = engine.getShareTargets(ShareContent(text = null, imageUri = "content://image", fileUri = null, mimeType = "image/jpeg"))
        assertThat(targets.any { it.appName == "WhatsApp" }).isTrue()
        assertThat(targets.first { it.appName == "WhatsApp" }.isRecommended).isTrue() // photos → WhatsApp recommended
    }

    @Test
    fun `getShareTargets includes clipboard for text content`() = runTest {
        every { mockPackageManager.getPackageInfo(any<String>(), any()) } throws PackageManager.NameNotFoundException()
        val engine = makeEngine()
        val targets = engine.getShareTargets(ShareContent(text = "Hello", imageUri = null, fileUri = null, mimeType = "text/plain"))
        assertThat(targets.any { it.appName == "Clipboard" }).isTrue()
    }
}

// ─── LiveTranslationBridge tests ──────────────────────────────────────────

class LiveTranslationBridgeTest {

    @Test
    fun `start sets isActive to true`() {
        val bridge = LiveTranslationBridge(
            translationService = FakeTranslationService(),
            displayCallback = {},
            dispatcher = Dispatchers.Unconfined,
        )
        bridge.start("hi", "en")
        assertThat(bridge.isLive()).isTrue()
    }

    @Test
    fun `stop sets isActive to false`() {
        val bridge = LiveTranslationBridge(
            translationService = FakeTranslationService(),
            displayCallback = {},
            dispatcher = Dispatchers.Unconfined,
        )
        bridge.start("hi", "en")
        bridge.stop()
        assertThat(bridge.isLive()).isFalse()
    }

    @Test
    fun `start does nothing when translation service is null`() {
        val bridge = LiveTranslationBridge(
            translationService = null,
            displayCallback = {},
            dispatcher = Dispatchers.Unconfined,
        )
        bridge.start("hi", "en")
        assertThat(bridge.isLive()).isFalse()
    }

    @Test
    fun `processAudioChunk translates and displays`() = runTest {
        val displayed = mutableListOf<String>()
        val bridge = LiveTranslationBridge(
            translationService = FakeTranslationService(transcription = "Namaste", translation = "Hello"),
            displayCallback = { displayed.add(it) },
            dispatcher = Dispatchers.Unconfined,
        )
        bridge.start("hi", "en")
        bridge.processAudioChunk(ByteArray(100))
        assertThat(displayed).contains("Hello")
    }

    @Test
    fun `processAudioChunk does nothing when not active`() = runTest {
        val translation = FakeTranslationService()
        val bridge = LiveTranslationBridge(translation, {}, Dispatchers.Unconfined)
        // Not started — should not call translation service.
        bridge.processAudioChunk(ByteArray(100))
        assertThat(translation.transcribeCount).isEqualTo(0)
    }
}
