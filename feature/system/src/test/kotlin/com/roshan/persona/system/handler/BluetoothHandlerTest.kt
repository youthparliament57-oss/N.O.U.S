// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.system.handler

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.skill.SystemOperation
import com.roshan.persona.common.AppError
import com.roshan.persona.common.Result
import com.roshan.persona.system.executor.ResourceCategory
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0147: [feature] BluetoothHandlerTest verified

class BluetoothHandlerTest {

    private class FakeBluetoothService(
        private val available: Boolean = true,
        private var enabled: Boolean = false,
        private val canToggleDirectly: Boolean = false,  // default API 31+ behavior
        private val setEnabledResult: Boolean = true,
        private val openPanelResult: Boolean = true,
    ) : BluetoothService {
        val setEnabledCalls = mutableListOf<Boolean>()
            private set
        var openPanelCalls = 0
            private set

        override fun isAvailable(): Boolean = available
        override fun isEnabled(): Boolean = enabled
        override fun canToggleDirectly(): Boolean = canToggleDirectly
        override fun setEnabled(enabled: Boolean): Boolean {
            setEnabledCalls.add(enabled)
            this.enabled = enabled
            return setEnabledResult
        }
        override fun openSettingsPanel(): Boolean {
            openPanelCalls++
            return openPanelResult
        }
    }

    private fun makeHandler(service: FakeBluetoothService) = BluetoothHandler(service)

    // ─── Tests ─────────────────────────────────────────────────────────────

    @Test
    fun `isAvailable returns true when service reports available`() {
        assertThat(makeHandler(FakeBluetoothService(available = true)).isAvailable()).isTrue()
    }

    @Test
    fun `isAvailable returns false when device has no Bluetooth`() {
        assertThat(makeHandler(FakeBluetoothService(available = false)).isAvailable()).isFalse()
    }

    @Test
    fun `execute returns Failure Unavailable when device has no Bluetooth`() = runTest {
        val result = makeHandler(FakeBluetoothService(available = false)).execute(SystemOperation.SetBluetooth(true))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unavailable::class.java)
    }

    @Test
    fun `execute toggles directly when canToggleDirectly is true (API 30-)`() = runTest {
        val service = FakeBluetoothService(canToggleDirectly = true, enabled = false)
        makeHandler(service).execute(SystemOperation.SetBluetooth(true))
        assertThat(service.setEnabledCalls).hasSize(1)
        assertThat(service.setEnabledCalls[0]).isTrue()
        assertThat(service.openPanelCalls).isEqualTo(0)
    }

    @Test
    fun `execute opens Settings Panel when canToggleDirectly is false (API 31+)`() = runTest {
        val service = FakeBluetoothService(canToggleDirectly = false, enabled = false)
        val result = makeHandler(service).execute(SystemOperation.SetBluetooth(true))
        assertThat(service.openPanelCalls).isEqualTo(1)
        assertThat(service.setEnabledCalls).isEmpty()
        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).warnings).contains("opened_settings_panel")
    }

    @Test
    fun `execute returns Failure Unsupported when Settings Panel launch fails`() = runTest {
        val service = FakeBluetoothService(canToggleDirectly = false, openPanelResult = false)
        val result = makeHandler(service).execute(SystemOperation.SetBluetooth(true))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unsupported::class.java)
    }

    @Test
    fun `isAlreadyInTargetState returns true when Bluetooth already matches target`() = runTest {
        val handler = makeHandler(FakeBluetoothService(enabled = true))
        assertThat(handler.isAlreadyInTargetState(SystemOperation.SetBluetooth(true))).isTrue()
    }

    @Test
    fun `isAlreadyInTargetState returns false when Bluetooth differs from target`() = runTest {
        val handler = makeHandler(FakeBluetoothService(enabled = false))
        assertThat(handler.isAlreadyInTargetState(SystemOperation.SetBluetooth(true))).isFalse()
    }

    @Test
    fun `capturePreState returns BluetoothPreState with previous enabled`() = runTest {
        val handler = makeHandler(FakeBluetoothService(enabled = true))
        val preState = handler.capturePreState(SystemOperation.SetBluetooth(false))
        assertThat(preState).isInstanceOf(BluetoothHandler.BluetoothPreState::class.java)
        assertThat((preState as BluetoothHandler.BluetoothPreState).wasEnabled).isTrue()
    }

    @Test
    fun `restorePreState toggles directly when canToggleDirectly is true`() = runTest {
        val service = FakeBluetoothService(canToggleDirectly = true, enabled = false)
        val handler = makeHandler(service)
        handler.restorePreState(SystemOperation.SetBluetooth(true), BluetoothHandler.BluetoothPreState(wasEnabled = true))
        assertThat(service.setEnabledCalls).hasSize(1)
        assertThat(service.setEnabledCalls[0]).isTrue()
    }

    @Test
    fun `restorePreState opens Settings Panel when cannot toggle directly`() = runTest {
        val service = FakeBluetoothService(canToggleDirectly = false, enabled = false)
        val handler = makeHandler(service)
        handler.restorePreState(SystemOperation.SetBluetooth(true), BluetoothHandler.BluetoothPreState(wasEnabled = true))
        assertThat(service.openPanelCalls).isEqualTo(1)
    }

    @Test
    fun `describeOperation returns human-readable description`() {
        val handler = makeHandler(FakeBluetoothService())
        assertThat(handler.describeOperation(SystemOperation.SetBluetooth(true))).isEqualTo("Bluetooth turned on")
        assertThat(handler.describeOperation(SystemOperation.SetBluetooth(false))).isEqualTo("Bluetooth turned off")
    }

    @Test
    fun `resourceCategory is CONNECTIVITY`() {
        assertThat(makeHandler(FakeBluetoothService()).resourceCategory).isEqualTo(ResourceCategory.CONNECTIVITY)
    }
}
