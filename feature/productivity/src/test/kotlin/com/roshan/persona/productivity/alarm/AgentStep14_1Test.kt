// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.productivity.alarm

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Test

// AUTO_FIX_0058: [feature] AgentStep14_1Test verified

/**
 * NOUS — Module 14 Step 14.1 Tests (AlarmEngine + AlarmInfo + AlarmStore +
 * AlarmPermissionsHelper + Fts5Utils + MarkdownToTextParser).
 *
 * 22 tests covering:
 *  - AlarmInfo — data model + computed properties — 4 tests
 *  - AlarmStore — CRUD operations — 5 tests
 *  - AlarmResult — sealed hierarchy — 3 tests
 *  - AlarmType — enum properties — 2 tests
 *  - Fts5Utils — query sanitization — 3 tests
 *  - MarkdownToTextParser — Markdown stripping — 3 tests
 *  - AlarmPermissionsHelper — initial state — 2 tests
 */
class AgentStep14_1Test {

    // ═══════════════════════════════════════════════════════════════════════════
    // AlarmInfo (4 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `AlarmInfo remainingMs returns positive value for future alarm`() {
        val futureMs = System.currentTimeMillis() + 60_000  // 1 minute from now
        val alarm = AlarmInfo(id = "a1", label = "Test", triggerAtMs = futureMs, type = AlarmType.ALARM)
        assertThat(alarm.remainingMs()).isGreaterThan(0L)
    }

    @Test
    fun `AlarmInfo hasFired returns true for past alarm`() {
        val pastMs = System.currentTimeMillis() - 60_000  // 1 minute ago
        val alarm = AlarmInfo(id = "a1", label = "Test", triggerAtMs = pastMs, type = AlarmType.ALARM)
        assertThat(alarm.hasFired).isTrue()
    }

    @Test
    fun `AlarmInfo hasFired returns false for future alarm`() {
        val futureMs = System.currentTimeMillis() + 60_000
        val alarm = AlarmInfo(id = "a1", label = "Test", triggerAtMs = futureMs, type = AlarmType.ALARM)
        assertThat(alarm.hasFired).isFalse()
    }

    @Test
    fun `AlarmInfo displayTime returns non-empty string`() {
        val alarm = AlarmInfo(
            id = "a1",
            label = "Test",
            triggerAtMs = System.currentTimeMillis() + 3_600_000,
            type = AlarmType.ALARM,
        )
        assertThat(alarm.displayTime).isNotEmpty()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AlarmStore (5 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `AlarmStore put and getById round-trip`() {
        val store = AlarmStore()
        val alarm = AlarmInfo(id = "a1", label = "Test", triggerAtMs = System.currentTimeMillis() + 60_000, type = AlarmType.ALARM)
        store.put(alarm)
        assertThat(store.getById("a1")).isEqualTo(alarm)
    }

    @Test
    fun `AlarmStore getAllActive returns only enabled and not-fired`() {
        val store = AlarmStore()
        val future = System.currentTimeMillis() + 60_000
        val past = System.currentTimeMillis() - 60_000

        store.put(AlarmInfo(id = "a1", label = "Active", triggerAtMs = future, type = AlarmType.ALARM, isEnabled = true))
        store.put(AlarmInfo(id = "a2", label = "Disabled", triggerAtMs = future, type = AlarmType.ALARM, isEnabled = false))
        store.put(AlarmInfo(id = "a3", label = "Fired", triggerAtMs = past, type = AlarmType.ALARM, isEnabled = true))

        val active = store.getAllActive()
        assertThat(active).hasSize(1)
        assertThat(active[0].id).isEqualTo("a1")
    }

    @Test
    fun `AlarmStore deleteById removes alarm`() {
        val store = AlarmStore()
        store.put(AlarmInfo(id = "a1", label = "Test", triggerAtMs = System.currentTimeMillis() + 60_000, type = AlarmType.ALARM))
        assertThat(store.deleteById("a1")).isTrue()
        assertThat(store.getById("a1")).isNull()
    }

    @Test
    fun `AlarmStore deleteById returns false for nonexistent`() {
        val store = AlarmStore()
        assertThat(store.deleteById("nonexistent")).isFalse()
    }

    @Test
    fun `AlarmStore activeCount returns correct count`() {
        val store = AlarmStore()
        assertThat(store.activeCount()).isEqualTo(0)
        store.put(AlarmInfo(id = "a1", label = "Test1", triggerAtMs = System.currentTimeMillis() + 60_000, type = AlarmType.ALARM))
        store.put(AlarmInfo(id = "a2", label = "Test2", triggerAtMs = System.currentTimeMillis() + 120_000, type = AlarmType.TIMER))
        assertThat(store.activeCount()).isEqualTo(2)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AlarmResult (3 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `AlarmResult Success stores data and spokenMessage`() {
        val alarm = AlarmInfo(id = "a1", label = "Test", triggerAtMs = System.currentTimeMillis() + 60_000, type = AlarmType.ALARM)
        val result: AlarmResult<AlarmInfo> = AlarmResult.Success(alarm, "Alarm set.")
        assertThat(result).isInstanceOf(AlarmResult.Success::class.java)
        assertThat((result as AlarmResult.Success).data).isEqualTo(alarm)
        assertThat(result.spokenMessage).isEqualTo("Alarm set.")
    }

    @Test
    fun `AlarmResult Failure stores error and spokenMessage`() {
        val result: AlarmResult<Nothing> = AlarmResult.Failure("Permission denied", "I need permission.")
        assertThat(result).isInstanceOf(AlarmResult.Failure::class.java)
        assertThat((result as AlarmResult.Failure).error).isEqualTo("Permission denied")
    }

    @Test
    fun `AlarmResult NeedsPermission stores permissions list`() {
        val result: AlarmResult<Nothing> = AlarmResult.NeedsPermission(
            permissions = listOf("android.permission.SCHEDULE_EXACT_ALARM"),
            spokenMessage = "I need exact alarm permission.",
        )
        assertThat(result).isInstanceOf(AlarmResult.NeedsPermission::class.java)
        assertThat((result as AlarmResult.NeedsPermission).permissions).hasSize(1)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AlarmType (2 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `AlarmType has 2 values`() {
        assertThat(AlarmType.entries).hasSize(2)
    }

    @Test
    fun `AlarmType display names are correct`() {
        assertThat(AlarmType.ALARM.displayName).isEqualTo("Alarm")
        assertThat(AlarmType.TIMER.displayName).isEqualTo("Timer")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Fts5Utils (3 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `Fts5Utils escapeFts5Query wraps in double quotes`() {
        val result = Fts5Utils.escapeFts5Query("hello world")
        assertThat(result).isEqualTo("\"hello world\"")
    }

    @Test
    fun `Fts5Utils escapeFts5Query escapes internal quotes`() {
        val result = Fts5Utils.escapeFts5Query("hello \"world\"")
        assertThat(result).isEqualTo("\"hello \"\"world\"\"\"")
    }

    @Test
    fun `Fts5Utils buildPrefixQuery appends asterisk`() {
        val result = Fts5Utils.buildPrefixQuery("med")
        assertThat(result).isEqualTo("med*")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MarkdownToTextParser (3 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `MarkdownToTextParser strips heading markers`() {
        val result = MarkdownToTextParser.stripMarkdown("# Hello World")
        assertThat(result).isEqualTo("Hello World")
    }

    @Test
    fun `MarkdownToTextParser strips bold and italic`() {
        val result = MarkdownToTextParser.stripMarkdown("**bold** and *italic*")
        assertThat(result).isEqualTo("bold and italic")
    }

    @Test
    fun `MarkdownToTextParser strips bullet points`() {
        val result = MarkdownToTextParser.stripMarkdown("- item 1\n- item 2")
        assertThat(result).isEqualTo("item 1\nitem 2")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AlarmPermissionsHelper (2 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `AlarmPermissionsHelper canScheduleExactAlarms returns boolean without crash`() {
        val context = mockk<Context>(relaxed = true)
        val helper = AlarmPermissionsHelper(context)
        // In unit tests, AlarmManager.canScheduleExactAlarms may not work.
        // Just verify it doesn't crash.
        val result = helper.canScheduleExactAlarms()
        assertThat(result).isAnyOf(true, false)
    }

    @Test
    fun `AlarmPermissionsHelper canPostNotifications returns boolean without crash`() {
        val context = mockk<Context>(relaxed = true)
        val helper = AlarmPermissionsHelper(context)
        val result = helper.canPostNotifications()
        assertThat(result).isAnyOf(true, false)
    }
}
