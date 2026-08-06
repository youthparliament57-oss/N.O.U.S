// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.cognitive.engine

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.cognitive.model.CognitiveConfig
import com.roshan.persona.cognitive.model.UncertaintyAction
import com.roshan.persona.persona.core.BuiltInPersonas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0113: [feature] ClarificationEngineTest verified

class ClarificationEngineTest {

    private val engine = ClarificationEngine(
        config = CognitiveConfig(maxClarificationQuestions = 2),
        dispatcher = Dispatchers.Unconfined,
    )

    // ─── detectAmbiguities tests ───────────────────────────────────────────

    @Test
    fun `detectAmbiguities returns empty for specific trip query`() {
        val result = engine.detectAmbiguities("Plan a 3-day Goa trip under 15000")
        assertThat(result).isEmpty()
    }

    @Test
    fun `detectAmbiguities detects missing destination for trip`() {
        val result = engine.detectAmbiguities("plan a 3-day trip")
        assertThat(result).contains(AmbiguityType.DESTINATION)
    }

    @Test
    fun `detectAmbiguities detects missing duration for trip`() {
        val result = engine.detectAmbiguities("plan a trip to Goa")
        assertThat(result).contains(AmbiguityType.DURATION)
    }

    @Test
    fun `detectAmbiguities detects both destination and duration for vague trip`() {
        val result = engine.detectAmbiguities("plan a trip")
        assertThat(result).contains(AmbiguityType.DESTINATION)
        assertThat(result).contains(AmbiguityType.DURATION)
    }

    @Test
    fun `detectAmbiguities detects missing comparison subjects`() {
        val result = engine.detectAmbiguities("compare phones")
        assertThat(result).contains(AmbiguityType.COMPARISON_SUBJECT)
    }

    @Test
    fun `detectAmbiguities returns empty for explicit comparison`() {
        val result = engine.detectAmbiguities("compare iPhone vs Android")
        assertThat(result).isEmpty()
    }

    @Test
    fun `detectAmbiguities detects vague purpose`() {
        val result = engine.detectAmbiguities("what should i do")
        assertThat(result).contains(AmbiguityType.PURPOSE)
    }

    @Test
    fun `detectAmbiguities detects missing product for purchase`() {
        val result = engine.detectAmbiguities("should i buy")
        assertThat(result).contains(AmbiguityType.PRODUCT)
    }

    @Test
    fun `detectAmbiguities returns empty for specific purchase`() {
        val result = engine.detectAmbiguities("should i buy a phone under 30000")
        assertThat(result).isEmpty()
    }

    @Test
    fun `detectAmbiguities returns empty for non-trip non-comparison query`() {
        assertThat(engine.detectAmbiguities("what is the capital of France")).isEmpty()
        assertThat(engine.detectAmbiguities("turn on the torch")).isEmpty()
    }

    // ─── checkClarification tests ──────────────────────────────────────────

    @Test
    fun `checkClarification returns null for clear query`() = runTest {
        val result = engine.checkClarification("Plan a 3-day Goa trip under 15000", BuiltInPersonas.ATLAS)
        assertThat(result).isNull()
    }

    @Test
    fun `checkClarification returns questions for ambiguous trip`() = runTest {
        val result = engine.checkClarification("plan a trip", BuiltInPersonas.ATLAS)
        assertThat(result).isNotNull()
        assertThat(result!!).hasSize(2)  // DESTINATION + DURATION
    }

    @Test
    fun `checkClarification respects maxClarificationQuestions limit`() = runTest {
        val limitedEngine = ClarificationEngine(
            config = CognitiveConfig(maxClarificationQuestions = 1),
            dispatcher = Dispatchers.Unconfined,
        )
        val result = limitedEngine.checkClarification("plan a trip", BuiltInPersonas.ATLAS)
        assertThat(result).hasSize(1)  // only 1 question despite 2 ambiguities
    }

    @Test
    fun `checkClarification styles question per persona Atlas`() = runTest {
        val result = engine.checkClarification("plan a trip", BuiltInPersonas.ATLAS)
        assertThat(result!![0].question).contains("sir")  // Atlas says "sir"
    }

    @Test
    fun `checkClarification styles question per persona Onyx terse`() = runTest {
        val result = engine.checkClarification("plan a trip", BuiltInPersonas.ONYX)
        // Onyx is terse — no question mark, just a period.
        assertThat(result!![0].question).doesNotContain("?")
    }

    @Test
    fun `checkClarification styles question per persona Nova enthusiastic`() = runTest {
        val result = engine.checkClarification("plan a trip", BuiltInPersonas.NOVA)
        // Nova replaces ? with ! (enthusiastic).
        assertThat(result!![0].question).contains("!")
    }

    @Test
    fun `checkClarification includes reasoning for each question`() = runTest {
        val result = engine.checkClarification("plan a trip", BuiltInPersonas.ATLAS)
        for (q in result!!) {
            assertThat(q.reasoning).isNotEmpty()
        }
    }

    @Test
    fun `checkClarification DURATION question has default options`() = runTest {
        val result = engine.checkClarification("plan a trip to Goa", BuiltInPersonas.ATLAS)
        // Should ask about DURATION (Goa is detected, duration is not).
        val durationQ = result!!.first { it.reasoning.contains("duration") }
        assertThat(durationQ.options).isNotNull()
        assertThat(durationQ.options).contains("3 days")
    }

    @Test
    fun `AmbiguityType has 6 distinct values`() {
        assertThat(AmbiguityType.entries).hasSize(6)
    }
}

class UncertaintyAwarenessTest {

