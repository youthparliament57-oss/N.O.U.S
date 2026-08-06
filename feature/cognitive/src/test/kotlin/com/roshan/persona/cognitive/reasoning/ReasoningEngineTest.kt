// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.cognitive.reasoning

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.cognitive.model.CognitiveConfig
import com.roshan.persona.cognitive.model.Constraint
import com.roshan.persona.cognitive.model.ConstraintType
import com.roshan.persona.cognitive.model.ReasoningStep
import com.roshan.persona.cognitive.model.SubTask
import com.roshan.persona.cognitive.model.SubTaskType
import com.roshan.persona.cognitive.model.VerificationResult
import com.roshan.persona.persona.core.BuiltInPersonas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0110: [feature] ReasoningEngineTest verified

class ChainOfThoughtTest {

    private class FakeLlm(private val response: String) : ReasoningLlm {
        var callCount = 0
            private set
        var lastTemperature: Float = 0f
            private set

        override suspend fun reason(prompt: String, temperature: Float): String {
            callCount++
            lastTemperature = temperature
            return response
        }
    }

    private val sampleResponse = """
        THOUGHT: Flights cost ₹3-5K, train costs ₹800.
        ACTION: Checking train schedule.
        OBSERVATION: Train departs 6 AM, arrives 2 PM.
        CONFIDENCE: 0.85
        ---
        THOUGHT: Train is much cheaper. ₹800 vs ₹3K saves ₹2.2K.
        ACTION: Comparing total budget.
        OBSERVATION: Train + hotel fits within ₹15K with ₹3K buffer.
        CONFIDENCE: 0.9
        ---
        THOUGHT: Train is the better option.
        ACTION: Finalizing recommendation.
        OBSERVATION: Take the train to Goa — saves money for activities.
        CONFIDENCE: 0.88
    """.trimIndent()

    private fun makeCoT(response: String = sampleResponse) = ChainOfThought(
        llm = FakeLlm(response),
        config = CognitiveConfig(minReasoningSteps = 3, maxReasoningSteps = 10),
        dispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `generate returns parsed reasoning steps`() = runTest {
        val cot = makeCoT()
        val steps = cot.generate("Plan a Goa trip", BuiltInPersonas.ATLAS)
        assertThat(steps).hasSize(3)
        assertThat(steps[0].thought).contains("Flights")
        assertThat(steps[0].action).contains("Checking")
        assertThat(steps[0].observation).contains("Train departs")
        assertThat(steps[0].confidence).isWithin(0.01f).of(0.85f)
    }

    @Test
    fun `generate returns empty list for blank LLM response`() = runTest {
        val cot = makeCoT(response = "")
        assertThat(cot.generate("query", BuiltInPersonas.ATLAS)).isEmpty()
    }

    @Test
    fun `generate passes persona temperature to LLM`() = runTest {
        val llm = FakeLlm(sampleResponse)
        val cot = ChainOfThought(llm, CognitiveConfig(), Dispatchers.Unconfined)
        cot.generate("query", BuiltInPersonas.ONYX)
        // Onyx temperature is 0.1.
        assertThat(llm.lastTemperature).isWithin(0.01f).of(0.1f)
    }

    @Test
    fun `generate passes Atlas temperature to LLM`() = runTest {
        val llm = FakeLlm(sampleResponse)
        val cot = ChainOfThought(llm, CognitiveConfig(), Dispatchers.Unconfined)
        cot.generate("query", BuiltInPersonas.ATLAS)
        assertThat(llm.lastTemperature).isWithin(0.01f).of(0.4f)
    }

    @Test
    fun `buildPrompt includes persona displayName`() {
        val cot = makeCoT()
        val prompt = cot.buildPrompt("test query", BuiltInPersonas.ATLAS, 5, "")
        assertThat(prompt).contains("Atlas")
    }

    @Test
    fun `buildPrompt includes target step count`() {
        val cot = makeCoT()
        val prompt = cot.buildPrompt("test query", BuiltInPersonas.ATLAS, 7, "")
        assertThat(prompt).contains("7")
    }

    @Test
    fun `buildPrompt includes context when provided`() {
        val cot = makeCoT()
        val prompt = cot.buildPrompt("query", BuiltInPersonas.ATLAS, 3, "User prefers budget options")
        assertThat(prompt).contains("User prefers budget")
    }

    @Test
    fun `buildPrompt includes persona-specific style for Onyx`() {
        val cot = makeCoT()
        val prompt = cot.buildPrompt("query", BuiltInPersonas.ONYX, 3, "")
        assertThat(prompt).contains("terse")
    }

    @Test
    fun `parseSteps handles missing CONFIDENCE field with default 0_8`() {
        val response = """
            THOUGHT: test
            ACTION: test action
            OBSERVATION: test observation
        """.trimIndent()
        val cot = makeCoT(response)
        val steps = cot.parseSteps(response)
        assertThat(steps).hasSize(1)
        assertThat(steps[0].confidence).isWithin(0.01f).of(0.8f)
    }

    @Test
    fun `parseSteps handles missing ACTION field with default`() {
        val response = "THOUGHT: test thought\nOBSERVATION: test obs"
        val cot = makeCoT(response)
        val steps = cot.parseSteps(response)
        assertThat(steps).hasSize(1)
        assertThat(steps[0].action).isEqualTo("Reasoning")
    }

    @Test
    fun `parseSteps skips blank blocks`() {
        val response = """
            THOUGHT: step 1
            ACTION: act
            OBSERVATION: obs

            ---

            THOUGHT: step 2
            ACTION: act2
            OBSERVATION: obs2
        """.trimIndent()
        val cot = makeCoT(response)
        val steps = cot.parseSteps(response)
        assertThat(steps).hasSize(2)
    }

    @Test
    fun `parseSteps clamps to maxReasoningSteps`() {
        val sb = StringBuilder()
        for (i in 1..15) {
            sb.append("THOUGHT: step $i\nACTION: act\nOBSERVATION: obs\nCONFIDENCE: 0.8")
            if (i < 15) sb.append("\n---\n")
        }
        val cot = makeCoT(response = sb.toString())
        val steps = cot.parseSteps(sb.toString())
        assertThat(steps.size).isAtMost(10)  // maxReasoningSteps
    }
}

class SelfCorrectorTest {

