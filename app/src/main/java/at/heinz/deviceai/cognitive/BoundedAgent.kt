package at.heinz.deviceai.cognitive

import at.heinz.deviceai.Risk
import at.heinz.deviceai.ToolRegistry
import at.heinz.deviceai.ToolExecutionRecord
import at.heinz.deviceai.ToolExecutionStatus
import at.heinz.deviceai.ToolGate
import at.heinz.deviceai.ToolProposal
import at.heinz.deviceai.ToolReceiptState
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.UUID

enum class AgentState {
    OBSERVE, RETRIEVE, PLAN, MODEL, VALIDATE, TOOL_PROPOSAL, GATED_EXECUTION,
    OBSERVE_RESULT, EVALUATE, STORE_EXPERIENCE, RESPOND
}

data class AgentAuditEvent(val sequence: Int, val state: AgentState, val detail: String) {
    init { require(sequence >= 0 && detail.length <= 256) }
}

data class AgentReflection(val failure: String, val evidence: String, val correction: String) {
    init {
        require(failure.isNotBlank() && evidence.isNotBlank() && correction.isNotBlank())
        require(listOf(failure, evidence, correction).all { it.length <= 512 })
    }
}

data class AgentModelOutput(
    val modelId: String,
    val response: String,
    val proposal: ToolProposal? = null
) {
    init { require(modelId.isNotBlank() && response.length <= 8_000) }
}

data class AgentToolObservation(
    val toolName: String,
    val status: ToolExecutionStatus,
    val output: String?
)

data class AgentToolApproval(val userConfirmed: Boolean, val destructiveConfirmed: Boolean = false)

fun interface AgentToolApprovalProvider {
    fun requestApproval(proposal: ToolProposal, risk: Risk): AgentToolApproval
}

fun interface AgentModel {
    fun generate(
        task: String,
        memories: List<MemoryRecord>,
        reflection: AgentReflection?,
        toolObservations: List<AgentToolObservation>
    ): AgentModelOutput
}

/** Optional explicit cancellation bridge. Existing AgentModel implementers are unaffected. */
interface CancellableAgentModel : AgentModel {
    fun cancelGeneration()
}

fun interface AgentEvaluator {
    fun evaluate(response: String, toolRecord: ToolExecutionRecord?): Evaluation
}

data class AgentLimits(
    val maxSteps: Int = 32,
    val maxToolCalls: Int = 4,
    val maxRetries: Int = 1
) {
    init {
        require(maxSteps in 1..64 && maxToolCalls in 0..16 && maxRetries in 0..2)
    }
}

data class AgentCheckpoint(
    val schemaVersion: Int,
    val task: String,
    val state: AgentState,
    val steps: Int,
    val toolCalls: Int,
    val retries: Int,
    val auditTrail: List<AgentAuditEvent>
) {
    init {
        require(schemaVersion == 1)
        require(task.isNotBlank() && task.length <= 8_000)
        require(steps in 0..64 && toolCalls in 0..16 && retries in 0..2)
        require(auditTrail.size <= 64)
        require(auditTrail.map { it.sequence } == auditTrail.indices.toList())
    }
}

object AgentCheckpointCodec {
    private const val MAGIC = "DEVICE_AI_AGENT_CHECKPOINT"
    private const val MAX_BYTES = 64 * 1024

    internal fun maxEncodedBytes(): Int = MAX_BYTES

    fun encode(checkpoint: AgentCheckpoint): ByteArray {
        val bytes = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeUTF(MAGIC)
                out.writeInt(checkpoint.schemaVersion)
                out.writeUTF(checkpoint.task)
                out.writeUTF(checkpoint.state.name)
                out.writeInt(checkpoint.steps)
                out.writeInt(checkpoint.toolCalls)
                out.writeInt(checkpoint.retries)
                out.writeInt(checkpoint.auditTrail.size)
                checkpoint.auditTrail.forEach {
                    out.writeInt(it.sequence)
                    out.writeUTF(it.state.name)
                    out.writeUTF(it.detail)
                }
            }
        }.toByteArray()
        require(bytes.size <= MAX_BYTES)
        return bytes
    }

    fun decode(bytes: ByteArray): AgentCheckpoint {
        return try {
            decodeAllowlisted(bytes)
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: java.io.IOException) {
            throw IllegalArgumentException("Invalid agent checkpoint.", e)
        }
    }

    private fun decodeAllowlisted(bytes: ByteArray): AgentCheckpoint {
        require(bytes.size in 1..MAX_BYTES)
        val input = DataInputStream(ByteArrayInputStream(bytes))
        require(input.readUTF() == MAGIC)
        require(input.readInt() == 1) { "Unsupported agent checkpoint schema." }
        val task = input.readUTF()
        val state = AgentState.valueOf(input.readUTF())
        val steps = input.readInt()
        val toolCalls = input.readInt()
        val retries = input.readInt()
        val count = input.readInt()
        require(count in 0..64)
        val trail = List(count) {
            AgentAuditEvent(input.readInt(), AgentState.valueOf(input.readUTF()), input.readUTF())
        }
        require(input.available() == 0) { "Unexpected checkpoint data." }
        return AgentCheckpoint(1, task, state, steps, toolCalls, retries, trail)
    }
}

