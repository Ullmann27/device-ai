package at.heinz.deviceai.codingagents

import at.heinz.deviceai.cockpit.StepState
import at.heinz.deviceai.localmodel.OwnerSecret
import at.heinz.deviceai.localmodel.SecretCipher
import at.heinz.deviceai.localmodel.SecretStorage
import at.heinz.deviceai.localmodel.SecretStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

/** Obviously fake; never real credentials. Every test that scans for secrets scans for exactly these two. */
internal const val FAKE_API_KEY = "sk-ant-test-0000-not-a-real-key-for-coding-agents"
internal const val FAKE_GITHUB_TOKEN = "github_pat_11FAKEFAKE00000000000_notARealTokenOnlyForTheTests000000000000000000000000000000"

internal class FakeAccount : CodingAgentClaudeAccount {
    @Volatile var key: String? = FAKE_API_KEY
    @Volatile var consent = true
    @Volatile var limitCents = 2_500
    @Volatile var spentCents = 0L
    val recorded = CopyOnWriteArrayList<Pair<String, CodingAgentTokens>>()
    override fun apiKey(): OwnerSecret? = key?.let(::OwnerSecret)
    override fun consentGiven(): Boolean = consent
    override fun monthlyLimitCents(): Int = limitCents
    override fun monthSpentCents(): Long = spentCents
    override fun record(model: String, tokens: CodingAgentTokens) { recorded += model to tokens }
}

internal class FakeGitHub : CodingAgentGitHub {
    val calls = CopyOnWriteArrayList<String>()
    /** A code every call fails with while it is set. */
    @Volatile var failure: String? = null
    @Volatile var defaultBranch = "main"
    @Volatile var canPush = true
    @Volatile var archived = false
    /** `repository@branch` to head commit. */
    val heads = ConcurrentHashMap<String, String>().apply { put("Ullmann27/device-ai@main", "a1b2c3d4e5f60718293a4b5c6d7e8f9012345678") }
    @Volatile var pull: CodingAgentGitHub.PullRequest? = null
    @Volatile var comparison = CodingAgentGitHub.Comparison(2, 3)
    @Volatile var expectedToken = FAKE_GITHUB_TOKEN

    private fun call(name: String, token: OwnerSecret) {
        check(token.reveal() == expectedToken)
        calls += name
        failure?.let { throw CodingAgentException(it) }
    }
    override fun repository(repository: String, token: OwnerSecret): CodingAgentGitHub.Repository {
        call("repository:$repository", token)
        return CodingAgentGitHub.Repository(defaultBranch, canPush, archived)
    }
    override fun branchHead(repository: String, branch: String, token: OwnerSecret): String? {
        call("head:$repository@$branch", token)
        return heads["$repository@$branch"]
    }
    override fun pullRequest(repository: String, headBranch: String, token: OwnerSecret): CodingAgentGitHub.PullRequest? {
        call("pull:$repository@$headBranch", token)
        return pull
    }
    override fun compare(repository: String, baseSha: String, headSha: String, token: OwnerSecret): CodingAgentGitHub.Comparison {
        call("compare:$repository", token)
        return comparison
    }
}

/**
 * A scripted Managed Agents account. Like the real service, a stream delivers only what happens after it was opened
 * and the event list holds everything that was ever emitted.
 */
internal class FakeCloud : CodingAgentCloud {
    private object End
    private object Broken

