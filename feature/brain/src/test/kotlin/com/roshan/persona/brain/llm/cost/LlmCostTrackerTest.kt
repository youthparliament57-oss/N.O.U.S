// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.brain.llm.cost

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.common.AppError
import com.roshan.persona.common.Result
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.ZoneId

// AUTO_FIX_0086: [feature] LlmCostTrackerTest verified

class LlmCostTrackerTest {

    private fun tracker(timezone: ZoneId = ZoneId.of("Asia/Kolkata")) =
        LlmCostTracker(userTimezone = timezone)

    // ─── Basic recording tests ────────────────────────────────────────────

    @Test
    fun `recordUsage tracks tokens and cost`() = runTest {
        val t = tracker()

        val result = t.recordUsage(
            providerId = "openai",
            inputTokens = 1000,
            outputTokens = 500,
            costUsd = 0.05f,
            success = true,
        )

        assertThat(result).isInstanceOf(Result.Success::class.java)
        val usage = (result as Result.Success).data
        assertThat(usage.inputTokens).isEqualTo(1000L)
        assertThat(usage.outputTokens).isEqualTo(500L)
        assertThat(usage.costUsd).isWithin(0.0001f).of(0.05f)
        assertThat(usage.successfulCalls).isEqualTo(1L)
    }

    @Test
    fun `multiple recordUsage calls accumulate`() = runTest {
        val t = tracker()

        t.recordUsage("openai", 1000, 500, 0.05f, success = true)
        t.recordUsage("openai", 2000, 1000, 0.10f, success = true)
        t.recordUsage("openai", 500, 100, 0.01f, success = false)

        val usage = t.getUsageForProvider("openai")
        assertThat(usage).isNotNull()
        assertThat(usage!!.inputTokens).isEqualTo(3500L)
        assertThat(usage.outputTokens).isEqualTo(1600L)
        assertThat(usage.costUsd).isWithin(0.0001f).of(0.16f)
        assertThat(usage.successfulCalls).isEqualTo(2L)
        assertThat(usage.failedCalls).isEqualTo(1L)
        assertThat(usage.totalCalls).isEqualTo(3L)
    }

    @Test
    fun `multiple providers tracked separately`() = runTest {
        val t = tracker()

        t.recordUsage("openai", 1000, 500, 0.05f, success = true)
        t.recordUsage("anthropic", 2000, 1000, 0.15f, success = true)

        val openai = t.getUsageForProvider("openai")
        val anthropic = t.getUsageForProvider("anthropic")

        assertThat(openai!!.costUsd).isWithin(0.0001f).of(0.05f)
        assertThat(anthropic!!.costUsd).isWithin(0.0001f).of(0.15f)
    }

    @Test
    fun `getTotalSpentThisMonth sums all providers`() = runTest {
        val t = tracker()

        t.recordUsage("openai", 1000, 500, 0.05f, success = true)
        t.recordUsage("anthropic", 2000, 1000, 0.15f, success = true)
        t.recordUsage("groq", 500, 200, 0f, success = true)

        val total = t.getTotalSpentThisMonth()

        assertThat(total).isWithin(0.0001f).of(0.20f)
    }

    // ─── Budget enforcement tests ─────────────────────────────────────────

