// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.system.di

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.skill.SystemOperation
import com.roshan.persona.common.AppError
import com.roshan.persona.common.CorrelationId
import com.roshan.persona.common.Result
import com.roshan.persona.database.dao.UndoActionDao
import com.roshan.persona.database.entity.UndoActionEntity
import com.roshan.persona.system.executor.AtomicBatchCoordinator
import com.roshan.persona.system.executor.ResourceCategory
import com.roshan.persona.system.executor.SystemOperationExecutorImpl
import com.roshan.persona.system.executor.SystemOperationExecutorImplTest.FakePermissionChecker
import com.roshan.persona.system.handler.NotificationHandler
import com.roshan.persona.system.handler.NotificationService
import com.roshan.persona.system.handler.ReversibleHandler
import com.roshan.persona.system.handler.SystemOperationHandler
import com.roshan.persona.system.handler.TorchHandler
import com.roshan.persona.system.undo.PersistentUndoStack
import com.roshan.persona.system.undo.SystemOperationSerializer
import com.roshan.persona.system.undo.ReversibleAction
import com.roshan.persona.system.util.ThermalGate
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AUTO_FIX_0133: [feature] SystemFacadeTest verified

class SystemFacadeTest {

    // ─── Fakes ─────────────────────────────────────────────────────────────

    private class FakeUndoActionDao : UndoActionDao {
        private val rows = mutableListOf<UndoActionEntity>()
        private val nextId = java.util.concurrent.atomic.AtomicInteger(1)

        override suspend fun insert(entity: UndoActionEntity): Long {
            val id = nextId.getAndIncrement().toLong()
            rows.add(entity.copy(id = id))
            return id
        }
        override suspend fun getLatest(): UndoActionEntity? = rows.maxByOrNull { it.id }
        override suspend fun getRecent(limit: Int): List<UndoActionEntity> =
            rows.sortedByDescending { it.id }.take(limit)
        override suspend fun deleteById(id: Long) { rows.removeAll { it.id == id } }
        override suspend fun trim(keepCount: Int) {
            if (rows.size <= keepCount) return
            val toKeep = rows.sortedByDescending { it.id }.take(keepCount).map { it.id }.toSet()
            rows.removeAll { it.id !in toKeep }
        }
        override suspend fun clear() = rows.clear()
        override suspend fun count(): Int = rows.size
    }

    private class FakeCameraService : com.roshan.persona.system.handler.CameraService {
        var torchEnabled = false
            private set
        override fun hasTorch(): Boolean = true
        override fun getTorchCameraId(): String? = "0"
        override fun setTorchMode(cameraId: String, enabled: Boolean) { torchEnabled = enabled }
        override fun setTorchBrightnessLevel(cameraId: String, level: Float) {}
        override fun supportsBrightnessControl(): Boolean = false
        override fun isTorchEnabled(cameraId: String): Boolean = torchEnabled
    }

    private class FakeNotificationService(
        private val hasPermission: Boolean = true,
        private val postResult: Boolean = true,
    ) : NotificationService {
        var postCount = 0
            private set
        override fun ensureChannel(channelId: String, name: String, description: String, importance: Int) {}
        override fun channelExists(channelId: String): Boolean = true
        override fun postNotification(id: Int, channelId: String, title: String, text: String, smallIconResId: Int): Boolean {
            postCount++
            return postResult
        }
        override fun cancelNotification(id: Int) {}
        override fun cancelAll() {}
        override fun hasNotificationPermission(): Boolean = hasPermission
    }

    private fun makeFacade(
        cameraService: FakeCameraService = FakeCameraService(),
        undoDao: UndoActionDao = FakeUndoActionDao(),
        notificationService: FakeNotificationService = FakeNotificationService(),
    ): Triple<SystemFacade, FakeCameraService, PersistentUndoStack> {
        val torchHandler = TorchHandler(cameraService)
        val handlers: Map<Class<out SystemOperation>, SystemOperationHandler<*>> = mapOf(
            SystemOperation.SetTorch::class.java to torchHandler,
        )
        val preStateStore = SystemOperationExecutorImpl.InMemoryPreStateStore()
        val executor = SystemOperationExecutorImpl(
            handlers = handlers,
            permissionChecker = FakePermissionChecker(),
            thermalGate = ThermalGate(null),
            preStateStore = preStateStore,
        )
        val undoStack = PersistentUndoStack(undoDao, SystemOperationSerializer)
        val batchCoordinator = AtomicBatchCoordinator(executor, handlers)
        val notificationHandler = NotificationHandler(notificationService)
        val facade = SystemFacade(executor, undoStack, batchCoordinator, notificationHandler)
        return Triple(facade, cameraService, undoStack)
    }

