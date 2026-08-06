// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.persona.jit

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0106: [feature] JitContextFetcherTest verified

class JitContextPayloadTest {

    @Test
    fun `toPromptString returns empty for null payload`() {
        val payload = JitContextPayload()
        assertThat(payload.toPromptString()).isEmpty()
    }

    @Test
    fun `toPromptString includes battery info`() {
        val payload = JitContextPayload(
            battery = BatteryInfo(level = 45, isCharging = false, isLow = false),
        )
        val str = payload.toPromptString()
        assertThat(str).contains("Battery: 45%")
        assertThat(str).contains("not charging")
    }

    @Test
    fun `toPromptString includes media info`() {
        val payload = JitContextPayload(
            media = MediaInfo(track = "Bohemian Rhapsody", artist = "Queen", genre = "rock", packageName = "com.spotify"),
        )
        val str = payload.toPromptString()
        assertThat(str).contains("Bohemian Rhapsody")
        assertThat(str).contains("Queen")
    }

    @Test
    fun `toPromptString includes interaction gap in hours`() {
        val payload = JitContextPayload(
            interactionGapMs = 14 * 3_600_000L,  // 14 hours
        )
        val str = payload.toPromptString()
        assertThat(str).contains("14h ago")
    }

    @Test
    fun `toPromptString includes missed calls from frequent contacts`() {
        val payload = JitContextPayload(
            callLog = CallLogInfo(
                totalToday = 5, missedToday = 2,
                missedFromFrequent = listOf("Mom", "Rohit"),
            ),
        )
        val str = payload.toPromptString()
        assertThat(str).contains("Mom")
        assertThat(str).contains("Rohit")
    }

    @Test
    fun `toPromptString includes app usage`() {
        val payload = JitContextPayload(
            appUsage = AppUsageInfo(packageName = "com.youtube", appName = "YouTube", foregroundSeconds = 1800),
        )
        val str = payload.toPromptString()
        assertThat(str).contains("YouTube")
    }

    @Test
    fun `BatteryInfo validates level bounds`() {
        BatteryInfo(level = 50, isCharging = false, isLow = false)
        try {
            BatteryInfo(level = 150, isCharging = false, isLow = false)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("level")
        }
    }

    @Test
    fun `GossipTriggerType has 4 distinct values`() {
        assertThat(GossipTriggerType.entries).hasSize(4)
        assertThat(GossipTriggerType.entries.map { it.name }).containsExactly(
            "BATTERY_HUNGRY", "MUSIC_MOOD", "USER_NEGLECT", "SOCIAL_CHECK",
        )
    }
}

class GossipTriggerEvaluatorTest {

    @Test
    fun `BATTERY_HUNGRY fires when battery < 15 and not charging`() {
        val payload = JitContextPayload(
            battery = BatteryInfo(level = 5, isCharging = false, isLow = true),
        )
        val triggers = GossipTriggerEvaluator.evaluate(payload)
        assertThat(triggers.any { it.type == GossipTriggerType.BATTERY_HUNGRY }).isTrue()
    }

    @Test
    fun `BATTERY_HUNGRY does NOT fire when charging`() {
        val payload = JitContextPayload(
            battery = BatteryInfo(level = 5, isCharging = true, isLow = true),
        )
        val triggers = GossipTriggerEvaluator.evaluate(payload)
        assertThat(triggers.none { it.type == GossipTriggerType.BATTERY_HUNGRY }).isTrue()
    }

    @Test
    fun `BATTERY_HUNGRY does NOT fire when battery >= 15`() {
        val payload = JitContextPayload(
            battery = BatteryInfo(level = 50, isCharging = false, isLow = false),
        )
        val triggers = GossipTriggerEvaluator.evaluate(payload)
        assertThat(triggers.none { it.type == GossipTriggerType.BATTERY_HUNGRY }).isTrue()
    }

