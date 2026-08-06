// Copyright (c) 2026 Roshan. All rights reserved.
package com.roshan.persona.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * NOUS — Master key (lives in Android Keystore, hardware-backed where available).
 *
 * The master key is the root of NOUS's key hierarchy:
 *
 *   MasterKey (Keystore, never extracted)
 *     ├── DbEncryptionKey (derived via HKDF-SHA256 + "nous/db")
 *     ├── FileEncryptionKey (derived via HKDF-SHA256 + "nous/files")
 *     ├── AuditLogKey (derived via HKDF-SHA256 + "nous/audit")
 *     └── TelemetrySigningKey (derived via HKDF-SHA256 + "nous/telemetry")
 *
 * The master key is generated on first launch and persists across app reinstalls
 * (because it's in Keystore, which survives app uninstall on most devices).
 *
 * On rooted devices, Keystore may be compromised — that's why we run [PlayIntegrity]
 * checks before relying on the master key.
 *
 * StrongBox (Pixel 6+ / Samsung S22+): used when available — keys never leave
 * the secure enclave even with kernel compromise.
 */
@Singleton
class MasterKeyManager @Inject constructor() {

    private val keystore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    /**
     * Get or create the master key. The key is:
     *   - AES-256
     *   - GCM mode (required for AndroidKeystore encryption/decryption operations)
     *   - No padding (GCM handles integrity via auth tag)
     *   - Hardware-backed (StrongBox if available, else TEE)
     *   - Not extractable
     *   - Not requiring user authentication (per-feature auth enforced at use)
     */
    fun getOrCreateMasterKey(): SecretKey {
        val existingKey = keystore.getKey(MASTER_KEY_ALIAS, null)
        if (existingKey != null) {
            return existingKey as? SecretKey
                ?: throw IllegalStateException("Master key exists but is not a SecretKey")
        }

        val builder = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(false)  // Per-feature auth at use site
            .setInvalidatedByBiometricEnrollment(false)  // Survive biometric changes

        // Try StrongBox first (Pixel 6+, Samsung S22+, etc.)
        // Fall back to TEE if not available.
        runCatching { builder.setIsStrongBoxBacked(true) }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    /**
     * Wipe the master key. Use only on "delete all my data" — clears all
     * derived keys' effectiveness.
     *
     * CRITICAL FIX #74: Added logging for audit trail.
     * This operation is IRREVERSIBLE - all encrypted data becomes unreadable.
     */
    fun wipe() {
        Timber.tag("NOUS.Security").w("MASTER KEY WIPE REQUESTED - All encrypted data will become unreadable")
        val result = runCatching { 
            keystore.deleteEntry(MASTER_KEY_ALIAS) 
        }
        if (result.isFailure) {
            Timber.tag("NOUS.Security").e(result.exceptionOrNull(), "Failed to wipe master key")
        } else {
            Timber.tag("NOUS.Security").i("Master key wiped successfully")
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val MASTER_KEY_ALIAS = "nous.master.v1"
    }
}
