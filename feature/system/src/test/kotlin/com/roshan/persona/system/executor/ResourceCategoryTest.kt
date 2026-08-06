// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.system.executor

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.skill.SystemOperation
import org.junit.Test

// AUTO_FIX_0134: [feature] ResourceCategoryTest verified

class ResourceCategoryTest {

    @Test
    fun `classify maps camera operations to CAMERA`() {
        assertThat(SystemOperationClassifier.classify(SystemOperation.SetTorch(true)))
            .isEqualTo(ResourceCategory.CAMERA)
        assertThat(SystemOperationClassifier.classify(SystemOperation.SetTorch(false)))
            .isEqualTo(ResourceCategory.CAMERA)
    }

    @Test
    fun `classify maps audio operations to AUDIO`() {
        assertThat(SystemOperationClassifier.classify(SystemOperation.AdjustVolume(1)))
            .isEqualTo(ResourceCategory.AUDIO)
        assertThat(SystemOperationClassifier.classify(SystemOperation.SetVolume(50)))
            .isEqualTo(ResourceCategory.AUDIO)
        assertThat(SystemOperationClassifier.classify(SystemOperation.SetRingerMode(com.roshan.persona.brain.skill.RingerMode.NORMAL)))
            .isEqualTo(ResourceCategory.AUDIO)
        assertThat(SystemOperationClassifier.classify(SystemOperation.SetDndMode(com.roshan.persona.brain.skill.DndMode.NONE)))
            .isEqualTo(ResourceCategory.AUDIO)
    }

    @Test
    fun `classify maps display operations to DISPLAY`() {
        assertThat(SystemOperationClassifier.classify(SystemOperation.SetDisplayBrightness(0.5f)))
            .isEqualTo(ResourceCategory.DISPLAY)
        assertThat(SystemOperationClassifier.classify(SystemOperation.LockScreen))
            .isEqualTo(ResourceCategory.DISPLAY)
        assertThat(SystemOperationClassifier.classify(SystemOperation.SetKeepScreenOn(true)))
            .isEqualTo(ResourceCategory.DISPLAY)
    }

    @Test
    fun `classify maps connectivity operations to CONNECTIVITY`() {
        assertThat(SystemOperationClassifier.classify(SystemOperation.SetWifi(true)))
            .isEqualTo(ResourceCategory.CONNECTIVITY)
        assertThat(SystemOperationClassifier.classify(SystemOperation.SetBluetooth(false)))
            .isEqualTo(ResourceCategory.CONNECTIVITY)
        assertThat(SystemOperationClassifier.classify(SystemOperation.SetAirplaneMode(true)))
            .isEqualTo(ResourceCategory.CONNECTIVITY)
        assertThat(SystemOperationClassifier.classify(SystemOperation.SetLocationMode(com.roshan.persona.brain.skill.LocationMode.OFF)))
            .isEqualTo(ResourceCategory.CONNECTIVITY)
        assertThat(SystemOperationClassifier.classify(SystemOperation.SetTethering(true)))
            .isEqualTo(ResourceCategory.CONNECTIVITY)
        assertThat(SystemOperationClassifier.classify(SystemOperation.SetNfc(false)))
            .isEqualTo(ResourceCategory.CONNECTIVITY)
    }

    @Test
    fun `classify maps app-launch operations to APP`() {
        assertThat(SystemOperationClassifier.classify(SystemOperation.OpenApp("com.example")))
            .isEqualTo(ResourceCategory.APP)
        assertThat(SystemOperationClassifier.classify(SystemOperation.OpenUrl("https://x.com")))
            .isEqualTo(ResourceCategory.APP)
        assertThat(SystemOperationClassifier.classify(SystemOperation.SetAlarm(7, 0)))
            .isEqualTo(ResourceCategory.APP)
        assertThat(SystemOperationClassifier.classify(SystemOperation.SetTimer(60)))
            .isEqualTo(ResourceCategory.APP)
    }

