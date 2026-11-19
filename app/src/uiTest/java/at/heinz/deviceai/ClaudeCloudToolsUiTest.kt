package at.heinz.deviceai

import android.app.Activity
import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import at.heinz.deviceai.cognitive.ApprovedWebToolGate
import at.heinz.deviceai.cognitive.CognitiveTurnTrace
import at.heinz.deviceai.cognitive.DeliberationCancellation
import at.heinz.deviceai.cognitive.InternetAccessMode
import at.heinz.deviceai.cognitive.InternetConsentPolicy
import at.heinz.deviceai.cognitive.LocalDialogueRetention
import at.heinz.deviceai.cognitive.ResearchMode
import at.heinz.deviceai.cognitive.WebToolRequest
import at.heinz.deviceai.localmodel.*
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.TEST_KEY
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.bashResult
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.citedText
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.messageStart
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.resultBlock
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.searchCitation
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.serverToolUse
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.stop
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.text
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.webSearchError
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.webSearchResult
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Reply
import at.heinz.deviceai.ui.ClaudeBrainDialog
import org.json.JSONObject
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
 * Owner order 19.11.2026: web search and code execution over the cloud, as two switches in the brain settings. The
 * switches in the real dialog, their stored state, and a real owner turn through AgentActivity.runLocal whose answer
 * comes from the cloud's search: the answer stays Continuum's, the marker stays „CONTINUUM · CLOUD“, the cited page
 * appears in the sources card. The Claude runtime talks to the local fake endpoint only – no real API call, no real
 * search, no real code execution.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class ClaudeCloudToolsUiTest {
    private val endpoint = FakeAnthropicEndpoint()
    private val wetter = "Wetter Wien – Vorhersage" to "https://wetter.example.org/wien"
    private val question = "Wie wird das Wetter morgen in Wien?"

    private class LocalFixture : LocalModelRuntime {
        override val state = LocalModelState.READY
        override val loadedModel = ImportedLocalModel("local-fixture", "v1", "a".repeat(64), 1,
            LocalModelCapabilities(true, false, false, false, true, 32_768, null, setOf(LocalModelBackend.CPU)))
        var calls = 0
        override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) = error("Unexpected model load")
        override fun unload(callback: (Result<Unit>) -> Unit) = error("Unexpected model unload")
        override fun stream(prompt: String, callback: LocalInferenceCallback) { calls++; error("The local model must not answer") }
        override fun respondToTools(responses: List<LocalToolResponse>, callback: LocalInferenceCallback) = error("No tool in fixture")
        override fun cancel() = Unit
    }

    private class Host(var mode: InternetAccessMode? = InternetAccessMode.ASK) : ClaudeBrainDialog.Host {
        val log = mutableListOf<String>()
        override fun busy() = false
        override fun brainChanged(previous: BrainKind) = Unit
        override fun log(line: String) { log += line }
        override fun internetMode() = mode
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun texts(view: View): List<String> = when (view) {
        is EditText -> emptyList()
        is TextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun <T : View> AlertDialog.tagged(tag: String): T = window!!.decorView.findViewWithTag(tag)
    private fun AlertDialog.tap(tag: String) { tagged<View>(tag).performClick(); idle() }
    private fun AlertDialog.text(tag: String) = tagged<TextView>(tag).text.toString()
    private fun AlertDialog.message() = findViewById<TextView>(android.R.id.message).text.toString()

    private fun get(a: AgentActivity, name: String): Any? = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(a)
    private fun set(a: AgentActivity, name: String, value: Any?) = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.set(a, value)
    private fun call(a: AgentActivity, name: String, vararg args: Any?) =
        AgentActivity::class.java.declaredMethods.single { it.name == name && it.parameterCount == args.size }
            .apply { isAccessible = true }.invoke(a, *args)
    private fun chat(a: AgentActivity) = texts(get(a, "chatContainer") as LinearLayout)
    private fun log(a: AgentActivity) = (get(a, "activityText") as TextView).text.toString()

    private fun settle(a: AgentActivity) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        do {
            idle()
            val quiet = try {
                (get(a, "localAgentExecutor") as ExecutorService).submit {}.get(200, TimeUnit.MILLISECONDS)
                true
            } catch (_: java.util.concurrent.TimeoutException) { false }
            idle()
            if (quiet && get(a, "activeChatCancellation") == null && !(get(a, "activeLocalRequest") as Boolean)) return
            Thread.sleep(20)
        } while (System.nanoTime() < deadline)
        fail("turn did not finish")
    }

    private fun cloudBrain(): ClaudeBrain {
        val brain = testBrain(endpoint = ClaudeModelRuntime.Endpoint(baseUrl = endpoint.baseUrl, maxRetries = 0,
            connectTimeout = Duration.ofSeconds(3), readTimeout = Duration.ofSeconds(8),
            requestTimeout = Duration.ofSeconds(20)))
        assertTrue(brain.saveKey(TEST_KEY))
        assertTrue(brain.confirmConsent(shareLocalMemory = false))
        assertTrue(brain.selectBrain(BrainKind.CLAUDE))
        return brain
    }

    private fun withTurns(brain: ClaudeBrain, block: (AgentActivity, LocalFixture) -> Unit) {
        LocalDialogueRetention.context.clear()
        val local = LocalFixture()
        val a = Robolectric.buildActivity(AgentActivity::class.java).get()
        set(a, "localRuntime", SelectableModelRuntime(local, brain.runtime) { brain.claudeSelected })
        set(a, "claudeBrain", brain)
        set(a, "receiptStore", FileToolExecutionReceiptStore(File(a.filesDir, "cloud-tool-receipts")))
        set(a, "bootstrapMemory", BootstrapMemoryStore(a))
        set(a, "chatContainer", LinearLayout(a))
        set(a, "activityText", TextView(a))
        set(a, "planCard", LinearLayout(a))
        set(a, "voiceEnabled", false)
        set(a, "foregroundLearningActive", true)
        call(a, "buildComposer")
        try {
            block(a, local)
        } finally {
            (get(a, "activeChatCancellation") as? DeliberationCancellation)?.cancel()
            brain.runtime.cancel()
            (get(a, "mainHandler") as Handler).removeCallbacksAndMessages(null)
            for (name in listOf("localAgentExecutor", "backlogExecutor", "learningIoExecutor", "documentIoExecutor"))
                (get(a, name) as ExecutorService).shutdownNow()
            LocalDialogueRetention.context.clear()
        }
    }

    private fun ask(a: AgentActivity, sentence: String) {
        call(a, "runLocal", sentence, sentence, null, CognitiveTurnTrace(get(a, "sessionId") as String), null, null)
        settle(a)
    }

    private fun searched(answer: String) = Reply.Stream(listOf(messageStart(input = 900)) +
        serverToolUse(0, "srvtoolu_01", "web_search", "{\"query\":\"wetter wien morgen\"}") +
        resultBlock(1, webSearchResult("srvtoolu_01", wetter)) +
        citedText(2, answer, searchCitation(wetter.second, wetter.first, "Wien: 4 °C, bewölkt")) +
        stop("end_turn", 70, serverToolUse = JSONObject().put("web_search_requests", 1)))

    private fun serverTypes(request: FakeAnthropicEndpoint.Request): List<String> = request.body.optJSONArray("tools")?.let { tools ->
        (0 until tools.length()).map { tools.getJSONObject(it).optString("type") }.filter { it.startsWith("web_") || it.startsWith("code_execution") }
    }.orEmpty()

    @After fun tearDown() = endpoint.close()

    // ── the two switches in the brain settings ──────────────────────────────────────────

    @Test fun bothSwitchesAreOffAndTurningOneOnShowsWhatLeavesTheDeviceAndWhatItCostsFirst() {
        val brain = testBrain()
        val host = Host()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val dialog = ClaudeBrainDialog.show(activity, brain, host).also { idle() }
        assertEquals("Websuche und Seitenabruf über die Cloud: AUS", dialog.text("claude-cloud-web"))
        assertEquals("Code-Ausführung in der Cloud (Berechnungen, Datenanalyse, Dateien erzeugen): AUS", dialog.text("claude-cloud-code"))
        assertFalse(dialog.tagged<TextView>("claude-cloud-web").isSelected)
        assertTrue(dialog.text("claude-cloud-tools-status").contains("Beide sind aus"))
        assertTrue("the month's counts are shown, also at zero",
            dialog.text("claude-cloud-tools-status").contains("0 Websuchen (0,00 USD, im Monatslimit enthalten)"))

        // Without the stored consent nothing can be switched on.
        dialog.tap("claude-cloud-web")
        assertFalse(brain.settings.cloudWebSearch)
        assertTrue(dialog.text("claude-brain-message").contains("erst nach der Zustimmung"))

        assertTrue(brain.saveKey(TEST_KEY) && brain.confirmConsent(false) && brain.selectBrain(BrainKind.CLAUDE))
        dialog.tap("claude-cloud-web")
        val confirmWeb = ShadowAlertDialog.getLatestAlertDialog()
        assertNotSame(dialog, confirmWeb)
        assertEquals(ClaudeServerToolTexts.WEB_EXPLANATION, confirmWeb.message())
        assertTrue(confirmWeb.message().contains("zusätzliches API-Guthaben"))
        // Leaving it off changes nothing.
        confirmWeb.getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); idle()
        assertFalse(brain.settings.cloudWebSearch)
        dialog.tap("claude-cloud-web")
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle()
        assertTrue(brain.settings.cloudWebSearch)
        assertEquals("✓ Websuche und Seitenabruf über die Cloud: EIN", dialog.text("claude-cloud-web"))
        assertTrue(dialog.tagged<TextView>("claude-cloud-web").isSelected)
        assertTrue(dialog.text("claude-cloud-tools-status").contains("ich frage dich vor einer Suche"))

        dialog.tap("claude-cloud-code")
        val confirmCode = ShadowAlertDialog.getLatestAlertDialog()
        assertEquals(ClaudeServerToolTexts.CODE_EXPLANATION, confirmCode.message())
        assertTrue(confirmCode.message().contains("NICHT in deinem Monatslimit enthalten"))
        confirmCode.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle()
        assertTrue(brain.settings.cloudCodeExecution)
        assertTrue(dialog.text("claude-cloud-tools-status").contains("Code-Ausführung: aktiv"))

        // Off is one tap, with no further question.
        dialog.tap("claude-cloud-web")
        dialog.tap("claude-cloud-code")
        assertFalse(brain.settings.cloudWebSearch || brain.settings.cloudCodeExecution)
        assertEquals(listOf("GEHIRN · Websuche und Seitenabruf über die Cloud: EIN",
            "GEHIRN · Code-Ausführung in der Cloud (Berechnungen, Datenanalyse, Dateien erzeugen): EIN",
            "GEHIRN · Websuche und Seitenabruf über die Cloud: AUS",
            "GEHIRN · Code-Ausführung in der Cloud (Berechnungen, Datenanalyse, Dateien erzeugen): AUS"), host.log)
        assertTrue("nothing was sent by switching", endpoint.requests.isEmpty())
    }

    @Test fun theSwitchesAreStoredAndAStoredSwitchWithoutConsentIsNotHonoured() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val store = PreferencesClaudeSettingsStore(activity)
        assertFalse(store.load().cloudWebSearch || store.load().cloudCodeExecution)
        assertTrue(store.save(ClaudeBrainSettings(brain = BrainKind.CLAUDE, consentGiven = true, cloudWebSearch = true)))
        PreferencesClaudeSettingsStore(activity).load().let { loaded ->
            assertTrue(loaded.cloudWebSearch)
            assertFalse(loaded.cloudCodeExecution)
        }
        assertTrue(store.save(ClaudeBrainSettings(consentGiven = true, cloudCodeExecution = true)))
        assertTrue(PreferencesClaudeSettingsStore(activity).load().cloudCodeExecution)
        // The stored consent is gone (edited or damaged preferences): both switches read as off.
        activity.getSharedPreferences("continuum.claude.brain", Activity.MODE_PRIVATE).edit().putBoolean("consent", false)
            .putBoolean("cloud_web_search", true).commit()
        PreferencesClaudeSettingsStore(activity).load().let { loaded ->
            assertFalse(loaded.cloudWebSearch || loaded.cloudCodeExecution)
        }
    }

    // ── a real owner turn ───────────────────────────────────────────────────────────────

    @Test fun aSearchedAnswerStaysContinuumsKeepsTheCloudMarkerAndShowsItsSource() {
        endpoint.enqueue(searched("Morgen wird es in Wien 4 Grad kalt und bewölkt."))
        val brain = cloudBrain()
        brain.webTurn.internetMode = { InternetAccessMode.ALWAYS_ON }
        assertTrue(brain.setCloudWebSearch(true))
        withTurns(brain) { a, local ->
            ask(a, question)
            val shown = chat(a)
            assertTrue(shown.toString(), shown.any { it.contains("Morgen wird es in Wien 4 Grad kalt und bewölkt.") &&
                it.contains("Über die Cloud im Internet nachgesehen: 1 Suche.") })
            // The cited page, through the app's own sources card: title and address from the response, nothing else.
            assertTrue(shown.toString(), shown.contains("QUELLEN"))
            assertTrue(shown.toString(), shown.any { it == "1. ${wetter.first}" })
            // One voice: nowhere in the conversation does the answer call itself Claude, and the marker stays.
            assertTrue(shown.toString(), shown.none { it.contains("Claude") })
            assertEquals("CONTINUUM · CLOUD", brain.marker())
            assertFalse(log(a), log(a).contains("Claude"))
            assertEquals(0, local.calls)
            assertEquals(listOf("web_search_20260209", "web_fetch_20260209"), serverTypes(endpoint.requests.single()))
            assertEquals(1L, brain.ledger.snapshot().serverTools.webSearches)
        }
    }

    @Test fun aFailedSearchIsSaidPlainlyAndNoSourcesCardAppears() {
        endpoint.enqueue(Reply.Stream(listOf(messageStart()) +
            serverToolUse(0, "srvtoolu_01", "web_search", "{\"query\":\"wetter wien\"}") +
            resultBlock(1, webSearchError("srvtoolu_01", "unavailable")) +
            text(2, "Ich konnte gerade nicht im Internet nachsehen.") + stop("end_turn", 30)))
        val brain = cloudBrain()
        brain.webTurn.internetMode = { InternetAccessMode.ALWAYS_ON }
        assertTrue(brain.setCloudWebSearch(true))
        withTurns(brain) { a, _ ->
            ask(a, question)
            val shown = chat(a)
            assertTrue(shown.toString(), shown.any { it.contains("Hinweis der App: 1 Websuche über die Cloud ist fehlgeschlagen " +
                "(Suchdienst nicht verfügbar).") })
            assertFalse(shown.toString(), shown.contains("QUELLEN"))
        }
    }

    @Test fun aComputedAnswerCarriesTheCloudNoteAndNotTheRawOutput() {
        endpoint.enqueue(Reply.Stream(listOf(messageStart()) +
            serverToolUse(0, "srvtoolu_01", "bash_code_execution", "{\"command\":\"python3 -c 'print(17**23)'\"}") +
            resultBlock(1, bashResult("srvtoolu_01", "RAW-STDOUT-19967568900859523802559065713\n")) +
            text(2, "17 hoch 23 ist 19967568900859523802559065713.") + stop("end_turn", 60, container = "container_fake_01")))
        val brain = cloudBrain()
        assertTrue(brain.setCloudCodeExecution(true))
        withTurns(brain) { a, _ ->
            ask(a, "Was ist 17 hoch 23? Rechne es genau aus.")
            val shown = chat(a)
            assertTrue(shown.toString(), shown.any { it.contains("17 hoch 23 ist 19967568900859523802559065713.") &&
                it.contains("Berechnet in der Cloud (Code-Ausführung, 1 Schritt).") })
            assertTrue(shown.none { it.contains("RAW-STDOUT") })
            assertTrue(shown.none { it.contains("Claude") })
            // Internet mode is not connected in this fixture (it reads as „Aus“): code execution does not need it.
            assertEquals(listOf("code_execution_20260521"), serverTypes(endpoint.requests.single()))
        }
    }

    /** Internet mode „Auf Nachfrage“: the cloud searches only in the turn the owner approved through the app's consent. */
    @Test fun onRequestModeDeclaresTheCloudSearchOnlyForTheApprovedTurnAndARevocationEndsIt() {
        val brain = cloudBrain()
        assertTrue(brain.setCloudWebSearch(true))
        withTurns(brain) { a, _ ->
            val consent = get(a, "internetConsent") as InternetConsentPolicy
            val gate = get(a, "webToolGate") as ApprovedWebToolGate
            brain.webTurn.internetMode = { consent.mode }
            consent.setMode(InternetAccessMode.ASK)

            // No approval: the request does not declare the cloud's search.
            endpoint.enqueue(Reply.Stream(listOf(messageStart()) + text(0, "Das weiß ich ohne Internet nicht.") + stop("end_turn")))
            ask(a, question)
            assertEquals(emptyList<String>(), serverTypes(endpoint.requests[0]))

            // The owner approves this one request in the app's consent path; the hook hands it to the next turn.
            val approval = gate.prepare(WebToolRequest.Search("Wetter Wien morgen"))!!
            val session = gate.approve(approval, ResearchMode.STANDARD)!!
            assertEquals(ClaudeServerToolTexts.CONSENT_DIALOG_NOTE, call(a, "cloudWebNote"))
            brain.webTurn.approve(question) { session.isAuthorized() }
            endpoint.enqueue(searched("Morgen 4 Grad."))
            ask(a, question)
            assertEquals(listOf("web_search_20260209", "web_fetch_20260209"), serverTypes(endpoint.requests[1]))
            assertTrue(chat(a).any { it == "1. ${wetter.first}" })

            // The approval was for that turn only.
            endpoint.enqueue(Reply.Stream(listOf(messageStart()) + text(0, "Ohne Freigabe suche ich nicht.") + stop("end_turn")))
            ask(a, question)
            assertEquals(emptyList<String>(), serverTypes(endpoint.requests[2]))

            // A revoked consent (the owner switches the internet off) ends an approved turn's web use before it is sent.
            val second = gate.approve(gate.prepare(WebToolRequest.Search("Wetter Wien morgen"))!!, ResearchMode.STANDARD)!!
            brain.webTurn.approve(question) { second.isAuthorized() }
            consent.setMode(InternetAccessMode.OFF)
            endpoint.enqueue(Reply.Stream(listOf(messageStart()) + text(0, "Das Internet ist aus.") + stop("end_turn")))
            ask(a, question)
            assertEquals(emptyList<String>(), serverTypes(endpoint.requests[3]))
            assertEquals(1L, brain.ledger.snapshot().serverTools.webSearches)
        }
    }

    /** The activity's hook: the session the app's consent path approved goes to the cloud turn that follows. */
    @Test fun theApprovedWebRequestOfTheConsentPathIsAnsweredByTheCloudSearch() {
        val brain = cloudBrain()
        assertTrue(brain.setCloudWebSearch(true))
        withTurns(brain) { a, local ->
            val consent = get(a, "internetConsent") as InternetConsentPolicy
            val gate = get(a, "webToolGate") as ApprovedWebToolGate
            brain.webTurn.internetMode = { consent.mode }
            consent.setMode(InternetAccessMode.ASK)
            val session = gate.approve(gate.prepare(WebToolRequest.Search("Wetter Wien morgen"))!!, ResearchMode.STANDARD)!!
            endpoint.enqueue(searched("Morgen 4 Grad."))
            call(a, "runApprovedWeb", question, session)
            settle(a)
            assertEquals(listOf("web_search_20260209", "web_fetch_20260209"), serverTypes(endpoint.requests.single()))
            assertTrue(chat(a).toString(), chat(a).any { it.contains("Morgen 4 Grad.") })
            assertTrue(chat(a).any { it == "1. ${wetter.first}" })
            assertEquals(0, local.calls)
        }
    }

    @Test fun theSwitchCommandsAnswerWithoutAModelTurn() {
        val brain = cloudBrain()
        withTurns(brain) { a, local ->
            for ((sentence, expected) in listOf(
                "Cloud-Websuche an" to "Websuche und Seitenabruf über die Cloud ist jetzt EIN.",
                "Cloud-Code-Ausführung an" to "Code-Ausführung in der Cloud (Berechnungen, Datenanalyse, Dateien erzeugen) ist jetzt EIN.",
                "Cloud-Werkzeuge zeigen" to "Websuche und Seitenabruf über die Cloud: EIN",
                "Cloud-Websuche aus" to "Websuche und Seitenabruf über die Cloud ist jetzt AUS.")) {
                assertEquals(sentence, true, call(a, "consumeCloudToolCommand", sentence))
                idle()
                assertTrue(chat(a).toString(), chat(a).any { it.contains(expected) })
            }
            assertFalse(brain.settings.cloudWebSearch)
            assertTrue(brain.settings.cloudCodeExecution)
            assertEquals(false, call(a, "consumeCloudToolCommand", "Wie wird das Wetter morgen in Wien?"))
            assertTrue(log(a).contains("CLOUD-WERKZEUGE · Schalter-Befehl ausgeführt · kein Modellaufruf"))
            assertTrue("no request and no local turn", endpoint.requests.isEmpty() && local.calls == 0)
        }
    }
}
