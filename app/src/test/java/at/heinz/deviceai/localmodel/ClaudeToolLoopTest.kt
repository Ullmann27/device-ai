package at.heinz.deviceai.localmodel

import at.heinz.deviceai.ArgSchema
import at.heinz.deviceai.Risk
import at.heinz.deviceai.ToolBackendFailure
import at.heinz.deviceai.ToolExecutionStatus
import at.heinz.deviceai.ToolGate
import at.heinz.deviceai.ToolPolicy
import at.heinz.deviceai.ToolProposal
import at.heinz.deviceai.ToolRegistry
import at.heinz.deviceai.ToolRunner
import at.heinz.deviceai.ToolSpec
import at.heinz.deviceai.cognitive.AgentEvaluator
import at.heinz.deviceai.cognitive.AgentLimits
import at.heinz.deviceai.cognitive.AgentModel
import at.heinz.deviceai.cognitive.AgentModelOutput
import at.heinz.deviceai.cognitive.AgentToolApproval
import at.heinz.deviceai.cognitive.BoundedAgentOrchestrator
import at.heinz.deviceai.cognitive.Evaluation
import at.heinz.deviceai.cognitive.InMemoryExperienceStore
import at.heinz.deviceai.cognitive.OwnerConversationTone
import at.heinz.deviceai.cognitive.OwnerToneStyle
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.TEST_KEY
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.messageStart
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.stop
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.text
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.thinking
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Companion.toolUse
import at.heinz.deviceai.localmodel.FakeAnthropicEndpoint.Reply
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Owner goal 19.11.2026: „… dass Opus oder Claude hier wirklich jedes Werkzeug bedienen kann.“ The tool loop of the
 * cloud brain against the local fake endpoint only – real SDK, recorded-shape streams, no real API call and no real
 * key: every registered tool is declared with a schema the API accepts and can be proposed, the round trip proposal →
 * ToolGate → owner approval → tool_result → answer works through the productive orchestrator and gate, several tool
 * calls of one turn are answered in ONE user message, failures go back as `is_error`, a paused turn is continued, and
 * the constant system frame (abilities, running model, tone) is byte-identical from turn to turn.
 */
class ClaudeToolLoopTest {
    private val endpoint = FakeAnthropicEndpoint()
    private val settings = AtomicReference(ClaudeBrainSettings(brain = BrainKind.CLAUDE, consentGiven = true))
    private val ledger = ClaudeUsageLedger(InMemoryClaudeLedgerStorage()) { "2026-11" }

    private fun runtime(ownerFrame: () -> List<String?> = { emptyList() }) =
        ClaudeModelRuntime(settings = settings::get, apiKey = { OwnerSecret(TEST_KEY) }, ledger = ledger,
            endpoint = ClaudeModelRuntime.Endpoint(baseUrl = endpoint.baseUrl, maxRetries = 0,
                connectTimeout = Duration.ofSeconds(3), readTimeout = Duration.ofSeconds(8), requestTimeout = Duration.ofSeconds(20)),
            ownerFrame = ownerFrame)

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
    private fun proposals(events: List<LocalInferenceEvent>) = events.filterIsInstance<LocalInferenceEvent.ToolProposal>().map { it.proposal }

    @After fun tearDown() = endpoint.close()

    private fun ownerTurn(head: String, request: String) = head + ClaudePromptFrame.OWNER_TURN_INSTRUCTIONS + "Aktueller Auftrag:\n$request\n"
    private val toolPrompt = ownerTurn(ClaudePromptFrame.HEAD_TOOLS, "Welche Version hat WhatsApp?")

    // ── every registered tool ────────────────────────────────────────────────────────────────────────────

    /** A value the registry's own validator accepts for [argument] of [spec]. */
    private fun sample(spec: ToolSpec, argument: String): Any = when (val schema = spec.schema.getValue(argument)) {
        is ArgSchema.Text -> when {
            spec.name in setOf("launch_app", "read_app_screen") -> "com.example.app"
            argument == "packageOrName" -> "com.whatsapp"
            else -> "Uhr"
        }
        is ArgSchema.Integer -> schema.min.toInt()
        ArgSchema.Flag -> if (ClaudeToolCatalog.jsonType(argument) == "boolean") true else "true"
        ArgSchema.SharedPath -> "/sdcard/Download/Bericht.pdf"
        ArgSchema.HttpsUrl -> "https://example.org/seite"
        ArgSchema.PathList -> listOf("/sdcard/Download")
    }