    private val awareness = UncertaintyAwareness(
        config = CognitiveConfig(
            confidenceCautiousThreshold = 0.7f,
            confidenceDeferThreshold = 0.3f,
        ),
    )

    @Test
    fun `handle returns Answer when confidence >= 0_7`() {
        val action = awareness.handle(0.8f, "What is the capital of France?")
        assertThat(action).isEqualTo(UncertaintyAction.Answer)
    }

    @Test
    fun `handle returns Answer at exactly 0_7`() {
        val action = awareness.handle(0.7f, "query")
        assertThat(action).isEqualTo(UncertaintyAction.Answer)
    }

    @Test
    fun `handle returns AskFollowUp when confidence 0_3 to 0_7`() {
        val action = awareness.handle(0.5f, "query")
        assertThat(action).isInstanceOf(UncertaintyAction.AskFollowUp::class.java)
        assertThat((action as UncertaintyAction.AskFollowUp).question).isNotEmpty()
    }

    @Test
    fun `handle returns AdmitUnknown when confidence < 0_3`() {
        val action = awareness.handle(0.2f, "query")
        assertThat(action).isInstanceOf(UncertaintyAction.AdmitUnknown::class.java)
        assertThat((action as UncertaintyAction.AdmitUnknown).message).contains("don't know")
    }

    @Test
    fun `handle returns AdmitUnknown at exactly 0`() {
        val action = awareness.handle(0f, "query")
        assertThat(action).isInstanceOf(UncertaintyAction.AdmitUnknown::class.java)
    }

    @Test
    fun `handle rejects confidence > 1`() {
        try {
            awareness.handle(1.5f, "query")
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("confidence")
        }
    }

    @Test
    fun `shouldSearchWeb returns true when confidence < 0_3`() {
        assertThat(awareness.shouldSearchWeb(0.2f)).isTrue()
    }

    @Test
    fun `shouldSearchWeb returns false when confidence >= 0_3`() {
        assertThat(awareness.shouldSearchWeb(0.5f)).isFalse()
    }

    @Test
    fun `shouldSearchWeb rejects confidence > 1`() {
        try {
            awareness.shouldSearchWeb(2f)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("confidence")
        }
    }
}

class ConfidenceModulatorTest {

    private val modulator = ConfidenceModulator(
        config = CognitiveConfig(
            confidenceAssertiveThreshold = 0.9f,
            confidenceCautiousThreshold = 0.7f,
            confidenceDeferThreshold = 0.3f,
        ),
    )

    // ─── getTone tests ─────────────────────────────────────────────────────

    @Test
    fun `getTone returns ASSERTIVE for confidence >= 0_9`() {
        assertThat(modulator.getTone(0.95f)).isEqualTo(Tone.ASSERTIVE)
    }

    @Test
    fun `getTone returns CAUTIOUS for confidence 0_7 to 0_9`() {
        assertThat(modulator.getTone(0.8f)).isEqualTo(Tone.CAUTIOUS)
    }

    @Test
    fun `getTone returns DEFER for confidence < 0_7`() {
        assertThat(modulator.getTone(0.5f)).isEqualTo(Tone.DEFER)
    }

    @Test
    fun `getTone at exact boundaries`() {
        assertThat(modulator.getTone(0.9f)).isEqualTo(Tone.ASSERTIVE)
        assertThat(modulator.getTone(0.7f)).isEqualTo(Tone.CAUTIOUS)
    }

    // ─── modulate tests ────────────────────────────────────────────────────

    @Test
    fun `modulate ASSERTIVE includes recommend for default persona`() {
        val result = modulator.modulate(0.95f, "take the train", "default")
        assertThat(result).contains("recommend")
        assertThat(result).contains("take the train")
    }

    @Test
    fun `modulate ASSERTIVE Atlas includes sir`() {
        val result = modulator.modulate(0.95f, "take the train", "atlas")
        assertThat(result).contains("sir")
    }

    @Test
    fun `modulate CAUTIOUS includes verify for default persona`() {
        val result = modulator.modulate(0.8f, "take the train", "default")
        assertThat(result).contains("verify")
    }

    @Test
    fun `modulate DEFER includes not fully confident for default persona`() {
        val result = modulator.modulate(0.5f, "take the train", "default")
        assertThat(result).contains("not fully confident")
    }

    @Test
    fun `modulate DEFER Atlas includes sir`() {
        val result = modulator.modulate(0.5f, "take the train", "atlas")
        assertThat(result).contains("sir")
    }

    @Test
    fun `modulate ONYX terse for all tones`() {
        val assertive = modulator.modulate(0.95f, "take the train", "onyx")
        val cautious = modulator.modulate(0.8f, "take the train", "onyx")
        val defer = modulator.modulate(0.5f, "take the train", "onyx")
        // Onyx is terse — short sentences.
        assertThat(assertive.length).isLessThan(50)
        assertThat(cautious).contains("Verify")
        assertThat(defer).contains("Uncertain")
    }

    @Test
    fun `modulate ARIA enthusiastic for assertive`() {
        val result = modulator.modulate(0.95f, "take the train", "aria")
        assertThat(result).contains("!")
    }

    @Test
    fun `modulate rejects blank recommendation`() {
        try {
            modulator.modulate(0.9f, "", "default")
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("recommendation")
        }
    }

    @Test
    fun `modulate rejects confidence > 1`() {
        try {
            modulator.modulate(2f, "test", "default")
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("confidence")
        }
    }

    @Test
    fun `Tone has 3 distinct values`() {
        assertThat(Tone.entries).hasSize(3)
        assertThat(Tone.entries.map { it.name }).containsExactly("ASSERTIVE", "CAUTIOUS", "DEFER")
    }
}