    @Test
    fun `classify maps clipboard operations to CLIPBOARD`() {
        assertThat(SystemOperationClassifier.classify(SystemOperation.CopyToClipboard("x")))
            .isEqualTo(ResourceCategory.CLIPBOARD)
    }

    @Test
    fun `classify maps notification operations to NOTIFICATION`() {
        assertThat(SystemOperationClassifier.classify(
            SystemOperation.PostNotification(1, "c", "C", "t", "b")
        )).isEqualTo(ResourceCategory.NOTIFICATION)
    }

    @Test
    fun `classify maps power operations to POWER`() {
        assertThat(SystemOperationClassifier.classify(SystemOperation.SetBatterySaver(true)))
            .isEqualTo(ResourceCategory.POWER)
    }

    @Test
    fun `classify maps Composite to SPECIAL`() {
        val composite = SystemOperation.Composite(listOf(
            SystemOperation.SetTorch(true),
            SystemOperation.SetVolume(50),
        ))
        assertThat(SystemOperationClassifier.classify(composite))
            .isEqualTo(ResourceCategory.SPECIAL)
    }

    @Test
    fun `groupByCategory groups operations correctly`() {
        val ops = listOf(
            SystemOperation.SetTorch(true),       // CAMERA
            SystemOperation.SetVolume(50),        // AUDIO
            SystemOperation.AdjustVolume(1),      // AUDIO
            SystemOperation.SetWifi(true),        // CONNECTIVITY
        )
        val groups = SystemOperationClassifier.groupByCategory(ops)
        assertThat(groups[ResourceCategory.CAMERA]).hasSize(1)
        assertThat(groups[ResourceCategory.AUDIO]).hasSize(2)
        assertThat(groups[ResourceCategory.CONNECTIVITY]).hasSize(1)
        assertThat(groups[ResourceCategory.DISPLAY]).isNull()
    }

    @Test
    fun `allSameCategory true when all ops same category`() {
        val ops = listOf(
            SystemOperation.AdjustVolume(1),
            SystemOperation.SetVolume(50),
            SystemOperation.SetRingerMode(com.roshan.persona.brain.skill.RingerMode.SILENT),
        )
        assertThat(SystemOperationClassifier.allSameCategory(ops)).isTrue()
    }

    @Test
    fun `allSameCategory false when ops span categories`() {
        val ops = listOf(
            SystemOperation.SetTorch(true),    // CAMERA
            SystemOperation.SetVolume(50),     // AUDIO
        )
        assertThat(SystemOperationClassifier.allSameCategory(ops)).isFalse()
    }

    @Test
    fun `allDifferentCategories true when each op is in a unique category`() {
        val ops = listOf(
            SystemOperation.SetTorch(true),                  // CAMERA
            SystemOperation.SetVolume(50),                   // AUDIO
            SystemOperation.SetDisplayBrightness(0.5f),      // DISPLAY
            SystemOperation.SetWifi(true),                   // CONNECTIVITY
            SystemOperation.OpenApp("com.x"),                // APP
        )
        assertThat(SystemOperationClassifier.allDifferentCategories(ops)).isTrue()
    }

    @Test
    fun `allDifferentCategories false when two ops share a category`() {
        val ops = listOf(
            SystemOperation.SetTorch(true),     // CAMERA
            SystemOperation.SetTorch(false),    // CAMERA (duplicate)
            SystemOperation.SetVolume(50),      // AUDIO
        )
        assertThat(SystemOperationClassifier.allDifferentCategories(ops)).isFalse()
    }

    @Test
    fun `allSameCategory returns true for empty list`() {
        assertThat(SystemOperationClassifier.allSameCategory(emptyList())).isTrue()
    }

    @Test
    fun `ResourceCategory has 10 distinct values`() {
        assertThat(ResourceCategory.entries).hasSize(10)
        assertThat(ResourceCategory.entries.map { it.name }.toSet())
            .containsExactly(
                "AUDIO", "CAMERA", "DISPLAY", "CONNECTIVITY",
                "APP", "CLIPBOARD", "NOTIFICATION", "POWER",
                "SETTINGS", "SPECIAL",
            )
    }
}
