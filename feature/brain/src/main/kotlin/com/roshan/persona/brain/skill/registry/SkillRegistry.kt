// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.brain.skill.registry

import com.roshan.persona.brain.intent.Intent
import com.roshan.persona.brain.skill.BrainSkill
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

/**
 * NOUS — Skill registry.
 *
 * Collects all [BrainSkill] implementations via Hilt multibinding. Skills are
 * auto-discovered at app startup — feature modules contribute their skills
 * via `@Binds @IntoSet` in their Hilt modules.
 *
 * Three types of skills (per spec §6 Layer 2):
 *   1. Built-in skills (CallSkill, SmsSkill, OpenAppSkill, ...) — compiled in
 *   2. JSON-based skills (declarative, no code) — stored in Room
 *   3. JS skills (QuickJS sandbox, sideload only) — stored in Room
 *
 * For Module 2, only source #1 (Hilt multibinding) is wired. Sources #2 and #3
 * are added when :feature:selfmodify (Module 13) ships.
 *
 * @see <a href="docs/spec/module2-spec-review.pdf">§6 Layer 2</a>
 */
@Singleton
class SkillRegistry @Inject constructor(
    /** Built-in skills from Hilt multibinding. */
    private val compiledSkills: Set<@JvmSuppressWildcards BrainSkill>,
) {
    /**
     * Immutable cache container holding pre-indexed maps for O(1) lookups.
     * Bolt Optimization: Indexed lookups by ID and Intent type avoid O(N) linear iteration
     * during high-frequency intent routing across registered skills.
     */
    private class CacheHolder(
        val allSkills: List<BrainSkill>,
        val skillsById: Map<String, BrainSkill>,
        val skillsByIntent: Map<KClass<out Intent>, List<BrainSkill>>,
    )

    @Volatile
    private var cacheHolder: CacheHolder? = null

    /** User-defined skills (JSON + JS, added dynamically — empty for Module 2). */
    @Volatile
    private var userSkills: List<BrainSkill> = emptyList()

    private fun getOrBuildCache(): CacheHolder {
        cacheHolder?.let { return it }
        val all: List<BrainSkill> = compiledSkills.toList() + userSkills
        val byId = all.associateBy { it.id }
        val byIntent = mutableMapOf<KClass<out Intent>, MutableList<BrainSkill>>()
        for (skill in all) {
            for (intent in skill.supportedIntents) {
                byIntent.getOrPut(intent) { mutableListOf() }.add(skill)
            }
        }
        val holder = CacheHolder(
            allSkills = all,
            skillsById = byId,
            skillsByIntent = byIntent,
        )
        cacheHolder = holder
        Timber.tag("NOUS.L2").v("SkillRegistry: ${all.size} skills (${compiledSkills.size} compiled, ${userSkills.size} user)")
        return holder
    }

    /**
     * Get all active skills.
     */
    fun getAllSkills(): List<BrainSkill> {
        return getOrBuildCache().allSkills
    }

    /**
     * Find skills that handle a given [intentType].
     * Optimized: O(1) map lookup instead of O(N) list filtering.
     */
    fun findSkillsForIntent(intentType: KClass<out Intent>): List<BrainSkill> {
        return getOrBuildCache().skillsByIntent[intentType] ?: emptyList()
    }

    /**
     * Find a skill by ID.
     * Optimized: O(1) map lookup instead of O(N) linear search.
     */
    fun findById(skillId: String): BrainSkill? {
        return getOrBuildCache().skillsById[skillId]
    }

    /**
     * Find skills that require a specific permission.
     */
    fun findSkillsRequiringPermission(permission: String): List<BrainSkill> {
        return getAllSkills().filter { permission in it.requiredPermissions }
    }

    /**
     * Add a user skill dynamically (JSON or JS skill from Module 13).
     * If a skill with the same ID exists, it is replaced.
     */
    fun addUserSkill(skill: BrainSkill) {
        userSkills = userSkills.filterNot { it.id == skill.id } + skill
        cacheHolder = null
        Timber.tag("NOUS.L2").i("User skill added: ${skill.id}")
    }

    /**
     * Remove a user skill by ID.
     */
    fun removeUserSkill(skillId: String): Boolean {
        val before = userSkills.size
        userSkills = userSkills.filterNot { it.id == skillId }
        val removed = userSkills.size < before
        if (removed) {
            cacheHolder = null
            Timber.tag("NOUS.L2").i("User skill removed: $skillId")
        }
        return removed
    }

    /** Total skill count (for debugging). */
    fun count(): Int = getAllSkills().size

    /** Refresh cache (called when user skills change in Room). */
    fun refresh() {
        cacheHolder = null
    }
}
// THREAD_FIX_005
// Skill Registry - Thread-safe skill lookups
