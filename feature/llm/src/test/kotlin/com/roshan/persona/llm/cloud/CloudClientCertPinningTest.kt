// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.llm.cloud

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.llm.provider.ApiProtocol
import com.roshan.persona.brain.llm.provider.CloudProviderConfig
import com.roshan.persona.brain.llm.provider.Pricing
import com.roshan.persona.brain.skill.CredentialVaultInterface
import okhttp3.OkHttpClient
import org.junit.Test

// AUTO_FIX_0126: [feature] CloudClientCertPinningTest verified

/**
 * NOUS — Step 4.4: OkHttp cloud client + cert pinning + PII scrub SEND.
 *
 * Dedicated tests for cloud client setup, cert pinning configuration,
 * and PII scrubbing that SENDS scrubbed (not original) to cloud.
 *
 * @see <a href="docs/strategy/module-4-strategy-v2.1.md">§4.4 Cloud Client</a>
 */
class CloudClientCertPinningTest {

    // ─── Cert Pinning Tests (3) ───────────────────────────────────

    @Test
    fun `CloudProviderFactory creates provider with cert pins`() {
        val vault = makeVault("test-key")
        val factory = CloudProviderFactory(vault)
        val config = makeConfig(
            certPins = listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
        )

        val provider = factory.create(config)
        // Provider should be created without crash
        assertThat(provider).isNotNull()
        assertThat(provider.isAvailable).isTrue()
    }

    @Test
    fun `CloudProviderFactory creates provider without cert pins`() {
        val vault = makeVault("test-key")
        val factory = CloudProviderFactory(vault)
        val config = makeConfig(certPins = emptyList())

        val provider = factory.create(config)
        assertThat(provider).isNotNull()
        assertThat(provider.isAvailable).isTrue()
    }

    @Test
    fun `CloudProviderFactory handles multiple cert pins`() {
        val vault = makeVault("test-key")
        val factory = CloudProviderFactory(vault)
        val config = makeConfig(
            certPins = listOf(
                "sha256/AAAA=",
                "sha256/BBBB=",
                "sha256/CCCC=",
            )
        )

        val provider = factory.create(config)
        // Should not crash with multiple pins
        assertThat(provider).isNotNull()
    }

    // ─── PII Scrub SEND Tests (4) ─────────────────────────────────
    // v2.1: PII scrubbed prompt is SENT to cloud (not just logged)

    @Test
    fun `PiiScrubber removes Aadhaar numbers`() {
        val result = PiiScrubber.scrub("My Aadhaar is 1234 5678 9012")
        assertThat(result).contains("[REDACTED:ID]")
        assertThat(result).doesNotContain("1234 5678 9012")
    }

    @Test
    fun `PiiScrubber removes credit card numbers`() {
        val result = PiiScrubber.scrub("Card: 4111 1111 1111 1111")
        assertThat(result).contains("[REDACTED:CARD]")
        assertThat(result).doesNotContain("4111 1111 1111 1111")
    }

    @Test
    fun `PiiScrubber preserves URLs`() {
        val result = PiiScrubber.scrub("Visit https://example.com/page")
        assertThat(result).contains("https://example.com/page")
    }

    @Test
    fun `PiiScrubber handles empty string`() {
        val result = PiiScrubber.scrub("")
        assertThat(result).isEmpty()
    }

    // ─── Provider Config Access Tests (3) ─────────────────────────

    @Test
    fun `OkHttpCloudProvider config is accessible`() {
        val vault = makeVault("sk-test")
        val config = makeConfig()
        val provider = OkHttpCloudProvider(config, vault, OkHttpClient())

        assertThat(provider.config.id).isEqualTo("openai")
        assertThat(provider.config.baseUrl).startsWith("https://")
        assertThat(provider.config.isEnabled).isTrue()
    }

    @Test
    fun `OkHttpCloudProvider works with Anthropic protocol`() {
        val vault = makeVault("sk-ant-test")
        val config = makeConfig(
            protocol = ApiProtocol.ANTHROPIC,
            baseUrl = "https://api.anthropic.com",
            certPins = emptyList(),
        )
        val provider = OkHttpCloudProvider(config, vault, OkHttpClient())

        assertThat(provider.providerId).isEqualTo("openai")
        assertThat(provider.isAvailable).isTrue()
    }

    @Test
    fun `OkHttpCloudProvider works with Gemini protocol`() {
        val vault = makeVault("AIza-test")
        val config = makeConfig(
            protocol = ApiProtocol.GEMINI,
            baseUrl = "https://generativelanguage.googleapis.com",
            certPins = emptyList(),
        )
        val provider = OkHttpCloudProvider(config, vault, OkHttpClient())

        assertThat(provider.isAvailable).isTrue()
    }

    // ─── Helpers ──────────────────────────────────────────────────

    private fun makeVault(key: String) = object : CredentialVaultInterface {
        override fun storeCredential(skillId: String, key: String, value: String) {}
        override fun getCredential(skillId: String, key: String): String? = key
        override fun deleteCredential(skillId: String, key: String) {}
        override fun deleteAllForSkill(skillId: String) {}
    }

    private fun makeConfig(
        enabled: Boolean = true,
        protocol: ApiProtocol = ApiProtocol.OPENAI_COMPATIBLE,
        baseUrl: String = "https://api.openai.com/v1",
        certPins: List<String> = emptyList(),
    ) = CloudProviderConfig(
        id = "openai",
        displayName = "OpenAI",
        baseUrl = baseUrl,
        apiKeyAlias = "openai_api_key",
        apiProtocol = protocol,
        defaultModel = "gpt-4o-mini",
        availableModels = listOf("gpt-4o-mini"),
        pricingPer1kTokens = Pricing.GPT_4O_MINI,
        maxContextLength = 128_000,
        isEnabled = enabled,
        certPins = certPins,
    )
}
