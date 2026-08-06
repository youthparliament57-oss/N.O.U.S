// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.memory.repository

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.database.dao.KgDao
import com.roshan.persona.database.entity.KgEdgeEntity
import com.roshan.persona.database.entity.KgEntityEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

// MEMORY_FIX_004: KG repository thread safe

class KnowledgeGraphRepositoryTest {

    private val dao: KgDao = mockk(relaxed = true)
    private val repo = KnowledgeGraphRepository(dao)

    // ─── Entity tests ──────────────────────────────────────────────

    @Test
    fun `addEntity creates new entity when not found`() = runTest {
        coEvery { dao.findEntity("PERSON", "Sunita") } returns null
        coEvery { dao.insertEntity(any()) } returns 1L

        val id = repo.addEntity("PERSON", "Sunita")

        assertThat(id).isEqualTo(1L)
        coVerify { dao.insertEntity(any()) }
    }

    @Test
    fun `addEntity increments mention count when already exists`() = runTest {
        val existing = makeEntity(id = 5, type = "PERSON", name = "Sunita")
        coEvery { dao.findEntity("PERSON", "Sunita") } returns existing

        val id = repo.addEntity("PERSON", "Sunita")

        assertThat(id).isEqualTo(5L)
        coVerify { dao.incrementMention(5, any()) }
        coVerify(exactly = 0) { dao.insertEntity(any()) }
    }

    @Test
    fun `getEntity delegates to dao`() = runTest {
        val entity = makeEntity(id = 1)
        coEvery { dao.getEntityById(1) } returns entity

        val result = repo.getEntity(1)

        assertThat(result).isEqualTo(entity)
    }

    @Test
    fun `getEntitiesByType delegates`() = runTest {
        coEvery { dao.getEntitiesByType("PERSON") } returns listOf(makeEntity(id = 1))

        val result = repo.getEntitiesByType("PERSON")

        assertThat(result).hasSize(1)
    }

    @Test
    fun `findEntity delegates`() = runTest {
        val entity = makeEntity(id = 1, name = "Mom")
        coEvery { dao.findEntity("PERSON", "Mom") } returns entity

        val result = repo.findEntity("PERSON", "Mom")

        assertThat(result).isEqualTo(entity)
    }

    @Test
    fun `findByAlias delegates`() = runTest {
        coEvery { dao.findByAlias("mom") } returns listOf(makeEntity(id = 1))

        val result = repo.findByAlias("mom")

        assertThat(result).hasSize(1)
    }

    @Test
    fun `forgetEntity soft deletes`() = runTest {
        repo.forgetEntity(1)
        coVerify { dao.softDeleteEntity(1) }
    }

    @Test
    fun `entityCount delegates`() = runTest {
        coEvery { dao.entityCount() } returns 100
        assertThat(repo.entityCount()).isEqualTo(100)
    }

    // ─── Edge tests ────────────────────────────────────────────────

    @Test
    fun `addEdge creates new edge`() = runTest {
        coEvery { dao.insertEdge(any()) } returns 1L

        val id = repo.addEdge(1, 2, "KNOWS")

        assertThat(id).isEqualTo(1L)
        coVerify { dao.insertEdge(any()) }
    }

    @Test
    fun `getOutgoingEdges delegates`() = runTest {
        val edge = makeEdge(id = 1, source = 1, target = 2)
        coEvery { dao.getOutgoingEdges(1) } returns listOf(edge)

        val result = repo.getOutgoingEdges(1)

        assertThat(result).hasSize(1)
    }

    @Test
    fun `getIncomingEdges delegates`() = runTest {
        val edge = makeEdge(id = 1, source = 2, target = 1)
        coEvery { dao.getIncomingEdges(1) } returns listOf(edge)

        val result = repo.getIncomingEdges(1)

        assertThat(result).hasSize(1)
    }

    @Test
    fun `getEdgesByType delegates`() = runTest {
        coEvery { dao.getEdgesByType("KNOWS") } returns listOf(makeEdge(id = 1))

        val result = repo.getEdgesByType("KNOWS")

        assertThat(result).hasSize(1)
    }

    @Test
    fun `getNeighbors returns both outgoing and incoming entities`() = runTest {
        val e1 = makeEntity(id = 1, name = "User")
        val e2 = makeEntity(id = 2, name = "Mom")
        val e3 = makeEntity(id = 3, name = "Dad")

        coEvery { dao.getOutgoingEdges(1) } returns listOf(
            makeEdge(id = 1, source = 1, target = 2)
        )
        coEvery { dao.getIncomingEdges(1) } returns listOf(
            makeEdge(id = 2, source = 3, target = 1)
        )
        coEvery { dao.getEntityById(2) } returns e2
        coEvery { dao.getEntityById(3) } returns e3

        val neighbors = repo.getNeighbors(1)

        assertThat(neighbors).hasSize(2)
        assertThat(neighbors.map { it.id }).containsExactly(2L, 3L)
    }

