package at.heinz.deviceai.codingagents

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * What the owner decided about the cloud coding agents. Nothing here is a secret: the GitHub token lives only in
 * the encrypted store, the Anthropic key in the brain's store. [consentGiven] is the explicit confirmation of
 * [CodingAgentTexts.CONSENT]; without it no resource is created and no run starts.
 */
data class CodingAgentSettings(
    val consentGiven: Boolean = false,
    val consentAtMillis: Long = 0L,
    /** The owner's allow-list, `owner/name`. A run is only ever started for one of these. */
    val repositories: List<String> = emptyList(),
    /** Hard session budget of one run in US cents (the API's `budget.max_list_cost`). */
    val budgetCents: Int = DEFAULT_BUDGET_CENTS,
    /** The most the owner lets one run cost; a budget above it is refused. */
    val maxBudgetCents: Int = DEFAULT_MAX_BUDGET_CENTS,
    val maxRunMinutes: Int = DEFAULT_MAX_RUN_MINUTES,
    /** Sent to the API exactly as written. The owner changes it in the settings; the app never picks another one. */
    val model: String = DEFAULT_MODEL,
    /** Counts every stored GitHub token; the vault credential remembers which one it holds. */
    val tokenGeneration: Int = 0
) {
    companion object {
        const val DEFAULT_MODEL = "claude-opus-5-5"
        const val DEFAULT_BUDGET_CENTS = 200
        const val DEFAULT_MAX_BUDGET_CENTS = 500
        const val MIN_BUDGET_CENTS = 25
        /** Above this the app does not go, whatever is typed. */
        const val ABSOLUTE_MAX_BUDGET_CENTS = 5_000
        const val DEFAULT_MAX_RUN_MINUTES = 30
        const val MIN_RUN_MINUTES = 5
        const val MAX_RUN_MINUTES = 180
        const val MAX_REPOSITORIES = 20
    }
}

/** `owner/name` of a GitHub repository; the only shape the allow-list accepts. */
object CodingAgentRepository {
    private val SHAPE = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?/[A-Za-z0-9._-]{1,100}")
    private val URL = Regex("(?i)^(?:https?://)?(?:www\\.)?github\\.com/")

    /** The canonical `owner/name` of what the owner typed (also a github.com link), or null when it is no repository. */
    fun normalise(input: String): String? {
        val candidate = input.trim().replace(URL, "").removeSuffix("/").removeSuffix(".git")
        if (!SHAPE.matches(candidate)) return null
        val name = candidate.substringAfter('/')
        return candidate.takeIf { name != "." && name != ".." && !candidate.substringBefore('/').contains("--") }
    }

    fun url(repository: String): String = "https://github.com/$repository"
}

/** IDs of what the app created in the owner's Anthropic account. Created once, reused for every run. */
data class CodingAgentResources(
    val agentId: String? = null,
    val agentVersion: Int = 0,
    /** Prompt version and model the stored agent was last written with; a difference means "update, not create". */
    val agentFingerprint: String? = null,
    val environmentId: String? = null,
    val environmentVersion: Int = 0,
    val vaultId: String? = null,
    val credentialId: String? = null,
    val credentialGeneration: Int = 0,
    /** Random tag of this installation in the names of what it created; set with the first resource. */
    val installTag: String? = null
) {
    val nothing: Boolean get() = agentId == null && environmentId == null && vaultId == null && credentialId == null

    fun complete(settings: CodingAgentSettings): Boolean = agentId != null && environmentId != null && vaultId != null &&
        credentialId != null && agentFingerprint == CodingAgentPrompt.fingerprint(settings.model) &&
        environmentVersion == CodingAgentEnvironment.VERSION && credentialGeneration == settings.tokenGeneration
}

enum class CodingAgentRunStatus(val final: Boolean, val label: String) {
    PREPARING(false, "wird gestartet"),
    RUNNING(false, "läuft"),
    WAITING_APPROVAL(false, "wartet auf deine Freigabe"),
    /** The stream broke and could not be re-opened; the session may still be working at Anthropic. */
    CONNECTION_LOST(false, "Verbindung unterbrochen, Stand unbekannt"),
    COMPLETED(true, "abgeschlossen"),
    STOPPED(true, "gestoppt"),
    BUDGET_REACHED(true, "Budget erreicht"),
    FAILED(true, "fehlgeschlagen"),
    /** Nothing was started at Anthropic for this order. */
    NOT_STARTED(true, "nicht gestartet")
}

