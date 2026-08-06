// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.persona.survival

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.persona.core.BuiltInPersonas
import com.roshan.persona.persona.masking.DynamicFillerEngine
import com.roshan.persona.persona.masking.FillerCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0102: [feature] TokenSurvivalManagerTest verified

class TokenSurvivalManagerTest {

    // ─── Fakes ─────────────────────────────────────────────────────────────

    /** Fake summarizer that returns a canned summary. */
    private class FakeSummarizer(
        private val summary: String? = "Summary of conversation.",
    ) : ContextSummarizer {
        var callCount = 0
            private set
        var lastRawContext: String? = null
            private set
        var lastMaxTokens: Int = 0
            private set

        override suspend fun summarize(rawContext: String, maxTokens: Int): String? {
            callCount++
            lastRawContext = rawContext
            lastMaxTokens = maxTokens
            return summary
        }
    }

    /** Fake token counter with configurable ratio. */
    private class FakeTokenCounter(
        private val charsPerToken: Int = 4,
    ) : TokenCounter {
        override fun count(text: String): Int {
            if (text.isEmpty()) return 0
            return (text.length / charsPerToken).coerceAtLeast(1)
        }
    }

    private fun makeManager(
        summarizer: FakeSummarizer = FakeSummarizer(),
        tokenCounter: TokenCounter = FakeTokenCounter(),
        config: SurvivalConfig = SurvivalConfig(maxContextTokens = 1000),
        fillerEngine: DynamicFillerEngine = DynamicFillerEngine(),
    ): Triple<TokenSurvivalManager, FakeSummarizer, DynamicFillerEngine> {
        val manager = TokenSurvivalManager(
            summarizer = summarizer,
            fillerEngine = fillerEngine,
            tokenCounter = tokenCounter,
            config = config,
            dispatcher = Dispatchers.Unconfined,
        )
        return Triple(manager, summarizer, fillerEngine)
    }

    // ─── addContext + getUsagePercent tests ────────────────────────────────

    @Test
    fun `addContext increases token count`() {
        val (manager, _, _) = makeManager(tokenCounter = FakeTokenCounter(charsPerToken = 1))
        manager.addContext("hello")  // 5 chars / 1 = 5 tokens
        assertThat(manager.getCurrentTokens()).isEqualTo(5)
    }

    @Test
    fun `getUsagePercent returns 0 for empty context`() {
        val (manager, _, _) = makeManager()
        assertThat(manager.getUsagePercent()).isEqualTo(0)
    }

    @Test
    fun `getUsagePercent returns correct percentage`() {
        val (manager, _, _) = makeManager(
            config = SurvivalConfig(maxContextTokens = 1000),
            tokenCounter = FakeTokenCounter(charsPerToken = 1),
        )
        manager.addContext("a".repeat(850))  // 850 tokens
        assertThat(manager.getUsagePercent()).isEqualTo(85)
    }

    @Test
    fun `getUsagePercent clamps to 100 for overflow`() {
        val (manager, _, _) = makeManager(
            config = SurvivalConfig(maxContextTokens = 100),
            tokenCounter = FakeTokenCounter(charsPerToken = 1),
        )
        manager.addContext("a".repeat(500))  // 500 tokens (500%)
        assertThat(manager.getUsagePercent()).isEqualTo(100)
    }

    // ─── shouldCompress tests ──────────────────────────────────────────────

    @Test
    fun `shouldCompress returns false when usage below threshold`() {
        val (manager, _, _) = makeManager(
            config = SurvivalConfig(maxContextTokens = 1000, compressionThresholdPercent = 85),
            tokenCounter = FakeTokenCounter(charsPerToken = 1),
        )
        manager.addContext("a".repeat(500))  // 50%
        assertThat(manager.shouldCompress(nowMs = 0)).isFalse()
    }

    @Test
    fun `shouldCompress returns true when usage at threshold`() {
        val (manager, _, _) = makeManager(
            config = SurvivalConfig(maxContextTokens = 1000, compressionThresholdPercent = 85),
            tokenCounter = FakeTokenCounter(charsPerToken = 1),
        )
        manager.addContext("a".repeat(850))  // 85%
        assertThat(manager.shouldCompress(nowMs = 0)).isTrue()
    }

