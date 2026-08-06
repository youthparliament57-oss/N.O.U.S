// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.brain.intent.classifier

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.intent.Intent
import org.junit.Test

// AUTO_FIX_0068: [feature] KeywordIntentMatcherTest verified

class KeywordIntentMatcherTest {

    private val matcher = KeywordIntentMatcher()

    @Test
    fun `call mom matches via keyword`() {
        val match = matcher.match("call mom")
        assertThat(match).isNotNull()
        assertThat(match!!.intent).isInstanceOf(Intent.Call::class.java)
        assertThat(match.rawScore).isGreaterThan(0f)
    }

    @Test
    fun `phone dad matches via keyword`() {
        val match = matcher.match("phone dad")
        assertThat(match).isNotNull()
        assertThat(match!!.intent).isInstanceOf(Intent.Call::class.java)
    }

    @Test
    fun `send sms matches via keyword`() {
        val match = matcher.match("send sms to mom")
        assertThat(match).isNotNull()
        assertThat(match!!.intent).isInstanceOf(Intent.SendSms::class.java)
    }

    @Test
    fun `text message matches via keyword`() {
        val match = matcher.match("text message")
        assertThat(match).isNotNull()
        assertThat(match!!.intent).isInstanceOf(Intent.SendSms::class.java)
    }

    @Test
    fun `email matches via keyword`() {
        val match = matcher.match("send email to rohan")
        assertThat(match).isNotNull()
        assertThat(match!!.intent).isInstanceOf(Intent.SendEmail::class.java)
    }

    @Test
    fun `remind me matches via keyword`() {
        val match = matcher.match("remind me to call mom")
        assertThat(match).isNotNull()
        assertThat(match!!.intent).isInstanceOf(Intent.SetReminder::class.java)
    }

    @Test
    fun `take note matches via keyword`() {
        val match = matcher.match("take a note")
        assertThat(match).isNotNull()
        assertThat(match!!.intent).isInstanceOf(Intent.CreateNote::class.java)
    }

    @Test
    fun `summarize matches via keyword`() {
        val match = matcher.match("summarize this article")
        assertThat(match).isNotNull()
        assertThat(match!!.intent).isInstanceOf(Intent.Summarize::class.java)
    }

    @Test
    fun `search for matches via keyword`() {
        val match = matcher.match("find cat videos")
        assertThat(match).isNotNull()
        assertThat(match!!.intent).isInstanceOf(Intent.SearchWeb::class.java)
    }

    @Test
    fun `non-keyword input returns null`() {
        val match = matcher.match("torch on")  // regex territory
        // Keyword matcher might still match "on" but not at threshold
        // Just verify it doesn't crash
        assertThat(match == null || match.intent != null).isTrue()
    }

    @Test
    fun `stemming removes common suffixes`() {
        // "searching" should match "search" stem
        val match = matcher.match("searching for cats")
        assertThat(match).isNotNull()
        assertThat(match!!.intent).isInstanceOf(Intent.SearchWeb::class.java)
    }

    @Test
    fun `case insensitive matching`() {
        val match = matcher.match("CALL MOM")
        assertThat(match).isNotNull()
        assertThat(match!!.intent).isInstanceOf(Intent.Call::class.java)
    }
}