    @Test
    fun `traverse finds reachable entities within maxHops`() = runTest {
        // 1 → 2 → 3 → 4
        coEvery { dao.getOutgoingEdges(1) } returns listOf(makeEdge(source = 1, target = 2))
        coEvery { dao.getOutgoingEdges(2) } returns listOf(makeEdge(source = 2, target = 3))
        coEvery { dao.getOutgoingEdges(3) } returns listOf(makeEdge(source = 3, target = 4))
        coEvery { dao.getOutgoingEdges(4) } returns emptyList()
        coEvery { dao.getIncomingEdges(any()) } returns emptyList()

        val reachable = repo.traverse(1, maxHops = 3)

        assertThat(reachable).containsExactly(2L, 3L, 4L)
    }

    @Test
    fun `traverse returns empty for isolated entity`() = runTest {
        coEvery { dao.getOutgoingEdges(1) } returns emptyList()
        coEvery { dao.getIncomingEdges(1) } returns emptyList()

        val reachable = repo.traverse(1, maxHops = 3)

        assertThat(reachable).isEmpty()
    }

    @Test
    fun `findPath returns direct path`() = runTest {
        coEvery { dao.getOutgoingEdges(1) } returns listOf(makeEdge(source = 1, target = 2))
        coEvery { dao.getIncomingEdges(1) } returns emptyList()
        coEvery { dao.getIncomingEdges(2) } returns emptyList()

        val path = repo.findPath(1, 2)

        assertThat(path).containsExactly(1L, 2L).inOrder()
    }

    @Test
    fun `findPath returns multi-hop path`() = runTest {
        // 1 → 2 → 3
        coEvery { dao.getOutgoingEdges(1) } returns listOf(makeEdge(source = 1, target = 2))
        coEvery { dao.getOutgoingEdges(2) } returns listOf(makeEdge(source = 2, target = 3))
        coEvery { dao.getIncomingEdges(any()) } returns emptyList()

        val path = repo.findPath(1, 3)

        assertThat(path).containsExactly(1L, 2L, 3L).inOrder()
    }

    @Test
    fun `findPath returns empty when no path exists`() = runTest {
        coEvery { dao.getOutgoingEdges(1) } returns emptyList()
        coEvery { dao.getIncomingEdges(1) } returns emptyList()

        val path = repo.findPath(1, 99)

        assertThat(path).isEmpty()
    }

    @Test
    fun `findPath returns single element for same start and end`() = runTest {
        val path = repo.findPath(1, 1)
        assertThat(path).containsExactly(1L)
    }

    @Test
    fun `reinforceEdge delegates`() = runTest {
        repo.reinforceEdge(1)
        coVerify { dao.reinforceEdge(1) }
    }

    @Test
    fun `contradictEdge delegates`() = runTest {
        repo.contradictEdge(1)
        coVerify { dao.contradictEdge(1) }
    }

    @Test
    fun `supersedeEdge delegates`() = runTest {
        repo.supersedeEdge(1, 2)
        coVerify { dao.supersedeEdge(1, 2, any()) }
    }

    @Test
    fun `forgetEdge soft deletes`() = runTest {
        repo.forgetEdge(1)
        coVerify { dao.softDeleteEdge(1) }
    }

    @Test
    fun `edgeCount delegates`() = runTest {
        coEvery { dao.edgeCount() } returns 50
        assertThat(repo.edgeCount()).isEqualTo(50)
    }

    // ─── Helpers ──────────────────────────────────────────────────

    private fun makeEntity(
        id: Long = 1,
        type: String = "PERSON",
        name: String = "Test",
    ): KgEntityEntity {
        val now = System.currentTimeMillis()
        return KgEntityEntity(
            id = id,
            type = type,
            canonicalName = name,
            aliases = null,
            metadata = null,
            confidenceAlpha = 1.0f,
            confidenceBeta = 1.0f,
            mentionCount = 1,
            lastMentioned = now,
            isDeleted = false,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun makeEdge(
        id: Long = 1,
        source: Long = 1,
        target: Long = 2,
        edgeType: String = "KNOWS",
    ): KgEdgeEntity {
        val now = System.currentTimeMillis()
        return KgEdgeEntity(
            id = id,
            sourceEntityId = source,
            targetEntityId = target,
            edgeType = edgeType,
            confidenceAlpha = 1.0f,
            confidenceBeta = 1.0f,
            validFrom = now,
            validTo = null,
            supersededBy = null,
            source = "inferred",
            metadata = null,
            isDeleted = false,
            createdAt = now,
            updatedAt = now,
        )
    }
}
