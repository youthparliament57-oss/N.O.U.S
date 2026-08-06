// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.brain.llm.registry

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.llm.provider.ApiProtocol
import com.roshan.persona.brain.llm.provider.BuiltInCloudProviders
import com.roshan.persona.brain.llm.provider.CloudProviderConfig
import com.roshan.persona.brain.llm.provider.Pricing
import com.roshan.persona.common.AppError
import com.roshan.persona.common.Result
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0090: [feature] CloudProviderRegistryTest verified

class CloudProviderRegistryTest {

    private fun customConfig(
        id: String = "custom_test",
        displayName: String = "Test Provider",
        baseUrl: String = "https://api.test.com/v1",
        enabled: Boolean = false,
    ) = CloudProviderConfig(
        id = id,
        displayName = displayName,
        baseUrl = baseUrl,
        apiKeyAlias = "test_key",
        apiProtocol = ApiProtocol.OPENAI_COMPATIBLE,
        defaultModel = "test-model",
        availableModels = listOf("test-model"),
        pricingPer1kTokens = Pricing.FREE,
        maxContextLength = 4_096,
        isEnabled = enabled,
        isBuiltIn = false,
    )

    // ─── Initialization tests ─────────────────────────────────────────────

    @Test
    fun `registry initializes with built-in providers`() {
        val registry = CloudProviderRegistry()

        val all = registry.getAll()

        assertThat(all).hasSize(BuiltInCloudProviders.ALL.size)
        assertThat(all.map { it.id }).containsAtLeast(
            "openai", "anthropic", "gemini", "groq", "openrouter",
        )
    }

    @Test
    fun `all built-in providers are disabled by default (ADR 0011)`() {
        val registry = CloudProviderRegistry()

        val enabled = registry.getEnabled()

        assertThat(enabled).isEmpty()
        assertThat(registry.hasAnyEnabled()).isFalse()
        assertThat(registry.enabledCount()).isEqualTo(0)
    }

    @Test
    fun `built-in providers are marked isBuiltIn true`() {
        val registry = CloudProviderRegistry()

        val openai = registry.getById("openai")

        assertThat(openai).isNotNull()
        assertThat(openai!!.isBuiltIn).isTrue()
        assertThat(openai.isEnabled).isFalse()
    }

    // ─── Add user provider tests ──────────────────────────────────────────

    @Test
    fun `addUserProvider adds custom provider`() = runTest {
        val registry = CloudProviderRegistry()
        val config = customConfig()

        val result = registry.addUserProvider(config)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        val added = (result as Result.Success).data
        assertThat(added.id).isEqualTo("custom_test")
        assertThat(added.isBuiltIn).isFalse()
        assertThat(registry.getById("custom_test")).isNotNull()
        assertThat(registry.totalCount()).isEqualTo(BuiltInCloudProviders.ALL.size + 1)
    }

    @Test
    fun `addUserProvider rejects ID without custom_ prefix`() = runTest {
        val registry = CloudProviderRegistry()
        val config = customConfig(id = "my_provider")  // missing prefix

        val result = registry.addUserProvider(config)

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        val error = (result as Result.Failure).error
        assertThat(error).isInstanceOf(AppError.Configuration.InvalidProviderConfig::class.java)
        assertThat(error.code).isEqualTo("CONFIG_PROVIDER_INVALID")
    }

    @Test
    fun `addUserProvider rejects duplicate ID`() = runTest {
        val registry = CloudProviderRegistry()
        val config = customConfig()

        registry.addUserProvider(config)
        val secondResult = registry.addUserProvider(config)

        assertThat(secondResult).isInstanceOf(Result.Failure::class.java)
        val error = (secondResult as Result.Failure).error
        assertThat(error).isInstanceOf(AppError.Configuration.ProviderAlreadyExists::class.java)
    }

    @Test
    fun `addUserProvider sets isBuiltIn to false regardless of input`() = runTest {
        val registry = CloudProviderRegistry()
        val config = customConfig().copy(isBuiltIn = true)  // user tries to fake it

        val result = registry.addUserProvider(config)

        val added = (result as Result.Success).data
        assertThat(added.isBuiltIn).isFalse()  // registry forces false
    }

    @Test
    fun `addUserProvider sets updatedAtEpochMs`() = runTest {
        val registry = CloudProviderRegistry()
        val config = customConfig().copy(updatedAtEpochMs = 0)

        val result = registry.addUserProvider(config)

        val added = (result as Result.Success).data
        assertThat(added.updatedAtEpochMs).isGreaterThan(0L)
    }

