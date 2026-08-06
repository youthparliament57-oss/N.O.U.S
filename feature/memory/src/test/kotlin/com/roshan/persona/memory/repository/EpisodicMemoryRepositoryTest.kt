// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.memory.repository

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.database.dao.EpisodicMemoryDao
import com.roshan.persona.database.entity.EpisodicMemoryEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

// MEMORY_FIX_006: Episodic memory repo ok

class EpisodicMemoryRepositoryTest {

    private val dao: EpisodicMemoryDao = mockk(relaxed = true)
    private val repo = EpisodicMemoryRepository(dao)

    @Test
    fun `store inserts memory with correct defaults`() = runTest {
        coEvery { dao.insert(any()) } returns 1L

        val id = repo.store(
            content = "User asked about weather",
            importance = 0.7f,
            tags = setOf("weather", "question"),
            sessionId = "session-1",
            correlationId = "corr-1",
        )

        assertThat(id).isEqualTo(1L)
        val entitySlot = slot<EpisodicMemoryEntity>()
        coVerify { dao.insert(capture(entitySlot)) }
        val entity = entitySlot.captured
        assertThat(entity.content).isEqualTo("User asked about weather")
        assertThat(entity.importance).isEqualTo(0.7f)
        assertThat(entity.initialImportance).isEqualTo(0.7f)
        assertThat(entity.isHot).isTrue()
        assertThat(entity.isDeleted).isFalse()
        assertThat(entity.isFlashbulb).isFalse()
        assertThat(entity.embeddingStatus).isEqualTo("pending")
        assertThat(entity.tags).isEqualTo("weather,question")
        assertThat(entity.decayConstantDays).isEqualTo(7.0f)
    }

    @Test
    fun `store with flashbulb flag sets isFlashbulb true`() = runTest {
        coEvery { dao.insert(any()) } returns 1L

        repo.store(content = "Birthday party!", importance = 0.9f, isFlashbulb = true)

        val entitySlot = slot<EpisodicMemoryEntity>()
        coVerify { dao.insert(capture(entitySlot)) }
        assertThat(entitySlot.captured.isFlashbulb).isTrue()
    }

    @Test
    fun `getById returns memory and updates lastAccessed`() = runTest {
        val entity = makeEntity(id = 1, lastAccessed = 1000L)
        coEvery { dao.getById(1) } returns entity

        val result = repo.getById(1)

        assertThat(result).isNotNull()
        assertThat(result!!.content).isEqualTo("test")
        coVerify { dao.update(any()) }  // lastAccessed updated
    }

    @Test
    fun `getById returns null for missing memory`() = runTest {
        coEvery { dao.getById(999) } returns null

        val result = repo.getById(999)

        assertThat(result).isNull()
    }

    @Test
    fun `getRecent delegates to dao`() = runTest {
        val entities = listOf(makeEntity(id = 1), makeEntity(id = 2))
        coEvery { dao.getRecent(50) } returns entities

        val result = repo.getRecent()

        assertThat(result).hasSize(2)
    }

    @Test
    fun `getHotCache delegates to dao`() = runTest {
        val entities = listOf(makeEntity(id = 1, isHot = true))
        coEvery { dao.getHotCache() } returns entities

        val result = repo.getHotCache()

        assertThat(result).hasSize(1)
        assertThat(result[0].isHot).isTrue()
    }

    @Test
    fun `getPendingEmbeddings delegates to dao with limit`() = runTest {
        coEvery { dao.getPendingEmbeddings(32) } returns emptyList()

        repo.getPendingEmbeddings()

        coVerify { dao.getPendingEmbeddings(32) }
    }

    @Test
    fun `updateEmbeddingStatus delegates to dao`() = runTest {
        repo.updateEmbeddingStatus(1, "computing")

        coVerify { dao.updateEmbeddingStatus(1, "computing") }
    }

    @Test
    fun `reinforce boosts importance and slows decay`() = runTest {
        val entity = makeEntity(id = 1, importance = 0.5f, decayConstantDays = 7.0f)
        coEvery { dao.getById(1) } returns entity

        repo.reinforce(1)

        val importanceSlot = slot<Float>()
        val decaySlot = slot<Float>()
        coVerify {
            dao.reinforce(1, capture(importanceSlot), any(), any(), capture(decaySlot))
        }
        // 0.5 * 1.5 = 0.75
        assertThat(importanceSlot.captured).isWithin(0.01f).of(0.75f)
        // 7.0 * 1.1 = 7.7
        assertThat(decaySlot.captured).isWithin(0.01f).of(7.7f)
    }

    @Test
    fun `reinforce caps importance at 1_0`() = runTest {
        val entity = makeEntity(id = 1, importance = 0.8f)
        coEvery { dao.getById(1) } returns entity

        repo.reinforce(1)

        val importanceSlot = slot<Float>()
        coVerify { dao.reinforce(1, capture(importanceSlot), any(), any(), any()) }
        // 0.8 * 1.5 = 1.2 → capped at 1.0
        assertThat(importanceSlot.captured).isWithin(0.01f).of(1.0f)
    }

