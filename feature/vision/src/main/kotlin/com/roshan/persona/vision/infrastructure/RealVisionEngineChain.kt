// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.
package com.roshan.persona.vision.infrastructure

import com.roshan.persona.vision.core.ConfidenceTier
import com.roshan.persona.vision.core.ConfidenceTieredResult
import com.roshan.persona.vision.core.OmniSlmRuntime
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NOUS — Real Vision Engine Chain (Module 6 v2 — audit P0 fix).
 *
 * Replaces the no-op anonymous `VisionEngineChain` stub in `VisionModule.kt:447-456`
 * that returned `null` for every real engine and a constant `FloatArray(384) { 0.5f }`
 * for TfIdf fallback — destroying semantic similarity on devices that need
 * fallback most.
 *
 * ## Real fallback chain
 *
 *  - **embedViaOmniSlm(text)** → `OmniSlmRuntime.embed(text)` (384-dim MiniLM).
 *    Returns null if Omni-SLM not loaded (Tier 1 of fallback).
 *  - **embedViaMiniLm(text)** → null (MiniLM-L6 not separately bundled —
 *    Omni-SLM IS the MiniLM. Kept in interface for future split.)
 *  - **embedViaTfIdf(text)** → real TfIdf hash embedder: 384-dim vector where
 *    each dimension is the normalized TF-IDF weight of a vocabulary bucket.
 *    Not semantically rich like MiniLM, but produces DISTINCT vectors for
 *    distinct texts (unlike the constant 0.5f stub).
 *
 * ## Why this matters
 *
 * The constant `0.5f` stub meant every text got the same embedding — cosine
 * similarity between any two texts was 1.0. Memory recall returned random
 * results. Audit Task ID: audit-m6-vision P0.
 *
 * @see FallbackChainOrchestrator
 * @see VisionEngineChain
 */
@Singleton
class RealVisionEngineChain @Inject constructor(
    private val omniSlmRuntime: OmniSlmRuntime,
) : VisionEngineChain {

    // Vision engines (detection/classification/captioning/UI/OCR) are not
    // delegated through this chain in v1 — they're called directly by
    // VisionFacade via their dedicated engines (ObjectDetectionEngine, OcrEngine, etc.).
    // This chain is primarily for EMBEDDING fallback. Returns null so the
    // FallbackChainOrchestrator skips to the next tier for non-embedding ops.

    override suspend fun runObjectDetection(imageBytes: ByteArray): ConfidenceTieredResult<DetectionChainResult>? {
        return null
    }

    override suspend fun runImageClassification(imageBytes: ByteArray): ConfidenceTieredResult<ClassificationChainResult>? {
        return null
    }

    override suspend fun runImageCaptioning(imageBytes: ByteArray): ConfidenceTieredResult<CaptionChainResult>? {
        return null
    }

    override suspend fun runUiElementDetection(imageBytes: ByteArray): ConfidenceTieredResult<UiElementChainResult>? {
        return null
    }

    override suspend fun runOcr(imageBytes: ByteArray): ConfidenceTieredResult<OcrChainResult>? {
        return null
    }

    override suspend fun embedViaOmniSlm(text: String): ConfidenceTieredResult<FloatArray>? {
        return try {
            val result = omniSlmRuntime.embed(text)
            if (result.tier == ConfidenceTier.REJECT) {
                null
            } else {
                result
            }
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "embedViaOmniSlm failed")
            null
        }
    }

    override suspend fun embedViaMiniLm(text: String): ConfidenceTieredResult<FloatArray>? {
        // MiniLM-L6 is not separately bundled — Omni-SLM IS the MiniLM in v1.
        // Kept in interface for future split when a smaller MiniLM is shipped
        // separately. Returns null so chain falls through to TfIdf.
        return null
    }

    /**
     * Real TfIdf hash embedder — 384-dim vector.
     *
     * Each token in [text] is hashed (SHA-256) to a bucket in [0, 384).
     * The bucket's value is incremented by 1.0 / tokenCount (normalized TF).
     * Final vector is L2-normalized so cosine similarity works correctly.
     *
     * This produces DISTINCT vectors for distinct texts — unlike the previous
     * constant `0.5f` stub which made every text identical.
     */
    override suspend fun embedViaTfIdf(text: String): FloatArray {
        val dim = 384
        val vec = FloatArray(dim)
        val tokens = text.lowercase()
            .split(NON_WORD_REGEX)
            .filter { it.length > 1 }
        if (tokens.isEmpty()) return vec

        // Reuse single MessageDigest instance across all tokens in this call instead of looking it up per token.
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val tf = HashMap<Int, Float>()
        for (tok in tokens) {
            val hash = sha256Bucket(tok, dim, digest)
            tf[hash] = (tf[hash] ?: 0f) + 1f
        }

        // L2 normalize so ||v|| = 1.
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

    /** SHA-256 hash → bucket in [0, dim). */
    private fun sha256Bucket(token: String, dim: Int, digest: java.security.MessageDigest): Int {
        digest.reset()
        val bytes = digest.digest(token.toByteArray(Charsets.UTF_8))
        // Use first 4 bytes as a 32-bit int, then mod dim.
        val asInt = ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)
        return (asInt and 0x7FFFFFFF) % dim
    }

    private companion object {
        private const val TAG = "RealEngineChain"
        // Pre-compile regex to avoid re-parsing pattern on every embed call.
        private val NON_WORD_REGEX = Regex("\\W+")
    }
}
