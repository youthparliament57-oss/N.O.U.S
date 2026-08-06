// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.brain.skill.sandbox

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.common.Result
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0093: [feature] JsSandboxTest verified

class JsSandboxTest {

    @Test
    fun `FakeJsSandbox returns canned result for known script`() = runTest {
        val script = "function run() { return 'hello'; }"
        val sandbox = FakeJsSandbox(
            available = true,
            results = mapOf(script to "hello"),
        )

        val result = sandbox.execute(script, emptyMap())

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).data).isEqualTo("hello")
    }

    @Test
    fun `FakeJsSandbox returns default result for unknown script`() = runTest {
        val sandbox = FakeJsSandbox(available = true)
        val script = "console.log('test');"

        val result = sandbox.execute(script, emptyMap())

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).data).contains("executed:")
    }

    @Test
    fun `FakeJsSandbox records executed scripts`() = runTest {
        val sandbox = FakeJsSandbox(available = true)
        val script = "test script"
        val params = mapOf("key" to "value")

        sandbox.execute(script, params)

        val executed = sandbox.getExecutedScripts()
        assertThat(executed).hasSize(1)
        assertThat(executed.first().first).isEqualTo(script)
        assertThat(executed.first().second).containsEntry("key", "value")
    }

    @Test
    fun `FakeJsSandbox unavailable returns Failure`() = runTest {
        val sandbox = FakeJsSandbox(available = false)

        val result = sandbox.execute("test", emptyMap())

        assertThat(result).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `NoOpJsSandbox is not available`() {
        assertThat(NoOpJsSandbox.isAvailable()).isFalse()
    }

    @Test
    fun `NoOpJsSandbox execute returns Failure`() = runTest {
        val result = NoOpJsSandbox.execute("test", emptyMap())

        assertThat(result).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `SandboxLimits DEFAULT has 32MB memory`() {
        assertThat(SandboxLimits.DEFAULT.maxMemoryBytes).isEqualTo(32L * 1024 * 1024)
    }

    @Test
    fun `SandboxLimits DEFAULT has 5 second timeout`() {
        assertThat(SandboxLimits.DEFAULT.timeoutMs).isEqualTo(5_000L)
    }

    @Test
    fun `SandboxLimits STRICT has no HTTP API`() {
        assertThat(SandboxLimits.STRICT.allowedApis).doesNotContain(JsApi.HTTP)
    }

    @Test
    fun `SandboxLimits DEFAULT has HTTP and CLIPBOARD APIs`() {
        assertThat(SandboxLimits.DEFAULT.allowedApis).contains(JsApi.HTTP)
        assertThat(SandboxLimits.DEFAULT.allowedApis).contains(JsApi.CLIPBOARD)
    }

    @Test
    fun `JsApi enum has all expected values`() {
        assertThat(JsApi.entries).containsAtLeast(
            JsApi.HTTP,
            JsApi.CLIPBOARD,
            JsApi.FILE_READ,
            JsApi.FILE_WRITE,
            JsApi.NOTIFICATION,
            JsApi.TTS,
        )
    }
}