enum class CodingAgentStopCause { OWNER, TIME_LIMIT }

data class CodingAgentTokens(val input: Long = 0, val output: Long = 0, val cacheRead: Long = 0, val cacheWrite: Long = 0) {
    /** What [this] has more than [recorded]; never negative. */
    fun since(recorded: CodingAgentTokens) = CodingAgentTokens((input - recorded.input).coerceAtLeast(0),
        (output - recorded.output).coerceAtLeast(0), (cacheRead - recorded.cacheRead).coerceAtLeast(0),
        (cacheWrite - recorded.cacheWrite).coerceAtLeast(0))
    val empty: Boolean get() = input == 0L && output == 0L && cacheRead == 0L && cacheWrite == 0L
}

/** A tool call the session paused on; [eventId] is what `user.tool_confirmation.tool_use_id` takes. */
data class CodingAgentApproval(val eventId: String, val tool: String, val summary: String)

/** What the app itself read from GitHub about a run's branch and pull request. Never the agent's own claim. */
data class CodingAgentGitFacts(
    val checkedAtMillis: Long,
    val branchSha: String? = null,
    val commitsAhead: Int? = null,
    val changedFiles: Int? = null,
    val pullNumber: Int? = null,
    val pullUrl: String? = null,
    val pullDraft: Boolean? = null,
    val pullState: String? = null,
    val pullMerged: Boolean? = null,
    val baseShaNow: String? = null,
    /** A stable problem code when GitHub could not be asked; then nothing above is known. */
    val problem: String? = null
)

