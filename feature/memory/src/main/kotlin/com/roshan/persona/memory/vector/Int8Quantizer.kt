// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.memory.vector

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * NOUS — INT8 Quantizer.
 *
 * Compresses float32 vectors (384-dim = 1536 bytes) to INT8 (384 + 4 = 388 bytes).
 * 4x compression with negligible recall loss.
 *
 * Supports:
 *   - [quantize]: float[] → byte[] (with 4-byte scale header)
 *   - [dequantize]: byte[] → float[]
 *   - [dotProductInt8]: direct INT8 dot product (no dequantization in search loop)
 *   - [batchSearchInt8]: batch search for HNSW candidates
 *
 * @see <a href="docs/strategy/module-3-strategy-v2.md">§9 Vector Storage — INT8</a>
 */
class Int8Quantizer {

    /**
     * Quantize float32 vector to INT8.
     *
     * Output format: [4-byte scale (float)] + [N bytes (int8 values)]
     * Size: N + 4 bytes (388 for 384-dim).
     */
    fun quantize(vector: FloatArray): ByteArray {
        require(vector.isNotEmpty()) { "Cannot quantize empty vector" }
        val maxAbs = vector.maxOfOrNull { abs(it) } ?: 0f
        val scale = if (maxAbs > 0) 127f / maxAbs else 1f
        val result = ByteArray(vector.size + 4)
        val scaleBits = java.lang.Float.floatToIntBits(scale)
        result[0] = (scaleBits and 0xFF).toByte()
        result[1] = ((scaleBits shr 8) and 0xFF).toByte()
        result[2] = ((scaleBits shr 16) and 0xFF).toByte()
        result[3] = ((scaleBits shr 24) and 0xFF).toByte()

        for (i in vector.indices) {
            result[i + 4] = (vector[i] * scale).toInt().coerceIn(-128, 127).toByte()
        }
        return result
    }

    /**
     * Dequantize INT8 vector back to float32.
     */
    fun dequantize(data: ByteArray): FloatArray {
        require(data.size > 4) { "Invalid quantized data: too short" }
        val scaleBits = (data[0].toInt() and 0xFF) or
            ((data[1].toInt() and 0xFF) shl 8) or
            ((data[2].toInt() and 0xFF) shl 16) or
            ((data[3].toInt() and 0xFF) shl 24)
        val scale = java.lang.Float.intBitsToFloat(scaleBits)
        val invScale = if (scale > 0) 1f / scale else 1f

        return FloatArray(data.size - 4) { i ->
            data[i + 4].toInt() * invScale
        }
    }

    /**
     * Direct INT8 dot-product — NO dequantization in search loop.
     *
     * Computes dot product directly between float32 query vector and INT8 stored vector.
     * Quantization scale factors cancel out in cosine similarity ratio, eliminating
     * per-element conversions and scale bit packing operations in the loop.
     *
     * @param queryVector the search query (float32)
     * @param storedData the stored INT8 vector (from memory_embeddings table)
     * @return cosine similarity score (0.0 to 1.0)
     */
    fun dotProductInt8(queryVector: FloatArray, storedData: ByteArray): Float {
        val dim = storedData.size - 4
        if (dim <= 0 || queryVector.size < dim) return 0f

        var dotProduct = 0f
        var queryNorm = 0f
        var storedNorm = 0L

        for (i in 0 until dim) {
            val q = queryVector[i]
            val s = storedData[i + 4].toInt()
            dotProduct += q * s
            queryNorm += q * q
            storedNorm += s * s
        }

        if (queryNorm <= 0f || storedNorm <= 0L) return 0f
        val denom = sqrt(queryNorm) * sqrt(storedNorm.toFloat())
        return dotProduct / denom
    }

    /**
     * Batch INT8 search — optimized for HNSW traversal.
     *
     * Precomputes query vector norm once across all candidates to avoid redundant computation.
     *
     * @param queryVector the search query (float32)
     * @param candidates list of (memoryId, INT8 data) from HNSW
     * @return list of (memoryId, similarity) sorted by similarity descending
     */
    fun batchSearchInt8(
        queryVector: FloatArray,
        candidates: List<Pair<Long, ByteArray>>,
    ): List<Pair<Long, Float>> {
        var queryNorm = 0f
        for (i in queryVector.indices) {
            val q = queryVector[i]
            queryNorm += q * q
        }
        if (queryNorm <= 0f) {
            return candidates.map { (id, _) -> id to 0f }
        }
        val queryNormSqrt = sqrt(queryNorm)

        return candidates
            .map { (id, data) -> id to dotProductInt8WithQueryNorm(queryVector, queryNormSqrt, data) }
            .sortedByDescending { it.second }
    }

    private fun dotProductInt8WithQueryNorm(
        queryVector: FloatArray,
        queryNormSqrt: Float,
        storedData: ByteArray,
    ): Float {
        val dim = storedData.size - 4
        if (dim <= 0 || queryVector.size < dim) return 0f

        var dotProduct = 0f
        var storedNorm = 0L

        for (i in 0 until dim) {
            val q = queryVector[i]
            val s = storedData[i + 4].toInt()
            dotProduct += q * s
            storedNorm += s * s
        }

        if (storedNorm <= 0L) return 0f
        val denom = queryNormSqrt * sqrt(storedNorm.toFloat())
        return dotProduct / denom
    }
}