    private class FakeLlm(private val response: String) : ReasoningLlm {
        override suspend fun reason(prompt: String, temperature: Float): String = response
    }

    private fun makeCorrector(llmResponse: String = "VERIFIED") = SelfCorrector(
        llm = FakeLlm(llmResponse),
        config = CognitiveConfig(),
        dispatcher = Dispatchers.Unconfined,
    )

    private fun validTrace(confidence: Float = 0.85f) = ReasoningTrace(
        steps = listOf(ReasoningStep(
            thought = "Train costs ₹800, fits budget.",
            action = "Checking schedule",
            observation = "Take the train to Goa.",
            confidence = confidence,
        )),
        finalAnswer = "Take the train to Goa for ₹800.",
        confidence = confidence,
    )

    // ─── ruleBasedVerify tests ─────────────────────────────────────────────

    @Test
    fun `ruleBasedVerify returns Verified for valid trace`() {
        val corrector = makeCorrector()
        val result = corrector.ruleBasedVerify(validTrace(), emptyList())
        // Rule-based passes → then LLM verify runs → returns "VERIFIED".
        // But we're testing ruleBasedVerify directly — it should return Verified.
        assertThat(result).isEqualTo(VerificationResult.Verified)
    }

    @Test
    fun `ruleBasedVerify returns Failed for zero confidence`() {
        val corrector = makeCorrector()
        val result = corrector.ruleBasedVerify(validTrace(confidence = 0f), emptyList())
        assertThat(result).isInstanceOf(VerificationResult.Failed::class.java)
    }

    @Test
    fun `ruleBasedVerify returns NeedsCorrection for budget constraint without numbers`() {
        val corrector = makeCorrector()
        val trace = ReasoningTrace(
            steps = listOf(ReasoningStep(
                thought = "I recommend the train.",
                action = "Finalizing",
                observation = "Take the train.",
                confidence = 0.8f,
            )),
            finalAnswer = "Take the train.",  // no numbers!
            confidence = 0.8f,
        )
        val constraints = listOf(Constraint(type = ConstraintType.BUDGET, value = "15000"))
        val result = corrector.ruleBasedVerify(trace, constraints)
        assertThat(result).isInstanceOf(VerificationResult.NeedsCorrection::class.java)
    }

