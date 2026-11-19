package at.heinz.deviceai.localmodel

import at.heinz.deviceai.cognitive.InternetAccessMode
import at.heinz.deviceai.localmodel.ClaudeServerToolPolicy.Decision
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.TEST_KEY
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.bashError
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.bashResult
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.citedText
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.messageStart
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.resultBlock
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.searchCitation
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.serverToolUse
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.stop
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.text
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.toolUse
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.webFetchError
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.webFetchResult
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.webSearchError
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.webSearchResult
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Reply
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Owner order 19.11.2026: „Ja bau das ein und mach alles fertig“ – web search, web fetch and code execution over the
 * cloud, as two owner switches that are off by default. Everything here runs against the local fake endpoint with
 * the real SDK: no real API call, no real key, no real search and no real code execution. The stream shapes are the
 * ones of Anthropic's documentation as read on 19.11.2026 (the passages are quoted at [FakeAnthropicEndpoint]).
 * What the real service does with these requests is NOT_RUN.
 */
class ClaudeServerToolsTest {
    private val endpoint = FakeAnthropicEndpoint()
    private val settings = AtomicReference(ClaudeBrainSettings(brain = BrainKind.CLAUDE, consentGiven = true,
        cloudWebSearch = true, cloudCodeExecution = true))
    private val ledger = ClaudeUsageLedger(InMemoryClaudeLedgerStorage()) { "2026-11" }
    private val decision = AtomicReference(Decision(ClaudeServerToolPlan(web = true, code = true), true, true, ClaudeWebGrant.ALWAYS))
    private val outcomes = CopyOnWriteArrayList<ClaudeServerToolOutcome>()

    private val local = ClaudeModelRuntime.Endpoint(baseUrl = endpoint.baseUrl, maxRetries = 0,
        connectTimeout = Duration.ofSeconds(3), readTimeout = Duration.ofSeconds(8), requestTimeout = Duration.ofSeconds(20))

    private fun runtime() = ClaudeModelRuntime(settings = settings::get, apiKey = { OwnerSecret(TEST_KEY) }, ledger = ledger,
        endpoint = local, cloudToolsFrame = { cloud -> ClaudeAbilityCatalogue.cloudTools(cloud) },
        serverTools = decision::get, onServerToolOutcome = { outcomes += it })

    private fun run(start: (LocalInferenceCallback) -> Unit): List<LocalInferenceEvent> {
        val events = CopyOnWriteArrayList<LocalInferenceEvent>()
        val done = CountDownLatch(1)
        start(LocalInferenceCallback { event ->
            events += event
            if (event is LocalInferenceEvent.Completed || event is LocalInferenceEvent.Failed || event == LocalInferenceEvent.Cancelled)
                done.countDown()
        })
        assertTrue("no terminal event", done.await(15, TimeUnit.SECONDS))
        return events.toList()
    }

    private fun texts(events: List<LocalInferenceEvent>) = events.filterIsInstance<LocalInferenceEvent.TextDelta>().joinToString("") { it.text }
    private fun failure(events: List<LocalInferenceEvent>) = (events.last() as LocalInferenceEvent.Failed).message
    private fun ownerTurn(head: String, request: String) = head + ClaudePromptFrame.OWNER_TURN_INSTRUCTIONS + "Aktueller Auftrag:\n$request\n"
    private val question = ownerTurn(ClaudePromptFrame.HEAD_TOOLS, "Wie wird das Wetter morgen in Wien?")
    private val plainAnswer get() = Reply.Stream(listOf(messageStart()) + text(0, "Gut.") + stop("end_turn"))

    private fun tools(request: FakeAnthropicEndpoint.Request): List<JSONObject> =
        request.body.optJSONArray("tools")?.let { array -> (0 until array.length()).map(array::getJSONObject) }.orEmpty()
    private fun serverTypes(request: FakeAnthropicEndpoint.Request) = tools(request).mapNotNull { it.optString("type").takeIf { type ->
        type.startsWith("web_") || type.startsWith("code_execution") } }
    private fun names(request: FakeAnthropicEndpoint.Request) = tools(request).map { it.getString("name") }

    @After fun tearDown() = endpoint.close()

    // ── the two switches ────────────────────────────────────────────────────────────────

    @Test fun bothSwitchesAreOffByDefaultAndNeedTheStoredConsent() {
        val fresh = ClaudeBrainSettings()
        assertFalse(fresh.cloudWebSearch)
        assertFalse(fresh.cloudCodeExecution)
        assertTrue(runCatching { ClaudeBrainSettings(cloudWebSearch = true) }.isFailure)
        assertTrue(runCatching { ClaudeBrainSettings(cloudCodeExecution = true) }.isFailure)

        val brain = testBrain()
        assertFalse("no switch without consent", brain.setCloudWebSearch(true))
        assertFalse(brain.setCloudCodeExecution(true))
        assertTrue(brain.confirmConsent(shareLocalMemory = false))
        assertTrue(brain.setCloudWebSearch(true))
        assertTrue(brain.setCloudCodeExecution(true))
        assertTrue(brain.settings.cloudWebSearch && brain.settings.cloudCodeExecution)
        // Revoking the consent takes both switches with it.
        assertTrue(brain.revokeConsent())
        assertFalse(brain.settings.cloudWebSearch || brain.settings.cloudCodeExecution)
    }

    /** Every combination of switch, brain, consent and internet consent: a tool is planned only when all agree. */
    @Test fun aToolIsDeclaredOnlyWhenSwitchBrainConsentAndInternetModeAllAllowIt() {
        for (web in listOf(false, true)) for (code in listOf(false, true)) for (grant in ClaudeWebGrant.entries) {
            val on = ClaudeBrainSettings(brain = BrainKind.CLAUDE, consentGiven = true, cloudWebSearch = web, cloudCodeExecution = code)
            val decided = ClaudeServerToolPolicy.decide(on) { grant }
            assertEquals("web $web/$grant", web && grant in setOf(ClaudeWebGrant.ALWAYS, ClaudeWebGrant.APPROVED_ONCE), decided.plan.web)
            // The sandbox has no internet: code execution does not depend on the internet mode.
            assertEquals("code $code/$grant", code, decided.plan.code)
            // The cloud brain is not selected: nothing, whatever the switches say.
            val localBrain = on.copy(brain = BrainKind.LOCAL)
            assertEquals(Decision.NONE, ClaudeServerToolPolicy.decide(localBrain) { grant })
        }
        // A turn whose answer becomes a document gets no server tool, and the catalogue gives that reason.
        val all = ClaudeBrainSettings(brain = BrainKind.CLAUDE, consentGiven = true, cloudWebSearch = true, cloudCodeExecution = true)
        val draft = ClaudeServerToolPolicy.decide(all, documentDraft = true) { ClaudeWebGrant.ALWAYS }
        assertFalse(draft.plan.any)
        assertTrue(ClaudeAbilityCatalogue.cloudTools(draft).contains("Für einen Dokumententwurf sucht die Cloud nicht im Internet."))
        assertTrue(ClaudeAbilityCatalogue.cloudTools(draft).contains("Für einen Dokumententwurf wird kein Code ausgeführt."))
        // The internet consent is not even asked while the web switch is off.
        var asked = 0
        ClaudeServerToolPolicy.decide(ClaudeBrainSettings(brain = BrainKind.CLAUDE, consentGiven = true, cloudCodeExecution = true)) {
            asked++; ClaudeWebGrant.ALWAYS
        }
        assertEquals(0, asked)
    }

