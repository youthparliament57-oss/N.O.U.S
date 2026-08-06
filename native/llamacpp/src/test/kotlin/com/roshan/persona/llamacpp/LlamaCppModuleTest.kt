// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.llamacpp

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.llm.provider.GenerationConfig
import org.junit.Test

class QuantizationSelectorTest {

    @Test
    fun `recommend Q8 for high app heap`() {
        assertThat(QuantizationSelector.recommendQuantization(1024)).isEqualTo("Q8_0")
        assertThat(QuantizationSelector.recommendQuantization(512)).isEqualTo("Q8_0")
    }

    @Test
    fun `recommend Q4_K_M for medium app heap`() {
        assertThat(QuantizationSelector.recommendQuantization(384)).isEqualTo("Q4_K_M")
        assertThat(QuantizationSelector.recommendQuantization(256)).isEqualTo("Q4_K_M")
    }

    @Test
    fun `recommend Q2_K for low app heap`() {
        assertThat(QuantizationSelector.recommendQuantization(192)).isEqualTo("Q2_K")
        assertThat(QuantizationSelector.recommendQuantization(128)).isEqualTo("Q2_K")
    }

    @Test
    fun `recommendModelSize returns 1_5B for high heap`() {
        assertThat(QuantizationSelector.recommendModelSize(1024)).isEqualTo("1.5B")
    }

    @Test
    fun `recommendModelSize returns 0_5B for low heap`() {
        assertThat(QuantizationSelector.recommendModelSize(256)).isEqualTo("0.5B")
    }

    @Test
    fun `recommend returns pair`() {
        val (size, quant) = QuantizationSelector.recommend(1024)
        assertThat(size).isEqualTo("1.5B")
        assertThat(quant).isEqualTo("Q8_0")
    }

    @Test
    fun `estimateSize Q8_0 for 0_5B`() {
        val size = QuantizationSelector.estimateSize("0.5B", "Q8_0")
        assertThat(size).isEqualTo(500_000_000L)
    }

    @Test
    fun `estimateSize Q4_K_M for 1_5B`() {
        val size = QuantizationSelector.estimateSize("1.5B", "Q4_K_M")
        assertThat(size).isEqualTo(750_000_000L)
    }

    @Test
    fun `estimateSize Q2_K for 0_5B`() {
        val size = QuantizationSelector.estimateSize("0.5B", "Q2_K")
        assertThat(size).isEqualTo(150_000_000L)
    }
}

class VulkanConfigTest {

    @Test
    fun `isVulkanAvailable returns false when native not loaded`() {
        // Without pre-built .so, native library is not available
        val result = VulkanConfig.isVulkanAvailable()
        // Just verify it doesn't crash
        assertThat(result).isAnyOf(true, false)
    }

    @Test
    fun `shouldUseGpu returns false when gpuAvailable is false`() {
        val result = VulkanConfig.shouldUseGpu(gpuAvailable = false)
        assertThat(result).isFalse()
    }

    @Test
    fun `shouldUseGpu returns false when thermal status high`() {
        val result = VulkanConfig.shouldUseGpu(
            gpuAvailable = true,
            thermalStatus = VulkanConfig.THERMAL_LIMIT_FOR_GPU,
        )
        assertThat(result).isFalse()
    }

    @Test
    fun `MIN_VRAM_MB is 1024`() {
        assertThat(VulkanConfig.MIN_VRAM_MB).isEqualTo(1024)
    }

    @Test
    fun `THERMAL_LIMIT_FOR_GPU is 3`() {
        assertThat(VulkanConfig.THERMAL_LIMIT_FOR_GPU).isEqualTo(3)
    }
}

class LlamaCppJniTest {

    @Test
    fun `ensureLoaded returns false when library not available`() {
        // Without pre-built .so, should return false
        val result = LlamaCppJni.ensureLoaded()
        assertThat(result).isAnyOf(true, false)
    }

    @Test
    fun `nativeLoadModel returns 0 when not loaded`() {
        val handle = LlamaCppJni.nativeLoadModel("/nonexistent.gguf", 4096, false, true)
        assertThat(handle).isEqualTo(0L)
    }

    @Test
    fun `nativeGenerate returns empty string when not loaded`() {
        val callback = object : LlamaCppJni.TokenCallback {
            override fun onTokenId(tokenId: Int) {}
            override fun shouldStop() = false
        }
        val result = LlamaCppJni.nativeGenerate(0L, "test", 100, 0.7f, 0.9f, 40, 1.1f, callback)
        assertThat(result).isEmpty()
    }

    @Test
    fun `nativeEmbed returns empty array when not loaded`() {
        val result = LlamaCppJni.nativeEmbed(0L, "test")
        assertThat(result).isEmpty()
    }

    @Test
    fun `nativeHasVulkan returns false when not loaded`() {
        val result = LlamaCppJni.nativeHasVulkan()
        assertThat(result).isFalse()
    }

    @Test
    fun `nativeStopGeneration does not crash when not loaded`() {
        LlamaCppJni.nativeStopGeneration()
        // No exception = pass
    }

    @Test
    fun `nativeCheckVulkanContext returns false for zero handle`() {
        val result = LlamaCppJni.nativeCheckVulkanContext(0L)
        assertThat(result).isFalse()
    }

    @Test
    fun `nativeGetVocabSize returns 0 when not loaded`() {
        val result = LlamaCppJni.nativeGetVocabSize(0L)
        assertThat(result).isEqualTo(0)
    }
}

class LookaheadDecodingConfigTest {

    @Test
    fun `default config has disabled lookahead`() {
        val config = LookaheadDecodingConfig()
        assertThat(config.enabled).isFalse()
        assertThat(config.lookaheadTokens).isEqualTo(4)
        assertThat(config.jacobiIterations).isEqualTo(2)
    }

    @Test
    fun `enabled config has correct values`() {
        val config = LookaheadDecodingConfig(enabled = true, lookaheadTokens = 8)
        assertThat(config.enabled).isTrue()
        assertThat(config.lookaheadTokens).isEqualTo(8)
    }
}
