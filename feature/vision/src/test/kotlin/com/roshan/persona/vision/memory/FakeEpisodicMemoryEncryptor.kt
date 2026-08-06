// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.vision.memory

/**
 * Test-only fake [EpisodicMemoryEncryptor] — returns plaintext (no crypto).
 *
 * Removed from `src/main` (Phase 1 stub-removal): production code uses
 * [AesEpisodicMemoryEncryptor] which is backed by AndroidKeystore
 * AES-256-GCM (hardware-backed). AndroidKeystore is not available in JVM
 * unit tests, so this fake lives in the test source set so
 * [VisualEpisodicMemoryTest] can verify the ring-buffer + serialize/deserialize
 * logic without standing up a real cipher.
 */
class FakeEpisodicMemoryEncryptor : EpisodicMemoryEncryptor {
    override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
    override fun decrypt(ciphertext: ByteArray): ByteArray = ciphertext
}