    /**
     * The declared tools on the wire, per switch state. Rules (docs „Server tools“ → „ZDR and allowed_callers“:
     * „To use a `_20260209` or later server tool with ZDR, disable dynamic filtering by setting `"allowed_callers":
     * ["direct"]` … This restricts the tool to direct calls only, bypassing the internal code execution step.“;
     * „Dynamic filtering with code execution“: „If you do include one, use `code_execution_20260120` or later“).
     */
    @Test fun theRequestDeclaresExactlyTheToolsOfTheSwitchState() {
        val states = listOf(
            ClaudeServerToolPlan(web = true, code = false) to listOf("web_search_20260209", "web_fetch_20260209"),
            ClaudeServerToolPlan(web = false, code = true) to listOf("code_execution_20260521"),
            ClaudeServerToolPlan(web = true, code = true) to listOf("web_search_20260209", "web_fetch_20260209", "code_execution_20260521"),
            ClaudeServerToolPlan() to emptyList())
        states.forEach { (plan, _) ->
            endpoint.enqueue(plainAnswer)
            decision.set(Decision(plan, plan.web, plan.code, ClaudeWebGrant.ALWAYS))
            run { runtime().stream(question, it) }
        }
        states.forEachIndexed { index, (plan, expected) ->
            val request = endpoint.requests[index]
            assertEquals("state $plan", expected, serverTypes(request))
            // After the app's own tools, in a fixed order.
            assertEquals(expected, tools(request).takeLast(expected.size).map { it.optString("type") })
            // A request cannot hold two tools named web_search: the app's own two are left out while the cloud searches.
            assertEquals(!plan.web, "web_read" in names(request))
            assertEquals(1, names(request).count { it == "web_search" })
            assertEquals(names(request).size, names(request).toSet().size)
            assertTrue("launch_app" in names(request))
        }
        val both = tools(endpoint.requests[2]).associateBy { it.getString("name") }
        val search = both.getValue("web_search")
        assertEquals(ClaudeServerToolPolicy.WEB_SEARCH_MAX_USES, search.getLong("max_uses"))
        assertEquals("[\"direct\"]", search.getJSONArray("allowed_callers").toString())
        val fetch = both.getValue("web_fetch")
        assertEquals(ClaudeServerToolPolicy.WEB_FETCH_MAX_USES, fetch.getLong("max_uses"))
        assertEquals(ClaudeServerToolPolicy.WEB_FETCH_MAX_CONTENT_TOKENS, fetch.getLong("max_content_tokens"))
        assertEquals("[\"direct\"]", fetch.getJSONArray("allowed_callers").toString())
        assertTrue(fetch.getJSONObject("citations").getBoolean("enabled"))
        // Addresses that only appear in a device tool result are not fetched.
        assertEquals("none", fetch.getJSONObject("url_sources").getJSONObject("client_tool_results").getString("type"))
        assertEquals("code_execution", both.getValue("code_execution").getString("name"))
        // Never forced, and no beta header is needed for these tool versions.
        assertEquals("auto", endpoint.requests[2].body.getJSONObject("tool_choice").getString("type"))
        assertTrue(ClaudeServerToolPolicy.WEB_SEARCH_MAX_USES in 1..5 && ClaudeServerToolPolicy.WEB_FETCH_MAX_USES in 1..5)
    }

    @Test fun aTextOnlyOwnerTurnGetsTheServerToolsButOtherPromptsNever() {
        repeat(4) { endpoint.enqueue(plainAnswer) }
        val runtime = runtime()
        run { runtime.streamTextOnly(ownerTurn(ClaudePromptFrame.HEAD_TEXT_ONLY, "Was ist 17 hoch 23?"), it) }
        // An isolated reasoning pass, a prompt that is not the owner's chat turn, and the connection test.
        run { runtime.streamThinking(question, it) }
        run { runtime.stream("Fasse diese Quellen zusammen: …", it) }
        val probed = CountDownLatch(1)
        runtime.probe { probed.countDown() }
        assertTrue(probed.await(15, TimeUnit.SECONDS))
        assertEquals(listOf("web_search_20260209", "web_fetch_20260209", "code_execution_20260521"), serverTypes(endpoint.requests[0]))
        assertEquals("only the server tools: a text-only round has none of the app's", 3, tools(endpoint.requests[0]).size)
        (1..3).forEach { assertEquals("request $it", emptyList<String>(), serverTypes(endpoint.requests[it])) }
        assertFalse(endpoint.requests[1].body.toString().contains("CLOUD-WERKZEUGE"))
    }

    @Test fun theRequestPrefixIsByteStablePerSwitchStateAndDiffersBetweenStates() {
        repeat(3) { endpoint.enqueue(plainAnswer) }
        val runtime = runtime()
        run { runtime.stream(question, it) }
        run { runtime.stream(ownerTurn(ClaudePromptFrame.HEAD_TOOLS, "Und übermorgen in Graz?"), it) }
        decision.set(Decision(ClaudeServerToolPlan(web = true), true, false, ClaudeWebGrant.ALWAYS))
        run { runtime.stream(question, it) }
        fun prefix(index: Int) = endpoint.requests[index].body.let { it.getJSONArray("tools").toString() + it.getJSONArray("system").toString() }
        assertEquals("same state, other question: identical tools and system", prefix(0), prefix(1))
        assertNotEquals("another switch state is another cache key", prefix(0), prefix(2))
    }

    // ── web search ──────────────────────────────────────────────────────────────────────

    private val wetter = "Wetter Wien – Vorhersage" to "https://wetter.example.org/wien"

