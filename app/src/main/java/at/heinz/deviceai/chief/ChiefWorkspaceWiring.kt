package at.heinz.deviceai.chief

import at.heinz.deviceai.routines.AgentMailbox
import at.heinz.deviceai.routines.AgentMessage
import at.heinz.deviceai.routines.AgentPriority
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/** Correlation for an already authorized CCC1 job; these fields grant no capability.
 * The injected port must verify the canonical journal, job/recovery and OS boundary.
 * No command text, new scheduler or mailbox store is accepted here. */
data class WorkspaceTaskScope(
    val workerId: String,
    val taskId: String,
    val sourceCommit: String,
    val requestMessageId: String,
    val requestHash: String,
    val jobId: String,
    val profile: String,
    val memoryMb: Int,
    val cpus: Int,
    val pids: Int,
    val timeoutMillis: Long
) {
    init {
        require(workerId.matches(Regex("[a-z][a-z0-9-]{2,63}")))
        require(taskId.matches(Regex("t-[a-f0-9]{16}")))
        require(sourceCommit.matches(Regex("[a-f0-9]{40}")))
        require(requestHash.matches(Regex("[a-f0-9]{64}")))
        require(listOf(requestMessageId, jobId).all { it.matches(Regex("[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}")) })
        require(profile in setOf("node-continuum", "node-backend", "android-compile", "android-unit", "android-build", "kotlin-fixture"))
        require(memoryMb in 256..6144 && cpus in 1..4 && pids in 16..256 && timeoutMillis in 1000..300_000)
    }
}

/** A trusted runner's observation, never a verified coding receipt or model proof. */
data class WorkspaceObservation(
    val scope: WorkspaceTaskScope,
    val workspaceId: String,
    val evidenceSha256: String,
    val teardownConfirmed: Boolean,
    val profileExitCode: Int
)

/** Trusted injection seam, not Android IPC. Implementations must confine fixed
 * profiles and make cancellation close their runner/container, including startup races. */
interface ChiefWorkspacePort {
    fun execute(scope: WorkspaceTaskScope): WorkspaceObservation
    fun cancel(scope: WorkspaceTaskScope)
}

enum class WorkspaceDeliveryStatus { DELIVERED, MAILBOX_REJECTED, MAILBOX_ERROR, REPORT_SCOPE_MISMATCH }
data class WorkspaceDelivery(val taskId: String, val workerId: String, val messageId: String, val status: WorkspaceDeliveryStatus)

/** Adapters for the existing pool factory and existing single Chief mailbox.
 * Durable execution/replay remains CCC1; mailbox delivery is intentionally not
 * presented as durable storage. No Activity/device transport is installed. */
object ChiefWorkspaceWiring {
    fun job(brief: WorkerBrief, scope: WorkspaceTaskScope, port: ChiefWorkspacePort): WorkerJob {
        require(brief.id == scope.workerId)
        require(scope.timeoutMillis <= brief.deadlineMillis)
        val cancelled = AtomicBoolean()
        val started = AtomicBoolean()
        return object : WorkerJob {
            override fun run(): String {
                check(started.compareAndSet(false, true)) { "WORKSPACE_JOB_ALREADY_STARTED" }
                if (cancelled.get()) throw CancellationException()
                val observed = port.execute(scope)
                if (cancelled.get()) throw CancellationException()
                check(observed.scope == scope) { "WORKSPACE_OBSERVATION_SCOPE_MISMATCH" }
                check(observed.workspaceId.matches(Regex("continuum-[a-f0-9]{24}"))) { "WORKSPACE_ID_INVALID" }
                check(observed.evidenceSha256.matches(Regex("[a-f0-9]{64}"))) { "WORKSPACE_EVIDENCE_INVALID" }
                check(observed.teardownConfirmed) { "WORKSPACE_TEARDOWN_UNCONFIRMED" }
                check(observed.profileExitCode in 0..255) { "WORKSPACE_EXIT_INVALID" }
                if (observed.profileExitCode != 0) throw WorkerBlocked(
                    "HOST_PROFILE_FAILED task=${scope.taskId} exit=${observed.profileExitCode} " +
                        "evidence=${observed.evidenceSha256} verification=PENDING")
                // DONE means the observation arrived. Independent CCC1 verification
                // and owner review still determine whether a coding task succeeded.
                return "HOST_PROFILE_REPORTED task=${scope.taskId} request=${scope.requestMessageId} " +
                    "workspace=${observed.workspaceId} profile=${scope.profile} exit=${observed.profileExitCode} " +
                    "evidence=${observed.evidenceSha256} verification=PENDING"
            }
            override fun cancel() {
                if (cancelled.compareAndSet(false, true)) port.cancel(scope)
            }
        }
    }

    /** Required delivery callback makes a full/rejected mailbox visible to Chief.
     * The caller must surface failures; this adapter creates no parallel outbox. */
    fun mailboxReporter(
        mailbox: AgentMailbox,
        scope: WorkspaceTaskScope,
        chiefId: String = "chief",
        onDelivery: (WorkspaceDelivery) -> Unit
    ): (WorkerReport) -> Unit {
        require(chiefId.matches(Regex("[A-Za-z0-9_-]{1,64}")))
        return { report ->
            val messageId = "workspace-" + UUID.randomUUID()
            val status = if (report.id != scope.workerId) WorkspaceDeliveryStatus.REPORT_SCOPE_MISMATCH else {
                val message = AgentMessage(messageId, "workspace:" + scope.workerId.take(54), chiefId,
                    if (report.status == WorkerStatus.DONE) AgentPriority.INFO else AgentPriority.URGENT,
                    "Workspace task=${scope.taskId} source=${scope.sourceCommit} request=${scope.requestMessageId} " +
                        "workerStatus=${report.status} " + report.summary.take(1_000))
                try {
                    if (mailbox.send(message)) WorkspaceDeliveryStatus.DELIVERED else WorkspaceDeliveryStatus.MAILBOX_REJECTED
                } catch (_: Exception) { WorkspaceDeliveryStatus.MAILBOX_ERROR }
            }
            onDelivery(WorkspaceDelivery(scope.taskId, scope.workerId, messageId, status))
        }
    }
}
