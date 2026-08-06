// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.voice.bargein

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.voice.capture.AudioCapturePipeline
import com.roshan.persona.voice.capture.EchoCanceller
import com.roshan.persona.voice.capture.VoiceActivityDetector
import com.roshan.persona.voice.tts.EmotionalTtsEngine
import com.roshan.persona.voice.tts.FakeTtsEngineAccessible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.math.sin

// AUTO_FIX_0182: [feature] BargeInDetectorTest verified

// VOICE_FIX_015: Barge-in detector ok

class BargeInDetectorTest {

    // ─── Fakes ─────────────────────────────────────────────────────────────

    /** Always-voice VAD for triggering barge-in deterministically. */
    private class AlwaysVoiceVad : VoiceActivityDetector {
        override val aggressiveness: Int = 2
        override fun hasVoice(chunk: ShortArray): Boolean = true
    }

    /** Configurable VAD — caller controls hasVoice return per call. */
    private class ScriptedVad(private val script: List<Boolean>) : VoiceActivityDetector {
        override val aggressiveness: Int = 2
        private var idx = 0
        override fun hasVoice(chunk: ShortArray): Boolean {
            if (idx >= script.size) return false
            return script[idx++]
        }
    }

    /** Echo canceller that returns input unchanged (test stand-in). */
    private class NoopEchoCanceller : EchoCanceller {
        override val isHardwareAccelerated: Boolean = false
        override fun cancelEcho(micInput: ShortArray, ttsReference: ShortArray?): ShortArray = micInput
        override fun release() = Unit
    }

    /** Loud sine-wave chunk (will trigger EnergyBased VAD as voice). */
    private fun voiceChunk(timestampMs: Long = System.currentTimeMillis()): AudioCapturePipeline.AudioChunk {
        val samples = ShortArray(AudioCapturePipeline.CHUNK_SAMPLES) { i ->
            (5000 * sin(2 * Math.PI * 220 * i / AudioCapturePipeline.SAMPLE_RATE)).toInt().toShort()
        }
        return AudioCapturePipeline.AudioChunk(samples, timestampMs, hasVoice = true)
    }

    /** Silent chunk (will NOT trigger EnergyBased VAD). */
    private fun silenceChunk(timestampMs: Long = System.currentTimeMillis()): AudioCapturePipeline.AudioChunk {
        return AudioCapturePipeline.AudioChunk(
            ShortArray(AudioCapturePipeline.CHUNK_SAMPLES) { 0 },
            timestampMs,
            hasVoice = false,
        )
    }

    private fun makeDetector(
        vad: VoiceActivityDetector = AlwaysVoiceVad(),
        config: BargeInDetector.Config = BargeInDetector.Config(),
    ): Pair<BargeInDetector, MutableSharedFlow<AudioCapturePipeline.AudioChunk>> {
        val fakeTts = FakeTtsEngineAccessible()
        val ttsEngine = EmotionalTtsEngine(fakeTts)
        val detector = BargeInDetector(
            tts = ttsEngine,
            echoCanceller = NoopEchoCanceller(),
            vad = vad,
            captureDispatcher = Dispatchers.Unconfined,
            config = config,
        )
        val audioFlow = MutableSharedFlow<AudioCapturePipeline.AudioChunk>(
            extraBufferCapacity = 64,
            onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
        )
        return detector to audioFlow
    }

    // ─── Tests ─────────────────────────────────────────────────────────────