    val calls = CopyOnWriteArrayList<String>()
    /** Method name to the codes its next calls fail with, one per call. */
    private val failures = ConcurrentHashMap<String, java.util.ArrayDeque<String>>()
    val sessionRequests = CopyOnWriteArrayList<CodingAgentCloud.SessionRequest>()
    val outcomes = CopyOnWriteArrayList<Triple<String, String, Int>>()
    val confirmations = CopyOnWriteArrayList<Triple<String, Boolean, String?>>()
    val credentials = CopyOnWriteArrayList<String>()
    val deletedSessions = CopyOnWriteArrayList<String>()
    val history = CopyOnWriteArrayList<CodingAgentEvent>()
    val interrupts = AtomicInteger()
    val streamsOpened = AtomicInteger()
    @Volatile private var open: LinkedBlockingQueue<Any>? = null
    @Volatile var onOutcome: () -> Unit = {}
    @Volatile var onConfirm: (String, Boolean) -> Unit = { _, _ -> }
    @Volatile var onInterrupt: () -> Unit = {}
    /** Called with the number of the stream that was just opened (1, 2, …). */
    @Volatile var onStream: (Int) -> Unit = {}
    @Volatile var state = CodingAgentCloud.SessionState("idle",
        CodingAgentEvent.Usage(null, 137, CodingAgentTokens(12_000, 3_400, 90_000, 8_000), 420), "satisfied", "Alle Kriterien erfüllt.")
    private val ids = AtomicInteger()

    fun failNext(method: String, vararg codes: String) { failures.getOrPut(method) { java.util.ArrayDeque() }.addAll(codes) }

    private fun call(name: String, detail: String = "") {
        calls += if (detail.isEmpty()) name else "$name:$detail"
        failures[name]?.let { queue -> synchronized(queue) { queue.poll() } }?.let { throw CodingAgentException(it) }
    }

    fun emit(vararg events: CodingAgentEvent) = events.forEach { event -> history += event; open?.offer(event) }
    /** The connection breaks; what was emitted stays in the history. */
    fun breakStream() { open?.offer(Broken) }
    fun endStream() { open?.offer(End) }

    override fun saveAgent(model: String, existing: String?): CodingAgentCloud.AgentRef {
        call("saveAgent", (if (existing == null) "create" else "update") + ":" + model)
        return CodingAgentCloud.AgentRef(existing ?: "agent_fake_01", ids.incrementAndGet())
    }
    override fun saveEnvironment(name: String, existing: String?): String {
        call("saveEnvironment", (if (existing == null) "create" else "update") + ":" + name)
        return existing ?: "env_fake_01"
    }
    override fun createVault(name: String): String { call("createVault"); return "vlt_fake_01" }
    override fun saveGitHubCredential(vaultId: String, existingCredentialId: String?, token: OwnerSecret): String {
        call("saveGitHubCredential", if (existingCredentialId == null) "create" else "update")
        credentials += token.reveal()
        return existingCredentialId ?: "vcrd_fake_01"
    }
    override fun deleteGitHubCredential(vaultId: String, credentialId: String) = call("deleteGitHubCredential", credentialId)
    override fun deleteVault(vaultId: String) = call("deleteVault", vaultId)
    override fun createSession(request: CodingAgentCloud.SessionRequest): CodingAgentCloud.SessionRef {
        call("createSession")
        sessionRequests += request
        return CodingAgentCloud.SessionRef("sesn_fake_0" + sessionRequests.size, "idle")
    }
    override fun openStream(sessionId: String): CodingAgentCloud.EventStream {
        call("openStream")
        val queue = LinkedBlockingQueue<Any>()
        open = queue
        val number = streamsOpened.incrementAndGet()
        onStream(number)
        return object : CodingAgentCloud.EventStream {
            override fun iterator(): Iterator<CodingAgentEvent> = object : Iterator<CodingAgentEvent> {
                private var next: Any? = null
                override fun hasNext(): Boolean {
                    if (next == null) next = queue.take()
                    if (next === Broken) throw CodingAgentException(CodingAgentFailure.STREAM_LOST)
                    return next !== End
                }
                override fun next(): CodingAgentEvent = (next as CodingAgentEvent).also { next = null }
            }
            override fun close() { queue.offer(End); if (open === queue) open = null }
        }
    }
    override fun listEvents(sessionId: String): List<CodingAgentEvent> { call("listEvents"); return history.toList() }
    override fun defineOutcome(sessionId: String, description: String, rubric: String, maxIterations: Int) {
        call("defineOutcome")
        outcomes += Triple(description, rubric, maxIterations)
        emit(Events.outcomeDefined())
        onOutcome()
    }
    override fun confirmTool(sessionId: String, toolUseEventId: String, allow: Boolean, denyMessage: String?) {
        call("confirmTool", toolUseEventId)
        confirmations += Triple(toolUseEventId, allow, denyMessage)
        emit(Events.confirmation(toolUseEventId, allow))
        onConfirm(toolUseEventId, allow)
    }
    override fun interrupt(sessionId: String) {
        call("interrupt")
        interrupts.incrementAndGet()
        onInterrupt()
    }
    override fun session(sessionId: String): CodingAgentCloud.SessionState { call("session"); return state }
    override fun deleteSession(sessionId: String) { call("deleteSession"); deletedSessions += sessionId }
}

