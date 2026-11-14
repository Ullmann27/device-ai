package at.heinz.deviceai

import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import at.heinz.deviceai.cognitive.CognitiveTurnTrace
import at.heinz.deviceai.cognitive.DeliberationCancellation
import at.heinz.deviceai.cognitive.LocalDialogueRetention
import at.heinz.deviceai.cognitive.LocalGroundedRecall
import at.heinz.deviceai.learning.ContinuumLearningCore
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Real deletion UI and orchestration. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class OwnerFactRevocationUiTest {
    private class Runtime : LocalModelRuntime {
        @Volatile override var state = LocalModelState.READY
        override val loadedModel = ImportedLocalModel("revocation-fixture", "v1", "c".repeat(64), 1,
            LocalModelCapabilities(true, false, false, false, true, 32_768, null,
                setOf(LocalModelBackend.CPU)))
        val prompts = CopyOnWriteArrayList<String>()
        val continuations = CopyOnWriteArrayList<List<LocalToolResponse>>()
        val cancellations = AtomicInteger()
        private val pending = AtomicReference<LocalInferenceCallback?>()
        val running get() = pending.get() != null

        override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) =
            error("Unexpected model load")
        override fun unload(callback: (Result<Unit>) -> Unit) = error("Unexpected model unload")
        override fun streamThinking(prompt: String, callback: LocalInferenceCallback) {
            callback.onEvent(LocalInferenceEvent.Failed("CONTROLLED_OPTIONAL_ADVISORY_UNAVAILABLE"))
        }
        override fun stream(prompt: String, callback: LocalInferenceCallback) {
            prompts += prompt
            begin(callback)
        }
        override fun respondToTools(responses: List<LocalToolResponse>, callback: LocalInferenceCallback) {
            continuations += responses.toList()
            begin(callback)
        }
        private fun begin(callback: LocalInferenceCallback) {
            check(pending.compareAndSet(null, callback)) { "Concurrent inference" }
            state = LocalModelState.GENERATING
        }
        fun complete(vararg events: LocalInferenceEvent) {
            val callback = checkNotNull(pending.getAndSet(null))
            state = LocalModelState.READY
            events.forEach(callback::onEvent)
            callback.onEvent(LocalInferenceEvent.Completed(LocalInferenceMetrics(0, 0, 0, null, null, null, 1)))
        }
        // Deliberately permits late native callbacks.
        override fun cancel() { cancellations.incrementAndGet() }
    }

    private data class Fixture(val activity: AgentActivity, val runtime: Runtime,
        val memory: BootstrapMemoryStore, val core: ContinuumLearningCore,
        val receipts: FileToolExecutionReceiptStore)

    private fun get(activity: AgentActivity, name: String): Any? = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(activity)
    private fun set(activity: AgentActivity, name: String, value: Any?) = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.set(activity, value)
    private fun call(activity: AgentActivity, name: String, vararg args: Any?): Any? =
        AgentActivity::class.java.declaredMethods.single { it.name == name && it.parameterCount == args.size }
            .apply { isAccessible = true }.invoke(activity, *args)
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun texts(view: View): List<String> = descendants(view).filterIsInstance<TextView>()
        .map { it.text.toString() }
    private fun until(label: String, condition: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!condition() && System.nanoTime() < end) {
            shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
            Thread.sleep(5)
        }
        assertTrue(label, condition())
    }
    private fun drain(activity: AgentActivity, name: String) {
        (get(activity, name) as ExecutorService).submit {}.get(10, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun withFixture(body: (Fixture) -> Unit) {
        LocalDialogueRetention.context.clear()
        val activity = Robolectric.buildActivity(AgentActivity::class.java).get()
        val runtime = Runtime()
        val memory = BootstrapMemoryStore(activity).apply { voiceEnabled = false }
        val core = ContinuumLearningCore(activity.filesDir)
        val receipts = FileToolExecutionReceiptStore(File(activity.filesDir, "revocation-receipts"))
        assertTrue(memory.addFact(FACT))
        core.confirmOwnerFact(FACT)
        assertTrue(core.activeOwnerFacts().any { it.content == FACT })
        set(activity, "localRuntime", runtime)
        set(activity, "bootstrapMemory", memory)
        set(activity, "receiptStore", receipts)
        set(activity, "chatContainer", LinearLayout(activity))
        set(activity, "activityText", TextView(activity))
        set(activity, "planCard", LinearLayout(activity))
        set(activity, "mode", AssistantMode.LOCAL)
        set(activity, "voiceEnabled", false)
        set(activity, "foregroundLearningActive", true)
        call(activity, "buildComposer")
        try { body(Fixture(activity, runtime, memory, core, receipts)) }
        finally {
            (get(activity, "activeChatCancellation") as? DeliberationCancellation)?.cancel()
            if (runtime.running) runtime.complete(LocalInferenceEvent.Cancelled)
            runCatching { drain(activity, "localAgentExecutor") }
            ShadowAlertDialog.getLatestAlertDialog()?.dismiss()
            (get(activity, "mainHandler") as Handler).removeCallbacksAndMessages(null)
            for (name in listOf("localAgentExecutor", "backlogExecutor", "learningIoExecutor", "documentIoExecutor"))
                (get(activity, name) as ExecutorService).shutdownNow()
            LocalDialogueRetention.context.clear()
        }
    }
    private fun startOwnerTurn(f: Fixture): DeliberationCancellation {
        val session = get(f.activity, "sessionId") as String
        call(f.activity, "runLocal", REQUEST, REQUEST,
            LocalGroundedRecall(FACT, "Explicit owner fact in this fixture"),
            CognitiveTurnTrace(session), null, null)
        until("The real model boundary receives the old fact") { f.runtime.running }
        assertTrue(f.runtime.prompts.single().contains(FACT))
        return get(f.activity, "activeChatCancellation") as DeliberationCancellation
    }
    private fun deletionDialog(f: Fixture): AlertDialog {
        call(f.activity, "showMemoryCenter")
        val center = checkNotNull(ShadowAlertDialog.getLatestAlertDialog())
        assertTrue(center.isShowing)
        val delete = descendants(center.window!!.decorView).single {
            it is TextView && it.text.toString() == "FAKTEN LÖSCHEN" && it.isClickable
        }
        assertTrue(delete.performClick())
        val confirmation = checkNotNull(ShadowAlertDialog.getLatestAlertDialog())
        assertNotSame(center, confirmation)
        assertTrue(confirmation.isShowing)
        assertEquals("Fakten löschen", confirmation.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())
        return confirmation
    }
    private fun confirmDeletion(f: Fixture) {
        assertTrue(deletionDialog(f).getButton(AlertDialog.BUTTON_POSITIVE).performClick())
    }
    private fun assertErased(f: Fixture) {
        drain(f.activity, "learningIoExecutor")
        assertTrue(f.memory.facts().isEmpty())
        assertTrue(f.core.activeOwnerFacts().isEmpty())
    }
    private fun assertNoOldOutput(f: Fixture) {
        drain(f.activity, "localAgentExecutor")
        val chat = get(f.activity, "chatContainer") as LinearLayout
        assertFalse(texts(chat).any { it.contains(LATE_REPLY) })
        assertNull(get(f.activity, "lastLocalReply"))
        assertTrue(f.receipts.all().isEmpty())
        assertTrue(f.runtime.continuations.isEmpty())
        assertEquals(1, f.runtime.prompts.size)
    }

    @Test fun confirmedDeletionCancelsPausedOwnerReply() = withFixture { f ->
        val cancellation = startOwnerTurn(f)
        confirmDeletion(f)
        assertTrue("Revocation must cancel before queued I/O",
            runCatching { cancellation.throwIfCancelled() }.isFailure)
        assertErased(f)
        f.runtime.complete(LocalInferenceEvent.TextDelta(LATE_REPLY))
        assertNoOldOutput(f)
    }

    @Test fun confirmedDeletionRejectsLateToolProposal() = withFixture { f ->
        val cancellation = startOwnerTurn(f)
        confirmDeletion(f)
        assertTrue("The old tool-planning turn must be revoked",
            runCatching { cancellation.throwIfCancelled() }.isFailure)
        assertErased(f)
        f.runtime.complete(LocalInferenceEvent.ToolProposal(
            ToolProposal("get_device_info", emptyMap())))
        assertNoOldOutput(f)
    }

    @Test fun cancelledDeletionPreservesPendingReplyAndFacts() = withFixture { f ->
        val cancellation = startOwnerTurn(f)
        val cancellations = f.runtime.cancellations.get()
        assertTrue(deletionDialog(f).getButton(AlertDialog.BUTTON_NEGATIVE).performClick())
        cancellation.throwIfCancelled()
        assertEquals(cancellations, f.runtime.cancellations.get())
        assertTrue(FACT in f.memory.facts())
        assertTrue(f.core.activeOwnerFacts().any { it.content == FACT })
        f.runtime.complete(LocalInferenceEvent.TextDelta(LATE_REPLY))
        drain(f.activity, "localAgentExecutor")
        assertTrue(texts(get(f.activity, "chatContainer") as View).any { it.contains(LATE_REPLY) })
    }

    @Test fun queuedDeletionBlocksReplacementRequestUntilRevocation() = withFixture { f ->
        startOwnerTurn(f)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val io = get(f.activity, "learningIoExecutor") as ExecutorService
        val blocker = io.submit {
            entered.countDown()
            check(release.await(20, TimeUnit.SECONDS))
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        try {
            confirmDeletion(f)
            f.runtime.complete(LocalInferenceEvent.Cancelled)
            drain(f.activity, "localAgentExecutor")
            assertTrue("The deletion still owns the pending I/O", FACT in f.memory.facts())
            assertTrue("Old worker completion must not reopen admission", get(f.activity, "busy") as Boolean)
            val input = get(f.activity, "input") as EditText
            input.setText(NEXT_REQUEST)
            assertTrue((get(f.activity, "sendButton") as Button).performClick())
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(NEXT_REQUEST, input.text.toString())
            assertEquals(1, f.runtime.prompts.size)
        } finally {
            release.countDown()
            blocker.get(5, TimeUnit.SECONDS)
        }
        assertErased(f)
        until("The completed deletion releases admission") { get(f.activity, "busy") == false }
    }

    companion object {
        private const val FACT = "Ich wohne in Kronenbrunn."
        private const val REQUEST = "Plane einen Spaziergang an meinem Wohnort."
        private const val LATE_REPLY = "Dein widerrufener Wohnort Kronenbrunn bleibt in dieser alten Antwort."
        private const val NEXT_REQUEST = "Erkläre kurz, was ein Test ist."
    }
}
