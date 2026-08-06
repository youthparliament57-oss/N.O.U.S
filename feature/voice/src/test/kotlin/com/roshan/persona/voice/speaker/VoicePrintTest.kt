// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.voice.speaker

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// AUTO_FIX_0183: [feature] VoicePrintTest verified

// VOICE_FIX_014: Voice print secure

class VoicePrintTest {

    @Test
    fun `VoicePrint requires matching embedding size and embeddingDim`() {
        try {
            VoicePrint(
                speakerId = "u1",
                embedding = FloatArray(10),
                embeddingDim = 192,  // mismatch
                displayName = "User",
            )
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("embedding size")
        }
    }

    @Test
    fun `VoicePrint rejects blank speakerId and displayName`() {
        try {
            VoicePrint(
                speakerId = "",
                embedding = FloatArray(192),
                embeddingDim = 192,
                displayName = "User",
            )
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("speakerId")
        }
        try {
            VoicePrint(
                speakerId = "u1",
                embedding = FloatArray(192),
                embeddingDim = 192,
                displayName = "   ",
            )
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("displayName")
        }
    }

    @Test
    fun `VoicePrint equals compares embedding content not identity`() {
        val emb = FloatArray(192) { it.toFloat() }
        val p1 = VoicePrint("u1", emb, 192, "User")
        val p2 = VoicePrint("u1", emb.copyOf(), 192, "User")
        assertThat(p1).isEqualTo(p2)
        assertThat(p1.hashCode()).isEqualTo(p2.hashCode())
    }

    @Test
    fun `SpeakerConfidence fromSimilarity classifies correctly`() {
        assertThat(SpeakerConfidence.fromSimilarity(0.9f)).isEqualTo(SpeakerConfidence.HIGH)
        assertThat(SpeakerConfidence.fromSimilarity(0.85f)).isEqualTo(SpeakerConfidence.HIGH)
        assertThat(SpeakerConfidence.fromSimilarity(0.75f)).isEqualTo(SpeakerConfidence.MEDIUM)
        assertThat(SpeakerConfidence.fromSimilarity(0.7f)).isEqualTo(SpeakerConfidence.MEDIUM)
        assertThat(SpeakerConfidence.fromSimilarity(0.5f)).isEqualTo(SpeakerConfidence.LOW)
        assertThat(SpeakerConfidence.fromSimilarity(0.0f)).isEqualTo(SpeakerConfidence.LOW)
    }

    @Test
    fun `SpeakerConfig rejects invalid thresholds`() {
        try {
            SpeakerConfig(matchThreshold = 1.5f)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("matchThreshold")
        }
        // highConfidenceThreshold must be >= matchThreshold.
        try {
            SpeakerConfig(matchThreshold = 0.8f, highConfidenceThreshold = 0.7f)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("highConfidenceThreshold")
        }
    }

    @Test
    fun `SpeakerConfig enforces multi-user limit of 5 speakers`() {
        assertThat(SpeakerConfig().maxEnrolledSpeakers).isEqualTo(5)
        try {
            SpeakerConfig(maxEnrolledSpeakers = 0)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("maxEnrolledSpeakers")
        }
    }

    @Test
    fun `InMemoryVoicePrintStore save-load-delete round-trips correctly`() = kotlinx.coroutines.test.runTest {
        val store = InMemoryVoicePrintStore()
        val print = VoicePrint(
            speakerId = "u1",
            embedding = FloatArray(192) { it.toFloat() },
            embeddingDim = 192,
            displayName = "Roshan",
        )
        store.save(print)

        assertThat(store.count()).isEqualTo(1)
        assertThat(store.load("u1")?.displayName).isEqualTo("Roshan")
        assertThat(store.loadAll()).hasSize(1)
        assertThat(store.load("nonexistent")).isNull()

        assertThat(store.delete("u1")).isTrue()
        assertThat(store.delete("u1")).isFalse()  // already deleted
        assertThat(store.count()).isEqualTo(0)
    }

    @Test
    fun `InMemoryVoicePrintStore deleteAll returns count and clears store`() = kotlinx.coroutines.test.runTest {
        val store = InMemoryVoicePrintStore()
        store.save(VoicePrint("u1", FloatArray(192), 192, "A"))
        store.save(VoicePrint("u2", FloatArray(192), 192, "B"))

        val deleted = store.deleteAll()
        assertThat(deleted).isEqualTo(2)
        assertThat(store.count()).isEqualTo(0)
    }
}