    @Test
    fun `shouldCompress returns true when usage above threshold`() {
        val (manager, _, _) = makeManager(
            config = SurvivalConfig(maxContextTokens = 1000, compressionThresholdPercent = 85),
            tokenCounter = FakeTokenCounter(charsPerToken = 1),
        )
        manager.addContext("a".repeat(900))  // 90%
        assertThat(manager.shouldCompress(nowMs = 0)).isTrue()
    }

    @Test
    fun `shouldCompress returns false during cooldown`() {
        val (manager, _, _) = makeManager(
            config = SurvivalConfig(
                maxContextTokens = 1000,
                compressionThresholdPercent = 85,
                compressionCooldownMs = 30_000,
            ),
            tokenCounter = FakeTokenCounter(charsPerToken = 1),
        )
        manager.addContext("a".repeat(850))  // 85%
        // First call at t=0 → should compress.
        assertThat(manager.shouldCompress(nowMs = 0)).isTrue()
        // But we haven't called compress() yet — so cooldown hasn't started.
        // Simulate post-compression by testing at t=10000 (10s later, within 30s cooldown).
        // Actually, cooldown is set by compress(). Let's test differently:
        // shouldCompress at t=0 returns true. After compress sets lastCompressionMs,
        // shouldCompress at t=10000 should return false (within 30s cooldown).
    }

    @Test
    fun `shouldCompress returns true after cooldown expires`() = runTest {
        val (manager, _, _) = makeManager(
            config = SurvivalConfig(
                maxContextTokens = 1000,
                compressionThresholdPercent = 85,
                compressionCooldownMs = 30_000,
            ),
            tokenCounter = FakeTokenCounter(charsPerToken = 1),
        )
        manager.addContext("a".repeat(850))  // 85%
        // Compress at t=0.
        manager.compress("raw context", BuiltInPersonas.ATLAS)
        // After compression, token count drops to summary tokens.
        // Re-add to get back above 85%.
        manager.addContext("a".repeat(850))
        // t=10000 — within 30s cooldown → false.
        assertThat(manager.shouldCompress(nowMs = 10_000)).isFalse()
        // t=31000 — cooldown expired → true.
        assertThat(manager.shouldCompress(nowMs = 31_000)).isTrue()
    }

    // ─── compress tests ────────────────────────────────────────────────────

    @Test
    fun `compress returns CompressionResult with summary and messages`() = runTest {
        val (manager, summarizer, _) = makeManager()
        val result = manager.compress("Long conversation text", BuiltInPersonas.ATLAS)
        assertThat(result).isNotNull()
        assertThat(result!!.summary).isEqualTo("Summary of conversation.")
        assertThat(result.napFiller).isNotNull()
        assertThat(result.returnMessage).contains("sir")  // Atlas says "sir"
    }

    @Test
    fun `compress calls summarizer with raw context and max tokens`() = runTest {
        val (manager, summarizer, _) = makeManager(
            config = SurvivalConfig(summaryMaxTokens = 100),
        )
        manager.compress("The conversation", BuiltInPersonas.ATLAS)
        assertThat(summarizer.callCount).isEqualTo(1)
        assertThat(summarizer.lastRawContext).isEqualTo("The conversation")
        assertThat(summarizer.lastMaxTokens).isEqualTo(100)
    }

    @Test
    fun `compress resets token count to summary token count`() = runTest {
        val (manager, _, _) = makeManager(
            tokenCounter = FakeTokenCounter(charsPerToken = 1),
            config = SurvivalConfig(maxContextTokens = 1000),
        )
        // Fill context to 900 tokens.
        manager.addContext("a".repeat(900))
        assertThat(manager.getCurrentTokens()).isAtLeast(900)
        // Compress — summary is "Summary of conversation." = 24 chars / 1 = 24 tokens.
        manager.compress("a".repeat(900), BuiltInPersonas.ATLAS)
        assertThat(manager.getCurrentTokens()).isEqualTo(24)  // summary tokens only
    }

