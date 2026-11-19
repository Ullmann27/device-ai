package at.heinz.deviceai.codingagents

import at.heinz.deviceai.localmodel.OwnerSecret
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.core.LogLevel
import com.anthropic.core.RequestOptions
import com.anthropic.core.Timeout
import com.anthropic.core.http.StreamResponse
import com.anthropic.errors.AnthropicException
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.NotFoundException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.ErrorType
import com.anthropic.models.beta.BetaCurrency
import com.anthropic.models.beta.BetaMonetaryAmount
import com.anthropic.models.beta.agents.AgentCreateParams
import com.anthropic.models.beta.agents.AgentUpdateParams
import com.anthropic.models.beta.agents.BetaManagedAgentsAgentToolset20260401Params
import com.anthropic.models.beta.agents.BetaManagedAgentsAgentToolsetDefaultConfigParams
import com.anthropic.models.beta.agents.BetaManagedAgentsAutoPolicy
import com.anthropic.models.beta.agents.BetaManagedAgentsModel
import com.anthropic.models.beta.agents.BetaManagedAgentsWebFetchToolConfigParams
import com.anthropic.models.beta.agents.BetaManagedAgentsWebSearchToolConfigParams
import com.anthropic.models.beta.environments.BetaCloudConfigParams
import com.anthropic.models.beta.environments.BetaLimitedNetworkParams
import com.anthropic.models.beta.environments.EnvironmentCreateParams
import com.anthropic.models.beta.environments.EnvironmentUpdateParams
import com.anthropic.models.beta.sessions.BetaManagedAgentsAgentParams
import com.anthropic.models.beta.sessions.BetaManagedAgentsGitHubRepositoryResourceParams
import com.anthropic.models.beta.sessions.SessionCreateParams
import com.anthropic.models.beta.sessions.events.BetaManagedAgentsStreamSessionEvents
import com.anthropic.models.beta.sessions.events.BetaManagedAgentsUserDefineOutcomeEventParams
import com.anthropic.models.beta.sessions.events.BetaManagedAgentsUserInterruptEventParams
import com.anthropic.models.beta.sessions.events.BetaManagedAgentsUserToolConfirmationEventParams
import com.anthropic.models.beta.sessions.events.EventListParams
import com.anthropic.models.beta.sessions.events.EventSendParams
import com.anthropic.models.beta.sessions.events.EventStreamParams
import com.anthropic.models.beta.vaults.VaultCreateParams
import com.anthropic.models.beta.vaults.credentials.BetaManagedAgentsEnvironmentVariableCreateParams
import com.anthropic.models.beta.vaults.credentials.BetaManagedAgentsEnvironmentVariableUpdateParams
import com.anthropic.models.beta.vaults.credentials.BetaManagedAgentsInjectionLocationParams
import com.anthropic.models.beta.vaults.credentials.CredentialCreateParams
import com.anthropic.models.beta.vaults.credentials.CredentialDeleteParams
import com.anthropic.models.beta.vaults.credentials.CredentialUpdateParams
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Claude Managed Agents (beta) through the official Anthropic Java SDK. The SDK sets the beta header
 * `managed-agents-2026-04-01` itself on every `client.beta().{agents,environments,sessions,vaults}` call.
 *
 * What this class guarantees on its own: no automatic SDK retry (a failed call is reported once), nothing is logged
 * or traced, and every failure leaves as a [CodingAgentException] with a stable code – the upstream message is read
 * only to tell two causes of the same HTTP status apart and never leaves this file.
 */
class SdkCodingAgentCloud(private val apiKey: OwnerSecret, private val endpoint: Endpoint = Endpoint()) : CodingAgentCloud, AutoCloseable {

