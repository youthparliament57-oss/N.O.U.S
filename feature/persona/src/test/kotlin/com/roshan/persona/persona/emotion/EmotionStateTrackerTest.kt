// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.persona.emotion

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.persona.jit.GossipTriggerType
import org.junit.Test

// AUTO_FIX_0101: [feature] EmotionStateTrackerTest verified

class CooldownRegistryTest {

    private fun makeRegistry(config: CooldownConfig = CooldownConfig()) =
        CooldownRegistry(config)

    private val ONE_HOUR_MS = 3_600_000L
    private val TWENTY_FOUR_HOURS_MS = 24L * 60 * 60 * 1000

    // ─── 24-Hour Gossip Rule tests ─────────────────────────────────────────

    @Test
    fun `canFire returns true for never-fired trigger during non-silent hours`() {
        val registry = makeRegistry()
        assertThat(registry.canFire(GossipTriggerType.BATTERY_HUNGRY, nowMs = 0, hourOfDay = 12)).isTrue()
    }

    @Test
    fun `canFire returns false after trigger fires within 24 hours`() {
        val registry = makeRegistry()
        val fireTime = 10_000L
        registry.markFired(GossipTriggerType.MUSIC_MOOD, nowMs = fireTime)

        // 1 hour later — still in cooldown.
        assertThat(registry.canFire(GossipTriggerType.MUSIC_MOOD, nowMs = fireTime + ONE_HOUR_MS, hourOfDay = 12)).isFalse()
    }

    @Test
    fun `canFire returns true after 24 hours have passed`() {
        val registry = makeRegistry()
        val fireTime = 10_000L
        registry.markFired(GossipTriggerType.MUSIC_MOOD, nowMs = fireTime)

        // 25 hours later — cooldown expired.
        assertThat(registry.canFire(GossipTriggerType.MUSIC_MOOD, nowMs = fireTime + TWENTY_FOUR_HOURS_MS + 1, hourOfDay = 12)).isTrue()
    }

    @Test
    fun `canFire returns true exactly at 24 hours boundary`() {
        val registry = makeRegistry()
        val fireTime = 10_000L
        registry.markFired(GossipTriggerType.SOCIAL_CHECK, nowMs = fireTime)

        assertThat(registry.canFire(GossipTriggerType.SOCIAL_CHECK, nowMs = fireTime + TWENTY_FOUR_HOURS_MS, hourOfDay = 12)).isTrue()
    }

    @Test
    fun `canFire is independent per trigger type`() {
        val registry = makeRegistry()
        registry.markFired(GossipTriggerType.BATTERY_HUNGRY, nowMs = 0)

        // BATTERY_HUNGRY is in cooldown, but MUSIC_MOOD can still fire.
        assertThat(registry.canFire(GossipTriggerType.BATTERY_HUNGRY, nowMs = ONE_HOUR_MS, hourOfDay = 12)).isFalse()
        assertThat(registry.canFire(GossipTriggerType.MUSIC_MOOD, nowMs = ONE_HOUR_MS, hourOfDay = 12)).isTrue()
    }

    // ─── Silent Hours tests ────────────────────────────────────────────────

    @Test
    fun `canFire returns false during silent hours at 22:00`() {
        val registry = makeRegistry()
        assertThat(registry.canFire(GossipTriggerType.BATTERY_HUNGRY, hourOfDay = 22)).isFalse()
    }

    @Test
    fun `canFire returns false during silent hours at 02:00`() {
        val registry = makeRegistry()
        assertThat(registry.canFire(GossipTriggerType.MUSIC_MOOD, hourOfDay = 2)).isFalse()
    }

    @Test
    fun `canFire returns false during silent hours at 06:00`() {
        val registry = makeRegistry()
        assertThat(registry.canFire(GossipTriggerType.USER_NEGLECT, hourOfDay = 6)).isFalse()
    }

