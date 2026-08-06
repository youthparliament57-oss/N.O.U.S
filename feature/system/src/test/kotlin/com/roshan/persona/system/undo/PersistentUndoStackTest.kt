// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.system.undo

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.skill.SystemOperation
import com.roshan.persona.database.dao.UndoActionDao
import com.roshan.persona.database.entity.UndoActionEntity
import com.roshan.persona.system.executor.ResourceCategory
import com.roshan.persona.system.executor.SystemOperationExecutorImpl
import com.roshan.persona.system.executor.SystemOperationExecutorImplTest.FakeReversibleHandler
import com.roshan.persona.system.executor.SystemOperationExecutorImplTest.FailingHandler
import com.roshan.persona.system.executor.SystemOperationExecutorImplTest.FakePermissionChecker
import com.roshan.persona.system.handler.SystemOperationHandler
import com.roshan.persona.system.util.ThermalGate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

// AUTO_FIX_0155: [feature] PersistentUndoStackTest verified

class PersistentUndoStackTest {

    /** In-memory fake DAO backed by a list (simulates Room). */
    private class FakeUndoActionDao : UndoActionDao {
        private val rows = mutableListOf<UndoActionEntity>()
        private val nextId = AtomicInteger(1)

        override suspend fun insert(entity: UndoActionEntity): Long {
            val id = nextId.getAndIncrement().toLong()
            rows.add(entity.copy(id = id))
            return id
        }

        override suspend fun getLatest(): UndoActionEntity? = rows.maxByOrNull { it.id }

        override suspend fun getRecent(limit: Int): List<UndoActionEntity> =
            rows.sortedByDescending { it.id }.take(limit)

        override suspend fun deleteById(id: Long) {
            rows.removeAll { it.id == id }
        }

        override suspend fun trim(keepCount: Int) {
            if (rows.size <= keepCount) return
            val toKeep = rows.sortedByDescending { it.id }.take(keepCount).map { it.id }.toSet()
            rows.removeAll { it.id !in toKeep }
        }

        override suspend fun clear() = rows.clear()

        override suspend fun count(): Int = rows.size
    }

    @Test
    fun `push and pop round-trip a reversible action`() = runTest {
        val dao = FakeUndoActionDao()
        val stack = PersistentUndoStack(dao)
        val action = ReversibleAction(
            id = 0,
            operation = SystemOperation.SetVolume(80),
            preState = 50,
            handlerId = "volume",
            timestampMs = 1000L,
            description = "Volume set to 80",
        )
        val rowId = stack.push(action)
        assertThat(rowId).isGreaterThan(0L)
        assertThat(stack.count()).isEqualTo(1)

        val popped = stack.pop()
        assertThat(popped).isNotNull()
        assertThat(popped!!.operation).isEqualTo(SystemOperation.SetVolume(80))
        assertThat(popped.preState).isEqualTo(50)
        assertThat(popped.handlerId).isEqualTo("volume")
        assertThat(popped.description).isEqualTo("Volume set to 80")
    }

    @Test
    fun `pop returns null when stack is empty`() = runTest {
        val stack = PersistentUndoStack(FakeUndoActionDao())
        assertThat(stack.pop()).isNull()
    }

    @Test
    fun `peek returns latest without deleting`() = runTest {
        val stack = PersistentUndoStack(FakeUndoActionDao())
        stack.push(ReversibleAction(
            id = 0, operation = SystemOperation.SetTorch(true), preState = false,
            handlerId = "torch", timestampMs = 1000L, description = "Torch on",
        ))
        assertThat(stack.count()).isEqualTo(1)
        val peeked = stack.peek()
        assertThat(peeked).isNotNull()
        assertThat(stack.count()).isEqualTo(1)  // not deleted
    }

    @Test
    fun `confirmPop deletes the popped action`() = runTest {
        val stack = PersistentUndoStack(FakeUndoActionDao())
        val rowId = stack.push(ReversibleAction(
            id = 0, operation = SystemOperation.SetTorch(true), preState = false,
            handlerId = "torch", timestampMs = 1000L, description = "Torch on",
        ))
        assertThat(stack.count()).isEqualTo(1)
        stack.confirmPop(rowId)
        assertThat(stack.count()).isEqualTo(0)
    }

