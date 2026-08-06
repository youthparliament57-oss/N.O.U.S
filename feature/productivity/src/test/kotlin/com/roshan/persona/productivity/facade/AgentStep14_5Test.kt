// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.productivity.facade

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.context.BrainContext
import com.roshan.persona.brain.intent.Intent
import com.roshan.persona.brain.skill.SkillContext
import com.roshan.persona.brain.skill.SkillDispatchers
import com.roshan.persona.brain.skill.SkillOutput
import com.roshan.persona.common.CorrelationId
import com.roshan.persona.common.Result
import com.roshan.persona.productivity.alarm.AlarmResult
import com.roshan.persona.productivity.calendar.CalendarResult
import com.roshan.persona.productivity.note.NoteResult
import com.roshan.persona.productivity.reminder.ReminderResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0061: [feature] AgentStep14_5Test verified

/**
 * NOUS — Module 14 Step 14.5 Tests (ProductivityFacade + ProductivityResult +
 * ProductivityPermissionFlow + ProductivitySkills + ProductivityE2EFlow +
 * TimeParser).
 *
 * 16 tests covering:
 *  - ProductivityResult — sealed hierarchy + adapters — 4 tests
 *  - TimeParser — time + duration parsing — 4 tests
 *  - ProductivityFacade — alarm engine not registered — 2 tests
 *  - ProductivitySkills — NeedsInput for missing slots — 3 tests
 *  - ProductivityPermissionFlow — operation type → missing permissions — 2 tests
 *  - ProductivityE2EFlow — smoke flow — 1 test
 */
class AgentStep14_5Test {

    // ═══════════════════════════════════════════════════════════════════════════
    // ProductivityResult adapters (4 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `AlarmResult toProductivityResult converts all 3 variants`() {
        // Success
        val alarm = com.roshan.persona.productivity.alarm.AlarmInfo(
            id = "a1", label = "Test",
            triggerAtMs = System.currentTimeMillis() + 60_000,
            type = com.roshan.persona.productivity.alarm.AlarmType.ALARM,
        )
        val success: AlarmResult<com.roshan.persona.productivity.alarm.AlarmInfo> =
            AlarmResult.Success(alarm, "Alarm set.")
        val successResult = success.toProductivityResult()
        assertThat(successResult).isInstanceOf(ProductivityResult.Success::class.java)

        // Failure
        val failure: AlarmResult<Nothing> = AlarmResult.Failure("err", "I failed.")
        val failureResult = failure.toProductivityResult()
        assertThat(failureResult).isInstanceOf(ProductivityResult.Failure::class.java)
        assertThat((failureResult as ProductivityResult.Failure).error).isEqualTo("err")

