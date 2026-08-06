// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.brain.rule.registry

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.intent.Intent
import com.roshan.persona.brain.rule.BrainRule
import com.roshan.persona.brain.rule.RuleAction
import com.roshan.persona.brain.rule.RuleCondition
import org.junit.Test

// AUTO_FIX_0079: [feature] RuleRegistryTest verified

class RuleRegistryTest {

    private fun createRule(
        id: String,
        priority: Int = 100,
        tags: Set<String> = emptySet(),
        intentType: kotlin.reflect.KClass<out Intent>? = null,
        enabled: Boolean = true,
    ): BrainRule = object : BrainRule {
        override val id = id
        override val priority = priority
        override val condition = if (intentType != null) {
            RuleCondition.IntentIs(intentType)
        } else {
            RuleCondition.Always
        }
        override val action = RuleAction.Respond("test")
        override val tags = tags
        override val isEnabled = enabled
        override val description = "Test rule $id"
    }

    @Test
    fun `empty registry returns empty list`() {
        val registry = RuleRegistry(emptySet())
        assertThat(registry.getActiveRules()).isEmpty()
        assertThat(registry.count()).isEqualTo(0)
    }

    @Test
    fun `rules sorted by priority ascending`() {
        val rule1 = createRule("rule_100", priority = 100)
        val rule2 = createRule("rule_10", priority = 10)
        val rule3 = createRule("rule_50", priority = 50)
        val registry = RuleRegistry(setOf(rule1, rule2, rule3))

        val rules = registry.getActiveRules()
        assertThat(rules.map { it.id }).containsExactly("rule_10", "rule_50", "rule_100").inOrder()
    }

    @Test
    fun `disabled rules filtered out`() {
        val enabledRule = createRule("enabled", enabled = true)
        val disabledRule = createRule("disabled", enabled = false)
        val registry = RuleRegistry(setOf(enabledRule, disabledRule))

        val rules = registry.getActiveRules()
        assertThat(rules).hasSize(1)
        assertThat(rules.first().id).isEqualTo("enabled")
    }

    @Test
    fun `findById returns matching rule`() {
        val rule = createRule("find_me")
        val registry = RuleRegistry(setOf(rule))

        assertThat(registry.findById("find_me")).isNotNull()
        assertThat(registry.findById("nonexistent")).isNull()
    }

    @Test
    fun `findByTag returns matching rules`() {
        val rule1 = createRule("rule1", tags = setOf("system"))
        val rule2 = createRule("rule2", tags = setOf("system", "torch"))
        val rule3 = createRule("rule3", tags = setOf("communication"))
        val registry = RuleRegistry(setOf(rule1, rule2, rule3))

        val systemRules = registry.findByTag("system")
        assertThat(systemRules).hasSize(2)
        assertThat(systemRules.map { it.id }).containsExactly("rule1", "rule2")
    }

    @Test
    fun `findByIntentType returns rules with IntentIs condition matching`() {
        val rule1 = createRule("rule1", intentType = Intent.TorchOn::class)
        val rule2 = createRule("rule2", intentType = Intent.Call::class)
        val rule3 = createRule("rule3")  // Always condition
        val registry = RuleRegistry(setOf(rule1, rule2, rule3))

        val torchRules = registry.findByIntentType(Intent.TorchOn::class)
        assertThat(torchRules).hasSize(1)
        assertThat(torchRules.first().id).isEqualTo("rule1")
    }

    @Test
    fun `addUserRule adds rule to registry`() {
        val registry = RuleRegistry(emptySet())
        val userRule = createRule("user_rule_1")

        registry.addUserRule(userRule)

        assertThat(registry.count()).isEqualTo(1)
        assertThat(registry.findById("user_rule_1")).isNotNull()
    }

    @Test
    fun `addUserRule with duplicate id replaces existing`() {
        val registry = RuleRegistry(emptySet())
        val rule1 = createRule("dup", priority = 100)
        val rule2 = createRule("dup", priority = 50)

        registry.addUserRule(rule1)
        registry.addUserRule(rule2)

        assertThat(registry.count()).isEqualTo(1)
        assertThat(registry.findById("dup")?.priority).isEqualTo(50)
    }

    @Test
    fun `removeUserRule removes rule`() {
        val registry = RuleRegistry(emptySet())
        val userRule = createRule("user_rule_1")
        registry.addUserRule(userRule)

        val removed = registry.removeUserRule("user_rule_1")

        assertThat(removed).isTrue()
        assertThat(registry.count()).isEqualTo(0)
    }

    @Test
    fun `removeUserRule returns false for nonexistent rule`() {
        val registry = RuleRegistry(emptySet())

        val removed = registry.removeUserRule("nonexistent")

        assertThat(removed).isFalse()
    }

    @Test
    fun `refresh invalidates cache`() {
        val rule1 = createRule("rule1")
        val registry = RuleRegistry(setOf(rule1))

        // First call populates cache
        val rules1 = registry.getActiveRules()
        assertThat(rules1).hasSize(1)

        // Add user rule (invalidates cache internally)
        registry.addUserRule(createRule("user_rule"))

        // Second call should see updated list
        val rules2 = registry.getActiveRules()
        assertThat(rules2).hasSize(2)

        // Manual refresh also works
        registry.refresh()
        val rules3 = registry.getActiveRules()
        assertThat(rules3).hasSize(2)
    }

    @Test
    fun `user rules mixed with compiled rules sorted together`() {
        val compiledRule = createRule("compiled", priority = 50)
        val registry = RuleRegistry(setOf(compiledRule))

        registry.addUserRule(createRule("user_high", priority = 10))
        registry.addUserRule(createRule("user_low", priority = 100))

        val rules = registry.getActiveRules()
        assertThat(rules.map { it.id }).containsExactly("user_high", "compiled", "user_low").inOrder()
    }
}