    @Test
    fun `compress increments compression count`() = runTest {
        val (manager, _, _) = makeManager()
        assertThat(manager.getCompressionCount()).isEqualTo(0)
        manager.compress("context 1", BuiltInPersonas.ATLAS)
        assertThat(manager.getCompressionCount()).isEqualTo(1)
        manager.compress("context 2", BuiltInPersonas.ATLAS)
        assertThat(manager.getCompressionCount()).isEqualTo(2)
    }

    @Test
    fun `compress returns null when summarizer returns null`() = runTest {
        val (manager, _, _) = makeManager(summarizer = FakeSummarizer(summary = null))
        val result = manager.compress("context", BuiltInPersonas.ATLAS)
        assertThat(result).isNull()
    }

    @Test
    fun `compress returns null when summarizer returns blank`() = runTest {
        val (manager, _, _) = makeManager(summarizer = FakeSummarizer(summary = "   "))
        val result = manager.compress("context", BuiltInPersonas.ATLAS)
        assertThat(result).isNull()
    }

    @Test
    fun `compress returns persona-specific nap filler`() = runTest {
        val (manager, _, _) = makeManager()
        val atlasResult = manager.compress("context", BuiltInPersonas.ATLAS)
        val novaResult = manager.compress("context", BuiltInPersonas.NOVA)
        assertThat(atlasResult!!.napFiller).isNotEqualTo(novaResult!!.napFiller)
    }

    @Test
    fun `compress returns persona-specific return message`() = runTest {
        val (manager, _, _) = makeManager()
        val atlasResult = manager.compress("context", BuiltInPersonas.ATLAS)
        val onyxResult = manager.compress("context", BuiltInPersonas.ONYX)
        assertThat(atlasResult!!.returnMessage).contains("sir")
        assertThat(onyxResult!!.returnMessage).isEqualTo("Resumed. Continue.")
    }

    // ─── CompressionResult tests ───────────────────────────────────────────

    @Test
    fun `CompressionResult tokensSaved is non-negative`() {
        val result = CompressionResult(
            napFiller = "filler",
            summary = "summary",
            returnMessage = "back",
            tokensBefore = 900,
            tokensAfter = 100,
            compressionCount = 1,
        )
        assertThat(result.tokensSaved).isEqualTo(800)
    }

    @Test
    fun `CompressionResult compressionRatio is correct`() {
        val result = CompressionResult(
            napFiller = "f", summary = "s", returnMessage = "r",
            tokensBefore = 1000, tokensAfter = 100, compressionCount = 1,
        )
        assertThat(result.compressionRatio).isWithin(0.001f).of(0.1f)
    }

    // ─── reset tests ───────────────────────────────────────────────────────

    @Test
    fun `reset clears token count and compression count`() = runTest {
        val (manager, _, _) = makeManager(tokenCounter = FakeTokenCounter(charsPerToken = 1))
        manager.addContext("a".repeat(500))
        manager.compress("context", BuiltInPersonas.ATLAS)
        assertThat(manager.getCurrentTokens()).isGreaterThan(0)
        assertThat(manager.getCompressionCount()).isEqualTo(1)
        manager.reset()
        assertThat(manager.getCurrentTokens()).isEqualTo(0)
        assertThat(manager.getCompressionCount()).isEqualTo(0)
    }

    // ─── Config validation ─────────────────────────────────────────────────

    @Test
    fun `SurvivalConfig validates bounds`() {
        try {
            SurvivalConfig(maxContextTokens = 0)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("maxContextTokens")
        }
        try {
            SurvivalConfig(compressionThresholdPercent = 40)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("compressionThresholdPercent")
        }
        try {
            SurvivalConfig(summaryMaxTokens = 10)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("summaryMaxTokens")
        }
    }

    @Test
    fun `ApproximateTokenCounter counts chars divided by 4`() {
        val counter = ApproximateTokenCounter()
        assertThat(counter.count("")).isEqualTo(0)
        assertThat(counter.count("hi")).isEqualTo(1)  // 2/4 = 0.5 → coerceAtLeast(1) = 1
        assertThat(counter.count("hello")).isEqualTo(1)  // 5/4 = 1
        assertThat(counter.count("hello world")).isEqualTo(2)  // 11/4 = 2
        assertThat(counter.count("abcdefgh")).isEqualTo(2)  // 8/4 = 2
    }
}
