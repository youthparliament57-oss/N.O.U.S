// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.vision.robustness

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.roshan.persona.vision.core.ConfidenceTier
import com.roshan.persona.vision.core.ConfidenceTieredResult
import com.roshan.persona.vision.core.ConfidenceThresholds
import com.roshan.persona.vision.core.DeviceCapability
import com.roshan.persona.vision.core.ExecuTorchBackend
import com.roshan.persona.vision.core.FakeHardwareProbe
import com.roshan.persona.vision.core.FakeOmniSlmDelegate
import com.roshan.persona.vision.core.FakeOmniSlmDelegateFactory
import com.roshan.persona.vision.core.ModelManager
import com.roshan.persona.vision.core.ModelStore
import com.roshan.persona.vision.core.OmniSlmEmbedResult
import com.roshan.persona.vision.core.OmniSlmQuantization
import com.roshan.persona.vision.core.OmniSlmRuntime
import com.roshan.persona.vision.core.RamTier
import com.roshan.persona.vision.core.SocVendor
import com.roshan.persona.vision.ocr.BoundingBox
import com.roshan.persona.vision.ocr.OcrBlock
import com.roshan.persona.vision.ocr.OcrResult
import com.roshan.persona.vision.ocr.RgbImage
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File

// AUTO_FIX_0119: [feature] VisionPatchesTest verified

/**
 * NOUS — Module 6 v4.2 Patches Test Suite (HIGH Priority Items).
 *
 * 30 tests covering:
 *  - VisionFailureHandler — 10 failure scenarios + recovery actions — 12 tests
 *  - CameraPermissionManager — state machine + photo picker fallback — 9 tests
 *  - FirstFrameParallelLoader — parallel loading + v2 fallback + upgrade — 9 tests
 */
class VisionPatchesTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // VisionFailureHandler (12 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    private val failureHandler = VisionFailureHandler(Dispatchers.Unconfined)

    @Test
    fun `VisionFailureHandler LOW_LIGHT returns offer flash recovery`() = runTest {
        val response = failureHandler.handleFailure(VisionFailureType.LOW_LIGHT)
        assertThat(response.spokenMessage).contains("dark")
        assertThat(response.recoveryAction).isEqualTo(RecoveryAction.OFFER_FLASH)
        assertThat(response.shouldRetry).isFalse()
    }

    @Test
    fun `VisionFailureHandler BLURRY_IMAGE returns retry with longer exposure`() = runTest {
        val response = failureHandler.handleFailure(VisionFailureType.BLURRY_IMAGE)
        assertThat(response.spokenMessage).contains("steady")
        assertThat(response.recoveryAction).isEqualTo(RecoveryAction.RETRY_WITH_LONGER_EXPOSURE)
        assertThat(response.shouldRetry).isTrue()
    }

    @Test
    fun `VisionFailureHandler NO_TEXT_FOUND returns no retry`() = runTest {
        val response = failureHandler.handleFailure(VisionFailureType.NO_TEXT_FOUND)
        assertThat(response.spokenMessage).contains("text")
        assertThat(response.shouldRetry).isFalse()
    }

    @Test
    fun `VisionFailureHandler OMNI_SLM_CRASH returns silent fallback to v2`() = runTest {
        val response = failureHandler.handleFailure(VisionFailureType.OMNI_SLM_CRASH)
        assertThat(response.userMessage).isNull()  // Silent
        assertThat(response.spokenMessage).isNull()
        assertThat(response.recoveryAction).isEqualTo(RecoveryAction.FALLBACK_TO_V2_STACK)
        assertThat(response.shouldFallback).isTrue()
    }

    @Test
    fun `VisionFailureHandler OCR_GIBBERISH returns retry with enhanced preprocessing`() = runTest {
        val response = failureHandler.handleFailure(VisionFailureType.OCR_GIBBERISH)
        assertThat(response.spokenMessage).contains("clearly")
        assertThat(response.recoveryAction).isEqualTo(RecoveryAction.RETRY_WITH_ENHANCED_PREPROCESSING)
        assertThat(response.shouldRetry).isTrue()
    }

    @Test
    fun `VisionFailureHandler CAMERA_PERMISSION_DENIED guides to settings`() = runTest {
        val response = failureHandler.handleFailure(VisionFailureType.CAMERA_PERMISSION_DENIED)
        assertThat(response.spokenMessage).contains("camera")
        assertThat(response.recoveryAction).isEqualTo(RecoveryAction.GUIDE_TO_SETTINGS)
    }

    @Test
    fun `VisionFailureHandler LMK_KILL restores from encrypted cache`() = runTest {
        val response = failureHandler.handleFailure(VisionFailureType.LMK_KILL)
        assertThat(response.spokenMessage).contains("paused")
        assertThat(response.recoveryAction).isEqualTo(RecoveryAction.RESTORE_FROM_ENCRYPTED_CACHE)
    }

    @Test
    fun `VisionFailureHandler THERMAL_THROTTLE reduces FPS and resolution`() = runTest {
        val response = failureHandler.handleFailure(VisionFailureType.THERMAL_THROTTLE)
        assertThat(response.spokenMessage).contains("warm")
        assertThat(response.recoveryAction).isEqualTo(RecoveryAction.REDUCE_FPS_AND_RESOLUTION)
    }

    @Test
    fun `VisionFailureHandler BATTERY_LOW disables captioning`() = runTest {
        val response = failureHandler.handleFailure(VisionFailureType.BATTERY_LOW)
        assertThat(response.spokenMessage).contains("power-saving")
        assertThat(response.recoveryAction).isEqualTo(RecoveryAction.DISABLE_CAPTIONING_REDUCE_FPS)
    }

    @Test
    fun `VisionFailureHandler MODEL_LOAD_FAILURE falls back to v2`() = runTest {
        val response = failureHandler.handleFailure(VisionFailureType.MODEL_LOAD_FAILURE)
        assertThat(response.spokenMessage).contains("warming up")
        assertThat(response.recoveryAction).isEqualTo(RecoveryAction.FALLBACK_TO_V2_STACK)
        assertThat(response.shouldFallback).isTrue()
    }

    @Test
    fun `VisionFailureHandler stats tracks failure counts by type`() = runTest {
        failureHandler.handleFailure(VisionFailureType.LOW_LIGHT)
        failureHandler.handleFailure(VisionFailureType.LOW_LIGHT)
        failureHandler.handleFailure(VisionFailureType.OMNI_SLM_CRASH)
        val stats = failureHandler.stats()
        assertThat(stats.totalFailuresHandled).isEqualTo(3)
        assertThat(stats.failureCounts[VisionFailureType.LOW_LIGHT]).isEqualTo(2)
        assertThat(stats.failureCounts[VisionFailureType.OMNI_SLM_CRASH]).isEqualTo(1)
    }

    @Test
    fun `VisionFailureHandler wrapOcrResult returns Success for usable result`() = runTest {
        val ocrResult = OcrResult(
            fullText = "Hello",
            blocks = listOf(OcrBlock("Hello", BoundingBox(0f, 0f, 1f, 0.1f), 0.95f, "en")),
            extractedFields = emptyList(),
            detectedDocumentType = com.roshan.persona.vision.ocr.DocumentType.UNKNOWN,
            structuredDocument = null,
            enhancementApplied = false,
            sourceImageHash = "abc",
            processingTimeMs = 100,
            overallConfidence = 0.95f,
        )
        val tiered = ConfidenceTieredResult.from(ocrResult, 0.95f)
        val result = failureHandler.wrapOcrResult(tiered)
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()!!.fullText).isEqualTo("Hello")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CameraPermissionManager (9 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    private fun makePermissionManager(): CameraPermissionManager {
        val context = mockk<Context>(relaxed = true)
        every {
            context.checkSelfPermission(android.Manifest.permission.CAMERA)
        } returns android.content.pm.PackageManager.PERMISSION_DENIED
        return CameraPermissionManager(context)
    }

    @Test
    fun `CameraPermission initial state is NOT_REQUESTED`() {
        val manager = makePermissionManager()
        assertThat(manager.permissionState.value).isEqualTo(CameraPermissionState.NOT_REQUESTED)
    }

    @Test
    fun `CameraPermission onPermissionGranted sets GRANTED`() {
        val manager = makePermissionManager()
        manager.onPermissionGranted()
        assertThat(manager.permissionState.value).isEqualTo(CameraPermissionState.GRANTED)
        assertThat(manager.getDenialCount()).isEqualTo(0)
    }

    @Test
    fun `CameraPermission onPermissionDenied once sets DENIED_ONCE`() {
        val manager = makePermissionManager()
        manager.onPermissionDenied()
        assertThat(manager.permissionState.value).isEqualTo(CameraPermissionState.DENIED_ONCE)
        assertThat(manager.getDenialCount()).isEqualTo(1)
    }

    @Test
    fun `CameraPermission onPermissionDenied twice sets DENIED_TWICE`() {
        val manager = makePermissionManager()
        manager.onPermissionDenied()
        manager.onPermissionDenied()
        assertThat(manager.permissionState.value).isEqualTo(CameraPermissionState.DENIED_TWICE)
    }

    @Test
    fun `CameraPermission onPermissionDenied three times sets DENIED_PERMANENTLY`() {
        val manager = makePermissionManager()
        manager.onPermissionDenied()
        manager.onPermissionDenied()
        manager.onPermissionDenied()
        assertThat(manager.permissionState.value).isEqualTo(CameraPermissionState.DENIED_PERMANENTLY)
    }

    @Test
    fun `CameraPermission getRationaleMessage returns different messages for different states`() {
        val manager = makePermissionManager()
        val notRequested = manager.getRationaleMessage()
        assertThat(notRequested).contains("camera access")

        manager.onPermissionDenied()
        val deniedOnce = manager.getRationaleMessage()
        assertThat(deniedOnce).contains("still need")

        manager.onPermissionDenied()
        val deniedTwice = manager.getRationaleMessage()
        assertThat(deniedTwice).contains("gallery")
    }

    @Test
    fun `CameraPermission shouldOfferPhotoPicker true after two denials`() {
        val manager = makePermissionManager()
        manager.onPermissionDenied()
        assertThat(manager.shouldOfferPhotoPicker()).isFalse()
        manager.onPermissionDenied()
        assertThat(manager.shouldOfferPhotoPicker()).isTrue()
    }

    @Test
    fun `CameraPermission getRecoveryAction returns correct actions`() {
        val manager = makePermissionManager()

        // NOT_REQUESTED → REQUEST_PERMISSION
        assertThat(manager.getRecoveryAction()).isEqualTo(CameraRecoveryAction.REQUEST_PERMISSION)

        // DENIED_TWICE → OFFER_PHOTO_PICKER (if supported) or GUIDE_TO_SETTINGS
        manager.onPermissionDenied()
        manager.onPermissionDenied()
        val action = manager.getRecoveryAction()
        assertThat(action).isAnyOf(
            CameraRecoveryAction.OFFER_PHOTO_PICKER,
            CameraRecoveryAction.OFFER_PHOTO_PICKER_OR_SETTINGS,
            CameraRecoveryAction.GUIDE_TO_SETTINGS,
        )
    }

    @Test
    fun `CameraPermission reset clears state`() {
        val manager = makePermissionManager()
        manager.onPermissionDenied()
        manager.onPermissionDenied()
        manager.reset()
        assertThat(manager.permissionState.value).isEqualTo(CameraPermissionState.NOT_REQUESTED)
        assertThat(manager.getDenialCount()).isEqualTo(0)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FirstFrameParallelLoader (9 tests)
    // ═══════════════════════════════════════════════════════════════════════════

    private fun makeModelManager(ramMb: Long = 8192): Pair<ModelManager, FakeOmniSlmDelegate> {
        val probe = FakeHardwareProbe(
            hardware = "qcom", board = "sm8450", ramMb = ramMb,
            npuPaths = setOf("/dev/vendor/npu"),
        )
        val detector = com.roshan.persona.vision.core.DeviceCapabilityDetector(probe)
        val storeDir = File(System.getProperty("java.io.tmpdir"), "test_vision_${System.nanoTime()}")
        val modelStore = ModelStore(storeDir)
        modelStore.path("omni-slm-int4-qnn.pte").writeText("fake model")

        val delegate = FakeOmniSlmDelegate().apply {
            loadResult = true
            embedResult = OmniSlmEmbedResult(FloatArray(384) { 0.5f }, 0.95f)
        }
        val manager = ModelManager(
            detector, modelStore,
            FakeOmniSlmDelegateFactory { delegate },
            Dispatchers.Unconfined,
        )
        return Pair(manager, delegate)
    }

    @Test
    fun `FirstFrameLoader initializeParallel returns success when camera + model ready`() = runTest {
        val (modelManager, _) = makeModelManager()
        val camera = FakeCameraProvider(openDelayMs = 10)
        val v2Stack = FakeV2StackProvider()
        val handler = VisionFailureHandler(Dispatchers.Unconfined)

        val loader = FirstFrameParallelLoader(
            modelManager, v2Stack, camera, handler, Dispatchers.Unconfined,
        )

        val result = loader.initializeParallel()
        assertThat(result.success).isTrue()
        assertThat(result.usedOmniSlm).isTrue()
        assertThat(result.v2StackActive).isFalse()
    }

    @Test
    fun `FirstFrameLoader initializeParallel returns failure when camera fails`() = runTest {
        val (modelManager, _) = makeModelManager()
        val camera = FakeCameraProvider(shouldFail = true)
        val v2Stack = FakeV2StackProvider()
        val handler = VisionFailureHandler(Dispatchers.Unconfined)

        val loader = FirstFrameParallelLoader(
            modelManager, v2Stack, camera, handler, Dispatchers.Unconfined,
        )

        val result = loader.initializeParallel()
        assertThat(result.success).isFalse()
        assertThat(result.errorMessage).contains("Camera")
    }

    @Test
    fun `FirstFrameLoader initializeParallel uses v2 fallback when Omni-SLM disabled`() = runTest {
        // 2GB RAM → Omni-SLM disabled (ultra-low tier)
        val (modelManager, _) = makeModelManager(ramMb = 2048)
        val camera = FakeCameraProvider(openDelayMs = 10)
        val v2Stack = FakeV2StackProvider(cannedCaption = "v2 fallback caption")
        val handler = VisionFailureHandler(Dispatchers.Unconfined)

        val loader = FirstFrameParallelLoader(
            modelManager, v2Stack, camera, handler, Dispatchers.Unconfined,
        )

        val result = loader.initializeParallel()
        assertThat(result.success).isTrue()
        assertThat(result.usedOmniSlm).isFalse()
        assertThat(result.v2StackActive).isTrue()
        assertThat(result.needsUpgrade).isTrue()
    }

    @Test
    fun `FirstFrameLoader processFirstFrame uses Omni-SLM when ready`() = runTest {
        val (modelManager, delegate) = makeModelManager()
        delegate.captionResult = com.roshan.persona.vision.core.OmniSlmCaptionResult("a snake plant", 0.88f)

        val camera = FakeCameraProvider(openDelayMs = 10)
        val v2Stack = FakeV2StackProvider()
        val handler = VisionFailureHandler(Dispatchers.Unconfined)

        val loader = FirstFrameParallelLoader(
            modelManager, v2Stack, camera, handler, Dispatchers.Unconfined,
        )

        loader.initializeParallel()
        camera.openCamera()

        var capturedResult: FirstFrameProcessResult? = null
        loader.processFirstFrame(RgbImage(100, 100, ByteArray(300))) { result ->
            capturedResult = result
        }

        assertThat(capturedResult).isNotNull()
        assertThat(capturedResult!!.source).isEqualTo(FrameProcessSource.OMNI_SLM)
        assertThat(capturedResult!!.caption).isEqualTo("a snake plant")
    }

    @Test
    fun `FirstFrameLoader processFirstFrame uses v2 stack when Omni-SLM disabled`() = runTest {
        val (modelManager, _) = makeModelManager(ramMb = 2048)  // Ultra-low → Omni-SLM disabled
        val camera = FakeCameraProvider(openDelayMs = 10)
        val v2Stack = FakeV2StackProvider(cannedCaption = "v2 result")
        val handler = VisionFailureHandler(Dispatchers.Unconfined)

        val loader = FirstFrameParallelLoader(
            modelManager, v2Stack, camera, handler, Dispatchers.Unconfined,
        )

        loader.initializeParallel()

        var capturedResult: FirstFrameProcessResult? = null
        loader.processFirstFrame(RgbImage(100, 100, ByteArray(300))) { result ->
            capturedResult = result
        }

        assertThat(capturedResult).isNotNull()
        assertThat(capturedResult!!.source).isEqualTo(FrameProcessSource.V2_STACK)
        assertThat(capturedResult!!.caption).isEqualTo("v2 result")
    }

    @Test
    fun `FirstFrameLoader stats tracks cold and warm starts`() = runTest {
        val (modelManager, _) = makeModelManager()
        val camera = FakeCameraProvider(openDelayMs = 10)
        val v2Stack = FakeV2StackProvider()
        val handler = VisionFailureHandler(Dispatchers.Unconfined)

        val loader = FirstFrameParallelLoader(
            modelManager, v2Stack, camera, handler, Dispatchers.Unconfined,
        )

        loader.initializeParallel()
        val stats = loader.stats()
        assertThat(stats.totalStarts).isAtLeast(1)
    }

    @Test
    fun `FirstFrameLoader reset clears loading state`() = runTest {
        val (modelManager, _) = makeModelManager()
        val loader = FirstFrameParallelLoader(
            modelManager,
            FakeV2StackProvider(),
            FakeCameraProvider(),
            VisionFailureHandler(Dispatchers.Unconfined),
            Dispatchers.Unconfined,
        )
        loader.initializeParallel()
        loader.reset()
        assertThat(loader.loadingState.value).isEqualTo(FirstFrameLoadingState.IDLE)
    }

    @Test
    fun `FirstFrameLoader loadingState transitions through expected states`() = runTest {
        val (modelManager, _) = makeModelManager()
        val loader = FirstFrameParallelLoader(
            modelManager,
            FakeV2StackProvider(),
            FakeCameraProvider(openDelayMs = 10),
            VisionFailureHandler(Dispatchers.Unconfined),
            Dispatchers.Unconfined,
        )
        assertThat(loader.loadingState.value).isEqualTo(FirstFrameLoadingState.IDLE)
        loader.initializeParallel()
        assertThat(loader.loadingState.value).isAnyOf(
            FirstFrameLoadingState.READY_OMNI_SLM,
            FirstFrameLoadingState.READY_V2_FALLBACK,
        )
    }

    @Test
    fun `FirstFrameLoader v2FallbackRate tracks fallback percentage`() = runTest {
        val (modelManager, _) = makeModelManager(ramMb = 2048)  // Ultra-low → v2 fallback
        val loader = FirstFrameParallelLoader(
            modelManager,
            FakeV2StackProvider(),
            FakeCameraProvider(openDelayMs = 10),
            VisionFailureHandler(Dispatchers.Unconfined),
            Dispatchers.Unconfined,
        )
        loader.initializeParallel()
        val stats = loader.stats()
        assertThat(stats.totalV2Fallbacks).isAtLeast(1)
        assertThat(stats.v2FallbackRate).isGreaterThan(0f)
    }
}
