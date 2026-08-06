// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.voice.clone

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// AUTO_FIX_0177: [feature] ClonedVoiceProfileTest verified

// VOICE_FIX_020: Cloned profile secure

class ClonedVoiceProfileTest {

    private fun validProfile(
        id: String = "p1",
        owner: String = "u1",
        vitsPath: String? = "/tmp/vits.onnx",
    ) = ClonedVoiceProfile(
        profileId = id,
        ownerSpeakerId = owner,
        embedding = FloatArray(192) { it.toFloat() },
        pitch = 150f,
        formants = Formants(500f, 1500f, 2500f),
        prosody = ProsodyCharacteristics(4.5f, 0.02f, 0.05f),
        vitsModelPath = vitsPath,
        watermarkSeed = 12345L,
        quality = CloneQualityMetrics(snrDb = 20f, durationSec = 30f, clippingRatio = 0.001f),
    )

    @Test
    fun `ClonedVoiceProfile validates required fields`() {
        try {
            validProfile(id = "")
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("profileId")
        }
        try {
            validProfile(owner = "")
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("ownerSpeakerId")
        }
    }

    @Test
    fun `ClonedVoiceProfile rejects non-positive pitch and formants`() {
        try {
            validProfile().copy(pitch = 0f)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("pitch")
        }
        try {
            validProfile().copy(formants = Formants(0f, 1500f, 2500f))
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("formants")
        }
    }

    @Test
    fun `supportsNeuralSynthesis is true iff vitsModelPath is not null`() {
        assertThat(validProfile().supportsNeuralSynthesis).isTrue()
        assertThat(validProfile(vitsPath = null).supportsNeuralSynthesis).isFalse()
    }

    @Test
    fun `ClonedVoiceProfile equals compares embedding content`() {
        val p1 = validProfile()
        val p2 = validProfile().copy(embedding = p1.embedding.copyOf())
        assertThat(p1).isEqualTo(p2)
        assertThat(p1.hashCode()).isEqualTo(p2.hashCode())
    }

    @Test
    fun `ProsodyCharacteristics rejects out-of-bounds jitter and shimmer`() {
        try {
            ProsodyCharacteristics(rate = 4.5f, jitter = 1.5f, shimmer = 0.05f)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("jitter")
        }
        try {
            ProsodyCharacteristics(rate = 4.5f, jitter = 0.02f, shimmer = -0.1f)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("shimmer")
        }
    }

    @Test
    fun `CloneQualityMetrics passes only when all thresholds met`() {
        // All pass.
        assertThat(CloneQualityMetrics(20f, 30f, 0.001f).passes).isTrue()
        // SNR too low.
        assertThat(CloneQualityMetrics(10f, 30f, 0.001f).passes).isFalse()
        // Duration too short.
        assertThat(CloneQualityMetrics(20f, 20f, 0.001f).passes).isFalse()
        // Clipping too high.
        assertThat(CloneQualityMetrics(20f, 30f, 0.05f).passes).isFalse()
    }

    // ─── EthicalGuardrail tests ───────────────────────────────────────────

    @Test
    fun `EthicalGuardrail allows own-voice cloning when voice print verified`() {
        val guardrail = EthicalGuardrail()
        val request = CloneRequest(
            ownershipType = OwnershipType.OWN_VOICE,
            voicePrintVerified = true,
        )
        assertThat(guardrail.check(request)).isEqualTo(GuardrailDecision.Allowed)
    }

    @Test
    fun `EthicalGuardrail denies own-voice cloning without voice print verification`() {
        val guardrail = EthicalGuardrail()
        val request = CloneRequest(
            ownershipType = OwnershipType.OWN_VOICE,
            voicePrintVerified = false,
        )
        val decision = guardrail.check(request)
        assertThat(decision).isInstanceOf(GuardrailDecision.Denied::class.java)
        assertThat((decision as GuardrailDecision.Denied).reason).contains("voice print")
    }

    @Test
    fun `EthicalGuardrail denies celebrity cloning without rights acknowledgment`() {
        val guardrail = EthicalGuardrail()
        val request = CloneRequest(
            ownershipType = OwnershipType.CELEBRITY,
            rightsAcknowledged = false,
        )
        val decision = guardrail.check(request)
        assertThat(decision).isInstanceOf(GuardrailDecision.Denied::class.java)
        assertThat((decision as GuardrailDecision.Denied).reason).contains("rights")
    }

    @Test
    fun `EthicalGuardrail allows celebrity cloning with rights acknowledgment`() {
        val guardrail = EthicalGuardrail()
        val request = CloneRequest(
            ownershipType = OwnershipType.CELEBRITY,
            rightsAcknowledged = true,
        )
        assertThat(guardrail.check(request)).isEqualTo(GuardrailDecision.Allowed)
    }

    @Test
    fun `EthicalGuardrail always allows deceased-loved-one cloning`() {
        val guardrail = EthicalGuardrail()
        val request = CloneRequest(
            ownershipType = OwnershipType.DECEASED_LOVED_ONE,
            voicePrintVerified = false,
            rightsAcknowledged = false,
        )
        assertThat(guardrail.check(request)).isEqualTo(GuardrailDecision.Allowed)
    }
}
