// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.brain.rule

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.context.BrainContext
import com.roshan.persona.brain.intent.Intent
import com.roshan.persona.brain.skill.SystemOperation
import org.junit.Test

// AUTO_FIX_0076: [feature] BrainRuleTest verified

class BrainRuleTest {

    private val testRule = object : BrainRule {
        override val id = "test_rule"
        override val priority = 50
        override val condition = RuleCondition.IntentIs(Intent.TorchOn::class)
        override val action = RuleAction.SystemCall(SystemOperation.SetTorch(on = true))
        override val tags = setOf("system", "test")
        override val description = "Test rule"
    }

    @Test
    fun `BrainRule has required fields`() {
        assertThat(testRule.id).isEqualTo("test_rule")
        assertThat(testRule.priority).isEqualTo(50)
        assertThat(testRule.tags).contains("system")
        assertThat(testRule.description).isEqualTo("Test rule")
        assertThat(testRule.isEnabled).isTrue()
    }

    @Test
    fun `RuleCondition IntentIs holds intent type`() {
        val condition = RuleCondition.IntentIs(Intent.TorchOn::class)
        assertThat(condition).isInstanceOf(RuleCondition.IntentIs::class.java)
        assertThat((condition as RuleCondition.IntentIs).intent).isEqualTo(Intent.TorchOn::class)
    }

    @Test
    fun `RuleCondition And composes multiple conditions`() {
        val condition = RuleCondition.And(
            listOf(
                RuleCondition.IntentIs(Intent.TorchOn::class),
                RuleCondition.Always,
            ),
        )
        assertThat(condition).isInstanceOf(RuleCondition.And::class.java)
        assertThat((condition as RuleCondition.And).conditions).hasSize(2)
    }

    @Test
    fun `RuleCondition Or composes multiple conditions`() {
        val condition = RuleCondition.Or(
            listOf(
                RuleCondition.IntentIs(Intent.TorchOn::class),
                RuleCondition.IntentIs(Intent.TorchOff::class),
            ),
        )
        assertThat((condition as RuleCondition.Or).conditions).hasSize(2)
    }

    @Test
    fun `RuleCondition Not negates condition`() {
        val condition = RuleCondition.Not(RuleCondition.Always)
        assertThat(condition).isInstanceOf(RuleCondition.Not::class.java)
    }

    @Test
    fun `RuleCondition Always is a singleton`() {
        val a = RuleCondition.Always
        val b = RuleCondition.Always
        assertThat(a).isSameInstanceAs(b)
    }

    @Test
    fun `RuleAction SystemCall holds operation`() {
        val action = RuleAction.SystemCall(SystemOperation.SetTorch(on = true))
        assertThat(action).isInstanceOf(RuleAction.SystemCall::class.java)
        val op = (action as RuleAction.SystemCall).operation
        assertThat(op).isInstanceOf(SystemOperation.SetTorch::class.java)
        assertThat((op as SystemOperation.SetTorch).on).isTrue()
    }

    @Test
    fun `RuleAction SkillInvoke holds skillId and params`() {
        val action = RuleAction.SkillInvoke(
            skillId = "call",
            params = mapOf("contact" to "mom"),
        )
        assertThat(action).isInstanceOf(RuleAction.SkillInvoke::class.java)
        val skillAction = action as RuleAction.SkillInvoke
        assertThat(skillAction.skillId).isEqualTo("call")
        assertThat(skillAction.params).containsEntry("contact", "mom")
    }

    @Test
    fun `RuleAction Composite holds multiple actions`() {
        val action = RuleAction.Composite(
            listOf(
                RuleAction.SystemCall(SystemOperation.SetTorch(on = true)),
                RuleAction.SystemCall(SystemOperation.AdjustVolume(delta = 1)),
            ),
        )
        assertThat(action).isInstanceOf(RuleAction.Composite::class.java)
        assertThat((action as RuleAction.Composite).actions).hasSize(2)
    }

    @Test
    fun `RuleAction DeferToLayer holds layer number`() {
        val action = RuleAction.DeferToLayer(layer = 3)
        assertThat(action).isInstanceOf(RuleAction.DeferToLayer::class.java)
        assertThat((action as RuleAction.DeferToLayer).layer).isEqualTo(3)
    }

    @Test
    fun `RuleAction Respond holds message`() {
        val action = RuleAction.Respond(message = "Done!")
        assertThat(action).isInstanceOf(RuleAction.Respond::class.java)
        assertThat((action as RuleAction.Respond).message).isEqualTo("Done!")
    }
}
