// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.voice.tts

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// AUTO_FIX_0172: [feature] ProsodyComputerTest verified

// VOICE_FIX_025: Prosody computer validated

class ProsodyComputerTest {

    @Test
    fun `NEUTRAL emotion preserves persona base pitch and rate`() {
        val prosody = ProsodyComputer.compute(
            persona = VoicePersona.JARVIS,
            emotion = Emotion.NEUTRAL,
        )
        assertThat(prosody.pitch).isWithin(0.001f).of(VoicePersona.JARVIS.basePitch)
        assertThat(prosody.rate).isWithin(0.001f).of(VoicePersona.JARVIS.baseRate)
    }

    @Test
    fun `HAPPY emotion raises pitch above persona base`() {
        val prosody = ProsodyComputer.compute(
            persona = VoicePersona.JARVIS,
            emotion = Emotion.HAPPY,
        )
        assertThat(prosody.pitch).isGreaterThan(VoicePersona.JARVIS.basePitch)
    }

    @Test
    fun `ULTRON plus HAPPY downgrades to NEUTRAL`() {
        val prosody = ProsodyComputer.compute(
            persona = VoicePersona.ULTRON,
            emotion = Emotion.HAPPY,
        )
        // ULTRON can't express HAPPY → emotion downgraded to NEUTRAL
        // → pitch == ULTRON base pitch (× 1.0 NEUTRAL multiplier)
        assertThat(prosody.pitch).isWithin(0.001f).of(VoicePersona.ULTRON.basePitch)
    }

    @Test
    fun `activity override multiplies rate`() {
        val override = ProsodyOverride(
            volumeMultiplier = 1.5f,
            rateMultiplier = 1.2f,
            maxTokens = 50,
            whisper = false,
        )
        val prosody = ProsodyComputer.compute(
            persona = VoicePersona.JARVIS,
            emotion = Emotion.NEUTRAL,
            override = override,
        )
        // Rate = JARVIS.baseRate (1.0) × NEUTRAL (1.0) × override (1.2) = 1.2
        assertThat(prosody.rate).isWithin(0.001f).of(1.2f)
        assertThat(prosody.volume).isWithin(0.001f).of(1.5f)
        assertThat(prosody.maxTokens).isEqualTo(50)
    }

    @Test
    fun `whisper mode forces low pitch and volume`() {
        val override = ProsodyOverride(
            volumeMultiplier = 0.3f,
            rateMultiplier = 0.7f,
            maxTokens = 20,
            whisper = true,
        )
        val prosody = ProsodyComputer.compute(
            persona = VoicePersona.HOPE,  // HOPE has highest base pitch (1.15)
            emotion = Emotion.HAPPY,       // would normally push pitch very high
            override = override,
        )
        // Whisper clamps pitch to ≤ 0.7
        assertThat(prosody.pitch).isAtMost(0.7f)
        assertThat(prosody.volume).isAtMost(0.4f)
        assertThat(prosody.whisper).isTrue()
    }

    @Test
    fun `concise activity override downgrades pause pattern to SHORT`() {
        val override = ProsodyOverride(
            volumeMultiplier = 1.5f,
            rateMultiplier = 1.2f,
            maxTokens = 30,  // concise → SHORT pauses
            whisper = false,
        )
        val prosody = ProsodyComputer.compute(
            persona = VoicePersona.JARVIS,
            emotion = Emotion.THOUGHTFUL,  // normally LONG pauses
            override = override,
        )
        assertThat(prosody.pausePattern).isEqualTo(PausePattern.SHORT)
    }

    @Test
    fun `computed pitch never exceeds TTS-safe bounds`() {
        // Try the most extreme combination: HOPE (1.15) × HAPPY (1.15) = 1.32
        val prosody = ProsodyComputer.compute(
            persona = VoicePersona.HOPE,
            emotion = Emotion.HAPPY,
        )
        assertThat(prosody.pitch).isAtMost(ProsodyProfile.MAX_PITCH)
        assertThat(prosody.pitch).isAtLeast(ProsodyProfile.MIN_PITCH)
    }

    @Test
    fun `computed prosody always has valid bounds`() {
        for (persona in VoicePersona.entries) {
            for (emotion in Emotion.entries) {
                val prosody = ProsodyComputer.compute(persona, emotion)
                assertThat(prosody.pitch).isAtLeast(ProsodyProfile.MIN_PITCH)
                assertThat(prosody.pitch).isAtMost(ProsodyProfile.MAX_PITCH)
                assertThat(prosody.rate).isAtLeast(ProsodyProfile.MIN_RATE)
                assertThat(prosody.rate).isAtMost(ProsodyProfile.MAX_RATE)
                assertThat(prosody.volume).isAtLeast(0.0f)
                assertThat(prosody.volume).isAtMost(1.0f)
            }
        }
    }
}
