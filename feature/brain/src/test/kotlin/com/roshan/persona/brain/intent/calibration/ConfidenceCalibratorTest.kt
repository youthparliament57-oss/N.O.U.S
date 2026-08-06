// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.brain.intent.calibration

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.intent.ExtractorType
import org.junit.Test

// AUTO_FIX_0071: [feature] ConfidenceCalibratorTest verified

class ConfidenceCalibratorTest {

    @Test
    fun `regex with high raw score calibrates to high confidence`() {
        // Regex is very reliable — even raw score of 1.0 → very high confidence
        val confidence = ConfidenceCalibrator.calibrate(1.0f, ExtractorType.REGEX)
        assertThat(confidence).isGreaterThan(0.99f)
    }

    @Test
    fun `embedding with low raw score calibrates to low confidence`() {
        // Embedding at raw 0.5 → calibrated should be lower
        val confidence = ConfidenceCalibrator.calibrate(0.5f, ExtractorType.EMBEDDING)
        assertThat(confidence).isLessThan(0.6f)
    }

    @Test
    fun `embedding with high raw score calibrates above threshold`() {
        // Embedding at raw 1.0 → calibrated should be above 0.85 threshold
        val confidence = ConfidenceCalibrator.calibrate(1.0f, ExtractorType.EMBEDDING)
        assertThat(confidence).isGreaterThan(0.85f)
    }

    @Test
    fun `keyword with medium raw score calibrates reasonably`() {
        // Keyword at raw 0.65 (threshold) → calibrated should be near 0.5-0.7
        val confidence = ConfidenceCalibrator.calibrate(0.65f, ExtractorType.KEYWORD)
        assertThat(confidence).isGreaterThan(0.3f)
        assertThat(confidence).isLessThan(0.9f)
    }

    @Test
    fun `ner with high raw score calibrates high`() {
        val confidence = ConfidenceCalibrator.calibrate(1.0f, ExtractorType.NER)
        assertThat(confidence).isGreaterThan(0.9f)
    }

    @Test
    fun `composite extractor is reliable`() {
        // Composite = multiple extractors agreed → high confidence at raw 1.0
        val confidence = ConfidenceCalibrator.calibrate(1.0f, ExtractorType.COMPOSITE)
        assertThat(confidence).isGreaterThan(0.95f)
    }

    @Test
    fun `calibration output is between 0 and 1`() {
        for (extractor in ExtractorType.entries) {
            for (rawScore in listOf(0f, 0.1f, 0.3f, 0.5f, 0.7f, 0.9f, 1.0f)) {
                val confidence = ConfidenceCalibrator.calibrate(rawScore, extractor)
                assertThat(confidence).isAtLeast(0f)
                assertThat(confidence).isAtMost(1f)
            }
        }
    }

    @Test
    fun `calibration is monotonic — higher raw = higher confidence`() {
        val low = ConfidenceCalibrator.calibrate(0.3f, ExtractorType.REGEX)
        val mid = ConfidenceCalibrator.calibrate(0.6f, ExtractorType.REGEX)
        val high = ConfidenceCalibrator.calibrate(0.9f, ExtractorType.REGEX)
        assertThat(low).isLessThan(mid)
        assertThat(mid).isLessThan(high)
    }

    @Test
    fun `regex is more reliable than embedding at same raw score`() {
        val regexConf = ConfidenceCalibrator.calibrate(0.85f, ExtractorType.REGEX)
        val embeddingConf = ConfidenceCalibrator.calibrate(0.85f, ExtractorType.EMBEDDING)
        assertThat(regexConf).isGreaterThan(embeddingConf)
    }
}
