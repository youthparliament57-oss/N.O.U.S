// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.cognitive.planner

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.cognitive.model.CognitiveConfig
import com.roshan.persona.cognitive.model.ConstraintType
import com.roshan.persona.cognitive.model.SubTaskType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0109: [feature] TaskPlannerTest verified

class DependencyGraphTest {

    @Test
    fun `topologicalSort returns empty list for empty input`() {
        val result = DependencyGraph.topologicalSort(emptyList(), emptyMap())
        assertThat(result).isEmpty()
    }

    @Test
    fun `topologicalSort returns single item for no dependencies`() {
        val result = DependencyGraph.topologicalSort(listOf("a"), emptyMap())
        assertThat(result).containsExactly("a")
    }

    @Test
    fun `topologicalSort orders dependencies first`() {
        val ids = listOf("budget", "flights", "hotels")
        val deps = mapOf("budget" to listOf("flights", "hotels"))
        val result = DependencyGraph.topologicalSort(ids, deps)
        assertThat(result).isNotNull()
        assertThat(result!!).contains("flights").before("budget")
        assertThat(result).contains("hotels").before("budget")
    }

    @Test
    fun `topologicalSort returns null for cycle`() {
        val ids = listOf("a", "b")
        val deps = mapOf("a" to listOf("b"), "b" to listOf("a"))
        val result = DependencyGraph.topologicalSort(ids, deps)
        assertThat(result).isNull()
    }

    @Test
    fun `topologicalSort handles chain dependency`() {
        val ids = listOf("a", "b", "c")
        val deps = mapOf("b" to listOf("a"), "c" to listOf("b"))
        val result = DependencyGraph.topologicalSort(ids, deps)
        assertThat(result).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun `parallelGroups returns single group for no dependencies`() {
        val ids = listOf("a", "b", "c")
        val result = DependencyGraph.parallelGroups(ids, emptyMap())
        assertThat(result).hasSize(1)
        assertThat(result!![0]).containsExactly("a", "b", "c")
    }

    @Test
    fun `parallelGroups separates dependent tasks into different groups`() {
        val ids = listOf("flights", "hotels", "weather", "budget", "itinerary")
        val deps = mapOf(
            "budget" to listOf("flights", "hotels"),
            "itinerary" to listOf("weather", "budget"),
        )
        val result = DependencyGraph.parallelGroups(ids, deps)
        assertThat(result).isNotNull()
        // Group 0: flights, hotels, weather (no deps).
        assertThat(result!![0]).containsExactly("flights", "hotels", "weather")
        // Group 1: budget (depends on flights + hotels).
        assertThat(result[1]).containsExactly("budget")
        // Group 2: itinerary (depends on weather + budget).
        assertThat(result[2]).containsExactly("itinerary")
    }

    @Test
    fun `parallelGroups returns null for cycle`() {
        val ids = listOf("a", "b")
        val deps = mapOf("a" to listOf("b"), "b" to listOf("a"))
        assertThat(DependencyGraph.parallelGroups(ids, deps)).isNull()
    }

    @Test
    fun `hasCycle returns true for circular dependency`() {
        val ids = listOf("a", "b")
        val deps = mapOf("a" to listOf("b"), "b" to listOf("a"))
        assertThat(DependencyGraph.hasCycle(ids, deps)).isTrue()
    }

    @Test
    fun `hasCycle returns false for valid DAG`() {
        val ids = listOf("a", "b", "c")
        val deps = mapOf("c" to listOf("b"), "b" to listOf("a"))
        assertThat(DependencyGraph.hasCycle(ids, deps)).isFalse()
    }
}

class TaskDecomposerTest {

    private class FakeLlm(private val response: String) : DecomposerLlm {
        override suspend fun decompose(goal: String, minTasks: Int, maxTasks: Int): String = response
    }

