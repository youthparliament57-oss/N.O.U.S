// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.voice.conversation

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.roshan.persona.voice.capture.AudioCapturePipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

// VOICE_FIX_001: Conversation engine safe

class ConversationEngineTest {

    private class FakeFillerPlayer : FillerPlayer {
        val played = mutableListOf<String>()
        override suspend fun playFiller(text: String) {
            played += text
        }
    }

    /** Predictor that always predicts end-of-speech. */
    private class AlwaysEndPredictor : EndOfSpeechPredictor(
        vapBackend = null,
        config = EndOfSpeechPredictor.Config(heuristicThreshold = 0.5f),
    ) {
        override fun predictEndOfSpeech(
            audioContext: ShortArray,
            features: AudioFeatures,
        ): EndOfSpeechPrediction {
            // Always return willEnd=true with confidence 0.8.
            return EndOfSpeechPrediction(
                willEndWithin1Second = true,
                confidence = 0.8f,
                source = PredictionSource.HEURISTIC,
            )
        }
    }

    /** Predictor that never predicts end-of-speech (forces silence-only commit). */
    private class NeverEndPredictor : EndOfSpeechPredictor(vapBackend = null) {
        override fun predictEndOfSpeech(
            audioContext: ShortArray,
            features: AudioFeatures,
        ): EndOfSpeechPrediction = EndOfSpeechPrediction(
            willEndWithin1Second = false,
            confidence = 0.1f,
            source = PredictionSource.HEURISTIC,
        )
    }

    private fun voiceChunk(ts: Long): AudioCapturePipeline.AudioChunk =
        AudioCapturePipeline.AudioChunk(
            samples = ShortArray(AudioCapturePipeline.CHUNK_SAMPLES) { 5000 },
            timestampMs = ts,
            hasVoice = true,
        )

    private fun silenceChunk(ts: Long): AudioCapturePipeline.AudioChunk =
        AudioCapturePipeline.AudioChunk(
            samples = ShortArray(AudioCapturePipeline.CHUNK_SAMPLES) { 0 },
            timestampMs = ts,
            hasVoice = false,
        )

    @After
    fun tearDown() {
        FillerSounds.reset()
    }

