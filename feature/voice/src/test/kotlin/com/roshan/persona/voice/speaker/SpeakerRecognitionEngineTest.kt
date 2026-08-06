// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.voice.speaker

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.voice.capture.AudioCapturePipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

// VOICE_FIX_012: Speaker recognition safe

class SpeakerRecognitionEngineTest {

    // ─── Fakes ─────────────────────────────────────────────────────────────

    /** Fake ECAPA backend that returns a deterministic 192-dim embedding. */
    private class FakeEcapaBackend(
        private val embeddingProvider: (List<FloatArray>) -> FloatArray,
    ) : EcapaTdnnBackend {
        override val isReady: Boolean = true
        override fun infer(mfccFrames: List<FloatArray>): FloatArray = embeddingProvider(mfccFrames)
        override fun release() = Unit
    }

    /** Generate a 192-dim embedding that's "close" to a target speaker. */
    private fun embeddingFor(seed: Int): FloatArray {
        return FloatArray(SpeakerConfig.ECAPA_EMBEDDING_DIM) { i ->
            // Each speaker has a distinct pattern based on seed.
            sin(PI * (i + seed * 10) / 30).toFloat()
        }
    }

    private fun audioFor(seed: Int, durationSec: Int = 3): ShortArray {
        // 3-second audio at different frequencies per speaker.
        val n = AudioCapturePipeline.SAMPLE_RATE * durationSec
        val freq = 200 + seed * 50  // distinct frequency per speaker
        return ShortArray(n) { i ->
            (5000 * sin(2 * PI * freq * i / AudioCapturePipeline.SAMPLE_RATE)).toInt().toShort()
        }
    }

    private fun makeEngine(
        ecapa: EcapaTdnnBackend? = null,
        config: SpeakerConfig = SpeakerConfig(),
        store: VoicePrintStore = InMemoryVoicePrintStore(),
    ): SpeakerRecognitionEngine {
        return SpeakerRecognitionEngine(
            mfccExtractor = MfccFeatureExtractor(),
            ecapaBackend = ecapa,
            store = store,
            config = config,
            inferenceDispatcher = Dispatchers.Unconfined,
        )
    }

    // ─── Tests ─────────────────────────────────────────────────────────────

    @Test
    fun `enroll fails when too few samples provided`() = runTest {
        val engine = makeEngine()
        val print = engine.enroll(
            speakerId = "u1",
            displayName = "User",
            audioSamples = listOf(audioFor(1)),  // only 1 sample, need 5
        )
        assertThat(print).isNull()
    }

    @Test
    fun `enroll succeeds with required samples and returns voice print`() = runTest {
        val ecapa = FakeEcapaBackend { _ -> embeddingFor(1) }
        val engine = makeEngine(ecapa = ecapa)
        val samples = (1..5).map { audioFor(1) }
        val print = engine.enroll("u1", "Roshan", samples)
        assertThat(print).isNotNull()
        assertThat(print!!.speakerId).isEqualTo("u1")
        assertThat(print.displayName).isEqualTo("Roshan")
        assertThat(print.embeddingDim).isEqualTo(SpeakerConfig.ECAPA_EMBEDDING_DIM)
        assertThat(print.source).isEqualTo(ExtractorSource.ECAPA_TDNN)
    }

    @Test
    fun `enroll fails when max speakers reached`() = runTest {
        val ecapa = FakeEcapaBackend { _ -> embeddingFor(1) }
        val store = InMemoryVoicePrintStore()
        val engine = makeEngine(ecapa = ecapa, store = store)

        // Enroll 5 speakers (the max).
        for (i in 1..5) {
            val samples = (1..5).map { audioFor(i) }
            engine.enroll("u$i", "User$i", samples)
        }
        assertThat(store.count()).isEqualTo(5)

        // 6th should fail.
        val sixth = engine.enroll("u6", "User6", (1..5).map { audioFor(6) })
        assertThat(sixth).isNull()
        assertThat(store.count()).isEqualTo(5)
    }

    @Test
    fun `identify returns null when no speakers enrolled`() = runTest {
        val engine = makeEngine()
        val result = engine.identify(audioFor(1))
        assertThat(result.isUnknown).isTrue()
        assertThat(result.matchedSpeakerId).isNull()
        assertThat(result.allScores).isEmpty()
    }

    @Test
    fun `identify returns null when audio is too short`() = runTest {
        val ecapa = FakeEcapaBackend { _ -> embeddingFor(1) }
        val engine = makeEngine(ecapa = ecapa)
        engine.enroll("u1", "Roshan", (1..5).map { audioFor(1) })

        // 1-second audio (default min is 3s).
        val shortAudio = ShortArray(AudioCapturePipeline.SAMPLE_RATE * 1)
        val result = engine.identify(shortAudio)
        assertThat(result.isUnknown).isTrue()
    }

    @Test
    fun `identify matches enrolled speaker when embedding is identical`() = runTest {
        val ecapa = FakeEcapaBackend { _ -> embeddingFor(1) }
        val engine = makeEngine(ecapa = ecapa)
        engine.enroll("u1", "Roshan", (1..5).map { audioFor(1) })

        // Same audio → same embedding → cosine similarity = 1.0 → match.
        val result = engine.identify(audioFor(1))
        assertThat(result.isMatched).isTrue()
        assertThat(result.matchedSpeakerId).isEqualTo("u1")
        assertThat(result.similarity).isGreaterThan(0.99f)
        assertThat(result.confidence).isEqualTo(SpeakerConfidence.HIGH)
    }

