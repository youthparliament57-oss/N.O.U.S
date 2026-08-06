// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.system.handler

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.skill.AudioStream
import com.roshan.persona.brain.skill.RingerMode
import com.roshan.persona.brain.skill.SystemOperation
import com.roshan.persona.common.Result
import com.roshan.persona.system.executor.ResourceCategory
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0153: [feature] VolumeHandlerTest verified

class VolumeHandlerTest {

    /** Fake audio service — records calls + configurable state. */
    private class FakeAudioService(
        private val volumes: MutableMap<AudioStream, Int> = mutableMapOf(
            AudioStream.MEDIA to 50,
            AudioStream.RING to 30,
            AudioStream.NOTIFICATION to 40,
            AudioStream.ALARM to 60,
            AudioStream.CALL to 50,
            AudioStream.SYSTEM to 50,
        ),
        private val maxVolumes: Map<AudioStream, Int> = AudioStream.entries.associateWith { 100 },
        private val ringerMode: RingerMode = RingerMode.NORMAL,
        private val throwOnSetVolume: Throwable? = null,
    ) : AudioService {
        val setVolumeCalls = mutableListOf<Pair<AudioStream, Int>>()
            private set
        val adjustVolumeCalls = mutableListOf<Pair<AudioStream, Int>>()
            private set

        override fun getStreamVolume(stream: AudioStream): Int = volumes[stream] ?: 0
        override fun setStreamVolume(stream: AudioStream, level: Int) {
            throwOnSetVolume?.let { throw it }
            val max = maxVolumes[stream] ?: 100
            volumes[stream] = level.coerceIn(0, max)
            setVolumeCalls.add(stream to level)
        }
        override fun getStreamMaxVolume(stream: AudioStream): Int = maxVolumes[stream] ?: 100
        override fun adjustStreamVolume(stream: AudioStream, delta: Int) {
            val current = volumes[stream] ?: 0
            val max = maxVolumes[stream] ?: 100
            volumes[stream] = (current + delta).coerceIn(0, max)
            adjustVolumeCalls.add(stream to delta)
        }
        override fun getRingerMode(): RingerMode = ringerMode
        override fun setRingerMode(mode: RingerMode) {}
        override fun streamToInt(stream: AudioStream): Int = stream.ordinal
    }

    /** Fake state probe — configurable call/music state. */
    private class FakeStateProbe(
        private val inCall: Boolean = false,
        private val musicActive: Boolean = false,
        private val silentMode: Boolean = false,
    ) : AudioStateProbe {
        override fun isInCall(): Boolean = inCall
        override fun isMusicActive(): Boolean = musicActive
        override fun isSilentMode(): Boolean = silentMode
    }

    private fun makeHandler(
        audio: FakeAudioService = FakeAudioService(),
        probe: AudioStateProbe = FakeStateProbe(),
    ) = VolumeHandler(audio, probe)

    // ─── Tests ─────────────────────────────────────────────────────────────

    @Test
    fun `isAvailable always returns true (all devices have AudioManager)`() {
        assertThat(makeHandler().isAvailable()).isTrue()
    }

    @Test
    fun `execute SetVolume sets the volume on the requested stream`() = runTest {
        val audio = FakeAudioService()
        val handler = makeHandler(audio)
        handler.execute(SystemOperation.SetVolume(level = 75, stream = AudioStream.MEDIA))
        assertThat(audio.setVolumeCalls).hasSize(1)
        assertThat(audio.setVolumeCalls[0]).isEqualTo(AudioStream.MEDIA to 75)
    }

    @Test
    fun `execute AdjustVolume adjusts the volume by delta`() = runTest {
        val audio = FakeAudioService()
        val handler = makeHandler(audio)
        handler.execute(SystemOperation.AdjustVolume(delta = 5, stream = AudioStream.MEDIA))
        assertThat(audio.adjustVolumeCalls).hasSize(1)
        assertThat(audio.adjustVolumeCalls[0]).isEqualTo(AudioStream.MEDIA to 5)
        // 50 + 5 = 55
        assertThat(audio.getStreamVolume(AudioStream.MEDIA)).isEqualTo(55)
    }

    @Test
    fun `execute AdjustVolume clamps to 0 on negative delta below zero`() = runTest {
        val audio = FakeAudioService().apply {
            // set MEDIA to 5
        }
        // Use a fresh audio service with MEDIA at 5
        val audioLow = object : FakeAudioService() {
            override fun getStreamVolume(stream: AudioStream): Int = if (stream == AudioStream.MEDIA) 5 else 50
        }
        val handler = makeHandler(audioLow)
        handler.execute(SystemOperation.AdjustVolume(delta = -10, stream = AudioStream.MEDIA))
        // 5 + (-10) = -5 → clamped to 0
        assertThat(audioLow.getStreamVolume(AudioStream.MEDIA)).isEqualTo(0)
    }

    // ─── Intelligent stream routing (Review 2 Elite 3) ─────────────────────

    @Test
    fun `intelligent routing uses CALL stream when in call and stream is MEDIA`() = runTest {
        val audio = FakeAudioService()
        val probe = FakeStateProbe(inCall = true)
        val handler = makeHandler(audio, probe)
        handler.execute(SystemOperation.SetVolume(level = 80, stream = AudioStream.MEDIA))
        // Should have routed to CALL, not MEDIA.
        assertThat(audio.setVolumeCalls[0].first).isEqualTo(AudioStream.CALL)
    }

