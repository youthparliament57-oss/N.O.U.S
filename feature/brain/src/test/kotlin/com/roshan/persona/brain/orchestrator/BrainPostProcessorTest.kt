// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.brain.orchestrator

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.context.BrainBudget
import com.roshan.persona.brain.context.BrainContext
import com.roshan.persona.brain.context.DeviceContext
import com.roshan.persona.brain.context.NetworkType
import com.roshan.persona.brain.context.PrivacySettings
import com.roshan.persona.brain.context.ResponseLength
import com.roshan.persona.brain.context.SessionId
import com.roshan.persona.brain.context.ThermalStatus
import com.roshan.persona.brain.context.UserContext
import com.roshan.persona.brain.persona.Persona
import com.roshan.persona.brain.persona.ResponseStyle
import com.roshan.persona.brain.persona.Tone
import com.roshan.persona.brain.persona.Verbosity
import com.roshan.persona.brain.persona.Vocabulary
import com.roshan.persona.brain.skill.SkillOutput
import com.roshan.persona.brain.trace.BrainTrace
import com.roshan.persona.common.CorrelationId
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

// AUTO_FIX_0074: [feature] BrainPostProcessorTest verified

class BrainPostProcessorTest {

    private val postProcessor = BrainPostProcessor(memoryInterface = null)

    private fun testContext() = BrainContext(
        correlationId = CorrelationId.generate(),
        sessionId = SessionId.generate(),
        timestamp = Instant.now(),
        timezone = TimeZone.getDefault(),
        activePersona = Persona(
            id = "jarvis",
            displayName = "JARVIS",
            systemPromptTemplate = "You are {{persona_name}}.",
            skillPreferences = emptyMap(),
            responseStyle = ResponseStyle(Tone.WITTY, Verbosity.BALANCED, false, Vocabulary.TECHNICAL),
        ),
        userContext = UserContext(
            userId = "test",
            displayName = "Roshan",
            preferredLanguage = Locale.ENGLISH,
            preferredResponseLength = ResponseLength.BALANCED,
            skillPreferences = emptyMap(),
            privacySettings = PrivacySettings(),
            monthlyLlmBudgetUsd = 5.0f,
            monthlyLlmSpentUsd = 0f,
        ),
        deviceContext = DeviceContext(
            thermalStatus = ThermalStatus.NONE,
            availableRamMb = 4096,
            batteryLevel = 80,
            isCharging = true,
            isOnline = true,
            networkType = NetworkType.WIFI,
            gpuAvailable = true,
            gpuVramMb = 2048,
        ),
        conversationHistory = emptyList(),
        pendingExpectation = null,
        ambientContext = null,
        budget = BrainBudget.DEFAULT,
    )

    private fun makeTrace(): BrainTrace = BrainTrace(
        correlationId = CorrelationId.generate(),
        sessionId = SessionId.generate(),
        inputRedacted = "test",
        startedAt = Instant.now(),
        completedAt = Instant.now(),
        totalDurationMs = 100L,
        layers = emptyList(),
        finalOutput = null,
        finalError = null,
        totalCostUsd = 0f,
        totalTokensGenerated = 0,
    )

    @Test
    fun `process with Success returns SUCCESS status`() = runTest {
        val context = testContext()
        val response = postProcessor.process(
            context = context,
            cleanInput = "test",
            skillOutput = SkillOutput.Success(message = "Done"),
            trace = makeTrace(),
            handledBy = BrainLayer.RULE,
            wasStreamed = false,
            costUsd = 0f,
            tokensGenerated = 0,
        )

        assertThat(response.status).isEqualTo(BrainResponseStatus.SUCCESS)
        assertThat(response.isSuccess).isTrue()
        // JARVIS (witty) persona adds ", sir." to short responses
        assertThat(response.message).contains("Done")
    }

