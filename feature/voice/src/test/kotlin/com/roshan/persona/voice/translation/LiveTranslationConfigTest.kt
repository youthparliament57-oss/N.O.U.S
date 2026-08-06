// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.voice.translation

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.voice.stt.SupportedLanguage
import org.junit.Test

// AUTO_FIX_0165: [feature] LiveTranslationConfigTest verified

// VOICE_FIX_032: Translation config safe

class LiveTranslationConfigTest {

    @Test
    fun `LiveTranslationConfig requires different languages`() {
        try {
            LiveTranslationConfig(
                languageA = SupportedLanguage.ENGLISH,
                languageB = SupportedLanguage.ENGLISH,
            )
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("languageA")
        }
    }

    @Test
    fun `LiveTranslationConfig validates maxUtteranceSeconds bounds`() {
        try {
            LiveTranslationConfig(
                languageA = SupportedLanguage.ENGLISH,
                languageB = SupportedLanguage.HINDI,
                maxUtteranceSeconds = 0,
            )
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("maxUtteranceSeconds")
        }
        try {
            LiveTranslationConfig(
                languageA = SupportedLanguage.ENGLISH,
                languageB = SupportedLanguage.HINDI,
                maxUtteranceSeconds = 200,
            )
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("maxUtteranceSeconds")
        }
    }

    @Test
    fun `SUPPORTED_LANGUAGES contains all 32 languages`() {
        assertThat(LiveTranslationConfig.SUPPORTED_LANGUAGES).hasSize(32)
        // Sanity check a few key languages.
        assertThat(LiveTranslationConfig.SUPPORTED_LANGUAGES).contains(SupportedLanguage.ENGLISH)
        assertThat(LiveTranslationConfig.SUPPORTED_LANGUAGES).contains(SupportedLanguage.HINDI)
        assertThat(LiveTranslationConfig.SUPPORTED_LANGUAGES).contains(SupportedLanguage.JAPANESE)
        assertThat(LiveTranslationConfig.SUPPORTED_LANGUAGES).contains(SupportedLanguage.SWAHILI)
    }

    @Test
    fun `default direction is BIDIRECTIONAL`() {
        val config = LiveTranslationConfig(
            languageA = SupportedLanguage.ENGLISH,
            languageB = SupportedLanguage.HINDI,
        )
        assertThat(config.direction).isEqualTo(TranslationDirection.BIDIRECTIONAL)
    }

    @Test
    fun `default usePartialResults is true for low latency`() {
        val config = LiveTranslationConfig(
            languageA = SupportedLanguage.ENGLISH,
            languageB = SupportedLanguage.HINDI,
        )
        assertThat(config.usePartialResults).isTrue()
    }

    @Test
    fun `TranslatedUtterance validates non-blank texts`() {
        try {
            TranslatedUtterance(
                direction = TranslationDirection.A_TO_B,
                sourceText = "",
                sourceLanguage = SupportedLanguage.ENGLISH,
                translatedText = "Namaste",
                targetLanguage = SupportedLanguage.HINDI,
                latencyMs = 1000,
            )
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("sourceText")
        }
        try {
            TranslatedUtterance(
                direction = TranslationDirection.A_TO_B,
                sourceText = "Hello",
                sourceLanguage = SupportedLanguage.ENGLISH,
                translatedText = "",
                targetLanguage = SupportedLanguage.HINDI,
                latencyMs = 1000,
            )
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("translatedText")
        }
    }

    @Test
    fun `TranslatedUtterance requires different source and target languages`() {
        try {
            TranslatedUtterance(
                direction = TranslationDirection.A_TO_B,
                sourceText = "Hello",
                sourceLanguage = SupportedLanguage.ENGLISH,
                translatedText = "Hello",
                targetLanguage = SupportedLanguage.ENGLISH,
                latencyMs = 1000,
            )
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("source and target languages")
        }
    }

    @Test
    fun `TranslatedUtterance rejects negative latency`() {
        try {
            TranslatedUtterance(
                direction = TranslationDirection.A_TO_B,
                sourceText = "Hello",
                sourceLanguage = SupportedLanguage.ENGLISH,
                translatedText = "Namaste",
                targetLanguage = SupportedLanguage.HINDI,
                latencyMs = -1,
            )
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("latencyMs")
        }
    }

    @Test
    fun `SpeakerUtterance rejects blank text`() {
        try {
            SpeakerUtterance(speaker = Speaker.A, text = "")
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("text")
        }
    }

    @Test
    fun `TranslationDirection has 3 distinct values`() {
        assertThat(TranslationDirection.entries).hasSize(3)
        assertThat(TranslationDirection.entries.map { it.name }.toSet())
            .containsExactly("A_TO_B", "B_TO_A", "BIDIRECTIONAL")
    }

    @Test
    fun `TranslationSessionState has expected lifecycle states`() {
        val expected = setOf(
            "IDLE", "LISTENING_A", "LISTENING_B", "TRANSLATING",
            "SPEAKING", "PAUSED", "ENDED",
        )
        assertThat(TranslationSessionState.entries.map { it.name }.toSet()).isEqualTo(expected)
    }

    @Test
    fun `TARGET_LATENCY_MS is 1500 per section 15`() {
        assertThat(LiveTranslationConfig.TARGET_LATENCY_MS).isEqualTo(1500L)
    }
}