data class CodingAgentRun(
    val id: String,
    val kind: CodingAgentTaskKind,
    val repository: String,
    val baseBranch: String,
    /** Head of [baseBranch] when the owner confirmed; null when GitHub did not report it. */
    val baseSha: String?,
    val workBranch: String,
    val task: String,
    val budgetCents: Int,
    val maxRunMinutes: Int,
    val model: String,
    val createdAtMillis: Long,
    val status: CodingAgentRunStatus = CodingAgentRunStatus.PREPARING,
    val sessionId: String? = null,
    val endedAtMillis: Long = 0L,
    val stopCause: CodingAgentStopCause? = null,
    /** A code of [CodingAgentFailure]; never an upstream text. */
    val failure: String? = null,
    /** `usage.list_cost` as the API reports it, in cents; null while it has reported none. */
    val costCents: Long? = null,
    val activeSeconds: Long? = null,
    val tokens: CodingAgentTokens = CodingAgentTokens(),
    /** What of [tokens] is already in the monthly counter. */
    val recorded: CodingAgentTokens = CodingAgentTokens(),
    val outcomeResult: String? = null,
    val outcomeExplanation: String? = null,
    /** The agent's last message: its own claims, shown as such. */
    val summary: String? = null,
    val progress: List<String> = emptyList(),
    val changedFiles: List<String> = emptyList(),
    val approvals: List<CodingAgentApproval> = emptyList(),
    /** Calls the app denied by rule, and executed calls that look like a breach of the rules. */
    val ruleNotes: List<String> = emptyList(),
    val facts: CodingAgentGitFacts? = null,
    val historyDeleted: Boolean = false,
    /** The session's final usage was read from the session object and is in the monthly counter. */
    val settled: Boolean = false,
    /** The owner has been told how this run ended. */
    val reported: Boolean = false
) {
    val active: Boolean get() = !status.final

    /** A session exists at Anthropic whose final cost this app has not recorded yet: its whole budget stays reserved. */
    val unsettled: Boolean get() = sessionId != null && !settled

    fun toJson(): JSONObject = JSONObject().put("id", id).put("kind", kind.name).put("repository", repository)
        .put("baseBranch", baseBranch).putOpt("baseSha", baseSha).put("workBranch", workBranch).put("task", task)
        .put("budgetCents", budgetCents).put("maxRunMinutes", maxRunMinutes).put("model", model)
        .put("createdAt", createdAtMillis).put("status", status.name).putOpt("sessionId", sessionId)
        .put("endedAt", endedAtMillis).putOpt("stopCause", stopCause?.name).putOpt("failure", failure)
        .putOpt("costCents", costCents).putOpt("activeSeconds", activeSeconds)
        .put("tokens", tokens.json()).put("recorded", recorded.json())
        .putOpt("outcomeResult", outcomeResult).putOpt("outcomeExplanation", outcomeExplanation).putOpt("summary", summary)
        .put("progress", JSONArray(progress)).put("changedFiles", JSONArray(changedFiles))
        .put("approvals", JSONArray(approvals.map { JSONObject().put("eventId", it.eventId).put("tool", it.tool).put("summary", it.summary) }))
        .put("ruleNotes", JSONArray(ruleNotes)).putOpt("facts", facts?.let(::factsJson)).put("historyDeleted", historyDeleted)
        .put("settled", settled).put("reported", reported)

    companion object {
        const val MAX_PROGRESS_LINES = 40
        const val BRANCH_PREFIX = "continuum-agent/"

        private fun CodingAgentTokens.json() = JSONObject().put("in", input).put("out", output).put("cacheRead", cacheRead)
            .put("cacheWrite", cacheWrite)
        private fun tokens(json: JSONObject?) = if (json == null) CodingAgentTokens() else CodingAgentTokens(json.optLong("in"),
            json.optLong("out"), json.optLong("cacheRead"), json.optLong("cacheWrite"))
        private fun strings(array: JSONArray?): List<String> = (0 until (array?.length() ?: 0)).map { array!!.getString(it) }
        private fun JSONObject.text(name: String): String? = if (has(name) && !isNull(name)) getString(name) else null
        private fun JSONObject.long(name: String): Long? = if (has(name) && !isNull(name)) getLong(name) else null
        private fun JSONObject.int(name: String): Int? = if (has(name) && !isNull(name)) getInt(name) else null
        private fun JSONObject.flag(name: String): Boolean? = if (has(name) && !isNull(name)) getBoolean(name) else null

        private fun factsJson(facts: CodingAgentGitFacts) = JSONObject().put("checkedAt", facts.checkedAtMillis)
            .putOpt("branchSha", facts.branchSha).putOpt("commitsAhead", facts.commitsAhead).putOpt("changedFiles", facts.changedFiles)
            .putOpt("pullNumber", facts.pullNumber).putOpt("pullUrl", facts.pullUrl).putOpt("pullDraft", facts.pullDraft)
            .putOpt("pullState", facts.pullState).putOpt("pullMerged", facts.pullMerged).putOpt("baseShaNow", facts.baseShaNow)
            .putOpt("problem", facts.problem)

        private fun facts(json: JSONObject) = CodingAgentGitFacts(json.optLong("checkedAt"), json.text("branchSha"),
            json.int("commitsAhead"), json.int("changedFiles"), json.int("pullNumber"), json.text("pullUrl"),
            json.flag("pullDraft"), json.text("pullState"), json.flag("pullMerged"), json.text("baseShaNow"), json.text("problem"))

        /** Null for an entry that cannot be read; a damaged entry is dropped instead of breaking the whole list. */
        fun fromJson(json: JSONObject): CodingAgentRun? = runCatching {
            CodingAgentRun(
                id = json.getString("id"), kind = CodingAgentTaskKind.valueOf(json.getString("kind")),
                repository = json.getString("repository"), baseBranch = json.getString("baseBranch"),
                baseSha = json.text("baseSha"), workBranch = json.getString("workBranch"), task = json.getString("task"),
                budgetCents = json.getInt("budgetCents"), maxRunMinutes = json.getInt("maxRunMinutes"),
                model = json.getString("model"), createdAtMillis = json.getLong("createdAt"),
                status = CodingAgentRunStatus.valueOf(json.getString("status")), sessionId = json.text("sessionId"),
                endedAtMillis = json.optLong("endedAt"), stopCause = json.text("stopCause")?.let(CodingAgentStopCause::valueOf),
                failure = json.text("failure"), costCents = json.long("costCents"), activeSeconds = json.long("activeSeconds"),
                tokens = tokens(json.optJSONObject("tokens")), recorded = tokens(json.optJSONObject("recorded")),
                outcomeResult = json.text("outcomeResult"), outcomeExplanation = json.text("outcomeExplanation"),
                summary = json.text("summary"), progress = strings(json.optJSONArray("progress")),
                changedFiles = strings(json.optJSONArray("changedFiles")),
                approvals = json.optJSONArray("approvals")?.let { array ->
                    (0 until array.length()).map { array.getJSONObject(it) }.map {
                        CodingAgentApproval(it.getString("eventId"), it.getString("tool"), it.getString("summary"))
                    }
                }.orEmpty(),
                ruleNotes = strings(json.optJSONArray("ruleNotes")), facts = json.optJSONObject("facts")?.let(::facts),
                historyDeleted = json.optBoolean("historyDeleted"), settled = json.optBoolean("settled"),
                reported = json.optBoolean("reported"))
        }.getOrNull()
    }
}

