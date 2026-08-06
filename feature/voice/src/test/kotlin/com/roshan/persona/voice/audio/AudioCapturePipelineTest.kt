// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.voice.audio

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

// VOICE_FIX_008: Capture pipeline resource safe

class AudioCapturePipelineTest {

    private fun makePipeline(
        chunks: List<AudioChunk>,
        capabilities: AudioEffectCapabilities = AudioEffectCapabilities.SOFTWARE_ONLY,
    ): AudioCapturePipeline {
        val source = FakeAudioSource(chunks = chunks)
        return AudioCapturePipeline.create(source, capabilities)
    }

    @Test
    fun `pipeline starts and stops cleanly`() = runTest {
        val pipeline = makePipeline(listOf(AudioChunk.silent()))
        assertThat(pipeline.isCapturing).isFalse()
        val started = pipeline.start()
        assertThat(started).isTrue()
        assertThat(pipeline.isCapturing).isTrue()
        pipeline.stop()
        assertThat(pipeline.isCapturing).isFalse()
        pipeline.release()
    }

    @Test
    fun `isUsingHardwareAec reflects capabilities`() = runTest {
        val hardwarePipeline = makePipeline(
            listOf(AudioChunk.silent()),
            capabilities = AudioEffectCapabilities.ALL_HARDWARE,
        )
        assertThat(hardwarePipeline.isUsingHardwareAec).isTrue()
        hardwarePipeline.release()

        val softwarePipeline = makePipeline(
            listOf(AudioChunk.silent()),
            capabilities = AudioEffectCapabilities.SOFTWARE_ONLY,
        )
        assertThat(softwarePipeline.isUsingHardwareAec).isFalse()
        softwarePipeline.release()
    }

    @Test
    fun `processChunk returns null when not capturing`() = runTest {
        val pipeline = makePipeline(listOf(AudioChunk.silent()))
        val result = pipeline.processChunk(AudioChunk.silent())
        assertThat(result).isNull()
        pipeline.release()
    }

    @Test
    fun `processChunk returns processed chunk with VAD result`() = runTest {
        val pipeline = makePipeline(listOf(AudioChunk.silent()))
        pipeline.start()

        val chunk = AudioChunk.sine(frequencyHz = 440, amplitude = 0.5f)
        val result = pipeline.processChunk(chunk)
        assertThat(result).isNotNull()
        assertThat(result!!.audio.sampleCount).isEqualTo(chunk.sampleCount)
        assertThat(result.vadResult).isNotNull()
        assertThat(result.sequenceNumber).isEqualTo(0L)
        pipeline.release()
    }

    @Test
    fun `processChunk increments sequence number`() = runTest {
        val pipeline = makePipeline(listOf(AudioChunk.silent()))
        pipeline.start()

        val chunk = AudioChunk.silent()
        val r1 = pipeline.processChunk(chunk)!!
        val r2 = pipeline.processChunk(chunk)!!
        val r3 = pipeline.processChunk(chunk)!!
        assertThat(r1.sequenceNumber).isEqualTo(0L)
        assertThat(r2.sequenceNumber).isEqualTo(1L)
        assertThat(r3.sequenceNumber).isEqualTo(2L)
        pipeline.release()
    }

    @Test
    fun `processChunk emits to audioStream`() = runTest {
        val pipeline = makePipeline(listOf(AudioChunk.silent()))
        pipeline.start()

        pipeline.audioStream.test {
            pipeline.processChunk(AudioChunk.sine(frequencyHz = 440, amplitude = 0.5f))
            val emitted = awaitItem()
            assertThat(emitted).isNotNull()
            assertThat(emitted.audio.sampleCount).isEqualTo(320)
            cancelAndIgnoreRemainingEvents()
        }
        pipeline.release()
    }

    @Test
    fun `processChunk applies all pipeline stages`() = runTest {
        val pipeline = makePipeline(listOf(AudioChunk.silent()))
        pipeline.start()

        // Set TTS reference so software AEC actually applies
        pipeline.onTtsPlaying(AudioChunk.sine(frequencyHz = 440, amplitude = 0.5f))

        val result = pipeline.processChunk(AudioChunk.sine(frequencyHz = 1000, amplitude = 0.5f))!!
        assertThat(result.pipelineStages.appliedAec).isTrue()
        assertThat(result.pipelineStages.appliedNoiseSuppression).isTrue()
        assertThat(result.pipelineStages.appliedAgc).isTrue()
        assertThat(result.pipelineStages.appliedVad).isTrue()
        pipeline.release()
    }

