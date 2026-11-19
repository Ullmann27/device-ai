package at.heinz.deviceai.codingagents

import at.heinz.deviceai.cockpit.StepState
import at.heinz.deviceai.localmodel.OwnerSecret
import at.heinz.deviceai.localmodel.SecretStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/** What the controller needs from the screen it reports to. Every method is called on the owner's UI thread. */
interface CodingAgentHost {
    /** Runs [action] on the UI thread; the only method called from a worker thread. */
    fun post(action: () -> Unit)
    /** A message of the coding agents in the conversation. Already free of secrets. */
    fun say(text: String)
    /** One line of the activity log. */
    fun activity(line: String)
    /** Opens the work-window timeline of a run. */
    fun runStarted(runId: String, title: String)
    fun step(runId: String, key: String, label: String, state: StepState)
    fun runEnded(runId: String, delivered: Boolean, verified: Boolean)
    /** Settings, the run list or a run changed: an open dialog redraws. */
    fun changed()
    fun openPanel()
    /** No other work is running in front, so a bare „Stopp“ can only mean the coding agent. */
    fun idle(): Boolean
}

/** Delayed work (the run-time limit). Returns the cancellation. */
fun interface CodingAgentTimer {
    fun after(millis: Long, action: () -> Unit): () -> Unit
}

/**
 * The cloud coding agents of the owner: setup, the order with its one confirmation, the run on Claude Managed
 * Agents, approvals, stop, the report and the run list. Android-free; the app is only the client of the session
 * Anthropic hosts.
 *
 * Guarantees of this class, whatever a caller does:
 *  - nothing leaves the device for a run without the Anthropic key, the Claude consent, the coding-agent consent, a
 *    GitHub token, an allow-listed repository, a budget inside the owner's limits and under the monthly limit, and
 *    the owner's explicit confirmation of exactly this order ([confirm]);
 *  - agent, environment and vault are created once by the guarded setup and then only updated in place;
 *  - a failed paid step is never repeated by itself: only the read-only event stream is re-opened, a bounded number
 *    of times, with the documented history-then-tail consolidation;
 *  - every text for the owner passes the redactor; a secret is never part of a message, a log line or a stored run;
 *  - the report states what the app read itself from GitHub and from the session, and marks the agent's own words.
 */
