package at.heinz.deviceai.chief

/**
 * An explicitly approved local worker profile. These are task-scoped specialists, NOT separate model
 * installations: their jobs may share one local Gemma runtime under the existing resource admission gates.
 */
data class ChiefSpecialist(
    val id: String,
    val capabilities: Set<String>,
    val allowedTools: Set<String>,
    val maxConcurrent: Int = 1
) {
    init {
        require(ID.matches(id))
        require(capabilities.isNotEmpty() && capabilities.size <= 16 && capabilities.all(CAPABILITY::matches))
        require(allowedTools.size <= 32 && allowedTools.all(ID::matches))
        require(maxConcurrent in 1..4)
    }

    companion object {
        internal val ID = Regex("[A-Za-z0-9_-]{1,64}")
        internal val CAPABILITY = Regex("[a-z][a-z0-9._-]{0,63}")
    }
}

/** A Chief-generated *proposal*, never permission. ToolGate still checks each effectful call. */
data class ChiefDelegation(
    val taskId: String,
    val goal: String,
    val capability: String,
    val requestedTools: Set<String> = emptySet(),
    val deadlineMillis: Long = 120_000
) {
    init {
        require(ChiefSpecialist.ID.matches(taskId))
        require(goal.isNotBlank() && goal.length <= 2_000)
        require(ChiefSpecialist.CAPABILITY.matches(capability))
        require(requestedTools.size <= 32 && requestedTools.all(ChiefSpecialist.ID::matches))
        require(deadlineMillis in 1..600_000)
    }
}

enum class ChiefDispatchOutcome {
    SUBMITTED, NO_SPECIALIST, ALL_BUSY, TOOL_SCOPE_REJECTED, DUPLICATE_TASK, POOL_FULL
}

/** An independent verifier must approve the evidence before it can influence future worker selection. */
data class ChiefOutcomeEvidence(
    val taskId: String,
    val receiptSha256: String,
    val verifiedSuccess: Boolean
) {
    init {
        require(ChiefSpecialist.ID.matches(taskId))
        require(Regex("[0-9a-f]{64}").matches(receiptSha256))
    }
}

data class ChiefSpecialistStats(val verifiedSuccesses: Int, val verifiedFailures: Int) {
    val samples: Int get() = verifiedSuccesses + verifiedFailures
    /** Laplace-smoothed reliability; an untested specialist begins at 0.5, not at a fabricated 100%. */
    val reliability: Double get() = (verifiedSuccesses + 1.0) / (samples + 2.0)
}

/**
 * One Chief-facing delegation coordinator, on top of the EXISTING ChiefWorkerPool.
 * - No specialist is registered without a trusted, explicit owner-approval callback.
 * - The Chief supplies a typed capability. Unknown capabilities never trigger invented tools or workers.
 * - Selection is bounded, deterministic and biased only by independently validated outcomes.
 * - Only Chief receives the final WorkerReport; workers never reply directly to the owner.
 * - Unverified "DONE" and untrusted model prose must NOT train the specialist ranking.
 *
 * The app integration must use its owner-approval UI for approveSpecialist and an independently
 * trusted receipt/JUnit/device verifier for verifyOutcome. Both default to deny.
 */