data class AgentRunResult(
    val response: String,
    val state: AgentState,
    val auditTrail: List<AgentAuditEvent>,
    val experience: Experience?,
    val blocker: String? = null,
    val taskEvidence: AgentTaskEvidence? = null
)

class BoundedAgentOrchestrator(
    private val model: AgentModel,
    private val memories: () -> List<MemoryRecord>,
    private val toolGate: ToolGate,
    private val evaluator: AgentEvaluator,
    private val experienceStore: ExperienceStore,
    private val approvalProvider: AgentToolApprovalProvider = AgentToolApprovalProvider { _, _ ->
        AgentToolApproval(userConfirmed = false)
    },
    private val modelReputation: ModelReputationStore? = null,
    private val limits: AgentLimits = AgentLimits(),
    private val checkpointStore: AgentCheckpointStore? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val callIdPrefix: String = UUID.randomUUID().toString(),
    private val cancellation: DeliberationCancellation = DeliberationCancellation(),
    // Trusted caller declares the evaluator's scope; model output cannot set this.
    // Form-only checks may allow a reply but never establish a successful skill example.
    private val evaluationConfirmsOutcome: Boolean = true,
    private val turnTrace: CognitiveTurnTrace? = null,
    // OFF by default: original runtime and tool-policy behavior are unchanged.
    private val recallPriorExperiences: Boolean = false,
    private val eventObserver: (AgentCognitiveEvent) -> Unit = {}
) {
    init {
        require(callIdPrefix.matches(Regex("[A-Za-z0-9._:-]{1,64}")))
    }

    private val audit = mutableListOf<AgentAuditEvent>()
    private var steps = 0
    private var calls = 0
    private var runIndex = 0

    fun checkpoint(task: String, state: AgentState, retries: Int = 0): AgentCheckpoint =
        AgentCheckpoint(1, task, state, steps, calls, retries, audit.toList())

    fun cancel() {
        cancellation.cancel()
        (model as? CancellableAgentModel)?.cancelGeneration()
        toolGate.cancel()
    }

    fun run(task: String): AgentRunResult {
        cancellation.throwIfCancelled()
        checkpointStore?.clear()
        return runFrom(task, null)
    }

    fun resumeSaved(): AgentRunResult? {
        val saved = checkpointStore?.load() ?: return null
        if (saved.state !in RESUMABLE_STATES) {
            return AgentRunResult(
                response = "Saved agent run requires operator review before retry.",
                state = saved.state,
                auditTrail = saved.auditTrail,
                experience = null,
                blocker = "The last checkpoint is at or after a tool proposal boundary; automatic resume is disabled to prevent duplicated side effects."
            )
        }
        return resume(saved)
    }

    fun discardSavedCheckpoint() {
        checkpointStore?.clear()
    }

    fun resume(checkpoint: AgentCheckpoint): AgentRunResult {
        require(checkpoint.state in RESUMABLE_STATES) {
            "Only checkpoints before tool proposal/execution can be resumed safely."
        }
        return runFrom(checkpoint.task, checkpoint)
    }

    private fun runFrom(task: String, checkpoint: AgentCheckpoint?): AgentRunResult {
        require(task.isNotBlank() && task.length <= 8_000)
        require(checkpoint == null || checkpoint.task == task)
        audit.clear()
        steps = checkpoint?.steps ?: 0
        calls = checkpoint?.toolCalls ?: 0
        checkpoint?.auditTrail?.let(audit::addAll)
        val startedAt = clock()
        val retrieved = HybridMemoryRetriever.retrieve(
            query = task,
            records = memories(),
            limit = 8,
            nowMillis = startedAt
        ).map { it.memory }
        // Untrusted, ephemeral hints only; never modify the user task or owner retrieval.
        val historicalHints = if (recallPriorExperiences) {
            AgentExperienceRecall.hints(experienceStore as? ExperienceLookup, task, startedAt)
        } else emptyList()
        val currentRun = runIndex++
        retrieved.forEach { turnTrace?.reference(TraceStage.MEMORY_REF, it.id) }
        var currentReflection: AgentReflection? = null
        var lastOutput = ""
        var lastRecord: ToolExecutionRecord? = null
        var lastEvaluation = Evaluation(false, 0.0, "No evaluation completed.")
        var chosen: AgentModelOutput? = null
        var retry = checkpoint?.retries ?: 0
        val usedTools = mutableListOf<String>()
        val failures = mutableListOf<String>()
        val toolObservations = mutableListOf<AgentToolObservation>()
        val toolEvidence = mutableListOf<String>()
        val modelIds = mutableListOf<String>()
        var ephemeralScreenContext=false
        val callProofs = mutableListOf<AgentToolEvidence>()
        val cognitiveEvents = mutableListOf<AgentCognitiveEvent>()
        val plannedToolNames = (model as? ReadOnlyToolPlan)?.plannedToolNames.orEmpty()
        fun emit(state: CognitiveWorkState, callId: String? = null) {
            val stage = when(state) {
                CognitiveWorkState.UNDERSTANDING -> TraceStage.TASK_RECEIVED
                CognitiveWorkState.PLANNING -> TraceStage.PLAN_CREATED
                CognitiveWorkState.WAITING_FOR_APPROVAL -> TraceStage.APPROVAL_REQUESTED
                CognitiveWorkState.EXECUTING -> TraceStage.TOOL_EXECUTION_STARTED
                CognitiveWorkState.VERIFYING -> TraceStage.OUTCOME_CHECK_STARTED
                CognitiveWorkState.COMPLETED -> TraceStage.OUTCOME_CONFIRMED
                CognitiveWorkState.BLOCKED -> TraceStage.OUTCOME_UNCONFIRMED
                else -> null
            }
            val terminal = setOf(CognitiveWorkState.COMPLETED, CognitiveWorkState.BLOCKED)
            val event = synchronized(cognitiveEvents) {
                // A delayed worker notification cannot supersede a terminal phase.
                if (cognitiveEvents.lastOrNull()?.state in terminal && state !in terminal) return
                AgentCognitiveEvent(cognitiveEvents.size, callIdPrefix, currentRun, state,
                    (clock() - startedAt).coerceAtLeast(0), callId).also {
                    cognitiveEvents += it
                    stage?.let { value -> turnTrace?.event(value) }
                }
            }
            // Callbacks may cross threads. Consumers use task/attempt/sequence,
            // never callback arrival time, to discard stale presentation events.
            runCatching { eventObserver(event) }
        }
        fun proof(verified: Boolean = false, candidateId: String? = null) = AgentTaskEvidence(
            callIdPrefix, currentRun, sha256Bytes(task.toByteArray(Charsets.UTF_8)).toHex(),
            plannedToolNames, callProofs.toList(), synchronized(cognitiveEvents) { cognitiveEvents.toList() },
            lastEvaluation.success, evaluationConfirmsOutcome, verified, candidateId)

        fun hasFreshToolOutcomes(): Boolean = !ephemeralScreenContext && toolEvidence.isNotEmpty() &&
            toolObservations.isNotEmpty() && toolObservations.all {
                it.status == ToolExecutionStatus.SUCCEEDED && !it.output.isNullOrBlank()
            }

        fun transition(state: AgentState, detail: String) {
            cancellation.throwIfCancelled()
            if (steps >= limits.maxSteps) throw StepLimitReached()
            steps++
            audit += AgentAuditEvent(audit.size, state, detail.take(256))
            if(!ephemeralScreenContext)checkpointStore?.save(checkpoint(task, state, retry))
        }

        try {
            turnTrace?.reference(TraceStage.TASK_REF, callIdPrefix)
            emit(CognitiveWorkState.UNDERSTANDING)
            if (checkpoint == null || checkpoint.state.ordinal < AgentState.OBSERVE.ordinal) transition(AgentState.OBSERVE, "Task received.")
            if (checkpoint == null || checkpoint.state.ordinal < AgentState.RETRIEVE.ordinal) transition(AgentState.RETRIEVE, "Memory retrieval completed.")
            if (checkpoint == null || checkpoint.state.ordinal < AgentState.PLAN.ordinal) transition(AgentState.PLAN, "Plan bounded to one registered-tool proposal.")
            emit(CognitiveWorkState.PLANNING)
            var done = false
            while (!done) {
                transition(AgentState.MODEL, "Model response requested.")
                val modelStarted = clock()
                chosen = model.generate(task,
                    if (historicalHints.isEmpty()) retrieved else retrieved + historicalHints,
                    currentReflection, toolObservations.toList())
                modelIds += chosen.modelId
                transition(AgentState.VALIDATE, "Response and optional proposal validated.")
                val proposal = chosen.proposal
                if (proposal != null) {
                    if(proposal.toolName=="read_app_screen"){
                        ephemeralScreenContext=true
                        checkpointStore?.clear()
                    }
                    turnTrace?.event(TraceStage.TOOL_PROPOSED)
                    transition(AgentState.TOOL_PROPOSAL, "Registered tool proposal received.")
                    if (calls >= limits.maxToolCalls) {
                        lastRecord = ToolExecutionRecord(
                            "agent-${calls + 1}", proposal.toolName, null, emptyMap(), "Agent tool-call budget exhausted.",
                            ToolExecutionStatus.REJECTED, 0
                        )
                        toolObservations += AgentToolObservation(
                            proposal.toolName, lastRecord.status, lastRecord.decisionReason
                        )
                        continue
                    } else {
                        calls++
                        val invalid = proposal.validate()
                        if (invalid != null) {
                            lastRecord = ToolExecutionRecord(
                                "agent-$calls", proposal.toolName, null, emptyMap(), invalid,
                                ToolExecutionStatus.REJECTED, 0
                            )
                            toolObservations += AgentToolObservation(proposal.toolName, lastRecord.status, invalid)
                        } else {
                            transition(AgentState.GATED_EXECUTION, "Tool policy gate evaluated.")
                            usedTools += proposal.toolName
                            val risk = ToolRegistry.get(proposal.toolName)!!.risk
                            val invocation = proposal.invocation("$callIdPrefix-$calls")
                            val priorReceipt = toolGate.recordProposal(invocation)
                            val requiresReview = priorReceipt != null &&
                                priorReceipt.state !in setOf(ToolReceiptState.PROPOSED, ToolReceiptState.APPROVED)
                            val approvalRequested = risk != Risk.READ_ONLY && !requiresReview
                            val approval = if (!approvalRequested) {
                                AgentToolApproval(false)
                            } else {
                                emit(CognitiveWorkState.WAITING_FOR_APPROVAL, invocation.id)
                                approvalProvider.requestApproval(proposal, risk)
                            }
                            val execution = toolGate.execute(
                                invocation,
                                approval.userConfirmed,
                                approval.destructiveConfirmed,
                                onExecutionStarted = { emit(CognitiveWorkState.EXECUTING, invocation.id) }
                            )
                            lastRecord = execution.record
                            turnTrace?.reference(TraceStage.CALL_REF, lastRecord.callId)
                            turnTrace?.event(if (lastRecord.status == ToolExecutionStatus.SUCCEEDED && execution.output != null)
                                TraceStage.TOOL_EXECUTED else TraceStage.GATED_FALLBACK, lastRecord.status)
                            // A replayed receipt deliberately returns no output. It is not
                            // a new observation and cannot establish new learning evidence.
                            if (lastRecord.status == ToolExecutionStatus.SUCCEEDED && !execution.output.isNullOrBlank()) {
                                val digest = MessageDigest.getInstance("SHA-256")
                                    .digest(execution.output.toByteArray(Charsets.UTF_8))
                                    .joinToString("") { "%02x".format(it) }
                                toolEvidence += "tool-call:${lastRecord.callId}:sha256:$digest"
                            }
                            val fresh = lastRecord.status == ToolExecutionStatus.SUCCEEDED && !execution.output.isNullOrBlank()
                            callProofs += AgentToolEvidence(invocation.id, proposal.toolName,
                                at.heinz.deviceai.toolArgumentFingerprint(invocation.request.arguments),
                                approvalRequested, approval.userConfirmed.takeIf { approvalRequested },
                                lastRecord.status, execution.output?.takeIf { fresh }?.let {
                                    sha256Bytes(it.toByteArray(Charsets.UTF_8)).toHex()
                                }, fresh)
                            transition(AgentState.OBSERVE_RESULT, "Tool result status: ${lastRecord.status}.")
                            toolObservations += AgentToolObservation(
                                proposal.toolName, execution.record.status, execution.output
                            )
                            continue
                        }
                    }
                    lastOutput = chosen.response
                } else {
                    lastOutput = chosen.response
                }
                transition(AgentState.EVALUATE, "Result evaluated.")
                emit(CognitiveWorkState.VERIFYING)
                lastEvaluation = evaluator.evaluate(lastOutput, lastRecord)
                cancellation.throwIfCancelled()
                modelReputation?.takeIf { evaluationConfirmsOutcome && hasFreshToolOutcomes() }?.updateAfterEvaluation(
                    chosen.modelId,
                    ModelCapability.TOOL_USE,
                    "agent",
                    ReputationEvidence(
                        evaluated = true,
                        success = lastEvaluation.success,
                        evaluatorScore = lastEvaluation.score,
                        failure = !lastEvaluation.success,
                        correction = currentReflection != null,
                        latencyMillis = (clock() - modelStarted).coerceAtLeast(0)
                    )
                )
                if (lastEvaluation.success || retry >= limits.maxRetries) {
                    done = true
                } else {
                    val evidence = lastRecord?.decisionReason ?: lastEvaluation.notes
                    failures += lastEvaluation.notes.ifBlank { "Evaluation failed." }
                    currentReflection = AgentReflection(
                        failure = lastEvaluation.notes.ifBlank { "The attempt did not meet the evaluator criteria." },
                        evidence = evidence.ifBlank { "No successful tool result was observed." },
                        correction = "Revise the response or proposal using only registered tools and the observed failure."
                    )
                    retry++
                    lastRecord = null
                }
            }
            transition(AgentState.STORE_EXPERIENCE, if(ephemeralScreenContext)"Ephemeral screen run; learning skipped." else "Evaluated attempt stored.")
            val finishedAt = clock().coerceAtLeast(startedAt)
            val resultExperience = Experience(
                id = experienceId(task, finishedAt, currentRun),
                taskType = "agent_task",
                requestSummary = task.take(512),
                memoryIds = retrieved.map { it.id },
                sourceIds = toolEvidence.toList(),
                modelIds = modelIds.distinct(),
                toolIds = usedTools.distinct(),
                planSummary = AgentExperienceRecall.observedRunSummary(usedTools, retry, historicalHints.size),
                resultSummary = lastOutput.take(512),
                failures = (failures + if (lastEvaluation.success) emptyList() else listOf(lastEvaluation.notes))
                    .map { it.take(256) },
                retries = retry,
                confidenceBefore = 0.5,
                confidenceAfter = lastEvaluation.score,
                outcome = when {
                    !lastEvaluation.success -> ExperienceOutcome.FAILURE
                    evaluationConfirmsOutcome && hasFreshToolOutcomes() -> ExperienceOutcome.SUCCESS
                    else -> ExperienceOutcome.PARTIAL
                },
                startedAt = startedAt,
                completedAt = finishedAt,
                evaluation = lastEvaluation
            )
            if(!ephemeralScreenContext)experienceStore.store(resultExperience)
            val blocker = if (lastEvaluation.success) null else
                "Retry budget exhausted: ${lastEvaluation.notes.ifBlank { "evaluation failed" }}"
            transition(AgentState.RESPOND, if (blocker == null) "Response ready." else "Structured blocker returned.")
            val verifiedOutcome = lastEvaluation.success && evaluationConfirmsOutcome && hasFreshToolOutcomes()
            emit(if (verifiedOutcome) CognitiveWorkState.COMPLETED else CognitiveWorkState.BLOCKED)
            val result = AgentRunResult(
                if (blocker == null) lastOutput else blocker,
                AgentState.RESPOND,
                audit.toList(),
                resultExperience.takeUnless { ephemeralScreenContext },
                blocker,
                proof(verifiedOutcome, resultExperience.id.takeIf { verifiedOutcome })
            )
            checkpointStore?.clear()
            return result
        } catch (_: StepLimitReached) {
            checkpointStore?.clear()
            emit(CognitiveWorkState.BLOCKED)
            return AgentRunResult(
                "Agent step budget exhausted.",
                audit.lastOrNull()?.state ?: AgentState.OBSERVE,
                audit.toList(),
                null,
                "Hard maximum step count reached; no further model or tool calls were made.",
                proof()
            )
        } catch (failure: Exception) {
            emit(CognitiveWorkState.BLOCKED)
            throw failure
        }
    }

    private fun experienceId(task: String, completedAt: Long, run: Int): String {
        val digest = MessageDigest.getInstance("SHA-256").digest((callIdPrefix + "\u0000" + task).toByteArray(Charsets.UTF_8))
            .take(16).joinToString("") { "%02x".format(it) }
        return "agent-$digest-$completedAt-$run"
    }

    private class StepLimitReached : RuntimeException()

    companion object {
        private val RESUMABLE_STATES = setOf(
            AgentState.OBSERVE,
            AgentState.RETRIEVE,
            AgentState.PLAN,
            AgentState.MODEL,
            AgentState.VALIDATE
        )
    }
}

