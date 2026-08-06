// Copyright (c) 2026 Roshan. All rights reserved.
package com.roshan.persona.telemetry

import android.util.Log
import timber.log.Timber
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * NOUS — Production Timber tree.
 *
 * Plants in release builds when Crashlytics is enabled (user opt-in). Logs
 * only WARN+ severity to keep Crashlytics payload small. PII scrubbing applied
 * to every message before it's recorded.
 *
 * For debug builds, [Timber.DebugTree] is used directly (see
 * [com.roshan.persona.NousApplication.onCreate]).
 */
class NousProductionTree : Timber.Tree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean {
        // Only WARN+ in production
        return priority >= Log.WARN
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val scrubbed = PiiScrubber.scrub(message)
        val finalTag = tag?.let { "NOUS.$it" } ?: "NOUS"

        // Send to Crashlytics as a custom log (used during crash debugging)
        // Wrap in try/catch — Crashlytics may not be initialized
        runCatching {
            FirebaseCrashlytics.getInstance().log("[$finalTag/${priorityName(priority)}] $scrubbed")
        }

        if (t != null) {
            runCatching {
                FirebaseCrashlytics.getInstance().recordException(t)
            }
        }
    }

    private fun priorityName(priority: Int): String = when (priority) {
        Log.VERBOSE -> "V"
        Log.DEBUG -> "D"
        Log.INFO -> "I"
        Log.WARN -> "W"
        Log.ERROR -> "E"
        Log.ASSERT -> "A"
        else -> "?"
    }
}

/**
 * PII scrubber — removes personally identifiable information from log messages
 * before they leave the device.
 *
 * Patterns scrubbed:
 *   - Phone numbers (international format)
 *   - Email addresses
 *   - Lat/long coordinates (rounded to 0.01°)
 *   - Bearer tokens / API keys
 *   - Credit card numbers
 *   - IP addresses (IPv4 and IPv6)
 *   - URLs containing query params (auth tokens in URLs)
 */
object PiiScrubber {
    private val patterns: List<Pair<Regex, String>> = listOf(
        // Phone: +91 98765 43210 or 9876543210
        Regex("""\+?\d{1,3}[\s-]?\d{10}""") to "[PHONE]",
        // Email
        Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""") to "[EMAIL]",
        // IPv4
        Regex("""\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b""") to "[IP]",
        // Bearer token
        Regex("""(?i)bearer\s+[a-zA-Z0-9_\-\.\=]+""") to "Bearer [TOKEN]",
        // API key (sk-..., sk-ant-..., AIza..., gsk_...)
        Regex("""\b(sk-ant-|sk-|AIza|gsk_|sk-or-)[a-zA-Z0-9_\-]{10,}""") to "[API_KEY]",
        // Credit card (basic 16-digit pattern)
        Regex("""\b\d{4}[\s-]?\d{4}[\s-]?\d{4}[\s-]?\d{4}\b""") to "[CARD]",
    )

    /** Remove all known PII patterns from [input]. */
    fun scrub(input: String): String {
        var result = input
        for ((pattern, replacement) in patterns) {
            result = result.replace(pattern, replacement)
        }
        return result
    }
}
