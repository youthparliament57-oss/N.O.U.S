// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.voice.wakeword

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.voice.audio.AudioChunk
import com.roshan.persona.voice.audio.AudioFormat
import com.roshan.persona.voice.audio.CircularAudioBuffer
import org.junit.Test

// AUTO_FIX_0180: [feature] WakeWordDetectorTest verified

// VOICE_FIX_017: Wake word detector validated

class WakeWordDetectionTest {

    @Test
    fun `none factory creates empty detection`() {
        val detection = WakeWordDetection.none()
        assertThat(detection.isDetected).isFalse()
        assertThat(detection.confidence).isEqualTo(0f)
        assertThat(detection.tier).isEqualTo(WakeWordTier.NONE)
        assertThat(detection.triggeringChunk).isNull()
        assertThat(detection.contextAudio).isNull()
        assertThat(detection.latencyMs).isEqualTo(0L)
    }

    @Test
    fun ` WakeWordTier fromNumber maps correctly`() {
        assertThat(WakeWordTier.fromNumber(0)).isEqualTo(WakeWordTier.NONE)
        assertThat(WakeWordTier.fromNumber(1)).isEqualTo(WakeWordTier.HARDWARE_DSP)
        assertThat(WakeWordTier.fromNumber(2)).isEqualTo(WakeWordTier.SOFTWARE_TFLITE)
        assertThat(WakeWordTier.fromNumber(3)).isEqualTo(WakeWordTier.MFCC_CONFIRMATION)
        assertThat(WakeWordTier.fromNumber(4)).isEqualTo(WakeWordTier.CONFIRMED)
    }

    @Test
    fun `WakeWordTier fromNumber returns null for invalid number`() {
        assertThat(WakeWordTier.fromNumber(99)).isNull()
        assertThat(WakeWordTier.fromNumber(-1)).isNull()
    }

    @Test
    fun `detection with all fields set preserves data`() {
        val chunk = AudioChunk.silent()
        val detection = WakeWordDetection(
            isDetected = true,
            confidence = 0.85f,
            tier = WakeWordTier.CONFIRMED,
            triggeringChunk = chunk,
            contextAudio = chunk,
            latencyMs = 42L,
            speakerPreCheckPassed = true,
        )
        assertThat(detection.isDetected).isTrue()
        assertThat(detection.confidence).isWithin(0.001f).of(0.85f)
        assertThat(detection.tier).isEqualTo(WakeWordTier.CONFIRMED)
        assertThat(detection.triggeringChunk).isEqualTo(chunk)
        assertThat(detection.contextAudio).isEqualTo(chunk)
        assertThat(detection.latencyMs).isEqualTo(42L)
        assertThat(detection.speakerPreCheckPassed).isTrue()
    }
}

class WakeWordConfigTest {

    @Test
    fun `default config has Hey NOUS phrase`() {
        val config = WakeWordConfig()
        assertThat(config.phrase).isEqualTo("Hey NOUS")
    }

    @Test
    fun `default config has reasonable threshold`() {
        val config = WakeWordConfig()
        assertThat(config.threshold).isGreaterThan(0.5f)
        assertThat(config.threshold).isAtMost(1.0f)
    }

    @Test
    fun `default config prefers hardware`() {
        val config = WakeWordConfig()
        assertThat(config.preferHardware).isTrue()
    }

    @Test
    fun `default config has 3 second cooldown`() {
        val config = WakeWordConfig()
        assertThat(config.cooldownMs).isEqualTo(3_000L)
    }

    @Test
    fun `speaker pre-check is disabled by default`() {
        val config = WakeWordConfig()
        assertThat(config.speakerPreCheckEnabled).isFalse()
        assertThat(config.ownerVoicePrint).isNull()
    }

    @Test
    fun `config with owner voice print stores it`() {
        val voicePrint = FloatArray(192) { 0.5f }
        val config = WakeWordConfig(
            speakerPreCheckEnabled = true,
            ownerVoicePrint = voicePrint,
        )
        assertThat(config.speakerPreCheckEnabled).isTrue()
        assertThat(config.ownerVoicePrint).isEqualTo(voicePrint)
    }

    @Test
    fun `config equals works for same values`() {
        val c1 = WakeWordConfig(phrase = "Hey NOUS", threshold = 0.8f)
        val c2 = WakeWordConfig(phrase = "Hey NOUS", threshold = 0.8f)
        assertThat(c1).isEqualTo(c2)
        assertThat(c1.hashCode()).isEqualTo(c2.hashCode())
    }

