// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.brain.llm.registry

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.llm.provider.Pricing
import com.roshan.persona.common.AppError
import com.roshan.persona.common.Result
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0091: [feature] LocalLlmModelCatalogTest verified

class LocalLlmModelCatalogTest {

    private fun customModel(
        id: String = "custom_test",
        displayName: String = "Test Model",
        downloadUrl: String? = "https://example.com/model.gguf",
        sizeBytes: Long = 1_000L * 1024 * 1024,
        contextLength: Int = 4_096,
    ) = LocalLlmModel(
        id = id,
        displayName = displayName,
        downloadUrl = downloadUrl,
        sha256 = null,
        sizeBytes = sizeBytes,
        contextLength = contextLength,
        quantization = "Q4_K_M",
        architecture = "qwen2",
    )

    // ─── Initialization tests ─────────────────────────────────────────────

    @Test
    fun `catalog initializes with built-in models`() {
        val catalog = LocalLlmModelCatalog()

        val all = catalog.getAll()

        assertThat(all).hasSize(BuiltInLocalModels.ALL.size)
        assertThat(all.map { it.id }).containsAtLeast(
            "qwen2.5-0.5b", "qwen2.5-1.5b", "llama-3.2-1b", "phi-3.5-mini",
        )
    }

    @Test
    fun `all built-in models are not downloaded by default`() {
        val catalog = LocalLlmModelCatalog()

        val downloaded = catalog.getDownloaded()

        assertThat(downloaded).isEmpty()
    }

    @Test
    fun `built-in models are marked isBuiltIn true`() {
        val catalog = LocalLlmModelCatalog()

        val qwen = catalog.getById("qwen2.5-0.5b")

        assertThat(qwen).isNotNull()
        assertThat(qwen!!.isBuiltIn).isTrue()
        assertThat(qwen.isDownloaded).isFalse()
    }

    @Test
    fun `default model is Qwen 2 5 0 5B when none selected`() {
        val catalog = LocalLlmModelCatalog()

        val default = catalog.getDefault()

        assertThat(default).isNotNull()
        assertThat(default!!.id).isEqualTo("qwen2.5-0.5b")
    }

    // ─── Add user model tests ─────────────────────────────────────────────

    @Test
    fun `addUserModel adds custom model`() = runTest {
        val catalog = LocalLlmModelCatalog()

        val result = catalog.addUserModel(customModel())

        assertThat(result).isInstanceOf(Result.Success::class.java)
        val added = (result as Result.Success).data
        assertThat(added.id).isEqualTo("custom_test")
        assertThat(added.isBuiltIn).isFalse()
        assertThat(catalog.getById("custom_test")).isNotNull()
    }

