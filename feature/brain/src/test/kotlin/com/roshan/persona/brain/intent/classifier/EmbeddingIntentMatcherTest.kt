// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.brain.intent.classifier

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.intent.Intent
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0066: [feature] EmbeddingIntentMatcherTest verified

class EmbeddingIntentMatcherTest {

    // Use real TfIdfEmbedder (not hash-based FakeEmbedder)
    private val embedder = TfIdfEmbedder()
    private val matcher = EmbeddingIntentMatcher(embedder)

    @Test
    fun `exact match of canonical phrase returns match`() = runTest {
        val match = matcher.match("turn on the torch")

        assertThat(match).isNotNull()
        assertThat(match!!.intent).isInstanceOf(Intent.TorchOn::class.java)
        assertThat(match.rawSimilarity).isGreaterThan(0.3f)
    }

    @Test
    fun `semantic match — flashlight on matches torch on`() = runTest {
        // "flashlight" appears in canonical phrases for TorchOn
        // TF-IDF should give high similarity
        val match = matcher.match("flashlight on")

        assertThat(match).isNotNull()
        assertThat(match!!.intent).isInstanceOf(Intent.TorchOn::class.java)
    }

    @Test
    fun `semantic match — call mom matches Call intent`() = runTest {
        val match = matcher.match("call mom")

        assertThat(match).isNotNull()
        assertThat(match!!.intent).isInstanceOf(Intent.Call::class.java)
    }

    @Test
    fun `semantic match — send text matches SMS intent`() = runTest {
        val match = matcher.match("send a text message")

        assertThat(match).isNotNull()
        assertThat(match!!.intent).isInstanceOf(Intent.SendSms::class.java)
    }

    @Test
    fun `semantic match — search google matches SearchWeb intent`() = runTest {
        val match = matcher.match("google einstein")

        assertThat(match).isNotNull()
        assertThat(match!!.intent).isInstanceOf(Intent.SearchWeb::class.java)
    }

    @Test
    fun `semantic match — set alarm matches SetAlarm intent`() = runTest {
        val match = matcher.match("wake me up at 6")

        assertThat(match).isNotNull()
        assertThat(match!!.intent).isInstanceOf(Intent.SetAlarm::class.java)
    }

    @Test
    fun `non-matching input returns null`() = runTest {
        // Words not in vocabulary → zero vector → no similarity
        val match = matcher.match("xyzabc12345 random gibberish")

        // Should return null (no match above threshold)
        assertThat(match).isNull()
    }

    @Test
    fun `threshold is respected`() {
        // 0.3 for TF-IDF (temporary); will be 0.82 when Module 6 ships MiniLM
        assertThat(EmbeddingIntentMatcher.SIMILARITY_THRESHOLD).isEqualTo(0.3f)
    }

    @Test
    fun `canonical phrases exist for major intents`() {
        assertThat(EmbeddingIntentMatcher.CANONICAL_PHRASES).containsKey(Intent.TorchOn::class)
        assertThat(EmbeddingIntentMatcher.CANONICAL_PHRASES).containsKey(Intent.Call::class)
        assertThat(EmbeddingIntentMatcher.CANONICAL_PHRASES).containsKey(Intent.SearchWeb::class)
        assertThat(EmbeddingIntentMatcher.CANONICAL_PHRASES).containsKey(Intent.SetAlarm::class)
    }

    @Test
    fun `each intent has at least 5 canonical phrases`() {
        for ((_, phrases) in EmbeddingIntentMatcher.CANONICAL_PHRASES) {
            assertThat(phrases.size).isAtLeast(5)
        }
    }

    @Test
    fun `TfIdfEmbedder produces non-zero vectors for known words`() = runTest {
        val result = embedder.embed("call mom")
        val vector = result.getOrNull()!!

        // Vector should have non-zero values (not all zeros)
        val nonZeroCount = vector.count { it != 0f }
        assertThat(nonZeroCount).isGreaterThan(0)
    }

    @Test
    fun `TfIdfEmbedder produces zero vector for empty input`() = runTest {
        val result = embedder.embed("")
        val vector = result.getOrNull()!!

        // All zeros
        assertThat(vector.all { it == 0f }).isTrue()
    }

    @Test
    fun `TfIdfEmbedder produces similar vectors for similar texts`() = runTest {
        val v1 = embedder.embed("call mom").getOrNull()!!
        val v2 = embedder.embed("call my mother").getOrNull()!!

        // Should have some overlap (both contain "call")
        val dotProduct = v1.indices.sumOf { (v1[it] * v2[it]).toDouble() }
        assertThat(dotProduct).isGreaterThan(0.0)
    }
}