    @Test fun aSearchAnswerCarriesItsCitedSourceTheAppNoteAndTheCountedSearch() {
        endpoint.enqueue(Reply.Stream(listOf(messageStart(input = 900)) + text(0, "Ich sehe nach. ") +
            serverToolUse(1, "srvtoolu_01", "web_search", "{\"query\":\"wetter wien morgen\"}") +
            resultBlock(2, webSearchResult("srvtoolu_01", wetter, "Nicht zitiert" to "https://other.example.org/x")) +
            citedText(3, "Morgen wird es in Wien 4 Grad kalt.", searchCitation(wetter.second, wetter.first, "Wien: 4 °C, bewölkt")) +
            stop("end_turn", 70, serverToolUse = JSONObject().put("web_search_requests", 1))))
        val events = run { runtime().stream(question, it) }
        assertTrue(events.last() is LocalInferenceEvent.Completed)
        assertEquals("Ich sehe nach. Morgen wird es in Wien 4 Grad kalt.\n\nÜber die Cloud im Internet nachgesehen: 1 Suche.",
            texts(events))
        // Only the cited page is a source – with the title and address the response carried, nothing added.
        assertEquals(listOf(ClaudeServerToolOutcome.Source(wetter.first, wetter.second)), outcomes.single().sources)
        val month = ledger.snapshot()
        assertEquals(ClaudeServerToolUsage(webSearches = 1), month.serverTools)
        // 10 USD per 1,000 searches: one cent, and it is part of the figure the monthly limit looks at.
        assertEquals(ClaudePricing.WEB_SEARCH_NANO_USD, month.serverToolNanoUsd)
        assertEquals(month.models.values.sumOf { it.costNanoUsd } + 10_000_000L, month.costNanoUsd)
        assertTrue(ledger.describe(2_500).contains("1 Websuchen (0,01 USD, in der Schätzung enthalten)"))
    }

    @Test fun aSearchThatReturnsAnErrorBlockIsSaidPlainlyAndNoSourceIsInvented() {
        endpoint.enqueue(Reply.Stream(listOf(messageStart()) +
            serverToolUse(0, "srvtoolu_01", "web_search", "{\"query\":\"wetter wien\"}") +
            resultBlock(1, webSearchError("srvtoolu_01", "max_uses_exceeded")) +
            serverToolUse(2, "srvtoolu_02", "web_search", "{\"query\":\"wetter\"}") +
            resultBlock(3, webSearchError("srvtoolu_02", "too_many_requests")) +
            text(4, "Ich konnte gerade nicht im Internet nachsehen.") +
            stop("end_turn", 40, serverToolUse = JSONObject().put("web_search_requests", 0))))
        val events = run { runtime().stream(question, it) }
        assertTrue("an error block is HTTP 200 and no exception", events.last() is LocalInferenceEvent.Completed)
        val answer = texts(events)
        assertTrue(answer, answer.contains("Hinweis der App: 2 Websuchen über die Cloud sind fehlgeschlagen " +
            "(Grenze von 3 Suchen je Auftrag erreicht; zu viele Anfragen, Ratenbegrenzung)."))
        assertTrue(outcomes.single().sources.isEmpty())
        assertEquals(listOf("max_uses_exceeded", "too_many_requests"), outcomes.single().searchErrors)
        // The API reports no billable search for a failed one; nothing is priced.
        assertEquals(0L, ledger.snapshot().serverToolNanoUsd)
    }

    @Test fun aSearchWithoutHitsIsNoErrorAndNoSource() {
        endpoint.enqueue(Reply.Stream(listOf(messageStart()) +
            serverToolUse(0, "srvtoolu_01", "web_search", "{\"query\":\"xqzv\"}") +
            resultBlock(1, webSearchResult("srvtoolu_01")) + text(2, "Dazu habe ich nichts gefunden.") +
            stop("end_turn", 30, serverToolUse = JSONObject().put("web_search_requests", 1))))
        val events = run { runtime().stream(question, it) }
        assertEquals("Dazu habe ich nichts gefunden.\n\nÜber die Cloud im Internet nachgesehen: 1 Suche.", texts(events))
        assertTrue(outcomes.single().sources.isEmpty() && outcomes.single().searchErrors.isEmpty())
    }

    // ── web fetch ───────────────────────────────────────────────────────────────────────

    @Test fun aFetchedPageIsASourceAndAFailedFetchIsSaidPlainly() {
        endpoint.enqueue(Reply.Stream(listOf(messageStart()) +
            serverToolUse(0, "srvtoolu_01", "web_fetch", "{\"url\":\"https://blog.example.org/artikel\"}") +
            resultBlock(1, webFetchResult("srvtoolu_01", "https://blog.example.org/artikel", "Der Artikel", "Langer Text …")) +
            serverToolUse(2, "srvtoolu_02", "web_fetch", "{\"url\":\"https://weg.example.org/\"}") +
            resultBlock(3, webFetchError("srvtoolu_02", "url_not_accessible")) +
            text(4, "Der Artikel sagt, dass es schneit. Die zweite Seite war nicht erreichbar.") +
            stop("end_turn", 50, serverToolUse = JSONObject().put("web_search_requests", 0).put("web_fetch_requests", 2))))
        val events = run { runtime().stream(question, it) }
        val answer = texts(events)
        assertTrue(answer, answer.endsWith("Über die Cloud im Internet nachgesehen: 2 Seitenabrufe.\n" +
            "Hinweis der App: 1 Seitenabruf über die Cloud ist fehlgeschlagen (Seite nicht erreichbar)."))
        assertEquals(listOf(ClaudeServerToolOutcome.Source("Der Artikel", "https://blog.example.org/artikel")), outcomes.single().sources)
        val month = ledger.snapshot()
        assertEquals(ClaudeServerToolUsage(webFetches = 2), month.serverTools)
        assertEquals("a fetch has no charge beyond its tokens", 0L, month.serverToolNanoUsd)
    }

    @Test fun onlyRealWebAddressesBecomeSourcesAndAMissingTitleIsNotMadeUp() {
        endpoint.enqueue(Reply.Stream(listOf(messageStart()) +
            citedText(0, "Text.", searchCitation("javascript:alert(1)", "Böse", "x"),
                searchCitation("https://a.example.org/ohne-titel", null, "y"),
                searchCitation("https://a.example.org/ohne-titel", "Doppelt", "z")) + stop("end_turn")))
        run { runtime().stream(question, it) }
        assertEquals(listOf(ClaudeServerToolOutcome.Source("https://a.example.org/ohne-titel", "https://a.example.org/ohne-titel")),
            outcomes.single().sources)
    }

    // ── code execution ──────────────────────────────────────────────────────────────────

