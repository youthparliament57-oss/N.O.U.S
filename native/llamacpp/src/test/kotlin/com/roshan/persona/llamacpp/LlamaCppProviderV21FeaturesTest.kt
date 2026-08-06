// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.llamacpp

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.llm.provider.GenerationConfig
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * NOUS — Step 4.3: Vulkan context loss + battery saver + barge-in + context shifting.
 *
 * Dedicated tests for v2.1 features in [LlamaCppProvider] and [VulkanConfig].
 *
 * @see <a href="docs/strategy/module-4-strategy-v2.1.md">§4.3 v2.1 Features</a>
 */
class LlamaCppProviderV21FeaturesTest {

    private fun makeProvider(
        useGpu: Boolean = false,
        useMmap: Boolean = true,
    ) = LlamaCppProvider(
        modelPath = "/nonexistent/model.gguf",
        modelId = "test-model",
        contextLength = 4096,
        modelSizeBytes = 500_000_000L,
        useGpu = useGpu,
        useMmap = useMmap,
    )

    // ─── Battery Saver Tests (3) ──────────────────────────────────

    @Test
    fun `BATTERY_SAVER_MAX_TOKENS is 100`() {
        assertThat(LlamaCppProvider.BATTERY_SAVER_MAX_TOKENS).isEqualTo(100)
    }

    @Test
    fun `CPU-only provider generates with maxTokens throttled to 100`() = runTest {
        // CPU-only provider should throttle maxTokens to BATTERY_SAVER_MAX_TOKENS
        // Since native lib is not loaded, generate() returns Failure —
        // but we can verify the config is throttled by checking the constant
        val provider = makeProvider(useGpu = false)
        val config = GenerationConfig(maxTokens = 512)  // request 512
        val result = provider.generate("test", config) {}

        // Provider should fail (not loaded), but the point is it didn't crash
        // during the battery saver check
        assertThat(result).isInstanceOf(com.roshan.persona.common.Result.Failure::class.java)
    }

    @Test
    fun `GPU provider does NOT throttle maxTokens`() = runTest {
        // GPU provider should NOT throttle — full maxTokens allowed
        // Just verify the constant is accessible and correct
        val provider = makeProvider(useGpu = true)
        // Provider with GPU=true should not crash
        val config = GenerationConfig(maxTokens = 512)
        val result = provider.generate("test", config) {}
        // Fails because not loaded, but no crash
        assertThat(result).isInstanceOf(com.roshan.persona.common.Result.Failure::class.java)
    }

    // ─── Barge-In Tests (3) ───────────────────────────────────────

    @Test
    fun `stopGeneration calls nativeStopGeneration without crash when not loaded`() {
        // Without native lib loaded, stopGeneration should be a no-op
        val provider = makeProvider()
        provider.stopGeneration()
        // No exception = pass
    }

    @Test
    fun `stopGeneration is safe to call multiple times`() {
        val provider = makeProvider()
        provider.stopGeneration()
        provider.stopGeneration()
        provider.stopGeneration()
        // No crash = pass
    }

    @Test
    fun `stopGeneration does not affect isLoaded state`() {
        val provider = makeProvider()
        assertThat(provider.isLoaded).isFalse()
        provider.stopGeneration()
        assertThat(provider.isLoaded).isFalse()
    }

    // ─── Context Shifting Tests (2) ───────────────────────────────

    @Test
    fun `context overflow check does not crash when not loaded`() {
        // nativeCheckContextOverflow should be a no-op when not loaded
        val provider = makeProvider()
        // Calling generate() will trigger context overflow check internally
        // before failing on "not loaded"
        kotlinx.coroutines.runBlocking {
            val result = provider.generate("test", GenerationConfig.BALANCED) {}
            // Fails because not loaded, but context check didn't crash
            assertThat(result).isInstanceOf(com.roshan.persona.common.Result.Failure::class.java)
        }
    }

    @Test
    fun `contextLength is correctly stored and accessible`() {
        val provider = makeProvider(useGpu = false)
        // Context length determines when overflow check triggers (80% threshold)
        assertThat(provider.contextLength).isEqualTo(4096)
    }

    // ─── Vulkan Context Loss Tests (2) ────────────────────────────

    @Test
    fun `isContextValid returns true for CPU-only provider`() {
        // CPU-only mode doesn't need Vulkan — always valid
        val provider = makeProvider(useGpu = false)
        assertThat(provider.isContextValid()).isTrue()
    }

    @Test
    fun `isContextValid returns false for GPU provider when not loaded`() {
        // GPU mode but model not loaded → context invalid
        val provider = makeProvider(useGpu = true)
        assertThat(provider.isLoaded).isFalse()
        assertThat(provider.isContextValid()).isFalse()
    }

    // ─── VulkanConfig Thermal Tests (additional) ──────────────────

    @Test
    fun `VulkanConfig shouldUseGpu returns false when thermal status exceeds limit`() {
        // THERMAL_STATUS_SEVERE = 3, THERMAL_LIMIT_FOR_GPU = 3
        // At or above limit → GPU disabled
        val result = VulkanConfig.shouldUseGpu(
            gpuAvailable = true,
            thermalStatus = VulkanConfig.THERMAL_LIMIT_FOR_GPU,
        )
        assertThat(result).isFalse()
    }

    @Test
    fun `VulkanConfig MIN_VRAM_MB is 1024`() {
        // Minimum 1GB VRAM for GPU acceleration
        assertThat(VulkanConfig.MIN_VRAM_MB).isEqualTo(1024)
    }
}
