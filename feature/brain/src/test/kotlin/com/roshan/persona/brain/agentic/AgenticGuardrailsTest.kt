// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.brain.agentic

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.common.CorrelationId
import org.junit.Test
import java.time.Instant

// AUTO_FIX_0083: [feature] AgenticGuardrailsTest verified

class AgenticGuardrailsTest {

    // ─── Initialization tests ─────────────────────────────────────────────

    @Test
    fun `default guardrails have sensible values`() {
        val g = AgenticGuardrails()

        assertThat(g.maxSteps).isEqualTo(AgenticGuardrails.DEFAULT_MAX_STEPS)
        assertThat(g.maxCostUsd).isEqualTo(AgenticGuardrails.DEFAULT_MAX_COST_USD)
        assertThat(g.maxDurationMs).isEqualTo(AgenticGuardrails.DEFAULT_MAX_DURATION_MS)
        assertThat(g.maxRepeats).isEqualTo(AgenticGuardrails.DEFAULT_MAX_REPEATS)
    }

    @Test
    fun `STRICT guardrails have tighter limits`() {
        val g = AgenticGuardrails.STRICT

        assertThat(g.maxSteps).isLessThan(AgenticGuardrails.DEFAULT_MAX_STEPS)
        assertThat(g.maxCostUsd).isLessThan(AgenticGuardrails.DEFAULT_MAX_COST_USD)
        assertThat(g.maxDurationMs).isLessThan(AgenticGuardrails.DEFAULT_MAX_DURATION_MS)
    }

    @Test
    fun `GENEROUS guardrails have looser limits`() {
        val g = AgenticGuardrails.GENEROUS

        assertThat(g.maxSteps).isGreaterThan(AgenticGuardrails.DEFAULT_MAX_STEPS)
        assertThat(g.maxCostUsd).isGreaterThan(AgenticGuardrails.DEFAULT_MAX_COST_USD)
        assertThat(g.maxDurationMs).isGreaterThan(AgenticGuardrails.DEFAULT_MAX_DURATION_MS)
    }

    @Test
    fun `guardrails reject invalid maxSteps`() {
        try {
            AgenticGuardrails(maxSteps = 0)
            assert(false) { "Should have thrown" }
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("maxSteps")
        }

        try {
            AgenticGuardrails(maxSteps = 101)
            assert(false) { "Should have thrown" }
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("maxSteps")
        }
    }

    @Test
    fun `guardrails reject negative cost`() {
        try {
            AgenticGuardrails(maxCostUsd = -0.1f)
            assert(false) { "Should have thrown" }
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("maxCostUsd")
        }
    }

    @Test
    fun `guardrails reject invalid duration`() {
        try {
            AgenticGuardrails(maxDurationMs = 500L)  // < 1 second
            assert(false) { "Should have thrown" }
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("maxDurationMs")
        }
    }

    @Test
    fun `guardrails reject invalid maxRepeats`() {
        try {
            AgenticGuardrails(maxRepeats = 0)
            assert(false) { "Should have thrown" }
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("maxRepeats")
        }
    }

    // ─── Step check tests ─────────────────────────────────────────────────

    @Test
    fun `check returns Proceed when all under limits`() {
        val g = AgenticGuardrails()

        val decision = g.check(currentStepCount = 5, currentCostUsd = 0.10f, elapsedMs = 10_000L)

        assertThat(decision).isInstanceOf(GuardrailDecision.Proceed::class.java)
    }

    @Test
    fun `check returns Stop MAX_STEPS when step count exceeded`() {
        val g = AgenticGuardrails(maxSteps = 10)

        val decision = g.check(currentStepCount = 10, currentCostUsd = 0f, elapsedMs = 0L)

        assertThat(decision).isInstanceOf(GuardrailDecision.Stop::class.java)
        val stop = decision as GuardrailDecision.Stop
        assertThat(stop.status).isEqualTo(WorkflowStatus.MAX_STEPS_EXCEEDED)
        assertThat(stop.reason).contains("Max steps")
    }

    @Test
    fun `check returns Stop BUDGET when cost exceeded`() {
        val g = AgenticGuardrails(maxCostUsd = 0.50f)

        val decision = g.check(currentStepCount = 0, currentCostUsd = 0.50f, elapsedMs = 0L)

        assertThat(decision).isInstanceOf(GuardrailDecision.Stop::class.java)
        val stop = decision as GuardrailDecision.Stop
        assertThat(stop.status).isEqualTo(WorkflowStatus.BUDGET_EXCEEDED)
        assertThat(stop.reason).contains("Budget")
    }

    @Test
    fun `check returns Stop TIMEOUT when time exceeded`() {
        val g = AgenticGuardrails(maxDurationMs = 60_000L)

        val decision = g.check(currentStepCount = 0, currentCostUsd = 0f, elapsedMs = 60_000L)

        assertThat(decision).isInstanceOf(GuardrailDecision.Stop::class.java)
        val stop = decision as GuardrailDecision.Stop
        assertThat(stop.status).isEqualTo(WorkflowStatus.TIMEOUT)
        assertThat(stop.reason).contains("Timeout")
    }

