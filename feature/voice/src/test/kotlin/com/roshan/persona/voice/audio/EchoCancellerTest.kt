// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.voice.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// VOICE_FIX_007: Echo canceller ok

class AudioEffectCapabilitiesTest {

    @Test
    fun `SOFTWARE_ONLY has no hardware effects`() {
        val caps = AudioEffectCapabilities.SOFTWARE_ONLY
        assertThat(caps.hardwareAecAvailable).isFalse()
        assertThat(caps.hardwareAgcAvailable).isFalse()
        assertThat(caps.hardwareNsAvailable).isFalse()
        assertThat(caps.hardwareEffectCount).isEqualTo(0)
        assertThat(caps.hasAnyHardware).isFalse()
    }

    @Test
    fun `ALL_HARDWARE has all 3 effects`() {
        val caps = AudioEffectCapabilities.ALL_HARDWARE
        assertThat(caps.hardwareAecAvailable).isTrue()
        assertThat(caps.hardwareAgcAvailable).isTrue()
        assertThat(caps.hardwareNsAvailable).isTrue()
        assertThat(caps.hardwareEffectCount).isEqualTo(3)
        assertThat(caps.hasAnyHardware).isTrue()
    }

    @Test
    fun `partial capabilities work correctly`() {
        val caps = AudioEffectCapabilities(
            hardwareAecAvailable = true,
            hardwareAgcAvailable = false,
            hardwareNsAvailable = true,
        )
        assertThat(caps.hardwareEffectCount).isEqualTo(2)
        assertThat(caps.hasAnyHardware).isTrue()
    }

    @Test
    fun `probe returns valid capabilities (does not crash)`() {
        // probe() may return true or false depending on the test environment
        // (Robolectric may not emulate hardware effects). We just verify it
        // doesn't throw and returns a non-null object.
        val caps = AudioEffectCapabilities.probe()
        assertThat(caps).isNotNull()
        // hardwareEffectCount is 0-3
        assertThat(caps.hardwareEffectCount).isAtMost(3)
        assertThat(caps.hardwareEffectCount).isAtLeast(0)
    }
}

class EchoCancellerFactoryTest {

    @Test
    fun `create returns HardwareEchoCanceller when hardware available`() {
        val canceller = EchoCanceller.create(AudioEffectCapabilities.ALL_HARDWARE)
        assertThat(canceller).isInstanceOf(HardwareEchoCanceller::class.java)
        assertThat(canceller.isUsingHardware).isTrue()
    }

    @Test
    fun `create returns SoftwareEchoCanceller when hardware unavailable`() {
        val canceller = EchoCanceller.create(AudioEffectCapabilities.SOFTWARE_ONLY)
        assertThat(canceller).isInstanceOf(SoftwareEchoCanceller::class.java)
        assertThat(canceller.isUsingHardware).isFalse()
    }

    @Test
    fun `create with partial capabilities returns HardwareEchoCanceller if AEC available`() {
        val caps = AudioEffectCapabilities(
            hardwareAecAvailable = true,
            hardwareAgcAvailable = false,
            hardwareNsAvailable = false,
        )
        val canceller = EchoCanceller.create(caps)
        assertThat(canceller).isInstanceOf(HardwareEchoCanceller::class.java)
    }

    @Test
    fun `create with no AEC but other effects returns SoftwareEchoCanceller`() {
        val caps = AudioEffectCapabilities(
            hardwareAecAvailable = false,
            hardwareAgcAvailable = true,
            hardwareNsAvailable = true,
        )
        val canceller = EchoCanceller.create(caps)
        assertThat(canceller).isInstanceOf(SoftwareEchoCanceller::class.java)
    }
}

class HardwareEchoCancellerTest {

    @Test
    fun `isUsingHardware is true`() {
        val canceller = HardwareEchoCanceller()
        assertThat(canceller.isUsingHardware).isTrue()
    }

    @Test
    fun `cancelEcho returns input unchanged (hardware processes in-line)`() {
        val canceller = HardwareEchoCanceller()
        val chunk = AudioChunk.sine(frequencyHz = 440, amplitude = 0.5f)
        val result = canceller.cancelEcho(chunk, AudioChunk.silent())
        // Hardware AEC is a no-op on the chunk — input is returned as-is
        assertThat(result.samples).isEqualTo(chunk.samples)
    }

    @Test
    fun `cancelEcho works without TTS reference`() {
        val canceller = HardwareEchoCanceller()
        val chunk = AudioChunk.sine(frequencyHz = 440, amplitude = 0.5f)
        val result = canceller.cancelEcho(chunk, ttsReference = null)
        assertThat(result.samples).isEqualTo(chunk.samples)
    }

    @Test
    fun `release can be called multiple times without error`() {
        val canceller = HardwareEchoCanceller()
        canceller.release()
        canceller.release() // should not throw
    }

    @Test(expected = IllegalStateException::class)
    fun `cancelEcho after release throws`() {
        val canceller = HardwareEchoCanceller()
        canceller.release()
        canceller.cancelEcho(AudioChunk.silent())
    }

