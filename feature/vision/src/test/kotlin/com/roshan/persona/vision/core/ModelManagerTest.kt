// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.vision.core

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File

// AUTO_FIX_0118: [feature] ModelManagerTest verified

/**
 * NOUS — Module 6 Step 6.1 Tests (ModelManager + DeviceCapabilityDetector +
 * OmniSlmRuntime + ConfidenceThresholds + 3-Backend Matrix).
 *
 * 25 tests covering:
 *  - DeviceCapabilityDetector (SoC vendor, NPU, RAM tier) — 8 tests
 *  - Backend selection (3-backend matrix) — 6 tests
 *  - ConfidenceThresholds — 6 tests
 *  - OmniSlmRuntime (load, evict, embed, NER, caption, VQA) — 5 tests
 */
class ModelManagerTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // Fake Hardware Probe — simulates any device
    // ═══════════════════════════════════════════════════════════════════════════

    private class FakeHardwareProbe(
        private val cpuInfo: String = "",
        private val hardware: String = "",
        private val board: String = "",
        private val ramMb: Long = 4096,
        private val npuPaths: Set<String> = emptySet(),
    ) : HardwareProbe {
        override fun readCpuInfo(): String = cpuInfo
        override fun readBuildHardware(): String = hardware
        override fun readBuildBoard(): String = board
        override fun totalRamMb(): Long = ramMb
        override fun fileExists(path: String): Boolean = path in npuPaths
    }

    private fun makeDetector(
        cpuInfo: String = "",
        hardware: String = "",
        board: String = "",
        ramMb: Long = 4096,
        npuPaths: Set<String> = emptySet(),
    ): DeviceCapabilityDetector {
        return DeviceCapabilityDetector(
            FakeHardwareProbe(cpuInfo, hardware, board, ramMb, npuPaths),
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DeviceCapabilityDetector — SoC vendor detection (8 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `detectSocVendor returns QUALCOMM for qcom hardware`() {
        val detector = makeDetector(hardware = "qcom", board = "sm8450")
        assertThat(detector.detectSocVendor()).isEqualTo(SocVendor.QUALCOMM)
    }

    @Test
    fun `detectSocVendor returns QUALCOMM for sdm board`() {
        val detector = makeDetector(board = "sdm660")
        assertThat(detector.detectSocVendor()).isEqualTo(SocVendor.QUALCOMM)
    }

    @Test
    fun `detectSocVendor returns MEDIATEK for mt6789 board (Helio G99)`() {
        val detector = makeDetector(board = "mt6789")
        assertThat(detector.detectSocVendor()).isEqualTo(SocVendor.MEDIATEK)
    }

    @Test
    fun `detectSocVendor returns MEDIATEK for Helio cpuinfo`() {
        val detector = makeDetector(cpuInfo = "MediaTek MT6762Z")
        assertThat(detector.detectSocVendor()).isEqualTo(SocVendor.MEDIATEK)
    }

    @Test
    fun `detectSocVendor returns GOOGLE_TENSOR for tensor hardware`() {
        val detector = makeDetector(hardware = "tensor", board = "zuma")
        assertThat(detector.detectSocVendor()).isEqualTo(SocVendor.GOOGLE_TENSOR)
    }

    @Test
    fun `detectSocVendor returns UNISOC for sc9863a board`() {
        val detector = makeDetector(board = "sc9863a")
        assertThat(detector.detectSocVendor()).isEqualTo(SocVendor.UNISOC)
    }

    @Test
    fun `detectSocVendor returns OTHER for unknown hardware`() {
        val detector = makeDetector(hardware = "unknown", board = "mystery")
        assertThat(detector.detectSocVendor()).isEqualTo(SocVendor.OTHER)
    }

    @Test
    fun `detect NPU availability returns true when /dev/vendor/npu exists`() {
        val detector = makeDetector(npuPaths = setOf("/dev/vendor/npu"))
        assertThat(detector.detectNpuAvailability()).isTrue()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RAM tier detection (3 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `detectRamTier returns HIGH for 8GB`() {
        val detector = makeDetector(ramMb = 8192)
        assertThat(detector.detectRamTier()).isEqualTo(RamTier.HIGH)
    }

    @Test
    fun `detectRamTier returns MID for 4GB`() {
        val detector = makeDetector(ramMb = 4096)
        assertThat(detector.detectRamTier()).isEqualTo(RamTier.MID)
    }

    @Test
    fun `detectRamTier returns LOW for 3GB`() {
        val detector = makeDetector(ramMb = 3072)
        assertThat(detector.detectRamTier()).isEqualTo(RamTier.LOW)
    }

    @Test
    fun `detectRamTier returns ULTRA for 2GB`() {
        val detector = makeDetector(ramMb = 2048)
        assertThat(detector.detectRamTier()).isEqualTo(RamTier.ULTRA)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 3-Backend matrix selection (6 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `selectOmniSlmBackend returns QNN for Qualcomm with NPU and Mid RAM`() {
        val cap = DeviceCapability(
            socVendor = SocVendor.QUALCOMM,
            hasNpu = true,
            ramTier = RamTier.MID,
            totalRamMb = 4096,
            board = "sm8450",
            hardware = "qcom",
        )
        assertThat(selectOmniSlmBackend(cap)).isEqualTo(ExecuTorchBackend.QNN)
    }

    @Test
    fun `selectOmniSlmBackend returns NEUROPILOT for MediaTek with NPU and Mid RAM`() {
        val cap = DeviceCapability(
            socVendor = SocVendor.MEDIATEK,
            hasNpu = true,
            ramTier = RamTier.MID,
            totalRamMb = 4096,
            board = "mt6789",
            hardware = "mt6789",
        )
        assertThat(selectOmniSlmBackend(cap)).isEqualTo(ExecuTorchBackend.NEUROPILOT)
    }

    @Test
    fun `selectOmniSlmBackend returns XNNPACK for MediaTek without NPU (Helio G85)`() {
        val cap = DeviceCapability(
            socVendor = SocVendor.MEDIATEK,
            hasNpu = false,
            ramTier = RamTier.LOW,
            totalRamMb = 3072,
            board = "mt6762z",
            hardware = "mt6762z",
        )
        assertThat(selectOmniSlmBackend(cap)).isEqualTo(ExecuTorchBackend.XNNPACK)
    }

    @Test
    fun `selectOmniSlmBackend returns DISABLED for ultra-low RAM`() {
        val cap = DeviceCapability(
            socVendor = SocVendor.QUALCOMM,
            hasNpu = false,
            ramTier = RamTier.ULTRA,
            totalRamMb = 2048,
            board = "msm8909",
            hardware = "qcom",
        )
        assertThat(selectOmniSlmBackend(cap)).isEqualTo(ExecuTorchBackend.DISABLED)
    }

    @Test
    fun `selectOmniSlmBackend returns XNNPACK for UNISOC`() {
        val cap = DeviceCapability(
            socVendor = SocVendor.UNISOC,
            hasNpu = false,
            ramTier = RamTier.LOW,
            totalRamMb = 3072,
            board = "sc9863a",
            hardware = "unisoc",
        )
        assertThat(selectOmniSlmBackend(cap)).isEqualTo(ExecuTorchBackend.XNNPACK)
    }

    @Test
    fun `resolveBackendChain returns NEUROPILOT then OPENCL then XNNPACK for MediaTek G99`() {
        val cap = DeviceCapability(
            socVendor = SocVendor.MEDIATEK,
            hasNpu = true,
            ramTier = RamTier.MID,
            totalRamMb = 4096,
            board = "mt6789",
            hardware = "mt6789",
        )
        val chain = resolveBackendChain(cap)
        assertThat(chain).containsExactly(
            ExecuTorchBackend.NEUROPILOT,
            ExecuTorchBackend.OPENCL,
            ExecuTorchBackend.XNNPACK,
        ).inOrder()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Quantization selection (2 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `selectOmniSlmQuantization returns INT4 for High RAM`() {
        val cap = DeviceCapability(
            socVendor = SocVendor.QUALCOMM,
            hasNpu = true,
            ramTier = RamTier.HIGH,
            totalRamMb = 8192,
            board = "sm8450",
            hardware = "qcom",
        )
        assertThat(selectOmniSlmQuantization(cap)).isEqualTo(OmniSlmQuantization.INT4)
    }

    @Test
    fun `selectOmniSlmQuantization returns INT2 for Low RAM (3GB)`() {
        val cap = DeviceCapability(
            socVendor = SocVendor.QUALCOMM,
            hasNpu = true,
            ramTier = RamTier.LOW,
            totalRamMb = 3072,
            board = "sm8450",
            hardware = "qcom",
        )
        assertThat(selectOmniSlmQuantization(cap)).isEqualTo(OmniSlmQuantization.INT2)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ConfidenceThresholds (6 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `classifyConfidence returns HIGH for 0_90`() {
        assertThat(ConfidenceThresholds.classifyConfidence(0.90f)).isEqualTo(ConfidenceTier.HIGH)
    }

    @Test
    fun `classifyConfidence returns MEDIUM for 0_70`() {
        assertThat(ConfidenceThresholds.classifyConfidence(0.70f)).isEqualTo(ConfidenceTier.MEDIUM)
    }

    @Test
    fun `classifyConfidence returns LOW for 0_50`() {
        assertThat(ConfidenceThresholds.classifyConfidence(0.50f)).isEqualTo(ConfidenceTier.LOW)
    }

    @Test
    fun `classifyConfidence returns REJECT for 0_20`() {
        assertThat(ConfidenceThresholds.classifyConfidence(0.20f)).isEqualTo(ConfidenceTier.REJECT)
    }

    @Test
    fun `formatTtsOutput asserts at HIGH confidence`() {
        val tts = ConfidenceThresholds.formatTtsOutput("Snake Plant", 0.95f)
        assertThat(tts).isEqualTo("That's a Snake Plant.")
    }

    @Test
    fun `formatTtsOutput hedges at LOW confidence`() {
        val tts = ConfidenceThresholds.formatTtsOutput("Snake Plant", 0.45f)
        assertThat(tts).isEqualTo("I'm not sure, but it looks like a Snake Plant.")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // OmniSlmRuntime (load + evict + embed + NER + caption + VQA) — 5 tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `OmniSlmRuntime load calls delegate with backend + quantization`() = runTest {
        val delegate = FakeOmniSlmDelegate()
        val runtime = OmniSlmRuntime(
            delegate = delegate,
            backend = ExecuTorchBackend.QNN,
            quantization = OmniSlmQuantization.INT4,
            dispatcher = Dispatchers.Unconfined,
        )
        val loaded = runtime.load()
        assertThat(loaded).isTrue()
        assertThat(delegate.loadCallCount).isEqualTo(1)
        assertThat(delegate.loadedBackend).isEqualTo(ExecuTorchBackend.QNN)
        assertThat(delegate.loadedQuantization).isEqualTo(OmniSlmQuantization.INT4)
        assertThat(runtime.isReady()).isTrue()
    }

    @Test
    fun `OmniSlmRuntime evict releases from RAM`() = runTest {
        val delegate = FakeOmniSlmDelegate()
        val runtime = OmniSlmRuntime(
            delegate = delegate,
            backend = ExecuTorchBackend.QNN,
            quantization = OmniSlmQuantization.INT4,
            dispatcher = Dispatchers.Unconfined,
        )
        runtime.load()
        assertThat(runtime.isResident()).isTrue()
        runtime.evict()
        assertThat(runtime.isResident()).isFalse()
        assertThat(delegate.unloadCallCount).isEqualTo(1)
    }

    @Test
    fun `OmniSlmRuntime embed wraps result in ConfidenceTieredResult`() = runTest {
        val delegate = FakeOmniSlmDelegate().apply {
            embedResult = OmniSlmEmbedResult(FloatArray(384) { 0.5f }, 0.95f)
        }
        val runtime = OmniSlmRuntime(
            delegate = delegate,
            backend = ExecuTorchBackend.QNN,
            quantization = OmniSlmQuantization.INT4,
            dispatcher = Dispatchers.Unconfined,
        )
        val result = runtime.embed("call mom")
        assertThat(result.tier).isEqualTo(ConfidenceTier.HIGH)
        assertThat(result.value).isNotNull()
        assertThat(result.value!!.size).isEqualTo(384)
        assertThat(result.confidence).isEqualTo(0.95f)
        assertThat(delegate.embedCallCount).isEqualTo(1)
    }

    @Test
    fun `OmniSlmRuntime extractEntities returns tiered entities`() = runTest {
        val entities = listOf(
            OmniSlmEntity("Mom", OmniSlmEntityType.PERSON, 0.95f, 5, 8),
            OmniSlmEntity("8 PM", OmniSlmEntityType.TIME, 0.88f, 14, 18),
        )
        val delegate = FakeOmniSlmDelegate().apply {
            nerResult = OmniSlmNerResult(entities, 0.91f)
        }
        val runtime = OmniSlmRuntime(
            delegate = delegate,
            backend = ExecuTorchBackend.QNN,
            quantization = OmniSlmQuantization.INT4,
            dispatcher = Dispatchers.Unconfined,
        )
        val result = runtime.extractEntities("Call Mom at 8 PM")
        assertThat(result.tier).isEqualTo(ConfidenceTier.HIGH)
        assertThat(result.value).hasSize(2)
        assertThat(result.value!![0].text).isEqualTo("Mom")
        assertThat(result.value!![0].type).isEqualTo(OmniSlmEntityType.PERSON)
    }

    @Test
    fun `DisabledOmniSlmRuntime isReady returns false and embed returns rejected`() = runTest {
        val runtime = DisabledOmniSlmRuntime(Dispatchers.Unconfined)
        assertThat(runtime.isDisabled).isTrue()
        assertThat(runtime.isReady()).isFalse()
        val result = runtime.embed("anything")
        assertThat(result.tier).isEqualTo(ConfidenceTier.REJECT)
        assertThat(result.value).isNull()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ModelManager — full integration (4 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    private class FakeModelStore : ModelStore(File(System.getProperty("java.io.tmpdir"), "test_models_${System.nanoTime()}")) {
        // Pre-create files to simulate on-disk models
        fun seed(filename: String) {
            path(filename).apply { parentFile?.mkdirs(); writeText("fake model bytes") }
        }
    }

    @Test
    fun `ModelManager returns DisabledOmniSlmRuntime for ultra-low RAM`() = runTest {
        val detector = makeDetector(ramMb = 2048, hardware = "qcom", board = "sm8450")
        val store = FakeModelStore()
        val factory = FakeOmniSlmDelegateFactory()
        val manager = ModelManager(detector, store, factory, Dispatchers.Unconfined)

        val runtime = manager.getOmniSlmRuntime()
        assertThat(runtime.isDisabled).isTrue()
    }

    @Test
    fun `ModelManager loads OmniSlmRuntime with QNN for Qualcomm flagship`() = runTest {
        val detector = makeDetector(ramMb = 8192, hardware = "qcom", board = "sm8450")
        val store = FakeModelStore().apply {
            seed("omni-slm-int4-qnn.pte")
        }
        val delegate = FakeOmniSlmDelegate()
        val factory = FakeOmniSlmDelegateFactory { delegate }
        val manager = ModelManager(detector, store, factory, Dispatchers.Unconfined)

        val runtime = manager.getOmniSlmRuntime()
        assertThat(runtime.isDisabled).isFalse()
        assertThat(runtime.backend).isEqualTo(ExecuTorchBackend.QNN)
        assertThat(runtime.quantization).isEqualTo(OmniSlmQuantization.INT4)
        assertThat(delegate.loadCallCount).isEqualTo(1)
    }

    @Test
    fun `ModelManager falls back to XNNPACK when QNN fails`() = runTest {
        val detector = makeDetector(ramMb = 8192, hardware = "qcom", board = "sm8450")
        val store = FakeModelStore().apply {
            // No QNN file present, but XNNPACK is.
            seed("omni-slm-int4-xnnpack.pte")
        }
        val delegate = FakeOmniSlmDelegate()
        val factory = FakeOmniSlmDelegateFactory { delegate }
        val manager = ModelManager(detector, store, factory, Dispatchers.Unconfined)

        val runtime = manager.getOmniSlmRuntime()
        assertThat(runtime.isDisabled).isFalse()
        // QNN file not on disk → falls back to XNNPACK
        assertThat(runtime.backend).isEqualTo(ExecuTorchBackend.XNNPACK)
    }

    @Test
    fun `ModelManager evictOmniSlm releases from RAM`() = runTest {
        val detector = makeDetector(ramMb = 8192, hardware = "qcom", board = "sm8450")
        val store = FakeModelStore().apply {
            seed("omni-slm-int4-qnn.pte")
        }
        val delegate = FakeOmniSlmDelegate()
        val factory = FakeOmniSlmDelegateFactory { delegate }
        val manager = ModelManager(detector, store, factory, Dispatchers.Unconfined)

        val runtime = manager.getOmniSlmRuntime()
        assertThat(runtime.isResident()).isTrue()

        val evicted = manager.evictOmniSlm()
        assertThat(evicted).isTrue()
        assertThat(runtime.isResident()).isFalse()
        assertThat(delegate.unloadCallCount).isEqualTo(1)
    }
}
