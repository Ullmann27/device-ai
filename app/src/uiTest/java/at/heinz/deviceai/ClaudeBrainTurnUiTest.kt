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
        val prompts = mutableListOf<String>()
        override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) =
            error("Unexpected model load")
        override fun unload(callback: (Result<Unit>) -> Unit) = error("Unexpected model unload")
        override fun stream(prompt: String, callback: LocalInferenceCallback) {
            calls++
            prompts += prompt
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
            // The agent thread may wait for something the main looper still has to deliver (a dialog decision).
            shadowOf(Looper.getMainLooper()).idle()
            val idle = try {
                (get(a, "localAgentExecutor") as ExecutorService).submit {}.get(200, TimeUnit.MILLISECONDS)
                true
            } catch (_: java.util.concurrent.TimeoutException) { false }
            shadowOf(Looper.getMainLooper()).idle()
            if (idle && get(a, "activeChatCancellation") == null && !(get(a, "activeLocalRequest") as Boolean)) return
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
            // The activity's fixed instruction block went to `system`, byte for byte. Since 19.11.2026 two more
            // constant blocks follow it: what the app can do, and which model runs (for the direct question only).
            // A fifth constant block says what web search and code execution over the cloud may do in this turn –
            // here: nothing, both owner switches are off, and no server tool is declared.
            val system = body.getJSONArray("system")
            assertEquals(5, system.length())
            assertEquals(at.heinz.deviceai.localmodel.ClaudeAbilityCatalogue.cloudTools(
                at.heinz.deviceai.localmodel.ClaudeServerToolPolicy.Decision.NONE), system.getJSONObject(4).getString("text"))
            val declared = body.getJSONArray("tools")
            assertTrue((0 until declared.length()).none { declared.getJSONObject(it).has("type") &&
                declared.getJSONObject(it).getString("type") != "custom" })
            assertTrue(system.getJSONObject(1).getString("text").endsWith(ClaudePromptFrame.OWNER_TURN_INSTRUCTIONS))
            assertEquals(at.heinz.deviceai.localmodel.ClaudeAbilityCatalogue.text(), system.getJSONObject(2).getString("text"))
            assertEquals(at.heinz.deviceai.localmodel.ClaudeAbilityCatalogue.runningOn(at.heinz.deviceai.localmodel.ClaudeModel.OPUS_5_5),
                system.getJSONObject(3).getString("text"))
            // With the cloud brain an explanation keeps the registered tools (the model decides, never forced).
            assertTrue(body.has("tools"))
            assertEquals("auto", body.getJSONObject("tool_choice").getString("type"))
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

    @Test fun toolCallFromClaudeStopsAtTheOwnersApprovalDialogAndADeclineGoesBackAsAnError() {
        val closing = "Ich habe die App nicht gestoppt, weil du die Freigabe abgelehnt hast."
        endpoint.enqueue(
            Reply.Stream(listOf(messageStart()) + FakeAnthropicEndpoint.toolUse(0, "toolu_fake_stop", "stop_app",
                "{\"packageOrName\": \"com.example.app\"}") + stop("tool_use")),
            Reply.Stream(listOf(messageStart()) + text(0, closing) + stop("end_turn")),
            // Only used if the answer check asks for one more attempt.
            Reply.Stream(listOf(messageStart()) + text(0, closing) + stop("end_turn")))
        val local = LocalFixture("LOKALE ANTWORT")
        withTurns(claudeBrain(), local) { a ->
            // „App stoppen“ needs the device bridge; since the tool precheck (19.11.2026) a bridge-only tool without it
            // is refused before the approval. Here the bridge is up, and a declined call must never be dispatched.
            set(a, "executor", DeviceToolExecutor(a, { true }, { _, _ -> error("a declined call must not be dispatched") }, null))
            val order = "Stoppe die App com.example.app."
            call(a, "runLocal", order, order, null, CognitiveTurnTrace(get(a, "sessionId") as String), null, null)
            // The proposal waits at the app's own confirmation dialog; nothing runs and nothing more is sent.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            var approval: AlertDialog? = null
            while (approval == null && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                approval = ShadowAlertDialog.getLatestAlertDialog()?.takeIf { it.isShowing }
                if (approval == null) Thread.sleep(20)
            }
            assertNotNull("Claude's tool call must reach the owner's approval dialog", approval)
            val shown = shadowOf(approval!!)
            assertEquals("Aktion freigeben", shown.title.toString())
            assertTrue(shown.message.toString().contains("App stoppen"))
            assertTrue(shown.message.toString().contains("com.example.app"))
            assertEquals(1, endpoint.requests.size)
            assertTrue(endpoint.requests[0].body.has("tools"))

            approval.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            settle(a)
            assertTrue(endpoint.requests.size in 2..3)
            val result = endpoint.requests[1].body.getJSONArray("messages").getJSONObject(2)
                .getJSONArray("content").getJSONObject(0)
            assertEquals("toolu_fake_stop", result.getString("tool_use_id"))
            assertTrue("a declined tool is reported to Claude as an error", result.getBoolean("is_error"))
            assertFalse(result.getString("content").contains("SUCCEEDED"))
            assertEquals(0, local.calls)
            // The app's own outcome line: the tool was declined, and no success is claimed.
            assertTrue(chat(a).toString(), chat(a).any { it.contains("stop_app meldet REJECTED") })
            assertTrue(chat(a).none { it.contains("SUCCEEDED") })
        }
    }

    // ---- owner order 19.11.2026: Continuum in the conversation, the cloud still visible ------------------------

    @Test fun aCloudTurnShowsContinuumAndTheCloudButNeverTheProviderOrAModelId() {
        endpoint.enqueue(Reply.Stream(listOf(messageStart()) + text(0, answer) + stop("end_turn")))
        withTurns(claudeBrain(), LocalFixture("LOKALE ANTWORT")) { a ->
            ask(a, task)
            val visible = chat(a) + (get(a, "activityText") as TextView).text.toString()
            assertTrue(visible.toString(), visible.any { it.contains(answer) })
            assertTrue(visible.toString(), visible.none { it.contains("Claude") || it.contains("CLAUDE") || it.contains("claude-") })
            // The privacy signal stays: every status wording of a cloud turn takes the cloud variant.
            assertEquals("Continuum plant in der Cloud …", call(a, "brainLabel", "Lokales Modell plant …", "Continuum plant in der Cloud …"))
            assertEquals("Cloud", call(a, "brainLabel", "Lokal", "Cloud"))
        }
    }

    // ---- owner wish 19.11.2026: one tone for both brains ------------------------------------------------------

    private fun talkPreferences(a: AgentActivity) = a.applicationContext.getSharedPreferences("continuum-owner-talk", 0)

    @Test fun thePersonalToneReachesTheCloudBrainAsAConstantBlockAndTheNameOnlyWithTheOwnersSwitch() {
        repeat(6) { endpoint.enqueue(Reply.Stream(listOf(messageStart()) + text(0, answer) + stop("end_turn"))) }
        val brain = claudeBrain()
        withTurns(brain, LocalFixture("LOKALE ANTWORT")) { a ->
            talkPreferences(a).edit().clear().putString("name", "Heinz").putString("name_statement", "Nenn mich Heinz")
                .putLong("name_at", 1L).commit()
            call(a, "wireOwnerTalkToCloudBrain")
            ask(a, task)
            ask(a, "Erkläre kurz, was ein Mock ist.")
            val before = endpoint.requests.size
            val first = endpoint.requests.first().body.getJSONArray("system")
            val second = endpoint.requests.last().body.getJSONArray("system")
            // The tone block keeps its place; the block about the cloud's web search and code execution follows it.
            assertEquals(6, first.length())
            assertTrue(first.getJSONObject(5).getString("text").startsWith("CLOUD-WERKZEUGE"))
            val tone = first.getJSONObject(4).getString("text")
            val style = at.heinz.deviceai.cognitive.OwnerToneStyle.claudeBlock(
                at.heinz.deviceai.cognitive.OwnerConversationTone.PERSONAL, null)!!
            assertTrue(tone, tone.startsWith(style))
            // Since 19.11.2026 the same block goes on with the true facts of the conversation and its rules
            // (OwnerConversationFacts): no invented memories, no claimed feelings, at most one question.
            val rules = tone.removePrefix(style)
            assertTrue(rules, rules.contains("Keine erfundenen Erinnerungen") && rules.contains("Behaupte keine eigenen Gefühle") &&
                rules.contains("Höchstens eine Frage pro Antwort"))
            assertFalse("his name stays on the device without his switch", first.toString().contains("Heinz"))
            // Byte-stable from turn to turn, and the on-device line is not sent a second time in the user message.
            assertEquals(first.toString(), second.toString())
            assertFalse(endpoint.requests.any { it.body.getJSONArray("messages").toString().contains("PERSÖNLICHER TON") })
            talkPreferences(a).edit().putBoolean("name_to_cloud", true).commit()
            ask(a, "Erkläre kurz, was ein Stub ist.")
            assertTrue(endpoint.requests.size > before)
            assertTrue(endpoint.requests.last().body.getJSONArray("system").getJSONObject(4).getString("text").contains("„Heinz“"))
            // „Sachlich“: no tone block at all.
            talkPreferences(a).edit().putString("tone", "FACTUAL").commit()
            assertNull(brain.ownerStyle())
        }
    }

    @Test fun theOnDeviceModelGetsTheSameToneAsOneShortLineAndNoneInSachlich() {
        val local = LocalFixture("Lokal: Ein Test prüft, ob ein Ergebnis einer Erwartung entspricht.")
        withTurns(testBrain(), local) { a ->
            talkPreferences(a).edit().clear().putString("name", "Heinz").putString("name_statement", "Nenn mich Heinz")
                .putLong("name_at", 1L).commit()
            ask(a, "Was hältst du von Tests?")
            val personal = local.prompts.last()
            assertTrue(personal, personal.contains("PERSÖNLICHER TON – nur die Art zu reden: Sprich mit Heinz wie ein alter Freund"))
            assertEquals(1, Regex("PERSÖNLICHER TON").findAll(personal).count())
            talkPreferences(a).edit().putString("tone", "FACTUAL").commit()
            ask(a, "Was hältst du von Mocks?")
            assertFalse(local.prompts.last().contains("PERSÖNLICHER TON"))
            assertTrue(endpoint.requests.isEmpty())
        }
    }

    private fun recall() = at.heinz.deviceai.cognitive.LocalGroundedRecall.boundedOrNull(
        "Der Besitzer hat notiert: Der Prüftermin ist am Freitag.", "Bestätigte Erinnerung · Lernkern")!!

    @Test fun confirmedMemoryIsNotSentToClaudeWithoutTheSeparateOptIn() {
        val local = LocalFixture("LOKALE ANTWORT")
        val brain = claudeBrain()
        withTurns(brain, local) { a ->
            call(a, "runLocal", task, task, recall(), CognitiveTurnTrace(get(a, "sessionId") as String), null, null)
            settle(a)
            assertTrue(chat(a).any { it.contains(ClaudeBrainFailure.ownerMessage(ClaudeBrainFailure.LOCAL_ONLY_MEMORY)) })
            assertTrue(endpoint.requests.isEmpty())
            assertEquals(0, local.calls)
        }
    }

    @Test fun confirmedMemoryGoesToClaudeOnlyAfterTheOwnerOptedIn() {
        endpoint.enqueue(Reply.Stream(listOf(messageStart()) + text(0, answer) + stop("end_turn")),
            Reply.Stream(listOf(messageStart()) + text(0, answer) + stop("end_turn")))
        val local = LocalFixture("LOKALE ANTWORT")
        val brain = claudeBrain()
        assertTrue(brain.setShareLocalMemory(true))
        withTurns(brain, local) { a ->
            call(a, "runLocal", task, task, recall(), CognitiveTurnTrace(get(a, "sessionId") as String), null, null)
            settle(a)
            assertTrue(endpoint.requests.isNotEmpty())
            assertTrue(endpoint.requests[0].body.getJSONArray("messages").getJSONObject(0).getString("content")
                .contains("Der Prüftermin ist am Freitag."))
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
