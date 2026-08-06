// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.brain.llm.layer

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.bus.BrainEvent
import com.roshan.persona.brain.bus.BrainBus
import com.roshan.persona.brain.context.AmbientSnapshot
import com.roshan.persona.brain.context.BrainBudget
import com.roshan.persona.brain.context.BrainContext
import com.roshan.persona.brain.context.DeviceContext
import com.roshan.persona.brain.context.NetworkType
import com.roshan.persona.brain.context.PrivacySettings
import com.roshan.persona.brain.context.ResponseLength
import com.roshan.persona.brain.context.SessionId
import com.roshan.persona.brain.context.ThermalStatus
import com.roshan.persona.brain.context.TimeOfDay
import com.roshan.persona.brain.context.UserContext
import com.roshan.persona.brain.llm.cache.CacheResult
import com.roshan.persona.brain.llm.cache.SemanticLlmCache
import com.roshan.persona.brain.llm.context.ContextInjector
import com.roshan.persona.brain.llm.provider.FinishReason
import com.roshan.persona.brain.llm.provider.GenerationConfig
import com.roshan.persona.brain.llm.provider.GenerationResult
import com.roshan.persona.brain.llm.provider.LocalLlmProvider
import com.roshan.persona.brain.llm.cache.CacheStats
import com.roshan.persona.brain.persona.Persona
import com.roshan.persona.brain.persona.ResponseStyle
import com.roshan.persona.brain.persona.Tone
import com.roshan.persona.brain.persona.Verbosity
import com.roshan.persona.brain.persona.Vocabulary
import com.roshan.persona.brain.skill.SkillOutput
import com.roshan.persona.common.AppError
import com.roshan.persona.common.CorrelationId
import com.roshan.persona.common.Result
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

// AUTO_FIX_0088: [feature] LocalLlmLayerTest verified

class LocalLlmLayerTest {

    // ─── Test fixtures ────────────────────────────────────────────────────

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

    private fun testUserContext() = UserContext(
        userId = "test-user-id",
        displayName = "Roshan",
        preferredLanguage = Locale.ENGLISH,
        preferredResponseLength = ResponseLength.BALANCED,
        skillPreferences = emptyMap(),
        privacySettings = PrivacySettings(),
        monthlyLlmBudgetUsd = 5.0f,
        monthlyLlmSpentUsd = 0.5f,
    )

    private fun testDeviceContext(
        thermal: ThermalStatus = ThermalStatus.NONE,
        battery: Int = 80,
        charging: Boolean = true,
        ramMb: Int = 4096,
    ) = DeviceContext(
        thermalStatus = thermal,
        availableRamMb = ramMb,
        batteryLevel = battery,
        isCharging = charging,
        isOnline = true,
        networkType = NetworkType.WIFI,
        gpuAvailable = true,
        gpuVramMb = 2048,
    )

    private fun testContext(
        device: DeviceContext = testDeviceContext(),
        budget: BrainBudget = BrainBudget.DEFAULT,
    ) = BrainContext(
        correlationId = CorrelationId.generate(),
        sessionId = SessionId.generate(),
        timestamp = Instant.parse("2026-07-04T09:30:00Z"),
        timezone = TimeZone.getTimeZone("Asia/Kolkata"),
        activePersona = testPersona(),
        userContext = testUserContext(),
        deviceContext = device,
        conversationHistory = emptyList(),
        pendingExpectation = null,
        ambientContext = null,
        budget = budget,
    )

