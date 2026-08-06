// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.system.handler

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.skill.SystemOperation
import com.roshan.persona.common.AppError
import com.roshan.persona.common.Result
import com.roshan.persona.system.executor.ResourceCategory
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0136: [feature] ClipboardHandlerTest verified

class ClipboardHandlerTest {

    private class FakeClipboardService(
        private var currentText: String? = null,
        private val copyResult: Boolean = true,
    ) : ClipboardService {
        val copyCalls = mutableListOf<String>()
            private set

        override fun copyText(text: String): Boolean {
            copyCalls.add(text)
            currentText = text
            return copyResult
        }
        override fun getCurrentText(): String? = currentText
    }

    private fun makeHandler(service: FakeClipboardService) = ClipboardHandler(service)

    // ─── Tests ─────────────────────────────────────────────────────────────

    @Test
    fun `isAvailable always returns true`() {
        assertThat(makeHandler(FakeClipboardService()).isAvailable()).isTrue()
    }

    @Test
    fun `execute copies text to clipboard`() = runTest {
        val service = FakeClipboardService(currentText = null)
        makeHandler(service).execute(SystemOperation.CopyToClipboard("Hello world"))
        assertThat(service.copyCalls).hasSize(1)
        assertThat(service.copyCalls[0]).isEqualTo("Hello world")
    }

    @Test
    fun `execute skips copy when clipboard already has same text (idempotent)`() = runTest {
        val service = FakeClipboardService(currentText = "Hello world")
        val result = makeHandler(service).execute(SystemOperation.CopyToClipboard("Hello world"))
        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).warnings).contains("idempotent_skip")
        assertThat(service.copyCalls).isEmpty()
    }

    @Test
    fun `execute copies when clipboard has different text`() = runTest {
        val service = FakeClipboardService(currentText = "Old text")
        makeHandler(service).execute(SystemOperation.CopyToClipboard("New text"))
        assertThat(service.copyCalls).hasSize(1)
        assertThat(service.copyCalls[0]).isEqualTo("New text")
    }

    @Test
    fun `execute copies when getCurrentText returns null (background)`() = runTest {
        val service = FakeClipboardService(currentText = null)
        makeHandler(service).execute(SystemOperation.CopyToClipboard("Hello"))
        assertThat(service.copyCalls).hasSize(1)
    }

    @Test
    fun `execute returns Failure Unknown when copy fails`() = runTest {
        val service = FakeClipboardService(copyResult = false)
        val result = makeHandler(service).execute(SystemOperation.CopyToClipboard("Hello"))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unknown::class.java)
    }

    @Test
    fun `execute copies empty string (idempotent check is content-based not length-based)`() = runTest {
        val service = FakeClipboardService(currentText = "non-empty")
        makeHandler(service).execute(SystemOperation.CopyToClipboard(""))
        assertThat(service.copyCalls).hasSize(1)
        assertThat(service.copyCalls[0]).isEmpty()
    }

    @Test
    fun `idempotent check is case-sensitive (Hello != hello)`() = runTest {
        val service = FakeClipboardService(currentText = "Hello")
        makeHandler(service).execute(SystemOperation.CopyToClipboard("hello"))
        assertThat(service.copyCalls).hasSize(1)  // copied because case differs
    }

    @Test
    fun `resourceCategory is SETTINGS`() {
        assertThat(makeHandler(FakeClipboardService()).resourceCategory).isEqualTo(ResourceCategory.SETTINGS)
    }

    @Test
    fun `requiredPermissions is empty`() {
        assertThat(makeHandler(FakeClipboardService()).requiredPermissions).isEmpty()
    }
}