    @Test
    fun `MUSIC_MOOD fires for sad genre`() {
        val payload = JitContextPayload(
            media = MediaInfo("Tears in Heaven", "Eric Clapton", "sad", "com.spotify"),
        )
        val triggers = GossipTriggerEvaluator.evaluate(payload)
        val musicTrigger = triggers.first { it.type == GossipTriggerType.MUSIC_MOOD }
        assertThat(musicTrigger.hint).contains("empathetic")
    }

    @Test
    fun `MUSIC_MOOD fires for dance genre with hype hint`() {
        val payload = JitContextPayload(
            media = MediaInfo("Levels", "Avicii", "dance", "com.spotify"),
        )
        val triggers = GossipTriggerEvaluator.evaluate(payload)
        val musicTrigger = triggers.first { it.type == GossipTriggerType.MUSIC_MOOD }
        assertThat(musicTrigger.hint).contains("energy")
    }

    @Test
    fun `MUSIC_MOOD does NOT fire for unknown genre`() {
        val payload = JitContextPayload(
            media = MediaInfo("Podcast", "Some Creator", "unknown", "com.podcast"),
        )
        val triggers = GossipTriggerEvaluator.evaluate(payload)
        assertThat(triggers.none { it.type == GossipTriggerType.MUSIC_MOOD }).isTrue()
    }

    @Test
    fun `USER_NEGLECT fires when gap > 12 hours`() {
        val payload = JitContextPayload(
            interactionGapMs = 14 * 3_600_000L,  // 14 hours
        )
        val triggers = GossipTriggerEvaluator.evaluate(payload)
        val neglect = triggers.first { it.type == GossipTriggerType.USER_NEGLECT }
        assertThat(neglect.hint).contains("14 hours")
    }

    @Test
    fun `USER_NEGLECT does NOT fire when gap < 12 hours`() {
        val payload = JitContextPayload(
            interactionGapMs = 5 * 3_600_000L,  // 5 hours
        )
        val triggers = GossipTriggerEvaluator.evaluate(payload)
        assertThat(triggers.none { it.type == GossipTriggerType.USER_NEGLECT }).isTrue()
    }

    @Test
    fun `SOCIAL_CHECK fires when frequent contacts missed`() {
        val payload = JitContextPayload(
            callLog = CallLogInfo(totalToday = 3, missedToday = 1, missedFromFrequent = listOf("Mom")),
        )
        val triggers = GossipTriggerEvaluator.evaluate(payload)
        val social = triggers.first { it.type == GossipTriggerType.SOCIAL_CHECK }
        assertThat(social.hint).contains("Mom")
    }

    @Test
    fun `SOCIAL_CHECK does NOT fire when no missed frequent contacts`() {
        val payload = JitContextPayload(
            callLog = CallLogInfo(totalToday = 5, missedToday = 0, missedFromFrequent = emptyList()),
        )
        val triggers = GossipTriggerEvaluator.evaluate(payload)
        assertThat(triggers.none { it.type == GossipTriggerType.SOCIAL_CHECK }).isTrue()
    }

    @Test
    fun `multiple triggers fire and are sorted by priority desc`() {
        val payload = JitContextPayload(
            battery = BatteryInfo(5, false, true),
            media = MediaInfo("Sad Song", "Artist", "sad", "com.x"),
            interactionGapMs = 15 * 3_600_000L,
        )
        val triggers = GossipTriggerEvaluator.evaluate(payload)
        assertThat(triggers).hasSize(3)
        // Priorities: BATTERY_HUNGRY=8, USER_NEGLECT=6, MUSIC_MOOD=5
        assertThat(triggers[0].type).isEqualTo(GossipTriggerType.BATTERY_HUNGRY)
        assertThat(triggers[1].type).isEqualTo(GossipTriggerType.USER_NEGLECT)
        assertThat(triggers[2].type).isEqualTo(GossipTriggerType.MUSIC_MOOD)
    }

    @Test
    fun `no triggers fire for empty payload`() {
        val payload = JitContextPayload()
        val triggers = GossipTriggerEvaluator.evaluate(payload)
        assertThat(triggers).isEmpty()
    }
}

class JitContextFetcherTest {

