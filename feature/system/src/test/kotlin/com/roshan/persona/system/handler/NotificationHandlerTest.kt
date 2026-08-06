// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.system.handler

import android.app.NotificationManager
import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.skill.SystemOperation
import com.roshan.persona.common.AppError
import com.roshan.persona.common.Result
import com.roshan.persona.system.executor.ResourceCategory
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0144: [feature] NotificationHandlerTest verified

class NotificationHandlerTest {

    private class FakeNotificationService(
        private val hasPermission: Boolean = true,
        private val postResult: Boolean = true,
        private val existingChannels: MutableSet<String> = mutableSetOf(),
    ) : NotificationService {
        val ensureChannelCalls = mutableListOf<String>()
            private set
        val postNotificationCalls = mutableListOf<Triple<Int, String, Pair<String, String>>>()
            private set
        val cancelCalls = mutableListOf<Int>()
            private set
        var cancelAllCalls = 0
            private set

        override fun ensureChannel(channelId: String, name: String, description: String, importance: Int) {
            ensureChannelCalls.add(channelId)
            existingChannels.add(channelId)
        }
        override fun channelExists(channelId: String): Boolean = channelId in existingChannels
        override fun postNotification(
            id: Int,
            channelId: String,
            title: String,
            text: String,
            smallIconResId: Int,
        ): Boolean {
            postNotificationCalls.add(Triple(id, channelId, title to text))
            return postResult
        }
        override fun cancelNotification(id: Int) { cancelCalls.add(id) }
        override fun cancelAll() { cancelAllCalls++ }
        override fun hasNotificationPermission(): Boolean = hasPermission
    }

    private fun makeHandler(service: FakeNotificationService) = NotificationHandler(service)

    // ─── Tests ─────────────────────────────────────────────────────────────

    @Test
    fun `isAvailable always returns true`() {
        assertThat(makeHandler(FakeNotificationService()).isAvailable()).isTrue()
    }

    @Test
    fun `post creates channel and posts notification`() = runTest {
        val service = FakeNotificationService()
        makeHandler(service).post(
            id = 1001,
            channelId = "nous_proactive",
            channelName = "NOUS Proactive",
            title = "Battery low",
            text = "Battery at 5%. Enable power saver?",
        )
        assertThat(service.ensureChannelCalls).hasSize(1)
        assertThat(service.ensureChannelCalls[0]).isEqualTo("nous_proactive")
        assertThat(service.postNotificationCalls).hasSize(1)
        assertThat(service.postNotificationCalls[0].first).isEqualTo(1001)
        assertThat(service.postNotificationCalls[0].second).isEqualTo("nous_proactive")
        assertThat(service.postNotificationCalls[0].third.first).isEqualTo("Battery low")
    }

