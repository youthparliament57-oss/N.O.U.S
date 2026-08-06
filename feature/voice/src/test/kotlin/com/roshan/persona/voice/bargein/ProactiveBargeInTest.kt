// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.voice.bargein

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.voice.tts.Emotion
import com.roshan.persona.voice.tts.EmotionalTtsEngine
import com.roshan.persona.voice.tts.FakeTtsEngineAccessible
import com.roshan.persona.voice.tts.VoicePersona
import org.junit.Test

// AUTO_FIX_0181: [feature] ProactiveBargeInTest verified

// VOICE_FIX_016: Proactive barge-in safe

class ProactiveBargeInTest {

    private fun makeProactiveBargeIn(
        config: ProactiveBargeIn.Config = ProactiveBargeIn.Config(),
    ): Triple<ProactiveBargeIn, EmotionalTtsEngine, FakeTtsEngineAccessible> {
        val fakeTts = FakeTtsEngineAccessible()
        val ttsEngine = EmotionalTtsEngine(fakeTts)
        val proactive = ProactiveBargeIn(ttsEngine, config)
        return Triple(proactive, ttsEngine, fakeTts)
    }

    @Test
    fun `priority below threshold does not fire barge-in`() {
        val (proactive, _, fakeTts) = makeProactiveBargeIn(
            config = ProactiveBargeIn.Config(threshold = 8),
        )
        var stopCalled = false
        var resumeCalled = false

        val fired = proactive.tryBargeIn(
            message = "You have a notification.",
            priority = 5,  // below threshold
            persona = VoicePersona.JARVIS,
            stopStt = { stopCalled = true },
            resumeStt = { resumeCalled = true },
        )

        assertThat(fired).isFalse()
        assertThat(stopCalled).isFalse()
        assertThat(resumeCalled).isFalse()
        assertThat(fakeTts.lastSpokenText).isNull()
    }

    @Test
    fun `priority at or above threshold fires barge-in with apology prefix`() {
        val (proactive, _, fakeTts) = makeProactiveBargeIn(
            config = ProactiveBargeIn.Config(threshold = 8, apologyPrefix = "Sorry to interrupt — "),
        )
        var stopCalled = false
        var resumeCalled = false

        val fired = proactive.tryBargeIn(
            message = "Your meeting starts in 1 minute.",
            priority = 8,
            persona = VoicePersona.JARVIS,
            stopStt = { stopCalled = true },
            resumeStt = { resumeCalled = true },
        )

        assertThat(fired).isTrue()
        assertThat(stopCalled).isTrue()
        // TTS hasn't completed yet — resume shouldn't be called yet.
        assertThat(resumeCalled).isFalse()
        // Apology prefix should be prepended.
        assertThat(fakeTts.lastSpokenText).startsWith("Sorry to interrupt — ")
        assertThat(fakeTts.lastSpokenText).contains("Your meeting starts in 1 minute.")
    }

    @Test
    fun `cooldown suppresses a second barge-in attempt`() {
        val (proactive, _, fakeTts) = makeProactiveBargeIn(
            config = ProactiveBargeIn.Config(threshold = 8, cooldownMs = 30_000),
        )

        // First barge-in should fire.
        val firstFired = proactive.tryBargeIn(
            message = "Emergency alert.",
            priority = 10,
            persona = VoicePersona.JARVIS,
            stopStt = {},
            resumeStt = {},
        )
        assertThat(firstFired).isTrue()
        assertThat(proactive.isInCooldown).isTrue()

        // Reset fake to detect the second call.
        fakeTts.reset()

        // Second barge-in attempt should be suppressed by cooldown.
        val secondFired = proactive.tryBargeIn(
            message = "Another emergency.",
            priority = 10,
            persona = VoicePersona.JARVIS,
            stopStt = {},
            resumeStt = {},
        )
        assertThat(secondFired).isFalse()
        assertThat(fakeTts.lastSpokenText).isNull()

        // clearCooldown should allow a third attempt.
        proactive.clearCooldown()
        assertThat(proactive.isInCooldown).isFalse()
    }

    @Test
    fun `blank message does not fire barge-in`() {
        val (proactive, _, fakeTts) = makeProactiveBargeIn()
        var stopCalled = false

        val fired = proactive.tryBargeIn(
            message = "",
            priority = 10,
            persona = VoicePersona.JARVIS,
            stopStt = { stopCalled = true },
            resumeStt = {},
        )

        assertThat(fired).isFalse()
        assertThat(stopCalled).isFalse()
        assertThat(fakeTts.lastSpokenText).isNull()
    }

    @Test
    fun `ProactiveBargeInPriority shouldBargeIn matches threshold`() {
        assertThat(ProactiveBargeInPriority.EMERGENCY_ALERT.shouldBargeIn(threshold = 8)).isTrue()
        assertThat(ProactiveBargeInPriority.SECURITY_THREAT.shouldBargeIn(threshold = 8)).isTrue()
        assertThat(ProactiveBargeInPriority.CRITICAL_REMINDER.shouldBargeIn(threshold = 8)).isTrue()
        assertThat(ProactiveBargeInPriority.IMPORTANT.shouldBargeIn(threshold = 8)).isFalse()
        assertThat(ProactiveBargeInPriority.NORMAL.shouldBargeIn(threshold = 8)).isFalse()
        assertThat(ProactiveBargeInPriority.INFO.shouldBargeIn(threshold = 8)).isFalse()
    }

    @Test
    fun `ProactiveBargeInPriority fromLevel round-trips correctly`() {
        for (priority in ProactiveBargeInPriority.entries) {
            val fromLevel = ProactiveBargeInPriority.fromLevel(priority.level)
            assertThat(fromLevel).isEqualTo(priority)
        }
        assertThat(ProactiveBargeInPriority.fromLevel(99)).isNull()
    }

    @Test
    fun `Config rejects out-of-bounds threshold and cooldown`() {
        try {
            ProactiveBargeIn.Config(threshold = 0)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("threshold")
        }
        try {
            ProactiveBargeIn.Config(threshold = 11)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("threshold")
        }
        try {
            ProactiveBargeIn.Config(cooldownMs = -1)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("cooldownMs")
        }
    }
}