    // ─── Fakes ─────────────────────────────────────────────────────────────

    private class FakeBatteryObserver(private val info: BatteryInfo?) : BatteryObserver {
        override fun fetch(): BatteryInfo? = info
    }

    private class FakeMediaObserver(private val info: MediaInfo?) : MediaObserver {
        override fun fetch(): MediaInfo? = info
        override fun hasPermission(): Boolean = info != null
    }

    private class FakeCallLogObserver(private val info: CallLogInfo?) : CallLogObserver {
        override fun fetch(): CallLogInfo? = info
        override fun hasPermission(): Boolean = info != null
    }

    private class FakeUsageStatsObserver(private val info: AppUsageInfo?) : UsageStatsObserver {
        override fun fetch(): AppUsageInfo? = info
        override fun hasPermission(): Boolean = info != null
    }

    private fun makeFetcher(
        battery: BatteryInfo? = null,
        media: MediaInfo? = null,
        callLog: CallLogInfo? = null,
        appUsage: AppUsageInfo? = null,
        gap: Long? = null,
    ): JitContextFetcher {
        return JitContextFetcher(
            batteryObserver = FakeBatteryObserver(battery),
            mediaObserver = FakeMediaObserver(media),
            callLogObserver = FakeCallLogObserver(callLog),
            usageStatsObserver = FakeUsageStatsObserver(appUsage),
            interactionGapProvider = { gap },
            dispatcher = Dispatchers.Unconfined,
        )
    }

    // ─── Tests ─────────────────────────────────────────────────────────────

    @Test
    fun `fetch returns payload with all observer data`() = runTest {
        val fetcher = makeFetcher(
            battery = BatteryInfo(45, false, false),
            media = MediaInfo("Song", "Artist", "pop", "com.x"),
            callLog = CallLogInfo(3, 1, listOf("Mom")),
            appUsage = AppUsageInfo("com.yt", "YouTube", 600),
            gap = 3_600_000L,
        )
        val payload = fetcher.fetch()
        assertThat(payload.battery?.level).isEqualTo(45)
        assertThat(payload.media?.track).isEqualTo("Song")
        assertThat(payload.callLog?.missedFromFrequent).contains("Mom")
        assertThat(payload.appUsage?.appName).isEqualTo("YouTube")
        assertThat(payload.interactionGapMs).isEqualTo(3_600_000L)
    }

    @Test
    fun `fetch evaluates gossip triggers`() = runTest {
        val fetcher = makeFetcher(
            battery = BatteryInfo(5, false, true),
            gap = 15 * 3_600_000L,
        )
        val payload = fetcher.fetch()
        assertThat(payload.gossipTriggers).hasSize(2)
        assertThat(payload.gossipTriggers.any { it.type == GossipTriggerType.BATTERY_HUNGRY }).isTrue()
        assertThat(payload.gossipTriggers.any { it.type == GossipTriggerType.USER_NEGLECT }).isTrue()
    }

    @Test
    fun `fetch returns empty payload when all observers return null`() = runTest {
        val fetcher = makeFetcher()
        val payload = fetcher.fetch()
        assertThat(payload.battery).isNull()
        assertThat(payload.media).isNull()
        assertThat(payload.callLog).isNull()
        assertThat(payload.appUsage).isNull()
        assertThat(payload.interactionGapMs).isNull()
        assertThat(payload.gossipTriggers).isEmpty()
    }

    @Test
    fun `fetch returns partial payload when some observers return null`() = runTest {
        val fetcher = makeFetcher(
            battery = BatteryInfo(80, true, false),
        )
        val payload = fetcher.fetch()
        assertThat(payload.battery?.level).isEqualTo(80)
        assertThat(payload.media).isNull()
    }

    @Test
    fun `fetch prompt string is non-empty when battery present`() = runTest {
        val fetcher = makeFetcher(battery = BatteryInfo(45, false, false))
        val payload = fetcher.fetch()
        assertThat(payload.toPromptString()).contains("Battery: 45%")
    }
}
