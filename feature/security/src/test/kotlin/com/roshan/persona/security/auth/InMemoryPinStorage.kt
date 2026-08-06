// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.security.auth

import de.mkammerer.argon2.Argon2Factory
import timber.log.Timber
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.inject.Inject
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * NOUS — In-memory PIN storage (TEST ONLY).
 *
 * Strategy §12.3 — lightweight [PinStorage] implementation that keeps the
 * Argon2id hash in-memory only (lost on process death). Used by:
 *
 *   - Unit tests that need a fast, JVM-only `PinStorage` (no SQLCipher /
 *     Room / Keystore bootstrap).
 *   - The initial app-lock setup before the user's PIN is persisted to
 *     Room+SQLCipher (only relevant for `dev` flavor smoke tests).
 *
 * Production code MUST inject [SqlCipherPinStorage] — this class is
 * deliberately placed in the `test` source set so it cannot accidentally
 * end up in the prod APK.
 *
 * ## Argon2 parameters
 *
 * Uses weaker parameters than [SqlCipherPinStorage] (iterations=2 vs. 10)
 * to keep unit tests fast (~30 ms per hash vs. ~150 ms). Memory and
 * parallelism match the production impl.
 *
 * @see SqlCipherPinStorage
 * @see PinStorage
 */
class InMemoryPinStorage @Inject constructor() : PinStorage {

    private val lock = ReentrantReadWriteLock()
    private var storedHash: String? = null

    override fun setPin(pin: CharArray): Boolean = lock.write {
        try {
            val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)
            // Lighter params for tests (iterations=2). Memory and parallelism
            // match production so we exercise the same code paths.
            storedHash = argon2.hash(
                /* iterations = */ 2,
                /* memory = */ 65536,
                /* parallelism = */ 4,
                pin,
            )
            // Zero out the raw PIN from caller's array (best effort).
            pin.indices.forEach { pin[it] = '\u0000' }
            true
        } catch (e: Exception) {
            Timber.tag("NOUS.Security").w(e, "Argon2 PIN hash failed")
            false
        }
    }

    override fun verifyPin(pin: CharArray): Boolean = lock.read {
        val hash = storedHash ?: return false
        try {
            val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)
            val matches = argon2.verify(hash, pin)
            pin.indices.forEach { pin[it] = '\u0000' }
            matches
        } catch (e: Exception) {
            Timber.tag("NOUS.Security").w(e, "Argon2 PIN verify failed")
            false
        }
    }

    override fun isPinSet(): Boolean = lock.read { storedHash != null }

    override fun clearPin() = lock.write {
        storedHash = null
    }
}
