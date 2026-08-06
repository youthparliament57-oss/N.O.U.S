// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.brain.llm.layer

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.bus.BrainEvent
import com.roshan.persona.brain.bus.BrainBus
import com.roshan.persona.brain.context.BrainBudget
import com.roshan.persona.brain.context.BrainContext
import com.roshan.persona.brain.context.DeviceContext
import com.roshan.persona.brain.context.NetworkType
import com.roshan.persona.brain.context.PrivacySettings
import com.roshan.persona.brain.context.ResponseLength
import com.roshan.persona.brain.context.SessionId
import com.roshan.persona.brain.context.ThermalStatus
import com.roshan.persona.brain.context.UserContext
import com.roshan.persona.brain.llm.context.ContextInjector
import com.roshan.persona.brain.llm.cost.LlmCostTracker
import com.roshan.persona.brain.llm.provider.ApiProtocol
import com.roshan.persona.brain.llm.provider.CloudLlmProvider
import com.roshan.persona.brain.llm.provider.CloudProviderConfig
import com.roshan.persona.brain.llm.provider.FinishReason
import com.roshan.persona.brain.llm.provider.GenerationConfig
import com.roshan.persona.brain.llm.provider.GenerationResult
import com.roshan.persona.brain.llm.provider.ModelInfo
import com.roshan.persona.brain.llm.provider.Pricing
import com.roshan.persona.brain.llm.registry.CloudProviderRegistry
import com.roshan.persona.brain.persona.Persona
import com.roshan.persona.brain.persona.ResponseStyle
import com.roshan.persona.brain.persona.Tone
import com.roshan.persona.brain.persona.Verbosity
import com.roshan.persona.brain.persona.Vocabulary
import com.roshan.persona.brain.skill.CredentialVaultInterface
import com.roshan.persona.brain.skill.SkillOutput
import com.roshan.persona.common.AppError
import com.roshan.persona.common.CorrelationId
import com.roshan.persona.common.Result
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

// AUTO_FIX_0087: [feature] CloudLlmLayerTest verified

class CloudLlmLayerTest {

    // ─── Fixtures ─────────────────────────────────────────────────────────

    private fun testPersona() = Persona(
        id = "jarvis",
        displayName = "JARVIS",
        systemPromptTemplate = "You are {{persona_name}}.",
        skillPreferences = emptyMap(),
        responseStyle = ResponseStyle(
            tone = Tone.WITTY,
            verbosity = Verbosity.BALANCED,
            useEmoji = false,
            vocabulary = Vocabulary.TECHNICAL,
        ),
    )

    private fun testUserContext(
        cloudEnabled: Boolean = true,
        budget: Float = 5.0f,
        spent: Float = 0f,
    ) = UserContext(
        userId = "test-user-id",
        displayName = "Roshan",
        preferredLanguage = Locale.ENGLISH,
        preferredResponseLength = ResponseLength.BALANCED,
        skillPreferences = emptyMap(),
        privacySettings = PrivacySettings(cloudLlmEnabled = cloudEnabled),
        monthlyLlmBudgetUsd = budget,
        monthlyLlmSpentUsd = spent,
    )

    private fun testDeviceContext() = DeviceContext(
        thermalStatus = ThermalStatus.NONE,
        availableRamMb = 4096,
        batteryLevel = 80,
        isCharging = true,
        isOnline = true,
        networkType = NetworkType.WIFI,
        gpuAvailable = true,
        gpuVramMb = 2048,
    )

    private fun testContext(
        cloudEnabled: Boolean = true,
        budget: Float = 5.0f,
        spent: Float = 0f,
    ) = BrainContext(
        correlationId = CorrelationId.generate(),
        sessionId = SessionId.generate(),
        timestamp = Instant.parse("2026-07-04T09:30:00Z"),
        timezone = TimeZone.getTimeZone("Asia/Kolkata"),
        activePersona = testPersona(),
        userContext = testUserContext(cloudEnabled, budget, spent),
        deviceContext = testDeviceContext(),
        conversationHistory = emptyList(),
        pendingExpectation = null,
        ambientContext = null,
        budget = BrainBudget.DEFAULT,
    )