    @Test
    fun `startListening transitions state from IDLE to LISTENING`() = runTest {
        val engine = ConversationEngine(
            predictor = NeverEndPredictor(),
            fillerPlayer = FakeFillerPlayer(),
            engineDispatcher = Dispatchers.Unconfined,
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val audioFlow = MutableSharedFlow<AudioCapturePipeline.AudioChunk>(extraBufferCapacity = 16)

        assertThat(engine.state.value).isEqualTo(ConversationState.IDLE)
        engine.startListening(audioFlow.asSharedFlow(), scope)
        assertThat(engine.state.value).isEqualTo(ConversationState.LISTENING)

        engine.stopListening()
        assertThat(engine.state.value).isEqualTo(ConversationState.IDLE)
    }

    @Test
    fun `startListening in non-IDLE state is a no-op`() = runTest {
        val engine = ConversationEngine(
            predictor = NeverEndPredictor(),
            fillerPlayer = FakeFillerPlayer(),
            engineDispatcher = Dispatchers.Unconfined,
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val flow = MutableSharedFlow<AudioCapturePipeline.AudioChunk>(extraBufferCapacity = 16)

        engine.onTtsStarted()  // state = SPEAKING
        engine.startListening(flow.asSharedFlow(), scope)
        assertThat(engine.state.value).isEqualTo(ConversationState.SPEAKING)  // unchanged
    }

    @Test
    fun `end-of-speech prediction followed by silence commits the turn`() = runTest {
        val engine = ConversationEngine(
            predictor = AlwaysEndPredictor(),
            fillerPlayer = FakeFillerPlayer(),
            engineDispatcher = Dispatchers.Unconfined,
            config = ConversationEngine.Config(commitSilenceMs = 100),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val audioFlow = MutableSharedFlow<AudioCapturePipeline.AudioChunk>(extraBufferCapacity = 16)

        var turnCommitted = false
        val sub = scope.launch {
            engine.events.collect { event ->
                if (event is ConversationEvent.TurnCommitted) turnCommitted = true
            }
        }

        engine.startListening(audioFlow.asSharedFlow(), scope)
        // Voice chunk → no commit.
        audioFlow.emit(voiceChunk(1000))
        // Silence chunk → predictor still says end + 100ms silence → commit.
        audioFlow.emit(silenceChunk(1100))

        assertThat(turnCommitted).isTrue()
        assertThat(engine.state.value).isEqualTo(ConversationState.IDLE)
        sub.cancel()
    }

    @Test
    fun `silence exceeding STT timeout commits turn even without prediction`() = runTest {
        val engine = ConversationEngine(
            predictor = NeverEndPredictor(),  // never predicts
            fillerPlayer = FakeFillerPlayer(),
            engineDispatcher = Dispatchers.Unconfined,
            config = ConversationEngine.Config(commitSilenceMs = 10_000),  // high to avoid double-commit
        )
        // Use a predictor config with a low silenceMsForEnd so silence triggers commit.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val audioFlow = MutableSharedFlow<AudioCapturePipeline.AudioChunk>(extraBufferCapacity = 16)

        var committed = false
        val sub = scope.launch {
            engine.events.collect { event ->
                if (event is ConversationEvent.TurnCommitted) committed = true
            }
        }

        engine.startListening(audioFlow.asSharedFlow(), scope)
        audioFlow.emit(voiceChunk(1000))
        // 600 ms of silence (exceeds default 500 ms silenceMsForEnd).
        audioFlow.emit(silenceChunk(1600))

        assertThat(committed).isTrue()
        sub.cancel()
    }

    @Test
    fun `onBrainProcessingStarted transitions to PROCESSING and plays filler after delay`() = runTest {
        val fillerPlayer = FakeFillerPlayer()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = ConversationEngine(
            predictor = NeverEndPredictor(),
            fillerPlayer = fillerPlayer,
            engineDispatcher = dispatcher,
            config = ConversationEngine.Config(fillerDelayMs = 500),
        )
        val scope = CoroutineScope(SupervisorJob() + dispatcher)

        // First, get the engine into LISTENING.
        val audioFlow = MutableSharedFlow<AudioCapturePipeline.AudioChunk>(extraBufferCapacity = 16)
        engine.startListening(audioFlow.asSharedFlow(), scope)
        runCurrent()

        // Now signal brain processing.
        engine.onBrainProcessingStarted(scope)
        assertThat(engine.state.value).isEqualTo(ConversationState.PROCESSING)

        // No filler immediately.
        assertThat(fillerPlayer.played).isEmpty()

        // After 500 ms, filler should play.
        advanceTimeBy(501)
        runCurrent()
        assertThat(fillerPlayer.played).hasSize(1)

        engine.stopListening()
    }

    @Test
    fun `onTtsStarted and onTtsDone transition state correctly`() = runTest {
        val engine = ConversationEngine(
            predictor = NeverEndPredictor(),
            fillerPlayer = FakeFillerPlayer(),
            engineDispatcher = Dispatchers.Unconfined,
        )
        engine.onTtsStarted()
        assertThat(engine.state.value).isEqualTo(ConversationState.SPEAKING)
        engine.onTtsDone()
        assertThat(engine.state.value).isEqualTo(ConversationState.IDLE)
    }

    @Test
    fun `FillerSounds cycles through phrases deterministically`() {
        FillerSounds.reset()
        val first = FillerSounds.getFiller(BrainProcessingState.SEARCHING_MEMORY)
        val second = FillerSounds.getFiller(BrainProcessingState.SEARCHING_MEMORY)
        val third = FillerSounds.getFiller(BrainProcessingState.SEARCHING_MEMORY)
        // Three consecutive calls should be different (cycling).
        assertThat(first).isNotEqualTo(second)
        assertThat(second).isNotEqualTo(third)
    }

    @Test
    fun `FillerSounds returns from appropriate category per brain state`() {
        FillerSounds.reset()
        val thinking = FillerSounds.getFiller(BrainProcessingState.SEARCHING_MEMORY)
        val searching = FillerSounds.getFiller(BrainProcessingState.CLOUD_LLM)
        val processing = FillerSounds.getFiller(BrainProcessingState.AGENTIC_EXECUTING)
        assertThat(FillerSounds.THINKING).contains(thinking)
        assertThat(FillerSounds.SEARCHING).contains(searching)
        assertThat(FillerSounds.PROCESSING).contains(processing)
    }

    @Test
    fun `ConversationEngine Config validates non-negative delays`() {
        try {
            ConversationEngine.Config(fillerDelayMs = -1)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("fillerDelayMs")
        }
        try {
            ConversationEngine.Config(commitSilenceMs = -1)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("commitSilenceMs")
        }
    }

    @Test
    fun `TurnCommitted equals compares content of triggerAudio`() {
        val samples = ShortArray(10) { it.toShort() }
        val e1 = ConversationEvent.TurnCommitted(samples, 1000L)
        val e2 = ConversationEvent.TurnCommitted(samples.copyOf(), 1000L)
        assertThat(e1).isEqualTo(e2)
        assertThat(e1.hashCode()).isEqualTo(e2.hashCode())
    }
}
