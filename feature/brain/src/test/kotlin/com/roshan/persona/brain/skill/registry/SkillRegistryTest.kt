// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.brain.skill.registry

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.intent.Intent
import com.roshan.persona.brain.skill.BrainSkill
import com.roshan.persona.brain.skill.SkillCapability
import com.roshan.persona.brain.skill.SkillContext
import com.roshan.persona.brain.skill.SkillOutput
import com.roshan.persona.common.CorrelationId
import com.roshan.persona.common.Result
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.reflect.KClass

// AUTO_FIX_0094: [feature] SkillRegistryTest verified

class SkillRegistryTest {

    private fun createSkill(
        id: String,
        supportedIntents: Set<KClass<out Intent>> = emptySet(),
        permissions: Set<String> = emptySet(),
    ): BrainSkill = object : BrainSkill {
        override val id = id
        override val displayName = "Test Skill $id"
        override val description = "Test"
        override val capabilities = setOf(SkillCapability("test", "test"))
        override val requiredPermissions = permissions
        override val supportedIntents = supportedIntents
        override suspend fun execute(intent: Intent, context: SkillContext): Result<SkillOutput> =
            Result.Success(SkillOutput.Success("test"), CorrelationId.generate(), 0)
    }

    @Test
    fun `empty registry returns empty list`() {
        val registry = SkillRegistry(emptySet())
        assertThat(registry.getAllSkills()).isEmpty()
        assertThat(registry.count()).isEqualTo(0)
    }

    @Test
    fun `compiled skills are accessible`() {
        val skill1 = createSkill("skill1")
        val skill2 = createSkill("skill2")
        val registry = SkillRegistry(setOf(skill1, skill2))

        assertThat(registry.getAllSkills()).hasSize(2)
        assertThat(registry.count()).isEqualTo(2)
    }

    @Test
    fun `findSkillsForIntent returns matching skills`() {
        val torchSkill = createSkill("torch", supportedIntents = setOf(Intent.TorchOn::class))
        val callSkill = createSkill("call", supportedIntents = setOf(Intent.Call::class))
        val registry = SkillRegistry(setOf(torchSkill, callSkill))

        val torchSkills = registry.findSkillsForIntent(Intent.TorchOn::class)
        assertThat(torchSkills).hasSize(1)
        assertThat(torchSkills.first().id).isEqualTo("torch")
    }

    @Test
    fun `findById returns matching skill`() {
        val skill = createSkill("find_me")
        val registry = SkillRegistry(setOf(skill))

        assertThat(registry.findById("find_me")).isNotNull()
        assertThat(registry.findById("nonexistent")).isNull()
    }

    @Test
    fun `findSkillsRequiringPermission returns matching skills`() {
        val skillWithPerm = createSkill("call", permissions = setOf("android.permission.CALL_PHONE"))
        val skillNoPerm = createSkill("torch")
        val registry = SkillRegistry(setOf(skillWithPerm, skillNoPerm))

        val permSkills = registry.findSkillsRequiringPermission("android.permission.CALL_PHONE")
        assertThat(permSkills).hasSize(1)
        assertThat(permSkills.first().id).isEqualTo("call")
    }

    @Test
    fun `addUserSkill adds skill to registry`() {
        val registry = SkillRegistry(emptySet())
        val userSkill = createSkill("user_skill_1")

        registry.addUserSkill(userSkill)

        assertThat(registry.count()).isEqualTo(1)
        assertThat(registry.findById("user_skill_1")).isNotNull()
    }

    @Test
    fun `addUserSkill with duplicate id replaces existing`() {
        val registry = SkillRegistry(emptySet())
        val skill1 = createSkill("dup", supportedIntents = setOf(Intent.TorchOn::class))
        val skill2 = createSkill("dup", supportedIntents = setOf(Intent.Call::class))

        registry.addUserSkill(skill1)
        registry.addUserSkill(skill2)

        assertThat(registry.count()).isEqualTo(1)
        val found = registry.findById("dup")
        assertThat(found?.supportedIntents).contains(Intent.Call::class)
    }

    @Test
    fun `removeUserSkill removes skill`() {
        val registry = SkillRegistry(emptySet())
        val userSkill = createSkill("user_skill_1")
        registry.addUserSkill(userSkill)

        val removed = registry.removeUserSkill("user_skill_1")

        assertThat(removed).isTrue()
        assertThat(registry.count()).isEqualTo(0)
    }

    @Test
    fun `removeUserSkill returns false for nonexistent`() {
        val registry = SkillRegistry(emptySet())
        assertThat(registry.removeUserSkill("nonexistent")).isFalse()
    }

    @Test
    fun `user skills mixed with compiled skills`() {
        val compiledSkill = createSkill("compiled")
        val registry = SkillRegistry(setOf(compiledSkill))

        registry.addUserSkill(createSkill("user1"))
        registry.addUserSkill(createSkill("user2"))

        assertThat(registry.count()).isEqualTo(3)
    }

    @Test
    fun `refresh invalidates cache`() {
        val skill1 = createSkill("skill1")
        val registry = SkillRegistry(setOf(skill1))

        registry.getAllSkills()  // populate cache
        registry.addUserSkill(createSkill("user1"))  // invalidates cache
        assertThat(registry.count()).isEqualTo(2)

        registry.refresh()  // manual refresh
        assertThat(registry.count()).isEqualTo(2)
    }
}
