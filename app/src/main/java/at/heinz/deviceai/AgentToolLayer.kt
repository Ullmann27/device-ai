package at.heinz.deviceai

import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.security.MessageDigest

data class ToolProposal(val toolName: String, val arguments: Map<String, Any?>) {
    fun validate(): String? {
        if (toolName.equals("shell", true) || toolName.equals("run_shell", true)) {
            return "Raw shell proposals are not supported."
        }
        if (arguments.keys.any { it.equals("command", true) || it.equals("shell", true) || it.equals("script", true) }) {
            return "Command-like arguments are not supported."
        }
        if (arguments.size > 16 || arguments.any { (key, value) ->
                !key.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,63}")) ||
                    !isStructuredValue(value) || !validArgumentType(toolName, key, value)
            }) return "Tool arguments must match a bounded registered-tool schema."
        return ToolRegistry.validate(ToolCallRequest("proposal", toolName, arguments))
    }

    private fun isStructuredValue(value: Any?): Boolean = when (value) {
        null, is Boolean -> true
        is Number -> value.toDouble().isFinite() && value.toDouble() % 1.0 == 0.0
        is String -> value.length <= 4_096
        is List<*> -> value.size <= 100 && value.all { it is String && isStructuredValue(it) }
        else -> false
    }

    private fun validArgumentType(name: String, key: String, value: Any?): Boolean = when (key) {
        "lines", "limit", "seconds" -> value is Number || value is String && value.toIntOrNull() != null
        "userOnly" -> value is Boolean || value is String &&
            (value.equals("true", true) || value.equals("false", true))
        "roots" -> (name == "find_files" || name == "get_large_files") &&
            (value is String || value is List<*> && value.all { it is String })
        else -> value is String
    }

    fun invocation(callId: String): ToolInvocation {
        require(validate() == null) { validate() ?: "Invalid tool proposal." }
        return ToolInvocation(callId, ToolCallRequest(callId, toolName, arguments))
    }
}

/**
 * A model turn must contain exactly one tool proposal before any action can be
 * considered. Streaming duplicates are ambiguous, never additional authority.
 */
internal class ToolProposalTurnGuard {
    private var first: ToolProposal? = null
    private var seen = 0

    @Synchronized
    fun observe(proposal: ToolProposal) {
        seen++
        first = if (seen == 1) proposal else null
    }

    @Synchronized
    fun acceptedProposal(): ToolProposal? = if (seen == 1) first else null

    @Synchronized
    fun isAmbiguous(): Boolean = seen > 1
}

data class ToolInvocation(val id: String, val request: ToolCallRequest) {
    init {
        require(id.isNotBlank() && id.length <= 128 && request.callId == id)
    }
}

enum class ToolExecutionStatus { SUCCEEDED, REJECTED, FAILED, TIMED_OUT, CANCELLED }

data class ToolExecutionRecord(
    val callId: String,
    val toolName: String,
    val risk: Risk?,
    val sanitizedArguments: Map<String, Any?>,
    val decisionReason: String,
    val status: ToolExecutionStatus,
    val elapsedMillis: Long,
    val outputLength: Int = 0
)

data class ToolExecutionResult(val output: String?, val record: ToolExecutionRecord)

/** A trusted backend can narrow an outcome to failure, never assert success. */
class ToolBackendFailure(val status: ToolExecutionStatus, message: String) : RuntimeException(message) {
    init { require(status in setOf(ToolExecutionStatus.FAILED, ToolExecutionStatus.TIMED_OUT, ToolExecutionStatus.CANCELLED)) }
}

data class ToolPolicy(
    val readOnlyEnabled: Boolean = false,
    val destructiveEnabled: Boolean = false,
    val maxCalls: Int = 4,
    val timeoutMillis: Long = 10_000,
    val maxOutputCharacters: Int = 8_000
) {
    init {
        require(maxCalls in 0..64 && timeoutMillis in 1..120_000 && maxOutputCharacters in 1..60_000)
    }
}

fun interface ToolRunner {
    fun execute(request: ToolCallRequest): String
}

