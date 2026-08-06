// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.memory.impl

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.context.SessionId
import com.roshan.persona.brain.memory.Memory
import com.roshan.persona.brain.rule.storage.ConversationStateData
import com.roshan.persona.brain.memory.MemoryType
import com.roshan.persona.database.dao.ConversationStateDao
import com.roshan.persona.database.entity.ConversationStateEntity
import com.roshan.persona.memory.consolidation.ConsolidationEngine
import com.roshan.persona.memory.consolidation.ConsolidationReport
import com.roshan.persona.memory.recall.HybridRecallEngine
import com.roshan.persona.memory.recall.RecalledMemory
import com.roshan.persona.memory.repository.EpisodicMemoryRepository
import com.roshan.persona.memory.repository.KnowledgeGraphRepository
import com.roshan.persona.memory.repository.SemanticFactRepository
import com.roshan.persona.memory.repository.UserFactRepository
import com.roshan.persona.common.Result
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

// MEMORY_FIX_001: Memory interface impl safe

class MemoryInterfaceImplTest {

    private val episodicRepo: EpisodicMemoryRepository = mockk(relaxed = true)
    private val semanticRepo: SemanticFactRepository = mockk(relaxed = true)
    private val userFactRepo: UserFactRepository = mockk(relaxed = true)
    private val kgRepo: KnowledgeGraphRepository = mockk(relaxed = true)
    private val recallEngine: HybridRecallEngine = mockk(relaxed = true)
    private val consolidationEngine: ConsolidationEngine = mockk(relaxed = true)

    private val impl = MemoryInterfaceImpl(
        episodicRepo, semanticRepo, userFactRepo, kgRepo, recallEngine, consolidationEngine
    )

    @Test
    fun `store delegates to episodicRepo`() = runTest {
        coEvery { episodicRepo.store(any(), any(), any(), any(), any(), any()) } returns 1L
        val memory = Memory(
            type = MemoryType.EPISODIC,
            content = "test memory",
            timestamp = Instant.now(),
            importance = 0.5f,
            tags = emptySet(),
        )

        val result = impl.store(memory)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        coVerify { episodicRepo.store(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `recall delegates to recallEngine`() = runTest {
        coEvery { recallEngine.recall(any(), any()) } returns listOf(
            RecalledMemory(id = 1, content = "test", source = com.roshan.persona.memory.recall.RecallSource.KEYWORD, rawScore = 0f, rrfScore = 0.5f, finalScore = 0.8f)
        )

        val result = impl.recall("test query")

        assertThat(result).isInstanceOf(Result.Success::class.java)
        val memories = (result as Result.Success).data
        assertThat(memories).hasSize(1)
        assertThat(memories[0].content).isEqualTo("test")
    }

    @Test
    fun `recall returns Failure on exception`() = runTest {
        coEvery { recallEngine.recall(any(), any()) } throws RuntimeException("crash")

        val result = impl.recall("test")

        assertThat(result).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `getUserFacts returns empty list when no facts`() = runTest {
        coEvery { userFactRepo.getAll() } returns emptyList()

        val result = impl.getUserFacts()

        assertThat(result).isInstanceOf(Result.Success::class.java)
        val facts = (result as Result.Success).data
        assertThat(facts).isEmpty()
    }

    @Test
    fun `forget delegates to episodicRepo`() = runTest {
        impl.forget(com.roshan.persona.brain.memory.MemoryId(42))

        coVerify { episodicRepo.forget(42) }
    }

    @Test
    fun `consolidate delegates to consolidationEngine`() = runTest {
        val report = ConsolidationReport(
            episodicPromoted = 1, kgEdgesCreated = 2, memoriesPruned = 3,
            hotCacheExpired = 4, vectorsReindexed = 5, memoriesCompressed = 6,
            prospectiveTriggered = 7, vacuumed = 8, durationMs = 100L,
        )
        coEvery { consolidationEngine.consolidate() } returns report

        val result = impl.consolidate()

        assertThat(result.memoriesPruned).isEqualTo(3)
        assertThat(result.vacuumed).isEqualTo(8)
    }
}

class RoomConversationStateStorageTest {

    private val dao: ConversationStateDao = mockk(relaxed = true)
    private val storage = RoomConversationStateStorage(dao)

    @Test
    fun `save upserts entity to dao`() = runTest {
        val state = ConversationStateData(
            sessionId = SessionId("test-session"),
            correlationId = com.roshan.persona.common.CorrelationId("test-corr"),
            skillId = "call",
            intent = com.roshan.persona.brain.intent.Intent.Unknown(raw = ""),
            stage = com.roshan.persona.brain.skill.ConversationStage.NEEDS_INPUT,
            partialOutput = null,
            createdAt = Instant.now(),
            expiresAt = Instant.now().plusSeconds(300),
        )

        val result = storage.save(state)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        coVerify { dao.upsert(any()) }
    }

    @Test
    fun `getActive returns null when no state exists`() = runTest {
        coEvery { dao.getActiveForSession(any(), any()) } returns null

        val result = storage.getActive(SessionId("test"))

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).data).isNull()
    }

    @Test
    fun `getActive returns state when valid`() = runTest {
        val entity = ConversationStateEntity(
            id = 1,
            sessionId = "test-session",
            correlationId = "test-corr",
            skillId = "call",
            intent = "Call",
            stage = "NEEDS_INPUT",
            partialOutput = null,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 300000,
        )
        coEvery { dao.getActiveForSession(any(), any()) } returns entity

        val result = storage.getActive(SessionId("test-session"))

        assertThat(result).isInstanceOf(Result.Success::class.java)
        val state = (result as Result.Success).data
        assertThat(state).isNotNull()
        assertThat(state!!.skillId).isEqualTo("call")
    }

    @Test
    fun `getActive deletes expired state and returns null`() = runTest {
        val entity = ConversationStateEntity(
            id = 1,
            sessionId = "test-session",
            correlationId = "test-corr",
            skillId = "call",
            intent = "Call",
            stage = "NEEDS_INPUT",
            partialOutput = null,
            createdAt = System.currentTimeMillis() - 600000,
            expiresAt = System.currentTimeMillis() - 100000,  // expired
        )
        coEvery { dao.getActiveForSession(any(), any()) } returns entity

        val result = storage.getActive(SessionId("test-session"))

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).data).isNull()
        coVerify { dao.deleteForSession("test-session") }
    }

    @Test
    fun `markCompleted deletes for session`() = runTest {
        storage.markCompleted(SessionId("test"))

        coVerify { dao.deleteForSession("test") }
    }

    @Test
    fun `deleteExpired delegates to dao`() = runTest {
        coEvery { dao.deleteExpired(any()) } returns 5

        val result = storage.deleteExpired()

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).data).isEqualTo(5)
    }
}