    /**
     * Production uses the SDK's own base URL (api.anthropic.com); [baseUrl] exists for the local fake endpoint of
     * the JVM tests only. The event stream is long-lived (the server sends heartbeats), so it has its own, long
     * read window and no total limit below the longest run the owner can set.
     */
    data class Endpoint(
        val baseUrl: String? = null,
        val connectTimeout: Duration = Duration.ofSeconds(20),
        val readTimeout: Duration = Duration.ofSeconds(60),
        val requestTimeout: Duration = Duration.ofSeconds(90),
        val streamReadTimeout: Duration = Duration.ofMinutes(15),
        val streamRequestTimeout: Duration = Duration.ofMinutes(CodingAgentSettings.MAX_RUN_MINUTES + 60L)
    )

    private enum class Subject { SETUP, VAULT, SESSION_CREATE, SESSION, STREAM }

    private val client: AnthropicClient by lazy {
        val builder = AnthropicOkHttpClient.builder()
            .apiKey(apiKey.reveal())
            .timeout(Timeout.builder().connect(endpoint.connectTimeout).read(endpoint.readTimeout)
                .request(endpoint.requestTimeout).build())
            .maxRetries(0)
            // Requests, responses and headers are never logged or traced.
            .logLevel(LogLevel.OFF)
            .openTelemetry(false)
        endpoint.baseUrl?.let { builder.baseUrl(it) }
        builder.build()
    }

    /** Whether this client was built for [key]; lets the owner of the instance replace it after a key change. */
    fun uses(key: OwnerSecret): Boolean = key == apiKey

    /**
     * Agent toolset with the server-evaluated `auto` permission policy on every tool and both web tools off: the
     * agent needs neither search nor fetch for work inside one repository. No MCP server, no custom tool, no
     * `multiagent` block – one agent, and nothing that could reach back into the phone.
     */
    private fun toolset() = BetaManagedAgentsAgentToolset20260401Params.builder()
        .defaultConfig(BetaManagedAgentsAgentToolsetDefaultConfigParams.builder()
            .enabled(true)
            .permissionPolicy(BetaManagedAgentsAutoPolicy.builder().build())
            .build())
        .addConfig(BetaManagedAgentsWebFetchToolConfigParams.builder().enabled(false).build())
        .addConfig(BetaManagedAgentsWebSearchToolConfigParams.builder().enabled(false).build())
        .build()

    override fun saveAgent(model: String, existing: String?): CodingAgentCloud.AgentRef = call(Subject.SETUP, existing != null) {
        val agents = client.beta().agents()
        val agent = if (existing == null) agents.create(AgentCreateParams.builder()
            .name(CodingAgentPrompt.AGENT_NAME)
            .description(CodingAgentPrompt.AGENT_DESCRIPTION)
            .model(BetaManagedAgentsModel.of(model))
            .system(CodingAgentPrompt.SYSTEM)
            .addTool(toolset())
            .build())
        else agents.update(existing, AgentUpdateParams.builder()
            // The current version is read first: an update with a stale version is a 409.
            .version(agents.retrieve(existing).version())
            .name(CodingAgentPrompt.AGENT_NAME)
            .description(CodingAgentPrompt.AGENT_DESCRIPTION)
            .model(BetaManagedAgentsModel.of(model))
            .system(CodingAgentPrompt.SYSTEM)
            .addTool(toolset())
            .build())
        CodingAgentCloud.AgentRef(agent.id(), agent.version())
    }

    private fun environmentConfig() = BetaCloudConfigParams.builder()
        .networking(BetaLimitedNetworkParams.builder()
            .allowPackageManagers(CodingAgentEnvironment.ALLOW_PACKAGE_MANAGERS)
            .allowMcpServers(CodingAgentEnvironment.ALLOW_MCP_SERVERS)
            .allowedHosts(CodingAgentEnvironment.ALLOWED_HOSTS)
            .build())
        .build()

    override fun saveEnvironment(name: String, existing: String?): String = call(Subject.SETUP, existing != null) {
        val environments = client.beta().environments()
        if (existing == null) environments.create(EnvironmentCreateParams.builder()
            .name(name).description(CodingAgentEnvironment.DESCRIPTION).config(environmentConfig()).build()).id()
        else environments.update(existing, EnvironmentUpdateParams.builder().config(environmentConfig()).build()).id()
    }

