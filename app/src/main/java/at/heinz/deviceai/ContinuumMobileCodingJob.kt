package at.heinz.deviceai

import at.heinz.deviceai.learning.StrictJson
import java.util.UUID

/**
 * EXP-00 / M4.2: one owner-confirmed, bounded coding order for the Termux worker.
 *
 * Android never sends shell text: only the fixed builder script, the fixed
 * operation `code-test` (or `code-cancel`) and four validated values. The same
 * grammar is enforced again by `scripts/continuum-mobile-coding.mjs` and by
 * the canonical backlog (`backlog-cli propose-test`). The receipt that Termux
 * returns is checked here before anything is shown as a verified patch.
 */
internal object ContinuumMobileCodingJob {
    private val JOB = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    private val MODULE = Regex("\\.github/continuum/[A-Za-z][A-Za-z0-9_-]{0,63}\\.mjs")
    private val EXPORT = Regex("[A-Za-z_][A-Za-z0-9_]{0,63}")
    private val SHA256 = Regex("[0-9a-f]{64}")
    private val COMMIT = Regex("[0-9a-f]{40}")
    const val MAX_CASES_JSON = 300
    const val MAX_RECEIPT_CHARS = 32_768

    data class Order(val jobId: String, val modulePath: String, val exportName: String, val casesJson: String)

    fun newOrder(modulePath: String, exportName: String, casesJson: String): Order =
        Order(UUID.randomUUID().toString(), modulePath, exportName, casesJson)

    fun problems(order: Order): List<String> = buildList {
        if (!JOB.matches(order.jobId)) add("Auftrags-ID ist keine UUIDv4")
        if (!MODULE.matches(order.modulePath)) add("Modul muss .github/continuum/<name>.mjs sein")
        if (!EXPORT.matches(order.exportName)) add("Export muss ein JavaScript-Bezeichner sein")
        if (order.casesJson.length !in 2..MAX_CASES_JSON || order.casesJson.any { it.code < 0x20 }) {
            add("Testfälle: 2..300 Zeichen JSON in einer Zeile")
        }
    }

    fun command(order: Order): ContinuumMobileBuildPlan.Command {
        require(problems(order).isEmpty()) { "Ungültiger Coding-Auftrag" }
        return ContinuumMobileBuildPlan.Command(
            ContinuumMobileBuildPlan.NODE_EXECUTABLE,
            listOf(
                ContinuumMobileBuildPlan.BUILDER_SCRIPT, "code-test",
                "--project", ContinuumMobileBuildPlan.PROJECT_DIR,
                "--job", order.jobId, "--module", order.modulePath,
                "--export", order.exportName, "--cases-json", order.casesJson
            ),
            ContinuumMobileBuildPlan.PROJECT_DIR
        )
    }

    fun cancelCommand(jobId: String): ContinuumMobileBuildPlan.Command {
        require(JOB.matches(jobId)) { "Ungültige Auftrags-ID" }
        return ContinuumMobileBuildPlan.Command(
            ContinuumMobileBuildPlan.NODE_EXECUTABLE,
            listOf(ContinuumMobileBuildPlan.BUILDER_SCRIPT, "code-cancel",
                "--project", ContinuumMobileBuildPlan.PROJECT_DIR, "--job", jobId),
            ContinuumMobileBuildPlan.PROJECT_DIR
        )
    }

    enum class Outcome {
        PATCH_VERIFIED_PENDING_REVIEW,
        CODING_FAILED,
        CANCELLED,
        BLOCKED,
        INVALID_RECEIPT
    }

    /** Small, display-safe view of a validated receipt; never raw terminal text. */
    data class Receipt(
        val outcome: Outcome,
        val jobId: String,
        val status: String,
        val commit: String?,
        val patchSha256: String?,
        val independentExecuted: Long?,
        val commands: List<String>,
        val failureCode: String?
    )

    private fun invalid(jobId: String) = Receipt(Outcome.INVALID_RECEIPT, jobId, "INVALID", null, null, null, emptyList(), null)

