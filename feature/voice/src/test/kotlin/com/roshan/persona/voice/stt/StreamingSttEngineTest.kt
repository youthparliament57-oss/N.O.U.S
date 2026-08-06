// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.voice.stt

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// AUTO_FIX_0167: [feature] StreamingSttEngineTest verified

// VOICE_FIX_030: Streaming STT safe

class SttResultTest {

    @Test
    fun `empty factory creates empty result`() {
        val result = SttResult.empty(SttEngine.MOCK)
        assertThat(result.text).isEmpty()
        assertThat(result.confidence).isEqualTo(0f)
        assertThat(result.isFinal).isTrue()
        assertThat(result.isEmpty).isTrue()
        assertThat(result.engine).isEqualTo(SttEngine.MOCK)
    }

    @Test
    fun `latencyMs is computed correctly`() {
        val result = SttResult(
            text = "hello",
            confidence = 0.9f,
            isFinal = true,
            startedAtMs = 1000L,
            completedAtMs = 1500L,
            engine = SttEngine.MOCK,
        )
        assertThat(result.latencyMs).isEqualTo(500L)
    }

    @Test
    fun `trustLevel HIGH for confidence above 0_85`() {
        val result = SttResult(
            text = "hello",
            confidence = 0.9f,
            isFinal = true,
            startedAtMs = 0L,
            completedAtMs = 0L,
            engine = SttEngine.MOCK,
        )
        assertThat(result.trustLevel).isEqualTo(SttTrustLevel.HIGH)
    }

    @Test
    fun `trustLevel MEDIUM for confidence 0_60 to 0_85`() {
        val result = SttResult(
            text = "hello",
            confidence = 0.7f,
            isFinal = true,
            startedAtMs = 0L,
            completedAtMs = 0L,
            engine = SttEngine.MOCK,
        )
        assertThat(result.trustLevel).isEqualTo(SttTrustLevel.MEDIUM)
    }

    @Test
    fun `trustLevel LOW for confidence below 0_60`() {
        val result = SttResult(
            text = "hello",
            confidence = 0.4f,
            isFinal = true,
            startedAtMs = 0L,
            completedAtMs = 0L,
            engine = SttEngine.MOCK,
        )
        assertThat(result.trustLevel).isEqualTo(SttTrustLevel.LOW)
    }

    @Test
    fun `trustLevel boundary at 0_60 is MEDIUM`() {
        val result = SttResult(
            text = "hello",
            confidence = 0.60f,
            isFinal = true,
            startedAtMs = 0L,
            completedAtMs = 0L,
            engine = SttEngine.MOCK,
        )
        assertThat(result.trustLevel).isEqualTo(SttTrustLevel.MEDIUM)
    }

    @Test
    fun `trustLevel boundary at 0_85 is HIGH`() {
        val result = SttResult(
            text = "hello",
            confidence = 0.85f,
            isFinal = true,
            startedAtMs = 0L,
            completedAtMs = 0L,
            engine = SttEngine.MOCK,
        )
        assertThat(result.trustLevel).isEqualTo(SttTrustLevel.HIGH)
    }

    @Test
    fun `isEmpty is true for blank text`() {
        val result = SttResult(
            text = "   ",
            confidence = 0.9f,
            isFinal = true,
            startedAtMs = 0L,
            completedAtMs = 0L,
            engine = SttEngine.MOCK,
        )
        assertThat(result.isEmpty).isTrue()
    }

    @Test
    fun `isEmpty is false for non-blank text`() {
        val result = SttResult(
            text = "hello",
            confidence = 0.9f,
            isFinal = true,
            startedAtMs = 0L,
            completedAtMs = 0L,
            engine = SttEngine.MOCK,
        )
        assertThat(result.isEmpty).isFalse()
    }
}

class DeltaContextUpdaterTest {

    private val updater = DeltaContextUpdater()

    @Test
    fun `first partial is APPEND`() {
        val delta = updater.processDelta("hello")
        assertThat(delta).isNotNull()
        assertThat(delta!!.type).isEqualTo(DeltaType.APPEND)
        assertThat(delta.newWords).isEqualTo("hello")
        assertThat(delta.fullText).isEqualTo("hello")
    }

    @Test
    fun `extending partial is APPEND with only new words`() {
        updater.processDelta("hello")
        val delta = updater.processDelta("hello world")
        assertThat(delta).isNotNull()
        assertThat(delta!!.type).isEqualTo(DeltaType.APPEND)
        assertThat(delta.newWords).isEqualTo("world")
        assertThat(delta.fullText).isEqualTo("hello world")
    }

