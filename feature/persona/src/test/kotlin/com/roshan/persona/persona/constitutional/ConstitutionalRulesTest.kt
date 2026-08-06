// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.persona.constitutional

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.persona.core.BuiltInPersonas
import org.junit.Test

// AUTO_FIX_0103: [feature] ConstitutionalRulesTest verified

class ConstitutionalRulesTest {

    @Test
    fun `CONSTITUTIONAL_RULE is non-empty and contains the ban`() {
        assertThat(ConstitutionalRules.CONSTITUTIONAL_RULE).isNotEmpty()
        assertThat(ConstitutionalRules.CONSTITUTIONAL_RULE).contains("BANNED PHRASES")
        assertThat(ConstitutionalRules.CONSTITUTIONAL_RULE).contains("Digital Companion")
    }

    @Test
    fun `BANNED_PHRASES list is non-empty`() {
        assertThat(ConstitutionalRules.BANNED_PHRASES).isNotEmpty()
        assertThat(ConstitutionalRules.BANNED_PHRASES.size).isAtLeast(10)
    }

    @Test
    fun `BANNED_PHRASES contains key AI disclaimers`() {
        assertThat(ConstitutionalRules.BANNED_PHRASES).contains("as an ai language model")
        assertThat(ConstitutionalRules.BANNED_PHRASES).contains("i am an ai")
        assertThat(ConstitutionalRules.BANNED_PHRASES).contains("i don't have feelings")
        assertThat(ConstitutionalRules.BANNED_PHRASES).contains("i'm just software")
    }

    @Test
    fun `buildSystemPrompt prepends constitutional rule to persona prompt`() {
        val personaPrompt = "You are Atlas — your steadfast companion."
        val result = ConstitutionalRules.buildSystemPrompt(personaPrompt)
        assertThat(result).startsWith(ConstitutionalRules.CONSTITUTIONAL_RULE)
        assertThat(result).contains(personaPrompt)
    }

    @Test
    fun `containsBannedPhrase detects AI disclaimer case-insensitive`() {
        assertThat(ConstitutionalRules.containsBannedPhrase("As an AI language model, I...")).isTrue()
        assertThat(ConstitutionalRules.containsBannedPhrase("I am an AI assistant.")).isTrue()
        assertThat(ConstitutionalRules.containsBannedPhrase("I DON'T HAVE FEELINGS but...")).isTrue()
    }

    @Test
    fun `containsBannedPhrase returns false for clean response`() {
        assertThat(ConstitutionalRules.containsBannedPhrase("I'm NOUS — your companion.")).isFalse()
        assertThat(ConstitutionalRules.containsBannedPhrase("Right away, sir.")).isFalse()
        assertThat(ConstitutionalRules.containsBannedPhrase("")).isFalse()
    }

    @Test
    fun `findFirstBannedPhrase returns the matching phrase`() {
        val phrase = ConstitutionalRules.findFirstBannedPhrase("Well, as an AI language model, I think...")
        assertThat(phrase).isEqualTo("as an ai language model")
    }

    @Test
    fun `findFirstBannedPhrase returns null for clean response`() {
        assertThat(ConstitutionalRules.findFirstBannedPhrase("I'm NOUS — your companion.")).isNull()
    }
}

class SystemPromptBuilderTest {

    @Test
    fun `build prepends constitutional rule`() {
        val prompt = SystemPromptBuilder.build(BuiltInPersonas.ATLAS)
        assertThat(prompt).startsWith(ConstitutionalRules.CONSTITUTIONAL_RULE)
    }

    @Test
    fun `build includes persona display name`() {
        val prompt = SystemPromptBuilder.build(BuiltInPersonas.ATLAS)
        assertThat(prompt).contains("Atlas")
    }

    @Test
    fun `build includes personality description`() {
        val prompt = SystemPromptBuilder.build(BuiltInPersonas.ONYX)
        // Onyx: extraversion=0.1 → "reserved and quiet"
        assertThat(prompt).contains("reserved")
    }

    @Test
    fun `build includes catchphrases for built-in personas`() {
        val prompt = SystemPromptBuilder.build(BuiltInPersonas.ATLAS)
        assertThat(prompt).contains("Indeed.")
        assertThat(prompt).contains("Right away.")
    }

    @Test
    fun `build includes tone and verbosity`() {
        val prompt = SystemPromptBuilder.build(BuiltInPersonas.VANGUARD)
        assertThat(prompt).contains("tactical")
        assertThat(prompt).contains("terse")
    }

    @Test
    fun `build includes allowsHappy constraint`() {
        val onyxPrompt = SystemPromptBuilder.build(BuiltInPersonas.ONYX)
        assertThat(onyxPrompt).contains("do not express happiness")
        val atlasPrompt = SystemPromptBuilder.build(BuiltInPersonas.ATLAS)
        assertThat(atlasPrompt).contains("express happiness")
    }

    @Test
    fun `build includes allowsUrgent constraint`() {
        val sagePrompt = SystemPromptBuilder.build(BuiltInPersonas.SAGE)
        assertThat(sagePrompt).contains("avoid urgency")
    }

    @Test
    fun `build includes placeholders for BrainPreProcessor`() {
        val prompt = SystemPromptBuilder.build(BuiltInPersonas.ATLAS)
        assertThat(prompt).contains("{{user_name}}")
        assertThat(prompt).contains("{{time}}")
        assertThat(prompt).contains("{{location}}")
    }

    @Test
    fun `build includes proactive style`() {
        val prompt = SystemPromptBuilder.build(BuiltInPersonas.ARIA)
        assertThat(prompt).contains("Enthusiastic")
    }
}
