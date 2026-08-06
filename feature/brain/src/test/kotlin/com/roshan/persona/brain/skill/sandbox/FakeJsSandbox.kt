// Copyright (c) 2026 Roshan. All rights reserved.
// Proprietary license — see LICENSE file for details.

package com.roshan.persona.brain.skill.sandbox

import com.roshan.persona.common.AppError
import com.roshan.persona.common.CorrelationId
import com.roshan.persona.common.Result

/**
 * Test-only fake [JsSandbox] — returns canned results.
 *
 * Removed from `src/main` (Phase 1 stub-removal): production code uses
 * :feature:selfmodify's [com.roshan.persona.selfmodify.RhinoJsSandbox]
 * (dev/internal flavors only). Prod flavor provides NO `JsSandbox` binding
 * (no class in Brain injects it) — ADR 0008's "Play Store safe" requirement
 * is met by NOT bundling any JS engine in the prod APK.
 *
 * This fake lives in the test source set so [JsSandboxTest] can verify
 * interface contracts without touching a real JS engine.
 */
class FakeJsSandbox(
    private val available: Boolean = true,
    private val results: Map<String, String> = emptyMap(),
) : JsSandbox {

    private val executedScripts = mutableListOf<Pair<String, Map<String, Any>>>()

    override suspend fun execute(
        script: String,
        params: Map<String, Any>,
        limits: SandboxLimits,
    ): Result<String> {
        if (!available) {
            return Result.Failure(
                error = AppError.Skill.Crashed(
                    skillName = "js_sandbox",
                    cause = IllegalStateException("JS sandbox not available"),
                ),
                correlationId = CorrelationId.generate(),
                durationNanos = 0,
            )
        }

        executedScripts.add(script to params)

        // Return canned result if available, otherwise echo script
        val result = results[script] ?: "executed: ${script.take(50)}"
        return Result.Success(
            data = result,
            correlationId = CorrelationId.generate(),
            durationNanos = 0,
        )
    }

    override fun isAvailable(): Boolean = available

    /** Get executed scripts (for test assertions). */
    fun getExecutedScripts(): List<Pair<String, Map<String, Any>>> = executedScripts.toList()
}

/**
 * Test-only no-op [JsSandbox] — always returns failure with "not available" error.
 *
 * Removed from `src/main` (Phase 1 stub-removal): production builds do NOT
 * bundle a JS engine in the prod APK (ADR 0008). When the prod flavor needs
 * a `JsSandbox` binding (currently no Brain class injects it), the `:app`
 * module's prod source set should provide an explicit no-op implementation
 * — this test-only object is the reference shape for such a binding.
 */
object NoOpJsSandbox : JsSandbox {
    override suspend fun execute(
        script: String,
        params: Map<String, Any>,
        limits: SandboxLimits,
    ): Result<String> = Result.Failure(
        error = AppError.Skill.Crashed(
            skillName = "js_sandbox",
            cause = IllegalStateException("JS sandbox disabled in this build"),
        ),
        correlationId = CorrelationId.generate(),
        durationNanos = 0,
    )

    override fun isAvailable(): Boolean = false
}
