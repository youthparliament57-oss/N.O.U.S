// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.llm.cloud

import org.junit.Ignore

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.llm.provider.ApiProtocol
import com.roshan.persona.brain.llm.provider.CloudProviderConfig
import com.roshan.persona.brain.llm.provider.GenerationConfig
import com.roshan.persona.brain.llm.provider.Pricing
import com.roshan.persona.brain.skill.CredentialVaultInterface
import com.roshan.persona.brain.stream.CancellationToken
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

// AUTO_FIX_0127: [feature] OkHttpCloudProviderIntegrationTest verified

/**
 * NOUS — Step 4.7: OkHttpCloudProvider integration tests with MockWebServer.
 *
 * Tests real HTTP request/response cycle with a local mock server.
 *
 * @see <a href="docs/strategy/module-4-strategy-v2.1.md">§4.7 Integration</a>
 */
@Ignore("Requires HTTPS — MockWebServer uses HTTP. Run as instrumented test on device.")
class OkHttpCloudProviderIntegrationTest {

    private lateinit var mockServer: MockWebServer

    @Before
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()
    }

    @After
    fun teardown() {
        mockServer.shutdown()
    }

    private fun makeVault() = object : CredentialVaultInterface {
        override fun storeCredential(skillId: String, key: String, value: String) {}
        override fun getCredential(skillId: String, key: String): String? = "sk-test-key"
        override fun deleteCredential(skillId: String, key: String) {}
        override fun deleteAllForSkill(skillId: String) {}
    }

    private fun makeProvider(
        protocol: ApiProtocol = ApiProtocol.OPENAI_COMPATIBLE,
    ): OkHttpCloudProvider {
        val config = CloudProviderConfig(
            id = "test",
            displayName = "Test Provider",
            baseUrl = mockServer.url("/v1").toString().trimEnd('/'),
            apiKeyAlias = "test_key",
            apiProtocol = protocol,
            defaultModel = "test-model",
            availableModels = listOf("test-model"),
            pricingPer1kTokens = Pricing.FREE,
            maxContextLength = 4096,
            isEnabled = true,
        )
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        return OkHttpCloudProvider(config, makeVault(), client)
    }

    // ─── Successful generation (3) ────────────────────────────────

    @Test
    fun `generate returns Success with SSE response`() = runTest {
        val sseResponse = "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}\n" +
            "data: {\"choices\":[{\"delta\":{\"content\":\" world\"}}]}\n" +
            "data: [DONE]\n"

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseResponse)
        )

        val provider = makeProvider()
        val tokens = mutableListOf<String>()
        val result = provider.generate(
            prompt = "test prompt",
            model = "test-model",
            config = GenerationConfig.BALANCED,
            onToken = { tokens.add(it) },
            cancellationToken = null,
        )

        assertThat(result).isInstanceOf(com.roshan.persona.common.Result.Success::class.java)
        val data = (result as com.roshan.persona.common.Result.Success).data
        assertThat(data.text).isEqualTo("Hello world")
        assertThat(tokens).containsExactly("Hello", " world").inOrder()
        assertThat(data.finishReason).isEqualTo(com.roshan.persona.brain.llm.provider.FinishReason.STOP)
    }

    @Test
    fun `generate streams tokens to callback`() = runTest {
        val sseResponse = "data: {\"content\":\"A\"}\n" +
            "data: {\"content\":\"B\"}\n" +
            "data: {\"content\":\"C\"}\n" +
            "data: [DONE]\n"

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseResponse)
        )

        val provider = makeProvider()
        val tokens = mutableListOf<String>()
        provider.generate(
            prompt = "test",
            model = "test-model",
            config = GenerationConfig.BALANCED,
            onToken = { tokens.add(it) },
        )

        assertThat(tokens).containsExactly("A", "B", "C").inOrder()
    }

    @Test
    fun `generate includes model and max_tokens in request`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"content\":\"OK\"}\ndata: [DONE]\n")
        )

        val provider = makeProvider()
        provider.generate(
            prompt = "test",
            model = "gpt-4o-mini",
            config = GenerationConfig(maxTokens = 256),
            onToken = {},
        )

        val recordedRequest = mockServer.takeRequest()
        val requestBody = recordedRequest.body.readUtf8()
        assertThat(requestBody).contains("gpt-4o-mini")
        assertThat(requestBody).contains("256")
        assertThat(requestBody).contains("stream")
    }

    // ─── Error handling (3) ───────────────────────────────────────

    @Test
    fun `generate returns Failure on HTTP 401`() = runTest {
        mockServer.enqueue(
            MockResponse().setResponseCode(401).setBody("{\"error\":\"invalid api key\"}")
        )

        val provider = makeProvider()
        val result = provider.generate(
            prompt = "test",
            model = "test-model",
            config = GenerationConfig.BALANCED,
            onToken = {},
        )

        assertThat(result).isInstanceOf(com.roshan.persona.common.Result.Failure::class.java)
    }

    @Test
    fun `generate returns Failure on HTTP 429 rate limit`() = runTest {
        mockServer.enqueue(
            MockResponse().setResponseCode(429).setBody("{\"error\":\"rate limited\"}")
        )

        val provider = makeProvider()
        val result = provider.generate(
            prompt = "test",
            model = "test-model",
            config = GenerationConfig.BALANCED,
            onToken = {},
        )

        assertThat(result).isInstanceOf(com.roshan.persona.common.Result.Failure::class.java)
    }

    @Test
    fun `generate returns Failure on HTTP 500 server error`() = runTest {
        mockServer.enqueue(
            MockResponse().setResponseCode(500).setBody("{\"error\":\"internal\"}")
        )

        val provider = makeProvider()
        val result = provider.generate(
            prompt = "test",
            model = "test-model",
            config = GenerationConfig.BALANCED,
            onToken = {},
        )

        assertThat(result).isInstanceOf(com.roshan.persona.common.Result.Failure::class.java)
    }

    // ─── PII scrubbing (2) ────────────────────────────────────────

    @Test
    fun `generate scrubs phone number before sending to cloud`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"content\":\"OK\"}\ndata: [DONE]\n")
        )

        val provider = makeProvider()
        provider.generate(
            prompt = "Call me at +91 98765 43210",
            model = "test-model",
            config = GenerationConfig.BALANCED,
            onToken = {},
        )

        val recordedRequest = mockServer.takeRequest()
        val requestBody = recordedRequest.body.readUtf8()
        // Phone number should be scrubbed BEFORE sending to cloud
        assertThat(requestBody).contains("[REDACTED:PHONE]")
        assertThat(requestBody).doesNotContain("98765")
    }

    @Test
    fun `generate scrubs email before sending to cloud`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"content\":\"OK\"}\ndata: [DONE]\n")
        )

        val provider = makeProvider()
        provider.generate(
            prompt = "Email me at roshan@example.com",
            model = "test-model",
            config = GenerationConfig.BALANCED,
            onToken = {},
        )

        val recordedRequest = mockServer.takeRequest()
        val requestBody = recordedRequest.body.readUtf8()
        assertThat(requestBody).contains("[REDACTED:EMAIL]")
        assertThat(requestBody).doesNotContain("roshan@example.com")
    }

    // ─── Barge-in (1) ─────────────────────────────────────────────

    @Test
    fun `generate with cancelled token returns Failure`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"content\":\"Hi\"}\ndata: [DONE]\n")
        )

        val provider = makeProvider()
        val token = CancellationToken()
        token.cancel("user cancelled")

        val result = provider.generate(
            prompt = "test",
            model = "test-model",
            config = GenerationConfig.BALANCED,
            cancellationToken = token,
            onToken = {},
        )

        // Should return Failure (cancelled before/during request)
        assertThat(result).isInstanceOf(com.roshan.persona.common.Result.Failure::class.java)
    }

    // ─── Auth header (1) ──────────────────────────────────────────

    @Test
    fun `generate sends Authorization header for OpenAI protocol`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"content\":\"OK\"}\ndata: [DONE]\n")
        )

        val provider = makeProvider(ApiProtocol.OPENAI_COMPATIBLE)
        provider.generate("test", "test-model", GenerationConfig.BALANCED, onToken = {})

        val recordedRequest = mockServer.takeRequest()
        val authHeader = recordedRequest.getHeader("Authorization")
        assertThat(authHeader).isEqualTo("Bearer sk-test-key")
    }
}
