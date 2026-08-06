// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.system.handler

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.skill.SystemOperation
import com.roshan.persona.common.AppError
import com.roshan.persona.common.Result
import com.roshan.persona.system.executor.ResourceCategory
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0141: [feature] ScreenHandlerTest verified

class ScreenHandlerTest {

    /** Fake accessibility service. */
    private class FakeAccessibilityService(
        private val enabled: Boolean = true,
        private val actionResult: Boolean = true,
    ) : NousAccessibilityServiceInterface {
        val performedActions = mutableListOf<Int>()
            private set

        override fun isServiceEnabled(): Boolean = enabled
        override fun performGlobalAction(action: Int): Boolean {
            performedActions.add(action)
            return actionResult
        }
    }

    /** Reuse FakeDisplayService from BrightnessHandlerTest. */
    private class FakeDisplayService(
        private val keepScreenOnResult: Boolean = true,
    ) : DisplayService {
        val keepScreenOnCalls = mutableListOf<Boolean>()
            private set

        override fun getBrightness(): Int = 128
        override fun setBrightness(level: Int) {}
        override fun isAutoBrightness(): Boolean = false
        override fun setAutoBrightness(enabled: Boolean) {}
        override fun applyWindowBrightness(level: Float?) {}
        override fun setKeepScreenOn(enabled: Boolean): Boolean {
            keepScreenOnCalls.add(enabled)
            return keepScreenOnResult
        }
    }

    private fun makeHandler(
        accessibility: NousAccessibilityServiceInterface = FakeAccessibilityService(),
        display: DisplayService = FakeDisplayService(),
    ) = ScreenHandler(accessibility, display)

    // ─── LockScreen tests (Review 2 Fix B) ─────────────────────────────────

    @Test
    fun `execute LockScreen uses AccessibilityService GLOBAL_ACTION_LOCK_SCREEN`() = runTest {
        val accessibility = FakeAccessibilityService(enabled = true)
        makeHandler(accessibility).execute(SystemOperation.LockScreen)
        assertThat(accessibility.performedActions).hasSize(1)
        assertThat(accessibility.performedActions[0]).isEqualTo(AccessibilityActions.GLOBAL_ACTION_LOCK_SCREEN)
    }

    @Test
    fun `execute LockScreen returns Failure when accessibility service disabled`() = runTest {
        val accessibility = FakeAccessibilityService(enabled = false)
        val result = makeHandler(accessibility).execute(SystemOperation.LockScreen)
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        val failure = result as Result.Failure
        assertThat(failure.error).isInstanceOf(AppError.Hardware.Unavailable::class.java)
        assertThat((failure.error as AppError.Hardware.Unavailable).resource).isEqualTo("accessibility_service")
    }

