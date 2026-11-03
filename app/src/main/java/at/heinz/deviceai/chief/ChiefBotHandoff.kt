package at.heinz.deviceai.chief

import at.heinz.deviceai.routines.AgentMailbox
import at.heinz.deviceai.routines.AgentMessage
import at.heinz.deviceai.routines.AgentPriority
import java.io.*
import java.util.Base64
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

enum class ChiefBotStage { ANALYZE, BUILD, VERIFY, REMEMBER, REPORT }

/** Typed references only. Neither profile names nor this envelope grant execution. */
class ChiefBotHandoff(
    val schemaVersion: Int = 1, val messageId: String, val fromAgentId: String, val toAgentId: String,
    val stage: ChiefBotStage, val taskId: String, val jobId: String, val sourceCommit: String,
    val attemptId: String, val requestMessageId: String, val requestHash: String,
    val grantFingerprint: String, val persistentWorkspaceId: String, val expiresAt: Long,
    requestedTools: Set<String>, val modelCalls: Int, val toolCalls: Int, evidenceRefs: List<String>
) {
    val requestedTools: Set<String> = Collections.unmodifiableSet(LinkedHashSet(requestedTools))
    val evidenceRefs: List<String> = Collections.unmodifiableList(ArrayList(evidenceRefs))
    init {
        require(schemaVersion == 1 && UUID.matches(messageId) && UUID.matches(jobId) && UUID.matches(requestMessageId))
        require(ChiefBotProfile.ID.matches(fromAgentId) && ChiefBotProfile.ID.matches(toAgentId))
        require(Regex("t-[a-f0-9]{16}").matches(taskId) && Regex("[a-f0-9]{40}").matches(sourceCommit))
        require(Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,95}").matches(attemptId))
        require(SHA.matches(requestHash) && SHA.matches(grantFingerprint))
        require(persistentWorkspaceId == "workspace-$toAgentId" && expiresAt > 0)
        require(modelCalls in 0..8 && toolCalls in 0..16)
        require(this.requestedTools.size <= 16 && this.requestedTools.all { Regex("[a-z][a-z0-9-]{1,63}").matches(it) })
        require(this.evidenceRefs.size <= 8 && this.evidenceRefs.distinct().size == this.evidenceRefs.size && this.evidenceRefs.all(SHA::matches))
    }
    companion object {
        private val UUID = Regex("[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}")
        private val SHA = Regex("[a-f0-9]{64}")
    }
}

/** A current read from canonical CCC1 authority, never accepted from a bot's message. */
data class ChiefBotTaskAdmission(
    val taskId: String, val jobId: String, val sourceCommit: String, val attemptId: String,
    val requestMessageId: String, val requestHash: String, val grantFingerprint: String,
    val currentGrant: Boolean, val terminal: Boolean, val unknownEffect: Boolean,
    val builderId: String, val verifierId: String, val allowedTools: Set<String>,
    val remainingModelCalls: Int, val remainingToolCalls: Int, val expiresAt: Long,
    val seenMessageIds: Set<String>, val assignedStages: Map<ChiefBotStage, String> = emptyMap()
)

/** Implemented only by trusted runner/transport code. No permissive default implementation.
 * claim MUST atomically recheck authority, debit the ORIGINAL task budget and durably record
 * the message/attempt before any effect. A new adapter instance is never replay authority. */
interface ChiefBotHandoffAuthority {
    fun inspect(taskId: String): ChiefBotTaskAdmission?
    fun claim(handoff: ChiefBotHandoff): Boolean
    fun claimCurrent(handoff: ChiefBotHandoff): Boolean
    fun evidenceCurrent(handoff: ChiefBotHandoff): Boolean
}

enum class ChiefBotHandoffDecision { ADMIT, REJECT, MAILBOX_REJECTED }
data class ChiefBotHandoffDelivery(val messageId: String, val decision: ChiefBotHandoffDecision,
                                  val handoff: ChiefBotHandoff? = null)