    @Test
    fun `intelligent routing uses MEDIA stream when not in call`() = runTest {
        val audio = FakeAudioService()
        val probe = FakeStateProbe(inCall = false)
        val handler = makeHandler(audio, probe)
        handler.execute(SystemOperation.SetVolume(level = 80, stream = AudioStream.MEDIA))
        assertThat(audio.setVolumeCalls[0].first).isEqualTo(AudioStream.MEDIA)
    }

    @Test
    fun `intelligent routing respects explicit non-MEDIA stream even when in call`() = runTest {
        val audio = FakeAudioService()
        val probe = FakeStateProbe(inCall = true)
        val handler = makeHandler(audio, probe)
        handler.execute(SystemOperation.SetVolume(level = 80, stream = AudioStream.RING))
        // User explicitly said RING — respect it even though in call.
        assertThat(audio.setVolumeCalls[0].first).isEqualTo(AudioStream.RING)
    }

    @Test
    fun `intelligent routing respects explicit ALARM stream`() = runTest {
        val audio = FakeAudioService()
        val probe = FakeStateProbe(inCall = true)
        val handler = makeHandler(audio, probe)
        handler.execute(SystemOperation.AdjustVolume(delta = 1, stream = AudioStream.ALARM))
        assertThat(audio.adjustVolumeCalls[0].first).isEqualTo(AudioStream.ALARM)
    }

    @Test
    fun `resolveStream returns CALL when in call and requested is MEDIA`() {
        val handler = makeHandler(probe = FakeStateProbe(inCall = true))
        assertThat(handler.resolveStream(AudioStream.MEDIA)).isEqualTo(AudioStream.CALL)
    }

    @Test
    fun `resolveStream returns MEDIA when not in call`() {
        val handler = makeHandler(probe = FakeStateProbe(inCall = false))
        assertThat(handler.resolveStream(AudioStream.MEDIA)).isEqualTo(AudioStream.MEDIA)
    }

    @Test
    fun `resolveStream returns requested stream when not MEDIA`() {
        val handler = makeHandler(probe = FakeStateProbe(inCall = true))
        assertThat(handler.resolveStream(AudioStream.RING)).isEqualTo(AudioStream.RING)
        assertThat(handler.resolveStream(AudioStream.ALARM)).isEqualTo(AudioStream.ALARM)
    }

    // ─── Idempotent (Review 2 Elite 1) ─────────────────────────────────────

    @Test
    fun `isAlreadyInTargetState returns true for SetVolume when volume already at target`() = runTest {
        val audio = FakeAudioService()  // MEDIA is at 50
        val handler = makeHandler(audio)
        assertThat(handler.isAlreadyInTargetState(SystemOperation.SetVolume(50, AudioStream.MEDIA))).isTrue()
    }

    @Test
    fun `isAlreadyInTargetState returns false for SetVolume when volume differs`() = runTest {
        val audio = FakeAudioService()  // MEDIA is at 50
        val handler = makeHandler(audio)
        assertThat(handler.isAlreadyInTargetState(SystemOperation.SetVolume(80, AudioStream.MEDIA))).isFalse()
    }

    @Test
    fun `isAlreadyInTargetState returns false for AdjustVolume (relative never idempotent)`() = runTest {
        val handler = makeHandler()
        assertThat(handler.isAlreadyInTargetState(SystemOperation.AdjustVolume(1, AudioStream.MEDIA))).isFalse()
    }

    // ─── Pre-state capture (Review 2 Fix A) ────────────────────────────────

    @Test
    fun `capturePreState returns VolumePreState with previous level`() = runTest {
        val audio = FakeAudioService()  // MEDIA at 50
        val handler = makeHandler(audio)
        val preState = handler.capturePreState(SystemOperation.SetVolume(80, AudioStream.MEDIA))
        assertThat(preState).isInstanceOf(VolumeHandler.VolumePreState::class.java)
        val v = preState as VolumeHandler.VolumePreState
        assertThat(v.previousLevel).isEqualTo(50)
        assertThat(v.stream).isEqualTo(AudioStream.MEDIA)
    }

    @Test
    fun `restorePreState restores the previous volume level`() = runTest {
        val audio = FakeAudioService()
        val handler = makeHandler(audio)
        val preState = VolumeHandler.VolumePreState(AudioStream.MEDIA, previousLevel = 30, maxLevel = 100)
        handler.restorePreState(SystemOperation.SetVolume(80, AudioStream.MEDIA), preState)
        assertThat(audio.getStreamVolume(AudioStream.MEDIA)).isEqualTo(30)
    }

    @Test
    fun `describeOperation returns human-readable description`() {
        val handler = makeHandler()
        assertThat(handler.describeOperation(SystemOperation.SetVolume(80, AudioStream.MEDIA)))
            .contains("80")
        assertThat(handler.describeOperation(SystemOperation.AdjustVolume(1, AudioStream.MEDIA)))
            .contains("up")
    }

    @Test
    fun `resourceCategory is AUDIO`() {
        assertThat(makeHandler().resourceCategory).isEqualTo(ResourceCategory.AUDIO)
    }

    @Test
    fun `execute returns Failure for unsupported operation type`() = runTest {
        val handler = makeHandler()
        val result = handler.execute(SystemOperation.SetTorch(true))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
    }
}