/** Session events built from the documented wire shapes and parsed by the productive parser. */
internal object Events {
    private val ids = AtomicInteger()
    private fun event(type: String, vararg fields: Pair<String, Any?>): CodingAgentEvent =
        CodingAgentEvent.parse(mapOf("type" to type, "id" to "sevt_" + ids.incrementAndGet(), "processed_at" to "2026-11-19T10:00:00Z") + fields)

    fun running() = event("session.status_running")
    fun rescheduled() = event("session.status_rescheduled")
    fun terminated() = event("session.status_terminated")
    fun idle(stop: String, vararg eventIds: String) = event("session.status_idle", "stop_reason" to mapOf("type" to stop, "event_ids" to eventIds.toList()))
    fun message(text: String) = event("agent.message", "content" to listOf(mapOf("type" to "text", "text" to text)))
    fun thinking() = event("agent.thinking")
    fun tool(name: String, input: Map<String, Any?>, permission: String = "allow", id: String? = null): CodingAgentEvent =
        CodingAgentEvent.parse(mapOf("type" to "agent.tool_use", "id" to (id ?: "sevt_" + ids.incrementAndGet()), "name" to name,
            "input" to input, "evaluated_permission" to permission,
            "evaluation" to mapOf("type" to "auto", "evaluated_permission" to mapOf("type" to permission))))
    fun bash(command: String, permission: String = "allow", id: String? = null) = tool("bash", mapOf("command" to command), permission, id)
    fun usage(cents: Long, input: Long = 1_000, output: Long = 200) = event("session.usage", "usage" to mapOf(
        "input_tokens" to input, "output_tokens" to output, "cache_read_input_tokens" to 0,
        "cache_creation" to mapOf("ephemeral_5m_input_tokens" to 0, "ephemeral_1h_input_tokens" to 0),
        "list_cost" to mapOf("amount" to cents.toString(), "currency" to "USD"), "active_seconds" to 61.5))
    fun outcomeDefined() = event("user.define_outcome", "outcome_id" to "outc_fake_01")
    fun evaluationStart(iteration: Int) = event("span.outcome_evaluation_start", "outcome_id" to "outc_fake_01", "iteration" to iteration)
    fun evaluationEnd(result: String, explanation: String, iteration: Int = 0) = event("span.outcome_evaluation_end",
        "outcome_id" to "outc_fake_01", "result" to result, "explanation" to explanation, "iteration" to iteration)
    fun error(type: String, retry: String) = event("session.error", "error" to mapOf("type" to type, "message" to "upstream text",
        "retry_status" to mapOf("type" to retry)))
    fun confirmation(toolUseId: String, allow: Boolean) = event("user.tool_confirmation", "tool_use_id" to toolUseId,
        "result" to if (allow) "allow" else "deny")
    fun interrupt() = event("user.interrupt")
}

internal class RecordingHost : CodingAgentHost {
    val said = CopyOnWriteArrayList<String>()
    val activities = CopyOnWriteArrayList<String>()
    val steps = CopyOnWriteArrayList<String>()
    val started = CopyOnWriteArrayList<String>()
    val ended = CopyOnWriteArrayList<String>()
    val panels = AtomicInteger()
    val changes = AtomicInteger()
    @Volatile var idleNow = true
    override fun post(action: () -> Unit) = action()
    override fun say(text: String) { said += text }
    override fun activity(line: String) { activities += line }
    override fun runStarted(runId: String, title: String) { started += title }
    override fun step(runId: String, key: String, label: String, state: StepState) { steps += "$key|$label|$state" }
    override fun runEnded(runId: String, delivered: Boolean, verified: Boolean) { ended += "$delivered|$verified" }
    override fun changed() { changes.incrementAndGet() }
    override fun openPanel() { panels.incrementAndGet() }
    override fun idle(): Boolean = idleNow
    /** Everything this host was ever given, for the secret scan. */
    fun everything(): String = (said + activities + steps + started + ended).joinToString("\n")
}

