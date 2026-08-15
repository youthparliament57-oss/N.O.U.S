// Copyright (c) 2026 Roshan. All rights reserved.
package com.roshan.persona.telemetry

import org.junit.Assert.assertEquals
import org.junit.Test

class PiiScrubberTest {

    @Test
    fun scrubPhoneNumbers() {
        val input = "Call me at +91 98765 43210 or 9876543210 now"
        val expected = "Call me at [PHONE] or [PHONE] now"
        assertEquals(expected, PiiScrubber.scrub(input))
    }

    @Test
    fun scrubEmailAddresses() {
        val input = "Send email to user@example.com for support"
        val expected = "Send email to [EMAIL] for support"
        assertEquals(expected, PiiScrubber.scrub(input))
    }

    @Test
    fun scrubIpAddresses() {
        val input = "Connected to server at 192.168.1.100 port 8080"
        val expected = "Connected to server at [IP] port 8080"
        assertEquals(expected, PiiScrubber.scrub(input))
    }

    @Test
    fun scrubBearerTokens() {
        val input = "Authorization: Bearer abc123def456xyz"
        val expected = "Authorization: Bearer [TOKEN]"
        assertEquals(expected, PiiScrubber.scrub(input))
    }

    @Test
    fun scrubApiKeys() {
        val input1 = "OpenAI key: sk-proj1234567890abcdef"
        assertEquals("OpenAI key: [API_KEY]", PiiScrubber.scrub(input1))

        val input2 = "Anthropic key: sk-ant-1234567890abcdef"
        assertEquals("Anthropic key: [API_KEY]", PiiScrubber.scrub(input2))
    }

    @Test
    fun scrubCreditCards() {
        val input = "Card number 1234-5678-9012-3456"
        val expected = "Card number [CARD]"
        assertEquals(expected, PiiScrubber.scrub(input))
    }

    @Test
    fun scrubCleanStringUnchanged() {
        val input = "System started successfully with no errors"
        assertEquals(input, PiiScrubber.scrub(input))
    }
}
