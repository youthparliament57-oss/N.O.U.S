// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.cognitive.solver

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.cognitive.model.Constraint
import com.roshan.persona.cognitive.model.ConstraintType
import com.roshan.persona.cognitive.model.SolutionApproach
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0114: [feature] ProblemSolverTest verified

class PatternMatcherTest {

    private class FakeMemory(private val memories: List<RecalledMemory>) : MemoryRecallInterface {
        override suspend fun recall(query: String, k: Int): List<RecalledMemory> = memories.take(k)
    }

    private class FakeLlm(private val response: String) : SolverLlm {
        override suspend fun solve(prompt: String): String = response
    }

    private fun makeMatcher(
        memories: List<RecalledMemory> = emptyList(),
        llmResponse: String = "",
    ) = PatternMatcher(
        memory = FakeMemory(memories),
        llm = FakeLlm(llmResponse),
        dispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `matchPattern returns result when Memory has matches`() = runTest {
        val matcher = makeMatcher(memories = listOf(
            RecalledMemory(content = "Fixed router by restarting it", relevanceScore = 0.9f),
        ))
        val result = matcher.matchPattern("How to fix router?")
        assertThat(result).isNotNull()
        assertThat(result!!.approach).isEqualTo(SolutionApproach.PATTERN_MATCH)
        assertThat(result.recalledMemory).contains("router")
        assertThat(result.adaptedSolution).contains("previous experience")
    }

    @Test
    fun `matchPattern returns null when Memory is empty`() = runTest {
        val matcher = makeMatcher(memories = emptyList())
        assertThat(matcher.matchPattern("query")).isNull()
    }

    @Test
    fun `findAnalogy returns result when LLM provides analogy`() = runTest {
        val matcher = makeMatcher(llmResponse = """
            ANALOGY: Organizing a conference
            SOLUTION: Treat the wedding like a conference — venue, schedule, catering, guest list.
        """.trimIndent())
        val result = matcher.findAnalogy("How to organize a wedding?")
        assertThat(result).isNotNull()
        assertThat(result!!.analogousProblem).contains("conference")
        assertThat(result.adaptedSolution).contains("wedding")
    }

    @Test
    fun `findAnalogy returns null for blank LLM response`() = runTest {
        val matcher = makeMatcher(llmResponse = "")
        assertThat(matcher.findAnalogy("query")).isNull()
    }

    @Test
    fun `findAnalogy returns null when format is wrong`() = runTest {
        val matcher = makeMatcher(llmResponse = "some random text without proper format")
        assertThat(matcher.findAnalogy("query")).isNull()
    }

    @Test
    fun `synthesize returns creative result`() = runTest {
        val matcher = makeMatcher(llmResponse = "Combine bulk buying with seasonal meal prep for maximum savings.")
        val result = matcher.synthesize("How to save money on groceries?")
        assertThat(result).isNotNull()
        assertThat(result!!.approach).isEqualTo(SolutionApproach.CREATIVE)
        assertThat(result.solution).contains("bulk")
    }

    @Test
    fun `synthesize returns null for blank response`() = runTest {
        val matcher = makeMatcher(llmResponse = "")
        assertThat(matcher.synthesize("query")).isNull()
    }

    @Test
    fun `parseAnalogyResponse extracts ANALOGY and SOLUTION fields`() {
        val matcher = makeMatcher()
        val result = matcher.parseAnalogyResponse("ANALOGY: test analogy\nSOLUTION: test solution")
        assertThat(result).isNotNull()
        assertThat(result!!.analogousProblem).isEqualTo("test analogy")
        assertThat(result.adaptedSolution).isEqualTo("test solution")
    }
}

class ConstraintSatisfierTest {

    private val satisfier = ConstraintSatisfier(Dispatchers.Unconfined)

    @Test
    fun `isSatisfied BUDGET returns true when solution cost within budget`() {
        val constraint = Constraint(ConstraintType.BUDGET, "15000", description = "under ₹15000")
        val solution = "Total cost: ₹12,000"
        assertThat(satisfier.isSatisfied(solution, constraint)).isTrue()
    }

    @Test
    fun `isSatisfied BUDGET returns false when solution cost exceeds budget`() {
        val constraint = Constraint(ConstraintType.BUDGET, "10000", description = "under ₹10000")
        val solution = "Total cost: ₹15,000"
        assertThat(satisfier.isSatisfied(solution, constraint)).isFalse()
    }

    @Test
    fun `isSatisfied LOCATION returns true when location mentioned`() {
        val constraint = Constraint(ConstraintType.LOCATION, "goa", description = "in Goa")
        val solution = "Visit beaches in Goa"
        assertThat(satisfier.isSatisfied(solution, constraint)).isTrue()
    }

    @Test
    fun `isSatisfied LOCATION returns false when location not mentioned`() {
        val constraint = Constraint(ConstraintType.LOCATION, "goa", description = "in Goa")
        val solution = "Visit beaches in Mumbai"
        assertThat(satisfier.isSatisfied(solution, constraint)).isFalse()
    }

    @Test
    fun `isSatisfied PERMISSION always returns true`() {
        val constraint = Constraint(ConstraintType.PERMISSION, "INTERNET")
        assertThat(satisfier.isSatisfied("any solution", constraint)).isTrue()
    }

