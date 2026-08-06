// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.persona.core

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.persona.Tone
import com.roshan.persona.brain.persona.Verbosity
import com.roshan.persona.brain.persona.Vocabulary
import com.roshan.persona.voice.tts.VoicePersona
import org.junit.Test

// AUTO_FIX_0105: [feature] PersonaProfileTest verified

class PersonaProfileTest {

    @Test
    fun `BigFivePersonality validates all traits in 0-1 range`() {
        BigFivePersonality(0.5f, 0.5f, 0.5f, 0.5f, 0.5f)  // valid
        try {
            BigFivePersonality(-0.1f, 0.5f, 0.5f, 0.5f, 0.5f)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("openness")
        }
        try {
            BigFivePersonality(0.5f, 1.5f, 0.5f, 0.5f, 0.5f)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("conscientiousness")
        }
    }

    @Test
    fun `BigFivePersonality toDescription returns non-empty for extreme values`() {
        val extreme = BigFivePersonality(0.9f, 0.9f, 0.9f, 0.9f, 0.9f)
        val desc = extreme.toDescription()
        assertThat(desc).contains("curious")
        assertThat(desc).contains("organized")
        assertThat(desc).contains("outgoing")
        assertThat(desc).contains("warm")
        assertThat(desc).contains("sensitive")
    }

    @Test
    fun `BigFivePersonality toDescription returns balanced for mid values`() {
        val mid = BigFivePersonality(0.5f, 0.5f, 0.5f, 0.5f, 0.5f)
        val desc = mid.toDescription()
        assertThat(desc).contains("balanced")
    }

    @Test
    fun `LlmHyperparams validates bounds`() {
        LlmHyperparams(temperature = 0.5f)  // valid
        try {
            LlmHyperparams(temperature = -0.1f)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("temperature")
        }
        try {
            LlmHyperparams(temperature = 0.5f, topP = 1.5f)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("topP")
        }
    }

    @Test
    fun `PersonaColorTheme validates hex color format`() {
        PersonaColorTheme("#0066CC", "#00C6FF", "#0A0E1A")  // valid
        try {
            PersonaColorTheme("blue", "#00C6FF", "#0A0E1A")
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("primary")
        }
    }

    @Test
    fun `PersonaProfile validates non-blank fields`() {
        try {
            BuiltInPersonas.ATLAS.copy(id = "")
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("id")
        }
    }

    @Test
    fun `PersonaArchetype has 9 distinct values`() {
        assertThat(PersonaArchetype.entries).hasSize(9)
        assertThat(PersonaArchetype.entries.map { it.name }.toSet()).containsExactly(
            "BUTLER", "COMPANION", "OPERATOR", "MENTOR", "PEER",
            "TACTICAL", "CHEERLEADER", "STEALTH", "RESCUER",
        )
    }

    @Test
    fun `ProactiveStyle has 9 distinct values`() {
        assertThat(ProactiveStyle.entries).hasSize(9)
    }

    @Test
    fun `MemoryIsolation has 2 values`() {
        assertThat(MemoryIsolation.entries).hasSize(2)
    }

    @Test
    fun `toBrainPersona converts PersonaProfile to Brain Persona`() {
        val brainPersona = BuiltInPersonas.ATLAS.toBrainPersona()
        assertThat(brainPersona.id).isEqualTo("atlas")
        assertThat(brainPersona.displayName).isEqualTo("Atlas")
        assertThat(brainPersona.responseStyle.tone).isEqualTo(Tone.WITTY)
        assertThat(brainPersona.voiceProfile?.voiceId).isEqualTo("ATLAS")
        assertThat(brainPersona.voiceProfile?.pitch).isEqualTo(0.90f)
        assertThat(brainPersona.isDefault).isTrue()
    }

    @Test
    fun `toBrainPersona prosody reflects personality`() {
        // Atlas: extraversion=0.4, neuroticism=0.1, allowsHappy=true
        val brainPersona = BuiltInPersonas.ATLAS.toBrainPersona()
        val prosody = brainPersona.voiceProfile!!.prosody
        assertThat(prosody.happiness).isEqualTo(0.4f)  // extraversion
        assertThat(prosody.sadness).isWithin(0.01f).of(0.05f)  // neuroticism * 0.5
        assertThat(prosody.calmness).isWithin(0.01f).of(0.9f)  // 1 - neuroticism

        // Onyx: allowsHappy=false → happiness=0
        val onyxBrain = BuiltInPersonas.ONYX.toBrainPersona()
        assertThat(onyxBrian.voiceProfile!!.prosody.happiness).isEqualTo(0f)
    }
}

