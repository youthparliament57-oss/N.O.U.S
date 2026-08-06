// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.connectivity.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// CONNECTIVITY_FIX_005: Connectivity models correct

class ConnectivityModelsTest {

    private fun validContact(
        id: String = "1",
        name: String = "Mom",
        phones: List<String> = listOf("+919876543210"),
    ) = ContactInfo(id = id, displayName = name, phoneNumbers = phones)

    // ─── ContactInfo ───────────────────────────────────────────────────────

    @Test
    fun `ContactInfo validates non-blank id`() {
        try {
            validContact(id = "")
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("id")
        }
    }

    @Test
    fun `ContactInfo validates non-blank displayName`() {
        try {
            validContact(name = "")
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("DisplayName")
        }
    }

    @Test
    fun `ContactInfo requires at least one phone number`() {
        try {
            validContact(phones = emptyList())
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("phone number")
        }
    }

    @Test
    fun `ContactInfo primaryPhone returns first number`() {
        val contact = validContact(phones = listOf("+919876543210", "+911234567890"))
        assertThat(contact.primaryPhone).isEqualTo("+919876543210")
    }

    // ─── SmsMessage ─────────────────────────────────────────────────────────

    @Test
    fun `SmsMessage validates non-blank id`() {
        try {
            SmsMessage(id = "", contact = null, phoneNumber = "+91", body = "hello", direction = SmsDirection.INCOMING, timestampMs = 0)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("id")
        }
    }

    @Test
    fun `SmsMessage validates non-blank body`() {
        try {
            SmsMessage(id = "1", contact = null, phoneNumber = "+91", body = "", direction = SmsDirection.INCOMING, timestampMs = 0)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("body")
        }
    }

    // ─── ShareContent ───────────────────────────────────────────────────────

    @Test
    fun `ShareContent requires at least one non-null field`() {
        try {
            ShareContent(text = null, imageUri = null, fileUri = null, mimeType = "text/plain")
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("non-null")
        }
    }

    @Test
    fun `ShareContent accepts text only`() {
        val content = ShareContent(text = "hello", imageUri = null, fileUri = null, mimeType = "text/plain")
        assertThat(content.text).isEqualTo("hello")
    }

    // ─── SimSlot ────────────────────────────────────────────────────────────

    @Test
    fun `SimSlot fromIndex returns correct slot`() {
        assertThat(SimSlot.fromIndex(0)).isEqualTo(SimSlot.SIM1)
        assertThat(SimSlot.fromIndex(1)).isEqualTo(SimSlot.SIM2)
        assertThat(SimSlot.fromIndex(5)).isNull()
    }

    // ─── Enum counts ────────────────────────────────────────────────────────

    @Test
    fun `CallDirection has 3 values`() {
        assertThat(CallDirection.entries).hasSize(3)
    }

    @Test
    fun `CallState has 5 values`() {
        assertThat(CallState.entries).hasSize(5)
    }

    @Test
    fun `SmsStatus has 4 values`() {
        assertThat(SmsStatus.entries).hasSize(4)
    }

    @Test
    fun `SpamLevel has 5 values`() {
        assertThat(SpamLevel.entries).hasSize(5)
    }

    @Test
    fun `CommsType has 5 values`() {
        assertThat(CommsType.entries).hasSize(5)
    }

    @Test
    fun `MessagingApp has 4 values`() {
        assertThat(MessagingApp.entries).hasSize(4)
    }

    @Test
    fun `MatchType has 4 values`() {
        assertThat(MatchType.entries).hasSize(4)
    }

    @Test
    fun `InsightType has 5 values`() {
        assertThat(InsightType.entries).hasSize(5)
    }

    @Test
    fun `SosStatus has 5 values`() {
        assertThat(SosStatus.entries).hasSize(5)
    }
}

class ConnectivityConfigTest {

    @Test
    fun `default config has valid values`() {
        val config = ConnectivityConfig()
        assertThat(config.maxFuzzyDistance).isEqualTo(3)
        assertThat(config.voiceVerificationRequired).isTrue()
        assertThat(config.sosCountdownSeconds).isEqualTo(5)
        assertThat(config.emergencyNumbers).contains("112")
        assertThat(config.emergencyNumbers).contains("911")
        assertThat(config.supportedMessagingApps).contains(MessagingApp.WHATSAPP)
    }

    @Test
    fun `config rejects zero maxFuzzyDistance`() {
        try {
            ConnectivityConfig(maxFuzzyDistance = 0)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("maxFuzzyDistance")
        }
    }

    @Test
    fun `config rejects empty emergencyNumbers`() {
        try {
            ConnectivityConfig(emergencyNumbers = emptySet())
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("emergencyNumbers")
        }
    }

    @Test
    fun `config rejects zero sosCountdownSeconds`() {
        try {
            ConnectivityConfig(sosCountdownSeconds = -1)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("sosCountdownSeconds")
        }
    }

    @Test
    fun `config rejects proactiveCheckIntervalHours > 48`() {
        try {
            ConnectivityConfig(proactiveCheckIntervalHours = 49)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("proactiveCheckIntervalHours")
        }
    }
}