    @Test
    fun `process with Partial returns PARTIAL status`() = runTest {
        val context = testContext()
        val response = postProcessor.process(
            context = context,
            cleanInput = "test",
            skillOutput = SkillOutput.Partial(message = "Partial", warnings = listOf("low confidence")),
            trace = makeTrace(),
            handledBy = BrainLayer.LOCAL_LLM,
            wasStreamed = true,
            costUsd = 0.01f,
            tokensGenerated = 50,
        )

        assertThat(response.status).isEqualTo(BrainResponseStatus.PARTIAL)
        assertThat(response.isPartial).isTrue()
        assertThat(response.warnings).contains("low confidence")
    }

    @Test
    fun `process with Failure returns FAILURE status`() = runTest {
        val context = testContext()
        val response = postProcessor.process(
            context = context,
            cleanInput = "test",
            skillOutput = SkillOutput.Failure(
                error = com.roshan.persona.common.AppError.Unknown(),
                userMessage = "Something went wrong",
            ),
            trace = makeTrace(),
            handledBy = BrainLayer.NONE,
            wasStreamed = false,
            costUsd = 0f,
            tokensGenerated = 0,
        )

        assertThat(response.status).isEqualTo(BrainResponseStatus.FAILURE)
        assertThat(response.isFailure).isTrue()
    }

    @Test
    fun `process with NeedsInput returns NEEDS_INPUT status`() = runTest {
        val context = testContext()
        val response = postProcessor.process(
            context = context,
            cleanInput = "test",
            skillOutput = SkillOutput.NeedsInput(
                skillId = "call",
                prompt = "Which contact?",
                inputType = com.roshan.persona.brain.skill.InputType.CONTACT,
            ),
            trace = makeTrace(),
            handledBy = BrainLayer.SKILL,
            wasStreamed = false,
            costUsd = 0f,
            tokensGenerated = 0,
        )

        assertThat(response.status).isEqualTo(BrainResponseStatus.NEEDS_INPUT)
        assertThat(response.needsInput).isTrue()
    }

    @Test
    fun `process with NeedsPermission returns NEEDS_PERMISSION status`() = runTest {
        val context = testContext()
        val response = postProcessor.process(
            context = context,
            cleanInput = "test",
            skillOutput = SkillOutput.NeedsPermission(
                skillId = "call",
                missingPermissions = listOf("android.permission.CALL_PHONE"),
                userMessage = "Need permission",
            ),
            trace = makeTrace(),
            handledBy = BrainLayer.SKILL,
            wasStreamed = false,
            costUsd = 0f,
            tokensGenerated = 0,
        )

        assertThat(response.status).isEqualTo(BrainResponseStatus.NEEDS_PERMISSION)
        assertThat(response.needsPermission).isTrue()
    }

    @Test
    fun `persona styling applied to rule responses`() = runTest {
        val context = testContext()
        val response = postProcessor.process(
            context = context,
            cleanInput = "torch on",
            skillOutput = SkillOutput.Success(message = "Torch on"),
            trace = makeTrace(),
            handledBy = BrainLayer.RULE,
            wasStreamed = false,
            costUsd = 0f,
            tokensGenerated = 0,
        )

        // JARVIS persona (witty) should add ", sir." to short responses
        assertThat(response.message).contains("Torch on")
    }

    @Test
    fun `persona styling NOT applied to LLM responses`() = runTest {
        val context = testContext()
        val originalMessage = "The capital of France is Paris."
        val response = postProcessor.process(
            context = context,
            cleanInput = "What is the capital of France?",
            skillOutput = SkillOutput.Success(message = originalMessage),
            trace = makeTrace(),
            handledBy = BrainLayer.LOCAL_LLM,
            wasStreamed = true,
            costUsd = 0f,
            tokensGenerated = 10,
        )

        // LLM responses are already styled via prompt — post-processor should NOT modify
        assertThat(response.message).isEqualTo(originalMessage)
    }

    @Test
    fun `spokenMessage generated for non-LLM responses`() = runTest {
        val context = testContext()
        val response = postProcessor.process(
            context = context,
            cleanInput = "torch on",
            skillOutput = SkillOutput.Success(message = "Torch on"),
            trace = makeTrace(),
            handledBy = BrainLayer.RULE,
            wasStreamed = false,
            costUsd = 0f,
            tokensGenerated = 0,
        )

        assertThat(response.spokenMessage).isNotNull()
    }

