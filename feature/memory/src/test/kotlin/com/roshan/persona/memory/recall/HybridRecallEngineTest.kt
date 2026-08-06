// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.memory.recall

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.memory.repository.EpisodicMemoryRepository
import com.roshan.persona.memory.repository.KnowledgeGraphRepository
import com.roshan.persona.memory.repository.SemanticFactRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

// MEMORY_FIX_002: Hybrid recall engine ok

class ContextWindowManagerTest {

    private val manager = ContextWindowManager(maxMemoryTokens = 100)

    @Test
    fun `estimateTokens returns at least 1`() {
        assertThat(manager.estimateTokens("")).isEqualTo(1)
        assertThat(manager.estimateTokens("ab")).isEqualTo(1)
        assertThat(manager.estimateTokens("abcd")).isEqualTo(1)
        assertThat(manager.estimateTokens("abcde")).isEqualTo(2)
    }

    @Test
    fun `trimToFit keeps most important items`() {
        data class Item(val name: String, val importance: Float, val text: String)

        val items = listOf(
            Item("low", 0.1f, "short"),
            Item("high", 0.9f, "this is a longer text that takes more tokens"),
            Item("medium", 0.5f, "medium length text"),
        )

        val trimmed = manager.trimToFit(
            items = items,
            importanceSelector = { it.importance },
            contentSelector = { it.text },
            maxTokens = 50,  // large enough for all items
        )

        assertThat(trimmed).isNotEmpty()
        assertThat(trimmed[0].name).isEqualTo("high")  // most important first
    }

    @Test
    fun `trimToFit returns empty for tiny budget`() {
        val items = listOf("very long text here" to 1.0f)
        val trimmed = manager.trimToFit(
            items = items,
            importanceSelector = { it.second },
            contentSelector = { it.first },
            maxTokens = 0,
        )
        assertThat(trimmed).isEmpty()
    }
}

class HybridRecallEngineTest {

    private val episodicRepo: EpisodicMemoryRepository = mockk(relaxed = true)
    private val semanticRepo: SemanticFactRepository = mockk(relaxed = true)
    private val kgRepo: KnowledgeGraphRepository = mockk(relaxed = true)
    private val engine = HybridRecallEngine(episodicRepo, semanticRepo, kgRepo)

    @Test
    fun `recall returns empty for no matching memories`() = runTest {
        coEvery { episodicRepo.getRecent(any()) } returns emptyList()

        val results = engine.recall("nonexistent query")

        assertThat(results).isEmpty()
    }

    @Test
    fun `recall returns matching memories`() = runTest {
        val entity = com.roshan.persona.database.entity.EpisodicMemoryEntity(
            id = 1,
            content = "The capital of France is Paris",
            summary = null,
            timestamp = System.currentTimeMillis(),
            importance = 0.8f,
            initialImportance = 0.8f,
            lastAccessed = System.currentTimeMillis(),
            lastReinforced = System.currentTimeMillis(),
            decayConstantDays = 7.0f,
            isFlashbulb = false,
            isConsolidated = false,
            isDeleted = false,
            isHot = true,
            emotionalValencePending = false,
            tags = "",
            correlationId = null,
            sessionId = null,
            embeddingStatus = "ready",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        coEvery { episodicRepo.getRecent(any()) } returns listOf(entity)
        coEvery { episodicRepo.computeCurrentImportance(any(), any()) } returns 0.8f

        val results = engine.recall("France")

        assertThat(results).isNotEmpty()
        assertThat(results[0].content).contains("Paris")
    }
}