    private fun testProviderConfig(
        id: String = "openai",
        enabled: Boolean = true,
        pricing: Pricing = Pricing.GPT_4O_MINI,
    ) = CloudProviderConfig(
        id = id,
        displayName = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        apiKeyAlias = "openai_api_key",
        apiProtocol = ApiProtocol.OPENAI_COMPATIBLE,
        defaultModel = "gpt-4o-mini",
        availableModels = listOf("gpt-4o-mini"),
        pricingPer1kTokens = pricing,
        maxContextLength = 128_000,
        isEnabled = enabled,
        isBuiltIn = true,
    )

    private fun fakeProvider(
        config: CloudProviderConfig = testProviderConfig(),
        generateResult: Result<GenerationResult> = Result.Success(
            data = GenerationResult(
                text = "The capital of France is Paris, which sits in the north-central part of the country.",
                tokensGenerated = 18,
                tokensPerSecond = 50f,
                durationMs = 350L,
                finishReason = FinishReason.STOP,
                promptTokens = 80,
                modelId = "gpt-4o-mini",
            ),
            correlationId = CorrelationId.generate(),
            durationNanos = 350_000_000,
        ),
        validateResult: Result<Unit> = Result.Success(
            data = Unit,
            correlationId = CorrelationId.generate(),
            durationNanos = 0,
        ),
        modelsResult: Result<List<ModelInfo>> = Result.Success(
            data = listOf(ModelInfo(
                id = "gpt-4o-mini",
                displayName = "GPT-4o Mini",
                contextLength = 128_000,
                supportsStreaming = true,
                supportsEmbeddings = false,
                supportsToolCalling = true,
                supportsVision = true,
                pricing = Pricing.GPT_4O_MINI,
            )),
            correlationId = CorrelationId.generate(),
            durationNanos = 0,
        ),
    ) = FakeCloudLlmProvider(config, generateResult, validateResult, modelsResult)

    private fun makeLayer(
        provider: CloudLlmProvider = fakeProvider(),
        cloudEnabled: Boolean = true,
        budget: Float = 5.0f,
        spent: Float = 0f,
        bus: BrainBus = BrainBus(),
    ): CloudLlmLayer {
        val registry = CloudProviderRegistry()
        // Enable the test provider
        registry.updateProvider(registry.getById("openai")!!.copy(isEnabled = true))

        val costTracker = LlmCostTracker()
        // Pre-record existing spend if specified
        if (spent > 0f) {
            costTracker.recordUsage(
                providerId = "openai",
                inputTokens = 1000,
                outputTokens = 500,
                costUsd = spent,
                success = true,
            )
        }

        return CloudLlmLayer(
            registry = registry,
            costTracker = costTracker,
            contextInjector = ContextInjector(memoryInterface = null),
            credentialVault = FakeCredentialVault(),
            brainBus = bus,
            providerFactory = { _ -> provider },
        )
    }

    // ─── Privacy opt-in tests ─────────────────────────────────────────────

    @Test
    fun `cloud disabled by user returns Failure without calling provider`() = runTest {
        val provider = fakeProvider()
        val layer = makeLayer(provider = provider, cloudEnabled = false)
        val context = testContext(cloudEnabled = false)

        val result = layer.process("Hello", context)

        val success = result as Result.Success
        val output = success.data as SkillOutput.Failure
        assertThat(output.userMessage).contains("disabled")
        assertThat((provider as FakeCloudLlmProvider).generateCallCount).isEqualTo(0)
    }

    @Test
    fun `cloud disabled metadata includes privacy_blocked`() = runTest {
        val layer = makeLayer(cloudEnabled = false)
        val context = testContext(cloudEnabled = false)

        val result = layer.process("Hello", context)

        val success = result as Result.Success
        assertThat(success.metadata["privacy_blocked"]).isEqualTo("true")
    }