    private fun fakeProvider(
        loaded: Boolean = true,
        sizeBytes: Long = 500L * 1024 * 1024, // 500 MB
        ctxLength: Int = 4096,
        generateResult: Result<GenerationResult> = Result.Success(
            data = GenerationResult(
                text = "The capital of France is Paris, which is located in the north-central part of the country.",
                tokensGenerated = 22,
                tokensPerSecond = 30f,
                durationMs = 750L,
                finishReason = FinishReason.STOP,
                promptTokens = 50,
                modelId = "qwen2.5-0.5b",
            ),
            correlationId = CorrelationId.generate(),
            durationNanos = 750_000_000,
        ),
        embedResult: Result<FloatArray> = Result.Success(
            data = FloatArray(384) { 0.1f },
            correlationId = CorrelationId.generate(),
            durationNanos = 1_000_000,
        ),
        loadResult: Result<Unit> = Result.Success(
            data = Unit,
            correlationId = CorrelationId.generate(),
            durationNanos = 0,
        ),
        generateDelayMs: Long = 0L,
    ) = FakeLocalLlmProvider(
        loaded = loaded,
        sizeBytes = sizeBytes,
        ctxLength = ctxLength,
        generateResult = generateResult,
        embedResult = embedResult,
        loadResult = loadResult,
        generateDelayMs = generateDelayMs,
    )

    private fun makeLayer(
        provider: LocalLlmProvider = fakeProvider(),
        cache: SemanticLlmCache? = null,
        bus: BrainBus = BrainBus(),
    ): LocalLlmLayer = LocalLlmLayer(
        provider = provider,
        cache = cache,
        contextInjector = ContextInjector(memoryInterface = null),
        brainBus = bus,
    )

    // ─── Guardrail tests ──────────────────────────────────────────────────

    @Test
    fun `guardrail SEVERE thermal defers to cloud`() = runTest {
        val provider = fakeProvider()
        val layer = makeLayer(provider = provider)
        val context = testContext(
            device = testDeviceContext(thermal = ThermalStatus.SEVERE),
        )

        val result = layer.process("Hello", context)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        val success = result as Result.Success
        assertThat(success.data).isInstanceOf(SkillOutput.Failure::class.java)
        val failure = success.data as SkillOutput.Failure
        assertThat(failure.userMessage).contains("can't run right now")
        // Provider.generate() should NOT have been called
        assertThat((provider as FakeLocalLlmProvider).generateCallCount).isEqualTo(0)
    }

    @Test
    fun `guardrail low RAM defers`() = runTest {
        // Model needs ~1000MB (500MB * 2); give 500MB RAM
        val provider = fakeProvider(sizeBytes = 500L * 1024 * 1024)
        val layer = makeLayer(provider = provider)
        val context = testContext(
            device = testDeviceContext(ramMb = 500),
        )

        val result = layer.process("Hello", context)

        val success = result as Result.Success
        assertThat(success.data).isInstanceOf(SkillOutput.Failure::class.java)
        assertThat((provider as FakeLocalLlmProvider).generateCallCount).isEqualTo(0)
    }

    @Test
    fun `guardrail critical battery and not charging defers`() = runTest {
        val provider = fakeProvider()
        val layer = makeLayer(provider = provider)
        val context = testContext(
            device = testDeviceContext(battery = 10, charging = false),
        )

        val result = layer.process("Hello", context)

        val success = result as Result.Success
        assertThat(success.data).isInstanceOf(SkillOutput.Failure::class.java)
        assertThat((provider as FakeLocalLlmProvider).generateCallCount).isEqualTo(0)
    }

    @Test
    fun `guardrail moderate thermal with low battery defers`() = runTest {
        val provider = fakeProvider()
        val layer = makeLayer(provider = provider)
        val context = testContext(
            device = testDeviceContext(
                thermal = ThermalStatus.MODERATE,
                battery = 25,
                charging = false,
            ),
        )

        val result = layer.process("Hello", context)

        val success = result as Result.Success
        assertThat(success.data).isInstanceOf(SkillOutput.Failure::class.java)
        assertThat((provider as FakeLocalLlmProvider).generateCallCount).isEqualTo(0)
    }

    @Test
    fun `guardrail moderate thermal with sufficient battery proceeds`() = runTest {
        val provider = fakeProvider()
        val layer = makeLayer(provider = provider)
        val context = testContext(
            device = testDeviceContext(
                thermal = ThermalStatus.MODERATE,
                battery = 50,
                charging = false,
            ),
        )

        val result = layer.process("Hello", context)

        // Should proceed to generation
        assertThat((provider as FakeLocalLlmProvider).generateCallCount).isEqualTo(1)
        val success = result as Result.Success
        assertThat(success.data).isInstanceOf(SkillOutput.Success::class.java)
    }

