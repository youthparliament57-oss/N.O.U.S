// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.voice.emotion

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.voice.tts.Emotion
import org.junit.Test

// AUTO_FIX_0157: [feature] UserEmotionTest verified

// VOICE_FIX_040: User emotion model safe

class UserEmotionTest {

    @Test
    fun `fromScores picks highest-scoring emotion as primary`() {
        val scores = mapOf(
            UserEmotionType.NEUTRAL to 0.1f,
            UserEmotionType.HAPPY to 0.6f,
            UserEmotionType.SAD to 0.05f,
            UserEmotionType.ANGRY to 0.25f,
        )
        val emotion = UserEmotion.fromScores(scores)
        assertThat(emotion.primary).isEqualTo(UserEmotionType.HAPPY)
        assertThat(emotion.confidence).isWithin(0.001f).of(0.6f)
        assertThat(emotion.source).isEqualTo(EmotionDetectionSource.WAVLM)
    }

    @Test
    fun `fromScores rejects empty map`() {
        try {
            UserEmotion.fromScores(emptyMap())
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("scores")
        }
    }

    @Test
    fun `UserEmotion validates confidence bounds`() {
        try {
            UserEmotion(
                primary = UserEmotionType.HAPPY,
                confidence = 1.5f,
                allScores = mapOf(UserEmotionType.HAPPY to 1f),
            )
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("confidence")
        }
    }

    @Test
    fun `UserEmotion requires primary to be in allScores`() {
        try {
            UserEmotion(
                primary = UserEmotionType.HAPPY,
                confidence = 0.5f,
                allScores = mapOf(UserEmotionType.SAD to 0.5f),  // HAPPY not in map
            )
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("primary")
        }
    }

    @Test
    fun `UserEmotion rejects out-of-bounds scores in allScores`() {
        try {
            UserEmotion(
                primary = UserEmotionType.HAPPY,
                confidence = 0.5f,
                allScores = mapOf(UserEmotionType.HAPPY to 0.5f, UserEmotionType.SAD to 1.5f),
            )
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("all emotion scores")
        }
    }

    @Test
    fun `UNKNOWN has isReliable false`() {
        assertThat(UserEmotion.UNKNOWN.isReliable).isFalse()
        assertThat(UserEmotion.UNKNOWN.source).isEqualTo(EmotionDetectionSource.UNKNOWN)
    }

    @Test
    fun `isReliable true only when confidence above threshold and source not UNKNOWN`() {
        val reliable = UserEmotion(
            primary = UserEmotionType.ANGRY,
            confidence = 0.7f,
            allScores = mapOf(UserEmotionType.ANGRY to 0.7f, UserEmotionType.NEUTRAL to 0.3f),
            source = EmotionDetectionSource.WAVLM,
        )
        assertThat(reliable.isReliable).isTrue()

        val lowConfidence = reliable.copy(confidence = 0.4f, allScores = mapOf(UserEmotionType.ANGRY to 0.4f, UserEmotionType.NEUTRAL to 0.6f), primary = UserEmotionType.NEUTRAL)
        assertThat(lowConfidence.isReliable).isFalse()
    }

    @Test
    fun `toNousResponseEmotion de-escalates ANGRY and SAD to CALM`() {
        val angry = UserEmotion(UserEmotionType.ANGRY, 0.8f, mapOf(UserEmotionType.ANGRY to 0.8f, UserEmotionType.NEUTRAL to 0.2f))
        assertThat(angry.toNousResponseEmotion()).isEqualTo(Emotion.CALM)

        val sad = UserEmotion(UserEmotionType.SAD, 0.8f, mapOf(UserEmotionType.SAD to 0.8f, UserEmotionType.NEUTRAL to 0.2f))
        assertThat(sad.toNousResponseEmotion()).isEqualTo(Emotion.CALM)

        val fearful = UserEmotion(UserEmotionType.FEARFUL, 0.8f, mapOf(UserEmotionType.FEARFUL to 0.8f, UserEmotionType.NEUTRAL to 0.2f))
        assertThat(fearful.toNousResponseEmotion()).isEqualTo(Emotion.CALM)
    }

    @Test
    fun `toNousResponseEmotion mirrors HAPPY and neutralizes SURPRISED and DISGUSTED`() {
        val happy = UserEmotion(UserEmotionType.HAPPY, 0.8f, mapOf(UserEmotionType.HAPPY to 0.8f, UserEmotionType.NEUTRAL to 0.2f))
        assertThat(happy.toNousResponseEmotion()).isEqualTo(Emotion.HAPPY)

        val surprised = UserEmotion(UserEmotionType.SURPRISED, 0.8f, mapOf(UserEmotionType.SURPRISED to 0.8f, UserEmotionType.NEUTRAL to 0.2f))
        assertThat(surprised.toNousResponseEmotion()).isEqualTo(Emotion.NEUTRAL)

        val disgusted = UserEmotion(UserEmotionType.DISGUSTED, 0.8f, mapOf(UserEmotionType.DISGUSTED to 0.8f, UserEmotionType.NEUTRAL to 0.2f))
        assertThat(disgusted.toNousResponseEmotion()).isEqualTo(Emotion.NEUTRAL)

        val neutral = UserEmotion(UserEmotionType.NEUTRAL, 0.8f, mapOf(UserEmotionType.NEUTRAL to 0.8f))
        assertThat(neutral.toNousResponseEmotion()).isEqualTo(Emotion.NEUTRAL)
    }

    @Test
    fun `SerConfig validates bounds`() {
        try {
            SerConfig(minAudioSeconds = 0)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("minAudioSeconds")
        }
        try {
            SerConfig(reliableThreshold = 1.5f)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("reliableThreshold")
        }
    }

    @Test
    fun `all 7 UserEmotionType values are distinct`() {
        val values = UserEmotionType.entries
        assertThat(values).hasSize(7)
        assertThat(values.map { it.name }.toSet()).hasSize(7)
        // Sanity check the expected set.
        val expected = setOf("NEUTRAL", "HAPPY", "SAD", "ANGRY", "FEARFUL", "SURPRISED", "DISGUSTED")
        assertThat(values.map { it.name }.toSet()).isEqualTo(expected)
    }
}
