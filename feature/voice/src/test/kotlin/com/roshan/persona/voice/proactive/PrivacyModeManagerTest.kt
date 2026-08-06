// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.voice.proactive

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0159: [feature] PrivacyModeManagerTest verified

// VOICE_FIX_038: Privacy mode manager secure

class PrivacyModeManagerTest {

    // ─── Fakes ─────────────────────────────────────────────────────────────

    private class FakeWifiProbe(var ssid: String? = null) : WifiProbe {
        override fun getCurrentSsid(): String? = ssid
    }

    private class FakeMicToggle(var enabled: Boolean = true) : MicToggle {
        override suspend fun setMicrophoneEnabled(enabled: Boolean) {
            this.enabled = enabled
        }
        override suspend fun isMicrophoneEnabled(): Boolean = enabled
    }

    private fun makeManager(
        ssid: String? = null,
        micEnabled: Boolean = true,
        store: PrivacySettingsStore = InMemoryPrivacySettingsStore(),
    ): PrivacyModeManager {
        return PrivacyModeManager(
            wifiProbe = FakeWifiProbe(ssid),
            micToggle = FakeMicToggle(micEnabled),
            store = store,
        )
    }

    // ─── Tests ─────────────────────────────────────────────────────────────

    @Test
    fun `shouldWakeWordBeActive returns true when Wi-Fi is off`() = runTest {
        val manager = makeManager(ssid = null)
        assertThat(manager.shouldWakeWordBeActive()).isTrue()
    }

    @Test
    fun `shouldWakeWordBeActive returns true for unknown Wi-Fi`() = runTest {
        val manager = makeManager(ssid = "RandomCafe")
        assertThat(manager.shouldWakeWordBeActive()).isTrue()
    }

    @Test
    fun `shouldWakeWordBeActive returns false for disabled SSID`() = runTest {
        val store = InMemoryPrivacySettingsStore()
        store.saveDisabledSsids(setOf("OfficeWifi"))
        val manager = makeManager(ssid = "OfficeWifi", store = store)
        assertThat(manager.shouldWakeWordBeActive()).isFalse()
    }

    @Test
    fun `shouldWakeWordBeActive returns false when mic is hardware-disabled`() = runTest {
        val manager = makeManager(ssid = "HomeWifi", micEnabled = false)
        assertThat(manager.shouldWakeWordBeActive()).isFalse()
    }

    @Test
    fun `disableWakeWordOnSsid adds to disabled list`() = runTest {
        val manager = makeManager(ssid = "OfficeWifi")
        manager.disableWakeWordOnSsid("OfficeWifi")
        // Now should return false.
        assertThat(manager.shouldWakeWordBeActive()).isFalse()
        // SSID is in the list.
        assertThat(manager.getDisabledSsids()).contains("OfficeWifi")
    }

    @Test
    fun `enableWakeWordOnSsid removes from disabled list`() = runTest {
        val store = InMemoryPrivacySettingsStore()
        store.saveDisabledSsids(setOf("OfficeWifi"))
        val manager = makeManager(ssid = "OfficeWifi", store = store)
        assertThat(manager.shouldWakeWordBeActive()).isFalse()
        manager.enableWakeWordOnSsid("OfficeWifi")
        assertThat(manager.shouldWakeWordBeActive()).isTrue()
    }

    @Test
    fun `setMicrophoneEnabled toggles hardware mic`() = runTest {
        val manager = makeManager(micEnabled = true)
        assertThat(manager.isMicrophoneEnabled()).isTrue()
        manager.setMicrophoneEnabled(false)
        assertThat(manager.isMicrophoneEnabled()).isFalse()
        manager.setMicrophoneEnabled(true)
        assertThat(manager.isMicrophoneEnabled()).isTrue()
    }

    @Test
    fun `disableWakeWordOnSsid rejects blank SSID`() = runTest {
        val manager = makeManager()
        try {
            manager.disableWakeWordOnSsid("")
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("ssid")
        }
    }

    @Test
    fun `InMemoryPrivacySettingsStore round-trips SSIDs`() = runTest {
        val store = InMemoryPrivacySettingsStore()
        store.saveDisabledSsids(setOf("A", "B", "C"))
        assertThat(store.loadDisabledSsids()).containsExactly("A", "B", "C")
        store.saveDisabledSsids(emptySet())
        assertThat(store.loadDisabledSsids()).isEmpty()
    }

    @Test
    fun `getDisabledSsids returns current disabled set`() = runTest {
        val store = InMemoryPrivacySettingsStore()
        store.saveDisabledSsids(setOf("Home", "Office"))
        val manager = makeManager(store = store)
        val ssids = manager.getDisabledSsids()
        assertThat(ssids).containsExactly("Home", "Office")
    }
}