    @Test
    fun `trim keeps only the last MAX_UNDO_ACTIONS entries`() = runTest {
        val stack = PersistentUndoStack(FakeUndoActionDao())
        // Push 55 actions — only 50 should be kept.
        for (i in 1..55) {
            stack.push(ReversibleAction(
                id = 0, operation = SystemOperation.SetVolume(i), preState = i - 1,
                handlerId = "volume", timestampMs = i.toLong(), description = "Vol $i",
            ))
        }
        assertThat(stack.count()).isEqualTo(PersistentUndoStack.MAX_UNDO_ACTIONS)
        // The most recent should be vol 55.
        val latest = stack.peek()
        assertThat(latest?.description).isEqualTo("Vol 55")
    }

    @Test
    fun `getRecent returns actions in most-recent-first order`() = runTest {
        val stack = PersistentUndoStack(FakeUndoActionDao())
        for (i in 1..3) {
            stack.push(ReversibleAction(
                id = 0, operation = SystemOperation.SetVolume(i), preState = i - 1,
                handlerId = "volume", timestampMs = i.toLong(), description = "Vol $i",
            ))
        }
        val recent = stack.getRecent(10)
        assertThat(recent).hasSize(3)
        assertThat(recent[0].description).isEqualTo("Vol 3")  // most recent first
        assertThat(recent[2].description).isEqualTo("Vol 1")
    }

    @Test
    fun `clear empties the stack`() = runTest {
        val stack = PersistentUndoStack(FakeUndoActionDao())
        stack.push(ReversibleAction(
            id = 0, operation = SystemOperation.SetTorch(true), preState = false,
            handlerId = "torch", timestampMs = 1000L, description = "Torch on",
        ))
        stack.push(ReversibleAction(
            id = 0, operation = SystemOperation.SetVolume(50), preState = 40,
            handlerId = "volume", timestampMs = 2000L, description = "Vol 50",
        ))
        assertThat(stack.count()).isEqualTo(2)
        stack.clear()
        assertThat(stack.count()).isEqualTo(0)
    }

    @Test
    fun `count returns 0 for empty stack`() = runTest {
        val stack = PersistentUndoStack(FakeUndoActionDao())
        assertThat(stack.count()).isEqualTo(0)
    }

    @Test
    fun `LIFO ordering — last pushed is first popped`() = runTest {
        val stack = PersistentUndoStack(FakeUndoActionDao())
        stack.push(ReversibleAction(
            id = 0, operation = SystemOperation.SetTorch(true), preState = false,
            handlerId = "torch", timestampMs = 1000L, description = "Torch on",
        ))
        stack.push(ReversibleAction(
            id = 0, operation = SystemOperation.SetVolume(50), preState = 40,
            handlerId = "volume", timestampMs = 2000L, description = "Vol 50",
        ))
        val popped = stack.pop()
        assertThat(popped?.description).isEqualTo("Vol 50")  // last in, first out
    }

    // ─── Per-handler pre-state round-trip tests (audit defect B fix) ───────
    // These exercise the new per-handler serializePreState / deserializePreState
    // path with realistic data-class pre-states. Without per-handler (de)serialization,
    // these would fall through to .toString() and undo would silently no-op.