    private fun makeDecomposer(response: String) = TaskDecomposer(
        llm = FakeLlm(response),
        config = CognitiveConfig(minSubTasks = 3, maxSubTasks = 7),
        dispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `decompose returns parsed sub-tasks`() = runTest {
        val response = """
            research_flights | Research flights from BLR to GOI | RESEARCH | search_web
            find_hotels | Find hotels in Goa | RESEARCH | search_web
            plan_budget | Calculate total budget | DECISION | recall_memory
        """.trimIndent()
        val decomposer = makeDecomposer(response)
        val result = decomposer.decompose("Plan a Goa trip")
        assertThat(result).hasSize(3)
        assertThat(result[0].id).isEqualTo("research_flights")
        assertThat(result[0].type).isEqualTo(SubTaskType.RESEARCH)
        assertThat(result[1].requiredTools).contains("search_web")
        assertThat(result[2].type).isEqualTo(SubTaskType.DECISION)
    }

    @Test
    fun `decompose returns empty list for blank response`() = runTest {
        val decomposer = makeDecomposer("")
        assertThat(decomposer.decompose("goal")).isEmpty()
    }

    @Test
    fun `decompose skips invalid lines`() = runTest {
        val response = """
            valid_task | Do something | RESEARCH | search_web
            invalid_line_without_pipes
            # comment line
            another_valid | Do another | ACTION
        """.trimIndent()
        val decomposer = makeDecomposer(response)
        val result = decomposer.decompose("goal")
        assertThat(result).hasSize(2)
        assertThat(result[0].id).isEqualTo("valid_task")
        assertThat(result[1].id).isEqualTo("another_valid")
    }

    @Test
    fun `depose defaults type to RESEARCH when type is unknown`() = runTest {
        val response = "task1 | Do something | UNKNOWN_TYPE | search_web"
        val decomposer = makeDecomposer(response)
        val result = decomposer.decompose("goal")
        assertThat(result[0].type).isEqualTo(SubTaskType.RESEARCH)
    }

    @Test
    fun `decompose parses multiple comma-separated tools`() = runTest {
        val response = "task1 | Do something | RESEARCH | search_web, recall_memory, fetch_url"
        val decomposer = makeDecomposer(response)
        val result = decomposer.decompose("goal")
        assertThat(result[0].requiredTools).hasSize(3)
        assertThat(result[0].requiredTools).contains("fetch_url")
    }

    @Test
    fun `decompose handles no tools field`() = runTest {
        val response = "task1 | Do something | RESEARCH"
        val decomposer = makeDecomposer(response)
        val result = decomposer.decompose("goal")
        assertThat(result[0].requiredTools).isEmpty()
    }
}

class TaskPlannerTest {

    private class FakeLlm(private val response: String) : DecomposerLlm {
        override suspend fun decompose(goal: String, minTasks: Int, maxTasks: Int): String = response
    }

    private val sampleResponse = """
        research_flights | Research flights | RESEARCH | search_web
        find_hotels | Find hotels | RESEARCH | search_web
        plan_budget | Calculate budget | DECISION | recall_memory
    """.trimIndent()

    private fun makePlanner(response: String = sampleResponse, availableTools: Set<String> = emptySet()) = TaskPlanner(
        decomposer = TaskDecomposer(FakeLlm(response), CognitiveConfig(), Dispatchers.Unconfined),
        config = CognitiveConfig(),
        dispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `decompose returns TaskPlan with sub-tasks`() = runTest {
        val planner = makePlanner()
        val plan = planner.decompose("Plan a Goa trip")
        assertThat(plan).isNotNull()
        assertThat(plan!!.rootGoal).isEqualTo("Plan a Goa trip")
        assertThat(plan.subTasks).hasSize(3)
    }

    @Test
    fun `decompose returns null for empty decomposition`() = runTest {
        val planner = makePlanner(response = "")
        assertThat(planner.decompose("goal")).isNull()
    }

    @Test
    fun `decompose filters sub-tasks with unavailable tools`() = runTest {
        val planner = makePlanner(availableTools = setOf("search_web"))
        // recall_memory is not in availableTools → plan_budget should be removed.
        val plan = planner.decompose("goal", availableToolIds = setOf("search_web"))
        assertThat(plan).isNotNull()
        assertThat(plan!!.subTasks).hasSize(2)  // flights + hotels (search_web only)
        assertThat(plan.removedSubTasks).hasSize(1)  // plan_budget (needs recall_memory)
        assertThat(plan.removedSubTasks[0].id).isEqualTo("plan_budget")
    }

    @Test
    fun `decompose does not filter when availableToolIds is empty`() = runTest {
        val planner = makePlanner()
        val plan = planner.decompose("goal", availableToolIds = emptySet())
        assertThat(plan!!.subTasks).hasSize(3)  // all kept
        assertThat(plan.removedSubTasks).isEmpty()
    }

    @Test
    fun `decompose computes parallel groups`() = runTest {
        val planner = makePlanner()
        val plan = planner.decompose("goal")
        // v1: no dependencies → all in one parallel group.
        assertThat(plan!!.parallelGroups).hasSize(1)
        assertThat(plan.parallelGroups[0]).hasSize(3)
    }

    @Test
    fun `decompose estimates resources`() = runTest {
        val planner = makePlanner()
        val plan = planner.decompose("goal")
        assertThat(plan!!.estimatedResources.estimatedCostUsd).isGreaterThan(0f)
        assertThat(plan.estimatedResources.llmCallsEstimated).isEqualTo(3)
    }

    @Test
    fun `replan removes failed sub-task and dependents`() = runTest {
        val planner = makePlanner()
        val originalPlan = planner.decompose("goal")!!
        val replanned = planner.replan(originalPlan, failedSubTaskId = "research_flights", failureReason = "sold out")
        assertThat(replanned).isNotNull()
        assertThat(replanned!!.subTasks).hasSize(2)  // flights removed
    }
}

class FuzzyConstraintInterpreterTest {