    override fun createVault(name: String): String = call(Subject.VAULT, false) {
        client.beta().vaults().create(VaultCreateParams.builder().displayName(name).build()).id()
    }

    override fun saveGitHubCredential(vaultId: String, existingCredentialId: String?, token: OwnerSecret): String =
        call(Subject.VAULT, true) {
            val credentials = client.beta().vaults().credentials()
            if (existingCredentialId == null) credentials.create(vaultId, CredentialCreateParams.builder()
                .displayName(CodingAgentPrompt.CREDENTIAL_NAME)
                .auth(BetaManagedAgentsEnvironmentVariableCreateParams.builder()
                    .secretName(CodingAgentPrompt.TOKEN_VARIABLE)
                    .secretValue(token.reveal())
                    // Substituted only on requests to these hosts, and only in request headers.
                    .limitedNetworking(CodingAgentEnvironment.TOKEN_HOSTS)
                    .injectionLocation(BetaManagedAgentsInjectionLocationParams.builder().header(true).body(false).build())
                    .build())
                .build()).id()
            else credentials.update(existingCredentialId, CredentialUpdateParams.builder()
                .vaultId(vaultId)
                .auth(BetaManagedAgentsEnvironmentVariableUpdateParams.builder().secretValue(token.reveal()).build())
                .build()).id()
        }

    override fun deleteGitHubCredential(vaultId: String, credentialId: String) = call(Subject.VAULT, true) {
        client.beta().vaults().credentials().delete(credentialId, CredentialDeleteParams.builder().vaultId(vaultId).build())
        Unit
    }

    override fun deleteVault(vaultId: String) = call(Subject.VAULT, true) {
        client.beta().vaults().delete(vaultId)
        Unit
    }

    override fun createSession(request: CodingAgentCloud.SessionRequest): CodingAgentCloud.SessionRef =
        call(Subject.SESSION_CREATE, true) {
            val session = client.beta().sessions().create(SessionCreateParams.builder()
                .agent(BetaManagedAgentsAgentParams.builder().id(request.agent.id).version(request.agent.version).build())
                .environmentId(request.environmentId)
                .title(request.title)
                .addVaultId(request.vaultId)
                .addResource(BetaManagedAgentsGitHubRepositoryResourceParams.builder()
                    .url(CodingAgentRepository.url(request.repository))
                    .authorizationToken(request.githubToken.reveal())
                    .branchCheckout(request.baseBranch)
                    .build())
                // Hard cap, enforced by Anthropic: integer string in cents, USD only.
                .limitBudget(BetaMonetaryAmount.builder().amount(request.budgetCents.toString()).currency(BetaCurrency.USD).build())
                .metadata(SessionCreateParams.Metadata.builder()
                    .putAdditionalProperty("continuum_run", JsonValue.from(request.runId)).build())
                .build())
            CodingAgentCloud.SessionRef(session.id(), session.status().asString())
        }

    override fun openStream(sessionId: String): CodingAgentCloud.EventStream = call(Subject.STREAM, true) {
        val options = RequestOptions.builder().timeout(Timeout.builder().connect(endpoint.connectTimeout)
            .read(endpoint.streamReadTimeout).request(endpoint.streamRequestTimeout).build()).build()
        SdkStream(client.beta().sessions().events().streamStreaming(sessionId, EventStreamParams.none(), options))
    }

    override fun listEvents(sessionId: String): List<CodingAgentEvent> = call(Subject.SESSION, true) {
        val events = ArrayList<CodingAgentEvent>()
        client.beta().sessions().events().list(sessionId, EventListParams.builder().order(EventListParams.Order.ASC).build())
            .autoPager().forEach { event -> event._json().orElse(null)?.let(::plainObject)?.let { events += CodingAgentEvent.parse(it) } }
        events
    }

    private fun send(sessionId: String, build: (EventSendParams.Builder) -> EventSendParams.Builder) = call(Subject.SESSION, true) {
        client.beta().sessions().events().send(sessionId, build(EventSendParams.builder()).build())
        Unit
    }

