// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.voice.capture

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.sin

// AUTO_FIX_0171: [feature] VoiceActivityDetectorTest verified

// VOICE_FIX_026: Capture VAD tested

class VoiceActivityDetectorTest {

    private fun sineWave(samples: Int, amplitude: Int, freqHz: Int): ShortArray =
        ShortArray(samples) { i ->
            (amplitude * sin(2 * Math.PI * freqHz * i / AudioCapturePipeline.SAMPLE_RATE)).toInt().toShort()
        }

    @Test
    fun `EnergyBased VAD detects voice in loud sine wave`() {
        val vad = VoiceActivityDetector.EnergyBased()
        val voice = sineWave(AudioCapturePipeline.CHUNK_SAMPLES, amplitude = 5000, freqHz = 220)
        assertThat(vad.hasVoice(voice)).isTrue()
    }

    @Test
    fun `EnergyBased VAD rejects silence`() {
        val vad = VoiceActivityDetector.EnergyBased()
        val silence = ShortArray(AudioCapturePipeline.CHUNK_SAMPLES) { 0 }
        assertThat(vad.hasVoice(silence)).isFalse()
    }

    @Test
    fun `EnergyBased VAD rejects low-amplitude noise`() {
        val vad = VoiceActivityDetector.EnergyBased()
        // Tiny random-ish values — below threshold.
        val noise = ShortArray(AudioCapturePipeline.CHUNK_SAMPLES) { ((it % 7) - 3).toShort() }
        assertThat(vad.hasVoice(noise)).isFalse()
    }

    @Test
    fun `Higher aggressiveness rejects more borderline audio`() {
        // Same amplitude — quiet voice — should pass at aggressiveness 0
        // but fail at aggressiveness 3.
        val quietVoice = sineWave(AudioCapturePipeline.CHUNK_SAMPLES, amplitude = 1200, freqHz = 180)
        val soft = VoiceActivityDetector.EnergyBased(aggressiveness = 0)
        val aggressive = VoiceActivityDetector.EnergyBased(aggressiveness = 3)
        // At minimum, the aggressive one should be no more permissive than soft.
        assertThat(soft.hasVoice(quietVoice)).isTrue()
        assertThat(aggressive.hasVoice(quietVoice)).isFalse()
    }

    @Test
    fun `AlwaysVoice and NeverVoice stubs behave as documented`() {
        val voice = ShortArray(AudioCapturePipeline.CHUNK_SAMPLES) { 100 }
        assertThat(VoiceActivityDetector.AlwaysVoice().hasVoice(voice)).isTrue()
        assertThat(VoiceActivityDetector.NeverVoice().hasVoice(voice)).isFalse()
    }

    // ─── AGC tests ──────────────────────────────────────────────────────────

    @Test
    fun `AGC amplifies quiet audio toward target RMS`() {
        val agc = AutomaticGainControl(targetRms = 3000f)
        val quiet = sineWave(AudioCapturePipeline.CHUNK_SAMPLES, amplitude = 200, freqHz = 200)
        val out = agc.apply(quiet)
        // Output RMS should be significantly closer to target than input.
        val inRms = rms(quiet)
        val outRms = rms(out)
        assertThat(outRms).isGreaterThan(inRms * 5f)
    }

    @Test
    fun `AGC attenuates loud audio to prevent clipping`() {
        val agc = AutomaticGainControl(targetRms = 3000f)
        val loud = sineWave(AudioCapturePipeline.CHUNK_SAMPLES, amplitude = 30_000, freqHz = 200)
        val out = agc.apply(loud)
        // No sample should clip beyond Short range.
        for (s in out) {
            assertThat(s.toInt()).isAtMost(Short.MAX_VALUE.toInt())
            assertThat(s.toInt()).isAtLeast(Short.MIN_VALUE.toInt())
        }
        // And the output RMS should be lower than the input.
        assertThat(rms(out)).isLessThan(rms(loud) * 0.9f)
    }

    @Test
    fun `AGC leaves near-silence untouched to avoid amplifying noise`() {
        val agc = AutomaticGainControl()
        val silence = ShortArray(AudioCapturePipeline.CHUNK_SAMPLES) { 1 }
        val out = agc.apply(silence)
        assertThat(out).isEqualTo(silence)
    }

    private fun rms(samples: ShortArray): Float {
        var sum = 0L
        for (s in samples) {
            val v = s.toInt()
            sum += v.toLong() * v.toLong()
        }
        return kotlin.math.sqrt(sum.toDouble() / samples.size).toFloat()
    }
}
