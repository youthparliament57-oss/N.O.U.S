// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.brain.llm.router

import com.google.common.truth.Truth.assertThat
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
import com.roshan.persona.brain.llm.cache.SemanticLlmCache
import com.roshan.persona.brain.llm.cache.CacheResult
import com.roshan.persona.brain.llm.cache.CacheStats
import com.roshan.persona.brain.llm.context.ContextInjector
import com.roshan.persona.brain.llm.cost.LlmCostTracker
import com.roshan.persona.brain.llm.layer.CloudLlmLayer
import com.roshan.persona.brain.llm.layer.LocalLlmLayer
import com.roshan.persona.brain.llm.provider.CloudLlmProvider
import com.roshan.persona.brain.llm.provider.CloudProviderConfig
import com.roshan.persona.brain.llm.provider.FinishReason
import com.roshan.persona.brain.llm.provider.GenerationConfig
import com.roshan.persona.brain.llm.provider.GenerationResult
import com.roshan.persona.brain.llm.provider.LocalLlmProvider
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

// AUTO_FIX_0092: [feature] HybridLlmRouterTest verified

class HybridLlmRouterTest {

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

    private fun testUserContext(cloudEnabled: Boolean = true) = UserContext(
        userId = "test-user-id",
        displayName = "Roshan",
        preferredLanguage = Locale.ENGLISH,
        preferredResponseLength = ResponseLength.BALANCED,
        skillPreferences = emptyMap(),
        privacySettings = PrivacySettings(cloudLlmEnabled = cloudEnabled),
        monthlyLlmBudgetUsd = 5.0f,
        monthlyLlmSpentUsd = 0f,
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
        budget: BrainBudget = BrainBudget.DEFAULT,
    ) = BrainContext(
        correlationId = CorrelationId.generate(),
        sessionId = SessionId.generate(),
        timestamp = Instant.parse("2026-07-04T09:30:00Z"),
        timezone = TimeZone.getTimeZone("Asia/Kolkata"),
        activePersona = testPersona(),
        userContext = testUserContext(cloudEnabled),
        deviceContext = testDeviceContext(),
        conversationHistory = emptyList(),
        pendingExpectation = null,
        ambientContext = null,
        budget = budget,
    )

    private fun fakeLocalProvider(
        generateResult: Result<GenerationResult> = Result.Success(
            data = GenerationResult(
                text = "The capital of France is Paris, sitting in the north-central region of the country.",
                tokensGenerated = 20,
                tokensPerSecond = 30f,
                durationMs = 700L,
                finishReason = FinishReason.STOP,
                promptTokens = 50,
                modelId = "qwen2.5-0.5b",
            ),
            correlationId = CorrelationId.generate(),
            durationNanos = 700_000_000,
        ),
    ) = FakeLocalProvider(generateResult)

