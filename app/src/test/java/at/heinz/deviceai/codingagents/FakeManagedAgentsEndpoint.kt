package at.heinz.deviceai.codingagents

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A local stand-in for the Claude Managed Agents endpoints, on 127.0.0.1 only, so the REAL SDK client can be driven
 * through a whole run. Paths and bodies follow the bundled API guidance (`shared/managed-agents-api-reference.md`
 * § Agents, Sessions, Events, Environments, Vaults, Credentials and § Error Handling; `shared/managed-agents-core.md`
 * § Session Object). It answers in those shapes and records what the SDK sent. It is no proof of how the real
 * service behaves – that stays NOT_RUN.
 *
 * (The Messages-API fake of the Claude brain tests replays one queued reply per request; a session needs an open
 * event stream next to concurrent calls, so this endpoint routes by path instead.)
 */
internal class FakeManagedAgentsEndpoint : AutoCloseable {
    class Request(val method: String, val path: String, val headers: Map<String, String>, val body: JSONObject?)
    private class Scripted(val status: Int, val type: String, val message: String)

    private val server = LoopbackHttpServer(::handle)
    val baseUrl: String = server.baseUrl
    val requests = CopyOnWriteArrayList<Request>()
    private val failures = ConcurrentHashMap<String, ConcurrentLinkedQueue<Scripted>>()
    /** Persisted session events, as `GET /v1/sessions/{id}/events` returns them. */
    val events = CopyOnWriteArrayList<JSONObject>()
    private val streams = CopyOnWriteArrayList<LinkedBlockingQueue<String>>()
    val streamsOpened = AtomicInteger()
    private val ids = AtomicInteger()
    @Volatile var agentVersion = 1
    @Volatile var sessionStatus = "idle"
    @Volatile var sessionUsage: JSONObject = usage(137)
    @Volatile var outcomeResult: String? = "satisfied"
    /** Called for every event a client sends to `POST /v1/sessions/{id}/events`. */
    @Volatile var onEvent: (JSONObject) -> Unit = {}

    /** The next request to [method] [pathPrefix] is answered with the documented error envelope. */
    fun failNext(method: String, pathPrefix: String, status: Int, type: String, message: String = "Scripted refusal.") {
        failures.getOrPut("$method $pathPrefix") { ConcurrentLinkedQueue() } += Scripted(status, type, message)
    }

    /** A session event the service emits: stored in the history and delivered to every open stream. */
    fun emit(type: String, vararg fields: Pair<String, Any?>): JSONObject {
        val event = JSONObject().put("type", type).put("id", "sevt_fake_" + ids.incrementAndGet()).put("processed_at", "2026-11-19T10:00:00Z")
        fields.forEach { (name, value) -> event.put(name, value ?: JSONObject.NULL) }
        events += event
        streams.forEach { it.offer("event: $type\ndata: $event\n\n") }
        return event
    }

    /** Ends every open stream the way a closed connection does: without a terminal event. */
    fun dropStreams() = streams.forEach { it.offer(END) }

    fun sent(method: String, path: String): List<Request> = requests.filter { it.method == method && it.path == path }

    override fun close() {
        dropStreams()
        server.close()
    }

    private fun handle(exchange: LoopbackHttpServer.Exchange) {
        val method = exchange.method
        val path = exchange.path
        val headers = exchange.headers
        val body = exchange.body.takeIf { it.isNotBlank() }?.let(::JSONObject)
        requests += Request(method, path, headers, body)
        failures.entries.firstOrNull { (key, queue) -> "$method $path".startsWith(key) && queue.isNotEmpty() }?.value?.poll()?.let { scripted ->
            return reply(exchange, scripted.status, JSONObject().put("type", "error")
                .put("error", JSONObject().put("type", scripted.type).put("message", scripted.message)).put("request_id", "req_fake"))
        }
        val parts = path.trim('/').split('/')
        when {
            method == "POST" && path == "/v1/agents" -> reply(exchange, 200, agent("agent_fake_01", body!!))
            method == "GET" && parts.size == 3 && parts[1] == "agents" -> reply(exchange, 200, agent(parts[2], JSONObject()))
            method == "POST" && parts.size == 3 && parts[1] == "agents" -> { agentVersion++; reply(exchange, 200, agent(parts[2], body!!)) }
            method == "POST" && path == "/v1/environments" -> reply(exchange, 200, environment("env_fake_01", body!!))
            method == "POST" && parts.size == 3 && parts[1] == "environments" -> reply(exchange, 200, environment(parts[2], body!!))
            method == "POST" && path == "/v1/vaults" -> reply(exchange, 200, stamped("vault", "vlt_fake_01").put("display_name", body!!.optString("display_name")))
            method == "DELETE" && parts.size == 3 && parts[1] == "vaults" -> reply(exchange, 200, JSONObject().put("type", "vault_deleted").put("id", parts[2]))
            method == "POST" && parts.size == 4 && parts[1] == "vaults" && parts[3] == "credentials" -> reply(exchange, 200, credential("vcrd_fake_01", parts[2]))
            method == "POST" && parts.size == 5 && parts[3] == "credentials" -> reply(exchange, 200, credential(parts[4], parts[2]))
            method == "DELETE" && parts.size == 5 && parts[3] == "credentials" ->
                reply(exchange, 200, JSONObject().put("type", "vault_credential_deleted").put("id", parts[4]))
            method == "POST" && path == "/v1/sessions" -> reply(exchange, 200, session("sesn_fake_01", body))
            method == "GET" && parts.size == 3 && parts[1] == "sessions" -> reply(exchange, 200, session(parts[2], null))
            method == "DELETE" && parts.size == 3 && parts[1] == "sessions" -> reply(exchange, 200, JSONObject().put("type", "session_deleted").put("id", parts[2]))
            method == "GET" && parts.size == 4 && parts[3] == "events" -> reply(exchange, 200, JSONObject().put("data", JSONArray(events)).put("next_page", JSONObject.NULL))
            method == "POST" && parts.size == 4 && parts[3] == "events" -> {
                val sent = body!!.getJSONArray("events")
                val echoed = JSONArray()
                for (index in 0 until sent.length()) {
                    val event = sent.getJSONObject(index)
                    // User events are echoed back on the stream (events, § Event Types (Received)).
                    val fields = event.keys().asSequence().filter { it != "type" }.map { it to event.get(it) }.toList().toTypedArray()
                    echoed.put(emit(event.getString("type"), *fields))
                    onEvent(event)
                }
                reply(exchange, 200, JSONObject().put("data", echoed))
            }
            method == "GET" && parts.size == 5 && parts[4] == "stream" -> stream(exchange)
            else -> reply(exchange, 404, JSONObject().put("type", "error").put("error", JSONObject().put("type", "not_found_error")
                .put("message", "No such route in the fake.")).put("request_id", "req_fake"))
        }
    }

