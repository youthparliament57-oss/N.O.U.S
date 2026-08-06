// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.system.handler

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.skill.SystemOperation
import com.roshan.persona.common.AppError
import com.roshan.persona.common.Result
import com.roshan.persona.system.executor.ResourceCategory
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0150: [feature] NfcHandlerTest verified

class NfcHandlerTest {

    private class FakeNfcService(
        private val available: Boolean = true,
        private var enabled: Boolean = false,
        private val canToggleDirectly: Boolean = false,  // default regular app
        private val setEnabledResult: Boolean = true,
        private val openSettingsResult: Boolean = true,
        private val throwOnSetEnabled: Throwable? = null,
    ) : NfcService {
        val setEnabledCalls = mutableListOf<Boolean>()
            private set
        var openSettingsCalls = 0
            private set

        override fun isAvailable(): Boolean = available
        override fun isEnabled(): Boolean = enabled
        override fun canToggleDirectly(): Boolean = canToggleDirectly
        override fun setEnabled(enabled: Boolean): Boolean {
            throwOnSetEnabled?.let { throw it }
            setEnabledCalls.add(enabled)
            this.enabled = enabled
            return setEnabledResult
        }
        override fun openSettingsPage(): Boolean {
            openSettingsCalls++
            return openSettingsResult
        }
    }

    private fun makeHandler(service: FakeNfcService) = NfcHandler(service)

    // ─── Tests ─────────────────────────────────────────────────────────────

    @Test
    fun `isAvailable returns true when device has NFC`() {
        assertThat(makeHandler(FakeNfcService(available = true)).isAvailable()).isTrue()
    }

    @Test
    fun `isAvailable returns false when device has no NFC chip`() {
        assertThat(makeHandler(FakeNfcService(available = false)).isAvailable()).isFalse()
    }

    @Test
    fun `execute returns Failure Unavailable when device has no NFC`() = runTest {
        val result = makeHandler(FakeNfcService(available = false))
            .execute(SystemOperation.SetNfc(true))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unavailable::class.java)
    }

    @Test
    fun `execute toggles directly when canToggleDirectly is true (system + rooted)`() = runTest {
        val service = FakeNfcService(canToggleDirectly = true, enabled = false)
        makeHandler(service).execute(SystemOperation.SetNfc(true))
        assertThat(service.setEnabledCalls).hasSize(1)
        assertThat(service.setEnabledCalls[0]).isTrue()
        assertThat(service.openSettingsCalls).isEqualTo(0)
    }

    @Test
    fun `execute opens Settings page when canToggleDirectly is false (regular app)`() = runTest {
        val service = FakeNfcService(canToggleDirectly = false, enabled = false)
        val result = makeHandler(service).execute(SystemOperation.SetNfc(true))
        assertThat(service.openSettingsCalls).isEqualTo(1)
        assertThat(service.setEnabledCalls).isEmpty()
        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).warnings).contains("opened_settings_page")
    }

    @Test
    fun `execute returns Failure Unsupported when Settings page launch fails`() = runTest {
        val service = FakeNfcService(canToggleDirectly = false, openSettingsResult = false)
        val result = makeHandler(service).execute(SystemOperation.SetNfc(true))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unsupported::class.java)
    }

    @Test
    fun `execute maps UnsupportedOperationException to Hardware Unsupported`() = runTest {
        val service = FakeNfcService(
            canToggleDirectly = true,
            throwOnSetEnabled = UnsupportedOperationException("NFC toggle not supported"),
        )
        val result = makeHandler(service).execute(SystemOperation.SetNfc(true))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unsupported::class.java)
    }

    @Test
    fun `execute returns Failure Unknown when direct toggle returns false`() = runTest {
        val service = FakeNfcService(canToggleDirectly = true, setEnabledResult = false)
        val result = makeHandler(service).execute(SystemOperation.SetNfc(true))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unknown::class.java)
    }

    @Test
    fun `isAlreadyInTargetState returns true when NFC already matches target`() = runTest {
        val handler = makeHandler(FakeNfcService(enabled = true))
        assertThat(handler.isAlreadyInTargetState(SystemOperation.SetNfc(true))).isTrue()
    }

    @Test
    fun `isAlreadyInTargetState returns false when NFC differs from target`() = runTest {
        val handler = makeHandler(FakeNfcService(enabled = false))
        assertThat(handler.isAlreadyInTargetState(SystemOperation.SetNfc(true))).isFalse()
    }

    @Test
    fun `capturePreState returns NfcPreState with previous enabled`() = runTest {
        val handler = makeHandler(FakeNfcService(enabled = true))
        val preState = handler.capturePreState(SystemOperation.SetNfc(false))
        assertThat(preState).isInstanceOf(NfcHandler.NfcPreState::class.java)
        assertThat((preState as NfcHandler.NfcPreState).wasEnabled).isTrue()
    }

    @Test
    fun `restorePreState toggles directly when canToggleDirectly is true`() = runTest {
        val service = FakeNfcService(canToggleDirectly = true, enabled = false)
        val handler = makeHandler(service)
        handler.restorePreState(
            SystemOperation.SetNfc(true),
            NfcHandler.NfcPreState(wasEnabled = true),
        )
        assertThat(service.setEnabledCalls).hasSize(1)
        assertThat(service.setEnabledCalls[0]).isTrue()
    }

    @Test
    fun `restorePreState opens Settings page when cannot toggle directly`() = runTest {
        val service = FakeNfcService(canToggleDirectly = false, enabled = false)
        val handler = makeHandler(service)
        handler.restorePreState(
            SystemOperation.SetNfc(true),
            NfcHandler.NfcPreState(wasEnabled = true),
        )
        assertThat(service.openSettingsCalls).isEqualTo(1)
    }

    @Test
    fun `describeOperation returns human-readable description`() {
        val handler = makeHandler(FakeNfcService())
        assertThat(handler.describeOperation(SystemOperation.SetNfc(true))).isEqualTo("NFC turned on")
        assertThat(handler.describeOperation(SystemOperation.SetNfc(false))).isEqualTo("NFC turned off")
    }

    @Test
    fun `resourceCategory is CONNECTIVITY`() {
        assertThat(makeHandler(FakeNfcService()).resourceCategory).isEqualTo(ResourceCategory.CONNECTIVITY)
    }

    @Test
    fun `requiredPermissions contains NFC`() {
        assertThat(makeHandler(FakeNfcService()).requiredPermissions)
            .contains(android.Manifest.permission.NFC)
    }
}
