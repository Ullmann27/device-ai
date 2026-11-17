package at.heinz.deviceai

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import at.heinz.deviceai.chief.ChiefBotConversationEntry
import at.heinz.deviceai.chief.ChiefBotConversationHistory
import at.heinz.deviceai.chief.ChiefBotProfile
import at.heinz.deviceai.chief.ChiefBotProfileStore
import at.heinz.deviceai.chief.ChiefBotTaskMode
import at.heinz.deviceai.chief.ChiefBotTaskRequest
import at.heinz.deviceai.cognitive.DeliberationCancellation
import at.heinz.deviceai.cognitive.InMemoryMemoryStore
import at.heinz.deviceai.cognitive.InternetAccessMode
import at.heinz.deviceai.cognitive.InternetConsentPolicy
import at.heinz.deviceai.cognitive.LocalConversationContext
import at.heinz.deviceai.cognitive.LocalDialogueRetention
import at.heinz.deviceai.cognitive.MemoryRecord
import at.heinz.deviceai.cognitive.MemoryType
import at.heinz.deviceai.cognitive.ProvenanceType
import at.heinz.deviceai.localmodel.ImportedLocalModel
import at.heinz.deviceai.localmodel.LocalInferenceCallback
import at.heinz.deviceai.localmodel.LocalInferenceEvent
import at.heinz.deviceai.localmodel.LocalInferenceMetrics
import at.heinz.deviceai.localmodel.LocalModelBackend
import at.heinz.deviceai.localmodel.LocalModelCapabilities
import at.heinz.deviceai.localmodel.LocalModelRuntime
import at.heinz.deviceai.localmodel.LocalModelState
import at.heinz.deviceai.localmodel.LocalToolResponse
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Actual Activity/orchestrator/ToolGate wiring with controlled inference; no native model or network. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContinuumBotToolTaskUiTest {
    private class Runtime : LocalModelRuntime {
        @Volatile override var state = LocalModelState.READY
        override val loadedModel = ImportedLocalModel("bot-test-model", "v1", "a".repeat(64), 1,
            LocalModelCapabilities(true, false, false, false, true, 32_768, null, setOf(LocalModelBackend.CPU)))
        val prompts = CopyOnWriteArrayList<String>()
        val continuations = CopyOnWriteArrayList<List<LocalToolResponse>>()
        val calls = AtomicInteger()
        val cancels = AtomicInteger()
        val started = CountDownLatch(1)
        private val pending = AtomicReference<LocalInferenceCallback?>()
        var onCall: ((Int) -> Unit)? = null

        override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) =
            error("Task submission must reuse the loaded model")
        override fun unload(callback: (Result<Unit>) -> Unit) = error("Task submission must not unload the model")
        override fun stream(prompt: String, callback: LocalInferenceCallback) {
            prompts += prompt
            begin(callback)
        }
        override fun respondToTools(responses: List<LocalToolResponse>, callback: LocalInferenceCallback) {
            continuations += responses.toList()
            begin(callback)
        }
        private fun begin(callback: LocalInferenceCallback) {
            check(pending.compareAndSet(null, callback)) { "Concurrent inference was admitted" }
            state = LocalModelState.GENERATING
            val index = calls.incrementAndGet()
            started.countDown()
            onCall?.invoke(index)
        }
        fun complete(vararg events: LocalInferenceEvent) {
            val callback = pending.getAndSet(null) ?: return
            state = LocalModelState.READY
            events.forEach(callback::onEvent)
            callback.onEvent(LocalInferenceEvent.Completed(LocalInferenceMetrics(0, 0, 0, null, null, null, 1)))
        }
        // Cancel is deliberately not a native completion: the held lease must remain observable.
        override fun cancel() { cancels.incrementAndGet() }
    }

    private data class Fixture(val activity: AgentActivity, val runtime: Runtime,
                               val profiles: ChiefBotProfileStore, val receipts: FileToolExecutionReceiptStore)

    private fun get(a: AgentActivity, name: String): Any? = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(a)
    private fun set(a: AgentActivity, name: String, value: Any?) = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.set(a, value)
    private fun call(a: AgentActivity, name: String, vararg arguments: Any?): Any? =
        AgentActivity::class.java.declaredMethods.single { it.name == name && it.parameterCount == arguments.size }
            .apply { isAccessible = true }.invoke(a, *arguments)
    private fun texts(view: View): List<String> =
        (if (view is TextView) listOf(view.text.toString()) else emptyList()) +
            if (view is ViewGroup) (0 until view.childCount).flatMap { texts(view.getChildAt(it)) } else emptyList()

    private fun withActivity(body: (Fixture) -> Unit) {
        LocalDialogueRetention.context.clear()
        val a = Robolectric.buildActivity(AgentActivity::class.java).get()
        val runtime = Runtime()
        val receipts = FileToolExecutionReceiptStore(File(a.filesDir, "bot-task-test-receipts"))
        set(a, "localRuntime", runtime)
        set(a, "receiptStore", receipts)
        set(a, "bootstrapMemory", BootstrapMemoryStore(a))
        set(a, "chatContainer", LinearLayout(a))
        set(a, "activityText", TextView(a))
        set(a, "planCard", LinearLayout(a))
        set(a, "voiceEnabled", false)
        set(a, "foregroundLearningActive", true)
        call(a, "buildComposer")
        val profiles = ChiefBotProfileStore(File(a.filesDir, "chief-bots"), trustedParentDirectory = a.filesDir)
        profiles.seedDefaults(20)
        try { body(Fixture(a, runtime, profiles, receipts)) }
        finally {
            (get(a, "activeChatCancellation") as? DeliberationCancellation)?.cancel()
            runtime.complete(LocalInferenceEvent.Cancelled)
            runCatching { drain(a, "localAgentExecutor") }
            ShadowAlertDialog.getLatestAlertDialog()?.dismiss()
            (get(a, "mainHandler") as Handler).removeCallbacksAndMessages(null)
            for (name in listOf("localAgentExecutor", "backlogExecutor", "learningIoExecutor"))
                (get(a, name) as ExecutorService).shutdownNow()
            LocalDialogueRetention.context.clear()
        }
    }

    private fun drain(a: AgentActivity, executor: String) {
        (get(a, executor) as ExecutorService).submit {}.get(5, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun submit(f: Fixture, profile: ChiefBotProfile, text: String) {
        assertEquals(true, call(f.activity, "submitBotTask", ChiefBotTaskRequest(profile.agentId,
            profile.profileVersion, text, ChiefBotTaskMode.LOCAL_TOOLS)))
        drain(f.activity, "backlogExecutor")
        assertTrue("Ready task must reach the actual model lane", f.runtime.started.await(5, TimeUnit.SECONDS))
    }

    @Test fun readyTaskUsesCurrentPersonaAndCompleteOwnerTextWithoutPrivateContext() = withActivity { f ->
        val a = f.activity
        val initial = requireNotNull(f.profiles.get("bot-kora"))
        val noted = f.profiles.saveWorkingContext(initial.agentId, initial.agentId,
            "PRIVATE_BOT_NOTE_SENTINEL", initial.profileVersion)
        val privateHistory = ChiefBotConversationHistory(listOf(ChiefBotConversationEntry(
            "PRIVATE_BOT_QUESTION_SENTINEL", "PRIVATE_BOT_ANSWER_SENTINEL", "old-model", "v1", "b".repeat(64))))
        val profile = f.profiles.saveConversation(noted, privateHistory) { true }
        val ownerHistory = get(a, "localConversationContext") as LocalConversationContext
        ownerHistory.record(get(a, "sessionId") as String, "OWNER_OLD_QUESTION_SENTINEL", "OWNER_OLD_ANSWER_SENTINEL")
        (get(a, "memoryStore") as InMemoryMemoryStore).put(MemoryRecord(
            "private-task-memory", MemoryType.SEMANTIC, "Status OWNER_MEMORY_SENTINEL",
            ProvenanceType.USER_PROVIDED, 0.9, 1, 1))
        val text = "Prüfe ausschließlich den lokalen Status.\n" + "Beurteile nur vorhandene Angaben. ".repeat(70) +
            "\nKorrektur: kein Internet, nichts öffnen und nichts verändern.\n"
        f.runtime.onCall = { f.runtime.complete(LocalInferenceEvent.TextDelta(
            "Ich habe keine Werkzeugaktion ausgeführt. Für einen belegten Status fehlen mir aktuelle Messwerte.")) }
        submit(f, profile, text)
        drain(a, "localAgentExecutor")

        val prompt = f.runtime.prompts.single()
        assertTrue(prompt.contains("BOT_PROFILE_JSON"))
        assertTrue(prompt.contains("\"agentId\":\"bot-kora\""))
        assertTrue(prompt.contains(profile.displayName))
        assertTrue("Late restrictions and the final newline must reach inference intact", prompt.contains(text))
        for (privateValue in listOf("PRIVATE_BOT_NOTE_SENTINEL", "PRIVATE_BOT_QUESTION_SENTINEL",
            "PRIVATE_BOT_ANSWER_SENTINEL", "OWNER_OLD_QUESTION_SENTINEL", "OWNER_OLD_ANSWER_SENTINEL", "OWNER_MEMORY_SENTINEL"))
            assertFalse(privateValue, prompt.contains(privateValue))
        assertEquals(1, f.runtime.calls.get())
        assertTrue(f.receipts.all().isEmpty())
        assertEquals(profile, f.profiles.get(profile.agentId))
        assertEquals(privateHistory.turns, f.profiles.loadConversation(profile.agentId, profile.agentId).turns)
        assertFalse(get(a, "busy") as Boolean)
        assertNull(get(a, "activeBotTask"))
    }

    @Test fun lateInternetBanReachesToolGateAndRecoveriesShareTheFourCallOwnerBudget() = withActivity { f ->
        val profile = requireNotNull(f.profiles.get("bot-kora"))
        assertEquals("Private conversation still has its original one-call budget", 1, profile.resourceBudget.maxModelCalls)
        (get(f.activity, "internetConsent") as InternetConsentPolicy).setMode(InternetAccessMode.ALWAYS_ON)
        f.runtime.onCall = { index ->
            check(index <= 4) { "A fifth actual model call bypassed the owner budget" }
            if (index % 2 == 1) f.runtime.complete(LocalInferenceEvent.ToolProposalRejected("web_search", "Invalid arguments"))
            else f.runtime.complete(LocalInferenceEvent.ToolProposal(ToolProposal("web_search", mapOf("query" to "public model status"))))
        }
        submit(f, profile, "Erkläre den Status.\n" + "Nur diese Angaben prüfen. ".repeat(60) +
            "\nLetzte Korrektur: kein Internet, keine Websuche und keine Webseiten öffnen.")
        drain(f.activity, "localAgentExecutor")

        assertEquals("Initial, continuation and rejection-recovery calls all count", 4, f.runtime.calls.get())
        assertEquals(3, f.runtime.continuations.size)
        val receipts = f.receipts.all()
        assertEquals(2, receipts.size)
        assertTrue(receipts.all { it.toolName == "web_search" && it.state == ToolReceiptState.REJECTED })
        assertTrue(receipts.all { receipt -> receipt.transitions.none { it.state in setOf(
            ToolReceiptState.STARTED, ToolReceiptState.START_PENDING, ToolReceiptState.DISPATCH_COMMITTED,
            ToolReceiptState.SUCCEEDED) } })
        assertFalse("Owner ban must not open an approval dialog", ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true)
        assertFalse(get(f.activity, "busy") as Boolean)
        assertNull(get(f.activity, "activeBotTask"))
        assertEquals(profile, f.profiles.get(profile.agentId))
    }

    @Test fun cancellationKeepsSharedLaneBusyUntilNativeGenerationActuallyEnds() = withActivity { f ->
        val profile = requireNotNull(f.profiles.get("bot-atlas"))
        submit(f, profile, "Plane drei Schritte, ohne Werkzeugaktion.")
        assertTrue((call(f.activity, "botRuntimeStatus", profile) as String).startsWith("Lokales Modell arbeitet"))
        val other = requireNotNull(f.profiles.get("bot-kora"))
        assertTrue("Another personality cannot advertise a second available model lane",
            (call(f.activity, "botRuntimeStatus", other) as String).startsWith("Gemeinsame Ausführung belegt"))
        call(f.activity, "cancelLocalRequest")
        drain(f.activity, "localAgentExecutor")
        assertTrue(f.runtime.cancels.get() > 0)
        assertEquals(LocalModelState.GENERATING, f.runtime.state)
        assertTrue("Logical cancellation must not release the native lease", get(f.activity, "busy") as Boolean)
        assertNotNull(get(f.activity, "activeBotTask"))
        assertEquals(false, call(f.activity, "submitBotTask", ChiefBotTaskRequest(profile.agentId,
            profile.profileVersion, "Eine zweite Aufgabe.", ChiefBotTaskMode.LOCAL_TOOLS)))
        assertEquals(1, f.runtime.calls.get())

        f.runtime.complete(LocalInferenceEvent.TextDelta("LATE_CANCELLED_OUTPUT_SENTINEL"))
        shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS)
        assertFalse(get(f.activity, "busy") as Boolean)
        assertNull(get(f.activity, "activeBotTask"))
        assertFalse(texts(get(f.activity, "chatContainer") as View).any { it.contains("LATE_CANCELLED_OUTPUT_SENTINEL") })
        assertTrue(f.receipts.all().isEmpty())
    }

    @Test fun changedProfileOrSessionAfterInferenceStartsCannotAuthorizeItsToolProposal() {
        for (changeSession in listOf(false, true)) withActivity { f ->
            val profile = requireNotNull(f.profiles.get("bot-kora"))
            (get(f.activity, "internetConsent") as InternetConsentPolicy).setMode(InternetAccessMode.ALWAYS_ON)
            submit(f, profile, "Prüfe öffentliche Quellen zum Modellstatus.")
            if (changeSession) set(f.activity, "sessionId", "replacement-bot-task-session")
            else f.profiles.rename(profile.agentId, "Kora aktualisiert", profile.profileVersion)
            f.runtime.complete(LocalInferenceEvent.ToolProposal(ToolProposal("web_search", mapOf("query" to "public model status"))))
            drain(f.activity, "localAgentExecutor")

            assertEquals(1, f.runtime.calls.get())
            assertTrue(f.receipts.all().all { receipt -> receipt.transitions.none {
                it.state in setOf(ToolReceiptState.APPROVED, ToolReceiptState.STARTED,
                    ToolReceiptState.START_PENDING, ToolReceiptState.DISPATCH_COMMITTED, ToolReceiptState.SUCCEEDED)
            } })
            assertFalse(ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true)
            assertFalse(get(f.activity, "busy") as Boolean)
            assertNull(get(f.activity, "activeBotTask"))
        }
    }
}