    @Test
    fun `config not equals for different values`() {
        val c1 = WakeWordConfig(threshold = 0.7f)
        val c2 = WakeWordConfig(threshold = 0.9f)
        assertThat(c1).isNotEqualTo(c2)
    }
}

class MfccExtractorTest {

    private val extractor = MfccExtractor()

    @Test
    fun `extract returns 13 coefficients by default`() {
        val chunk = AudioChunk.silent()
        val mfcc = extractor.extract(chunk)
        assertThat(mfcc.size).isEqualTo(13)
    }

    @Test
    fun `extract from silent chunk returns finite values`() {
        val chunk = AudioChunk.silent()
        val mfcc = extractor.extract(chunk)
        for (c in mfcc) {
            assertThat(c.isFinite()).isTrue()
        }
    }

    @Test
    fun `extract from sine wave returns non-zero coefficients`() {
        val chunk = AudioChunk.sine(frequencyHz = 1000, amplitude = 0.5f)
        val mfcc = extractor.extract(chunk)
        // At least some coefficients should be non-zero
        val hasNonZero = mfcc.any { kotlin.math.abs(it) > 1e-6f }
        assertThat(hasNonZero).isTrue()
    }

    @Test
    fun `extract from different frequencies produces different MFCCs`() {
        val chunk1 = AudioChunk.sine(frequencyHz = 500, amplitude = 0.5f)
        val chunk2 = AudioChunk.sine(frequencyHz = 2000, amplitude = 0.5f)
        val mfcc1 = extractor.extract(chunk1)
        val mfcc2 = extractor.extract(chunk2)
        // At least one coefficient should differ significantly
        var anyDifference = false
        for (i in mfcc1.indices) {
            if (kotlin.math.abs(mfcc1[i] - mfcc2[i]) > 0.1f) {
                anyDifference = true
                break
            }
        }
        assertThat(anyDifference).isTrue()
    }

    @Test
    fun `cosineSimilarity of identical vectors is 1`() {
        val a = floatArrayOf(1f, 2f, 3f)
        val sim = extractor.cosineSimilarity(a, a)
        assertThat(sim).isWithin(0.001f).of(1f)
    }

