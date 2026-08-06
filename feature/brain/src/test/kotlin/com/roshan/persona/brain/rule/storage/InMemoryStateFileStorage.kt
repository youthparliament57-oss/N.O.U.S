// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.brain.rule.storage

import com.roshan.persona.brain.context.SessionId

/**
 * Test-only in-memory [StateFileStorage] for tests.
 *
 * Removed from `src/main` (Phase 1 stub-removal): production code uses
 * :feature:memory's `RoomStateFileStorage` (bound via MemoryModule) which
 * persists to Room. This in-memory variant lives in the test source set
 * so future CrashSafeConversationStateStorage tests can verify the storage
 * contract without touching Room.
 */
class InMemoryStateFileStorage : StateFileStorage {
    private val storage = mutableMapOf<SessionId, String>()

    override suspend fun write(sessionId: SessionId, data: String) {
        storage[sessionId] = data
    }

    override suspend fun read(sessionId: SessionId): String? = storage[sessionId]

    override suspend fun delete(sessionId: SessionId) {
        storage.remove(sessionId)
    }

    override suspend fun listAll(): List<SessionId> = storage.keys.toList()
}
