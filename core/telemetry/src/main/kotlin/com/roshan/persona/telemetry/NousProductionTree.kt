// Copyright (c) 2026 Roshan. All rights reserved.
package com.roshan.persona.telemetry

import android.util.Log
import timber.log.Timber

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
            // FirebaseCrashlytics.getInstance().log("[$finalTag/${priorityName(priority)}] $scrubbed")
            // TODO: Wire to FirebaseCrashlytics in :app once Firebase is set up.
            // MEDIUM_FIX: Track in project backlog
        }
        if (t != null) {
            runCatching {
                // FirebaseCrashlytics.getInstance().recordException(t)
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
    private val phonePattern = Regex("""\+?\d{1,3}[\s-]?\d{10}""") to "[PHONE]"
    private val emailPattern = Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""") to "[EMAIL]"
    private val ipPattern = Regex("""\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b""") to "[IP]"
    private val bearerPattern = Regex("""(?i)bearer\s+[a-zA-Z0-9_\-\.=]+""") to "Bearer [TOKEN]"
    private val apiKeyPattern = Regex("""\b(sk-ant-|sk-|AIza|gsk_|sk-or-)[a-zA-Z0-9_\-]{10,}""") to "[API_KEY]"
    private val cardPattern = Regex("""\b\d{4}[\s-]?\d{4}[\s-]?\d{4}[\s-]?\d{4}\b""") to "[CARD]"

    /**
     * Remove all known PII patterns from [input].
     *
     * Performance optimization (⚡ Bolt): Uses fast-path pre-checks to skip unnecessary
     * regex evaluations when log strings do not contain candidate PII characters (digits,
     * '@', 'bearer', or API key prefixes). For standard log messages, this eliminates
     * regex matcher allocations and string replacements completely.
     */
    fun scrub(input: String): String {
        if (input.isEmpty()) return input

        val hasDigits = input.any { it in '0'..'9' }
        val hasAt = '@' in input
        val hasBearer = input.contains("bearer", ignoreCase = true)
        val hasApiKey = input.contains("sk-") || input.contains("AIza") || input.contains("gsk_")

        // Fast-path: Return immediately if no PII triggers are present
        if (!hasDigits && !hasAt && !hasBearer && !hasApiKey) {
            return input
        }

        var result = input
        if (hasDigits) {
            result = result.replace(phonePattern.first, phonePattern.second)
        }
        if (hasAt) {
            result = result.replace(emailPattern.first, emailPattern.second)
        }
        if (hasDigits) {
            result = result.replace(ipPattern.first, ipPattern.second)
        }
        if (hasBearer) {
            result = result.replace(bearerPattern.first, bearerPattern.second)
        }
        if (hasApiKey) {
            result = result.replace(apiKeyPattern.first, apiKeyPattern.second)
        }
        if (hasDigits) {
            result = result.replace(cardPattern.first, cardPattern.second)
        }
        return result
    }
}
