// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.system.handler

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.skill.SystemOperation
import com.roshan.persona.common.AppError
import com.roshan.persona.common.Result
import com.roshan.persona.system.executor.ResourceCategory
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0140: [feature] UrlHandlerTest verified

class UrlHandlerTest {

    private class FakeUrlLauncherService(
        private val openResult: Boolean = true,
    ) : UrlLauncherService {
        val openCalls = mutableListOf<String>()
            private set

        override fun openUrl(url: String): Boolean {
            openCalls.add(url)
            return openResult
        }
        override fun isSchemeAllowed(url: String): Boolean {
            val scheme = try {
                android.net.Uri.parse(url).scheme?.lowercase()
            } catch (t: Throwable) { null } ?: return false
            return scheme in UrlLauncherService.ALLOWED_SCHEMES
        }
    }

    private fun makeHandler(service: FakeUrlLauncherService) = UrlHandler(service)

    // ─── Tests ─────────────────────────────────────────────────────────────

    @Test
    fun `isAvailable always returns true`() {
        assertThat(makeHandler(FakeUrlLauncherService()).isAvailable()).isTrue()
    }

    @Test
    fun `execute opens HTTPS URL successfully`() = runTest {
        val service = FakeUrlLauncherService()
        makeHandler(service).execute(SystemOperation.OpenUrl("https://example.com"))
        assertThat(service.openCalls).hasSize(1)
        assertThat(service.openCalls[0]).isEqualTo("https://example.com")
    }

    @Test
    fun `execute opens HTTP URL successfully`() = runTest {
        val service = FakeUrlLauncherService()
        makeHandler(service).execute(SystemOperation.OpenUrl("http://example.com"))
        assertThat(service.openCalls).hasSize(1)
    }

    @Test
    fun `execute opens tel URL for dialer`() = runTest {
        val service = FakeUrlLauncherService()
        makeHandler(service).execute(SystemOperation.OpenUrl("tel:+919876543210"))
        assertThat(service.openCalls).hasSize(1)
    }

    @Test
    fun `execute opens mailto URL for email`() = runTest {
        val service = FakeUrlLauncherService()
        makeHandler(service).execute(SystemOperation.OpenUrl("mailto:user@example.com"))
        assertThat(service.openCalls).hasSize(1)
    }

    @Test
    fun `execute opens geo URL for maps`() = runTest {
        val service = FakeUrlLauncherService()
        makeHandler(service).execute(SystemOperation.OpenUrl("geo:0,0?q=Bangalore"))
        assertThat(service.openCalls).hasSize(1)
    }

    @Test
    fun `execute rejects file URL for security`() = runTest {
        val service = FakeUrlLauncherService()
        val result = makeHandler(service).execute(
            SystemOperation.OpenUrl("file:///data/user/0/com.roshan.persona/files/secret.txt")
        )
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unsupported::class.java)
        assertThat(service.openCalls).isEmpty()
    }

    @Test
    fun `execute rejects content URL for security`() = runTest {
        val service = FakeUrlLauncherService()
        val result = makeHandler(service).execute(
            SystemOperation.OpenUrl("content://com.android.contacts/data/1")
        )
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unsupported::class.java)
        assertThat(service.openCalls).isEmpty()
    }

    @Test
    fun `execute returns Failure Unsupported when no app handles URL`() = runTest {
        val service = FakeUrlLauncherService(openResult = false)
        val result = makeHandler(service).execute(SystemOperation.OpenUrl("https://example.com"))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unsupported::class.java)
    }

    @Test
    fun `execute rejects URL with no scheme`() = runTest {
        val service = FakeUrlLauncherService()
        val result = makeHandler(service).execute(SystemOperation.OpenUrl("example.com"))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat(service.openCalls).isEmpty()
    }

    @Test
    fun `resourceCategory is SETTINGS`() {
        assertThat(makeHandler(FakeUrlLauncherService()).resourceCategory).isEqualTo(ResourceCategory.SETTINGS)
    }

    @Test
    fun `requiredPermissions is empty`() {
        assertThat(makeHandler(FakeUrlLauncherService()).requiredPermissions).isEmpty()
    }

    @Test
    fun `ALLOWED_SCHEMES contains http https mailto tel sms geo market`() {
        val allowed = UrlLauncherService.ALLOWED_SCHEMES
        assertThat(allowed).contains("http")
        assertThat(allowed).contains("https")
        assertThat(allowed).contains("mailto")
        assertThat(allowed).contains("tel")
        assertThat(allowed).contains("sms")
        assertThat(allowed).contains("geo")
        assertThat(allowed).contains("market")
    }

    @Test
    fun `BLOCKED_SCHEMES contains file and content`() {
        val blocked = UrlLauncherService.BLOCKED_SCHEMES
        assertThat(blocked).contains("file")
        assertThat(blocked).contains("content")
    }
}