    @Test
    fun `ruleBasedVerify detects contradiction between steps`() {
        val corrector = makeCorrector()
        val trace = ReasoningTrace(
            steps = listOf(
                ReasoningStep(thought = "Flights are cheaper", action = "a", observation = "o", confidence = 0.8f),
                ReasoningStep(thought = "Actually, no — trains are cheaper", action = "a", observation = "o", confidence = 0.8f),
            ),
            finalAnswer = "Take the train.",
            confidence = 0.8f,
        )
        val result = corrector.ruleBasedVerify(trace, emptyList())
        assertThat(result).isInstanceOf(VerificationResult.NeedsCorrection::class.java)
    }

    // ─── parseVerificationResponse tests ───────────────────────────────────

    @Test
    fun `parseVerificationResponse VERIFIED returns Verified`() {
        val corrector = makeCorrector()
        assertThat(corrector.parseVerificationResponse("VERIFIED"))
            .isEqualTo(VerificationResult.Verified)
    }

    @Test
    fun `parseVerificationResponse NEEDS_CORRECTION parses issue`() {
        val corrector = makeCorrector()
        val result = corrector.parseVerificationResponse("NEEDS_CORRECTION: Budget is wrong")
        assertThat(result).isInstanceOf(VerificationResult.NeedsCorrection::class.java)
        assertThat((result as VerificationResult.NeedsCorrection).issue).contains("Budget")
    }

    @Test
    fun `parseVerificationResponse FAILED parses reason`() {
        val corrector = makeCorrector()
        val result = corrector.parseVerificationResponse("FAILED: Fundamentally flawed")
        assertThat(result).isInstanceOf(VerificationResult.Failed::class.java)
        assertThat((result as VerificationResult.Failed).reason).contains("flawed")
    }

    @Test
    fun `parseVerificationResponse unknown returns Verified (safe default)`() {
        val corrector = makeCorrector()
        assertThat(corrector.parseVerificationResponse("something random"))
            .isEqualTo(VerificationResult.Verified)
    }

    // ─── verify (full) tests ───────────────────────────────────────────────

    @Test
    fun `verify returns Verified when LLM says VERIFIED`() = runTest {
        val corrector = makeCorrector(llmResponse = "VERIFIED")
        val result = corrector.verify(validTrace(), emptyList(), BuiltInPersonas.ATLAS)
        assertThat(result).isEqualTo(VerificationResult.Verified)
    }

    @Test
    fun `verify returns NeedsCorrection when LLM says NEEDS_CORRECTION`() = runTest {
        val corrector = makeCorrector(llmResponse = "NEEDS_CORRECTION: The budget is wrong")
        val result = corrector.verify(validTrace(), emptyList(), BuiltInPersonas.ATLAS)
        assertThat(result).isInstanceOf(VerificationResult.NeedsCorrection::class.java)
    }
}

class ReasoningEngineTest {

    private class FakeLlm(private val response: String) : ReasoningLlm {
        var callCount = 0
            private set
        override suspend fun reason(prompt: String, temperature: Float): String {
            callCount++
            return response
        }
    }

    private val sampleResponse = """
        THOUGHT: Train costs ₹800.
        ACTION: Checking budget.
        OBSERVATION: Take the train to Goa — fits ₹15K budget with ₹3K buffer.
        CONFIDENCE: 0.9
    """.trimIndent()

    private fun makeEngine(llmResponse: String = sampleResponse): Pair<ReasoningEngine, FakeLlm> {
        val llm = FakeLlm(llmResponse)
        val cot = ChainOfThought(llm, CognitiveConfig(), Dispatchers.Unconfined)
        val corrector = SelfCorrector(llm, CognitiveConfig(), Dispatchers.Unconfined)
        val engine = ReasoningEngine(cot, corrector, CognitiveConfig(), Dispatchers.Unconfined)
        return engine to llm
    }