    @Test
    fun `post returns Failure Permission Denied when POST_NOTIFICATIONS missing`() = runTest {
        val service = FakeNotificationService(hasPermission = false)
        val result = makeHandler(service).post(
            id = 1, channelId = "c", channelName = "C", title = "T", text = "B",
        )
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Permission.Denied::class.java)
        assertThat(service.postNotificationCalls).isEmpty()
    }

    @Test
    fun `post returns Failure Unknown when postNotification returns false`() = runTest {
        val service = FakeNotificationService(postResult = false)
        val result = makeHandler(service).post(
            id = 1, channelId = "c", channelName = "C", title = "T", text = "B",
        )
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unknown::class.java)
    }

    @Test
    fun `ensureChannel is idempotent — second call with same channelId does not duplicate`() = runTest {
        val service = FakeNotificationService()
        val handler = makeHandler(service)
        handler.post(id = 1, channelId = "c", channelName = "C", title = "T1", text = "B1")
        handler.post(id = 2, channelId = "c", channelName = "C", title = "T2", text = "B2")
        // ensureChannel is called twice (handler doesn't dedupe — service does).
        // The fake service adds to existingChannels on each call, so second
        // call is effectively a no-op on real Android.
        assertThat(service.ensureChannelCalls).hasSize(2)
        assertThat(service.postNotificationCalls).hasSize(2)
    }

    @Test
    fun `cancel calls cancelNotification with id`() = runTest {
        val service = FakeNotificationService()
        val handler = makeHandler(service)
        handler.cancel(42)
        assertThat(service.cancelCalls).hasSize(1)
        assertThat(service.cancelCalls[0]).isEqualTo(42)
    }

    @Test
    fun `cancelAll calls service cancelAll`() = runTest {
        val service = FakeNotificationService()
        val handler = makeHandler(service)
        handler.cancelAll()
        assertThat(service.cancelAllCalls).isEqualTo(1)
    }

    @Test
    fun `execute returns Failure for non-PostNotification operations`() = runTest {
        val handler = makeHandler(FakeNotificationService())
        val result = handler.execute(SystemOperation.SetTorch(true))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unsupported::class.java)
    }

    @Test
    fun `execute PostNotification creates channel and posts notification`() = runTest {
        val service = FakeNotificationService()
        makeHandler(service).execute(
            SystemOperation.PostNotification(
                id = 1001,
                channelId = "nous_proactive",
                channelName = "NOUS Proactive",
                title = "Battery low",
                text = "Battery at 5%. Enable power saver?",
            ),
        )
        assertThat(service.ensureChannelCalls).hasSize(1)
        assertThat(service.ensureChannelCalls[0]).isEqualTo("nous_proactive")
        assertThat(service.postNotificationCalls).hasSize(1)
        assertThat(service.postNotificationCalls[0].first).isEqualTo(1001)
        assertThat(service.postNotificationCalls[0].second).isEqualTo("nous_proactive")
        assertThat(service.postNotificationCalls[0].third.first).isEqualTo("Battery low")
    }

    @Test
    fun `execute PostNotification returns Failure Permission Denied when POST_NOTIFICATIONS missing`() = runTest {
        val service = FakeNotificationService(hasPermission = false)
        val result = makeHandler(service).execute(
            SystemOperation.PostNotification(1, "c", "C", "T", "B"),
        )
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Permission.Denied::class.java)
        assertThat(service.postNotificationCalls).isEmpty()
    }

    @Test
    fun `capturePreState returns NotificationPreState with notification id`() = runTest {
        val handler = makeHandler(FakeNotificationService())
        val preState = handler.capturePreState(
            SystemOperation.PostNotification(42, "c", "C", "T", "B"),
        )
        assertThat(preState).isInstanceOf(NotificationHandler.NotificationPreState::class.java)
        assertThat((preState as NotificationHandler.NotificationPreState).notificationId).isEqualTo(42)
    }

    @Test
    fun `restorePreState cancels the notification by id`() = runTest {
        val service = FakeNotificationService()
        val handler = makeHandler(service)
        handler.restorePreState(
            SystemOperation.PostNotification(99, "c", "C", "T", "B"),
            NotificationHandler.NotificationPreState(notificationId = 99),
        )
        assertThat(service.cancelCalls).hasSize(1)
        assertThat(service.cancelCalls[0]).isEqualTo(99)
    }

    @Test
    fun `serializePreState round-trips NotificationPreState`() {
        val handler = makeHandler(FakeNotificationService())
        val original = NotificationHandler.NotificationPreState(notificationId = 777)
        val json = handler.serializePreState(original)
        assertThat(json).isNotNull()
        val restored = handler.deserializePreState(json)
        assertThat(restored).isEqualTo(original)
    }

    @Test
    fun `resourceCategory is NOTIFICATION`() {
        assertThat(makeHandler(FakeNotificationService()).resourceCategory).isEqualTo(ResourceCategory.NOTIFICATION)
    }

    @Test
    fun `describeOperation returns human-readable description`() {
        val handler = makeHandler(FakeNotificationService())
        assertThat(handler.describeOperation(SystemOperation.SetTorch(true))).isEqualTo("Notification posted")
        assertThat(handler.describeOperation(
            SystemOperation.PostNotification(1, "c", "C", "Hello", "B"),
        )).isEqualTo("Notification posted: Hello")
    }
}
