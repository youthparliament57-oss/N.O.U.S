// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.llamacpp

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.llm.provider.GenerationConfig
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LlamaCppProviderTest {

    // Create provider with CPU-only (no GPU) for testing
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

    @Test
    fun `isLoaded returns false initially`() {
        val provider = makeProvider()
        assertThat(provider.isLoaded).isFalse()
    }

    @Test
    fun `constructor properties are accessible`() {
        val provider = makeProvider()
        assertThat(provider.modelId).isEqualTo("test-model")
        assertThat(provider.contextLength).isEqualTo(4096)
        assertThat(provider.modelSizeBytes).isEqualTo(500_000_000L)
    }

    @Test
    fun `availableVramMb is 0 when CPU-only`() {
        val provider = makeProvider(useGpu = false)
        assertThat(provider.availableVramMb).isEqualTo(0)
    }

    @Test
    fun `load returns Failure when native library not available`() = runTest {
        val provider = makeProvider()
        val result = provider.load()
        // Without pre-built .so, load should fail gracefully
        assertThat(result).isInstanceOf(com.roshan.persona.common.Result.Failure::class.java)
    }

    @Test
    fun `generate returns Failure when not loaded`() = runTest {
        val provider = makeProvider()
        val result = provider.generate(
            prompt = "test prompt",
            config = GenerationConfig.BALANCED,
            onToken = {},
        )
        assertThat(result).isInstanceOf(com.roshan.persona.common.Result.Failure::class.java)
    }

    @Test
    fun `embed returns Failure when not loaded`() = runTest {
        val provider = makeProvider()
        val result = provider.embed("test text")
        assertThat(result).isInstanceOf(com.roshan.persona.common.Result.Failure::class.java)
    }

    @Test
    fun `unload is safe when not loaded`() = runTest {
        val provider = makeProvider()
        // Should not crash
        val result = provider.unload()
        assertThat(result).isInstanceOf(com.roshan.persona.common.Result.Success::class.java)
    }

    @Test
    fun `stopGeneration does not crash when not loaded`() {
        val provider = makeProvider()
        // Should not crash
        provider.stopGeneration()
    }

    @Test
    fun `swapPersona returns false when not loaded`() = runTest {
        val provider = makeProvider()
        val result = provider.swapPersona("/nonexistent/lora.gguf")
        assertThat(result).isFalse()
    }

    @Test
    fun `isContextValid returns true when CPU-only`() {
        val provider = makeProvider(useGpu = false)
        // CPU-only mode doesn't need Vulkan context
        assertThat(provider.isContextValid()).isTrue()
    }

    @Test
    fun `isContextValid returns false when not loaded with GPU`() {
        val provider = makeProvider(useGpu = true)
        // Not loaded → context invalid
        assertThat(provider.isContextValid()).isFalse()
    }

    @Test
    fun `lookaheadConfig has correct defaults`() {
        val config = LookaheadDecodingConfig()
        assertThat(config.enabled).isFalse()
        assertThat(config.lookaheadTokens).isEqualTo(4)
        assertThat(config.jacobiIterations).isEqualTo(2)
    }
}
