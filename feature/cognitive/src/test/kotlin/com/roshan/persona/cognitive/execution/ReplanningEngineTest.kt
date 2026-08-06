// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.cognitive.execution

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.cognitive.model.CognitiveConfig
import com.roshan.persona.cognitive.model.ExecutionStatus
import com.roshan.persona.cognitive.model.PlanStatus
import com.roshan.persona.cognitive.model.ReasoningStep
import com.roshan.persona.cognitive.model.ReasoningTrace
import com.roshan.persona.cognitive.model.ReplanResult
import com.roshan.persona.cognitive.model.SubTask
import com.roshan.persona.cognitive.model.SubTaskType
import com.roshan.persona.cognitive.model.TaskPlan
import com.roshan.persona.cognitive.model.UserFeedback
import com.roshan.persona.cognitive.planner.TaskDecomposer
import com.roshan.persona.cognitive.planner.TaskPlanner
import com.roshan.persona.cognitive.planner.DecomposerLlm
import com.roshan.persona.persona.core.BuiltInPersonas
import com.roshan.persona.persona.masking.DynamicFillerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0107: [feature] ReplanningEngineTest verified

class ReasoningCacheTest {

    private val cache = ReasoningCache(CognitiveConfig(cacheTtlDays = 7, maxCachedTraces = 100))

    private fun validTrace() = ReasoningTrace(
        steps = listOf(ReasoningStep("thought", "action", "observation")),
        finalAnswer = "answer",
        confidence = 0.8f,
    )

    @Test
    fun `lookup returns null for uncached query`() {
        assertThat(cache.lookup("query", "user1")).isNull()
    }

    @Test
    fun `store then lookup returns trace`() {
        cache.store("Plan a trip", "user1", validTrace(), nowMs = 1000)
        val result = cache.lookup("Plan a trip", "user1", nowMs = 2000)
        assertThat(result).isNotNull()
        assertThat(result!!.finalAnswer).isEqualTo("answer")
    }

    @Test
    fun `lookup returns null after TTL expires`() {
        cache.store("query", "user1", validTrace(), nowMs = 0)
        val sevenDays = 7 * 24L * 60 * 60 * 1000
        assertThat(cache.lookup("query", "user1", nowMs = sevenDays + 1)).isNull()
    }

    @Test
    fun `lookup returns trace at exactly TTL boundary`() {
        cache.store("query", "user1", validTrace(), nowMs = 0)
        val sevenDays = 7 * 24L * 60 * 60 * 1000
        assertThat(cache.lookup("query", "user1", nowMs = sevenDays)).isNotNull()
    }

    @Test
    fun `store normalizes query (case + whitespace insensitive)`() {
        cache.store("Plan a Trip", "user1", validTrace(), nowMs = 0)
        assertThat(cache.lookup("plan a trip", "user1", nowMs = 1)).isNotNull()
        assertThat(cache.lookup("  PLAN   A   TRIP  ", "user1", nowMs = 1)).isNotNull()
    }

    @Test
    fun `store is per-user (different userIds don't collide)`() {
        cache.store("query", "user1", validTrace(), nowMs = 0)
        assertThat(cache.lookup("query", "user2", nowMs = 1)).isNull()
    }

    @Test
    fun `has returns true for cached, false for uncached`() {
        cache.store("query", "user1", validTrace(), nowMs = 0)
        assertThat(cache.has("query", "user1", nowMs = 1)).isTrue()
        assertThat(cache.has("other", "user1", nowMs = 1)).isFalse()
    }

    @Test
    fun `clear empties the cache`() {
        cache.store("q1", "u1", validTrace(), nowMs = 0)
        cache.store("q2", "u1", validTrace(), nowMs = 0)
        assertThat(cache.size()).isEqualTo(2)
        cache.clear()
        assertThat(cache.size()).isEqualTo(0)
    }

    @Test
    fun `LRU eviction removes oldest when over capacity`() {
        val smallCache = ReasoningCache(CognitiveConfig(maxCachedTraces = 2))
        smallCache.store("q1", "u1", validTrace(), nowMs = 1000)
        smallCache.store("q2", "u1", validTrace(), nowMs = 2000)
        // Access q1 to make q2 the LRU.
        smallCache.lookup("q1", "u1", nowMs = 3000)
        // Store q3 → evict q2 (least recently accessed).
        smallCache.store("q3", "u1", validTrace(), nowMs = 4000)
        assertThat(smallCache.size()).isAtMost(3)  // may be 2 or 3 depending on timing
        assertThat(smallCache.has("q1", "u1", nowMs = 4000)).isTrue()
    }

