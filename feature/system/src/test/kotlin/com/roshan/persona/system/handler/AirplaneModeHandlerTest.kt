// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.system.handler

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.skill.SystemOperation
import com.roshan.persona.common.AppError
import com.roshan.persona.common.Result
import com.roshan.persona.system.executor.ResourceCategory
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0142: [feature] AirplaneModeHandlerTest verified

class AirplaneModeHandlerTest {

    private class FakeAirplaneModeService(
        private var on: Boolean = false,
        private val canToggleDirectly: Boolean = false,  // default regular app
        private val setOnResult: Boolean = true,
        private val openSettingsResult: Boolean = true,
    ) : AirplaneModeService {
        val setOnCalls = mutableListOf<Boolean>()
            private set
        var openSettingsCalls = 0
            private set

        override fun isOn(): Boolean = on
        override fun canToggleDirectly(): Boolean = canToggleDirectly
        override fun setOn(on: Boolean): Boolean {
            setOnCalls.add(on)
            this.on = on
            return setOnResult
        }
        override fun openSettingsPage(): Boolean {
            openSettingsCalls++
            return openSettingsResult
        }
    }

    private fun makeHandler(service: FakeAirplaneModeService) = AirplaneModeHandler(service)

    // ─── Tests ─────────────────────────────────────────────────────────────

    @Test
    fun `isAvailable always returns true (all devices support airplane mode)`() {
        assertThat(makeHandler(FakeAirplaneModeService()).isAvailable()).isTrue()
    }

    @Test
    fun `execute toggles directly when canToggleDirectly is true (system app + rooted)`() = runTest {
        val service = FakeAirplaneModeService(canToggleDirectly = true, on = false)
        makeHandler(service).execute(SystemOperation.SetAirplaneMode(true))
        assertThat(service.setOnCalls).hasSize(1)
        assertThat(service.setOnCalls[0]).isTrue()
        assertThat(service.openSettingsCalls).isEqualTo(0)
    }

    @Test
    fun `execute opens Settings page when canToggleDirectly is false (regular app)`() = runTest {
        val service = FakeAirplaneModeService(canToggleDirectly = false, on = false)
        val result = makeHandler(service).execute(SystemOperation.SetAirplaneMode(true))
        assertThat(service.openSettingsCalls).isEqualTo(1)
        assertThat(service.setOnCalls).isEmpty()
        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).warnings).contains("opened_settings_page")
    }

    @Test
    fun `execute returns Failure Unsupported when Settings page launch fails`() = runTest {
        val service = FakeAirplaneModeService(canToggleDirectly = false, openSettingsResult = false)
        val result = makeHandler(service).execute(SystemOperation.SetAirplaneMode(true))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unsupported::class.java)
    }

    @Test
    fun `execute returns Failure Unknown when direct toggle fails`() = runTest {
        val service = FakeAirplaneModeService(canToggleDirectly = true, setOnResult = false)
        val result = makeHandler(service).execute(SystemOperation.SetAirplaneMode(true))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unknown::class.java)
    }

    @Test
    fun `isAlreadyInTargetState returns true when airplane mode already matches`() = runTest {
        val handler = makeHandler(FakeAirplaneModeService(on = true))
        assertThat(handler.isAlreadyInTargetState(SystemOperation.SetAirplaneMode(true))).isTrue()
    }

    @Test
    fun `isAlreadyInTargetState returns false when airplane mode differs`() = runTest {
        val handler = makeHandler(FakeAirplaneModeService(on = false))
        assertThat(handler.isAlreadyInTargetState(SystemOperation.SetAirplaneMode(true))).isFalse()
    }

    @Test
    fun `capturePreState returns AirplaneModePreState with previous on`() = runTest {
        val handler = makeHandler(FakeAirplaneModeService(on = true))
        val preState = handler.capturePreState(SystemOperation.SetAirplaneMode(false))
        assertThat(preState).isInstanceOf(AirplaneModeHandler.AirplaneModePreState::class.java)
        assertThat((preState as AirplaneModeHandler.AirplaneModePreState).wasOn).isTrue()
    }

    @Test
    fun `restorePreState toggles directly when canToggleDirectly is true`() = runTest {
        val service = FakeAirplaneModeService(canToggleDirectly = true, on = false)
        val handler = makeHandler(service)
        handler.restorePreState(SystemOperation.SetAirplaneMode(true), AirplaneModeHandler.AirplaneModePreState(wasOn = true))
        assertThat(service.setOnCalls).hasSize(1)
        assertThat(service.setOnCalls[0]).isTrue()
    }

    @Test
    fun `restorePreState opens Settings page when cannot toggle directly`() = runTest {
        val service = FakeAirplaneModeService(canToggleDirectly = false, on = false)
        val handler = makeHandler(service)
        handler.restorePreState(SystemOperation.SetAirplaneMode(true), AirplaneModeHandler.AirplaneModePreState(wasOn = true))
        assertThat(service.openSettingsCalls).isEqualTo(1)
    }

    @Test
    fun `describeOperation returns human-readable description`() {
        val handler = makeHandler(FakeAirplaneModeService())
        assertThat(handler.describeOperation(SystemOperation.SetAirplaneMode(true))).isEqualTo("Airplane mode turned on")
        assertThat(handler.describeOperation(SystemOperation.SetAirplaneMode(false))).isEqualTo("Airplane mode turned off")
    }

    @Test
    fun `resourceCategory is CONNECTIVITY`() {
        assertThat(makeHandler(FakeAirplaneModeService()).resourceCategory).isEqualTo(ResourceCategory.CONNECTIVITY)
    }

    @Test
    fun `requiredPermissions is empty (WRITE_SECURE_SETTINGS checked at runtime)`() {
        assertThat(makeHandler(FakeAirplaneModeService()).requiredPermissions).isEmpty()
    }
}
