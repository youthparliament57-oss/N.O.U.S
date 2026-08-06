// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.voice.translate

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

// VOICE_FIX_010: Translation engine validated

class LiveTranslationEngineTest {

    private fun makeEngine(
        translate: suspend (String, String, String) -> String = { text, _, _ -> text },
    ): LiveTranslationEngine {
        return LiveTranslationEngine(
            sttProvider = MockTranslateSttProvider(),
            llmTranslate = translate,
            ttsProvider = MockTranslateTtsProvider(),
        )
    }

    @Test
    fun `SUPPORTED_LANGUAGES contains 32 languages`() {
        assertThat(LiveTranslationEngine.SUPPORTED_LANGUAGES.size).isEqualTo(32)
    }

    @Test
    fun `SUPPORTED_LANGUAGES includes English and Hindi`() {
        assertThat(LiveTranslationEngine.SUPPORTED_LANGUAGES).contains("en")
        assertThat(LiveTranslationEngine.SUPPORTED_LANGUAGES).contains("hi")
    }

    @Test
    fun `LANGUAGE_COUNT is 32`() {
        assertThat(LiveTranslationEngine.LANGUAGE_COUNT).isEqualTo(32)
    }

    @Test
    fun `isActive is false before start`() {
        val engine = makeEngine()
        assertThat(engine.isActive).isFalse()
        engine.release()
    }

