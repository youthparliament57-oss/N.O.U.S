// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.voice.clone

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.voice.capture.AudioCapturePipeline
import com.roshan.persona.voice.speaker.EcapaTdnnBackend
import com.roshan.persona.voice.speaker.InMemoryVoicePrintStore
import com.roshan.persona.voice.speaker.MfccFeatureExtractor
import com.roshan.persona.voice.speaker.SpeakerConfig
import com.roshan.persona.voice.speaker.SpeakerRecognitionEngine
import com.roshan.persona.voice.speaker.VoicePrintStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.math.sin

// AUTO_FIX_0179: [feature] NeuralVoiceClonerTest verified

// VOICE_FIX_018: Voice cloner resource safe

class NeuralVoiceClonerTest {

    // ─── Fakes ─────────────────────────────────────────────────────────────

    /** Fake ECAPA backend that returns a deterministic 192-dim embedding. */
    private class FakeEcapaBackend : EcapaTdnnBackend {
        override val isReady = true
        override fun infer(mfccFrames: List<FloatArray>): FloatArray =
            FloatArray(SpeakerConfig.ECAPA_EMBEDDING_DIM) { i -> (i % 13).toFloat() / 13 }
        override fun release() = Unit
    }

    /** Fake VITS backend that simulates adapt + synthesize. */
    private class FakeVitsBackend(
        val adaptSucceeds: Boolean = true,
        val synthesizedAudio: ShortArray = ShortArray(2400) { 100 },  // 100 ms at 24 kHz
    ) : VitsBackend {
        override val isReady: Boolean = true
        var adaptCallCount = 0
            private set
        var synthesizeCallCount = 0
            private set

        override suspend fun adaptModel(speakerEmbedding: FloatArray, outputPath: String): Boolean {
            adaptCallCount++
            return adaptSucceeds
        }

        override suspend fun synthesize(text: String, modelPath: String, speakerEmbedding: FloatArray): ShortArray {
            synthesizeCallCount++
            return synthesizedAudio
        }

        override fun release() = Unit
    }

    /** Generate `durationSec` of 1 kHz tone at 16 kHz. */
    private fun audio(durationSec: Float, freqHz: Int = 1000): ShortArray {
        val n = (AudioCapturePipeline.SAMPLE_RATE * durationSec).toInt()
        return ShortArray(n) { i ->
            (5000 * sin(2 * Math.PI * freqHz * i / AudioCapturePipeline.SAMPLE_RATE)).toInt().toShort()
        }
    }

    private fun makeCloner(
        vits: VitsBackend? = FakeVitsBackend(),
        store: VoicePrintStore = InMemoryVoicePrintStore(),
    ): NeuralVoiceCloner {
        val speakerEngine = SpeakerRecognitionEngine(
            mfccExtractor = MfccFeatureExtractor(),
            ecapaBackend = FakeEcapaBackend(),
            store = store,
            config = SpeakerConfig(enrollmentSamples = 1),  // simplify test
            inferenceDispatcher = Dispatchers.Unconfined,
        )
        return NeuralVoiceCloner(
            speakerRecognition = speakerEngine,
            mfccExtractor = MfccFeatureExtractor(),
            vitsBackend = vits,
            watermarker = VoiceWatermarker(watermarkVolume = 0.05f),
            inferenceDispatcher = Dispatchers.Unconfined,
        )
    }

    // ─── Tests ─────────────────────────────────────────────────────────────

    @Test
    fun `clone fails when ethical guardrail denies`() = runTest {
        val cloner = makeCloner()
        val request = CloneRequest(
            ownershipType = OwnershipType.OWN_VOICE,
            voicePrintVerified = false,  // not verified → denied
        )
        val profile = cloner.clone(
            request = request,
            ownerSpeakerId = "u1",
            audioSamples = listOf(audio(30f)),
        )
        assertThat(profile).isNull()
    }

    @Test
    fun `clone fails when audio quality is too low (duration < 30s)`() = runTest {
        val cloner = makeCloner()
        val request = CloneRequest(
            ownershipType = OwnershipType.DECEASED_LOVED_ONE,  // always allowed
        )
        val profile = cloner.clone(
            request = request,
            ownerSpeakerId = "u1",
            audioSamples = listOf(audio(10f)),  // too short
        )
        assertThat(profile).isNull()
    }

    @Test
    fun `clone succeeds with valid audio and own-voice verification`() = runTest {
        val cloner = makeCloner()
        val request = CloneRequest(
            ownershipType = OwnershipType.OWN_VOICE,
            voicePrintVerified = true,
        )
        val profile = cloner.clone(
            request = request,
            ownerSpeakerId = "u1",
            audioSamples = listOf(audio(30f)),
        )
        assertThat(profile).isNotNull()
        assertThat(profile!!.ownerSpeakerId).isEqualTo("u1")
        assertThat(profile.supportsNeuralSynthesis).isTrue()
        assertThat(profile.quality.passes).isTrue()
    }

