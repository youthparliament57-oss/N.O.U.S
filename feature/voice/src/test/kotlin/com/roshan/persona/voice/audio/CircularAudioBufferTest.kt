// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.voice.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// VOICE_FIX_009: Circular buffer thread safe

class CircularAudioBufferTest {

    private val format = AudioFormat.VOICE_16K

    @Test
    fun `new buffer is empty`() {
        val buffer = CircularAudioBuffer()
        assertThat(buffer.size).isEqualTo(0)
        assertThat(buffer.isFull).isFalse()
        assertThat(buffer.currentDurationMs).isEqualTo(0L)
    }

    @Test
    fun `append adds chunk and increments size`() {
        val buffer = CircularAudioBuffer()
        val chunk = AudioChunk.silent()
        buffer.append(chunk)
        assertThat(buffer.size).isEqualTo(1)
        assertThat(buffer.currentDurationMs).isEqualTo(20L)
    }

    @Test
    fun `default buffer capacity is 100 chunks for 2 seconds at 20ms`() {
        val buffer = CircularAudioBuffer()
        // 2000ms / 20ms = 100 chunks
        for (i in 1..99) {
            buffer.append(AudioChunk.silent())
            assertThat(buffer.isFull).isFalse()
        }
        buffer.append(AudioChunk.silent())
        assertThat(buffer.size).isEqualTo(100)
        assertThat(buffer.isFull).isTrue()
    }

    @Test
    fun `appending to full buffer evicts oldest chunk`() {
        val buffer = CircularAudioBuffer(bufferDurationMs = 60) // 3 chunks
        val c1 = AudioChunk.sine(frequencyHz = 100, timestampMs = 100L)
        val c2 = AudioChunk.sine(frequencyHz = 200, timestampMs = 200L)
        val c3 = AudioChunk.sine(frequencyHz = 300, timestampMs = 300L)
        val c4 = AudioChunk.sine(frequencyHz = 400, timestampMs = 400L)

        buffer.append(c1)
        buffer.append(c2)
        buffer.append(c3)
        assertThat(buffer.size).isEqualTo(3)
        assertThat(buffer.isFull).isTrue()

        // Append c4 → should evict c1
        val evicted = buffer.append(c4)
        assertThat(evicted).isNotNull()
        assertThat(evicted!!.timestampMs).isEqualTo(100L)
        assertThat(buffer.size).isEqualTo(3)

        // Oldest should now be c2
        assertThat(buffer.peekOldest()!!.timestampMs).isEqualTo(200L)
        // Newest should be c4
        assertThat(buffer.peekNewest()!!.timestampMs).isEqualTo(400L)
    }

    @Test
    fun `appending to non-full buffer does not evict`() {
        val buffer = CircularAudioBuffer(bufferDurationMs = 60)
        buffer.append(AudioChunk.silent())
        val evicted = buffer.append(AudioChunk.silent())
        assertThat(evicted).isNull()
    }

    @Test
    fun `assigns monotonic sequence numbers`() {
        val buffer = CircularAudioBuffer(bufferDurationMs = 60)
        val c1 = AudioChunk.silent(timestampMs = 100L)
        val c2 = AudioChunk.silent(timestampMs = 200L)
        val c3 = AudioChunk.silent(timestampMs = 300L)
        val c4 = AudioChunk.silent(timestampMs = 400L)

        buffer.append(c1)
        buffer.append(c2)
        buffer.append(c3)
        buffer.append(c4) // evicts c1, but seq was assigned before eviction

        // After eviction, c2 should have seq=1, c3=2, c4=3
        assertThat(buffer.peekOldest()!!.sequenceNumber).isEqualTo(1L)
        assertThat(buffer.peekNewest()!!.sequenceNumber).isEqualTo(3L)
    }

    @Test
    fun `snapshot of empty buffer returns empty chunk`() {
        val buffer = CircularAudioBuffer()
        val snap = buffer.snapshot()
        assertThat(snap.sampleCount).isEqualTo(0)
    }