class CodingAgentController(
    val store: CodingAgentStore,
    private val tokenStore: SecretStore,
    private val account: CodingAgentClaudeAccount,
    private val cloudFor: (OwnerSecret) -> CodingAgentCloud,
    private val github: CodingAgentGitHub,
    private val background: Executor,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { java.util.UUID.randomUUID().toString().replace("-", "").take(8) },
    private val timer: CodingAgentTimer = CodingAgentTimer { _, _ -> {} },
    private val pause: (Long) -> Unit = { Thread.sleep(it) },
    /** Ordinary progress is written to the stored run at most this often; a changed status always at once. */
    private val persistIntervalMillis: Long = PERSIST_INTERVAL_MILLIS
) {
    private sealed interface Pending {
        val atMillis: Long
        class Confirm(val run: CodingAgentRun, val onlyAllowed: Boolean, override val atMillis: Long) : Pending
        class AskRepository(val kind: CodingAgentTaskKind, val task: String, val candidates: List<String>,
                            override val atMillis: Long) : Pending
    }

    /** One run this process is following. */
    private class Live {
        /** A worker of this process is reading the session (also between two connection attempts). */
        @Volatile var following = false
        /** This read was started by the app coming to the front, not by the owner: a failure is not announced again. */
        @Volatile var quietLoss = false
        @Volatile var stream: CodingAgentCloud.EventStream? = null
        @Volatile var stop: CodingAgentStopCause? = null
        @Volatile var cancelTimer: (() -> Unit)? = null
        @Volatile var lastPersist = 0L
        /** Paused calls that were answered (by rule or by the owner) or are being answered. */
        val answered: MutableSet<String> = ConcurrentHashMap.newKeySet()
        val announced: MutableSet<String> = ConcurrentHashMap.newKeySet()
        @Volatile var lastStep: String? = null
    }

    @Volatile var host: CodingAgentHost? = null
        private set
    @Volatile private var pending: Pending? = null
    @Volatile private var preparing = false
    @Volatile private var token: OwnerSecret? = null
    @Volatile private var tokenLoaded = false
    @Volatile private var lastQuietReconnect = Long.MIN_VALUE / 2
    private val lives = ConcurrentHashMap<String, Live>()

    private val redactor = CodingAgentRedactor {
        listOfNotNull(runCatching { account.apiKey()?.reveal() }.getOrNull(), token?.reveal())
    }

    val settings: CodingAgentSettings get() = store.settings
    val resources: CodingAgentResources get() = store.resources
    fun runs(): List<CodingAgentRun> = store.runs()
    fun tokenPresent(): Boolean = loadedToken() != null
    /** The order that waits for the owner's „Ja“, as text; null when none waits. */
    fun pendingConfirmation(): String? = (livePending() as? Pending.Confirm)?.let { CodingAgentTexts.confirmation(it.run, it.onlyAllowed) }

    fun statusText(): String = CodingAgentTexts.status(settings, resources, account.apiKey() != null, account.consentGiven(),
        tokenPresent()) + "\n" + CodingAgentTexts.monthly(account.monthSpentCents(), reservedCents(), account.monthlyLimitCents())

    // ── Host ────────────────────────────────────────────────────────────────────────────────────────────────────

    /** The screen is there (again): unfinished business of an earlier process is settled once, without polling. */
    fun attach(host: CodingAgentHost) {
        this.host = host
        store.runs().forEach { run ->
            when {
                lives.containsKey(run.id) -> Unit
                run.active && run.sessionId == null ->
                    conclude(run.id, CodingAgentRunStatus.NOT_STARTED, CodingAgentFailure.APP_RESTARTED_BEFORE_START)
                // Known to be cut off and the app is in front again: one quiet re-read, at most once a minute. A failure
                // changes nothing and says nothing new; „Aktualisieren“ stays the owner's own way.
                run.status == CodingAgentRunStatus.CONNECTION_LOST -> if (ready() && clock() - lastQuietReconnect >= QUIET_RECONNECT_GAP_MILLIS) {
                    lastQuietReconnect = clock()
                    reconnect(run.id, quiet = true)
                }
                // A session of an earlier process: one reconnect, history first. No timer, no polling.
                run.active -> if (ready()) reconnect(run.id) else markLost(run.id, null)
                !run.reported -> report(run.id)
            }
        }
    }

    fun detach(host: CodingAgentHost) { if (this.host === host) this.host = null }

    private fun ready(): Boolean = account.apiKey() != null && account.consentGiven() && settings.consentGiven

    private fun ui(action: (CodingAgentHost) -> Unit) { host?.let { current -> current.post { action(current) } } }
    private fun say(text: String) { val clean = redactor.clean(text); ui { it.say(clean) } }
    private fun activity(line: String) { val clean = redactor.clean(line); ui { it.activity(clean) } }
    private fun changed() = ui { it.changed() }
    private fun step(runId: String, key: String, label: String, state: StepState) {
        val clean = redactor.clean(label)
        ui { it.step(runId, key, clean, state) }
    }

    // ── Setup ───────────────────────────────────────────────────────────────────────────────────────────────────

    fun giveConsent(): Boolean = store.updateSettings { it.copy(consentGiven = true, consentAtMillis = clock()) }.also { changed() }

    /** Revoking does not touch a running session at Anthropic; it only keeps this app from starting or following one. */
    fun revokeConsent(): Boolean = store.updateSettings { it.copy(consentGiven = false, consentAtMillis = 0L) }.also {
        pending = null
        changed()
    }

    /** Stores the fine-grained token encrypted on the device. Nothing is sent; the owner text says what happened. */
    fun saveToken(candidate: String): String {
        val trimmed = candidate.trim()
        return when {
            CodingAgentGitHubToken.classic(trimmed) -> CodingAgentTexts.TOKEN_CLASSIC
            !CodingAgentGitHubToken.plausible(trimmed) -> CodingAgentTexts.TOKEN_IMPLAUSIBLE
            !tokenStore.save(trimmed) -> CodingAgentTexts.TOKEN_NOT_STORED
            else -> {
                token = OwnerSecret(trimmed)
                tokenLoaded = true
                store.updateSettings { it.copy(tokenGeneration = it.tokenGeneration + 1) }
                changed()
                CodingAgentTexts.TOKEN_STORED
            }
        }
    }

    /** Removes the token from the device at once, then the credential from the vault at Anthropic. */
    fun removeToken(done: (String) -> Unit) {
        if (store.runs().any { it.active }) return done(CodingAgentTexts.TOKEN_REMOVE_WHILE_RUNNING)
        val cleared = tokenStore.clear()
        token = null
        tokenLoaded = true
        pending = null
        val local = if (cleared) CodingAgentTexts.TOKEN_REMOVED_LOCAL else CodingAgentTexts.TOKEN_REMOVE_LOCAL_FAILED
        val stored = store.resources
        val vault = stored.vaultId
        val credential = stored.credentialId
        changed()
        if (vault == null || credential == null) return done(local + " " + CodingAgentTexts.TOKEN_VAULT_EMPTY)
        val key = account.apiKey() ?: return done(local + " " + CodingAgentTexts.TOKEN_VAULT_NEEDS_KEY)
        background.execute {
            val problem = try {
                cloudFor(key).deleteGitHubCredential(vault, credential)
                null
            } catch (failure: CodingAgentException) {
                failure.code.takeUnless { it == CodingAgentFailure.RESOURCE_GONE }
            } catch (failure: Throwable) { CodingAgentFailure.UNEXPECTED }
            if (problem == null) store.updateResources { it.copy(credentialId = null, credentialGeneration = 0) }
            val text = local + " " + (if (problem == null) CodingAgentTexts.TOKEN_VAULT_REMOVED
                else CodingAgentTexts.tokenVaultKept(CodingAgentFailure.message(problem)))
            ui { done(redactor.clean(text)); it.changed() }
        }
    }

    /** Null when the repository was added, otherwise the owner text. Format only; GitHub is asked at run time. */
    fun addRepository(input: String): String? {
        val repository = CodingAgentRepository.normalise(input) ?: return CodingAgentTexts.repositoryInvalid(input)
        if (settings.repositories.any { it.equals(repository, ignoreCase = true) }) return null
        if (settings.repositories.size >= CodingAgentSettings.MAX_REPOSITORIES) return CodingAgentTexts.REPOSITORIES_FULL
        return if (store.updateSettings { it.copy(repositories = it.repositories + repository) }) null.also { changed() }
            else CodingAgentFailure.message(CodingAgentFailure.STORAGE)
    }

    fun removeRepository(repository: String): Boolean =
        store.updateSettings { it.copy(repositories = it.repositories.filterNot { entry -> entry.equals(repository, ignoreCase = true) }) }
            .also { pending = null; changed() }

    /** Null when stored; a budget above the owner's maximum or outside the app's bounds is refused with a text. */
    fun setBudget(input: String): String? {
        val cents = CodingAgentMoney.cents(input) ?: return CodingAgentTexts.AMOUNT_INVALID
        if (cents !in CodingAgentSettings.MIN_BUDGET_CENTS..settings.maxBudgetCents) return CodingAgentTexts.budgetOutOfRange(cents, settings)
        return stored { it.copy(budgetCents = cents) }
    }

    fun setMaxBudget(input: String): String? {
        val cents = CodingAgentMoney.cents(input) ?: return CodingAgentTexts.AMOUNT_INVALID
        if (cents !in CodingAgentSettings.MIN_BUDGET_CENTS..CodingAgentSettings.ABSOLUTE_MAX_BUDGET_CENTS)
            return CodingAgentTexts.MAX_BUDGET_RANGE
        return stored { it.copy(maxBudgetCents = cents, budgetCents = it.budgetCents.coerceAtMost(cents)) }
    }

    fun setMaxRunMinutes(input: String): String? {
        val minutes = input.trim().toIntOrNull()?.takeIf { it in CodingAgentSettings.MIN_RUN_MINUTES..CodingAgentSettings.MAX_RUN_MINUTES }
            ?: return CodingAgentTexts.RUN_MINUTES_RANGE
        return stored { it.copy(maxRunMinutes = minutes) }
    }

    /** The owner's own choice; sent to the API exactly as written. The stored agent is updated at the next setup. */
    fun setModel(input: String): String? {
        val model = input.trim()
        if (CodingAgentStore.sanitise(CodingAgentSettings(model = model)).model != model) return CodingAgentTexts.MODEL_INVALID
        return stored { it.copy(model = model) }
    }

    private fun stored(change: (CodingAgentSettings) -> CodingAgentSettings): String? =
        if (store.updateSettings(change)) null.also { pending = null; changed() } else CodingAgentFailure.message(CodingAgentFailure.STORAGE)

    /** „Bei Anthropic einrichten“: creates what is missing, updates what is outdated. Costs no model tokens. */
    fun provision(done: (String) -> Unit) {
        val refusal = when {
            account.apiKey() == null -> CodingAgentFailure.NO_API_KEY
            !account.consentGiven() -> CodingAgentFailure.NO_CLAUDE_CONSENT
            !settings.consentGiven -> CodingAgentFailure.NO_AGENT_CONSENT
            loadedToken() == null -> CodingAgentFailure.NO_GITHUB_TOKEN
            else -> null
        }
        if (refusal != null) return done(CodingAgentFailure.message(refusal))
        val key = account.apiKey()!!
        val secret = loadedToken()!!
        background.execute {
            val text = try {
                ensureResources(cloudFor(key), secret)
                CodingAgentTexts.provisioned(store.resources)
            } catch (failure: CodingAgentException) {
                CodingAgentFailure.message(failure.code)
            } catch (failure: Throwable) { CodingAgentFailure.message(CodingAgentFailure.UNEXPECTED) }
            ui { done(redactor.clean(text)); it.changed() }
        }
    }

    /**
     * The guarded one-time setup: a stored id is never created again, only updated when the prompt version, the
     * model, the sandbox version or the token changed. Every id is stored the moment it exists, so a failure half
     * way leaves nothing behind that the next setup would create twice.
     */
    private fun ensureResources(cloud: CodingAgentCloud, secret: OwnerSecret): CodingAgentResources {
        val settings = store.settings
        if (store.resources.installTag == null) saved { it.copy(installTag = newId().take(6)) }
        fun gone(clear: (CodingAgentResources) -> CodingAgentResources, action: () -> Unit) = try { action() } catch (failure: CodingAgentException) {
            // What the app had created was removed in the account: forget it, so the owner's next setup creates it anew.
            if (failure.code == CodingAgentFailure.RESOURCE_GONE) store.updateResources(clear)
            throw failure
        }
        val fingerprint = CodingAgentPrompt.fingerprint(settings.model)
        if (store.resources.agentId == null || store.resources.agentFingerprint != fingerprint)
            gone({ it.copy(agentId = null, agentVersion = 0, agentFingerprint = null) }) {
                val agent = cloud.saveAgent(settings.model, store.resources.agentId)
                saved { it.copy(agentId = agent.id, agentVersion = agent.version, agentFingerprint = fingerprint) }
            }
        if (store.resources.environmentId == null || store.resources.environmentVersion != CodingAgentEnvironment.VERSION)
            gone({ it.copy(environmentId = null, environmentVersion = 0) }) {
                val id = cloud.saveEnvironment(CodingAgentEnvironment.NAME + "-" + store.resources.installTag, store.resources.environmentId)
                saved { it.copy(environmentId = id, environmentVersion = CodingAgentEnvironment.VERSION) }
            }
        if (store.resources.vaultId == null) {
            val id = cloud.createVault(CodingAgentPrompt.VAULT_NAME)
            saved { it.copy(vaultId = id, credentialId = null, credentialGeneration = 0) }
        }
        if (store.resources.credentialId == null || store.resources.credentialGeneration != settings.tokenGeneration)
            gone({ if (it.credentialId == null) it.copy(vaultId = null) else it.copy(credentialId = null, credentialGeneration = 0) }) {
                val id = cloud.saveGitHubCredential(store.resources.vaultId!!, store.resources.credentialId, secret)
                saved { it.copy(credentialId = id, credentialGeneration = settings.tokenGeneration) }
            }
        return store.resources
    }

    private fun saved(change: (CodingAgentResources) -> CodingAgentResources) {
        if (!store.updateResources(change)) throw CodingAgentException(CodingAgentFailure.STORAGE)
    }

    private fun loadedToken(): OwnerSecret? {
        if (!tokenLoaded) synchronized(this) {
            if (!tokenLoaded) {
                token = runCatching { tokenStore.load() }.getOrNull()
                tokenLoaded = true
            }
        }
        return token
    }

    // ── Order ───────────────────────────────────────────────────────────────────────────────────────────────────

    /** Why no run may start now, or null. [repository] null checks everything that does not depend on one. */
    fun refusal(repository: String?, budgetCents: Int): CodingAgentRefusal? {
        val settings = store.settings
        fun no(code: String) = CodingAgentRefusal(code)
        return when {
            account.apiKey() == null -> no(CodingAgentFailure.NO_API_KEY)
            !account.consentGiven() -> no(CodingAgentFailure.NO_CLAUDE_CONSENT)
            !settings.consentGiven -> no(CodingAgentFailure.NO_AGENT_CONSENT)
            loadedToken() == null -> no(CodingAgentFailure.NO_GITHUB_TOKEN)
            settings.repositories.isEmpty() -> no(CodingAgentFailure.NO_REPOSITORY)
            repository != null && settings.repositories.none { it == repository } -> no(CodingAgentFailure.REPOSITORY_NOT_ALLOWED)
            store.runs().any { it.active } || preparing -> no(CodingAgentFailure.RUN_ACTIVE)
            store.runs().size >= CodingAgentStore.MAX_RUNS -> no(CodingAgentFailure.TOO_MANY_RUNS)
            budgetCents !in CodingAgentSettings.MIN_BUDGET_CENTS..settings.maxBudgetCents ->
                CodingAgentRefusal(CodingAgentFailure.BUDGET_OUT_OF_RANGE, CodingAgentTexts.budgetOutOfRange(budgetCents, settings))
            else -> {
                val spent = account.monthSpentCents()
                val reserved = reservedCents()
                val limit = account.monthlyLimitCents()
                if (spent + reserved + budgetCents > limit) CodingAgentRefusal(CodingAgentFailure.MONTHLY_LIMIT,
                    CodingAgentTexts.monthlyLimit(budgetCents, spent, reserved, limit)) else null
            }
        }
    }

    /** Budgets of sessions whose final cost is not in the monthly counter yet (running, or ended without a reading). */
    fun reservedCents(): Long = store.runs().filter { it.unsettled && !it.historyDeleted }.sumOf { it.budgetCents.toLong() }

    /**
     * One owner turn. True when it belonged to the coding agents: the answer to their open question, or one of the
     * order forms of [CodingAgentOrderParser]. Everything else returns false and keeps today's route.
     */
    fun take(spoken: String, assistantName: String?): Boolean {
        val text = spoken.trim()
        if (text.isEmpty()) return false
        if (answer(text)) return true
        return when (val command = CodingAgentOrderParser.parse(text, assistantName, settings.repositories)) {
            null -> false
            CodingAgentCommand.Panel -> true.also { ui { it.openPanel() } }
            CodingAgentCommand.Stop -> true.also { stop() }
            CodingAgentCommand.Status -> true.also { say(statusLine()) }
            is CodingAgentCommand.Order -> true.also { order(command.kind, command.repository, command.task) }
        }
    }

    /** True when [spoken] would be taken as the answer to an open question or as a stop of a running agent. */
    fun controls(spoken: String, assistantName: String? = null): Boolean {
        val word = normal(spoken)
        return when {
            livePending() is Pending.Confirm && (word in YES_WORDS || word in NO_WORDS) -> true
            waitingApproval() != null && (word in ALLOW_WORDS || word in DENY_WORDS) -> true
            else -> CodingAgentOrderParser.parse(spoken.trim(), assistantName, settings.repositories) == CodingAgentCommand.Stop
        }
    }

    private fun normal(spoken: String): String = spoken.trim().lowercase().trimEnd('.', '!', ' ').replace(Regex("[,\\s]+"), " ")
    private fun waitingApproval(): CodingAgentRun? = store.runs().firstOrNull { it.active && it.approvals.isNotEmpty() }
    private fun livePending(): Pending? = pending?.takeIf { clock() - it.atMillis <= CONFIRMATION_VALID_MILLIS }.also { if (it == null) pending = null }

    private fun answer(text: String): Boolean {
        val word = normal(text)
        when (val open = livePending()) {
            is Pending.Confirm -> return when (word) {
                in YES_WORDS -> true.also { confirm() }
                in NO_WORDS -> true.also { decline() }
                // Anything else is not a yes: the order is dropped and the sentence keeps its own route.
                else -> false.also { pending = null; activity("CODING-AGENT · Bestätigung verworfen · nichts gestartet"); changed() }
            }
            is Pending.AskRepository -> {
                if (word in NO_WORDS) return true.also { decline() }
                val chosen = word.toIntOrNull()?.let { open.candidates.getOrNull(it - 1) }
                    ?: (CodingAgentOrderParser.resolve(text, open.candidates) as? CodingAgentRepoChoice.Named)?.repository
                pending = null
                if (chosen == null) return false.also { activity("CODING-AGENT · Rückfrage verworfen · nichts gestartet") }
                prepare(open.kind, chosen, open.task, settings.budgetCents, onlyAllowed = false)
                return true
            }
            null -> Unit
        }
        waitingApproval()?.let { run ->
            if (word in ALLOW_WORDS) return true.also { decide(run.id, run.approvals.first().eventId, allow = true) }
            if (word in DENY_WORDS) return true.also { decide(run.id, run.approvals.first().eventId, allow = false) }
        }
        if (word in STOP_WORDS && store.runs().any { it.active } && host?.idle() == true) return true.also { stop() }
        return false
    }

    /** An order from the conversation or the dialog. Nothing starts here: it ends in the confirmation or a text. */
    fun order(kind: CodingAgentTaskKind, repository: CodingAgentRepoChoice, task: String, budgetCents: Int = settings.budgetCents) {
        pending = null
        val text = task.trim().take(CodingAgentOrderParser.MAX_ORDER_CHARACTERS)
        if (text.isEmpty()) return say(CodingAgentFailure.message(CodingAgentFailure.EMPTY_TASK))
        refusal(null, budgetCents)?.let { return say(it.message) }
        when (repository) {
            CodingAgentRepoChoice.NoneAllowed -> say(CodingAgentFailure.message(CodingAgentFailure.NO_REPOSITORY))
            is CodingAgentRepoChoice.Unknown -> say(CodingAgentTexts.unknownRepository(repository.said, settings.repositories))
            is CodingAgentRepoChoice.Ask -> {
                pending = Pending.AskRepository(kind, text, repository.candidates, clock())
                say(CodingAgentTexts.askRepository(repository.candidates))
            }
            is CodingAgentRepoChoice.Named -> prepare(kind, repository.repository, text, budgetCents, onlyAllowed = false)
            is CodingAgentRepoChoice.OnlyAllowed -> prepare(kind, repository.repository, text, budgetCents, onlyAllowed = true)
        }
    }

    /** Reads the default branch and its head from GitHub (the first thing that leaves the device), then asks. */
    private fun prepare(kind: CodingAgentTaskKind, repository: String, task: String, budgetCents: Int, onlyAllowed: Boolean) {
        refusal(repository, budgetCents)?.let { return say(it.message) }
        val secret = loadedToken() ?: return
        val settings = store.settings
        preparing = true
        activity("CODING-AGENT · lese Standardzweig von $repository bei GitHub")
        background.execute {
            val result = runCatching {
                val facts = github.repository(repository, secret)
                if (facts.archived || !facts.canPush) throw CodingAgentException(CodingAgentFailure.GITHUB_READ_ONLY)
                facts.defaultBranch to github.branchHead(repository, facts.defaultBranch, secret)
            }
            preparing = false
            result.onSuccess { (branch, head) ->
                val id = newId()
                val run = CodingAgentRun(id = id, kind = kind, repository = repository, baseBranch = branch, baseSha = head,
                    workBranch = CodingAgentRun.BRANCH_PREFIX + id, task = task, budgetCents = budgetCents,
                    maxRunMinutes = settings.maxRunMinutes, model = settings.model, createdAtMillis = clock())
                pending = Pending.Confirm(run, onlyAllowed, clock())
                say(CodingAgentTexts.confirmation(run, onlyAllowed))
                changed()
            }.onFailure { failure ->
                say(CodingAgentTexts.NOT_STARTED + " " + CodingAgentFailure.message((failure as? CodingAgentException)?.code
                    ?: CodingAgentFailure.GITHUB_UNEXPECTED))
                changed()
            }
        }
    }

    fun decline() {
        if (pending == null) return
        pending = null
        say(CodingAgentTexts.DECLINED)
        changed()
    }

    /** The owner's „Ja“ (spoken or tapped) to the order shown last. The only way a run starts. */
    fun confirm() {
        val confirmed = (livePending() as? Pending.Confirm) ?: return say(CodingAgentFailure.message(CodingAgentFailure.NOT_CONFIRMED))
        pending = null
        // Settings may have changed since the question: every precondition is checked again.
        refusal(confirmed.run.repository, confirmed.run.budgetCents)?.let { return say(it.message) }
        val run = confirmed.run.copy(createdAtMillis = clock())
        if (!store.putRun(run)) return say(CodingAgentFailure.message(CodingAgentFailure.STORAGE))
        val live = Live()
        lives[run.id] = live
        ui { it.runStarted(run.id, "Coding-Agent · " + run.repository) }
        step(run.id, STEP_SETUP, "Bei Anthropic vorbereiten", StepState.ACTIVE)
        say(CodingAgentTexts.started(run))
        changed()
        background.execute { launch(run.id, live) }
    }

    private fun launch(runId: String, live: Live) {
        val run = store.run(runId) ?: return
        try {
            val key = account.apiKey() ?: throw CodingAgentException(CodingAgentFailure.NO_API_KEY)
            val secret = loadedToken() ?: throw CodingAgentException(CodingAgentFailure.NO_GITHUB_TOKEN)
            val cloud = cloudFor(key)
            val ready = ensureResources(cloud, secret)
            // „Stopp“ before the session exists: nothing is created for this order.
            if (live.stop != null) return conclude(runId, CodingAgentRunStatus.STOPPED, null)
            val session = cloud.createSession(CodingAgentCloud.SessionRequest(
                agent = CodingAgentCloud.AgentRef(ready.agentId!!, ready.agentVersion), environmentId = ready.environmentId!!,
                vaultId = ready.vaultId!!, title = "Continuum " + run.kind.label + " · " + run.repository + " · " + run.id,
                repository = run.repository, baseBranch = run.baseBranch, githubToken = secret, budgetCents = run.budgetCents,
                runId = run.id))
            if (store.updateRun(runId) { it.copy(sessionId = session.id, status = CodingAgentRunStatus.RUNNING) } == null)
                throw CodingAgentException(CodingAgentFailure.STORAGE)
            step(runId, STEP_SETUP, "Bei Anthropic vorbereitet", StepState.DONE)
            activity("CODING-AGENT · Sitzung angelegt · Budget " + CodingAgentMoney.usd(run.budgetCents))
            armTimeLimit(runId, live)
            follow(runId, live, cloud, kickoff = true)
        } catch (failure: CodingAgentException) {
            conclude(runId, null, failure.code)
        } catch (failure: Throwable) {
            conclude(runId, null, CodingAgentFailure.UNEXPECTED)
        }
    }

    private fun armTimeLimit(runId: String, live: Live) {
        val run = store.run(runId) ?: return
        val left = run.createdAtMillis + run.maxRunMinutes * 60_000L - clock()
        live.cancelTimer?.invoke()
        live.cancelTimer = timer.after(left.coerceAtLeast(0L)) { stop(runId, CodingAgentStopCause.TIME_LIMIT) }
    }

    // ── Run ─────────────────────────────────────────────────────────────────────────────────────────────────────

    private class Broken(val code: String) : RuntimeException(code, null, false, false)

    /**
     * Follows the session until it is really done. Stream first, then the complete history, then the live tail;
     * the state is rebuilt from the whole history on every (re)connect, so no event counts twice. [kickoff]: this
     * is the start the owner just confirmed – the outcome is sent once, after the stream is open, and only when the
     * history does not already hold it.
     */
    private fun follow(runId: String, live: Live, cloud: CodingAgentCloud, kickoff: Boolean) {
        val run = store.run(runId) ?: return
        val sessionId = run.sessionId ?: return
        live.following = true
        try { read(run, sessionId, live, cloud, kickoff) } finally { live.following = false }
    }

    private fun read(run: CodingAgentRun, sessionId: String, live: Live, cloud: CodingAgentCloud, kickoff: Boolean) {
        val runId = run.id
        var attempts = 0
        var kick = kickoff
        while (true) {
            val fold = CodingAgentFold(run.workBranch, run.baseBranch, redactor)
            var received = false
            val problem: String? = try {
                cloud.openStream(sessionId).use { stream ->
                    live.stream = stream
                    cloud.listEvents(sessionId).forEach { fold.accept(it) }
                    if (kick) {
                        try {
                            if (!fold.outcomeDefined) cloud.defineOutcome(sessionId, CodingAgentPrompt.outcomeDescription(run),
                                CodingAgentPrompt.rubric(run), CodingAgentPrompt.OUTCOME_ITERATIONS)
                        } catch (failure: CodingAgentException) {
                            // The order was not delivered. It is not sent a second time by itself.
                            throw Broken(failure.code)
                        }
                        kick = false
                        step(runId, STEP_WORK, "Agent arbeitet", StepState.ACTIVE)
                    } else if (!fold.outcomeDefined) throw Broken(CodingAgentFailure.SESSION_WITHOUT_ORDER)
                    apply(runId, fold, live, cloud, force = true)
                    if (!fold.final) for (event in stream) {
                        received = true
                        fold.accept(event)
                        apply(runId, fold, live, cloud, force = false)
                        if (fold.final) break
                    }
                }
                if (fold.final) null else CodingAgentFailure.STREAM_LOST
            } catch (failure: Broken) {
                return conclude(runId, null, failure.code)
            } catch (failure: CodingAgentException) {
                // A terminal event may have arrived just before the connection broke.
                if (fold.final) null else failure.code
            } finally {
                live.stream = null
            }
            if (problem == null) return finish(runId, fold, live, cloud)
            if (received) attempts = 0
            if (problem in CodingAgentFailure.NOT_RECONNECTABLE || attempts >= MAX_RECONNECTS) return markLost(runId, problem)
            attempts++
            activity("CODING-AGENT · Verbindung unterbrochen · neuer Versuch $attempts von $MAX_RECONNECTS")
            pause(RECONNECT_PAUSE_MILLIS * attempts)
        }
    }

    /** Brings the stored run in line with the folded events and answers what the session is waiting for. */
    private fun apply(runId: String, fold: CodingAgentFold, live: Live, cloud: CodingAgentCloud, force: Boolean) {
        val run = store.run(runId) ?: return
        val sessionId = run.sessionId ?: return
        fold.pendingApprovals.forEach { call ->
            val id = call.id ?: return@forEach
            if (id in live.answered) return@forEach
            val summary = CodingAgentProgress.describe(call.tool, call.input)
            val rule = CodingAgentRules.violation(call.tool, call.input, run.workBranch, run.baseBranch) ?: return@forEach
            // Against the fixed rules of every run: denied at once, the owner is not asked to break his own rule.
            cloud.confirmTool(sessionId, id, allow = false, denyMessage = CodingAgentTexts.denyMessage(rule))
            live.answered += id
            fold.note(CodingAgentTexts.deniedByRule(summary, rule))
            activity("CODING-AGENT · abgelehnt nach Regel · $rule")
        }
        val approvals = fold.pendingApprovals.filter { it.id != null && it.id !in live.answered }
            .map { CodingAgentApproval(it.id!!, it.tool, redactor.clean(CodingAgentProgress.describe(it.tool, it.input))) }
        val status = when {
            fold.final -> run.status
            approvals.isNotEmpty() -> CodingAgentRunStatus.WAITING_APPROVAL
            else -> CodingAgentRunStatus.RUNNING
        }
        val next = run.copy(status = status, approvals = approvals, progress = fold.progress, changedFiles = fold.changedFiles,
            ruleNotes = fold.ruleNotes, summary = fold.summary ?: run.summary, costCents = fold.costCents ?: run.costCents,
            tokens = if (fold.tokens.empty) run.tokens else fold.tokens, activeSeconds = fold.activeSeconds ?: run.activeSeconds,
            outcomeResult = fold.outcomeResult ?: run.outcomeResult, outcomeExplanation = fold.outcomeExplanation ?: run.outcomeExplanation)
        val important = next.status != run.status || next.approvals != run.approvals || next.outcomeResult != run.outcomeResult ||
            next.costCents != run.costCents || next.ruleNotes != run.ruleNotes
        val now = clock()
        if (!force && !important && now - live.lastPersist < persistIntervalMillis) return
        live.lastPersist = now
        if (next != run) store.updateRun(runId) { next }
        fold.progress.lastOrNull()?.takeIf { it != live.lastStep }?.let { line ->
            live.lastStep = line
            step(runId, STEP_WORK, line, if (approvals.isEmpty()) StepState.ACTIVE else StepState.WAITING)
        }
        approvals.filter { live.announced.add(it.eventId) }.forEach { approval ->
            step(runId, STEP_APPROVAL, "Freigabe nötig", StepState.WAITING)
            say(CodingAgentTexts.approval(next, approval))
        }
        changed()
    }

    /** The owner's answer to a paused tool call. */
    fun decide(runId: String, eventId: String, allow: Boolean) {
        val run = store.run(runId) ?: return
        val approval = run.approvals.firstOrNull { it.eventId == eventId } ?: return
        val sessionId = run.sessionId ?: return
        val key = account.apiKey() ?: return say(CodingAgentFailure.message(CodingAgentFailure.NO_API_KEY))
        val live = lives[runId]
        live?.answered?.add(eventId)
        store.updateRun(runId) { it.copy(approvals = it.approvals.filterNot { entry -> entry.eventId == eventId },
            status = if (it.approvals.size <= 1 && it.status == CodingAgentRunStatus.WAITING_APPROVAL) CodingAgentRunStatus.RUNNING else it.status) }
        changed()
        background.execute {
            try {
                cloudFor(key).confirmTool(sessionId, eventId, allow, if (allow) null else CodingAgentTexts.OWNER_DENY_MESSAGE)
                step(runId, STEP_APPROVAL, if (allow) "Freigabe erteilt" else "Freigabe abgelehnt", StepState.DONE)
                activity("CODING-AGENT · " + (if (allow) "erlaubt" else "abgelehnt") + " · " + approval.summary)
            } catch (failure: Throwable) {
                // Not delivered: the question is open again.
                live?.answered?.remove(eventId)
                store.updateRun(runId) { if (it.active) it.copy(approvals = it.approvals + approval, status = CodingAgentRunStatus.WAITING_APPROVAL) else it }
                say(CodingAgentTexts.decisionNotSent(CodingAgentFailure.message((failure as? CodingAgentException)?.code ?: CodingAgentFailure.UNEXPECTED)))
                changed()
            }
        }
    }

    /** „Stopp“: the documented interrupt. The report follows when the session has really come to rest. */
    fun stop(runId: String? = null, cause: CodingAgentStopCause = CodingAgentStopCause.OWNER) {
        val run = (if (runId != null) store.run(runId) else store.runs().firstOrNull { it.active })
            ?: return say(CodingAgentTexts.NOTHING_RUNNING)
        if (!run.active) return if (cause == CodingAgentStopCause.OWNER) say(CodingAgentTexts.NOTHING_RUNNING) else Unit
        val live = lives[run.id]
        val sessionId = run.sessionId
        if (sessionId == null) {
            live?.stop = cause
            return say(CodingAgentTexts.STOP_BEFORE_SESSION)
        }
        val key = account.apiKey() ?: return say(CodingAgentFailure.message(CodingAgentFailure.NO_API_KEY))
        store.updateRun(run.id) { it.copy(stopCause = cause) }
        activity("CODING-AGENT · Stopp wird gesendet" + if (cause == CodingAgentStopCause.TIME_LIMIT) " · Laufzeit erreicht" else "")
        background.execute {
            val cloud = cloudFor(key)
            try {
                // A call that waits for the owner is denied first, so the session is not left waiting on it.
                (store.run(run.id)?.approvals ?: emptyList()).forEach { approval ->
                    live?.answered?.add(approval.eventId)
                    runCatching { cloud.confirmTool(sessionId, approval.eventId, false, CodingAgentTexts.STOP_DENY_MESSAGE) }
                }
                cloud.interrupt(sessionId)
                store.updateRun(run.id) { it.copy(approvals = emptyList()) }
                changed()
                // Nobody is reading this session right now (connection lost earlier): read it to its end.
                if (lives[run.id]?.following != true && store.run(run.id)?.active == true) reconnect(run.id)
            } catch (failure: Throwable) {
                store.updateRun(run.id) { it.copy(stopCause = null) }
                say(CodingAgentTexts.stopNotSent(CodingAgentFailure.message((failure as? CodingAgentException)?.code ?: CodingAgentFailure.UNEXPECTED)))
                changed()
            }
        }
    }

    /** „Aktualisieren“: re-reads a session this process is not following; a finished run gets fresh facts and cost. */
    fun refresh(runId: String) {
        val run = store.run(runId) ?: return
        if (run.sessionId == null || run.historyDeleted) return
        if (!ready()) return say(CodingAgentFailure.message(when {
            account.apiKey() == null -> CodingAgentFailure.NO_API_KEY
            !account.consentGiven() -> CodingAgentFailure.NO_CLAUDE_CONSENT
            else -> CodingAgentFailure.NO_AGENT_CONSENT
        }))
        val key = account.apiKey() ?: return
        val followed = lives[runId]?.following == true
        if (run.active && !followed) return reconnect(runId)
        background.execute {
            // One reading of the session object: the cost so far of a running session, the final one of a finished.
            settle(runId, cloudFor(key))
            if (!followed) {
                val facts = gitFacts(run)
                store.updateRun(runId) { it.copy(facts = facts, reported = false) }
                report(runId)
            }
            changed()
        }
    }

    private fun reconnect(runId: String, quiet: Boolean = false) {
        val key = account.apiKey() ?: return markLost(runId, CodingAgentFailure.NO_API_KEY)
        val live = lives.getOrPut(runId) { Live() }
        live.quietLoss = quiet
        store.updateRun(runId) { if (it.active) it.copy(status = CodingAgentRunStatus.RUNNING, failure = null) else it }
        ui { it.runStarted(runId, "Coding-Agent · " + (store.run(runId)?.repository ?: "")) }
        step(runId, STEP_WORK, "Verbindung zur Sitzung wird aufgebaut", StepState.ACTIVE)
        changed()
        background.execute {
            try {
                armTimeLimit(runId, live)
                follow(runId, live, cloudFor(key), kickoff = false)
            } catch (failure: Throwable) {
                markLost(runId, (failure as? CodingAgentException)?.code ?: CodingAgentFailure.UNEXPECTED)
            }
        }
    }

    /** The stream is gone and was not re-opened. The session may still be working; nothing is claimed about it. */
    private fun markLost(runId: String, code: String?) {
        val live = lives.remove(runId)
        live?.cancelTimer?.invoke()
        val run = store.updateRun(runId) { if (it.active) it.copy(status = CodingAgentRunStatus.CONNECTION_LOST, failure = code) else it } ?: return
        step(runId, STEP_WORK, "Verbindung unterbrochen", StepState.BLOCKED)
        ui { it.runEnded(runId, delivered = false, verified = false) }
        // The owner already knows this run is cut off: a quiet re-read that failed again is not said a second time.
        if (live?.quietLoss != true) say(CodingAgentTexts.connectionLost(run, code?.takeIf { it != CodingAgentFailure.STREAM_LOST }))
        changed()
    }

    /** The session came to rest: final state from the session object, facts from GitHub, then the report. */
    private fun finish(runId: String, fold: CodingAgentFold, live: Live, cloud: CodingAgentCloud) {
        val run = store.run(runId) ?: return
        live.cancelTimer?.invoke()
        step(runId, STEP_WORK, "Sitzung beendet", StepState.DONE)
        step(runId, STEP_REPORT, "Ergebnis auf GitHub prüfen", StepState.ACTIVE)
        val stop = fold.finalStop
        val (status, failure) = when {
            stop == "budget_reached" -> CodingAgentRunStatus.BUDGET_REACHED to null
            run.stopCause != null -> CodingAgentRunStatus.STOPPED to null
            stop == "end_turn" -> if (fold.failure != null && fold.outcomeResult == null) CodingAgentRunStatus.FAILED to fold.failure
                else CodingAgentRunStatus.COMPLETED to null
            stop == "retries_exhausted" -> CodingAgentRunStatus.FAILED to (fold.failure ?: CodingAgentFailure.SESSION_RETRIES_EXHAUSTED)
            stop == "refusal" -> CodingAgentRunStatus.FAILED to CodingAgentFailure.SESSION_REFUSAL
            stop == "terminated" -> if (fold.outcomeResult == "satisfied") CodingAgentRunStatus.COMPLETED to null
                else CodingAgentRunStatus.FAILED to (fold.failure ?: CodingAgentFailure.SESSION_TERMINATED)
            else -> CodingAgentRunStatus.FAILED to CodingAgentFailure.SESSION_UNKNOWN_STOP
        }
        store.updateRun(runId) {
            it.copy(status = status, failure = failure, endedAtMillis = clock(), approvals = emptyList(), progress = fold.progress,
                changedFiles = fold.changedFiles, ruleNotes = fold.ruleNotes, summary = fold.summary ?: it.summary,
                costCents = fold.costCents ?: it.costCents, tokens = if (fold.tokens.empty) it.tokens else fold.tokens,
                activeSeconds = fold.activeSeconds ?: it.activeSeconds, outcomeResult = fold.outcomeResult ?: it.outcomeResult,
                outcomeExplanation = fold.outcomeExplanation ?: it.outcomeExplanation, reported = false)
        }
        settle(runId, cloud)
        val facts = gitFacts(run)
        store.updateRun(runId) { it.copy(facts = facts) }
        lives.remove(runId)
        report(runId)
    }

    /** A run that ends without a session coming to rest: refused, failed before or at the start, or stopped before it. */
    private fun conclude(runId: String, status: CodingAgentRunStatus?, failure: String?) {
        lives.remove(runId)?.cancelTimer?.invoke()
        val run = store.updateRun(runId) {
            it.copy(status = status ?: if (it.sessionId == null) CodingAgentRunStatus.NOT_STARTED else CodingAgentRunStatus.FAILED,
                failure = failure, endedAtMillis = clock(), approvals = emptyList(), reported = false,
                // Nothing was created at Anthropic for it: there is no cost to wait for.
                settled = it.sessionId == null)
        } ?: return
        if (run.sessionId != null) {
            account.apiKey()?.let { settle(runId, cloudFor(it)) }
            val facts = gitFacts(run)
            store.updateRun(runId) { it.copy(facts = facts) }
        }
        report(runId)
    }

    /** Reads the session's final usage and adds what is new to the one monthly counter. Never fails the caller. */
    private fun settle(runId: String, cloud: CodingAgentCloud) {
        val run = store.run(runId) ?: return
        val sessionId = run.sessionId ?: return
        if (run.settled || run.historyDeleted) return
        val state = try {
            var read = cloud.session(sessionId)
            // The stream reports „idle“ slightly before the session object does: a finished run waits a moment for it.
            var waits = 0
            while (!run.active && (read.status == "running" || read.status == "rescheduling") && waits++ < SETTLE_READS) {
                pause(SETTLE_PAUSE_MILLIS)
                read = cloud.session(sessionId)
            }
            read
        } catch (failure: CodingAgentException) {
            // The session no longer exists: there is nothing left to read, and nothing more can be spent on it.
            if (failure.code == CodingAgentFailure.SESSION_GONE) store.updateRun(runId) { it.copy(settled = true) }
            return
        } catch (failure: Throwable) { return }
        val tokens = if (state.usage.tokens.empty) run.tokens else state.usage.tokens
        val fresh = tokens.since(run.recorded)
        if (!fresh.empty) runCatching { account.record(run.model, fresh) }
        store.updateRun(runId) {
            it.copy(tokens = tokens, recorded = tokens, costCents = state.usage.costCents ?: it.costCents,
                activeSeconds = state.usage.activeSeconds ?: it.activeSeconds, outcomeResult = state.outcomeResult ?: it.outcomeResult,
                outcomeExplanation = state.outcomeExplanation?.let { text -> redactor.clean(text).take(CodingAgentFold.MAX_EXPLANATION) }
                    ?: it.outcomeExplanation,
                // A running session can still spend; only a session at rest has its final figure.
                settled = !it.active && state.status != "running" && state.status != "rescheduling")
        }
    }

    /** What exists on GitHub, asked by the app itself with the owner's token. */
    private fun gitFacts(run: CodingAgentRun): CodingAgentGitFacts {
        val secret = loadedToken() ?: return CodingAgentGitFacts(clock(), problem = CodingAgentFailure.NO_GITHUB_TOKEN)
        return try {
            val head = github.branchHead(run.repository, run.workBranch, secret)
            val pull = github.pullRequest(run.repository, run.workBranch, secret)
            val baseNow = github.branchHead(run.repository, run.baseBranch, secret)
            val comparison = if (head != null && run.baseSha != null)
                runCatching { github.compare(run.repository, run.baseSha, head, secret) }.getOrNull() else null
            CodingAgentGitFacts(clock(), head, comparison?.aheadBy, comparison?.changedFiles, pull?.number, pull?.url, pull?.draft,
                pull?.state, pull?.merged, baseNow)
        } catch (failure: CodingAgentException) {
            CodingAgentGitFacts(clock(), problem = failure.code)
        } catch (failure: Throwable) {
            CodingAgentGitFacts(clock(), problem = CodingAgentFailure.GITHUB_UNEXPECTED)
        }
    }

    /** Tells the owner how a run ended – once, and only while a screen is there to show it. */
    private fun report(runId: String) {
        val run = store.run(runId) ?: return
        val current = host ?: return
        if (run.reported || run.active) return
        if (store.updateRun(runId) { it.copy(reported = true) } == null) return
        val text = redactor.clean(CodingAgentReport.compose(run, clock()))
        val delivered = run.status == CodingAgentRunStatus.COMPLETED
        val verified = delivered && run.facts?.pullUrl != null && run.facts.pullDraft == true && run.facts.pullMerged != true
        current.post {
            current.step(runId, STEP_REPORT, CodingAgentReport.stepLabel(run), if (verified) StepState.DONE else if (delivered) StepState.UNVERIFIED else StepState.FAILED)
            current.activity(redactor.clean("CODING-AGENT · " + run.status.label + (run.failure?.let { " · $it" } ?: "")))
            current.say(text)
            current.runEnded(runId, delivered, verified)
            current.changed()
        }
    }

    fun reportText(runId: String): String? = store.run(runId)?.let { redactor.clean(CodingAgentReport.compose(it, clock())) }

    /** One sentence about what the coding agent is doing right now. */
    fun statusLine(): String {
        val run = store.runs().firstOrNull { it.active } ?: return store.runs().firstOrNull()
            ?.let { "Es läuft kein Coding-Agent. Letzter Lauf: " + CodingAgentReport.listLine(it) + "." } ?: CodingAgentTexts.NOTHING_RUNNING
        return CodingAgentReport.statusLine(run, clock())
    }

    /** Permanently deletes the session with its event history and sandbox at Anthropic. Branch and PR stay on GitHub. */
    fun deleteHistory(runId: String, done: (String) -> Unit) {
        val run = store.run(runId) ?: return
        val sessionId = run.sessionId ?: return done(CodingAgentTexts.HISTORY_NONE)
        if (run.historyDeleted) return done(CodingAgentTexts.HISTORY_ALREADY_DELETED)
        if (run.active) return done(CodingAgentTexts.HISTORY_WHILE_RUNNING)
        val key = account.apiKey() ?: return done(CodingAgentFailure.message(CodingAgentFailure.NO_API_KEY))
        background.execute {
            val cloud = cloudFor(key)
            settle(runId, cloud)
            val problem = try {
                cloud.deleteSession(sessionId)
                null
            } catch (failure: CodingAgentException) {
                failure.code.takeUnless { it == CodingAgentFailure.SESSION_GONE }
            } catch (failure: Throwable) { CodingAgentFailure.UNEXPECTED }
            if (problem == null) store.updateRun(runId) { it.copy(historyDeleted = true, settled = true) }
            ui { done(redactor.clean(if (problem == null) CodingAgentTexts.HISTORY_DELETED
                else CodingAgentTexts.historyNotDeleted(CodingAgentFailure.message(problem)))); it.changed() }
        }
    }

    /** Removes a run from the list on this device. Only possible once nothing of it is left at Anthropic. */
    fun removeRun(runId: String): Boolean {
        val run = store.run(runId) ?: return false
        if (run.active || (run.sessionId != null && !run.historyDeleted)) return false
        return store.removeRun(runId).also { changed() }
    }

    override fun toString(): String = "CodingAgentController(runs=${store.runs().size}, secrets=redacted)"

    companion object {
        const val MAX_RECONNECTS = 2
        const val RECONNECT_PAUSE_MILLIS = 3_000L
        const val PERSIST_INTERVAL_MILLIS = 2_000L
        const val SETTLE_READS = 5
        const val SETTLE_PAUSE_MILLIS = 300L
        const val CONFIRMATION_VALID_MILLIS = 5 * 60_000L
        const val QUIET_RECONNECT_GAP_MILLIS = 60_000L
        const val STEP_SETUP = "agent-setup"
        const val STEP_WORK = "agent-work"
        const val STEP_APPROVAL = "agent-approval"
        const val STEP_REPORT = "agent-report"

        /** Only these start a run; „ok“, „mach“ or a nod do not. */
        val YES_WORDS = setOf("ja", "ja bitte", "ja starten", "ja start", "ja los", "ja genau", "starten")
        val NO_WORDS = setOf("nein", "nein danke", "nicht starten", "abbrechen", "abbruch", "stopp", "stop", "lass es", "doch nicht")
        val ALLOW_WORDS = setOf("erlauben", "erlaube", "erlaubt", "ja erlauben", "freigeben", "freigabe erteilen")
        val DENY_WORDS = setOf("ablehnen", "ablehne", "lehne ab", "nicht erlauben", "verweigern", "nein ablehnen")
        val STOP_WORDS = setOf("stopp", "stop")
    }
}