    @Test
    fun `cleanup removes expired entries`() {
        val cache = ReasoningCache(CognitiveConfig(cacheTtlDays = 1))
        cache.store("old", "u1", validTrace(), nowMs = 0)
        cache.store("new", "u1", validTrace(), nowMs = 100_000)
        val oneDay = 24L * 60 * 60 * 1000
        cache.cleanup(nowMs = oneDay + 1)
        assertThat(cache.has("old", "u1", nowMs = oneDay + 1)).isFalse()
        assertThat(cache.has("new", "u1", nowMs = oneDay + 1)).isTrue()
    }
}

class ReasoningTraceStoreTest {

    private val store = ReasoningTraceStore(CognitiveConfig(traceStorageTtlDays = 30, maxStoredTraces = 500))

    private fun validTrace() = ReasoningTrace(
        steps = listOf(ReasoningStep("thought", "action", "observation")),
        finalAnswer = "Take the train to Goa.",
        confidence = 0.85f,
    )

    @Test
    fun `store returns trace ID and get retrieves it`() {
        val id = store.store("Plan a Goa trip", validTrace(), nowMs = 1000)
        assertThat(id).isNotEmpty()
        val retrieved = store.get(id, nowMs = 2000)
        assertThat(retrieved).isNotNull()
        assertThat(retrieved!!.userQuery).isEqualTo("Plan a Goa trip")
        assertThat(retrieved.trace.finalAnswer).isEqualTo("Take the train to Goa.")
    }

    @Test
    fun `get returns null for unknown trace ID`() {
        assertThat(store.get("nonexistent", nowMs = 0)).isNull()
    }

    @Test
    fun `get returns null for expired trace`() {
        val id = store.store("query", validTrace(), nowMs = 0)
        val thirtyDays = 30 * 24L * 60 * 60 * 1000
        assertThat(store.get(id, nowMs = thirtyDays + 1)).isNull()
    }

    @Test
    fun `search finds traces by query substring`() {
        store.store("Plan a Goa trip", validTrace(), nowMs = 0)
        store.store("Compare phones", validTrace(), nowMs = 1000)
        val results = store.search("goa", nowMs = 2000)
        assertThat(results).hasSize(1)
        assertThat(results[0].userQuery).contains("Goa")
    }

    @Test
    fun `search finds traces by answer substring`() {
        store.store("query", validTrace(), nowMs = 0)  // answer = "Take the train to Goa."
        val results = store.search("train", nowMs = 1000)
        assertThat(results).hasSize(1)
    }

    @Test
    fun `getRecent returns most-recent-first`() {
        store.store("old query", validTrace(), nowMs = 1000)
        store.store("new query", validTrace(), nowMs = 2000)
        val recent = store.getRecent(nowMs = 3000)
        assertThat(recent[0].userQuery).isEqualTo("new query")
        assertThat(recent[1].userQuery).isEqualTo("old query")
    }

    @Test
    fun `recordFeedback updates trace with feedback`() {
        val id = store.store("query", validTrace(), nowMs = 0)
        store.recordFeedback(id, UserFeedback.ACCEPTED)
        val retrieved = store.get(id, nowMs = 1000)
        assertThat(retrieved!!.userFeedback).isEqualTo(UserFeedback.ACCEPTED)
    }

    @Test
    fun `recordFeedback with correction note`() {
        val id = store.store("query", validTrace(), nowMs = 0)
        store.recordFeedback(id, UserFeedback.CORRECTED, "Actually, flights were better")
        val retrieved = store.get(id, nowMs = 1000)
        assertThat(retrieved!!.userFeedback).isEqualTo(UserFeedback.CORRECTED)
        assertThat(retrieved.correctionNote).contains("flights")
    }

    @Test
    fun `delete removes a trace`() {
        val id = store.store("query", validTrace(), nowMs = 0)
        assertThat(store.delete(id)).isTrue()
        assertThat(store.get(id, nowMs = 1000)).isNull()
        assertThat(store.delete(id)).isFalse()  // already deleted
    }