    private fun json(value: Any): Any = if (value is List<*>) JSONArray(value) else value

    @Test fun everyRegisteredToolIsDeclaredWithASchemaTheApiAccepts() {
        endpoint.enqueue(Reply.Stream(listOf(messageStart()) + text(0, "Gut.") + stop("end_turn")))
        run { runtime().stream(toolPrompt, it) }
        val tools = endpoint.requests.single().body.getJSONArray("tools")
        val declared = (0 until tools.length()).map { tools.getJSONObject(it) }
        // All of the registry, except the one tool whose result is device-local screen content.
        assertEquals(ToolRegistry.all().map { it.name }.filterNot { it in ClaudeLocalOnlyGuard.LOCAL_ONLY_TOOLS }.sorted(),
            declared.map { it.getString("name") })
        for (tool in declared) {
            val name = tool.getString("name")
            val spec = ToolRegistry.get(name)!!
            // The checks the Messages API itself applies to a custom tool definition.
            assertTrue(name, Regex("^[a-zA-Z0-9_-]{1,64}$").matches(name))
            assertTrue(name, tool.getString("description").length in 10..2_000)
            assertFalse("custom tools carry no server type: $name", tool.has("type") && tool.getString("type") != "custom")
            val schema = tool.getJSONObject("input_schema")
            assertEquals(name, "object", schema.getString("type"))
            val properties = schema.optJSONObject("properties") ?: JSONObject()
            assertEquals(name, (spec.required + spec.optional).sorted(), properties.keys().asSequence().toList().sorted())
            for (key in properties.keys()) {
                val property = properties.getJSONObject(key)
                assertTrue("$name.$key", property.getString("type") in setOf("string", "integer", "boolean", "array"))
                assertTrue("$name.$key", property.getString("description").isNotBlank())
                if (property.getString("type") == "array") assertEquals("string", property.getJSONObject("items").getString("type"))
            }
            val required = schema.optJSONArray("required")?.let { array -> (0 until array.length()).map { array.getString(it) } }.orEmpty()
            assertEquals(name, spec.required.sorted(), required)
            assertFalse("no forced or strict-only fields: $name", tool.has("cache_control"))
        }
        // Never forced (a forced tool_choice is rejected on these models); at most one call per turn.
        val choice = endpoint.requests.single().body.getJSONObject("tool_choice")
        assertEquals("auto", choice.getString("type"))
        assertTrue(choice.getBoolean("disable_parallel_tool_use"))
    }

    @Test fun everyRegisteredToolCanBeProposedByTheCloudBrainAndItsResultGoesBack() {
        val specs = ToolRegistry.all().filterNot { it.name in ClaudeLocalOnlyGuard.LOCAL_ONLY_TOOLS }
        assertTrue(specs.size >= 30)
        val runtime = runtime()
        specs.forEachIndexed { index, spec ->
            val arguments = spec.required.sorted().associateWith { sample(spec, it) }
            val input = JSONObject().apply { arguments.forEach { (key, value) -> put(key, json(value)) } }.toString()
            endpoint.enqueue(
                Reply.Stream(listOf(messageStart()) + toolUse(0, "toolu_all_$index", spec.name, input) + stop("tool_use")),
                Reply.Stream(listOf(messageStart()) + text(0, "Erledigt: ${spec.label}.") + stop("end_turn")))
            val first = run { runtime.stream(toolPrompt, it) }
            // A proposal and nothing else: validation passed, nothing was executed here.
            assertEquals(spec.name, listOf(ToolProposal(spec.name, arguments)), proposals(first))
            assertTrue(spec.name, first.none { it is LocalInferenceEvent.ToolProposalRejected })
            assertNull(spec.name, ToolProposal(spec.name, arguments).validate())
            val observation = LocalToolContinuation.encode(spec.name, "SUCCEEDED", "ok")!!
            assertEquals("Erledigt: ${spec.label}.", texts(run { runtime.respondToTools(listOf(observation), it) }))
            val result = endpoint.requests.last().body.getJSONArray("messages").getJSONObject(2).getJSONArray("content").getJSONObject(0)
            assertEquals("toolu_all_$index", result.getString("tool_use_id"))
            assertFalse(spec.name, result.optBoolean("is_error", false))
        }
        assertEquals(specs.size * 2, endpoint.requests.size)
    }

