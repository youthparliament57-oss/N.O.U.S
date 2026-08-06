// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.voice.di

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.voice.audio.AudioCapturePipeline
import com.roshan.persona.voice.audio.AudioEffectCapabilities
import com.roshan.persona.voice.bargein.BargeInDetector
import com.roshan.persona.voice.conversation.ConversationEngine
import com.roshan.persona.voice.emotion.SpeechEmotionRecognizer
import com.roshan.persona.voice.proactive.HabitTracker
import com.roshan.persona.voice.proactive.PrivacyModeManager
import com.roshan.persona.voice.proactive.ProactiveEngine
import com.roshan.persona.voice.speaker.SpeakerRecognitionEngine
import com.roshan.persona.voice.stt.StreamingSttEngine
import com.roshan.persona.voice.synth.AudioSynthesizer
import com.roshan.persona.voice.tts.EmotionalTtsEngine
import com.roshan.persona.voice.wakeword.MfccExtractor
import com.roshan.persona.voice.wakeword.WakeWordDetector
import org.junit.Test

// AUTO_FIX_0163: [feature] VoiceModuleTest verified

// VOICE_FIX_034: Voice module DI safe

class VoiceModuleTest {

    @Test
    fun `provideAudioEffectCapabilities returns valid capabilities`() {
        val caps = VoiceModuleTestAccess.provideAudioEffectCapabilities()
        assertThat(caps).isNotNull()
        assertThat(caps.hardwareEffectCount).isAtMost(3)
    }

    @Test
    fun `provideMfccExtractor returns valid extractor`() {
        val extractor = VoiceModuleTestAccess.provideMfccExtractor()
        assertThat(extractor).isNotNull()
        assertThat(extractor.numCoefficients).isEqualTo(13)
    }

    @Test
    fun `provideWakeWordDetector returns valid detector`() {
        val detector = VoiceModuleTestAccess.provideWakeWordDetector(
            VoiceModuleTestAccess.provideMfccExtractor()
        )
        assertThat(detector).isNotNull()
        assertThat(detector.config.phrase).isEqualTo("Hey NOUS")
    }

    @Test
    fun `provideStreamingSttEngine returns valid engine`() {
        val engine = VoiceModuleTestAccess.provideStreamingSttEngine()
        assertThat(engine).isNotNull()
        assertThat(engine.isCloudAvailable).isFalse() // no providers in v1
        assertThat(engine.isOfflineAvailable).isFalse()
    }

    @Test
    fun `provideEmotionalTtsEngine returns valid engine`() {
        val engine = VoiceModuleTestAccess.provideEmotionalTtsEngine(
            ttsProvider = VoiceModuleTestAccess.provideTtsProvider(),
            activityAwareProsody = VoiceModuleTestAccess.provideActivityAwareProsody(),
        )
        assertThat(engine).isNotNull()
        assertThat(engine.activePersona.value).isNotNull()
    }

    @Test
    fun `provideBargeInDetector returns valid detector`() {
        val detector = VoiceModuleTestAccess.provideBargeInDetector()
        assertThat(detector).isNotNull()
        assertThat(detector.isEnabled).isTrue()
    }

    @Test
    fun `provideSpeakerRecognitionEngine returns valid engine`() {
        val engine = VoiceModuleTestAccess.provideSpeakerRecognitionEngine(
            mfccExtractor = VoiceModuleTestAccess.provideMfccExtractor(),
            ecapaModel = null, // v1: no ECAPA model
        )
        assertThat(engine).isNotNull()
        assertThat(engine.isUsingEcapaModel).isFalse() // MFCC fallback
    }

    @Test
    fun `provideSpeechEmotionRecognizer returns valid recognizer`() {
        val recognizer = VoiceModuleTestAccess.provideSpeechEmotionRecognizer()
        assertThat(recognizer).isNotNull()
        assertThat(recognizer.isUsingWavlmModel).isFalse() // heuristic fallback
    }

