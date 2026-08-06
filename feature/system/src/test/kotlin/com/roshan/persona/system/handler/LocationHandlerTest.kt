// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.system.handler

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.skill.LocationMode
import com.roshan.persona.brain.skill.SystemOperation
import com.roshan.persona.common.AppError
import com.roshan.persona.common.Result
import com.roshan.persona.system.executor.ResourceCategory
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0139: [feature] LocationHandlerTest verified

class LocationHandlerTest {

    private class FakeLocationService(
        private var mode: LocationMode = LocationMode.OFF,
        private val canToggleDirectly: Boolean = false,
        private val setModeResult: Boolean = true,
        private val openSettingsResult: Boolean = true,
    ) : LocationService {
        val setModeCalls = mutableListOf<LocationMode>()
            private set
        var openSettingsCalls = 0
            private set

        override fun getMode(): LocationMode = mode
        override fun canToggleDirectly(): Boolean = canToggleDirectly
        override fun setMode(mode: LocationMode): Boolean {
            setModeCalls.add(mode)
            this.mode = mode
            return setModeResult
        }
        override fun openSettingsPage(): Boolean {
            openSettingsCalls++
            return openSettingsResult
        }
    }

    private fun makeHandler(service: FakeLocationService) = LocationHandler(service)

    // ─── Tests ─────────────────────────────────────────────────────────────

    @Test
    fun `isAvailable always returns true`() {
        assertThat(makeHandler(FakeLocationService()).isAvailable()).isTrue()
    }

    @Test
    fun `execute toggles directly when canToggleDirectly is true (system + rooted)`() = runTest {
        val service = FakeLocationService(canToggleDirectly = true, mode = LocationMode.OFF)
        makeHandler(service).execute(SystemOperation.SetLocationMode(LocationMode.HIGH_ACCURACY))
        assertThat(service.setModeCalls).hasSize(1)
        assertThat(service.setModeCalls[0]).isEqualTo(LocationMode.HIGH_ACCURACY)
        assertThat(service.openSettingsCalls).isEqualTo(0)
    }

    @Test
    fun `execute opens Settings page when canToggleDirectly is false (regular app)`() = runTest {
        val service = FakeLocationService(canToggleDirectly = false, mode = LocationMode.OFF)
        val result = makeHandler(service).execute(SystemOperation.SetLocationMode(LocationMode.HIGH_ACCURACY))
        assertThat(service.openSettingsCalls).isEqualTo(1)
        assertThat(service.setModeCalls).isEmpty()
        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).warnings).contains("opened_settings_page")
    }

    @Test
    fun `execute returns Failure Unsupported when Settings page launch fails`() = runTest {
        val service = FakeLocationService(canToggleDirectly = false, openSettingsResult = false)
        val result = makeHandler(service).execute(SystemOperation.SetLocationMode(LocationMode.HIGH_ACCURACY))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unsupported::class.java)
    }

    @Test
    fun `execute returns Failure Unknown when direct toggle fails`() = runTest {
        val service = FakeLocationService(canToggleDirectly = true, setModeResult = false)
        val result = makeHandler(service).execute(SystemOperation.SetLocationMode(LocationMode.HIGH_ACCURACY))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unknown::class.java)
    }

    @Test
    fun `isAlreadyInTargetState returns true when mode already matches`() = runTest {
        val handler = makeHandler(FakeLocationService(mode = LocationMode.HIGH_ACCURACY))
        assertThat(handler.isAlreadyInTargetState(SystemOperation.SetLocationMode(LocationMode.HIGH_ACCURACY))).isTrue()
    }

    @Test
    fun `isAlreadyInTargetState returns false when mode differs`() = runTest {
        val handler = makeHandler(FakeLocationService(mode = LocationMode.OFF))
        assertThat(handler.isAlreadyInTargetState(SystemOperation.SetLocationMode(LocationMode.HIGH_ACCURACY))).isFalse()
    }

    @Test
    fun `capturePreState returns LocationPreState with previous mode`() = runTest {
        val handler = makeHandler(FakeLocationService(mode = LocationMode.BATTERY_SAVING))
        val preState = handler.capturePreState(SystemOperation.SetLocationMode(LocationMode.OFF))
        assertThat(preState).isInstanceOf(LocationHandler.LocationPreState::class.java)
        assertThat((preState as LocationHandler.LocationPreState).previousMode).isEqualTo(LocationMode.BATTERY_SAVING)
    }

    @Test
    fun `restorePreState toggles directly when canToggleDirectly is true`() = runTest {
        val service = FakeLocationService(canToggleDirectly = true, mode = LocationMode.HIGH_ACCURACY)
        val handler = makeHandler(service)
        handler.restorePreState(
            SystemOperation.SetLocationMode(LocationMode.HIGH_ACCURACY),
            LocationHandler.LocationPreState(previousMode = LocationMode.OFF),
        )
        assertThat(service.setModeCalls).hasSize(1)
        assertThat(service.setModeCalls[0]).isEqualTo(LocationMode.OFF)
    }

    @Test
    fun `restorePreState opens Settings page when cannot toggle directly`() = runTest {
        val service = FakeLocationService(canToggleDirectly = false, mode = LocationMode.HIGH_ACCURACY)
        val handler = makeHandler(service)
        handler.restorePreState(
            SystemOperation.SetLocationMode(LocationMode.HIGH_ACCURACY),
            LocationHandler.LocationPreState(previousMode = LocationMode.OFF),
        )
        assertThat(service.openSettingsCalls).isEqualTo(1)
    }

    @Test
    fun `describeOperation returns human-readable description for each mode`() {
        val handler = makeHandler(FakeLocationService())
        assertThat(handler.describeOperation(SystemOperation.SetLocationMode(LocationMode.HIGH_ACCURACY))).contains("high accuracy")
        assertThat(handler.describeOperation(SystemOperation.SetLocationMode(LocationMode.BATTERY_SAVING))).contains("battery saving")
        assertThat(handler.describeOperation(SystemOperation.SetLocationMode(LocationMode.SENSORS_ONLY))).contains("sensors only")
        assertThat(handler.describeOperation(SystemOperation.SetLocationMode(LocationMode.OFF))).contains("off")
    }

    @Test
    fun `resourceCategory is CONNECTIVITY`() {
        assertThat(makeHandler(FakeLocationService()).resourceCategory).isEqualTo(ResourceCategory.CONNECTIVITY)
    }

    @Test
    fun `requiredPermissions contains ACCESS_FINE_LOCATION and ACCESS_COARSE_LOCATION`() {
        val perms = makeHandler(FakeLocationService()).requiredPermissions
        assertThat(perms).contains(android.Manifest.permission.ACCESS_FINE_LOCATION)
        assertThat(perms).contains(android.Manifest.permission.ACCESS_COARSE_LOCATION)
    }
}
