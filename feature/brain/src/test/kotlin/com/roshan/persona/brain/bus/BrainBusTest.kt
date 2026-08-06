// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.brain.bus

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.intent.Intent
import com.roshan.persona.common.CorrelationId
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0080: [feature] BrainBusTest verified

class BrainBusTest {

    @Test
    fun `BrainBus emits events to subscribers`() = runTest {
        val bus = BrainBus()
        val correlationId = CorrelationId.generate()
        val event = BrainEvent.IntentClassified(
            intent = Intent.TorchOn,
            confidence = 0.95f,
            correlationId = correlationId,
        )

        bus.events.test {
            bus.emit(event)
            val received = awaitItem()
            assertThat(received).isEqualTo(event)
            assertThat(received.correlationId).isEqualTo(correlationId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `BrainBus tryEmit returns true when buffer has space`() = runTest {
        val bus = BrainBus()
        val event = BrainEvent.IntentClassified(
            intent = Intent.TorchOn,
            confidence = 0.95f,
            correlationId = CorrelationId.generate(),
        )

        val emitted = bus.tryEmit(event)
        assertThat(emitted).isTrue()
    }

    @Test
    fun `BrainBus replay is zero — new subscribers don't see old events`() = runTest {
        val bus = BrainBus()
        val event = BrainEvent.IntentClassified(
            intent = Intent.TorchOn,
            confidence = 0.95f,
            correlationId = CorrelationId.generate(),
        )

        // Emit before anyone subscribes
        bus.emit(event)

        // Now subscribe — should NOT see the old event
        bus.events.test {
            // No items expected (replay = 0)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `BrainEvent RuleFired shouldAudit is true`() {
        val event = BrainEvent.RuleFired(
            ruleId = "torch_on_rule",
            ruleTags = setOf("system", "torch"),
            correlationId = CorrelationId.generate(),
        )

        assertThat(event.shouldAudit).isTrue()
    }

    @Test
    fun `BrainEvent IntentClassified shouldAudit is false by default`() {
        val event = BrainEvent.IntentClassified(
            intent = Intent.TorchOn,
            confidence = 0.95f,
            correlationId = CorrelationId.generate(),
        )

        assertThat(event.shouldAudit).isFalse()
    }

    @Test
    fun `BrainEvent SkillInvoked shouldAudit is true`() {
        val event = BrainEvent.SkillInvoked(
            skillId = "call",
            intentLabel = "Call",
            correlationId = CorrelationId.generate(),
        )

        assertThat(event.shouldAudit).isTrue()
    }

    @Test
    fun `BrainEvent LlmCallStarted shouldAudit is true`() {
        val event = BrainEvent.LlmCallStarted(
            provider = "local",
            modelId = "qwen2.5-0.5b",
            correlationId = CorrelationId.generate(),
        )

        assertThat(event.shouldAudit).isTrue()
    }
}
