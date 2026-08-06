// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.wakeword

import timber.log.Timber

/**
 * NOUS — Wake Word Detection JNI Bridge.
 *
 * Kotlin-side JNI declarations for the wake word detection native library.
 * The C++ implementation lives in `:native:wakeword/src/main/cpp/jarvis_wakeword.cpp`
 * (loaded as `libjarvis_wakeword.so`).
 *
 * ## Features
 *   - Real-time wake word detection from audio stream
 *   - Configurable detection threshold
 *   - Low-power processing suitable for always-on listening
 *   - Graceful fallback when native library unavailable
 *
 * ## Native Library Loading
 *
 * Loaded lazily on first use. If the library is not present (dev build
 * without pre-built .so), [ensureLoaded] returns false and callers
 * should fall back to alternative wake word detection (e.g., server-side).
 */
object WakewordJni {
    private const val TAG = "WakewordJni"

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
            System.loadLibrary("jarvis_wakeword")
            loaded = true
            Timber.tag(TAG).i("Native library 'jarvis_wakeword' loaded.")
            true
        } catch (e: UnsatisfiedLinkError) {
            loadFailed = true
            Timber.tag(TAG).w("libjarvis_wakeword.so not available — Wake word detection disabled.", e)
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
     * Initialize wake word detector with the given model.
     *
     * @param modelPath absolute path to the wake word model file
     * @param sampleRate expected audio sample rate (typically 16000)
     * @return true if initialization succeeded
     */
    @JvmStatic
    external fun nativeInitDetector(modelPath: String, sampleRate: Int): Boolean

    /**
     * Destroy the wake word detector and release resources.
     */
    @JvmStatic
    external fun nativeDestroyDetector()

    /**
     * Process audio buffer and return per-frame confidence scores.
     *
     * @param samples 16 kHz, 16-bit mono PCM samples
     * @param sampleRate sample rate of audio
     * @return float array of confidence scores (one per processed frame)
     */
    @JvmStatic
    external fun nativeProcessAudio(samples: ShortArray, sampleRate: Int): FloatArray

    /**
     * Check if wake word was detected in the given audio buffer.
     *
     * @param samples 16 kHz, 16-bit mono PCM samples
     * @param sampleRate sample rate of audio
     * @param threshold confidence threshold for detection (typically 0.3-0.7)
     * @return true if wake word detected above threshold
     */
    @JvmStatic
    external fun nativeDetect(
        samples: ShortArray,
        sampleRate: Int,
        threshold: Float,
    ): Boolean

    /**
     * Reset internal detector state (clear buffers).
     */
    @JvmStatic
    external fun nativeReset()

    /**
     * Check if the native library is actually available (not stub mode).
     *
     * @return true if full wake word detection is available
     */
    @JvmStatic
    external fun nativeIsAvailable(): Boolean

    /**
     * Get required sample rate for this detector.
     *
     * @return sample rate in Hz (typically 16000)
     */
    @JvmStatic
    external fun nativeGetRequiredSampleRate(): Int

    /**
     * Get required frame size for this detector.
     *
     * @return number of samples per frame (typically 160 for 10ms at 16kHz)
     */
    @JvmStatic
    external fun nativeGetRequiredFrameSize(): Int
}