    @Test fun aComputedAnswerIsNotTheRawOutputAndCarriesTheCloudNote() {
        endpoint.enqueue(Reply.Stream(listOf(messageStart()) +
            serverToolUse(0, "srvtoolu_01", "bash_code_execution", "{\"command\":\"python3 -c 'print(17**23)'\"}") +
            resultBlock(1, bashResult("srvtoolu_01", "19967568900859523802559065713\n")) +
            text(2, "17 hoch 23 ist 19967568900859523802559065713.") +
            stop("end_turn", 60, container = "container_fake_01")))
        val events = run { runtime().streamTextOnly(ownerTurn(ClaudePromptFrame.HEAD_TEXT_ONLY, "Was ist 17 hoch 23?"), it) }
        assertEquals("17 hoch 23 ist 19967568900859523802559065713.\n\nBerechnet in der Cloud (Code-Ausführung, 1 Schritt).",
            texts(events))
        // The response reported no code_execution_requests: the step seen in the response is counted.
        assertEquals(ClaudeServerToolUsage(codeExecutions = 1), ledger.snapshot().serverTools)
        assertEquals("code execution has no price in the estimate", 0L, ledger.snapshot().serverToolNanoUsd)
        assertTrue(ledger.describe(2_500).contains("1 Code-Ausführungen (nicht in der Schätzung enthalten)"))
    }

    @Test fun theReportedCodeExecutionCountWinsAndErrorsAndFilesAreSaidPlainly() {
        endpoint.enqueue(Reply.Stream(listOf(messageStart()) +
            serverToolUse(0, "srvtoolu_01", "bash_code_execution", "{\"command\":\"python3 plot.py\"}") +
            resultBlock(1, bashResult("srvtoolu_01", "ok", fileIds = listOf("file_fake_01"))) +
            serverToolUse(2, "srvtoolu_02", "bash_code_execution", "{\"command\":\"python3 big.py\"}") +
            resultBlock(3, bashError("srvtoolu_02", "execution_time_exceeded")) +
            text(4, "Die zweite Rechnung lief zu lange.") +
            stop("end_turn", 60, serverToolUse = JSONObject().put("web_search_requests", 0).put("web_fetch_requests", 0)
                .put("code_execution_requests", 5))))
        val events = run { runtime().stream(question, it) }
        val answer = texts(events)
        assertTrue(answer, answer.contains("Berechnet in der Cloud (Code-Ausführung, 2 Schritte)."))
        assertTrue(answer, answer.contains("Hinweis der App: 1 Schritt der Code-Ausführung ist fehlgeschlagen " +
            "(Zeitlimit der Ausführung überschritten)."))
        assertTrue(answer, answer.contains("In der Cloud-Umgebung wurde 1 Datei erzeugt. Das Abholen auf dein Gerät ist noch nicht eingebaut"))
        assertEquals(5L, ledger.snapshot().serverTools.codeExecutions)
    }

    /** No `max_uses` exists for code execution: the runtime counts the steps in the stream and ends the turn. */
    @Test fun theCodeStepCapEndsTheTurnAndTheStepsSeenAreStillCounted() {
        val steps = (0..ClaudeServerToolPolicy.CODE_MAX_STEPS).flatMap { step ->
            serverToolUse(step * 2, "srvtoolu_$step", "bash_code_execution", "{\"command\":\"echo $step\"}") +
                resultBlock(step * 2 + 1, bashResult("srvtoolu_$step", "$step"))
        }
        endpoint.enqueue(Reply.Stream(listOf(messageStart(input = 300)) + steps + text(99, "Fertig.") + stop("end_turn")))
        val events = run { runtime().stream(question, it) }
        assertEquals(ClaudeBrainFailure.CLOUD_CODE_LIMIT, failure(events))
        assertFalse(texts(events).contains("Fertig."))
        val month = ledger.snapshot()
        assertEquals((ClaudeServerToolPolicy.CODE_MAX_STEPS + 1).toLong(), month.serverTools.codeExecutions)
        assertEquals("the cut-off request is booked as an estimate", 1L, month.estimatedRequests)
        assertTrue(ClaudeBrainFailure.ownerMessage(ClaudeBrainFailure.CLOUD_CODE_LIMIT).contains("${ClaudeServerToolPolicy.CODE_MAX_STEPS} Schritten"))
        assertTrue(ClaudeBrainFailure.discardsPartialOutput(ClaudeBrainFailure.CLOUD_CODE_LIMIT))
    }

    // ── pause_turn and tool continuations ───────────────────────────────────────────────

    /**
     * Docs „Server tools“ → pause_turn: „continue the turn by re-sending the request with the assistant content
     * appended to messages“; „Web search tool“ → „Search results“: „send the assistant's content blocks back exactly
     * as you received them, including each result's `encrypted_content`“; „Code execution tool“ → „pause_turn“:
     * „Send the response's container ID back with that request“.
     */
    @Test fun aPausedTurnIsContinuedWithItsServerBlocksUnchangedAndItsContainer() {
        endpoint.enqueue(
            Reply.Stream(listOf(messageStart(input = 500)) +
                serverToolUse(0, "srvtoolu_01", "web_search", "{\"query\":\"wetter wien\"}") +
                resultBlock(1, webSearchResult("srvtoolu_01", wetter)) +
                serverToolUse(2, "srvtoolu_02", "bash_code_execution", "{\"command\":\"python3 mean.py\"}") +
                resultBlock(3, bashResult("srvtoolu_02", "4.5")) +
                stop("pause_turn", 30, serverToolUse = JSONObject().put("web_search_requests", 1), container = "container_fake_07")),
            Reply.Stream(listOf(messageStart(input = 700)) +
                citedText(0, "Im Mittel 4,5 Grad.", searchCitation(wetter.second, wetter.first, "4 °C / 5 °C")) + stop("end_turn", 20)))
        val events = run { runtime().stream(question, it) }
        assertEquals("Im Mittel 4,5 Grad.\n\nÜber die Cloud im Internet nachgesehen: 1 Suche.\n" +
            "Berechnet in der Cloud (Code-Ausführung, 1 Schritt).", texts(events))
        assertEquals(2, endpoint.requests.size)
        val (first, second) = endpoint.requests
        assertEquals("the turn keeps its tools", first.body.getJSONArray("tools").toString(), second.body.getJSONArray("tools").toString())
        assertFalse(first.body.has("container"))
        assertEquals("container_fake_07", second.body.getString("container"))
        val messages = second.body.getJSONArray("messages")
        assertEquals("the user turn and the paused assistant turn, nothing added", 2, messages.length())
        val echoed = messages.getJSONObject(1).getJSONArray("content")
        assertEquals(listOf("server_tool_use", "web_search_tool_result", "server_tool_use", "bash_code_execution_tool_result"),
            (0 until echoed.length()).map { echoed.getJSONObject(it).getString("type") })
        assertEquals("EqgfFAKE-encrypted-content",
            echoed.getJSONObject(1).getJSONArray("content").getJSONObject(0).getString("encrypted_content"))
        assertEquals("srvtoolu_01", echoed.getJSONObject(1).getString("tool_use_id"))
        // Both responses are counted; the source of the second one arrives as well.
        assertEquals(ClaudeServerToolUsage(webSearches = 1, codeExecutions = 1), ledger.snapshot().serverTools)
        assertEquals(wetter.second, outcomes.flatMap { it.sources }.single().url)
    }