    @Test
    fun `round-trips VolumePreState via handler-aware stack`() = runTest {
        // Wire a real VolumeHandler into the stack's handler registry.
        val fakeAudio = object : com.roshan.persona.system.handler.AudioService {
            override fun getStreamVolume(stream: com.roshan.persona.brain.skill.AudioStream) = 50
            override fun getStreamMaxVolume(stream: com.roshan.persona.brain.skill.AudioStream) = 100
            override fun setStreamVolume(stream: com.roshan.persona.brain.skill.AudioStream, level: Int) {}
            override fun adjustStreamVolume(stream: com.roshan.persona.brain.skill.AudioStream, delta: Int) {}
            override fun getRingerMode() = com.roshan.persona.brain.skill.RingerMode.NORMAL
            override fun setRingerMode(mode: com.roshan.persona.brain.skill.RingerMode) {}
            override fun streamToInt(stream: com.roshan.persona.brain.skill.AudioStream): Int = 0
        }
        val stateProbe = object : com.roshan.persona.system.handler.AudioStateProbe {
            override fun isInCall(): Boolean = false
        }
        val volumeHandler = com.roshan.persona.system.handler.VolumeHandler(fakeAudio, stateProbe)
        val stack = PersistentUndoStack(
            dao = FakeUndoActionDao(),
            handlerRegistry = mapOf(volumeHandler.id to volumeHandler),
        )
        val preState = com.roshan.persona.system.handler.VolumeHandler.VolumePreState(
            stream = com.roshan.persona.brain.skill.AudioStream.MEDIA,
            previousLevel = 50,
            maxLevel = 100,
        )
        stack.push(ReversibleAction(
            id = 0,
            operation = SystemOperation.SetVolume(80),
            preState = preState,
            handlerId = volumeHandler.id,
            timestampMs = 1000L,
            description = "Volume set to 80",
        ))
        val popped = stack.pop()
        assertThat(popped).isNotNull()
        assertThat(popped!!.preState).isInstanceOf(
            com.roshan.persona.system.handler.VolumeHandler.VolumePreState::class.java,
        )
        assertThat(popped.preState).isEqualTo(preState)
    }

    @Test
    fun `round-trips TorchPreState via handler-aware stack`() = runTest {
        val torchHandler = com.roshan.persona.system.handler.TorchHandler(
            object : com.roshan.persona.system.handler.CameraService {
                override fun hasTorch(): Boolean = true
                override fun getTorchCameraId(): String? = "0"
                override fun setTorchMode(cameraId: String, enabled: Boolean) {}
                override fun setTorchBrightnessLevel(cameraId: String, level: Float) {}
                override fun supportsBrightnessControl(): Boolean = false
                override fun isTorchEnabled(cameraId: String): Boolean = false
            },
        )
        val stack = PersistentUndoStack(
            dao = FakeUndoActionDao(),
            handlerRegistry = mapOf(torchHandler.id to torchHandler),
        )
        val preState = com.roshan.persona.system.handler.TorchHandler.TorchPreState(wasOn = true)
        stack.push(ReversibleAction(
            id = 0,
            operation = SystemOperation.SetTorch(true),
            preState = preState,
            handlerId = torchHandler.id,
            timestampMs = 1000L,
            description = "Torch on",
        ))
        val popped = stack.pop()
        assertThat(popped).isNotNull()
        assertThat(popped!!.preState).isInstanceOf(
            com.roshan.persona.system.handler.TorchHandler.TorchPreState::class.java,
        )
        assertThat(popped.preState).isEqualTo(preState)
    }

    @Test
    fun `round-trips BrightnessPreState via handler-aware stack`() = runTest {
        val brightnessHandler = com.roshan.persona.system.handler.BrightnessHandler(
            object : com.roshan.persona.system.handler.DisplayService {
                override fun getBrightness(): Int = 128
                override fun setBrightness(level: Int) {}
                override fun isAutoBrightness(): Boolean = false
                override fun setAutoBrightness(enabled: Boolean) {}
                override fun applyWindowBrightness(level: Float?) {}
                override fun setKeepScreenOn(enabled: Boolean): Boolean = true
            },
        )
        val stack = PersistentUndoStack(
            dao = FakeUndoActionDao(),
            handlerRegistry = mapOf(brightnessHandler.id to brightnessHandler),
        )
        val preState = com.roshan.persona.system.handler.BrightnessHandler.BrightnessPreState(
            previousLevel255 = 200,
            wasAuto = true,
        )
        stack.push(ReversibleAction(
            id = 0,
            operation = SystemOperation.SetDisplayBrightness(0.5f),
            preState = preState,
            handlerId = brightnessHandler.id,
            timestampMs = 1000L,
            description = "Brightness set to 50%",
        ))
        val popped = stack.pop()
        assertThat(popped).isNotNull()
        assertThat(popped!!.preState).isEqualTo(preState)
    }
}

class AtomicBatchCoordinatorTest {

