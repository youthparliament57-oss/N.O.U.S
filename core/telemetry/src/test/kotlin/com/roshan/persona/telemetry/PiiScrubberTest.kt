// Copyright (c) 2026 Roshan. All rights reserved.
package com.roshan.persona.telemetry

import org.junit.Assert.assertEquals
import org.junit.Test

class PiiScrubberTest {

    @Test
    fun scrub_emptyString_returnsEmptyString() {
        val input = ""
        val expected = ""
        assertEquals(expected, PiiScrubber.scrub(input))
    }

    @Test
    fun scrub_plainTextMessage_fastPathReturnsOriginal() {
        val input = "Initialization completed successfully. System ready."
        val expected = "Initialization completed successfully. System ready."
        assertEquals(expected, PiiScrubber.scrub(input))
    }

    @Test
    fun scrub_phoneNumbers_scrubsPhone() {
        val input1 = "Call me at +91 9876543210 immediately."
        val expected1 = "Call me at [PHONE] immediately."
        assertEquals(expected1, PiiScrubber.scrub(input1))

        val input2 = "User phone: +919876543210"
        val expected2 = "User phone: [PHONE]"
        assertEquals(expected2, PiiScrubber.scrub(input2))
    }

    @Test
    fun scrub_emailAddress_scrubsEmail() {
        val input = "User email user.name+tag@domain.co.uk logged in."
        val expected = "User email [EMAIL] logged in."
        assertEquals(expected, PiiScrubber.scrub(input))
    }

    @Test
    fun scrub_ipAddress_scrubsIp() {
        val input = "Connection failed to 192.168.1.100 on port 8080"
        val expected = "Connection failed to [IP] on port 8080"
        assertEquals(expected, PiiScrubber.scrub(input))
    }

    @Test
    fun scrub_bearerToken_scrubsBearerToken() {
        val input1 = "Header Authorization: Bearer abc123def456.789"
        val expected1 = "Header Authorization: Bearer [TOKEN]"
        assertEquals(expected1, PiiScrubber.scrub(input1))

        val input2 = "Header Authorization: bearer secret_token_value"
        val expected2 = "Header Authorization: Bearer [TOKEN]"
        assertEquals(expected2, PiiScrubber.scrub(input2))
    }

    @Test
    fun scrub_apiKeys_scrubsApiKeys() {
        val input1 = "Key: sk-abcdef1234567890"
        val expected1 = "Key: [API_KEY]"
        assertEquals(expected1, PiiScrubber.scrub(input1))

        val input2 = "Anthropic key sk-ant-api03-abcdef1234567890"
        val expected2 = "Anthropic key [API_KEY]"
        assertEquals(expected2, PiiScrubber.scrub(input2))

        val input3 = "Google key AIzaSyA1234567890abcdef"
        val expected3 = "Google key [API_KEY]"
        assertEquals(expected3, PiiScrubber.scrub(input3))
    }

    @Test
    fun scrub_creditCard_scrubsCreditCard() {
        val input = "Payment failed for card 4532-1234-5678-9012"
        val expected = "Payment failed for card [CARD]"
        assertEquals(expected, PiiScrubber.scrub(input))
    }

    @Test
    fun scrub_mixedPii_scrubsAllPatterns() {
        val input = "User test@example.com with key sk-ant-abcdef1234567890 called from 10.0.0.1"
        val expected = "User [EMAIL] with key [API_KEY] called from [IP]"
        assertEquals(expected, PiiScrubber.scrub(input))
    }
}
