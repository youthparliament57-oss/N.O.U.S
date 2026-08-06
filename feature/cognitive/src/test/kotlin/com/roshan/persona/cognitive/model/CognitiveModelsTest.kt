// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.cognitive.model

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.common.CorrelationId
import org.junit.Test

// AUTO_FIX_0111: [feature] CognitiveModelsTest verified

class CognitiveModelsTest {

    // ─── Helper builders ───────────────────────────────────────────────────

    private fun validStep(confidence: Float = 0.8f) = ReasoningStep(
        thought = "Flights cost ₹3-5K",
        action = "Checking train schedule",
        observation = "Train departs 6 AM",
        confidence = confidence,
    )

    private fun validTrace(confidence: Float = 0.85f) = ReasoningTrace(
        steps = listOf(validStep()),
        finalAnswer = "Take the train to Goa.",
        confidence = confidence,
    )

    private fun validSubTask(id: String = "task_1") = SubTask(
        id = id,
        description = "Research flights",
        type = SubTaskType.RESEARCH,
    )

    private fun validTaskPlan() = TaskPlan(
        rootGoal = "Plan a Goa trip",
        subTasks = listOf(validSubTask("a"), validSubTask("b")),
    )

    private fun validConstraint() = Constraint(
        type = ConstraintType.BUDGET,
        value = "15000",
        description = "under ₹15,000",
    )

    // ─── ReasoningTrace tests ───────────────────────────────────────────────

    @Test
    fun `ReasoningTrace validates confidence bounds`() {
        try {
            validTrace(confidence = 1.5f)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("confidence")
        }
    }

    @Test
    fun `ReasoningTrace rejects blank finalAnswer`() {
        try {
            validTrace().copy(finalAnswer = "")
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("finalAnswer")
        }
    }

    @Test
    fun `ReasoningTrace rejects empty steps`() {
        try {
            validTrace().copy(steps = emptyList())
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("steps")
        }
    }

    @Test
    fun `ReasoningTrace supports correction fields`() {
        val trace = validTrace().copy(
            correctionApplied = true,
            originalAnswer = "Take the flight.",
        )
        assertThat(trace.correctionApplied).isTrue()
        assertThat(trace.originalAnswer).isEqualTo("Take the flight.")
    }

    // ─── ReasoningStep tests ────────────────────────────────────────────────

    @Test
    fun `ReasoningStep validates confidence bounds`() {
        try {
            validStep(confidence = 2f)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("confidence")
        }
    }

    @Test
    fun `ReasoningStep rejects blank thought`() {
        try {
            validStep().copy(thought = "")
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("thought")
        }
    }

    // ─── VerificationResult tests ───────────────────────────────────────────

    @Test
    fun `VerificationResult Verified is a data object`() {
        val result: VerificationResult = VerificationResult.Verified
        assertThat(result).isEqualTo(VerificationResult.Verified)
    }

    @Test
    fun `VerificationResult NeedsCorrection carries issue and fix`() {
        val result = VerificationResult.NeedsCorrection(
            issue = "Budget constraint not met",
            suggestedFix = "Switch from flight to train",
        )
        assertThat(result.issue).contains("Budget")
        assertThat(result.suggestedFix).contains("train")
    }

    @Test
    fun `VerificationResult Failed carries reason`() {
        val result = VerificationResult.Failed("LLM could not verify")
        assertThat(result.reason).isNotEmpty()
    }

    // ─── TaskPlan tests ─────────────────────────────────────────────────────

    @Test
    fun `TaskPlan rejects blank rootGoal`() {
        try {
            validTaskPlan().copy(rootGoal = "")
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("rootGoal")
        }
    }

    @Test
    fun `TaskPlan rejects empty subTasks`() {
        try {
            validTaskPlan().copy(subTasks = emptyList())
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("subTasks")
        }
    }

