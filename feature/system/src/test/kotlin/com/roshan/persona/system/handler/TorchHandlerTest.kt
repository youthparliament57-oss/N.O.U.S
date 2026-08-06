// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.system.handler

import android.hardware.camera2.CameraAccessException
import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.skill.SystemOperation
import com.roshan.persona.common.AppError
import com.roshan.persona.common.Result
import com.roshan.persona.system.executor.ResourceCategory
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0145: [feature] TorchHandlerTest verified

class TorchHandlerTest {

    /** Fake camera service for testing. */
    private class FakeCameraService(
        private val hasTorch: Boolean = true,
        private val cameraId: String? = "0",
        private val supportsBrightness: Boolean = false,
        private val throwOnSetTorch: Throwable? = null,
        private val throwOnBrightness: Throwable? = null,
    ) : CameraService {
        var torchEnabled = false
            private set
        var lastBrightnessLevel: Float? = null
            private set
        val setTorchCalls = mutableListOf<Pair<String, Boolean>>()
            private set

        override fun hasTorch(): Boolean = hasTorch
        override fun getTorchCameraId(): String? = cameraId
        override fun setTorchMode(cameraId: String, enabled: Boolean) {
            throwOnSetTorch?.let { throw it }
            torchEnabled = enabled
            setTorchCalls.add(cameraId to enabled)
        }
        override fun setTorchBrightnessLevel(cameraId: String, level: Float) {
            throwOnBrightness?.let { throw it }
            lastBrightnessLevel = level
        }
        override fun supportsBrightnessControl(): Boolean = supportsBrightness
        override fun isTorchEnabled(cameraId: String): Boolean = torchEnabled
    }

    private fun makeHandler(camera: FakeCameraService) = TorchHandler(camera)

    // ─── Tests ─────────────────────────────────────────────────────────────

    @Test
    fun `isAvailable returns true when device has torch`() {
        val handler = makeHandler(FakeCameraService(hasTorch = true))
        assertThat(handler.isAvailable()).isTrue()
    }

    @Test
    fun `isAvailable returns false when device has no torch`() {
        val handler = makeHandler(FakeCameraService(hasTorch = false, cameraId = null))
        assertThat(handler.isAvailable()).isFalse()
    }

    @Test
    fun `execute returns Failure Unavailable when device has no torch`() = runTest {
        val handler = makeHandler(FakeCameraService(hasTorch = false, cameraId = null))
        val result = handler.execute(SystemOperation.SetTorch(true))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        val failure = result as Result.Failure
        assertThat(failure.error).isInstanceOf(AppError.Hardware.Unavailable::class.java)
    }

    @Test
    fun `execute turns torch on when operation is on=true`() = runTest {
        val camera = FakeCameraService()
        val handler = makeHandler(camera)
        handler.execute(SystemOperation.SetTorch(true))
        assertThat(camera.torchEnabled).isTrue()
        assertThat(camera.setTorchCalls).hasSize(1)
        assertThat(camera.setTorchCalls[0]).isEqualTo("0" to true)
    }

    @Test
    fun `execute turns torch off when operation is on=false`() = runTest {
        val camera = FakeCameraService().apply { torchEnabled = true }
        val handler = makeHandler(camera)
        handler.execute(SystemOperation.SetTorch(false))
        assertThat(camera.torchEnabled).isFalse()
    }

    @Test
    fun `execute maps CameraAccessException CAMERA_IN_USE to Hardware InUse`() = runTest {
        val camera = FakeCameraService(
            throwOnSetTorch = CameraAccessException(CameraAccessException.CAMERA_IN_USE),
        )
        val handler = makeHandler(camera)
        val result = handler.execute(SystemOperation.SetTorch(true))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        val failure = result as Result.Failure
        assertThat(failure.error).isInstanceOf(AppError.Hardware.InUse::class.java)
        assertThat((failure.error as AppError.Hardware.InUse).resource).isEqualTo("torch")
    }

    @Test
    fun `execute maps CameraAccessException CAMERA_DISCONNECTED to Hardware Unavailable`() = runTest {
        val camera = FakeCameraService(
            throwOnSetTorch = CameraAccessException(CameraAccessException.CAMERA_DISCONNECTED),
        )
        val handler = makeHandler(camera)
        val result = handler.execute(SystemOperation.SetTorch(true))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat((result as Result.Failure).error).isInstanceOf(AppError.Hardware.Unavailable::class.java)
    }

    @Test
    fun `execute sets brightness level when supported and brightness < 1`() = runTest {
        val camera = FakeCameraService(supportsBrightness = true)
        val handler = makeHandler(camera)
        handler.execute(SystemOperation.SetTorch(on = true, brightness = 0.5f))
        assertThat(camera.lastBrightnessLevel).isWithin(0.001f).of(0.5f)
    }

    @Test
    fun `execute does not set brightness when unsupported`() = runTest {
        val camera = FakeCameraService(supportsBrightness = false)
        val handler = makeHandler(camera)
        handler.execute(SystemOperation.SetTorch(on = true, brightness = 0.5f))
        assertThat(camera.lastBrightnessLevel).isNull()
    }

    @Test
    fun `execute does not set brightness when torch is off`() = runTest {
        val camera = FakeCameraService(supportsBrightness = true)
        val handler = makeHandler(camera)
        handler.execute(SystemOperation.SetTorch(on = false, brightness = 0.5f))
        assertThat(camera.lastBrightnessLevel).isNull()
    }

    @Test
    fun `isAlreadyInTargetState returns true when torch already matches`() = runTest {
        val camera = FakeCameraService().apply { torchEnabled = true }
        val handler = makeHandler(camera)
        assertThat(handler.isAlreadyInTargetState(SystemOperation.SetTorch(true))).isTrue()
    }

    @Test
    fun `isAlreadyInTargetState returns false when torch differs`() = runTest {
        val camera = FakeCameraService().apply { torchEnabled = false }
        val handler = makeHandler(camera)
        assertThat(handler.isAlreadyInTargetState(SystemOperation.SetTorch(true))).isFalse()
    }

    @Test
    fun `capturePreState returns previous on-off state`() = runTest {
        val camera = FakeCameraService().apply { torchEnabled = true }
        val handler = makeHandler(camera)
        val preState = handler.capturePreState(SystemOperation.SetTorch(false))
        assertThat(preState).isInstanceOf(TorchHandler.TorchPreState::class.java)
        assertThat((preState as TorchHandler.TorchPreState).wasOn).isTrue()
    }

    @Test
    fun `restorePreState turns torch back to previous state`() = runTest {
        val camera = FakeCameraService().apply { torchEnabled = false }
        val handler = makeHandler(camera)
        handler.restorePreState(SystemOperation.SetTorch(false), TorchHandler.TorchPreState(wasOn = true))
        assertThat(camera.torchEnabled).isTrue()
    }

    @Test
    fun `describeOperation returns human-readable description`() {
        val handler = makeHandler(FakeCameraService())
        assertThat(handler.describeOperation(SystemOperation.SetTorch(true))).isEqualTo("Torch turned on")
        assertThat(handler.describeOperation(SystemOperation.SetTorch(false))).isEqualTo("Torch turned off")
    }

    @Test
    fun `resourceCategory is CAMERA`() {
        assertThat(makeHandler(FakeCameraService()).resourceCategory).isEqualTo(ResourceCategory.CAMERA)
    }

    @Test
    fun `requiredPermissions contains CAMERA`() {
        assertThat(makeHandler(FakeCameraService()).requiredPermissions)
            .contains(android.Manifest.permission.CAMERA)
    }
}
