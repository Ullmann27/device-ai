package at.heinz.deviceai.cognitive

import java.io.File

/**
 * The app's side of the shared development backlog ([DevelopmentBacklog]).
 *
 * - Every [DevelopmentCoordinator] job is a backlog task: started = propose,
 *   accept, claim (lease); a finished proposal = submit (artifact PROPOSAL with
 *   the SHA-256 of the rendered proposal) -> VERIFYING; a failed, cancelled or
 *   timed-out job = fail with its reason. The app never produces test
 *   evidence, so app work never reaches DONE on the device.
 * - An interrupted job (app killed) keeps its claim until the lease ends; then
 *   [recover] returns it to READY (or FAILED when its budget is used up).
 * - Human decisions (accept, reject, abort, retry) use the same operations.
 * - Every local operation gets an opId and is journaled in the same atomic
 *   write (`deviceOpLog`), so it can be exported as an exactly-once operation
 *   log for `developer-agent.mjs backlog apply --lenient` and re-applied on top
 *   of an imported canonical backlog without duplicates.
 *
 * File access only; run every method off the UI thread.
 */
class DeviceDevelopmentBacklog(dir: File, private val clock: () -> Long = System::currentTimeMillis) {
    val store = DevelopmentBacklogStore(File(dir, "development-backlog.json"), ACTOR, clock)

    data class Change(
        val doc: Map<String, Any?>,
        val reports: List<DevelopmentBacklog.OpReport>,
        val recovered: DevelopmentBacklogStore.Recovery?
    )

    data class ImportResult(
        val revision: Long, val tasks: Int, val replayed: Int, val alreadyIncluded: Int,
        val rejected: List<String>, val recovered: DevelopmentBacklogStore.Recovery?
    )

    private fun newOpId() = "android:" + DevelopmentBacklog.newToken().take(24)

    @Suppress("UNCHECKED_CAST")
    private fun logOf(doc: Map<String, Any?>): List<Map<String, Any?>> = (doc[LOG] as? List<Map<String, Any?>>).orEmpty()

    /** Same document with [entries] appended to the device operation log (bounded, drops counted). */
    private fun withLog(doc: Map<String, Any?>, entries: List<Map<String, Any?>>): Map<String, Any?> {
        if (entries.isEmpty()) return doc
        val next = LinkedHashMap(doc)
        val log = ArrayList<Any?>(logOf(doc))
        log.addAll(entries)
        var dropped = (doc[LOG_DROPPED] as? Long) ?: 0L
        while (log.size > MAX_LOG) { log.removeAt(0); dropped++ }
        next[LOG] = log
        if (dropped > 0) next[LOG_DROPPED] = dropped
        return next
    }

    /** Apply local operations atomically; only operations that changed state are journaled. */
    fun apply(ops: List<Map<String, Any?>>, stopOnError: Boolean = true): Change {
        val now = clock()
        val stamped = ops.map { op ->
            LinkedHashMap(op).apply {
                putIfAbsent("actor", ACTOR)
                putIfAbsent("at", now)
                putIfAbsent("opId", newOpId())
            }
        }
        var reports: List<DevelopmentBacklog.OpReport> = emptyList()
        val result = store.update { doc ->
            val (next, r) = DevelopmentBacklog.applyOps(doc, stamped, stopOnError)
            reports = r
            withLog(next, stamped.filterIndexed { k, _ -> r[k].ok && r[k].changed })
        }
        return Change(result.doc, reports, result.recovered)
    }

    /** Record the start of a development job; returns the claim token for [finishJob]. */
    fun startJob(task: DevelopmentTask): String {
        val id = taskIdOf(task.taskId)
        val token = DevelopmentBacklog.newToken()
        val subject = linkedMapOf<String, Any?>("sources" to task.sources.size.toLong())
        task.sources.firstOrNull()?.let { subject["firstSource"] = DevelopmentBacklog.singleLine(it.path, 300) }
        apply(listOf(
            mapOf("op" to "propose", "task" to mapOf("id" to id, "title" to DevelopmentBacklog.singleLine(task.goal.trim(), 200).ifEmpty { "Entwicklungsaufgabe" },
                "kind" to "IMPLEMENT", "source" to "android", "priority" to 500L, "maxAttempts" to 3L, "subject" to subject),
                "reason" to "Entwicklungsassistent auf dem Gerät gestartet"),
            mapOf("op" to "accept", "id" to id, "reason" to "vom Nutzer gestartet"),
            mapOf("op" to "claim", "id" to id, "owner" to ACTOR, "token" to token, "leaseMs" to JOB_LEASE_MS)))
        return token
    }

