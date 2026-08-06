// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.memory.vector

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.abs

// MEMORY_FIX_003: Vector search validated

class Int8QuantizerTest {

    private val quantizer = Int8Quantizer()

    @Test
    fun `quantize and dequantize preserves approximate values`() {
        val original = floatArrayOf(0.1f, -0.2f, 0.3f, -0.4f, 0.5f)
        val quantized = quantizer.quantize(original)
        val dequantized = quantizer.dequantize(quantized)

        assertThat(dequantized.size).isEqualTo(original.size)
        for (i in original.indices) {
            assertThat(abs(dequantized[i] - original[i])).isLessThan(0.02f)
        }
    }

    @Test
    fun `quantize produces smaller output than float32`() {
        val vector = FloatArray(384) { it * 0.001f }
        val quantized = quantizer.quantize(vector)

        // 384 + 4 (scale) = 388 bytes vs 384 * 4 = 1536 bytes
        assertThat(quantized.size).isEqualTo(388)
        assertThat(quantized.size).isLessThan(384 * 4)
    }

    @Test
    fun `dotProductInt8 returns high similarity for identical vectors`() {
        val vector = FloatArray(384) { (it % 10) * 0.1f }
        val stored = quantizer.quantize(vector)

        val similarity = quantizer.dotProductInt8(vector, stored)

        // Identical vectors should have similarity close to 1.0
        assertThat(similarity).isGreaterThan(0.95f)
    }

    @Test
    fun `dotProductInt8 returns low similarity for orthogonal vectors`() {
        val v1 = FloatArray(384) { if (it < 192) 1f else 0f }
        val v2 = FloatArray(384) { if (it >= 192) 1f else 0f }
        val stored = quantizer.quantize(v2)

        val similarity = quantizer.dotProductInt8(v1, stored)

        assertThat(similarity).isLessThan(0.1f)
    }

    @Test
    fun `dotProductInt8 returns zero for zero vector`() {
        val zero = FloatArray(384) { 0f }
        val stored = quantizer.quantize(FloatArray(384) { 1f })

        val similarity = quantizer.dotProductInt8(zero, stored)

        assertThat(similarity).isEqualTo(0f)
    }

    @Test
    fun `batchSearchInt8 returns sorted results`() {
        val query = FloatArray(4) { 1f }
        val candidates = listOf(
            1L to quantizer.quantize(FloatArray(4) { 0f }),
            2L to quantizer.quantize(FloatArray(4) { 1f }),
            3L to quantizer.quantize(FloatArray(4) { 0.5f }),
        )

        val results = quantizer.batchSearchInt8(query, candidates)

        assertThat(results).hasSize(3)
        assertThat(results[0].first).isEqualTo(2L)  // most similar
        assertThat(results[1].first).isEqualTo(3L)
        assertThat(results[2].first).isEqualTo(1L)  // least similar
    }
}

class HnswIndexTest {

    private val index = HnswIndex(dimension = 4)

    @Test
    fun `addItem increases size`() {
        assertThat(index.size()).isEqualTo(0)
        index.addItem(floatArrayOf(1f, 0f, 0f, 0f), 1)
        assertThat(index.size()).isEqualTo(1)
    }

    @Test
    fun `search returns k nearest neighbors`() {
        index.addItem(floatArrayOf(1f, 0f, 0f, 0f), 1)
        index.addItem(floatArrayOf(0f, 1f, 0f, 0f), 2)
        index.addItem(floatArrayOf(1f, 1f, 0f, 0f), 3)

        val results = index.search(floatArrayOf(1f, 0f, 0f, 0f), k = 2)

        assertThat(results).hasSize(2)
        assertThat(results[0].first).isEqualTo(1L)  // exact match
    }

    @Test
    fun `softDelete excludes from search`() {
        index.addItem(floatArrayOf(1f, 0f, 0f, 0f), 1)
        index.addItem(floatArrayOf(0f, 1f, 0f, 0f), 2)
        index.softDelete(1)

        val results = index.search(floatArrayOf(1f, 0f, 0f, 0f), k = 5)

        assertThat(results).hasSize(1)
        assertThat(results[0].first).isEqualTo(2L)
        assertThat(index.size()).isEqualTo(1)
    }

    @Test
    fun `clear removes all vectors`() {
        index.addItem(floatArrayOf(1f, 0f, 0f, 0f), 1)
        index.addItem(floatArrayOf(0f, 1f, 0f, 0f), 2)
        index.clear()

        assertThat(index.size()).isEqualTo(0)
    }

    @Test
    fun `needsRebuild returns true for different versions`() {
        assertThat(index.needsRebuild("v1", "v2")).isTrue()
    }

    @Test
    fun `needsRebuild returns false for same version`() {
        assertThat(index.needsRebuild("v1", "v1")).isFalse()
    }

    @Test
    fun `search returns empty for empty index`() {
        val results = index.search(floatArrayOf(1f, 0f, 0f, 0f), k = 5)
        assertThat(results).isEmpty()
    }

    @Test
    fun `addItem after softDelete un-deletes`() {
        index.addItem(floatArrayOf(1f, 0f, 0f, 0f), 1)
        index.softDelete(1)
        assertThat(index.size()).isEqualTo(0)

        index.addItem(floatArrayOf(1f, 0f, 0f, 0f), 1)
        assertThat(index.size()).isEqualTo(1)
    }
}