class ToolGate(
    private val policy: ToolPolicy,
    private val runner: ToolRunner,
    private val clock: () -> Long = System::nanoTime,
    private val receiptStore: ToolExecutionReceiptStore? = null,
    private val receiptClock: () -> Long = System::currentTimeMillis
) {
    private val calls = AtomicInteger()
    private val cancelled = AtomicBoolean()
    private val activeFutures = ConcurrentHashMap.newKeySet<Future<String>>()
    private val pendingStarts = ConcurrentHashMap.newKeySet<ToolExecutionStartLatch>()
    private val consumedCallIds = ConcurrentHashMap.newKeySet<String>()

    fun cancel() {
        cancelled.set(true)
        pendingStarts.forEach { it.revokeIfPending() }
        activeFutures.forEach { it.cancel(true) }
    }

    fun recordProposal(invocation: ToolInvocation): ToolExecutionReceipt? {
        val request = invocation.request
        val validation = ToolProposal(request.name, request.arguments).validate()
        require(validation == null) { validation ?: "Invalid tool proposal." }
        val spec = checkNotNull(ToolRegistry.get(request.name))
        val store = receiptStore ?: return null
        val proposedAt = receiptClock().coerceAtLeast(0)
        return store.propose(
            ToolExecutionReceipt(
                callId = request.callId,
                toolName = request.name,
                argumentFingerprint = toolArgumentFingerprint(request.arguments),
                sanitizedArguments = sanitizeArguments(request.arguments),
                risk = spec.risk,
                createdAtMillis = proposedAt,
                transitions = listOf(ToolReceiptTransition(ToolReceiptState.PROPOSED, proposedAt))
            )
        )
    }

    fun execute(
        invocation: ToolInvocation,
        userConfirmed: Boolean = false,
        destructiveConfirmed: Boolean = false,
        onExecutionStarted: (() -> Unit)? = null
    ): ToolExecutionResult {
        val request = invocation.request
        val spec = ToolRegistry.get(request.name)
        val start = clock()
        fun result(status: ToolExecutionStatus, reason: String, output: String? = null): ToolExecutionResult {
            val elapsed = ((clock() - start).coerceAtLeast(0) / 1_000_000)
            return ToolExecutionResult(
                output,
                ToolExecutionRecord(
                    request.callId, request.name, spec?.risk, sanitizeArguments(request.arguments),
                    reason, status, elapsed, output?.length ?: 0
                )
            )
        }

        if (cancelled.get()) {
            runCatching {
                val prior = receiptStore?.get(request.callId)
                if (prior != null && prior.state in setOf(ToolReceiptState.PROPOSED, ToolReceiptState.APPROVED)) {
                    receiptStore.transition(
                        request.callId, prior.state, ToolReceiptState.CANCELLED, receiptClock().coerceAtLeast(0)
                    )
                }
            }
            return result(ToolExecutionStatus.CANCELLED, "Task cancelled.")
        }
        val validation = ToolProposal(request.name, request.arguments).validate()
        if (validation != null) return result(ToolExecutionStatus.REJECTED, validation)
        var receipt = try {
            recordProposal(invocation)
        } catch (_: Exception) {
            return result(ToolExecutionStatus.REJECTED, "Durable tool receipt could not be verified or saved.")
        }
        when (receipt?.state) {
            ToolReceiptState.SUCCEEDED -> return result(
                ToolExecutionStatus.SUCCEEDED,
                "This call id already completed; its tool was not run again."
            )
            ToolReceiptState.STARTED -> return result(
                ToolExecutionStatus.REJECTED,
                "This call id has an ambiguous prior outcome and requires operator review; it was not retried."
            )
            ToolReceiptState.FAILED, ToolReceiptState.TIMED_OUT, ToolReceiptState.CANCELLED,
            ToolReceiptState.REJECTED -> return result(
                receipt.state.toExecutionStatus(),
                "This call id already has a terminal receipt; it was not retried."
            )
            else -> Unit
        }
        fun rejectWithReceipt(status: ToolExecutionStatus, reason: String): ToolExecutionResult {
            val current = receipt
            if (current != null) {
                val terminal = if (status == ToolExecutionStatus.CANCELLED) ToolReceiptState.CANCELLED
                else ToolReceiptState.REJECTED
                receiptStore?.transition(request.callId, current.state, terminal, receiptClock().coerceAtLeast(0))
            }
            return result(status, reason)
        }
        when (spec!!.risk) {
            Risk.READ_ONLY -> if (!policy.readOnlyEnabled) {
                return rejectWithReceipt(ToolExecutionStatus.REJECTED, "Read-only tools are disabled by operator policy.")
            }
            Risk.CONFIRM -> if (!userConfirmed) {
                return rejectWithReceipt(ToolExecutionStatus.REJECTED, "Explicit user confirmation is required.")
            }
            Risk.DESTRUCTIVE -> {
                if (!policy.destructiveEnabled || !userConfirmed || !destructiveConfirmed) {
                    return rejectWithReceipt(
                        ToolExecutionStatus.REJECTED,
                        "Destructive policy and explicit strong confirmation are required."
                    )
                }
            }
        }

        val reservedCall = calls.getAndIncrement()
        if (reservedCall >= policy.maxCalls) {
            calls.decrementAndGet()
            return rejectWithReceipt(ToolExecutionStatus.REJECTED, "Tool-call budget exhausted.")
        }
        if (!consumedCallIds.add(request.callId)) {
            calls.decrementAndGet()
            return rejectWithReceipt(ToolExecutionStatus.REJECTED, "Duplicate tool call id rejected by replay guard.")
        }
        if (receipt != null) {
            if (receipt.state == ToolReceiptState.PROPOSED) {
                receipt = receiptStore?.transition(
                    request.callId, ToolReceiptState.PROPOSED, ToolReceiptState.APPROVED,
                    receiptClock().coerceAtLeast(0)
                )
            }
            receipt = receiptStore?.transition(
                request.callId, ToolReceiptState.APPROVED, ToolReceiptState.STARTED,
                receiptClock().coerceAtLeast(0)
            )
            if (receipt == null) {
                calls.decrementAndGet()
                return result(
                    ToolExecutionStatus.REJECTED,
                    "Durable receipt state changed; execution was blocked for operator review."
                )
            }
        }
        fun persistTerminal(state: ToolReceiptState, output: String?): Boolean {
            val store = receiptStore ?: return true
            val digest = output?.takeIf { it.isNotEmpty() }?.let {
                MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { byte ->
                    "%02x".format(byte)
                }
            }
            return runCatching {
                store.transition(
                    request.callId, ToolReceiptState.STARTED, state, receiptClock().coerceAtLeast(0),
                    output?.length ?: 0, digest
                ) != null
            }.getOrDefault(false)
        }
        val dispatch = ToolExecutionStartLatch()
        pendingStarts += dispatch
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "device-ai-tool").apply { isDaemon = true }
        }
        return try {
            val future = executor.submit(Callable {
                fun checkStartEligibility() {
                    if (dispatch.wasRevokedBeforeCommit || cancelled.get() || Thread.currentThread().isInterrupted)
                        throw CancellationException("Backend start cancelled.")
                    if (clock() - start >= policy.timeoutMillis * 1_000_000L)
                        throw ToolBackendFailure(ToolExecutionStatus.TIMED_OUT, "Backend start deadline expired.")
                }
                checkStartEligibility()
                // The observer may consume interruption. Recheck the independent
                // per-execution lease before any backend operation can begin.
                runCatching { onExecutionStarted?.invoke() }
                checkStartEligibility()
                // This CAS is the only start-commit point. A timeout/cancel that
                // wins the race revokes the pending dispatch before backend work.
                // A committed dispatch cannot later be reported as definitely unrun.
                if (!dispatch.commitIfPending()) throw CancellationException("Dispatch was revoked before start.")
                runner.execute(request)
            })
            activeFutures += future
            if (cancelled.get()) future.cancel(true)
            val output = try {
                future.get(policy.timeoutMillis, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                dispatch.revokeIfPending()
                future.cancel(true)
                val saved = persistTerminal(ToolReceiptState.TIMED_OUT, null)
                return result(
                    ToolExecutionStatus.TIMED_OUT,
                    if (saved && !dispatch.wasCommitted) "Tool deadline expired before backend dispatch."
                    else "Tool timed out after possible dispatch; outcome UNKNOWN and operator review is required."
                )
            }
            if (cancelled.get()) {
                val saved = persistTerminal(ToolReceiptState.CANCELLED, null)
                result(
                    ToolExecutionStatus.CANCELLED,
                    if (saved && !dispatch.wasCommitted) "Task cancelled before backend dispatch."
                    else "Task cancelled after possible dispatch; outcome UNKNOWN and operator review is required.",
                    output.take(policy.maxOutputCharacters)
                )
            } else if (output.length > policy.maxOutputCharacters) {
                val saved = persistTerminal(ToolReceiptState.FAILED, null)
                result(ToolExecutionStatus.FAILED,
                    if (saved) "Tool output exceeded the limit; observation is incomplete."
                    else "Output exceeded the limit and receipt is ambiguous; operator review is required.",
                    output.take(policy.maxOutputCharacters))
            } else {
                val limited = output.take(policy.maxOutputCharacters)
                val saved = persistTerminal(ToolReceiptState.SUCCEEDED, limited)
                result(
                    if (saved) ToolExecutionStatus.SUCCEEDED else ToolExecutionStatus.FAILED,
                    if (saved) "Allowed by tool policy."
                    else "Tool returned, but its completion receipt could not be committed; operator review is required.",
                    limited
                )
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            dispatch.revokeIfPending()
            persistTerminal(ToolReceiptState.CANCELLED, null)
            result(ToolExecutionStatus.CANCELLED, if (dispatch.wasCommitted)
                "Tool interrupted after possible dispatch; outcome UNKNOWN." else "Tool interrupted before dispatch.")
        } catch (_: CancellationException) {
            dispatch.revokeIfPending()
            persistTerminal(ToolReceiptState.CANCELLED, null)
            result(ToolExecutionStatus.CANCELLED, if (dispatch.wasCommitted)
                "Tool cancelled after possible dispatch; outcome UNKNOWN." else "Tool cancelled before dispatch.")
        } catch (_: TimeoutException) {
            dispatch.revokeIfPending()
            persistTerminal(ToolReceiptState.TIMED_OUT, null)
            result(ToolExecutionStatus.TIMED_OUT, if (dispatch.wasCommitted)
                "Tool timed out after possible dispatch; outcome UNKNOWN." else "Tool timed out before dispatch.")
        } catch (e: java.util.concurrent.ExecutionException) {
            val status = (e.cause as? ToolBackendFailure)?.status ?: ToolExecutionStatus.FAILED
            val state = when (status) {
                ToolExecutionStatus.TIMED_OUT -> ToolReceiptState.TIMED_OUT
                ToolExecutionStatus.CANCELLED -> ToolReceiptState.CANCELLED
                else -> ToolReceiptState.FAILED
            }
            val saved = persistTerminal(state, null)
            result(status, if (saved) "Backend reported $status."
                else "Backend outcome is ambiguous; operator review is required.")
        } catch (_: Exception) {
            persistTerminal(ToolReceiptState.FAILED, null)
            result(ToolExecutionStatus.FAILED, "Tool execution failed.")
        } finally {
            dispatch.revokeIfPending()
            pendingStarts.remove(dispatch)
            activeFutures.removeIf { it.isDone }
            executor.shutdownNow()
        }

    }

    private fun sanitizeArguments(arguments: Map<String, Any?>): Map<String, Any?> =
        arguments.mapValues { (key, value) ->
            if (key.contains(Regex("password|token|secret|credential|authorization", RegexOption.IGNORE_CASE))) {
                "[REDACTED]"
            } else {
                when (value) {
                    is String -> "[STRING ${value.length.coerceAtMost(4_096)} chars]"
                    is Number -> "[NUMBER]"
                    is Boolean, null -> value
                    is List<*> -> "[LIST ${value.size.coerceAtMost(100)} items]"
                    else -> "[UNSUPPORTED]"
                }
            }
        }
}

