package at.heinz.deviceai

import at.heinz.deviceai.learning.StrictJson

/**
 * CCC1 RED→GREEN patch receipt (Argon-4-Dossier 9.1, Punkt 6) as a checkable
 * data object. evaluate() alone yields at most SELF_CONSISTENT_UNATTESTED: it cannot see git.
 * evaluateAttested() yields GREEN_ATTESTED_PENDING_REVIEW only with an attestation of
 * .github/continuum/ccc1-redgreen-verifier.mjs (real git objects, patch digest, re-executed
 * tests) bound to sha256 of exactly this canonical JSON. Self-consistency requires:
 *  - every commit id is a full 40-hex SHA, the patch hash is 64-hex SHA-256,
 *    base, RED and GREEN commits are pairwise different;
 *  - the RED run belongs to the RED commit, exited non-zero and really failed
 *    at least one of the declared target tests (and only target tests);
 *  - the GREEN run belongs to the GREEN commit, exited 0 with zero failures and
 *    passed EVERY target test;
 *  - nobody claims merge approval, installation or device verification.
 * Timeout, cancellation or a missing exit code is UNKNOWN – never success.
 * It is review evidence, never authority to merge, sign or install.
 */
data class Ccc1TestRun(
    val sha: String, val command: String, val executed: Long, val failed: Long, val skipped: Long,
    val failingTests: List<String>, val passedTargets: List<String>,
    val exitCode: Long?, val timedOut: Boolean = false, val cancelled: Boolean = false
)

