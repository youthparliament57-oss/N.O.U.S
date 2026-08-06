// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.cognitive.di

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.cognitive.decision.CriteriaAnalyzer
import com.roshan.persona.cognitive.decision.DecisionMaker
import com.roshan.persona.cognitive.decision.PreferenceAligner
import com.roshan.persona.cognitive.decision.RiskAssessor
import com.roshan.persona.cognitive.engine.ClarificationEngine
import com.roshan.persona.cognitive.engine.ConfidenceModulator
import com.roshan.persona.cognitive.engine.UncertaintyAwareness
import com.roshan.persona.cognitive.execution.PlanExecutorBridge
import com.roshan.persona.cognitive.execution.ReasoningCache
import com.roshan.persona.cognitive.execution.ReasoningTraceStore
import com.roshan.persona.cognitive.execution.ReplanningEngine
import com.roshan.persona.cognitive.model.CognitiveConfig
import com.roshan.persona.cognitive.model.ExecutionStatus
import com.roshan.persona.cognitive.model.SubTask
import com.roshan.persona.cognitive.model.SubTaskType
import com.roshan.persona.cognitive.model.TaskPlan
import com.roshan.persona.cognitive.planner.FuzzyConstraintInterpreter
import com.roshan.persona.cognitive.planner.TaskDecomposer
import com.roshan.persona.cognitive.planner.TaskPlanner
import com.roshan.persona.cognitive.planner.DecomposerLlm
import com.roshan.persona.cognitive.reasoning.ChainOfThought
import com.roshan.persona.cognitive.reasoning.ReasoningEngine
import com.roshan.persona.cognitive.reasoning.ReasoningLlm
import com.roshan.persona.cognitive.reasoning.SelfCorrector
import com.roshan.persona.cognitive.solver.ConstraintSatisfier
import com.roshan.persona.cognitive.solver.MemoryRecallInterface
import com.roshan.persona.cognitive.solver.PatternMatcher
import com.roshan.persona.cognitive.solver.ProblemSolver
import com.roshan.persona.cognitive.solver.SolverLlm
import com.roshan.persona.cognitive.solver.RecalledMemory
import com.roshan.persona.cognitive.execution.AgenticExecutorInterface
import com.roshan.persona.persona.core.BuiltInPersonas
import com.roshan.persona.persona.masking.DynamicFillerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0108: [feature] CognitiveFacadeTest verified

class CognitiveFacadeTest {

    // ─── Fakes ─────────────────────────────────────────────────────────────

    private class FakeReasoningLlm(private val response: String) : ReasoningLlm {
        override suspend fun reason(prompt: String, temperature: Float): String = response
    }

    private class FakeDecomposerLlm(private val response: String) : DecomposerLlm {
        override suspend fun decompose(goal: String, minTasks: Int, maxTasks: Int): String = response
    }

    private class FakeSolverLlm(private val response: String) : SolverLlm {
        override suspend fun solve(prompt: String): String = response
    }

    private class FakeMemory(private val memories: List<RecalledMemory>) : MemoryRecallInterface {
        override suspend fun recall(query: String, k: Int): List<RecalledMemory> = memories.take(k)
    }

    private class FakeAgenticExecutor : AgenticExecutorInterface {
        val executed = mutableListOf<String>()
        override suspend fun executeSubTask(subTask: SubTask): ExecutionStatus {
            executed.add(subTask.id)
            return ExecutionStatus.SUCCEEDED
        }
    }

    private val reasoningResponse = """
        THOUGHT: The user wants a trip plan.
        ACTION: Considering options.
        OBSERVATION: Take the train to Goa — fits budget.
        CONFIDENCE: 0.85
    """.trimIndent()

    private val decomposerResponse = """
        task_a | Research options | RESEARCH | search_web
        task_b | Plan budget | DECISION | recall_memory
    """.trimIndent()

    // ─── Facade builder ────────────────────────────────────────────────────

