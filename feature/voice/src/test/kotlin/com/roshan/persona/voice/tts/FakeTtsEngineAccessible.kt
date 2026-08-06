// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.voice.tts

/**
 * Test-only [TtsEngine] that records every call. Reusable across test files
 * (BargeInDetector tests, EmotionalTtsEngine tests, future step tests).
 *
 * Marked `open` so test doubles can subclass if needed. Keep this in the
 * `tts` test package alongside the production [TtsEngine] interface it fakes.
 */
class FakeTtsEngineAccessible : TtsEngine {
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

    /** Reset all recorded state. Useful between sub-tests in one method. */
    fun reset() {
        lastPitch = null
        lastRate = null
        lastVolume = null
        lastSpokenText = null
        lastUtteranceId = null
        speakReturn = true
        stopCalled = false
        shutdownCalled = false
        doneCallbacks.clear()
    }
}
