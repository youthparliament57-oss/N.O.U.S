// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.system.handler

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.skill.SystemOperation
import com.roshan.persona.common.AppError
import com.roshan.persona.common.Result
import com.roshan.persona.system.executor.ResourceCategory
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0138: [feature] TetheringHandlerTest verified

class TetheringHandlerTest {

    private class FakeTetheringService(
        private val supported: Boolean = true,
        private var active: Boolean = false,
        private val startResult: Boolean = true,
        private val stopResult: Boolean = true,
        private val throwOnStart: Throwable? = null,
    ) : TetheringService {
        val startCalls = mutableListOf<Unit>()
            private set
        val stopCalls = mutableListOf<Unit>()
            private set

        override fun isTetheringSupported(): Boolean = supported
        override fun isTetheringActive(): Boolean = active
        override fun startTethering(): Boolean {
            throwOnStart?.let { throw it }
            startCalls.add(Unit)
            active = startResult
            return startResult
        }
        override fun stopTethering(): Boolean {
            stopCalls.add(Unit)
            active = false
            return stopResult
        }
    }

    private fun makeHandler(service: FakeTetheringService) = TetheringHandler(service)

    // ─── Tests ─────────────────────────────────────────────────────────────

    @Test
    fun `isAvailable returns true when tethering is supported`() {
        assertThat(makeHandler(FakeTetheringService(supported = true)).isAvailable()).isTrue()
    }

    @Test
    fun `isAvailable returns false when tethering is unsupported by carrier`() {
        assertThat(makeHandler(FakeTetheringService(supported = false)).isAvailable()).isFalse()
    }

    @Test
    fun `execute returns Failure Unsupported when tethering not supported`() = runTest {
        val result = makeHandler(FakeTetheringService(supported = false))
            .execute(SystemOperation.SetTethering(true))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unsupported::class.java)
    }

    @Test
    fun `execute starts tethering when enabled is true`() = runTest {
        val service = FakeTetheringService(supported = true, active = false)
        makeHandler(service).execute(SystemOperation.SetTethering(true))
        assertThat(service.startCalls).hasSize(1)
        assertThat(service.stopCalls).isEmpty()
    }

    @Test
    fun `execute stops tethering when enabled is false`() = runTest {
        val service = FakeTetheringService(supported = true, active = true)
        makeHandler(service).execute(SystemOperation.SetTethering(false))
        assertThat(service.stopCalls).hasSize(1)
        assertThat(service.startCalls).isEmpty()
    }

    @Test
    fun `execute maps IllegalArgumentException to Hardware Unsupported (carrier block)`() = runTest {
        val service = FakeTetheringService(
            supported = true,
            throwOnStart = IllegalArgumentException("Carrier blocks tethering"),
        )
        val result = makeHandler(service).execute(SystemOperation.SetTethering(true))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unsupported::class.java)
    }

    @Test
    fun `execute returns Failure Unknown when startTethering returns false`() = runTest {
        val service = FakeTetheringService(supported = true, startResult = false)
        val result = makeHandler(service).execute(SystemOperation.SetTethering(true))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unknown::class.java)
    }

    @Test
    fun `isAlreadyInTargetState returns true when tethering already matches`() = runTest {
        val handler = makeHandler(FakeTetheringService(supported = true, active = true))
        assertThat(handler.isAlreadyInTargetState(SystemOperation.SetTethering(true))).isTrue()
    }

    @Test
    fun `isAlreadyInTargetState returns false when tethering differs`() = runTest {
        val handler = makeHandler(FakeTetheringService(supported = true, active = false))
        assertThat(handler.isAlreadyInTargetState(SystemOperation.SetTethering(true))).isFalse()
    }

    @Test
    fun `capturePreState returns TetheringPreState with previous enabled`() = runTest {
        val handler = makeHandler(FakeTetheringService(supported = true, active = true))
        val preState = handler.capturePreState(SystemOperation.SetTethering(false))
        assertThat(preState).isInstanceOf(TetheringHandler.TetheringPreState::class.java)
        assertThat((preState as TetheringHandler.TetheringPreState).wasEnabled).isTrue()
    }

    @Test
    fun `restorePreState starts tethering when previous was enabled`() = runTest {
        val service = FakeTetheringService(supported = true, active = false)
        val handler = makeHandler(service)
        handler.restorePreState(
            SystemOperation.SetTethering(false),
            TetheringHandler.TetheringPreState(wasEnabled = true),
        )
        assertThat(service.startCalls).hasSize(1)
    }

    @Test
    fun `restorePreState stops tethering when previous was disabled`() = runTest {
        val service = FakeTetheringService(supported = true, active = true)
        val handler = makeHandler(service)
        handler.restorePreState(
            SystemOperation.SetTethering(true),
            TetheringHandler.TetheringPreState(wasEnabled = false),
        )
        assertThat(service.stopCalls).hasSize(1)
    }

    @Test
    fun `describeOperation returns human-readable description`() {
        val handler = makeHandler(FakeTetheringService())
        assertThat(handler.describeOperation(SystemOperation.SetTethering(true))).contains("on")
        assertThat(handler.describeOperation(SystemOperation.SetTethering(false))).contains("off")
    }

    @Test
    fun `resourceCategory is CONNECTIVITY`() {
        assertThat(makeHandler(FakeTetheringService()).resourceCategory).isEqualTo(ResourceCategory.CONNECTIVITY)
    }

    @Test
    fun `requiredPermissions contains CHANGE_NETWORK_STATE and ACCESS_NETWORK_STATE`() {
        val perms = makeHandler(FakeTetheringService()).requiredPermissions
        assertThat(perms).contains(android.Manifest.permission.CHANGE_NETWORK_STATE)
        assertThat(perms).contains(android.Manifest.permission.ACCESS_NETWORK_STATE)
    }
}
