// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.voice.capture

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.math.sin

// AUTO_FIX_0168: [feature] AudioCapturePipelineTest verified

// VOICE_FIX_029: Capture pipeline validated

class AudioCapturePipelineTest {

    // ─── Fakes ─────────────────────────────────────────────────────────────

    /** Deterministic AudioSource that emits sine-wave chunks. */
    private class FakeAudioSource(
        private val amplitude: Int = 5000,
        private val voiceLike: Boolean = true,
    ) : AudioSource {
        private var callCount = 0
        override fun start() = Unit
        override fun readChunk(): ShortArray {
            val baseFreq = if (voiceLike) 220 else 440
            return ShortArray(AudioCapturePipeline.CHUNK_SAMPLES) { i ->
                val phase = (callCount * AudioCapturePipeline.CHUNK_SAMPLES + i)
                (amplitude * sin(2 * Math.PI * baseFreq * phase / AudioCapturePipeline.SAMPLE_RATE))
                    .toInt().toShort()
            }.also { callCount++ }
        }
        override fun stop() = Unit
        override fun release() = Unit
    }

    private fun makePipeline(
        source: AudioSource = FakeAudioSource(),
        vad: VoiceActivityDetector = VoiceActivityDetector.EnergyBased(),
    ) = AudioCapturePipeline(
        audioSource = source,
        echoCanceller = EchoCanceller.Noop(),
        noiseSuppressor = NoiseSuppressor.Noop(),
        automaticGainControl = AutomaticGainControl(),
        voiceActivityDetector = vad,
        circularBuffer = CircularAudioBuffer(capacity = 1024),
        captureDispatcher = Dispatchers.Unconfined,
    )

    // ─── Tests ─────────────────────────────────────────────────────────────

    @Test
    fun `processChunk runs all 4 stages and returns a tagged AudioChunk`() {
        val pipeline = makePipeline()
        val raw = ShortArray(AudioCapturePipeline.CHUNK_SAMPLES) { 5000 }
        val chunk = pipeline.processChunk(raw)

        assertThat(chunk.samples.size).isEqualTo(AudioCapturePipeline.CHUNK_SAMPLES)
        assertThat(chunk.timestampMs).isGreaterThan(0L)
        // With a loud sine-wave-ish input, EnergyBased VAD should detect voice.
        assertThat(chunk.hasVoice).isTrue()
    }

    @Test
    fun `processChunk tags silence chunks as hasVoice=false`() {
        val pipeline = makePipeline()
        val silence = ShortArray(AudioCapturePipeline.CHUNK_SAMPLES) { 0 }
        val chunk = pipeline.processChunk(silence)
        assertThat(chunk.hasVoice).isFalse()
    }

    @Test
    fun `start emits chunks to subscribers via SharedFlow`() = runTest {
        // Use a real (non-Unconfined) dispatcher so the capture loop yields
        // between chunks and the subscriber gets a chance to collect.
        val captureDispatcher = StandardTestDispatcher(testScheduler)
        val pipeline = AudioCapturePipeline(
            audioSource = FakeAudioSource(),
            echoCanceller = EchoCanceller.Noop(),
            noiseSuppressor = NoiseSuppressor.Noop(),
            automaticGainControl = AutomaticGainControl(),
            voiceActivityDetector = VoiceActivityDetector.EnergyBased(),
            circularBuffer = CircularAudioBuffer(capacity = 1024),
            captureDispatcher = captureDispatcher,
        )
        val scope = CoroutineScope(SupervisorJob() + captureDispatcher)

        val collected = mutableListOf<AudioCapturePipeline.AudioChunk>()
        val collectJob = scope.launch {
            pipeline.chunks.collect { collected += it }
        }

        pipeline.start(scope)
        // Run enough dispatcher ticks for a few chunks to flow through.
        advanceTimeBy(100)
        runCurrent()

        pipeline.stop()
        collectJob.cancel()

        assertThat(collected).isNotEmpty()
        val first = collected.first()
        assertThat(first.samples.size).isEqualTo(AudioCapturePipeline.CHUNK_SAMPLES)
    }

    @Test
    fun `start is idempotent — calling twice does not spawn two loops`() = runTest {
        val captureDispatcher = StandardTestDispatcher(testScheduler)
        val pipeline = AudioCapturePipeline(
            audioSource = FakeAudioSource(),
            echoCanceller = EchoCanceller.Noop(),
            noiseSuppressor = NoiseSuppressor.Noop(),
            automaticGainControl = AutomaticGainControl(),
            voiceActivityDetector = VoiceActivityDetector.EnergyBased(),
            circularBuffer = CircularAudioBuffer(capacity = 1024),
            captureDispatcher = captureDispatcher,
        )
        val scope = CoroutineScope(SupervisorJob() + captureDispatcher)
        pipeline.start(scope)
        // Second start should be a no-op — does not throw, does not spawn
        // a second loop. isRunning stays true.
        pipeline.start(scope)
        assertThat(pipeline.isRunning).isTrue()
        pipeline.stop()
        assertThat(pipeline.isRunning).isFalse()
    }

    @Test
    fun `stop is idempotent`() = runTest {
        val pipeline = makePipeline()
        // stop without start should be a no-op
        pipeline.stop()
        assertThat(pipeline.isRunning).isFalse()
    }

    @Test
    fun `CircularAudioBuffer snapshot returns data in chronological order`() {
        val buf = CircularAudioBuffer(capacity = 8)
        buf.write(shortArrayOf(1, 2, 3, 4))
        assertThat(buf.snapshot().toList()).isEqualTo(listOf<Short>(1, 2, 3, 4))
        // Now overflow: write 6 more samples (total written = 10, cap = 8).
        buf.write(shortArrayOf(5, 6, 7, 8, 9, 10))
        // Expect the last 8: 3, 4, 5, 6, 7, 8, 9, 10
        assertThat(buf.snapshot().toList()).isEqualTo(
            listOf<Short>(3, 4, 5, 6, 7, 8, 9, 10)
        )
    }

    @Test
    fun `CircularAudioBuffer size reflects total written capped at capacity`() {
        val buf = CircularAudioBuffer(capacity = 8)
        assertThat(buf.size()).isEqualTo(0)
        buf.write(shortArrayOf(1, 2, 3))
        assertThat(buf.size()).isEqualTo(3)
        buf.write(shortArrayOf(4, 5, 6, 7, 8, 9, 10))  // total = 10
        assertThat(buf.size()).isEqualTo(8)
    }

    @Test
    fun `CircularAudioBuffer clear resets state`() {
        val buf = CircularAudioBuffer(capacity = 8)
        buf.write(shortArrayOf(1, 2, 3))
        buf.clear()
        assertThat(buf.size()).isEqualTo(0)
        assertThat(buf.snapshot().toList()).isEmpty()
    }

    @Test
    fun `CircularAudioBuffer rejects non-power-of-2 capacity`() {
        try {
            CircularAudioBuffer(capacity = 100)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("power of 2")
        }
    }

    @Test
    fun `pipeline constants match strategy doc`() {
        // Sanity check the constants — these are referenced by every other
        // component in Module 5 (wake word, STT, etc.).
        assertThat(AudioCapturePipeline.SAMPLE_RATE).isEqualTo(16_000)
        assertThat(AudioCapturePipeline.CHUNK_SAMPLES).isEqualTo(320)
        assertThat(AudioCapturePipeline.CHUNK_DURATION_MS).isEqualTo(20L)
        assertThat(AudioCapturePipeline.CIRCULAR_BUFFER_SECONDS).isEqualTo(2)
    }
}
