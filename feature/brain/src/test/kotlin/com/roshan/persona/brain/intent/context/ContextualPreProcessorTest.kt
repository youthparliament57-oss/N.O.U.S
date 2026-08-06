// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.brain.intent.context

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.context.BrainContext
import com.roshan.persona.brain.context.Expectation
import com.roshan.persona.brain.context.SessionId
import com.roshan.persona.brain.context.Turn
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

// AUTO_FIX_0070: [feature] ContextualPreProcessorTest verified

class ContextualPreProcessorTest {

    private val preProcessor = ContextualPreProcessor()

    private fun testContext(
        pendingExpectation: Expectation? = null,
        lastTurn: String? = "Open my calendar",
    ): BrainContext {
        val history = if (lastTurn != null) {
            listOf(
                Turn(
                    correlationId = CorrelationId.generate(),
                    userText = lastTurn,
                    brainResponse = "Calendar opened",
                    timestamp = Instant.now(),
                    intentLabel = "OpenApp",
                    layerLabel = "L2",
                ),
            )
        } else {
            emptyList()
        }

        return BrainContext(
            correlationId = CorrelationId.generate(),
            sessionId = SessionId.generate(),
            timestamp = Instant.now(),
            timezone = TimeZone.getTimeZone("Asia/Kolkata"),
            activePersona = Persona(
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
            ),
            userContext = com.roshan.persona.brain.context.UserContext(
                userId = "test-user",
                displayName = "Test",
                preferredLanguage = Locale.ENGLISH,
                preferredResponseLength = com.roshan.persona.brain.context.ResponseLength.BALANCED,
                skillPreferences = emptyMap(),
                privacySettings = com.roshan.persona.brain.context.PrivacySettings(),
                monthlyLlmBudgetUsd = 5.0f,
                monthlyLlmSpentUsd = 0f,
            ),
            deviceContext = com.roshan.persona.brain.context.DeviceContext(
                thermalStatus = com.roshan.persona.brain.context.ThermalStatus.NONE,
                availableRamMb = 4096,
                batteryLevel = 80,
                isCharging = true,
                isOnline = true,
                networkType = com.roshan.persona.brain.context.NetworkType.WIFI,
                gpuAvailable = true,
                gpuVramMb = 2048,
            ),
            conversationHistory = history,
            pendingExpectation = pendingExpectation,
            ambientContext = null,
            budget = com.roshan.persona.brain.context.BrainBudget.DEFAULT,
        )
    }

    @Test
    fun `no expectation returns input unchanged`() {
        val context = testContext(pendingExpectation = null)
        val result = preProcessor.applyContext("torch on", context)
        assertThat(result).isEqualTo("torch on")
    }

    @Test
    fun `no history returns input unchanged even with expectation`() {
        val context = testContext(
            pendingExpectation = Expectation.AwaitingChoice(emptyList()),
            lastTurn = null,
        )
        val result = preProcessor.applyContext("the 3 PM one", context)
        assertThat(result).isEqualTo("the 3 PM one")
    }

    @Test
    fun `AwaitingContact appends previous turn`() {
        val context = testContext(
            pendingExpectation = Expectation.AwaitingContact(Intent.Call(contact = null)),
        )
        val result = preProcessor.applyContext("mom", context)
        assertThat(result).isEqualTo("Open my calendar. mom")
    }

    @Test
    fun `AwaitingChoice appends previous turn`() {
        val context = testContext(
            pendingExpectation = Expectation.AwaitingChoice(listOf("3 PM", "4 PM")),
        )
        val result = preProcessor.applyContext("the 3 PM one", context)
        assertThat(result).isEqualTo("Open my calendar. the 3 PM one")
    }

    @Test
    fun `AwaitingConfirmation appends previous turn`() {
        val context = testContext(
            pendingExpectation = Expectation.AwaitingConfirmation(
                com.roshan.persona.brain.context.PendingAction(
                    description = "Call Mom",
                    intent = Intent.Call(contact = "Mom"),
                    skillId = "call",
                ),
            ),
        )
        val result = preProcessor.applyContext("yes", context)
        assertThat(result).isEqualTo("Open my calendar. yes")
    }

    @Test
    fun `looksLikeContinuation returns true for short non-verb input`() {
        assertThat(preProcessor.looksLikeContinuation("mom")).isTrue()
        assertThat(preProcessor.looksLikeContinuation("the 3 PM one")).isTrue()
        assertThat(preProcessor.looksLikeContinuation("yes")).isFalse()  // yes is in standalone set
        assertThat(preProcessor.looksLikeContinuation("call mom")).isFalse()
    }

    @Test
    fun `looksLikeContinuation returns false for standalone verbs`() {
        assertThat(preProcessor.looksLikeContinuation("call mom")).isFalse()
        assertThat(preProcessor.looksLikeContinuation("send sms")).isFalse()
        assertThat(preProcessor.looksLikeContinuation("torch on")).isFalse()
    }

    @Test
    fun `looksLikeContinuation returns false for long input`() {
        val longInput = "this is a very long input that goes beyond five tokens"
        assertThat(preProcessor.looksLikeContinuation(longInput)).isFalse()
    }
}
