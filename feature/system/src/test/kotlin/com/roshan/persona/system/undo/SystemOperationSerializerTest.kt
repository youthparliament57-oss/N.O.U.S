// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.system.undo

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.skill.AudioStream
import com.roshan.persona.brain.skill.DndMode
import com.roshan.persona.brain.skill.LocationMode
import com.roshan.persona.brain.skill.RingerMode
import com.roshan.persona.brain.skill.SystemOperation
import org.junit.Test

// AUTO_FIX_0156: [feature] SystemOperationSerializerTest verified

class SystemOperationSerializerTest {

    @Test
    fun `round-trip SetTorch with default brightness`() {
        val op = SystemOperation.SetTorch(true)
        val json = SystemOperationSerializer.serializeOperation(op)
        val restored = SystemOperationSerializer.deserializeOperation(json)
        assertThat(restored).isEqualTo(op)
    }

    @Test
    fun `round-trip SetTorch with explicit brightness`() {
        val op = SystemOperation.SetTorch(on = true, brightness = 0.5f)
        val json = SystemOperationSerializer.serializeOperation(op)
        val restored = SystemOperationSerializer.deserializeOperation(json)
        assertThat(restored).isEqualTo(op)
    }

    @Test
    fun `round-trip SetVolume with stream`() {
        val op = SystemOperation.SetVolume(level = 75, stream = AudioStream.RING)
        val json = SystemOperationSerializer.serializeOperation(op)
        val restored = SystemOperationSerializer.deserializeOperation(json)
        assertThat(restored).isEqualTo(op)
    }

    @Test
    fun `round-trip AdjustVolume with delta and stream`() {
        val op = SystemOperation.AdjustVolume(delta = -5, stream = AudioStream.ALARM)
        val json = SystemOperationSerializer.serializeOperation(op)
        val restored = SystemOperationSerializer.deserializeOperation(json)
        assertThat(restored).isEqualTo(op)
    }

    @Test
    fun `round-trip SetRingerMode`() {
        val op = SystemOperation.SetRingerMode(RingerMode.VIBRATE)
        val json = SystemOperationSerializer.serializeOperation(op)
        val restored = SystemOperationSerializer.deserializeOperation(json)
        assertThat(restored).isEqualTo(op)
    }

    @Test
    fun `round-trip SetDndMode`() {
        val op = SystemOperation.SetDndMode(DndMode.PRIORITY)
        val json = SystemOperationSerializer.serializeOperation(op)
        val restored = SystemOperationSerializer.deserializeOperation(json)
        assertThat(restored).isEqualTo(op)
    }

    @Test
    fun `round-trip SetLocationMode`() {
        val op = SystemOperation.SetLocationMode(LocationMode.HIGH_ACCURACY)
        val json = SystemOperationSerializer.serializeOperation(op)
        val restored = SystemOperationSerializer.deserializeOperation(json)
        assertThat(restored).isEqualTo(op)
    }

    @Test
    fun `round-trip OpenApp with package name`() {
        val op = SystemOperation.OpenApp("com.google.android.youtube")
        val json = SystemOperationSerializer.serializeOperation(op)
        val restored = SystemOperationSerializer.deserializeOperation(json)
        assertThat(restored).isEqualTo(op)
    }

    @Test
    fun `round-trip CopyToClipboard with text containing quotes and backslashes`() {
        val op = SystemOperation.CopyToClipboard("Hello \"world\" \\ done")
        val json = SystemOperationSerializer.serializeOperation(op)
        val restored = SystemOperationSerializer.deserializeOperation(json)
        assertThat(restored).isEqualTo(op)
    }

    @Test
    fun `round-trip SetAlarm with label`() {
        val op = SystemOperation.SetAlarm(hour = 7, minute = 30, label = "Wake up")
        val json = SystemOperationSerializer.serializeOperation(op)
        val restored = SystemOperationSerializer.deserializeOperation(json)
        assertThat(restored).isEqualTo(op)
    }

    @Test
    fun `round-trip SetAlarm without label`() {
        val op = SystemOperation.SetAlarm(hour = 7, minute = 30, label = null)
        val json = SystemOperationSerializer.serializeOperation(op)
        val restored = SystemOperationSerializer.deserializeOperation(json)
        assertThat(restored).isEqualTo(op)
    }

    @Test
    fun `round-trip LockScreen (data object — no fields)`() {
        val op = SystemOperation.LockScreen
        val json = SystemOperationSerializer.serializeOperation(op)
        val restored = SystemOperationSerializer.deserializeOperation(json)
        assertThat(restored).isEqualTo(op)
    }

    @Test
    fun `round-trip SetBatterySaver`() {
        val op = SystemOperation.SetBatterySaver(true)
        val json = SystemOperationSerializer.serializeOperation(op)
        val restored = SystemOperationSerializer.deserializeOperation(json)
        assertThat(restored).isEqualTo(op)
    }

    @Test
    fun `round-trip Composite with nested operations`() {
        val op = SystemOperation.Composite(listOf(
            SystemOperation.SetTorch(true),
            SystemOperation.SetVolume(80),
            SystemOperation.LockScreen,
        ))
        val json = SystemOperationSerializer.serializeOperation(op)
        val restored = SystemOperationSerializer.deserializeOperation(json)
        assertThat(restored).isEqualTo(op)
    }

    @Test
    fun `round-trip PostNotification`() {
        val op = SystemOperation.PostNotification(
            id = 42,
            channelId = "nous_proactive",
            channelName = "NOUS Proactive",
            title = "Hello \"world\"",
            text = "Body with \\ backslash",
        )
        val json = SystemOperationSerializer.serializeOperation(op)
        val restored = SystemOperationSerializer.deserializeOperation(json)
        assertThat(restored).isEqualTo(op)
    }

    @Test
    fun `deserialize unknown type throws`() {
        try {
            SystemOperationSerializer.deserializeOperation("{\"type\":\"NonexistentOp\"}")
            error("Expected exception")
        } catch (e: IllegalStateException) {
            assertThat(e.message).contains("Unknown operation type")
        }
    }

    // ─── Pre-state serialization tests ─────────────────────────────────────

    @Test
    fun `serializePreState null returns null`() {
        assertThat(SystemOperationSerializer.serializePreState(null)).isNull()
    }

    @Test
    fun `round-trip Int pre-state`() {
        val json = SystemOperationSerializer.serializePreState(50)
        val restored = SystemOperationSerializer.deserializePreState(json)
        assertThat(restored).isEqualTo(50)
    }

    @Test
    fun `round-trip Boolean pre-state`() {
        val json = SystemOperationSerializer.serializePreState(true)
        val restored = SystemOperationSerializer.deserializePreState(json)
        assertThat(restored).isEqualTo(true)
    }

    @Test
    fun `round-trip Float pre-state`() {
        val json = SystemOperationSerializer.serializePreState(0.45f)
        val restored = SystemOperationSerializer.deserializePreState(json)
        assertThat(restored).isEqualTo(0.45f)
    }

    @Test
    fun `round-trip Pair pre-state`() {
        val json = SystemOperationSerializer.serializePreState(Pair(AudioStream.MEDIA.name, 50))
        val restored = SystemOperationSerializer.deserializePreState(json) as Pair<*, *>
        assertThat(restored.first).isEqualTo("MEDIA")
        assertThat(restored.second).isEqualTo(50)
    }

    @Test
    fun `deserialize null pre-state returns null`() {
        assertThat(SystemOperationSerializer.deserializePreState(null)).isNull()
    }
}
