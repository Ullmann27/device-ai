package at.heinz.deviceai

import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.TextView
import at.heinz.deviceai.chief.ChiefBotProfileStore
import at.heinz.deviceai.chief.ChiefBotTaskMode
import at.heinz.deviceai.chief.ChiefBotTaskRequest
import at.heinz.deviceai.cognitive.*
import at.heinz.deviceai.localmodel.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/** Controlled Activity prompt boundary. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class OwnerFallbackIdentityUiTest {
    private val task = "Bitte mache einen Vergleich mehrerer Konzepte mit Widerspruch und Bedingung. " +
        "Beschreibe die verschiedenen Möglichkeiten. ".repeat(20)

    private class Runtime : LocalModelRuntime {
        override val state = LocalModelState.READY
        override val loadedModel = ImportedLocalModel("identity-test-model", "v7", "b".repeat(64), 1,
            LocalModelCapabilities(true, false, false, false, true, 32_768, null, setOf(LocalModelBackend.CPU)))
        val prompts = CopyOnWriteArrayList<String>()
        val advisoryPrompts = CopyOnWriteArrayList<String>()
        var onAdvisory: () -> Unit = {}
        override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) =
            error("No model load in fixture")
        override fun unload(callback: (Result<Unit>) -> Unit) = error("No model unload in fixture")
        override fun streamThinking(prompt: String, callback: LocalInferenceCallback) {
            advisoryPrompts += prompt
            onAdvisory()
            callback.onEvent(LocalInferenceEvent.Failed("CONTROLLED_OPTIONAL_ADVISORY_UNAVAILABLE"))
        }
        override fun stream(prompt: String, callback: LocalInferenceCallback) {
            prompts += prompt
            callback.onEvent(LocalInferenceEvent.Failed("CONTROLLED_PROMPT_CAPTURE_COMPLETE"))
        }
        override fun respondToTools(responses: List<LocalToolResponse>, callback: LocalInferenceCallback) =
            error("No tool continuation in fixture")
        override fun cancel() = Unit
    }

    private fun get(a: AgentActivity, name: String): Any? = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(a)
    private fun set(a: AgentActivity, name: String, value: Any?) = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.set(a, value)
    private fun call(a: AgentActivity, name: String, vararg arguments: Any?): Any? =
        AgentActivity::class.java.declaredMethods.single { it.name == name && it.parameterCount == arguments.size }
            .apply { isAccessible = true }.invoke(a, *arguments)
    private fun drain(a: AgentActivity, name: String = "localAgentExecutor") {
        (get(a, name) as ExecutorService).submit {}.get(10, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun withActivity(body: (AgentActivity, Runtime, ChiefBotProfileStore) -> Unit) {
        LocalDialogueRetention.context.clear()
        val a = Robolectric.buildActivity(AgentActivity::class.java).get()
        val runtime = Runtime()
        set(a, "localRuntime", runtime)
        set(a, "receiptStore", FileToolExecutionReceiptStore(File(a.filesDir, "identity-test-receipts")))
        set(a, "bootstrapMemory", BootstrapMemoryStore(a))
        set(a, "chatContainer", LinearLayout(a))
        set(a, "activityText", TextView(a))
        set(a, "planCard", LinearLayout(a))
        set(a, "voiceEnabled", false)
        set(a, "foregroundLearningActive", true)
        call(a, "buildComposer")
        val profiles = ChiefBotProfileStore(File(a.filesDir, "chief-bots"), trustedParentDirectory = a.filesDir)
        profiles.seedDefaults(20)
        try { body(a, runtime, profiles) }
        finally {
            (get(a, "activeChatCancellation") as? DeliberationCancellation)?.cancel()
            runCatching { drain(a) }
            ShadowAlertDialog.getLatestAlertDialog()?.dismiss()
            (get(a, "mainHandler") as Handler).removeCallbacksAndMessages(null)
            for (name in listOf("localAgentExecutor", "backlogExecutor", "learningIoExecutor", "documentIoExecutor"))
                (get(a, name) as ExecutorService).shutdownNow()
            LocalDialogueRetention.context.clear()
        }
    }

    private fun ownerRun(a: AgentActivity) {
        call(a, "runLocal", task, task, null, CognitiveTurnTrace(get(a, "sessionId") as String), null, null)
        drain(a)
    }

    private fun ownerState(): ContinuumRuntimeState {
        val genome = CognitiveGenome("fallback-owner-fixture")
        val identity = IdentityState.initial(genome).evolve { it.copy(chosenName = "Jarvis",
            communicationProfile = mapOf("style" to "kurz", "tone" to "freundlich", "language" to "de-at",
                "custom" to "UNTRUSTED_PROFILE_PAYLOAD")) }
        val instance = ContinuumInstanceId(identity.identityId)
        val root = ContinuumObjectId.of(ContinuumObjectType.STATE_MANIFEST, "fixture".toByteArray())
        val seed = ContinuumSeed(instance, GovernanceVersion(1), StateFormatVersion(1), 1, 1,
            ModelAdapterAbiVersion(1), "owner:test", listOf(ComputeNodeId("fixture")), root, createdAtMillis = 1L)
        val memory = MemoryRecord("private-fixture", MemoryType.SEMANTIC, "PRIVATE_ARCHIVE_SENTINEL",
            ProvenanceType.USER_PROVIDED, 0.9, 1L, 1L, privacyClass = PrivacyClass.PRIVATE)
        return ContinuumRuntimeState(seed, CognitiveArchive(identity, genome, listOf(memory)),
            ContinuumStateManifest(1L, instance, identity.generation, "a".repeat(64), 1, 0, null, 1L),
            ContinuumSeedLoadSource.PRIMARY, ArchiveLoadSource.PRIMARY)
    }

    @Test fun fallbackUsesIdentityAvailableAtPromptAssemblyWithoutImportingArchiveMemory() =
        withActivity { a, runtime, _ ->
            assertNull(get(a, "continuumState"))
            val available = ownerState()
            // Fixture transition, not device recovery.
            runtime.onAdvisory = { set(a, "continuumState", available) }
            ownerRun(a)
            assertEquals(1, runtime.advisoryPrompts.size)
            val prompt = runtime.prompts.single()
            assertTrue(prompt.contains("Aktueller Auftrag:\n$task"))
            assertFalse("Fallback must really be selected", prompt.contains("## CONTINUUM IDENTITY / GOVERNANCE"))
            assertSame(available, get(a, "continuumState"))
            assertEquals("assistant_name=\"Jarvis\"",
                prompt.lineSequence().single { it.startsWith("assistant_name=") })
            assertTrue(prompt.contains("communication_style=kurz"))
            assertTrue(prompt.contains("communication_tone=freundlich"))
            assertTrue(prompt.contains("communication_language=de-at"))
            assertTrue(prompt.contains("active_local_model=identity-test-model@v7"))
            assertTrue(prompt.contains("active_model_sha256=${runtime.loadedModel.sha256}"))
            assertFalse(prompt.contains("PRIVATE_ARCHIVE_SENTINEL"))
            assertFalse(prompt.contains("UNTRUSTED_PROFILE_PAYLOAD"))
            assertFalse(prompt.contains("assistant_name=\"Continuum\""))
        }

    @Test fun unavailableIdentityUsesDefaultWithoutInventingAProfile() = withActivity { a, runtime, _ ->
        ownerRun(a)
        val prompt = runtime.prompts.single()
        assertTrue(prompt.contains("Aktueller Auftrag:\n$task"))
        assertTrue(prompt.contains("assistant_name=\"Continuum\""))
        assertTrue(prompt.contains("active_local_model=identity-test-model@v7"))
        assertTrue(prompt.contains("active_model_sha256=${runtime.loadedModel.sha256}"))
        assertFalse(prompt.contains("communication_style="))
    }

    @Test fun assignedBotFallbackDoesNotBorrowOwnerNameOrProfile() = withActivity { a, runtime, profiles ->
        set(a, "continuumState", ownerState())
        val profile = requireNotNull(profiles.get("bot-kora"))
        assertEquals(true, call(a, "submitBotTask", ChiefBotTaskRequest(profile.agentId,
            profile.profileVersion, task, ChiefBotTaskMode.LOCAL_TOOLS)))
        drain(a, "backlogExecutor")
        drain(a)
        val prompt = runtime.prompts.single()
        assertTrue(prompt.contains("BOT_PROFILE_JSON"))
        assertTrue(prompt.contains(profile.displayName))
        assertFalse(prompt.contains("assistant_name="))
        assertFalse(prompt.contains("communication_style="))
        assertFalse(prompt.contains("PRIVATE_ARCHIVE_SENTINEL"))
        assertFalse(prompt.contains("UNTRUSTED_PROFILE_PAYLOAD"))
        assertTrue(runtime.advisoryPrompts.isEmpty())
    }

    @Test fun realCompilerBudgetFailureDoesNotStartAnIdentityFallbackInference() =
        withActivity { a, runtime, _ ->
            set(a, "continuumState", ownerState())
            set(a, "contextCompiler\$delegate", lazyOf(AiContextCompiler(AiContextPolicy(maxBytes = 1_024))))
            ownerRun(a)
            assertTrue(runtime.advisoryPrompts.isEmpty())
            assertTrue(runtime.prompts.isEmpty())
            assertEquals("MANDATORY_CONTEXT_EXCEEDS_BUDGET", get(a, "localRuntimeError"))
            assertFalse(get(a, "busy") as Boolean)
        }
}