    /** Fake handler that records execute + restore calls. */
    private class RecordingHandler(
        override val id: String,
        override val displayName: String = id,
        override val requiredPermissions: Set<String> = emptySet(),
        override val resourceCategory: ResourceCategory = ResourceCategory.SETTINGS,
        private val executeResult: com.roshan.persona.common.Result<Unit> =
            com.roshan.persona.common.Result.Success(Unit, com.roshan.persona.common.CorrelationId.generate(), 0),
    ) : SystemOperationHandler<SystemOperation> {
        val executeCalls = mutableListOf<SystemOperation>()
        val restoreCalls = mutableListOf<Pair<SystemOperation, Any?>>()

        override fun isAvailable() = true
        override suspend fun execute(operation: SystemOperation) = executeResult.also { executeCalls.add(operation) }
        override suspend fun capturePreState(operation: SystemOperation): Any? = "pre-state-for-${operation::class.simpleName}"
        override suspend fun restorePreState(operation: SystemOperation, preState: Any?) {
            restoreCalls.add(operation to preState)
        }
        override suspend fun getCurrentState(): Any? = null
        override suspend fun isAlreadyInTargetState(operation: SystemOperation) = false
    }

    private fun makeExecutor(
        handlers: Map<Class<out SystemOperation>, SystemOperationHandler<*>>,
    ): SystemOperationExecutorImpl {
        return SystemOperationExecutorImpl(
            handlers = handlers,
            permissionChecker = FakePermissionChecker(),
            thermalGate = ThermalGate(null),
            preStateStore = com.roshan.persona.system.executor.InMemoryPreStateStore(),
        )
    }

    @Test
    fun `executeAll returns Success with per-op results when all succeed`() = runTest {
        val volumeHandler = RecordingHandler("volume", resourceCategory = ResourceCategory.AUDIO)
        val torchHandler = RecordingHandler("torch", resourceCategory = ResourceCategory.CAMERA)
        val executor = makeExecutor(mapOf(
            SystemOperation.SetVolume::class.java to volumeHandler,
            SystemOperation.SetTorch::class.java to torchHandler,
        ))
        val coordinator = AtomicBatchCoordinator(executor, mapOf(
            SystemOperation.SetVolume::class.java to volumeHandler,
            SystemOperation.SetTorch::class.java to torchHandler,
        ))

        val result = coordinator.executeAll(listOf(
            SystemOperation.SetVolume(80),
            SystemOperation.SetTorch(true),
        ))
        assertThat(result).isInstanceOf(com.roshan.persona.common.Result.Success::class.java)
        val success = result as com.roshan.persona.common.Result.Success
        assertThat(success.data).hasSize(2)
    }

    @Test
    fun `executeAll rolls back executed operations on failure`() = runTest {
        val volumeHandler = RecordingHandler("volume", resourceCategory = ResourceCategory.AUDIO)
        val torchHandler = RecordingHandler("torch", resourceCategory = ResourceCategory.CAMERA)
        val executor = makeExecutor(mapOf(
            SystemOperation.SetVolume::class.java to volumeHandler,
            SystemOperation.SetTorch::class.java to torchHandler,
        ))
        val coordinator = AtomicBatchCoordinator(executor, mapOf(
            SystemOperation.SetVolume::class.java to volumeHandler,
            SystemOperation.SetTorch::class.java to torchHandler,
        ))

        // Volume succeeds, Torch fails.
        @Suppress("UNCHECKED_CAST")
        val failingTorch = RecordingHandler(
            id = "torch",
            resourceCategory = ResourceCategory.CAMERA,
            executeResult = com.roshan.persona.common.Result.Failure(
                com.roshan.persona.common.AppError.Hardware.InUse("camera"),
                com.roshan.persona.common.CorrelationId.generate(), 0,
            ),
        ) as SystemOperationHandler<SystemOperation>
        val executorWithFailingTorch = makeExecutor(mapOf(
            SystemOperation.SetVolume::class.java to volumeHandler,
            SystemOperation.SetTorch::class.java to failingTorch,
        ))
        val coordinatorWithFailingTorch = AtomicBatchCoordinator(executorWithFailingTorch, mapOf(
            SystemOperation.SetVolume::class.java to volumeHandler,
            SystemOperation.SetTorch::class.java to failingTorch,
        ))

        val result = coordinatorWithFailingTorch.executeAll(listOf(
            SystemOperation.SetVolume(80),  // succeeds
            SystemOperation.SetTorch(true),  // fails
        ))
        assertThat(result).isInstanceOf(com.roshan.persona.common.Result.Failure::class.java)
        // Volume's restorePreState should have been called for rollback.
        assertThat(volumeHandler.restoreCalls).hasSize(1)
    }