    // ─── No enabled provider tests ────────────────────────────────────────

    @Test
    fun `no enabled providers returns Failure`() = runTest {
        val provider = fakeProvider()
        val registry = CloudProviderRegistry()  // all disabled by default
        val layer = CloudLlmLayer(
            registry = registry,
            costTracker = LlmCostTracker(),
            contextInjector = ContextInjector(memoryInterface = null),
            credentialVault = FakeCredentialVault(),
            brainBus = BrainBus(),
            providerFactory = { _ -> provider },
        )

        val result = layer.process("Hello", testContext())

        val success = result as Result.Success
        val output = success.data as SkillOutput.Failure
        assertThat(output.userMessage).contains("No cloud provider")
        assertThat((provider as FakeCloudLlmProvider).generateCallCount).isEqualTo(0)
    }

    // ─── Budget enforcement tests ─────────────────────────────────────────

    @Test
    fun `budget exceeded returns Failure without calling provider`() = runTest {
        val provider = fakeProvider()
        // Spent $0.9999 of $1.00 budget — remaining $0.0001, any positive estimated cost exceeds
        val layer = makeLayer(provider = provider, budget = 1.0f, spent = 0.9999f)
        val context = testContext(budget = 1.0f, spent = 0.9999f)

        val result = layer.process("Hello", context)

        val success = result as Result.Success
        val output = success.data as SkillOutput.Failure
        assertThat(output.error).isInstanceOf(AppError.Configuration.BudgetExceeded::class.java)
        assertThat((provider as FakeCloudLlmProvider).generateCallCount).isEqualTo(0)
    }

    @Test
    fun `budget exceeded message includes budget amount`() = runTest {
        val layer = makeLayer(budget = 1.0f, spent = 0.9999f)
        val context = testContext(budget = 1.0f, spent = 0.9999f)

        val result = layer.process("Hello", context)

        val success = result as Result.Success
        val output = success.data as SkillOutput.Failure
        assertThat(output.userMessage).contains("budget")
        assertThat(output.userMessage).contains("$1.00")
    }

    @Test
    fun `within budget proceeds with provider call`() = runTest {
        val provider = fakeProvider()
        val layer = makeLayer(provider = provider, budget = 5.0f, spent = 0f)

        val result = layer.process("Hello", testContext(budget = 5.0f))

        assertThat((provider as FakeCloudLlmProvider).generateCallCount).isEqualTo(1)
        val success = result as Result.Success
        assertThat(success.data).isInstanceOf(SkillOutput.Success::class.java)
    }

    // ─── Cost tracking tests ──────────────────────────────────────────────

    @Test
    fun `successful call records actual cost`() = runTest {
        val provider = fakeProvider()  // returns 80 input + 18 output tokens
        val layer = makeLayer(provider = provider)
        val costTracker = LlmCostTracker()
        // Replace the cost tracker via reflection-free approach: rebuild layer
        val registry = CloudProviderRegistry()
        registry.updateProvider(registry.getById("openai")!!.copy(isEnabled = true))
        val realLayer = CloudLlmLayer(
            registry = registry,
            costTracker = costTracker,
            contextInjector = ContextInjector(memoryInterface = null),
            credentialVault = FakeCredentialVault(),
            brainBus = BrainBus(),
            providerFactory = { _ -> provider },
        )

        realLayer.process("Hello", testContext())

        val usage = costTracker.getUsageForProvider("openai")
        assertThat(usage).isNotNull()
        assertThat(usage!!.inputTokens).isEqualTo(80L)
        assertThat(usage.outputTokens).isEqualTo(18L)
        assertThat(usage.costUsd).isGreaterThan(0f)
        assertThat(usage.successfulCalls).isEqualTo(1L)
    }