    // ─── Update provider tests ────────────────────────────────────────────

    @Test
    fun `updateProvider updates existing provider`() = runTest {
        val registry = CloudProviderRegistry()
        registry.addUserProvider(customConfig())

        val updated = registry.getById("custom_test")!!.copy(
            displayName = "Updated Name",
            isEnabled = true,
        )
        val result = registry.updateProvider(updated)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        val data = (result as Result.Success).data
        assertThat(data.displayName).isEqualTo("Updated Name")
        assertThat(data.isEnabled).isTrue()
    }

    @Test
    fun `updateProvider on built-in preserves isBuiltIn flag`() = runTest {
        val registry = CloudProviderRegistry()

        val openai = registry.getById("openai")!!
        val updated = openai.copy(
            isEnabled = true,
            isBuiltIn = false,  // try to change it
        )
        val result = registry.updateProvider(updated)

        val data = (result as Result.Success).data
        assertThat(data.isBuiltIn).isTrue()  // preserved
        assertThat(data.isEnabled).isTrue()  // updated
    }

    @Test
    fun `updateProvider returns Failure for unknown ID`() = runTest {
        val registry = CloudProviderRegistry()

        val result = registry.updateProvider(customConfig(id = "custom_nonexistent"))

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error)
            .isInstanceOf(AppError.Configuration.ProviderNotFound::class.java)
    }

    // ─── Delete provider tests ────────────────────────────────────────────

    @Test
    fun `deleteUserProvider deletes custom provider`() = runTest {
        val registry = CloudProviderRegistry()
        registry.addUserProvider(customConfig())

        val result = registry.deleteUserProvider("custom_test")

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat(registry.getById("custom_test")).isNull()
    }

    @Test
    fun `deleteUserProvider rejects built-in provider`() = runTest {
        val registry = CloudProviderRegistry()

        val result = registry.deleteUserProvider("openai")

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error)
            .isInstanceOf(AppError.Configuration.ProviderBuiltinCannotDelete::class.java)
        // Provider still exists
        assertThat(registry.getById("openai")).isNotNull()
    }

    @Test
    fun `deleteUserProvider returns Failure for unknown ID`() = runTest {
        val registry = CloudProviderRegistry()

        val result = registry.deleteUserProvider("custom_nonexistent")

        assertThat(result).isInstanceOf(Result.Failure::class.java)
    }

    // ─── Enable / disable tests ───────────────────────────────────────────

    @Test
    fun `enableProvider enables a provider`() = runTest {
        val registry = CloudProviderRegistry()

        val result = registry.enableProvider("openai")

        val data = (result as Result.Success).data
        assertThat(data.isEnabled).isTrue()
        assertThat(registry.hasAnyEnabled()).isTrue()
        assertThat(registry.enabledCount()).isEqualTo(1)
    }

    @Test
    fun `disableProvider disables a provider`() = runTest {
        val registry = CloudProviderRegistry()
        registry.enableProvider("openai")

        val result = registry.disableProvider("openai")

        val data = (result as Result.Success).data
        assertThat(data.isEnabled).isFalse()
        assertThat(registry.hasAnyEnabled()).isFalse()
    }

    @Test
    fun `enableProvider returns Failure for unknown ID`() = runTest {
        val registry = CloudProviderRegistry()

        val result = registry.enableProvider("custom_nonexistent")

        assertThat(result).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `multiple providers can be enabled`() = runTest {
        val registry = CloudProviderRegistry()

        registry.enableProvider("openai")
        registry.enableProvider("anthropic")
        registry.enableProvider("groq")

        assertThat(registry.enabledCount()).isEqualTo(3)
        val enabled = registry.getEnabled()
        assertThat(enabled.map { it.id }).containsExactly("openai", "anthropic", "groq")
    }

    // ─── Display order tests ──────────────────────────────────────────────

    @Test
    fun `getAll returns built-in providers first, then user-added alphabetically`() = runTest {
        val registry = CloudProviderRegistry()
        registry.addUserProvider(customConfig(id = "custom_zeta", displayName = "Zeta"))
        registry.addUserProvider(customConfig(id = "custom_alpha", displayName = "Alpha"))

        val all = registry.getAll()

        // Built-in first (in BuiltInCloudProviders.ALL order)
        assertThat(all[0].id).isEqualTo("openai")
        assertThat(all[1].id).isEqualTo("anthropic")
        // Then user-added sorted alphabetically
        val userAdded = all.drop(BuiltInCloudProviders.ALL.size)
        assertThat(userAdded[0].displayName).isEqualTo("Alpha")
        assertThat(userAdded[1].displayName).isEqualTo("Zeta")
    }

    // ─── Cost computation tests ───────────────────────────────────────────

    @Test
    fun `computeCostUsd returns correct cost for OpenAI pricing`() {
        val config = BuiltInCloudProviders.OPENAI.copy(
            pricingPer1kTokens = Pricing.GPT_4O_MINI,  // 0.00015 input, 0.0006 output
        )

        val cost = config.computeCostUsd(inputTokens = 1000, outputTokens = 500)

        // Expected: (1000/1000 * 0.00015) + (500/1000 * 0.0006) = 0.00015 + 0.0003 = 0.00045
        assertThat(cost).isWithin(0.0001f).of(0.00045f)
    }

    @Test
    fun `computeCostUsd returns zero for free pricing`() {
        val config = customConfig().copy(pricingPer1kTokens = Pricing.FREE)

        val cost = config.computeCostUsd(inputTokens = 5000, outputTokens = 2000)

        assertThat(cost).isEqualTo(0f)
    }

    // ─── Validation tests ─────────────────────────────────────────────────

    @Test
    fun `CloudProviderConfig rejects blank ID`() {
        try {
            customConfig(id = "")
            assert(false) { "Should have thrown" }
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("Provider ID cannot be blank")
        }
    }

    @Test
    fun `CloudProviderConfig rejects non-HTTPS base URL`() {
        try {
            customConfig(baseUrl = "http://api.test.com/v1")
            assert(false) { "Should have thrown" }
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("HTTPS")
        }
    }

    @Test
    fun `CloudProviderConfig rejects negative pricing`() {
        try {
            customConfig().copy(
                pricingPer1kTokens = Pricing(inputPer1k = -0.1f, outputPer1k = 0f),
            )
            assert(false) { "Should have thrown" }
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("Input pricing cannot be negative")
        }
    }

    @Test
    fun `CloudProviderConfig rejects invalid maxContextLength`() {
        try {
            customConfig().copy(maxContextLength = 0)
            assert(false) { "Should have thrown" }
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("maxContextLength")
        }
    }

    @Test
    fun `CloudProviderConfig rejects invalid ID format`() {
        try {
            customConfig(id = "invalid id with spaces")
            assert(false) { "Should have thrown" }
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("alphanumeric")
        }
    }

    // ─── Built-in provider tests ──────────────────────────────────────────

    @Test
    fun `BuiltInCloudProviders byId returns correct provider`() {
        val openai = BuiltInCloudProviders.byId("openai")

        assertThat(openai).isNotNull()
        assertThat(openai!!.displayName).isEqualTo("OpenAI")
    }

    @Test
    fun `BuiltInCloudProviders byId returns null for unknown ID`() {
        val unknown = BuiltInCloudProviders.byId("nonexistent")

        assertThat(unknown).isNull()
    }

    @Test
    fun `BuiltInCloudProviders isBuiltIn returns true for known IDs`() {
        assertThat(BuiltInCloudProviders.isBuiltIn("openai")).isTrue()
        assertThat(BuiltInCloudProviders.isBuiltIn("anthropic")).isTrue()
        assertThat(BuiltInCloudProviders.isBuiltIn("custom_test")).isFalse()
    }

    @Test
    fun `each built-in provider has sensible defaults`() {
        for (provider in BuiltInCloudProviders.ALL) {
            assertThat(provider.id).isNotEmpty()
            assertThat(provider.displayName).isNotEmpty()
            assertThat(provider.baseUrl).startsWith("https://")
            assertThat(provider.defaultModel).isNotEmpty()
            assertThat(provider.availableModels).isNotEmpty()
            assertThat(provider.isEnabled).isFalse()  // privacy default
            assertThat(provider.isBuiltIn).isTrue()
            assertThat(provider.maxContextLength).isGreaterThan(0)
        }
    }

    @Test
    fun `OpenAI built-in uses OpenAI-compatible protocol`() {
        assertThat(BuiltInCloudProviders.OPENAI.apiProtocol).isEqualTo(ApiProtocol.OPENAI_COMPATIBLE)
    }

    @Test
    fun `Anthropic built-in uses Anthropic protocol`() {
        assertThat(BuiltInCloudProviders.ANTHROPIC.apiProtocol).isEqualTo(ApiProtocol.ANTHROPIC)
    }

    @Test
    fun `Gemini built-in uses Gemini protocol`() {
        assertThat(BuiltInCloudProviders.GEMINI.apiProtocol).isEqualTo(ApiProtocol.GEMINI)
    }
}
