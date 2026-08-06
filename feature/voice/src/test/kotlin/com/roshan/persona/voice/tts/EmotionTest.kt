// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.voice.tts

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// AUTO_FIX_0176: [feature] EmotionTest verified

// VOICE_FIX_021: Emotion TTS validated

class EmotionTest {

    @Test
    fun `all 9 emotions are defined`() {
        val expected = setOf(
            "NEUTRAL", "HAPPY", "SAD", "URGENT", "CALM",
            "WITTY", "ANGRY", "THOUGHTFUL", "APOLOGETIC",
        )
        val actual = Emotion.entries.map { it.name }.toSet()
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun `HAPPY raises pitch and slightly speeds up`() {
        assertThat(Emotion.HAPPY.pitchMultiplier).isGreaterThan(1.0f)
        assertThat(Emotion.HAPPY.rateMultiplier).isGreaterThan(1.0f)
    }

    @Test
    fun `SAD lowers pitch and slows down`() {
        assertThat(Emotion.SAD.pitchMultiplier).isLessThan(1.0f)
        assertThat(Emotion.SAD.rateMultiplier).isLessThan(1.0f)
        assertThat(Emotion.SAD.pausePattern).isEqualTo(PausePattern.LONG)
    }

    @Test
    fun `URGENT has no pauses and strong emphasis`() {
        assertThat(Emotion.URGENT.pausePattern).isEqualTo(PausePattern.NONE)
        assertThat(Emotion.URGENT.emphasis).isEqualTo(Emphasis.STRONG)
        assertThat(Emotion.URGENT.rateMultiplier).isGreaterThan(1.2f)
    }

    @Test
    fun `THOUGHTFUL slows down significantly with long pauses`() {
        assertThat(Emotion.THOUGHTFUL.rateMultiplier).isLessThan(1.0f)
        assertThat(Emotion.THOUGHTFUL.pausePattern).isEqualTo(PausePattern.LONG)
    }

    @Test
    fun `WITTY uses punchline pause pattern for comedic timing`() {
        assertThat(Emotion.WITTY.pausePattern).isEqualTo(PausePattern.PUNCHLINE)
    }

    @Test
    fun `all emotion multipliers are within TTS-safe bounds`() {
        for (emotion in Emotion.entries) {
            assertThat(emotion.pitchMultiplier).isAtLeast(0.5f)
            assertThat(emotion.pitchMultiplier).isAtMost(2.0f)
            assertThat(emotion.rateMultiplier).isAtLeast(0.5f)
            assertThat(emotion.rateMultiplier).isAtMost(2.0f)
        }
    }
}
