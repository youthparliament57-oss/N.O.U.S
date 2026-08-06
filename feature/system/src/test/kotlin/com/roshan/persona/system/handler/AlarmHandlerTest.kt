// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.system.handler

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.skill.SystemOperation
import com.roshan.persona.common.AppError
import com.roshan.persona.common.Result
import com.roshan.persona.system.executor.ResourceCategory
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0154: [feature] AlarmHandlerTest verified

class AlarmHandlerTest {

    private class FakeAlarmClockService(
        private val setAlarmResult: Boolean = true,
        private val throwOnSetAlarm: Throwable? = null,
    ) : AlarmClockService {
        val setAlarmCalls = mutableListOf<Triple<Int, Int, String?>>()
            private set
        var showAlarmsCalls = 0
            private set

        override fun setAlarm(hour: Int, minute: Int, label: String?, daysOfWeek: IntArray): Boolean {
            throwOnSetAlarm?.let { throw it }
            setAlarmCalls.add(Triple(hour, minute, label))
            return setAlarmResult
        }
        override fun showAlarms(): Boolean {
            showAlarmsCalls++
            return true
        }
    }

    private fun makeHandler(service: FakeAlarmClockService) = AlarmHandler(service)

    // ─── Tests ─────────────────────────────────────────────────────────────

    @Test
    fun `isAvailable always returns true`() {
        assertThat(makeHandler(FakeAlarmClockService()).isAvailable()).isTrue()
    }

    @Test
    fun `execute sets alarm with valid hour and minute`() = runTest {
        val service = FakeAlarmClockService()
        makeHandler(service).execute(SystemOperation.SetAlarm(hour = 7, minute = 30, label = "Wake up"))
        assertThat(service.setAlarmCalls).hasSize(1)
        assertThat(service.setAlarmCalls[0]).isEqualTo(Triple(7, 30, "Wake up"))
    }

    @Test
    fun `execute sets alarm without label`() = runTest {
        val service = FakeAlarmClockService()
        makeHandler(service).execute(SystemOperation.SetAlarm(hour = 7, minute = 30, label = null))
        assertThat(service.setAlarmCalls[0].third).isNull()
    }

    @Test
    fun `execute returns Failure Unsupported for invalid hour (negative)`() = runTest {
        val service = FakeAlarmClockService()
        val result = makeHandler(service).execute(SystemOperation.SetAlarm(hour = -1, minute = 30))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unsupported::class.java)
        assertThat(service.setAlarmCalls).isEmpty()
    }

    @Test
    fun `execute returns Failure Unsupported for invalid hour (24)`() = runTest {
        val service = FakeAlarmClockService()
        val result = makeHandler(service).execute(SystemOperation.SetAlarm(hour = 24, minute = 0))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat(service.setAlarmCalls).isEmpty()
    }

    @Test
    fun `execute returns Failure Unsupported for invalid minute (60)`() = runTest {
        val service = FakeAlarmClockService()
        val result = makeHandler(service).execute(SystemOperation.SetAlarm(hour = 7, minute = 60))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat(service.setAlarmCalls).isEmpty()
    }

    @Test
    fun `execute maps ActivityNotFoundException to Hardware Unsupported`() = runTest {
        val service = FakeAlarmClockService(
            throwOnSetAlarm = android.content.ActivityNotFoundException("No clock app"),
        )
        val result = makeHandler(service).execute(SystemOperation.SetAlarm(hour = 7, minute = 30))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unsupported::class.java)
    }

    @Test
    fun `execute returns Failure Unsupported when setAlarm returns false`() = runTest {
        val service = FakeAlarmClockService(setAlarmResult = false)
        val result = makeHandler(service).execute(SystemOperation.SetAlarm(hour = 7, minute = 30))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unsupported::class.java)
    }

    @Test
    fun `resourceCategory is APP`() {
        assertThat(makeHandler(FakeAlarmClockService()).resourceCategory).isEqualTo(ResourceCategory.APP)
    }

    @Test
    fun `requiredPermissions contains SET_ALARM`() {
        assertThat(makeHandler(FakeAlarmClockService()).requiredPermissions)
            .contains("com.android.alarm.permission.SET_ALARM")
    }
}