    @Test fun theScreenReaderIsNeitherDeclaredNorAccepted() {
        endpoint.enqueue(Reply.Stream(listOf(messageStart()) +
            toolUse(0, "toolu_screen", "read_app_screen", "{\"packageOrName\": \"com.example.bank\"}") + stop("tool_use")))
        val events = run { runtime().stream(toolPrompt, it) }
        assertTrue(proposals(events).isEmpty())
        val rejected = events.filterIsInstance<LocalInferenceEvent.ToolProposalRejected>().single()
        assertEquals("read_app_screen", rejected.name)
        assertTrue(rejected.reason.contains("bleiben auf dem Gerät"))
    }

    // ── the whole round trip through the productive orchestrator and gate ────────────────────────────────

    /** The cloud runtime as the orchestrator's model, with the same result encoding the Activity uses. */
    private fun agentModel(runtime: ClaudeModelRuntime): AgentModel {
        var submitted = 0
        return AgentModel { _, _, _, observations ->
            // As in the Activity: a new observation continues the tool conversation, anything else is a fresh prompt.
            val fresh = observations.drop(submitted)
            submitted = observations.size
            val events = if (fresh.isEmpty()) run { runtime.stream(toolPrompt, it) }
                else run { callback ->
                    runtime.respondToTools(fresh.map { LocalToolContinuation.encode(it.toolName, it.status.name, it.output)!! }, callback)
                }
            (events.last() as? LocalInferenceEvent.Failed)?.let { error(it.message) }
            AgentModelOutput("claude-opus-5-5", texts(events), proposals(events).singleOrNull())
        }
    }

    private class Device : ToolRunner {
        val calls = mutableListOf<String>()
        var fail = false
        override fun execute(request: at.heinz.deviceai.ToolCallRequest): String {
            calls += request.name + request.arguments
            if (fail) throw ToolBackendFailure(ToolExecutionStatus.FAILED, "Shizuku ist nicht verbunden.")
            return "WhatsApp gestoppt."
        }
    }

    private fun orchestrator(runtime: ClaudeModelRuntime, device: Device, approvals: MutableList<Pair<String, Risk>>, approve: Boolean) =
        BoundedAgentOrchestrator(model = agentModel(runtime), memories = { emptyList() },
            toolGate = ToolGate(ToolPolicy(readOnlyEnabled = true), device),
            evaluator = AgentEvaluator { _, _ -> Evaluation(true, 1.0) }, experienceStore = InMemoryExperienceStore(),
            approvalProvider = { proposal, risk -> approvals += proposal.toolName to risk; AgentToolApproval(userConfirmed = approve) },
            limits = AgentLimits(maxToolCalls = 2), callIdPrefix = "cloud-loop", evaluationConfirmsOutcome = false)