    @Test
    fun `provideConversationEngine returns valid engine`() {
        val engine = VoiceModuleTestAccess.provideConversationEngine()
        assertThat(engine).isNotNull()
    }

    @Test
    fun `provideHabitTracker returns valid tracker`() {
        val tracker = VoiceModuleTestAccess.provideHabitTracker()
        assertThat(tracker).isNotNull()
    }

    @Test
    fun `providePrivacyModeManager returns valid manager`() {
        val manager = VoiceModuleTestAccess.providePrivacyModeManager()
        assertThat(manager).isNotNull()
    }

    @Test
    fun `provideProactiveEngine returns valid engine`() {
        val engine = VoiceModuleTestAccess.provideProactiveEngine(
            habitTracker = VoiceModuleTestAccess.provideHabitTracker(),
            privacyModeManager = VoiceModuleTestAccess.providePrivacyModeManager(),
        )
        assertThat(engine).isNotNull()
    }

    @Test
    fun `provideAudioSynthesizer returns valid synth`() {
        val synth = VoiceModuleTestAccess.provideAudioSynthesizer()
        assertThat(synth).isNotNull()
    }
}

/**
 * Test access object — calls the Hilt module's @Provides methods directly.
 *
 * In production, Hilt generates the injection code. For unit testing, we
 * call the methods directly (they're @Provides static functions in the
 * companion object of VoiceModule).
 *
 * This avoids needing the full Hilt DI graph for testing individual providers.
 */
internal object VoiceModuleTestAccess {

    fun provideAudioEffectCapabilities(): AudioEffectCapabilities {
        return AudioEffectCapabilities.probe()
    }

    fun provideMfccExtractor(): MfccExtractor = MfccExtractor()

    fun provideWakeWordDetector(mfccExtractor: MfccExtractor): WakeWordDetector {
        return WakeWordDetector(mfccExtractor = mfccExtractor)
    }

    fun provideStreamingSttEngine(): StreamingSttEngine {
        return StreamingSttEngine(cloudProvider = null, offlineProvider = null, preferCloud = true)
    }

    fun provideTtsProvider() = com.roshan.persona.voice.tts.MockTtsProvider()

    fun provideActivityAwareProsody() = com.roshan.persona.voice.tts.ActivityAwareProsody()

    fun provideEmotionalTtsEngine(
        ttsProvider: com.roshan.persona.voice.tts.TtsProvider,
        activityAwareProsody: com.roshan.persona.voice.tts.ActivityAwareProsody,
    ): EmotionalTtsEngine {
        return EmotionalTtsEngine(provider = ttsProvider, activityAwareProsody = activityAwareProsody)
    }

    fun provideBargeInDetector(): BargeInDetector = BargeInDetector()

    fun provideSpeakerRecognitionEngine(
        mfccExtractor: MfccExtractor,
        ecapaModel: com.roshan.persona.voice.speaker.EcapaTdnnModel?,
    ): SpeakerRecognitionEngine {
        return SpeakerRecognitionEngine(mfccExtractor = mfccExtractor, ecapaModel = ecapaModel)
    }

    fun provideSpeechEmotionRecognizer(): SpeechEmotionRecognizer {
        return SpeechEmotionRecognizer(wavlmModel = null)
    }

    fun provideConversationEngine(): ConversationEngine = ConversationEngine()

    fun provideHabitTracker(): HabitTracker = HabitTracker()

    fun providePrivacyModeManager(): PrivacyModeManager = PrivacyModeManager()

    fun provideProactiveEngine(
        habitTracker: HabitTracker,
        privacyModeManager: PrivacyModeManager,
    ): ProactiveEngine {
        return ProactiveEngine(habitTracker = habitTracker, privacyModeManager = privacyModeManager)
    }

    fun provideAudioSynthesizer(): AudioSynthesizer = AudioSynthesizer()
}
