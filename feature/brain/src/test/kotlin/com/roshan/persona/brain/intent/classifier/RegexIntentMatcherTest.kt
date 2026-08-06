// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.brain.intent.classifier

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.intent.Intent
import org.junit.Test

// AUTO_FIX_0067: [feature] RegexIntentMatcherTest verified

class RegexIntentMatcherTest {

    private val matcher = RegexIntentMatcher()

    @Test
    fun `torch on matches TorchOn intent`() {
        val match = matcher.match("torch on")
        assertThat(match).isNotNull()
        assertThat(match!!.intent).isEqualTo(Intent.TorchOn)
    }

    @Test
    fun `turn on the torch matches TorchOn intent`() {
        val match = matcher.match("turn on the torch")
        assertThat(match).isNotNull()
        assertThat(match!!.intent).isEqualTo(Intent.TorchOn)
    }

    @Test
    fun `torch off matches TorchOff intent`() {
        val match = matcher.match("torch off")
        assertThat(match).isNotNull()
        assertThat(match!!.intent).isEqualTo(Intent.TorchOff)
    }

    @Test
    fun `volume up matches VolumeUp intent`() {
        val match = matcher.match("volume up")
        assertThat(match).isNotNull()
        assertThat(match!!.intent).isInstanceOf(Intent.VolumeUp::class.java)
        assertThat((match.intent as Intent.VolumeUp).amount).isNull()
    }

    @Test
    fun `volume up 5 matches VolumeUp with amount`() {
        val match = matcher.match("volume up 5")
        assertThat(match).isNotNull()
        assertThat(match!!.intent).isInstanceOf(Intent.VolumeUp::class.java)
        assertThat((match.intent as Intent.VolumeUp).amount).isEqualTo(5)
    }

    @Test
    fun `set volume to 50 matches SetVolume intent`() {
        val match = matcher.match("set volume to 50")
        assertThat(match).isNotNull()
        assertThat(match!!.intent).isInstanceOf(Intent.SetVolume::class.java)
        assertThat((match.intent as Intent.SetVolume).level).isEqualTo(50)
    }

    @Test
    fun `brightness 75 matches SetBrightness intent`() {
        val match = matcher.match("brightness 75%")
        assertThat(match).isNotNull()
        assertThat(match!!.intent).isInstanceOf(Intent.SetBrightness::class.java)
        assertThat((match.intent as Intent.SetBrightness).level).isEqualTo(75)
    }

    @Test
    fun `call mom matches Call intent with contact`() {
        val match = matcher.match("call mom")
        assertThat(match).isNotNull()
        assertThat(match!!.intent).isInstanceOf(Intent.Call::class.java)
        assertThat((match.intent as Intent.Call).contact).isEqualTo("mom")
    }

    @Test
    fun `call 9876543210 matches Call intent with phone`() {
        val match = matcher.match("call 9876543210")
        assertThat(match).isNotNull()
        assertThat(match!!.intent).isInstanceOf(Intent.Call::class.java)
        assertThat((match.intent as Intent.Call).contact).isEqualTo("9876543210")
    }

    @Test
    fun `open youtube matches OpenApp intent`() {
        val match = matcher.match("open youtube")
        assertThat(match).isNotNull()
        assertThat(match!!.intent).isInstanceOf(Intent.OpenApp::class.java)
        assertThat((match.intent as Intent.OpenApp).appName).isEqualTo("youtube")
    }

    @Test
    fun `search for cats matches SearchWeb intent`() {
        val match = matcher.match("search for cats")
        assertThat(match).isNotNull()
        assertThat(match!!.intent).isInstanceOf(Intent.SearchWeb::class.java)
        assertThat((match.intent as Intent.SearchWeb).query).isEqualTo("cats")
    }

    @Test
    fun `set alarm for 7am matches SetAlarm intent`() {
        val match = matcher.match("set alarm for 7am")
        assertThat(match).isNotNull()
        assertThat(match!!.intent).isInstanceOf(Intent.SetAlarm::class.java)
        assertThat((match.intent as Intent.SetAlarm).time).isEqualTo("7am")
    }

    @Test
    fun `set timer for 10 minutes matches SetTimer intent`() {
        val match = matcher.match("set timer for 10 minutes")
        assertThat(match).isNotNull()
        assertThat(match!!.intent).isInstanceOf(Intent.SetTimer::class.java)
        assertThat((match.intent as Intent.SetTimer).duration).isEqualTo("10 minutes")
    }

    @Test
    fun `list automations matches ListAutomations intent`() {
        val match = matcher.match("list my automations")
        assertThat(match).isNotNull()
        assertThat(match!!.intent).isEqualTo(Intent.ListAutomations)
    }

    @Test
    fun `non-matching input returns null`() {
        val match = matcher.match("what is the meaning of life")
        assertThat(match).isNull()
    }

    @Test
    fun `empty input returns null`() {
        val match = matcher.match("")
        assertThat(match).isNull()
    }

    @Test
    fun `case insensitive matching works`() {
        val match = matcher.match("TORCH ON")
        assertThat(match).isNotNull()
        assertThat(match!!.intent).isEqualTo(Intent.TorchOn)
    }
}