/** Where the one small JSON document of the coding agents is kept (app-private preferences in production). */
interface CodingAgentStorage {
    fun read(): String?
    fun write(value: String): Boolean
}

class InMemoryCodingAgentStorage : CodingAgentStorage {
    @Volatile var value: String? = null
    @Volatile var failWrites = false
    override fun read(): String? = value
    override fun write(value: String): Boolean { if (failWrites) return false; this.value = value; return true }
}

/**
 * Settings, resource IDs and the run list. Survives app restarts; holds no secret. A value that cannot be read
 * falls back to the safe default (no consent, no repository, nothing provisioned).
 */
class CodingAgentStore(private val storage: CodingAgentStorage) {
    private var loaded = false
    private var currentSettings = CodingAgentSettings()
    private var currentResources = CodingAgentResources()
    private var currentRuns: List<CodingAgentRun> = emptyList()

    val settings: CodingAgentSettings @Synchronized get() { load(); return currentSettings }
    val resources: CodingAgentResources @Synchronized get() { load(); return currentResources }

    /** Newest first. */
    @Synchronized fun runs(): List<CodingAgentRun> { load(); return currentRuns }
    @Synchronized fun run(id: String): CodingAgentRun? { load(); return currentRuns.firstOrNull { it.id == id } }

    /** False when the change could not be stored; the previous value then stays in force. */
    @Synchronized fun updateSettings(change: (CodingAgentSettings) -> CodingAgentSettings): Boolean {
        load()
        val next = sanitise(change(currentSettings))
        return persist(next, currentResources, currentRuns)
    }

    @Synchronized fun updateResources(change: (CodingAgentResources) -> CodingAgentResources): Boolean {
        load()
        return persist(currentSettings, change(currentResources), currentRuns)
    }

    @Synchronized fun putRun(run: CodingAgentRun): Boolean {
        load()
        return persist(currentSettings, currentResources, listOf(run) + currentRuns.filter { it.id != run.id })
    }

    /** The changed run, or null when it does not exist (any more) or the write failed. */
    @Synchronized fun updateRun(id: String, change: (CodingAgentRun) -> CodingAgentRun): CodingAgentRun? {
        load()
        val before = currentRuns.firstOrNull { it.id == id } ?: return null
        val after = change(before)
        if (after == before) return before
        return after.takeIf { persist(currentSettings, currentResources, currentRuns.map { if (it.id == id) after else it }) }
    }

    @Synchronized fun removeRun(id: String): Boolean {
        load()
        return persist(currentSettings, currentResources, currentRuns.filter { it.id != id })
    }

    private fun persist(settings: CodingAgentSettings, resources: CodingAgentResources, runs: List<CodingAgentRun>): Boolean {
        val json = JSONObject()
            .put("settings", JSONObject().put("consent", settings.consentGiven).put("consentAt", settings.consentAtMillis)
                .put("repositories", JSONArray(settings.repositories)).put("budgetCents", settings.budgetCents)
                .put("maxBudgetCents", settings.maxBudgetCents).put("maxRunMinutes", settings.maxRunMinutes)
                .put("model", settings.model).put("tokenGeneration", settings.tokenGeneration))
            .put("resources", JSONObject().putOpt("agentId", resources.agentId).put("agentVersion", resources.agentVersion)
                .putOpt("agentFingerprint", resources.agentFingerprint).putOpt("environmentId", resources.environmentId)
                .put("environmentVersion", resources.environmentVersion).putOpt("vaultId", resources.vaultId)
                .putOpt("credentialId", resources.credentialId).put("credentialGeneration", resources.credentialGeneration)
                .putOpt("installTag", resources.installTag))
            .put("runs", JSONArray(runs.map { it.toJson() }))
        if (!runCatching { storage.write(json.toString()) }.getOrDefault(false)) return false
        currentSettings = settings
        currentResources = resources
        currentRuns = runs
        return true
    }

