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
        var holdThinking = false
        private val pending = AtomicReference<LocalInferenceCallback?>()
        val running get() = pending.get() != null

        override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) =
            error("Unexpected model load")
        override fun unload(callback: (Result<Unit>) -> Unit) = error("Unexpected model unload")
        override fun streamThinking(prompt: String, callback: LocalInferenceCallback) {
            if (holdThinking) stream(prompt, callback)
            else callback.onEvent(LocalInferenceEvent.Failed("CONTROLLED_OPTIONAL_ADVISORY_UNAVAILABLE"))
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
    private fun prepareActivity(activity: AgentActivity, runtime: Runtime,
                                memory: BootstrapMemoryStore, receipts: FileToolExecutionReceiptStore) {
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
    }
    private fun closeActivity(activity: AgentActivity, runtime: Runtime) {
        (get(activity, "activeChatCancellation") as? DeliberationCancellation)?.cancel()
        if (runtime.running) runtime.complete(LocalInferenceEvent.Cancelled)
        runCatching { drain(activity, "localAgentExecutor") }
        runCatching { drain(activity, "learningIoExecutor") }
        ShadowAlertDialog.getLatestAlertDialog()?.dismiss()
        (get(activity, "mainHandler") as Handler).removeCallbacksAndMessages(null)
        for (name in listOf("localAgentExecutor", "backlogExecutor", "learningIoExecutor", "documentIoExecutor"))
            (get(activity, name) as ExecutorService).shutdownNow()
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
        prepareActivity(activity, runtime, memory, receipts)
        try { body(Fixture(activity, runtime, memory, core, receipts)) }
        finally {
            closeActivity(activity, runtime)
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
        val decor = center.window!!.decorView
        val metrics = f.activity.resources.displayMetrics
        decor.measure(View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
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
        shadowOf(Looper.getMainLooper()).idle()
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
        assertTrue(LocalDialogueRetention.context.priorUserTurns(get(f.activity, "sessionId") as String).isEmpty())
        assertTrue(f.receipts.all().isEmpty())
        assertTrue(f.runtime.continuations.isEmpty())
        assertEquals(1, f.runtime.prompts.size)
    }

    @Test fun confirmedDeletionClearsThinkingJournalAndRejectsLateReflection() = withFixture { f ->
        val thinking = at.heinz.deviceai.learning.ContinuumThinkingMemory(f.activity.filesDir, f.core)
        thinking.reflect(at.heinz.deviceai.learning.ThinkingRun("thinking-ui", REQUEST, listOf(FACT)))
        val journal = File(f.activity.filesDir, "learning-core-v2/thinking-journal-v1.log")
        assertTrue(journal.length() > 0L)
        startOwnerTurn(f)
        confirmDeletion(f)
        assertErased(f)
        assertFalse("Thinking sidecar must be empty after confirmed deletion",
            journal.isFile && journal.length() > 0L)
        f.runtime.complete(LocalInferenceEvent.TextDelta(LATE_REPLY))
        assertNoOldOutput(f)
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            thinking.reflect(at.heinz.deviceai.learning.ThinkingRun("thinking-ui", REQUEST, listOf(LATE_REPLY)))
        }
        val fresh = at.heinz.deviceai.learning.ContinuumThinkingMemory(f.activity.filesDir,
            ContinuumLearningCore(f.activity.filesDir))
        assertTrue(fresh.runJournal("thinking-ui").isEmpty())
    }

    @Test fun thinkingJournalFailureCannotReportSuccessfulOwnerRevocation() = withFixture { f ->
        val before = f.core.activeOwnerFacts()
        val journal = File(f.activity.filesDir, "learning-core-v2/thinking-journal-v1.log")
        assertTrue(journal.mkdirs())
        File(journal, "existing-entry").writeText("Blocked journal truncation")
        confirmDeletion(f)
        drain(f.activity, "learningIoExecutor")
        val chat = texts(get(f.activity, "chatContainer") as View).joinToString("\n")
        assertTrue(chat.contains("Widerruf im Continuum-Lernkern ist fehlgeschlagen"))
        assertFalse(chat.contains("sie werden nicht mehr verwendet"))
        assertEquals(before, ContinuumLearningCore(f.activity.filesDir).activeOwnerFacts())
        assertEquals("Nachricht senden", (get(f.activity, "sendButton") as Button).contentDescription.toString())
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
            assertEquals("Old worker completion must not reopen admission", "Device AI arbeitet",
                (get(f.activity, "sendButton") as Button).contentDescription.toString())
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

    @Test fun replacementActivityWaitsForOriginalDeletionAndCanRememberAgain() = withFixture { f ->
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val blocker = (get(f.activity, "learningIoExecutor") as ExecutorService).submit {
            entered.countDown()
            check(release.await(20, TimeUnit.SECONDS))
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        val next = Robolectric.buildActivity(AgentActivity::class.java).get()
        val runtime = Runtime()
        try {
            confirmDeletion(f)
            f.activity.finish()
            assertTrue(f.activity.isFinishing)
            prepareActivity(next, runtime, BootstrapMemoryStore(next),
                FileToolExecutionReceiptStore(File(next.filesDir, "replacement-receipts")))
            val input = get(next, "input") as EditText
            input.setText("Merke dir: $FACT")
            assertTrue((get(next, "sendButton") as Button).performClick())
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("Merke dir: $FACT", input.text.toString())
            assertTrue(runtime.prompts.isEmpty())
            release.countDown()
            blocker.get(5, TimeUnit.SECONDS)
            assertErased(f)
            assertTrue("A finished Activity emits no deletion reply",
                texts(get(f.activity, "chatContainer") as View).none { it.contains("wurden aus dem lokalen") })
            assertTrue((get(next, "sendButton") as Button).performClick())
            until("A fresh explicit statement reactivates the fact") {
                FACT in BootstrapMemoryStore(next).facts() &&
                    ContinuumLearningCore(next.filesDir).activeOwnerFacts().any { it.content == FACT }
            }
            assertTrue(runtime.prompts.isEmpty())
        } finally {
            release.countDown()
            blocker.get(5, TimeUnit.SECONDS)
            closeActivity(next, runtime)
        }
    }

    @Test fun failedLedgerDeletionReleasesAdmissionAndCanBeRetried() = withFixture { f ->
        val ledger = File(f.activity.filesDir, "learning-core-v2/experiences-v2.bin")
        val original = ledger.readBytes()
        ledger.writeBytes(original.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 0x55).toByte() })
        try {
            confirmDeletion(f)
            drain(f.activity, "learningIoExecutor")
            val chat = texts(get(f.activity, "chatContainer") as View).joinToString("\n")
            assertTrue(chat.contains("Widerruf im Continuum-Lernkern ist fehlgeschlagen"))
            assertFalse(chat.contains("sie werden nicht mehr verwendet"))
            assertEquals("Nachricht senden", (get(f.activity, "sendButton") as Button).contentDescription.toString())
        } finally { ledger.writeBytes(original) }
        confirmDeletion(f)
        assertErased(f)
        assertEquals(0, f.runtime.cancellations.get())
    }

    @Test fun unrelatedModelDiagnosticSurvivesDeletion() = withFixture { f ->
        call(f.activity, "runLocalSmokeTest", true)
        until("The isolated model diagnostic starts") { f.runtime.running }
        val cancellation = get(f.activity, "activeChatCancellation") as DeliberationCancellation
        val cancellations = f.runtime.cancellations.get()
        assertFalse(f.runtime.prompts.single().contains(FACT))
        confirmDeletion(f)
        assertErased(f)
        cancellation.throwIfCancelled()
        assertEquals(cancellations, f.runtime.cancellations.get())
        assertTrue(f.runtime.running)
        f.runtime.complete(LocalInferenceEvent.TextDelta("Der Roboter untersucht Steine auf dem Mond."))
        drain(f.activity, "localAgentExecutor")
        assertTrue(texts(get(f.activity, "chatContainer") as View).any { it.contains("Der Roboter untersucht") })
    }

    @Test fun unrelatedCodingPlanningSurvivesDeletion() = withFixture { f ->
        f.runtime.holdThinking = true
        val goal = "Bewahre negative Ergebnisse."
        val planning = checkNotNull(ContinuumCodingPlanning.create(goal, "a".repeat(40),
            listOf("lib/distance.mjs"), ContinuumCodingTask.Limits(1, 8192)))
        call(f.activity, "runLocal", goal, goal, null,
            CognitiveTurnTrace(get(f.activity, "sessionId") as String), planning, null)
        until("The independent coding plan starts") { f.runtime.running }
        val cancellation = get(f.activity, "activeChatCancellation") as DeliberationCancellation
        val cancellations = f.runtime.cancellations.get()
        assertFalse(f.runtime.prompts.single().contains(FACT))
        confirmDeletion(f)
        assertErased(f)
        cancellation.throwIfCancelled()
        assertEquals(cancellations, f.runtime.cancellations.get())
        assertTrue(f.runtime.running)
        f.runtime.complete(LocalInferenceEvent.TextDelta(
            """{"schema":"continuum-coding-plan","version":1,"kind":"BUGFIX","goal":"Bewahre negative Ergebnisse.","acceptance":["Negative Werte bleiben negativ"]}"""))
        drain(f.activity, "localAgentExecutor")
        val review = checkNotNull(ShadowAlertDialog.getLatestAlertDialog())
        assertTrue(review.isShowing)
        assertTrue(descendants(review.window!!.decorView).filterIsInstance<EditText>()
            .any { it.text.toString() == goal })
        assertNull(shadowOf(f.activity).nextStartedActivity)
    }

    @Test fun confirmedDeletionRevokesSharedTeamBeforeQueuedLedgerWriteAndRejectsLateReply() = withFixture { f ->
        val profiles = at.heinz.deviceai.chief.ChiefBotProfileStore(File(f.activity.filesDir, "shared-team"))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val token = AtomicReference<DeliberationCancellation>()
        val calls = AtomicInteger()
        val team = at.heinz.deviceai.chief.ChiefBotTeam(profiles,
            object : at.heinz.deviceai.chief.ChiefTeamModel {
                override fun readySha256() = "a".repeat(64)
                override fun generate(prompt: String, cancellation: DeliberationCancellation, deadlineMillis: Long): String {
                    assertTrue(prompt.contains(FACT))
                    token.set(cancellation)
                    calls.incrementAndGet()
                    entered.countDown()
                    val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                    while (release.count > 0 && System.nanoTime() < end) {
                        try { release.await(50, TimeUnit.MILLISECONDS) } catch (_: InterruptedException) { }
                    }
                    check(release.count == 0L)
                    returned.countDown()
                    return LATE_REPLY
                }
            }, sharedKnowledge = f.core)
        val writeRelease = CountDownLatch(1)
        val writeEntered = CountDownLatch(1)
        val blocked = (get(f.activity, "learningIoExecutor") as ExecutorService).submit {
            writeEntered.countDown()
            check(writeRelease.await(10, TimeUnit.SECONDS))
        }
        try {
            assertTrue(writeEntered.await(5, TimeUnit.SECONDS))
            team.initialize()
            team.configure("bot-atlas", listOf("bot-kora", "bot-lint"), 0)
            val id = "team-" + java.util.UUID.randomUUID()
            val request = "Prüfe meine bestätigte Wohnangabe in Kronenbrunn."
            val review = team.reviewSharedKnowledge(id, request, 1)
            val grant = review.approve(setOf(review.facts.single { it.text == FACT }.id), true)
            set(f.activity, "botTeam", team)
            set(f.activity, "botTeamOwnsLane", true)
            call(f.activity, "setBusy", true)
            team.submit(id, request, 1, true, shared = grant)
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val priorCancellations = f.runtime.cancellations.get()
            confirmDeletion(f)
            assertThrows(java.util.concurrent.CancellationException::class.java) { token.get().throwIfCancelled() }
            assertTrue(f.core.activeOwnerFacts().any { it.content == FACT })
            assertEquals(priorCancellations + 1, f.runtime.cancellations.get())
            writeRelease.countDown()
            blocked.get(5, TimeUnit.SECONDS)
            assertErased(f)
            release.countDown()
            assertTrue(returned.await(5, TimeUnit.SECONDS))
            assertTrue(team.awaitIdle(10_000))
            assertEquals(1, calls.get())
            assertTrue(profiles.teamState().tasks.single().responses.isEmpty())
            assertFalse(texts(get(f.activity, "chatContainer") as View).any { it.contains(LATE_REPLY) })
        } finally {
            writeRelease.countDown()
            release.countDown()
            team.close()
            set(f.activity, "botTeam", null)
            set(f.activity, "botTeamOwnsLane", false)
        }
    }

    @Test fun confirmedDeletionRejectsPreviouslyQueuedSharedTeamBeforeEitherExecutorCompletes() = withFixture { f ->
        val profiles = at.heinz.deviceai.chief.ChiefBotProfileStore(File(f.activity.filesDir, "queued-shared-team"))
        val calls = AtomicInteger()
        val team = at.heinz.deviceai.chief.ChiefBotTeam(profiles,
            object : at.heinz.deviceai.chief.ChiefTeamModel {
                override fun readySha256() = "a".repeat(64)
                override fun generate(prompt: String, cancellation: DeliberationCancellation, deadlineMillis: Long): String {
                    calls.incrementAndGet()
                    return LATE_REPLY
                }
            }, sharedKnowledge = f.core)
        val backlogRelease = CountDownLatch(1)
        val writeRelease = CountDownLatch(1)
        val backlogEntered = CountDownLatch(1)
        val writeEntered = CountDownLatch(1)
        val backlog = (get(f.activity, "backlogExecutor") as ExecutorService).submit {
            backlogEntered.countDown()
            check(backlogRelease.await(10, TimeUnit.SECONDS))
        }
        val write = (get(f.activity, "learningIoExecutor") as ExecutorService).submit {
            writeEntered.countDown()
            check(writeRelease.await(10, TimeUnit.SECONDS))
        }
        try {
            assertTrue(backlogEntered.await(5, TimeUnit.SECONDS))
            assertTrue(writeEntered.await(5, TimeUnit.SECONDS))
            team.initialize()
            team.configure("bot-atlas", listOf("bot-kora", "bot-lint"), 0)
            val id = "team-" + java.util.UUID.randomUUID()
            val request = "Prüfe meine bestätigte Wohnangabe in Kronenbrunn."
            val review = team.reviewSharedKnowledge(id, request, 1)
            val grant = review.approve(setOf(review.facts.single { it.text == FACT }.id), true)
            set(f.activity, "botTeam", team)
            assertEquals(true, call(f.activity, "submitBotTeam", id, request, 1L, grant))
            assertTrue(profiles.teamState().tasks.isEmpty())
            confirmDeletion(f)
            assertTrue(f.core.activeOwnerFacts().any { it.content == FACT })
            backlogRelease.countDown()
            backlog.get(5, TimeUnit.SECONDS)
            drain(f.activity, "backlogExecutor")
            assertTrue(team.awaitIdle(10_000))
            assertEquals(0, calls.get())
            assertTrue(profiles.teamState().tasks.isEmpty())
            assertTrue(f.core.activeOwnerFacts().any { it.content == FACT })
            writeRelease.countDown()
            write.get(5, TimeUnit.SECONDS)
            assertErased(f)
        } finally {
            backlogRelease.countDown()
            writeRelease.countDown()
            team.close()
            set(f.activity, "botTeam", null)
            set(f.activity, "botTeamOwnsLane", false)
        }
    }

    private fun queueRememberedAnswer(f: Fixture) {
        assertTrue(f.memory.clearFacts())
        f.core.revokeAllOwnerFacts()
        val question = at.heinz.deviceai.learning.ContinuumCognitiveRuntime(f.activity.filesDir, f.core,
            conversationId = get(f.activity, "sessionId") as String).reply("Wo wohne ich?")
        assertFalse(question.grounded)
        call(f.activity, "rememberOwnerFact", FACT, "Gemerkt: $FACT")
        (get(f.activity, "learningIoExecutor") as ExecutorService).submit {}.get(10, TimeUnit.SECONDS)
        assertTrue(f.core.dialogueHistory().last().grounded)
        assertTrue(f.core.dialogueHistory().last().answer.contains("Kronenbrunn"))
    }

    @Test fun queuedRememberedAnswerCannotOutliveRevocation() = withFixture { f ->
        queueRememberedAnswer(f)
        // Confirm before queued reply delivery.
        call(f.activity, "forgetOwnerFacts")
        assertErased(f)
        val chat = texts(get(f.activity, "chatContainer") as View).joinToString("\n")
        assertFalse(chat.contains("Kronenbrunn"))
        assertFalse(chat.contains("Zur vorherigen offenen Frage"))
    }

    @Test fun newerOwnerRequestKeepsActualMemoryWriteReceipt() = withFixture { f ->
        queueRememberedAnswer(f)
        call(f.activity, "addUserMessage", NEXT_REQUEST)
        shadowOf(Looper.getMainLooper()).idle()
        val chat = texts(get(f.activity, "chatContainer") as View).joinToString("\n")
        assertTrue(chat.contains("Gemerkt."))
        assertTrue(f.core.activeOwnerFacts().any { it.content == FACT })
        assertFalse(chat.contains("Zur vorherigen offenen Frage"))
    }

    companion object {
        private const val FACT = "Ich wohne in Kronenbrunn."
        private const val REQUEST = "Plane einen Spaziergang an meinem Wohnort."
        private const val LATE_REPLY = "Dein widerrufener Wohnort Kronenbrunn bleibt in dieser alten Antwort."
        private const val NEXT_REQUEST = "Erkläre kurz, was ein Test ist."
    }
}
