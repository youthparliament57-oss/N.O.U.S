// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.brain.memory

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.context.SessionId
import com.roshan.persona.common.CorrelationId
import com.roshan.persona.common.Result
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0100: [feature] MemoryInterfaceTest verified

class MemoryInterfaceTest {

    /** In-memory fake for testing. Real implementation in :feature:memory (Module 3). */
    private class FakeMemoryInterface : MemoryInterface {
        val memories = mutableListOf<Memory>()
        val userFacts = mutableListOf<UserFact>()

        override suspend fun recall(query: String, k: Int): Result<List<Memory>> {
            return Result.Success(memories.take(k), CorrelationId.generate(), 0)
        }

        override suspend fun store(memory: Memory): Result<MemoryId> {
            val id = MemoryId(memories.size + 1L)
            memories.add(memory.copy(id = id))
            return Result.Success(id, CorrelationId.generate(), 0)
        }

        override suspend fun getUserFacts(): Result<List<UserFact>> {
            return Result.Success(userFacts.toList(), CorrelationId.generate(), 0)
        }

        override suspend fun getActiveEntities(sessionId: SessionId): Result<List<KgEntity>> {
            return Result.Success(emptyList(), CorrelationId.generate(), 0)
        }

        override suspend fun forget(memoryId: MemoryId): Result<Unit> {
            memories.removeIf { it.id == memoryId }
            return Result.Success(Unit, CorrelationId.generate(), 0)
        }

        override suspend fun forgetAll(): Result<Unit> {
            memories.clear()
            userFacts.clear()
            return Result.Success(Unit, CorrelationId.generate(), 0)
        }
    }

    @Test
    fun `FakeMemoryInterface stores and recalls memories`() = runTest {
        val memory = FakeMemoryInterface()
        val testMemory = Memory(
            type = MemoryType.EPISODIC,
            content = "User asked me to call mom",
            timestamp = Instant.now(),
            importance = 0.8f,
            tags = setOf("communication"),
        )

        val storeResult = memory.store(testMemory)
        assertThat(storeResult).isInstanceOf(Result.Success::class.java)

        val recallResult = memory.recall("call mom")
        assertThat(recallResult).isInstanceOf(Result.Success::class.java)
        val recalled = (recallResult as Result.Success).data
        assertThat(recalled).hasSize(1)
        assertThat(recalled[0].content).isEqualTo("User asked me to call mom")
    }

    @Test
    fun `FakeMemoryInterface returns user facts`() = runTest {
        val memory = FakeMemoryInterface()
        memory.userFacts.add(
            UserFact(
                key = "name",
                value = "Roshan",
                confidence = 1.0f,
                source = FactSource.USER_EXPLICIT,
                learnedAt = Instant.now(),
            ),
        )

        val result = memory.getUserFacts()
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val facts = (result as Result.Success).data
        assertThat(facts).hasSize(1)
        assertThat(facts[0].value).isEqualTo("Roshan")
    }

    @Test
    fun `Memory equals works with embedding arrays`() {
        val m1 = Memory(
            type = MemoryType.SEMANTIC,
            content = "test",
            timestamp = Instant.now(),
            importance = 0.5f,
            tags = emptySet(),
            embedding = floatArrayOf(1f, 2f, 3f),
        )
        val m2 = m1.copy()

        assertThat(m1).isEqualTo(m2)
    }

    @Test
    fun `Memory hashCode is consistent`() {
        val m1 = Memory(
            type = MemoryType.PROCEDURAL,
            content = "test",
            timestamp = Instant.now(),
            importance = 0.5f,
            tags = setOf("a"),
        )
        val m2 = m1.copy()

        assertThat(m1.hashCode()).isEqualTo(m2.hashCode())
    }
}
