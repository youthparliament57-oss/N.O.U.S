// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.system.handler

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.skill.SystemOperation
import com.roshan.persona.common.AppError
import com.roshan.persona.common.Result
import com.roshan.persona.system.executor.ResourceCategory
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0137: [feature] TimerHandlerTest verified

class TimerHandlerTest {

    private class FakeTimerService(
        private val setTimerResult: Boolean = true,
        private val throwOnSetTimer: Throwable? = null,
    ) : TimerService {
        val setTimerCalls = mutableListOf<Pair<Long, String?>>()
            private set

        override fun setTimer(durationSeconds: Long, label: String?, skipUi: Boolean): Boolean {
            throwOnSetTimer?.let { throw it }
            setTimerCalls.add(durationSeconds to label)
            return setTimerResult
        }
        override fun showTimers(): Boolean = true
    }

    private fun makeHandler(service: FakeTimerService) = TimerHandler(service)

    // ─── Tests ─────────────────────────────────────────────────────────────

    @Test
    fun `isAvailable always returns true`() {
        assertThat(makeHandler(FakeTimerService()).isAvailable()).isTrue()
    }

    @Test
    fun `execute sets timer with valid duration`() = runTest {
        val service = FakeTimerService()
        makeHandler(service).execute(SystemOperation.SetTimer(durationSeconds = 300, label = "Tea"))
        assertThat(service.setTimerCalls).hasSize(1)
        assertThat(service.setTimerCalls[0]).isEqualTo(300L to "Tea")
    }

    @Test
    fun `execute sets timer without label`() = runTest {
        val service = FakeTimerService()
        makeHandler(service).execute(SystemOperation.SetTimer(durationSeconds = 60, label = null))
        assertThat(service.setTimerCalls[0].second).isNull()
    }

    @Test
    fun `execute returns Failure Unsupported for zero duration`() = runTest {
        val service = FakeTimerService()
        val result = makeHandler(service).execute(SystemOperation.SetTimer(durationSeconds = 0))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unsupported::class.java)
        assertThat(service.setTimerCalls).isEmpty()
    }

    @Test
    fun `execute returns Failure Unsupported for negative duration`() = runTest {
        val service = FakeTimerService()
        val result = makeHandler(service).execute(SystemOperation.SetTimer(durationSeconds = -10))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat(service.setTimerCalls).isEmpty()
    }

    @Test
    fun `execute returns Failure Unsupported for duration exceeding 24 hours`() = runTest {
        val service = FakeTimerService()
        val result = makeHandler(service).execute(SystemOperation.SetTimer(durationSeconds = 25 * 60 * 60))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unsupported::class.java)
        assertThat(service.setTimerCalls).isEmpty()
    }

    @Test
    fun `execute accepts exactly 24 hours`() = runTest {
        val service = FakeTimerService()
        makeHandler(service).execute(SystemOperation.SetTimer(durationSeconds = 24 * 60 * 60))
        assertThat(service.setTimerCalls).hasSize(1)
    }

    @Test
    fun `execute maps ActivityNotFoundException to Hardware Unsupported`() = runTest {
        val service = FakeTimerService(
            throwOnSetTimer = android.content.ActivityNotFoundException("No timer app"),
        )
        val result = makeHandler(service).execute(SystemOperation.SetTimer(durationSeconds = 60))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unsupported::class.java)
    }

    @Test
    fun `execute returns Failure Unsupported when setTimer returns false`() = runTest {
        val service = FakeTimerService(setTimerResult = false)
        val result = makeHandler(service).execute(SystemOperation.SetTimer(durationSeconds = 60))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unsupported::class.java)
    }

    @Test
    fun `resourceCategory is APP`() {
        assertThat(makeHandler(FakeTimerService()).resourceCategory).isEqualTo(ResourceCategory.APP)
    }

    @Test
    fun `requiredPermissions contains SET_ALARM`() {
        assertThat(makeHandler(FakeTimerService()).requiredPermissions)
            .contains("com.android.alarm.permission.SET_ALARM")
    }
}
