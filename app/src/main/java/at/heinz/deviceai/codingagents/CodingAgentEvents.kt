package at.heinz.deviceai.codingagents

/**
 * One session event in the shape the app works with. Parsed from the raw JSON object of the event stream or the
 * event list (field names as documented for Claude Managed Agents: `agent.message`, `agent.tool_use` with
 * `evaluated_permission`, `session.status_idle` with `stop_reason`, `session.usage`, `span.outcome_evaluation_end`,
 * `session.error`). An event type this app does not know is kept as [Other] and changes nothing.
 */
sealed interface CodingAgentEvent {
    val id: String?

    data class Message(override val id: String?, val text: String) : CodingAgentEvent
    data class Thinking(override val id: String?) : CodingAgentEvent
    /** [permission]: `allow`, `ask` or `deny` as the server evaluated the call; anything else is passed through. */
    data class ToolUse(override val id: String?, val tool: String, val input: Map<String, Any?>, val permission: String,
                       val mcp: Boolean) : CodingAgentEvent
    data class ToolResult(override val id: String?, val toolUseId: String?, val error: Boolean) : CodingAgentEvent
    data class Running(override val id: String?) : CodingAgentEvent
    data class Rescheduled(override val id: String?) : CodingAgentEvent
    data class Terminated(override val id: String?) : CodingAgentEvent
    /** [stopReason]: `end_turn`, `requires_action`, `retries_exhausted`, `budget_reached`, `refusal` or an unknown value. */
    data class Idle(override val id: String?, val stopReason: String, val eventIds: List<String>) : CodingAgentEvent
    data class Usage(override val id: String?, val costCents: Long?, val tokens: CodingAgentTokens, val activeSeconds: Long?) : CodingAgentEvent
    data class OutcomeDefined(override val id: String?) : CodingAgentEvent
    data class EvaluationStart(override val id: String?, val iteration: Int) : CodingAgentEvent
    data class EvaluationEnd(override val id: String?, val result: String, val explanation: String, val iteration: Int) : CodingAgentEvent
    /** [retry]: `retrying`, `exhausted` or `terminal`. */
    data class Error(override val id: String?, val type: String, val retry: String) : CodingAgentEvent
    data class Confirmation(override val id: String?, val toolUseId: String, val allowed: Boolean) : CodingAgentEvent
    data class Interrupt(override val id: String?) : CodingAgentEvent
    data class Other(override val id: String?, val type: String) : CodingAgentEvent

    companion object {
        private fun Map<*, *>.text(name: String): String? = this[name] as? String
        private fun Map<*, *>.map(name: String): Map<*, *>? = this[name] as? Map<*, *>
        private fun Map<*, *>.number(name: String): Long? = when (val value = this[name]) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }

        @Suppress("UNCHECKED_CAST")
        fun parse(raw: Map<String, Any?>): CodingAgentEvent {
            val id = raw.text("id")?.takeIf { it.isNotEmpty() }
            return when (val type = raw.text("type").orEmpty()) {
                "agent.message" -> Message(id, (raw["content"] as? List<*>).orEmpty().mapNotNull { block ->
                    (block as? Map<*, *>)?.takeIf { it.text("type") == "text" }?.text("text")
                }.joinToString("\n").trim())
                "agent.thinking" -> Thinking(id)
                "agent.tool_use", "agent.mcp_tool_use" -> ToolUse(id, raw.text("name").orEmpty(),
                    (raw["input"] as? Map<String, Any?>).orEmpty(), raw.text("evaluated_permission").orEmpty(),
                    mcp = type == "agent.mcp_tool_use")
                "agent.tool_result", "agent.mcp_tool_result" -> ToolResult(id, raw.text("tool_use_id") ?: raw.text("mcp_tool_use_id"),
                    raw["is_error"] == true)
                "session.status_running" -> Running(id)
                "session.status_rescheduled" -> Rescheduled(id)
                "session.status_terminated" -> Terminated(id)
                "session.status_idle" -> raw.map("stop_reason").let { stop ->
                    Idle(id, stop?.text("type").orEmpty(), (stop?.get("event_ids") as? List<*>).orEmpty().filterIsInstance<String>())
                }
                "session.usage" -> usage(id, raw.map("usage"))
                "user.define_outcome" -> OutcomeDefined(id)
                "span.outcome_evaluation_start" -> EvaluationStart(id, raw.number("iteration")?.toInt() ?: 0)
                "span.outcome_evaluation_end" -> EvaluationEnd(id, raw.text("result").orEmpty(), raw.text("explanation").orEmpty(),
                    raw.number("iteration")?.toInt() ?: 0)
                "session.error" -> raw.map("error").let { error ->
                    Error(id, error?.text("type").orEmpty(), error?.map("retry_status")?.text("type").orEmpty())
                }
                "user.tool_confirmation" -> Confirmation(id, raw.text("tool_use_id").orEmpty(), raw.text("result") == "allow")
                "user.interrupt" -> Interrupt(id)
                else -> Other(id, type)
            }
        }

