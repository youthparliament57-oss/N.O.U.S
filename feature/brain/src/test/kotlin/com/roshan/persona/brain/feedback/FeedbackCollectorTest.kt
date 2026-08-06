// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.brain.feedback

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.common.CorrelationId
import org.junit.Test

// AUTO_FIX_0063: [feature] FeedbackCollectorTest verified

class FeedbackCollectorTest {

    private val collector = FeedbackCollector()

    @Test
    fun `recordExplicit stores entry`() {
        val id = CorrelationId.generate()
        collector.recordExplicit(id, FeedbackRating.POSITIVE)

        val entries = collector.getForRequest(id)
        assertThat(entries).hasSize(1)
        assertThat(entries[0].rating).isEqualTo(FeedbackRating.POSITIVE)
        assertThat(entries[0].type).isEqualTo(FeedbackType.EXPLICIT)
    }

    @Test
    fun `recordExplicit with comment stores comment`() {
        val id = CorrelationId.generate()
        collector.recordExplicit(id, FeedbackRating.NEGATIVE, "Wrong answer")

        val entries = collector.getForRequest(id)
        assertThat(entries[0].comment).isEqualTo("Wrong answer")
    }

    @Test
    fun `detectImplicit detects thank you`() {
        val id = CorrelationId.generate()
        val detected = collector.detectImplicit(id, "Thank you!")

        assertThat(detected).isTrue()
        val entries = collector.getForRequest(id)
        assertThat(entries).hasSize(1)
        assertThat(entries[0].rating).isEqualTo(FeedbackRating.POSITIVE)
        assertThat(entries[0].type).isEqualTo(FeedbackType.IMPLICIT)
    }

    @Test
    fun `detectImplicit detects thanks`() {
        val id = CorrelationId.generate()
        val detected = collector.detectImplicit(id, "thanks")

        assertThat(detected).isTrue()
    }

    @Test
    fun `detectImplicit detects perfect`() {
        val id = CorrelationId.generate()
        val detected = collector.detectImplicit(id, "That's perfect!")

        assertThat(detected).isTrue()
    }

    @Test
    fun `detectImplicit detects wrong`() {
        val id = CorrelationId.generate()
        val detected = collector.detectImplicit(id, "That's wrong")

        assertThat(detected).isTrue()
        val entries = collector.getForRequest(id)
        assertThat(entries[0].rating).isEqualTo(FeedbackRating.NEGATIVE)
    }

    @Test
    fun `detectImplicit returns false for neutral input`() {
        val id = CorrelationId.generate()
        val detected = collector.detectImplicit(id, "What's the weather?")

        assertThat(detected).isFalse()
    }

    @Test
    fun `detectImplicit is case insensitive`() {
        val id = CorrelationId.generate()
        val detected = collector.detectImplicit(id, "THANK YOU")

        assertThat(detected).isTrue()
    }

    @Test
    fun `recordCorrection stores correction`() {
        val id = CorrelationId.generate()
        collector.recordCorrection(id, "The correct answer is 42")

        val entries = collector.getForRequest(id)
        assertThat(entries).hasSize(1)
        assertThat(entries[0].type).isEqualTo(FeedbackType.CORRECTION)
        assertThat(entries[0].comment).isEqualTo("The correct answer is 42")
    }

    @Test
    fun `getStats returns aggregate stats`() {
        collector.recordExplicit(CorrelationId.generate(), FeedbackRating.POSITIVE)
        collector.recordExplicit(CorrelationId.generate(), FeedbackRating.NEGATIVE)
        collector.detectImplicit(CorrelationId.generate(), "thanks")
        collector.recordCorrection(CorrelationId.generate(), "correct answer")

        val stats = collector.getStats()
        assertThat(stats.totalEntries).isEqualTo(4)
        assertThat(stats.positiveCount).isEqualTo(2)
        assertThat(stats.negativeCount).isEqualTo(2)
        assertThat(stats.explicitCount).isEqualTo(2)
        assertThat(stats.implicitCount).isEqualTo(1)
        assertThat(stats.correctionCount).isEqualTo(1)
        assertThat(stats.satisfactionRate).isWithin(0.01f).of(0.5f)
    }

    @Test
    fun `deleteForRequest removes entries for that request`() {
        val id1 = CorrelationId.generate()
        val id2 = CorrelationId.generate()
        collector.recordExplicit(id1, FeedbackRating.POSITIVE)
        collector.recordExplicit(id2, FeedbackRating.POSITIVE)

        val removed = collector.deleteForRequest(id1)

        assertThat(removed).isEqualTo(1)
        assertThat(collector.getForRequest(id1)).isEmpty()
        assertThat(collector.getForRequest(id2)).hasSize(1)
    }

    @Test
    fun `clear removes all entries`() {
        collector.recordExplicit(CorrelationId.generate(), FeedbackRating.POSITIVE)
        collector.recordExplicit(CorrelationId.generate(), FeedbackRating.NEGATIVE)

        collector.clear()

        assertThat(collector.getAll()).isEmpty()
    }

    @Test
    fun `getAll returns entries sorted by timestamp descending`() {
        val id1 = CorrelationId.generate()
        val id2 = CorrelationId.generate()
        collector.recordExplicit(id1, FeedbackRating.POSITIVE)
        Thread.sleep(10)
        collector.recordExplicit(id2, FeedbackRating.POSITIVE)

        val all = collector.getAll()
        assertThat(all).hasSize(2)
        assertThat(all[0].timestamp).isAtLeast(all[1].timestamp)
    }

    @Test
    fun `max entries is enforced`() {
        val smallCollector = FeedbackCollector(maxEntries = 3)
        for (i in 1..5) {
            smallCollector.recordExplicit(CorrelationId.generate(), FeedbackRating.POSITIVE)
        }

        val stats = smallCollector.getStats()
        assertThat(stats.totalEntries).isEqualTo(3)
    }
}
