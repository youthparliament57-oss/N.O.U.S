// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.system.executor

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.skill.SystemOperation
import com.roshan.persona.brain.skill.router.PermissionChecker
import com.roshan.persona.common.AppError
import com.roshan.persona.common.CorrelationId
import com.roshan.persona.common.Result
import com.roshan.persona.system.handler.ReversibleHandler
import com.roshan.persona.system.handler.SystemOperationHandler
import com.roshan.persona.system.util.ThermalGate
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import android.os.PowerManager

// AUTO_FIX_0135: [feature] SystemOperationExecutorImplTest verified

class SystemOperationExecutorImplTest {

    // ─── Fakes ─────────────────────────────────────────────────────────────

    /** Fake handler that records execute calls + supports pre-state capture. */
    private class FakeReversibleHandler(
        override val id: String = "fake",
        override val displayName: String = "Fake",
        override val requiredPermissions: Set<String> = emptySet(),
        override val resourceCategory: ResourceCategory = ResourceCategory.SETTINGS,
        private val available: Boolean = true,
        private val executeResult: Result<Unit> = Result.Success(Unit, CorrelationId.generate(), 0),
        private val targetStateReached: Boolean = false,
    ) : ReversibleHandler<SystemOperation.SetVolume> {

        val executeCalls = mutableListOf<SystemOperation.SetVolume>()
        val preStateCaptures = mutableListOf<SystemOperation.SetVolume>()
        val preStateRestores = mutableListOf<Pair<SystemOperation.SetVolume, Any?>>()

        override fun isAvailable(): Boolean = available

        override suspend fun execute(operation: SystemOperation.SetVolume): Result<Unit> {
            executeCalls.add(operation)
            return executeResult
        }

        override suspend fun capturePreState(operation: SystemOperation.SetVolume): Any? {
            preStateCaptures.add(operation)
            return 50  // previous volume level
        }

        override suspend fun restorePreState(operation: SystemOperation.SetVolume, preState: Any?) {
            preStateRestores.add(operation to preState)
        }

        override suspend fun getCurrentState(): Any? = 50

        override suspend fun isAlreadyInTargetState(operation: SystemOperation.SetVolume): Boolean {
            return targetStateReached
        }

        override fun describeOperation(operation: SystemOperation.SetVolume): String {
            return "Volume set to ${operation.level}"
        }
    }

    /** Fake handler that always fails. */
    private class FailingHandler(
        private val error: AppError = AppError.Hardware.Unknown("fake"),
    ) : SystemOperationHandler<SystemOperation.SetTorch> {
        override val id = "failing"
        override val displayName = "Failing"
        override val requiredPermissions: Set<String> = emptySet()
        override val resourceCategory = ResourceCategory.CAMERA
        override fun isAvailable() = true
        override suspend fun execute(operation: SystemOperation.SetTorch): Result<Unit> =
            Result.Failure(error, CorrelationId.generate(), 0)
        override suspend fun capturePreState(operation: SystemOperation.SetTorch) = null
        override suspend fun restorePreState(operation: SystemOperation.SetTorch, preState: Any?) = Unit
        override suspend fun getCurrentState(): Any? = null
        override suspend fun isAlreadyInTargetState(operation: SystemOperation.SetTorch) = false
    }

    /** Fake permission checker. */
    private class FakePermissionChecker(private val granted: Set<String> = emptySet()) : PermissionChecker {
        override fun isGranted(permission: String): Boolean = permission in granted
    }

    private fun makeExecutor(
        handlers: Map<Class<out SystemOperation>, SystemOperationHandler<*>> = emptyMap(),
        permissionChecker: PermissionChecker = FakePermissionChecker(),
        thermalGate: ThermalGate = ThermalGate(null),
        preStateStore: PreStateStore = InMemoryPreStateStore(),
    ): SystemOperationExecutorImpl {
        return SystemOperationExecutorImpl(
            handlers = handlers,
            permissionChecker = permissionChecker,
            thermalGate = thermalGate,
            preStateStore = preStateStore,
        )
    }

    // ─── Tests ─────────────────────────────────────────────────────────────

    @Test
    fun `execute returns Failure when no handler registered for operation`() = runTest {
        val executor = makeExecutor(handlers = emptyMap())
        val result = executor.execute(SystemOperation.SetTorch(true))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        val failure = result as Result.Failure
        assertThat(failure.error).isInstanceOf(AppError.Hardware.Unsupported::class.java)
    }