        /** `usage` of a `session.usage` event or of the session object; `list_cost.amount` is an integer string in cents. */
        fun usage(id: String?, usage: Map<*, *>?): Usage = Usage(id,
            costCents = usage?.map("list_cost")?.takeIf { it.text("currency").let { currency -> currency == null || currency == "USD" } }
                ?.number("amount"),
            tokens = CodingAgentTokens(usage?.number("input_tokens") ?: 0, usage?.number("output_tokens") ?: 0,
                usage?.number("cache_read_input_tokens") ?: 0,
                usage?.map("cache_creation")?.values?.sumOf { (it as? Number)?.toLong() ?: 0L } ?: 0),
            activeSeconds = (usage?.get("active_seconds") as? Number)?.toLong())
    }
}

/**
 * The rules of a run as the app can check them on a tool call's input. It is the last of four walls (GitHub token
 * scope, the server's `auto` evaluation, the agent's instructions, this check) and deliberately simple: a call the
 * session paused on is denied without asking when it matches, and an executed call that matches is listed in the
 * report. It cannot see through an obfuscated command, which is why the report also compares the default branch.
 */
object CodingAgentRules {
    private val FORCE = Regex("git\\s+push\\b[^\\n;|&]*(?:--force\\b|--force-with-lease\\b|\\s-f\\b|\\s\\+\\S)")
    private val PUSH = Regex("git\\s+push\\b([^\\n;|&]*)")
    private val DELETE = Regex("git\\s+push\\b[^\\n;|&]*(?:--delete\\b|\\s-d\\b|\\s:\\S)|git\\s+branch\\s+(?:-D|-d|--delete)\\b|" +
        "(?i)-X\\s*DELETE\\b[^\\n]*git/refs")
    // Merging a pull request or a branch on GitHub. A local „git merge“ into the work branch is ordinary work; pushing
    // its result anywhere but the work branch is caught by the push rule below.
    private val MERGE = Regex("(?i)gh\\s+pr\\s+merge\\b|/pulls/\\d+/merge\\b|/repos/[^\\s/]+/[^\\s/]+/merges\\b")
    private val WORKFLOWS = Regex("\\.github/workflows")
    private val SECRETS = Regex("(?i)gh\\s+secret\\b|/actions/secrets\\b|/dependabot/secrets\\b|/codespaces/secrets\\b")
    private val RELEASES = Regex("(?i)gh\\s+release\\b|/repos/[^\\s/]+/[^\\s/]+/releases\\b|git\\s+tag\\b|git\\s+push\\b[^\\n;|&]*--tags\\b|git/refs/tags")
    private val SIGNING = Regex("(?i)\\.jks\\b|\\.keystore\\b|\\.p12\\b|\\.pfx\\b|keystore\\.properties|signing\\.properties")

    /** The rule [tool] with [input] breaks for a run on [workBranch], in the owner's words; null when none. */
    fun violation(tool: String, input: Map<String, Any?>, workBranch: String, baseBranch: String): String? {
        val path = input["file_path"] as? String
        if (tool == "write" || tool == "edit") {
            return when {
                path == null -> null
                WORKFLOWS.containsMatchIn(path) -> "Workflows und CI werden nicht geändert"
                SIGNING.containsMatchIn(path) -> "Signaturdateien werden nicht geändert"
                else -> null
            }
        }
        if (tool != "bash") return null
        val command = (input["command"] as? String) ?: return null
        return when {
            FORCE.containsMatchIn(command) -> "kein Force-Push"
            DELETE.containsMatchIn(command) -> "keine Zweige löschen"
            MERGE.containsMatchIn(command) -> "nichts zusammenführen"
            SECRETS.containsMatchIn(command) -> "keine Secrets"
            RELEASES.containsMatchIn(command) -> "keine Releases oder Tags"
            WORKFLOWS.containsMatchIn(command) && Regex(">|\\btee\\b|\\bsed\\s+-i|\\brm\\b|\\bmv\\b|\\bcp\\b|git\\s+add\\b").containsMatchIn(command) ->
                "Workflows und CI werden nicht geändert"
            PUSH.findAll(command).any { push ->
                val arguments = push.groupValues[1]
                !arguments.contains(workBranch) || Regex("(?<![\\w/-])(?:HEAD:)?" + Regex.escape(baseBranch) + "(?![\\w/-])").containsMatchIn(
                    arguments.replace(workBranch, " "))
            } -> "Push nur auf den Arbeitszweig $workBranch"
            else -> null
        }
    }
}

