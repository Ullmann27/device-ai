package at.heinz.deviceai.codingagents

import at.heinz.deviceai.localmodel.OwnerSecret

/**
 * The boundary to Claude Managed Agents. Anthropic hosts the agent loop and the sandbox; the app is only this
 * client. Every method blocks, is called off the main thread, and fails with a [CodingAgentException] that carries
 * a code of [CodingAgentFailure] – never an upstream text and never a secret. Nothing here retries.
 *
 * Production: [SdkCodingAgentCloud] (official Anthropic Java SDK). Tests: a scripted fake, and the SDK
 * implementation against a local fake endpoint. Agent, environment and vault are persistent: the guarded setup
 * ([CodingAgentController.provision]) creates each once, stores its id and afterwards only updates it in place.
 */
interface CodingAgentCloud {
    class AgentRef(val id: String, val version: Int)
    class SessionRef(val id: String, val status: String)

    /** [githubToken] travels in the request body as the repository resource's `authorization_token`. */
    class SessionRequest(
        val agent: AgentRef,
        val environmentId: String,
        val vaultId: String,
        val title: String,
        val repository: String,
        val baseBranch: String,
        val githubToken: OwnerSecret,
        val budgetCents: Int,
        val runId: String
    ) {
        override fun toString(): String = "SessionRequest($repository@$baseBranch, budget=$budgetCents, token=redacted)"
    }

    /** `usage` and `status` of the session object; the outcome fields are the last entry of `outcome_evaluations`. */
    class SessionState(val status: String, val usage: CodingAgentEvent.Usage, val outcomeResult: String?, val outcomeExplanation: String?)

    /** A live event stream. Iterating blocks until the next event; [close] ends the HTTP stream from any thread. */
    interface EventStream : Iterable<CodingAgentEvent>, AutoCloseable {
        override fun close()
    }

    /** Creates the agent when [existing] is null, otherwise updates it in place (a new version, same id). */
    fun saveAgent(model: String, existing: String?): AgentRef
    /** Creates the sandbox template [name] when [existing] is null, otherwise updates its configuration in place. */
    fun saveEnvironment(name: String, existing: String?): String
    fun createVault(name: String): String
    /** Creates the GitHub credential in [vaultId], or replaces the secret of [existingCredentialId]. Returns the credential id. */
    fun saveGitHubCredential(vaultId: String, existingCredentialId: String?, token: OwnerSecret): String
    fun deleteGitHubCredential(vaultId: String, credentialId: String)
    fun deleteVault(vaultId: String)

    /** Registers the session; no work starts and nothing is billed for tokens until the outcome is sent. */
    fun createSession(request: SessionRequest): SessionRef
    fun openStream(sessionId: String): EventStream
    /** The complete persisted history, oldest first. */
    fun listEvents(sessionId: String): List<CodingAgentEvent>
    fun defineOutcome(sessionId: String, description: String, rubric: String, maxIterations: Int)
    fun confirmTool(sessionId: String, toolUseEventId: String, allow: Boolean, denyMessage: String?)
    fun interrupt(sessionId: String)
    fun session(sessionId: String): SessionState
    /** Permanently deletes the session with its event history and sandbox. */
    fun deleteSession(sessionId: String)
}

/** What the app itself asks GitHub with the owner's token: facts for the confirmation and for the report. */
interface CodingAgentGitHub {
    class Repository(val defaultBranch: String, val canPush: Boolean, val archived: Boolean)
    class PullRequest(val number: Int, val url: String, val draft: Boolean, val state: String, val merged: Boolean)
    class Comparison(val aheadBy: Int, val changedFiles: Int)

    fun repository(repository: String, token: OwnerSecret): Repository
    /** Head commit of [branch], or null when the branch does not exist. */
    fun branchHead(repository: String, branch: String, token: OwnerSecret): String?
    fun pullRequest(repository: String, headBranch: String, token: OwnerSecret): PullRequest?
    fun compare(repository: String, baseSha: String, headSha: String, token: OwnerSecret): Comparison
}

/** The existing Claude account of the app: key, consent and the one monthly counter. Nothing is duplicated here. */
interface CodingAgentClaudeAccount {
    fun apiKey(): OwnerSecret?
    fun consentGiven(): Boolean
    fun monthlyLimitCents(): Int
    /** The monthly estimate of the existing spend guard, in whole cents. */
    fun monthSpentCents(): Long
    /** Adds an agent session's tokens to that same monthly counter. */
    fun record(model: String, tokens: CodingAgentTokens)
}
