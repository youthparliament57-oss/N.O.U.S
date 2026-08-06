// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.brain.context

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.intent.Intent
import com.roshan.persona.brain.persona.Persona
import com.roshan.persona.brain.persona.ResponseStyle
import com.roshan.persona.brain.persona.Tone
import com.roshan.persona.brain.persona.Verbosity
import com.roshan.persona.brain.persona.Vocabulary
import com.roshan.persona.common.CorrelationId
import org.junit.Test
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

// AUTO_FIX_0099: [feature] BrainContextTest verified

class BrainContextTest {

    private fun testPersona() = Persona(
        id = "jarvis",
        displayName = "JARVIS",
        systemPromptTemplate = "You are JARVIS.",
        skillPreferences = emptyMap(),
        responseStyle = ResponseStyle(
            tone = Tone.WITTY,
            verbosity = Verbosity.BALANCED,
            useEmoji = false,
            vocabulary = Vocabulary.TECHNICAL,
        ),
    )

    private fun testUserContext() = UserContext(
        userId = "test-user-id",
        displayName = "Roshan",
        preferredLanguage = Locale.ENGLISH,
        preferredResponseLength = ResponseLength.BALANCED,
        skillPreferences = emptyMap(),
        privacySettings = PrivacySettings(),
        monthlyLlmBudgetUsd = 5.0f,
        monthlyLlmSpentUsd = 0.5f,
    )

    private fun testDeviceContext() = DeviceContext(
        thermalStatus = ThermalStatus.NONE,
        availableRamMb = 4096,
        batteryLevel = 80,
        isCharging = true,
        isOnline = true,
        networkType = NetworkType.WIFI,
        gpuAvailable = true,
        gpuVramMb = 2048,
    )

    private fun testContext(
        pendingExpectation: Expectation? = null,
        budget: BrainBudget = BrainBudget.DEFAULT,
    ) = BrainContext(
        correlationId = CorrelationId.generate(),
        sessionId = SessionId.generate(),
        timestamp = Instant.now(),
        timezone = TimeZone.getTimeZone("Asia/Kolkata"),
        activePersona = testPersona(),
        userContext = testUserContext(),
        deviceContext = testDeviceContext(),
        conversationHistory = emptyList(),
        pendingExpectation = pendingExpectation,
        ambientContext = null,
        budget = budget,
    )

    @Test
    fun `BrainContext is immutable and copyable`() {
        val original = testContext()
        val modified = original.copy(
            budget = BrainBudget.LOCAL_ONLY,
        )

        assertThat(original.budget.allowCloud).isTrue()
        assertThat(modified.budget.allowCloud).isFalse()
        assertThat(original.correlationId).isEqualTo(modified.correlationId)
    }

    @Test
    fun `SessionId generates unique values`() {
        val id1 = SessionId.generate()
        val id2 = SessionId.generate()

        assertThat(id1.value).isNotEqualTo(id2.value)
        assertThat(id1.toString()).isEqualTo(id1.value)
    }

    @Test
    fun `CorrelationId in BrainContext is preserved`() {
        val cid = CorrelationId.generate()
        val context = testContext().copy(correlationId = cid)

        assertThat(context.correlationId).isEqualTo(cid)
    }

    @Test
    fun `BrainBudget DEFAULT allows cloud and agentic`() {
        val budget = BrainBudget.DEFAULT

        assertThat(budget.allowCloud).isTrue()
        assertThat(budget.allowAgentic).isTrue()
        assertThat(budget.maxLatencyMs).isEqualTo(5_000L)
        assertThat(budget.maxCostUsd).isEqualTo(0.05f)
        assertThat(budget.maxLlmTokens).isEqualTo(1_024)
    }

    @Test
    fun `BrainBudget LOCAL_ONLY disables cloud`() {
        val budget = BrainBudget.LOCAL_ONLY

        assertThat(budget.allowCloud).isFalse()
        assertThat(budget.allowAgentic).isTrue()  // agentic still allowed with local LLM
    }

    @Test
    fun `BrainBudget STRICT is conservative`() {
        val budget = BrainBudget.STRICT

        assertThat(budget.maxLatencyMs).isLessThan(BrainBudget.DEFAULT.maxLatencyMs)
        assertThat(budget.maxCostUsd).isLessThan(BrainBudget.DEFAULT.maxCostUsd)
        assertThat(budget.maxLlmTokens).isLessThan(BrainBudget.DEFAULT.maxLlmTokens)
        assertThat(budget.allowAgentic).isFalse()
    }

    @Test
    fun `BrainBudget GENEROUS is permissive`() {
        val budget = BrainBudget.GENEROUS

        assertThat(budget.maxLatencyMs).isGreaterThan(BrainBudget.DEFAULT.maxLatencyMs)
        assertThat(budget.maxCostUsd).isGreaterThan(BrainBudget.DEFAULT.maxCostUsd)
        assertThat(budget.maxLlmTokens).isGreaterThan(BrainBudget.DEFAULT.maxLlmTokens)
    }

    @Test
    fun `Expectation AwaitingContact preserves original intent`() {
        val originalIntent = Intent.Call(contact = null)
        val expectation = Expectation.AwaitingContact(originalIntent)

        assertThat(expectation.originalIntent).isEqualTo(originalIntent)
    }

    @Test
    fun `PendingAction carries intent + skillId`() {
        val intent = Intent.Call(contact = "Mom")
        val pending = PendingAction(
            description = "Call Mom",
            intent = intent,
            skillId = "call",
        )

        assertThat(pending.intent).isInstanceOf(Intent.Call::class.java)
        assertThat(pending.skillId).isEqualTo("call")
    }
}
