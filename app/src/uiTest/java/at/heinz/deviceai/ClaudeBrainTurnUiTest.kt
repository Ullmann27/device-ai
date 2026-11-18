package at.heinz.deviceai

import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import at.heinz.deviceai.cognitive.CognitiveTurnTrace
import at.heinz.deviceai.cognitive.DeliberationCancellation
import at.heinz.deviceai.cognitive.LocalDialogueRetention
import at.heinz.deviceai.localmodel.*
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.TEST_KEY
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.error
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.messageStart
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.stop
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.text
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Reply
import org.junit.After
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
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/**
 * A real owner turn through AgentActivity.runLocal with Claude selected as the brain. The Claude runtime talks to
 * the local fake endpoint; the on-device runtime is a counting fixture, so every assertion "nothing reached the
 * local model" and "nothing was sent" is exact. No real API call.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class ClaudeBrainTurnUiTest {
    private val answer = "Ein Test vergleicht ein Ergebnis mit einer festgelegten Erwartung."
    private val task = "Erkläre kurz, was ein Test ist."
    private val endpoint = FakeAnthropicEndpoint()

    private class LocalFixture(val reply: String) : LocalModelRuntime {
        override val state = LocalModelState.READY
        override val loadedModel = ImportedLocalModel("local-fixture", "v1", "a".repeat(64), 1,
            LocalModelCapabilities(true, false, false, false, true, 32_768, null, setOf(LocalModelBackend.CPU)))
        var calls = 0
        override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) =
            error("Unexpected model load")
        override fun unload(callback: (Result<Unit>) -> Unit) = error("Unexpected model unload")
        override fun stream(prompt: String, callback: LocalInferenceCallback) {
            calls++
            callback.onEvent(LocalInferenceEvent.TextDelta(reply))
            callback.onEvent(LocalInferenceEvent.Completed(LocalInferenceMetrics(0, 0, 0, null, null, null, 1)))
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

    private fun texts(view: View): List<String> = when (view) {
        is TextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun chat(a: AgentActivity) = texts(get(a, "chatContainer") as LinearLayout)

    private fun settle(a: AgentActivity) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        do {
            (get(a, "localAgentExecutor") as ExecutorService).submit {}.get(20, TimeUnit.SECONDS)
            shadowOf(Looper.getMainLooper()).idle()
            if (get(a, "activeChatCancellation") == null && !(get(a, "activeLocalRequest") as Boolean)) return
            Thread.sleep(20)
        } while (System.nanoTime() < deadline)
        fail("turn did not finish")
    }

    private fun claudeBrain(withKey: Boolean = true): ClaudeBrain {
        val brain = testBrain(endpoint = ClaudeModelRuntime.Endpoint(baseUrl = endpoint.baseUrl, maxRetries = 0,
            connectTimeout = Duration.ofSeconds(3), readTimeout = Duration.ofSeconds(8),
            requestTimeout = Duration.ofSeconds(20)))
        if (withKey) assertTrue(brain.saveKey(TEST_KEY))
        assertTrue(brain.confirmConsent(shareLocalMemory = false))
        assertTrue(brain.selectBrain(BrainKind.CLAUDE))
        return brain
    }

    private fun withTurns(brain: ClaudeBrain, local: LocalFixture, block: (AgentActivity) -> Unit) {
        LocalDialogueRetention.context.clear()
        val a = Robolectric.buildActivity(AgentActivity::class.java).get()
        set(a, "localRuntime", SelectableModelRuntime(local, brain.runtime) { brain.claudeSelected })
        set(a, "claudeBrain", brain)
        set(a, "receiptStore", FileToolExecutionReceiptStore(File(a.filesDir, "claude-turn-receipts")))
        set(a, "bootstrapMemory", BootstrapMemoryStore(a))
        set(a, "chatContainer", LinearLayout(a))
        set(a, "activityText", TextView(a))
        set(a, "planCard", LinearLayout(a))
        set(a, "voiceEnabled", false)
        set(a, "foregroundLearningActive", true)
        call(a, "buildComposer")
        try {
            block(a)
        } finally {
            (get(a, "activeChatCancellation") as? DeliberationCancellation)?.cancel()
            brain.runtime.cancel()
            (get(a, "mainHandler") as Handler).removeCallbacksAndMessages(null)
            for (name in listOf("localAgentExecutor", "backlogExecutor", "learningIoExecutor", "documentIoExecutor"))
                (get(a, name) as ExecutorService).shutdownNow()
            LocalDialogueRetention.context.clear()
        }
    }

    private fun ask(a: AgentActivity, text: String) {
        call(a, "runLocal", text, text, null, CognitiveTurnTrace(get(a, "sessionId") as String), null, null)
        settle(a)
    }

    @After fun tearDown() = endpoint.close()

    @Test fun ownerTurnIsAnsweredByClaudeAndNeverTouchesTheLocalModel() {
        endpoint.enqueue(Reply.Stream(listOf(messageStart()) + text(0, answer) + stop("end_turn")))
        val local = LocalFixture("LOKALE ANTWORT")
        withTurns(claudeBrain(), local) { a ->
            ask(a, task)
            assertTrue(chat(a).toString(), chat(a).any { it.contains(answer) })
            assertEquals(0, local.calls)
            val request = endpoint.requests.single()
            assertEquals("Bearer $TEST_KEY", request.headers["authorization"])
            val body = request.body
            assertEquals("claude-opus-5-5", body.getString("model"))
            val user = body.getJSONArray("messages").getJSONObject(0).getString("content")
            assertTrue(user.contains(task))
            // The activity's fixed instruction block went to `system`, byte for byte.
            val system = body.getJSONArray("system")
            assertEquals(2, system.length())
            assertTrue(system.getJSONObject(1).getString("text").endsWith(ClaudePromptFrame.OWNER_TURN_INSTRUCTIONS))
            assertFalse(user.contains(ClaudePromptFrame.OWNER_TURN_INSTRUCTIONS))
            // Nothing on screen or in the log shows the key.
            assertTrue((chat(a) + (get(a, "activityText") as TextView).text.toString()).none { it.contains("sk-ant") })
        }
    }

    @Test fun switchingBackToLocalWorksInTheSameActivityWithoutRestart() {
        endpoint.enqueue(Reply.Stream(listOf(messageStart()) + text(0, answer) + stop("end_turn")))
        val local = LocalFixture("Lokal: Ein Test prüft, ob ein Ergebnis einer Erwartung entspricht.")
        val brain = claudeBrain()
        withTurns(brain, local) { a ->
            ask(a, task)
            assertEquals(1, endpoint.requests.size)
            assertEquals(0, local.calls)
            assertTrue(brain.selectBrain(BrainKind.LOCAL))
            ask(a, task)
            assertTrue(local.calls >= 1)
            assertEquals("nothing more is sent after switching back", 1, endpoint.requests.size)
            assertTrue(chat(a).any { it.contains(local.reply) })
        }
    }

    private fun refusedBeforeSending(brain: ClaudeBrain, question: String, expected: String) {
        val local = LocalFixture("LOKALE ANTWORT")
        withTurns(brain, local) { a ->
            ask(a, question)
            assertTrue(chat(a).toString(), chat(a).any {
                it.contains(ClaudeBrainFailure.ownerMessage(expected))
            })
            assertTrue("nothing may be sent", endpoint.requests.isEmpty())
            assertEquals("no silent local fallback", 0, local.calls)
            assertTrue("Claude stays selected until the owner decides", brain.claudeSelected)
            // The switch back is offered, never performed by the app.
            val offer = ShadowAlertDialog.getLatestAlertDialog()
            assertNotNull(offer)
            assertTrue(offer.isShowing)
            assertEquals("AUF LOKAL UMSCHALTEN", offer.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())
            assertEquals("BEI CLAUDE BLEIBEN", offer.getButton(AlertDialog.BUTTON_NEGATIVE).text.toString())
            assertFalse(chat(a).any { it.contains("LOKALE ANTWORT") })
        }
    }

    @Test fun messageThatExcludesTheCloudIsNotSentAndNotAnsweredLocallyBehindTheOwnersBack() =
        refusedBeforeSending(claudeBrain(), "Antworte bitte nur lokal: Was ist ein Test?",
            ClaudeBrainFailure.LOCAL_ONLY_OWNER_VETO)

    @Test fun selectedClaudeWithoutAKeySaysSoAndOffersTheSwitchBack() =
        refusedBeforeSending(claudeBrain(withKey = false), task, ClaudeBrainFailure.NO_API_KEY)

    @Test fun rejectedKeyIsReportedInGermanWithoutFallback() {
        endpoint.enqueue(Reply.Error(401, error("authentication_error", "invalid x-api-key")))
        val local = LocalFixture("LOKALE ANTWORT")
        withTurns(claudeBrain(), local) { a ->
            ask(a, task)
            assertTrue(chat(a).toString(), chat(a).any {
                it.contains(ClaudeBrainFailure.ownerMessage(ClaudeBrainFailure.INVALID_API_KEY))
            })
            assertEquals(0, local.calls)
            assertEquals(1, endpoint.requests.size)
            assertFalse(chat(a).any { it.contains("Lokaler Modellfehler") })
        }
    }

    @Test fun refusalDiscardsTheTextThatWasAlreadyStreamed() {
        endpoint.enqueue(Reply.Stream(listOf(messageStart()) + text(0, "Dieser Anfang ist keine Antwort.") +
            stop("refusal", 5, org.json.JSONObject().put("type", "refusal").put("category", "cyber"))))
        val local = LocalFixture("LOKALE ANTWORT")
        withTurns(claudeBrain(), local) { a ->
            ask(a, task)
            assertTrue(chat(a).any { it.contains(ClaudeBrainFailure.ownerMessage(ClaudeBrainFailure.REFUSAL)) })
            assertFalse(chat(a).toString(), chat(a).any { it.contains("Dieser Anfang ist keine Antwort.") })
            assertEquals(0, local.calls)
        }
    }

    @Test fun localBrainIsUntouchedByTheNewGates() {
        // Default settings: LOCAL. A message that excludes the cloud is an ordinary local turn.
        val brain = testBrain()
        val local = LocalFixture("Lokal: Ein Test prüft, ob ein Ergebnis einer Erwartung entspricht.")
        withTurns(brain, local) { a ->
            ask(a, "Antworte bitte nur lokal: Was ist ein Test?")
            assertTrue(local.calls >= 1)
            assertTrue(endpoint.requests.isEmpty())
            assertTrue(chat(a).any { it.contains(local.reply) })
        }
    }
}