    @Test fun aToolCallRunsOnlyAfterTheGateAndTheOwnersApprovalAndItsResultGoesBackToTheModel() {
        endpoint.enqueue(
            Reply.Stream(listOf(messageStart()) + thinking(0, "sig-loop") + text(1, "Ich stoppe WhatsApp.") +
                toolUse(2, "toolu_stop", "stop_app", "{\"packageOrName\": \"com.whatsapp\"}") + stop("tool_use")),
            Reply.Stream(listOf(messageStart()) + text(0, "WhatsApp ist gestoppt.") + stop("end_turn")))
        val device = Device()
        val approvals = mutableListOf<Pair<String, Risk>>()
        val result = orchestrator(runtime(), device, approvals, approve = true).run("Stoppe WhatsApp")

        // The owner was asked for exactly this call, with the registry's risk; only then the device was touched.
        assertEquals(listOf("stop_app" to Risk.CONFIRM), approvals)
        assertEquals(listOf("stop_app{packageOrName=com.whatsapp}"), device.calls)
        assertEquals("WhatsApp ist gestoppt.", result.response)
        assertNull(result.blocker)

        val followUp = endpoint.requests[1].body
        val messages = followUp.getJSONArray("messages")
        assertEquals(3, messages.length())
        // The assistant turn goes back unchanged, thinking block and signature included.
        val assistant = messages.getJSONObject(1).getJSONArray("content")
        assertEquals(listOf("thinking", "text", "tool_use"), (0 until assistant.length()).map { assistant.getJSONObject(it).getString("type") })
        assertEquals("sig-loop", assistant.getJSONObject(0).getString("signature"))
        assertEquals("", assistant.getJSONObject(0).getString("thinking"))
        val toolResult = messages.getJSONObject(2).getJSONArray("content").getJSONObject(0)
        assertEquals("tool_result", toolResult.getString("type"))
        assertEquals("toolu_stop", toolResult.getString("tool_use_id"))
        assertTrue(toolResult.getString("content").contains("WhatsApp gestoppt."))
        assertFalse(toolResult.optBoolean("is_error", false))
        // System and tools are byte-identical in the follow-up: the replayed thinking block stays valid.
        assertEquals(endpoint.requests[0].body.getJSONArray("system").toString(), followUp.getJSONArray("system").toString())
        assertEquals(endpoint.requests[0].body.getJSONArray("tools").toString(), followUp.getJSONArray("tools").toString())
    }

    @Test fun withoutTheOwnersApprovalNothingRunsAndTheModelLearnsItAsAnError() {
        endpoint.enqueue(
            Reply.Stream(listOf(messageStart()) + toolUse(0, "toolu_stop", "stop_app", "{\"packageOrName\": \"com.whatsapp\"}") + stop("tool_use")),
            Reply.Stream(listOf(messageStart()) + text(0, "Dann lasse ich WhatsApp laufen.") + stop("end_turn")),
            // Only used if the orchestrator asks for one more attempt.
            Reply.Stream(listOf(messageStart()) + text(0, "Dann lasse ich WhatsApp laufen.") + stop("end_turn")))
        val device = Device()
        val approvals = mutableListOf<Pair<String, Risk>>()
        val result = orchestrator(runtime(), device, approvals, approve = false).run("Stoppe WhatsApp")
        assertEquals("stop_app" to Risk.CONFIRM, approvals.first())
        assertTrue("nothing was executed", device.calls.isEmpty())
        assertFalse(result.response.contains("gestoppt."))
        // Whenever the model is asked again, the refusal is the result of that call, marked as an error.
        endpoint.requests.drop(1).map { it.body.getJSONArray("messages") }.filter { it.length() == 3 }.forEach { messages ->
            val toolResult = messages.getJSONObject(2).getJSONArray("content").getJSONObject(0)
            assertEquals("toolu_stop", toolResult.getString("tool_use_id"))
            assertTrue(toolResult.getBoolean("is_error"))
            assertFalse(toolResult.getString("content").contains("SUCCEEDED"))
        }
    }

    @Test fun aFailedToolGoesBackAsAnErrorResultAndTheModelSaysSo() {
        endpoint.enqueue(
            Reply.Stream(listOf(messageStart()) + toolUse(0, "toolu_find", "find_files", "{\"query\": \"Argon4\"}") + stop("tool_use")),
            Reply.Stream(listOf(messageStart()) + text(0, "Die Dateisuche braucht Shizuku und ist gerade nicht verbunden.") + stop("end_turn")))
        val runtime = runtime()
        assertEquals(listOf(ToolProposal("find_files", mapOf("query" to "Argon4"))), proposals(run { runtime.stream(toolPrompt, it) }))
        // What the app reports when the Shizuku bridge is missing, in the encoding the Activity sends back.
        val failed = LocalToolContinuation.encode("find_files", "FAILED", "Shizuku ist nicht verbunden.")!!
        assertEquals("Die Dateisuche braucht Shizuku und ist gerade nicht verbunden.", texts(run { runtime.respondToTools(listOf(failed), it) }))
        val toolResult = endpoint.requests[1].body.getJSONArray("messages").getJSONObject(2).getJSONArray("content").getJSONObject(0)
        assertEquals("toolu_find", toolResult.getString("tool_use_id"))
        assertTrue(toolResult.getBoolean("is_error"))
        assertEquals("FAILED", JSONObject(toolResult.getString("content")).getString("status"))
        assertTrue(toolResult.getString("content").contains("Shizuku ist nicht verbunden."))
    }