class BuiltInPersonasTest {

    @Test
    fun `ALL contains exactly 9 personas`() {
        assertThat(BuiltInPersonas.ALL).hasSize(9)
    }

    @Test
    fun `ALL personas have unique IDs`() {
        val ids = BuiltInPersonas.ALL.map { it.id }
        assertThat(ids).containsNoDuplicates()
    }

    @Test
    fun `ALL personas have unique displayNames`() {
        val names = BuiltInPersonas.ALL.map { it.displayName }
        assertThat(names).containsNoDuplicates()
    }

    @Test
    fun `ALL personas have unique VoicePersona mappings`() {
        val voices = BuiltInPersonas.ALL.map { it.voicePersona }
        assertThat(voices).containsNoDuplicates()
    }

    @Test
    fun `ALL personas have unique color themes`() {
        val colors = BuiltInPersonas.ALL.map { it.colorTheme.primary }
        assertThat(colors).containsNoDuplicates()
    }

    @Test
    fun `exactly one persona isDefault`() {
        val defaults = BuiltInPersonas.ALL.filter { it.isDefault }
        assertThat(defaults).hasSize(1)
        assertThat(defaults[0].id).isEqualTo("atlas")
    }

    @Test
    fun `get by ID returns correct persona`() {
        assertThat(BuiltInPersonas.get("atlas")?.displayName).isEqualTo("Atlas")
        assertThat(BuiltInPersonas.get("onyx")?.displayName).isEqualTo("Onyx")
        assertThat(BuiltInPersonas.get("nonexistent")).isNull()
    }

    @Test
    fun `findByName matches exact case-insensitive`() {
        assertThat(BuiltInPersonas.findByName("Atlas")?.id).isEqualTo("atlas")
        assertThat(BuiltInPersonas.findByName("NOVA")?.id).isEqualTo("nova")
        assertThat(BuiltInPersonas.findByName("onyx")?.id).isEqualTo("onyx")
    }

    @Test
    fun `findByName matches contains`() {
        assertThat(BuiltInPersonas.findByName("Atl")?.id).isEqualTo("atlas")
        assertThat(BuiltInPersonas.findByName("zen")?.id).isEqualTo("zenith")
    }

    @Test
    fun `findByName returns null for no match`() {
        assertThat(BuiltInPersonas.findByName("Nonexistent")).isNull()
    }

    @Test
    fun `DEFAULT is Atlas`() {
        assertThat(BuiltInPersonas.DEFAULT.id).isEqualTo("atlas")
    }

    @Test
    fun `cold personas have allowsHappy false`() {
        assertThat(BuiltInPersonas.ONYX.allowsHappy).isFalse()
        assertThat(BuiltInPersonas.VANGUARD.allowsHappy).isFalse()
        assertThat(BuiltInPersonas.WRAITH.allowsHappy).isFalse()
    }

    @Test
    fun `gentle personas have allowsUrgent false`() {
        assertThat(BuiltInPersonas.SAGE.allowsUrgent).isFalse()
    }

    @Test
    fun `temperature correlates with archetype — cold personas low temp`() {
        assertThat(BuiltInPersonas.ONYX.llmHyperparams.temperature).isAtMost(0.2f)
        assertThat(BuiltInPersonas.VANGUARD.llmHyperparams.temperature).isAtMost(0.2f)
        assertThat(BuiltInPersonas.WRAITH.llmHyperparams.temperature).isAtMost(0.2f)
    }

    @Test
    fun `temperature correlates with archetype — cheerful personas high temp`() {
        assertThat(BuiltInPersonas.ARIA.llmHyperparams.temperature).isAtLeast(0.9f)
        assertThat(BuiltInPersonas.ECHO.llmHyperparams.temperature).isAtLeast(0.7f)
    }

    @Test
    fun `all built-in personas have at least one catchphrase`() {
        for (persona in BuiltInPersonas.ALL) {
            assertThat(persona.catchphrases).isNotEmpty()
        }
    }

    @Test
    fun `all built-in personas are marked isBuiltIn`() {
        for (persona in BuiltInPersonas.ALL) {
            assertThat(persona.isBuiltIn).isTrue()
        }
    }
}
