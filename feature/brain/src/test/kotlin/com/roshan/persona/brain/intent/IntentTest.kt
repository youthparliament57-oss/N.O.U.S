// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.brain.intent

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.common.CorrelationId
import org.junit.Test

// AUTO_FIX_0064: [feature] IntentTest verified

class IntentTest {

    @Test
    fun `Intent TorchOn is object singleton`() {
        val a: Intent = Intent.TorchOn
        val b: Intent = Intent.TorchOn

        assertThat(a).isSameInstanceAs(b)
    }

    @Test
    fun `Intent Call with contact slot`() {
        val intent = Intent.Call(contact = "Mom")

        assertThat(intent).isInstanceOf(Intent.Call::class.java)
        assertThat((intent as Intent.Call).contact).isEqualTo("Mom")
    }

    @Test
    fun `Intent Call with null contact triggers NeedsInput`() {
        val intent = Intent.Call(contact = null)

        assertThat((intent as Intent.Call).contact).isNull()
    }

    @Test
    fun `Intent Compound with SEQUENTIAL executor`() {
        val intent = Intent.Compound(
            intents = listOf(Intent.TorchOn, Intent.Call("Mom")),
            executor = CompoundExecutor.SEQUENTIAL,
        )

        assertThat(intent).isInstanceOf(Intent.Compound::class.java)
        assertThat((intent as Intent.Compound).intents).hasSize(2)
        assertThat(intent.executor).isEqualTo(CompoundExecutor.SEQUENTIAL)
    }

    @Test
    fun `Intent Unknown carries hint and raw input`() {
        val intent = Intent.Unknown(
            hint = Intent.Question::class,
            raw = "what's the meaning of life",
        )

        assertThat(intent).isInstanceOf(Intent.Unknown::class.java)
        assertThat((intent as Intent.Unknown).raw).isEqualTo("what's the meaning of life")
        assertThat(intent.hint).isEqualTo(Intent.Question::class)
    }

    @Test
    fun `IntentClassification high confidence is final`() {
        val classification = IntentClassification(
            intent = Intent.TorchOn,
            confidence = 0.95f,
            extractor = ExtractorType.REGEX,
            extractedSlots = emptyMap(),
            durationNanos = 1_000_000,
            correlationId = CorrelationId.generate(),
            usedNer = false,
            isCompound = false,
        )

        assertThat(classification.isFinal).isTrue()
        assertThat(classification.shouldDefer).isFalse()
    }

    @Test
    fun `IntentClassification low confidence should defer`() {
        val classification = IntentClassification(
            intent = Intent.TorchOn,
            confidence = 0.6f,
            extractor = ExtractorType.EMBEDDING,
            extractedSlots = emptyMap(),
            durationNanos = 25_000_000,
            correlationId = CorrelationId.generate(),
            usedNer = false,
            isCompound = false,
        )

        assertThat(classification.isFinal).isFalse()
        assertThat(classification.shouldDefer).isTrue()
    }

    @Test
    fun `IntentClassification with Unknown intent never final`() {
        val classification = IntentClassification(
            intent = Intent.Unknown(raw = "xyz"),
            confidence = 0.95f,
            extractor = ExtractorType.EMBEDDING,
            extractedSlots = emptyMap(),
            durationNanos = 80_000_000,
            correlationId = CorrelationId.generate(),
            usedNer = false,
            isCompound = false,
        )

        assertThat(classification.isFinal).isFalse()
    }

    @Test
    fun `ExtractorType order matches reliability`() {
        assertThat(ExtractorType.REGEX.ordinal).isLessThan(ExtractorType.KEYWORD.ordinal)
        assertThat(ExtractorType.KEYWORD.ordinal).isLessThan(ExtractorType.EMBEDDING.ordinal)
    }
}
