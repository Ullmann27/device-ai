package at.heinz.deviceai

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import at.heinz.deviceai.cognitive.*
import at.heinz.deviceai.localmodel.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/** Real Activity and context compiler. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class LocalContextBudgetUiTest {
    private class Runtime : LocalModelRuntime {
        override val state = LocalModelState.READY
        override val toolFreePromptUtf8Bytes = 8_000
        override val tokenAwarePromptUtf8Bytes = 3_000
        override val loadedModel = ImportedLocalModel("context-budget-fixture", "v1", "a".repeat(64), 1,
            LocalModelCapabilities(true, false, false, false, true, 8_192, null,
                setOf(LocalModelBackend.CPU)))
        val prompts = CopyOnWriteArrayList<String>()
        override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) =
            error("Unexpected model load")
        override fun unload(callback: (Result<Unit>) -> Unit) = error("Unexpected model unload")
        override fun stream(prompt: String, callback: LocalInferenceCallback) =
            error("Unexpected tool-bearing inference")
        override fun streamTextOnly(prompt: String, callback: LocalInferenceCallback) {
            prompts += prompt
            callback.onEvent(LocalInferenceEvent.Failed("CONTROLLED_PROMPT_CAPTURE_COMPLETE"))
        }
        override fun respondToTools(responses: List<LocalToolResponse>, callback: LocalInferenceCallback) =
            error("Unexpected tool continuation")
        override fun cancel() = Unit
    }

    private fun get(a: AgentActivity, name: String): Any? = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(a)
    private fun set(a: AgentActivity, name: String, value: Any?) = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.set(a, value)
    private fun call(a: AgentActivity, name: String, vararg arguments: Any?): Any? =
        AgentActivity::class.java.declaredMethods.single { it.name == name && it.parameterCount == arguments.size }
            .apply { isAccessible = true }.invoke(a, *arguments)
    private fun drain(a: AgentActivity) {
        (get(a, "localAgentExecutor") as ExecutorService).submit {}.get(10, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun text(view: View): String = when (view) {
        is TextView -> view.text.toString()
        is ViewGroup -> (0 until view.childCount).joinToString("\n") { text(view.getChildAt(it)) }
        else -> ""
    }
    private fun ownerState(memories: List<MemoryRecord>): ContinuumRuntimeState {
        val genome = CognitiveGenome("context-budget-owner")
        val identity = IdentityState.initial(genome).evolve { it.copy(chosenName = "Jarvis") }
        val instance = ContinuumInstanceId(identity.identityId)
        val root = ContinuumObjectId.of(ContinuumObjectType.STATE_MANIFEST, "budget-fixture".toByteArray())
        val seed = ContinuumSeed(instance, GovernanceVersion(1), StateFormatVersion(1), 1, 1,
            ModelAdapterAbiVersion(1), "owner:test", listOf(ComputeNodeId("fixture")), root, createdAtMillis = 1L)
        return ContinuumRuntimeState(seed, CognitiveArchive(identity, genome, memories),
            ContinuumStateManifest(1L, instance, identity.generation, "b".repeat(64), memories.size, 0, null, 1L),
            ContinuumSeedLoadSource.PRIMARY, ArchiveLoadSource.PRIMARY)
    }
    private fun optionalMemories(): List<MemoryRecord> {
        val now = System.currentTimeMillis()
        return List(8) { index -> MemoryRecord("optional-$index", MemoryType.SEMANTIC,
            "OPTIONAL_MEMORY_$index " + "Plan Varianten Erklärung Vergleich Gründe Grenzen Beispiele. ".repeat(18),
            ProvenanceType.UNVERIFIED, 0.8, now, now, tags = listOf("plan", "varianten"),
            privacyClass = PrivacyClass.PRIVATE) }
    }
    private fun withActivity(memories: List<MemoryRecord> = emptyList(),
                             body: (AgentActivity, Runtime) -> Unit) {
        LocalDialogueRetention.context.clear()
        RuntimeEnvironment.getApplication().getSharedPreferences("continuum-internet", Context.MODE_PRIVATE)
            .edit().putString("mode", "OFF").commit()
        val a = Robolectric.buildActivity(AgentActivity::class.java).get()
        val runtime = Runtime()
        set(a, "localRuntime", runtime)
        set(a, "continuumState", ownerState(memories))
        set(a, "receiptStore", FileToolExecutionReceiptStore(File(a.filesDir, "budget-test-receipts")))
        set(a, "bootstrapMemory", BootstrapMemoryStore(a))
        set(a, "chatContainer", LinearLayout(a))
        set(a, "activityText", TextView(a))
        set(a, "planCard", LinearLayout(a))
        set(a, "voiceEnabled", false)
        set(a, "foregroundLearningActive", true)
        call(a, "buildComposer")
        memories.forEach((get(a, "memoryStore") as InMemoryMemoryStore)::put)
        try { body(a, runtime) }
        finally {
            (get(a, "activeChatCancellation") as? DeliberationCancellation)?.cancel()
            runCatching { drain(a) }
            (get(a, "mainHandler") as Handler).removeCallbacksAndMessages(null)
            for (name in listOf("localAgentExecutor", "backlogExecutor", "learningIoExecutor", "documentIoExecutor"))
                (get(a, name) as ExecutorService).shutdownNow()
            LocalDialogueRetention.context.clear()
        }
    }
    private fun run(a: AgentActivity, runtime: Runtime, request: String): String {
        call(a, "runLocal", request, request, null,
            CognitiveTurnTrace(get(a, "sessionId") as String), null, null)
        drain(a)
        val visible = text(get(a, "chatContainer") as View)
        assertEquals(visible, 1, runtime.prompts.size)
        val prompt = runtime.prompts.single()
        assertTrue("Native UTF-8 budget exceeded", prompt.toByteArray(Charsets.UTF_8).size <=
            runtime.toolFreePromptUtf8Bytes)
        assertTrue("Real compiler must run", prompt.contains("## CONTINUUM IDENTITY / GOVERNANCE"))
        assertFalse(visible, visible.contains("MANDATORY_CONTEXT_EXCEEDS_BUDGET"))
        assertFalse(visible, visible.contains("LOCAL_FOLLOWUP_HISTORY"))
        return prompt
    }

    @Test fun optionalMemoriesCannotDisplaceCompleteOwnerTurns() =
        withActivity(optionalMemories()) { a, runtime ->
            val context = get(a, "localConversationContext") as LocalConversationContext
            val session = get(a, "sessionId") as String
            val owners = List(3) { index ->
                "ANGABE_${index}_ANFANG: Unser Plan hat die Variante $index. " +
                    "Die Variante berücksichtigt Gründe, Grenzen und nächste Schritte. ".repeat(12) +
                    "ANGABE_${index}_ENDE: Diese Variante ist nicht beschlossen."
            }
            owners.forEach { context.record(session, it, "Die Variante ist vorläufig beschrieben.") }
            val question = "Was bedeutet das für unseren Plan?"
            val prompt = run(a, runtime, question)
            assertTrue(prompt.contains(question))
            owners.forEach { assertTrue("Complete owner statement missing", prompt.contains(it)) }
            assertFalse("Optional group must yield space", (0..7).all { prompt.contains("OPTIONAL_MEMORY_$it") })
            assertEquals(8, (get(a, "memoryStore") as InMemoryMemoryStore).all().size)
        }

    @Test fun longNonModelReportLeavesRoomForTheActualFollowUp() = withActivity { a, runtime ->
        val context = get(a, "localConversationContext") as LocalConversationContext
        val owner = "Vergleiche die beiden Varianten unseres Plans."
        val report = "Lokaler Testbericht. " +
            "Unbestätigte Einzelbeobachtung mit ausführlicher Beschreibung. ".repeat(190) + "REPORT_TAIL"
        context.recordNonModel(get(a, "sessionId") as String, owner, report)
        val question = "Was bedeutet das für unseren Plan?"
        val prompt = run(a, runtime, question)
        assertTrue(prompt.contains(owner))
        assertTrue(prompt.contains(question))
        assertTrue("Missing observations stay explicit", prompt.contains("Auskunft fehlt"))
        assertFalse(prompt.contains("REPORT_TAIL"))
    }

    @Test fun longCurrentRequestKeepsItsFinalNegationWithinNativeBudget() =
        withActivity(optionalMemories()) { a, runtime ->
            val ending = "ABSCHLUSS: Die zweite Variante ist nicht beschlossen; behaupte keine Zustimmung."
            val request = "Erkläre ausführlich die Varianten unseres Plans. " +
                "Für jede Variante erläutere Gründe, Grenzen und konkrete Beispiele. ".repeat(55) + ending
            assertTrue(request.toByteArray(Charsets.UTF_8).size > 4_000)
            val prompt = run(a, runtime, request)
            assertTrue("Current request was shortened", prompt.contains(request))
            assertTrue(prompt.contains(ending))
        }
}