    @Test
    fun `voice chunks below minVoiceChunks threshold do not fire barge-in`() = runTest {
        val (detector, audioFlow) = makeDetector(
            vad = ScriptedVad(listOf(true)),  // only 1 voice chunk, but minVoiceChunks=2
            config = BargeInDetector.Config(minVoiceChunks = 2, cooldownMs = 0),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        var events = 0
        val sub = scope.launch { detector.events.collect { events++ } }

        detector.startMonitoring(audioFlow.asSharedFlow(), scope)
        audioFlow.emit(voiceChunk(1000))
        runCurrent()

        detector.stop()
        sub.cancel()
        assertThat(events).isEqualTo(0)
    }

    @Test
    fun `two consecutive voice chunks fire barge-in when minVoiceChunks is 2`() = runTest {
        val (detector, audioFlow) = makeDetector(
            vad = ScriptedVad(listOf(true, true)),
            config = BargeInDetector.Config(minVoiceChunks = 2, cooldownMs = 0),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        var events = 0
        val sub = scope.launch { detector.events.collect { events++ } }

        detector.startMonitoring(audioFlow.asSharedFlow(), scope)
        audioFlow.emit(voiceChunk(1000))
        audioFlow.emit(voiceChunk(1020))
        runCurrent()

        detector.stop()
        sub.cancel()
        assertThat(events).isEqualTo(1)
    }

    @Test
    fun `non-consecutive voice chunks reset the counter and do not fire`() = runTest {
        // voice, silence, voice — only 1 consecutive at a time, minVoiceChunks=2
        val (detector, audioFlow) = makeDetector(
            vad = ScriptedVad(listOf(true, false, true)),
            config = BargeInDetector.Config(minVoiceChunks = 2, cooldownMs = 0),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        var events = 0
        val sub = scope.launch { detector.events.collect { events++ } }

        detector.startMonitoring(audioFlow.asSharedFlow(), scope)
        audioFlow.emit(voiceChunk(1000))
        audioFlow.emit(silenceChunk(1020))
        audioFlow.emit(voiceChunk(1040))
        runCurrent()

        detector.stop()
        sub.cancel()
        assertThat(events).isEqualTo(0)
    }

    @Test
    fun `cooldown suppresses barge-in events for cooldownMs`() = runTest {
        val (detector, audioFlow) = makeDetector(
            vad = AlwaysVoiceVad(),
            config = BargeInDetector.Config(minVoiceChunks = 1, cooldownMs = 1000),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        var events = 0
        val sub = scope.launch { detector.events.collect { events++ } }

        detector.startMonitoring(audioFlow.asSharedFlow(), scope)
        audioFlow.emit(voiceChunk(1000))  // should fire (1st event)
        runCurrent()
        assertThat(events).isEqualTo(1)

        audioFlow.emit(voiceChunk(1010))  // should be suppressed (in cooldown)
        runCurrent()
        assertThat(events).isEqualTo(1)

        advanceTimeBy(1001)  // cooldown expires
        runCurrent()
        audioFlow.emit(voiceChunk(2020))  // should fire again (2nd event)
        runCurrent()
        assertThat(events).isEqualTo(2)

        detector.stop()
        sub.cancel()
    }

    @Test
    fun `startMonitoring is idempotent`() = runTest {
        val (detector, audioFlow) = makeDetector()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        detector.startMonitoring(audioFlow.asSharedFlow(), scope)
        assertThat(detector.isMonitoring).isTrue()
        // Second call should be a no-op — does not throw, does not spawn second monitor.
        detector.startMonitoring(audioFlow.asSharedFlow(), scope)
        assertThat(detector.isMonitoring).isTrue()

        detector.stop()
        assertThat(detector.isMonitoring).isFalse()
    }

    @Test
    fun `stop is idempotent and safe when not monitoring`() {
        val (detector, _) = makeDetector()
        // stop without start should be a no-op
        detector.stop()
        assertThat(detector.isMonitoring).isFalse()
    }

    @Test
    fun `Config rejects out-of-bounds minVoiceChunks`() {
        try {
            BargeInDetector.Config(minVoiceChunks = 0)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("minVoiceChunks")
        }
        try {
            BargeInDetector.Config(minVoiceChunks = 11)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("minVoiceChunks")
        }
    }

    @Test
    fun `BargeInEvent equals and hashCode work correctly`() {
        val samples = ShortArray(10) { it.toShort() }
        val e1 = BargeInDetector.BargeInEvent(
            timestampMs = 1000L,
            ttsStopLatencyMs = 12L,
            triggerAudio = samples,
        )
        val e2 = BargeInDetector.BargeInEvent(
            timestampMs = 1000L,
            ttsStopLatencyMs = 12L,
            triggerAudio = samples.copyOf(),
        )
        assertThat(e1).isEqualTo(e2)
        assertThat(e1.hashCode()).isEqualTo(e2.hashCode())
    }
}