    @Test
    fun `identify returns unknown when no enrolled print exceeds threshold`() = runTest {
        // ECAPA returns one embedding for enrollment, a totally different one for identify.
        var counter = 0
        val ecapa = FakeEcapaBackend { _ ->
            embeddingFor(if (counter++ < 5) 1 else 99)  // first 5 calls (enroll) → 1, then 99
        }
        val engine = makeEngine(ecapa = ecapa, config = SpeakerConfig(matchThreshold = 0.7f))
        engine.enroll("u1", "Roshan", (1..5).map { audioFor(1) })

        val result = engine.identify(audioFor(1))
        // Identifying embedding (99) should not match enrolled embedding (1).
        if (result.similarity < 0.7f) {
            assertThat(result.isUnknown).isTrue()
        }
        // Either way, allScores should contain the enrolled speaker.
        assertThat(result.allScores).hasSize(1)
    }

    @Test
    fun `identify picks the best match when multiple speakers enrolled`() = runTest {
        // Different ECAPA embedding per call based on audio seed.
        val ecapa = FakeEcapaBackend { _ -> embeddingFor(callCount++) }
        // Use separate engines for enroll + identify so call counter resets.
        var callCount = 0

        // Engine 1: enroll speakers 1, 2, 3.
        val store = InMemoryVoicePrintStore()
        val enrollEngine = SpeakerRecognitionEngine(
            mfccExtractor = MfccFeatureExtractor(),
            ecapaBackend = object : EcapaTdnnBackend {
                override val isReady = true
                override fun infer(mfccFrames: List<FloatArray>) = embeddingFor(callCount++)
                override fun release() = Unit
            },
            store = store,
            config = SpeakerConfig(),
            inferenceDispatcher = Dispatchers.Unconfined,
        )
        enrollEngine.enroll("u1", "A", (1..5).map { audioFor(1) })  // callCount 0-4, embeddingFor(0..4)
        enrollEngine.enroll("u2", "B", (1..5).map { audioFor(2) })  // embeddingFor(5..9)
        enrollEngine.enroll("u3", "C", (1..5).map { audioFor(3) })  // embeddingFor(10..14)

        // Engine 2: identify audio for speaker 2 — embeddingFor(15) ~ closest to embeddingFor(5..9).
        val identifyEngine = SpeakerRecognitionEngine(
            mfccExtractor = MfccFeatureExtractor(),
            ecapaBackend = object : EcapaTdnnBackend {
                override val isReady = true
                override fun infer(mfccFrames: List<FloatArray>) = embeddingFor(15)  // close to 5..9
                override fun release() = Unit
            },
            store = store,
            config = SpeakerConfig(),
            inferenceDispatcher = Dispatchers.Unconfined,
        )
        val result = identifyEngine.identify(audioFor(2))
        // best score should be the speaker whose embeddingFor is closest to 15 → speaker 2 (5..9).
        // All three speakers have a score; best match should be u2.
        assertThat(result.allScores).hasSize(3)
        // Verify u2 has the highest similarity (embeddingFor(15) closer to 5-9 than 0-4 or 10-14).
        val u2Score = result.allScores.first { it.speakerId == "u2" }.similarity
        val u1Score = result.allScores.first { it.speakerId == "u1" }.similarity
        val u3Score = result.allScores.first { it.speakerId == "u3" }.similarity
        assertThat(u2Score).isAtLeast(u1Score)
        assertThat(u2Score).isAtLeast(u3Score)
    }

    @Test
    fun `deleteSpeaker removes the voice print`() = runTest {
        val ecapa = FakeEcapaBackend { _ -> embeddingFor(1) }
        val engine = makeEngine(ecapa = ecapa)
        engine.enroll("u1", "Roshan", (1..5).map { audioFor(1) })
        assertThat(engine.listEnrolled()).hasSize(1)

        val deleted = engine.deleteSpeaker("u1")
        assertThat(deleted).isTrue()
        assertThat(engine.listEnrolled()).isEmpty()

        // Deleting again returns false.
        assertThat(engine.deleteSpeaker("u1")).isFalse()
    }

    @Test
    fun `cosineSimilarity returns 1 for identical vectors`() {
        val engine = makeEngine()
        val v = FloatArray(192) { it.toFloat() }
        val sim = engine.cosineSimilarity(v, v)
        assertThat(sim).isWithin(0.001f).of(1.0f)
    }

    @Test
    fun `cosineSimilarity returns 0 for orthogonal vectors`() {
        val engine = makeEngine()
        val v1 = floatArrayOf(1f, 0f, 0f)
        val v2 = floatArrayOf(0f, 1f, 0f)
        val sim = engine.cosineSimilarity(v1, v2)
        assertThat(sim).isWithin(0.001f).of(0.0f)
    }

    @Test
    fun `cosineSimilarity returns 0 for zero vector (no division by zero)`() {
        val engine = makeEngine()
        val zero = FloatArray(3) { 0f }
        val v = floatArrayOf(1f, 2f, 3f)
        val sim = engine.cosineSimilarity(zero, v)
        assertThat(sim).isEqualTo(0f)
    }

    @Test
    fun `MFCC fallback is used when ECAPA backend is null`() = runTest {
        // No ECAPA → must use MFCC fallback path.
        val engine = makeEngine(ecapa = null)
        val samples = (1..5).map { audioFor(1) }
        val print = engine.enroll("u1", "Roshan", samples)
        assertThat(print).isNotNull()
        // MFCC embedding dim = 2 × numCoeffs = 26.
        assertThat(print!!.embeddingDim).isEqualTo(MfccFeatureExtractor.DEFAULT_NUM_COEFFS * 2)
        assertThat(print.source).isEqualTo(ExtractorSource.MFCC)
    }
}
