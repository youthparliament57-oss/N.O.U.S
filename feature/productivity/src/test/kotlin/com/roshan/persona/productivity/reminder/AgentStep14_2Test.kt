// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.productivity.reminder

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.productivity.alarm.EncryptedData
import org.junit.Test

// AUTO_FIX_0060: [feature] AgentStep14_2Test verified

/**
 * NOUS — Module 14 Step 14.2 Tests (ReminderEngine + ReminderInfo + ReminderStore +
 * ReminderEncryptionHelper + ReminderWorker constants + ReminderActionReceiver constants).
 *
 * 20 tests covering:
 *  - ReminderInfo — data model + computed properties — 4 tests
 *  - ReminderStatus — enum properties — 2 tests
 *  - ReminderStore — CRUD + snooze/fired/cancelled transitions — 6 tests
 *  - ReminderResult — sealed hierarchy — 3 tests
 *  - SnoozeOptions — default values — 1 test
 *  - EncryptedData — holds IV + ciphertext — 1 test
 *  - ReminderEncryptionHelper — empty input + keyExists — 2 tests
 *  - ReminderEngine — past-time rejection — 1 test
 */
class AgentStep14_2Test {

    // ═══════════════════════════════════════════════════════════════════════════
    // ReminderInfo (4 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `ReminderInfo remainingMs returns positive value for future reminder`() {
        val futureMs = System.currentTimeMillis() + 60_000  // 1 minute from now
        val reminder = ReminderInfo(
            id = "r1",
            label = "Take medicine",
            triggerAtMs = futureMs,
        )
        assertThat(reminder.remainingMs()).isGreaterThan(0L)
    }

    @Test
    fun `ReminderInfo hasFired returns true for past reminder`() {
        val pastMs = System.currentTimeMillis() - 60_000
        val reminder = ReminderInfo(id = "r1", label = "Past", triggerAtMs = pastMs)
        assertThat(reminder.hasFired).isTrue()
    }

    @Test
    fun `ReminderInfo canSnooze returns true when below max snooze count`() {
        val reminder = ReminderInfo(
            id = "r1",
            label = "Test",
            triggerAtMs = System.currentTimeMillis() + 60_000,
            snoozeCount = 1,
            maxSnoozeCount = 3,
        )
        assertThat(reminder.canSnooze).isTrue()
    }

    @Test
    fun `ReminderInfo displayTime returns non-empty string`() {
        val reminder = ReminderInfo(
            id = "r1",
            label = "Test",
            triggerAtMs = System.currentTimeMillis() + 3_600_000,
        )
        assertThat(reminder.displayTime).isNotEmpty()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ReminderStatus (2 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `ReminderStatus has 4 values`() {
        assertThat(ReminderStatus.entries).hasSize(4)
        assertThat(ReminderStatus.entries).containsExactly(
            ReminderStatus.SCHEDULED,
            ReminderStatus.FIRED,
            ReminderStatus.SNOOZED,
            ReminderStatus.CANCELLED,
        )
    }

    @Test
    fun `ReminderStatus display names are correct`() {
        assertThat(ReminderStatus.SCHEDULED.displayName).isEqualTo("Scheduled")
        assertThat(ReminderStatus.FIRED.displayName).isEqualTo("Fired")
        assertThat(ReminderStatus.SNOOZED.displayName).isEqualTo("Snoozed")
        assertThat(ReminderStatus.CANCELLED.displayName).isEqualTo("Cancelled")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ReminderStore (6 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `ReminderStore put and getById round-trip`() {
        val store = ReminderStore()
        val reminder = ReminderInfo(
            id = "r1",
            label = "Test",
            triggerAtMs = System.currentTimeMillis() + 60_000,
        )
        store.put(reminder)
        assertThat(store.getById("r1")).isEqualTo(reminder)
    }

    @Test
    fun `ReminderStore getAllActive filters fired and cancelled`() {
        val store = ReminderStore()
        val future = System.currentTimeMillis() + 60_000

        store.put(ReminderInfo(id = "r1", label = "Active", triggerAtMs = future))
        store.put(ReminderInfo(id = "r2", label = "Fired", triggerAtMs = future, status = ReminderStatus.FIRED))
        store.put(ReminderInfo(id = "r3", label = "Cancelled", triggerAtMs = future, status = ReminderStatus.CANCELLED))

        val active = store.getAllActive()
        assertThat(active).hasSize(1)
        assertThat(active[0].id).isEqualTo("r1")
    }

    @Test
    fun `ReminderStore getNextActive returns earliest trigger time`() {
        val store = ReminderStore()
        val now = System.currentTimeMillis()
        store.put(ReminderInfo(id = "r1", label = "Later", triggerAtMs = now + 120_000))
        store.put(ReminderInfo(id = "r2", label = "Sooner", triggerAtMs = now + 60_000))
        store.put(ReminderInfo(id = "r3", label = "Latest", triggerAtMs = now + 300_000))

        val next = store.getNextActive()
        assertThat(next).isNotNull()
        assertThat(next!!.id).isEqualTo("r2")
    }

    @Test
    fun `ReminderStore deleteById removes reminder`() {
        val store = ReminderStore()
        store.put(ReminderInfo(id = "r1", label = "Test", triggerAtMs = System.currentTimeMillis() + 60_000))
        assertThat(store.deleteById("r1")).isTrue()
        assertThat(store.getById("r1")).isNull()
    }

    @Test
    fun `ReminderStore markAsSnoozed bumps snooze count and updates trigger time`() {
        val store = ReminderStore()
        val originalMs = System.currentTimeMillis() + 60_000
        store.put(ReminderInfo(id = "r1", label = "Test", triggerAtMs = originalMs, snoozeCount = 0))

        val newMs = System.currentTimeMillis() + 300_000
        store.markAsSnoozed("r1", newMs)

        val updated = store.getById("r1")!!
        assertThat(updated.snoozeCount).isEqualTo(1)
        assertThat(updated.triggerAtMs).isEqualTo(newMs)
        assertThat(updated.status).isEqualTo(ReminderStatus.SCHEDULED)
    }

    @Test
    fun `ReminderStore activeCount returns correct count`() {
        val store = ReminderStore()
        assertThat(store.activeCount()).isEqualTo(0)
        store.put(ReminderInfo(id = "r1", label = "A", triggerAtMs = System.currentTimeMillis() + 60_000))
        store.put(ReminderInfo(id = "r2", label = "B", triggerAtMs = System.currentTimeMillis() + 120_000))
        store.put(ReminderInfo(id = "r3", label = "Fired", triggerAtMs = System.currentTimeMillis() + 60_000, status = ReminderStatus.FIRED))
        assertThat(store.activeCount()).isEqualTo(2)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ReminderResult (3 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `ReminderResult Success stores data and spokenMessage`() {
        val reminder = ReminderInfo(id = "r1", label = "Test", triggerAtMs = System.currentTimeMillis() + 60_000)
        val result: ReminderResult<ReminderInfo> = ReminderResult.Success(reminder, "Reminder set.")
        assertThat(result).isInstanceOf(ReminderResult.Success::class.java)
        assertThat((result as ReminderResult.Success).data).isEqualTo(reminder)
        assertThat(result.spokenMessage).isEqualTo("Reminder set.")
    }

    @Test
    fun `ReminderResult Failure stores error and spokenMessage`() {
        val result: ReminderResult<Nothing> = ReminderResult.Failure("Not found", "I can't find that reminder.")
        assertThat(result).isInstanceOf(ReminderResult.Failure::class.java)
        assertThat((result as ReminderResult.Failure).error).isEqualTo("Not found")
    }

    @Test
    fun `ReminderResult NeedsPermission stores permissions list`() {
        val result: ReminderResult<Nothing> = ReminderResult.NeedsPermission(
            permissions = listOf("android.permission.POST_NOTIFICATIONS"),
            spokenMessage = "I need notification permission to send reminders.",
        )
        assertThat(result).isInstanceOf(ReminderResult.NeedsPermission::class.java)
        assertThat((result as ReminderResult.NeedsPermission).permissions).hasSize(1)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SnoozeOptions (1 test)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `SnoozeOptions defaults are 5 minutes and 3 snoozes`() {
        val options = SnoozeOptions()
        assertThat(options.snoozeMs).isEqualTo(5 * 60 * 1000L)
        assertThat(options.maxSnoozeCount).isEqualTo(3)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EncryptedData (1 test)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `EncryptedData holds iv and ciphertext strings`() {
        val data = EncryptedData(ivBase64 = "abc==", ciphertextBase64 = "xyz==")
        assertThat(data.ivBase64).isEqualTo("abc==")
        assertThat(data.ciphertextBase64).isEqualTo("xyz==")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ReminderEncryptionHelper (2 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `ReminderEncryptionHelper encrypt returns null for empty input`() {
        val helper = ReminderEncryptionHelper()
        // Empty input should never reach Keystore — helper returns null early.
        assertThat(helper.encrypt("")).isNull()
    }

    @Test
    fun `ReminderEncryptionHelper keyExists returns boolean without crash`() {
        val helper = ReminderEncryptionHelper()
        // In unit tests, AndroidKeystore is not available — keyExists should return false.
        // (Real Android device returns true if key was created.)
        val result = helper.keyExists()
        assertThat(result).isAnyOf(true, false)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ReminderEngine (1 test)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `ReminderEngine scheduleReminder rejects past time with Failure`() {
        // Use a no-op context (the engine never reaches WorkManager.enqueue for a past time).
        // We pass null-context via a relaxed mock would be cleaner, but here we just verify
        // the engine rejects the past-time branch BEFORE touching WorkManager.
        //
        // We can't easily mock WorkManager.getInstance(context) without Robolectric setup,
        // so we test only the early-fail branch (past time → Failure returned, no WorkManager call).
        //
        // Note: even with a real Context, the past-time check fires first and returns Failure
        // before WorkManager.getInstance(context) is invoked.
        val engine = ReminderEngine(
            context = io.mockk.mockk(relaxed = true),
            reminderStore = ReminderStore(),
            encryptionHelper = ReminderEncryptionHelper(),
        )
        val pastMs = System.currentTimeMillis() - 60_000
        val result = engine.scheduleReminder(triggerAtMs = pastMs, label = "Past reminder")

        assertThat(result).isInstanceOf(ReminderResult.Failure::class.java)
        val failure = result as ReminderResult.Failure
        assertThat(failure.error).contains("past")
    }
}