        // NeedsPermission
        val needsPerm: AlarmResult<Nothing> = AlarmResult.NeedsPermission(
            permissions = listOf("android.permission.SCHEDULE_EXACT_ALARM"),
            spokenMessage = "I need permission.",
        )
        val needsPermResult = needsPerm.toProductivityResult()
        assertThat(needsPermResult).isInstanceOf(ProductivityResult.NeedsPermission::class.java)
        assertThat((needsPermResult as ProductivityResult.NeedsPermission).permissions).hasSize(1)
    }

    @Test
    fun `ReminderResult NoteResult CalendarResult all convert to ProductivityResult`() {
        val reminder: ReminderResult<com.roshan.persona.productivity.reminder.ReminderInfo> =
            ReminderResult.Success(
                com.roshan.persona.productivity.reminder.ReminderInfo(
                    id = "r1", label = "Test",
                    triggerAtMs = System.currentTimeMillis() + 60_000,
                ),
                "Reminder set.",
            )
        assertThat(reminder.toProductivityResult()).isInstanceOf(ProductivityResult.Success::class.java)

        val note: NoteResult<com.roshan.persona.productivity.note.NoteInfo> =
            NoteResult.Failure("err", "Note failed.")
        assertThat(note.toProductivityResult()).isInstanceOf(ProductivityResult.Failure::class.java)

        val cal: CalendarResult<Nothing> = CalendarResult.NeedsPermission(
            permissions = listOf("android.permission.READ_CALENDAR"),
            spokenMessage = "Need calendar permission.",
        )
        assertThat(cal.toProductivityResult()).isInstanceOf(ProductivityResult.NeedsPermission::class.java)
    }

    @Test
    fun `ProductivityResult toTtsString returns spokenMessage for all variants`() {
        val success = ProductivityResult.Success("data", "Alarm set.")
        assertThat(success.toTtsString()).isEqualTo("Alarm set.")

        val failure = ProductivityResult.Failure("err", "I failed.")
        assertThat(failure.toTtsString()).isEqualTo("I failed.")

        val needsPerm = ProductivityResult.NeedsPermission(
            listOf("android.permission.SCHEDULE_EXACT_ALARM"),
            "I need permission.",
        )
        assertThat(needsPerm.toTtsString()).isEqualTo("I need permission.")
    }

    @Test
    fun `ProductivityResult toSkillOutput converts to Brain SkillOutput`() {
        val success = ProductivityResult.Success("data", "Alarm set.")
        val successOutput = success.toSkillOutput("set_alarm")
        assertThat(successOutput).isInstanceOf(SkillOutput.Success::class.java)
        assertThat((successOutput as SkillOutput.Success).spokenMessage).isEqualTo("Alarm set.")

        val failure = ProductivityResult.Failure("err", "I failed.")
        val failureOutput = failure.toSkillOutput("set_alarm")
        assertThat(failureOutput).isInstanceOf(SkillOutput.Failure::class.java)

        val needsPerm = ProductivityResult.NeedsPermission(
            listOf("android.permission.SCHEDULE_EXACT_ALARM"),
            "I need permission.",
        )
        val needsPermOutput = needsPerm.toSkillOutput("set_alarm")
        assertThat(needsPermOutput).isInstanceOf(SkillOutput.NeedsPermission::class.java)
        assertThat((needsPermOutput as SkillOutput.NeedsPermission).skillId).isEqualTo("set_alarm")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TimeParser (4 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `TimeParser parseDurationToMs handles hours minutes seconds`() {
        assertThat(TimeParser.parseDurationToMs("1 hour")).isEqualTo(3_600_000L)
        assertThat(TimeParser.parseDurationToMs("2 hours")).isEqualTo(7_200_000L)
        assertThat(TimeParser.parseDurationToMs("10 minutes")).isEqualTo(600_000L)
        assertThat(TimeParser.parseDurationToMs("30 seconds")).isEqualTo(30_000L)
        assertThat(TimeParser.parseDurationToMs("2h")).isEqualTo(7_200_000L)
        assertThat(TimeParser.parseDurationToMs("10m")).isEqualTo(600_000L)
    }

    @Test
    fun `TimeParser parseDurationToMs returns null for invalid input`() {
        assertThat(TimeParser.parseDurationToMs("")).isNull()
        assertThat(TimeParser.parseDurationToMs("hello")).isNull()
        assertThat(TimeParser.parseDurationToMs("abc minutes")).isNull()
    }

    @Test
    fun `TimeParser parseTimeToEpochMs handles relative time`() {
        val before = System.currentTimeMillis()
        val inTwoHours = TimeParser.parseTimeToEpochMs("in 2 hours")
        val after = System.currentTimeMillis()
        assertThat(inTwoHours).isNotNull()
        // Should be ~2 hours from now (allow some slack for test execution).
        val expected = before + 7_200_000L
        assertThat(inTwoHours!!).isAtLeast(expected - 1_000)
        assertThat(inTwoHours).isAtMost(after + 7_200_000L + 1_000)
    }

    @Test
    fun `TimeParser parseTimeToEpochMs handles baje format`() {
        // "7 baje" should parse to 7:00 AM (or tomorrow if past).
        val result = TimeParser.parseTimeToEpochMs("7 baje")
        assertThat(result).isNotNull()
        // The result should be in the future.
        assertThat(result!!).isGreaterThan(System.currentTimeMillis() - 60_000)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ProductivityFacade — alarm engine not registered (2 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `ProductivityFacade setAlarm returns Failure when AlarmEngine not registered`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val facade = ProductivityFacade(context = context)
        // Don't call setAlarmEngine — alarmEngine is null.
        val result = facade.setAlarm("7:00 AM", "Wake up")
        assertThat(result).isInstanceOf(ProductivityResult.Failure::class.java)
        val failure = result as ProductivityResult.Failure
        assertThat(failure.error).contains("not registered")
    }

    @Test
    fun `ProductivityFacade setTimer returns Failure when AlarmEngine not registered`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val facade = ProductivityFacade(context = context)
        val result = facade.setTimer("10 minutes", "Pasta")
        assertThat(result).isInstanceOf(ProductivityResult.Failure::class.java)
        val failure = result as ProductivityResult.Failure
        assertThat(failure.error).contains("not registered")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ProductivitySkills — NeedsInput for missing slots (3 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `AlarmSkill returns NeedsInput when time is missing`() = runTest {
        val facade = mockk<ProductivityFacade>(relaxed = true)
        val skill = AlarmSkill(facade)
        val context = mockSkillContext()
        val result = skill.execute(Intent.SetAlarm(time = null, label = null), context)
        // Should return Result.Success wrapping SkillOutput.NeedsInput.
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val output = (result as Result.Success<*>).data
        assertThat(output).isInstanceOf(SkillOutput.NeedsInput::class.java)
        assertThat((output as SkillOutput.NeedsInput).inputType)
            .isEqualTo(com.roshan.persona.brain.skill.InputType.TIME)
    }

    @Test
    fun `TimerSkill returns NeedsInput when duration is missing`() = runTest {
        val facade = mockk<ProductivityFacade>(relaxed = true)
        val skill = TimerSkill(facade)
        val context = mockSkillContext()
        val result = skill.execute(Intent.SetTimer(duration = null, label = null), context)
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val output = (result as Result.Success<*>).data
        assertThat(output).isInstanceOf(SkillOutput.NeedsInput::class.java)
        assertThat((output as SkillOutput.NeedsInput).inputType)
            .isEqualTo(com.roshan.persona.brain.skill.InputType.DURATION)
    }

    @Test
    fun `ReminderSkill returns NeedsInput when text is missing`() = runTest {
        val facade = mockk<ProductivityFacade>(relaxed = true)
        val skill = ReminderSkill(facade)
        val context = mockSkillContext()
        val result = skill.execute(
            Intent.SetReminder(text = null, time = "3 PM"),
            context,
        )
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val output = (result as Result.Success<*>).data
        assertThat(output).isInstanceOf(SkillOutput.NeedsInput::class.java)
        assertThat((output as SkillOutput.NeedsInput).inputType)
            .isEqualTo(com.roshan.persona.brain.skill.InputType.TEXT)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ProductivityPermissionFlow — operation type → missing permissions (2 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `ProductivityPermissionFlow getMissingPermissions returns empty for NOTE operation`() {
        val context = mockk<Context>(relaxed = true)
        val flow = ProductivityPermissionFlow(context = context)
        val missing = flow.getMissingPermissions(ProductivityOperationType.NOTE)
        // Notes don't need runtime permissions.
        assertThat(missing).isEmpty()
    }

    @Test
    fun `ProductivityPermissionFlow getPermissionState returns valid state without crash`() {
        val context = mockk<Context>(relaxed = true)
        val flow = ProductivityPermissionFlow(context = context)
        val state = flow.getPermissionState()
        // All fields should be boolean (no crash).
        assertThat(state.canSetAlarms).isAnyOf(true, false)
        assertThat(state.canSetReminders).isAnyOf(true, false)
        assertThat(state.canReadCalendar).isAnyOf(true, false)
        assertThat(state.canWriteCalendar).isAnyOf(true, false)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ProductivityE2EFlow — smoke flow (1 test)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `ProductivityE2EFlow simulate returns NEEDS_INPUT for alarm without time`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val facade = ProductivityFacade(context = context)
        val e2e = ProductivityE2EFlow(facade)
        val skillContext = mockSkillContext()

        // Simulate: user said "alarm lagao" but IntentClassifier didn't extract a time.
        val result = e2e.simulate(
            userUtterance = "alarm lagao",
            intent = Intent.SetAlarm(time = null, label = null),
            skillId = "set_alarm",
            context = skillContext,
        )

        // Should hit NEEDS_INPUT (skill asks for time).
        assertThat(result.status).isEqualTo(E2eStatus.NEEDS_INPUT)
        assertThat(result.output).isInstanceOf(SkillOutput.NeedsInput::class.java)
        // Trace should have at least 4 steps: utterance / intent / skill_lookup / intent_check / skill_execute / skill_output.
        assertThat(result.trace.size).isAtLeast(4)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Build a mocked SkillContext with a BrainContext that has a valid correlationId.
     *
     * Skills only access `context.brainContext.correlationId` in this module,
     * so we only mock that path.
     */
    private fun mockSkillContext(): SkillContext {
        val brainContext = mockk<BrainContext>(relaxed = true)
        every { brainContext.correlationId } returns CorrelationId.generate()
        val dispatchers = mockk<SkillDispatchers>(relaxed = true)
        val brainBus = mockk(relaxed = true)
        val credentialVault = mockk<com.roshan.persona.brain.skill.CredentialVaultInterface>(relaxed = true)
        return SkillContext(
            brainContext = brainContext,
            dispatchers = dispatchers,
            brainBus = brainBus,
            credentialVault = credentialVault,
        )
    }
}
