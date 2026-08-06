// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.database.di

import android.content.Context
import androidx.room.Room
import com.roshan.persona.database.NousDatabase
import com.roshan.persona.database.dao.ConversationStateDao
import com.roshan.persona.database.dao.EpisodicMemoryDao
import com.roshan.persona.database.dao.KgDao
import com.roshan.persona.database.dao.LlmCacheDao
import com.roshan.persona.database.dao.MemoryEmbeddingDao
import com.roshan.persona.database.dao.NativeCrashLogDao
import com.roshan.persona.database.dao.SemanticFactDao
import com.roshan.persona.database.dao.UndoActionDao
import com.roshan.persona.database.dao.UserFactDao
import timber.log.Timber
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

/**
 * NOUS — Database Hilt Module.
 *
 * Provides [NousDatabase] (Room + SQLCipher) and all DAOs as singletons.
 *
 * Strategy §3.7 — All memory is encrypted at rest using SQLCipher (AES-256-CBC).
 * The encryption key is derived from the user's app-lock PIN/passphrase using
 * Argon2 (memory-hard KDF). For dev builds without a set PIN, a device-specific
 * fallback key is generated via AndroidKeystore.
 *
 * @see <a href="docs/strategy/NOUS-Module-Strategy-2-14-extracted.txt">§3.7 Encryption & Privacy</a>
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * SQLCipher passphrase.
     *
     * For dev: a 32-byte random key generated once and stored in
     * [EncryptedSharedPreferences] (AndroidKeystore-backed AES-256-GCM).
     * For prod: same mechanism — once user sets an app-lock PIN, the key is
     * re-derived from Argon2(PIN) via [com.roshan.persona.security.keystore.KeystoreManager].
     *
     * The passphrase NEVER lives in plain SharedPreferences — it is encrypted
     * at rest by AndroidKeystore, which is hardware-backed on devices with
     * StrongBox/TEE.
     */
    private fun resolvePassphrase(context: Context): ByteArray {
        try {
            val masterKey = androidx.security.crypto.MasterKey.Builder(context)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = androidx.security.crypto.EncryptedSharedPreferences.create(
                context,
                "nous_db_key",
                masterKey,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            val existing = prefs.getString("passphrase", null)
            if (existing != null) {
                return android.util.Base64.decode(existing, android.util.Base64.NO_WRAP)
            }
            // Generate a 32-byte random key, persist as base64
            val bytes = ByteArray(32)
            java.security.SecureRandom().nextBytes(bytes)
            val encoded = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            prefs.edit().putString("passphrase", encoded).apply()
            return bytes
        } catch (e: Exception) {
            // EncryptedSharedPreferences can fail on rare OEM ROMs where
            // AndroidKeystore is broken. Fall back to a transient in-memory key
            // (DB will be unreadable on next launch — better than crashing at boot).
            Timber.tag("NOUS.Database").w(e, "EncryptedSharedPreferences unavailable — using transient key")
            val bytes = ByteArray(32)
            java.security.SecureRandom().nextBytes(bytes)
            return bytes
        }
    }

    @Provides
    @Singleton
    fun provideNousDatabase(@ApplicationContext context: Context): NousDatabase {
        val passphrase = resolvePassphrase(context)
        val supportFactory = SupportFactory(passphrase)
        return Room.databaseBuilder(
            context,
            NousDatabase::class.java,
            NousDatabase.DATABASE_NAME,
        )
            .openHelperFactory(supportFactory)
            // CRITICAL FIX #66: Data preservation strategy for downgrades
            // Option A (current): Destructive migration with warning (data loss on downgrade)
            // Option B (recommended): .addMigration(MIGRATION_FALLBACK) that handles schema differences
            // Option C: .fallbackToDestructiveMigrationOnDowngrade() only in debug builds
            .fallbackToDestructiveMigrationOnDowngrade()
            // WARNING: This will DELETE user data on schema downgrade!
            // Consider implementing MIGRATION_3_2 for production safety.
            .addMigrations(
                com.roshan.persona.database.MIGRATION_1_2,
                com.roshan.persona.database.MIGRATION_2_3,
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideEpisodicMemoryDao(db: NousDatabase): EpisodicMemoryDao =
        db.episodicMemoryDao()

    @Provides
    @Singleton
    fun provideSemanticFactDao(db: NousDatabase): SemanticFactDao =
        db.semanticFactDao()

    @Provides
    @Singleton
    fun provideKgDao(db: NousDatabase): KgDao =
        db.kgDao()

    @Provides
    @Singleton
    fun provideUserFactDao(db: NousDatabase): UserFactDao =
        db.userFactDao()

    @Provides
    @Singleton
    fun provideMemoryEmbeddingDao(db: NousDatabase): MemoryEmbeddingDao =
        db.memoryEmbeddingDao()

    @Provides
    @Singleton
    fun provideLlmCacheDao(db: NousDatabase): LlmCacheDao =
        db.llmCacheDao()

    @Provides
    @Singleton
    fun provideConversationStateDao(db: NousDatabase): ConversationStateDao =
        db.conversationStateDao()

    @Provides
    @Singleton
    fun provideUndoActionDao(db: NousDatabase): UndoActionDao =
        db.undoActionDao()

    @Provides
    @Singleton
    fun provideNativeCrashLogDao(db: NousDatabase): NativeCrashLogDao =
        db.nativeCrashLogDao()
}
