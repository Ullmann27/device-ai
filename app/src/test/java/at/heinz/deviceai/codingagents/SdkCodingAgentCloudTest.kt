package at.heinz.deviceai.codingagents

import at.heinz.deviceai.localmodel.OwnerSecret
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The real Anthropic Java SDK client of the coding agents against a local fake endpoint: what it puts on the wire
 * for every resource (asserted against the request shapes of the bundled API guidance), how the documented error
 * envelope becomes an owner-facing code, and one whole run – setup, session, event stream, approval round trip,
 * interrupt, final state – through the productive controller. No request leaves 127.0.0.1 and no real key exists.
 * How the real service behaves is NOT_RUN.
 */
class SdkCodingAgentCloudTest {
    private val endpoint = FakeManagedAgentsEndpoint()
    private val pool = Executors.newCachedThreadPool { task -> Thread(task, "sdk-cloud-test").apply { isDaemon = true } }
    private val cloud = SdkCodingAgentCloud(OwnerSecret(FAKE_API_KEY), SdkCodingAgentCloud.Endpoint(baseUrl = endpoint.baseUrl,
        connectTimeout = Duration.ofSeconds(3), readTimeout = Duration.ofSeconds(8), requestTimeout = Duration.ofSeconds(20),
        streamReadTimeout = Duration.ofSeconds(20), streamRequestTimeout = Duration.ofSeconds(60)))

    @After fun tearDown() { cloud.close(); endpoint.close(); pool.shutdownNow() }

    private fun await(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (!condition()) {
            if (System.nanoTime() > deadline) throw AssertionError("timed out waiting for: $what")
            Thread.sleep(5)
        }
    }

