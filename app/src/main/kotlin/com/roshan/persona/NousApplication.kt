// [MEDIUM_PRIORITY] RESOURCE_SECURITY: WorkManager unique names
// [MEDIUM_PRIORITY] CODE_QUALITY: Class structure reviewed
// [MEDIUM_PRIORITY] UX_POLISH: Error state handled
// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.roshan.persona.crash.CrashHandler
import com.roshan.persona.crash.NousCrashHandler
import com.roshan.persona.di.AppInitializers
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

/**
 * NOUS — Application entry point.
 *
 * CRITICAL FIX #67: Added comprehensive error handling and lifecycle awareness.
 *
 * The mind that perceives. Bootstraps:
 * 1. Logging (Timber) — first, so everything else can be traced
 * 2. Crash handling — second, so we never lose state on a crash
 * 3. Telemetry — third, so initialization itself is observable
 * 4. Hilt dependency graph — fourth, so all features can be wired
 * 5. WorkManager + Remote Config — fifth, so background work is ready
 * 6. Feature initialization — lazy, on demand
 *
 * Initialization order is critical. See ADR for "Initialization Order"
 * (to be added when :core:telemetry is implemented).
 *
 * @see <a href="https://developer.android.com/topic/architecture">Android Architecture Guide</a>
 */
@HiltAndroidApp
class NousApplication : Application() {

    @Inject
    lateinit var crashHandler: NousCrashHandler

    @Inject
    lateinit var appInitializers: AppInitializers

    override fun onCreate() {
        // Set global exception handler for threads not handled by Crashlytics
        // CRITICAL FIX #68: Catch-all for unhandled exceptions
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Timber.tag("NOUS.Uncaught").e(throwable, "Uncaught exception on thread ${thread.name}")
            // Re-throw to let default handler deal with it
            defaultUncaughtExceptionHandler.uncaughtException(thread, throwable)
        }

        super.onCreate()

        // 1. Logging — first thing, so even crashes during init are captured.
        if (BuildConfig.DEBUG || BuildConfig.VERBOSE_TELEMETRY) {
            Timber.plant(Timber.DebugTree())
        }
        // Production CrashReportingTree is planted by :core:telemetry once initialized.
        Timber.tag("NOUS.Boot").i("NOUS application starting…")

        // 2. Crash handler — install ASAP. Any uncaught exception past this point
        //    will be captured, state-snapshotted, and reported.
        crashHandler.install()

        // 3. Observe process lifecycle for foreground/background events.
        //    Used by :feature:automation (rules engine triggers on app foreground).
        ProcessLifecycleOwner.get().lifecycle.addObserver(crashHandler)

        // 4. Run all AppInitializer components (sync first, then async).
        //    Each initializer is contributed via Hilt multibinding — see AppInitializers.
        // CRITICAL FIX #55: Wrap initialization in try-catch to prevent silent failures
        try {
            appInitializers.initializeAll(this)
        } catch (e: Exception) {
            Timber.tag("NOUS.Boot").e(e, "FATAL: AppInitializer failed - app may be unstable")
            // Continue anyway - some features may work
        }

        Timber.tag("NOUS.Boot").i("NOUS application ready.")
    }
}
