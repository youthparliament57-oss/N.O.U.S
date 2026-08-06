// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.voice.proactive

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

// AUTO_FIX_0161: [feature] ProactiveTriggerTest verified

// VOICE_FIX_036: Proactive trigger safe

class ProactiveTriggerTest {

    @Test
    fun `ProactiveTrigger validates priority bounds`() {
        try {
            ProactiveTrigger(
                type = TriggerType.MORNING_BRIEFING,
                message = "Good morning",
                priority = 0,
            )
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("priority")
        }
        try {
            ProactiveTrigger(
                type = TriggerType.MORNING_BRIEFING,
                message = "Good morning",
                priority = 11,
            )
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("priority")
        }
    }

    @Test
    fun `ProactiveTrigger rejects blank message`() {
        try {
            ProactiveTrigger(
                type = TriggerType.MORNING_BRIEFING,
                message = "",
                priority = 5,
            )
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("message")
        }
    }

    @Test
    fun `interruptsSilentHours is true for priority 8 and above`() {
        val low = ProactiveTrigger(TriggerType.MORNING_BRIEFING, "msg", priority = 7)
        val high = ProactiveTrigger(TriggerType.BATTERY_LOW, "msg", priority = 8)
        val urgent = ProactiveTrigger(TriggerType.SECURITY_ALERT, "msg", priority = 10)
        assertThat(low.interruptsSilentHours).isFalse()
        assertThat(high.interruptsSilentHours).isTrue()
        assertThat(urgent.interruptsSilentHours).isTrue()
    }

    @Test
    fun `allowsProactiveBargeIn matches interruptsSilentHours threshold`() {
        val normal = ProactiveTrigger(TriggerType.MISSED_CALLS, "msg", priority = 7)
        val urgent = ProactiveTrigger(TriggerType.CALENDAR_REMINDER, "msg", priority = 8)
        assertThat(normal.allowsProactiveBargeIn).isFalse()
        assertThat(urgent.allowsProactiveBargeIn).isTrue()
    }

    @Test
    fun `SilentHours contains handles wrap-around midnight`() {
        val silent = SilentHours(
            start = LocalTime.of(22, 0),
            end = LocalTime.of(7, 0),
        )
        // 23:00 → in silent hours (after 22:00).
        assertThat(silent.contains(LocalTime.of(23, 0), DayOfWeek.MONDAY)).isTrue()
        // 02:00 → in silent hours (before 07:00).
        assertThat(silent.contains(LocalTime.of(2, 0), DayOfWeek.MONDAY)).isTrue()
        // 12:00 → not in silent hours.
        assertThat(silent.contains(LocalTime.of(12, 0), DayOfWeek.MONDAY)).isFalse()
        // 21:59 → not yet silent.
        assertThat(silent.contains(LocalTime.of(21, 59), DayOfWeek.MONDAY)).isFalse()
        // 07:01 → no longer silent.
        assertThat(silent.contains(LocalTime.of(7, 1), DayOfWeek.MONDAY)).isFalse()
    }

    @Test
    fun `SilentHours respects enabledDays`() {
        val weekdayOnly = SilentHours(
            start = LocalTime.of(22, 0),
            end = LocalTime.of(7, 0),
            enabledDays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
        )
        // Late night Monday → silent.
        assertThat(weekdayOnly.contains(LocalTime.of(23, 0), DayOfWeek.MONDAY)).isTrue()
        // Late night Saturday → not silent (weekend excluded).
        assertThat(weekdayOnly.contains(LocalTime.of(23, 0), DayOfWeek.SATURDAY)).isFalse()
    }

    @Test
    fun `SilentHours same-day window works for afternoon quiet`() {
        val afternoon = SilentHours(
            start = LocalTime.of(13, 0),
            end = LocalTime.of(14, 0),
        )
        assertThat(afternoon.contains(LocalTime.of(13, 30), DayOfWeek.MONDAY)).isTrue()
        assertThat(afternoon.contains(LocalTime.of(12, 30), DayOfWeek.MONDAY)).isFalse()
        assertThat(afternoon.contains(LocalTime.of(14, 30), DayOfWeek.MONDAY)).isFalse()
    }

    @Test
    fun `SilentHours rejects start == end`() {
        try {
            SilentHours(start = LocalTime.of(10, 0), end = LocalTime.of(10, 0))
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("start and end")
        }
    }

    @Test
    fun `all 9 TriggerType values are distinct`() {
        val expected = setOf(
            "MORNING_BRIEFING", "CALENDAR_REMINDER", "BATTERY_LOW", "WEATHER_ALERT",
            "MISSED_CALLS", "HEALTH_REMINDER", "SECURITY_ALERT", "ROUTINE",
            "HABIT_SUGGESTION",
        )
        assertThat(TriggerType.entries.map { it.name }.toSet()).isEqualTo(expected)
    }
}