    private val interpreter = FuzzyConstraintInterpreter(Dispatchers.Unconfined)

    @Test
    fun `interpret budget-friendly returns BUDGET constraint`() = runTest {
        val result = interpreter.interpret("budget-friendly")
        assertThat(result).isNotNull()
        assertThat(result!!.type).isEqualTo(ConstraintType.BUDGET)
        assertThat(result.isFuzzy).isTrue()
    }

    @Test
    fun `interpret affordable returns BUDGET constraint`() = runTest {
        val result = interpreter.interpret("affordable")
        assertThat(result!!.type).isEqualTo(ConstraintType.BUDGET)
    }

    @Test
    fun `interpret fast returns TIME constraint`() = runTest {
        val result = interpreter.interpret("fast")
        assertThat(result!!.type).isEqualTo(ConstraintType.TIME)
        assertThat(result.description).contains("Fast")
    }

    @Test
    fun `interpret comfortable returns PREFERENCE constraint`() = runTest {
        val result = interpreter.interpret("comfortable")
        assertThat(result!!.type).isEqualTo(ConstraintType.PREFERENCE)
    }

    @Test
    fun `interpret nearby returns LOCATION constraint`() = runTest {
        val result = interpreter.interpret("nearby")
        assertThat(result!!.type).isEqualTo(ConstraintType.LOCATION)
    }

    @Test
    fun `interpret luxury returns BUDGET premium constraint`() = runTest {
        val result = interpreter.interpret("luxury")
        assertThat(result!!.type).isEqualTo(ConstraintType.BUDGET)
        assertThat(result.value).isEqualTo("premium")
    }

    @Test
    fun `interpret unknown phrase returns null`() = runTest {
        assertThat(interpreter.interpret("some random text")).isNull()
    }

    @Test
    fun `isFuzzyPhrase detects fuzzy phrases in longer text`() {
        assertThat(interpreter.isFuzzyPhrase("I want something budget-friendly")).isTrue()
        assertThat(interpreter.isFuzzyPhrase("find a fast route")).isTrue()
        assertThat(interpreter.isFuzzyPhrase("look for comfortable hotels")).isTrue()
    }

    @Test
    fun `isFuzzyPhrase returns false for non-fuzzy text`() {
        assertThat(interpreter.isFuzzyPhrase("under 15000 rupees")).isFalse()
        assertThat(interpreter.isFuzzyPhrase("3 days in Goa")).isFalse()
    }

    @Test
    fun `interpret with contextType BUDGET and budget keyword returns constraint`() = runTest {
        val result = interpreter.interpret("budget option", contextType = ConstraintType.BUDGET)
        assertThat(result).isNotNull()
        assertThat(result!!.type).isEqualTo(ConstraintType.BUDGET)
    }
}