    @Test fun aPausedTurnIsNotContinuedOnceTheSwitchOrTheWebConsentIsGone() {
        for (after in listOf(Decision.NONE, Decision(ClaudeServerToolPlan(code = true), true, true, ClaudeWebGrant.INTERNET_OFF))) {
            val before = endpoint.requests.size
            endpoint.enqueue(Reply.Stream(listOf(messageStart()) +
                serverToolUse(0, "srvtoolu_01", "web_search", "{\"query\":\"wetter wien\"}") + resultBlock(1, webSearchResult("srvtoolu_01", wetter)) +
                stop("pause_turn", 30)))
            // Allowed when the turn starts; the owner switches off while the first response is on its way.
            val asked = java.util.concurrent.atomic.AtomicInteger()
            val runtime = ClaudeModelRuntime(settings = settings::get, apiKey = { OwnerSecret(TEST_KEY) }, ledger = ledger,
                endpoint = local, serverTools = {
                    if (asked.getAndIncrement() == 0) Decision(ClaudeServerToolPlan(web = true, code = true), true, true, ClaudeWebGrant.ALWAYS)
                    else after
                })
            val events = run { runtime.stream(question, it) }
            assertEquals(ClaudeBrainFailure.CLOUD_TOOL_WITHDRAWN, failure(events))
            assertEquals("nothing further was sent", before + 1, endpoint.requests.size)
        }
        assertTrue(ClaudeBrainFailure.ownerMessage(ClaudeBrainFailure.CLOUD_TOOL_WITHDRAWN).contains("nicht fortgesetzt"))
    }

    @Test fun aToolContinuationKeepsTheServerToolsAndIsRefusedOnceTheyAreWithdrawn() {
        endpoint.enqueue(
            Reply.Stream(listOf(messageStart()) +
                serverToolUse(0, "srvtoolu_01", "web_search", "{\"query\":\"wetter wien\"}") + resultBlock(1, webSearchResult("srvtoolu_01", wetter)) +
                toolUse(2, "toolu_01", "get_device_info", "{}") + stop("tool_use", 30, serverToolUse = JSONObject().put("web_search_requests", 1))),
            Reply.Stream(listOf(messageStart()) + text(0, "Erledigt.") + stop("end_turn", 10)))
        val runtime = runtime()
        val proposed = run { runtime.stream(question, it) }
        assertEquals(1, proposed.filterIsInstance<LocalInferenceEvent.ToolProposal>().size)
        assertFalse("the note belongs to the end of the turn", texts(proposed).contains("Über die Cloud"))
        val done = run { runtime.respondToTools(listOf(LocalToolResponse("get_device_info", "{\"status\":\"SUCCEEDED\"}")), it) }
        assertEquals("Erledigt.\n\nÜber die Cloud im Internet nachgesehen: 1 Suche.", texts(done))
        assertEquals(endpoint.requests[0].body.getJSONArray("tools").toString(), endpoint.requests[1].body.getJSONArray("tools").toString())

        endpoint.enqueue(Reply.Stream(listOf(messageStart()) + toolUse(0, "toolu_02", "get_device_info", "{}") + stop("tool_use", 30)))
        run { runtime.stream(question, it) }
        decision.set(Decision(ClaudeServerToolPlan(), true, true, ClaudeWebGrant.NOT_APPROVED))
        val refused = run { runtime.respondToTools(listOf(LocalToolResponse("get_device_info", "{\"status\":\"SUCCEEDED\"}")), it) }
        assertEquals(ClaudeBrainFailure.CLOUD_TOOL_WITHDRAWN, failure(refused))
        assertEquals(3, endpoint.requests.size)
    }

    // ── the app's internet consent still rules ──────────────────────────────────────────

    @Test fun theWebTurnFollowsTheInternetModeAndAnApprovalCountsForItsOwnSentenceOnly() {
        val turn = ClaudeWebTurn()
        assertEquals("fail closed until the app connects its setting", ClaudeWebGrant.INTERNET_OFF, turn.grant())
        var mode = InternetAccessMode.ALWAYS_ON
        turn.internetMode = { mode }
        turn.begin("Wie wird das Wetter morgen in Wien?")
        assertEquals(ClaudeWebGrant.ALWAYS, turn.grant())
        mode = InternetAccessMode.OFF
        assertEquals(ClaudeWebGrant.INTERNET_OFF, turn.grant())

        mode = InternetAccessMode.ASK
        assertEquals(ClaudeWebGrant.NOT_APPROVED, turn.grant())
        var valid = true
        turn.approve("Wie wird das Wetter morgen in Wien?") { valid }
        assertEquals("an approval counts from the start of its turn", ClaudeWebGrant.NOT_APPROVED, turn.grant())
        turn.begin("Wie wird das Wetter morgen in Wien?")
        assertEquals(ClaudeWebGrant.APPROVED_ONCE, turn.grant())
        valid = false
        assertEquals("a revoked approval ends at once", ClaudeWebGrant.NOT_APPROVED, turn.grant())
        valid = true
        // The next turn has no approval any more – one approval, one turn.
        turn.begin("Wie wird das Wetter morgen in Wien?")
        assertEquals(ClaudeWebGrant.NOT_APPROVED, turn.grant())
        // An approval for another sentence is not used and does not survive.
        turn.approve("Was kostet ein Zugticket nach Graz?") { true }
        turn.begin("Erzähl mir einen Witz.")
        assertEquals(ClaudeWebGrant.NOT_APPROVED, turn.grant())
        turn.begin("Was kostet ein Zugticket nach Graz?")
        assertEquals(ClaudeWebGrant.NOT_APPROVED, turn.grant())
        // The owner's own veto beats every mode.
        mode = InternetAccessMode.ALWAYS_ON
        turn.begin("Erkläre mir Photosynthese, aber ohne Internetrecherche.")
        assertEquals(ClaudeWebGrant.OWNER_VETO, turn.grant())
        // A failing setting reads as OFF.
        turn.internetMode = { error("unavailable") }
        turn.begin("Wie wird das Wetter?")
        assertEquals(ClaudeWebGrant.INTERNET_OFF, turn.grant())
    }

    private fun cloudBrain(): ClaudeBrain = testBrain(endpoint = local, ledger = ledger).also { brain ->
        assertTrue(brain.saveKey(TEST_KEY))
        assertTrue(brain.confirmConsent(shareLocalMemory = false))
        assertTrue(brain.selectBrain(BrainKind.CLAUDE))
    }