    @Test fun aToolThatFailsOnTheDeviceNeverBecomesASuccess() {
        endpoint.enqueue(
            Reply.Stream(listOf(messageStart()) + toolUse(0, "toolu_find", "find_files", "{\"query\": \"Argon4\"}") + stop("tool_use")),
            Reply.Stream(listOf(messageStart()) + text(0, "Die Dateisuche ist fehlgeschlagen.") + stop("end_turn")),
            Reply.Stream(listOf(messageStart()) + text(0, "Die Dateisuche ist fehlgeschlagen.") + stop("end_turn")))
        val device = Device().apply { fail = true }
        val result = orchestrator(runtime(), device, mutableListOf(), approve = true).run("Such die Datei Argon4")
        assertEquals(listOf("find_files{query=Argon4}"), device.calls)
        assertFalse(result.response.contains("gefunden:"))
        endpoint.requests.drop(1).map { it.body.getJSONArray("messages") }.filter { it.length() == 3 }.forEach { messages ->
            assertTrue(messages.getJSONObject(2).getJSONArray("content").getJSONObject(0).getBoolean("is_error"))
        }
    }

    // ── several tool calls in one turn ──────────────────────────────────────────────────────────────────

    @Test fun ofSeveralToolCallsInOneTurnOnlyTheFirstIsProposedAndAllAreAnsweredInOneUserMessage() {
        endpoint.enqueue(
            Reply.Stream(listOf(messageStart()) + thinking(0, "sig-two") + toolUse(1, "toolu_a", "get_battery_info", "{}") +
                toolUse(2, "toolu_b", "delete_file", "{\"path\": \"/sdcard/Download/alt.pdf\"}") +
                toolUse(3, "toolu_c", "get_battery_info", "{}") + stop("tool_use")),
            Reply.Stream(listOf(messageStart()) + text(0, "Der Akku steht bei 80 Prozent.") + stop("end_turn")))
        val runtime = runtime()
        val first = run { runtime.stream(toolPrompt, it) }
        // Exactly one proposal reaches the app – the gate and the owner decide about it as about any single call.
        assertEquals(listOf(ToolProposal("get_battery_info", emptyMap())), proposals(first))
        assertTrue(first.none { it is LocalInferenceEvent.ToolProposalRejected })
        assertTrue(first.last() is LocalInferenceEvent.Completed)

        val observation = LocalToolContinuation.encode("get_battery_info", "SUCCEEDED", "level=80")!!
        assertEquals("Der Akku steht bei 80 Prozent.", texts(run { runtime.respondToTools(listOf(observation), it) }))
        val messages = endpoint.requests[1].body.getJSONArray("messages")
        // user · assistant (unchanged) · ONE user message with a result for every call, in the order of the calls.
        assertEquals(3, messages.length())
        assertEquals("user", messages.getJSONObject(2).getString("role"))
        val results = messages.getJSONObject(2).getJSONArray("content")
        assertEquals(3, results.length())
        assertEquals(listOf("tool_result", "tool_result", "tool_result"), (0 until 3).map { results.getJSONObject(it).getString("type") })
        assertEquals(listOf("toolu_a", "toolu_b", "toolu_c"), (0 until 3).map { results.getJSONObject(it).getString("tool_use_id") })
        assertEquals(observation.response, results.getJSONObject(0).getString("content"))
        assertFalse(results.getJSONObject(0).optBoolean("is_error", false))
        // The calls that were not run say so, as errors – also the second call of the same tool.
        for (index in 1..2) {
            assertTrue(results.getJSONObject(index).getBoolean("is_error"))
            assertEquals(ClaudeModelRuntime.SKIPPED_TOOL_RESULT, results.getJSONObject(index).getString("content"))
            assertEquals("NOT_RUN", JSONObject(results.getJSONObject(index).getString("content")).getString("status"))
        }
        val assistant = messages.getJSONObject(1).getJSONArray("content")
        assertEquals(listOf("thinking", "tool_use", "tool_use", "tool_use"),
            (0 until assistant.length()).map { assistant.getJSONObject(it).getString("type") })
    }