    private fun makeFacade(
        reasoningLlm: ReasoningLlm = FakeReasoningLlm(reasoningResponse),
        decomposerLlm: DecomposerLlm = FakeDecomposerLlm(decomposerResponse),
        solverLlm: SolverLlm = FakeSolverLlm(""),
        memory: MemoryRecallInterface = FakeMemory(emptyList()),
        agenticExecutor: AgenticExecutorInterface = FakeAgenticExecutor(),
        config: CognitiveConfig = CognitiveConfig(),
    ): CognitiveFacade {
        val clarificationEngine = ClarificationEngine(config, Dispatchers.Unconfined)
        val uncertaintyAwareness = UncertaintyAwareness(config)
        val confidenceModulator = ConfidenceModulator(config)
        val taskDecomposer = TaskDecomposer(decomposerLlm, config, Dispatchers.Unconfined)
        val taskPlanner = TaskPlanner(taskDecomposer, config, Dispatchers.Unconfined)
        val fuzzyInterpreter = FuzzyConstraintInterpreter(Dispatchers.Unconfined)
        val chainOfThought = ChainOfThought(reasoningLlm, config, Dispatchers.Unconfined)
        val selfCorrector = SelfCorrector(reasoningLlm, config, Dispatchers.Unconfined)
        val reasoningEngine = ReasoningEngine(chainOfThought, selfCorrector, config, Dispatchers.Unconfined)
        val criteriaAnalyzer = CriteriaAnalyzer(Dispatchers.Unconfined)
        val riskAssessor = RiskAssessor(Dispatchers.Unconfined)
        val preferenceAligner = PreferenceAligner(Dispatchers.Unconfined)
        val decisionMaker = DecisionMaker(criteriaAnalyzer, riskAssessor, preferenceAligner, confidenceModulator, config, Dispatchers.Unconfined)
        val patternMatcher = PatternMatcher(memory, solverLlm, Dispatchers.Unconfined)
        val constraintSatisfier = ConstraintSatisfier(Dispatchers.Unconfined)
        val problemSolver = ProblemSolver(patternMatcher, constraintSatisfier, Dispatchers.Unconfined)
        val reasoningCache = ReasoningCache(config)
        val reasoningTraceStore = ReasoningTraceStore(config)
        val fillerEngine = DynamicFillerEngine()
        val replanningEngine = ReplanningEngine(taskPlanner, fillerEngine, config, Dispatchers.Unconfined)
        val planExecutorBridge = PlanExecutorBridge(agenticExecutor, replanningEngine, config, Dispatchers.Unconfined)

        return CognitiveFacade(
            clarificationEngine, taskPlanner, reasoningEngine, decisionMaker,
            problemSolver, uncertaintyAwareness, confidenceModulator,
            reasoningCache, reasoningTraceStore, planExecutorBridge, config,
        )
    }

    // ─── Tests ─────────────────────────────────────────────────────────────

    @Test
    fun `process returns Trace for clear query`() = runTest {
        val facade = makeFacade()
        val result = facade.process(
            query = "What is the capital of France?",
            persona = BuiltInPersonas.ATLAS,
            userId = "user1",
        )
        assertThat(result).isInstanceOf(CognitiveResult.Trace::class.java)
        val trace = (result as CognitiveResult.Trace).trace
        assertThat(trace.steps).isNotEmpty()
        assertThat(trace.finalAnswer).isNotEmpty()
    }

    @Test
    fun `process returns NeedsClarification for ambiguous query`() = runTest {
        val facade = makeFacade()
        val result = facade.process(
            query = "plan a trip",
            persona = BuiltInPersonas.ATLAS,
            userId = "user1",
        )
        assertThat(result).isInstanceOf(CognitiveResult.NeedsClarification::class.java)
        val clarification = result as CognitiveResult.NeedsClarification
        assertThat(clarification.questions).isNotEmpty()
    }

