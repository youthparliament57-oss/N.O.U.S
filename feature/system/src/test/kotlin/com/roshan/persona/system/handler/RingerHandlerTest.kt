// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.system.handler

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.skill.RingerMode
import com.roshan.persona.brain.skill.SystemOperation
import com.roshan.persona.common.AppError
import com.roshan.persona.common.Result
import com.roshan.persona.system.executor.ResourceCategory
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0143: [feature] RingerHandlerTest verified

class RingerHandlerTest {

    /** Fake audio service with configurable ringer mode + SecurityException toggle. */
    private class FakeAudioService(
        private var ringer: RingerMode = RingerMode.NORMAL,
        private val throwOnSetRinger: SecurityException? = null,
    ) : AudioService {
        val setRingerCalls = mutableListOf<RingerMode>()
            private set

        override fun getStreamVolume(stream: com.roshan.persona.brain.skill.AudioStream): Int = 50
        override fun setStreamVolume(stream: com.roshan.persona.brain.skill.AudioStream, level: Int) {}
        override fun getStreamMaxVolume(stream: com.roshan.persona.brain.skill.AudioStream): Int = 100
        override fun adjustStreamVolume(stream: com.roshan.persona.brain.skill.AudioStream, delta: Int) {}
        override fun getRingerMode(): RingerMode = ringer
        override fun setRingerMode(mode: RingerMode) {
            throwOnSetRinger?.let { throw it }
            ringer = mode
            setRingerCalls.add(mode)
        }
        override fun streamToInt(stream: com.roshan.persona.brain.skill.AudioStream): Int = stream.ordinal
    }

    private fun makeHandler(audio: FakeAudioService) = RingerHandler(audio)

    // ─── Tests ─────────────────────────────────────────────────────────────

    @Test
    fun `isAvailable always returns true`() {
        assertThat(makeHandler(FakeAudioService()).isAvailable()).isTrue()
    }

    @Test
    fun `execute sets ringer mode to SILENT`() = runTest {
        val audio = FakeAudioService(ringer = RingerMode.NORMAL)
        val handler = makeHandler(audio)
        handler.execute(SystemOperation.SetRingerMode(RingerMode.SILENT))
        assertThat(audio.setRingerCalls).hasSize(1)
        assertThat(audio.setRingerCalls[0]).isEqualTo(RingerMode.SILENT)
    }

    @Test
    fun `execute sets ringer mode to VIBRATE`() = runTest {
        val audio = FakeAudioService(ringer = RingerMode.NORMAL)
        val handler = makeHandler(audio)
        handler.execute(SystemOperation.SetRingerMode(RingerMode.VIBRATE))
        assertThat(audio.setRingerCalls[0]).isEqualTo(RingerMode.VIBRATE)
    }

    @Test
    fun `execute sets ringer mode to NORMAL`() = runTest {
        val audio = FakeAudioService(ringer = RingerMode.SILENT)
        val handler = makeHandler(audio)
        handler.execute(SystemOperation.SetRingerMode(RingerMode.NORMAL))
        assertThat(audio.setRingerCalls[0]).isEqualTo(RingerMode.NORMAL)
    }

    @Test
    fun `execute returns Failure SpecialAccessRequired on SecurityException`() = runTest {
        val audio = FakeAudioService(
            throwOnSetRinger = SecurityException("Need DnD access"),
        )
        val handler = makeHandler(audio)
        val result = handler.execute(SystemOperation.SetRingerMode(RingerMode.SILENT))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        val failure = result as Result.Failure
        assertThat(failure.error).isInstanceOf(AppError.Permission.SpecialAccessRequired::class.java)
        val err = failure.error as AppError.Permission.SpecialAccessRequired
        assertThat(err.settingsIntent).isEqualTo(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    }

    // ─── Idempotent (Review 2 Elite 1) ─────────────────────────────────────

    @Test
    fun `isAlreadyInTargetState returns true when ringer already matches`() = runTest {
        val audio = FakeAudioService(ringer = RingerMode.SILENT)
        val handler = makeHandler(audio)
        assertThat(handler.isAlreadyInTargetState(SystemOperation.SetRingerMode(RingerMode.SILENT))).isTrue()
    }

    @Test
    fun `isAlreadyInTargetState returns false when ringer differs`() = runTest {
        val audio = FakeAudioService(ringer = RingerMode.NORMAL)
        val handler = makeHandler(audio)
        assertThat(handler.isAlreadyInTargetState(SystemOperation.SetRingerMode(RingerMode.SILENT))).isFalse()
    }

    // ─── Pre-state capture (Review 2 Fix A) ────────────────────────────────

    @Test
    fun `capturePreState returns previous ringer mode`() = runTest {
        val audio = FakeAudioService(ringer = RingerMode.VIBRATE)
        val handler = makeHandler(audio)
        val preState = handler.capturePreState(SystemOperation.SetRingerMode(RingerMode.SILENT))
        assertThat(preState).isInstanceOf(RingerHandler.RingerPreState::class.java)
        assertThat((preState as RingerHandler.RingerPreState).previousMode).isEqualTo(RingerMode.VIBRATE)
    }

    @Test
    fun `restorePreState restores previous ringer mode`() = runTest {
        val audio = FakeAudioService(ringer = RingerMode.SILENT)
        val handler = makeHandler(audio)
        val preState = RingerHandler.RingerPreState(previousMode = RingerMode.VIBRATE)
        handler.restorePreState(SystemOperation.SetRingerMode(RingerMode.SILENT), preState)
        assertThat(audio.setRingerCalls).hasSize(1)
        assertThat(audio.setRingerCalls[0]).isEqualTo(RingerMode.VIBRATE)
    }

    @Test
    fun `getCurrentState returns current ringer mode`() = runTest {
        val audio = FakeAudioService(ringer = RingerMode.VIBRATE)
        val handler = makeHandler(audio)
        val state = handler.getCurrentState()
        assertThat(state).isInstanceOf(RingerHandler.RingerPreState::class.java)
        assertThat((state as RingerHandler.RingerPreState).previousMode).isEqualTo(RingerMode.VIBRATE)
    }

    @Test
    fun `describeOperation returns human-readable description`() {
        val handler = makeHandler(FakeAudioService())
        assertThat(handler.describeOperation(SystemOperation.SetRingerMode(RingerMode.NORMAL)))
            .isEqualTo("Ringer set to normal")
        assertThat(handler.describeOperation(SystemOperation.SetRingerMode(RingerMode.VIBRATE)))
            .isEqualTo("Ringer set to vibrate")
        assertThat(handler.describeOperation(SystemOperation.SetRingerMode(RingerMode.SILENT)))
            .isEqualTo("Ringer set to silent")
    }

    @Test
    fun `resourceCategory is AUDIO`() {
        assertThat(makeHandler(FakeAudioService()).resourceCategory).isEqualTo(ResourceCategory.AUDIO)
    }

    @Test
    fun `requiredPermissions is empty (ringer doesn't need a manifest permission)`() {
        assertThat(makeHandler(FakeAudioService()).requiredPermissions).isEmpty()
    }
}