@JvmInline
value class SkillId(val value: String) {
    init { require(value.matches(Regex("[a-z][a-z0-9_.-]{1,63}"))) }
}

@JvmInline
value class SkillVersion(val value: String) {
    init { require(value.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) }
}

enum class SkillCapability { DEVICE_DIAGNOSTICS, APP_MANAGEMENT, FILE_READ, RESEARCH }
enum class SkillSource { BUILT_IN, IMPORTED, USER }
enum class SkillStatus { DISABLED, ENABLED }

data class SkillDependency(val id: SkillId, val version: SkillVersion)

data class Skill(
    val id: SkillId,
    val version: SkillVersion,
    val name: String,
    val description: String,
    val capabilities: Set<SkillCapability>,
    val dependencies: Set<SkillDependency> = emptySet(),
    val source: SkillSource = SkillSource.BUILT_IN,
    val status: SkillStatus = SkillStatus.DISABLED,
    val toolNames: Set<String> = emptySet()
) {
    init {
        require(name.isNotBlank() && description.isNotBlank())
        require(toolNames.all { ToolRegistry.get(it) != null })
    }
}

data class SkillKey(val id: SkillId, val version: SkillVersion)
data class SkillAuditEvent(val action: String, val key: SkillKey)

class SkillGraph {
    private val skills = linkedMapOf<SkillKey, Skill>()
    private val audit = mutableListOf<SkillAuditEvent>()