    /** Record the outcome of a job started with [startJob]. */
    fun finishJob(taskId: String, token: String, outcome: DevelopmentOutcome): Change {
        val id = taskIdOf(taskId)
        val op: Map<String, Any?> = if (outcome.state == DevelopmentJobState.COMPLETED) {
            val proposal = outcome.proposal!!.render().toByteArray(Charsets.UTF_8)
            mapOf("op" to "submit", "id" to id, "token" to token,
                "artifact" to mapOf("type" to "PROPOSAL", "sha256" to DevelopmentBacklog.sha256Of(proposal)))
        } else {
            val code = when (outcome.state) {
                DevelopmentJobState.CANCELLED -> "CANCELLED"
                DevelopmentJobState.TIMED_OUT -> "TIMED_OUT"
                else -> outcome.failure?.kind?.name ?: "FAILED"
            }
            mapOf("op" to "fail", "id" to id, "token" to token,
                "error" to mapOf("code" to code, "message" to DevelopmentBacklog.singleLine(outcome.failure?.message.orEmpty(), 400)))
        }
        return apply(listOf(op))
    }

    /** Crash recovery: expired claims return to READY (or FAILED when the budget is used up). */
    fun recover(): List<String> = apply(listOf(mapOf("op" to "expire"))).reports.firstOrNull()?.expired.orEmpty()

    /** A human decision on one task: accept, reject, abort or retry (the latter three need a reason). */
    fun decide(id: String, action: String, reason: String?): DevelopmentBacklog.OpReport {
        require(action in setOf("accept", "reject", "abort", "retry")) { "Unknown action $action" }
        val op = linkedMapOf<String, Any?>("op" to action, "id" to id)
        if (!reason.isNullOrBlank()) op["reason"] = DevelopmentBacklog.singleLine(reason.trim(), 400)
        return apply(listOf(op), stopOnError = false).reports.single()
    }

    fun read(): DevelopmentBacklogStore.Loaded = store.read()

    fun views(): List<DevelopmentBacklog.TaskView> = DevelopmentBacklog.views(store.read().doc)

    /** Pending local operations as a JSON array for `developer-agent.mjs backlog apply --ops <file> --lenient`. */
    fun exportOperationLog(): ByteArray = (DevelopmentBacklog.canonical(logOf(store.read().doc)) + "\n").toByteArray(Charsets.UTF_8)

    /** The full local backlog in canonical form (integrity hash included). */
    fun exportBacklog(): ByteArray = DevelopmentBacklog.encode(store.read().doc)

    /**
     * Adopt a canonical backlog (e.g. downloaded from the agent state branch) and
     * re-apply the local operations on top of it. Operations the imported backlog
     * already contains (by opId) leave the log; rejected ones stay in the log and
     * are reported. The imported file must verify (integrity, rules) first.
     */
    fun importBacklog(bytes: ByteArray): ImportResult {
        val imported = DevelopmentBacklog.decode(bytes).doc
        var replayed = 0
        var included = 0
        val rejected = ArrayList<String>()
        val result = store.update { current ->
            val pending = logOf(current)
            var doc: Map<String, Any?> = LinkedHashMap(imported).apply { remove(LOG); remove(LOG_DROPPED) }
            val keep = ArrayList<Map<String, Any?>>()
            for (op in pending) {
                try {
                    val r = DevelopmentBacklog.applyOp(doc, op)
                    if (r.duplicate) { included++; continue }
                    doc = r.doc
                    replayed++
                    keep += op
                } catch (e: BacklogException) {
                    rejected += "${op["op"]} ${op["id"] ?: ""}: ${e.code}"
                    keep += op
                }
            }
            val next = LinkedHashMap(withLog(doc, keep))
            // a different lineage may have a lower revision; the local file still only moves forward
            next["revision"] = maxOf(next["revision"] as Long, (current["revision"] as Long) + 1)
            next
        }
        return ImportResult(result.doc["revision"] as Long, (result.doc["tasks"] as List<*>).size, replayed, included, rejected, result.recovered)
    }

    companion object {
        const val ACTOR = "android-app"
        const val LOG = "deviceOpLog"
        const val LOG_DROPPED = "deviceOpLogDropped"
        const val MAX_LOG = 500
        /** Covers the coordinator's hard 60 s budget with margin; an expired lease means the app died mid-job. */
        const val JOB_LEASE_MS = 300_000L

        /** Development job ids ("dev-" + UUID) are already valid backlog ids; anything else is hashed. */
        fun taskIdOf(jobId: String): String {
            val lower = jobId.lowercase()
            return if (Regex("[a-z][a-z0-9-]{2,63}").matches(lower)) lower
            else "dev-" + DevelopmentBacklog.sha256Of(jobId.toByteArray(Charsets.UTF_8)).take(32)
        }
    }
}
