// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.voice.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// VOICE_FIX_003: Audio chunk handling ok

class AudioChunkTest {

    private val format = AudioFormat.VOICE_16K

    @Test
    fun `silent chunk has all zero samples`() {
        val chunk = AudioChunk.silent(durationMs = 20)
        assertThat(chunk.samples.all { it == 0.toShort() }).isTrue()
    }

    @Test
    fun `silent chunk duration matches requested duration`() {
        val chunk = AudioChunk.silent(durationMs = 100)
        // 100 ms at 16 kHz = 1600 samples
        assertThat(chunk.sampleCount).isEqualTo(1600)
        assertThat(chunk.durationMs).isEqualTo(100L)
    }

    @Test
    fun `silent chunk is reported as silent`() {
        val chunk = AudioChunk.silent()
        assertThat(chunk.isSilent).isTrue()
    }

    @Test
    fun `sine chunk is not silent`() {
        val chunk = AudioChunk.sine(frequencyHz = 440, amplitude = 0.5f)
        assertThat(chunk.isSilent).isFalse()
    }

    @Test
    fun `sine chunk RMS amplitude is non-zero`() {
        val chunk = AudioChunk.sine(frequencyHz = 440, amplitude = 0.5f)
        assertThat(chunk.rmsAmplitude).isGreaterThan(0.1f)
        assertThat(chunk.rmsAmplitude).isLessThan(0.5f) // RMS < peak for sine
    }

    @Test
    fun `sine chunk peak amplitude matches requested amplitude`() {
        val chunk = AudioChunk.sine(frequencyHz = 440, amplitude = 0.5f)
        // Peak should be very close to 0.5 (might be slightly less due to phase alignment)
        assertThat(chunk.peakAmplitude).isWithin(0.05f).of(0.5f)
    }

    @Test
    fun `silent chunk RMS amplitude is zero`() {
        val chunk = AudioChunk.silent()
        assertThat(chunk.rmsAmplitude).isEqualTo(0f)
    }

    @Test
    fun `silent chunk peak amplitude is zero`() {
        val chunk = AudioChunk.silent()
        assertThat(chunk.peakAmplitude).isEqualTo(0f)
    }

    @Test
    fun `20ms chunk at 16kHz has 320 samples`() {
        val chunk = AudioChunk.silent(durationMs = 20)
        assertThat(chunk.sampleCount).isEqualTo(320)
    }

    @Test
    fun `durationMs is computed from sample count and sample rate`() {
        val chunk = AudioChunk.silent(durationMs = 50)
        // 50 ms = 800 samples at 16 kHz
        assertThat(chunk.sampleCount).isEqualTo(800)
        assertThat(chunk.durationMs).isEqualTo(50L)
    }

    @Test
    fun `toByteBuffer has correct size and byte order`() {
        val chunk = AudioChunk.silent(durationMs = 20)
        val buffer = chunk.toByteBuffer()
        // 320 samples * 2 bytes = 640 bytes
        assertThat(buffer.capacity()).isEqualTo(640)
        assertThat(buffer.isDirect).isTrue()
    }

    @Test
    fun `toByteBuffer preserves sample data`() {
        val chunk = AudioChunk.sine(frequencyHz = 1000, amplitude = 0.8f)
        val buffer = chunk.toByteBuffer()
        val shortBuffer = buffer.asShortBuffer()
        val recovered = ShortArray(chunk.sampleCount)
        shortBuffer.get(recovered)
        assertThat(recovered.contentEquals(chunk.samples)).isTrue()
    }

    @Test
    fun `withSamples creates new chunk with same metadata`() {
        val original = AudioChunk(
            format = format,
            samples = ShortArray(320),
            timestampMs = 12345L,
            sequenceNumber = 42L,
        )
        val newSamples = ShortArray(320) { it.toShort() }
        val processed = original.withSamples(newSamples)
        assertThat(processed.samples).isNotSameInstanceAs(original.samples)
        assertThat(processed.samples.contentEquals(newSamples)).isTrue()
        assertThat(processed.timestampMs).isEqualTo(original.timestampMs)
        assertThat(processed.sequenceNumber).isEqualTo(original.sequenceNumber)
        assertThat(processed.format).isEqualTo(original.format)
    }

    @Test
    fun `equals compares samples by content not reference`() {
        val samples = ShortArray(10) { it.toShort() }
        val chunk1 = AudioChunk(format, samples, 0L)
        val chunk2 = AudioChunk(format, samples.copyOf(), 0L)
        assertThat(chunk1).isEqualTo(chunk2)
    }

    @Test
    fun `hashCode is consistent with equals`() {
        val samples = ShortArray(10) { it.toShort() }
        val chunk1 = AudioChunk(format, samples, 0L)
        val chunk2 = AudioChunk(format, samples.copyOf(), 0L)
        assertThat(chunk1.hashCode()).isEqualTo(chunk2.hashCode())
    }

    @Test
    fun `chunks with different timestamps are not equal`() {
        val samples = ShortArray(10)
        val chunk1 = AudioChunk(format, samples, 100L)
        val chunk2 = AudioChunk(format, samples, 200L)
        assertThat(chunk1).isNotEqualTo(chunk2)
    }
}
