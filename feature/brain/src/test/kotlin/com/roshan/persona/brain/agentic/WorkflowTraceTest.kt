// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.brain.agentic

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.context.SessionId
import com.roshan.persona.common.CorrelationId
import org.junit.Test
import java.time.Instant

// AUTO_FIX_0081: [feature] WorkflowTraceTest verified

class WorkflowTraceTest {

    // ─── Builder tests ────────────────────────────────────────────────────

    @Test
    fun `WorkflowTraceBuilder starts with zero steps and cost`() {
        val builder = WorkflowTraceBuilder(
            workflowId = "wf-1",
            correlationId = CorrelationId.generate(),
            sessionId = SessionId.generate(),
            userInput = "test input",
        )

        assertThat(builder.stepCount()).isEqualTo(0)
        assertThat(builder.currentCost()).isEqualTo(0f)
        assertThat(builder.getSteps()).isEmpty()
    }

    @Test
    fun `recordStep adds step and accumulates cost`() {
        val builder = WorkflowTraceBuilder(
            workflowId = "wf-1",
            correlationId = CorrelationId.generate(),
            sessionId = SessionId.generate(),
            userInput = "test",
        )

        builder.recordStep(
            thought = "First thought",
            action = AgenticStep.ToolCall("search_web", mapOf("query" to "AI")),
            observation = AgenticStepResult.ToolSuccess(
                toolId = "search_web",
                output = "Found results",
                correlationId = CorrelationId.generate(),
                durationMs = 500L,
            ),
            durationMs = 500L,
            costUsd = 0.001f,
        )

        assertThat(builder.stepCount()).isEqualTo(1)
        assertThat(builder.currentCost()).isWithin(0.0001f).of(0.001f)

        builder.recordStep(
            thought = "Second thought",
            action = AgenticStep.LlmQuery("Summarize"),
            observation = AgenticStepResult.LlmSuccess(
                text = "Summary",
                tokensUsed = 50,
                correlationId = CorrelationId.generate(),
                durationMs = 800L,
                costUsd = 0.005f,
            ),
            durationMs = 800L,
            costUsd = 0.005f,
        )

        assertThat(builder.stepCount()).isEqualTo(2)
        assertThat(builder.currentCost()).isWithin(0.0001f).of(0.006f)
    }

    @Test
    fun `recordStep assigns sequential step numbers`() {
        val builder = makeBuilder()

        builder.recordStep(
            thought = null,
            action = AgenticStep.FinalAnswer("done"),
            observation = null,
            durationMs = 0L,
            costUsd = 0f,
        )
        builder.recordStep(
            thought = null,
            action = AgenticStep.FinalAnswer("done"),
            observation = null,
            durationMs = 0L,
            costUsd = 0f,
        )
        builder.recordStep(
            thought = null,
            action = AgenticStep.FinalAnswer("done"),
            observation = null,
            durationMs = 0L,
            costUsd = 0f,
        )

        val steps = builder.getSteps()
        assertThat(steps[0].stepNumber).isEqualTo(1)
        assertThat(steps[1].stepNumber).isEqualTo(2)
        assertThat(steps[2].stepNumber).isEqualTo(3)
    }

    // ─── Build tests ──────────────────────────────────────────────────────

    @Test
    fun `build produces immutable trace with all data`() {
        val builder = makeBuilder()
        builder.recordStep(
            thought = "thinking",
            action = AgenticStep.ToolCall("search_web", mapOf("query" to "AI")),
            observation = AgenticStepResult.ToolSuccess(
                toolId = "search_web",
                output = "results",
                correlationId = CorrelationId.generate(),
                durationMs = 100L,
            ),
            durationMs = 100L,
            costUsd = 0.01f,
        )

        val trace = builder.build(
            finalStatus = WorkflowStatus.COMPLETED,
            finalAnswer = "Done",
        )

        assertThat(trace.workflowId).isEqualTo("wf-1")
        assertThat(trace.userInput).isEqualTo("test input")
        assertThat(trace.steps).hasSize(1)
        assertThat(trace.finalStatus).isEqualTo(WorkflowStatus.COMPLETED)
        assertThat(trace.finalAnswer).isEqualTo("Done")
        assertThat(trace.failureReason).isNull()
        assertThat(trace.totalCostUsd).isWithin(0.0001f).of(0.01f)
        assertThat(trace.totalDurationMs).isAtLeast(0L)
    }

    @Test
    fun `build with FAILED status includes failure reason`() {
        val builder = makeBuilder()

        val trace = builder.build(
            finalStatus = WorkflowStatus.FAILED,
            finalAnswer = null,
            failureReason = "LLM failed",
        )

        assertThat(trace.finalStatus).isEqualTo(WorkflowStatus.FAILED)
        assertThat(trace.finalAnswer).isNull()
        assertThat(trace.failureReason).isEqualTo("LLM failed")
    }

    // ─── Trace computed properties ────────────────────────────────────────

    @Test
    fun `stepCount returns number of steps`() {
        val builder = makeBuilder()
        builder.recordStep(
            thought = null,
            action = AgenticStep.FinalAnswer("a"),
            observation = null,
            durationMs = 0L,
            costUsd = 0f,
        )
        builder.recordStep(
            thought = null,
            action = AgenticStep.FinalAnswer("b"),
            observation = null,
            durationMs = 0L,
            costUsd = 0f,
        )

        val trace = builder.build(WorkflowStatus.COMPLETED, "done")

        assertThat(trace.stepCount).isEqualTo(2)
    }

