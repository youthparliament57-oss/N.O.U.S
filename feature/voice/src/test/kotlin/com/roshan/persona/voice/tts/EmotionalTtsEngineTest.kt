// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.voice.tts

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// AUTO_FIX_0175: [feature] EmotionalTtsEngineTest verified

// VOICE_FIX_022: Emotional TTS engine safe

/**
 * Fake [TtsEngine] that records every call so tests can assert on prosody
 * parameters without needing Robolectric / Android [android.speech.tts.TextToSpeech].
 */
private class FakeTtsEngine : TtsEngine {
    var lastPitch: Float? = null
        private set
    var lastRate: Float? = null
        private set
    var lastVolume: Float? = null
        private set
    var lastSpokenText: String? = null
        private set
    var lastUtteranceId: String? = null
        private set
    var speakReturn: Boolean = true
    var stopCalled: Boolean = false
        private set
    var shutdownCalled: Boolean = false
        private set
    private val doneCallbacks = mutableMapOf<String, () -> Unit>()

    override fun setPitch(pitch: Float) { lastPitch = pitch }
    override fun setSpeechRate(rate: Float) { lastRate = rate }
    override fun setVolume(volume: Float) { lastVolume = volume }

    override fun speak(text: String, utteranceId: String): Boolean {
        lastSpokenText = text
        lastUtteranceId = utteranceId
        return speakReturn
    }

    override fun stop() { stopCalled = true }
    override fun shutdown() { shutdownCalled = true }

    override fun setOnDone(utteranceId: String, callback: () -> Unit) {
        doneCallbacks[utteranceId] = callback
    }

    /** Test helper: simulate the TTS engine finishing the current utterance. */
    fun simulateDone() {
        val id = lastUtteranceId ?: return
        doneCallbacks.remove(id)?.invoke()
    }
}

class EmotionalTtsEngineTest {

    @Test
    fun `speak applies persona and emotion prosody to TTS engine`() {
        val fake = FakeTtsEngine()
        val engine = EmotionalTtsEngine(fake)

        engine.speak(
            text = "Hello there.",
            emotion = Emotion.HAPPY,
            persona = VoicePersona.JARVIS,
        )

        // JARVIS base 0.9 × HAPPY 1.15 = 1.035
        assertThat(fake.lastPitch).isWithin(0.001f).of(0.9f * 1.15f)
        // JARVIS base 1.0 × HAPPY 1.05 = 1.05
        assertThat(fake.lastRate).isWithin(0.001f).of(1.0f * 1.05f)
    }

    @Test
    fun `speak with SLEEPING activity triggers whisper prosody`() {
        val fake = FakeTtsEngine()
        val engine = EmotionalTtsEngine(fake)

        engine.speak(
            text = "Your alarm is set.",
            emotion = Emotion.CALM,
            persona = VoicePersona.JARVIS,
            activity = UserActivity.SLEEPING,
        )

        // Whisper clamps pitch to ≤ 0.7
        assertThat(fake.lastPitch).isAtMost(0.7f)
        // Volume ≤ 0.4
        assertThat(fake.lastVolume).isAtMost(0.4f)
    }

    @Test
    fun `speak with IN_MEETING activity suppresses TTS entirely`() {
        val fake = FakeTtsEngine()
        val engine = EmotionalTtsEngine(fake)

        var doneCalled = false
        engine.speak(
            text = "You have a meeting in 5 minutes.",
            emotion = Emotion.NEUTRAL,
            persona = VoicePersona.JARVIS,
            activity = UserActivity.IN_MEETING,
            onDone = { doneCalled = true },
        )

        // TTS should NOT have been called, but onDone should fire.
        assertThat(fake.lastSpokenText).isNull()
        assertThat(doneCalled).isTrue()
    }

