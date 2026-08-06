// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.voice.emotion

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.voice.capture.AudioCapturePipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

// AUTO_FIX_0158: [feature] SpeechEmotionRecognizerTest verified

// VOICE_FIX_039: Speech emotion recognizer ok

class SpeechEmotionRecognizerTest {

    // ─── Fakes ─────────────────────────────────────────────────────────────

    private class FakeWavlmBackend(
        private val scores: Map<UserEmotionType, Float>,
        private val ready: Boolean = true,
    ) : WavlmSerBackend {
        override val isReady: Boolean = ready
        override fun classify(audio: ShortArray): Map<UserEmotionType, Float> = scores
        override fun release() = Unit
    }

    private fun audio(durationSec: Int, freqHz: Int = 220, amplitude: Int = 5000): ShortArray {
        val n = AudioCapturePipeline.SAMPLE_RATE * durationSec
        return ShortArray(n) { i ->
            (amplitude * sin(2 * PI * freqHz * i / AudioCapturePipeline.SAMPLE_RATE)).toInt().toShort()
        }
    }

    private fun makeRecognizer(
        wavlm: WavlmSerBackend? = null,
        config: SerConfig = SerConfig(),
    ): SpeechEmotionRecognizer = SpeechEmotionRecognizer(
        wavlmBackend = wavlm,
        config = config,
        inferenceDispatcher = Dispatchers.Unconfined,
    )

    // ─── Tests ─────────────────────────────────────────────────────────────

    @Test
    fun `recognize returns UNKNOWN for audio shorter than minAudioSeconds`() = runTest {
        val recognizer = makeRecognizer()
        val short = audio(durationSec = 0)  // empty
        val result = recognizer.recognize(short)
        assertThat(result).isEqualTo(UserEmotion.UNKNOWN)
    }

    @Test
    fun `recognize uses WavLM when backend is available and ready`() = runTest {
        val wavlm = FakeWavlmBackend(
            scores = mapOf(
                UserEmotionType.ANGRY to 0.8f,
                UserEmotionType.NEUTRAL to 0.2f,
            ),
        )
        val recognizer = makeRecognizer(wavlm = wavlm)
        val result = recognizer.recognize(audio(2))
        assertThat(result.source).isEqualTo(EmotionDetectionSource.WAVLM)
        assertThat(result.primary).isEqualTo(UserEmotionType.ANGRY)
    }

    @Test
    fun `recognize normalizes WavLM scores via fromScores`() = runTest {
        // Raw scores not summing to 1 — should be normalized.
        val wavlm = FakeWavlmBackend(
            scores = mapOf(
                UserEmotionType.HAPPY to 4f,
                UserEmotionType.NEUTRAL to 1f,
            ),
        )
        val recognizer = makeRecognizer(wavlm = wavlm)
        val result = recognizer.recognize(audio(2))
        // After normalization: HAPPY=0.8, NEUTRAL=0.2.
        assertThat(result.primary).isEqualTo(UserEmotionType.HAPPY)
        assertThat(result.confidence).isWithin(0.001f).of(0.8f)
        assertThat(result.allScores.values.sum()).isWithin(0.001f).of(1.0f)
    }

    @Test
    fun `recognize falls back to heuristic when WavLM is null`() = runTest {
        val recognizer = makeRecognizer(wavlm = null)
        val result = recognizer.recognize(audio(2))
        assertThat(result.source).isEqualTo(EmotionDetectionSource.HEURISTIC)
    }

    @Test
    fun `recognize falls back to heuristic when WavLM is not ready`() = runTest {
        val wavlm = FakeWavlmBackend(scores = emptyMap(), ready = false)
        val recognizer = makeRecognizer(wavlm = wavlm)
        val result = recognizer.recognize(audio(2))
        assertThat(result.source).isEqualTo(EmotionDetectionSource.HEURISTIC)
    }

    @Test
    fun `normalizeScores produces values summing to 1`() {
        val recognizer = makeRecognizer()
        val scores = mapOf(
            UserEmotionType.HAPPY to 3f,
            UserEmotionType.SAD to 1f,
            UserEmotionType.ANGRY to 6f,
        )
        val normalized = recognizer.normalizeScores(scores)
        assertThat(normalized.values.sum()).isWithin(0.001f).of(1.0f)
        assertThat(normalized[UserEmotionType.ANGRY]).isWithin(0.001f).of(0.6f)
        assertThat(normalized[UserEmotionType.HAPPY]).isWithin(0.001f).of(0.3f)
    }

    @Test
    fun `normalizeScores returns uniform when all values are zero`() {
        val recognizer = makeRecognizer()
        val scores = mapOf(
            UserEmotionType.HAPPY to 0f,
            UserEmotionType.SAD to 0f,
            UserEmotionType.ANGRY to 0f,
        )
        val normalized = recognizer.normalizeScores(scores)
        // Uniform: each emotion gets 1/3.
        for (v in normalized.values) {
            assertThat(v).isWithin(0.001f).of(1f / 3)
        }
    }

    @Test
    fun `runHeuristic returns all 7 emotions with non-negative scores`() {
        val recognizer = makeRecognizer()
        val scores = recognizer.runHeuristic(audio(2))
        assertThat(scores).hasSize(7)
        for ((_, v) in scores) {
            assertThat(v).isAtLeast(0f)
        }
        // Scores should sum to ~1.0 after normalization.
        assertThat(scores.values.sum()).isWithin(0.01f).of(1.0f)
    }

    @Test
    fun `computeRmsEnergy returns 0 for silence`() {
        val recognizer = makeRecognizer()
        val silence = ShortArray(1600) { 0 }
        assertThat(recognizer.computeRmsEnergy(silence)).isEqualTo(0f)
    }

    @Test
    fun `computeRmsEnergy returns positive for tone`() {
        val recognizer = makeRecognizer()
        val energy = recognizer.computeRmsEnergy(audio(1))
        assertThat(energy).isGreaterThan(0f)
    }

    @Test
    fun `computePitchStats returns non-negative mean and variability`() {
        val recognizer = makeRecognizer()
        val stats = recognizer.computePitchStats(audio(1, freqHz = 220))
        assertThat(stats.meanHz).isAtLeast(0f)
        assertThat(stats.variability).isAtLeast(0f)
    }

    @Test
    fun `computeSpeakingRate returns non-negative value`() {
        val recognizer = makeRecognizer()
        val rate = recognizer.computeSpeakingRate(audio(2))
        assertThat(rate).isAtLeast(0f)
    }

    @Test
    fun `WavLM exception is caught and falls back to heuristic`() = runTest {
        val crashingWavlm = object : WavlmSerBackend {
            override val isReady: Boolean = true
            override fun classify(audio: ShortArray): Map<UserEmotionType, Float> = error("ONNX crash")
            override fun release() = Unit
        }
        val recognizer = makeRecognizer(wavlm = crashingWavlm)
        val result = recognizer.recognize(audio(2))
        // Should not throw — should fall back to heuristic.
        assertThat(result.source).isEqualTo(EmotionDetectionSource.HEURISTIC)
    }
}
