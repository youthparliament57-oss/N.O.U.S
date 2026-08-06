// Copyright (c) 2026 Roshan. All rights reserved.

package com.roshan.persona.brain.skill.router

import com.google.common.truth.Truth.assertThat
import com.roshan.persona.brain.bus.BrainBus
import com.roshan.persona.brain.context.BrainBudget
import com.roshan.persona.brain.context.BrainContext
import com.roshan.persona.brain.context.DeviceContext
import com.roshan.persona.brain.context.NetworkType
import com.roshan.persona.brain.context.PrivacySettings
import com.roshan.persona.brain.context.ResponseLength
import com.roshan.persona.brain.context.SessionId
import com.roshan.persona.brain.context.ThermalStatus
import com.roshan.persona.brain.context.UserContext
import com.roshan.persona.brain.intent.CompoundExecutor
import com.roshan.persona.brain.intent.Intent
import com.roshan.persona.brain.persona.Persona
import com.roshan.persona.brain.persona.ResponseStyle
import com.roshan.persona.brain.persona.Tone
import com.roshan.persona.brain.persona.Verbosity
import com.roshan.persona.brain.persona.Vocabulary
import com.roshan.persona.brain.skill.BrainSkill
import com.roshan.persona.brain.skill.SkillCapability
import com.roshan.persona.brain.skill.SkillContext
import com.roshan.persona.brain.skill.SkillDispatchers
import com.roshan.persona.brain.skill.SkillOutput
import com.roshan.persona.brain.skill.executor.FakeSystemOperationExecutor
import com.roshan.persona.brain.skill.registry.SkillRegistry
import com.roshan.persona.common.CorrelationId
import com.roshan.persona.common.Result
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import kotlin.reflect.KClass

// AUTO_FIX_0095: [feature] SkillRouterTest verified

class SkillRouterTest {

    private val systemExecutor = FakeSystemOperationExecutor()
    private val permissionChecker = FakePermissionChecker()
    private val brainBus = BrainBus()
    private val dispatchers = SkillDispatchers(
        io = Dispatchers.Unconfined,
        default = Dispatchers.Unconfined,
        llm = Dispatchers.Unconfined,
        cv = Dispatchers.Unconfined,
        audio = Dispatchers.Unconfined,
    )

    private fun createSkill(
        id: String,
        supportedIntents: Set<KClass<out Intent>> = emptySet(),
        permissions: Set<String> = emptySet(),
        output: SkillOutput = SkillOutput.Success("test"),
    ): BrainSkill = object : BrainSkill {
        override val id = id
        override val displayName = "Test $id"
        override val description = "Test skill"
        override val capabilities = setOf(SkillCapability("test", "test"))
        override val requiredPermissions = permissions
        override val supportedIntents = supportedIntents
        override val timeoutMs = 5_000L

        override suspend fun execute(intent: Intent, context: SkillContext): Result<SkillOutput> =
            Result.Success(output, context.brainContext.correlationId, 0)
    }

    private fun createContext(): BrainContext = BrainContext(
        correlationId = CorrelationId.generate(),
        sessionId = SessionId("test"),
        timestamp = Instant.now(),
        timezone = TimeZone.getTimeZone("UTC"),
        activePersona = Persona(
            id = "test",
            displayName = "Test",
            systemPromptTemplate = "Test",
            skillPreferences = emptyMap(),
            responseStyle = ResponseStyle(Tone.WITTY, Verbosity.BALANCED, false, Vocabulary.SIMPLE),
        ),
        userContext = UserContext(
            userId = "user",
            displayName = null,
            preferredLanguage = Locale.ENGLISH,
            preferredResponseLength = ResponseLength.BALANCED,
            skillPreferences = emptyMap(),
            privacySettings = PrivacySettings(),
            monthlyLlmBudgetUsd = 5f,
            monthlyLlmSpentUsd = 0f,
        ),
        deviceContext = DeviceContext(
            thermalStatus = ThermalStatus.NONE,
            availableRamMb = 4096,
            batteryLevel = 80,
            isCharging = true,
            isOnline = true,
            networkType = NetworkType.WIFI,
            gpuAvailable = true,
            gpuVramMb = 2048,
        ),
        conversationHistory = emptyList(),
        pendingExpectation = null,
        ambientContext = null,
        budget = BrainBudget.DEFAULT,
    )