    @Test
    fun `processChunk without TTS reference does not apply software AEC`() = runTest {
        // For software AEC, appliedAec should be false when no TTS reference is set
        val pipeline = makePipeline(
            listOf(AudioChunk.silent()),
            capabilities = AudioEffectCapabilities.SOFTWARE_ONLY,
        )
        pipeline.start()

        val result = pipeline.processChunk(AudioChunk.sine(frequencyHz = 440, amplitude = 0.5f))!!
        // Software AEC no-ops without TTS reference, so appliedAec = false
        assertThat(result.pipelineStages.appliedAec).isFalse()
        pipeline.release()
    }

    @Test
    fun `processChunk with TTS reference applies software AEC`() = runTest {
        val pipeline = makePipeline(
            listOf(AudioChunk.silent()),
            capabilities = AudioEffectCapabilities.SOFTWARE_ONLY,
        )
        pipeline.start()

        val ttsChunk = AudioChunk.sine(frequencyHz = 440, amplitude = 0.5f)
        pipeline.onTtsPlaying(ttsChunk)
        val result = pipeline.processChunk(AudioChunk.sine(frequencyHz = 1000, amplitude = 0.5f))!!
        // Now software AEC has a reference, so appliedAec = true
        assertThat(result.pipelineStages.appliedAec).isTrue()
        pipeline.release()
    }

    @Test
    fun `onTtsStopped clears TTS reference`() = runTest {
        val pipeline = makePipeline(
            listOf(AudioChunk.silent()),
            capabilities = AudioEffectCapabilities.SOFTWARE_ONLY,
        )
        pipeline.start()

        pipeline.onTtsPlaying(AudioChunk.sine(frequencyHz = 440, amplitude = 0.5f))
        pipeline.onTtsStopped()
        val result = pipeline.processChunk(AudioChunk.sine(frequencyHz = 440, amplitude = 0.5f))!!
        // No TTS reference → no AEC applied
        assertThat(result.pipelineStages.appliedAec).isFalse()
        pipeline.release()
    }

    @Test
    fun `circularBuffer accumulates processed chunks`() = runTest {
        val pipeline = makePipeline(listOf(AudioChunk.silent()))
        pipeline.start()

        // Process 3 chunks
        pipeline.processChunk(AudioChunk.silent())
        pipeline.processChunk(AudioChunk.silent())
        pipeline.processChunk(AudioChunk.silent())

        // Circular buffer should have 3 chunks
        assertThat(pipeline.circularBuffer.size).isEqualTo(3)
        pipeline.release()
    }

    @Test
    fun `getRecentAudio returns circular buffer snapshot`() = runTest {
        val pipeline = makePipeline(listOf(AudioChunk.silent()))
        pipeline.start()

        pipeline.processChunk(AudioChunk.silent())
        pipeline.processChunk(AudioChunk.silent())

        val recent = pipeline.getRecentAudio()
        // 2 chunks × 320 samples = 640 samples
        assertThat(recent.sampleCount).isEqualTo(640)
        pipeline.release()
    }

    @Test
    fun `getRecentAudio with duration returns last N ms`() = runTest {
        val pipeline = makePipeline(listOf(AudioChunk.silent()))
        pipeline.start()

        // Process 5 chunks (100 ms total)
        repeat(5) { pipeline.processChunk(AudioChunk.silent()) }

        // Get last 40 ms = 2 chunks
        val recent = pipeline.getRecentAudio(durationMs = 40)
        assertThat(recent.sampleCount).isEqualTo(640)
        pipeline.release()
    }

    @Test
    fun `reset clears pipeline state`() = runTest {
        val pipeline = makePipeline(listOf(AudioChunk.silent()))
        pipeline.start()

        pipeline.processChunk(AudioChunk.silent())
        pipeline.processChunk(AudioChunk.silent())
        assertThat(pipeline.circularBuffer.size).isEqualTo(2)

        pipeline.reset()
        assertThat(pipeline.circularBuffer.size).isEqualTo(0)
        pipeline.release()
    }

    @Test
    fun `release prevents further processing`() = runTest {
        val pipeline = makePipeline(listOf(AudioChunk.silent()))
        pipeline.release()
        try {
            pipeline.start()
            assert(false) { "Expected IllegalStateException" }
        } catch (e: IllegalStateException) {
            // Expected
        }
    }

    @Test
    fun `start returns true when already started (idempotent)`() = runTest {
        val pipeline = makePipeline(listOf(AudioChunk.silent()))
        pipeline.start()
        val secondStart = pipeline.start()
        assertThat(secondStart).isTrue()
        pipeline.release()
    }

    @Test
    fun `stop is safe to call when not started`() = runTest {
        val pipeline = makePipeline(listOf(AudioChunk.silent()))
        pipeline.stop() // should not throw
        pipeline.release()
    }