    private fun reply(exchange: LoopbackHttpServer.Exchange, status: Int, body: JSONObject) = exchange.reply(status, body.toString())

    /** Server-sent events; only what happens after the stream is open is delivered (events, § Stream-first ordering). */
    private fun stream(exchange: LoopbackHttpServer.Exchange) {
        val queue = LinkedBlockingQueue<String>()
        streams += queue
        streamsOpened.incrementAndGet()
        try {
            exchange.stream { write ->
                write(": connected\n\n")
                while (true) {
                    val next = queue.poll(30, TimeUnit.SECONDS) ?: break
                    if (next == END) break
                    write(next)
                }
            }
        } catch (ignored: Exception) {
            // The client closed the connection.
        } finally {
            streams -= queue
        }
    }

    private fun stamped(type: String, id: String) = JSONObject().put("type", type).put("id", id).put("created_at", "2026-11-19T09:00:00Z")
        .put("updated_at", "2026-11-19T09:00:00Z").put("archived_at", JSONObject.NULL).put("metadata", JSONObject())

    private fun agent(id: String, body: JSONObject) = stamped("agent", id).put("version", agentVersion).put("name", body.optString("name", "Continuum Coding-Agent"))
        .put("description", body.optString("description")).put("system", body.optString("system"))
        .put("model", JSONObject().put("id", body.optString("model", "claude-opus-5-5")).put("speed", "standard"))
        .put("tools", body.optJSONArray("tools") ?: JSONArray()).put("mcp_servers", JSONArray()).put("skills", JSONArray())

    private fun environment(id: String, body: JSONObject) = stamped("environment", id).put("name", body.optString("name", "env"))
        .put("description", body.optString("description")).put("config", body.optJSONObject("config") ?: JSONObject().put("type", "cloud"))

    /** Secret fields are write-only and never returned (tools, § Vaults). */
    private fun credential(id: String, vaultId: String) = stamped("vault_credential", id).put("vault_id", vaultId)
        .put("display_name", "GitHub-Token").put("auth", JSONObject().put("type", "environment_variable").put("secret_name", "GITHUB_TOKEN")
            .put("networking", JSONObject().put("type", "limited").put("allowed_hosts", JSONArray(listOf("api.github.com"))))
            .put("injection_location", JSONObject().put("header", true).put("body", false)))

    private fun session(id: String, created: JSONObject?) = stamped("session", id).put("title", created?.optString("title") ?: "Continuum")
        .put("status", sessionStatus).put("environment_id", "env_fake_01")
        .put("agent", JSONObject().put("type", "agent").put("id", "agent_fake_01").put("version", agentVersion))
        .put("resources", JSONArray()).put("vault_ids", JSONArray(listOf("vlt_fake_01")))
        .put("usage", if (created != null) JSONObject() else sessionUsage)
        .put("budget", created?.optJSONObject("budget") ?: JSONObject.NULL)
        .put("stats", JSONObject().put("active_seconds", 420.0).put("duration_seconds", 600.0))
        .put("outcome_evaluations", JSONArray().apply {
            if (created == null) outcomeResult?.let { put(JSONObject().put("type", "outcome_evaluation").put("outcome_id", "outc_fake_01")
                .put("description", "Auftrag").put("result", it).put("explanation", "Alle Kriterien erfüllt.").put("iteration", 0)
                .put("completed_at", "2026-11-19T10:05:00Z")) }
        })

    companion object {
        private const val END = "\u0000end"

        /** `usage` of the session object (core, § Session Object): `list_cost.amount` is an integer string in cents. */
        fun usage(cents: Int): JSONObject = JSONObject().put("input_tokens", 12_000).put("output_tokens", 3_400)
            .put("cache_read_input_tokens", 90_000)
            .put("cache_creation", JSONObject().put("ephemeral_5m_input_tokens", 8_000).put("ephemeral_1h_input_tokens", 0))
            .put("list_cost", JSONObject().put("amount", cents.toString()).put("currency", "USD")).put("active_seconds", 420.0)
            .put("server_tool_use", JSONObject().put("web_search_requests", 0).put("web_fetch_requests", 0))
    }
}
