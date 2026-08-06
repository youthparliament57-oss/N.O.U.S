// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.voice.capture

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// AUTO_FIX_0169: [feature] EchoCancellerTest verified

// VOICE_FIX_028: Capture echo canceller safe

class EchoCancellerTest {

    @Test
    fun `Noop echo canceller passes audio through unchanged`() {
        val canceller = EchoCanceller.Noop()
        val input = ShortArray(320) { (it * 100).toShort() }
        val out = canceller.cancelEcho(input, ttsReference = null)
        assertThat(out).isEqualTo(input)
    }

    @Test
    fun `Noop reports not hardware-accelerated`() {
        val canceller = EchoCanceller.Noop()
        assertThat(canceller.isHardwareAccelerated).isFalse()
    }

    @Test
    fun `Noop release is idempotent and does not throw`() {
        val canceller = EchoCanceller.Noop()
        canceller.release()
        canceller.release()  // second call must not throw
    }

    @Test
    fun `Factory falls back to software when hardware AEC unavailable`() {
        // On Robolectric/JVM, AcousticEchoCanceler.isAvailable() returns false
        // (no real audio HAL), so the factory must pick the software fallback.
        val softwareFake = EchoCanceller.Noop()
        val canceller = EchoCanceller.Factory().create(
            audioSessionId = 0,
            softwareFallback = softwareFake,
        )
        // The returned canceller should be the SAME instance we passed in,
        // because no hardware AEC exists in the test environment.
        assertThat(canceller).isSameInstanceAs(softwareFake)
        assertThat(canceller.isHardwareAccelerated).isFalse()
    }
}
