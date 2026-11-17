package at.heinz.deviceai

import android.content.Context
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import at.heinz.deviceai.cognitive.DeliberationCancellation
import at.heinz.deviceai.learning.ContinuumLearningCore
import at.heinz.deviceai.localmodel.ImportedLocalModel
import at.heinz.deviceai.localmodel.LocalInferenceCallback
import at.heinz.deviceai.localmodel.LocalModelRuntime
import at.heinz.deviceai.localmodel.LocalModelState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Actual Activity caller, preferences and recovered ledger; the executor only makes delivery order explicit. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
class BootstrapRecoveryCallerUiTest {
    private class QueuedExecutor : AbstractExecutorService() {
        private val tasks = ArrayDeque<Runnable>()
        private var stopped = false
        val pending: Int get() = tasks.size
        override fun execute(command: Runnable) {
            if (stopped) throw RejectedExecutionException("Test queue is closed")
            tasks.addLast(command)
        }
        override fun shutdown() { stopped = true }
        override fun shutdownNow(): MutableList<Runnable> {
            stopped = true
            return tasks.toMutableList().also { tasks.clear() }
        }
        override fun isShutdown() = stopped
        override fun isTerminated() = stopped && tasks.isEmpty()
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = isTerminated()

        /** Finish real I/O away from the main looper, leaving its UI delivery queued for the test. */
        fun runWorker() {
            check(tasks.isNotEmpty()) { "No caller work was scheduled" }
            val task = tasks.removeFirst()
            val failure = AtomicReference<Throwable?>()
            val thread = Thread({
                try { task.run() } catch (error: Throwable) { failure.set(error) }
            }, "bootstrap-recovery-test-worker").apply { isDaemon = true }
            thread.start()
            thread.join(5_000)
            assertFalse("The bounded caller worker must complete", thread.isAlive)
            failure.get()?.let { throw AssertionError("Caller worker failed", it) }
        }
    }

