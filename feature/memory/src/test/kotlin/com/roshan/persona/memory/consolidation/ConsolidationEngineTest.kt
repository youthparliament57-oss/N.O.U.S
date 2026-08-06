// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.memory.consolidation

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.memory.repository.EpisodicMemoryRepository
import com.roshan.persona.memory.repository.KnowledgeGraphRepository
import com.roshan.persona.memory.repository.SemanticFactRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

// MEMORY_FIX_007: Consolidation engine validated

class ConsolidationEngineTest {

    private val episodicRepo: EpisodicMemoryRepository = mockk(relaxed = true)
    private val semanticRepo: SemanticFactRepository = mockk(relaxed = true)
    private val kgRepo: KnowledgeGraphRepository = mockk(relaxed = true)
    private val engine = ConsolidationEngine(episodicRepo, semanticRepo, kgRepo)

    @Test
    fun `consolidate runs all 7 phases and returns report`() = runTest {
        coEvery { episodicRepo.applyDecay(any()) } returns 100
        coEvery { episodicRepo.tombstoneLowImportance(any(), any()) } returns 5
        coEvery { episodicRepo.expireHotCache(any()) } returns 10
        coEvery { episodicRepo.vacuumTombstoned() } returns 5

        val report = engine.consolidate()

        assertThat(report).isNotNull()
        assertThat(report.memoriesPruned).isEqualTo(5)
        assertThat(report.hotCacheExpired).isEqualTo(10)
        assertThat(report.vacuumed).isEqualTo(5)
        assertThat(report.durationMs).isAtLeast(0L)
    }

    @Test
    fun `consolidate calls applyDecay`() = runTest {
        coEvery { episodicRepo.applyDecay(any()) } returns 0
        coEvery { episodicRepo.tombstoneLowImportance(any(), any()) } returns 0
        coEvery { episodicRepo.expireHotCache(any()) } returns 0
        coEvery { episodicRepo.vacuumTombstoned() } returns 0

        engine.consolidate()

        coVerify { episodicRepo.applyDecay(any()) }
    }

    @Test
    fun `consolidate calls tombstoneLowImportance with default threshold`() = runTest {
        coEvery { episodicRepo.applyDecay(any()) } returns 0
        coEvery { episodicRepo.tombstoneLowImportance(any(), any()) } returns 0
        coEvery { episodicRepo.expireHotCache(any()) } returns 0
        coEvery { episodicRepo.vacuumTombstoned() } returns 0

        engine.consolidate()

        coVerify { episodicRepo.tombstoneLowImportance(0.05f, any()) }
    }

    @Test
    fun `consolidate calls expireHotCache with 24h`() = runTest {
        coEvery { episodicRepo.applyDecay(any()) } returns 0
        coEvery { episodicRepo.tombstoneLowImportance(any(), any()) } returns 0
        coEvery { episodicRepo.expireHotCache(any()) } returns 0
        coEvery { episodicRepo.vacuumTombstoned() } returns 0

        engine.consolidate()

        coVerify { episodicRepo.expireHotCache(24) }
    }

    @Test
    fun `consolidate calls vacuumTombstoned`() = runTest {
        coEvery { episodicRepo.applyDecay(any()) } returns 0
        coEvery { episodicRepo.tombstoneLowImportance(any(), any()) } returns 0
        coEvery { episodicRepo.expireHotCache(any()) } returns 0
        coEvery { episodicRepo.vacuumTombstoned() } returns 42

        val report = engine.consolidate()

        assertThat(report.vacuumed).isEqualTo(42)
    }
}
