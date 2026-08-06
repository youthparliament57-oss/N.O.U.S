// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.brain.intent.compound

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.intent.CompoundExecutor
import com.roshan.persona.brain.intent.Intent
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0069: [feature] CompoundIntentDetectorTest verified

class CompoundIntentDetectorTest {

    private val detector = CompoundIntentDetector()

    @Test
    fun `single intent returns null`() = runTest {
        val result = detector.detect("torch on") { _ ->
            Intent.TorchOn
        }
        assertThat(result).isNull()
    }

    @Test
    fun `sequential compound with then`() = runTest {
        val result = detector.detect("torch on then call mom") { sub ->
            when {
                sub.contains("torch") -> Intent.TorchOn
                sub.contains("call") -> Intent.Call(contact = "mom")
                else -> null
            }
        }
        assertThat(result).isNotNull()
        assertThat(result!!.executor).isEqualTo(CompoundExecutor.SEQUENTIAL)
        assertThat(result.intents).hasSize(2)
        assertThat(result.intents[0]).isEqualTo(Intent.TorchOn)
        assertThat(result.intents[1]).isInstanceOf(Intent.Call::class.java)
    }

    @Test
    fun `parallel compound with and`() = runTest {
        val result = detector.detect("mute and dim screen") { sub ->
            when {
                sub.contains("mute") -> Intent.Mute
                sub.contains("dim") -> Intent.SetBrightness(level = 30)
                else -> null
            }
        }
        assertThat(result).isNotNull()
        assertThat(result!!.executor).isEqualTo(CompoundExecutor.PARALLEL)
        assertThat(result.intents).hasSize(2)
    }

    @Test
    fun `conditional compound with if`() = runTest {
        val result = detector.detect("if battery low then enable saver") { sub ->
            when {
                sub.contains("battery") -> Intent.Unknown(raw = sub)
                sub.contains("saver") -> Intent.SetBrightness(level = 0)
                else -> null
            }
        }
        assertThat(result).isNotNull()
        assertThat(result!!.executor).isEqualTo(CompoundExecutor.CONDITIONAL)
    }

    @Test
    fun `compound returns null when any sub-utterance fails`() = runTest {
        val result = detector.detect("torch on then xyzabc") { sub ->
            when {
                sub.contains("torch") -> Intent.TorchOn
                else -> null  // fails on second part
            }
        }
        assertThat(result).isNull()
    }

    @Test
    fun `after that connector splits sequentially`() = runTest {
        val result = detector.detect("call mom after that send sms") { sub ->
            when {
                sub.contains("call") -> Intent.Call(contact = "mom")
                sub.contains("sms") -> Intent.SendSms(contact = "mom", message = null)
                else -> null
            }
        }
        assertThat(result).isNotNull()
        assertThat(result!!.executor).isEqualTo(CompoundExecutor.SEQUENTIAL)
    }

    @Test
    fun `also connector splits in parallel`() = runTest {
        val result = detector.detect("torch on also mute") { sub ->
            when {
                sub.contains("torch") -> Intent.TorchOn
                sub.contains("mute") -> Intent.Mute
                else -> null
            }
        }
        assertThat(result).isNotNull()
        assertThat(result!!.executor).isEqualTo(CompoundExecutor.PARALLEL)
    }
}
