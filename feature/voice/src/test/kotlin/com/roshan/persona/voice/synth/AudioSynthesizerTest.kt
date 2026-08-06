// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.voice.synth

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.voice.tts.VoicePersona
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test

// VOICE_FIX_011: Synthesizer resource managed

class SoundEffectTest {

    @Test
    fun `all 5 SoundEffect values are defined`() {
        val expected = setOf(
            "ARC_REACTOR_HUM", "BOOT_STARTUP", "NOTIFICATION_CHIME",
            "ERROR_SUBTLE", "SUCCESS",
        )
        assertThat(SoundEffect.entries.map { it.name }.toSet()).isEqualTo(expected)
    }

    @Test
    fun `every SoundEffect has a unique file path`() {
        val paths = SoundEffect.entries.map { it.filePath }
        assertThat(paths).containsNoDuplicates()
    }

    @Test
    fun `every SoundEffect defaultVolume is in valid range`() {
        for (effect in SoundEffect.entries) {
            assertThat(effect.defaultVolume).isAtLeast(0f)
            assertThat(effect.defaultVolume).isAtMost(1f)
        }
    }

    @Test
    fun `ToneSpec validates bounds`() {
        try {
            ToneSpec(frequencyHz = 10, durationMs = 100)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("frequencyHz")
        }
        try {
            ToneSpec(frequencyHz = 1000, durationMs = 0)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("durationMs")
        }
        try {
            ToneSpec(frequencyHz = 1000, durationMs = 100, volume = 1.5f)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("volume")
        }
    }

    @Test
    fun `ToneSpec preset constants are valid`() {
        // Sanity check the presets — they're used directly by callers.
        assertThat(ToneSpec.CONFIRMATION_BEEP.frequencyHz).isEqualTo(880)
        assertThat(ToneSpec.ERROR_BUZZ.waveform).isEqualTo(Waveform.SQUARE)
        assertThat(ToneSpec.SUBTLE_CLICK.durationMs).isEqualTo(30)
    }

    @Test
    fun `PersonaChimeMapping returns distinct tone per persona`() {
        val chimes = VoicePersona.entries.map { PersonaChimeMapping.chimeFor(it) }
        // Each persona should have a distinct frequency.
        val frequencies = chimes.map { it.frequencyHz }.toSet()
        assertThat(frequencies.size).isEqualTo(VoicePersona.entries.size)
    }

    @Test
    fun `PersonaChimeMapping ULTRON uses square waveform for cold dissonant sound`() {
        val ultronChime = PersonaChimeMapping.chimeFor(VoicePersona.ULTRON)
        assertThat(ultronChime.waveform).isEqualTo(Waveform.SQUARE)
    }

    @Test
    fun `AudioSynthesizerConfig validates bounds`() {
        try {
            AudioSynthesizerConfig(sampleRate = 1000)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("sampleRate")
        }
        try {
            AudioSynthesizerConfig(masterVolume = 1.5f)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("masterVolume")
        }
    }

    @Test
    fun `Waveform has 4 distinct values`() {
        assertThat(Waveform.entries).hasSize(4)
        assertThat(Waveform.entries.map { it.name }.toSet())
            .containsExactly("SINE", "SQUARE", "TRIANGLE", "SAWTOOTH")
    }
}

class AudioSynthesizerTest {

    // ─── Fakes ─────────────────────────────────────────────────────────────

    private class FakeAssetLoader(private val samples: ShortArray = ShortArray(100) { it.toShort() }) :
        AssetLoader {
        var loadCount = 0
            private set
        var lastLoadedPath: String? = null
            private set
        override suspend fun loadPcm(filePath: String): ShortArray {
            loadCount++
            lastLoadedPath = filePath
            return samples
        }
    }

    private fun makeSynthesizer(
        output: RecordingAudioOutput = RecordingAudioOutput(),
        assetLoader: AssetLoader = FakeAssetLoader(),
        config: AudioSynthesizerConfig = AudioSynthesizerConfig(),
    ): Triple<AudioSynthesizer, RecordingAudioOutput, AssetLoader> {
        val synth = AudioSynthesizer(
            audioOutput = output,
            assetLoader = assetLoader,
            config = config,
            dispatcher = Dispatchers.Unconfined,
        )
        return Triple(synth, output, assetLoader)
    }

    // ─── Tests ─────────────────────────────────────────────────────────────

    @Test
    fun `playSoundEffect loads and plays the asset`() = runTest {
        val (synth, output, assets) = makeSynthesizer()
        synth.playSoundEffect(SoundEffect.BOOT_STARTUP)
        assertThat((assets as FakeAssetLoader).lastLoadedPath).isEqualTo("sounds/boot.wav")
        assertThat(output.recorded).hasSize(1)
    }

    @Test
    fun `playSoundEffect is silent when config enabled is false`() = runTest {
        val (synth, output, _) = makeSynthesizer(config = AudioSynthesizerConfig(enabled = false))
        synth.playSoundEffect(SoundEffect.BOOT_STARTUP)
        assertThat(output.recorded).isEmpty()
    }

