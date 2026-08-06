// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.hacker

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NOUS — Hacker Feature Module (Dynamic Feature — on-demand, FLAVOR-GATED).
 *
 * Per ADR-0009: This module is ONLY available in `dev` and `internal` flavors.
 * In `prod` (Play Store), [isAvailable] is always false and the module is excluded.
 *
 * ## Flavor Behavior (ADR-0009)
 * | Flavor | Available? | Delivery |
 * |--------|------------|----------|
 * | dev    | true       | Bundled  |
 * | internal | true      | On-demand |
 * | prod   | false      | N/A      |
 *
 * ## Sub-feature Gating
 * Each sub-feature has its own BuildConfig flag that can be overridden by Remote Config:
 * - WiFi Password Reader: [BuildConfig.ENABLE_WIFI_PASSWORD]
 * - Steganography: [BuildConfig.ENABLE_STEGANOGRAPHY]
 * - Tor Proxy: [BuildConfig.ENABLE_TOR]
 * - Terminal: [BuildConfig.ENABLE_TERMINAL]
 */
@Singleton
class HackerFeature @Inject constructor() {

    /**
     * Whether this feature is available on this device/flavor.
     *
     * Gated by BuildConfig.ENABLE_HACKER per ADR-0009.
     * Always false in prod builds.
     */
    val isAvailable: Boolean
        get() = try {
            // Access BuildConfig from the dynamic feature module's package
            com.roshan.persona.hacker.BuildConfig.ENABLE_HACKER
        } catch (e: Exception) {
            Timber.w("Could not read ENABLE_HACKER BuildConfig — defaulting to disabled")
            false
        }

    /** Check if a specific sub-feature is enabled. */
    fun isSubFeatureEnabled(feature: HackerSubFeature): Boolean {
        return try {
            when (feature) {
                HackerSubFeature.WIFI_PASSWORD -> 
                    com.roshan.persona.hacker.BuildConfig.ENABLE_WIFI_PASSWORD
                HackerSubFeature.STEGANOGRAPHY -> 
                    com.roshan.persona.hacker.BuildConfig.ENABLE_STEGANOGRAPHY
                HackerSubFeature.TOR -> 
                    com.roshan.persona.hacker.BuildConfig.ENABLE_TOR
                HackerSubFeature.TERMINAL -> 
                    com.roshan.persona.hacker.BuildConfig.ENABLE_TERMINAL
            }
        } catch (e: Exception) {
            false
        }
    }

    init {
        Timber.i("Hacker feature module loaded (available=%s, flavor=%s)".format(
            isAvailable,
            try { com.roshan.persona.hacker.BuildConfig.FLAVOR } catch (e: Exception) { "unknown" }
        ))
    }

    /** Get the feature's display name for UI. */
    val displayName: String = "Hacker"

    /** Get the feature's description for the Play Feature Delivery UI. */
    val description: String = "Ethical hacking tools (sideload only)"

    /**
     * Show disclaimer dialog before first use.
     * Per ADR-0009: User must accept that tools are "for authorized security testing only".
     */
    fun requireDisclaimerAccepted(activity: android.app.Activity): Boolean {
        val prefs = activity.getSharedPreferences("hacker_prefs", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean("disclaimer_accepted", false)) {
            return true
        }

        android.app.AlertDialog.Builder(activity)
            .setTitle("Disclaimer")
            .setMessage("These tools are for authorized security testing only.")
            .setPositiveButton("Accept") { _, _ ->
                prefs.edit().putBoolean("disclaimer_accepted", true).apply()
            }
            .setNegativeButton("Decline", null)
            .show()

        return false
    }
}

/**
 * Sub-features of the hacker toolkit, each independently gateable.
 */
enum class HackerSubFeature {
    /** Extract saved WiFi passwords (requires root) */
    WIFI_PASSWORD,
    /** Hide data in images (steganography) */
    STEGANOGRAPHY,
    /** Anonymous traffic via Tor SOCKS5 proxy */
    TOR,
    /** Shell command execution (root optional) */
    TERMINAL
}
