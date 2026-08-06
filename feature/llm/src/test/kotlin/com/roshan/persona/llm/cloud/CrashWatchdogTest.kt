// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.llm.cloud

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// AUTO_FIX_0129: [feature] CrashWatchdogTest verified

/**
 * NOUS — Step 4.10: CrashWatchdog + native_crash_logs + LlmModule tests.
 *
 * @see <a href="docs/strategy/module-4-strategy-v2.1.md">§4.10 Hilt + Crash Logs</a>
 */
class CrashWatchdogTest {

    @Test
    fun `signalCodeToString returns SIGSEGV for code 11`() {
        val watchdog = CrashWatchdog { _, _ -> }
        assertThat(watchdog.signalCodeToString(11)).isEqualTo("SIGSEGV")
    }

    @Test
    fun `signalCodeToString returns SIGABRT for code 6`() {
        val watchdog = CrashWatchdog { _, _ -> }
        assertThat(watchdog.signalCodeToString(6)).isEqualTo("SIGABRT")
    }

    @Test
    fun `signalCodeToString returns UNKNOWN for unknown signal`() {
        val watchdog = CrashWatchdog { _, _ -> }
        assertThat(watchdog.signalCodeToString(99)).isEqualTo("UNKNOWN(99)")
    }

    @Test
    fun `handleCrash calls onCrashDetected callback with correct signal`() {
        var receivedSignal = -1
        var receivedName = ""

        val watchdog = CrashWatchdog { code, name ->
            receivedSignal = code
            receivedName = name
        }

        watchdog.handleCrash(CrashWatchdog.SIGSEGV)

        assertThat(receivedSignal).isEqualTo(11)
        assertThat(receivedName).isEqualTo("SIGSEGV")
    }

    @Test
    fun `handleCrash handles SIGABRT`() {
        var receivedSignal = -1
        val watchdog = CrashWatchdog { code, _ -> receivedSignal = code }

        watchdog.handleCrash(CrashWatchdog.SIGABRT)

        assertThat(receivedSignal).isEqualTo(6)
    }

    @Test
    fun `handleCrash handles SIGBUS`() {
        var receivedName = ""
        val watchdog = CrashWatchdog { _, name -> receivedName = name }

        watchdog.handleCrash(CrashWatchdog.SIGBUS)

        assertThat(receivedName).isEqualTo("SIGBUS")
    }

    @Test
    fun `signal constants are correct`() {
        assertThat(CrashWatchdog.SIGSEGV).isEqualTo(11)
        assertThat(CrashWatchdog.SIGABRT).isEqualTo(6)
        assertThat(CrashWatchdog.SIGBUS).isEqualTo(7)
        assertThat(CrashWatchdog.SIGFPE).isEqualTo(8)
        assertThat(CrashWatchdog.SIGILL).isEqualTo(4)
    }
}