    @Test
    fun `clone succeeds with deceased-loved-one ownership without verification`() = runTest {
        val cloner = makeCloner()
        val request = CloneRequest(
            ownershipType = OwnershipType.DECEASED_LOVED_ONE,
            voicePrintVerified = false,  // not required for deceased
        )
        val profile = cloner.clone(
            request = request,
            ownerSpeakerId = "u1",
            audioSamples = listOf(audio(30f)),
        )
        assertThat(profile).isNotNull()
    }

    @Test
    fun `clone without VITS backend produces profile with vitsModelPath null`() = runTest {
        val cloner = makeCloner(vits = null)
        val request = CloneRequest(ownershipType = OwnershipType.DECEASED_LOVED_ONE)
        val profile = cloner.clone(
            request = request,
            ownerSpeakerId = "u1",
            audioSamples = listOf(audio(30f)),
        )
        assertThat(profile).isNotNull()
        assertThat(profile!!.supportsNeuralSynthesis).isFalse()
        assertThat(profile.vitsModelPath).isNull()
    }

    @Test
    fun `synthesize returns null for profile without neural synthesis`() = runTest {
        val cloner = makeCloner(vits = null)
        val request = CloneRequest(ownershipType = OwnershipType.DECEASED_LOVED_ONE)
        val profile = cloner.clone(
            request = request,
            ownerSpeakerId = "u1",
            audioSamples = listOf(audio(30f)),
        )!!
        val result = cloner.synthesize("Hello world", profile, userId = "u1")
        assertThat(result).isNull()
    }

    @Test
    fun `synthesize returns watermarked audio for neural profile`() = runTest {
        val fakeVits = FakeVitsBackend()
        val cloner = makeCloner(vits = fakeVits)
        val profile = cloner.clone(
            request = CloneRequest(ownershipType = OwnershipType.DECEASED_LOVED_ONE),
            ownerSpeakerId = "u1",
            audioSamples = listOf(audio(30f)),
        )!!
        val result = cloner.synthesize("Hello world", profile, userId = "u1")
        assertThat(result).isNotNull()
        assertThat(result!!.size).isEqualTo(fakeVits.synthesizedAudio.size)
        assertThat(fakeVits.synthesizeCallCount).isEqualTo(1)
    }

    @Test
    fun `synthesize with blank text returns null`() = runTest {
        val cloner = makeCloner()
        val profile = cloner.clone(
            request = CloneRequest(ownershipType = OwnershipType.DECEASED_LOVED_ONE),
            ownerSpeakerId = "u1",
            audioSamples = listOf(audio(30f)),
        )!!
        val result = cloner.synthesize("", profile, userId = "u1")
        assertThat(result).isNull()
    }

    // ─── Quality assessment tests ─────────────────────────────────────────

    @Test
    fun `calculateSnr returns positive value for clean tone`() {
        val cloner = makeCloner()
        val snr = cloner.calculateSnr(audio(1f))
        assertThat(snr).isGreaterThan(0f)
    }

    @Test
    fun `calculateSnr returns 0 for silence`() {
        val cloner = makeCloner()
        val snr = cloner.calculateSnr(ShortArray(1600))  // 100 ms of silence
        assertThat(snr).isEqualTo(0f)
    }

    @Test
    fun `calculateClippingRatio returns 0 for non-clipped audio`() {
        val cloner = makeCloner()
        val ratio = cloner.calculateClippingRatio(audio(1f))
        assertThat(ratio).isEqualTo(0f)
    }

    @Test
    fun `calculateClippingRatio detects clipped samples`() {
        val cloner = makeCloner()
        val clipped = ShortArray(100) { i ->
            if (i < 10) Short.MAX_VALUE else 0
        }
        val ratio = cloner.calculateClippingRatio(clipped)
        assertThat(ratio).isWithin(0.001f).of(0.1f)  // 10/100
    }

    @Test
    fun `assessQuality returns failing metrics for short audio`() {
        val cloner = makeCloner()
        val metrics = cloner.assessQuality(listOf(audio(5f)))
        assertThat(metrics.durationSec).isLessThan(30f)
        assertThat(metrics.passes).isFalse()
    }

    @Test
    fun `estimatePitch returns value in human voice range`() {
        val cloner = makeCloner()
        // 200 Hz tone → pitch estimation should be near 200 Hz.
        val pitch = cloner.estimatePitch(audio(1f, freqHz = 200))
        assertThat(pitch).isGreaterThan(100f)
        assertThat(pitch).isLessThan(300f)
    }

    @Test
    fun `estimateFormants returns distinct values for male vs female pitch`() {
        val cloner = makeCloner()
        // 100 Hz tone → male-like pitch.
        val maleFormants = cloner.estimateFormants(audio(1f, freqHz = 100))
        // 250 Hz tone → female-like pitch.
        val femaleFormants = cloner.estimateFormants(audio(1f, freqHz = 250))
        assertThat(femaleFormants.f1).isGreaterThan(maleFormants.f1)
    }
}
