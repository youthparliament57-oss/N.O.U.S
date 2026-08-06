// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.voice.tts

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// AUTO_FIX_0174: [feature] VoicePersonaTest verified

// VOICE_FIX_023: Voice persona validated

class VoicePersonaTest {

    @Test
    fun `all 9 personas are defined`() {
        val expected = setOf(
            "JARVIS", "FRIDAY", "ULTRON", "KAREN", "VERONICA",
            "EDITH", "TADASHI", "HOPE", "PROWLER",
        )
        val actual = VoicePersona.entries.map { it.name }.toSet()
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun `JARVIS has lower pitch than FRIDAY`() {
        assertThat(VoicePersona.JARVIS.basePitch).isLessThan(VoicePersona.FRIDAY.basePitch)
    }

    @Test
    fun `ULTRON cannot express HAPPY`() {
        assertThat(VoicePersona.ULTRON.canExpress(Emotion.HAPPY)).isFalse()
        assertThat(VoicePersona.ULTRON.canExpress(Emotion.ANGRY)).isTrue()
    }

    @Test
    fun `EDITH cannot express HAPPY but can express URGENT`() {
        assertThat(VoicePersona.EDITH.allowsHappy).isFalse()
        assertThat(VoicePersona.EDITH.allowsUrgent).isTrue()
    }

    @Test
    fun `all persona base pitches and rates are within TTS-safe bounds`() {
        for (persona in VoicePersona.entries) {
            assertThat(persona.basePitch).isAtLeast(0.5f)
            assertThat(persona.basePitch).isAtMost(2.0f)
            assertThat(persona.baseRate).isAtLeast(0.5f)
            assertThat(persona.baseRate).isAtMost(2.0f)
        }
    }

    @Test
    fun `every persona has a unique display name`() {
        val names = VoicePersona.entries.map { it.displayName }
        assertThat(names).containsNoDuplicates()
    }

    @Test
    fun `every persona has a unique accent`() {
        // Multiple personas can share an accent category in principle, but
        // for the v1 9 personas each is intentionally distinct.
        val accents = VoicePersona.entries.map { it.accent }
        assertThat(accents).containsNoDuplicates()
    }
}