    private fun system(request: FakeAnthropicEndpoint.Request): String = request.body.getJSONArray("system").let { blocks ->
        (0 until blocks.length()).joinToString("\n") { blocks.getJSONObject(it).getString("text") }
    }

    @Test fun theBrainDeclaresTheCloudSearchOnlyWithinTheInternetConsentAndTheCatalogueSaysWhy() {
        val brain = cloudBrain()
        var mode = InternetAccessMode.ALWAYS_ON
        brain.webTurn.internetMode = { mode }
        val sentence = "Wie wird das Wetter morgen in Wien?"
        fun ask(): FakeAnthropicEndpoint.Request {
            endpoint.enqueue(plainAnswer)
            run { brain.runtime.stream(question, it) }
            return endpoint.requests.last()
        }
        // Both switches off: nothing is declared and the catalogue says that neither is available.
        brain.beginOwnerTurn(sentence)
        ask().let { request ->
            assertEquals(emptyList<String>(), serverTypes(request))
            assertTrue(system(request).contains("Websuche: Über die Cloud ausgeschaltet."))
            assertTrue(system(request).contains("Code-Ausführung: Über die Cloud ausgeschaltet."))
            assertTrue("the app's own web tools stay", "web_read" in names(request))
        }
        assertTrue(brain.setCloudWebSearch(true))
        assertTrue(brain.cloudWebReady)
        // ALWAYS_ON: declared.
        brain.beginOwnerTurn(sentence)
        ask().let { request ->
            assertEquals(listOf("web_search_20260209", "web_fetch_20260209"), serverTypes(request))
            assertTrue(system(request).contains("Der Besitzer hat das Internet dauerhaft freigegeben."))
            assertTrue(system(request).contains("Code-Ausführung: Über die Cloud ausgeschaltet."))
        }
        // OFF: not declared, and the brain is told so.
        mode = InternetAccessMode.OFF
        brain.beginOwnerTurn(sentence)
        ask().let { request ->
            assertEquals(emptyList<String>(), serverTypes(request))
            assertTrue(system(request).contains("Der Besitzer hat den Internetmodus der App ausgeschaltet. Du kannst nicht im Internet suchen."))
        }
        // ASK without an approval for this turn: not declared; the app's own web_search (which asks him) stays.
        mode = InternetAccessMode.ASK
        brain.beginOwnerTurn(sentence)
        ask().let { request ->
            assertEquals(emptyList<String>(), serverTypes(request))
            assertTrue(system(request).contains("für diesen Auftrag hat der Besitzer kein Internet freigegeben"))
            assertTrue("web_search" in names(request) && "web_read" in names(request))
        }
        // ASK with the owner's approval for exactly this sentence: declared for this turn, and only this one.
        brain.webTurn.approve(sentence) { true }
        brain.beginOwnerTurn(sentence)
        ask().let { request ->
            assertEquals(listOf("web_search_20260209", "web_fetch_20260209"), serverTypes(request))
            assertTrue(system(request).contains("Der Besitzer hat das Internet für genau diesen Auftrag freigegeben."))
        }
        brain.beginOwnerTurn(sentence)
        assertEquals(emptyList<String>(), serverTypes(ask()))
        // A sentence that excludes research: never, also with the permanent grant.
        mode = InternetAccessMode.ALWAYS_ON
        brain.beginOwnerTurn("Beantworte das ohne Internetrecherche: Wie wird das Wetter?")
        ask().let { request ->
            assertEquals(emptyList<String>(), serverTypes(request))
            assertTrue(system(request).contains("schließt eine Internetrecherche aus"))
        }
        // Code execution does not need the internet mode.
        assertTrue(brain.setCloudCodeExecution(true))
        mode = InternetAccessMode.OFF
        brain.beginOwnerTurn(sentence)
        ask().let { request ->
            assertEquals(listOf("code_execution_20260521"), serverTypes(request))
            assertTrue(system(request).contains("Code-Ausführung: In dieser Anfrage freigeschaltet"))
        }
        // A document draft: nothing is declared, so no line of the app can end up inside the document.
        mode = InternetAccessMode.ALWAYS_ON
        brain.beginOwnerTurn(sentence, documentDraft = true)
        assertEquals(emptyList<String>(), serverTypes(ask()))
        brain.beginOwnerTurn(sentence)
        assertEquals(listOf("web_search_20260209", "web_fetch_20260209", "code_execution_20260521"), serverTypes(ask()))
        // Without the cloud brain nothing is planned at all.
        assertTrue(brain.selectBrain(BrainKind.LOCAL))
        assertFalse(brain.cloudWebReady)
    }

    @Test fun theBrainHandsTheSourcesOfATurnOutOnceAndANewTurnStartsEmpty() {
        val brain = cloudBrain()
        brain.webTurn.internetMode = { InternetAccessMode.ALWAYS_ON }
        assertTrue(brain.setCloudWebSearch(true))
        fun searched() = Reply.Stream(listOf(messageStart()) +
            serverToolUse(0, "srvtoolu_01", "web_search", "{\"query\":\"wetter wien\"}") + resultBlock(1, webSearchResult("srvtoolu_01", wetter)) +
            citedText(2, "4 Grad.", searchCitation(wetter.second, wetter.first, "4 °C")) +
            stop("end_turn", 20, serverToolUse = JSONObject().put("web_search_requests", 1)))
        brain.beginOwnerTurn("Wie wird das Wetter morgen in Wien?")
        assertNull(brain.takeServerToolOutcome())
        endpoint.enqueue(searched())
        run { brain.runtime.stream(question, it) }
        val outcome = brain.takeServerToolOutcome()!!
        assertEquals(listOf(ClaudeServerToolOutcome.Source(wetter.first, wetter.second)), outcome.sources)
        assertEquals(1, outcome.webSearches)
        assertNull("handed out once", brain.takeServerToolOutcome())
        // An answer that was never shown leaves nothing behind for the next turn.
        endpoint.enqueue(searched())
        run { brain.runtime.stream(question, it) }
        brain.beginOwnerTurn("Erzähl mir einen Witz.")
        assertNull(brain.takeServerToolOutcome())
        assertEquals(2L, ledger.snapshot().serverTools.webSearches)
    }

    // ── cost control ────────────────────────────────────────────────────────────────────

