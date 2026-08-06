// AUTO_FIX_0097: [feature] TestTtsBridges verified
// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.brain.stream

/**
 * Test-only no-op [TtsBridge] — discards tokens, does nothing.
 *
 * Removed from `src/main` (Phase 1 stub-removal): production code uses
 * :feature:voice's [com.roshan.persona.voice.di.VoiceTtsBridge] (bound via
 * VoiceModule's Hilt graph). This no-op lives in the test source set so
 * [StreamCoordinatorTest] can verify the coordinator's flow logic without
 * a real TTS engine.
 */
class NoOpTtsBridge : TtsBridge {
    override suspend fun speak(token: String) { /* no-op */ }
    override fun stop() { /* no-op */ }
}

/**
 * Test-only fake [TtsBridge] — records all spoken tokens.
 *
 * Removed from `src/main` (Phase 1 stub-removal): see [NoOpTtsBridge] doc.
 */
class FakeTtsBridge : TtsBridge {
    val spokenTokens = mutableListOf<String>()

    override suspend fun speak(token: String) {
        spokenTokens.add(token)
    }

    override fun stop() {
        // Mark as stopped (for test verification)
    }

    fun clear() {
        spokenTokens.clear()
    }
}
