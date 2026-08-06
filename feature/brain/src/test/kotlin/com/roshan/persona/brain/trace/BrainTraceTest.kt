// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.brain.trace

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.context.SessionId
import com.roshan.persona.brain.skill.SkillOutput
import com.roshan.persona.common.AppError
import com.roshan.persona.common.CorrelationId
import org.junit.Test
import java.time.Instant

// AUTO_FIX_0096: [feature] BrainTraceTest verified

class BrainTraceTest {

    @Test
    fun `BrainTrace isSuccess when finalOutput is Success`() {
        val trace = buildTrace(
            finalOutput = SkillOutput.Success(message = "Done"),
            finalError = null,
        )

        assertThat(trace.isSuccess).isTrue()
        assertThat(trace.isPartial).isFalse()
    }

    @Test
    fun `BrainTrace isPartial when finalOutput is Partial`() {
        val trace = buildTrace(
            finalOutput = SkillOutput.Partial(
                message = "Done",
                warnings = listOf("Low confidence"),
            ),
            finalError = null,
        )

        assertThat(trace.isSuccess).isFalse()
        assertThat(trace.isPartial).isTrue()
    }

    @Test
    fun `BrainTrace not success when finalError present`() {
        val trace = buildTrace(
            finalOutput = null,
            finalError = AppError.Unknown(),
        )

        assertThat(trace.isSuccess).isFalse()
    }

    @Test
    fun `layerSummary returns layer chain with decisions`() {
        val trace = buildTrace(
            finalOutput = SkillOutput.Success(message = "Done"),
            finalError = null,
            layers = listOf(
                LayerTrace(
                    layer = Layer.INTENT,
                    enteredAt = Instant.now(),
                    exitedAt = Instant.now(),
                    durationMs = 5,
                    decision = LayerDecision.PROCEED,
                    skipReason = null,
                    output = null,
                    error = null,
                ),
                LayerTrace(
                    layer = Layer.RULE,
                    enteredAt = Instant.now(),
                    exitedAt = null,
                    durationMs = null,
                    decision = LayerDecision.SKIP,
                    skipReason = "L0 returned final",
                    output = null,
                    error = null,
                ),
            ),
        )

        val summary = trace.layerSummary()
        assertThat(summary).contains("L0 ✓")
        assertThat(summary).contains("L1 ⊘")
    }

    @Test
    fun `Layer fromNumber returns correct layer`() {
        assertThat(Layer.fromNumber(0)).isEqualTo(Layer.INTENT)
        assertThat(Layer.fromNumber(5)).isEqualTo(Layer.AGENTIC)
    }

    private fun buildTrace(
        finalOutput: SkillOutput?,
        finalError: AppError?,
        layers: List<LayerTrace> = emptyList(),
    ): BrainTrace = BrainTrace(
        correlationId = CorrelationId.generate(),
        sessionId = SessionId.generate(),
        inputRedacted = "[REDACTED]",
        startedAt = Instant.now(),
        completedAt = Instant.now(),
        totalDurationMs = 100L,
        layers = layers,
        finalOutput = finalOutput,
        finalError = finalError,
        totalCostUsd = 0f,
        totalTokensGenerated = 0,
    )
}
