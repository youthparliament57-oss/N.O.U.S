// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.llm.cloud

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.llm.provider.ApiProtocol
import com.roshan.persona.brain.llm.provider.CloudProviderConfig
import com.roshan.persona.brain.llm.provider.Pricing
import com.roshan.persona.brain.skill.CredentialVaultInterface
import com.roshan.persona.common.getOrNull
import okhttp3.OkHttpClient
import org.junit.Test

// AUTO_FIX_0130: [feature] CloudProviderTest verified

class PiiScrubberTest {

    @Test
    fun `scrub removes phone numbers`() {
        val result = PiiScrubber.scrub("Call +91 98765 43210")
        assertThat(result).contains("[REDACTED:PHONE]")
        assertThat(result).doesNotContain("98765")
    }

    @Test
    fun `scrub removes emails`() {
        val result = PiiScrubber.scrub("Email roshan@example.com")
        assertThat(result).contains("[REDACTED:EMAIL]")
    }

    @Test
    fun `scrub passes clean text unchanged`() {
        val result = PiiScrubber.scrub("What is the capital of France?")
        assertThat(result).isEqualTo("What is the capital of France?")
    }

    @Test
    fun `scrub handles multiple PII types`() {
        val result = PiiScrubber.scrub("Call +91 98765 43210 or email me@nowhere.com")
        assertThat(result).contains("[REDACTED:PHONE]")
        assertThat(result).contains("[REDACTED:EMAIL]")
    }
}

class StreamParserTest {

    @Test
    fun `parse extracts content from OpenAI SSE format`() {
        val sseData = "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}\n" +
            "data: {\"choices\":[{\"delta\":{\"content\":\" world\"}}]}\n" +
            "data: [DONE]\n"

        val responseBody = createMockBody(sseData)
        val parser = StreamParser(responseBody)
        val tokens = mutableListOf<String>()
        val result = parser.parse({ tokens.add(it) }, { false })

        assertThat(result).isEqualTo("Hello world")
        assertThat(tokens.size).isEqualTo(2)
    }

    @Test
    fun `parse handles DONE marker`() {
        val sseData = "data: {\"content\":\"Hi\"}\ndata: [DONE]\n"
        val responseBody = createMockBody(sseData)
        val parser = StreamParser(responseBody)
        val result = parser.parse({ }, { false })

        assertThat(result).isEqualTo("Hi")
    }

    @Test
    fun `parse returns empty for no data lines`() {
        val sseData = "event: ping\n\n"
        val responseBody = createMockBody(sseData)
        val parser = StreamParser(responseBody)
        val result = parser.parse({ }, { false })

        assertThat(result).isEmpty()
    }

    @Test
    fun `parse supports shouldStop callback`() {
        val sseData = "data: {\"content\":\"A\"}\ndata: {\"content\":\"B\"}\ndata: {\"content\":\"C\"}\n"
        val responseBody = createMockBody(sseData)
        val parser = StreamParser(responseBody)
        var count = 0
        val result = parser.parse(
            { count++ },
            { count >= 1 }  // stop after first token
        )

        assertThat(result).isEqualTo("A")
    }

    private fun createMockBody(data: String): okhttp3.ResponseBody {
        return okhttp3.ResponseBody.create(
            null,
            data.toByteArray()
        )
    }
}

class CloudProviderFactoryTest {

    @Test
    fun `create returns OkHttpCloudProvider`() {
        val vault = object : CredentialVaultInterface {
            override fun storeCredential(skillId: String, key: String, value: String) {}
            override fun getCredential(skillId: String, key: String): String? = "fake-key"
            override fun deleteCredential(skillId: String, key: String) {}
            override fun deleteAllForSkill(skillId: String) {}
        }
        val factory = CloudProviderFactory(vault)
        val config = CloudProviderConfig(
            id = "test",
            displayName = "Test",
            baseUrl = "https://api.test.com/v1",
            apiKeyAlias = "test_key",
            apiProtocol = ApiProtocol.OPENAI_COMPATIBLE,
            defaultModel = "test-model",
            availableModels = listOf("test-model"),
            pricingPer1kTokens = Pricing.FREE,
            maxContextLength = 4096,
            isEnabled = true,
        )

        val provider = factory.create(config)
        assertThat(provider).isInstanceOf(OkHttpCloudProvider::class.java)
        assertThat(provider.providerId).isEqualTo("test")
    }
}

class OkHttpCloudProviderTest {

    private fun makeConfig(
        enabled: Boolean = true,
        protocol: ApiProtocol = ApiProtocol.OPENAI_COMPATIBLE,
    ) = CloudProviderConfig(
        id = "openai",
        displayName = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        apiKeyAlias = "openai_api_key",
        apiProtocol = protocol,
        defaultModel = "gpt-4o-mini",
        availableModels = listOf("gpt-4o-mini"),
        pricingPer1kTokens = Pricing.GPT_4O_MINI,
        maxContextLength = 128_000,
        isEnabled = enabled,
    )

    private val vault = object : CredentialVaultInterface {
        override fun storeCredential(skillId: String, key: String, value: String) {}
        override fun getCredential(skillId: String, key: String): String? = "sk-test-key"
        override fun deleteCredential(skillId: String, key: String) {}
        override fun deleteAllForSkill(skillId: String) {}
    }

    @Test
    fun `isAvailable returns false when disabled`() {
        val provider = OkHttpCloudProvider(makeConfig(enabled = false), vault, OkHttpClient())
        assertThat(provider.isAvailable).isFalse()
    }

    @Test
    fun `isAvailable returns true when enabled with key`() {
        val provider = OkHttpCloudProvider(makeConfig(enabled = true), vault, OkHttpClient())
        assertThat(provider.isAvailable).isTrue()
    }

    @Test
    fun `isAvailable returns false when no API key`() {
        val emptyVault = object : CredentialVaultInterface {
            override fun storeCredential(skillId: String, key: String, value: String) {}
            override fun getCredential(skillId: String, key: String): String? = null
            override fun deleteCredential(skillId: String, key: String) {}
            override fun deleteAllForSkill(skillId: String) {}
        }
        val provider = OkHttpCloudProvider(makeConfig(enabled = true), emptyVault, OkHttpClient())
        assertThat(provider.isAvailable).isFalse()
    }

    @Test
    fun `listAvailableModels returns models from config`() {
        val provider = OkHttpCloudProvider(makeConfig(), vault, OkHttpClient())
        val result = kotlinx.coroutines.runBlocking { provider.listAvailableModels() }
        val models = result.getOrNull
        assertThat(models).isNotNull()
        assertThat(models!!.size).isEqualTo(1)
        assertThat(models[0].id).isEqualTo("gpt-4o-mini")
    }
}