    @Test
    fun `snapshot concatenates all chunks in order`() {
        val buffer = CircularAudioBuffer(bufferDurationMs = 60) // 3 chunks
        // Each chunk has distinct samples so we can verify ordering
        val c1 = AudioChunk(format, ShortArray(320) { 1 }, 100L)
        val c2 = AudioChunk(format, ShortArray(320) { 2 }, 200L)
        val c3 = AudioChunk(format, ShortArray(320) { 3 }, 300L)
        buffer.append(c1)
        buffer.append(c2)
        buffer.append(c3)

        val snap = buffer.snapshot()
        assertThat(snap.sampleCount).isEqualTo(960)
        // First 320 samples should be 1, next 320 = 2, next 320 = 3
        assertThat(snap.samples[0]).isEqualTo(1.toShort())
        assertThat(snap.samples[319]).isEqualTo(1.toShort())
        assertThat(snap.samples[320]).isEqualTo(2.toShort())
        assertThat(snap.samples[639]).isEqualTo(2.toShort())
        assertThat(snap.samples[640]).isEqualTo(3.toShort())
        assertThat(snap.samples[959]).isEqualTo(3.toShort())
        // Oldest timestamp should be preserved
        assertThat(snap.timestampMs).isEqualTo(100L)
    }

    @Test
    fun `snapshot does not mutate buffer`() {
        val buffer = CircularAudioBuffer(bufferDurationMs = 60)
        buffer.append(AudioChunk.silent())
        buffer.append(AudioChunk.silent())
        val before = buffer.size
        buffer.snapshot()
        assertThat(buffer.size).isEqualTo(before)
    }

    @Test
    fun `snapshotLast returns recent N ms of audio`() {
        val buffer = CircularAudioBuffer(bufferDurationMs = 100) // 5 chunks
        val c1 = AudioChunk(format, ShortArray(320) { 1 }, 100L)
        val c2 = AudioChunk(format, ShortArray(320) { 2 }, 200L)
        val c3 = AudioChunk(format, ShortArray(320) { 3 }, 300L)
        val c4 = AudioChunk(format, ShortArray(320) { 4 }, 400L)
        val c5 = AudioChunk(format, ShortArray(320) { 5 }, 500L)
        buffer.append(c1)
        buffer.append(c2)
        buffer.append(c3)
        buffer.append(c4)
        buffer.append(c5)

        // Get last 40 ms = 2 chunks = c4 + c5
        val recent = buffer.snapshotLast(40)
        assertThat(recent.sampleCount).isEqualTo(640)
        assertThat(recent.samples[0]).isEqualTo(4.toShort())
        assertThat(recent.samples[639]).isEqualTo(5.toShort())
        assertThat(recent.timestampMs).isEqualTo(400L)
    }

    @Test
    fun `snapshotLast with duration greater than buffer returns everything`() {
        val buffer = CircularAudioBuffer(bufferDurationMs = 60)
        buffer.append(AudioChunk.silent())
        buffer.append(AudioChunk.silent())
        val snap = buffer.snapshotLast(10_000)
        assertThat(snap.sampleCount).isEqualTo(640)
    }

    @Test
    fun `peekNewest on empty buffer returns null`() {
        val buffer = CircularAudioBuffer()
        assertThat(buffer.peekNewest()).isNull()
    }

    @Test
    fun `peekOldest on empty buffer returns null`() {
        val buffer = CircularAudioBuffer()
        assertThat(buffer.peekOldest()).isNull()
    }

    @Test
    fun `clear empties the buffer`() {
        val buffer = CircularAudioBuffer(bufferDurationMs = 60)
        buffer.append(AudioChunk.silent())
        buffer.append(AudioChunk.silent())
        assertThat(buffer.size).isEqualTo(2)
        buffer.clear()
        assertThat(buffer.size).isEqualTo(0)
        assertThat(buffer.currentDurationMs).isEqualTo(0L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `append rejects chunk with wrong format`() {
        val buffer = CircularAudioBuffer(format = AudioFormat.VOICE_16K)
        val wrongFormat = AudioFormat(sampleRateHz = 44_100)
        buffer.append(AudioChunk(wrongFormat, ShortArray(wrongFormat.samplesPerFrame), 0L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `append rejects chunk with wrong size`() {
        val buffer = CircularAudioBuffer(format = AudioFormat.VOICE_16K)
        // Wrong size — should be 320 samples (20ms @ 16kHz), passing 100
        buffer.append(AudioChunk(format, ShortArray(100), 0L))
    }

    @Test
    fun `buffer with custom duration has correct capacity`() {
        val buffer = CircularAudioBuffer(bufferDurationMs = 500) // 25 chunks
        for (i in 1..25) buffer.append(AudioChunk.silent())
        assertThat(buffer.isFull).isTrue()
    }
}