    // ─── Tests ─────────────────────────────────────────────────────────────

    @Test
    fun `executeCommand routes SetTorch to TorchHandler`() = runTest {
        val (facade, camera, _) = makeFacade()
        val result = facade.executeCommand(SystemOperation.SetTorch(true))
        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat(camera.torchEnabled).isTrue()
    }

    @Test
    fun `executeCommand pushes reversible action to undo stack`() = runTest {
        val (facade, _, _) = makeFacade()
        facade.executeCommand(SystemOperation.SetTorch(true))
        assertThat(facade.getUndoCount()).isEqualTo(1)
    }

    @Test
    fun `undoLastAction restores previous state`() = runTest {
        val (facade, camera, _) = makeFacade()
        // Set torch on (was off by default in FakeCameraService).
        facade.executeCommand(SystemOperation.SetTorch(true))
        assertThat(camera.torchEnabled).isTrue()
        assertThat(facade.getUndoCount()).isEqualTo(1)
        // Undo — should turn torch back off.
        val undone = facade.undoLastAction()
        assertThat(undone).isTrue()
        assertThat(camera.torchEnabled).isFalse()
        assertThat(facade.getUndoCount()).isEqualTo(0)
    }

    @Test
    fun `undoLastAction returns false when stack is empty`() = runTest {
        val (facade, _, _) = makeFacade()
        assertThat(facade.undoLastAction()).isFalse()
    }

    @Test
    fun `getActionHistory returns recent actions`() = runTest {
        val (facade, _, _) = makeFacade()
        facade.executeCommand(SystemOperation.SetTorch(true))
        facade.executeCommand(SystemOperation.SetTorch(false))
        val history = facade.getActionHistory()
        assertThat(history).hasSize(2)
        // Most recent first.
        assertThat(history[0].description).contains("off")
        assertThat(history[1].description).contains("on")
    }

    @Test
    fun `clearActionHistory empties the stack`() = runTest {
        val (facade, _, _) = makeFacade()
        facade.executeCommand(SystemOperation.SetTorch(true))
        assertThat(facade.getUndoCount()).isEqualTo(1)
        facade.clearActionHistory()
        assertThat(facade.getUndoCount()).isEqualTo(0)
    }

    @Test
    fun `executeBatch routes to AtomicBatchCoordinator`() = runTest {
        val (facade, _, _) = makeFacade()
        val result = facade.executeBatch(listOf(
            SystemOperation.SetTorch(true),
            SystemOperation.SetTorch(false),
        ))
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val success = result as Result.Success
        assertThat(success.data).hasSize(2)
    }

    @Test
    fun `executeBatch with empty list returns Success with empty results`() = runTest {
        val (facade, _, _) = makeFacade()
        val result = facade.executeBatch(emptyList())
        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).data).isEmpty()
    }

    @Test
    fun `postNotification delegates to NotificationHandler`() = runTest {
        val (facade, _, _) = makeFacade()
        val result = facade.postNotification(
            id = 1, channelId = "test", channelName = "Test", title = "T", text = "B",
        )
        assertThat(result).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `cancelNotification delegates to NotificationHandler`() = runTest {
        val (facade, _, _) = makeFacade()
        // Should not throw.
        facade.cancelNotification(42)
    }

    @Test
    fun `cancelAllNotifications delegates to NotificationHandler`() = runTest {
        val (facade, _, _) = makeFacade()
        facade.cancelAllNotifications()
    }

    @Test
    fun `executeCommand with no handler returns Failure`() = runTest {
        // Handler registry only has SetTorch — SetVolume should fail.
        val (facade, _, _) = makeFacade()
        val result = facade.executeCommand(
            com.roshan.persona.brain.skill.SystemOperation.SetVolume(50)
        )
        assertThat(result).isInstanceOf(Result.Failure::class.java)
    }
}