    @Test
    fun `execute routes operation to matching handler`() = runTest {
        val handler = FakeReversibleHandler()
        val executor = makeExecutor(
            handlers = mapOf(SystemOperation.SetVolume::class.java to handler),
        )
        executor.execute(SystemOperation.SetVolume(80))
        assertThat(handler.executeCalls).hasSize(1)
        assertThat(handler.executeCalls[0].level).isEqualTo(80)
    }

    @Test
    fun `execute returns Failure when handler isAvailable returns false`() = runTest {
        val handler = FakeReversibleHandler(available = false)
        val executor = makeExecutor(
            handlers = mapOf(SystemOperation.SetVolume::class.java to handler),
        )
        val result = executor.execute(SystemOperation.SetVolume(80))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        val failure = result as Result.Failure
        assertThat(failure.error).isInstanceOf(AppError.Hardware.Unavailable::class.java)
    }

    @Test
    fun `execute returns Failure when required permission is missing`() = runTest {
        val handler = FakeReversibleHandler(
            requiredPermissions = setOf("android.permission.CAMERA"),
        )
        val executor = makeExecutor(
            handlers = mapOf(SystemOperation.SetVolume::class.java to handler),
            permissionChecker = FakePermissionChecker(granted = emptySet()),
        )
        val result = executor.execute(SystemOperation.SetVolume(80))
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        val failure = result as Result.Failure
        assertThat(failure.error).isInstanceOf(AppError.Permission.Denied::class.java)
    }

    @Test
    fun `execute proceeds when all required permissions are granted`() = runTest {
        val handler = FakeReversibleHandler(
            requiredPermissions = setOf("android.permission.CAMERA"),
        )
        val executor = makeExecutor(
            handlers = mapOf(SystemOperation.SetVolume::class.java to handler),
            permissionChecker = FakePermissionChecker(granted = setOf("android.permission.CAMERA")),
        )
        val result = executor.execute(SystemOperation.SetVolume(80))
        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat(handler.executeCalls).hasSize(1)
    }

    @Test
    fun `execute skips hardware call when isAlreadyInTargetState returns true`() = runTest {
        val handler = FakeReversibleHandler(targetStateReached = true)
        val executor = makeExecutor(
            handlers = mapOf(SystemOperation.SetVolume::class.java to handler),
        )
        val result = executor.execute(SystemOperation.SetVolume(80))
        // Should return Success with idempotent_skip warning.
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val success = result as Result.Success
        assertThat(success.warnings).contains("idempotent_skip")
        // Handler's execute should NOT have been called.
        assertThat(handler.executeCalls).isEmpty()
    }

    @Test
    fun `execute captures pre-state before execution for reversible handlers`() = runTest {
        val handler = FakeReversibleHandler()
        val preStateStore = InMemoryPreStateStore()
        val executor = makeExecutor(
            handlers = mapOf(SystemOperation.SetVolume::class.java to handler),
            preStateStore = preStateStore,
        )
        val op = SystemOperation.SetVolume(80)
        executor.execute(op)
        assertThat(handler.preStateCaptures).hasSize(1)
        // Pre-state should be stored for undo.
        val stored = preStateStore.get(op)
        assertThat(stored).isNotNull()
        assertThat(stored!!.preState).isEqualTo(50)
    }

    @Test
    fun `execute does not store pre-state when handler is not reversible`() = runTest {
        val handler = FailingHandler()  // not a ReversibleHandler
        val preStateStore = InMemoryPreStateStore()
        val executor = makeExecutor(
            handlers = mapOf(SystemOperation.SetTorch::class.java to handler),
            preStateStore = preStateStore,
        )
        val op = SystemOperation.SetTorch(true)
        executor.execute(op)
        // FailingHandler returns Failure — pre-state not stored even if it were reversible.
        assertThat(preStateStore.get(op)).isNull()
    }