    @Test fun theSearchesCountTowardTheMonthlyLimitAndTheCountsAreShownAlsoAtZero() {
        val counter = ClaudeUsageLedger(InMemoryClaudeLedgerStorage()) { "2026-11" }
        assertTrue(counter.describeServerTools().startsWith("Cloud-Werkzeuge im Monat 2026-11: 0 Websuchen (0,00 USD, im Monatslimit enthalten)"))
        assertFalse("no extra line in the cost summary while nothing was used", counter.describe(100).contains("Cloud-Werkzeuge"))
        assertTrue(counter.recordServerTools(ClaudeServerToolUsage(webSearches = 99, webFetches = 4, codeExecutions = 7)))
        assertFalse(counter.limitReached(100))
        assertTrue(counter.recordServerTools(ClaudeServerToolUsage(webSearches = 1)))
        assertTrue("100 searches are one dollar: the limit of one dollar is reached", counter.limitReached(100))
        val shown = counter.describeServerTools()
        assertTrue(shown, shown.contains("100 Websuchen (1,00 USD, im Monatslimit enthalten)"))
        assertTrue(shown, shown.contains("4 Seitenabrufe (kein Aufpreis, nur die Token der Seite)"))
        assertTrue(shown, shown.contains("7 Code-Ausführungen (Preis nicht im Monatslimit enthalten"))
        assertTrue(shown, shown.contains(ClaudePricing.SERVER_TOOLS_AS_OF))
        // Token usage and server tools are kept side by side.
        assertTrue(counter.record("claude-opus-5-5", "claude-opus-5-5", ClaudeTokenUsage(1_000, 100)))
        assertEquals(ClaudeServerToolUsage(100, 4, 7), counter.snapshot().serverTools)
        assertEquals(1L, counter.snapshot().requests)
    }

    @Test fun aNewMonthStartsTheServerToolCountsAtZeroAndTheLimitBlocksASearchTurnBeforeItIsSent() {
        var month = "2026-11"
        val counter = ClaudeUsageLedger(InMemoryClaudeLedgerStorage()) { month }
        counter.recordServerTools(ClaudeServerToolUsage(webSearches = 5))
        month = "2026-12"
        assertTrue(counter.snapshot().serverTools.none)
        assertEquals(0L, counter.snapshot().costNanoUsd)

        // The limit is checked before every request and every continuation, as before.
        ledger.recordServerTools(ClaudeServerToolUsage(webSearches = 2_500))
        val events = run { runtime().stream(question, it) }
        assertEquals(ClaudeBrainFailure.SPEND_LIMIT_REACHED, failure(events))
        assertEquals(0, endpoint.requests.size)
    }

    // ── catalogue and owner texts ───────────────────────────────────────────────────────

    @Test fun theCatalogueNamesAnAbilityOnlyWhenItsToolIsInTheRequest() {
        val none = ClaudeAbilityCatalogue.cloudTools(Decision.NONE)
        assertFalse(none.contains("freigeschaltet –"))
        assertTrue(none.contains("behaupte nicht, du könntest selbst im Internet suchen"))
        assertTrue(none.contains("Du kannst in dieser Anfrage keinen Code ausführen"))

        val web = ClaudeAbilityCatalogue.cloudTools(Decision(ClaudeServerToolPlan(web = true), true, false, ClaudeWebGrant.ALWAYS))
        assertTrue(web.contains("Websuche: In dieser Anfrage freigeschaltet – die Werkzeuge web_search (suchen) und web_fetch"))
        assertTrue(web.contains("höchstens 3 Suchen und 3 Seitenabrufe je Auftrag"))
        assertTrue(web.contains("erfinde keine Quelle"))
        assertTrue(web.contains("Schreibe nichts Privates des Besitzers"))
        assertTrue(web.contains("Code-Ausführung: Über die Cloud ausgeschaltet."))

        val code = ClaudeAbilityCatalogue.cloudTools(Decision(ClaudeServerToolPlan(code = true), false, true, null))
        assertTrue(code.contains("Websuche: Über die Cloud ausgeschaltet."))
        assertTrue(code.contains("Code-Ausführung: In dieser Anfrage freigeschaltet – das Werkzeug code_execution"))
        assertTrue(code.contains("nur hinein, wenn er in diesem Auftrag genau das verlangt"))
        assertTrue(code.contains("kann die App noch nicht aufs Gerät holen"))
        assertTrue(code.contains("höchstens ${ClaudeServerToolPolicy.CODE_MAX_STEPS} Schritte je Auftrag"))

        // Switch on, but the internet consent says no: the reason, never the ability.
        for (grant in listOf(ClaudeWebGrant.INTERNET_OFF, ClaudeWebGrant.NOT_APPROVED, ClaudeWebGrant.OWNER_VETO)) {
            val refused = ClaudeAbilityCatalogue.cloudTools(Decision(ClaudeServerToolPlan(), true, false, grant))
            assertTrue(grant.name, refused.contains("Websuche: In dieser Anfrage nicht freigeschaltet:"))
            assertFalse(grant.name, refused.contains("web_fetch"))
        }
        // The same state gives the same bytes; the conversation voice stays Continuum.
        assertEquals(web, ClaudeAbilityCatalogue.cloudTools(Decision(ClaudeServerToolPlan(web = true), true, false, ClaudeWebGrant.ALWAYS)))
        listOf(none, web, code).forEach { assertFalse(it.contains("Claude")) }
    }

    @Test fun theSwitchTextsSayWhatLeavesTheDeviceAndWhatItCosts() {
        val web = ClaudeServerToolTexts.WEB_EXPLANATION
        assertTrue(web.contains("laufen bei Anthropic, nicht auf deinem Gerät"))
        assertTrue(web.contains("Die Suchanfragen formuliert das Cloud-Modell selbst"))
        assertTrue(web.contains("zusätzliches API-Guthaben (laut Anthropic-Dokumentation 10 USD je 1.000 Suchen"))
        assertTrue(web.contains("rechne ich in dein Monatslimit ein"))
        assertTrue(web.contains("bei „Aus“ sucht die Cloud nie"))
        val code = ClaudeServerToolTexts.CODE_EXPLANATION
        assertTrue(code.contains("Das Modell kann Inhalte aus unserem Gespräch in diese Umgebung schreiben"))
        assertTrue(code.contains("Bildschirminhalte anderer Apps gehen nie dorthin"))
        assertTrue(code.contains("ihr Preis ist NICHT in deinem Monatslimit enthalten"))
        assertTrue(code.contains("Dateien, die dort entstehen, kann ich nicht auf dein Gerät holen"))
        assertEquals("Websuche und Seitenabruf über die Cloud", ClaudeServerToolTexts.WEB_LABEL)
        assertEquals("Code-Ausführung in der Cloud (Berechnungen, Datenanalyse, Dateien erzeugen)", ClaudeServerToolTexts.CODE_LABEL)

        val on = ClaudeBrainSettings(brain = BrainKind.CLAUDE, consentGiven = true, cloudWebSearch = true, cloudCodeExecution = true)
        assertTrue(ClaudeServerToolTexts.status(on, InternetAccessMode.OFF, true).contains("wirkt gerade nicht, weil dein Internetmodus „Aus“ ist"))
        assertTrue(ClaudeServerToolTexts.status(on, InternetAccessMode.ASK, true).contains("ich frage dich vor einer Suche"))
        assertTrue(ClaudeServerToolTexts.status(on, InternetAccessMode.ALWAYS_ON, true).contains("Websuche: aktiv"))
        assertTrue(ClaudeServerToolTexts.status(on, InternetAccessMode.ALWAYS_ON, false).contains("kein API-Schlüssel"))
        assertTrue(ClaudeServerToolTexts.status(on.copy(brain = BrainKind.LOCAL), InternetAccessMode.ALWAYS_ON, true)
            .contains("Sie wirken gerade nicht: Die Cloud ist nicht als Gehirn gewählt."))
        assertTrue(ClaudeServerToolTexts.status(ClaudeBrainSettings(), null, false).contains("Beide sind aus"))
    }