    @Test
    fun `TaskPlan getSubTask finds by ID`() {
        val plan = validTaskPlan()
        assertThat(plan.getSubTask("a")?.description).isEqualTo("Research flights")
        assertThat(plan.getSubTask("nonexistent")).isNull()
    }

    @Test
    fun `TaskPlan subTaskIds returns all IDs`() {
        val plan = validTaskPlan()
        assertThat(plan.subTaskIds).containsExactly("a", "b")
    }

    // ─── SubTask tests ──────────────────────────────────────────────────────

    @Test
    fun `SubTask rejects blank id`() {
        try {
            validSubTask("").copy(id = "")
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("id")
        }
    }

    @Test
    fun `SubTask rejects negative estimatedTimeMs`() {
        try {
            validSubTask().copy(estimatedTimeMs = -1)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("estimatedTimeMs")
        }
    }

    @Test
    fun `SubTaskType has 5 distinct values`() {
        assertThat(SubTaskType.entries).hasSize(5)
        assertThat(SubTaskType.entries.map { it.name }).containsExactly(
            "RESEARCH", "DECISION", "ACTION", "VERIFICATION", "SYNTHESIS",
        )
    }

    // ─── ResourceEstimate tests ─────────────────────────────────────────────

    @Test
    fun `ResourceEstimate rejects negative cost`() {
        try {
            ResourceEstimate(estimatedCostUsd = -0.1f)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("estimatedCostUsd")
        }
    }

    // ─── ClarificationQuestion tests ────────────────────────────────────────

    @Test
    fun `ClarificationQuestion validates non-blank fields`() {
        try {
            ClarificationQuestion(question = "", reasoning = "because")
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("question")
        }
    }

    @Test
    fun `ClarificationQuestion allows null options (free-text)`() {
        val q = ClarificationQuestion(question = "Where?", reasoning = "Need destination")
        assertThat(q.options).isNull()
    }

    // ─── DecisionResult tests ───────────────────────────────────────────────

    @Test
    fun `DecisionResult rejects empty options`() {
        try {
            DecisionResult(
                options = emptyList(),
                recommendation = DecisionOption("x", emptyMap(), 0.5f),
                rationale = "because",
                criteria = emptyList(),
            )
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("options")
        }
    }

    @Test
    fun `DecisionResult requires recommendation in options`() {
        val optA = DecisionOption("A", mapOf("price" to 0.5f), 0.5f)
        val optB = DecisionOption("B", mapOf("price" to 0.8f), 0.8f)
        try {
            DecisionResult(
                options = listOf(optA),
                recommendation = optB,  // B is not in options
                rationale = "because",
                criteria = emptyList(),
            )
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("recommendation")
        }
    }

    @Test
    fun `DecisionOption validates score bounds`() {
        try {
            DecisionOption("X", mapOf("price" to 1.5f), 0.5f)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("scores")
        }
    }

    // ─── ProblemSolution tests ──────────────────────────────────────────────

    @Test
    fun `ProblemSolution rejects blank solution`() {
        try {
            ProblemSolution(solution = "", approach = SolutionApproach.PATTERN_MATCH)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("solution")
        }
    }

    @Test
    fun `SolutionApproach has 4 distinct values`() {
        assertThat(SolutionApproach.entries).hasSize(4)
    }

    // ─── Constraint tests ───────────────────────────────────────────────────

    @Test
    fun `Constraint rejects blank value`() {
        try {
            Constraint(type = ConstraintType.BUDGET, value = "")
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("value")
        }
    }

    @Test
    fun `ConstraintType has 7 distinct values`() {
        assertThat(ConstraintType.entries).hasSize(7)
    }

    @Test
    fun `RelaxedConstraint validates non-blank fields`() {
        try {
            RelaxedConstraint(
                originalConstraint = validConstraint(),
                relaxedValue = "",
                reason = "because",
            )
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("relaxedValue")
        }
    }

    // ─── ReplanResult tests ─────────────────────────────────────────────────

