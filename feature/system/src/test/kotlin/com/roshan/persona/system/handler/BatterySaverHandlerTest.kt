// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.system.handler

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.skill.SystemOperation
import com.roshan.persona.common.AppError
import com.roshan.persona.common.Result
import com.roshan.persona.system.executor.ResourceCategory
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0148: [feature] BatterySaverHandlerTest verified

class BatterySaverHandlerTest {

    private class FakeBatterySaverService(
        private var on: Boolean = false,
        private val setResult: Boolean = true,
    ) : BatterySaverService {
        val setOnCalls = mutableListOf<Boolean>()
            private set

        override fun isOn(): Boolean = on
        override fun setOn(on: Boolean): Boolean {
            setOnCalls.add(on)
            this.on = on
            return setResult
        }
    }

    private fun makeHandler(service: FakeBatterySaverService) = BatterySaverHandler(service)

    @Test
    fun `isAvailable always returns true`() {
        assertThat(makeHandler(FakeBatterySaverService()).isAvailable()).isTrue()
    }

    @Test
    fun `execute turns battery saver on`() = runTest {
        val service = FakeBatterySaverService(on = false)
        makeHandler(service).execute(SystemOperation.SetBatterySaver(true))
        assertThat(service.setOnCalls).hasSize(1)
        assertThat(service.setOnCalls[0]).isTrue()
    }

    @Test
    fun `execute turns battery saver off`() = runTest {
        val service = FakeBatterySaverService(on = true)
        makeHandler(service).execute(SystemOperation.SetBatterySaver(false))
        assertThat(service.setOnCalls[0]).isFalse()
    }

    @Test
    fun `execute returns Failure Unknown when setOn returns false`() = runTest {
        val service = FakeBatterySaverService(setResult = false)
        val result = makeHandler(service).execute(SystemOperation.SetBatterySaver(true))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unknown::class.java)
    }

    @Test
    fun `isAlreadyInTargetState returns true when already matches`() = runTest {
        val handler = makeHandler(FakeBatterySaverService(on = true))
        assertThat(handler.isAlreadyInTargetState(SystemOperation.SetBatterySaver(true))).isTrue()
    }

    @Test
    fun `capturePreState returns BatterySaverPreState with previous on`() = runTest {
        val handler = makeHandler(FakeBatterySaverService(on = true))
        val preState = handler.capturePreState(SystemOperation.SetBatterySaver(false))
        assertThat((preState as BatterySaverHandler.BatterySaverPreState).wasOn).isTrue()
    }

    @Test
    fun `restorePreState restores previous state`() = runTest {
        val service = FakeBatterySaverService(on = false)
        val handler = makeHandler(service)
        handler.restorePreState(
            SystemOperation.SetBatterySaver(true),
            BatterySaverHandler.BatterySaverPreState(wasOn = true),
        )
        assertThat(service.setOnCalls[0]).isTrue()
    }

    @Test
    fun `describeOperation returns human-readable`() {
        val handler = makeHandler(FakeBatterySaverService())
        assertThat(handler.describeOperation(SystemOperation.SetBatterySaver(true))).contains("on")
        assertThat(handler.describeOperation(SystemOperation.SetBatterySaver(false))).contains("off")
    }

    @Test
    fun `resourceCategory is SETTINGS`() {
        assertThat(makeHandler(FakeBatterySaverService()).resourceCategory).isEqualTo(ResourceCategory.SETTINGS)
    }

    @Test
    fun `requiredPermissions is empty`() {
        assertThat(makeHandler(FakeBatterySaverService()).requiredPermissions).isEmpty()
    }
}
