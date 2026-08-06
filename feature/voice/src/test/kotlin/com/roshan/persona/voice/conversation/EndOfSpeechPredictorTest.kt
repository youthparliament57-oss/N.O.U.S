// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.voice.conversation

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.voice.capture.AudioCapturePipeline
import org.junit.Test

// VOICE_FIX_002: EOS predictor validated

class EndOfSpeechPredictorTest {

    // ─── Fakes ─────────────────────────────────────────────────────────────

    private class FakeVapBackend(
        private val probability: Float = 0.9f,
        private val ready: Boolean = true,
    ) : VapBackend {
        override val isReady: Boolean = ready
        override fun predict(audioContext: ShortArray): Float = probability
        override fun release() = Unit
    }

    private fun audioContext(seconds: Int = 3): ShortArray =
        ShortArray(AudioCapturePipeline.SAMPLE_RATE * seconds) { 5000 }

    // ─── Tests ─────────────────────────────────────────────────────────────

    @Test
    fun `VAP predicts end-of-speech when probability above threshold`() {
        val predictor = EndOfSpeechPredictor(
            vapBackend = FakeVapBackend(probability = 0.9f),
            config = EndOfSpeechPredictor.Config(vapConfidenceThreshold = 0.7f),
        )
        val prediction = predictor.predictEndOfSpeech(audioContext(), AudioFeatures.UNKNOWN)
        assertThat(prediction.willEndWithin1Second).isTrue()
        assertThat(prediction.source).isEqualTo(PredictionSource.VAP)
        assertThat(prediction.confidence).isAtLeast(0.7f)
    }

    @Test
    fun `VAP does not predict end-of-speech when probability below threshold`() {
        val predictor = EndOfSpeechPredictor(
            vapBackend = FakeVapBackend(probability = 0.3f),
            config = EndOfSpeechPredictor.Config(vapConfidenceThreshold = 0.7f),
        )
        // Low VAP probability + no heuristic signals → no end prediction.
        val prediction = predictor.predictEndOfSpeech(
            audioContext(),
            AudioFeatures(intonationDropScore = 0f, rateSlowdownScore = 0f, hasFillerWord = false, silenceMs = 0),
        )
        assertThat(prediction.willEndWithin1Second).isFalse()
    }

    @Test
    fun `VAP borderline probability blends with heuristic`() {
        // VAP returns 0.5 (borderline) + strong silence signal → end predicted.
        val predictor = EndOfSpeechPredictor(
            vapBackend = FakeVapBackend(probability = 0.5f),
            config = EndOfSpeechPredictor.Config(vapConfidenceThreshold = 0.7f, heuristicThreshold = 0.6f),
        )
        val features = AudioFeatures(
            intonationDropScore = 0f,
            rateSlowdownScore = 0f,
            hasFillerWord = false,
            silenceMs = 500,  // max silence → score 1.0 × 0.3 = 0.3
        )
        val prediction = predictor.predictEndOfSpeech(audioContext(), features)
        // Heuristic score = 0.3 (silence only) → below 0.6, so willEnd = false
        // unless VAP borderline blending pushes it over.
        // Blended = 0.5 × 0.7 + 0.3 × 0.3 = 0.35 + 0.09 = 0.44 → still below threshold.
        assertThat(prediction.source).isEqualTo(PredictionSource.VAP)
    }

    @Test
    fun `falls back to heuristic when VAP backend is null`() {
        val predictor = EndOfSpeechPredictor(vapBackend = null)
        val features = AudioFeatures(
            intonationDropScore = 0.5f,
            rateSlowdownScore = 0.5f,
            hasFillerWord = true,
            silenceMs = 400,
        )
        val prediction = predictor.predictEndOfSpeech(audioContext(), features)
        assertThat(prediction.source).isEqualTo(PredictionSource.HEURISTIC)
        // Heuristic score = 0.5×0.3 + 0.5×0.2 + 1.0×0.2 + 0.8×0.3 = 0.15+0.1+0.2+0.24 = 0.69
        assertThat(prediction.confidence).isWithin(0.01f).of(0.69f)
        assertThat(prediction.willEndWithin1Second).isTrue()  // 0.69 > 0.6 threshold
    }

    @Test
    fun `falls back to heuristic when VAP backend not ready`() {
        val predictor = EndOfSpeechPredictor(
            vapBackend = FakeVapBackend(ready = false),
        )
        val prediction = predictor.predictEndOfSpeech(audioContext(), AudioFeatures.UNKNOWN)
        assertThat(prediction.source).isEqualTo(PredictionSource.HEURISTIC)
    }

    @Test
    fun `heuristic with no signals returns low score and no end prediction`() {
        val predictor = EndOfSpeechPredictor(vapBackend = null)
        val features = AudioFeatures(
            intonationDropScore = 0f,
            rateSlowdownScore = 0f,
            hasFillerWord = false,
            silenceMs = 0,
        )
        val prediction = predictor.predictEndOfSpeech(audioContext(), features)
        assertThat(prediction.confidence).isWithin(0.001f).of(0f)
        assertThat(prediction.willEndWithin1Second).isFalse()
    }

    @Test
    fun `heuristic with all max signals returns score 1 and predicts end`() {
        val predictor = EndOfSpeechPredictor(vapBackend = null)
        val features = AudioFeatures(
            intonationDropScore = 1f,
            rateSlowdownScore = 1f,
            hasFillerWord = true,
            silenceMs = 500,
        )
        val prediction = predictor.predictEndOfSpeech(audioContext(), features)
        // All signals maxed → 0.3 + 0.2 + 0.2 + 0.3 = 1.0
        assertThat(prediction.confidence).isWithin(0.001f).of(1f)
        assertThat(prediction.willEndWithin1Second).isTrue()
    }

    @Test
    fun `silenceScore maps silence duration correctly`() {
        val predictor = EndOfSpeechPredictor(
            vapBackend = null,
            config = EndOfSpeechPredictor.Config(silenceMsForEnd = 500),
        )
        assertThat(predictor.silenceScore(0)).isWithin(0.001f).of(0f)
        assertThat(predictor.silenceScore(250)).isWithin(0.001f).of(0.5f)
        assertThat(predictor.silenceScore(500)).isWithin(0.001f).of(1f)
        assertThat(predictor.silenceScore(1000)).isWithin(0.001f).of(1f)  // saturated
    }

    @Test
    fun `AudioFeatures validates bounds`() {
        try {
            AudioFeatures(intonationDropScore = 1.5f, rateSlowdownScore = 0f, hasFillerWord = false, silenceMs = 0)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("intonationDropScore")
        }
        try {
            AudioFeatures(intonationDropScore = 0f, rateSlowdownScore = 0f, hasFillerWord = false, silenceMs = -1)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("silenceMs")
        }
    }

    @Test
    fun `Config rejects out-of-bounds thresholds`() {
        try {
            EndOfSpeechPredictor.Config(vapConfidenceThreshold = 1.5f)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("vapConfidenceThreshold")
        }
        try {
            EndOfSpeechPredictor.Config(heuristicThreshold = -0.1f)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("heuristicThreshold")
        }
    }
}