    @Test
    fun `speak with blank text is a no-op that calls onDone`() {
        val fake = FakeTtsEngine()
        val engine = EmotionalTtsEngine(fake)

        var doneCalled = false
        engine.speak(
            text = "",
            emotion = Emotion.NEUTRAL,
            persona = VoicePersona.JARVIS,
            onDone = { doneCalled = true },
        )

        assertThat(fake.lastSpokenText).isNull()
        assertThat(doneCalled).isTrue()
    }

    @Test
    fun `stopForBargeIn stops TTS and invokes barge-in callback`() {
        val fake = FakeTtsEngine()
        val engine = EmotionalTtsEngine(fake)

        var bargeInCalled = false
        engine.speak(
            text = "I'm still talking and the user interrupted me.",
            emotion = Emotion.NEUTRAL,
            persona = VoicePersona.JARVIS,
            onBargeIn = { bargeInCalled = true },
        )

        assertThat(engine.isSpeaking).isTrue()
        engine.stopForBargeIn()
        assertThat(fake.stopCalled).isTrue()
        assertThat(bargeInCalled).isTrue()
        assertThat(engine.isSpeaking).isFalse()
    }

    @Test
    fun `LONG pause pattern inserts ellipses between sentences`() {
        val fake = FakeTtsEngine()
        val engine = EmotionalTtsEngine(fake)

        engine.speak(
            text = "Hello there. How are you?",
            emotion = Emotion.THOUGHTFUL,  // LONG pauses
            persona = VoicePersona.JARVIS,
        )

        assertThat(fake.lastSpokenText).contains("...")
        assertThat(fake.lastSpokenText).doesNotContain(". ")
    }

    @Test
    fun `PUNCHLINE pause pattern inserts comma before last clause`() {
        val fake = FakeTtsEngine()
        val engine = EmotionalTtsEngine(fake)

        engine.speak(
            text = "I would tell you a joke about UDP but you might not get it",
            emotion = Emotion.WITTY,  // PUNCHLINE
            persona = VoicePersona.JARVIS,
        )

        // PUNCHLINE inserts a comma before the last ~4 words.
        assertThat(fake.lastSpokenText).contains(",")
    }

    @Test
    fun `NONE pause pattern does not modify text`() {
        val fake = FakeTtsEngine()
        val engine = EmotionalTtsEngine(fake)

        val text = "Fire alarm. Evacuate now."
        engine.speak(
            text = text,
            emotion = Emotion.URGENT,  // NONE pauses
            persona = VoicePersona.JARVIS,
        )

        // No ellipses inserted (URGENT = NONE pattern).
        // Text may have emphasis markers but no pause insertion.
        assertThat(fake.lastSpokenText).doesNotContain("...")
    }

    @Test
    fun `whisper mode softens exclamation marks`() {
        val fake = FakeTtsEngine()
        val engine = EmotionalTtsEngine(fake)

        engine.speak(
            text = "Wake up! The alarm is ringing!",
            emotion = Emotion.CALM,
            persona = VoicePersona.JARVIS,
            activity = UserActivity.SLEEPING,  // triggers whisper
        )

        // Whisper mode replaces ! with . (whisper-shouting is weird)
        assertThat(fake.lastSpokenText).doesNotContain("!")
    }

    @Test
    fun `TTS engine failure calls onDone without hanging`() {
        val fake = FakeTtsEngine().apply { speakReturn = false }
        val engine = EmotionalTtsEngine(fake)

        var doneCalled = false
        engine.speak(
            text = "Hello.",
            emotion = Emotion.NEUTRAL,
            persona = VoicePersona.JARVIS,
            onDone = { doneCalled = true },
        )

        assertThat(doneCalled).isTrue()
        assertThat(engine.isSpeaking).isFalse()
    }

    @Test
    fun `release stops and shuts down the underlying TTS engine`() {
        val fake = FakeTtsEngine()
        val engine = EmotionalTtsEngine(fake)

        engine.speak(
            text = "Hello.",
            emotion = Emotion.NEUTRAL,
            persona = VoicePersona.JARVIS,
        )
        engine.release()

        assertThat(fake.stopCalled).isTrue()
        assertThat(fake.shutdownCalled).isTrue()
    }
}