    @Test
    fun `addUserModel rejects ID without custom_ prefix`() = runTest {
        val catalog = LocalLlmModelCatalog()
        val model = customModel(id = "my_model")  // missing prefix

        val result = catalog.addUserModel(model)

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error)
            .isInstanceOf(AppError.Configuration.InvalidProviderConfig::class.java)
    }

    @Test
    fun `addUserModel rejects duplicate ID`() = runTest {
        val catalog = LocalLlmModelCatalog()
        catalog.addUserModel(customModel())

        val result = catalog.addUserModel(customModel())

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error)
            .isInstanceOf(AppError.Configuration.ProviderAlreadyExists::class.java)
    }

    @Test
    fun `addUserModel rejects non-HTTPS URL`() = runTest {
        val catalog = LocalLlmModelCatalog()
        val model = customModel(downloadUrl = "http://example.com/model.gguf")

        val result = catalog.addUserModel(model)

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        val error = (result as Result.Failure).error as AppError.Configuration.InvalidProviderConfig
        assertThat(error.reason).contains("HTTPS")
    }

    @Test
    fun `addUserModel rejects URL without gguf extension`() = runTest {
        val catalog = LocalLlmModelCatalog()
        val model = customModel(downloadUrl = "https://example.com/model.bin")

        val result = catalog.addUserModel(model)

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        val error = (result as Result.Failure).error as AppError.Configuration.InvalidProviderConfig
        assertThat(error.reason).contains(".gguf")
    }

    @Test
    fun `addUserModel accepts null download URL (sideloaded only)`() = runTest {
        val catalog = LocalLlmModelCatalog()
        val model = customModel(downloadUrl = null)

        val result = catalog.addUserModel(model)

        assertThat(result).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `addUserModel sets isBuiltIn to false regardless of input`() = runTest {
        val catalog = LocalLlmModelCatalog()
        val model = customModel().copy(isBuiltIn = true)  // user tries to fake

        val result = catalog.addUserModel(model)

        val added = (result as Result.Success).data
        assertThat(added.isBuiltIn).isFalse()
    }

    // ─── Mark downloaded tests ────────────────────────────────────────────

    @Test
    fun `markDownloaded updates model status`() = runTest {
        val catalog = LocalLlmModelCatalog()

        val result = catalog.markDownloaded("qwen2.5-0.5b", "/data/files/models/qwen2.5-0.5b.gguf")

        assertThat(result).isInstanceOf(Result.Success::class.java)
        val model = (result as Result.Success).data
        assertThat(model.isDownloaded).isTrue()
        assertThat(model.localFilePath).isEqualTo("/data/files/models/qwen2.5-0.5b.gguf")
        assertThat(catalog.getDownloaded()).hasSize(1)
    }

    @Test
    fun `markNotDownloaded clears download status`() = runTest {
        val catalog = LocalLlmModelCatalog()
        catalog.markDownloaded("qwen2.5-0.5b", "/data/files/models/qwen2.5-0.5b.gguf")

        val result = catalog.markNotDownloaded("qwen2.5-0.5b")

        val model = (result as Result.Success).data
        assertThat(model.isDownloaded).isFalse()
        assertThat(model.localFilePath).isNull()
        assertThat(catalog.getDownloaded()).isEmpty()
    }

    @Test
    fun `markDownloaded on unknown model returns Failure`() = runTest {
        val catalog = LocalLlmModelCatalog()

        val result = catalog.markDownloaded("nonexistent", "/some/path")

        assertThat(result).isInstanceOf(Result.Failure::class.java)
    }

    // ─── Set default tests ────────────────────────────────────────────────

    @Test
    fun `setDefault sets model as default`() = runTest {
        val catalog = LocalLlmModelCatalog()
        catalog.markDownloaded("qwen2.5-0.5b", "/some/path")

        val result = catalog.setDefault("qwen2.5-0.5b")

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).data.isDefault).isTrue()
        assertThat(catalog.getDefault()?.id).isEqualTo("qwen2.5-0.5b")
    }

    @Test
    fun `setDefault unsets previous default`() = runTest {
        val catalog = LocalLlmModelCatalog()
        catalog.markDownloaded("qwen2.5-0.5b", "/path1")
        catalog.markDownloaded("qwen2.5-1.5b", "/path2")
        catalog.setDefault("qwen2.5-0.5b")

        catalog.setDefault("qwen2.5-1.5b")

        assertThat(catalog.getById("qwen2.5-0.5b")!!.isDefault).isFalse()
        assertThat(catalog.getById("qwen2.5-1.5b")!!.isDefault).isTrue()
        assertThat(catalog.getDefault()?.id).isEqualTo("qwen2.5-1.5b")
    }

    @Test
    fun `setDefault on undownloaded model returns Failure`() = runTest {
        val catalog = LocalLlmModelCatalog()

        val result = catalog.setDefault("qwen2.5-0.5b")  // not downloaded yet

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error)
            .isInstanceOf(AppError.Configuration.InvalidProviderConfig::class.java)
    }

    @Test
    fun `setDefault on unknown model returns Failure`() = runTest {
        val catalog = LocalLlmModelCatalog()

        val result = catalog.setDefault("nonexistent")

        assertThat(result).isInstanceOf(Result.Failure::class.java)
    }

    // ─── Delete user model tests ──────────────────────────────────────────

    @Test
    fun `deleteUserModel deletes custom model`() = runTest {
        val catalog = LocalLlmModelCatalog()
        catalog.addUserModel(customModel())

        val result = catalog.deleteUserModel("custom_test")

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat(catalog.getById("custom_test")).isNull()
    }

    @Test
    fun `deleteUserModel rejects built-in model`() = runTest {
        val catalog = LocalLlmModelCatalog()

        val result = catalog.deleteUserModel("qwen2.5-0.5b")

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error)
            .isInstanceOf(AppError.Configuration.ProviderBuiltinCannotDelete::class.java)
        assertThat(catalog.getById("qwen2.5-0.5b")).isNotNull()
    }

    @Test
    fun `deleteUserModel on unknown returns Failure`() = runTest {
        val catalog = LocalLlmModelCatalog()

        val result = catalog.deleteUserModel("custom_nonexistent")

        assertThat(result).isInstanceOf(Result.Failure::class.java)
    }

    // ─── Update model tests ───────────────────────────────────────────────

    @Test
    fun `updateModel updates existing model`() = runTest {
        val catalog = LocalLlmModelCatalog()
        catalog.addUserModel(customModel())

        val updated = catalog.getById("custom_test")!!.copy(
            displayName = "Updated Name",
            sizeBytes = 2_000L * 1024 * 1024,
        )
        val result = catalog.updateModel(updated)

        val data = (result as Result.Success).data
        assertThat(data.displayName).isEqualTo("Updated Name")
        assertThat(data.sizeBytes).isEqualTo(2_000L * 1024 * 1024)
    }

    @Test
    fun `updateModel preserves isBuiltIn flag`() = runTest {
        val catalog = LocalLlmModelCatalog()

        val qwen = catalog.getById("qwen2.5-0.5b")!!
        val updated = qwen.copy(
            isBuiltIn = false,  // try to change
            displayName = "Renamed Qwen",
        )
        val result = catalog.updateModel(updated)

        val data = (result as Result.Success).data
        assertThat(data.isBuiltIn).isTrue()  // preserved
        assertThat(data.displayName).isEqualTo("Renamed Qwen")  // updated
    }

    // ─── Display order tests ──────────────────────────────────────────────

    @Test
    fun `getAll returns built-in first (smallest to largest), then user-added alphabetically`() = runTest {
        val catalog = LocalLlmModelCatalog()
        catalog.addUserModel(customModel(id = "custom_zeta", displayName = "Zeta Model"))
        catalog.addUserModel(customModel(id = "custom_alpha", displayName = "Alpha Model"))

        val all = catalog.getAll()

        // Built-in first (in BuiltInLocalModels.ALL order)
        assertThat(all[0].id).isEqualTo("qwen2.5-0.5b")
        // Then user-added sorted alphabetically
        val userAdded = all.drop(BuiltInLocalModels.ALL.size)
        assertThat(userAdded[0].displayName).isEqualTo("Alpha Model")
        assertThat(userAdded[1].displayName).isEqualTo("Zeta Model")
    }

    // ─── Counts tests ─────────────────────────────────────────────────────

    @Test
    fun `totalCount includes built-in and user-added`() = runTest {
        val catalog = LocalLlmModelCatalog()
        catalog.addUserModel(customModel())
        catalog.addUserModel(customModel(id = "custom_another", displayName = "Another"))

        assertThat(catalog.totalCount()).isEqualTo(BuiltInLocalModels.ALL.size + 2)
    }

    @Test
    fun `downloadedCount tracks downloaded models`() = runTest {
        val catalog = LocalLlmModelCatalog()

        assertThat(catalog.downloadedCount()).isEqualTo(0)

        catalog.markDownloaded("qwen2.5-0.5b", "/path1")
        assertThat(catalog.downloadedCount()).isEqualTo(1)

        catalog.markDownloaded("qwen2.5-1.5b", "/path2")
        assertThat(catalog.downloadedCount()).isEqualTo(2)

        catalog.markNotDownloaded("qwen2.5-0.5b")
        assertThat(catalog.downloadedCount()).isEqualTo(1)
    }

    // ─── Validation tests ─────────────────────────────────────────────────

    @Test
    fun `LocalLlmModel rejects blank ID`() {
        try {
            customModel(id = "")
            assert(false) { "Should have thrown" }
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("Model ID cannot be blank")
        }
    }

    @Test
    fun `LocalLlmModel rejects zero size`() {
        try {
            customModel(sizeBytes = 0)
            assert(false) { "Should have thrown" }
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("Size must be positive")
        }
    }

    @Test
    fun `LocalLlmModel rejects invalid context length`() {
        try {
            customModel(contextLength = 0)
            assert(false) { "Should have thrown" }
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("Context length")
        }
    }

    // ─── Built-in catalog tests ───────────────────────────────────────────

    @Test
    fun `BuiltInLocalModels byId returns correct model`() {
        val qwen = BuiltInLocalModels.byId("qwen2.5-0.5b")

        assertThat(qwen).isNotNull()
        assertThat(qwen!!.displayName).contains("Qwen 2.5 0.5B")
    }

    @Test
    fun `BuiltInLocalModels byId returns null for unknown ID`() {
        val unknown = BuiltInLocalModels.byId("nonexistent")

        assertThat(unknown).isNull()
    }

    @Test
    fun `BuiltInLocalModels isBuiltIn returns true for known IDs`() {
        assertThat(BuiltInLocalModels.isBuiltIn("qwen2.5-0.5b")).isTrue()
        assertThat(BuiltInLocalModels.isBuiltIn("llama-3.2-1b")).isTrue()
        assertThat(BuiltInLocalModels.isBuiltIn("custom_test")).isFalse()
    }

    @Test
    fun `each built-in model has sensible defaults`() {
        for (model in BuiltInLocalModels.ALL) {
            assertThat(model.id).isNotEmpty()
            assertThat(model.displayName).isNotEmpty()
            assertThat(model.downloadUrl).startsWith("https://")
            assertThat(model.downloadUrl).contains(".gguf")
            assertThat(model.sizeBytes).isGreaterThan(0L)
            assertThat(model.contextLength).isGreaterThan(0)
            assertThat(model.quantization).isNotEmpty()
            assertThat(model.architecture).isNotEmpty()
            assertThat(model.isBuiltIn).isTrue()
            assertThat(model.isDownloaded).isFalse()
        }
    }

    @Test
    fun `DEFAULT_MODEL is the smallest Qwen model`() {
        assertThat(BuiltInLocalModels.DEFAULT_MODEL.id).isEqualTo("qwen2.5-0.5b")
    }

    @Test
    fun `built-in models are ordered smallest to largest`() {
        val sizes = BuiltInLocalModels.ALL.map { it.sizeBytes }

        for (i in 0 until sizes.size - 1) {
            assertThat(sizes[i]).isAtMost(sizes[i + 1])
        }
    }
}