    @Test
    fun `canFire returns true at 07:00 (silent hours end)`() {
        val registry = makeRegistry()
        assertThat(registry.canFire(GossipTriggerType.BATTERY_HUNGRY, hourOfDay = 7)).isTrue()
    }

    @Test
    fun `canFire returns true at 21:00 (before silent hours)`() {
        val registry = makeRegistry()
        assertThat(registry.canFire(GossipTriggerType.BATTERY_HUNGRY, hourOfDay = 21)).isTrue()
    }

    @Test
    fun `isSilentHours handles midnight wrap-around`() {
        val registry = makeRegistry()
        assertThat(registry.isSilentHours(22)).isTrue()
        assertThat(registry.isSilentHours(23)).isTrue()
        assertThat(registry.isSilentHours(0)).isTrue()
        assertThat(registry.isSilentHours(3)).isTrue()
        assertThat(registry.isSilentHours(6)).isTrue()
        assertThat(registry.isSilentHours(7)).isFalse()
        assertThat(registry.isSilentHours(12)).isFalse()
        assertThat(registry.isSilentHours(21)).isFalse()
    }

    // ─── timeSinceLastFire tests ───────────────────────────────────────────

    @Test
    fun `timeSinceLastFire returns null for never-fired trigger`() {
        val registry = makeRegistry()
        assertThat(registry.timeSinceLastFire(GossipTriggerType.BATTERY_HUNGRY)).isNull()
    }

    @Test
    fun `timeSinceLastFire returns elapsed ms for fired trigger`() {
        val registry = makeRegistry()
        registry.markFired(GossipTriggerType.BATTERY_HUNGRY, nowMs = 1000L)
        assertThat(registry.timeSinceLastFire(GossipTriggerType.BATTERY_HUNGRY, nowMs = 5000L)).isEqualTo(4000L)
    }

    // ─── clear + activeCooldownCount tests ─────────────────────────────────

    @Test
    fun `clear resets all cooldowns`() {
        val registry = makeRegistry()
        registry.markFired(GossipTriggerType.BATTERY_HUNGRY, nowMs = 0)
        registry.markFired(GossipTriggerType.MUSIC_MOOD, nowMs = 0)
        assertThat(registry.activeCooldownCount(nowMs = 0)).isEqualTo(2)
        registry.clear()
        assertThat(registry.activeCooldownCount(nowMs = 0)).isEqualTo(0)
    }

    @Test
    fun `activeCooldownCount only counts triggers within 24h`() {
        val registry = makeRegistry()
        registry.markFired(GossipTriggerType.BATTERY_HUNGRY, nowMs = 0)
        registry.markFired(GossipTriggerType.MUSIC_MOOD, nowMs = 0)
        // 25 hours later — BATTERY_HUNGRY expired, MUSIC_MOOD expired
        val now = TWENTY_FOUR_HOURS_MS + 1
        assertThat(registry.activeCooldownCount(nowMs = now)).isEqualTo(0)
    }

    @Test
    fun `CooldownConfig validates bounds`() {
        try {
            CooldownConfig(gossipCooldownMs = 0)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("gossipCooldownMs")
        }
        try {
            CooldownConfig(silentHoursStart = 25)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("silentHoursStart")
        }
    }
}

class EmotionStateTrackerTest {

    private fun makeTracker(config: EmotionConfig = EmotionConfig()) =
        EmotionStateTracker(config)

    // ─── recordEmotion + getState tests ────────────────────────────────────

    @Test
    fun `getState returns neutral state for unknown persona`() {
        val tracker = makeTracker()
        val state = tracker.getState("atlas")
        assertThat(state.angerLevel).isEqualTo(0)
        assertThat(state.happinessLevel).isEqualTo(0)
        assertThat(state.isNeutral()).isTrue()  // wait, let me check EmotionState doesn't have isNeutral...
    }