    @Test
    fun `free pricing records zero cost`() = runTest {
        val providerConfig = testProviderConfig().copy(pricingPer1kTokens = Pricing.FREE)
        val provider = fakeProvider(config = providerConfig)
        val registry = CloudProviderRegistry()
        registry.updateProvider(registry.getById("openai")!!.copy(
            isEnabled = true,
            pricingPer1kTokens = Pricing.FREE,
        ))
        val costTracker = LlmCostTracker()
        val layer = CloudLlmLayer(
            registry = registry,
            costTracker = costTracker,
            contextInjector = ContextInjector(memoryInterface = null),
            credentialVault = FakeCredentialVault(),
            brainBus = BrainBus(),
            providerFactory = { _ -> provider },
        )

        layer.process("Hello", testContext())

        val usage = costTracker.getUsageForProvider("openai")
        assertThat(usage!!.costUsd).isEqualTo(0f)
    }

    // ─── BrainBus event tests ─────────────────────────────────────────────

    @Test
    fun `successful call emits LlmCallStarted and LlmCallCompleted`() = runTest {
        val provider = fakeProvider()
        val bus = BrainBus()
        val layer = makeLayer(provider = provider, bus = bus)

        bus.events.test {
            layer.process("Hello", testContext())

            val started = awaitItem()
            assertThat(started).isInstanceOf(BrainEvent.LlmCallStarted::class.java)
            assertThat((started as BrainEvent.LlmCallStarted).provider).isEqualTo("openai")

            val completed = awaitItem()
            assertThat(completed).isInstanceOf(BrainEvent.LlmCallCompleted::class.java)
            assertThat((completed as BrainEvent.LlmCallCompleted).provider).isEqualTo("openai")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `provider failure emits LlmCallFailed`() = runTest {
        val error = AppError.Network.HttpStatus(status = 401, body = "invalid api key")
        val provider = fakeProvider(
            generateResult = Result.Failure(
                error = error,
                correlationId = CorrelationId.generate(),
                durationNanos = 0,
            ),
        )
        val bus = BrainBus()
        val layer = makeLayer(provider = provider, bus = bus)

        bus.events.test {
            layer.process("Hello", testContext())

            val started = awaitItem()
            assertThat(started).isInstanceOf(BrainEvent.LlmCallStarted::class.java)

            val failed = awaitItem()
            assertThat(failed).isInstanceOf(BrainEvent.LlmCallFailed::class.java)
            assertThat((failed as BrainEvent.LlmCallFailed).error).isEqualTo(error)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── PII scrubbing tests ──────────────────────────────────────────────

    @Test
    fun `prompt is PII-scrubbed before being sent to provider`() = runTest {
        val provider = fakeProvider()
        val layer = makeLayer(provider = provider)

        // User input contains a phone number
        layer.process("Call me at +91 98765 43210", testContext())

        val capturedPrompt = (provider as FakeCloudLlmProvider).lastPrompt
        assertThat(capturedPrompt).isNotNull()
        assertThat(capturedPrompt).contains("[REDACTED:PHONE]")
        assertThat(capturedPrompt).doesNotContain("98765 43210")
    }

    @Test
    fun `prompt is PII-scrubbed for email addresses`() = runTest {
        val provider = fakeProvider()
        val layer = makeLayer(provider = provider)

        layer.process("Email me at roshan@example.com", testContext())

        val capturedPrompt = (provider as FakeCloudLlmProvider).lastPrompt
        assertThat(capturedPrompt).contains("[REDACTED:EMAIL]")
        assertThat(capturedPrompt).doesNotContain("roshan@example.com")
    }

    @Test
    fun `prompt without PII passes through unchanged`() = runTest {
        val provider = fakeProvider()
        val layer = makeLayer(provider = provider)

        layer.process("What is the capital of France?", testContext())

        val capturedPrompt = (provider as FakeCloudLlmProvider).lastPrompt
        // Should still contain the user input (no PII to scrub)
        assertThat(capturedPrompt).contains("capital of France")
    }

    // ─── Generation success tests ─────────────────────────────────────────

    @Test
    fun `successful generation returns Success with metadata`() = runTest {
        val provider = fakeProvider()
        val layer = makeLayer(provider = provider)

        val result = layer.process("Hello", testContext())

        val success = result as Result.Success
        val output = success.data as SkillOutput.Success
        assertThat(output.message).contains("Paris")
        assertThat(output.data["provider"]).isEqualTo("openai")
        assertThat(output.data["model"]).isEqualTo("gpt-4o-mini")
        assertThat(output.data["tokens_generated"]).isEqualTo("18")
        assertThat(output.data["cost_usd"]).isNotNull()
    }

    @Test
    fun `low confidence response returns Partial`() = runTest {
        val provider = fakeProvider(
            generateResult = Result.Success(
                data = GenerationResult(
                    text = "Yes.",  // very short
                    tokensGenerated = 2,
                    tokensPerSecond = 50f,
                    durationMs = 50L,
                    finishReason = FinishReason.STOP,
                    promptTokens = 20,
                    modelId = "gpt-4o-mini",
                ),
                correlationId = CorrelationId.generate(),
                durationNanos = 50_000_000,
            ),
        )
        val layer = makeLayer(provider = provider)

        val result = layer.process("Complex question", testContext())

        val success = result as Result.Success
        assertThat(success.data).isInstanceOf(SkillOutput.Partial::class.java)
        val partial = success.data as SkillOutput.Partial
        assertThat(partial.warnings).isNotEmpty()
        assertThat(partial.data["low_confidence"]).isEqualTo("true")
    }

    @Test
    fun `LENGTH finish reason returns Partial`() = runTest {
        val provider = fakeProvider(
            generateResult = Result.Success(
                data = GenerationResult(
                    text = "The answer is complex and requires multiple paragraphs to fully explain...",
                    tokensGenerated = 50,
                    tokensPerSecond = 50f,
                    durationMs = 1000L,
                    finishReason = FinishReason.LENGTH,
                    promptTokens = 30,
                    modelId = "gpt-4o-mini",
                ),
                correlationId = CorrelationId.generate(),
                durationNanos = 1_000_000_000,
            ),
        )
        val layer = makeLayer(provider = provider)

        val result = layer.process("Explain quantum mechanics", testContext())

        val success = result as Result.Success
        assertThat(success.data).isInstanceOf(SkillOutput.Partial::class.java)
    }

    // ─── Provider failure tests ───────────────────────────────────────────

    @Test
    fun `provider generation failure returns Failure and records failed call`() = runTest {
        val error = AppError.Network.HttpStatus(status = 500, body = "server error")
        val provider = fakeProvider(
            generateResult = Result.Failure(
                error = error,
                correlationId = CorrelationId.generate(),
                durationNanos = 0,
            ),
        )
        val registry = CloudProviderRegistry()
        registry.updateProvider(registry.getById("openai")!!.copy(isEnabled = true))
        val costTracker = LlmCostTracker()
        val layer = CloudLlmLayer(
            registry = registry,
            costTracker = costTracker,
            contextInjector = ContextInjector(memoryInterface = null),
            credentialVault = FakeCredentialVault(),
            brainBus = BrainBus(),
            providerFactory = { _ -> provider },
        )

        val result = layer.process("Hello", testContext())

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isEqualTo(error)
        val usage = costTracker.getUsageForProvider("openai")
        assertThat(usage!!.failedCalls).isEqualTo(1L)
        assertThat(usage.successfulCalls).isEqualTo(0L)
        // Failed call records zero cost
        assertThat(usage.costUsd).isEqualTo(0f)
    }

    // ─── Preferred provider / model tests ─────────────────────────────────

    @Test
    fun `preferred provider is used when enabled`() = runTest {
        val provider = fakeProvider()
        val registry = CloudProviderRegistry()
        registry.enableProvider("anthropic")
        registry.enableProvider("openai")
        val layer = CloudLlmLayer(
            registry = registry,
            costTracker = LlmCostTracker(),
            contextInjector = ContextInjector(memoryInterface = null),
            credentialVault = FakeCredentialVault(),
            brainBus = BrainBus(),
            providerFactory = { _ -> provider },
        )

        val result = layer.process(
            userInput = "Hello",
            context = testContext(),
            preferredProviderId = "anthropic",
        )

        // Provider was called (the factory returned the same fake regardless of config)
        assertThat((provider as FakeCloudLlmProvider).generateCallCount).isEqualTo(1)
        // Provider received Anthropic's model name
        assertThat(provider.lastModel).isEqualTo("claude-3-5-sonnet-20241022")
    }

    @Test
    fun `preferred model overrides default model`() = runTest {
        val provider = fakeProvider()
        val layer = makeLayer(provider = provider)

        layer.process(
            userInput = "Hello",
            context = testContext(),
            preferredModel = "gpt-4o",
        )

        assertThat((provider as FakeCloudLlmProvider).lastModel).isEqualTo("gpt-4o")
    }

    @Test
    fun `unknown preferred provider falls back to first enabled`() = runTest {
        val provider = fakeProvider()
        val layer = makeLayer(provider = provider)

        layer.process(
            userInput = "Hello",
            context = testContext(),
            preferredProviderId = "nonexistent_provider",
        )

        // Should still call the provider (fell back to default enabled)
        assertThat((provider as FakeCloudLlmProvider).generateCallCount).isEqualTo(1)
    }

    // ─── Streaming tests ──────────────────────────────────────────────────

    @Test
    fun `streaming tokens are passed through onToken callback`() = runTest {
        val provider = fakeProvider()
        val layer = makeLayer(provider = provider)
        val receivedTokens = mutableListOf<String>()

        layer.process(
            userInput = "Hello",
            context = testContext(),
            onToken = { token -> receivedTokens.add(token) },
        )

        // FakeCloudLlmProvider emits 3 tokens: "Paris", " is", " the"
        assertThat(receivedTokens).hasSize(3)
        assertThat(receivedTokens[0]).isEqualTo("Paris")
    }

    // ─── Fakes ────────────────────────────────────────────────────────────

    private class FakeCloudLlmProvider(
        override val config: CloudProviderConfig,
        private val generateResult: Result<GenerationResult>,
        private val validateResult: Result<Unit>,
        private val modelsResult: Result<List<ModelInfo>>,
    ) : CloudLlmProvider {
        var generateCallCount = 0
            private set
        var lastPrompt: String? = null
            private set
        var lastModel: String? = null
            private set

        override val providerId: String = config.id
        override val displayName: String = config.displayName
        override val isAvailable: Boolean = config.isEnabled

        override suspend fun generate(
            prompt: String,
            model: String,
            config: GenerationConfig,
            onToken: (String) -> Unit,
            cancellationToken: com.roshan.persona.brain.stream.CancellationToken?,
        ): Result<GenerationResult> {
            generateCallCount++
            lastPrompt = prompt
            lastModel = model
            if (generateResult is Result.Success) {
                listOf("Paris", " is", " the").forEach { onToken(it) }
            }
            return generateResult
        }

        override suspend fun embed(text: String, model: String?): Result<FloatArray> =
            Result.Success(FloatArray(384) { 0.1f }, CorrelationId.generate(), 0)

        override suspend fun validateCredentials(): Result<Unit> = validateResult

        override suspend fun listAvailableModels(): Result<List<ModelInfo>> = modelsResult
    }

    private class FakeCredentialVault : CredentialVaultInterface {
        private val store = mutableMapOf<String, String>()

        override fun storeCredential(skillId: String, key: String, value: String) {
            store["$skillId:$key"] = value
        }

        override fun getCredential(skillId: String, key: String): String? = store["$skillId:$key"]

        override fun deleteCredential(skillId: String, key: String) {
            store.remove("$skillId:$key")
        }

        override fun deleteAllForSkill(skillId: String) {
            store.keys.filter { it.startsWith("$skillId:") }.forEach { store.remove(it) }
        }
    }
}
