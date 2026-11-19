package at.heinz.deviceai

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import org.robolectric.RuntimeEnvironment
import at.heinz.deviceai.cognitive.CognitiveTurnTrace
import at.heinz.deviceai.cognitive.DeliberationCancellation
import at.heinz.deviceai.cognitive.LocalDialogueRetention
import at.heinz.deviceai.localmodel.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/**
 * Owner switch „Gespräch im Modell behalten“ through the real local turn lifecycle (runLocal → prompt assembly →
 * orchestrator → delivery) with a stand-in runtime that keeps, appends and closes like the LiteRT-LM runtime.
 * Robolectric, HOST: no model ran and nothing here is a device result.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class LocalDialogueReuseUiTest {
    private class Call(val entry: String, val sent: String, val request: LocalDialogueRequest?)

    private class Runtime : LocalModelRuntime {
        override val state = LocalModelState.READY
        override val loadedModel = ImportedLocalModel("reuse-fixture", "v1", "a".repeat(64), 1,
            LocalModelCapabilities(true, false, false, false, true, 32_768, null, setOf(LocalModelBackend.CPU)))
        override val contextTokens: Int? = 4_096
        override val activeBackend: LocalModelBackend? = LocalModelBackend.CPU
        val calls = mutableListOf<Call>()
        /** Every native conversation that was started through the dialogue entry: what it received and produced. */
        val conversations = mutableListOf<MutableList<String>>()
        val answers = ArrayDeque<String>()
        var releases = 0
        var beforeReply: (Int) -> Unit = {}
        private var kept: Pair<String, MutableList<String>>? = null
        private var tokens = 0

        private fun reply(callback: LocalInferenceCallback): String {
            beforeReply(calls.size)
            val answer = answers.removeFirstOrNull() ?: "Dazu liegt mir keine weitere Auskunft vor."
            callback.onEvent(LocalInferenceEvent.TextDelta(answer))
            callback.onEvent(LocalInferenceEvent.Completed(LocalInferenceMetrics(0, answer.length, 1, null, null, 1, 2)))
            return answer
        }

        override fun streamDialogue(request: LocalDialogueRequest, callback: LocalInferenceCallback) {
            val current = kept
            val followUp = request.followUp
            val reuse = current != null && followUp != null && current.first == request.conversationKey
            val conversation = if (reuse) current.second else mutableListOf<String>().also { conversations += it }
            kept = null
            request.receipt.dispatched(reuse, if (reuse || followUp == null) null
                else LocalDialogueReuse.RUNTIME_NO_KEPT_CONVERSATION, if (reuse) tokens else null)
            val sent = if (reuse) followUp else request.freshPrompt
            calls += Call(if (reuse) "FOLLOW_UP" else "DIALOGUE_FRESH", sent, request)
            conversation += sent
            tokens = if (reuse) tokens + 200 else 900
            kept = request.conversationKey to conversation
            request.receipt.retained(tokens)
            conversation += reply(callback)
        }

        override fun streamTextOnly(prompt: String, callback: LocalInferenceCallback) {
            kept = null
            calls += Call("TEXT_ONLY", prompt, null)
            reply(callback)
        }

        override fun stream(prompt: String, callback: LocalInferenceCallback) {
            kept = null
            calls += Call("TOOL_PATH", prompt, null)
            reply(callback)
        }

        override fun streamThinking(prompt: String, callback: LocalInferenceCallback) {
            kept = null
            calls += Call("THINKING", prompt, null)
            callback.onEvent(LocalInferenceEvent.Failed("No isolated thinking in this fixture."))
        }

        override fun releaseDialogue() { releases++; kept = null }
        override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) =
            error("Unexpected model load")
        override fun unload(callback: (Result<Unit>) -> Unit) = error("Unexpected model unload")
        override fun respondToTools(responses: List<LocalToolResponse>, callback: LocalInferenceCallback) =
            error("No tool in fixture")
        override fun cancel() = Unit
    }

    private fun get(a: AgentActivity, name: String): Any? = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(a)
    private fun set(a: AgentActivity, name: String, value: Any?) = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.set(a, value)
    private fun call(a: AgentActivity, name: String, vararg args: Any?) =
        AgentActivity::class.java.declaredMethods.single { it.name == name && it.parameterCount == args.size }
            .apply { isAccessible = true }.invoke(a, *args)
    private fun text(view: View): String = when (view) {
        is TextView -> view.text.toString()
        is ViewGroup -> (0 until view.childCount).joinToString("\n") { text(view.getChildAt(it)) }
        else -> ""
    }
    private fun drain(a: AgentActivity) = repeat(6) {
        (get(a, "localAgentExecutor") as ExecutorService).submit {}.get(10, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun count(text: String, part: String) = text.windowed(part.length).count { it == part }

    private val questions = listOf(
        "Was ist der Unterschied zwischen einer Hypothese und einer Theorie?",
        "Erkläre mir bitte genauer, wie eine Theorie überprüft wird.",
        "Was bedeutet in diesem Zusammenhang der Begriff Falsifizierbarkeit?")
    private val replies = listOf(
        "Eine Hypothese ist eine überprüfbare Vermutung, eine Theorie ein vielfach bestätigtes Erklärungsgebäude.",
        "Man leitet Vorhersagen ab und vergleicht sie in wiederholbaren Versuchen mit den Beobachtungen.",
        "Falsifizierbar heißt, dass eine Aussage durch eine mögliche Beobachtung widerlegt werden könnte.")

    /** Runs [turns] as consecutive owner turns of one chat; [switch] null leaves the stored default untouched. */
    private fun dialogue(switch: Boolean?, turns: List<Pair<String, String>> = questions.zip(replies),
                         arrange: (AgentActivity, Runtime) -> Unit = { _, _ -> },
                         inspect: (AgentActivity, Runtime, String) -> Unit) {
        LocalDialogueRetention.context.clear()
        // Every run starts from the same empty app storage, so two runs of the same dialogue are comparable.
        (RuntimeEnvironment.getApplication() as Context).filesDir.listFiles()?.forEach { it.deleteRecursively() }
        val a = Robolectric.buildActivity(AgentActivity::class.java).get()
        val runtime = Runtime()
        set(a, "localRuntime", runtime)
        set(a, "receiptStore", FileToolExecutionReceiptStore(File(a.filesDir, "reuse-receipts")))
        set(a, "bootstrapMemory", BootstrapMemoryStore(a))
        set(a, "chatContainer", LinearLayout(a))
        set(a, "activityText", TextView(a))
        set(a, "planCard", LinearLayout(a))
        set(a, "voiceEnabled", false)
        set(a, "foregroundLearningActive", true)
        call(a, "buildComposer")
        if (switch != null) set(a, "dialogueReuseSwitch", switch)
        arrange(a, runtime)
        try {
            for ((question, answer) in turns) {
                runtime.answers += answer
                // Every owner message advances the owner revision exactly once (addUserMessage).
                set(a, "ownerReplyRevision", (get(a, "ownerReplyRevision") as Long) + 1)
                call(a, "runLocal", question, question, null, CognitiveTurnTrace(get(a, "sessionId") as String), null, null)
                drain(a)
                assertFalse("the lane is free again", get(a, "busy") as Boolean)
            }
            inspect(a, runtime, text(get(a, "chatContainer") as View))
        } finally {
            (get(a, "activeChatCancellation") as? DeliberationCancellation)?.cancel()
            runCatching { drain(a) }
            (get(a, "mainHandler") as Handler).removeCallbacksAndMessages(null)
            for (name in listOf("localAgentExecutor", "backlogExecutor", "learningIoExecutor", "documentIoExecutor"))
                (get(a, name) as ExecutorService).shutdownNow()
            LocalDialogueRetention.context.clear()
        }
    }

    private fun decisions(a: AgentActivity) = (get(a, "dialogueReuse") as LocalDialogueReuseController).recent().map { it.code() }
    private fun clockless(prompt: String) = prompt.replace(Regex("Referenzzeit laut Geräteuhr: [^\n]*"), "CLOCK")

    @Test fun theSwitchIsOffByDefaultAndEveryTurnSendsTheCompletePromptAsBefore() = dialogue(null) { a, runtime, visible ->
        assertFalse(LocalDialogueReuseHost.enabled(a))
        assertEquals(listOf("TEXT_ONLY", "TEXT_ONLY", "TEXT_ONLY"), runtime.calls.map { it.entry })
        assertTrue("no native conversation was ever kept", runtime.conversations.isEmpty())
        assertEquals("the runtime extension is not touched at all", 0, runtime.releases)
        assertTrue(decisions(a).isEmpty())
        // The complete prompt carries the dialogue so far, as it always did.
        assertTrue(runtime.calls[1].sent.contains(questions[0]) && runtime.calls[1].sent.contains(replies[0]))
        assertTrue(runtime.calls[2].sent.contains(questions[1]) && runtime.calls[2].sent.contains(replies[1]))
        replies.forEach { assertTrue(visible, visible.contains(it)) }
        assertFalse(visible, visible.contains("GESPRÄCH IM MODELL"))
    }

    @Test fun withTheSwitchOneConversationSeesEveryStatementAndAnswerExactlyOnceInOrder() =
        dialogue(true) { a, runtime, visible ->
            assertEquals(listOf("DIALOGUE_FRESH", "FOLLOW_UP", "FOLLOW_UP"), runtime.calls.map { it.entry })
            assertEquals(listOf("FRESH:NO_KEPT_CONVERSATION", "REUSED", "REUSED"), decisions(a))
            assertEquals(1, runtime.conversations.size)
            val seen = runtime.conversations.single().joinToString("\n")
            var position = -1
            for (index in questions.indices) {
                assertEquals(questions[index], 1, count(seen, questions[index]))
                assertEquals(replies[index], 1, count(seen, replies[index]))
                val questionAt = seen.indexOf(questions[index])
                val replyAt = seen.indexOf(replies[index])
                assertTrue("order of turn ${index + 1}", position < questionAt && questionAt < replyAt)
                position = replyAt
            }
            // A follow-up is the new message with its form and clock lines – no instructions, no history.
            for (index in 1..2) {
                val followUp = runtime.calls[index].sent
                assertTrue(followUp, followUp.contains(LocalDialogueReusePrompt.MESSAGE_HEADER + questions[index] + "\n"))
                assertTrue(followUp, followUp.contains("Referenzzeit laut Geräteuhr:"))
                assertFalse(followUp, followUp.contains("You are Device AI"))
                assertFalse(followUp, followUp.contains("GESPRÄCHSDATEN —"))
                // The complete prompt of the same turn was built unchanged and travels along as the fallback.
                val complete = runtime.calls[index].request!!.freshPrompt
                assertTrue(complete.contains(questions[index - 1]) && complete.contains(replies[index - 1]))
                assertTrue("${followUp.length} vs ${complete.length}", followUp.length * 2 < complete.length)
            }
            // The owner sees exactly the generated answers.
            replies.forEach { assertEquals(visible, 1, count(visible, it)) }
            assertTrue((get(a, "dialogueReuse") as LocalDialogueReuseController).holdsConversation)
        }

    @Test fun theCompletePromptOfEveryTurnIsTheSameWithAndWithoutTheSwitch() {
        val without = mutableListOf<String>()
        dialogue(false) { _, runtime, _ -> without += runtime.calls.map { clockless(it.sent) } }
        val with = mutableListOf<String>()
        dialogue(true) { _, runtime, _ -> with += runtime.calls.map { clockless(it.request!!.freshPrompt) } }
        assertEquals(3, without.size)
        assertEquals(without, with)
    }

    @Test fun aStoppedGenerationIsNeverContinued() = dialogue(true, arrange = { a, runtime ->
        // STOP while the second answer is generated: the turn is cancelled inside the app.
        runtime.beforeReply = { call -> if (call == 2) (get(a, "activeChatCancellation") as DeliberationCancellation).cancel() }
    }) { a, runtime, visible ->
        assertEquals(listOf("DIALOGUE_FRESH", "FOLLOW_UP", "DIALOGUE_FRESH"), runtime.calls.map { it.entry })
        assertEquals("the third turn starts a new conversation from its complete prompt", 2, runtime.conversations.size)
        assertNull(runtime.calls[2].request!!.followUp)
        assertNotEquals(runtime.calls[1].request!!.conversationKey, runtime.calls[2].request!!.conversationKey)
        assertEquals("FRESH:PREVIOUS_TURN_NOT_COMMITTED", decisions(a).last())
        assertFalse("the stopped answer was not shown", visible.contains(replies[1]))
        assertTrue(visible, visible.contains(replies[2]))
    }

    @Test fun aTurnThatIsNotPlainTextRunsOnTheUnchangedPathAndEndsTheKeptConversation() = dialogue(true, listOf(
        questions[0] to replies[0],
        "Öffne bitte die Einstellungen des Geräts." to "Dafür müsste ich ein Werkzeug vorschlagen; in diesem Zug wurde nichts ausgeführt.",
        questions[2] to replies[2])) { a, runtime, _ ->
        assertEquals("DIALOGUE_FRESH", runtime.calls.first().entry)
        assertTrue(runtime.calls.drop(1).dropLast(1).none { it.request != null })
        assertTrue(runtime.calls.any { it.entry == "TOOL_PATH" })
        assertTrue("the kept conversation was released for the unchanged turn", runtime.releases >= 1)
        assertEquals("DIALOGUE_FRESH", runtime.calls.last().entry)
        assertNull(runtime.calls.last().request!!.followUp)
        assertTrue(decisions(a).toString(), decisions(a).contains("FRESH:NOT_TEXT_ONLY"))
    }

    @Test fun theProcessRuntimeStackForwardsTheDialogueEntryThroughItsMemoryAndThermalGates() {
        class Native : LocalModelRuntime {
            override val state = LocalModelState.READY
            override val loadedModel: ImportedLocalModel? = null
            val dialogue = mutableListOf<LocalDialogueRequest>()
            val other = mutableListOf<String>()
            override fun streamDialogue(request: LocalDialogueRequest, callback: LocalInferenceCallback) {
                dialogue += request
                callback.onEvent(LocalInferenceEvent.Completed(LocalInferenceMetrics(1, 1, 1, null, null, 1, 1)))
            }
            override fun stream(prompt: String, callback: LocalInferenceCallback) { other += prompt }
            override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) = Unit
            override fun unload(callback: (Result<Unit>) -> Unit) = Unit
            override fun cancel() = Unit
        }
        val app = (RuntimeEnvironment.getApplication() as Context)
        val native = Native()
        val factory = LocalModelHost::class.java.getDeclaredMethod("thermallyGuarded",
            Context::class.java, LocalModelRuntime::class.java, PowerManager::class.java)
        factory.isAccessible = true
        val guarded = factory.invoke(LocalModelHost, app, native,
            app.getSystemService(Context.POWER_SERVICE) as PowerManager) as LocalModelRuntime
        val request = LocalDialogueRequest("key", "complete prompt", "follow-up")
        val events = mutableListOf<LocalInferenceEvent>()
        guarded.streamDialogue(request) { events += it }
        assertSame("the native entry was reached, not the interface default", request, native.dialogue.singleOrNull())
        assertTrue(native.other.isEmpty())
        assertTrue(events.single() is LocalInferenceEvent.Completed)
    }
}