    @Synchronized
    fun install(skill: Skill) = installAll(listOf(skill))

    @Synchronized
    fun installAll(additions: Collection<Skill>) {
        val combined = skills.toMutableMap()
        additions.forEach { skill ->
            val key = SkillKey(skill.id, skill.version)
            require(key !in combined) { "Duplicate skill version: $key" }
            combined[key] = skill.copy(status = SkillStatus.DISABLED)
        }
        combined.forEach { (key, skill) ->
            skill.dependencies.forEach { dependency ->
                require(SkillKey(dependency.id, dependency.version) in combined) {
                    "Missing skill dependency: ${dependency.id}@${dependency.version}"
                }
            }
            require(skill.dependencies.none { SkillKey(it.id, it.version) == key }) {
                "A skill cannot depend on itself."
            }
        }
        val visiting = mutableSetOf<SkillKey>()
        val visited = mutableSetOf<SkillKey>()
        fun visit(key: SkillKey) {
            if (key in visiting) error("Skill dependency cycle detected.")
            if (!visited.add(key)) return
            visiting += key
            combined.getValue(key).dependencies.forEach { visit(SkillKey(it.id, it.version)) }
            visiting -= key
        }
        combined.keys.forEach(::visit)
        skills.clear()
        skills.putAll(combined)
        additions.forEach { audit += SkillAuditEvent("installed", SkillKey(it.id, it.version)) }
    }

