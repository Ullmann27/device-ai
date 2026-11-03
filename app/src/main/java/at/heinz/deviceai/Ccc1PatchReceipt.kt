package at.heinz.deviceai

/** Stub: CCC1 RED→GREEN patch receipt (Argon-4-Dossier 9.1 Punkt 6). Not yet validating. */
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
    enum class Verdict { GREEN_VERIFIED_PENDING_REVIEW, RED_ONLY, UNKNOWN, INVALID }
    data class Evaluation(val verdict: Verdict, val reasons: List<String>)

    fun evaluate(): Evaluation = Evaluation(Verdict.GREEN_VERIFIED_PENDING_REVIEW, emptyList())
    fun toJson(): String = "{}"

    companion object {
        fun parse(json: String): Ccc1PatchReceipt? = null
    }
}
