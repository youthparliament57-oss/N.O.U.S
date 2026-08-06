// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.llm.download

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.llm.registry.LocalLlmModelCatalog
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

// AUTO_FIX_0132: [feature] ModelDownloaderTest verified

/**
 * NOUS — Step 4.8: ModelDownloader tests.
 *
 * Tests GGUF validation, SHA256 verification, URL validation,
 * DownloadProgress, and delete operations.
 *
 * @see <a href="docs/strategy/module-4-strategy-v2.1.md">§4.8 Model Downloader</a>
 */
class ModelDownloaderTest {

    private lateinit var tempDir: File
    private lateinit var downloader: ModelDownloader
    private val modelCatalog = LocalLlmModelCatalog()

    @Before
    fun setup() {
        tempDir = Files.createTempDirectory("nous-test").toFile()
        downloader = ModelDownloader(
            httpClient = OkHttpClient(),
            modelCatalog = modelCatalog,
            modelsDir = tempDir.absolutePath,
        )
    }

    @After
    fun teardown() {
        tempDir.deleteRecursively()
    }

    // ─── DownloadProgress tests (4) ────────────────────────────────

    @Test
    fun `DownloadProgress percent is 50 when half downloaded`() {
        val progress = ModelDownloader.DownloadProgress(
            downloadedBytes = 500,
            totalBytes = 1000,
        )
        assertThat(progress.percent).isEqualTo(50)
    }

    @Test
    fun `DownloadProgress percent is 0 when nothing downloaded`() {
        val progress = ModelDownloader.DownloadProgress(
            downloadedBytes = 0,
            totalBytes = 1000,
        )
        assertThat(progress.percent).isEqualTo(0)
    }

    @Test
    fun `DownloadProgress percent is 100 when fully downloaded`() {
        val progress = ModelDownloader.DownloadProgress(
            downloadedBytes = 1000,
            totalBytes = 1000,
        )
        assertThat(progress.percent).isEqualTo(100)
    }

    @Test
    fun `DownloadProgress percent is 0 when totalBytes is zero`() {
        val progress = ModelDownloader.DownloadProgress(
            downloadedBytes = 500,
            totalBytes = 0,
        )
        assertThat(progress.percent).isEqualTo(0)
    }

    // ─── URL validation (2) ───────────────────────────────────────

    @Test
    fun `download rejects HTTP URL with Failure`() = runTest {
        val result = downloader.download(
            modelId = "test-model",
            downloadUrl = "http://example.com/model.gguf",
            modelSizeBytes = 1000,
        )

        assertThat(result).isInstanceOf(com.roshan.persona.common.Result.Failure::class.java)
        val error = (result as com.roshan.persona.common.Result.Failure).error
        assertThat(error.message).contains("HTTPS")
    }

    @Test
    fun `download rejects FTP URL with Failure`() = runTest {
        val result = downloader.download(
            modelId = "test-model",
            downloadUrl = "ftp://example.com/model.gguf",
            modelSizeBytes = 1000,
        )

        assertThat(result).isInstanceOf(com.roshan.persona.common.Result.Failure::class.java)
    }

    // ─── SHA256 verification (2) ──────────────────────────────────

    @Test
    fun `calculateSha256 returns correct hash for known content`() {
        val testFile = File(tempDir, "test.txt")
        testFile.writeText("hello")

        val hash = downloader.calculateSha256(testFile)

        // SHA-256 of "hello" = 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
        assertThat(hash).isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824")
    }

    @Test
    fun `calculateSha256 returns correct hash for empty file`() {
        val testFile = File(tempDir, "empty.txt")
        testFile.writeText("")

        val hash = downloader.calculateSha256(testFile)

        // SHA-256 of empty string = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        assertThat(hash).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
    }

    // ─── GGUF validation (2) ──────────────────────────────────────

    @Test
    fun `isValidGguf returns true for valid GGUF magic bytes`() {
        val ggufFile = File(tempDir, "model.gguf")
        // GGUF magic: 0x46554747 in little-endian = bytes [0x47, 0x47, 0x55, 0x46]
        // As int: 0x46554747
        ggufFile.outputStream().use { out ->
            out.write(0x47)  // G
            out.write(0x47)  // G
            out.write(0x55)  // U
            out.write(0x46)  // F
            out.write(0x00)  // version (padding)
        }

        assertThat(downloader.isValidGguf(ggufFile)).isTrue()
    }

    @Test
    fun `isValidGguf returns false for invalid header`() {
        val badFile = File(tempDir, "not-gguf.bin")
        badFile.outputStream().use { out ->
            out.write(0x00)
            out.write(0x01)
            out.write(0x02)
            out.write(0x03)
        }

        assertThat(downloader.isValidGguf(badFile)).isFalse()
    }

    // ─── Delete (1) ───────────────────────────────────────────────

    @Test
    fun `delete removes file and marks model as not downloaded`() = runTest {
        // Create a fake model file with name matching what delete() expects
        val modelFile = File(tempDir, "qwen2.5-0.5b.gguf")
        modelFile.writeText("fake model content")

        // Mark as downloaded in catalog
        modelCatalog.markDownloaded("qwen2.5-0.5b", modelFile.absolutePath)

        // Delete it
        downloader.delete("qwen2.5-0.5b")

        // File should be gone
        assertThat(modelFile.exists()).isFalse()
        // Catalog should show as not downloaded
        assertThat(modelCatalog.getById("qwen2.5-0.5b")?.isDownloaded).isFalse()
    }
}