    override fun defineOutcome(sessionId: String, description: String, rubric: String, maxIterations: Int) = send(sessionId) {
        it.addEvent(BetaManagedAgentsUserDefineOutcomeEventParams.builder().description(description).textRubric(rubric)
            .maxIterations(maxIterations).build())
    }

    override fun confirmTool(sessionId: String, toolUseEventId: String, allow: Boolean, denyMessage: String?) = send(sessionId) {
        val confirmation = BetaManagedAgentsUserToolConfirmationEventParams.builder().toolUseId(toolUseEventId)
            .result(if (allow) BetaManagedAgentsUserToolConfirmationEventParams.Result.ALLOW
                else BetaManagedAgentsUserToolConfirmationEventParams.Result.DENY)
        if (!allow && denyMessage != null) confirmation.denyMessage(denyMessage)
        it.addEvent(confirmation.build())
    }

    override fun interrupt(sessionId: String) = send(sessionId) {
        it.addEvent(BetaManagedAgentsUserInterruptEventParams.builder().build())
    }

    override fun session(sessionId: String): CodingAgentCloud.SessionState = call(Subject.SESSION, true) {
        val session = client.beta().sessions().retrieve(sessionId)
        val usage = session.usage()
        val cost = usage.listCost().orElse(null)
        val creation = usage.cacheCreation().orElse(null)
        val evaluation = session.outcomeEvaluations().lastOrNull()
        CodingAgentCloud.SessionState(session.status().asString(),
            CodingAgentEvent.Usage(null,
                costCents = cost?.takeIf { it.currency() == BetaCurrency.USD }?.amount()?.toLongOrNull(),
                tokens = CodingAgentTokens(usage.inputTokens().orElse(0).toLong(), usage.outputTokens().orElse(0).toLong(),
                    usage.cacheReadInputTokens().orElse(0).toLong(),
                    (creation?.ephemeral5mInputTokens()?.orElse(0) ?: 0).toLong() + (creation?.ephemeral1hInputTokens()?.orElse(0) ?: 0).toLong()),
                activeSeconds = usage.activeSeconds().orElse(null)?.toLong()),
            evaluation?.result(), evaluation?.explanation()?.orElse(null))
    }

    override fun deleteSession(sessionId: String) = call(Subject.SESSION, true) {
        client.beta().sessions().delete(sessionId)
        Unit
    }

    override fun close() { runCatching { client.close() } }

    override fun toString(): String = "SdkCodingAgentCloud(credentials=redacted)"

    private inner class SdkStream(private val response: StreamResponse<BetaManagedAgentsStreamSessionEvents>) : CodingAgentCloud.EventStream {
        private val closed = AtomicBoolean(false)
        private val source: Iterator<BetaManagedAgentsStreamSessionEvents> by lazy { response.stream().iterator() }

        override fun iterator(): Iterator<CodingAgentEvent> = object : Iterator<CodingAgentEvent> {
            private var next: CodingAgentEvent? = null

            override fun hasNext(): Boolean {
                while (next == null) {
                    if (closed.get()) return false
                    val raw = try {
                        call(Subject.STREAM, true) { if (source.hasNext()) source.next() else null }
                    } catch (failure: CodingAgentException) {
                        // Closed from our side (STOP, time limit, a finished run): the end, not a failure.
                        if (closed.get()) return false
                        throw failure
                    } ?: return false
                    next = raw._json().orElse(null)?.let(::plainObject)?.let { CodingAgentEvent.parse(it) }
                }
                return true
            }

            override fun next(): CodingAgentEvent {
                if (!hasNext()) throw NoSuchElementException()
                return next!!.also { next = null }
            }
        }

        override fun close() { if (closed.compareAndSet(false, true)) runCatching { response.close() } }
    }

    private fun errorDetail(failure: AnthropicServiceException): String = runCatching {
        failure.body().asObject().orElse(null)?.get("error")?.asObject()?.orElse(null)?.get("message")?.asString()?.orElse(null)
    }.getOrNull().orEmpty().lowercase()