    @Test
    fun `execute returns Failure when handler throws exception`() = runTest {
        // Handler that throws on execute.
        val crashingHandler = object : SystemOperationHandler<SystemOperation.SetTorch> {
            override val id = "crash"
            override val displayName = "Crash"
            override val requiredPermissions: Set<String> = emptySet()
            override val resourceCategory = ResourceCategory.CAMERA
            override fun isAvailable() = true
            override suspend fun execute(operation: SystemOperation.SetTorch): Result<Unit> {
                error("Hardware exploded")
            }
            override suspend fun capturePreState(operation: SystemOperation.SetTorch) = null
            override suspend fun restorePreState(operation: SystemOperation.SetTorch, preState: Any?) = Unit
            override suspend fun getCurrentState(): Any? = null
            override suspend fun isAlreadyInTargetState(operation: SystemOperation.SetTorch) = false
        }
        val executor = makeExecutor(
            handlers = mapOf(SystemOperation.SetTorch::class.java to crashingHandler),
        )
        val result = executor.execute(SystemOperation.SetTorch(true))
        // Should be caught by HardwareExceptionMapper → Failure, not propagated.
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        val failure = result as Result.Failure
        assertThat(failure.error).isInstanceOf(AppError.Hardware.Unknown::class.java)
    }

    @Test
    fun `execute unwraps Composite and dispatches each child operation`() = runTest {
        val volumeHandler = FakeReversibleHandler()
        val executor = makeExecutor(
            handlers = mapOf(SystemOperation.SetVolume::class.java to volumeHandler),
        )
        val composite = SystemOperation.Composite(listOf(
            SystemOperation.SetVolume(50),
            SystemOperation.SetVolume(60),
        ))
        executor.execute(composite)
        // Both child operations should have been dispatched.
        assertThat(volumeHandler.executeCalls).hasSize(2)
    }

    @Test
    fun `executeAll returns Success with per-op results when all succeed`() = runTest {
        val handler = FakeReversibleHandler()
        val executor = makeExecutor(
            handlers = mapOf(SystemOperation.SetVolume::class.java to handler),
        )
        val result = executor.executeAll(listOf(
            SystemOperation.SetVolume(50),
            SystemOperation.SetVolume(60),
        ))
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val success = result as Result.Success
        assertThat(success.data).hasSize(2)
        assertThat(success.data.all { it is Result.Success }).isTrue()
    }

    @Test
    fun `executeAll stops on first failure (v1 behavior)`() = runTest {
        val failingHandler = FailingHandler()
        val executor = makeExecutor(
            handlers = mapOf(SystemOperation.SetTorch::class.java to failingHandler),
        )
        val result = executor.executeAll(listOf(
            SystemOperation.SetTorch(true),   // fails
            SystemOperation.SetTorch(false),  // should not be attempted
        ))
        assertThat(result).isInstanceOf(Result.Success::class.java)
        val success = result as Result.Success
        // Only 1 result (stopped after first failure).
        assertThat(success.data).hasSize(1)
        assertThat(success.data[0]).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `InMemoryPreStateStore round-trips store-get-remove`() = runTest {
        val store = InMemoryPreStateStore()
        val op = SystemOperation.SetVolume(80)
        store.store(op, 50, "volume")
        assertThat(store.get(op)?.preState).isEqualTo(50)
        store.remove(op)
        assertThat(store.get(op)).isNull()
    }

    @Test
    fun `InMemoryPreStateStore clear empties the store`() = runTest {
        val store = InMemoryPreStateStore()
        store.store(SystemOperation.SetVolume(80), 50, "volume")
        store.store(SystemOperation.SetVolume(60), 40, "volume")
        store.clear()
        assertThat(store.get(SystemOperation.SetVolume(80))).isNull()
        assertThat(store.get(SystemOperation.SetVolume(60))).isNull()
    }

    @Test
    fun `ThermalGate allows operation when powerManager is null`() {
        val gate = ThermalGate(null)
        // Should allow when API unavailable.
        assertThat(gate.getThermalHeadroom()).isNull()
    }

    @Test
    fun `ThermalGate thermalFailure builds correct AppError`() {
        val gate = ThermalGate(null)
        val failure = gate.thermalFailure(2.8f)
        assertThat(failure.error).isInstanceOf(AppError.Hardware.ThermalThrottled::class.java)
        val err = failure.error as AppError.Hardware.ThermalThrottled
        assertThat(err.headroom).isWithin(0.001f).of(2.8f)
    }

    @Test
    fun `HARDWARE_HEAVY_OPERATION_TYPES contains torch brightness tethering nfc`() {
        assertThat(HARDWARE_HEAVY_OPERATION_TYPES).contains("SetTorch")
        assertThat(HARDWARE_HEAVY_OPERATION_TYPES).contains("SetDisplayBrightness")
        assertThat(HARDWARE_HEAVY_OPERATION_TYPES).contains("SetTethering")
        assertThat(HARDWARE_HEAVY_OPERATION_TYPES).contains("SetNfc")
    }
}