    @Test
    fun `reason returns ReasoningTrace with steps and answer`() = runTest {
        val (engine, _) = makeEngine()
        val trace = engine.reason("Plan a Goa trip", BuiltInPersonas.ATLAS)
        assertThat(trace.steps).isNotEmpty()
        assertThat(trace.finalAnswer).isNotEmpty()
        assertThat(trace.confidence).isGreaterThan(0f)
    }

    @Test
    fun `reason returns low-confidence trace when LLM returns empty`() = runTest {
        val (engine, _) = makeEngine(llmResponse = "")
        val trace = engine.reason("query", BuiltInPersonas.ATLAS)
        assertThat(trace.confidence).isAtMost(0.2f)
        assertThat(trace.finalAnswer).contains("couldn't")
    }

    @Test
    fun `reason applies correction when verification finds issue`() = runTest {
        // First LLM call: generates steps. Second: verifies → NEEDS_CORRECTION.
        // Third: regenerates with correction. Fourth: verifies → VERIFIED.
        // But our FakeLlm always returns the same response...
        // Let's use a different approach: the corrector's Llm returns NEEDS_CORRECTION.
        val llm = object : ReasoningLlm {
            var callCount = 0
            override suspend fun reason(prompt: String, temperature: Float): String {
                callCount++
                // If the prompt contains "Verify" → return NEEDS_CORRECTION on first verify.
                // Otherwise return the sample response.
                return if (prompt.contains("Verify") && callCount == 2) {
                    "NEEDS_CORRECTION: Budget not mentioned in answer"
                } else if (prompt.contains("Verify") && callCount == 4) {
                    "VERIFIED"
                } else {
                    sampleResponse
                }
            }
        }
        val cot = ChainOfThought(llm, CognitiveConfig(), Dispatchers.Unconfined)
        val corrector = SelfCorrector(llm, CognitiveConfig(), Dispatchers.Unconfined)
        val engine = ReasoningEngine(cot, corrector, CognitiveConfig(), Dispatchers.Unconfined)

        val trace = engine.reason("Plan a Goa trip", BuiltInPersonas.ATLAS)
        // Correction should have been applied.
        assertThat(trace.correctionApplied).isTrue()
        assertThat(trace.originalAnswer).isNotNull()
    }

    @Test
    fun `estimateComplexity returns min for short simple query`() {
        val (engine, _) = makeEngine()
        val complexity = engine.estimateComplexity("what time is it", null)
        assertThat(complexity).isEqualTo(CognitiveConfig.DEFAULT_MIN_REASONING_STEPS)
    }

    @Test
    fun `estimateComplexity returns max when subTasks present`() {
        val (engine, _) = makeEngine()
        val subTasks = listOf(SubTask("a", "do thing", SubTaskType.RESEARCH))
        val complexity = engine.estimateComplexity("short", subTasks)
        assertThat(complexity).isEqualTo(CognitiveConfig.DEFAULT_MAX_REASONING_STEPS)
    }

    @Test
    fun `estimateComplexity returns higher for complex keywords`() {
        val (engine, _) = makeEngine()
        val complexity = engine.estimateComplexity("compare iPhone vs Android and recommend the best", null)
        assertThat(complexity).isGreaterThan(CognitiveConfig.DEFAULT_MIN_REASONING_STEPS)
    }

    @Test
    fun `reason respects maxCorrectionAttempts limit`() = runTest {
        // LLM always returns NEEDS_CORRECTION → max 3 attempts then return with reduced confidence.
        val llm = object : ReasoningLlm {
            override suspend fun reason(prompt: String, temperature: Float): String {
                return if (prompt.contains("Verify")) "NEEDS_CORRECTION: always wrong" else sampleResponse
            }
        }
        val cot = ChainOfThought(llm, CognitiveConfig(maxCorrectionAttempts = 3), Dispatchers.Unconfined)
        val corrector = SelfCorrector(llm, CognitiveConfig(maxCorrectionAttempts = 3), Dispatchers.Unconfined)
        val engine = ReasoningEngine(cot, corrector, CognitiveConfig(maxCorrectionAttempts = 3), Dispatchers.Unconfined)

        val trace = engine.reason("query", BuiltInPersonas.ATLAS)
        // After max attempts → reduced confidence.
        assertThat(trace.confidence).isAtMost(0.7f)
        // Correction was attempted.
        assertThat(trace.correctionApplied).isTrue()
    }
}