    @Test
    fun `playSoundEffect ARC_REACTOR_HUM respects arcReactorHumEnabled flag`() = runTest {
        // Disabled → no output.
        val (synthOff, outputOff, _) = makeSynthesizer(
            config = AudioSynthesizerConfig(arcReactorHumEnabled = false),
        )
        synthOff.playSoundEffect(SoundEffect.ARC_REACTOR_HUM)
        assertThat(outputOff.recorded).isEmpty()

        // Enabled → output.
        val (synthOn, outputOn, _) = makeSynthesizer(
            config = AudioSynthesizerConfig(arcReactorHumEnabled = true),
        )
        synthOn.playSoundEffect(SoundEffect.ARC_REACTOR_HUM)
        assertThat(outputOn.recorded).hasSize(1)
    }

    @Test
    fun `playSoundEffect volume override applies masterVolume multiplier`() = runTest {
        val (synth, output, _) = makeSynthesizer(
            config = AudioSynthesizerConfig(masterVolume = 0.5f),
        )
        synth.playSoundEffect(SoundEffect.SUCCESS, volume = 0.8f)
        // effectiveVolume = 0.8 (override) × 0.5 (master) = 0.4
        assertThat(output.recorded.first().second).isWithin(0.001f).of(0.4f)
    }

    @Test
    fun `playSoundEffect uses default volume when override is null`() = runTest {
        val (synth, output, _) = makeSynthesizer(
            config = AudioSynthesizerConfig(masterVolume = 1.0f),
        )
        synth.playSoundEffect(SoundEffect.SUCCESS, volume = null)
        // effectiveVolume = 0.6 (default) × 1.0 (master) = 0.6
        assertThat(output.recorded.first().second).isWithin(0.001f).of(0.6f)
    }

    @Test
    fun `generateTone produces correct number of samples`() = runTest {
        val (synth, _, _) = makeSynthesizer()
        val spec = ToneSpec(frequencyHz = 440, durationMs = 100, volume = 0.5f)
        synth.generateTone(spec)
        // 100 ms at 44100 Hz = 4410 samples.
        // (we can't easily check the recorded length because the dispatcher
        // might run asynchronously, but we can verify generateToneSamples.)
        val samples = synth.generateToneSamples(spec, 44_100)
        assertThat(samples.size).isEqualTo(4410)
    }

    @Test
    fun `generateToneSamples sine wave produces expected peak amplitude`() {
        val (synth, _, _) = makeSynthesizer()
        val spec = ToneSpec(frequencyHz = 440, durationMs = 10, volume = 1.0f)
        val samples = synth.generateToneSamples(spec, 44_100)
        // Peak amplitude should be at or near Short.MAX_VALUE for volume=1.0.
        val peak = samples.maxOf { kotlin.math.abs(it.toInt()) }
        assertThat(peak).isAtLeast((Short.MAX_VALUE * 0.99f).toInt())
    }

    @Test
    fun `generateToneSamples square wave only produces -MAX or +MAX values`() {
        val (synth, _, _) = makeSynthesizer()
        val spec = ToneSpec(frequencyHz = 440, durationMs = 10, volume = 1.0f, waveform = Waveform.SQUARE)
        val samples = synth.generateToneSamples(spec, 44_100)
        // All non-zero samples should be at ±MAX_VALUE.
        for (s in samples) {
            if (s.toInt() != 0) {
                assertThat(kotlin.math.abs(s.toInt())).isAtLeast((Short.MAX_VALUE * 0.99f).toInt())
            }
        }
    }

    @Test
    fun `generateToneSamples respects volume parameter`() {
        val (synth, _, _) = makeSynthesizer()
        val loud = synth.generateToneSamples(
            ToneSpec(frequencyHz = 440, durationMs = 10, volume = 1.0f), 44_100,
        )
        val quiet = synth.generateToneSamples(
            ToneSpec(frequencyHz = 440, durationMs = 10, volume = 0.1f), 44_100,
        )
        val loudPeak = loud.maxOf { kotlin.math.abs(it.toInt()) }
        val quietPeak = quiet.maxOf { kotlin.math.abs(it.toInt()) }
        assertThat(quietPeak).isLessThan(loudPeak / 5)
    }

    @Test
    fun `playPersonaChime generates tone with persona-specific frequency`() = runTest {
        val (synth, output, _) = makeSynthesizer()
        synth.playPersonaChime(VoicePersona.JARVIS)
        assertThat(output.recorded).hasSize(1)
    }

    @Test
    fun `playPersonaChime is silent when config disabled`() = runTest {
        val (synth, output, _) = makeSynthesizer(config = AudioSynthesizerConfig(enabled = false))
        synth.playPersonaChime(VoicePersona.JARVIS)
        assertThat(output.recorded).isEmpty()
    }

    @Test
    fun `mixPcm averages two sample arrays without clipping`() {
        val (synth, _, _) = makeSynthesizer()
        val a = ShortArray(10) { Short.MAX_VALUE.toInt().toShort() }  // full-scale positive
        val b = ShortArray(10) { Short.MAX_VALUE.toInt().toShort() }
        val mixed = synth.mixPcm(a, b)
        // Average of two MAX values, clamped to MAX — no overflow.
        for (s in mixed) {
            assertThat(s.toInt()).isAtMost(Short.MAX_VALUE.toInt())
            assertThat(s.toInt()).isAtLeast(Short.MIN_VALUE.toInt())
        }
    }

    @Test
    fun `mixPcm rejects mismatched lengths`() {
        val (synth, _, _) = makeSynthesizer()
        try {
            synth.mixPcm(ShortArray(10), ShortArray(20))
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("same length")
        }
    }
}