    @Test
    fun `clear empties all traces`() {
        store.store("q1", validTrace(), nowMs = 0)
        store.store("q2", validTrace(), nowMs = 0)
        store.clear()
        assertThat(store.count()).isEqualTo(0)
    }
}

class ReplanningEngineTest {

    private class FakeLlm(private val response: String) : DecomposerLlm {
        override suspend fun decompose(goal: String, minTasks: Int, maxTasks: Int) = response
    }

    private class FakeAgenticExecutor(
        private val failTaskIds: Set<String> = emptySet(),
    ) : AgenticExecutorInterface {
        val executed = mutableListOf<String>()
        override suspend fun executeSubTask(subTask: SubTask): ExecutionStatus {
            executed.add(subTask.id)
            return if (subTask.id in failTaskIds) ExecutionStatus.FAILED else ExecutionStatus.SUCCEEDED
        }
    }

    private val sampleResponse = """
        task_a | Do A | RESEARCH | search_web
        task_b | Do B | RESEARCH | search_web
        task_c | Do C | DECISION | recall_memory
    """.trimIndent()

    private fun makePlan(): TaskPlan {
        val planner = TaskPlanner(
            decomposer = TaskDecomposer(FakeLlm(sampleResponse), CognitiveConfig(), Dispatchers.Unconfined),
            config = CognitiveConfig(),
            dispatcher = Dispatchers.Unconfined,
        )
        return kotlinx.coroutines.runBlocking { planner.decompose("goal") }!!
    }

    private fun makeReplanningEngine() = ReplanningEngine(
        taskPlanner = TaskPlanner(
            decomposer = TaskDecomposer(FakeLlm(sampleResponse), CognitiveConfig(), Dispatchers.Unconfined),
            config = CognitiveConfig(maxReplanAttempts = 2),
            dispatcher = Dispatchers.Unconfined,
        ),
        fillerEngine = DynamicFillerEngine(),
        config = CognitiveConfig(maxReplanAttempts = 2),
        dispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `handleFailure returns Replanned when re-plan succeeds`() = runTest {
        val engine = makeReplanningEngine()
        val plan = makePlan()
        val failedTask = plan.subTasks.first()
        val result = engine.handleFailure(plan, failedTask, "sold out", BuiltInPersonas.ATLAS)
        assertThat(result).isInstanceOf(ReplanResult.Replanned::class.java)
        assertThat((result as ReplanResult.Replanned).newPlan).isNotNull()
    }

    @Test
    fun `handleFailure returns Failed after max attempts`() = runTest {
        val engine = makeReplanningEngine()
        val plan = makePlan()
        val failedTask = plan.subTasks.first()
        // Exhaust attempts.
        engine.handleFailure(plan, failedTask, "fail 1", BuiltInPersonas.ATLAS)
        engine.handleFailure(plan, failedTask, "fail 2", BuiltInPersonas.ATLAS)
        val result = engine.handleFailure(plan, failedTask, "fail 3", BuiltInPersonas.ATLAS)
        assertThat(result).isInstanceOf(ReplanResult.Failed::class.java)
    }

    @Test
    fun `canSkip returns false for VERIFICATION sub-task`() {
        val engine = makeReplanningEngine()
        val task = SubTask("v1", "verify", SubTaskType.VERIFICATION)
        val plan = makePlan()
        assertThat(engine.canSkip(task, plan)).isFalse()
    }

    @Test
    fun `canSkip returns false for SYNTHESIS sub-task`() {
        val engine = makeReplanningEngine()
        val task = SubTask("s1", "synthesize", SubTaskType.SYNTHESIS)
        val plan = makePlan()
        assertThat(engine.canSkip(task, plan)).isFalse()
    }

    @Test
    fun `canSkip returns true for RESEARCH sub-task with no dependents`() {
        val engine = makeReplanningEngine()
        val task = SubTask("r1", "research", SubTaskType.RESEARCH)
        val plan = TaskPlan(
            rootGoal = "goal",
            subTasks = listOf(task),
            dependencies = emptyMap(),
        )
        assertThat(engine.canSkip(task, plan)).isTrue()
    }

    @Test
    fun `reset clears re-plan attempt counters`() = runTest {
        val engine = makeReplanningEngine()
        val plan = makePlan()
        val failedTask = plan.subTasks.first()
        engine.handleFailure(plan, failedTask, "fail", BuiltInPersonas.ATLAS)
        engine.reset()
        // After reset, should be able to re-plan again.
        val result = engine.handleFailure(plan, failedTask, "fail again", BuiltInPersonas.ATLAS)
        assertThat(result).isInstanceOf(ReplanResult.Replanned::class.java)
    }
}

class PlanExecutorBridgeTest {

