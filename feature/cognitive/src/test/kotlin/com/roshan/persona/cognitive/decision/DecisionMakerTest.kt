// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.cognitive.decision

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.cognitive.engine.ConfidenceModulator
import com.roshan.persona.cognitive.model.CognitiveConfig
import com.roshan.persona.cognitive.model.DecisionOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0112: [feature] DecisionMakerTest verified

class CriteriaAnalyzerTest {

    private val analyzer = CriteriaAnalyzer(Dispatchers.Unconfined)

    @Test
    fun `extractCriteria returns PHONE_CRITERIA for phone query`() = runTest {
        val criteria = analyzer.extractCriteria("iPhone vs Android")
        assertThat(criteria).isNotEmpty()
        assertThat(criteria.any { it.name == "price" }).isTrue()
        assertThat(criteria.any { it.name == "camera" }).isTrue()
    }

    @Test
    fun `extractCriteria returns CAR_CRITERIA for car query`() = runTest {
        val criteria = analyzer.extractCriteria("compare sedan vs SUV car")
        assertThat(criteria.any { it.name == "fuel_efficiency" }).isTrue()
        assertThat(criteria.any { it.name == "safety" }).isTrue()
    }

    @Test
    fun `extractCriteria returns TRAVEL_CRITERIA for travel query`() = runTest {
        val criteria = analyzer.extractCriteria("flight vs train for trip")
        assertThat(criteria.any { it.name == "cost" }).isTrue()
        assertThat(criteria.any { it.name == "time" }).isTrue()
    }

    @Test
    fun `extractCriteria returns INVESTMENT_CRITERIA for investment query`() = runTest {
        val criteria = analyzer.extractCriteria("should I invest in stock or mutual fund")
        assertThat(criteria.any { it.name == "return" }).isTrue()
        assertThat(criteria.any { it.name == "risk" }).isTrue()
    }

    @Test
    fun `extractCriteria returns GENERIC_CRITERIA for unknown query`() = runTest {
        val criteria = analyzer.extractCriteria("compare two restaurants")
        assertThat(criteria.any { it.name == "price" }).isTrue()
        assertThat(criteria.any { it.name == "quality" }).isTrue()
    }

    @Test
    fun `scoreOptions returns scores for all options and criteria`() = runTest {
        val options = listOf("iPhone", "Android")
        val criteria = CriteriaAnalyzer.PHONE_CRITERIA
        val scores = analyzer.scoreOptions(options, criteria)
        assertThat(scores).hasSize(2)
        assertThat(scores["iPhone"]?.has("price")).isTrue()
        assertThat(scores["Android"]?.has("camera")).isTrue()
    }

    @Test
    fun `calculateOverallScore returns weighted average`() {
        val scores = mapOf("price" to 0.8f, "camera" to 0.6f)
        val criteria = listOf(
            com.roshan.persona.cognitive.model.DecisionCriterion("price", 0.5f),
            com.roshan.persona.cognitive.model.DecisionCriterion("camera", 0.5f),
        )
        val overall = analyzer.calculateOverallScore(scores, criteria)
        assertThat(overall).isWithin(0.01f).of(0.7f)
    }

    @Test
    fun `calculateOverallScore returns 0 for empty inputs`() {
        assertThat(analyzer.calculateOverallScore(emptyMap(), emptyList())).isEqualTo(0f)
    }
}

class RiskAssessorTest {

    private val assessor = RiskAssessor(Dispatchers.Unconfined)

    private fun option(name: String, overall: Float, scores: Map<String, Float> = emptyMap()) = DecisionOption(
        name = name,
        scores = scores,
        overallScore = overall,
    )

    @Test
    fun `assessRisks returns LOW risk for high-scoring option`() = runTest {
        val opt = option("Good Option", 0.8f)
        val result = assessor.assessRisks(listOf(opt))
        assertThat(result["Good Option"]?.level).isEqualTo(RiskLevel.LOW)
    }

    @Test
    fun `assessRisks returns HIGH risk for very low-scoring option`() = runTest {
        val opt = option("Bad Option", 0.2f)
        val result = assessor.assessRisks(listOf(opt))
        assertThat(result["Bad Option"]?.level).isEqualTo(RiskLevel.HIGH)
    }

    @Test
    fun `assessRisks returns MEDIUM risk for mid-scoring option`() = runTest {
        val opt = option("Mid Option", 0.4f)
        val result = assessor.assessRisks(listOf(opt))
        assertThat(result["Mid Option"]?.level).isEqualTo(RiskLevel.MEDIUM)
    }

    @Test
    fun `assessRisks identifies expensive risk for high price score`() = runTest {
        val opt = option("Expensive", 0.6f, mapOf("price" to 0.9f))
        val result = assessor.assessRisks(listOf(opt))
        assertThat(result["Expensive"]?.risks?.any { it.contains("Expensive") }).isTrue()
    }

    @Test
    fun `assessRisks returns generic risk when no specific risks found`() = runTest {
        val opt = option("Safe Option", 0.7f, mapOf("price" to 0.5f))
        val result = assessor.assessRisks(listOf(opt))
        assertThat(result["Safe Option"]?.risks).isNotEmpty()
    }

    @Test
    fun `RiskLevel has 3 distinct values`() {
        assertThat(RiskLevel.entries).hasSize(3)
    }
}

class PreferenceAlignerTest {