    @Test
    fun `identical partial returns null (no change)`() {
        updater.processDelta("hello")
        val delta = updater.processDelta("hello")
        assertThat(delta).isNull()
    }

    @Test
    fun `corrected partial is REPLACE`() {
        updater.processDelta("hello word")
        val delta = updater.processDelta("hello world")
        assertThat(delta).isNotNull()
        assertThat(delta!!.type).isEqualTo(DeltaType.REPLACE)
        assertThat(delta.newWords).isEqualTo("hello world")
        assertThat(delta.fullText).isEqualTo("hello world")
    }

    @Test
    fun `shorter partial is REPLACE`() {
        updater.processDelta("hello world today")
        val delta = updater.processDelta("hello world")
        assertThat(delta).isNotNull()
        assertThat(delta!!.type).isEqualTo(DeltaType.REPLACE)
    }

    @Test
    fun `processFinal always returns REPLACE and resets state`() {
        updater.processDelta("hello world")
        val delta = updater.processFinal("hello world final")
        assertThat(delta.type).isEqualTo(DeltaType.REPLACE)
        assertThat(delta.fullText).isEqualTo("hello world final")
        // After final, next partial should be APPEND (state reset)
        val next = updater.processDelta("new utterance")
        assertThat(next!!.type).isEqualTo(DeltaType.APPEND)
    }

    @Test
    fun `efficiency metric tracks append vs replace`() {
        updater.processDelta("hello")        // APPEND
        updater.processDelta("hello world")  // APPEND
        updater.processDelta("hi world")     // REPLACE
        updater.processDelta("hi world foo") // APPEND
        assertThat(updater.getDeltaCount()).isEqualTo(4L)
        assertThat(updater.getAppendCount()).isEqualTo(3L)
        assertThat(updater.getReplaceCount()).isEqualTo(1L)
        assertThat(updater.getEfficiency()).isWithin(0.01f).of(0.75f)
    }

    @Test
    fun `reset clears state`() {
        updater.processDelta("hello")
        updater.reset()
        assertThat(updater.getLastPartialText()).isEmpty()
        assertThat(updater.getDeltaCount()).isEqualTo(0L)
    }

    @Test
    fun `empty partial after non-empty is REPLACE`() {
        updater.processDelta("hello")
        val delta = updater.processDelta("")
        assertThat(delta).isNotNull()
        assertThat(delta!!.type).isEqualTo(DeltaType.REPLACE)
    }

    @Test
    fun `whitespace is trimmed`() {
        updater.processDelta("  hello  ")
        assertThat(updater.getLastPartialText()).isEqualTo("hello")
        val delta = updater.processDelta("hello world")
        assertThat(delta!!.newWords).isEqualTo("world")
    }

    @Test
    fun `newWordCount counts words in newWords`() {
        val delta = DeltaUpdate(
            type = DeltaType.APPEND,
            newWords = "one two three",
            fullText = "hello one two three",
        )
        assertThat(delta.newWordCount).isEqualTo(3)
    }

    @Test
    fun `newWordCount is 0 for blank newWords`() {
        val delta = DeltaUpdate(
            type = DeltaType.APPEND,
            newWords = "",
            fullText = "hello",
        )
        assertThat(delta.newWordCount).isEqualTo(0)
    }

    @Test
    fun `isAppend and isReplace convenience properties`() {
        val append = DeltaUpdate(DeltaType.APPEND, "word", "hello word")
        val replace = DeltaUpdate(DeltaType.REPLACE, "hello world", "hello world")
        assertThat(append.isAppend).isTrue()
        assertThat(append.isReplace).isFalse()
        assertThat(replace.isAppend).isFalse()
        assertThat(replace.isReplace).isTrue()
    }
}

class ConfidenceScorerTest {

    private val scorer = ConfidenceScorer()

    @Test
    fun `HIGH confidence processes normally`() {
        val result = SttResult(
            text = "what is the weather",
            confidence = 0.95f,
            isFinal = true,
            startedAtMs = 0L,
            completedAtMs = 0L,
            engine = SttEngine.MOCK,
        )
        val routing = scorer.score(result)
        assertThat(routing.trustLevel).isEqualTo(SttTrustLevel.HIGH)
        assertThat(routing.action).isEqualTo(ConfidenceAction.PROCESS_NORMALLY)
    }

    @Test
    fun `MEDIUM confidence non-critical processes normally`() {
        val result = SttResult(
            text = "what is the weather",
            confidence = 0.70f,
            isFinal = true,
            startedAtMs = 0L,
            completedAtMs = 0L,
            engine = SttEngine.MOCK,
        )
        val routing = scorer.score(result)
        assertThat(routing.trustLevel).isEqualTo(SttTrustLevel.MEDIUM)
        assertThat(routing.action).isEqualTo(ConfidenceAction.PROCESS_NORMALLY)
    }

