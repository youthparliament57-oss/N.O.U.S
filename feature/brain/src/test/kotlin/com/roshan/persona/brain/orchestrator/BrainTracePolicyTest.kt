// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.brain.orchestrator

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.context.SessionId
import com.roshan.persona.brain.skill.SkillOutput
import com.roshan.persona.brain.trace.BrainTrace
import com.roshan.persona.common.CorrelationId
import org.junit.Test
import java.time.Instant

// AUTO_FIX_0075: [feature] BrainTracePolicyTest verified

class BrainTracePolicyTest {

    private val policy = BrainTracePolicy(maxCachedTraces = 100, retentionDays = 90)

    private fun makeTrace(correlationId: CorrelationId = CorrelationId.generate()): BrainTrace {
        return BrainTrace(
            correlationId = correlationId,
            sessionId = SessionId.generate(),
            inputRedacted = "test",
            startedAt = Instant.now(),
            completedAt = Instant.now(),
            totalDurationMs = 100L,
            layers = emptyList(),
            finalOutput = SkillOutput.Success("ok"),
            finalError = null,
            totalCostUsd = 0f,
            totalTokensGenerated = 0,
        )
    }

    @Test
    fun `addTrace stores trace`() {
        val trace = makeTrace()
        policy.addTrace(trace)

        assertThat(policy.getAll()).hasSize(1)
    }

    @Test
    fun `getById returns trace by correlation ID`() {
        val id = CorrelationId.generate()
        val trace = makeTrace(id)
        policy.addTrace(trace)

        val result = policy.getById(id)
        assertThat(result).isEqualTo(trace)
    }

    @Test
    fun `getById returns null for unknown ID`() {
        val result = policy.getById(CorrelationId.generate())
        assertThat(result).isNull()
    }

    @Test
    fun `getRecent returns last N traces`() {
        for (i in 1..10) {
            policy.addTrace(makeTrace())
        }

        val recent = policy.getRecent(3)
        assertThat(recent).hasSize(3)
    }

    @Test
    fun `deleteById removes trace`() {
        val id = CorrelationId.generate()
        policy.addTrace(makeTrace(id))

        val removed = policy.deleteById(id)

        assertThat(removed).isTrue()
        assertThat(policy.getById(id)).isNull()
    }

    @Test
    fun `deleteById returns false for unknown ID`() {
        val removed = policy.deleteById(CorrelationId.generate())
        assertThat(removed).isFalse()
    }

    @Test
    fun `clearAll removes all traces`() {
        policy.addTrace(makeTrace())
        policy.addTrace(makeTrace())

        policy.clearAll()

        assertThat(policy.getAll()).isEmpty()
    }

    @Test
    fun `cleanupExpired removes old traces`() {
        // Add an old trace (100 days ago)
        policy.addTrace(makeTrace(), Instant.now().minusSeconds(100 * 24 * 60 * 60))
        // Add a recent trace
        policy.addTrace(makeTrace(), Instant.now())

        val removed = policy.cleanupExpired()

        assertThat(removed).isEqualTo(1)
        assertThat(policy.getAll()).hasSize(1)
    }

    @Test
    fun `maxCachedTraces is enforced`() {
        val smallPolicy = BrainTracePolicy(maxCachedTraces = 3)
        for (i in 1..5) {
            smallPolicy.addTrace(makeTrace())
        }

        assertThat(smallPolicy.getAll()).hasSize(3)
    }

    @Test
    fun `getStats returns correct stats`() {
        policy.addTrace(makeTrace(), Instant.now().minusSeconds(60))
        policy.addTrace(makeTrace(), Instant.now())

        val stats = policy.getStats()
        assertThat(stats.cachedCount).isEqualTo(2)
        assertThat(stats.retentionDays).isEqualTo(90)
        assertThat(stats.oldestTimestamp).isNotNull()
        assertThat(stats.newestTimestamp).isNotNull()
    }

    @Test
    fun `exportForBackup returns all traces`() {
        policy.addTrace(makeTrace())
        policy.addTrace(makeTrace())

        val exported = policy.exportForBackup()
        assertThat(exported).hasSize(2)
    }
}