    @Test fun anInvalidFirstCallIsRejectedAndTheOthersAreStillNotRun() {
        endpoint.enqueue(
            Reply.Stream(listOf(messageStart()) + toolUse(0, "toolu_bad", "delete_file", "{\"path\": \"/sdcard/x\", \"command\": \"rm -rf /\"}") +
                toolUse(1, "toolu_next", "get_device_info", "{}") + stop("tool_use")),
            Reply.Stream(listOf(messageStart()) + text(0, "Das mache ich nicht.") + stop("end_turn")))
        val runtime = runtime()
        val first = run { runtime.stream(toolPrompt, it) }
        assertTrue(proposals(first).isEmpty())
        val rejected = first.filterIsInstance<LocalInferenceEvent.ToolProposalRejected>().single()
        val response = LocalToolResponse(rejected.name, JSONObject().put("status", "REJECTED").put("reason", rejected.reason).toString())
        run { runtime.respondToTools(listOf(response), it) }
        val results = endpoint.requests[1].body.getJSONArray("messages").getJSONObject(2).getJSONArray("content")
        assertEquals(2, results.length())
        assertTrue(results.getJSONObject(0).getBoolean("is_error"))
        assertEquals(response.response, results.getJSONObject(0).getString("content"))
        assertEquals(ClaudeModelRuntime.SKIPPED_TOOL_RESULT, results.getJSONObject(1).getString("content"))
    }

    // ── stop reasons ──────────────────────────────────────────────────────────────────────────────────────

    @Test fun aPausedTurnIsContinuedWithoutAnAddedUserMessage() {
        endpoint.enqueue(
            Reply.Stream(listOf(messageStart(input = 100)) + text(0, "Der erste Teil. ") + stop("pause_turn", 30)),
            Reply.Stream(listOf(messageStart(input = 140)) + text(0, "Der zweite Teil.") + stop("end_turn", 20)))
        val runtime = runtime()
        val events = run { runtime.streamTextOnly(ownerTurn(ClaudePromptFrame.HEAD_TEXT_ONLY, "Erzähl mir etwas Langes."), it) }
        assertEquals("Der erste Teil. Der zweite Teil.", texts(events))
        assertTrue(events.last() is LocalInferenceEvent.Completed)
        assertEquals(1, events.count { it is LocalInferenceEvent.Completed || it is LocalInferenceEvent.Failed })
        val (open, resumed) = endpoint.requests.map { it.body }
        // The same turn again with what was said so far – and nothing like „Continue“ appended.
        val messages = resumed.getJSONArray("messages")
        assertEquals(2, messages.length())
        assertEquals(open.getJSONArray("messages").getJSONObject(0).toString(), messages.getJSONObject(0).toString())
        assertEquals("assistant", messages.getJSONObject(1).getString("role"))
        assertEquals("Der erste Teil. ", messages.getJSONObject(1).getJSONArray("content").getJSONObject(0).getString("text"))
        assertEquals(open.getJSONArray("system").toString(), resumed.getJSONArray("system").toString())
        assertEquals(open.getString("model"), resumed.getString("model"))
        // Both requests are counted.
        assertEquals(2L, ledger.snapshot().requests)
        assertEquals(LocalModelState.READY, runtime.state)
    }

    @Test fun aTurnThatKeepsPausingEndsHonestlyAfterTheBound() {
        repeat(ClaudeGenerationPolicy.MAX_PAUSE_CONTINUATIONS + 1) {
            endpoint.enqueue(Reply.Stream(listOf(messageStart()) + text(0, "Teil. ") + stop("pause_turn")))
        }
        val runtime = runtime()
        val events = run { runtime.streamTextOnly(ownerTurn(ClaudePromptFrame.HEAD_TEXT_ONLY, "Erzähl."), it) }
        assertEquals(ClaudeBrainFailure.UNEXPECTED_RESPONSE, failure(events))
        assertEquals(ClaudeGenerationPolicy.MAX_PAUSE_CONTINUATIONS + 1, endpoint.requests.size)
        assertEquals(LocalModelState.READY, runtime.state)
    }