/** AES-GCM is not what these tests are about: a reversible stand-in keeps the secret store's own logic real. */
internal class PlainCipher : SecretCipher {
    override fun encrypt(plain: ByteArray) = SecretCipher.Sealed(byteArrayOf(1), plain.reversedArray())
    override fun decrypt(sealed: SecretCipher.Sealed): ByteArray = sealed.data.reversedArray()
}

internal class MemorySecrets : SecretStorage {
    val values = ConcurrentHashMap<String, String>()
    override fun read(name: String): String? = values[name]
    override fun write(values: Map<String, String?>): Boolean {
        values.forEach { (key, value) -> if (value == null) this.values.remove(key) else this.values[key] = value }
        return true
    }
}

/** A controller with scripted boundaries. [background] runs work at once on the calling thread unless replaced. */
internal class Rig(background: Executor = Executor { it.run() }, val storage: InMemoryCodingAgentStorage = InMemoryCodingAgentStorage(),
                   val secrets: MemorySecrets = MemorySecrets()) {
    val account = FakeAccount()
    val cloud = FakeCloud()
    val github = FakeGitHub()
    val host = RecordingHost()
    @Volatile var now = 1_800_000_000_000L
    val timers = CopyOnWriteArrayList<Pair<Long, () -> Unit>>()
    val pauses = CopyOnWriteArrayList<Long>()
    private val ids = AtomicInteger()
    val store = CodingAgentStore(storage)
    val tokenStore = SecretStore("github_agent_token", PlainCipher(), secrets, CodingAgentGitHubToken::plausible)
    val controller = CodingAgentController(store, tokenStore, account, { cloud }, github, background, clock = { now },
        newId = { "run%04d".format(ids.incrementAndGet()) },
        timer = CodingAgentTimer { millis, action -> timers += millis to action; { } }, pause = { pauses += it },
        persistIntervalMillis = 0L)

    /** Everything the owner flow needs: both consents, the token, one allowed repository, a screen. */
    fun ready(vararg repositories: String = arrayOf("Ullmann27/device-ai")): Rig {
        controller.giveConsent()
        check(controller.saveToken(FAKE_GITHUB_TOKEN) == CodingAgentTexts.TOKEN_STORED)
        repositories.forEach { check(controller.addRepository(it) == null) }
        controller.attach(host)
        return this
    }

    val run: CodingAgentRun get() = store.runs().first()

    /** Everything that was shown, logged or stored anywhere in this rig. */
    fun emitted(): String = host.everything() + "\n" + storage.value.orEmpty() + "\n" + cloud.calls.joinToString("\n") +
        "\n" + store.runs().joinToString("\n") { it.toString() + CodingAgentReport.compose(it, now) } + "\n" + controller.toString() +
        "\n" + controller.statusText()

    companion object {
        /** A finished, successful session: work on the branch, a draft PR, a satisfied rubric, a report. */
        fun happyScript(cloud: FakeCloud) = {
            cloud.emit(Events.running(), Events.thinking(),
                Events.bash("git checkout -b continuum-agent/run0001"),
                Events.tool("edit", mapOf("file_path" to "/workspace/device-ai/app/src/main/java/Parser.kt")),
                Events.bash("./gradlew test"),
                Events.bash("git push origin continuum-agent/run0001"),
                Events.evaluationStart(0), Events.evaluationEnd("satisfied", "Alle Kriterien erfüllt."),
                Events.message("GEÄNDERT: Parser.kt\nGEPRÜFT: ./gradlew test – grün\nNICHT GEPRÜFT: Gerätetest\nPULL REQUEST: https://github.com/Ullmann27/device-ai/pull/912"),
                Events.usage(137, 12_000, 3_400), Events.idle("end_turn"))
        }
    }
}