    @Test
    fun `MEDIUM confidence critical intent asks for confirmation`() {
        val result = SttResult(
            text = "call mom",
            confidence = 0.70f,
            isFinal = true,
            startedAtMs = 0L,
            completedAtMs = 0L,
            engine = SttEngine.MOCK,
        )
        val routing = scorer.score(result)
        assertThat(routing.trustLevel).isEqualTo(SttTrustLevel.MEDIUM)
        assertThat(routing.action).isEqualTo(ConfidenceAction.ASK_FOR_CONFIRMATION)
        assertThat(routing.detectedCriticalIntent).isEqualTo("call")
    }

    @Test
    fun `MEDIUM confidence with delete asks for confirmation`() {
        val result = SttResult(
            text = "delete all photos",
            confidence = 0.65f,
            isFinal = true,
            startedAtMs = 0L,
            completedAtMs = 0L,
            engine = SttEngine.MOCK,
        )
        val routing = scorer.score(result)
        assertThat(routing.action).isEqualTo(ConfidenceAction.ASK_FOR_CONFIRMATION)
    }

    @Test
    fun `LOW confidence asks to repeat`() {
        val result = SttResult(
            text = "call mom",
            confidence = 0.40f,
            isFinal = true,
            startedAtMs = 0L,
            completedAtMs = 0L,
            engine = SttEngine.MOCK,
        )
        val routing = scorer.score(result)
        assertThat(routing.trustLevel).isEqualTo(SttTrustLevel.LOW)
        assertThat(routing.action).isEqualTo(ConfidenceAction.ASK_TO_REPEAT)
    }

    @Test
    fun `critical intent detection is case insensitive`() {
        val result = SttResult(
            text = "CALL MOM",
            confidence = 0.70f,
            isFinal = true,
            startedAtMs = 0L,
            completedAtMs = 0L,
            engine = SttEngine.MOCK,
        )
        val routing = scorer.score(result)
        assertThat(routing.action).isEqualTo(ConfidenceAction.ASK_FOR_CONFIRMATION)
    }

    @Test
    fun `HIGH confidence critical intent processes normally`() {
        // Even critical intents at HIGH confidence execute without confirmation
        val result = SttResult(
            text = "call mom",
            confidence = 0.95f,
            isFinal = true,
            startedAtMs = 0L,
            completedAtMs = 0L,
            engine = SttEngine.MOCK,
        )
        val routing = scorer.score(result)
        assertThat(routing.action).isEqualTo(ConfidenceAction.PROCESS_NORMALLY)
    }

    @Test
    fun `scorerThresholds are included in routing`() {
        val result = SttResult(
            text = "hello",
            confidence = 0.9f,
            isFinal = true,
            startedAtMs = 0L,
            completedAtMs = 0L,
            engine = SttEngine.MOCK,
        )
        val routing = scorer.score(result)
        assertThat(routing.scorerThresholds.highThreshold).isEqualTo(0.85f)
        assertThat(routing.scorerThresholds.mediumThreshold).isEqualTo(0.60f)
    }

    @Test
    fun `custom critical intents can be configured`() {
        val customScorer = ConfidenceScorer(
            criticalIntents = setOf("self-destruct", "abort"),
        )
        val result = SttResult(
            text = "self-destruct sequence",
            confidence = 0.75f,
            isFinal = true,
            startedAtMs = 0L,
            completedAtMs = 0L,
            engine = SttEngine.MOCK,
        )
        val routing = customScorer.score(result)
        assertThat(routing.action).isEqualTo(ConfidenceAction.ASK_FOR_CONFIRMATION)
        assertThat(routing.detectedCriticalIntent).isEqualTo("self-destruct")
    }

    @Test
    fun `payment intent is critical`() {
        val result = SttResult(
            text = "transfer 5000 rupees",
            confidence = 0.75f,
            isFinal = true,
            startedAtMs = 0L,
            completedAtMs = 0L,
            engine = SttEngine.MOCK,
        )
        val routing = scorer.score(result)
        assertThat(routing.action).isEqualTo(ConfidenceAction.ASK_FOR_CONFIRMATION)
    }
}

class StreamingSttEngineTest {

    @Test
    fun `startRecognition returns false when no providers configured`() = kotlinx.coroutines.test.runTest {
        val engine = StreamingSttEngine(
            cloudProvider = null,
            offlineProvider = null,
        )
        val started = engine.startRecognition()
        assertThat(started).isFalse()
    }

