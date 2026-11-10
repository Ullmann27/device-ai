package at.heinz.deviceai

import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.TextView
import at.heinz.deviceai.chief.TurnPhase
import at.heinz.deviceai.cognitive.CognitiveTurnTrace
import at.heinz.deviceai.cognitive.DeliberationCancellation
import at.heinz.deviceai.cognitive.LocalDialogueRetention
import at.heinz.deviceai.cockpit.CockpitAnimationBus
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.CockpitAnimationTriggers
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class LocalTurnOutcomeUiTest {
    private class Runtime(val fail: Boolean) : LocalModelRuntime {
        override val state = LocalModelState.READY
        override val loadedModel = ImportedLocalModel("outcome-fixture", "v1", "a".repeat(64), 1,
            LocalModelCapabilities(true, false, false, false, true, 32_768, null,
                setOf(LocalModelBackend.CPU)))
        var beforeReply: () -> Unit = {}
        var calls = 0
        override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) =
            error("Unexpected model load")
        override fun unload(callback: (Result<Unit>) -> Unit) = error("Unexpected model unload")
        override fun stream(prompt: String, callback: LocalInferenceCallback) {
            calls++
            beforeReply()
            if (fail) callback.onEvent(LocalInferenceEvent.Failed("CONTROLLED_MODEL_FAILURE"))
            else {
                callback.onEvent(LocalInferenceEvent.TextDelta("Ein Test vergleicht ein Ergebnis mit einer festgelegten Erwartung."))
                callback.onEvent(LocalInferenceEvent.Completed(LocalInferenceMetrics(0, 0, 0, null, null, null, 1)))
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
    private fun drain(a: AgentActivity) {
        (get(a, "localAgentExecutor") as ExecutorService).submit {}.get(10, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun withRun(fail: Boolean, revokeSession: Boolean = false, cancel: Boolean = false,
        inspect: (AgentActivity, CockpitAnimationTriggers, List<CockpitAnimationEvent>) -> Unit) {
        LocalDialogueRetention.context.clear()
        val a = Robolectric.buildActivity(AgentActivity::class.java).get()
        val runtime = Runtime(fail)
        set(a, "localRuntime", runtime)
        set(a, "receiptStore", FileToolExecutionReceiptStore(File(a.filesDir, "outcome-receipts")))
        set(a, "bootstrapMemory", BootstrapMemoryStore(a))
        set(a, "chatContainer", LinearLayout(a))
        set(a, "activityText", TextView(a))
        set(a, "planCard", LinearLayout(a))
        set(a, "voiceEnabled", false)
        set(a, "foregroundLearningActive", true)
        call(a, "buildComposer")
        val events = mutableListOf<CockpitAnimationEvent>()
        (get(a, "cockpitBus") as CockpitAnimationBus).subscribe { events += it }
        if (revokeSession) runtime.beforeReply = { set(a, "sessionId", "replacement-session") }
        if (cancel) runtime.beforeReply = { (get(a, "activeChatCancellation") as DeliberationCancellation).cancel() }
        try {
            val task = "Erkläre kurz, was ein Test ist."
            call(a, "runLocal", task, task, null, CognitiveTurnTrace(get(a, "sessionId") as String), null, null)
            drain(a)
            assertEquals(1, runtime.calls)
            assertFalse(get(a, "activeLocalRequest") as Boolean)
            val turn = get(a, "activeChiefTurn") as at.heinz.deviceai.chief.TurnLifecycle
            assertEquals(TurnPhase.ENDED, turn.phase)
            assertEquals(1, events.filterIsInstance<CockpitAnimationEvent.TurnEnded>().size)
            inspect(a, get(a, "cockpitTriggers") as CockpitAnimationTriggers, events)
        } finally {
            (get(a, "activeChatCancellation") as? DeliberationCancellation)?.cancel()
            runCatching { drain(a) }
            (get(a, "mainHandler") as Handler).removeCallbacksAndMessages(null)
            for (name in listOf("localAgentExecutor", "backlogExecutor", "learningIoExecutor", "documentIoExecutor"))
                (get(a, name) as ExecutorService).shutdownNow()
            LocalDialogueRetention.context.clear()
        }
    }

    @Test fun failedModelCannotMarkWorkDone() = withRun(fail = true) { _, triggers, events ->
        assertEquals("FAILED", triggers.latestTimeline().single { it.key == "work" }.state.name)
        assertFalse(events.filterIsInstance<CockpitAnimationEvent.TurnEnded>().single().delivered)
    }

    @Test fun acceptedAnswerIsNotVerifiedTaskSuccess() = withRun(fail = false) { a, triggers, events ->
        assertEquals("UNVERIFIED", triggers.latestTimeline().single { it.key == "work" }.state.name)
        assertTrue("Accepted reply must not retain a false failure", triggers.latestTimeline().none {
            it.state.name == "FAILED" || it.state.name == "BLOCKED"
        })
        assertTrue(events.filterIsInstance<CockpitAnimationEvent.TurnEnded>().single().delivered)
        val chat = get(a, "chatContainer") as LinearLayout
        assertTrue(chat.childCount > 0)
    }

    @Test fun replacedSessionCannotReportSuccess() = withRun(fail = false, revokeSession = true) { _, triggers, events ->
        assertEquals("FAILED", triggers.latestTimeline().single { it.key == "work" }.state.name)
        assertFalse(events.filterIsInstance<CockpitAnimationEvent.TurnEnded>().single().delivered)
    }

    @Test fun cancelledGenerationCannotReportSuccess() = withRun(fail = false, cancel = true) { _, triggers, events ->
        assertEquals("FAILED", triggers.latestTimeline().single { it.key == "work" }.state.name)
        assertFalse(events.filterIsInstance<CockpitAnimationEvent.TurnEnded>().single().delivered)
    }
}