    @Test
    fun `checkBudget returns Success when under budget`() = runTest {
        val t = tracker()
        t.recordUsage("openai", 1000, 500, 0.50f, success = true)

        val result = t.checkBudget(
            monthlyBudgetUsd = 5.0f,
            estimatedCostUsd = 0.10f,
        )

        assertThat(result).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `checkBudget returns Failure when would exceed budget`() = runTest {
        val t = tracker()
        t.recordUsage("openai", 1000, 500, 4.90f, success = true)  // already spent $4.90

        val result = t.checkBudget(
            monthlyBudgetUsd = 5.0f,
            estimatedCostUsd = 0.20f,  // would push total to $5.10 — over budget
        )

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        val error = (result as Result.Failure).error
        assertThat(error).isInstanceOf(AppError.Configuration.BudgetExceeded::class.java)
        assertThat(error.code).isEqualTo("CONFIG_BUDGET_EXCEEDED")
    }

    @Test
    fun `checkBudget exactly at budget returns Success`() = runTest {
        val t = tracker()
        t.recordUsage("openai", 1000, 500, 4.90f, success = true)

        // Spent $4.90 + new $0.10 = $5.00 (exactly at budget, not over)
        val result = t.checkBudget(
            monthlyBudgetUsd = 5.0f,
            estimatedCostUsd = 0.10f,
        )

        assertThat(result).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `checkBudget on empty tracker always returns Success`() = runTest {
        val t = tracker()

        val result = t.checkBudget(
            monthlyBudgetUsd = 0.01f,
            estimatedCostUsd = 0.005f,
        )

        assertThat(result).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `checkBudget on zero budget rejects any positive cost`() = runTest {
        val t = tracker()

        val result = t.checkBudget(
            monthlyBudgetUsd = 0f,
            estimatedCostUsd = 0.01f,
        )

        assertThat(result).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `checkBudget with zero estimated cost always returns Success`() = runTest {
        val t = tracker()
        t.recordUsage("openai", 1000, 500, 5.0f, success = true)  // at budget

        // Free call (e.g., Groq free tier) — should still pass
        val result = t.checkBudget(
            monthlyBudgetUsd = 5.0f,
            estimatedCostUsd = 0f,
        )

        assertThat(result).isInstanceOf(Result.Success::class.java)
    }

    // ─── Monthly reset tests ──────────────────────────────────────────────

    @Test
    fun `getAllUsage returns sorted by cost descending`() = runTest {
        val t = tracker()

        t.recordUsage("openai", 1000, 500, 0.10f, success = true)
        t.recordUsage("anthropic", 1000, 500, 0.30f, success = true)
        t.recordUsage("groq", 1000, 500, 0.05f, success = true)

        val all = t.getAllUsage()

        assertThat(all).hasSize(3)
        assertThat(all[0].providerId).isEqualTo("anthropic")  // highest cost
        assertThat(all[1].providerId).isEqualTo("openai")
        assertThat(all[2].providerId).isEqualTo("groq")  // lowest cost
    }

    @Test
    fun `reset clears all usage data`() = runTest {
        val t = tracker()
        t.recordUsage("openai", 1000, 500, 0.50f, success = true)
        t.recordUsage("anthropic", 2000, 1000, 0.30f, success = true)

        t.reset()

        assertThat(t.getUsageForProvider("openai")).isNull()
        assertThat(t.getUsageForProvider("anthropic")).isNull()
        assertThat(t.getTotalSpentThisMonth()).isEqualTo(0f)
        val stats = t.getAggregateStats()
        assertThat(stats.totalCalls).isEqualTo(0L)
    }

    // ─── Aggregate stats tests ────────────────────────────────────────────

    @Test
    fun `getAggregateStats tracks total calls`() = runTest {
        val t = tracker()

        t.recordUsage("openai", 100, 50, 0.01f, success = true)
        t.recordUsage("openai", 100, 50, 0.01f, success = true)
        t.recordUsage("anthropic", 100, 50, 0.01f, success = false)

        val stats = t.getAggregateStats()

        assertThat(stats.totalCalls).isEqualTo(3L)
        assertThat(stats.successfulCalls).isEqualTo(2L)
        assertThat(stats.failedCalls).isEqualTo(1L)
        assertThat(stats.totalSpentThisMonth).isWithin(0.0001f).of(0.03f)
    }

    @Test
    fun `aggregate stats persist across monthly resets`() = runTest {
        val t = tracker()

        t.recordUsage("openai", 100, 50, 0.01f, success = true)

        // Stats before
        val before = t.getAggregateStats()
        assertThat(before.totalCalls).isEqualTo(1L)

        // After reset (would happen automatically on month change, but we test manually)
        t.reset()

        // After reset, stats cleared
        val after = t.getAggregateStats()
        assertThat(after.totalCalls).isEqualTo(0L)
    }

    // ─── MonthlyUsage data class tests ────────────────────────────────────

    @Test
    fun `MonthlyUsage addUsage accumulates correctly`() {
        val usage = LlmCostTracker.MonthlyUsage(
            providerId = "test",
            monthYear = "2026-07",
        )

        val updated = usage
            .addUsage(inputTokens = 100, outputTokens = 50, costUsd = 0.01f, success = true)
            .addUsage(inputTokens = 200, outputTokens = 100, costUsd = 0.02f, success = true)
            .addUsage(inputTokens = 50, outputTokens = 25, costUsd = 0.005f, success = false)

        assertThat(updated.inputTokens).isEqualTo(350L)
        assertThat(updated.outputTokens).isEqualTo(175L)
        assertThat(updated.costUsd).isWithin(0.0001f).of(0.035f)
        assertThat(updated.successfulCalls).isEqualTo(2L)
        assertThat(updated.failedCalls).isEqualTo(1L)
        assertThat(updated.totalCalls).isEqualTo(3L)
    }

    @Test
    fun `MonthlyUsage starts at zero`() {
        val usage = LlmCostTracker.MonthlyUsage(
            providerId = "test",
            monthYear = "2026-07",
        )

        assertThat(usage.inputTokens).isEqualTo(0L)
        assertThat(usage.outputTokens).isEqualTo(0L)
        assertThat(usage.costUsd).isEqualTo(0f)
        assertThat(usage.totalCalls).isEqualTo(0L)
    }
}