    @Test fun aPausedTurnIsNotContinuedPastTheOwnersMonthlyLimit() {
        endpoint.enqueue(Reply.Stream(listOf(messageStart(input = 100)) + text(0, "Teil. ") + stop("pause_turn", 30)))
        settings.set(settings.get().copy(monthlyLimitCents = ClaudeBrainSettings.MIN_MONTHLY_LIMIT_CENTS))
        // The ledger is already at the limit once this first answer is booked.
        ledger.record("claude-opus-5-5", "claude-opus-5-5", ClaudeTokenUsage(240_000, 0))
        ledger.record("claude-opus-5-5", "claude-opus-5-5", ClaudeTokenUsage(9_000, 0))
        val limited = ledger.limitReached(ClaudeBrainSettings.MIN_MONTHLY_LIMIT_CENTS)
        val runtime = runtime()
        val events = run { runtime.streamTextOnly(ownerTurn(ClaudePromptFrame.HEAD_TEXT_ONLY, "Erzähl."), it) }
        if (limited) {
            // Already at the limit: not even the first request leaves the device.
            assertEquals(ClaudeBrainFailure.SPEND_LIMIT_REACHED, failure(events))
            assertTrue(endpoint.requests.isEmpty())
        } else {
            assertEquals(ClaudeBrainFailure.SPEND_LIMIT_REACHED, failure(events))
            assertEquals("no continuation was sent", 1, endpoint.requests.size)
        }
    }

    @Test fun aRefusalOrACutOffTurnAuthorizesNoToolEvenWithSeveralCalls() {
        for (reason in listOf("refusal", "max_tokens")) {
            endpoint.enqueue(Reply.Stream(listOf(messageStart()) + toolUse(0, "toolu_x", "delete_file", "{\"path\": \"/sdcard/Docu") +
                stop(reason)))
            val events = run { runtime().stream(toolPrompt, it) }
            assertTrue(reason, events.none { it is LocalInferenceEvent.ToolProposal || it is LocalInferenceEvent.ToolProposalRejected })
            assertEquals(reason, if (reason == "refusal") ClaudeBrainFailure.REFUSAL else LocalModelGenerationPolicy.OUTPUT_TOKEN_LIMIT,
                failure(events))
        }
    }

    // ── the constant system frame ───────────────────────────────────────────────────────────────────────

    private val frame: () -> List<String?> = {
        listOf(ClaudeAbilityCatalogue.text(), ClaudeAbilityCatalogue.runningOn(settings.get().model),
            OwnerToneStyle.claudeBlock(OwnerConversationTone.PERSONAL, "Heinz"))
    }

    @Test fun theOwnersChatTurnCarriesAbilitiesRunningModelAndToneAsConstantSystemBlocks() {
        endpoint.enqueue(Reply.Stream(listOf(messageStart(input = 3_000, cacheWrite = 2_800)) + text(0, "Eins.") + stop("end_turn")),
            Reply.Stream(listOf(messageStart(input = 200, cacheRead = 2_800)) + text(0, "Zwei.") + stop("end_turn")))
        val runtime = runtime(frame)
        run { runtime.streamTextOnly(ownerTurn(ClaudePromptFrame.HEAD_TEXT_ONLY, "Erklär mir kurz, was ein Test ist."), it) }
        run { runtime.streamTextOnly(ownerTurn(ClaudePromptFrame.HEAD_TEXT_ONLY, "Und was ist ein Mock?") +
            "\nGerätezeit: 19.11.2026, 10:05 Uhr\n", it) }
        val (first, second) = endpoint.requests.map { it.body }
        val system = first.getJSONArray("system")
        assertEquals(5, system.length())
        assertEquals(ClaudePromptFrame.RUNTIME_PREFACE, system.getJSONObject(0).getString("text"))
        assertEquals(ClaudePromptFrame.HEAD_TEXT_ONLY + ClaudePromptFrame.OWNER_TURN_INSTRUCTIONS, system.getJSONObject(1).getString("text"))
        assertEquals(ClaudeAbilityCatalogue.text(), system.getJSONObject(2).getString("text"))
        assertEquals(ClaudeAbilityCatalogue.runningOn(ClaudeModel.OPUS_5_5), system.getJSONObject(3).getString("text"))
        assertEquals(OwnerToneStyle.claudeBlock(OwnerConversationTone.PERSONAL, "Heinz"), system.getJSONObject(4).getString("text"))
        // One cache breakpoint, on the last block: tools and system are cached together.
        assertEquals(listOf(false, false, false, false, true), (0 until 5).map { system.getJSONObject(it).has("cache_control") })
        // Byte-stable from turn to turn: everything that changes is in the user message.
        assertEquals(system.toString(), second.getJSONArray("system").toString())
        assertEquals("Aktueller Auftrag:\nErklär mir kurz, was ein Test ist.\n", first.getJSONArray("messages").getJSONObject(0).getString("content"))
        assertTrue(second.getJSONArray("messages").getJSONObject(0).getString("content").contains("Gerätezeit: 19.11.2026"))
        assertFalse(system.toString().contains("19.11.2026"))
        assertFalse(system.toString().contains("Erklär mir kurz"))
    }