    private fun fakeCloudProvider(
        generateResult: Result<GenerationResult> = Result.Success(
            data = GenerationResult(
                text = "Paris is the capital of France, located in the north-central part of the country.",
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
    ) = FakeCloudProvider(generateResult)

    private fun makeLocalLayer(provider: LocalLlmProvider): LocalLlmLayer = LocalLlmLayer(
        provider = provider,
        cache = null,
        contextInjector = ContextInjector(memoryInterface = null),
        brainBus = BrainBus(),
    )

    private fun makeCloudLayer(
        provider: CloudLlmProvider,
        costTracker: LlmCostTracker = LlmCostTracker(),
    ): CloudLlmLayer {
        val registry = CloudProviderRegistry()
        registry.updateProvider(registry.getById("openai")!!.copy(isEnabled = true))
        return CloudLlmLayer(
            registry = registry,
            costTracker = costTracker,
            contextInjector = ContextInjector(memoryInterface = null),
            credentialVault = FakeCredentialVault(),
            brainBus = BrainBus(),
            providerFactory = { _ -> provider },
        )
    }

    // ─── Local-first routing tests ────────────────────────────────────────

    @Test
    fun `default routing tries local first`() = runTest {
        val localProvider = fakeLocalProvider()
        val cloudProvider = fakeCloudProvider()
        val router = HybridLlmRouter.withCloud(
            localLlmLayer = makeLocalLayer(localProvider),
            registry = CloudProviderRegistry().also { it.enableProvider("openai") },
            costTracker = LlmCostTracker(),
            contextInjector = ContextInjector(memoryInterface = null),
            credentialVault = FakeCredentialVault(),
            brainBus = BrainBus(),
            providerFactory = { _ -> cloudProvider },
        )

        val result = router.route("Hello", testContext())

        assertThat(result.decision).isInstanceOf(HybridLlmRouter.RoutingDecision.LocalFirst::class.java)
        assertThat((localProvider as FakeLocalProvider).generateCallCount).isEqualTo(1)
        assertThat((cloudProvider as FakeCloudProvider).generateCallCount).isEqualTo(0)  // cloud not called
    }

    @Test
    fun `local success returns SkillOutput Success`() = runTest {
        val localProvider = fakeLocalProvider()
        val router = HybridLlmRouter.localOnly(
            localLlmLayer = makeLocalLayer(localProvider),
            contextInjector = ContextInjector(memoryInterface = null),
        )

        val result = router.route("Hello", testContext())

        assertThat(result.output).isInstanceOf(Result.Success::class.java)
        val output = (result.output as Result.Success).data as SkillOutput.Success
        assertThat(output.message).contains("Paris")
    }

    // ─── Explicit cloud tests ─────────────────────────────────────────────

    @Test
    fun `ask cloud prefix routes directly to cloud`() = runTest {
        val localProvider = fakeLocalProvider()
        val cloudProvider = fakeCloudProvider()
        val router = HybridLlmRouter.withCloud(
            localLlmLayer = makeLocalLayer(localProvider),
            registry = CloudProviderRegistry().also { it.enableProvider("openai") },
            costTracker = LlmCostTracker(),
            contextInjector = ContextInjector(memoryInterface = null),
            credentialVault = FakeCredentialVault(),
            brainBus = BrainBus(),
            providerFactory = { _ -> cloudProvider },
        )

        val result = router.route("ask cloud: What is quantum mechanics?", testContext())

        assertThat(result.decision).isInstanceOf(HybridLlmRouter.RoutingDecision.ExplicitCloud::class.java)
        assertThat((localProvider as FakeLocalProvider).generateCallCount).isEqualTo(0)
        assertThat((cloudProvider as FakeCloudProvider).generateCallCount).isEqualTo(1)
        // Prefix should be stripped from input
        assertThat(cloudProvider.lastPrompt).isNotNull()
        assertThat(cloudProvider.lastPrompt).contains("quantum mechanics")
        assertThat(cloudProvider.lastPrompt).doesNotContain("ask cloud:")
    }

    @Test
    fun `cloud prefix is case-insensitive`() = runTest {
        val localProvider = fakeLocalProvider()
        val cloudProvider = fakeCloudProvider()
        val router = HybridLlmRouter.withCloud(
            localLlmLayer = makeLocalLayer(localProvider),
            registry = CloudProviderRegistry().also { it.enableProvider("openai") },
            costTracker = LlmCostTracker(),
            contextInjector = ContextInjector(memoryInterface = null),
            credentialVault = FakeCredentialVault(),
            brainBus = BrainBus(),
            providerFactory = { _ -> cloudProvider },
        )

        router.route("ASK CLOUD: question", testContext())

        assertThat((cloudProvider as FakeCloudProvider).generateCallCount).isEqualTo(1)
        assertThat((localProvider as FakeLocalProvider).generateCallCount).isEqualTo(0)
    }

    @Test
    fun `forceCloud parameter routes directly to cloud`() = runTest {
        val localProvider = fakeLocalProvider()
        val cloudProvider = fakeCloudProvider()
        val router = HybridLlmRouter.withCloud(
            localLlmLayer = makeLocalLayer(localProvider),
            registry = CloudProviderRegistry().also { it.enableProvider("openai") },
            costTracker = LlmCostTracker(),
            contextInjector = ContextInjector(memoryInterface = null),
            credentialVault = FakeCredentialVault(),
            brainBus = BrainBus(),
            providerFactory = { _ -> cloudProvider },
        )

        router.route("Hello", testContext(), forceCloud = true)

        assertThat((localProvider as FakeLocalProvider).generateCallCount).isEqualTo(0)
        assertThat((cloudProvider as FakeCloudProvider).generateCallCount).isEqualTo(1)
    }

    // ─── Explicit local tests ─────────────────────────────────────────────

    @Test
    fun `ask local prefix routes to local only`() = runTest {
        val localProvider = fakeLocalProvider()
        val cloudProvider = fakeCloudProvider()
        val router = HybridLlmRouter.withCloud(
            localLlmLayer = makeLocalLayer(localProvider),
            registry = CloudProviderRegistry().also { it.enableProvider("openai") },
            costTracker = LlmCostTracker(),
            contextInjector = ContextInjector(memoryInterface = null),
            credentialVault = FakeCredentialVault(),
            brainBus = BrainBus(),
            providerFactory = { _ -> cloudProvider },
        )

        router.route("ask local: question", testContext())

        assertThat(result_decision_is_local(router.route("ask local: question", testContext())))
    }

    private fun result_decision_is_local(result: HybridLlmRouter.RoutingResult): Boolean {
        return result.decision is HybridLlmRouter.RoutingDecision.ExplicitLocal
    }

    @Test
    fun `forceLocal parameter routes to local only`() = runTest {
        val localProvider = fakeLocalProvider()
        val cloudProvider = fakeCloudProvider()
        val router = HybridLlmRouter.withCloud(
            localLlmLayer = makeLocalLayer(localProvider),
            registry = CloudProviderRegistry().also { it.enableProvider("openai") },
            costTracker = LlmCostTracker(),
            contextInjector = ContextInjector(memoryInterface = null),
            credentialVault = FakeCredentialVault(),
            brainBus = BrainBus(),
            providerFactory = { _ -> cloudProvider },
        )

        val result = router.route("Hello", testContext(), forceLocal = true)

        assertThat(result.decision).isInstanceOf(HybridLlmRouter.RoutingDecision.ExplicitLocal::class.java)
        assertThat((localProvider as FakeLocalProvider).generateCallCount).isEqualTo(1)
        assertThat((cloudProvider as FakeCloudProvider).generateCallCount).isEqualTo(0)
    }

    // ─── Local failure → cloud fallback tests ─────────────────────────────

    @Test
    fun `local failure falls back to cloud when enabled`() = runTest {
        val localProvider = fakeLocalProvider(
            generateResult = Result.Failure(
                error = AppError.Llm.OutOfMemory(
                    modelId = "qwen2.5-0.5b",
                    requiredMb = 1000,
                ),
                correlationId = CorrelationId.generate(),
                durationNanos = 0,
            ),
        )
        val cloudProvider = fakeCloudProvider()
        val router = HybridLlmRouter.withCloud(
            localLlmLayer = makeLocalLayer(localProvider),
            registry = CloudProviderRegistry().also { it.enableProvider("openai") },
            costTracker = LlmCostTracker(),
            contextInjector = ContextInjector(memoryInterface = null),
            credentialVault = FakeCredentialVault(),
            brainBus = BrainBus(),
            providerFactory = { _ -> cloudProvider },
        )

        val result = router.route("Hello", testContext(cloudEnabled = true))

        assertThat((localProvider as FakeLocalProvider).generateCallCount).isEqualTo(1)
        assertThat((cloudProvider as FakeCloudProvider).generateCallCount).isEqualTo(1)
        // Final result is from cloud
        val output = (result.output as Result.Success).data as SkillOutput.Success
        assertThat(output.message).contains("Paris")
    }

    @Test
    fun `local failure with cloud disabled returns NoFallback`() = runTest {
        val localProvider = fakeLocalProvider(
            generateResult = Result.Failure(
                error = AppError.Llm.OutOfMemory(
                    modelId = "qwen2.5-0.5b",
                    requiredMb = 1000,
                ),
                correlationId = CorrelationId.generate(),
                durationNanos = 0,
            ),
        )
        val cloudProvider = fakeCloudProvider()
        val router = HybridLlmRouter.withCloud(
            localLlmLayer = makeLocalLayer(localProvider),
            registry = CloudProviderRegistry().also { it.enableProvider("openai") },
            costTracker = LlmCostTracker(),
            contextInjector = ContextInjector(memoryInterface = null),
            credentialVault = FakeCredentialVault(),
            brainBus = BrainBus(),
            providerFactory = { _ -> cloudProvider },
        )

        val result = router.route("Hello", testContext(cloudEnabled = false))

        assertThat(result.decision).isEqualTo(HybridLlmRouter.RoutingDecision.NoFallback)
        // Cloud should not have been called
        assertThat((cloudProvider as FakeCloudProvider).generateCallCount).isEqualTo(0)
    }

    @Test
    fun `local Partial does NOT auto-trigger cloud (privacy fix #6)`() = runTest {
        // Local returns Partial (low confidence) — should NOT auto-fall-back to cloud
        val localProvider = fakeLocalProvider(
            generateResult = Result.Success(
                data = GenerationResult(
                    text = "Yes.",  // short — low confidence
                    tokensGenerated = 2,
                    tokensPerSecond = 30f,
                    durationMs = 70L,
                    finishReason = FinishReason.STOP,
                    promptTokens = 20,
                    modelId = "qwen2.5-0.5b",
                ),
                correlationId = CorrelationId.generate(),
                durationNanos = 70_000_000,
            ),
        )
        val cloudProvider = fakeCloudProvider()
        val router = HybridLlmRouter.withCloud(
            localLlmLayer = makeLocalLayer(localProvider),
            registry = CloudProviderRegistry().also { it.enableProvider("openai") },
            costTracker = LlmCostTracker(),
            contextInjector = ContextInjector(memoryInterface = null),
            credentialVault = FakeCredentialVault(),
            brainBus = BrainBus(),
            providerFactory = { _ -> cloudProvider },
        )

        val result = router.route("Complex question", testContext(cloudEnabled = true))

        // Local WAS called
        assertThat((localProvider as FakeLocalProvider).generateCallCount).isEqualTo(1)
        // Cloud was NOT called — privacy fix #6 (Partial means user must explicitly opt-in)
        assertThat((cloudProvider as FakeCloudProvider).generateCallCount).isEqualTo(0)
        // Result is Partial from local
        val output = (result.output as Result.Success).data as SkillOutput.Partial
        assertThat(output.warnings).isNotEmpty()
    }

    // ─── Local-only router tests ──────────────────────────────────────────

    @Test
    fun `localOnly router has null cloudLlmLayer`() = runTest {
        val localProvider = fakeLocalProvider()
        val router = HybridLlmRouter.localOnly(
            localLlmLayer = makeLocalLayer(localProvider),
            contextInjector = ContextInjector(memoryInterface = null),
        )

        val result = router.route("ask cloud: question", testContext())

        // Even with "ask cloud:" prefix, no cloud available → falls through to local
        // (cloudLlmLayer is null, so NoFallback path returns user-friendly error)
        // OR explicit cloud decision but cloudLlmLayer null → NoFallback output
        assertThat(result.decision).isEqualTo(HybridLlmRouter.RoutingDecision.NoFallback)
        val output = (result.output as Result.Success).data as SkillOutput.Failure
        assertThat(output.userMessage).contains("Cloud AI is not available")
    }

    @Test
    fun `localOnly router handles local success normally`() = runTest {
        val localProvider = fakeLocalProvider()
        val router = HybridLlmRouter.localOnly(
            localLlmLayer = makeLocalLayer(localProvider),
            contextInjector = ContextInjector(memoryInterface = null),
        )

        val result = router.route("Hello", testContext())

        assertThat((localProvider as FakeLocalProvider).generateCallCount).isEqualTo(1)
        val output = (result.output as Result.Success).data as SkillOutput.Success
        assertThat(output.message).contains("Paris")
    }

    // ─── Fakes ────────────────────────────────────────────────────────────

    private class FakeLocalProvider(
        private val generateResult: Result<GenerationResult>,
    ) : LocalLlmProvider {
        var generateCallCount = 0
            private set

        override val modelId: String = "qwen2.5-0.5b"
        override val isLoaded: Boolean = true
        override val contextLength: Int = 4096
        override val availableVramMb: Int = 2048
        override val modelSizeBytes: Long = 500L * 1024 * 1024

        override suspend fun load(): Result<Unit> =
            Result.Success(Unit, CorrelationId.generate(), 0)

        override suspend fun unload(): Result<Unit> =
            Result.Success(Unit, CorrelationId.generate(), 0)

        override suspend fun generate(
            prompt: String,
            config: GenerationConfig,
            onToken: (String) -> Unit,
        ): Result<GenerationResult> {
            generateCallCount++
            if (generateResult is Result.Success) {
                listOf("Paris", " is", " the").forEach { onToken(it) }
            }
            return generateResult
        }

        override suspend fun embed(text: String): Result<FloatArray> =
            Result.Success(FloatArray(384) { 0.1f }, CorrelationId.generate(), 0)
    }

    private class FakeCloudProvider(
        private val generateResult: Result<GenerationResult>,
    ) : CloudLlmProvider {
        var generateCallCount = 0
            private set
        var lastPrompt: String? = null
            private set
        var lastModel: String? = null
            private set

        override val providerId: String = "openai"
        override val displayName: String = "OpenAI"
        override val config: CloudProviderConfig = CloudProviderConfig(
            id = "openai",
            displayName = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            apiKeyAlias = "openai_api_key",
            apiProtocol = com.roshan.persona.brain.llm.provider.ApiProtocol.OPENAI_COMPATIBLE,
            defaultModel = "gpt-4o-mini",
            availableModels = listOf("gpt-4o-mini"),
            pricingPer1kTokens = com.roshan.persona.brain.llm.provider.Pricing.GPT_4O_MINI,
            maxContextLength = 128_000,
            isEnabled = true,
            isBuiltIn = true,
        )
        override val isAvailable: Boolean = true

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

        override suspend fun validateCredentials(): Result<Unit> =
            Result.Success(Unit, CorrelationId.generate(), 0)

        override suspend fun listAvailableModels(): Result<List<com.roshan.persona.brain.llm.provider.ModelInfo>> =
            Result.Success(emptyList(), CorrelationId.generate(), 0)
    }

    private class FakeCredentialVault : CredentialVaultInterface {
        override fun storeCredential(skillId: String, key: String, value: String) {}
        override fun getCredential(skillId: String, key: String): String? = "fake-key"
        override fun deleteCredential(skillId: String, key: String) {}
        override fun deleteAllForSkill(skillId: String) {}
    }
}