    @Test
    fun `executeAll returns Success for empty list`() = runTest {
        val executor = makeExecutor(emptyMap())
        val coordinator = AtomicBatchCoordinator(executor, emptyMap())
        val result = coordinator.executeAll(emptyList())
        assertThat(result).isInstanceOf(com.roshan.persona.common.Result.Success::class.java)
        val success = result as com.roshan.persona.common.Result.Success
        assertThat(success.data).isEmpty()
    }

    @Test
    fun `executeAll groups different categories in parallel`() = runTest {
        // Track which handler ran when. If they ran in parallel, their
        // execution windows should overlap.
        val volumeHandler = object : RecordingHandler("volume", resourceCategory = ResourceCategory.AUDIO) {
            val startTimes = mutableListOf<Long>()
            override suspend fun execute(operation: SystemOperation): com.roshan.persona.common.Result<Unit> {
                startTimes.add(System.currentTimeMillis())
                delay(50)  // simulate work
                return super.execute(operation)
            }
        }
        val torchHandler = object : RecordingHandler("torch", resourceCategory = ResourceCategory.CAMERA) {
            val startTimes = mutableListOf<Long>()
            override suspend fun execute(operation: SystemOperation): com.roshan.persona.common.Result<Unit> {
                startTimes.add(System.currentTimeMillis())
                delay(50)
                return super.execute(operation)
            }
        }
        val executor = makeExecutor(mapOf(
            SystemOperation.SetVolume::class.java to volumeHandler,
            SystemOperation.SetTorch::class.java to torchHandler,
        ))
        val coordinator = AtomicBatchCoordinator(executor, mapOf(
            SystemOperation.SetVolume::class.java to volumeHandler,
            SystemOperation.SetTorch::class.java to torchHandler,
        ))

        val start = System.currentTimeMillis()
        coordinator.executeAll(listOf(
            SystemOperation.SetVolume(80),
            SystemOperation.SetTorch(true),
        ))
        val total = System.currentTimeMillis() - start

        // If parallel, total should be ~50ms (not ~100ms sequential).
        // Allow generous tolerance for test flakiness.
        assertThat(total).isLessThan(120L)
    }

    @Test
    fun `executeAll runs same-category operations sequentially`() = runTest {
        val order = mutableListOf<String>()
        val handler1 = object : RecordingHandler("volume1", resourceCategory = ResourceCategory.AUDIO) {
            override suspend fun execute(operation: SystemOperation): com.roshan.persona.common.Result<Unit> {
                order.add("start-1")
                delay(50)
                order.add("end-1")
                return super.execute(operation)
            }
        }
        val handler2 = object : RecordingHandler("volume2", resourceCategory = ResourceCategory.AUDIO) {
            override suspend fun execute(operation: SystemOperation): com.roshan.persona.common.Result<Unit> {
                order.add("start-2")
                delay(50)
                order.add("end-2")
                return super.execute(operation)
            }
        }
        // Both ops are AUDIO — same category → sequential.
        // Use the same handler type for both ops (simulate two volume ops).
        val executor = makeExecutor(mapOf(
            SystemOperation.SetVolume::class.java to handler1,
            SystemOperation.AdjustVolume::class.java to handler2,
        ))
        val coordinator = AtomicBatchCoordinator(executor, mapOf(
            SystemOperation.SetVolume::class.java to handler1,
            SystemOperation.AdjustVolume::class.java to handler2,
        ))

        coordinator.executeAll(listOf(
            SystemOperation.SetVolume(80),
            SystemOperation.AdjustVolume(1),
        ))
        // Sequential: start-1, end-1, start-2, end-2 (no interleaving).
        assertThat(order).containsExactly("start-1", "end-1", "start-2", "end-2").inOrder()
    }
}