    @Test
    fun `execute LockScreen returns Failure when performGlobalAction returns false`() = runTest {
        val accessibility = FakeAccessibilityService(enabled = true, actionResult = false)
        val result = makeHandler(accessibility).execute(SystemOperation.LockScreen)
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unknown::class.java)
    }

    @Test
    fun `execute LockScreen returns Success when accessibility performs action`() = runTest {
        val accessibility = FakeAccessibilityService(enabled = true, actionResult = true)
        val result = makeHandler(accessibility).execute(SystemOperation.LockScreen)
        assertThat(result).isInstanceOf(Result.Success::class.java)
    }

    // ─── SetKeepScreenOn tests ─────────────────────────────────────────────

    @Test
    fun `execute SetKeepScreenOn true calls displayService setKeepScreenOn true`() = runTest {
        val display = FakeDisplayService()
        makeHandler(display = display).execute(SystemOperation.SetKeepScreenOn(true))
        assertThat(display.keepScreenOnCalls).hasSize(1)
        assertThat(display.keepScreenOnCalls[0]).isTrue()
    }

    @Test
    fun `execute SetKeepScreenOn false calls displayService setKeepScreenOn false`() = runTest {
        val display = FakeDisplayService()
        makeHandler(display = display).execute(SystemOperation.SetKeepScreenOn(false))
        assertThat(display.keepScreenOnCalls[0]).isFalse()
    }

    @Test
    fun `execute SetKeepScreenOn returns Failure when no foreground window`() = runTest {
        val display = FakeDisplayService(keepScreenOnResult = false)
        val result = makeHandler(display = display).execute(SystemOperation.SetKeepScreenOn(true))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unavailable::class.java)
    }

    @Test
    fun `execute returns Failure for unsupported operation type`() = runTest {
        val result = makeHandler().execute(SystemOperation.SetTorch(true))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unsupported::class.java)
    }

    // ─── Pre-state capture ─────────────────────────────────────────────────

    @Test
    fun `capturePreState returns null for LockScreen (not reversible)`() = runTest {
        val handler = makeHandler()
        val preState = handler.capturePreState(SystemOperation.LockScreen)
        assertThat(preState).isNull()
    }

    @Test
    fun `capturePreState returns KeepScreenOnPreState for SetKeepScreenOn true`() = runTest {
        val handler = makeHandler()
        val preState = handler.capturePreState(SystemOperation.SetKeepScreenOn(true))
        assertThat(preState).isInstanceOf(ScreenHandler.KeepScreenOnPreState::class.java)
        assertThat((preState as ScreenHandler.KeepScreenOnPreState).previousEnabled).isFalse()
    }

    @Test
    fun `capturePreState returns KeepScreenOnPreState for SetKeepScreenOn false`() = runTest {
        val handler = makeHandler()
        val preState = handler.capturePreState(SystemOperation.SetKeepScreenOn(false))
        assertThat(preState).isInstanceOf(ScreenHandler.KeepScreenOnPreState::class.java)
        assertThat((preState as ScreenHandler.KeepScreenOnPreState).previousEnabled).isTrue()
    }

    @Test
    fun `restorePreState toggles keep-screen-on back for SetKeepScreenOn`() = runTest {
        val display = FakeDisplayService()
        val handler = makeHandler(display = display)
        val preState = ScreenHandler.KeepScreenOnPreState(previousEnabled = false)
        handler.restorePreState(SystemOperation.SetKeepScreenOn(true), preState)
        assertThat(display.keepScreenOnCalls).hasSize(1)
        assertThat(display.keepScreenOnCalls[0]).isFalse()
    }

    @Test
    fun `restorePreState is no-op for LockScreen`() = runTest {
        val display = FakeDisplayService()
        val handler = makeHandler(display = display)
        // Should not throw or call display.
        handler.restorePreState(SystemOperation.LockScreen, null)
        assertThat(display.keepScreenOnCalls).isEmpty()
    }

    // ─── Idempotent ────────────────────────────────────────────────────────

    @Test
    fun `isAlreadyInTargetState always returns false (cannot read window flag)`() = runTest {
        val handler = makeHandler()
        assertThat(handler.isAlreadyInTargetState(SystemOperation.LockScreen)).isFalse()
        assertThat(handler.isAlreadyInTargetState(SystemOperation.SetKeepScreenOn(true))).isFalse()
    }

    @Test
    fun `isAvailable always returns true`() {
        assertThat(makeHandler().isAvailable()).isTrue()
    }

    @Test
    fun `resourceCategory is DISPLAY`() {
        assertThat(makeHandler().resourceCategory).isEqualTo(ResourceCategory.DISPLAY)
    }

    @Test
    fun `requiredPermissions is empty`() {
        assertThat(makeHandler().requiredPermissions).isEmpty()
    }

    @Test
    fun `AccessibilityActions constants match Android AccessibilityService values`() {
        // Sanity check — these are the constants we use to call performGlobalAction.
        assertThat(AccessibilityActions.GLOBAL_ACTION_LOCK_SCREEN).isEqualTo(8)
        assertThat(AccessibilityActions.GLOBAL_ACTION_BACK).isEqualTo(1)
        assertThat(AccessibilityActions.GLOBAL_ACTION_HOME).isEqualTo(2)
    }

    @Test
    fun `NoopAccessibilityService returns false for all actions`() {
        val noop = NoopAccessibilityService()
        assertThat(noop.isServiceEnabled()).isFalse()
        assertThat(noop.performGlobalAction(AccessibilityActions.GLOBAL_ACTION_LOCK_SCREEN)).isFalse()
    }
}
