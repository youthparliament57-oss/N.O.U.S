// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.voice.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// VOICE_FIX_006: Audio format handling safe

class AudioFormatTest {

    @Test
    fun `default format is 16kHz 16-bit mono`() {
        val format = AudioFormat()
        assertThat(format.sampleRateHz).isEqualTo(16_000)
        assertThat(format.bitsPerSample).isEqualTo(16)
        assertThat(format.channels).isEqualTo(1)
    }

    @Test
    fun `VOICE_16K constant matches default`() {
        assertThat(AudioFormat.VOICE_16K).isEqualTo(AudioFormat())
    }

    @Test
    fun `bytesPerSample is bitsPerSample divided by 8`() {
        assertThat(AudioFormat.VOICE_16K.bytesPerSample).isEqualTo(2)
        assertThat(AudioFormat(sampleRateHz = AudioFormat.VOICE_16K.sampleRateHz, bitsPerSample = 8).bytesPerSample).isEqualTo(1)
        assertThat(AudioFormat(sampleRateHz = AudioFormat.VOICE_16K.sampleRateHz, bitsPerSample = 24).bytesPerSample).isEqualTo(3)
    }

    @Test
    fun `bytesPerSecond is sampleRate times bytesPerSample times channels`() {
        val format = AudioFormat.VOICE_16K
        // 16000 * 2 * 1 = 32000 bytes/sec
        assertThat(format.bytesPerSecond).isEqualTo(32_000)
    }

    @Test
    fun `samplesPerFrame for 16kHz 20ms is 320`() {
        // 16000 * 20 / 1000 = 320 samples
        assertThat(AudioFormat.VOICE_16K.samplesPerFrame).isEqualTo(320)
    }

    @Test
    fun `bytesPerFrame for 16kHz 20ms 16-bit is 640`() {
        // 320 samples * 2 bytes = 640 bytes
        assertThat(AudioFormat.VOICE_16K.bytesPerFrame).isEqualTo(640)
    }

    @Test
    fun `frameDurationMs is always 20`() {
        assertThat(AudioFormat.VOICE_16K.frameDurationMs).isEqualTo(20)
    }

    @Test
    fun `SFX_44K is 44100 Hz`() {
        assertThat(AudioFormat.SFX_44K.sampleRateHz).isEqualTo(44_100)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `sampleRate below 8000 throws`() {
        AudioFormat(sampleRateHz = 4000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `sampleRate above 48000 throws`() {
        AudioFormat(sampleRateHz = 96_000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `bitsPerSample of 12 throws`() {
        AudioFormat(bitsPerSample = 12)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `channels of 0 throws`() {
        AudioFormat(channels = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `channels above 2 throws`() {
        AudioFormat(channels = 6)
    }

    @Test
    fun `stereo format is allowed`() {
        val stereo = AudioFormat(channels = 2)
        assertThat(stereo.channels).isEqualTo(2)
        assertThat(stereo.bytesPerSecond).isEqualTo(16_000 * 2 * 2)
    }
}