    @Test
    fun `voiceStream filters to only voice chunks`() = runTest {
        val pipeline = makePipeline(listOf(AudioChunk.silent()))
        pipeline.start()

        pipeline.voiceStream.test {
            // Process a voice chunk (multi-band signal)
            val samples = ShortArray(320)
            for (i in samples.indices) {
                val t = i.toDouble() / 16_000.0
                val v = 0.3 * (
                    kotlin.math.sin(2 * Math.PI * 500 * t) +
                        kotlin.math.sin(2 * Math.PI * 2000 * t) +
                        kotlin.math.sin(2 * Math.PI * 3500 * t)
                    )
                samples[i] = (v * Short.MAX_VALUE).toInt().toShort()
            }
            val voiceChunk = AudioChunk(AudioFormat.VOICE_16K, samples, 0L)
            pipeline.processChunk(voiceChunk)
            // Should emit the voice chunk
            val emitted = awaitItem()
            assertThat(emitted.vadResult.isVoice).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
        pipeline.release()
    }
}

class FakeAudioSourceTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Test
    fun `start returns true and sets capturing state`() = runTest(testDispatcher) {
        val source = FakeAudioSource(
            chunks = listOf(AudioChunk.silent()),
            coroutineContext = testDispatcher,
        )
        val started = source.start()
        assertThat(started).isTrue()
        source.release()
    }

    @Test
    fun `chunkStream emits provided chunks`() = runTest(testDispatcher) {
        val chunk1 = AudioChunk.silent()
        val chunk2 = AudioChunk.sine(frequencyHz = 440, amplitude = 0.5f)
        val source = FakeAudioSource(
            chunks = listOf(chunk1, chunk2),
            coroutineContext = testDispatcher,
        )
        source.start()

        source.chunkStream().test {
            val first = awaitItem()
            assertThat(first.samples).isEqualTo(chunk1.samples)
            val second = awaitItem()
            assertThat(second.samples).isEqualTo(chunk2.samples)
            awaitComplete()
        }
        source.release()
    }

    @Test
    fun `looping source emits chunks indefinitely`() = runTest(testDispatcher) {
        val chunk1 = AudioChunk.silent()
        val source = FakeAudioSource(
            chunks = listOf(chunk1),
            loop = true,
            chunkDelayMs = 1L, // small delay to prevent tight loop
            coroutineContext = testDispatcher,
        )
        source.start()

        source.chunkStream().test {
            awaitItem() // first
            awaitItem() // second (looped)
            awaitItem() // third (looped)
            cancelAndIgnoreRemainingEvents()
        }
        source.release()
    }

    @Test
    fun `non-looping source completes after emitting all chunks`() = runTest(testDispatcher) {
        val source = FakeAudioSource(
            chunks = listOf(AudioChunk.silent(), AudioChunk.silent()),
            loop = false,
            coroutineContext = testDispatcher,
        )
        source.start()

        source.chunkStream().test {
            awaitItem()
            awaitItem()
            awaitComplete()
        }
        source.release()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty chunk list throws`() {
        FakeAudioSource(chunks = emptyList())
    }

    @Test
    fun `stop is safe to call multiple times`() = runTest(testDispatcher) {
        val source = FakeAudioSource(
            chunks = listOf(AudioChunk.silent()),
            coroutineContext = testDispatcher,
        )
        source.start()
        source.stop()
        source.stop() // should not throw
        source.release()
    }

    @Test
    fun `release prevents further start`() = runTest(testDispatcher) {
        val source = FakeAudioSource(
            chunks = listOf(AudioChunk.silent()),
            coroutineContext = testDispatcher,
        )
        source.release()
        val started = source.start()
        assertThat(started).isFalse()
    }

    @Test(expected = IllegalStateException::class)
    fun `chunkStream throws when not started`() {
        val source = FakeAudioSource(
            chunks = listOf(AudioChunk.silent()),
            coroutineContext = testDispatcher,
        )
        source.chunkStream() // should throw
    }
}

class ProcessedAudioChunkTest {

    @Test
    fun `processed chunk preserves audio and VAD result`() {
        val audio = AudioChunk.silent()
        val vadResult = VadResult(
            isVoice = false,
            bandEnergiesDb = FloatArray(6) { -100f },
            voiceBandCount = 0,
            consecutiveVoiceChunks = 0,
            consecutiveSilentChunks = 1,
            noiseFloorDb = FloatArray(6) { -50f },
        )
        val chunk = ProcessedAudioChunk(
            audio = audio,
            vadResult = vadResult,
            sequenceNumber = 42L,
            timestampMs = 12345L,
            pipelineStages = PipelineStages(
                appliedAec = true,
                appliedNoiseSuppression = true,
                appliedAgc = true,
                appliedVad = true,
            ),
        )
        assertThat(chunk.audio).isEqualTo(audio)
        assertThat(chunk.vadResult).isEqualTo(vadResult)
        assertThat(chunk.sequenceNumber).isEqualTo(42L)
        assertThat(chunk.timestampMs).isEqualTo(12345L)
        assertThat(chunk.pipelineStages.appliedAec).isTrue()
    }
}
