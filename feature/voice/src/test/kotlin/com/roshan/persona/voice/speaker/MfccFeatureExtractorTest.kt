// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.voice.speaker

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.voice.capture.AudioCapturePipeline
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

// AUTO_FIX_0184: [feature] MfccFeatureExtractorTest verified

// VOICE_FIX_013: MFCC extractor validated

class MfccFeatureExtractorTest {

    private val extractor = MfccFeatureExtractor()

    /** Generate `durationMs` of sine-wave audio at `freqHz`, amplitude 5000. */
    private fun sineWave(durationMs: Int, freqHz: Int, amplitude: Int = 5000): ShortArray {
        val n = (AudioCapturePipeline.SAMPLE_RATE * durationMs / 1000).toInt()
        return ShortArray(n) { i ->
            (amplitude * sin(2 * PI * freqHz * i / AudioCapturePipeline.SAMPLE_RATE)).toInt().toShort()
        }
    }

    @Test
    fun `extractFrames returns empty list for audio shorter than one frame`() {
        // 25 ms frame = 400 samples; 10 ms audio = 160 samples (too short).
        val tooShort = sineWave(durationMs = 10, freqHz = 220)
        assertThat(extractor.extractFrames(tooShort)).isEmpty()
    }

    @Test
    fun `extractFrames returns multiple frames for longer audio`() {
        // 100 ms audio → (100 - 25) / 10 + 1 = 8.5 → 8 frames.
        val audio = sineWave(durationMs = 100, freqHz = 220)
        val frames = extractor.extractFrames(audio)
        assertThat(frames).isNotEmpty()
        assertThat(frames.size).isAtLeast(7)
        // Each frame has numCoeffs dimensions.
        for (frame in frames) {
            assertThat(frame.size).isEqualTo(MfccFeatureExtractor.DEFAULT_NUM_COEFFS)
        }
    }

    @Test
    fun `extractEmbedding returns 2x numCoeffs dimensions`() {
        val audio = sineWave(durationMs = 100, freqHz = 220)
        val embedding = extractor.extractEmbedding(audio)
        // 13 mean + 13 variance = 26 dims.
        assertThat(embedding.size).isEqualTo(MfccFeatureExtractor.DEFAULT_NUM_COEFFS * 2)
    }

    @Test
    fun `extractEmbedding returns zero embedding for audio too short`() {
        val tooShort = sineWave(durationMs = 5, freqHz = 220)
        val embedding = extractor.extractEmbedding(tooShort)
        assertThat(embedding.size).isEqualTo(MfccFeatureExtractor.DEFAULT_NUM_COEFFS * 2)
        for (v in embedding) assertThat(v).isEqualTo(0f)
    }

    @Test
    fun `hammingWindow produces correct shape and bounds`() {
        val window = extractor.hammingWindow(8)
        assertThat(window.size).isEqualTo(8)
        // Hamming window: first and last samples should be ~0.08 (not zero).
        assertThat(window[0]).isWithin(0.01f).of(0.08f)
        assertThat(window[7]).isWithin(0.01f).of(0.08f)
        // Middle sample should be ~1.0 (peak).
        // For n=8, peak is at index 3.5; check index 3 or 4.
        assertThat(window[3]).isGreaterThan(0.9f)
        assertThat(window[4]).isGreaterThan(0.9f)
    }

    @Test
    fun `preEmphasize produces correct first sample and applies filter`() {
        val samples = ShortArray(5) { (it + 1).toShort() }  // [1, 2, 3, 4, 5]
        val out = extractor.preEmphasize(samples)
        // First sample = samples[0] / MAX_VALUE.
        assertThat(out[0]).isWithin(0.001f).of(1f / Short.MAX_VALUE)
        // Second sample = x[1]/MAX - α × x[0]/MAX = (2 - 0.97 × 1) / MAX
        val expected = (2f - 0.97f * 1f) / Short.MAX_VALUE
        assertThat(out[1]).isWithin(0.001f).of(expected)
    }

    @Test
    fun `fft computes correct DFT for a known signal`() {
        // A pure sine wave at frequency f should produce a peak in the FFT
        // at bin f. Test with a simple 2-bin signal.
        val n = 8
        val real = FloatArray(n)
        val imag = FloatArray(n)
        // Impulse at index 0 → constant in frequency domain.
        real[0] = 1f
        extractor.fft(real, imag, n)
        // All bins should be 1.0 (real) + 0.0 (imag).
        for (k in 0 until n) {
            assertThat(real[k]).isWithin(0.01f).of(1f)
            assertThat(imag[k]).isWithin(0.01f).of(0f)
        }
    }

    @Test
    fun `dct produces numCoeffs outputs from numFilters inputs`() {
        val logMel = FloatArray(MfccFeatureExtractor.DEFAULT_NUM_FILTERS) { it.toFloat() }
        val mfcc = extractor.dct(logMel)
        assertThat(mfcc.size).isEqualTo(MfccFeatureExtractor.DEFAULT_NUM_COEFFS)
    }

    @Test
    fun `MfccFeatureExtractor init rejects invalid config`() {
        try {
            MfccFeatureExtractor(numFilters = 4)  // < 8
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("numFilters")
        }
        try {
            MfccFeatureExtractor(numCoeffs = 50, numFilters = 26)  // coeffs > filters
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("numCoeffs")
        }
    }

    @Test
    fun `same audio produces same embedding (determinism)`() {
        val audio = sineWave(durationMs = 100, freqHz = 220)
        val emb1 = extractor.extractEmbedding(audio)
        val emb2 = extractor.extractEmbedding(audio)
        assertThat(emb1).usingTolerance(0.0001).containsExactly(emb2)
    }
}