class ChiefDelegationCoordinator(
    private val pool: ChiefWorkerPool,
    private val approveSpecialist: (ChiefSpecialist) -> Boolean = { false },
    private val verifyOutcome: (ChiefOutcomeEvidence, WorkerReport) -> Boolean = { _, _ -> false },
    private val maxSpecialists: Int = 20
) {
    init { require(maxSpecialists in 1..20) }

    private data class Assignment(val specialistId: String, val brief: WorkerBrief)
    private val specialists = LinkedHashMap<String, ChiefSpecialist>()
    private val assignments = LinkedHashMap<String, Assignment>()
    private val completed = LinkedHashMap<String, Pair<String, WorkerReport>>()
    private val seenTaskIds = LinkedHashSet<String>()
    private val trustedReceiptHashes = LinkedHashSet<String>()
    private val scores = HashMap<String, ChiefSpecialistStats>()

    fun register(candidate: ChiefSpecialist): Boolean {
        // External approval is called outside the coordinator lock.
        if (!approveSpecialist(candidate)) return false
        return synchronized(this) {
            if (candidate.id in specialists || specialists.size >= maxSpecialists) false
            else {
                specialists[candidate.id] = candidate
                true
            }
        }
    }

    @Synchronized fun registeredSpecialists(): List<ChiefSpecialist> = specialists.values.toList()

    @Synchronized fun activeAssignments(): Map<String, String> =
        assignments.mapValues { (_, assignment) -> assignment.specialistId }

    @Synchronized fun stats(specialistId: String): ChiefSpecialistStats =
        scores[specialistId] ?: ChiefSpecialistStats(0, 0)

    /** Factory helpers can resolve the registered worker for a WorkerBrief by its unique task id. */
    @Synchronized fun specialistForTask(taskId: String): ChiefSpecialist? =
        assignments[taskId]?.specialistId?.let(specialists::get)

    /**
     * A single atomic choice and submission. No fallback to an unregistered or broader-scoped worker.
     * Uses the pool's own capacity gate in addition to per-specialist concurrency limits.
     */
    @Synchronized fun delegate(request: ChiefDelegation, onChiefReport: (WorkerReport) -> Unit): ChiefDispatchOutcome {
        if (request.taskId in seenTaskIds || request.taskId in assignments) return ChiefDispatchOutcome.DUPLICATE_TASK
        val capable = specialists.values.filter { request.capability in it.capabilities }
        if (capable.isEmpty()) return ChiefDispatchOutcome.NO_SPECIALIST
        val authorized = capable.filter { it.allowedTools.containsAll(request.requestedTools) }
        if (authorized.isEmpty()) return ChiefDispatchOutcome.TOOL_SCOPE_REJECTED

        val available = authorized.filter { specialist ->
            assignments.values.count { it.specialistId == specialist.id } < specialist.maxConcurrent
        }
        if (available.isEmpty()) return ChiefDispatchOutcome.ALL_BUSY

        // Historical verified performance is a *tie-breaker*, never a new authorization.
        val selected = available.sortedWith(
            compareByDescending<ChiefSpecialist> { stats(it.id).reliability }
                .thenBy { assignments.values.count { a -> a.specialistId == it.id } }
                .thenBy { it.id }
        ).first()
        val brief = WorkerBrief(request.taskId, request.goal, request.requestedTools, request.deadlineMillis)
        assignments[request.taskId] = Assignment(selected.id, brief)
        val accepted = pool.submit(brief) { report ->
            synchronized(this) {
                assignments.remove(request.taskId)
                completed[request.taskId] = selected.id to report
                while (completed.size > 512) completed.remove(completed.keys.first())
            }
            runCatching { onChiefReport(report) }
        }
        if (!accepted) {
            assignments.remove(request.taskId)
            return ChiefDispatchOutcome.POOL_FULL
        }
        seenTaskIds += request.taskId
        while (seenTaskIds.size > 4_096) seenTaskIds.remove(seenTaskIds.first())
        return ChiefDispatchOutcome.SUBMITTED
    }

    /**
     * Learn only from a completed run plus a receipt checked by an independent verifier.
     * The verifier is intentionally NOT a property of model output or WorkerStatus.DONE.
     */
    fun recordVerifiedOutcome(evidence: ChiefOutcomeEvidence): Boolean {
        val completedRun = synchronized(this) { completed[evidence.taskId] } ?: return false
        val (specialistId, report) = completedRun
        if (evidence.verifiedSuccess && report.status != WorkerStatus.DONE) return false
        if (!verifyOutcome(evidence, report)) return false
        return synchronized(this) {
            if (completed[evidence.taskId] != completedRun ||
                evidence.receiptSha256 in trustedReceiptHashes) false
            else {
                val old = scores[specialistId] ?: ChiefSpecialistStats(0, 0)
                scores[specialistId] = if (evidence.verifiedSuccess)
                    old.copy(verifiedSuccesses = (old.verifiedSuccesses + 1).coerceAtMost(1_000_000))
                else old.copy(verifiedFailures = (old.verifiedFailures + 1).coerceAtMost(1_000_000))
                trustedReceiptHashes += evidence.receiptSha256
                while (trustedReceiptHashes.size > 4_096) trustedReceiptHashes.remove(trustedReceiptHashes.first())
                true
            }
        }
    }
}