/** German one-line descriptions of events for the work window, the agents area and the activity log. */
object CodingAgentProgress {
    private fun oneLine(text: String, limit: Int): String =
        text.replace(Regex("\\s+"), " ").trim().let { if (it.length <= limit) it else it.take(limit - 1) + "…" }

    /** What a tool call does, short enough for a list line; the caller redacts it. */
    fun describe(tool: String, input: Map<String, Any?>): String = when (tool) {
        "bash" -> "Befehl: " + oneLine((input["command"] as? String).orEmpty(), 160)
        "read" -> "Liest " + oneLine((input["file_path"] as? String).orEmpty(), 120)
        "write" -> "Schreibt " + oneLine((input["file_path"] as? String).orEmpty(), 120)
        "edit" -> "Ändert " + oneLine((input["file_path"] as? String).orEmpty(), 120)
        "glob" -> "Sucht Dateien: " + oneLine((input["pattern"] as? String).orEmpty(), 120)
        "grep" -> "Sucht im Code: " + oneLine((input["pattern"] as? String).orEmpty(), 120)
        else -> "Werkzeug " + oneLine(tool, 60)
    }

    fun evaluation(result: String): String = when (result) {
        "satisfied" -> "erfüllt"
        "needs_revision" -> "Nacharbeit nötig"
        "max_iterations_reached" -> "nach der letzten erlaubten Runde nicht vollständig erfüllt"
        "failed" -> "nicht bewertbar (Auftrag und Raster passen nicht zusammen)"
        "interrupted" -> "unterbrochen"
        else -> "unbekanntes Ergebnis"
    }

    /** The session's own error types (`session.error.error.type`) as codes of [CodingAgentFailure]. */
    fun failure(errorType: String): String = when (errorType) {
        "repository_authentication_error" -> CodingAgentFailure.SESSION_REPOSITORY_AUTH
        "repository_forbidden_error", "repository_not_found_error" -> CodingAgentFailure.SESSION_REPOSITORY_ACCESS
        "repository_clone_error", "repository_checkout_error" -> CodingAgentFailure.SESSION_REPOSITORY_CLONE
        "billing_error" -> CodingAgentFailure.SESSION_BILLING
        "model_overloaded_error", "model_rate_limited_error", "model_request_failed_error" -> CodingAgentFailure.SESSION_MODEL
        "credential_host_unreachable_error" -> CodingAgentFailure.SESSION_CREDENTIAL_HOST
        else -> CodingAgentFailure.SESSION_RETRIES_EXHAUSTED
    }

    /** The line an event adds to the progress list; null for events that are not worth a line. */
    fun line(event: CodingAgentEvent): String? = when (event) {
        is CodingAgentEvent.Message -> event.text.takeIf { it.isNotEmpty() }?.let { "Agent: " + oneLine(it, 240) }
        is CodingAgentEvent.Thinking -> "Agent denkt nach"
        is CodingAgentEvent.ToolUse -> when (event.permission) {
            "deny" -> "Von Anthropic abgelehnt · " + describe(event.tool, event.input)
            "ask" -> "Wartet auf Freigabe · " + describe(event.tool, event.input)
            else -> describe(event.tool, event.input)
        }
        is CodingAgentEvent.EvaluationStart -> "Ergebnisprüfung, Runde ${event.iteration + 1}"
        is CodingAgentEvent.EvaluationEnd -> "Ergebnisprüfung, Runde ${event.iteration + 1}: " + evaluation(event.result)
        is CodingAgentEvent.Error -> (if (event.retry == "retrying") "Störung, Anthropic versucht es erneut: " else "Fehler der Sitzung: ") +
            CodingAgentFailure.message(failure(event.type))
        is CodingAgentEvent.Rescheduled -> "Sitzung wird nach einer Störung neu eingeplant"
        is CodingAgentEvent.Interrupt -> "Stopp ist bei der Sitzung angekommen"
        else -> null
    }
}