/** Existing mailbox + existing workspace jobs. Does not start a process or create another scheduler. */
class ChiefBotHandoffAdmission(
    private val profiles: ChiefBotProfileStore,
    private val mailbox: AgentMailbox,
    private val authority: ChiefBotHandoffAuthority,
    private val clock: () -> Long = System::currentTimeMillis
) {
    fun admit(handoff: ChiefBotHandoff): ChiefBotHandoffDecision =
        if (valid(handoff, claimed = false)) ChiefBotHandoffDecision.ADMIT else ChiefBotHandoffDecision.REJECT

    fun send(handoff: ChiefBotHandoff): ChiefBotHandoffDecision {
        if (admit(handoff) != ChiefBotHandoffDecision.ADMIT) return ChiefBotHandoffDecision.REJECT
        return if (mailbox.send(AgentMessage(handoff.messageId, handoff.fromAgentId, handoff.toAgentId,
                AgentPriority.INFO, encode(handoff)))) ChiefBotHandoffDecision.ADMIT else ChiefBotHandoffDecision.MAILBOX_REJECTED
    }

    /** Every consumed message is rechecked. Stale or foreign transport metadata is never executed. */
    fun receive(agentId: String): List<ChiefBotHandoffDelivery> = mailbox.drain(agentId).map { message ->
        val handoff = runCatching { decode(message.body) }.getOrNull()
        if (handoff == null || handoff.messageId != message.id || handoff.fromAgentId != message.from ||
            handoff.toAgentId != agentId || message.to != agentId || admit(handoff) != ChiefBotHandoffDecision.ADMIT)
            ChiefBotHandoffDelivery(message.id, ChiefBotHandoffDecision.REJECT)
        else ChiefBotHandoffDelivery(message.id, ChiefBotHandoffDecision.ADMIT, handoff)
    }

    /** Factory for ChiefWorkerPool; the existing canonical port still owns OS admission and teardown. */
    fun workspaceJob(brief: WorkerBrief, handoff: ChiefBotHandoff, scope: WorkspaceTaskScope,
                     port: ChiefWorkspacePort): WorkerJob {
        require(handoff.stage in setOf(ChiefBotStage.BUILD, ChiefBotStage.VERIFY))
        require(handoff.toolCalls >= 1 && scope.timeoutMillis <= handoff.expiresAt - clock())
        require(scope.workerId == handoff.toAgentId && scope.taskId == handoff.taskId &&
            scope.jobId == handoff.jobId && scope.sourceCommit == handoff.sourceCommit &&
            scope.requestMessageId == handoff.requestMessageId && scope.requestHash == handoff.requestHash)
        val started = AtomicBoolean()
        val guarded = object : ChiefWorkspacePort {
            override fun execute(scope: WorkspaceTaskScope): WorkspaceObservation {
                check(started.compareAndSet(false, true)) { "BOT_HANDOFF_ALREADY_STARTED" }
                check(valid(handoff, claimed = false) && authority.claim(handoff)) { "BOT_HANDOFF_ADMISSION_REJECTED" }
                check(valid(handoff, claimed = true)) { "BOT_HANDOFF_CLAIM_REVOKED" }
                val result = port.execute(scope)
                check(valid(handoff, claimed = true)) { "BOT_HANDOFF_RESULT_STALE" }
                return result
            }
            override fun cancel(scope: WorkspaceTaskScope) = port.cancel(scope)
        }
        return ChiefWorkspaceWiring.job(brief, scope, guarded)
    }

    private fun valid(h: ChiefBotHandoff, claimed: Boolean): Boolean = runCatching {
        val now = clock()
        val from = profiles.get(h.fromAgentId) ?: return false
        val to = profiles.get(h.toAgentId) ?: return false
        val current = authority.inspect(h.taskId) ?: return false
        if (from.agentId != h.fromAgentId || to.workspaceId != h.persistentWorkspaceId ||
            !current.currentGrant || current.terminal || current.unknownEffect || now >= h.expiresAt ||
            h.expiresAt > current.expiresAt || current.expiresAt <= now ||
            current.taskId != h.taskId || current.jobId != h.jobId || current.sourceCommit != h.sourceCommit ||
            current.attemptId != h.attemptId || current.requestMessageId != h.requestMessageId ||
            current.requestHash != h.requestHash || current.grantFingerprint != h.grantFingerprint ||
            !current.allowedTools.containsAll(h.requestedTools)) return false
        if (current.builderId == current.verifierId) return false
        val assigned = when (h.stage) {
            ChiefBotStage.BUILD -> current.builderId
            ChiefBotStage.VERIFY -> current.verifierId
            else -> current.assignedStages[h.stage] ?: return false
        }
        if (assigned != h.toAgentId || profiles.get(current.builderId) == null || profiles.get(current.verifierId) == null) return false
        if (!claimed && (h.messageId in current.seenMessageIds || h.modelCalls > current.remainingModelCalls ||
                h.toolCalls > current.remainingToolCalls || current.remainingModelCalls < 0 || current.remainingToolCalls < 0)) return false
        if (claimed && !authority.claimCurrent(h)) return false
        if (h.stage in setOf(ChiefBotStage.VERIFY, ChiefBotStage.REMEMBER, ChiefBotStage.REPORT) &&
            !authority.evidenceCurrent(h)) return false
        true
    }.getOrDefault(false)

    private fun encode(h: ChiefBotHandoff): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(h.schemaVersion)
            listOf(h.messageId,h.fromAgentId,h.toAgentId,h.stage.name,h.taskId,h.jobId,h.sourceCommit,h.attemptId,
                h.requestMessageId,h.requestHash,h.grantFingerprint,h.persistentWorkspaceId).forEach(out::writeUTF)
            out.writeLong(h.expiresAt); out.writeInt(h.modelCalls); out.writeInt(h.toolCalls)
            out.writeInt(h.requestedTools.size); h.requestedTools.sorted().forEach(out::writeUTF)
            out.writeInt(h.evidenceRefs.size); h.evidenceRefs.forEach(out::writeUTF)
        }
        return "GENESIS1:" + Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    private fun decode(body: String): ChiefBotHandoff {
        require(body.length <= 4_000 && body.startsWith("GENESIS1:"))
        DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(body.removePrefix("GENESIS1:")))).use { input ->
            val version = input.readInt(); val fields = List(12) { input.readUTF() }
            val expiry = input.readLong(); val models = input.readInt(); val tools = input.readInt()
            val toolCount = input.readInt().also { require(it in 0..16) }
            val requested = List(toolCount) { input.readUTF() }; require(requested.distinct().size == toolCount)
            val refs = List(input.readInt().also { require(it in 0..8) }) { input.readUTF() }
            require(input.available() == 0)
            return ChiefBotHandoff(version, fields[0],fields[1],fields[2],ChiefBotStage.valueOf(fields[3]),
                fields[4],fields[5],fields[6],fields[7],fields[8],fields[9],fields[10],fields[11],expiry,
                requested.toSet(),models,tools,refs)
        }
    }
}
