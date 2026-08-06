// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.brain.rule.storage

import com.roshan.persona.brain.context.SessionId
import com.roshan.persona.brain.intent.Intent
import com.roshan.persona.brain.skill.ConversationStage
import com.roshan.persona.brain.skill.SkillOutput
import com.roshan.persona.common.CorrelationId
import com.roshan.persona.common.Result
import java.time.Instant

/**
 * Test-only in-memory [ConversationStateStorage] for tests.
 *
 * Removed from `src/main` (Phase 1 stub-removal): production code uses
 * [CrashSafeConversationStateStorage] (bound via
 * [com.roshan.persona.brain.di.BrainBindingsModule]) which persists to disk
 * via :feature:memory's `RoomStateFileStorage`. This in-memory variant
 * lives in the test source set so [ConversationStateStorageTest] can verify
 * the storage contract without touching the file system or Room.
 */
class InMemoryConversationStateStorage : ConversationStateStorage {

    private val states = mutableMapOf<SessionId, ConversationStateData>()

    override suspend fun save(state: ConversationStateData): Result<Unit> {
        states[state.sessionId] = state
        return Result.Success(Unit, state.correlationId, 0)
    }

    override suspend fun getActive(sessionId: SessionId): Result<ConversationStateData?> {
        val state = states[sessionId]
        val active = if (state != null && !state.isExpired() && state.stage == ConversationStage.NEEDS_INPUT) {
            state
        } else {
            null
        }
        return Result.Success(active, CorrelationId.generate(), 0)
    }

    override suspend fun markCompleted(sessionId: SessionId): Result<Unit> {
        states.remove(sessionId)
        return Result.Success(Unit, CorrelationId.generate(), 0)
    }

    override suspend fun markExpired(sessionId: SessionId): Result<Unit> {
        states.remove(sessionId)
        return Result.Success(Unit, CorrelationId.generate(), 0)
    }

    override suspend fun deleteAllForSession(sessionId: SessionId): Result<Unit> {
        states.remove(sessionId)
        return Result.Success(Unit, CorrelationId.generate(), 0)
    }

    override suspend fun deleteExpired(): Result<Int> {
        val now = Instant.now()
        val expired = states.values.filter { it.isExpired(now) }
        expired.forEach { states.remove(it.sessionId) }
        return Result.Success(expired.size, CorrelationId.generate(), 0)
    }

    /** Clear all (for testing). */
    fun clear() {
        states.clear()
    }
}