    /**
     * A verified patch requires: official Termux result (err=-1), exit 0, the
     * expected job, a full commit, patch and verification hashes, three real
     * fixed-profile runs with exit 0 and executed tests, an independent run
     * with at least as many tests as the candidate, and no merge/install/APK claim.
     */
    fun evaluate(expectedJobId: String, exitCode: Int?, termuxError: Int?, stdout: String?): Receipt {
        if (exitCode == null || termuxError != -1 || stdout == null || stdout.length > MAX_RECEIPT_CHARS) return invalid(expectedJobId)
        val r = try { StrictJson.parseObject(stdout) } catch (_: IllegalArgumentException) { return invalid(expectedJobId) }
        if (r["schema"] != "continuum-mobile-coding-receipt" || r["version"] != 1L || r["job"] != expectedJobId) return invalid(expectedJobId)
        val status = r["status"] as? String ?: return invalid(expectedJobId)
        val commit = (r["commit"] as? String)?.takeIf { COMMIT.matches(it) }
        val failureCode = ((r["failure"] as? Map<*, *>)?.get("code") as? String)?.takeIf { Regex("[A-Z][A-Z0-9_]{1,47}").matches(it) }
        if (r["approvedForMerge"] != false || r["installed"] != false || r["apk"] != null || r["deviceVerified"] != false) {
            return invalid(expectedJobId)
        }
        val runs = (r["commands"] as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()
        val commands = runs.map { "${it["phase"]}: exit ${it["exitCode"]}, ${it["executed"]} Tests" }
        return when (status) {
            "CODING_PATCH_VERIFIED_PENDING_REVIEW" -> {
                val patch = (r["patch"] as? Map<*, *>)?.get("sha256") as? String
                val evidence = r["evidence"] as? Map<*, *>
                val verification = evidence?.get("verificationSha256") as? String
                val candidate = evidence?.get("candidateExecuted") as? Long
                val independent = evidence?.get("independentExecuted") as? Long
                val phases = runs.map { it["phase"] }
                val runsOk = phases == listOf("baseline", "candidate", "independent") && runs.all {
                    it["exitCode"] == 0L && it["passed"] == true && (it["executed"] as? Long ?: 0L) > 0L && it["failed"] == 0L
                }
                if (exitCode == 0 && commit != null && patch != null && SHA256.matches(patch) &&
                    verification != null && SHA256.matches(verification) && candidate != null &&
                    independent != null && independent >= candidate && runsOk
                ) Receipt(Outcome.PATCH_VERIFIED_PENDING_REVIEW, expectedJobId, status, commit, patch, independent, commands, null)
                else invalid(expectedJobId)
            }
            "FAILED_CODING_CHECKS" ->
                if (exitCode != 0 && failureCode != null) Receipt(Outcome.CODING_FAILED, expectedJobId, status, commit, null, null, commands, failureCode)
                else invalid(expectedJobId)
            "CANCELLED" -> Receipt(Outcome.CANCELLED, expectedJobId, status, commit, null, null, emptyList(), null)
            else ->
                if (status.startsWith("BLOCKED_") && status.length <= 48 && exitCode != 0) {
                    Receipt(Outcome.BLOCKED, expectedJobId, status, commit, null, null, emptyList(), failureCode)
                } else invalid(expectedJobId)
        }
    }

    fun explanation(receipt: Receipt): String = when (receipt.outcome) {
        Outcome.PATCH_VERIFIED_PENDING_REVIEW ->
            "Termux meldet einen in zwei getrennten Arbeitsständen geprüften Patch (${receipt.patchSha256?.take(12)}) " +
                "für Commit ${receipt.commit?.take(12)}. Er ist NICHT übernommen und braucht deine Prüfung."
        Outcome.CODING_FAILED -> "Der Coding-Auftrag ist fehlgeschlagen (${receipt.failureCode}). Kein Patch wurde erzeugt."
        Outcome.CANCELLED -> "Der Coding-Auftrag wurde abgebrochen."
        Outcome.BLOCKED -> "Der Coding-Auftrag wurde nicht ausgeführt: ${receipt.status}."
        Outcome.INVALID_RECEIPT -> "Keine verlässliche Quittung; kein geprüfter Patch nachgewiesen."
    }
}