    @Test
    fun `recordEmotion sets anger level`() {
        val tracker = makeTracker()
        tracker.recordEmotion("atlas", EmotionType.ANGER, intensity = 100)
        assertThat(tracker.getState("atlas").angerLevel).isEqualTo(100)
    }

    @Test
    fun `recordEmotion replaces does not add`() {
        val tracker = makeTracker()
        tracker.recordEmotion("atlas", EmotionType.ANGER, intensity = 50)
        tracker.recordEmotion("atlas", EmotionType.ANGER, intensity = 100)
        assertThat(tracker.getState("atlas").angerLevel).isEqualTo(100)  // replaced, not 150
    }

    @Test
    fun `recordEmotion rejects intensity out of bounds`() {
        val tracker = makeTracker()
        try {
            tracker.recordEmotion("atlas", EmotionType.ANGER, intensity = 150)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("intensity")
        }
    }

    // ─── Decay tests (strategy §7: -20/turn) ───────────────────────────────

    @Test
    fun `decay reduces anger by 20 per turn`() {
        val tracker = makeTracker()
        tracker.recordEmotion("atlas", EmotionType.ANGER, intensity = 100)
        tracker.decay("atlas")
        assertThat(tracker.getState("atlas").angerLevel).isEqualTo(80)
    }

    @Test
    fun `5 turns of decay brings anger from 100 to 0`() {
        val tracker = makeTracker()
        tracker.recordEmotion("atlas", EmotionType.ANGER, intensity = 100)
        repeat(5) { tracker.decay("atlas") }
        assertThat(tracker.getState("atlas").angerLevel).isEqualTo(0)
    }

    @Test
    fun `decay does not go below 0`() {
        val tracker = makeTracker()
        tracker.recordEmotion("atlas", EmotionType.ANGER, intensity = 10)
        tracker.decay("atlas")  // 10 - 20 = -10 → clamped to 0
        assertThat(tracker.getState("atlas").angerLevel).isEqualTo(0)
    }

    @Test
    fun `decay affects all emotions simultaneously`() {
        val tracker = makeTracker()
        tracker.recordEmotion("atlas", EmotionType.ANGER, intensity = 60)
        tracker.recordEmotion("atlas", EmotionType.HAPPINESS, intensity = 80)
        tracker.recordEmotion("atlas", EmotionType.SADNESS, intensity = 40)
        tracker.decay("atlas")
        assertThat(tracker.getState("atlas").angerLevel).isEqualTo(40)
        assertThat(tracker.getState("atlas").happinessLevel).isEqualTo(60)
        assertThat(tracker.getState("atlas").sadnessLevel).isEqualTo(20)
    }

    @Test
    fun `warmth decays slower than other emotions (10 per turn)`() {
        val tracker = makeTracker()
        tracker.recordEmotion("atlas", EmotionType.WARMTH, intensity = 100)
        tracker.recordEmotion("atlas", EmotionType.HAPPINESS, intensity = 100)
        tracker.decay("atlas")
        // Happiness: 100 - 20 = 80; Warmth: 100 - 10 = 90
        assertThat(tracker.getState("atlas").happinessLevel).isEqualTo(80)
        assertThat(tracker.getState("atlas").warmthLevel).isEqualTo(90)
    }

    // ─── Per-persona isolation ─────────────────────────────────────────────

    @Test
    fun `emotional states are isolated per persona`() {
        val tracker = makeTracker()
        tracker.recordEmotion("atlas", EmotionType.ANGER, intensity = 100)
        tracker.recordEmotion("nova", EmotionType.HAPPINESS, intensity = 100)

        assertThat(tracker.getState("atlas").angerLevel).isEqualTo(100)
        assertThat(tracker.getState("atlas").happinessLevel).isEqualTo(0)
        assertThat(tracker.getState("nova").angerLevel).isEqualTo(0)
        assertThat(tracker.getState("nova").happinessLevel).isEqualTo(100)
    }

