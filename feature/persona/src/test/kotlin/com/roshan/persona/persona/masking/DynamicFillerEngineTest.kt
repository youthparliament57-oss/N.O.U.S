// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.persona.masking

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.persona.core.BuiltInPersonas
import org.junit.Test

// AUTO_FIX_0104: [feature] DynamicFillerEngineTest verified

class DynamicFillerEngineTest {

    private fun makeEngine(config: FillerConfig = FillerConfig()) =
        DynamicFillerEngine(config)

    // ─── shouldPlayFiller tests ────────────────────────────────────────────

    @Test
    fun `shouldPlayFiller returns false when estimated time below threshold`() {
        val engine = makeEngine(config = FillerConfig(latencyThresholdMs = 1000))
        // 500ms — below threshold.
        assertThat(engine.shouldPlayFiller(estimatedProcessingMs = 500, nowMs = 0)).isFalse()
    }

    @Test
    fun `shouldPlayFiller returns true when estimated time exceeds threshold`() {
        val engine = makeEngine(config = FillerConfig(latencyThresholdMs = 1000))
        // 2000ms — above threshold, no filler playing, no cooldown.
        assertThat(engine.shouldPlayFiller(estimatedProcessingMs = 2000, nowMs = 0)).isTrue()
    }

    @Test
    fun `shouldPlayFiller returns true at exact threshold boundary`() {
        val engine = makeEngine(config = FillerConfig(latencyThresholdMs = 1000))
        assertThat(engine.shouldPlayFiller(estimatedProcessingMs = 1000, nowMs = 0)).isTrue()
    }

    @Test
    fun `shouldPlayFiller returns false when filler already playing`() {
        val engine = makeEngine()
        engine.markFillerStarted(nowMs = 0)
        assertThat(engine.shouldPlayFiller(estimatedProcessingMs = 5000, nowMs = 100)).isFalse()
    }

    @Test
    fun `shouldPlayFiller returns false during cooldown period`() {
        val engine = makeEngine(config = FillerConfig(fillerCooldownMs = 3000))
        engine.markFillerStarted(nowMs = 1000)
        // 1 second later — still in 3s cooldown.
        assertThat(engine.shouldPlayFiller(estimatedProcessingMs = 5000, nowMs = 2000)).isFalse()
    }

    @Test
    fun `shouldPlayFiller returns true after cooldown expires`() {
        val engine = makeEngine(config = FillerConfig(fillerCooldownMs = 3000))
        engine.markFillerStarted(nowMs = 1000)
        engine.markFillerFinished()
        // 4 seconds later — cooldown expired.
        assertThat(engine.shouldPlayFiller(estimatedProcessingMs = 5000, nowMs = 5000)).isTrue()
    }

    // ─── getFiller tests ───────────────────────────────────────────────────

    @Test
    fun `getFiller returns persona-specific filler for THINKING`() {
        val engine = makeEngine()
        val filler = engine.getFiller(BuiltInPersonas.ATLAS, FillerCategory.THINKING)
        assertThat(filler).isNotNull()
        assertThat(filler).contains("sir")  // Atlas says "sir"
    }

    @Test
    fun `getFiller returns different fillers for different personas`() {
        val engine = makeEngine()
        val atlasFiller = engine.getFiller(BuiltInPersonas.ATLAS, FillerCategory.THINKING)
        val onyxFiller = engine.getFiller(BuiltInPersonas.ONYX, FillerCategory.THINKING)
        assertThat(atlasFiller).isNotNull()
        assertThat(onyxFiller).isNotNull()
        assertThat(atlasFiller).isNotEqualTo(onyxFiller)
    }

    @Test
    fun `getFiller returns null for SILENT persona (Wraith)`() {
        val engine = makeEngine()
        val filler = engine.getFiller(BuiltInPersonas.WRAITH, FillerCategory.THINKING)
        assertThat(filler).isNull()
    }

    @Test
    fun `getFiller returns non-null for each category`() {
        val engine = makeEngine()
        for (category in FillerCategory.entries) {
            val filler = engine.getFiller(BuiltInPersonas.ATLAS, category)
            assertThat(filler).isNotNull()
        }
    }

    @Test
    fun `getFiller returns SEARCHING filler for Nova`() {
        val engine = makeEngine()
        val filler = engine.getFiller(BuiltInPersonas.NOVA, FillerCategory.SEARCHING)
        assertThat(filler).isNotNull()
        // Nova's search fillers should be casual.
        assertThat(filler!!.contains("Looking") || filler.contains("check") || filler.contains("Searching")).isTrue()
    }

    @Test
    fun `getFiller returns CONTEXT_COMPRESSION filler for Onyx (terse)`() {
        val engine = makeEngine()
        val filler = engine.getFiller(BuiltInPersonas.ONYX, FillerCategory.CONTEXT_COMPRESSION)
        assertThat(filler).isNotNull()
        // Onyx fillers are terse — single words like "Reorganizing."
        assertThat(filler!!.length).isLessThan(30)
    }

    // ─── markFillerStarted + markFillerFinished tests ──────────────────────

    @Test
    fun `markFillerStarted sets isFillerPlaying true`() {
        val engine = makeEngine()
        engine.markFillerStarted(nowMs = 0)
        assertThat(engine.isFillerPlaying()).isTrue()
    }

    @Test
    fun `markFillerFinished sets isFillerPlaying false`() {
        val engine = makeEngine()
        engine.markFillerStarted(nowMs = 0)
        engine.markFillerFinished()
        assertThat(engine.isFillerPlaying()).isFalse()
    }

    @Test
    fun `reset clears all state`() {
        val engine = makeEngine()
        engine.markFillerStarted(nowMs = 1000)
        engine.reset()
        assertThat(engine.isFillerPlaying()).isFalse()
        // After reset, should be able to play filler immediately (no cooldown).
        assertThat(engine.shouldPlayFiller(estimatedProcessingMs = 5000, nowMs = 100)).isTrue()
    }

    // ─── Config validation ─────────────────────────────────────────────────

    @Test
    fun `FillerConfig validates bounds`() {
        try {
            FillerConfig(latencyThresholdMs = 0)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("latencyThresholdMs")
        }
        try {
            FillerConfig(fillerCooldownMs = -1)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("fillerCooldownMs")
        }
    }

    @Test
    fun `FillerCategory has 4 distinct values`() {
        assertThat(FillerCategory.entries).hasSize(4)
        assertThat(FillerCategory.entries.map { it.name }).containsExactly(
            "THINKING", "SEARCHING", "PROCESSING", "CONTEXT_COMPRESSION",
        )
    }

    @Test
    fun `all 8 non-silent personas have fillers for all 4 categories`() {
        val engine = makeEngine()
        for (persona in BuiltInPersonas.ALL) {
            if (persona.proactiveStyle == com.roshan.persona.persona.core.ProactiveStyle.SILENT) continue
            for (category in FillerCategory.entries) {
                val filler = engine.getFiller(persona, category)
                assertThat(filler).named("filler for ${persona.id}/$category").isNotNull()
            }
        }
    }
}
