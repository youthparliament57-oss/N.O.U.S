// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.system.handler

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.skill.SystemOperation
import com.roshan.persona.common.AppError
import com.roshan.persona.common.Result
import com.roshan.persona.system.executor.ResourceCategory
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0151: [feature] BrightnessHandlerTest verified

class BrightnessHandlerTest {

    /** Fake display service with configurable state + exception toggles. */
    private class FakeDisplayService(
        private var brightness: Int = 128,
        private var autoBrightness: Boolean = false,
        private val throwOnSetBrightness: Throwable? = null,
        private val keepScreenOnResult: Boolean = true,
    ) : DisplayService {
        val setBrightnessCalls = mutableListOf<Int>()
            private set
        var autoBrightnessToggled: Boolean? = null
            private set
        var windowBrightnessApplied: Float? = null
            private set
        val keepScreenOnCalls = mutableListOf<Boolean>()
            private set

        override fun getBrightness(): Int = brightness
        override fun setBrightness(level: Int) {
            throwOnSetBrightness?.let { throw it }
            brightness = level
            setBrightnessCalls.add(level)
        }
        override fun isAutoBrightness(): Boolean = autoBrightness
        override fun setAutoBrightness(enabled: Boolean) {
            autoBrightness = enabled
            autoBrightnessToggled = enabled
        }
        override fun applyWindowBrightness(level: Float?) {
            windowBrightnessApplied = level
        }
        override fun setKeepScreenOn(enabled: Boolean): Boolean {
            keepScreenOnCalls.add(enabled)
            return keepScreenOnResult
        }
    }

    private fun makeHandler(display: FakeDisplayService) = BrightnessHandler(display)

    // ─── Tests ─────────────────────────────────────────────────────────────

    @Test
    fun `isAvailable always returns true`() {
        assertThat(makeHandler(FakeDisplayService()).isAvailable()).isTrue()
    }

    @Test
    fun `execute sets brightness to 255 when level is 1_0`() = runTest {
        val display = FakeDisplayService()
        makeHandler(display).execute(SystemOperation.SetDisplayBrightness(1.0f))
        assertThat(display.setBrightnessCalls).hasSize(1)
        assertThat(display.setBrightnessCalls[0]).isEqualTo(255)
    }

    @Test
    fun `execute sets brightness to 0 when level is 0_0`() = runTest {
        val display = FakeDisplayService()
        makeHandler(display).execute(SystemOperation.SetDisplayBrightness(0.0f))
        assertThat(display.setBrightnessCalls[0]).isEqualTo(0)
    }

    @Test
    fun `execute sets brightness to 128 when level is 0_5`() = runTest {
        val display = FakeDisplayService()
        makeHandler(display).execute(SystemOperation.SetDisplayBrightness(0.5f))
        assertThat(display.setBrightnessCalls[0]).isEqualTo(128)  // 0.5 * 255 = 127.5 → 127
    }

    @Test
    fun `execute applies window brightness immediately`() = runTest {
        val display = FakeDisplayService()
        makeHandler(display).execute(SystemOperation.SetDisplayBrightness(0.8f))
        assertThat(display.windowBrightnessApplied).isWithin(0.01f).of(0.8f)
    }

    @Test
    fun `execute disables auto-brightness when setting explicit level`() = runTest {
        val display = FakeDisplayService(autoBrightness = true)
        makeHandler(display).execute(SystemOperation.SetDisplayBrightness(0.5f))
        assertThat(display.autoBrightnessToggled).isFalse()
    }

    @Test
    fun `execute does not toggle auto-brightness when already off`() = runTest {
        val display = FakeDisplayService(autoBrightness = false)
        makeHandler(display).execute(SystemOperation.SetDisplayBrightness(0.5f))
        assertThat(display.autoBrightnessToggled).isNull()
    }

    @Test
    fun `execute returns Failure SpecialAccessRequired on SecurityException`() = runTest {
        val display = FakeDisplayService(
            throwOnSetBrightness = SecurityException("Need WRITE_SETTINGS"),
        )
        val result = makeHandler(display).execute(SystemOperation.SetDisplayBrightness(0.5f))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        val failure = result as Result.Failure
        assertThat(failure.error).isInstanceOf(AppError.Permission.SpecialAccessRequired::class.java)
        assertThat((failure.error as AppError.Permission.SpecialAccessRequired).settingsIntent)
            .isEqualTo(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS)
    }

    @Test
    fun `isAlreadyInTargetState returns false when auto-brightness is on`() = runTest {
        val display = FakeDisplayService(brightness = 128, autoBrightness = true)
        val handler = makeHandler(display)
        // Even if brightness matches, auto-brightness being on means we need to disable it.
        assertThat(handler.isAlreadyInTargetState(SystemOperation.SetDisplayBrightness(0.5f))).isFalse()
    }

    @Test
    fun `isAlreadyInTargetState returns true when manual + level matches`() = runTest {
        val display = FakeDisplayService(brightness = 128, autoBrightness = false)
        val handler = makeHandler(display)
        // 128/255 ≈ 0.502 — within tolerance of 0.5.
        assertThat(handler.isAlreadyInTargetState(SystemOperation.SetDisplayBrightness(0.5f))).isTrue()
    }

    @Test
    fun `isAlreadyInTargetState returns false when level differs`() = runTest {
        val display = FakeDisplayService(brightness = 200, autoBrightness = false)
        val handler = makeHandler(display)
        assertThat(handler.isAlreadyInTargetState(SystemOperation.SetDisplayBrightness(0.5f))).isFalse()
    }

    @Test
    fun `capturePreState returns previous level and auto mode`() = runTest {
        val display = FakeDisplayService(brightness = 200, autoBrightness = true)
        val handler = makeHandler(display)
        val preState = handler.capturePreState(SystemOperation.SetDisplayBrightness(0.5f))
        assertThat(preState).isInstanceOf(BrightnessHandler.BrightnessPreState::class.java)
        val ps = preState as BrightnessHandler.BrightnessPreState
        assertThat(ps.previousLevel255).isEqualTo(200)
        assertThat(ps.wasAuto).isTrue()
    }

    @Test
    fun `restorePreState restores previous brightness and re-enables auto`() = runTest {
        val display = FakeDisplayService(brightness = 100, autoBrightness = false)
        val handler = makeHandler(display)
        val preState = BrightnessHandler.BrightnessPreState(previousLevel255 = 200, wasAuto = true)
        handler.restorePreState(SystemOperation.SetDisplayBrightness(0.5f), preState)
        assertThat(display.setBrightnessCalls).contains(200)
        assertThat(display.autoBrightnessToggled).isTrue()  // re-enabled
    }

    @Test
    fun `describeOperation returns percentage`() {
        val handler = makeHandler(FakeDisplayService())
        assertThat(handler.describeOperation(SystemOperation.SetDisplayBrightness(0.5f))).contains("50%")
        assertThat(handler.describeOperation(SystemOperation.SetDisplayBrightness(1.0f))).contains("100%")
    }

    @Test
    fun `resourceCategory is DISPLAY`() {
        assertThat(makeHandler(FakeDisplayService()).resourceCategory).isEqualTo(ResourceCategory.DISPLAY)
    }

    @Test
    fun `requiredPermissions contains WRITE_SETTINGS`() {
        assertThat(makeHandler(FakeDisplayService()).requiredPermissions)
            .contains(android.Manifest.permission.WRITE_SETTINGS)
    }
}