    @Test
    fun `check returns Proceed at exactly one below max steps`() {
        val g = AgenticGuardrails(maxSteps = 10)

        val decision = g.check(currentStepCount = 9, currentCostUsd = 0f, elapsedMs = 0L)

        assertThat(decision).isInstanceOf(GuardrailDecision.Proceed::class.java)
    }

    // ─── Loop detection tests ─────────────────────────────────────────────

    @Test
    fun `checkLoop returns Proceed when no previous calls`() {
        val g = AgenticGuardrails(maxRepeats = 2)

        val decision = g.checkLoop(
            toolId = "search_web",
            params = mapOf("query" to "AI news"),
            previousSteps = emptyList(),
        )

        assertThat(decision).isInstanceOf(GuardrailDecision.Proceed::class.java)
    }

    @Test
    fun `checkLoop returns Proceed when same call under max repeats`() {
        val g = AgenticGuardrails(maxRepeats = 2)
        val previousSteps = listOf(
            makeToolCallStep(1, "search_web", mapOf("query" to "AI news")),
        )

        val decision = g.checkLoop("search_web", mapOf("query" to "AI news"), previousSteps)

        assertThat(decision).isInstanceOf(GuardrailDecision.Proceed::class.java)
    }

    @Test
    fun `checkLoop returns Stop when same call exceeds max repeats`() {
        val g = AgenticGuardrails(maxRepeats = 2)
        val previousSteps = listOf(
            makeToolCallStep(1, "search_web", mapOf("query" to "AI news")),
            makeToolCallStep(2, "search_web", mapOf("query" to "AI news")),
        )

        val decision = g.checkLoop("search_web", mapOf("query" to "AI news"), previousSteps)

        assertThat(decision).isInstanceOf(GuardrailDecision.Stop::class.java)
        val stop = decision as GuardrailDecision.Stop
        assertThat(stop.status).isEqualTo(WorkflowStatus.FAILED)
        assertThat(stop.reason).contains("Loop")
    }

    @Test
    fun `checkLoop returns Proceed when same tool but different params`() {
        val g = AgenticGuardrails(maxRepeats = 2)
        val previousSteps = listOf(
            makeToolCallStep(1, "search_web", mapOf("query" to "AI news")),
            makeToolCallStep(2, "search_web", mapOf("query" to "AI news")),
        )

        // Different query — should be allowed
        val decision = g.checkLoop("search_web", mapOf("query" to "ML news"), previousSteps)

        assertThat(decision).isInstanceOf(GuardrailDecision.Proceed::class.java)
    }

    @Test
    fun `checkLoop returns Proceed when different tool`() {
        val g = AgenticGuardrails(maxRepeats = 2)
        val previousSteps = listOf(
            makeToolCallStep(1, "search_web", mapOf("query" to "AI news")),
            makeToolCallStep(2, "search_web", mapOf("query" to "AI news")),
        )

        val decision = g.checkLoop("fetch_url", mapOf("url" to "https://example.com"), previousSteps)

        assertThat(decision).isInstanceOf(GuardrailDecision.Proceed::class.java)
    }

    @Test
    fun `checkLoop ignores LlmQuery steps`() {
        val g = AgenticGuardrails(maxRepeats = 2)
        val previousSteps = listOf(
            makeLlmQueryStep(1, "What is AI?"),
            makeLlmQueryStep(2, "What is AI?"),
        )

        // LlmQuery steps shouldn't count toward tool loop detection
        val decision = g.checkLoop("search_web", mapOf("query" to "AI"), previousSteps)

        assertThat(decision).isInstanceOf(GuardrailDecision.Proceed::class.java)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private fun makeToolCallStep(
        stepNumber: Int,
        toolId: String,
        params: Map<String, String>,
    ): WorkflowStep = WorkflowStep(
        stepNumber = stepNumber,
        thought = "Calling $toolId",
        action = AgenticStep.ToolCall(toolId = toolId, params = params),
        observation = AgenticStepResult.ToolSuccess(
            toolId = toolId,
            output = "result",
            correlationId = CorrelationId.generate(),
            durationMs = 100L,
        ),
        durationMs = 100L,
        costUsd = 0f,
        startedAt = Instant.now(),
    )

    private fun makeLlmQueryStep(
        stepNumber: Int,
        prompt: String,
    ): WorkflowStep = WorkflowStep(
        stepNumber = stepNumber,
        thought = "Asking LLM",
        action = AgenticStep.LlmQuery(prompt = prompt),
        observation = AgenticStepResult.LlmSuccess(
            text = "answer",
            tokensUsed = 10,
            correlationId = CorrelationId.generate(),
            durationMs = 500L,
            costUsd = 0.001f,
        ),
        durationMs = 500L,
        costUsd = 0.001f,
        startedAt = Instant.now(),
    )
}