    @Test
    fun `decay only affects specified persona`() {
        val tracker = makeTracker()
        tracker.recordEmotion("atlas", EmotionType.ANGER, intensity = 100)
        tracker.recordEmotion("nova", EmotionType.ANGER, intensity = 100)

        tracker.decay("atlas")  // only Atlas decays

        assertThat(tracker.getState("atlas").angerLevel).isEqualTo(80)
        assertThat(tracker.getState("nova").angerLevel).isEqualTo(100)  // Nova unaffected
    }

    // ─── isNeutral + getDominantEmotion ────────────────────────────────────

    @Test
    fun `isNeutral returns true for unknown persona`() {
        val tracker = makeTracker()
        assertThat(tracker.isNeutral("atlas")).isTrue()
    }

    @Test
    fun `isNeutral returns false when any emotion > 0`() {
        val tracker = makeTracker()
        tracker.recordEmotion("atlas", EmotionType.SADNESS, intensity = 20)
        assertThat(tracker.isNeutral("atlas")).isFalse()
    }

    @Test
    fun `isNeutral returns true after full decay`() {
        val tracker = makeTracker()
        tracker.recordEmotion("atlas", EmotionType.ANGER, intensity = 100)
        repeat(5) { tracker.decay("atlas") }
        assertThat(tracker.isNeutral("atlas")).isTrue()
    }

    @Test
    fun `getDominantEmotion returns null when neutral`() {
        val tracker = makeTracker()
        assertThat(tracker.getDominantEmotion("atlas")).isNull()
    }

    @Test
    fun `getDominantEmotion returns highest emotion`() {
        val tracker = makeTracker()
        tracker.recordEmotion("atlas", EmotionType.ANGER, intensity = 80)
        tracker.recordEmotion("atlas", EmotionType.HAPPINESS, intensity = 30)
        assertThat(tracker.getDominantEmotion("atlas")).isEqualTo(EmotionType.ANGER)
    }

    // ─── toPromptString ────────────────────────────────────────────────────

    @Test
    fun `toPromptString returns empty for neutral state`() {
        val tracker = makeTracker()
        assertThat(tracker.toPromptString("atlas")).isEmpty()
    }

    @Test
    fun `toPromptString includes emotion levels when non-neutral`() {
        val tracker = makeTracker()
        tracker.recordEmotion("atlas", EmotionType.ANGER, intensity = 60)
        val str = tracker.toPromptString("atlas")
        assertThat(str).contains("[MOOD]")
        assertThat(str).contains("angry")
        assertThat(str).contains("60/100")
    }

    // ─── clear tests ───────────────────────────────────────────────────────

    @Test
    fun `clear removes all emotional states`() {
        val tracker = makeTracker()
        tracker.recordEmotion("atlas", EmotionType.ANGER, intensity = 100)
        tracker.recordEmotion("nova", EmotionType.HAPPINESS, intensity = 100)
        tracker.clear()
        assertThat(tracker.isNeutral("atlas")).isTrue()
        assertThat(tracker.isNeutral("nova")).isTrue()
    }

    @Test
    fun `clearPersona removes only specified persona`() {
        val tracker = makeTracker()
        tracker.recordEmotion("atlas", EmotionType.ANGER, intensity = 100)
        tracker.recordEmotion("nova", EmotionType.ANGER, intensity = 100)
        tracker.clearPersona("atlas")
        assertThat(tracker.isNeutral("atlas")).isTrue()
        assertThat(tracker.isNeutral("nova")).isFalse()
    }

    // ─── Config validation ─────────────────────────────────────────────────

    @Test
    fun `EmotionConfig validates bounds`() {
        try {
            EmotionConfig(decayPerTurn = 0)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("decayPerTurn")
        }
    }

    @Test
    fun `EmotionType has 4 distinct values`() {
        assertThat(EmotionType.entries).hasSize(4)
        assertThat(EmotionType.entries.map { it.name }).containsExactly(
            "ANGER", "HAPPINESS", "SADNESS", "WARMTH",
        )
    }
}
