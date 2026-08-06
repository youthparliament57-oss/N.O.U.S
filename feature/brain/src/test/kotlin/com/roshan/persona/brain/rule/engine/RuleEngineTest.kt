// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.brain.rule.engine

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.context.BrainContext
import com.roshan.persona.brain.intent.Intent
import com.roshan.persona.brain.rule.BrainRule
import com.roshan.persona.brain.rule.RuleAction
import com.roshan.persona.brain.rule.RuleCondition
import com.roshan.persona.brain.rule.registry.RuleRegistry
import com.roshan.persona.brain.skill.SystemOperation
import com.roshan.persona.common.CorrelationId
import org.junit.Test

// AUTO_FIX_0078: [feature] RuleEngineTest verified

class RuleEngineTest {

    private fun createTestRule(
        id: String,
        priority: Int = 100,
        condition: RuleCondition = RuleCondition.Always,
        action: RuleAction = RuleAction.Respond("test"),
        enabled: Boolean = true,
    ): BrainRule = object : BrainRule {
        override val id = id
        override val priority = priority
        override val condition = condition
        override val action = action
        override val tags = setOf("test")
        override val isEnabled = enabled
        override val description = "Test rule $id"
    }

    private fun createRegistry(vararg rules: BrainRule): RuleRegistry {
        return RuleRegistry(compiledRules = rules.toSet())
    }