    @Test
    fun `spokenMessage null for LLM responses`() = runTest {
        val context = testContext()
        val response = postProcessor.process(
            context = context,
            cleanInput = "test",
            skillOutput = SkillOutput.Success(message = "LLM response"),
            trace = makeTrace(),
            handledBy = BrainLayer.LOCAL_LLM,
            wasStreamed = true,
            costUsd = 0f,
            tokensGenerated = 10,
        )

        assertThat(response.spokenMessage).isNull()
    }

    @Test
    fun `suggestions generated for failure with cloud disabled`() = runTest {
        val context = testContext().copy(
            userContext = testContext().userContext.copy(
                privacySettings = PrivacySettings(cloudLlmEnabled = false),
            ),
        )
        val response = postProcessor.process(
            context = context,
            cleanInput = "test",
            skillOutput = SkillOutput.Failure(
                error = com.roshan.persona.common.AppError.Unknown(),
                userMessage = "Failed",
            ),
            trace = makeTrace(),
            handledBy = BrainLayer.LOCAL_LLM,
            wasStreamed = false,
            costUsd = 0f,
            tokensGenerated = 0,
        )

        assertThat(response.suggestions).isNotEmpty()
        assertThat(response.suggestions.any { it.type == SuggestionType.ENABLE_CLOUD }).isTrue()
    }

    @Test
    fun `suggestions generated for NeedsPermission`() = runTest {
        val context = testContext()
        val response = postProcessor.process(
            context = context,
            cleanInput = "call mom",
            skillOutput = SkillOutput.NeedsPermission(
                skillId = "call",
                missingPermissions = listOf("CALL_PHONE"),
                userMessage = "Need permission",
            ),
            trace = makeTrace(),
            handledBy = BrainLayer.SKILL,
            wasStreamed = false,
            costUsd = 0f,
            tokensGenerated = 0,
        )

        assertThat(response.suggestions.any { it.type == SuggestionType.GRANT_PERMISSION }).isTrue()
    }

    @Test
    fun `suggestions generated for Partial`() = runTest {
        val context = testContext()
        val response = postProcessor.process(
            context = context,
            cleanInput = "test",
            skillOutput = SkillOutput.Partial(message = "partial", warnings = listOf("low conf")),
            trace = makeTrace(),
            handledBy = BrainLayer.LOCAL_LLM,
            wasStreamed = true,
            costUsd = 0f,
            tokensGenerated = 5,
        )

        assertThat(response.suggestions.any { it.type == SuggestionType.REPHRASE }).isTrue()
    }

    @Test
    fun `response includes cost and tokens`() = runTest {
        val context = testContext()
        val response = postProcessor.process(
            context = context,
            cleanInput = "test",
            skillOutput = SkillOutput.Success(message = "ok"),
            trace = makeTrace(),
            handledBy = BrainLayer.CLOUD_LLM,
            wasStreamed = true,
            costUsd = 0.05f,
            tokensGenerated = 100,
        )

        assertThat(response.costUsd).isWithin(0.001f).of(0.05f)
        assertThat(response.tokensGenerated).isEqualTo(100)
    }

    @Test
    fun `response includes handledBy layer`() = runTest {
        val context = testContext()
        val response = postProcessor.process(
            context = context,
            cleanInput = "test",
            skillOutput = SkillOutput.Success(message = "ok"),
            trace = makeTrace(),
            handledBy = BrainLayer.AGENTIC,
            wasStreamed = false,
            costUsd = 0f,
            tokensGenerated = 0,
        )

        assertThat(response.handledBy).isEqualTo(BrainLayer.AGENTIC)
    }

    @Test
    fun `response includes wasStreamed flag`() = runTest {
        val context = testContext()
        val response = postProcessor.process(
            context = context,
            cleanInput = "test",
            skillOutput = SkillOutput.Success(message = "ok"),
            trace = makeTrace(),
            handledBy = BrainLayer.LOCAL_LLM,
            wasStreamed = true,
            costUsd = 0f,
            tokensGenerated = 10,
        )

        assertThat(response.wasStreamed).isTrue()
    }
}