    @Test fun aToolTurnKeepsTheSameFrameAndTheSameToolListOnEveryTurn() {
        endpoint.enqueue(Reply.Stream(listOf(messageStart()) + text(0, "Eins.") + stop("end_turn")),
            Reply.Stream(listOf(messageStart()) + text(0, "Zwei.") + stop("end_turn")))
        val runtime = runtime(frame)
        run { runtime.stream(ownerTurn(ClaudePromptFrame.HEAD_TOOLS, "Wie viel Speicher ist frei?"), it) }
        run { runtime.stream(ownerTurn(ClaudePromptFrame.HEAD_TOOLS, "Welche Version hat WhatsApp?"), it) }
        val (first, second) = endpoint.requests.map { it.body }
        assertEquals(first.getJSONArray("tools").toString(), second.getJSONArray("tools").toString())
        assertEquals(first.getJSONArray("system").toString(), second.getJSONArray("system").toString())
        assertEquals(first.getJSONObject("tool_choice").toString(), second.getJSONObject("tool_choice").toString())
        assertEquals(5, first.getJSONArray("system").length())
        assertEquals(ClaudePromptFrame.HEAD_TOOLS + ClaudePromptFrame.OWNER_TURN_INSTRUCTIONS,
            first.getJSONArray("system").getJSONObject(1).getString("text"))
    }

    @Test fun inSachlichAndForOtherPromptShapesNoToneBlockIsSent() {
        endpoint.enqueue(Reply.Stream(listOf(messageStart()) + text(0, "Eins.") + stop("end_turn")),
            Reply.Stream(listOf(messageStart()) + text(0, "Zwei.") + stop("end_turn")),
            Reply.Stream(listOf(messageStart()) + text(0, "Drei.") + stop("end_turn")))
        val factual = runtime { listOf(ClaudeAbilityCatalogue.text(), ClaudeAbilityCatalogue.runningOn(ClaudeModel.OPUS_5_5),
            OwnerToneStyle.claudeBlock(OwnerConversationTone.FACTUAL, "Heinz"), "  ") }
        run { factual.streamTextOnly(ownerTurn(ClaudePromptFrame.HEAD_TEXT_ONLY, "Was ist ein Test?"), it) }
        // A plan request, a summary job or a bot task is not the owner's chat turn: no abilities, no tone.
        val personal = runtime(frame)
        run { personal.streamTextOnly("Zerlege den Auftrag in höchstens 6 Teilaufträge.\nAuftrag: …", it) }
        run { personal.streamThinking("Prüfe diese Aussage.", it) }
        val systems = endpoint.requests.map { it.body.getJSONArray("system") }
        assertEquals(4, systems[0].length())
        assertFalse(systems[0].toString().contains("GESPRÄCHSTON"))
        assertEquals(1, systems[1].length())
        assertEquals(ClaudePromptFrame.RUNTIME_PREFACE, systems[1].getJSONObject(0).getString("text"))
        assertEquals(2, systems[2].length())
        assertFalse(systems[2].toString().contains("GESPRÄCHSTON"))
        assertFalse(systems[1].toString().contains("WAS DIE APP KANN"))
    }
}
