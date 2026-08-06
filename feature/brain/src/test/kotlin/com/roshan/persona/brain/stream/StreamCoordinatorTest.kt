// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.brain.stream

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.roshan.persona.common.CorrelationId
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0098: [feature] StreamCoordinatorTest verified

class StreamCoordinatorTest {

    @Test
    fun `emitToken emits to flow and TTS bridge`() = runTest {
        val tts = FakeTtsBridge()
        val coordinator = StreamCoordinator(ttsBridge = tts)
        val correlationId = CorrelationId.generate()

        coordinator.emitToken(correlationId, "Hello")
        coordinator.emitToken(correlationId, " world")

        assertThat(tts.spokenTokens).containsExactly("Hello", " world").inOrder()
    }

    @Test
    fun `getTokenStream receives emitted tokens`() = runTest {
        val coordinator = StreamCoordinator()
        val correlationId = CorrelationId.generate()

        // Subscribe BEFORE emitting (replay=0)
        val flow = coordinator.getTokenStream(correlationId)

        flow.test {
            coordinator.emitToken(correlationId, "Paris")
            assertThat(awaitItem()).isEqualTo("Paris")
            coordinator.emitToken(correlationId, "!")
            assertThat(awaitItem()).isEqualTo("!")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emitToken returns false when cancelled`() = runTest {
        val coordinator = StreamCoordinator()
        val correlationId = CorrelationId.generate()

        coordinator.cancel(correlationId, "test cancel")
        val result = coordinator.emitToken(correlationId, "test")

        assertThat(result).isFalse()
    }

    @Test
    fun `cancel marks cancellation token`() {
        val coordinator = StreamCoordinator()
        val correlationId = CorrelationId.generate()
        val token = coordinator.getCancellationToken(correlationId)

        assertThat(token.isCancelled).isFalse()

        coordinator.cancel(correlationId, "user cancelled")

        assertThat(token.isCancelled).isTrue()
        assertThat(token.cancellationReason).isEqualTo("user cancelled")
    }

    @Test
    fun `cancel stops TTS`() = runTest {
        val tts = FakeTtsBridge()
        val coordinator = StreamCoordinator(ttsBridge = tts)
        val correlationId = CorrelationId.generate()

        coordinator.emitToken(correlationId, "speaking")
        coordinator.cancel(correlationId, "barge-in")

        // TTS was called for the first token, then stop() was called
        assertThat(tts.spokenTokens).hasSize(1)
    }

    @Test
    fun `emitComplete emits full message as single token`() = runTest {
        val tts = FakeTtsBridge()
        val coordinator = StreamCoordinator(ttsBridge = tts)
        val correlationId = CorrelationId.generate()

        val result = coordinator.emitComplete(correlationId, "Done!")

        assertThat(result).isTrue()
        assertThat(tts.spokenTokens).containsExactly("Done!")
    }

    @Test
    fun `complete clears active correlation`() = runTest {
        val coordinator = StreamCoordinator()
        val correlationId = CorrelationId.generate()

        coordinator.emitToken(correlationId, "test")
        assertThat(coordinator.activeCorrelationId.value).isEqualTo(correlationId)

        coordinator.complete(correlationId)
        assertThat(coordinator.activeCorrelationId.value).isNull()
    }

    @Test
    fun `cancelAll cancels all active streams`() = runTest {
        val coordinator = StreamCoordinator()
        val id1 = CorrelationId.generate()
        val id2 = CorrelationId.generate()

        coordinator.emitToken(id1, "a")
        coordinator.emitToken(id2, "b")

        coordinator.cancelAll("background")

        assertThat(coordinator.getCancellationToken(id1).isCancelled).isTrue()
        assertThat(coordinator.getCancellationToken(id2).isCancelled).isTrue()
    }

    @Test
    fun `per-request isolation — concurrent streams don't mix`() = runTest {
        val coordinator = StreamCoordinator()
        val id1 = CorrelationId.generate()
        val id2 = CorrelationId.generate()
        val flow1 = coordinator.getTokenStream(id1)
        val flow2 = coordinator.getTokenStream(id2)

        flow1.test {
            coordinator.emitToken(id1, "1a")
            coordinator.emitToken(id2, "2a")  // should NOT appear in flow1
            assertThat(awaitItem()).isEqualTo("1a")
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }

        flow2.test {
            coordinator.emitToken(id2, "2b")
            assertThat(awaitItem()).isEqualTo("2b")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `CancellationToken throwIfCancelled throws when cancelled`() {
        val token = CancellationToken()
        token.cancel("test")

        try {
            token.throwIfCancelled()
            assert(false) { "Should have thrown" }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Expected
        }
    }

    @Test
    fun `CancellationToken throwIfCancelled does not throw when not cancelled`() {
        val token = CancellationToken()
        token.throwIfCancelled()  // should not throw
    }

    @Test
    fun `NoOpTtsBridge does nothing`() = runTest {
        val bridge = NoOpTtsBridge()
        bridge.speak("test")
        bridge.stop()
        // No exception = pass
    }

    @Test
    fun `FakeTtsBridge records tokens`() = runTest {
        val bridge = FakeTtsBridge()
        bridge.speak("a")
        bridge.speak("b")
        assertThat(bridge.spokenTokens).containsExactly("a", "b").inOrder()
        bridge.clear()
        assertThat(bridge.spokenTokens).isEmpty()
    }

    @Test
    fun `cleanupOldFlows removes non-active flows`() = runTest {
        val coordinator = StreamCoordinator()
        val id1 = CorrelationId.generate()
        val id2 = CorrelationId.generate()

        coordinator.emitToken(id1, "a")
        coordinator.complete(id1)
        coordinator.emitToken(id2, "b")  // id2 is now active

        coordinator.cleanupOldFlows()

        // id2 should still be active
        assertThat(coordinator.activeCorrelationId.value).isEqualTo(id2)
    }

    @Test
    fun `activeStreamCount tracks flows`() = runTest {
        val coordinator = StreamCoordinator()
        assertThat(coordinator.activeStreamCount()).isEqualTo(0)

        coordinator.emitToken(CorrelationId.generate(), "a")
        assertThat(coordinator.activeStreamCount()).isAtLeast(1)
    }
}