    private fun createRouter(skills: Set<BrainSkill>): SkillRouter {
        val registry = SkillRegistry(skills)
        return SkillRouter(registry, permissionChecker, systemExecutor, dispatchers, brainBus, FakeCredentialVault())
    }

    @Test
    fun `no matching skill returns Failure`() = runTest {
        val router = createRouter(emptySet())
        val context = createContext()

        val result = router.route(Intent.TorchOn, context)

        assertThat(result).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `matching skill executes and returns Success`() = runTest {
        val skill = createSkill(
            "torch",
            supportedIntents = setOf(Intent.TorchOn::class),
            output = SkillOutput.Success("Torch on."),
        )
        val router = createRouter(setOf(skill))
        val context = createContext()

        val result = router.route(Intent.TorchOn, context)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        val data = (result as Result.Success).data
        assertThat(data).isInstanceOf(SkillOutput.Success::class.java)
        assertThat((data as SkillOutput.Success).message).isEqualTo("Torch on.")
    }

    @Test
    fun `missing permission returns NeedsPermission not Failure`() = runTest {
        val skill = createSkill(
            "call",
            supportedIntents = setOf(Intent.Call::class),
            permissions = setOf("android.permission.CALL_PHONE"),
        )
        val router = createRouter(setOf(skill))
        val context = createContext()

        // Permission NOT granted
        val result = router.route(Intent.Call(contact = "mom"), context)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        val data = (result as Result.Success).data
        assertThat(data).isInstanceOf(SkillOutput.NeedsPermission::class.java)
        val needsPerm = data as SkillOutput.NeedsPermission
        assertThat(needsPerm.missingPermissions).contains("android.permission.CALL_PHONE")
    }

    @Test
    fun `granted permission allows skill execution`() = runTest {
        val skill = createSkill(
            "call",
            supportedIntents = setOf(Intent.Call::class),
            permissions = setOf("android.permission.CALL_PHONE"),
            output = SkillOutput.Success("Calling mom."),
        )
        permissionChecker.grantAll(setOf("android.permission.CALL_PHONE"))
        val router = createRouter(setOf(skill))
        val context = createContext()

        val result = router.route(Intent.Call(contact = "mom"), context)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        val data = (result as Result.Success).data
        assertThat(data).isInstanceOf(SkillOutput.Success::class.java)
    }

    @Test
    fun `compound SEQUENTIAL executes all intents in order`() = runTest {
        val torchSkill = createSkill(
            "torch",
            supportedIntents = setOf(Intent.TorchOn::class),
            output = SkillOutput.Success("Torch on."),
        )
        val muteSkill = createSkill(
            "mute",
            supportedIntents = setOf(Intent.Mute::class),
            output = SkillOutput.Success("Muted."),
        )
        val router = createRouter(setOf(torchSkill, muteSkill))
        val context = createContext()

        val compound = Intent.Compound(
            intents = listOf(Intent.TorchOn, Intent.Mute),
            executor = CompoundExecutor.SEQUENTIAL,
        )

        val result = router.route(compound, context)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        val data = (result as Result.Success).data
        assertThat(data).isInstanceOf(SkillOutput.Success::class.java)
        assertThat((data as SkillOutput.Success).message).contains("2 actions completed")
    }

    @Test
    fun `compound PARALLEL executes all intents`() = runTest {
        val torchSkill = createSkill(
            "torch",
            supportedIntents = setOf(Intent.TorchOn::class),
            output = SkillOutput.Success("Torch on."),
        )
        val muteSkill = createSkill(
            "mute",
            supportedIntents = setOf(Intent.Mute::class),
            output = SkillOutput.Success("Muted."),
        )
        val router = createRouter(setOf(torchSkill, muteSkill))
        val context = createContext()

        val compound = Intent.Compound(
            intents = listOf(Intent.TorchOn, Intent.Mute),
            executor = CompoundExecutor.PARALLEL,
        )

        val result = router.route(compound, context)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        val data = (result as Result.Success).data
        assertThat(data).isInstanceOf(SkillOutput.Success::class.java)
        assertThat((data as SkillOutput.Success).message).contains("in parallel")
    }

    @Test
    fun `compound with partial failure returns Partial`() = runTest {
        val torchSkill = createSkill(
            "torch",
            supportedIntents = setOf(Intent.TorchOn::class),
            output = SkillOutput.Success("Torch on."),
        )
        // Second skill has no matching intent → will fail
        val router = createRouter(setOf(torchSkill))
        val context = createContext()

        val compound = Intent.Compound(
            intents = listOf(Intent.TorchOn, Intent.Mute),  // Mute has no skill
            executor = CompoundExecutor.SEQUENTIAL,
        )

        val result = router.route(compound, context)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        val data = (result as Result.Success).data
        assertThat(data).isInstanceOf(SkillOutput.Partial::class.java)
        assertThat((data as SkillOutput.Partial).message).contains("1 of 2")
    }

    @Test
    fun `NeedsInput skill output is passed through`() = runTest {
        val skill = createSkill(
            "call",
            supportedIntents = setOf(Intent.Call::class),
            output = SkillOutput.NeedsInput("call", "Who?", com.roshan.persona.brain.skill.InputType.CONTACT),
        )
        permissionChecker.grantAll(setOf("android.permission.CALL_PHONE"))
        // Override skill permissions
        val realSkill = object : BrainSkill {
            override val id = "call"
            override val displayName = "Call"
            override val description = "Call"
            override val capabilities = setOf(SkillCapability("call", "call"))
            override val requiredPermissions = emptySet<String>()
            override val supportedIntents: Set<KClass<out Intent>> = setOf(Intent.Call::class)
            override suspend fun execute(intent: Intent, context: SkillContext): Result<SkillOutput> =
                Result.Success(
                    SkillOutput.NeedsInput("call", "Who do you want to call?", com.roshan.persona.brain.skill.InputType.CONTACT),
                    context.brainContext.correlationId, 0,
                )
        }
        val router = createRouter(setOf(realSkill))
        val context = createContext()

        val result = router.route(Intent.Call(contact = null), context)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        val data = (result as Result.Success).data
        assertThat(data).isInstanceOf(SkillOutput.NeedsInput::class.java)
        assertThat((data as SkillOutput.NeedsInput).prompt).isEqualTo("Who do you want to call?")
    }

    @Test
    fun `skill timeout returns Failure`() = runTest {
        val skill = object : BrainSkill {
            override val id = "slow"
            override val displayName = "Slow"
            override val description = "Slow"
            override val capabilities = setOf(SkillCapability("slow", "slow"))
            override val requiredPermissions = emptySet<String>()
            override val supportedIntents: Set<KClass<out Intent>> = setOf(Intent.TorchOn::class)
            override val timeoutMs = 100L  // 100ms timeout
            override suspend fun execute(intent: Intent, context: SkillContext): Result<SkillOutput> {
                kotlinx.coroutines.delay(500)  // 500ms — will timeout
                return Result.Success(SkillOutput.Success("done"), context.brainContext.correlationId, 0)
            }
        }
        val router = createRouter(setOf(skill))
        val context = createContext()

        val result = router.route(Intent.TorchOn, context)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        val data = (result as Result.Success).data
        assertThat(data).isInstanceOf(SkillOutput.Failure::class.java)
        val failure = data as SkillOutput.Failure
        assertThat(failure.error).isInstanceOf(com.roshan.persona.common.AppError.Skill.TimedOut::class.java)
    }
}

private class FakeCredentialVault : com.roshan.persona.brain.skill.CredentialVaultInterface {
    private val store = mutableMapOf<String, String>()
    override fun storeCredential(skillId: String, key: String, value: String) {
        store["$skillId:$key"] = value
    }
    override fun getCredential(skillId: String, key: String): String? = store["$skillId:$key"]
    override fun deleteCredential(skillId: String, key: String) {
        store.remove("$skillId:$key")
    }
    override fun deleteAllForSkill(skillId: String) {
        store.keys.filter { it.startsWith("$skillId:") }.forEach { store.remove(it) }
    }
}