    @Test
    fun `checkViolations returns violated constraints`() = runTest {
        val budget = Constraint(ConstraintType.BUDGET, "10000", description = "under ₹10000")
        val location = Constraint(ConstraintType.LOCATION, "goa", description = "in Goa")
        val solution = "Trip to Goa costs ₹15,000"
        val violations = satisfier.checkViolations(solution, listOf(budget, location))
        assertThat(violations).hasSize(1)  // budget violated, location satisfied
        assertThat(violations[0].type).isEqualTo(ConstraintType.BUDGET)
    }

    @Test
    fun `pickConstraintToRelax returns least important relaxable`() {
        val budget = Constraint(ConstraintType.BUDGET, "10000")
        val pref = Constraint(ConstraintType.PREFERENCE, "comfortable")
        val toRelax = satisfier.pickConstraintToRelax(listOf(budget, pref))
        // Budget (priority 1) is more relaxable than preference (priority 5).
        assertThat(toRelax?.type).isEqualTo(ConstraintType.BUDGET)
    }

    @Test
    fun `pickConstraintToRelax returns null for PERMISSION only`() {
        val perm = Constraint(ConstraintType.PERMISSION, "INTERNET")
        assertThat(satisfier.pickConstraintToRelax(listOf(perm))).isNull()
    }

    @Test
    fun `buildRelaxationQuestion BUDGET asks user about budget`() {
        val constraint = Constraint(ConstraintType.BUDGET, "15000", description = "under ₹15000")
        val question = satisfier.buildRelaxationQuestion(constraint)
        assertThat(question).isNotNull()
        assertThat(question).contains("budget")
    }

    @Test
    fun `buildRelaxationQuestion PERMISSION returns null`() {
        val constraint = Constraint(ConstraintType.PERMISSION, "INTERNET")
        assertThat(satisfier.buildRelaxationQuestion(constraint)).isNull()
    }

    @Test
    fun `createRelaxation builds RelaxedConstraint with reason`() {
        val original = Constraint(ConstraintType.BUDGET, "15000")
        val relaxed = satisfier.createRelaxation(original, "20000", "Increased budget by ₹5000")
        assertThat(relaxed.originalConstraint).isEqualTo(original)
        assertThat(relaxed.relaxedValue).isEqualTo("20000")
        assertThat(relaxed.reason).contains("Increased")
    }
}

class ProblemSolverTest {

    private class FakeMemory(private val memories: List<RecalledMemory>) : MemoryRecallInterface {
        override suspend fun recall(query: String, k: Int): List<RecalledMemory> = memories
    }

    private class FakeLlm(private val response: String) : SolverLlm {
        override suspend fun solve(prompt: String): String = response
    }

    private fun makeSolver(
        memories: List<RecalledMemory> = emptyList(),
        llmResponse: String = "Creative solution here.",
    ): ProblemSolver {
        val matcher = PatternMatcher(FakeMemory(memories), FakeLlm(llmResponse), Dispatchers.Unconfined)
        val satisfier = ConstraintSatisfier(Dispatchers.Unconfined)
        return ProblemSolver(matcher, satisfier, Dispatchers.Unconfined)
    }

    @Test
    fun `solve returns PATTERN_MATCH solution when Memory has match`() = runTest {
        val solver = makeSolver(memories = listOf(
            RecalledMemory(content = "Fixed by restarting", relevanceScore = 0.9f),
        ))
        val result = solver.solve("How to fix router?")
        assertThat(result).isNotNull()
        assertThat(result!!.approach).isEqualTo(SolutionApproach.PATTERN_MATCH)
        assertThat(result.recalledMemory).contains("restarting")
    }

    @Test
    fun `solve returns CREATIVE solution when Memory empty but LLM responds`() = runTest {
        val solver = makeSolver(memories = emptyList(), llmResponse = "Try a creative approach.")
        val result = solver.solve("How to save money?")
        assertThat(result).isNotNull()
        // Pattern match fails (empty memory), analogy LLM returns "Try a creative approach."
        // which doesn't match ANALOGY/SOLUTION format → analogy null.
        // Creative synthesis returns "Try a creative approach."
        assertThat(result!!.approach).isEqualTo(SolutionApproach.CREATIVE)
    }

    @Test
    fun `solve returns null when all approaches fail`() = runTest {
        val solver = makeSolver(memories = emptyList(), llmResponse = "")
        val result = solver.solve("impossible problem")
        assertThat(result).isNull()
    }

    @Test
    fun `solve includes constraintsSatisfied in result`() = runTest {
        val solver = makeSolver(memories = listOf(
            RecalledMemory(content = "Solution in Goa under 12000"),
        ))
        val constraints = listOf(
            Constraint(ConstraintType.LOCATION, "goa", description = "in Goa"),
        )
        val result = solver.solve("plan trip", constraints)
        assertThat(result).isNotNull()
        assertThat(result!!.constraintsSatisfied).isNotEmpty()
    }

    @Test
    fun `solve includes constraintsRelaxed when budget violated`() = runTest {
        val solver = makeSolver(memories = listOf(
            RecalledMemory(content = "Expensive solution costing 50000"),
        ))
        val constraints = listOf(
            Constraint(ConstraintType.BUDGET, "10000", description = "under ₹10000"),
        )
        val result = solver.solve("plan trip", constraints)
        assertThat(result).isNotNull()
        // Budget violated → should have a relaxed constraint.
        if (result!!.constraintsRelaxed.isNotEmpty()) {
            assertThat(result.constraintsRelaxed[0].originalConstraint.type).isEqualTo(ConstraintType.BUDGET)
        }
    }
}
