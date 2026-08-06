// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.voice.tts

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// AUTO_FIX_0173: [feature] ActivityAwareProsodyTest verified

// VOICE_FIX_024: Activity aware prosody ok

class ActivityAwareProsodyTest {

    private val prosody = ActivityAwareProsody()

    @Test
    fun `RUNNING produces loud fast concise override`() {
        val override = prosody.computeOverride(UserActivity.RUNNING)
        assertThat(override.volumeMultiplier).isGreaterThan(1.0f)
        assertThat(override.rateMultiplier).isGreaterThan(1.0f)
        assertThat(override.maxTokens).isAtMost(50)
        assertThat(override.whisper).isFalse()
    }

    @Test
    fun `IN_VEHICLE matches RUNNING for noisy environment`() {
        val override = prosody.computeOverride(UserActivity.IN_VEHICLE)
        assertThat(override.volumeMultiplier).isGreaterThan(1.0f)
        assertThat(override.maxTokens).isAtMost(50)
    }

    @Test
    fun `SLEEPING produces whisper override with low volume and short max tokens`() {
        val override = prosody.computeOverride(UserActivity.SLEEPING)
        assertThat(override.whisper).isTrue()
        assertThat(override.volumeMultiplier).isAtMost(0.4f)
        assertThat(override.maxTokens).isAtMost(20)
    }

    @Test
    fun `WALKING produces neutral override`() {
        val override = prosody.computeOverride(UserActivity.WALKING)
        assertThat(override.volumeMultiplier).isWithin(0.001f).of(1.0f)
        assertThat(override.rateMultiplier).isWithin(0.001f).of(1.0f)
        assertThat(override.maxTokens).isEqualTo(100)
        assertThat(override.whisper).isFalse()
    }

    @Test
    fun `STILL produces default override`() {
        val override = prosody.computeOverride(UserActivity.STILL)
        assertThat(override.volumeMultiplier).isWithin(0.001f).of(1.0f)
        assertThat(override.maxTokens).isEqualTo(200)
    }

    @Test
    fun `IN_MEETING produces low-volume override as safety net`() {
        val override = prosody.computeOverride(UserActivity.IN_MEETING)
        // Even though Brain should suppress TTS entirely, the override is
        // a safety net with low volume and short max tokens.
        assertThat(override.volumeMultiplier).isAtMost(0.5f)
        assertThat(override.maxTokens).isAtMost(50)
    }

    @Test
    fun `UNKNOWN activity falls back to DEFAULT override`() {
        val override = prosody.computeOverride(UserActivity.UNKNOWN)
        assertThat(override).isEqualTo(ProsodyOverride.DEFAULT)
    }

    @Test
    fun `STILL plus sleeping ambient snapshot refines to SLEEPING`() {
        val sleepingAmbient = AmbientSnapshot(
            lightLux = 1.0f,        // pitch dark
            isFaceDown = true,
            hourOfDay = 2,           // 2 AM
        )
        val override = prosody.computeOverride(UserActivity.STILL, sleepingAmbient)
        // Should be refined to SLEEPING → whisper override
        assertThat(override.whisper).isTrue()
        assertThat(override.volumeMultiplier).isAtMost(0.4f)
    }

    @Test
    fun `STILL plus daytime ambient snapshot stays STILL`() {
        val daytimeAmbient = AmbientSnapshot(
            lightLux = 500f,
            isFaceDown = false,
            hourOfDay = 14,  // 2 PM
        )
        val override = prosody.computeOverride(UserActivity.STILL, daytimeAmbient)
        // No refinement → STILL → default-like override
        assertThat(override.whisper).isFalse()
        assertThat(override.volumeMultiplier).isWithin(0.001f).of(1.0f)
    }

    @Test
    fun `AmbientSnapshot isLikelySleeping requires dark late-night and face-down`() {
        // All three → sleeping
        val sleeping = AmbientSnapshot(lightLux = 5f, isFaceDown = true, hourOfDay = 3)
        assertThat(sleeping.isLikelySleeping()).isTrue()

        // Missing any one → not sleeping
        val bright = sleeping.copy(lightLux = 200f)
        assertThat(bright.isLikelySleeping()).isFalse()

        val daytime = sleeping.copy(hourOfDay = 10)
        assertThat(daytime.isLikelySleeping()).isFalse()

        val faceUp = sleeping.copy(isFaceDown = false)
        assertThat(faceUp.isLikelySleeping()).isFalse()
    }
}