    private fun load() {
        if (loaded) return
        loaded = true
        val json = runCatching { storage.read()?.let(::JSONObject) }.getOrNull() ?: return
        fun JSONObject.text(name: String): String? = if (has(name) && !isNull(name)) optString(name).takeIf { it.isNotEmpty() } else null
        json.optJSONObject("settings")?.let { stored ->
            val repositories = stored.optJSONArray("repositories")
            currentSettings = sanitise(CodingAgentSettings(
                consentGiven = stored.optBoolean("consent"), consentAtMillis = stored.optLong("consentAt"),
                repositories = (0 until (repositories?.length() ?: 0)).map { repositories!!.optString(it) },
                budgetCents = stored.optInt("budgetCents", CodingAgentSettings.DEFAULT_BUDGET_CENTS),
                maxBudgetCents = stored.optInt("maxBudgetCents", CodingAgentSettings.DEFAULT_MAX_BUDGET_CENTS),
                maxRunMinutes = stored.optInt("maxRunMinutes", CodingAgentSettings.DEFAULT_MAX_RUN_MINUTES),
                model = stored.text("model") ?: CodingAgentSettings.DEFAULT_MODEL,
                tokenGeneration = stored.optInt("tokenGeneration")))
        }
        json.optJSONObject("resources")?.let { stored ->
            currentResources = CodingAgentResources(stored.text("agentId"), stored.optInt("agentVersion"),
                stored.text("agentFingerprint"), stored.text("environmentId"), stored.optInt("environmentVersion"),
                stored.text("vaultId"), stored.text("credentialId"), stored.optInt("credentialGeneration"),
                stored.text("installTag"))
        }
        json.optJSONArray("runs")?.let { stored ->
            currentRuns = (0 until stored.length()).mapNotNull { index -> stored.optJSONObject(index)?.let(CodingAgentRun::fromJson) }
        }
    }

    companion object {
        /** Runs kept in the list; a new run is refused while this many still have their history at Anthropic. */
        const val MAX_RUNS = 100

        /** Every stored or typed value is brought into its allowed range; an unusable repository entry is dropped. */
        fun sanitise(settings: CodingAgentSettings): CodingAgentSettings {
            val max = settings.maxBudgetCents.coerceIn(CodingAgentSettings.MIN_BUDGET_CENTS, CodingAgentSettings.ABSOLUTE_MAX_BUDGET_CENTS)
            return settings.copy(
                repositories = settings.repositories.mapNotNull(CodingAgentRepository::normalise).distinctBy { it.lowercase() }
                    .take(CodingAgentSettings.MAX_REPOSITORIES),
                maxBudgetCents = max,
                budgetCents = settings.budgetCents.coerceIn(CodingAgentSettings.MIN_BUDGET_CENTS, max),
                maxRunMinutes = settings.maxRunMinutes.coerceIn(CodingAgentSettings.MIN_RUN_MINUTES, CodingAgentSettings.MAX_RUN_MINUTES),
                model = settings.model.trim().takeIf { Regex("claude-[a-z0-9.-]{3,60}").matches(it) } ?: CodingAgentSettings.DEFAULT_MODEL,
                tokenGeneration = settings.tokenGeneration.coerceAtLeast(0))
        }
    }
}

/** Owner-facing amounts: „2,00 $“. */
object CodingAgentMoney {
    fun usd(cents: Long): String = String.format(Locale.GERMANY, "%.2f $", cents / 100.0)
    fun usd(cents: Int): String = usd(cents.toLong())

    /** Whole or decimal US dollars as typed („2“, „2,50“, „2.5“) in cents, or null when it is no amount. */
    fun cents(input: String): Int? {
        val text = input.trim().removeSuffix("$").removeSuffix("USD").trim().replace(',', '.')
        if (!Regex("\\d{1,4}(?:\\.\\d{1,2})?").matches(text)) return null
        val whole = text.substringBefore('.').toInt()
        val fraction = text.substringAfter('.', "").padEnd(2, '0').toInt()
        return whole * 100 + fraction
    }
}