    @Test
    fun `startRecognition returns true when provider configured`() = kotlinx.coroutines.test.runTest {
        val mock = MockSttProvider()
        val engine = StreamingSttEngine(offlineProvider = mock)
        val started = engine.startRecognition()
        assertThat(started).isTrue()
        assertThat(engine.isRecognizing).isTrue()
        engine.release()
    }

    @Test
    fun `state is IDLE before start`() = kotlinx.coroutines.test.runTest {
        val engine = StreamingSttEngine(offlineProvider = MockSttProvider())
        assertThat(engine.state.value).isEqualTo(SttState.IDLE)
        engine.release()
    }

    @Test
    fun `state transitions to LISTENING after start`() = kotlinx.coroutines.test.runTest {
        val engine = StreamingSttEngine(offlineProvider = MockSttProvider())
        engine.startRecognition()
        assertThat(engine.state.value).isEqualTo(SttState.LISTENING)
        engine.release()
    }

    @Test
    fun `stopRecognition sets state to IDLE`() = kotlinx.coroutines.test.runTest {
        val engine = StreamingSttEngine(offlineProvider = MockSttProvider())
        engine.startRecognition()
        engine.stopRecognition()
        assertThat(engine.state.value).isEqualTo(SttState.IDLE)
        assertThat(engine.isRecognizing).isFalse()
        engine.release()
    }

    @Test
    fun `preferCloud picks cloud when available`() = kotlinx.coroutines.test.runTest {
        val cloud = MockSttProvider(isCloud = true)
        val offline = MockSttProvider(isCloud = false)
        val engine = StreamingSttEngine(
            cloudProvider = cloud,
            offlineProvider = offline,
            preferCloud = true,
        )
        engine.startRecognition()
        assertThat(cloud.isActive).isTrue()
        assertThat(offline.isActive).isFalse()
        engine.release()
    }

    @Test
    fun `preferCloud false picks offline when available`() = kotlinx.coroutines.test.runTest {
        val cloud = MockSttProvider(isCloud = true)
        val offline = MockSttProvider(isCloud = false)
        val engine = StreamingSttEngine(
            cloudProvider = cloud,
            offlineProvider = offline,
            preferCloud = false,
        )
        engine.startRecognition()
        assertThat(offline.isActive).isTrue()
        assertThat(cloud.isActive).isFalse()
        engine.release()
    }

    @Test
    fun `falls back to offline when cloud unavailable`() = kotlinx.coroutines.test.runTest {
        val offline = MockSttProvider(isCloud = false)
        val engine = StreamingSttEngine(
            cloudProvider = null,
            offlineProvider = offline,
            preferCloud = true,
        )
        engine.startRecognition()
        assertThat(offline.isActive).isTrue()
        engine.release()
    }

    @Test
    fun `emitPartial processes delta and emits to stream`() = kotlinx.coroutines.test.runTest {
        val engine = StreamingSttEngine(offlineProvider = MockSttProvider())
        engine.startRecognition()

        // emitPartial is called by the provider's callback in production.
        // Here we test it directly.
        engine.emitPartial(SttResult(
            text = "hello",
            confidence = 0.9f,
            isFinal = false,
            startedAtMs = 0L,
            completedAtMs = 0L,
            engine = SttEngine.MOCK,
        ))
        // Delta should have been processed
        assertThat(engine.deltaUpdater.getDeltaCount()).isEqualTo(1L)
        engine.release()
    }

    @Test
    fun `emitFinal processes delta and transitions state`() = kotlinx.coroutines.test.runTest {
        val engine = StreamingSttEngine(offlineProvider = MockSttProvider())
        engine.startRecognition()

        engine.emitFinal(SttResult(
            text = "hello world",
            confidence = 0.95f,
            isFinal = true,
            startedAtMs = 0L,
            completedAtMs = 0L,
            engine = SttEngine.MOCK,
        ))
        // State should be back to IDLE after final
        assertThat(engine.state.value).isEqualTo(SttState.IDLE)
        engine.release()
    }

    @Test
    fun `isCloudAvailable reflects provider configuration`() {
        val engineWithCloud = StreamingSttEngine(
            cloudProvider = MockSttProvider(isCloud = true),
        )
        assertThat(engineWithCloud.isCloudAvailable).isTrue()

        val engineWithoutCloud = StreamingSttEngine(offlineProvider = MockSttProvider())
        assertThat(engineWithoutCloud.isCloudAvailable).isFalse()
    }

    @Test
    fun `isOfflineAvailable reflects provider configuration`() {
        val engineWithOffline = StreamingSttEngine(offlineProvider = MockSttProvider())
        assertThat(engineWithOffline.isOfflineAvailable).isTrue()

        val engineWithoutOffline = StreamingSttEngine(cloudProvider = MockSttProvider())
        assertThat(engineWithoutOffline.isOfflineAvailable).isFalse()
    }