    private fun createTestContext(): BrainContext {
        return BrainContext(
            correlationId = CorrelationId.generate(),
            sessionId = com.roshan.persona.brain.context.SessionId("test-session"),
            timestamp = java.time.Instant.now(),
            timezone = java.util.TimeZone.getTimeZone("UTC"),
            activePersona = com.roshan.persona.brain.persona.Persona(
                id = "test",
                displayName = "Test",
                systemPromptTemplate = "Test",
                skillPreferences = emptyMap(),
                responseStyle = com.roshan.persona.brain.persona.ResponseStyle(
                    tone = com.roshan.persona.brain.persona.Tone.WITTY,
                    verbosity = com.roshan.persona.brain.persona.Verbosity.BALANCED,
                    useEmoji = false,
                    vocabulary = com.roshan.persona.brain.persona.Vocabulary.SIMPLE,
                ),
            ),
            userContext = com.roshan.persona.brain.context.UserContext(
                userId = "test",
                displayName = null,
                preferredLanguage = java.util.Locale.ENGLISH,
                preferredResponseLength = com.roshan.persona.brain.context.ResponseLength.BALANCED,
                skillPreferences = emptyMap(),
                privacySettings = com.roshan.persona.brain.context.PrivacySettings(),
                monthlyLlmBudgetUsd = 5f,
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
            conversationHistory = emptyList(),
            pendingExpectation = null,
            ambientContext = null,
            budget = com.roshan.persona.brain.context.BrainBudget.DEFAULT,
        )
    }

    @Test
    fun `no rules returns NoMatch`() {
        val registry = createRegistry()
        val engine = RuleEngine(registry)
        val context = createTestContext()

        val result = engine.evaluate(Intent.TorchOn, context)

        assertThat(result).isInstanceOf(RuleEngineResult.NoMatch::class.java)
        assertThat((result as RuleEngineResult.NoMatch).rulesChecked).isEqualTo(0)
    }

    @Test
    fun `matching rule returns Matched`() {
        val rule = createTestRule(
            id = "torch_on",
            condition = RuleCondition.IntentIs(Intent.TorchOn::class),
            action = RuleAction.SystemCall(SystemOperation.SetTorch(on = true)),
        )
        val registry = createRegistry(rule)
        val engine = RuleEngine(registry)
        val context = createTestContext()

        val result = engine.evaluate(Intent.TorchOn, context)

        assertThat(result).isInstanceOf(RuleEngineResult.Matched::class.java)
        val matched = result as RuleEngineResult.Matched
        assertThat(matched.rule.id).isEqualTo("torch_on")
        assertThat(matched.action).isInstanceOf(RuleAction.SystemCall::class.java)
    }

    @Test
    fun `non-matching rule returns NoMatch`() {
        val rule = createTestRule(
            id = "torch_on",
            condition = RuleCondition.IntentIs(Intent.TorchOn::class),
        )
        val registry = createRegistry(rule)
        val engine = RuleEngine(registry)
        val context = createTestContext()

        val result = engine.evaluate(Intent.TorchOff, context)

        assertThat(result).isInstanceOf(RuleEngineResult.NoMatch::class.java)
    }

    @Test
    fun `lower priority number fires first`() {
        val lowPriorityRule = createTestRule(
            id = "low_priority",
            priority = 100,
            condition = RuleCondition.Always,
            action = RuleAction.Respond("low"),
        )
        val highPriorityRule = createTestRule(
            id = "high_priority",
            priority = 10,
            condition = RuleCondition.Always,
            action = RuleAction.Respond("high"),
        )
        val registry = createRegistry(lowPriorityRule, highPriorityRule)
        val engine = RuleEngine(registry)
        val context = createTestContext()

        val result = engine.evaluate(Intent.TorchOn, context)

        assertThat(result).isInstanceOf(RuleEngineResult.Matched::class.java)
        val matched = result as RuleEngineResult.Matched
        assertThat(matched.rule.id).isEqualTo("high_priority")
    }

    @Test
    fun `disabled rules are skipped`() {
        val rule = createTestRule(
            id = "disabled_rule",
            condition = RuleCondition.Always,
            enabled = false,
        )
        val registry = createRegistry(rule)
        val engine = RuleEngine(registry)
        val context = createTestContext()

        val result = engine.evaluate(Intent.TorchOn, context)

        assertThat(result).isInstanceOf(RuleEngineResult.NoMatch::class.java)
    }

    @Test
    fun `Always condition matches any intent`() {
        val rule = createTestRule(
            id = "catch_all",
            condition = RuleCondition.Always,
        )
        val registry = createRegistry(rule)
        val engine = RuleEngine(registry)
        val context = createTestContext()

        val result = engine.evaluate(Intent.Mute, context)

        assertThat(result).isInstanceOf(RuleEngineResult.Matched::class.java)
    }

    @Test
    fun `And condition requires all sub-conditions`() {
        val rule = createTestRule(
            id = "and_rule",
            condition = RuleCondition.And(
                listOf(
                    RuleCondition.IntentIs(Intent.TorchOn::class),
                    RuleCondition.Always,
                ),
            ),
        )
        val registry = createRegistry(rule)
        val engine = RuleEngine(registry)
        val context = createTestContext()

        val matchResult = engine.evaluate(Intent.TorchOn, context)
        assertThat(matchResult).isInstanceOf(RuleEngineResult.Matched::class.java)

        val noMatchResult = engine.evaluate(Intent.TorchOff, context)
        assertThat(noMatchResult).isInstanceOf(RuleEngineResult.NoMatch::class.java)
    }

    @Test
    fun `Or condition requires any sub-condition`() {
        val rule = createTestRule(
            id = "or_rule",
            condition = RuleCondition.Or(
                listOf(
                    RuleCondition.IntentIs(Intent.TorchOn::class),
                    RuleCondition.IntentIs(Intent.TorchOff::class),
                ),
            ),
        )
        val registry = createRegistry(rule)
        val engine = RuleEngine(registry)
        val context = createTestContext()

        val onResult = engine.evaluate(Intent.TorchOn, context)
        assertThat(onResult).isInstanceOf(RuleEngineResult.Matched::class.java)

        val offResult = engine.evaluate(Intent.TorchOff, context)
        assertThat(offResult).isInstanceOf(RuleEngineResult.Matched::class.java)

        val muteResult = engine.evaluate(Intent.Mute, context)
        assertThat(muteResult).isInstanceOf(RuleEngineResult.NoMatch::class.java)
    }

    @Test
    fun `Not condition negates sub-condition`() {
        val rule = createTestRule(
            id = "not_rule",
            condition = RuleCondition.Not(
                RuleCondition.IntentIs(Intent.TorchOn::class),
            ),
        )
        val registry = createRegistry(rule)
        val engine = RuleEngine(registry)
        val context = createTestContext()

        val onResult = engine.evaluate(Intent.TorchOn, context)
        assertThat(onResult).isInstanceOf(RuleEngineResult.NoMatch::class.java)

        val offResult = engine.evaluate(Intent.TorchOff, context)
        assertThat(offResult).isInstanceOf(RuleEngineResult.Matched::class.java)
    }

    @Test
    fun `IntentMatches predicate evaluates correctly`() {
        val rule = createTestRule(
            id = "volume_up_predicate",
            condition = RuleCondition.IntentMatches { it is Intent.VolumeUp },
        )
        val registry = createRegistry(rule)
        val engine = RuleEngine(registry)
        val context = createTestContext()

        val result = engine.evaluate(Intent.VolumeUp(amount = 5), context)
        assertThat(result).isInstanceOf(RuleEngineResult.Matched::class.java)
    }

    @Test
    fun `Context condition evaluates brain context`() {
        val rule = createTestRule(
            id = "low_battery_rule",
            condition = RuleCondition.Context { it.deviceContext.batteryLevel < 20 },
        )
        val registry = createRegistry(rule)
        val engine = RuleEngine(registry)
        val lowBatteryContext = createTestContext().copy(
            deviceContext = createTestContext().deviceContext.copy(batteryLevel = 15),
        )
        val normalContext = createTestContext()

        val lowResult = engine.evaluate(Intent.TorchOn, lowBatteryContext)
        assertThat(lowResult).isInstanceOf(RuleEngineResult.Matched::class.java)

        val normalResult = engine.evaluate(Intent.TorchOn, normalContext)
        assertThat(normalResult).isInstanceOf(RuleEngineResult.NoMatch::class.java)
    }

    @Test
    fun `first matching rule wins when multiple match`() {
        val rule1 = createTestRule(
            id = "first",
            priority = 10,
            condition = RuleCondition.Always,
            action = RuleAction.Respond("first"),
        )
        val rule2 = createTestRule(
            id = "second",
            priority = 20,
            condition = RuleCondition.Always,
            action = RuleAction.Respond("second"),
        )
        val registry = createRegistry(rule1, rule2)
        val engine = RuleEngine(registry)
        val context = createTestContext()

        val result = engine.evaluate(Intent.TorchOn, context)

        assertThat(result).isInstanceOf(RuleEngineResult.Matched::class.java)
        assertThat((result as RuleEngineResult.Matched).rule.id).isEqualTo("first")
    }

    @Test
    fun `errored rule is skipped without crashing`() {
        val rule = createTestRule(
            id = "error_rule",
            priority = 10,
            condition = RuleCondition.IntentMatches { throw RuntimeException("test error") },
        )
        val fallbackRule = createTestRule(
            id = "fallback",
            priority = 20,
            condition = RuleCondition.Always,
        )
        val registry = createRegistry(rule, fallbackRule)
        val engine = RuleEngine(registry)
        val context = createTestContext()

        val result = engine.evaluate(Intent.TorchOn, context)

        assertThat(result).isInstanceOf(RuleEngineResult.Matched::class.java)
        assertThat((result as RuleEngineResult.Matched).rule.id).isEqualTo("fallback")
    }

    @Test
    fun `evaluation completes within budget`() {
        val rules = (1..100).map { i ->
            createTestRule(
                id = "rule_$i",
                priority = i,
                condition = RuleCondition.IntentIs(Intent.TorchOn::class),
            )
        }
        val registry = createRegistry(*rules.toTypedArray())
        val engine = RuleEngine(registry)
        val context = createTestContext()

        val startNanos = System.nanoTime()
        val result = engine.evaluate(Intent.Mute, context)
        val durationMs = (System.nanoTime() - startNanos) / 1_000_000

        assertThat(result).isInstanceOf(RuleEngineResult.NoMatch::class.java)
        assertThat(durationMs).isLessThan(50L)
    }
}