    // ─── Provider load tests ──────────────────────────────────────────────

    @Test
    fun `provider loaded if not already loaded`() = runTest {
        val provider = fakeProvider(loaded = false)
        val layer = makeLayer(provider = provider)

        val result = layer.process("Hello", testContext())

        assertThat((provider as FakeLocalLlmProvider).loadCallCount).isEqualTo(1)
        assertThat(provider.generateCallCount).isEqualTo(1)
    }

    @Test
    fun `provider load failure returns Failure`() = runTest {
        val loadError = AppError.Llm.ModelLoadFailed(
            modelId = "qwen2.5-0.5b",
            cause = RuntimeException("disk error"),
        )
        val provider = fakeProvider(
            loaded = false,
            loadResult = Result.Failure(
                error = loadError,
                correlationId = CorrelationId.generate(),
                durationNanos = 0,
            ),
        )
        val bus = BrainBus()
        val layer = makeLayer(provider = provider, bus = bus)

        bus.events.test {
            val result = layer.process("Hello", testContext())
            // Should have emitted LlmCallFailed event
            val event = awaitItem()
            assertThat(event).isInstanceOf(BrainEvent.LlmCallFailed::class.java)
            val failed = event as BrainEvent.LlmCallFailed
            assertThat(failed.modelId).isEqualTo("qwen2.5-0.5b")

            assertThat(result).isInstanceOf(Result.Failure::class.java)
            val failure = result as Result.Failure
            assertThat(failure.error).isEqualTo(loadError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── Cache tests ──────────────────────────────────────────────────────

    @Test
    fun `cache hit returns cached response without invoking generate`() = runTest {
        val provider = fakeProvider()
        val cache = FakeSemanticLlmCache(
            lookupResult = CacheResult.Hit(
                response = "Cached answer: Paris.",
                similarity = 0.97f,
                cachedAt = Instant.now(),
            ),
        )
        val layer = makeLayer(provider = provider, cache = cache)

        val result = layer.process("What is France's capital?", testContext())

        // Provider.generate should NOT have been called
        assertThat((provider as FakeLocalLlmProvider).generateCallCount).isEqualTo(0)
        val success = result as Result.Success
        assertThat(success.data).isInstanceOf(SkillOutput.Success::class.java)
        val output = success.data as SkillOutput.Success
        assertThat(output.message).contains("Cached answer: Paris.")
        assertThat(output.data["cache_hit"]).isEqualTo("true")
    }

    @Test
    fun `cache miss invokes generate and stores result`() = runTest {
        val provider = fakeProvider()
        val cache = FakeSemanticLlmCache(lookupResult = CacheResult.Miss)
        val layer = makeLayer(provider = provider, cache = cache)

        val result = layer.process("What is France's capital?", testContext())

        // Provider.generate should have been called
        assertThat((provider as FakeLocalLlmProvider).generateCallCount).isEqualTo(1)
        // Cache.store should have been called
        assertThat(cache.storeCallCount).isEqualTo(1)
        // Store should include the response text
        assertThat(cache.lastStoredResponse).contains("Paris")
    }

    @Test
    fun `cache store failure is non-fatal`() = runTest {
        val provider = fakeProvider()
        val cache = FakeSemanticLlmCache(
            lookupResult = CacheResult.Miss,
            storeShouldThrow = true,
        )
        val layer = makeLayer(provider = provider, cache = cache)

        val result = layer.process("Hello", testContext())

        // Should still succeed despite cache store failure
        val success = result as Result.Success
        assertThat(success.data).isInstanceOf(SkillOutput.Success::class.java)
    }

    @Test
    fun `no cache still works`() = runTest {
        val provider = fakeProvider()
        val layer = makeLayer(provider = provider, cache = null)

        val result = layer.process("Hello", testContext())

        assertThat((provider as FakeLocalLlmProvider).generateCallCount).isEqualTo(1)
        val success = result as Result.Success
        assertThat(success.data).isInstanceOf(SkillOutput.Success::class.java)
    }

    // ─── Generation success tests ─────────────────────────────────────────

    @Test
    fun `successful generation returns Success with metadata`() = runTest {
        val provider = fakeProvider(
            generateResult = Result.Success(
                data = GenerationResult(
                    text = "The capital of France is Paris, which sits in the north-central part of the country.",
                    tokensGenerated = 18,
                    tokensPerSecond = 32.5f,
                    durationMs = 553L,
                    finishReason = FinishReason.STOP,
                    promptTokens = 50,
                    modelId = "qwen2.5-0.5b",
                ),
                correlationId = CorrelationId.generate(),
                durationNanos = 553_000_000,
            ),
        )
        val layer = makeLayer(provider = provider)

        val result = layer.process("What is France's capital?", testContext())

        val success = result as Result.Success
        val output = success.data as SkillOutput.Success
        assertThat(output.message).contains("Paris")
        assertThat(output.data["model_id"]).isEqualTo("qwen2.5-0.5b")
        assertThat(output.data["tokens_generated"]).isEqualTo("18")
        assertThat(output.data["tokens_per_second"]).isEqualTo("32.5")
        assertThat(output.data["finish_reason"]).isEqualTo("STOP")
        assertThat(output.data["cache_hit"]).isEqualTo("false")
    }

    @Test
    fun `successful generation emits LlmCallStarted and LlmCallCompleted`() = runTest {
        val provider = fakeProvider()
        val bus = BrainBus()
        val layer = makeLayer(provider = provider, bus = bus)

        bus.events.test {
            layer.process("Hello", testContext())

            val started = awaitItem()
            assertThat(started).isInstanceOf(BrainEvent.LlmCallStarted::class.java)
            assertThat((started as BrainEvent.LlmCallStarted).provider).isEqualTo("local")

            val completed = awaitItem()
            assertThat(completed).isInstanceOf(BrainEvent.LlmCallCompleted::class.java)
            assertThat((completed as BrainEvent.LlmCallCompleted).costUsd).isEqualTo(0f)

            cancelAndIgnoreRemainingEvents()
        }
    }

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

        // FakeLocalLlmProvider emits 3 tokens: "Paris", " is", " the"
        assertThat(receivedTokens).hasSize(3)
        assertThat(receivedTokens[0]).isEqualTo("Paris")
    }

    // ─── Low confidence (Privacy fix #6) tests ────────────────────────────

    @Test
    fun `low confidence due to short response returns Partial`() = runTest {
        val provider = fakeProvider(
            generateResult = Result.Success(
                data = GenerationResult(
                    text = "Yes.",  // very short
                    tokensGenerated = 2,  // below default threshold of 10
                    tokensPerSecond = 30f,
                    durationMs = 100L,
                    finishReason = FinishReason.STOP,
                    promptTokens = 20,
                    modelId = "qwen2.5-0.5b",
                ),
                correlationId = CorrelationId.generate(),
                durationNanos = 100_000_000,
            ),
        )
        val layer = makeLayer(provider = provider)

        val result = layer.process("Tell me about quantum physics", testContext())

        val success = result as Result.Success
        assertThat(success.data).isInstanceOf(SkillOutput.Partial::class.java)
        val partial = success.data as SkillOutput.Partial
        assertThat(partial.warnings).isNotEmpty()
        assertThat(partial.warnings[0]).contains("not fully confident")
        assertThat(partial.warnings[0]).contains("verify with cloud")
        assertThat(partial.data["low_confidence"]).isEqualTo("true")
    }

    @Test
    fun `low confidence due to LENGTH finish reason returns Partial`() = runTest {
        val provider = fakeProvider(
            generateResult = Result.Success(
                data = GenerationResult(
                    text = "The answer to your question is complex and requires careful consideration of multiple factors including historical context, theoretical underpinnings, and practical implications which when taken together reveal a nuanced picture that cannot be fully captured in this response.",
                    tokensGenerated = 50,
                    tokensPerSecond = 30f,
                    durationMs = 1700L,
                    finishReason = FinishReason.LENGTH,  // hit maxTokens
                    promptTokens = 30,
                    modelId = "qwen2.5-0.5b",
                ),
                correlationId = CorrelationId.generate(),
                durationNanos = 1_700_000_000,
            ),
        )
        val layer = makeLayer(provider = provider)

        val result = layer.process("Explain quantum mechanics", testContext())

        val success = result as Result.Success
        assertThat(success.data).isInstanceOf(SkillOutput.Partial::class.java)
    }

    @Test
    fun `low confidence due to repetition returns Partial`() = runTest {
        val provider = fakeProvider(
            generateResult = Result.Success(
                data = GenerationResult(
                    text = "Yes yes yes yes yes yes yes yes yes yes yes yes yes yes",
                    tokensGenerated = 14,
                    tokensPerSecond = 30f,
                    durationMs = 470L,
                    finishReason = FinishReason.STOP,
                    promptTokens = 10,
                    modelId = "qwen2.5-0.5b",
                ),
                correlationId = CorrelationId.generate(),
                durationNanos = 470_000_000,
            ),
        )
        val layer = makeLayer(provider = provider)

        val result = layer.process("Is the sky blue?", testContext())

        val success = result as Result.Success
        assertThat(success.data).isInstanceOf(SkillOutput.Partial::class.java)
    }

    @Test
    fun `low confidence Partial does NOT auto-call cloud (privacy fix #6)`() = runTest {
        val provider = fakeProvider(
            generateResult = Result.Success(
                data = GenerationResult(
                    text = "Yes.",
                    tokensGenerated = 2,
                    tokensPerSecond = 30f,
                    durationMs = 100L,
                    finishReason = FinishReason.STOP,
                    promptTokens = 20,
                    modelId = "qwen2.5-0.5b",
                ),
                correlationId = CorrelationId.generate(),
                durationNanos = 100_000_000,
            ),
        )
        val layer = makeLayer(provider = provider)

        val result = layer.process("Complex question", testContext())

        // Critical privacy assertion: only ONE generate call (no cloud fallback)
        assertThat((provider as FakeLocalLlmProvider).generateCallCount).isEqualTo(1)
        val success = result as Result.Success
        val partial = success.data as SkillOutput.Partial
        // Warning should explicitly mention opt-in for cloud
        assertThat(partial.warnings[0]).contains("opt-in")
    }

    // ─── Failure tests ────────────────────────────────────────────────────

    @Test
    fun `provider generation failure returns Failure and emits event`() = runTest {
        val error = AppError.Llm.OutOfMemory(
            modelId = "qwen2.5-0.5b",
            requiredMb = 1000,
        )
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
            val result = layer.process("Hello", testContext())

            // Should emit LlmCallStarted then LlmCallFailed
            val started = awaitItem()
            assertThat(started).isInstanceOf(BrainEvent.LlmCallStarted::class.java)

            val failed = awaitItem()
            assertThat(failed).isInstanceOf(BrainEvent.LlmCallFailed::class.java)
            assertThat((failed as BrainEvent.LlmCallFailed).error).isEqualTo(error)

            assertThat(result).isInstanceOf(Result.Failure::class.java)
            assertThat((result as Result.Failure).error).isEqualTo(error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `timeout returns Failure`() = runTest {
        // Use a provider that takes 5 seconds to respond
        val provider = fakeProvider(generateDelayMs = 5_000L)
        val layer = makeLayer(provider = provider)
        val context = testContext(
            budget = BrainBudget(maxLatencyMs = 100L),  // 100ms budget
        )

        val result = layer.process("Hello", context)

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        val failure = result as Result.Failure
        // Should be a timeout-style error
        assertThat(failure.error.code).contains("LLM_")
    }

    // ─── Cache store uses correct embedding model version ─────────────────

    @Test
    fun `cache store uses configured embedding model version`() = runTest {
        val provider = fakeProvider()
        val cache = FakeSemanticLlmCache(lookupResult = CacheResult.Miss)
        val layer = LocalLlmLayer(
            provider = provider,
            cache = cache,
            contextInjector = ContextInjector(memoryInterface = null),
            brainBus = BrainBus(),
            embeddingModelVersion = "custom-version-v2",
        )

        layer.process("Hello", testContext())

        assertThat(cache.lastStoredModelVersion).isEqualTo("custom-version-v2")
    }

    @Test
    fun `cache store is skipped when generation produces no tokens`() = runTest {
        val provider = fakeProvider(
            generateResult = Result.Success(
                data = GenerationResult(
                    text = "",
                    tokensGenerated = 0,  // zero tokens
                    tokensPerSecond = 0f,
                    durationMs = 50L,
                    finishReason = FinishReason.ERROR,
                    promptTokens = 10,
                    modelId = "qwen2.5-0.5b",
                ),
                correlationId = CorrelationId.generate(),
                durationNanos = 50_000_000,
            ),
        )
        val cache = FakeSemanticLlmCache(lookupResult = CacheResult.Miss)
        val layer = makeLayer(provider = provider, cache = cache)

        layer.process("Hello", testContext())

        // Should NOT have stored since no tokens were generated
        assertThat(cache.storeCallCount).isEqualTo(0)
    }

    // ─── Fakes ────────────────────────────────────────────────────────────

    private class FakeLocalLlmProvider(
        private val loaded: Boolean,
        private val sizeBytes: Long,
        private val ctxLength: Int,
        private val generateResult: Result<GenerationResult>,
        private val embedResult: Result<FloatArray>,
        private val loadResult: Result<Unit>,
        private val generateDelayMs: Long = 0L,
    ) : LocalLlmProvider {
        var generateCallCount = 0
            private set
        var loadCallCount = 0
            private set
        var embedCallCount = 0
            private set

        override val modelId: String = "qwen2.5-0.5b"
        override val isLoaded: Boolean get() = loaded
        override val contextLength: Int get() = ctxLength
        override val availableVramMb: Int = 2048
        override val modelSizeBytes: Long get() = sizeBytes

        override suspend fun load(): Result<Unit> {
            loadCallCount++
            return loadResult
        }

        override suspend fun unload(): Result<Unit> = Result.Success(
            data = Unit,
            correlationId = CorrelationId.generate(),
            durationNanos = 0,
        )

        override suspend fun generate(
            prompt: String,
            config: GenerationConfig,
            onToken: (String) -> Unit,
        ): Result<GenerationResult> {
            generateCallCount++
            if (generateDelayMs > 0) {
                kotlinx.coroutines.delay(generateDelayMs)
            }
            // Simulate streaming 3 tokens
            if (generateResult is Result.Success) {
                listOf("Paris", " is", " the").forEach { onToken(it) }
            }
            return generateResult
        }

        override suspend fun embed(text: String): Result<FloatArray> {
            embedCallCount++
            return embedResult
        }
    }

    private class FakeSemanticLlmCache(
        private val lookupResult: CacheResult,
        private val storeShouldThrow: Boolean = false,
    ) : SemanticLlmCache {
        var storeCallCount = 0
            private set
        var lastStoredResponse: String? = null
            private set
        var lastStoredModelVersion: String? = null
            private set

        override suspend fun lookup(prompt: String, embedding: FloatArray): Result<CacheResult> {
            return Result.Success(lookupResult, CorrelationId.generate(), 0)
        }

        override suspend fun store(
            prompt: String,
            response: String,
            embedding: FloatArray,
            modelVersion: String,
        ): Result<Unit> {
            storeCallCount++
            lastStoredResponse = response
            lastStoredModelVersion = modelVersion
            if (storeShouldThrow) throw RuntimeException("disk full")
            return Result.Success(Unit, CorrelationId.generate(), 0)
        }

        override suspend fun purgeForModelVersion(currentModelVersion: String): Result<Int> {
            return Result.Success(0, CorrelationId.generate(), 0)
        }

        override suspend fun clear(): Result<Unit> {
            return Result.Success(Unit, CorrelationId.generate(), 0)
        }

        override suspend fun getStats(): Result<CacheStats> {
            return Result.Success(
                CacheStats(
                    totalEntries = 0,
                    totalSizeBytes = 0,
                    hitCount = 0,
                    missCount = 0,
                    hitRate = 0f,
                    oldestEntryAt = null,
                    newestEntryAt = null,
                    embeddingModelVersion = "test",
                ),
                CorrelationId.generate(),
                0,
            )
        }
    }
}