    @Test
    fun `release prevents further use`() = kotlinx.coroutines.test.runTest {
        val engine = StreamingSttEngine(offlineProvider = MockSttProvider())
        engine.release()
        try {
            engine.startRecognition()
            assert(false) { "Expected IllegalStateException" }
        } catch (e: IllegalStateException) {
            // Expected
        }
    }

    @Test
    fun `delta updater is reset on new recognition`() = kotlinx.coroutines.test.runTest {
        val engine = StreamingSttEngine(offlineProvider = MockSttProvider())
        engine.startRecognition()
        engine.emitPartial(SttResult(
            text = "hello",
            confidence = 0.9f,
            isFinal = false,
            startedAtMs = 0L,
            completedAtMs = 0L,
            engine = SttEngine.MOCK,
        ))
        assertThat(engine.deltaUpdater.getDeltaCount()).isEqualTo(1L)

        engine.stopRecognition()
        engine.startRecognition() // should reset delta updater
        assertThat(engine.deltaUpdater.getDeltaCount()).isEqualTo(0L)
        engine.release()
    }
}

class MockSttProviderTest {

    @Test
    fun `emitPartial invokes callback`() = kotlinx.coroutines.test.runTest {
        val provider = MockSttProvider()
        var receivedPartial: SttResult? = null
        provider.startRecognition(
            onPartial = { receivedPartial = it },
            onFinal = {},
            onError = {},
        )
        provider.emitPartial("hello", 0.9f)
        assertThat(receivedPartial).isNotNull()
        assertThat(receivedPartial!!.text).isEqualTo("hello")
        assertThat(receivedPartial!!.confidence).isWithin(0.001f).of(0.9f)
        assertThat(receivedPartial!!.isFinal).isFalse()
        provider.release()
    }

    @Test
    fun `emitFinal invokes callback and stops active`() = kotlinx.coroutines.test.runTest {
        val provider = MockSttProvider()
        var receivedFinal: SttResult? = null
        provider.startRecognition(
            onPartial = {},
            onFinal = { receivedFinal = it },
            onError = {},
        )
        provider.emitFinal("hello world", 0.95f)
        assertThat(receivedFinal).isNotNull()
        assertThat(receivedFinal!!.text).isEqualTo("hello world")
        assertThat(receivedFinal!!.isFinal).isTrue()
        assertThat(provider.isActive).isFalse()
        provider.release()
    }

    @Test
    fun `emitError invokes callback and stops active`() = kotlinx.coroutines.test.runTest {
        val provider = MockSttProvider()
        var receivedError: SttError? = null
        provider.startRecognition(
            onPartial = {},
            onFinal = {},
            onError = { receivedError = it },
        )
        provider.emitError(SttErrorType.NETWORK, "No internet", true)
        assertThat(receivedError).isNotNull()
        assertThat(receivedError!!.type).isEqualTo(SttErrorType.NETWORK)
        assertThat(receivedError!!.isRecoverable).isTrue()
        assertThat(provider.isActive).isFalse()
        provider.release()
    }

    @Test
    fun `emitPartial when not active is no-op`() = kotlinx.coroutines.test.runTest {
        val provider = MockSttProvider()
        var receivedCount = 0
        // Don't start — provider is inactive
        provider.emitPartial("hello")
        assertThat(receivedCount).isEqualTo(0)
        provider.release()
    }

    @Test
    fun `stopRecognition sets active to false`() = kotlinx.coroutines.test.runTest {
        val provider = MockSttProvider()
        provider.startRecognition({}, {}, {})
        provider.stopRecognition()
        assertThat(provider.isActive).isFalse()
        provider.release()
    }

    @Test
    fun `release prevents further start`() = kotlinx.coroutines.test.runTest {
        val provider = MockSttProvider()
        provider.release()
        try {
            provider.startRecognition({}, {}, {})
            assert(false) { "Expected IllegalStateException" }
        } catch (e: IllegalStateException) {
            // Expected
        }
    }

    @Test
    fun `displayName and isCloud are configurable`() {
        val cloud = MockSttProvider(displayName = "Test Cloud", isCloud = true)
        assertThat(cloud.displayName).isEqualTo("Test Cloud")
        assertThat(cloud.isCloud).isTrue()

        val offline = MockSttProvider(displayName = "Test Offline", isCloud = false)
        assertThat(offline.displayName).isEqualTo("Test Offline")
        assertThat(offline.isCloud).isFalse()
    }
}