/**
 * The state of one run, folded from its events. It is always rebuilt from the complete event history (after a
 * reconnect or an app restart) and never added to incrementally across connections, so no event counts twice.
 */
class CodingAgentFold(private val workBranch: String, private val baseBranch: String, private val redactor: CodingAgentRedactor) {
    private val seen = HashSet<String>()
    private val asks = LinkedHashMap<String, CodingAgentEvent.ToolUse>()
    private val files = LinkedHashSet<String>()
    private val notes = LinkedHashSet<String>()
    private val lines = ArrayDeque<String>()

    var outcomeDefined = false; private set
    var summary: String? = null; private set
    var costCents: Long? = null; private set
    var tokens = CodingAgentTokens(); private set
    var activeSeconds: Long? = null; private set
    var outcomeResult: String? = null; private set
    var outcomeExplanation: String? = null; private set
    /** The last `stop_reason` of an idle that is not `requires_action`, or "terminated". Null while the session works. */
    var finalStop: String? = null; private set
    var failure: String? = null; private set
    var interrupted = false; private set
    var events = 0; private set

    val pendingApprovals: List<CodingAgentEvent.ToolUse> get() = asks.values.toList()
    val changedFiles: List<String> get() = files.toList()
    val ruleNotes: List<String> get() = notes.toList()
    val progress: List<String> get() = lines.toList()
    val final: Boolean get() = finalStop != null

    /**
     * Takes one event. Returns false when it was seen before (same id): its content is not applied again, but
     * [final] still reflects it, so a terminal event that was already in the history ends the stream loop.
     */
    fun accept(event: CodingAgentEvent): Boolean {
        val id = event.id
        if (id != null && !seen.add(id)) return false
        events++
        when (event) {
            is CodingAgentEvent.OutcomeDefined -> outcomeDefined = true
            is CodingAgentEvent.Message -> if (event.text.isNotEmpty()) summary = redactor.clean(event.text).take(MAX_SUMMARY)
            is CodingAgentEvent.ToolUse -> tool(event)
            is CodingAgentEvent.Confirmation -> asks.remove(event.toolUseId)
            is CodingAgentEvent.Usage -> {
                event.costCents?.let { costCents = it }
                if (!event.tokens.empty) tokens = event.tokens
                event.activeSeconds?.let { activeSeconds = it }
            }
            is CodingAgentEvent.EvaluationEnd -> {
                outcomeResult = event.result
                outcomeExplanation = redactor.clean(event.explanation).take(MAX_EXPLANATION)
            }
            is CodingAgentEvent.Error -> if (event.retry != "retrying") failure = CodingAgentProgress.failure(event.type)
            is CodingAgentEvent.Interrupt -> interrupted = true
            is CodingAgentEvent.Running -> finalStop = null
            is CodingAgentEvent.Idle -> finalStop = event.stopReason.takeIf { it != "requires_action" }?.ifEmpty { "unknown" }
            is CodingAgentEvent.Terminated -> { finalStop = "terminated"; asks.clear() }
            else -> Unit
        }
        if (final) asks.clear()
        CodingAgentProgress.line(event)?.let { line ->
            val clean = redactor.clean(line)
            if (lines.lastOrNull() != clean) lines.addLast(clean)
            while (lines.size > CodingAgentRun.MAX_PROGRESS_LINES) lines.removeFirst()
        }
        return true
    }

    private fun tool(event: CodingAgentEvent.ToolUse) {
        val id = event.id
        val rule = CodingAgentRules.violation(event.tool, event.input, workBranch, baseBranch)
        when (event.permission) {
            "ask" -> if (id != null) asks[id] = event
            "deny" -> Unit
            else -> {
                if (event.tool == "write" || event.tool == "edit") (event.input["file_path"] as? String)?.let {
                    if (files.size < MAX_FILES) files += redactor.clean(it).take(200)
                }
                // Executed without a pause although it looks like a breach: the owner must see it.
                if (rule != null && notes.size < MAX_NOTES) notes += redactor.clean("Ausgeführt, obwohl es wie ein Regelverstoß aussieht " +
                    "($rule): " + CodingAgentProgress.describe(event.tool, event.input))
            }
        }
    }

    /** Notes the app adds itself (a call it denied by rule). */
    fun note(text: String) { if (notes.size < MAX_NOTES) notes += redactor.clean(text) }

    companion object {
        const val MAX_SUMMARY = 6_000
        const val MAX_EXPLANATION = 2_000
        const val MAX_FILES = 60
        const val MAX_NOTES = 20
    }
}
