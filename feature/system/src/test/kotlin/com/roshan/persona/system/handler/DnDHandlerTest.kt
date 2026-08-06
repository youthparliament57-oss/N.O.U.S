// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.system.handler

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.skill.DndMode
import com.roshan.persona.brain.skill.SystemOperation
import com.roshan.persona.common.AppError
import com.roshan.persona.common.Result
import com.roshan.persona.system.executor.ResourceCategory
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0149: [feature] DnDHandlerTest verified

class DnDHandlerTest {

    private class FakeDnDService(
        private var mode: DndMode = DndMode.ALL,
        private val hasAccess: Boolean = true,
        private val setModeResult: Boolean = true,
    ) : DnDService {
        val setModeCalls = mutableListOf<DndMode>()
            private set

        override fun getMode(): DndMode = mode
        override fun setMode(mode: DndMode): Boolean {
            setModeCalls.add(mode)
            this.mode = mode
            return setModeResult
        }
        override fun hasDndAccess(): Boolean = hasAccess
    }

    private fun makeHandler(service: FakeDnDService) = DnDHandler(service)

    @Test
    fun `isAvailable always returns true`() {
        assertThat(makeHandler(FakeDnDService()).isAvailable()).isTrue()
    }

    @Test
    fun `execute sets DnD to NONE when access granted`() = runTest {
        val service = FakeDnDService(hasAccess = true, mode = DndMode.ALL)
        makeHandler(service).execute(SystemOperation.SetDndMode(DndMode.NONE))
        assertThat(service.setModeCalls).hasSize(1)
        assertThat(service.setModeCalls[0]).isEqualTo(DndMode.NONE)
    }

    @Test
    fun `execute returns Failure SpecialAccessRequired when DnD access missing`() = runTest {
        val service = FakeDnDService(hasAccess = false)
        val result = makeHandler(service).execute(SystemOperation.SetDndMode(DndMode.NONE))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Permission.SpecialAccessRequired::class.java)
        assertThat(service.setModeCalls).isEmpty()
    }

    @Test
    fun `execute returns Failure Unknown when setMode returns false`() = runTest {
        val service = FakeDnDService(hasAccess = true, setModeResult = false)
        val result = makeHandler(service).execute(SystemOperation.SetDndMode(DndMode.PRIORITY))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unknown::class.java)
    }

    @Test
    fun `isAlreadyInTargetState returns true when mode matches`() = runTest {
        val handler = makeHandler(FakeDnDService(mode = DndMode.PRIORITY))
        assertThat(handler.isAlreadyInTargetState(SystemOperation.SetDndMode(DndMode.PRIORITY))).isTrue()
    }

    @Test
    fun `capturePreState returns DndPreState with previous mode`() = runTest {
        val handler = makeHandler(FakeDnDService(mode = DndMode.ALARMS))
        val preState = handler.capturePreState(SystemOperation.SetDndMode(DndMode.NONE))
        assertThat((preState as DnDHandler.DndPreState).previousMode).isEqualTo(DndMode.ALARMS)
    }

    @Test
    fun `restorePreState restores previous mode`() = runTest {
        val service = FakeDnDService(mode = DndMode.NONE)
        val handler = makeHandler(service)
        handler.restorePreState(SystemOperation.SetDndMode(DndMode.NONE), DnDHandler.DndPreState(DndMode.PRIORITY))
        assertThat(service.setModeCalls).hasSize(1)
        assertThat(service.setModeCalls[0]).isEqualTo(DndMode.PRIORITY)
    }

    @Test
    fun `describeOperation returns human-readable for each mode`() {
        val handler = makeHandler(FakeDnDService())
        assertThat(handler.describeOperation(SystemOperation.SetDndMode(DndMode.NONE))).contains("silence")
        assertThat(handler.describeOperation(SystemOperation.SetDndMode(DndMode.PRIORITY))).contains("priority")
        assertThat(handler.describeOperation(SystemOperation.SetDndMode(DndMode.ALARMS))).contains("alarms")
        assertThat(handler.describeOperation(SystemOperation.SetDndMode(DndMode.ALL))).contains("off")
    }

    @Test
    fun `resourceCategory is AUDIO`() {
        assertThat(makeHandler(FakeDnDService()).resourceCategory).isEqualTo(ResourceCategory.AUDIO)
    }
}
