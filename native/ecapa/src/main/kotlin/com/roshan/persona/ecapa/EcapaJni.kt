// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.ecapa

import timber.log.Timber

/**
 * NOUS — ECAPA-TDNN Speaker Verification JNI Bridge.
 *
 * Kotlin-side JNI declarations for the ECAPA-TDNN native library.
 * The C++ implementation lives in `:native:ecapa/src/main/cpp/jarvis_ecapa.cpp`
 * (loaded as `libjarvis_ecapa.so`).
 *
 * ## Features
 *   - Speaker embedding extraction (192-dim vector)
 *   - Speaker verification (cosine similarity comparison)
 *   - Speaker enrollment (store embeddings for later verification)
 *   - Graceful fallback when native library unavailable
 *
 * ## Native Library Loading
 *
 * Loaded lazily on first use. If the library is not present (dev build
 * without pre-built .so), [ensureLoaded] returns false and callers
 * should fall back to alternative authentication methods.
 */
object EcapaJni {
    private const val TAG = "EcapaJni"

    @Volatile
    private var loaded: Boolean = false
    @Volatile
    private var loadFailed: Boolean = false

    /**
     * Load the native library. Safe to call multiple times.
     *
     * @return true if loaded successfully, false if library not available
     */
    @Synchronized
    fun ensureLoaded(): Boolean {
        if (loaded) return true
        if (loadFailed) return false
        return try {
            System.loadLibrary("jarvis_ecapa")
            loaded = true
            Timber.tag(TAG).i("Native library 'jarvis_ecapa' loaded.")
            true
        } catch (e: UnsatisfiedLinkError) {
            loadFailed = true
            Timber.tag(TAG).w("libjarvis_ecapa.so not available — Speaker verification disabled.", e)
            false
        } catch (e: SecurityException) {
            loadFailed = true
            Timber.tag(TAG).w("Security manager blocked library load.", e)
            false
        }
    }

    /** Whether the native library is loaded. */
    fun isLoaded(): Boolean = loaded

    /** Reset load state. For tests only. */
    internal fun resetLoadState() {
        loaded = false
        loadFailed = false
    }

    // ─── JNI Declarations ───────────────────────────────────────────

    /**
     * Initialize ECAPA-TDNN model with the given model path.
     *
     * @param modelPath absolute path to the model file
     * @return native model handle (>0) on success, 0 on failure
     */
    @JvmStatic
    external fun nativeInitModel(modelPath: String): Long

    /**
     * Free an ECAPA model and release native memory.
     *
     * @param modelHandle native model handle from [nativeInitModel]
     */
    @JvmStatic
    external fun nativeFreeModel(modelHandle: Long)

    /**
     * Extract speaker embedding from audio.
     *
     * @param modelHandle native model handle
     * @param samples 16 kHz, 16-bit mono PCM samples
     * @param sampleRate sample rate of audio (typically 16000)
     * @param numChannels number of audio channels (1 = mono)
     * @return float array embedding vector (typically 192 floats), or empty on failure
     */
    @JvmStatic
    external fun nativeEmbed(
        modelHandle: Long,
        samples: ShortArray,
        sampleRate: Int,
        numChannels: Int,
    ): FloatArray

    /**
     * Verify if two embeddings belong to the same speaker.
     *
     * @param modelHandle native model handle
     * @param embedding1 first speaker embedding
     * @param embedding2 second speaker embedding
     * @param threshold similarity threshold for positive match (typically 0.3-0.5)
     * @return cosine similarity score (0.0 to 1.0), higher = more similar
     */
    @JvmStatic
    external fun nativeVerify(
        modelHandle: Long,
        embedding1: FloatArray,
        embedding2: FloatArray,
        threshold: Double,
    ): Float

    /**
     * Enroll a speaker by storing their embedding.
     *
     * @param modelHandle native model handle
     * @param speakerId unique identifier for the speaker
     * @param embedding speaker's embedding vector
     * @return true if enrollment succeeded
     */
    @JvmStatic
    external fun nativeEnroll(
        modelHandle: Long,
        speakerId: String,
        embedding: FloatArray,
    ): Boolean

    /**
     * Check if the native library is actually available (not stub mode).
     *
     * @return true if full ECAPA functionality is available
     */
    @JvmStatic
    external fun nativeIsAvailable(): Boolean
}
