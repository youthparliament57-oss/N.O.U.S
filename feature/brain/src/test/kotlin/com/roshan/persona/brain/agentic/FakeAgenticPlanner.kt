// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.brain.agentic

import com.roshan.persona.brain.context.BrainContext
import com.roshan.persona.common.CorrelationId
import com.roshan.persona.common.Result

/**
 * Test-only fake [AgenticPlanner] — returns predetermined decisions in sequence.
 *
 * Removed from `src/main` (Phase 1 stub-removal): production code uses
 * [LlmAgenticPlanner] (bound via [com.roshan.persona.brain.di.BrainBindingsModule])
 * which delegates to the real LLM via [LlmCaller]. This fake lives in the
 * test source set so [AgenticOrchestratorTest] can drive the ReAct loop
 * without an actual LLM.
 *
 * Returns predetermined decisions in sequence. Useful for testing the
 * orchestrator without an LLM. If the planner runs out of decisions, it
 * returns a default FinalAnswer so the workflow terminates cleanly.
 */
class FakeAgenticPlanner(
    private val decisions: List<PlannerDecision>,
) : AgenticPlanner {

    private var callIndex = 0
    var callCount = 0
        private set

    override suspend fun planNext(
        userInput: String,
        toolCatalog: List<AgenticTool>,
        previousSteps: List<WorkflowStep>,
        context: BrainContext,
    ): Result<PlannerDecision> {
        callCount++
        val correlationId = CorrelationId.generate()

        if (callIndex >= decisions.size) {
            // Return a final answer if we run out of decisions
            return Result.Success(
                data = PlannerDecision(
                    thought = "No more decisions — returning default final answer",
                    action = AgenticStep.FinalAnswer("Workflow completed."),
                ),
                correlationId = correlationId,
                durationNanos = 0,
            )
        }

        val decision = decisions[callIndex]
        callIndex++
        return Result.Success(decision, correlationId, 0)
    }
}
