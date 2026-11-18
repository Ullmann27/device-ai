package at.heinz.deviceai

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import at.heinz.deviceai.cognitive.AnswerContinuationController
import at.heinz.deviceai.cognitive.CognitiveTurnTrace
import at.heinz.deviceai.cognitive.DeliberationCancellation
import at.heinz.deviceai.cognitive.LocalDialogueRetention
import at.heinz.deviceai.cognitive.LocalPartialAnswerPresentation
import at.heinz.deviceai.cockpit.CockpitAnimationBus
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
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
 * Code64: bounded automatic continuation through the real local turn lifecycle (runLocal → orchestrator → delivery →
 * re-entry into the existing „weiter“ path) with a fake runtime that stops at the output limit. Robolectric, HOST.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class LocalAnswerContinuationUiTest {
    private sealed class Step {
        data class Limited(val text: String) : Step()
        data class Complete(val text: String) : Step()
        object Fail : Step()
    }

    private class Runtime(private val steps: List<Step>) : LocalModelRuntime {
        override val state = LocalModelState.READY
        override val loadedModel = ImportedLocalModel("continuation-fixture", "v1", "a".repeat(64), 1,
            LocalModelCapabilities(true, false, false, false, true, 32_768, null, setOf(LocalModelBackend.CPU)))
        val prompts = mutableListOf<String>()
        var beforeReply: (Int) -> Unit = {}
        override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) =
            error("Unexpected model load")
        override fun unload(callback: (Result<Unit>) -> Unit) = error("Unexpected model unload")
        override fun stream(prompt: String, callback: LocalInferenceCallback) {
            prompts += prompt
            beforeReply(prompts.size)
            when (val step = steps[prompts.size - 1]) {
                is Step.Limited -> {
                    callback.onEvent(LocalInferenceEvent.TextDelta(step.text))
                    callback.onEvent(LocalInferenceEvent.Failed(LocalModelGenerationPolicy.OUTPUT_TOKEN_LIMIT))
                }
                is Step.Complete -> {
                    callback.onEvent(LocalInferenceEvent.TextDelta(step.text))
                    callback.onEvent(LocalInferenceEvent.Completed(LocalInferenceMetrics(0, 0, 0, null, null, null, 1)))
                }
                Step.Fail -> callback.onEvent(LocalInferenceEvent.Failed("CONTROLLED_MODEL_FAILURE"))
            }
        }
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
    /** Every segment is one executor run plus one delivery on the main looper. */
    private fun drain(a: AgentActivity) = repeat(6) {
        (get(a, "localAgentExecutor") as ExecutorService).submit {}.get(10, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private val first = "Ein Unit-Test prüft eine einzelne Funktion und vergleicht ihr Ergebnis mit einer"
    private val second = "festgelegten Erwartung. Ein Integrationstest prüft dagegen das Zusammenspiel mehrerer"
    private val third = "Bausteine über ihre echten Schnittstellen."
    private val task = "Erkläre ausführlich den Unterschied zwischen Unit-Tests und Integrationstests."

    private fun withTurn(vararg steps: Step, arrange: (AgentActivity, Runtime) -> Unit = { _, _ -> },
                         inspect: (AgentActivity, Runtime, String, List<CockpitAnimationEvent>) -> Unit) {
        LocalDialogueRetention.context.clear()
        val a = Robolectric.buildActivity(AgentActivity::class.java).get()
        val runtime = Runtime(steps.toList())
        set(a, "localRuntime", runtime)
        set(a, "receiptStore", FileToolExecutionReceiptStore(File(a.filesDir, "continuation-receipts")))
        set(a, "bootstrapMemory", BootstrapMemoryStore(a))
        set(a, "chatContainer", LinearLayout(a))
        set(a, "activityText", TextView(a))
        set(a, "planCard", LinearLayout(a))
        set(a, "voiceEnabled", false)
        set(a, "foregroundLearningActive", true)
        call(a, "buildComposer")
        val events = mutableListOf<CockpitAnimationEvent>()
        (get(a, "cockpitBus") as CockpitAnimationBus).subscribe { events += it }
        arrange(a, runtime)
        try {
            call(a, "runLocal", task, task, null, CognitiveTurnTrace(get(a, "sessionId") as String), null, null)
            drain(a)
            assertFalse("the lane is free again", get(a, "busy") as Boolean)
            assertFalse(get(a, "activeLocalRequest") as Boolean)
            assertFalse((get(a, "answerContinuation") as AnswerContinuationController).pending)
            inspect(a, runtime, text(get(a, "chatContainer") as View), events)
        } finally {
            (get(a, "activeChatCancellation") as? DeliberationCancellation)?.cancel()
            runCatching { drain(a) }
            (get(a, "mainHandler") as Handler).removeCallbacksAndMessages(null)
            for (name in listOf("localAgentExecutor", "backlogExecutor", "learningIoExecutor", "documentIoExecutor"))
                (get(a, name) as ExecutorService).shutdownNow()
            LocalDialogueRetention.context.clear()
        }
    }

    private fun count(text: String, part: String) = text.windowed(part.length).count { it == part }

    @Test fun twoLimitedSegmentsAreContinuedAndShownAsOneAnswer() =
        withTurn(Step.Limited(first), Step.Limited(second), Step.Complete(third)) { a, runtime, visible, events ->
            assertEquals(visible, 3, runtime.prompts.size)
            assertTrue(visible, visible.contains("$first $second $third"))
            assertEquals("each part is shown once", 1, count(visible, "vergleicht ihr Ergebnis"))
            assertFalse(visible, visible.contains(LocalPartialAnswerPresentation.CONTINUE_HINT))
            assertFalse("the finished turn no longer claims a running continuation",
                (get(a, "activityText") as TextView).text.toString().contains("Antwort wird fortgesetzt"))
            // The re-entered turns are the existing continuation path with the end of the answer so far …
            assertTrue(runtime.prompts[1], runtime.prompts[1].contains("vergleicht ihr Ergebnis mit einer"))
            assertTrue(runtime.prompts[2], runtime.prompts[2].contains("das Zusammenspiel mehrerer"))
            // … and the visible work state names them truthfully instead of an owner command „weiter“.
            val commands = events.filterIsInstance<CockpitAnimationEvent.CommandFlyIn>().map { it.toString() }
            assertEquals(commands.toString(), 3, commands.size)
            assertTrue(commands[1], commands[1].contains("Antwort wird fortgesetzt (2/3)"))
            assertTrue(commands[2], commands[2].contains("Antwort wird fortgesetzt (3/3)"))
            assertTrue(commands.toString(), commands.none { it.contains("weiter") })
        }

    @Test fun afterTheLastAllowedSegmentTheAnswerComesFirstThenTheManualHint() =
        withTurn(Step.Limited(first), Step.Limited(second), Step.Limited("Bausteine über ihre echten"),
            Step.Complete("Ein vierter Abschnitt darf nicht erzeugt werden.")) { _, runtime, visible, _ ->
            assertEquals(visible, 3, runtime.prompts.size)
            assertTrue(visible, visible.contains("$first $second Bausteine über ihre echten …\n\n" +
                LocalPartialAnswerPresentation.CONTINUE_HINT))
            assertEquals(1, count(visible, LocalPartialAnswerPresentation.CONTINUE_HINT))
            assertFalse(visible, visible.contains("vierter Abschnitt"))
        }

    @Test fun stopDuringTheContinuationShowsThePartThatWasGenerated() =
        withTurn(Step.Limited(first), Step.Complete("$second $third"), arrange = { a, runtime ->
            runtime.beforeReply = { call -> if (call == 2) (get(a, "activeChatCancellation") as DeliberationCancellation).cancel() }
        }) { _, runtime, visible, _ ->
            assertEquals(visible, 2, runtime.prompts.size)
            assertTrue(visible, visible.contains("$first …\n\n" + LocalPartialAnswerPresentation.CONTINUE_HINT))
            assertFalse("the stopped segment is not shown", visible.contains("Zusammenspiel mehrerer"))
        }

    @Test fun aFailingSecondSegmentShowsThePartThenTheFailure() =
        withTurn(Step.Limited(first), Step.Fail, Step.Fail) { _, _, visible, _ ->
            val part = visible.indexOf("$first …\n\n" + LocalPartialAnswerPresentation.CONTINUE_HINT)
            val failure = visible.indexOf("Lokaler Modellfehler")
            assertTrue(visible, part >= 0 && failure > part)
        }

    @Test fun aNewOwnerMessageDuringTheContinuationEndsItAfterTheRunningSegment() =
        withTurn(Step.Limited(first), Step.Limited(second), Step.Complete(third), arrange = { a, runtime ->
            runtime.beforeReply = { call -> if (call == 2) set(a, "ownerReplyRevision", (get(a, "ownerReplyRevision") as Long) + 1) }
        }) { _, runtime, visible, _ ->
            assertEquals(visible, 2, runtime.prompts.size)
            assertTrue(visible, visible.contains("$first $second …\n\n" + LocalPartialAnswerPresentation.CONTINUE_HINT))
        }

    @Test fun aSingleCompleteAnswerIsUnchanged() =
        withTurn(Step.Complete("$first $second $third")) { _, runtime, visible, events ->
            assertEquals(1, runtime.prompts.size)
            assertTrue(visible, visible.contains("$first $second $third"))
            assertEquals(1, events.filterIsInstance<CockpitAnimationEvent.CommandFlyIn>().size)
        }
}