    /** [existingResource]: the call names something stored earlier, so a 404 means "gone", not "beta unavailable". */
    private fun <T> call(subject: Subject, existingResource: Boolean, block: () -> T): T = try {
        block()
    } catch (failure: CodingAgentException) {
        throw failure
    } catch (failure: UnauthorizedException) {
        throw CodingAgentException(CodingAgentFailure.INVALID_API_KEY, failure)
    } catch (failure: PermissionDeniedException) {
        val detail = errorDetail(failure)
        throw CodingAgentException(when {
            failure.errorType().orElse(null) == ErrorType.BILLING_ERROR || "credit balance" in detail -> CodingAgentFailure.NO_CREDIT
            "beta" in detail || "managed agents" in detail -> CodingAgentFailure.BETA_UNAVAILABLE
            else -> CodingAgentFailure.PERMISSION_DENIED
        }, failure)
    } catch (failure: NotFoundException) {
        throw CodingAgentException(when {
            subject == Subject.SESSION || subject == Subject.STREAM -> CodingAgentFailure.SESSION_GONE
            existingResource -> CodingAgentFailure.RESOURCE_GONE
            // The collection itself is unknown to this account: the beta is not available for it.
            else -> CodingAgentFailure.BETA_UNAVAILABLE
        }, failure)
    } catch (failure: RateLimitException) {
        throw CodingAgentException(CodingAgentFailure.RATE_LIMITED, failure)
    } catch (failure: BadRequestException) {
        val detail = errorDetail(failure)
        throw CodingAgentException(when {
            failure.errorType().orElse(null) == ErrorType.BILLING_ERROR || "credit balance" in detail -> CodingAgentFailure.NO_CREDIT
            "beta" in detail -> CodingAgentFailure.BETA_UNAVAILABLE
            subject == Subject.SESSION_CREATE && "budget" in detail -> CodingAgentFailure.BUDGET_REJECTED
            subject == Subject.SESSION_CREATE && ("repositor" in detail || "authorization_token" in detail) ->
                CodingAgentFailure.SESSION_REPOSITORY_ACCESS
            subject == Subject.SESSION_CREATE && ("archived" in detail || "not found" in detail) -> CodingAgentFailure.RESOURCE_GONE
            "model" in detail -> CodingAgentFailure.MODEL_UNAVAILABLE
            subject == Subject.VAULT -> CodingAgentFailure.VAULT
            else -> CodingAgentFailure.BAD_REQUEST
        }, failure)
    } catch (failure: AnthropicServiceException) {
        throw CodingAgentException(when (failure.statusCode()) {
            402 -> CodingAgentFailure.NO_CREDIT
            409 -> if (subject == Subject.VAULT) CodingAgentFailure.VAULT else CodingAgentFailure.CONFLICT
            413, 422 -> CodingAgentFailure.BAD_REQUEST
            in 500..599 -> CodingAgentFailure.OVERLOADED
            else -> CodingAgentFailure.UNEXPECTED
        }, failure)
    } catch (failure: AnthropicIoException) {
        val causes = generateSequence<Throwable>(failure) { it.cause }.take(8).toList()
        val timedOut = causes.any { it is SocketTimeoutException ||
            (it is InterruptedIOException && it.message.orEmpty().contains("timeout", ignoreCase = true)) }
        throw CodingAgentException(when {
            subject == Subject.STREAM -> CodingAgentFailure.STREAM_LOST
            timedOut -> CodingAgentFailure.TIMEOUT
            else -> CodingAgentFailure.OFFLINE
        }, failure)
    } catch (failure: AnthropicException) {
        // Invalid data, a stream error event without a status, anything the SDK could not type.
        throw CodingAgentException(if (subject == Subject.STREAM) CodingAgentFailure.STREAM_LOST else CodingAgentFailure.UNEXPECTED, failure)
    }

    private companion object {
        @Suppress("UNCHECKED_CAST")
        fun plainObject(value: JsonValue): Map<String, Any?>? =
            runCatching { value.convert(Map::class.java) as? Map<String, Any?> }.getOrNull()
    }
}
