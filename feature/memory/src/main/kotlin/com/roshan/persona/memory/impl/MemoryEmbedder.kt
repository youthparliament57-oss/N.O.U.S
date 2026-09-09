// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.
package com.roshan.persona.memory.impl

/**
 * NOUS — Memory Embedder Interface.
 *
 * Abstraction over the embedding model used by [MemoryInterfaceImpl] to
 * compute semantic vectors for stored memories.
 *
 * ## Implementations
 *
 *  - **OmniSlmEmbedderAdapter** (production) — wraps Module 6's `OmniSlmRuntime.embed()`
 *    to produce 384-dim MiniLM embeddings.
 *  - **TfIdfHashEmbedder** (fallback) — pure-Kotlin SHA-256 hash embedder
 *    (same as `RealVisionEngineChain.embedViaTfIdf`). No native dependency.
 *  - **NoOpEmbedder** (tests) — returns zero vector.
 *
 * @see MemoryInterfaceImpl
 */
interface MemoryEmbedder {
    /**
     * Embed [text] into a fixed-dimensional float vector.
     *
     * @return the embedding (dimension depends on impl — 384 for MiniLM).
     */
    suspend fun embed(text: String): FloatArray

    /** The dimensionality of vectors produced by [embed]. */
    val dimension: Int
}

/**
 * Fallback embedder — pure-Kotlin SHA-256 hash TF-IDF.
 *
 * Produces 384-dim vectors (same as MiniLM) so it's compatible with the
 * HNSW index. Not semantically rich, but DISTINCT for distinct texts —
 * vector search will at least find exact-token matches.
 *
 * Used when Module 6's Omni-SLM is not yet loaded (early boot) or when
 * the native library is unavailable.
 */
class TfIdfHashEmbedder(
    override val dimension: Int = 384,
) : MemoryEmbedder {
    override suspend fun embed(text: String): FloatArray {
        val vec = FloatArray(dimension)
        val tokens = text.lowercase()
            .split(NON_WORD_REGEX)
            .filter { it.length > 1 }
        if (tokens.isEmpty()) return vec

        // Reuse single MessageDigest instance across all tokens in this call instead of looking it up per token.
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val tf = HashMap<Int, Float>()
        for (tok in tokens) {
            val hash = sha256Bucket(tok, dimension, digest)
            tf[hash] = (tf[hash] ?: 0f) + 1f
        }
        var norm = 0.0
        for ((bucket, weight) in tf) {
            vec[bucket] = weight / tokens.size
            norm += (vec[bucket] * vec[bucket]).toDouble()
        }
        norm = kotlin.math.sqrt(norm)
        if (norm > 0) {
            for (i in vec.indices) vec[i] = (vec[i] / norm).toFloat()
        }
        return vec
    }

    private fun sha256Bucket(token: String, dim: Int, digest: java.security.MessageDigest): Int {
        digest.reset()
        val bytes = digest.digest(token.toByteArray(Charsets.UTF_8))
        val asInt = ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)
        return (asInt and 0x7FFFFFFF) % dim
    }

    private companion object {
        // Pre-compile regex to avoid re-parsing pattern on every embed call.
        private val NON_WORD_REGEX = Regex("\\W+")
    }
}
