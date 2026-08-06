// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.memory.repository

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.database.dao.SemanticFactDao
import com.roshan.persona.database.dao.UserFactDao
import com.roshan.persona.database.entity.SemanticFactEntity
import com.roshan.persona.database.entity.UserFactEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

// MEMORY_FIX_005: Semantic fact repo safe

class SemanticFactRepositoryTest {

    private val dao: SemanticFactDao = mockk(relaxed = true)
    private val repo = SemanticFactRepository(dao)

    @Test
    fun `store inserts fact with Bayesian defaults`() = runTest {
        coEvery { dao.insert(any()) } returns 1L

        val id = repo.store(
            subjectEntityId = 10,
            predicate = "mother_is",
            objectValue = "Sunita",
            source = "user_explicit",
        )

        assertThat(id).isEqualTo(1L)
        coVerify { dao.insert(any()) }
    }

    @Test
    fun `getById delegates to dao`() = runTest {
        val entity = makeFact(id = 1)
        coEvery { dao.getById(1) } returns entity

        val result = repo.getById(1)

        assertThat(result).isEqualTo(entity)
    }

    @Test
    fun `getForSubject delegates to dao`() = runTest {
        val facts = listOf(makeFact(id = 1), makeFact(id = 2))
        coEvery { dao.getForSubject(10) } returns facts

        val result = repo.getForSubject(10)

        assertThat(result).hasSize(2)
    }

    @Test
    fun `getByPredicate delegates to dao`() = runTest {
        coEvery { dao.getByPredicate("mother_is") } returns listOf(makeFact(id = 1))

        val result = repo.getByPredicate("mother_is")

        assertThat(result).hasSize(1)
    }

    @Test
    fun `reinforce delegates to dao`() = runTest {
        repo.reinforce(1)
        coVerify { dao.reinforce(1) }
    }

    @Test
    fun `contradict delegates to dao`() = runTest {
        repo.contradict(1)
        coVerify { dao.contradict(1) }
    }

    @Test
    fun `supersede sets valid_to and links`() = runTest {
        repo.supersede(oldId = 1, newId = 2)
        coVerify { dao.supersede(1, 2, any()) }
    }

    @Test
    fun `forget soft deletes`() = runTest {
        repo.forget(1)
        coVerify { dao.softDelete(1) }
    }

    @Test
    fun `count delegates`() = runTest {
        coEvery { dao.count() } returns 42
        assertThat(repo.count()).isEqualTo(42)
    }

    @Test
    fun `computeConfidence returns alpha over alpha_plus_beta`() {
        val entity = makeFact(id = 1, alpha = 3.0f, beta = 1.0f)
        val confidence = repo.computeConfidence(entity)
        // 3 / (3 + 1) = 0.75
        assertThat(confidence).isWithin(0.01f).of(0.75f)
    }

    @Test
    fun `computeConfidence returns 0_5 for equal alpha beta`() {
        val entity = makeFact(id = 1, alpha = 1.0f, beta = 1.0f)
        val confidence = repo.computeConfidence(entity)
        assertThat(confidence).isWithin(0.01f).of(0.5f)
    }

    private fun makeFact(
        id: Long = 1,
        alpha: Float = 1.0f,
        beta: Float = 1.0f,
        predicate: String = "test_pred",
        objectValue: String = "test_val",
    ): SemanticFactEntity {
        val now = System.currentTimeMillis()
        return SemanticFactEntity(
            id = id,
            subjectEntityId = null,
            predicate = predicate,
            objectValue = objectValue,
            objectEntityId = null,
            confidenceAlpha = alpha,
            confidenceBeta = beta,
            source = "user_explicit",
            validFrom = now,
            validTo = null,
            supersededBy = null,
            isDeleted = false,
            createdAt = now,
            updatedAt = now,
        )
    }
}

class UserFactRepositoryTest {

    private val dao: UserFactDao = mockk(relaxed = true)
    private val repo = UserFactRepository(dao)

    @Test
    fun `store creates new fact when key doesn't exist`() = runTest {
        coEvery { dao.getByKey("name") } returns null
        coEvery { dao.insert(any()) } returns 1L

        val id = repo.store("name", "Roshan")

        assertThat(id).isEqualTo(1L)
        coVerify { dao.insert(any()) }
    }

    @Test
    fun `store updates existing fact and reinforces`() = runTest {
        val existing = makeUserFact(id = 5, key = "name", value = "Old")
        coEvery { dao.getByKey("name") } returns existing

        val id = repo.store("name", "Roshan")

        assertThat(id).isEqualTo(5L)
        coVerify { dao.update(any()) }
        coVerify { dao.reinforce(5, any()) }
    }

    @Test
    fun `getByKey delegates to dao`() = runTest {
        val fact = makeUserFact(id = 1, key = "name", value = "Roshan")
        coEvery { dao.getByKey("name") } returns fact

        val result = repo.getByKey("name")

        assertThat(result).isEqualTo(fact)
    }

    @Test
    fun `getAll delegates to dao`() = runTest {
        coEvery { dao.getAll() } returns listOf(makeUserFact(id = 1))

        val result = repo.getAll()

        assertThat(result).hasSize(1)
    }

    @Test
    fun `reinforce delegates`() = runTest {
        repo.reinforce(1)
        coVerify { dao.reinforce(1, any()) }
    }

    @Test
    fun `contradict delegates`() = runTest {
        repo.contradict(1)
        coVerify { dao.contradict(1) }
    }

    @Test
    fun `forget soft deletes by key`() = runTest {
        val fact = makeUserFact(id = 5, key = "name")
        coEvery { dao.getByKey("name") } returns fact

        repo.forget("name")

        coVerify { dao.softDelete(5) }
    }

    @Test
    fun `forget does nothing for missing key`() = runTest {
        coEvery { dao.getByKey("missing") } returns null

        repo.forget("missing")

        coVerify(exactly = 0) { dao.softDelete(any()) }
    }

    @Test
    fun `count delegates`() = runTest {
        coEvery { dao.count() } returns 10
        assertThat(repo.count()).isEqualTo(10)
    }

    private fun makeUserFact(
        id: Long = 1,
        key: String = "test",
        value: String = "val",
    ): UserFactEntity {
        val now = System.currentTimeMillis()
        return UserFactEntity(
            id = id,
            key = key,
            value = value,
            confidenceAlpha = 1.0f,
            confidenceBeta = 1.0f,
            source = "user_explicit",
            learnedAt = now,
            lastConfirmed = now,
            isDeleted = false,
            createdAt = now,
            updatedAt = now,
        )
    }
}