    @Test
    fun `reinforce does nothing for missing memory`() = runTest {
        coEvery { dao.getById(999) } returns null

        repo.reinforce(999)

        coVerify(exactly = 0) { dao.reinforce(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `applyDecay delegates to dao with factor`() = runTest {
        coEvery { dao.applyDecay(0.99f) } returns 100

        val affected = repo.applyDecay()

        assertThat(affected).isEqualTo(100)
    }

    @Test
    fun `tombstoneLowImportance delegates with threshold and cutoff`() = runTest {
        coEvery { dao.tombstoneLowImportance(any(), any()) } returns 5

        val tombstoned = repo.tombstoneLowImportance(threshold = 0.05f, minAgeDays = 30)

        assertThat(tombstoned).isEqualTo(5)
    }

    @Test
    fun `forget soft deletes by id`() = runTest {
        repo.forget(42)

        coVerify { dao.softDelete(42) }
    }

    @Test
    fun `expireHotCache delegates with 24h cutoff`() = runTest {
        coEvery { dao.expireHotCache(any()) } returns 10

        val expired = repo.expireHotCache(maxAgeHours = 24)

        assertThat(expired).isEqualTo(10)
    }

    @Test
    fun `vacuumTombstoned delegates to dao`() = runTest {
        coEvery { dao.vacuumTombstoned() } returns 20

        val deleted = repo.vacuumTombstoned()

        assertThat(deleted).isEqualTo(20)
    }

    @Test
    fun `count delegates to dao`() = runTest {
        coEvery { dao.count() } returns 42

        val count = repo.count()

        assertThat(count).isEqualTo(42)
    }

    @Test
    fun `computeCurrentImportance decays over time`() {
        val now = System.currentTimeMillis()
        val entity = makeEntity(
            id = 1,
            importance = 1.0f,
            decayConstantDays = 7.0f,
            lastReinforced = now - (7L * 24 * 60 * 60 * 1000),  // 7 days ago
        )

        val decayed = repo.computeCurrentImportance(entity, now)

        // After 7 days with decayConstant=7: importance * e^(-7/7) = 1.0 * e^(-1) ≈ 0.368
        assertThat(decayed).isWithin(0.05f).of(0.368f)
    }

    @Test
    fun `computeCurrentImportance returns full importance for flashbulb`() {
        val now = System.currentTimeMillis()
        val entity = makeEntity(
            id = 1,
            importance = 0.9f,
            isFlashbulb = true,
            lastReinforced = now - (365L * 24 * 60 * 60 * 1000),  // 1 year ago
        )

        val decayed = repo.computeCurrentImportance(entity, now)

        // Flashbulb memories don't decay
        assertThat(decayed).isEqualTo(0.9f)
    }

    @Test
    fun `computeCurrentImportance returns near-zero after long time`() {
        val now = System.currentTimeMillis()
        val entity = makeEntity(
            id = 1,
            importance = 0.5f,
            decayConstantDays = 7.0f,
            lastReinforced = now - (70L * 24 * 60 * 60 * 1000),  // 70 days ago (10 half-lives)
        )

        val decayed = repo.computeCurrentImportance(entity, now)

        // After 70 days: 0.5 * e^(-70/7) = 0.5 * e^(-10) ≈ 0.0000227
        assertThat(decayed).isLessThan(0.001f)
    }

    @Test
    fun `computeCurrentImportance returns full importance at time zero`() {
        val now = System.currentTimeMillis()
        val entity = makeEntity(
            id = 1,
            importance = 0.7f,
            lastReinforced = now,  // just now
        )

        val decayed = repo.computeCurrentImportance(entity, now)

        assertThat(decayed).isWithin(0.01f).of(0.7f)
    }

    // ─── Helpers ──────────────────────────────────────────────────

    private fun makeEntity(
        id: Long = 1,
        content: String = "test",
        importance: Float = 0.5f,
        initialImportance: Float = importance,
        lastAccessed: Long = System.currentTimeMillis(),
        lastReinforced: Long = System.currentTimeMillis(),
        decayConstantDays: Float = 7.0f,
        isFlashbulb: Boolean = false,
        isHot: Boolean = false,
    ): EpisodicMemoryEntity {
        val now = System.currentTimeMillis()
        return EpisodicMemoryEntity(
            id = id,
            content = content,
            summary = null,
            timestamp = now,
            importance = importance,
            initialImportance = initialImportance,
            lastAccessed = lastAccessed,
            lastReinforced = lastReinforced,
            decayConstantDays = decayConstantDays,
            isFlashbulb = isFlashbulb,
            isHot = isHot,
            tags = "",
            embeddingStatus = "ready",
            createdAt = now,
            updatedAt = now,
        )
    }
}
