// [MEDIUM_PRIORITY] RESOURCE_SECURITY: Cipher mode appropriate
// [MEDIUM_PRIORITY] CODE_QUALITY: Class structure reviewed
// [MEDIUM_PRIORITY] UX_POLISH: Success feedback

// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.init

import android.content.Context
import androidx.room.RoomDatabase
import com.roshan.persona.database.NousDatabase
import com.roshan.persona.di.AppInitializer
import com.roshan.persona.di.Priority
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NOUS — Database Initializer.
 *
 * Strategy §3.6: Opens the SQLCipher-encrypted Room database at app boot.
 * Triggers schema migration if needed (MIGRATION_1_2, MIGRATION_2_3).
 *
 * CRITICAL priority + blocking — app cannot function without DB.
 */
@Singleton
class DatabaseInitializer @Inject constructor(
    private val database: NousDatabase,
) : AppInitializer {
    override val priority: Priority = Priority.CRITICAL
    override val isBlocking: Boolean = true
    
    override fun initialize(context: Context) {
        // Trigger DB open (Room lazily opens on first query — we force it here).
        // This also runs any pending migrations synchronously.
        try {
            database.openHelper.writableDatabase
            Timber.tag("NOUS.Init").i("Database opened successfully (v${NousDatabase.DATABASE_VERSION})")
        } catch (e: Exception) {
            Timber.tag("NOUS.Init").e(e, "Database initialization failed")
            throw IllegalStateException("Database init failed", e)
        }
    }
}

/**
 * NOUS — Timber Logging Initializer.
 *
 * Strategy §2.4: Plants a Timber tree for structured logging in dev builds.
 * In prod, plants a release tree that strips PII and writes to file (TODO).
 */
@Singleton
class TimberInitializer @Inject constructor() : AppInitializer {
    override val priority: Priority = Priority.CRITICAL
    override val isBlocking: Boolean = true
    
    override fun initialize(context: Context) {
        if (Timber.treeCount == 0) {
            Timber.plant(object : Timber.DebugTree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    // PII scrubbing happens in :core:telemetry's NousProductionTree
                    // For dev, plain DebugTree is fine.
                    super.log(priority, "NOUS.$tag", message, t)
                }
            })
            Timber.tag("Init").i("Timber planted (dev DebugTree)")
        }
    }
}

/**
 * NOUS — Preload Initializer.
 *
 * Strategy §4.7: Predictive preloading of LLM model is triggered when device
 * is charging + idle. For now, this initializer just logs intent — actual
 * preload logic lives in :feature:llm's PredictiveModelPreloader (TODO wire).
 */
@Singleton
class PreloadInitializer @Inject constructor() : AppInitializer {
    override val priority: Priority = Priority.LAZY
    override val isBlocking: Boolean = false
    
    override fun initialize(context: Context) {
        Timber.tag("NOUS.Init").d("Preload initializer ran (no-op until :feature:llm wires)")
        // TODO: When :feature:llm provides PredictiveModelPreloader via Hilt,
        // inject it here and call checkAndPreload().
    }
}
