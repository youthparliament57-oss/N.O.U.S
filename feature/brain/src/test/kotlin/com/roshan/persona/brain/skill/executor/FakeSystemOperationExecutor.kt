// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.brain.skill.executor

import com.roshan.persona.brain.skill.SystemOperation
import com.roshan.persona.common.CorrelationId
import com.roshan.persona.common.Result

/**
 * Test-only fake [SystemOperationExecutor] — records all operations without executing.
 *
 * Removed from `src/main` (Phase 1 stub-removal): production code uses
 * :feature:system's `SystemOperationExecutorImpl` which wraps real Android
 * system services (CameraManager, AudioManager, WindowManager). This fake
 * lives in the test source set so unit tests can verify which operations
 * the SkillRouter attempted without actually firing them.
 */
class FakeSystemOperationExecutor : SystemOperationExecutor {

    private val executedOperations = mutableListOf<SystemOperation>()

    override suspend fun execute(operation: SystemOperation): Result<Unit> {
        executedOperations.add(operation)
        return Result.Success(Unit, CorrelationId.generate(), 0)
    }

    override suspend fun executeAll(operations: List<SystemOperation>): Result<List<Result<Unit>>> {
        val results = operations.map { execute(it) }
        return Result.Success(results, CorrelationId.generate(), 0)
    }

    /** Get all recorded operations (for test assertions). */
    fun getExecutedOperations(): List<SystemOperation> = executedOperations.toList()

    /** Clear recorded operations. */
    fun clear() {
        executedOperations.clear()
    }
}