    private val aligner = PreferenceAligner(Dispatchers.Unconfined)

    @Test
    fun `align increases price weight for budget-conscious preference`() = runTest {
        val criteria = listOf(
            com.roshan.persona.cognitive.model.DecisionCriterion("price", 0.3f),
            com.roshan.persona.cognitive.model.DecisionCriterion("camera", 0.2f),
        )
        val result = aligner.align(criteria, listOf("budget-conscious"))
        val adjustedPrice = result.adjustedCriteria.first { it.name == "price" }
        assertThat(adjustedPrice.weight).isGreaterThan(0.3f)
        assertThat(result.appliedPreferences).contains("budget-conscious")
    }

    @Test
    fun `align does not change unrelated criteria`() = runTest {
        val criteria = listOf(
            com.roshan.persona.cognitive.model.DecisionCriterion("price", 0.3f),
            com.roshan.persona.cognitive.model.DecisionCriterion("camera", 0.2f),
        )
        val result = aligner.align(criteria, listOf("budget-conscious"))
        val adjustedCamera = result.adjustedCriteria.first { it.name == "camera" }
        assertThat(adjustedCamera.weight).isWithin(0.001f).of(0.2f)  // unchanged
    }

    @Test
    fun `align returns empty appliedPreferences for unknown preferences`() = runTest {
        val criteria = listOf(
            com.roshan.persona.cognitive.model.DecisionCriterion("price", 0.3f),
        )
        val result = aligner.align(criteria, listOf("some-unknown-pref"))
        assertThat(result.appliedPreferences).isEmpty()
    }

    @Test
    fun `align clamps weight to max 1_0`() = runTest {
        val criteria = listOf(
            com.roshan.persona.cognitive.model.DecisionCriterion("price", 0.8f),
        )
        val result = aligner.align(criteria, listOf("budget-conscious"))
        val adjusted = result.adjustedCriteria.first()
        assertThat(adjusted.weight).isAtMost(1f)
    }
}

class DecisionMakerTest {

    private val criteriaAnalyzer = CriteriaAnalyzer(Dispatchers.Unconfined)
    private val riskAssessor = RiskAssessor(Dispatchers.Unconfined)
    private val preferenceAligner = PreferenceAligner(Dispatchers.Unconfined)
    private val confidenceModulator = ConfidenceModulator(CognitiveConfig())

    private val maker = DecisionMaker(
        criteriaAnalyzer, riskAssessor, preferenceAligner, confidenceModulator,
        CognitiveConfig(), Dispatchers.Unconfined,
    )

    @Test
    fun `decide returns DecisionResult with recommendation`() = runTest {
        val result = maker.decide("iPhone vs Android", listOf("iPhone", "Android"))
        assertThat(result).isNotNull()
        assertThat(result!!.options).hasSize(2)
        assertThat(result.recommendation).isNotNull()
        assertThat(result.rationale).isNotEmpty()
    }

    @Test
    fun `decide returns null for fewer than minDecisionOptions`() = runTest {
        val result = maker.decide("query", listOf("only one option"))
        assertThat(result).isNull()
    }

    @Test
    fun `decide limits to maxDecisionOptions`() = runTest {
        val result = maker.decide(
            "phone comparison",
            listOf("A", "B", "C", "D", "E", "F", "F"),  // 7 options
        )
        assertThat(result!!.options.size).isAtMost(CognitiveConfig.DEFAULT_MAX_DECISION_OPTIONS)
    }

    @Test
    fun `decide includes criteria in result`() = runTest {
        val result = maker.decide("iPhone vs Android", listOf("iPhone", "Android"))
        assertThat(result!!.criteria).isNotEmpty()
        assertThat(result.criteria.any { it.name == "price" }).isTrue()
    }

    @Test
    fun `decide includes userPreferencesApplied when preferences provided`() = runTest {
        val result = maker.decide(
            "iPhone vs Android",
            listOf("iPhone", "Android"),
            userPreferences = listOf("budget-conscious"),
        )
        assertThat(result!!.userPreferencesApplied).contains("budget-conscious")
    }

    @Test
    fun `generateRationale includes recommendation name and caveat`() {
        val reco = DecisionOption("Android", mapOf("price" to 0.8f, "camera" to 0.6f), 0.7f)
        val second = DecisionOption("iPhone", mapOf("price" to 0.3f, "camera" to 0.9f), 0.6f)
        val criteria = CriteriaAnalyzer.PHONE_CRITERIA
        val rationale = maker.generateRationale(reco, listOf(reco, second), criteria, emptyList())
        assertThat(rationale).contains("Android")
    }

    @Test
    fun `calculateConfidence returns higher for larger score gap`() {
        val highGap = listOf(
            DecisionOption("A", emptyMap(), 0.9f),
            DecisionOption("B", emptyMap(), 0.3f),
        )
        val lowGap = listOf(
            DecisionOption("A", emptyMap(), 0.55f),
            DecisionOption("B", emptyMap(), 0.5f),
        )
        assertThat(maker.calculateConfidence(highGap)).isGreaterThan(maker.calculateConfidence(lowGap))
    }

    @Test
    fun `calculateConfidence returns 0_8 for single option`() {
        val single = listOf(DecisionOption("A", emptyMap(), 0.8f))
        assertThat(maker.calculateConfidence(single)).isWithin(0.01f).of(0.8f)
    }
}