    @Test
    fun `ReplanResult Replanned carries new plan and message`() {
        val result = ReplanResult.Replanned(validTaskPlan(), "Switched to train")
        assertThat(result.newPlan.rootGoal).isEqualTo("Plan a Goa trip")
        assertThat(result.message).contains("train")
    }

    @Test
    fun `ReplanResult Failed carries reason`() {
        val result = ReplanResult.Failed("No alternatives available")
        assertThat(result.reason).isNotEmpty()
    }

    // ─── PlanExecutionResult tests ──────────────────────────────────────────

    @Test
    fun `PlanExecutionResult validates non-negative replansTriggered`() {
        try {
            PlanExecutionResult(
                status = PlanStatus.COMPLETED,
                completedSubTasks = listOf(validSubTask()),
                failedSubTasks = emptyList(),
                replansTriggered = -1,
            )
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("replansTriggered")
        }
    }

    @Test
    fun `PlanStatus has 4 distinct values`() {
        assertThat(PlanStatus.entries).hasSize(4)
    }

    @Test
    fun `ExecutionStatus has 5 distinct values`() {
        assertThat(ExecutionStatus.entries).hasSize(5)
    }

    // ─── StoredReasoningTrace tests ─────────────────────────────────────────

    @Test
    fun `StoredReasoningTrace validates non-blank fields`() {
        try {
            StoredReasoningTrace(
                traceId = "",
                timestamp = 0,
                userQuery = "query",
                trace = validTrace(),
            )
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("traceId")
        }
    }

    @Test
    fun `UserFeedback has 3 distinct values`() {
        assertThat(UserFeedback.entries).hasSize(3)
    }

    // ─── UncertaintyAction tests ────────────────────────────────────────────

    @Test
    fun `UncertaintyAction AskFollowUp carries question`() {
        val action = UncertaintyAction.AskFollowUp("Could you clarify the destination?")
        assertThat(action.question).contains("clarify")
    }

    @Test
    fun `UncertaintyAction AdmitUnknown carries message`() {
        val action = UncertaintyAction.AdmitUnknown("I don't know enough about this.")
        assertThat(action.message).contains("don't know")
    }
}

class CognitiveConfigTest {

    @Test
    fun `default config has valid values`() {
        val config = CognitiveConfig()
        assertThat(config.maxClarificationQuestions).isEqualTo(2)
        assertThat(config.maxCorrectionAttempts).isEqualTo(3)
        assertThat(config.confidenceAssertiveThreshold).isEqualTo(0.9f)
        assertThat(config.cacheTtlDays).isEqualTo(7)
        assertThat(config.traceStorageTtlDays).isEqualTo(30)
    }

    @Test
    fun `config rejects maxSubTasks < minSubTasks`() {
        try {
            CognitiveConfig(minSubTasks = 5, maxSubTasks = 3)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("maxSubTasks")
        }
    }

    @Test
    fun `config rejects confidenceCautious > confidenceAssertive`() {
        try {
            CognitiveConfig(
                confidenceAssertiveThreshold = 0.7f,
                confidenceCautiousThreshold = 0.9f,
            )
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("confidenceCautiousThreshold")
        }
    }

    @Test
    fun `config rejects confidenceDefer > confidenceCautious`() {
        try {
            CognitiveConfig(
                confidenceCautiousThreshold = 0.3f,
                confidenceDeferThreshold = 0.5f,
            )
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("confidenceDeferThreshold")
        }
    }

    @Test
    fun `config rejects zero cacheTtlDays`() {
        try {
            CognitiveConfig(cacheTtlDays = 0)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("cacheTtlDays")
        }
    }

    @Test
    fun `config rejects maxCorrectionAttempts > 5`() {
        try {
            CognitiveConfig(maxCorrectionAttempts = 6)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("maxCorrectionAttempts")
        }
    }

    @Test
    fun `config rejects negative planExecutionTimeoutMs`() {
        try {
            CognitiveConfig(planExecutionTimeoutMs = -1)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("planExecutionTimeoutMs")
        }
    }
}
