// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.llm.cloud

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.llm.provider.ApiProtocol
import com.roshan.persona.brain.llm.provider.BuiltInCloudProviders
import com.roshan.persona.brain.llm.provider.CloudProviderConfig
import com.roshan.persona.brain.llm.provider.Pricing
import com.roshan.persona.brain.skill.CredentialVaultInterface
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0131: [feature] AnthropicGeminiFactoryTest verified

/**
 * NOUS — Step 4.6: Anthropic + Gemini + Factory tests.
 *
 * @see <a href="docs/strategy/module-4-strategy-v2.1.md">§4.6 API Protocols</a>
 */
class AnthropicGeminiFactoryTest {

    private fun makeVault() = object : CredentialVaultInterface {
        override fun storeCredential(skillId: String, key: String, value: String) {}
        override fun getCredential(skillId: String, key: String): String? = "test-key"
        override fun deleteCredential(skillId: String, key: String) {}
        override fun deleteAllForSkill(skillId: String) {}
    }

    // ─── Factory: protocol-specific creation (4) ──────────────────

    @Test
    fun `Factory creates provider with Anthropic config`() {
        val factory = CloudProviderFactory(makeVault())
        val provider = factory.create(BuiltInCloudProviders.ANTHROPIC)
        assertThat(provider.providerId).isEqualTo("anthropic")
        assertThat(provider.config.apiProtocol).isEqualTo(ApiProtocol.ANTHROPIC)
    }

    @Test
    fun `Factory creates provider with Gemini config`() {
        val factory = CloudProviderFactory(makeVault())
        val provider = factory.create(BuiltInCloudProviders.GEMINI)
        assertThat(provider.providerId).isEqualTo("gemini")
        assertThat(provider.config.apiProtocol).isEqualTo(ApiProtocol.GEMINI)
    }

    @Test
    fun `Factory creates provider with custom user-added config`() {
        val factory = CloudProviderFactory(makeVault())
        val customConfig = CloudProviderConfig(
            id = "custom_my-vllm",
            displayName = "My vLLM Server",
            baseUrl = "https://vllm.my-server.com/v1",
            apiKeyAlias = "vllm_key",
            apiProtocol = ApiProtocol.OPENAI_COMPATIBLE,
            defaultModel = "llama-3.2-1b",
            availableModels = listOf("llama-3.2-1b"),
            pricingPer1kTokens = Pricing.FREE,
            maxContextLength = 8192,
            isEnabled = true,
            isBuiltIn = false,
        )
        val provider = factory.create(customConfig)
        assertThat(provider.providerId).isEqualTo("custom_my-vllm")
        assertThat(provider.displayName).isEqualTo("My vLLM Server")
    }

    @Test
    fun `Factory creates provider with Groq config`() {
        val factory = CloudProviderFactory(makeVault())
        val provider = factory.create(BuiltInCloudProviders.GROQ)
        assertThat(provider.providerId).isEqualTo("groq")
        assertThat(provider.config.apiProtocol).isEqualTo(ApiProtocol.OPENAI_COMPATIBLE)
    }

    // ─── Provider availability per protocol (2) ───────────────────

    @Test
    fun `Anthropic provider isAvailable with API key`() {
        val factory = CloudProviderFactory(makeVault())
        val provider = factory.create(BuiltInCloudProviders.ANTHROPIC.copy(isEnabled = true))
        assertThat(provider.isAvailable).isTrue()
    }

    @Test
    fun `Gemini provider isAvailable with API key`() {
        val factory = CloudProviderFactory(makeVault())
        val provider = factory.create(BuiltInCloudProviders.GEMINI.copy(isEnabled = true))
        assertThat(provider.isAvailable).isTrue()
    }

    // ─── Built-in provider configs (2) ────────────────────────────

    @Test
    fun `Built-in providers have correct pricing`() {
        assertThat(BuiltInCloudProviders.OPENAI.pricingPer1kTokens).isNotNull()
        assertThat(BuiltInCloudProviders.ANTHROPIC.pricingPer1kTokens).isNotNull()
        assertThat(BuiltInCloudProviders.GEMINI.pricingPer1kTokens).isNotNull()
        assertThat(BuiltInCloudProviders.GROQ.pricingPer1kTokens).isEqualTo(Pricing.GROQ_FREE)
    }

    @Test
    fun `Built-in providers have correct maxContextLength`() {
        assertThat(BuiltInCloudProviders.GEMINI.maxContextLength).isEqualTo(1_000_000)
        assertThat(BuiltInCloudProviders.OPENAI.maxContextLength).isEqualTo(128_000)
        assertThat(BuiltInCloudProviders.ANTHROPIC.maxContextLength).isEqualTo(200_000)
    }

    // ─── Multiple providers coexist (2) ───────────────────────────

    @Test
    fun `Multiple providers can be created independently`() {
        val factory = CloudProviderFactory(makeVault())
        val openai = factory.create(BuiltInCloudProviders.OPENAI.copy(isEnabled = true))
        val anthropic = factory.create(BuiltInCloudProviders.ANTHROPIC.copy(isEnabled = true))
        val gemini = factory.create(BuiltInCloudProviders.GEMINI.copy(isEnabled = true))

        assertThat(openai.providerId).isNotEqualTo(anthropic.providerId)
        assertThat(anthropic.providerId).isNotEqualTo(gemini.providerId)
        assertThat(openai.providerId).isNotEqualTo(gemini.providerId)
    }

    @Test
    fun `Provider listAvailableModels returns models from config`() = runTest {
        val factory = CloudProviderFactory(makeVault())
        val provider = factory.create(BuiltInCloudProviders.OPENROUTER.copy(isEnabled = true))
        val result = provider.listAvailableModels()
        val models = (result as com.roshan.persona.common.Result.Success).data
        assertThat(models.size).isGreaterThan(0)
        assertThat(models.any { it.id.contains("gpt") }).isTrue()
    }
}
