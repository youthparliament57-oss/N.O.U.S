// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.system.handler

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.skill.SystemOperation
import com.roshan.persona.common.AppError
import com.roshan.persona.common.Result
import com.roshan.persona.system.executor.ResourceCategory
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0146: [feature] WifiHandlerTest verified

class WifiHandlerTest {

    private class FakeWifiService(
        private val available: Boolean = true,
        private var enabled: Boolean = false,
        private val canToggleDirectly: Boolean = false,  // default API 29+ behavior
        private val setEnabledResult: Boolean = true,
        private val openPanelResult: Boolean = true,
    ) : WifiService {
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

    private fun makeHandler(service: FakeWifiService) = WifiHandler(service)

    // ─── Tests ─────────────────────────────────────────────────────────────

    @Test
    fun `isAvailable returns true when service reports available`() {
        assertThat(makeHandler(FakeWifiService(available = true)).isAvailable()).isTrue()
    }

    @Test
    fun `isAvailable returns false when service reports unavailable`() {
        assertThat(makeHandler(FakeWifiService(available = false)).isAvailable()).isFalse()
    }

    @Test
    fun `execute returns Failure Unavailable when device has no WiFi`() = runTest {
        val result = makeHandler(FakeWifiService(available = false)).execute(SystemOperation.SetWifi(true))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unavailable::class.java)
    }

    @Test
    fun `execute toggles directly when canToggleDirectly is true (API 28-)`() = runTest {
        val service = FakeWifiService(canToggleDirectly = true, enabled = false)
        makeHandler(service).execute(SystemOperation.SetWifi(true))
        assertThat(service.setEnabledCalls).hasSize(1)
        assertThat(service.setEnabledCalls[0]).isTrue()
        assertThat(service.openPanelCalls).isEqualTo(0)
    }

    @Test
    fun `execute opens Settings Panel when canToggleDirectly is false (API 29+)`() = runTest {
        val service = FakeWifiService(canToggleDirectly = false, enabled = false)
        val result = makeHandler(service).execute(SystemOperation.SetWifi(true))
        assertThat(service.openPanelCalls).isEqualTo(1)
        assertThat(service.setEnabledCalls).isEmpty()
        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).warnings).contains("opened_settings_panel")
    }

    @Test
    fun `execute returns Failure Unsupported when Settings Panel launch fails`() = runTest {
        val service = FakeWifiService(canToggleDirectly = false, openPanelResult = false)
        val result = makeHandler(service).execute(SystemOperation.SetWifi(true))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unsupported::class.java)
    }

    @Test
    fun `execute returns Failure Unknown when direct toggle fails`() = runTest {
        val service = FakeWifiService(canToggleDirectly = true, setEnabledResult = false)
        val result = makeHandler(service).execute(SystemOperation.SetWifi(true))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unknown::class.java)
    }

    @Test
    fun `isAlreadyInTargetState returns true when WiFi already matches target`() = runTest {
        val service = FakeWifiService(enabled = true)
        val handler = makeHandler(service)
        assertThat(handler.isAlreadyInTargetState(SystemOperation.SetWifi(true))).isTrue()
    }

    @Test
    fun `isAlreadyInTargetState returns false when WiFi differs from target`() = runTest {
        val service = FakeWifiService(enabled = false)
        val handler = makeHandler(service)
        assertThat(handler.isAlreadyInTargetState(SystemOperation.SetWifi(true))).isFalse()
    }

    @Test
    fun `capturePreState returns WifiPreState with previous enabled`() = runTest {
        val service = FakeWifiService(enabled = true)
        val handler = makeHandler(service)
        val preState = handler.capturePreState(SystemOperation.SetWifi(false))
        assertThat(preState).isInstanceOf(WifiHandler.WifiPreState::class.java)
        assertThat((preState as WifiHandler.WifiPreState).wasEnabled).isTrue()
    }

    @Test
    fun `restorePreState toggles directly when canToggleDirectly is true`() = runTest {
        val service = FakeWifiService(canToggleDirectly = true, enabled = false)
        val handler = makeHandler(service)
        handler.restorePreState(SystemOperation.SetWifi(true), WifiHandler.WifiPreState(wasEnabled = true))
        assertThat(service.setEnabledCalls).hasSize(1)
        assertThat(service.setEnabledCalls[0]).isTrue()
    }

    @Test
    fun `restorePreState opens Settings Panel when cannot toggle directly`() = runTest {
        val service = FakeWifiService(canToggleDirectly = false, enabled = false)
        val handler = makeHandler(service)
        handler.restorePreState(SystemOperation.SetWifi(true), WifiHandler.WifiPreState(wasEnabled = true))
        assertThat(service.openPanelCalls).isEqualTo(1)
    }

    @Test
    fun `describeOperation returns human-readable description`() {
        val handler = makeHandler(FakeWifiService())
        assertThat(handler.describeOperation(SystemOperation.SetWifi(true))).isEqualTo("Wi-Fi turned on")
        assertThat(handler.describeOperation(SystemOperation.SetWifi(false))).isEqualTo("Wi-Fi turned off")
    }

    @Test
    fun `resourceCategory is CONNECTIVITY`() {
        assertThat(makeHandler(FakeWifiService()).resourceCategory).isEqualTo(ResourceCategory.CONNECTIVITY)
    }
}