    private fun code(block: () -> Unit): String = try { block(); "NO_FAILURE" } catch (failure: CodingAgentException) { failure.code }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)

    private fun request(repository: String = "Ullmann27/device-ai") = CodingAgentCloud.SessionRequest(
        CodingAgentCloud.AgentRef("agent_fake_01", 1), "env_fake_01", "vlt_fake_01", "Continuum Coding-Auftrag", repository, "main",
        OwnerSecret(FAKE_GITHUB_TOKEN), 200, "run0001")

    // ── What the SDK sends ──────────────────────────────────────────────────────────────────────────────────────

    @Test fun theAgentIsCreatedOnceWithItsRulesItsToolsAndTheStrictestWorkablePolicy() {
        val created = cloud.saveAgent("claude-opus-5-5", null)
        assertEquals("agent_fake_01", created.id)
        assertEquals(1, created.version)
        val sent = endpoint.sent("POST", "/v1/agents").single()
        // The SDK adds the beta header itself on every managed-agents call (overview, § Beta Headers).
        assertTrue(sent.headers.getValue("anthropic-beta").contains("managed-agents-2026-04-01"))
        // api-reference, § CreateAgent Request Body: model, system and tools are top-level fields of the agent.
        val body = sent.body!!
        assertEquals("claude-opus-5-5", body.getString("model"))
        assertEquals(CodingAgentPrompt.SYSTEM, body.getString("system"))
        assertEquals(CodingAgentPrompt.AGENT_NAME, body.getString("name"))
        val toolset = body.getJSONArray("tools").objects().single()
        assertEquals("agent_toolset_20260401", toolset.getString("type"))
        // tools, § Permission Policies: `auto` – the server evaluates every call; a paused one comes to the owner.
        assertEquals("auto", toolset.getJSONObject("default_config").getJSONObject("permission_policy").getString("type"))
        // tools, § Agent Toolset: a bare toolset enables the web tools; both are switched off explicitly.
        val configs = toolset.getJSONArray("configs").objects().associate { it.getString("name") to it.getBoolean("enabled") }
        assertEquals(mapOf("web_fetch" to false, "web_search" to false), configs)
        // No MCP server, no custom tool, no multiagent block: nothing reaches a third service or the phone.
        listOf("mcp_servers", "multiagent", "skills").forEach { assertFalse(it, body.has(it)) }
        assertFalse(body.toString().contains(FAKE_GITHUB_TOKEN))

        // An update is the same agent with a new version (core, § Versioning); the current version is read first.
        endpoint.agentVersion = 4
        val updated = cloud.saveAgent("claude-sonnet-5-5", "agent_fake_01")
        assertEquals("agent_fake_01", updated.id)
        assertEquals(5, updated.version)
        assertEquals(1, endpoint.sent("GET", "/v1/agents/agent_fake_01").size)
        val update = endpoint.sent("POST", "/v1/agents/agent_fake_01").single().body!!
        assertEquals(4, update.getInt("version"))
        assertEquals("claude-sonnet-5-5", update.getString("model"))
        assertEquals(1, endpoint.sent("POST", "/v1/agents").size)
    }

    @Test fun theSandboxIsLimitedToTheListedHostsAndTheTokenLivesInTheVaultForGitHubOnly() {
        assertEquals("env_fake_01", cloud.saveEnvironment("continuum-coding-agents-abc123", null))
        // environments, § Networking: `limited` is deny-by-default.
        val environment = endpoint.sent("POST", "/v1/environments").single().body!!
        assertEquals("continuum-coding-agents-abc123", environment.getString("name"))
        val config = environment.getJSONObject("config")
        assertEquals("cloud", config.getString("type"))
        val networking = config.getJSONObject("networking")
        assertEquals("limited", networking.getString("type"))
        assertTrue(networking.getBoolean("allow_package_managers"))
        assertFalse(networking.getBoolean("allow_mcp_servers"))
        assertEquals(CodingAgentEnvironment.ALLOWED_HOSTS, (0 until networking.getJSONArray("allowed_hosts").length()).map { networking.getJSONArray("allowed_hosts").getString(it) })
        assertEquals("env_fake_01", cloud.saveEnvironment("continuum-coding-agents-abc123", "env_fake_01"))
        assertEquals("limited", endpoint.sent("POST", "/v1/environments/env_fake_01").single().body!!.getJSONObject("config")
            .getJSONObject("networking").getString("type"))

        assertEquals("vlt_fake_01", cloud.createVault("Continuum Coding-Agenten"))
        assertEquals("Continuum Coding-Agenten", endpoint.sent("POST", "/v1/vaults").single().body!!.getString("display_name"))
        assertEquals("vcrd_fake_01", cloud.saveGitHubCredential("vlt_fake_01", null, OwnerSecret(FAKE_GITHUB_TOKEN)))
        // tools, § Vaults – environment-variable credential shape: substituted at egress, for these hosts, header only.
        val auth = endpoint.sent("POST", "/v1/vaults/vlt_fake_01/credentials").single().body!!.getJSONObject("auth")
        assertEquals("environment_variable", auth.getString("type"))
        assertEquals("GITHUB_TOKEN", auth.getString("secret_name"))
        assertEquals(FAKE_GITHUB_TOKEN, auth.getString("secret_value"))
        assertEquals("limited", auth.getJSONObject("networking").getString("type"))
        assertEquals("[\"api.github.com\"]", auth.getJSONObject("networking").getJSONArray("allowed_hosts").toString())
        assertTrue(auth.getJSONObject("injection_location").getBoolean("header"))
        assertFalse(auth.getJSONObject("injection_location").getBoolean("body"))

        // A new token replaces the secret of the same credential; removing the token deletes the credential.
        assertEquals("vcrd_fake_01", cloud.saveGitHubCredential("vlt_fake_01", "vcrd_fake_01", OwnerSecret(FAKE_GITHUB_TOKEN + "9")))
        assertEquals(FAKE_GITHUB_TOKEN + "9", endpoint.sent("POST", "/v1/vaults/vlt_fake_01/credentials/vcrd_fake_01").single().body!!
            .getJSONObject("auth").getString("secret_value"))
        cloud.deleteGitHubCredential("vlt_fake_01", "vcrd_fake_01")
        assertEquals(1, endpoint.sent("DELETE", "/v1/vaults/vlt_fake_01/credentials/vcrd_fake_01").size)
        cloud.deleteVault("vlt_fake_01")
        assertEquals(1, endpoint.sent("DELETE", "/v1/vaults/vlt_fake_01").size)
    }

    @Test fun aSessionPinsTheAgentMountsOneRepositoryAndCarriesTheHardBudget() {
        val session = cloud.createSession(request())
        assertEquals("sesn_fake_01", session.id)
        assertEquals("idle", session.status)
        // api-reference, § CreateSession Request Body.
        val body = endpoint.sent("POST", "/v1/sessions").single().body!!
        val agent = body.getJSONObject("agent")
        assertEquals("agent", agent.getString("type"))
        assertEquals("agent_fake_01", agent.getString("id"))
        assertEquals(1, agent.getInt("version"))
        assertEquals("env_fake_01", body.getString("environment_id"))
        assertEquals("[\"vlt_fake_01\"]", body.getJSONArray("vault_ids").toString())
        // environments, § GitHub Repositories: one resource, checked out at the base branch; the token is never echoed.
        val resource = body.getJSONArray("resources").objects().single()
        assertEquals("github_repository", resource.getString("type"))
        assertEquals("https://github.com/Ullmann27/device-ai", resource.getString("url"))
        assertEquals(FAKE_GITHUB_TOKEN, resource.getString("authorization_token"))
        assertEquals("branch", resource.getJSONObject("checkout").getString("type"))
        assertEquals("main", resource.getJSONObject("checkout").getString("name"))
        // core, § Session budgets: `amount` is an integer string in cents, USD only.
        val budget = body.getJSONObject("budget")
        assertEquals("limit", budget.getString("type"))
        assertEquals("200", budget.getJSONObject("max_list_cost").get("amount"))
        assertEquals("USD", budget.getJSONObject("max_list_cost").getString("currency"))
        // No initial_events: the order is sent only after the stream is open (client patterns, § 7 Stream-first).
        assertFalse(body.has("initial_events"))
        assertEquals("run0001", body.getJSONObject("metadata").getString("continuum_run"))
    }

    @Test fun theOrderTheAnswerAndTheStopAreTheDocumentedEvents() {
        cloud.defineOutcome("sesn_fake_01", "Auftrag", "# Raster", 3)
        cloud.confirmTool("sesn_fake_01", "sevt_abc", allow = true, denyMessage = null)
        cloud.confirmTool("sesn_fake_01", "sevt_def", allow = false, denyMessage = "nicht so")
        cloud.interrupt("sesn_fake_01")
        val sent = endpoint.sent("POST", "/v1/sessions/sesn_fake_01/events").map { it.body!!.getJSONArray("events").objects().single() }
        // outcomes, § The user.define_outcome event.
        assertEquals("user.define_outcome", sent[0].getString("type"))
        assertEquals("Auftrag", sent[0].getString("description"))
        assertEquals("text", sent[0].getJSONObject("rubric").getString("type"))
        assertEquals("# Raster", sent[0].getJSONObject("rubric").getString("content"))
        assertEquals(3, sent[0].getInt("max_iterations"))
        // client patterns, § 4 tool_confirmation round-trip.
        assertEquals("user.tool_confirmation", sent[1].getString("type"))
        assertEquals("sevt_abc", sent[1].getString("tool_use_id"))
        assertEquals("allow", sent[1].getString("result"))
        assertFalse(sent[1].has("deny_message"))
        assertEquals("deny", sent[2].getString("result"))
        assertEquals("nicht so", sent[2].getString("deny_message"))
        // events, § Interrupt.
        assertEquals("user.interrupt", sent[3].getString("type"))
    }

    @Test fun theSessionObjectGivesCostTokensAndTheLastEvaluation() {
        val state = cloud.session("sesn_fake_01")
        assertEquals("idle", state.status)
        assertEquals(137L, state.usage.costCents)
        assertEquals(CodingAgentTokens(12_000, 3_400, 90_000, 8_000), state.usage.tokens)
        assertEquals(420L, state.usage.activeSeconds)
        assertEquals("satisfied", state.outcomeResult)
        assertEquals("Alle Kriterien erfüllt.", state.outcomeExplanation)
        cloud.deleteSession("sesn_fake_01")
        assertEquals(1, endpoint.sent("DELETE", "/v1/sessions/sesn_fake_01").size)
    }

    // ── The event stream ────────────────────────────────────────────────────────────────────────────────────────

    @Test fun streamAndHistoryDeliverTheDocumentedEventsAndTolerateUnknownOnes() {
        val received = java.util.concurrent.CopyOnWriteArrayList<CodingAgentEvent>()
        val stream = cloud.openStream("sesn_fake_01")
        val reader = pool.submit { for (event in stream) received += event }
        await("stream open") { endpoint.streamsOpened.get() == 1 }
        endpoint.emit("session.status_running")
        endpoint.emit("agent.message", "content" to JSONArray().put(JSONObject().put("type", "text").put("text", "Ich fange an.")))
        endpoint.emit("agent.tool_use", "name" to "bash", "input" to JSONObject().put("command", "./gradlew test"), "evaluated_permission" to "ask",
            "evaluation" to JSONObject().put("type", "auto").put("evaluated_permission", JSONObject().put("type", "ask").put("reason_code", "indeterminate")))
        endpoint.emit("session.status_idle", "stop_reason" to JSONObject().put("type", "requires_action").put("event_ids", JSONArray().put("sevt_fake_3")))
        endpoint.emit("session.something_new_2027", "payload" to 1)
        endpoint.emit("session.usage", "usage" to FakeManagedAgentsEndpoint.usage(42))
        endpoint.emit("span.outcome_evaluation_end", "outcome_id" to "outc_fake_01", "outcome_evaluation_start_id" to "sevt_x", "result" to "satisfied",
            "explanation" to "erfüllt", "iteration" to 0)
        endpoint.emit("session.error", "error" to JSONObject().put("type", "repository_authentication_error").put("message", "bad token")
            .put("repository_url", "https://github.com/x/y").put("retry_status", JSONObject().put("type", "terminal")))
        endpoint.emit("session.status_idle", "stop_reason" to JSONObject().put("type", "end_turn"))
        await("events") { received.size == 9 }
        assertTrue(received[0] is CodingAgentEvent.Running)
        assertEquals("Ich fange an.", (received[1] as CodingAgentEvent.Message).text)
        val tool = received[2] as CodingAgentEvent.ToolUse
        assertEquals(listOf("bash", "ask", "./gradlew test", "sevt_fake_3"), listOf(tool.tool, tool.permission, tool.input["command"], tool.id))
        assertEquals(CodingAgentEvent.Idle("sevt_fake_4", "requires_action", listOf("sevt_fake_3")), received[3])
        assertEquals("session.something_new_2027", (received[4] as CodingAgentEvent.Other).type)
        assertEquals(42L, (received[5] as CodingAgentEvent.Usage).costCents)
        assertEquals("satisfied", (received[6] as CodingAgentEvent.EvaluationEnd).result)
        assertEquals(CodingAgentEvent.Error("sevt_fake_8", "repository_authentication_error", "terminal"), received[7])
        assertEquals("end_turn", (received[8] as CodingAgentEvent.Idle).stopReason)

        // The history is the same events, oldest first, with the same ids: the base of the lossless reconnect.
        assertEquals(received.toList(), cloud.listEvents("sesn_fake_01"))

        // Closing from another thread ends the iteration without a failure (STOP, a finished run).
        stream.close()
        reader.get(10, TimeUnit.SECONDS)
    }

    @Test fun aStreamThatEndsWithoutATerminalEventJustEnds() {
        val stream = cloud.openStream("sesn_fake_01")
        val reader = pool.submit<Int> { var count = 0; for (event in stream) count++; count }
        await("stream open") { endpoint.streamsOpened.get() == 1 }
        endpoint.emit("session.status_running")
        endpoint.dropStreams()
        // The controller, not this class, decides that a tail without a terminal event is a lost connection.
        assertEquals(1, reader.get(10, TimeUnit.SECONDS))
        stream.close()
    }

    // ── Failures ────────────────────────────────────────────────────────────────────────────────────────────────

    @Test fun theDocumentedErrorEnvelopeBecomesAStableCodeAndIsNeverRetried() {
        // api-reference, § Error Handling: status and inner error.type.
        listOf(Triple(401, "authentication_error", CodingAgentFailure.INVALID_API_KEY),
            Triple(403, "permission_error", CodingAgentFailure.PERMISSION_DENIED),
            Triple(429, "rate_limit_error", CodingAgentFailure.RATE_LIMITED),
            Triple(500, "api_error", CodingAgentFailure.OVERLOADED),
            Triple(529, "overloaded_error", CodingAgentFailure.OVERLOADED),
            Triple(409, "invalid_request_error", CodingAgentFailure.CONFLICT),
            Triple(400, "invalid_request_error", CodingAgentFailure.BAD_REQUEST)).forEach { (status, type, expected) ->
            val before = endpoint.requests.size
            endpoint.failNext("POST", "/v1/agents", status, type)
            assertEquals("$status $type", expected, code { cloud.saveAgent("claude-opus-5-5", null) })
            assertEquals("no automatic retry after $status", before + 1, endpoint.requests.size)
        }
    }

    @Test fun theSameStatusIsToldApartByWhatWasAsked() {
        // The collection itself is unknown to the account: the beta is not available for it.
        endpoint.failNext("POST", "/v1/agents", 404, "not_found_error")
        assertEquals(CodingAgentFailure.BETA_UNAVAILABLE, code { cloud.saveAgent("claude-opus-5-5", null) })
        endpoint.failNext("POST", "/v1/agents", 403, "permission_error", "Managed Agents beta is not enabled for this organization.")
        assertEquals(CodingAgentFailure.BETA_UNAVAILABLE, code { cloud.saveAgent("claude-opus-5-5", null) })
        // Something stored earlier is gone.
        endpoint.failNext("GET", "/v1/agents/agent_fake_01", 404, "not_found_error")
        assertEquals(CodingAgentFailure.RESOURCE_GONE, code { cloud.saveAgent("claude-opus-5-5", "agent_fake_01") })
        endpoint.failNext("GET", "/v1/sessions/sesn_gone", 404, "not_found_error")
        assertEquals(CodingAgentFailure.SESSION_GONE, code { cloud.session("sesn_gone") })
        endpoint.failNext("GET", "/v1/sessions/sesn_gone/events/stream", 404, "not_found_error")
        assertEquals(CodingAgentFailure.SESSION_GONE, code { cloud.openStream("sesn_gone").use { it.iterator().hasNext() } })
        // Vault errors.
        endpoint.failNext("POST", "/v1/vaults/vlt_fake_01/credentials", 409, "invalid_request_error", "secret_name must be unique")
        assertEquals(CodingAgentFailure.VAULT, code { cloud.saveGitHubCredential("vlt_fake_01", null, OwnerSecret(FAKE_GITHUB_TOKEN)) })
        endpoint.failNext("POST", "/v1/vaults", 400, "invalid_request_error", "display_name is too long")
        assertEquals(CodingAgentFailure.VAULT, code { cloud.createVault("x") })
        // Session create: budget, credit, model, repository.
        endpoint.failNext("POST", "/v1/sessions", 400, "invalid_request_error", "budget.max_list_cost: models without a list price can't be budgeted")
        assertEquals(CodingAgentFailure.BUDGET_REJECTED, code { cloud.createSession(request()) })
        endpoint.failNext("POST", "/v1/sessions", 400, "invalid_request_error", "Your credit balance is too low to access the Anthropic API.")
        assertEquals(CodingAgentFailure.NO_CREDIT, code { cloud.createSession(request()) })
        endpoint.failNext("POST", "/v1/sessions", 400, "invalid_request_error", "resources.0: repository could not be resolved")
        assertEquals(CodingAgentFailure.SESSION_REPOSITORY_ACCESS, code { cloud.createSession(request()) })
        endpoint.failNext("POST", "/v1/agents", 400, "invalid_request_error", "model: claude-x is not available")
        assertEquals(CodingAgentFailure.MODEL_UNAVAILABLE, code { cloud.saveAgent("claude-x", null) })
    }

    @Test fun anUnreachableServiceIsOfflineAndNothingOfTheRequestIsEverPrinted() {
        val captured = ByteArrayOutputStream()
        val out = System.out
        val err = System.err
        System.setOut(PrintStream(captured, true))
        System.setErr(PrintStream(captured, true))
        val failures = ArrayList<CodingAgentException>()
        try {
            val closedPort = java.net.ServerSocket(0).use { it.localPort }
            val offline = SdkCodingAgentCloud(OwnerSecret(FAKE_API_KEY), SdkCodingAgentCloud.Endpoint(baseUrl = "http://127.0.0.1:$closedPort",
                connectTimeout = Duration.ofSeconds(2), readTimeout = Duration.ofSeconds(2), requestTimeout = Duration.ofSeconds(4)))
            try { offline.createVault("x"); fail("no failure") } catch (failure: CodingAgentException) { failures += failure }
            println(offline)
            offline.close()
            endpoint.failNext("POST", "/v1/sessions", 401, "authentication_error", "invalid x-api-key $FAKE_API_KEY")
            try { cloud.createSession(request()); fail("no failure") } catch (failure: CodingAgentException) { failures += failure }
            cloud.saveGitHubCredential("vlt_fake_01", null, OwnerSecret(FAKE_GITHUB_TOKEN))
            cloud.createSession(request())
            println(cloud)
            println(request())
            failures.forEach { it.printStackTrace(); println(it); println(it.message) }
        } finally {
            System.setOut(out)
            System.setErr(err)
        }
        assertEquals(listOf(CodingAgentFailure.OFFLINE, CodingAgentFailure.INVALID_API_KEY), failures.map { it.code })
        val printed = captured.toString("UTF-8")
        assertTrue(printed.contains("SdkCodingAgentCloud(credentials=redacted)"))
        assertFalse("API key printed", printed.contains(FAKE_API_KEY))
        assertFalse("GitHub token printed", printed.contains(FAKE_GITHUB_TOKEN))
        assertNull(failures.last().cause)
        // The key travels in the authentication header of the request and nowhere else.
        val sent = endpoint.requests.last()
        assertTrue((sent.headers["x-api-key"] ?: sent.headers["authorization"]).orEmpty().contains(FAKE_API_KEY))
        assertFalse(sent.path.contains(FAKE_API_KEY) || sent.body.toString().contains(FAKE_API_KEY))
    }

    // ── One whole run through the productive controller and the real SDK client ─────────────────────────────────

    @Test fun aWholeRunThroughTheRealSdkClient() {
        val rig = Rig(pool)
        val github = FakeGitHub().apply {
            heads["Ullmann27/device-ai@continuum-agent/run0001"] = "f".repeat(40)
            pull = CodingAgentGitHub.PullRequest(912, "https://github.com/Ullmann27/device-ai/pull/912", true, "open", false)
        }
        val host = RecordingHost()
        val controller = CodingAgentController(rig.store, rig.tokenStore, rig.account, { cloud }, github, pool, clock = { rig.now },
            newId = { "run0001" }, pause = { }, persistIntervalMillis = 0L)
        controller.giveConsent()
        controller.saveToken(FAKE_GITHUB_TOKEN)
        controller.addRepository("Ullmann27/device-ai")
        controller.attach(host)
        endpoint.onEvent = { event ->
            when (event.getString("type")) {
                // The session starts working when it receives the outcome, pauses on a call, and waits for the owner.
                "user.define_outcome" -> {
                    endpoint.emit("session.status_running")
                    endpoint.emit("agent.tool_use", "name" to "bash", "input" to JSONObject().put("command", "./gradlew publishToMavenLocal"),
                        "evaluated_permission" to "ask", "evaluation" to JSONObject().put("type", "auto")
                            .put("evaluated_permission", JSONObject().put("type", "ask").put("reason_code", "indeterminate")))
                    endpoint.emit("session.status_idle", "stop_reason" to JSONObject().put("type", "requires_action"))
                }
                "user.tool_confirmation" -> {
                    endpoint.emit("session.status_running")
                    endpoint.emit("agent.tool_use", "name" to "edit", "input" to JSONObject().put("file_path", "/workspace/device-ai/Parser.kt"),
                        "evaluated_permission" to "allow")
                    endpoint.emit("agent.message", "content" to JSONArray().put(JSONObject().put("type", "text")
                        .put("text", "GEÄNDERT: Parser.kt\nNICHT GEPRÜFT: Gerätetest\nPULL REQUEST: https://github.com/Ullmann27/device-ai/pull/912")))
                    endpoint.emit("span.outcome_evaluation_end", "outcome_id" to "outc_fake_01", "outcome_evaluation_start_id" to "sevt_s",
                        "result" to "satisfied", "explanation" to "Alle Kriterien erfüllt.", "iteration" to 0)
                    endpoint.emit("session.usage", "usage" to FakeManagedAgentsEndpoint.usage(137))
                    endpoint.emit("session.status_idle", "stop_reason" to JSONObject().put("type", "end_turn"))
                }
            }
        }
        assertTrue(controller.take("Coding-Agent: behebe in device-ai den Fehler im Parser und mach einen Pull Request", "Jarvis"))
        await("confirmation") { controller.pendingConfirmation() != null }
        assertTrue(endpoint.requests.isEmpty())
        assertTrue(controller.take("Ja", "Jarvis"))
        await("approval") { rig.store.runs().firstOrNull()?.status == CodingAgentRunStatus.WAITING_APPROVAL }
        assertTrue(controller.take("Erlauben", "Jarvis"))
        await("report") { host.ended.isNotEmpty() }

        val run = rig.store.runs().single()
        assertEquals(CodingAgentRunStatus.COMPLETED, run.status)
        assertEquals(137L, run.costCents)
        assertEquals(listOf("/workspace/device-ai/Parser.kt"), run.changedFiles)
        assertTrue(host.said.last(), host.said.last().startsWith("Coding-Agent fertig: Der Entwurfs-Pull-Request liegt vor."))
        // Setup once, then the session; the stream was opened before the order was sent.
        val wire = endpoint.requests.map { it.method + " " + it.path }
        assertEquals(listOf("POST /v1/agents", "POST /v1/environments", "POST /v1/vaults", "POST /v1/vaults/vlt_fake_01/credentials",
            "POST /v1/sessions"), wire.take(5))
        val stream = wire.indexOf("GET /v1/sessions/sesn_fake_01/events/stream")
        val firstSend = wire.indexOf("POST /v1/sessions/sesn_fake_01/events")
        assertTrue("stream-first: $wire", stream in 5 until firstSend)
        assertEquals(1, endpoint.streamsOpened.get())
        assertEquals(listOf("user.define_outcome", "user.tool_confirmation"),
            endpoint.sent("POST", "/v1/sessions/sesn_fake_01/events").map { it.body!!.getJSONArray("events").getJSONObject(0).getString("type") })
        assertEquals(1, endpoint.sent("GET", "/v1/sessions/sesn_fake_01").size)
        assertEquals(listOf("claude-opus-5-5" to CodingAgentTokens(12_000, 3_400, 90_000, 8_000)), rig.account.recorded.toList())
        // Every managed-agents request carried the beta header; none was sent twice by a retry.
        assertTrue(endpoint.requests.all { it.headers.getValue("anthropic-beta").contains("managed-agents-2026-04-01") })
        assertFalse((host.everything() + rig.storage.value).contains(FAKE_GITHUB_TOKEN))
    }
}
