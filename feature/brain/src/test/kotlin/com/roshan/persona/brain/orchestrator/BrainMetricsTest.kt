// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.brain.orchestrator

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.context.SessionId
import com.roshan.persona.brain.skill.SkillOutput
import com.roshan.persona.brain.trace.BrainTrace
import com.roshan.persona.brain.trace.Layer
import com.roshan.persona.brain.trace.LayerDecision
import com.roshan.persona.brain.trace.LayerTrace
import com.roshan.persona.common.AppError
import com.roshan.persona.common.CorrelationId
import org.junit.Test
import java.time.Instant

// AUTO_FIX_0072: [feature] BrainMetricsTest verified

class BrainMetricsTest {

    private val metrics = BrainMetrics()

    private fun makeTrace(
        success: Boolean = true,
        costUsd: Float = 0f,
        tokens: Int = 0,
        layers: List<LayerTrace> = listOf(
            LayerTrace(Layer.INTENT, Instant.now(), Instant.now(), 5L, LayerDecision.PROCEED, null, null, null),
        ),
    ): BrainTrace {
        return BrainTrace(
            correlationId = CorrelationId.generate(),
            sessionId = SessionId.generate(),
            inputRedacted = "test",
            startedAt = Instant.now(),
            completedAt = Instant.now(),
            totalDurationMs = 100L,
            layers = layers,
            finalOutput = if (success) SkillOutput.Success("ok") else null,
            finalError = if (!success) AppError.Unknown() else null,
            totalCostUsd = costUsd,
            totalTokensGenerated = tokens,
        )
    }

    @Test
    fun `record increments total requests`() {
        metrics.record(makeTrace())
        metrics.record(makeTrace())

        val stats = metrics.getAggregateStats()
        assertThat(stats.totalRequests).isEqualTo(2)
    }

    @Test
    fun `record tracks successes and failures`() {
        metrics.record(makeTrace(success = true))
        metrics.record(makeTrace(success = false))

        val stats = metrics.getAggregateStats()
        assertThat(stats.totalSuccesses).isEqualTo(1)
        assertThat(stats.totalFailures).isEqualTo(1)
        assertThat(stats.successRate).isWithin(0.01f).of(0.5f)
    }

    @Test
    fun `record tracks cost`() {
        metrics.record(makeTrace(costUsd = 0.05f))
        metrics.record(makeTrace(costUsd = 0.10f))

        val stats = metrics.getAggregateStats()
        assertThat(stats.totalCostUsd).isWithin(0.001f).of(0.15f)
    }

    @Test
    fun `record tracks tokens`() {
        metrics.record(makeTrace(tokens = 100))
        metrics.record(makeTrace(tokens = 200))

        val stats = metrics.getAggregateStats()
        assertThat(stats.totalTokensGenerated).isEqualTo(300L)
    }

    @Test
    fun `record tracks per-layer invocations`() {
        metrics.record(makeTrace(layers = listOf(
            LayerTrace(Layer.INTENT, Instant.now(), Instant.now(), 5L, LayerDecision.PROCEED, null, null, null),
            LayerTrace(Layer.RULE, Instant.now(), Instant.now(), 10L, LayerDecision.PROCEED, null, null, null),
        )))

        val l1Stats = metrics.getLayerStats(Layer.RULE)
        assertThat(l1Stats).isNotNull()
        assertThat(l1Stats!!.invocations).isEqualTo(1)
        assertThat(l1Stats.successes).isEqualTo(1)
    }

    @Test
    fun `record tracks cloud fallback`() {
        // L3 deferred → L4 handled
        metrics.record(makeTrace(layers = listOf(
            LayerTrace(Layer.LOCAL_LLM, Instant.now(), Instant.now(), 100L, LayerDecision.DEFER, null, null, null),
            LayerTrace(Layer.CLOUD_LLM, Instant.now(), Instant.now(), 500L, LayerDecision.PROCEED, null, null, null),
        )))

        val stats = metrics.getAggregateStats()
        assertThat(stats.cloudFallbackCount).isEqualTo(1)
    }

    @Test
    fun `cloud fallback rate is computed correctly`() {
        // 2 L3 invocations, 1 deferral → 50% fallback rate
        metrics.record(makeTrace(layers = listOf(
            LayerTrace(Layer.LOCAL_LLM, Instant.now(), Instant.now(), 100L, LayerDecision.DEFER, null, null, null),
            LayerTrace(Layer.CLOUD_LLM, Instant.now(), Instant.now(), 500L, LayerDecision.PROCEED, null, null, null),
        )))
        metrics.record(makeTrace(layers = listOf(
            LayerTrace(Layer.LOCAL_LLM, Instant.now(), Instant.now(), 100L, LayerDecision.PROCEED, null, null, null),
        )))

        val stats = metrics.getAggregateStats()
        assertThat(stats.cloudFallbackRate).isWithin(0.01f).of(0.5f)
    }

    @Test
    fun `isCloudFallbackRateHigh triggers when above threshold`() {
        metrics.record(makeTrace(layers = listOf(
            LayerTrace(Layer.LOCAL_LLM, Instant.now(), Instant.now(), 100L, LayerDecision.DEFER, null, null, null),
        )))

        assertThat(metrics.isCloudFallbackRateHigh(0.3f)).isTrue()
    }

    @Test
    fun `recordBudgetExhaustion increments counter`() {
        metrics.recordBudgetExhaustion()
        metrics.recordBudgetExhaustion()

        val stats = metrics.getAggregateStats()
        assertThat(stats.budgetExhaustionCount).isEqualTo(2)
    }

    @Test
    fun `recordCancellation increments counter`() {
        metrics.recordCancellation()

        val stats = metrics.getAggregateStats()
        assertThat(stats.cancellationCount).isEqualTo(1)
    }

    @Test
    fun `reset clears all metrics`() {
        metrics.record(makeTrace())
        metrics.recordBudgetExhaustion()

        metrics.reset()

        val stats = metrics.getAggregateStats()
        assertThat(stats.totalRequests).isEqualTo(0)
        assertThat(stats.budgetExhaustionCount).isEqualTo(0)
    }

    @Test
    fun `getLayerStats returns null for layers with no invocations`() {
        val stats = metrics.getLayerStats(Layer.AGENTIC)
        assertThat(stats).isNull()
    }

    @Test
    fun `getAllLayerStats returns stats for all invoked layers`() {
        metrics.record(makeTrace(layers = listOf(
            LayerTrace(Layer.INTENT, Instant.now(), Instant.now(), 5L, LayerDecision.PROCEED, null, null, null),
            LayerTrace(Layer.RULE, Instant.now(), Instant.now(), 10L, LayerDecision.PROCEED, null, null, null),
        )))

        val allStats = metrics.getAllLayerStats()
        assertThat(allStats).hasSize(2)
    }

    @Test
    fun `latency percentiles are computed`() {
        // Record 10 traces with different latencies
        for (i in 1..10) {
            metrics.record(makeTrace(layers = listOf(
                LayerTrace(Layer.INTENT, Instant.now(), Instant.now(), i.toLong() * 10, LayerDecision.PROCEED, null, null, null),
            )))
        }

        val stats = metrics.getLayerStats(Layer.INTENT)!!
        assertThat(stats.p50LatencyMs).isAtLeast(10L)
        assertThat(stats.p95LatencyMs).isAtLeast(stats.p50LatencyMs)
    }
}