    @Test
    fun `startTranslation with valid languages returns true`() = runTest {
        val engine = makeEngine()
        val result = engine.startTranslation("en", "hi")
        assertThat(result).isTrue()
        assertThat(engine.isActive).isTrue()
        engine.release()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `startTranslation rejects unsupported language A`() = runTest {
        val engine = makeEngine()
        engine.startTranslation("xx", "en")
        engine.release()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `startTranslation rejects unsupported language B`() = runTest {
        val engine = makeEngine()
        engine.startTranslation("en", "yy")
        engine.release()
    }

    @Test
    fun `stopTranslation sets active to false`() = runTest {
        val engine = makeEngine()
        engine.startTranslation("en", "hi")
        engine.stopTranslation()
        assertThat(engine.isActive).isFalse()
        engine.release()
    }

    @Test
    fun `release prevents further start`() = runTest {
        val engine = makeEngine()
        engine.release()
        try {
            engine.startTranslation("en", "hi")
            assert(false) { "Expected IllegalStateException" }
        } catch (e: IllegalStateException) {
            // Expected
        }
    }

    @Test
    fun `startTranslation is idempotent when already active`() = runTest {
        val engine = makeEngine()
        engine.startTranslation("en", "hi")
        val second = engine.startTranslation("en", "hi")
        assertThat(second).isTrue()
        engine.release()
    }
}

class MockTranslateSttProviderTest {

    @Test
    fun `emitPartial invokes callback when active`() = runTest {
        val provider = MockTranslateSttProvider()
        var receivedPartial: Pair<String, String>? = null
        provider.startRecognition(
            onPartial = { text, lang -> receivedPartial = text to lang },
            onFinal = { _, _ -> },
            onError = {},
        )
        provider.emitPartial("hello", "en")
        assertThat(receivedPartial).isEqualTo("hello" to "en")
        provider.release()
    }

    @Test
    fun `emitFinal invokes callback when active`() = runTest {
        val provider = MockTranslateSttProvider()
        var receivedFinal: Pair<String, String>? = null
        provider.startRecognition(
            onPartial = { _, _ -> },
            onFinal = { text, lang -> receivedFinal = text to lang },
            onError = {},
        )
        provider.emitFinal("hello world", "en")
        assertThat(receivedFinal).isEqualTo("hello world" to "en")
        provider.release()
    }

    @Test
    fun `emitError invokes callback when active`() = runTest {
        val provider = MockTranslateSttProvider()
        var receivedError: String? = null
        provider.startRecognition(
            onPartial = { _, _ -> },
            onFinal = { _, _ -> },
            onError = { receivedError = it },
        )
        provider.emitError("network error")
        assertThat(receivedError).isEqualTo("network error")
        provider.release()
    }

    @Test
    fun `stopRecognition sets active to false`() = runTest {
        val provider = MockTranslateSttProvider()
        provider.startRecognition(
            onPartial = { _, _ -> },
            onFinal = { _, _ -> },
            onError = {},
        )
        provider.stopRecognition()
        assertThat(provider.isActive).isFalse()
        provider.release()
    }

    @Test
    fun `emitPartial when not active is no-op`() = runTest {
        val provider = MockTranslateSttProvider()
        var received = false
        provider.startRecognition(
            onPartial = { _, _ -> received = true },
            onFinal = { _, _ -> },
            onError = {},
        )
        provider.stopRecognition()
        provider.emitPartial("hello", "en")
        assertThat(received).isFalse()
        provider.release()
    }

    @Test
    fun `release prevents further start`() = runTest {
        val provider = MockTranslateSttProvider()
        provider.release()
        try {
            provider.startRecognition(
                onPartial = { _, _ -> },
                onFinal = { _, _ -> },
                onError = {},
            )
            assert(false) { "Expected IllegalStateException" }
        } catch (e: IllegalStateException) {
            // Expected
        }
    }
}

class MockTranslateTtsProviderTest {

    @Test
    fun `speak records text and language`() = runTest {
        val provider = MockTranslateTtsProvider()
        provider.speak("hello", "en") {}
        assertThat(provider.getSpeakCount()).isEqualTo(1)
        val (text, lang) = provider.getSpokenTexts()[0]
        assertThat(text).isEqualTo("hello")
        assertThat(lang).isEqualTo("en")
        provider.release()
    }

    @Test
    fun `speak invokes onDone immediately by default`() = runTest {
        val provider = MockTranslateTtsProvider()
        var doneCalled = false
        provider.speak("hello", "en") { doneCalled = true }
        assertThat(doneCalled).isTrue()
        provider.release()
    }

    @Test
    fun `stop marks provider as stopped`() = runTest {
        val provider = MockTranslateTtsProvider()
        provider.speak("hello", "en") {}
        provider.stop()
        assertThat(provider.wasStopped()).isTrue()
        provider.release()
    }

    @Test
    fun `release prevents further speak`() = runTest {
        val provider = MockTranslateTtsProvider()
        provider.release()
        try {
            provider.speak("hello", "en") {}
            assert(false) { "Expected IllegalStateException" }
        } catch (e: IllegalStateException) {
            // Expected
        }
    }

    @Test
    fun `multiple speak calls increment count`() = runTest {
        val provider = MockTranslateTtsProvider()
        provider.speak("hello", "en") {}
        provider.speak("world", "hi") {}
        provider.speak("foo", "es") {}
        assertThat(provider.getSpeakCount()).isEqualTo(3)
        provider.release()
    }
}

class TranslationEventTest {

    @Test
    fun `Started stores both languages`() {
        val event = TranslationEvent.Started("en", "hi")
        assertThat(event.languageA).isEqualTo("en")
        assertThat(event.languageB).isEqualTo("hi")
    }

    @Test
    fun `PartialTranslated with null targetText is valid`() {
        val event = TranslationEvent.PartialTranslated(
            sourceText = "hello",
            targetText = null,
            sourceLang = "en",
            targetLang = "hi",
        )
        assertThat(event.targetText).isNull()
    }

    @Test
    fun `FinalTranslated stores all fields`() {
        val event = TranslationEvent.FinalTranslated(
            sourceText = "hello",
            targetText = "नमस्ते",
            sourceLang = "en",
            targetLang = "hi",
            audioPlaybackStarted = true,
        )
        assertThat(event.targetText).isEqualTo("नमस्ते")
        assertThat(event.audioPlaybackStarted).isTrue()
    }

    @Test
    fun `Error stores message`() {
        val event = TranslationEvent.Error("network failed")
        assertThat(event.message).isEqualTo("network failed")
    }

    @Test
    fun `Stopped is a singleton object`() {
        val event1: TranslationEvent = TranslationEvent.Stopped
        val event2: TranslationEvent = TranslationEvent.Stopped
        assertThat(event1).isSameInstanceAs(event2)
    }
}
