// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.brain.rule.storage

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.context.SessionId
import com.roshan.persona.brain.intent.Intent
import com.roshan.persona.brain.skill.ConversationStage
import com.roshan.persona.brain.skill.SkillOutput
import com.roshan.persona.common.CorrelationId
import com.roshan.persona.common.Result
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

// AUTO_FIX_0077: [feature] ConversationStateStorageTest verified

class ConversationStateStorageTest {

    private val storage = InMemoryConversationStateStorage()

    private fun createState(
        sessionId: String = "session-1",
        skillId: String = "call_skill",
    ): ConversationStateData {
        return ConversationStateData.create(
            sessionId = SessionId(sessionId),
            correlationId = CorrelationId.generate(),
            skillId = skillId,
            intent = Intent.Call(contact = null),
            partialOutput = SkillOutput.NeedsInput(
                skillId = skillId,
                prompt = "Who do you want to call?",
                inputType = com.roshan.persona.brain.skill.InputType.CONTACT,
            ),
        )
    }

    @Test
    fun `save and getActive returns state`() = runTest {
        val state = createState()

        storage.save(state)

        val active = storage.getActive(SessionId("session-1"))
        assertThat(active).isNotNull()
        val data = (active as Result.Success).data
        assertThat(data).isNotNull()
        assertThat(data!!.skillId).isEqualTo("call_skill")
        assertThat(data.stage).isEqualTo(ConversationStage.NEEDS_INPUT)
    }

    @Test
    fun `getActive returns null for nonexistent session`() = runTest {
        val active = storage.getActive(SessionId("nonexistent"))
        val data = (active as Result.Success).data
        assertThat(data).isNull()
    }

    @Test
    fun `markCompleted removes state`() = runTest {
        val state = createState()
        storage.save(state)

        storage.markCompleted(SessionId("session-1"))

        val active = storage.getActive(SessionId("session-1"))
        assertThat((active as Result.Success).data).isNull()
    }

    @Test
    fun `markExpired removes state`() = runTest {
        val state = createState()
        storage.save(state)

        storage.markExpired(SessionId("session-1"))

        val active = storage.getActive(SessionId("session-1"))
        assertThat((active as Result.Success).data).isNull()
    }

    @Test
    fun `deleteAllForSession removes all states for session`() = runTest {
        val state = createState()
        storage.save(state)

        storage.deleteAllForSession(SessionId("session-1"))

        val active = storage.getActive(SessionId("session-1"))
        assertThat((active as Result.Success).data).isNull()
    }

    @Test
    fun `expired state not returned by getActive`() = runTest {
        val state = createState().copy(
            createdAt = Instant.now().minusSeconds(600),  // 10 min ago
            expiresAt = Instant.now().minusSeconds(60),   // expired 1 min ago
        )
        storage.save(state)

        val active = storage.getActive(SessionId("session-1"))
        assertThat((active as Result.Success).data).isNull()
    }

    @Test
    fun `deleteExpired removes only expired states`() = runTest {
        val expiredState = createState(sessionId = "expired").copy(
            createdAt = Instant.now().minusSeconds(600),
            expiresAt = Instant.now().minusSeconds(60),
        )
        val activeState = createState(sessionId = "active")
        storage.save(expiredState)
        storage.save(activeState)

        val result = storage.deleteExpired()
        val deletedCount = (result as Result.Success).data
        assertThat(deletedCount).isEqualTo(1)

        // Active state should still exist
        val active = storage.getActive(SessionId("active"))
        assertThat((active as Result.Success).data).isNotNull()

        // Expired state should be gone
        val expired = storage.getActive(SessionId("expired"))
        assertThat((expired as Result.Success).data).isNull()
    }

    @Test
    fun `ConversationStateData isExpired returns true after TTL`() {
        val state = createState()
        val futureNow = Instant.now().plusSeconds(600)  // 10 min in future

        assertThat(state.isExpired(futureNow)).isTrue()
    }

    @Test
    fun `ConversationStateData isExpired returns false within TTL`() {
        val state = createState()
        val immediateNow = Instant.now()

        assertThat(state.isExpired(immediateNow)).isFalse()
    }

    @Test
    fun `create sets default TTL of 5 minutes`() {
        val state = createState()

        val durationSeconds = state.expiresAt.epochSecond - state.createdAt.epochSecond
        assertThat(durationSeconds).isEqualTo(300)  // 5 minutes = 300 seconds
    }

    @Test
    fun `multiple sessions are independent`() = runTest {
        val state1 = createState(sessionId = "session-1")
        val state2 = createState(sessionId = "session-2")
        storage.save(state1)
        storage.save(state2)

        storage.markCompleted(SessionId("session-1"))

        val active1 = storage.getActive(SessionId("session-1"))
        val active2 = storage.getActive(SessionId("session-2"))

        assertThat((active1 as Result.Success).data).isNull()
        assertThat((active2 as Result.Success).data).isNotNull()
    }

    @Test
    fun `clear removes all states`() = runTest {
        val state1 = createState(sessionId = "session-1")
        val state2 = createState(sessionId = "session-2")
        storage.save(state1)
        storage.save(state2)

        storage.clear()

        assertThat((storage.getActive(SessionId("session-1")) as Result.Success).data).isNull()
        assertThat((storage.getActive(SessionId("session-2")) as Result.Success).data).isNull()
    }
}