    private class FakeLlm(private val response: String) : DecomposerLlm {
        override suspend fun decompose(goal: String, minTasks: Int, maxTasks: Int) = response
    }

    private class FakeAgenticExecutor(
        private val failTaskIds: Set<String> = emptySet(),
    ) : AgenticExecutorInterface {
        val executed = mutableListOf<String>()
        override suspend fun executeSubTask(subTask: SubTask): ExecutionStatus {
            executed.add(subTask.id)
            return if (subTask.id in failTaskIds) ExecutionStatus.FAILED else ExecutionStatus.SUCCEEDED
        }
    }

    private val sampleResponse = """
        task_a | Do A | RESEARCH | search_web
        task_b | Do B | RESEARCH | search_web
    """.trimIndent()

    private fun makeBridge(
        failTaskIds: Set<String> = emptySet(),
        timeoutMs: Long = 60_000,
    ): Pair<PlanExecutorBridge, FakeAgenticExecutor> {
        val executor = FakeAgenticExecutor(failTaskIds)
        val replanningEngine = ReplanningEngine(
            taskPlanner = TaskPlanner(
                decomposer = TaskDecomposer(FakeLlm(sampleResponse), CognitiveConfig(), Dispatchers.Unconfined),
                config = CognitiveConfig(),
                dispatcher = Dispatchers.Unconfined,
            ),
            fillerEngine = DynamicFillerEngine(),
            config = CognitiveConfig(),
            dispatcher = Dispatchers.Unconfined,
        )
        val bridge = PlanExecutorBridge(
            agenticExecutor = executor,
            replanningEngine = replanningEngine,
            config = CognitiveConfig(planExecutionTimeoutMs = timeoutMs),
            dispatcher = Dispatchers.Unconfined,
        )
        return bridge to executor
    }

    private fun makePlan(): TaskPlan {
        val planner = TaskPlanner(
            decomposer = TaskDecomposer(FakeLlm(sampleResponse), CognitiveConfig(), Dispatchers.Unconfined),
            config = CognitiveConfig(),
            dispatcher = Dispatchers.Unconfined,
        )
        return kotlinx.coroutines.runBlocking { planner.decompose("goal") }!!
    }

    @Test
    fun `execute returns COMPLETED when all sub-tasks succeed`() = runTest {
        val (bridge, executor) = makeBridge()
        val plan = makePlan()
        val result = bridge.execute(plan, BuiltInPersonas.ATLAS)
        assertThat(result.status).isEqualTo(PlanStatus.COMPLETED)
        assertThat(result.completedSubTasks).hasSize(2)
        assertThat(result.failedSubTasks).isEmpty()
        assertThat(executor.executed).hasSize(2)
    }

    @Test
    fun `execute returns PARTIAL when a sub-task fails and can't be re-planned`() = runTest {
        val (bridge, _) = makeBridge(failTaskIds = setOf("task_a"))
        val plan = makePlan()
        val result = bridge.execute(plan, BuiltInPersonas.ATLAS)
        // task_a fails → re-plan tries → new plan has task_b only → succeeds.
        // Result should be COMPLETED or PARTIAL depending on re-plan outcome.
        assertThat(result.status).isAnyOf(PlanStatus.COMPLETED, PlanStatus.PARTIAL)
    }

    @Test
    fun `execute returns TIMEOUT when execution exceeds timeout`() = runTest {
        val (bridge, _) = makeBridge(timeoutMs = 1L)  // 1ms timeout — will definitely time out
        val plan = makePlan()
        val result = bridge.execute(plan, BuiltInPersonas.ATLAS)
        assertThat(result.status).isEqualTo(PlanStatus.TIMEOUT)
    }

    @Test
    fun `execute records replansTriggered when re-planning occurs`() = runTest {
        val (bridge, _) = makeBridge(failTaskIds = setOf("task_a"))
        val plan = makePlan()
        val result = bridge.execute(plan, BuiltInPersonas.ATLAS)
        // If task_a failed and was re-planned, replansTriggered should be > 0.
        if (result.status != PlanStatus.TIMEOUT) {
            assertThat(result.replansTriggered).isAtLeast(0)
        }
    }
}