    @Test
    fun `process returns Trace from cache on second call`() = runTest {
        val facade = makeFacade()
        // First call — processes and caches.
        facade.process("What is 2+2?", BuiltInPersonas.ATLAS, "user1")
        // Second call — should return from cache.
        val result = facade.process("What is 2+2?", BuiltInPersonas.ATLAS, "user1")
        assertThat(result).isInstanceOf(CognitiveResult.Trace::class.java)
        assertThat((result as CognitiveResult.Trace).fromCache).isTrue()
    }

    @Test
    fun `process returns Uncertain when confidence is very low`() = runTest {
        // LLM returns empty → reasoning engine generates low-confidence trace.
        val facade = makeFacade(reasoningLlm = FakeReasoningLlm(""))
        val result = facade.process(
            query = "What is the meaning of life?",
            persona = BuiltInPersonas.ATLAS,
            userId = "user1",
        )
        // With empty LLM response → confidence ≤ 0.1 → UncertaintyAwareness → AdmitUnknown.
        assertThat(result).isInstanceOf(CognitiveResult.Uncertain::class.java)
    }

    @Test
    fun `process stores trace in TraceStore`() = runTest {
        val facade = makeFacade()
        facade.process("What is the capital of France?", BuiltInPersonas.ATLAS, "user1")
        val history = facade.getTraceHistory()
        assertThat(history).isNotEmpty()
        assertThat(history[0].userQuery).isEqualTo("What is the capital of France?")
    }

    @Test
    fun `getCachedTrace returns null for uncached query`() {
        val facade = makeFacade()
        assertThat(facade.getCachedTrace("never asked", "user1")).isNull()
    }

    @Test
    fun `getStoredTrace returns null for unknown ID`() {
        val facade = makeFacade()
        assertThat(facade.getStoredTrace("nonexistent")).isNull()
    }

    @Test
    fun `searchTraces finds by query substring`() = runTest {
        val facade = makeFacade()
        facade.process("What is the capital of France?", BuiltInPersonas.ATLAS, "user1")
        val results = facade.searchTraces("capital")
        assertThat(results).isNotEmpty()
    }

    @Test
    fun `deleteStoredTrace returns false for unknown ID`() {
        val facade = makeFacade()
        assertThat(facade.deleteStoredTrace("nonexistent")).isFalse()
    }

    @Test
    fun `deleteStoredTrace returns true after storing`() = runTest {
        val facade = makeFacade()
        facade.process("query", BuiltInPersonas.ATLAS, "user1")
        val history = facade.getTraceHistory()
        assertThat(history).isNotEmpty()
        val traceId = history[0].traceId
        assertThat(facade.deleteStoredTrace(traceId)).isTrue()
        assertThat(facade.getStoredTrace(traceId)).isNull()
    }

    @Test
    fun `executePlan routes to PlanExecutorBridge`() = runTest {
        val executor = FakeAgenticExecutor()
        val facade = makeFacade(agenticExecutor = executor)
        val plan = TaskPlan(
            rootGoal = "test goal",
            subTasks = listOf(SubTask("a", "do A", SubTaskType.RESEARCH)),
        )
        val result = facade.executePlan(plan, BuiltInPersonas.ATLAS)
        assertThat(result.completedSubTasks).hasSize(1)
        assertThat(executor.executed).contains("a")
    }

    @Test
    fun `recordTraceFeedback stores feedback`() = runTest {
        val facade = makeFacade()
        facade.process("query", BuiltInPersonas.ATLAS, "user1")
        val history = facade.getTraceHistory()
        val traceId = history[0].traceId
        facade.recordTraceFeedback(traceId, com.roshan.persona.cognitive.model.UserFeedback.ACCEPTED)
        val stored = facade.getStoredTrace(traceId)
        assertThat(stored?.userFeedback).isEqualTo(com.roshan.persona.cognitive.model.UserFeedback.ACCEPTED)
    }
}