    @Test
    fun `totalTokensUsed sums LLM tokens`() {
        val builder = makeBuilder()
        builder.recordStep(
            thought = "call tool",
            action = AgenticStep.ToolCall("search", emptyMap()),
            observation = AgenticStepResult.ToolSuccess(
                toolId = "search",
                output = "results",
                correlationId = CorrelationId.generate(),
                durationMs = 100L,
            ),
            durationMs = 100L,
            costUsd = 0f,
        )
        builder.recordStep(
            thought = "ask llm",
            action = AgenticStep.LlmQuery("summarize"),
            observation = AgenticStepResult.LlmSuccess(
                text = "summary",
                tokensUsed = 100,
                correlationId = CorrelationId.generate(),
                durationMs = 500L,
                costUsd = 0.01f,
            ),
            durationMs = 500L,
            costUsd = 0.01f,
        )
        builder.recordStep(
            thought = "ask llm again",
            action = AgenticStep.LlmQuery("more"),
            observation = AgenticStepResult.LlmSuccess(
                text = "more",
                tokensUsed = 50,
                correlationId = CorrelationId.generate(),
                durationMs = 300L,
                costUsd = 0.005f,
            ),
            durationMs = 300L,
            costUsd = 0.005f,
        )

        val trace = builder.build(WorkflowStatus.COMPLETED, "done")

        assertThat(trace.totalTokensUsed).isEqualTo(150)
    }

    @Test
    fun `toolCallCount counts only tool calls`() {
        val builder = makeBuilder()
        builder.recordStep(
            thought = null,
            action = AgenticStep.ToolCall("search", emptyMap()),
            observation = null,
            durationMs = 0L,
            costUsd = 0f,
        )
        builder.recordStep(
            thought = null,
            action = AgenticStep.LlmQuery("why?"),
            observation = null,
            durationMs = 0L,
            costUsd = 0f,
        )
        builder.recordStep(
            thought = null,
            action = AgenticStep.ToolCall("fetch", emptyMap()),
            observation = null,
            durationMs = 0L,
            costUsd = 0f,
        )

        val trace = builder.build(WorkflowStatus.COMPLETED, "done")

        assertThat(trace.toolCallCount).isEqualTo(2)
        assertThat(trace.llmQueryCount).isEqualTo(1)
    }

    @Test
    fun `isCompleted returns true only for COMPLETED status`() {
        val builder1 = makeBuilder()
        val trace1 = builder1.build(WorkflowStatus.COMPLETED, "done")
        assertThat(trace1.isCompleted).isTrue()

        val builder2 = makeBuilder()
        val trace2 = builder2.build(WorkflowStatus.FAILED, null, "error")
        assertThat(trace2.isCompleted).isFalse()

        val builder3 = makeBuilder()
        val trace3 = builder3.build(WorkflowStatus.MAX_STEPS_EXCEEDED, null, "max steps")
        assertThat(trace3.isCompleted).isFalse()
    }

    @Test
    fun `totalCostUsd sums all step costs`() {
        val builder = makeBuilder()
        builder.recordStep(
            thought = null,
            action = AgenticStep.FinalAnswer("a"),
            observation = null,
            durationMs = 0L,
            costUsd = 0.01f,
        )
        builder.recordStep(
            thought = null,
            action = AgenticStep.FinalAnswer("b"),
            observation = null,
            durationMs = 0L,
            costUsd = 0.02f,
        )
        builder.recordStep(
            thought = null,
            action = AgenticStep.FinalAnswer("c"),
            observation = null,
            durationMs = 0L,
            costUsd = 0.005f,
        )

        val trace = builder.build(WorkflowStatus.COMPLETED, "done")

        assertThat(trace.totalCostUsd).isWithin(0.0001f).of(0.035f)
    }

    // ─── WorkflowStep tests ───────────────────────────────────────────────

    @Test
    fun `WorkflowStep stores all fields correctly`() {
        val startedAt = Instant.now()
        val step = WorkflowStep(
            stepNumber = 5,
            thought = "I should search",
            action = AgenticStep.ToolCall("search_web", mapOf("query" to "AI")),
            observation = AgenticStepResult.ToolSuccess(
                toolId = "search_web",
                output = "Found results",
                correlationId = CorrelationId.generate(),
                durationMs = 200L,
            ),
            durationMs = 250L,
            costUsd = 0.002f,
            startedAt = startedAt,
        )

        assertThat(step.stepNumber).isEqualTo(5)
        assertThat(step.thought).isEqualTo("I should search")
        assertThat(step.durationMs).isEqualTo(250L)
        assertThat(step.costUsd).isWithin(0.0001f).of(0.002f)
        assertThat(step.startedAt).isEqualTo(startedAt)
    }

    @Test
    fun `WorkflowStep allows null thought and observation`() {
        val step = WorkflowStep(
            stepNumber = 1,
            thought = null,
            action = AgenticStep.FinalAnswer("done"),
            observation = null,
            durationMs = 0L,
            costUsd = 0f,
            startedAt = Instant.now(),
        )

        assertThat(step.thought).isNull()
        assertThat(step.observation).isNull()
    }

    // ─── WorkflowStatus tests ─────────────────────────────────────────────

    @Test
    fun `WorkflowStatus has all expected values`() {
        val statuses = WorkflowStatus.entries

        assertThat(statuses).containsAtLeast(
            WorkflowStatus.COMPLETED,
            WorkflowStatus.FAILED,
            WorkflowStatus.MAX_STEPS_EXCEEDED,
            WorkflowStatus.BUDGET_EXCEEDED,
            WorkflowStatus.TIMEOUT,
            WorkflowStatus.CANCELLED,
        )
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private fun makeBuilder() = WorkflowTraceBuilder(
        workflowId = "wf-1",
        correlationId = CorrelationId.generate(),
        sessionId = SessionId.generate(),
        userInput = "test input",
    )
}