    @Synchronized
    fun activate(key: SkillKey): Skill {
        val skill = skills[key] ?: error("Unknown skill version.")
        require(skill.dependencies.all { skills[SkillKey(it.id, it.version)]?.status == SkillStatus.ENABLED }) {
            "All skill dependencies must be enabled first."
        }
        return skill.copy(status = SkillStatus.ENABLED).also {
            skills[key] = it
            audit += SkillAuditEvent("enabled", key)
        }
    }

    @Synchronized
    fun disable(key: SkillKey): Skill {
        val skill = skills[key] ?: error("Unknown skill version.")
        require(skills.values.none { it.status == SkillStatus.ENABLED && SkillDependency(key.id, key.version) in it.dependencies }) {
            "Enabled dependents must be disabled first."
        }
        return skill.copy(status = SkillStatus.DISABLED).also {
            skills[key] = it
            audit += SkillAuditEvent("disabled", key)
        }
    }

    @Synchronized fun get(key: SkillKey): Skill? = skills[key]
    @Synchronized fun all(): List<Skill> = skills.values.sortedWith(compareBy({ it.id.value }, { it.version.value }))
    @Synchronized fun auditTrail(): List<SkillAuditEvent> = audit.toList()
}

interface SkillImplementation {
    val key: SkillKey
    fun execute(arguments: Map<String, Map<String, Any?>>, gate: ToolGate): List<ToolExecutionResult>
}

class DeviceStatusSkillImplementation(
    override val key: SkillKey = SkillKey(SkillId("device_status"), SkillVersion("1.0.0"))
) : SkillImplementation {
    override fun execute(
        arguments: Map<String, Map<String, Any?>>,
        gate: ToolGate
    ): List<ToolExecutionResult> = listOf("get_device_info", "get_battery_info").mapIndexed { index, name ->
        val proposal = ToolProposal(name, arguments[name].orEmpty())
        require(proposal.validate() == null)
        gate.execute(proposal.invocation("device-status-${index + 1}"))
    }
}

object BuiltInSkills {
    val deviceStatus = Skill(
        id = SkillId("device_status"),
        version = SkillVersion("1.0.0"),
        name = "Device status",
        description = "Reads basic device and battery information through registered read-only tools.",
        capabilities = setOf(SkillCapability.DEVICE_DIAGNOSTICS),
        toolNames = setOf("get_device_info", "get_battery_info")
    )
}