data class Ccc1PatchReceipt(
    val repo: String, val baseSha: String, val redSha: String, val greenSha: String,
    val patchSha256: String, val targetTests: List<String>,
    val red: Ccc1TestRun?, val green: Ccc1TestRun?,
    val approvedForMerge: Boolean = false, val installed: Boolean = false, val deviceVerified: Boolean = false
) {
    /** SELF_CONSISTENT_UNATTESTED = fields agree with each other only (M2 level, review #370/2);
     *  GREEN_ATTESTED_PENDING_REVIEW additionally needs a matching host verifier attestation. */
    enum class Verdict { GREEN_ATTESTED_PENDING_REVIEW, SELF_CONSISTENT_UNATTESTED, RED_ONLY, UNKNOWN, INVALID }
    data class Evaluation(val verdict: Verdict, val reasons: List<String>)

    fun evaluate(): Evaluation {
        val invalid = mutableListOf<String>()
        if (!REPO.matches(repo)) invalid += "REPO"
        if (listOf(baseSha, redSha, greenSha).any { !SHA1.matches(it) }) invalid += "COMMIT_ID"
        if (setOf(baseSha, redSha, greenSha).size != 3) invalid += "COMMITS_NOT_DISTINCT"
        if (!SHA256.matches(patchSha256)) invalid += "PATCH_HASH"
        if (targetTests.isEmpty() || targetTests.size > MAX_TESTS || targetTests.any { !TEST_ID.matches(it) } ||
            targetTests.toSet().size != targetTests.size) invalid += "TARGET_TESTS"
        if (approvedForMerge || installed || deviceVerified) invalid += "FORBIDDEN_CLAIM"
        val r = red ?: return Evaluation(Verdict.INVALID, invalid + "RED_MISSING")
        if (invalid.isNotEmpty()) return Evaluation(Verdict.INVALID, invalid)
        if (incomplete(r) || green?.let(::incomplete) == true) return Evaluation(Verdict.UNKNOWN, listOf("RUN_INCOMPLETE"))
        if (r.sha != redSha) invalid += "RED_SHA"
        if (r.exitCode == 0L || r.failed < 1 || r.failingTests.isEmpty() || r.executed < 1 ||
            !targetTests.containsAll(r.failingTests)) invalid += "RED_NOT_FAILING_ON_TARGETS"
        if (invalid.isNotEmpty()) return Evaluation(Verdict.INVALID, invalid)
        val g = green ?: return Evaluation(Verdict.RED_ONLY, listOf("GREEN_MISSING"))
        if (g.sha != greenSha) invalid += "GREEN_SHA"
        if (g.exitCode != 0L || g.failed != 0L || g.failingTests.isNotEmpty() || g.executed < targetTests.size ||
            !g.passedTargets.containsAll(targetTests)) invalid += "GREEN_NOT_PASSING_ALL_TARGETS"
        return if (invalid.isEmpty()) Evaluation(Verdict.SELF_CONSISTENT_UNATTESTED, listOf("NO_ATTESTATION"))
        else Evaluation(Verdict.INVALID, invalid)
    }

    /**
     * GREEN only if this receipt is self-consistent AND the host verifier attested exactly these bytes:
     * receiptSha256 == sha256(toJson()), verdict VERIFIED without reasons, same patch digest, its OWN
     * re-run saw RED fail as claimed and GREEN with zero failures, and both JUnit artifacts are hashed.
     */
    fun evaluateAttested(attestationJson: String): Evaluation {
        val self = evaluate()
        if (self.verdict != Verdict.SELF_CONSISTENT_UNATTESTED) return self
        val a = try { StrictJson.parseObject(attestationJson) } catch (_: IllegalArgumentException) { null }
            ?: return Evaluation(Verdict.SELF_CONSISTENT_UNATTESTED, listOf("ATTESTATION_UNREADABLE"))
        val why = mutableListOf<String>()
        if (a["schema"] != ATTESTATION_SCHEMA || a["version"] != 1L) why += "ATTESTATION_SCHEMA"
        if (a["receiptSha256"] != sha256(toJson())) why += "ATTESTATION_FOR_OTHER_RECEIPT"
        if (a["verdict"] != "VERIFIED" || (a["reasons"] as? List<*>)?.isEmpty() != true) why += "VERIFIER_REJECTED"
        if (a["patchSha256"] != patchSha256) why += "PATCH_HASH_MISMATCH"
        val r = a["red"] as? Map<*, *>; val g = a["green"] as? Map<*, *>
        if (r == null || g == null || listOf(r, g).any { (it["xmlSha256"] as? String)?.let(SHA256::matches) != true })
            why += "ARTIFACTS_MISSING"
        else {
            if (r["failed"] != red?.failed || r["executed"] != red?.executed || r["exitCode"] == 0L) why += "RED_RUN_DIFFERS"
            if (g["failed"] != 0L || g["exitCode"] != 0L || g["executed"] != green?.executed) why += "GREEN_RUN_DIFFERS"
        }
        return if (why.isEmpty()) Evaluation(Verdict.GREEN_ATTESTED_PENDING_REVIEW, emptyList())
        else Evaluation(Verdict.SELF_CONSISTENT_UNATTESTED, why)
    }

    private fun incomplete(run: Ccc1TestRun) = run.exitCode == null || run.timedOut || run.cancelled

    /** Canonical JSON: fixed key order, no whitespace, so equal receipts hash equally. */
    fun toJson(): String = buildString {
        append("{\"schema\":").append(q(SCHEMA)).append(",\"version\":1")
        append(",\"repo\":").append(q(repo)).append(",\"baseSha\":").append(q(baseSha))
        append(",\"redSha\":").append(q(redSha)).append(",\"greenSha\":").append(q(greenSha))
        append(",\"patchSha256\":").append(q(patchSha256))
        append(",\"targetTests\":").append(list(targetTests))
        append(",\"red\":").append(red?.let(::run) ?: "null")
        append(",\"green\":").append(green?.let(::run) ?: "null")
        append(",\"approvedForMerge\":").append(approvedForMerge)
        append(",\"installed\":").append(installed)
        append(",\"deviceVerified\":").append(deviceVerified).append('}')
    }

    companion object {
        const val SCHEMA = "continuum-ccc1-patch-receipt"
        const val ATTESTATION_SCHEMA = "continuum-ccc1-redgreen-attestation"
        private fun sha256(s: String) = java.security.MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        private const val MAX_TESTS = 512
        private val SHA1 = Regex("[0-9a-f]{40}")
        private val SHA256 = Regex("[0-9a-f]{64}")
        private val REPO = Regex("[A-Za-z0-9_.-]{1,64}/[A-Za-z0-9_.-]{1,100}")
        private val TEST_ID = Regex("[A-Za-z0-9_.$\\[\\]-]{3,300}")

        private fun q(s: String) = buildString {
            append('"')
            s.forEach { c -> when {
                c == '"' -> append("\\\""); c == '\\' -> append("\\\\")
                c < ' ' -> append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> append(c) } }
            append('"')
        }
        private fun list(items: List<String>) = items.joinToString(",", "[", "]") { q(it) }
        private fun run(r: Ccc1TestRun) = "{\"sha\":${q(r.sha)},\"command\":${q(r.command)}," +
            "\"executed\":${r.executed},\"failed\":${r.failed},\"skipped\":${r.skipped}," +
            "\"failingTests\":${list(r.failingTests)},\"passedTargets\":${list(r.passedTargets)}," +
            "\"exitCode\":${r.exitCode ?: "null"},\"timedOut\":${r.timedOut},\"cancelled\":${r.cancelled}}"

        private fun strings(v: Any?): List<String>? = (v as? List<*>)?.map { it as? String ?: return null }
        private fun parseRun(v: Any?): Ccc1TestRun? {
            val m = v as? Map<*, *> ?: return null
            return Ccc1TestRun(
                sha = m["sha"] as? String ?: return null, command = m["command"] as? String ?: return null,
                executed = m["executed"] as? Long ?: return null, failed = m["failed"] as? Long ?: return null,
                skipped = m["skipped"] as? Long ?: return null,
                failingTests = strings(m["failingTests"]) ?: return null,
                passedTargets = strings(m["passedTargets"]) ?: return null,
                exitCode = if (m.containsKey("exitCode") && m["exitCode"] == null) null else m["exitCode"] as? Long ?: return null,
                timedOut = m["timedOut"] as? Boolean ?: return null, cancelled = m["cancelled"] as? Boolean ?: return null)
        }

        /** Strict parse; any missing/mistyped field or unknown schema/version yields null. */
        fun parse(json: String): Ccc1PatchReceipt? {
            val m = try { StrictJson.parseObject(json) } catch (_: IllegalArgumentException) { return null }
            if (m["schema"] != SCHEMA || m["version"] != 1L) return null
            return Ccc1PatchReceipt(
                repo = m["repo"] as? String ?: return null, baseSha = m["baseSha"] as? String ?: return null,
                redSha = m["redSha"] as? String ?: return null, greenSha = m["greenSha"] as? String ?: return null,
                patchSha256 = m["patchSha256"] as? String ?: return null,
                targetTests = strings(m["targetTests"]) ?: return null,
                red = if (m["red"] == null) null else parseRun(m["red"]) ?: return null,
                green = if (m["green"] == null) null else parseRun(m["green"]) ?: return null,
                approvedForMerge = m["approvedForMerge"] as? Boolean ?: return null,
                installed = m["installed"] as? Boolean ?: return null,
                deviceVerified = m["deviceVerified"] as? Boolean ?: return null)
        }
    }
}
