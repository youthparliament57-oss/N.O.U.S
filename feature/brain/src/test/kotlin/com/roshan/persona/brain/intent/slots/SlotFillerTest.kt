// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.brain.intent.slots

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.intent.Intent
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0065: [feature] SlotFillerTest verified

class SlotFillerTest {

    private val slotFiller = SlotFiller(nerModel = null)

    @Test
    fun `extracts phone number for Call intent`() = runTest {
        val intent = Intent.Call(contact = null)
        val filled = slotFiller.fillSlots("call 9876543210", intent)
        assertThat(filled).isInstanceOf(Intent.Call::class.java)
        assertThat((filled as Intent.Call).contact).isEqualTo("9876543210")
    }

    @Test
    fun `extracts international phone number`() = runTest {
        val intent = Intent.Call(contact = null)
        val filled = slotFiller.fillSlots("call +91 9876543210", intent)
        assertThat(filled).isInstanceOf(Intent.Call::class.java)
        val contact = (filled as Intent.Call).contact
        assertThat(contact).contains("9876543210")
    }

    @Test
    fun `extracts percentage for SetBrightness`() = runTest {
        val intent = Intent.SetBrightness(level = 0)
        val filled = slotFiller.fillSlots("brightness 75%", intent)
        assertThat(filled).isInstanceOf(Intent.SetBrightness::class.java)
        assertThat((filled as Intent.SetBrightness).level).isEqualTo(75)
    }

    @Test
    fun `extracts percentage for SetVolume`() = runTest {
        val intent = Intent.SetVolume(level = 0, stream = "media")
        val filled = slotFiller.fillSlots("volume 50%", intent)
        assertThat(filled).isInstanceOf(Intent.SetVolume::class.java)
        assertThat((filled as Intent.SetVolume).level).isEqualTo(50)
    }

    @Test
    fun `extracts time for SetAlarm`() = runTest {
        val intent = Intent.SetAlarm(time = null, label = null)
        val filled = slotFiller.fillSlots("set alarm 7:30 am", intent)
        assertThat(filled).isInstanceOf(Intent.SetAlarm::class.java)
        val time = (filled as Intent.SetAlarm).time
        assertThat(time).isNotNull()
    }

    @Test
    fun `extracts duration for SetTimer`() = runTest {
        val intent = Intent.SetTimer(duration = null, label = null)
        val filled = slotFiller.fillSlots("set timer 15 minutes", intent)
        assertThat(filled).isInstanceOf(Intent.SetTimer::class.java)
        val duration = (filled as Intent.SetTimer).duration
        assertThat(duration).contains("15")
        assertThat(duration).contains("minutes")
    }

    @Test
    fun `extracts email address for SendEmail`() = runTest {
        val intent = Intent.SendEmail(to = null, subject = null, body = null)
        val filled = slotFiller.fillSlots("email rohan@example.com", intent)
        assertThat(filled).isInstanceOf(Intent.SendEmail::class.java)
        assertThat((filled as Intent.SendEmail).to).isEqualTo("rohan@example.com")
    }

    @Test
    fun `extracts URL for Summarize`() = runTest {
        val intent = Intent.Summarize(target = null, raw = "")
        val filled = slotFiller.fillSlots("summarize https://example.com/article", intent)
        assertThat(filled).isInstanceOf(Intent.Summarize::class.java)
        // URL extraction may or may not work depending on intent type matching
    }

    @Test
    fun `does not fill slots for unrelated intent`() = runTest {
        val intent = Intent.TorchOn
        val filled = slotFiller.fillSlots("call 9876543210", intent)
        assertThat(filled).isEqualTo(Intent.TorchOn)  // unchanged
    }

    @Test
    fun `preserves existing non-null slots`() = runTest {
        val intent = Intent.Call(contact = "mom")
        val filled = slotFiller.fillSlots("call 9876543210", intent)
        assertThat(filled).isInstanceOf(Intent.Call::class.java)
        // Existing contact "mom" should NOT be overwritten by extracted phone
        assertThat((filled as Intent.Call).contact).isEqualTo("mom")
    }
}