    // ── commands ────────────────────────────────────────────────────────────────────────

    @Test fun onlyTheExactCommandShapesSwitchAndAQuestionNeverDoes() {
        fun parse(text: String) = ClaudeServerToolCommand.parse(text, "Continuum")
        val on = ClaudeServerToolCommand.Command.WEB_ON
        listOf("Cloud-Websuche an", "cloud websuche ein", "Schalte die Cloud-Websuche ein", "Cloud-Websuche einschalten.",
            "Continuum, Cloud-Websuche an", "Bitte Cloud-Suche aktivieren").forEach { assertEquals(it, on, parse(it)) }
        listOf("Cloud-Websuche aus", "Schalte die Cloud-Websuche aus", "Cloud-Internetsuche deaktivieren")
            .forEach { assertEquals(it, ClaudeServerToolCommand.Command.WEB_OFF, parse(it)) }
        listOf("Cloud-Code-Ausführung an", "cloud code ausführung ein", "Cloud-Code einschalten", "Cloud-Berechnungen an")
            .forEach { assertEquals(it, ClaudeServerToolCommand.Command.CODE_ON, parse(it)) }
        listOf("Cloud-Code-Ausführung aus", "Schalte die Cloud-Code-Ausführung ab")
            .forEach { assertEquals(it, ClaudeServerToolCommand.Command.CODE_OFF, parse(it)) }
        listOf("Cloud-Werkzeuge zeigen", "Zeig mir die Cloud-Werkzeuge", "Ist die Cloud-Websuche an?",
            "Ist die Cloud-Code-Ausführung aktiv?", "Status der Cloud-Werkzeuge")
            .forEach { assertEquals(it, ClaudeServerToolCommand.Command.STATUS, parse(it)) }
        // Not a command: a question about switching, a negation, a longer sentence, an ordinary request.
        listOf("Cloud-Websuche an?", "Soll ich die Cloud-Websuche an machen?", "Cloud-Websuche nicht an",
            "Such im Internet nach dem Wetter", "Mach die Websuche an", "Cloud-Websuche an und such nach dem Wetter in Wien",
            "Cloud an", "Was ist die Cloud-Websuche?").forEach { assertNull(it, parse(it)) }
    }

    @Test fun aCommandSwitchesOnlyWithConsentSaysWhatItMeansAndSendsNothing() {
        val brain = testBrain(endpoint = local, ledger = ledger)
        fun say(text: String) = ClaudeServerToolCommand.reply(brain, text, "Continuum", InternetAccessMode.ASK)
        assertNull(say("Wie spät ist es?"))
        assertTrue(say("Cloud-Websuche an")!!.contains("erst nach deiner Zustimmung"))
        assertFalse(brain.settings.cloudWebSearch)
        assertTrue(brain.saveKey(TEST_KEY) && brain.confirmConsent(false) && brain.selectBrain(BrainKind.CLAUDE))

        val webOn = say("Cloud-Websuche an")!!
        assertTrue(brain.settings.cloudWebSearch)
        assertTrue(webOn.startsWith("Websuche und Seitenabruf über die Cloud ist jetzt EIN."))
        assertTrue(webOn.contains(ClaudeServerToolTexts.WEB_EXPLANATION))
        assertTrue(webOn.contains("ich frage dich vor einer Suche"))
        assertTrue(webOn.contains("„Cloud-Websuche aus“"))
        val codeOn = say("Cloud-Code-Ausführung an")!!
        assertTrue(brain.settings.cloudCodeExecution)
        assertTrue(codeOn.contains(ClaudeServerToolTexts.CODE_EXPLANATION))

        val shown = say("Cloud-Werkzeuge zeigen")!!
        assertTrue(shown.contains("Websuche und Seitenabruf über die Cloud: EIN"))
        assertTrue(shown.contains("Code-Ausführung in der Cloud (Berechnungen, Datenanalyse, Dateien erzeugen): EIN"))
        assertTrue(shown.contains("Cloud-Werkzeuge im Monat 2026-11: 0 Websuchen"))

        assertTrue(say("Cloud-Websuche aus")!!.contains("ist jetzt AUS"))
        assertTrue(say("Cloud-Code-Ausführung aus")!!.contains("ist jetzt AUS"))
        assertFalse(brain.settings.cloudWebSearch || brain.settings.cloudCodeExecution)
        assertEquals("a command is no model turn", 0, endpoint.requests.size)
        assertTrue(ClaudeServerToolCommand.reply(null, "Cloud-Websuche an", null, null)!!.contains("nicht eingerichtet"))
    }

    @Test fun theNoteWordsEveryDocumentedErrorCodeAndNeverShowsAnUnknownText() {
        listOf("too_many_requests", "invalid_tool_input", "max_uses_exceeded", "query_too_long", "request_too_large", "unavailable")
            .forEach { assertFalse(it, ClaudeServerToolTexts.searchError(it).startsWith("Fehlercode")) }
        listOf("invalid_tool_input", "url_too_long", "url_not_allowed", "url_not_in_prior_context", "url_not_accessible",
            "too_many_requests", "unsupported_content_type", "content_too_large", "max_uses_exceeded", "unavailable")
            .forEach { assertFalse(it, ClaudeServerToolTexts.fetchError(it).startsWith("Fehlercode")) }
        listOf("unavailable", "execution_time_exceeded", "invalid_tool_input", "too_many_requests", "output_file_too_large", "file_not_found")
            .forEach { assertFalse(it, ClaudeServerToolTexts.codeError(it).startsWith("Fehlercode")) }
        assertEquals("Fehlercode new_code", ClaudeServerToolTexts.searchError("new_code"))
        assertNull(ClaudeServerToolOutcome().note())
        assertTrue(ClaudeServerToolOutcome().none)
    }
}
