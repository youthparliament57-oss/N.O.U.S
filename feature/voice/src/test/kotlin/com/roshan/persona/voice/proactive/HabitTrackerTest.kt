// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.voice.proactive

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

// AUTO_FIX_0162: [feature] HabitTrackerTest verified

// VOICE_FIX_035: Habit tracker validated

class HabitTrackerTest {

    // ─── Fakes ─────────────────────────────────────────────────────────────

    private class FakeMemory(private val memories: List<EpisodicMemory>) : MemoryInterface {
        var recallCount = 0
            private set
        override suspend fun recall(query: String, k: Int): List<EpisodicMemory> {
            recallCount++
            // Return memories whose action matches the query, or all if "daily routine".
            return if (query == "daily routine") memories.take(k) else memories.filter { it.actionDescription == query }.take(k)
        }
    }

    /** Build N days of memories for an action at a given time. */
    private fun repeatedAction(
        action: String,
        time: LocalTime,
        days: Int,
        startDate: LocalDate = LocalDate.of(2026, 1, 1),
    ): List<EpisodicMemory> = (0 until days).map { i ->
        EpisodicMemory(action, time, startDate.plusDays(i.toLong()))
    }

    // ─── Tests ─────────────────────────────────────────────────────────────

    @Test
    fun `detectHabits returns empty when no memories`() runTest@{
        val tracker = HabitTracker(FakeMemory(emptyList()))
        val habits = tracker.detectHabits()
        assertThat(habits).isEmpty()
    }

    @Test
    fun `detectHabits finds pattern with 5+ repetition days`() = runTest {
        val memories = repeatedAction(
            action = "open_camera",
            time = LocalTime.of(8, 0),
            days = 7,
        )
        val tracker = HabitTracker(FakeMemory(memories))
        val habits = tracker.detectHabits()
        assertThat(habits).hasSize(1)
        assertThat(habits[0].description).isEqualTo("open_camera")
        assertThat(habits[0].triggerTime.hour).isEqualTo(8)
        assertThat(habits[0].confidence).isGreaterThan(0.5f)
    }

    @Test
    fun `detectHabits filters out patterns below minRepetitionDays`() = runTest {
        val memories = repeatedAction(
            action = "open_camera",
            time = LocalTime.of(8, 0),
            days = 3,  // below default minRepetitionDays=5
        )
        val tracker = HabitTracker(FakeMemory(memories))
        val habits = tracker.detectHabits()
        assertThat(habits).isEmpty()
    }

    @Test
    fun `detectHabits filters by confidenceThreshold`() = runTest {
        // Action happens only 3 of 10 days → confidence ~0.3, below 0.7 threshold.
        val startDate = LocalDate.of(2026, 1, 1)
        val memories = (0 until 10).map { i ->
            val time = if (i % 3 == 0) LocalTime.of(8, 0) else LocalTime.of(12, 0)
            EpisodicMemory("open_camera", time, startDate.plusDays(i.toLong()))
        }
        val tracker = HabitTracker(FakeMemory(memories))
        val habits = tracker.detectHabits()
        // Confidence too low for either time bucket.
        assertThat(habits).isEmpty()
    }

    @Test
    fun `detectHabits handles multiple distinct actions`() = runTest {
        val startDate = LocalDate.of(2026, 1, 1)
        val memories = repeatedAction("open_camera", LocalTime.of(8, 0), 6, startDate) +
            repeatedAction("call_mom", LocalTime.of(19, 0), 6, startDate)
        val tracker = HabitTracker(FakeMemory(memories))
        val habits = tracker.detectHabits()
        assertThat(habits).hasSize(2)
        assertThat(habits.map { it.description }).containsExactly("open_camera", "call_mom")
    }

    @Test
    fun `suggestHabit returns false when already suggested today`() = runTest {
        val today = LocalDate.of(2026, 6, 1)
        val habit = Habit(
            description = "open_camera",
            triggerTime = LocalTime.of(8, 0),
            triggerDays = listOf(DayOfWeek.MONDAY),
            confidence = 0.9f,
            lastSuggested = today,
        )
        val tracker = HabitTracker(FakeMemory(emptyList()))
        val result = tracker.suggestHabit(habit, today)
        assertThat(result).isFalse()
    }

    @Test
    fun `suggestHabit returns false when user already did action today`() = runTest {
        val today = LocalDate.of(2026, 6, 1)
        val memory = FakeMemory(listOf(EpisodicMemory("open_camera", LocalTime.of(8, 0), today)))
        val habit = Habit(
            description = "open_camera",
            triggerTime = LocalTime.of(8, 0),
            triggerDays = listOf(DayOfWeek.MONDAY),
            confidence = 0.9f,
            lastSuggested = null,
        )
        val tracker = HabitTracker(memory)
        val result = tracker.suggestHabit(habit, today)
        assertThat(result).isFalse()
    }

    @Test
    fun `suggestHabit returns true when not yet suggested and user hasn't done action`() = runTest {
        val today = LocalDate.of(2026, 6, 1)
        // Memory has an old action (yesterday), not today.
        val memory = FakeMemory(listOf(EpisodicMemory("open_camera", LocalTime.of(8, 0), today.minusDays(1))))
        val habit = Habit(
            description = "open_camera",
            triggerTime = LocalTime.of(8, 0),
            triggerDays = listOf(DayOfWeek.MONDAY),
            confidence = 0.9f,
            lastSuggested = null,
        )
        val tracker = HabitTracker(memory)
        val result = tracker.suggestHabit(habit, today)
        assertThat(result).isTrue()
    }

    @Test
    fun `Habit validates bounds`() {
        try {
            Habit(description = "", triggerTime = LocalTime.NOON, triggerDays = emptyList(), confidence = 0.5f)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("description")
        }
        try {
            Habit(description = "x", triggerTime = LocalTime.NOON, triggerDays = emptyList(), confidence = 1.5f)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("confidence")
        }
    }

    @Test
    fun `Config validates bounds`() {
        try {
            HabitTracker.Config(minRepetitionDays = 0)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("minRepetitionDays")
        }
        try {
            HabitTracker.Config(confidenceThreshold = 1.5f)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("confidenceThreshold")
        }
    }

    @Test
    fun `findRecurringPatterns buckets by time window`() {
        // Three memories at 7:55, 8:05, 8:12 — should all bucket into the
        // 8:00 window (with default 30-min window: 7:30-8:00, 8:00-8:30).
        val date = LocalDate.of(2026, 1, 1)
        val memories = listOf(
            EpisodicMemory("open_camera", LocalTime.of(7, 55), date),
            EpisodicMemory("open_camera", LocalTime.of(8, 5), date.plusDays(1)),
            EpisodicMemory("open_camera", LocalTime.of(8, 12), date.plusDays(2)),
            EpisodicMemory("open_camera", LocalTime.of(8, 0), date.plusDays(3)),
            EpisodicMemory("open_camera", LocalTime.of(8, 15), date.plusDays(4)),
        )
        val tracker = HabitTracker(FakeMemory(memories))
        val patterns = tracker.findRecurringPatterns(memories)
        assertThat(patterns).hasSize(1)
        assertThat(patterns[0].description).isEqualTo("open_camera")
        // Average time should be near 8 AM.
        assertThat(patterns[0].averageTime.hour).isEqualTo(8)
    }
}
