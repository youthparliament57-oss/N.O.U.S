// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.llm.local

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0125: [feature] PredictiveModelPreloaderTest verified

/**
 * NOUS — Step 4.9: PredictiveModelPreloader + KV cache + LoRA + Lookahead tests.
 *
 * @see <a href="docs/strategy/module-4-strategy-v2.1.md">§4.9 Preloader + Advanced</a>
 */
class PredictiveModelPreloaderTest {

    private val modelManager: LocalModelManager = mockk(relaxed = true)
    private val preloader = PredictiveModelPreloader(modelManager)

    // ─── shouldPreload conditions (4) ─────────────────────────────

    @Test
    fun `shouldPreload returns true when charging + idle + battery above 30`() {
        val result = preloader.shouldPreload(
            isCharging = true,
            batteryLevel = 80,
            isIdle = true,
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `shouldPreload returns false when not charging`() {
        val result = preloader.shouldPreload(
            isCharging = false,
            batteryLevel = 80,
            isIdle = true,
        )
        assertThat(result).isFalse()
    }

    @Test
    fun `shouldPreload returns false when battery below 30`() {
        val result = preloader.shouldPreload(
            isCharging = true,
            batteryLevel = 20,
            isIdle = true,
        )
        assertThat(result).isFalse()
    }

    @Test
    fun `shouldPreload returns false when not idle`() {
        val result = preloader.shouldPreload(
            isCharging = true,
            batteryLevel = 80,
            isIdle = false,
        )
        assertThat(result).isFalse()
    }

    // ─── Preload behavior (2) ─────────────────────────────────────

    @Test
    fun `preload returns true when model loads successfully`() = runTest {
        coEvery { modelManager.isLoaded() } returns false
        coEvery { modelManager.loadDefault() } returns true

        val result = preloader.preload()

        assertThat(result).isTrue()
        coVerify { modelManager.loadDefault() }
    }

    @Test
    fun `preload skips when model already loaded`() = runTest {
        coEvery { modelManager.isLoaded() } returns true

        val result = preloader.preload()

        assertThat(result).isTrue()
        coVerify(exactly = 0) { modelManager.loadDefault() }
    }

    // ─── Unload (1) ───────────────────────────────────────────────

    @Test
    fun `unload calls modelManager unload when loaded`() = runTest {
        coEvery { modelManager.isLoaded() } returns true

        preloader.unload()

        coVerify { modelManager.unload() }
    }

    // ─── isModelPreloaded (1) ─────────────────────────────────────

    @Test
    fun `isModelPreloaded delegates to modelManager`() {
        coEvery { modelManager.isLoaded() } returns true
        assertThat(preloader.isModelPreloaded).isTrue()

        coEvery { modelManager.isLoaded() } returns false
        assertThat(preloader.isModelPreloaded).isFalse()
    }
}
