// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.voice.proactive

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.voice.bargein.ProactiveBargeIn
import com.roshan.persona.voice.tts.Emotion
import com.roshan.persona.voice.tts.EmotionalTtsEngine
import com.roshan.persona.voice.tts.FakeTtsEngineAccessible
import com.roshan.persona.voice.tts.VoicePersona
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalTime

// AUTO_FIX_0160: [feature] ProactiveEngineTest verified

// VOICE_FIX_037: Proactive engine ok

class ProactiveEngineTest {

    // ─── Fakes ─────────────────────────────────────────────────────────────

    private class FakeStateProbe(
        var inCall: Boolean = false,
        var inMeeting: Boolean = false,
        var speaking: Boolean = false,
        var proactiveEnabled: Boolean = true,
    ) : ProactiveStateProbe {
        var stopSttCalled = false
        var resumeSttCalled = false
        override fun isUserInCall(): Boolean = inCall
        override fun isUserInMeeting(): Boolean = inMeeting
        override fun isUserSpeaking(): Boolean = speaking
        override fun isProactiveSpeechEnabled(): Boolean = proactiveEnabled
        override fun stopStt() { stopSttCalled = true }
        override fun resumeStt() { resumeSttCalled = true }
    }

    private class FakeTriggerSource(private val triggers: List<ProactiveTrigger>) : TriggerSource {
        var evaluateCount = 0
        override suspend fun evaluate(): List<ProactiveTrigger> {
            evaluateCount++
            return triggers
        }
    }

    private fun makeEngine(
        triggers: List<ProactiveTrigger> = emptyList(),
        state: FakeStateProbe = FakeStateProbe(),
        silentHours: SilentHours = SilentHours(
            start = LocalTime.of(3, 0),
            end = LocalTime.of(4, 0),
        ),  // narrow window unlikely to be active
        bargeIn: ProactiveBargeIn? = null,
    ): Triple<ProactiveEngine, EmotionalTtsEngine, FakeTtsEngineAccessible> {
        val fakeTts = FakeTtsEngineAccessible()
        val ttsEngine = EmotionalTtsEngine(fakeTts)
        val engine = ProactiveEngine(
            tts = ttsEngine,
            proactiveBargeIn = bargeIn,
            triggerSources = listOf(FakeTriggerSource(triggers)),
            stateProbe = state,
            personaProvider = { VoicePersona.JARVIS },
            config = ProactiveEngine.Config(silentHours = silentHours),
            dispatcher = Dispatchers.Unconfined,
        )
        return Triple(engine, ttsEngine, fakeTts)
    }

    // ─── Tests ─────────────────────────────────────────────────────────────

    @Test
    fun `checkAndSpeak returns null when proactive speech is disabled`() = runTest {
        val state = FakeStateProbe(proactiveEnabled = false)
        val (engine, _, _) = makeEngine(
            triggers = listOf(ProactiveTrigger(TriggerType.MORNING_BRIEFING, "Hi", priority = 5)),
            state = state,
        )
        val result = engine.checkAndSpeak()
        assertThat(result).isNull()
    }

    @Test
    fun `checkAndSpeak speaks highest-priority trigger that passes shouldSpeak`() = runTest {
        val (engine, _, fakeTts) = makeEngine(
            triggers = listOf(
                ProactiveTrigger(TriggerType.MORNING_BRIEFING, "Low priority", priority = 3),
                ProactiveTrigger(TriggerType.CALENDAR_REMINDER, "Meeting in 15 min", priority = 8),
                ProactiveTrigger(TriggerType.BATTERY_LOW, "Battery low", priority = 9),
            ),
        )
        val result = engine.checkAndSpeak()
        // Should speak the highest priority (BATTERY_LOW, 9).
        assertThat(result?.type).isEqualTo(TriggerType.BATTERY_LOW)
        assertThat(fakeTts.lastSpokenText).contains("Battery low")
    }

    @Test
    fun `checkAndSpeak skips trigger when user is in call`() = runTest {
        val state = FakeStateProbe(inCall = true)
        val (engine, _, fakeTts) = makeEngine(
            triggers = listOf(ProactiveTrigger(TriggerType.MORNING_BRIEFING, "Hi", priority = 5)),
            state = state,
        )
        val result = engine.checkAndSpeak()
        assertThat(result).isNull()
        assertThat(fakeTts.lastSpokenText).isNull()
    }

    @Test
    fun `checkAndSpeak skips trigger when user is in meeting`() = runTest {
        val state = FakeStateProbe(inMeeting = true)
        val (engine, _, fakeTts) = makeEngine(
            triggers = listOf(ProactiveTrigger(TriggerType.MORNING_BRIEFING, "Hi", priority = 5)),
            state = state,
        )
        val result = engine.checkAndSpeak()
        assertThat(result).isNull()
    }

    @Test
    fun `checkAndSpeak suppresses low-priority triggers during silent hours`() = runTest {
        // Use silent hours that are always active (00:00 → 23:59) to force silent mode.
        val alwaysSilent = SilentHours(start = LocalTime.of(0, 0), end = LocalTime.of(23, 59))
        val (engine, _, fakeTts) = makeEngine(
            triggers = listOf(
                ProactiveTrigger(TriggerType.MORNING_BRIEFING, "Low", priority = 5),  // < 8
                ProactiveTrigger(TriggerType.BATTERY_LOW, "Urgent", priority = 9),    // ≥ 8
            ),
            silentHours = alwaysSilent,
        )
        val result = engine.checkAndSpeak()
        // Low-priority should be suppressed; urgent should fire.
        assertThat(result?.type).isEqualTo(TriggerType.BATTERY_LOW)
    }

    @Test
    fun `shouldSpeak returns false within minIntervalMs of last speak`() = runTest {
        val (engine, _, _) = makeEngine(
            triggers = listOf(ProactiveTrigger(TriggerType.MORNING_BRIEFING, "Hi", priority = 5)),
        )
        // First speak should fire.
        val first = engine.checkAndSpeak()
        assertThat(first).isNotNull()
        // Immediate second check should be suppressed by cooldown.
        val second = engine.checkAndSpeak()
        assertThat(second).isNull()
    }

    @Test
    fun `checkAndSpeak returns null when no triggers fire`() = runTest {
        val (engine, _, _) = makeEngine(triggers = emptyList())
        assertThat(engine.checkAndSpeak()).isNull()
    }

    @Test
    fun `ProactiveEngine Config validates bounds`() {
        try {
            ProactiveEngine.Config(minIntervalMs = -1)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("minIntervalMs")
        }
        try {
            ProactiveEngine.Config(proactiveBargeInThreshold = 11)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("proactiveBargeInThreshold")
        }
    }
}
