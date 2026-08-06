// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.system.handler

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.skill.SystemOperation
import com.roshan.persona.common.AppError
import com.roshan.persona.common.Result
import com.roshan.persona.system.executor.ResourceCategory
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0152: [feature] AppHandlerTest verified

class AppHandlerTest {

    private class FakeAppLauncherService(
        private val installedApps: List<Pair<String, String>> = listOf(
            "com.google.android.youtube" to "YouTube",
            "com.whatsapp" to "WhatsApp",
            "com.instagram.android" to "Instagram",
        ),
        private val launchResult: Boolean = true,
        private val foregroundPackage: String? = null,
    ) : AppLauncherService {
        val launchCalls = mutableListOf<String>()
            private set

        override fun launchByPackage(packageName: String): Boolean {
            launchCalls.add(packageName)
            return launchResult
        }
        override fun findPackageByName(appName: String): String? {
            return AppNameMatcher().findBestMatch(appName, installedApps)?.first
        }
        override fun getAppDisplayName(packageName: String): String? =
            installedApps.firstOrNull { it.first == packageName }?.second
        override fun isAppInForeground(packageName: String): Boolean =
            foregroundPackage == packageName
    }

    private fun makeHandler(service: FakeAppLauncherService) = AppHandler(service)

    // ─── Tests ─────────────────────────────────────────────────────────────

    @Test
    fun `isAvailable always returns true`() {
        assertThat(makeHandler(FakeAppLauncherService()).isAvailable()).isTrue()
    }

    @Test
    fun `execute launches app directly when packageName provided`() = runTest {
        val service = FakeAppLauncherService()
        makeHandler(service).execute(SystemOperation.OpenApp(packageName = "com.whatsapp"))
        assertThat(service.launchCalls).hasSize(1)
        assertThat(service.launchCalls[0]).isEqualTo("com.whatsapp")
    }

    @Test
    fun `execute resolves fuzzy app name when packageName is null`() = runTest {
        val service = FakeAppLauncherService()
        makeHandler(service).execute(
            SystemOperation.OpenApp(packageName = null, appName = "YouTube")
        )
        assertThat(service.launchCalls).hasSize(1)
        assertThat(service.launchCalls[0]).isEqualTo("com.google.android.youtube")
    }

    @Test
    fun `execute resolves fuzzy name with typo via Levenshtein`() = runTest {
        val service = FakeAppLauncherService()
        makeHandler(service).execute(
            SystemOperation.OpenApp(packageName = null, appName = "Youtoob")
        )
        // Levenshtein distance "youtoob" vs "youtube" = 1 (within threshold 3).
        assertThat(service.launchCalls).hasSize(1)
        assertThat(service.launchCalls[0]).isEqualTo("com.google.android.youtube")
    }

    @Test
    fun `execute returns Failure Unsupported when app not found by name`() = runTest {
        val service = FakeAppLauncherService()
        val result = makeHandler(service).execute(
            SystemOperation.OpenApp(packageName = null, appName = "NonexistentApp12345")
        )
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unsupported::class.java)
        assertThat(service.launchCalls).isEmpty()
    }

    @Test
    fun `execute returns Failure Unsupported when both packageName and appName are null`() = runTest {
        val service = FakeAppLauncherService()
        val result = makeHandler(service).execute(SystemOperation.OpenApp(packageName = null))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unsupported::class.java)
    }

    @Test
    fun `execute returns Failure Unknown when launch fails`() = runTest {
        val service = FakeAppLauncherService(launchResult = false)
        val result = makeHandler(service).execute(SystemOperation.OpenApp(packageName = "com.whatsapp"))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unknown::class.java)
    }

    @Test
    fun `execute skips launch via idempotent when app already foreground`() = runTest {
        val service = FakeAppLauncherService(foregroundPackage = "com.whatsapp")
        val result = makeHandler(service).execute(SystemOperation.OpenApp(packageName = "com.whatsapp"))
        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).warnings).contains("idempotent_skip")
        assertThat(service.launchCalls).isEmpty()
    }

    @Test
    fun `describeOperation is not overridden (StatelessSystemOperationHandler base)`() {
        // AppHandler extends StatelessSystemOperationHandler which doesn't
        // implement describeOperation. Just verify id/displayName.
        val handler = makeHandler(FakeAppLauncherService())
        assertThat(handler.id).isEqualTo("app")
        assertThat(handler.displayName).isEqualTo("App Launcher")
    }

    @Test
    fun `resourceCategory is SETTINGS`() {
        assertThat(makeHandler(FakeAppLauncherService()).resourceCategory).isEqualTo(ResourceCategory.SETTINGS)
    }

    @Test
    fun `requiredPermissions is empty`() {
        assertThat(makeHandler(FakeAppLauncherService()).requiredPermissions).isEmpty()
    }
}

class AppNameMatcherTest {

    private val matcher = AppNameMatcher()
    private val apps = listOf(
        "com.google.android.youtube" to "YouTube",
        "com.whatsapp" to "WhatsApp",
        "com.instagram.android" to "Instagram",
        "com.facebook.katana" to "Facebook",
    )

    @Test
    fun `exact match (case-insensitive) returns instantly`() {
        val match = matcher.findBestMatch("youtube", apps)
        assertThat(match?.first).isEqualTo("com.google.android.youtube")
    }

    @Test
    fun `exact match with original case works`() {
        val match = matcher.findBestMatch("YouTube", apps)
        assertThat(match?.first).isEqualTo("com.google.android.youtube")
    }

    @Test
    fun `contains match works for partial query`() {
        val match = matcher.findBestMatch("Tube", apps)
        assertThat(match?.first).isEqualTo("com.google.android.youtube")
    }

    @Test
    fun `Levenshtein matches typo within threshold`() {
        val match = matcher.findBestMatch("Youtoob", apps)
        assertThat(match?.first).isEqualTo("com.google.android.youtube")
    }

    @Test
    fun `returns null when no match within threshold`() {
        val match = matcher.findBestMatch("TotallyUnknownApp", apps)
        assertThat(match).isNull()
    }

    @Test
    fun `returns null for blank query`() {
        assertThat(matcher.findBestMatch("", apps)).isNull()
        assertThat(matcher.findBestMatch("   ", apps)).isNull()
    }

    @Test
    fun `returns null for empty app list`() {
        assertThat(matcher.findBestMatch("YouTube", emptyList())).isNull()
    }

    @Test
    fun `levenshtein distance for identical strings is 0`() {
        assertThat(matcher.levenshtein("hello", "hello")).isEqualTo(0)
    }

    @Test
    fun `levenshtein distance for one substitution is 1`() {
        assertThat(matcher.levenshtein("hello", "hallo")).isEqualTo(1)
    }

    @Test
    fun `levenshtein distance handles insertion and deletion`() {
        assertThat(matcher.levenshtein("cat", "cats")).isEqualTo(1)  // insertion
        assertThat(matcher.levenshtein("cats", "cat")).isEqualTo(1)  // deletion
    }
}