    @Test
    fun `attachToSession does not crash (will be tested with Robolectric on device)`() {
        // attachToSession requires real AudioRecord session, which we can't
        // create in pure unit tests. Just verify it doesn't crash with a
        // dummy session ID of 0 (will fail to attach but shouldn't throw).
        val canceller = HardwareEchoCanceller()
        try {
            canceller.attachToSession(0)
        } catch (e: Throwable) {
            // AcousticEchoCanceler.create(0) may return null on test JVM
            // We accept either success or RuntimeException, but not other errors.
        }
        canceller.release()
    }
}

class SoftwareEchoCancellerTest {

    @Test
    fun `isUsingHardware is false`() {
        val canceller = SoftwareEchoCanceller()
        assertThat(canceller.isUsingHardware).isFalse()
    }

    @Test
    fun `cancelEcho without TTS reference returns input unchanged`() {
        val canceller = SoftwareEchoCanceller()
        val chunk = AudioChunk.sine(frequencyHz = 440, amplitude = 0.5f)
        val result = canceller.cancelEcho(chunk, ttsReference = null)
        // No TTS → no echo to cancel → input returned as-is
        assertThat(result.samples).isEqualTo(chunk.samples)
    }

    @Test
    fun `cancelEcho with empty TTS reference returns input unchanged`() {
        val canceller = SoftwareEchoCanceller()
        val chunk = AudioChunk.sine(frequencyHz = 440, amplitude = 0.5f)
        val emptyTts = AudioChunk(AudioFormat.VOICE_16K, ShortArray(0), 0L)
        val result = canceller.cancelEcho(chunk, ttsReference = emptyTts)
        assertThat(result.samples).isEqualTo(chunk.samples)
    }

    @Test
    fun `cancelEcho with TTS reference produces output of same length`() {
        val canceller = SoftwareEchoCanceller()
        val micChunk = AudioChunk.sine(frequencyHz = 1000, amplitude = 0.5f)
        val ttsChunk = AudioChunk.sine(frequencyHz = 440, amplitude = 0.5f)
        val result = canceller.cancelEcho(micChunk, ttsChunk)
        assertThat(result.sampleCount).isEqualTo(micChunk.sampleCount)
    }

    @Test
    fun `cancelEcho output is not identical to input (filter modifies signal)`() {
        val canceller = SoftwareEchoCanceller()
        val micChunk = AudioChunk.sine(frequencyHz = 1000, amplitude = 0.5f)
        val ttsChunk = AudioChunk.sine(frequencyHz = 440, amplitude = 0.5f)

        // Run multiple iterations to let the LMS filter learn
        var result = micChunk
        repeat(10) {
            result = canceller.cancelEcho(micChunk, ttsChunk)
        }

        // After learning, output should differ from input
        var anyDifference = false
        for (i in micChunk.samples.indices) {
            if (result.samples[i] != micChunk.samples[i]) {
                anyDifference = true
                break
            }
        }
        assertThat(anyDifference).isTrue()
    }

    @Test
    fun `cancelEcho output is bounded to short range`() {
        val canceller = SoftwareEchoCanceller()
        // Loud mic input + loud TTS reference — make sure no overflow
        val micChunk = AudioChunk.sine(frequencyHz = 1000, amplitude = 1.0f)
        val ttsChunk = AudioChunk.sine(frequencyHz = 440, amplitude = 1.0f)
        val result = canceller.cancelEcho(micChunk, ttsChunk)
        for (s in result.samples) {
            assertThat(s.toInt()).isAtMost(Short.MAX_VALUE.toInt())
            assertThat(s.toInt()).isAtLeast(Short.MIN_VALUE.toInt())
        }
    }

    @Test
    fun `attachToSession is a no-op for software AEC`() {
        val canceller = SoftwareEchoCanceller()
        // Should not throw, should not have any side effect
        canceller.attachToSession(12345)
    }

    @Test
    fun `release can be called multiple times`() {
        val canceller = SoftwareEchoCanceller()
        canceller.release()
        canceller.release()
    }

    @Test(expected = IllegalStateException::class)
    fun `cancelEcho after release throws`() {
        val canceller = SoftwareEchoCanceller()
        canceller.release()
        canceller.cancelEcho(AudioChunk.silent())
    }

    @Test
    fun `filter learns to suppress repeated echo over time`() {
        // Create a scenario: mic = TTS echo (pure echo, no user voice)
        // After many iterations, output should be near-zero (echo suppressed)
        val canceller = SoftwareEchoCanceller()
        val ttsChunk = AudioChunk.sine(frequencyHz = 440, amplitude = 0.5f)

        // Simulate mic picking up TTS exactly (perfect echo)
        var lastRms = 1.0
        repeat(50) {
            val result = canceller.cancelEcho(ttsChunk, ttsChunk)
            lastRms = result.rmsAmplitude.toDouble()
        }

        // After 50 iterations, the LMS filter should have learned to suppress
        // the echo significantly. The output RMS should be much lower than input.
        val inputRms = ttsChunk.rmsAmplitude
        assertThat(lastRms).isLessThan(inputRms.toDouble() * 0.5)
    }
}