    @Test
    fun `cosineSimilarity of orthogonal vectors is 0`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0f, 1f)
        val sim = extractor.cosineSimilarity(a, b)
        assertThat(sim).isWithin(0.001f).of(0f)
    }

    @Test
    fun `cosineSimilarity of opposite vectors is -1`() {
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(-1f, -2f, -3f)
        val sim = extractor.cosineSimilarity(a, b)
        assertThat(sim).isWithin(0.001f).of(-1f)
    }

    @Test
    fun `cosineSimilarity of zero vectors is 0`() {
        val a = floatArrayOf(0f, 0f, 0f)
        val b = floatArrayOf(0f, 0f, 0f)
        val sim = extractor.cosineSimilarity(a, b)
        assertThat(sim).isEqualTo(0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cosineSimilarity rejects different-size vectors`() {
        extractor.cosineSimilarity(floatArrayOf(1f, 2f), floatArrayOf(1f, 2f, 3f))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `extract rejects non-16kHz audio`() {
        val wrongFormat = AudioFormat(sampleRateHz = 44_100)
        val chunk = AudioChunk(wrongFormat, ShortArray(wrongFormat.samplesPerFrame), 0L)
        extractor.extract(chunk)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `extract rejects wrong chunk size`() {
        val chunk = AudioChunk(AudioFormat.VOICE_16K, ShortArray(100), 0L)
        extractor.extract(chunk)
    }

    @Test
    fun `extractor with custom numCoefficients works`() {
        val custom = MfccExtractor(numCoefficients = 20)
        val mfcc = custom.extract(AudioChunk.silent())
        assertThat(mfcc.size).isEqualTo(20)
    }

    @Test
    fun `extractor with custom numFilters works`() {
        val custom = MfccExtractor(numFilters = 40)
        val mfcc = custom.extract(AudioChunk.sine(frequencyHz = 1000, amplitude = 0.5f))
        assertThat(mfcc.size).isEqualTo(13) // coefficients count unchanged
    }
}

class WakeWordDetectorTest {

    @Test
    fun `processAudio returns none when not active`() {
        val detector = WakeWordDetector()
        val result = detector.processAudio(AudioChunk.silent())
        assertThat(result.isDetected).isFalse()
    }

    @Test
    fun `start returns false when no detectors configured`() {
        val detector = WakeWordDetector(
            hardwareDetector = null,
            softwareDetector = null,
        )
        val started = detector.start()
        assertThat(started).isFalse()
    }

    @Test
    fun `start returns true when software detector configured`() {
        val software = SoftwareWakeWordDetector()
        val detector = WakeWordDetector(softwareDetector = software)
        val started = detector.start()
        assertThat(started).isTrue()
        assertThat(detector.isActive).isTrue()
        detector.release()
    }

    @Test
    fun `processAudio returns none when TFLite score below threshold`() {
        // SoftwareWakeWordDetector stub returns 0 (below threshold)
        val software = SoftwareWakeWordDetector()
        val detector = WakeWordDetector(
            config = WakeWordConfig(threshold = 0.5f),
            softwareDetector = software,
        )
        detector.start()
        val result = detector.processAudio(AudioChunk.silent())
        assertThat(result.isDetected).isFalse()
        detector.release()
    }

    @Test
    fun `isActive is false before start`() {
        val detector = WakeWordDetector(softwareDetector = SoftwareWakeWordDetector())
        assertThat(detector.isActive).isFalse()
    }

    @Test
    fun `isActive is true after start`() {
        val detector = WakeWordDetector(softwareDetector = SoftwareWakeWordDetector())
        detector.start()
        assertThat(detector.isActive).isTrue()
        detector.release()
    }

    @Test
    fun `stop sets active to false`() {
        val detector = WakeWordDetector(softwareDetector = SoftwareWakeWordDetector())
        detector.start()
        detector.stop()
        assertThat(detector.isActive).isFalse()
    }

    @Test
    fun `release prevents further processing`() {
        val detector = WakeWordDetector(softwareDetector = SoftwareWakeWordDetector())
        detector.start()
        detector.release()
        val result = detector.processAudio(AudioChunk.silent())
        assertThat(result.isDetected).isFalse()
    }

    @Test
    fun `start after release throws`() {
        val detector = WakeWordDetector(softwareDetector = SoftwareWakeWordDetector())
        detector.release()
        try {
            detector.start()
            assert(false) { "Expected IllegalStateException" }
        } catch (e: IllegalStateException) {
            // Expected
        }
    }

    @Test
    fun `handleHardwareTrigger with null context returns hardware detection`() {
        val detector = WakeWordDetector(softwareDetector = SoftwareWakeWordDetector())
        detector.start()
        val result = detector.handleHardwareTrigger(null)
        assertThat(result.isDetected).isTrue()
        assertThat(result.tier).isEqualTo(WakeWordTier.HARDWARE_DSP)
        detector.release()
    }

    @Test
    fun `handleHardwareTrigger with MFCC template does confirmation`() {
        val template = MfccExtractor().extract(AudioChunk.sine(frequencyHz = 440, amplitude = 0.5f))
        val detector = WakeWordDetector(
            softwareDetector = SoftwareWakeWordDetector(),
            wakeWordTemplate = template,
        )
        detector.start()
        val context = AudioChunk.sine(frequencyHz = 440, amplitude = 0.5f)
        val result = detector.handleHardwareTrigger(context)
        // Should pass MFCC confirmation (template matches itself)
        assertThat(result.isDetected).isTrue()
        assertThat(result.tier).isEqualTo(WakeWordTier.CONFIRMED)
        detector.release()
    }

    @Test
    fun `handleHardwareTrigger with non-matching template rejects`() {
        val template = MfccExtractor().extract(AudioChunk.sine(frequencyHz = 440, amplitude = 0.5f))
        val detector = WakeWordDetector(
            config = WakeWordConfig(mfccThreshold = 0.99f), // very high bar
            softwareDetector = SoftwareWakeWordDetector(),
            wakeWordTemplate = template,
        )
        detector.start()
        // Use different frequency — MFCC won't match
        val context = AudioChunk.sine(frequencyHz = 3000, amplitude = 0.5f)
        val result = detector.handleHardwareTrigger(context)
        assertThat(result.isDetected).isFalse()
        detector.release()
    }

    @Test
    fun `cooldown prevents rapid re-detection`() {
        val template = MfccExtractor().extract(AudioChunk.sine(frequencyHz = 440, amplitude = 0.5f))
        val detector = WakeWordDetector(
            config = WakeWordConfig(cooldownMs = 60_000L), // 1 minute cooldown
            softwareDetector = SoftwareWakeWordDetector(),
            wakeWordTemplate = template,
        )
        detector.start()
        val context = AudioChunk.sine(frequencyHz = 440, amplitude = 0.5f)
        val first = detector.handleHardwareTrigger(context)
        assertThat(first.isDetected).isTrue()
        // Second call within cooldown should be rejected
        val second = detector.handleHardwareTrigger(context)
        assertThat(second.isDetected).isFalse()
        detector.release()
    }

    @Test
    fun `reset clears cooldown`() {
        val template = MfccExtractor().extract(AudioChunk.sine(frequencyHz = 440, amplitude = 0.5f))
        val detector = WakeWordDetector(
            config = WakeWordConfig(cooldownMs = 60_000L),
            softwareDetector = SoftwareWakeWordDetector(),
            wakeWordTemplate = template,
        )
        detector.start()
        val context = AudioChunk.sine(frequencyHz = 440, amplitude = 0.5f)
        detector.handleHardwareTrigger(context)
        detector.reset()
        // After reset, should detect again
        val result = detector.handleHardwareTrigger(context)
        assertThat(result.isDetected).isTrue()
        detector.release()
    }

    @Test
    fun `isUsingHardware is false when only software configured`() {
        val detector = WakeWordDetector(softwareDetector = SoftwareWakeWordDetector())
        assertThat(detector.isUsingHardware).isFalse()
    }
}

class HardwareWakeWordDetectorTest {

    @Test
    fun `isAvailable is false when manager is null`() {
        val detector = HardwareWakeWordDetector(soundTriggerManager = null) {}
        assertThat(detector.isAvailable).isFalse()
    }

    @Test
    fun `start returns false when not available`() {
        val detector = HardwareWakeWordDetector(soundTriggerManager = null) {}
        val started = detector.start()
        assertThat(started).isFalse()
    }

    @Test
    fun `isActive is false before start`() {
        val detector = HardwareWakeWordDetector(soundTriggerManager = null) {}
        assertThat(detector.isActive).isFalse()
    }

    @Test
    fun `stop is safe when not started`() {
        val detector = HardwareWakeWordDetector(soundTriggerManager = null) {}
        detector.stop() // should not throw
    }

    @Test
    fun `release is idempotent`() {
        val detector = HardwareWakeWordDetector(soundTriggerManager = null) {}
        detector.release()
        detector.release()
    }
}

class SoftwareWakeWordDetectorTest {

    @Test
    fun `start returns true even without model`() {
        val detector = SoftwareWakeWordDetector()
        val started = detector.start()
        assertThat(started).isTrue()
    }

    @Test
    fun `detect returns 0 when not active`() {
        val detector = SoftwareWakeWordDetector()
        val score = detector.detect(AudioChunk.silent())
        assertThat(score).isEqualTo(0f)
    }

    @Test
    fun `detect returns 0 for active detector without model (v1 stub)`() {
        val detector = SoftwareWakeWordDetector()
        detector.start()
        val score = detector.detect(AudioChunk.silent())
        assertThat(score).isEqualTo(0f)
        detector.release()
    }

    @Test
    fun `isModelAvailable is false when no path provided`() {
        val detector = SoftwareWakeWordDetector()
        assertThat(detector.isModelAvailable).isFalse()
    }

    @Test
    fun `isModelAvailable is true when path provided`() {
        val detector = SoftwareWakeWordDetector(modelPath = "/some/path/model.tflite")
        assertThat(detector.isModelAvailable).isTrue()
    }

    @Test
    fun `release prevents further detection`() {
        val detector = SoftwareWakeWordDetector()
        detector.start()
        detector.release()
        val score = detector.detect(AudioChunk.silent())
        assertThat(score).isEqualTo(0f)
    }

    @Test
    fun `start is idempotent`() {
        val detector = SoftwareWakeWordDetector()
        detector.start()
        val second = detector.start()
        assertThat(second).isTrue()
        detector.release()
    }

    @Test
    fun `stop sets active to false`() {
        val detector = SoftwareWakeWordDetector()
        detector.start()
        detector.stop()
        assertThat(detector.isActive).isFalse()
    }
}