    private class Runtime : LocalModelRuntime {
        override val state = LocalModelState.EMPTY
        override val loadedModel: ImportedLocalModel? = null
        var cancellations = 0
        override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) =
            error("Deterministic memory recall must not load a model")
        override fun unload(callback: (Result<Unit>) -> Unit) = error("No model was loaded")
        override fun stream(prompt: String, callback: LocalInferenceCallback) =
            error("Deterministic memory recall must not infer")
        override fun cancel() { cancellations++ }
    }

    private data class Fixture(
        val activity: AgentActivity,
        val memory: BootstrapMemoryStore,
        val queue: QueuedExecutor,
        val runtime: Runtime,
        val input: EditText,
        val chat: LinearLayout
    )

    private fun get(activity: AgentActivity, name: String): Any? = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(activity)
    private fun set(activity: AgentActivity, name: String, value: Any?) {
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)
    }
    private fun call(activity: AgentActivity, name: String, vararg args: Any?): Any? =
        AgentActivity::class.java.declaredMethods.single { it.name == name && it.parameterCount == args.size }
            .apply { isAccessible = true }.invoke(activity, *args)
    private fun texts(view: View): List<String> = (if (view is TextView) listOf(view.text.toString()) else emptyList()) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { texts(view.getChildAt(it)) } else emptyList()
    private fun assistantTexts(f: Fixture): List<String> = (0 until f.chat.childCount)
        .map { f.chat.getChildAt(it) }.filterIsInstance<LinearLayout>()
        // LinearLayout supplies a vertical default as well; only the horizontal component identifies the speaker.
        .filter { (it.gravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) == Gravity.START }.flatMap(::texts)
    private fun chatSnapshot(f: Fixture): String = (0 until f.chat.childCount).joinToString(" | ") { index ->
        val row = f.chat.getChildAt(index)
        "row=$index gravity=${(row as? LinearLayout)?.gravity?.toString(16)} text=${texts(row)}"
    }

    private fun fixture(test: (Fixture) -> Unit) {
        val activity = Robolectric.buildActivity(AgentActivity::class.java).get()
        val queue = QueuedExecutor()
        val originalQueue = get(activity, "learningIoExecutor") as ExecutorService
        originalQueue.shutdownNow()
        val runtime = Runtime()
        activity.getSharedPreferences("device_ai_neural_practice_v1", Context.MODE_PRIVATE)
            .edit().putBoolean("enabled", false).commit()
        val memory = BootstrapMemoryStore(activity).apply { voiceEnabled = false }
        set(activity, "learningIoExecutor", queue)
        set(activity, "bootstrapMemory", memory)
        set(activity, "localRuntime", runtime)
        set(activity, "mode", AssistantMode.LOCAL)
        set(activity, "chatContainer", LinearLayout(activity))
        set(activity, "activityText", TextView(activity))
        set(activity, "voiceEnabled", false)
        set(activity, "foregroundLearningActive", true)
        call(activity, "buildComposer")
        val f = Fixture(activity, memory, queue, runtime,
            get(activity, "input") as EditText, get(activity, "chatContainer") as LinearLayout)
        try { test(f) } finally {
            (get(activity, "activeChatCancellation") as? DeliberationCancellation)?.cancel()
            for (name in listOf("localAgentExecutor", "backlogExecutor", "learningIoExecutor")) {
                (get(activity, name) as ExecutorService).shutdownNow()
            }
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun submit(f: Fixture, request: String, throughComposer: Boolean = false) {
        f.input.setText(request)
        if (throughComposer) call(f.activity, "sendCurrentInput", true)
        else call(f.activity, "runReviewedBootstrapDecision", request)
        assertEquals("The caller consumes exactly the submitted text before asynchronous work", "", f.input.text.toString())
        assertEquals(1, texts(f.chat).count { it == request })
    }

    private fun recoverWithStalePreferences(f: Fixture): ContinuumLearningCore {
        val original = ContinuumLearningCore(f.activity.filesDir)
        original.confirmOwnerFact(RECOVERED)
        original.confirmOwnerFact("Ich trinke gerne Tee.")
        original.confirmOwnerFact(LOST_AFTER_BACKUP)
        assertTrue(f.memory.addFact(RECOVERED))
        assertTrue(f.memory.addFact(LOST_AFTER_BACKUP))
        val primary = File(f.activity.filesDir, "learning-core-v2/experiences-v2.bin")
        primary.writeBytes(primary.readBytes().also { bytes ->
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x55).toByte()
        })
        return ContinuumLearningCore.withAutomaticRecovery(f.activity.filesDir).also { recovered ->
            assertTrue(recovered.activeOwnerFacts().isEmpty())
            assertTrue(recovered.recoveredOwnerFactsForReview().facts.any { it.content == RECOVERED })
        }
    }

    @Test fun stalePreferencesCannotBypassRecoveryThroughTheActualComposer() = fixture { f ->
        val core = recoverWithStalePreferences(f)
        submit(f, "Was weißt du?", throughComposer = true)
        assertEquals(true, get(f.activity, "busy"))
        assertEquals(1, f.queue.pending)
        assertTrue(assistantTexts(f).isEmpty())
        f.queue.runWorker()
        shadowOf(Looper.getMainLooper()).idle()
        val answer = assistantTexts(f).joinToString("\n")
        assertTrue("The caller must actually answer: ${chatSnapshot(f)}", answer.isNotBlank())
        assertFalse("A recovered statement is not current consent", answer.contains(RECOVERED))
        assertFalse("A missing newer statement must not be re-imported from stale preferences", answer.contains(LOST_AFTER_BACKUP))
        assertTrue(core.activeOwnerFacts().isEmpty())
        assertEquals(false, get(f.activity, "busy"))
    }

    @Test fun structuredKnowledgeCannotProveARecoveredLegacyFact() = fixture { f ->
        recoverWithStalePreferences(f)
        submit(f, "Prüfe Wissen: Testobjekt | Farbe | Azurblau")
        assertEquals(1, f.queue.pending)
        f.queue.runWorker()
        shadowOf(Looper.getMainLooper()).idle()
        val answers = assistantTexts(f)
        assertTrue("Absent confirmed evidence must remain unknown: ${chatSnapshot(f)}", answers.any { it.startsWith("UNBEKANNT") })
        assertFalse(answers.any { it.contains("BESTÄTIGT gespeichert") || it.contains("Quelle ") })
    }

    @Test fun healthyLegacyFactsStillAnswerAndDeliveryPreservesANewDraft() = fixture { f ->
        assertTrue(f.memory.addFact(RECOVERED))
        submit(f, "Was weißt du?")
        f.input.setText(NEXT_DRAFT)
        f.queue.runWorker()
        assertTrue("UI completion remains queued", assistantTexts(f).isEmpty())
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Healthy confirmed memory must remain visible: ${chatSnapshot(f)}", assistantTexts(f).any { it.contains(RECOVERED) })
        assertEquals(NEXT_DRAFT, f.input.text.toString())
        assertEquals(1, texts(f.chat).count { it == "Was weißt du?" })
        assertEquals(false, get(f.activity, "busy"))
        assertNull(get(f.activity, "activeChatCancellation"))
    }

    @Test fun stopAfterWorkerCompletionDropsTheQueuedReplyAndKeepsTheDraft() = fixture { f ->
        assertTrue(f.memory.addFact(RECOVERED))
        submit(f, "Was weißt du?")
        f.queue.runWorker()
        f.input.setText(NEXT_DRAFT)
        assertTrue((get(f.activity, "sendButton") as View).performClick())
        assertTrue("The actual STOP action reaches cancellation", f.runtime.cancellations > 0)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(assistantTexts(f).isEmpty())
        assertEquals(NEXT_DRAFT, f.input.text.toString())
        assertEquals(false, get(f.activity, "busy"))
        assertNull(get(f.activity, "activeChatCancellation"))
    }

    @Test fun sameTokenFromAnOldSessionIsReleasedWithoutPublishingItsReply() = fixture { f ->
        assertTrue(f.memory.addFact(RECOVERED))
        submit(f, "Was weißt du?")
        f.queue.runWorker()
        set(f.activity, "sessionId", "replacement-owner-session")
        f.input.setText(NEXT_DRAFT)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(assistantTexts(f).isEmpty())
        assertEquals(false, get(f.activity, "busy"))
        assertNull(get(f.activity, "activeChatCancellation"))
        assertEquals(NEXT_DRAFT, f.input.text.toString())
    }

    @Test fun supersededCallbackCannotReleaseAnotherRequestsBusyState() = fixture { f ->
        assertTrue(f.memory.addFact(RECOVERED))
        submit(f, "Was weißt du?")
        f.queue.runWorker()
        val newer = DeliberationCancellation()
        set(f.activity, "activeChatCancellation", newer)
        f.input.setText(NEXT_DRAFT)
        shadowOf(Looper.getMainLooper()).idle()
        assertSame(newer, get(f.activity, "activeChatCancellation"))
        assertEquals(true, get(f.activity, "busy"))
        assertEquals(true, get(f.activity, "activeLocalRequest"))
        newer.throwIfCancelled()
        assertTrue(assistantTexts(f).isEmpty())
        assertEquals(NEXT_DRAFT, f.input.text.toString())
    }

    @Test fun rejectedSubmissionReleasesItsOwnLaneAndKeepsTheUserMessage() = fixture { f ->
        f.queue.shutdown()
        submit(f, "Was weißt du?")
        assertEquals(0, f.queue.pending)
        assertEquals(false, get(f.activity, "busy"))
        assertEquals(false, get(f.activity, "activeLocalRequest"))
        assertNull(get(f.activity, "activeChatCancellation"))
        assertTrue("Rejected I/O must report failure: ${chatSnapshot(f)}", assistantTexts(f).any { it.contains("nicht sicher verfügbar") })
    }

    companion object {
        private const val RECOVERED = "Fakt: Testobjekt | Farbe | Azurblau"
        private const val LOST_AFTER_BACKUP = "Mein verlorenes Projekt heißt Lumenpfad."
        private const val NEXT_DRAFT = "Mein neuer noch nicht gesendeter Entwurf."
    }
}
