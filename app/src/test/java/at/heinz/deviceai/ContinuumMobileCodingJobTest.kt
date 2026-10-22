package at.heinz.deviceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ContinuumMobileCodingJobTest {
    private val job = "3f2b8c1e-9a4d-4c2b-8e7f-1a2b3c4d5e6f"
    private val commit = "a".repeat(40)

    private fun receipt(
        status: String = "CODING_PATCH_VERIFIED_PENDING_REVIEW",
        jobId: String = job,
        runs: String = """[{"phase":"baseline","exitCode":0,"passed":true,"executed":1,"failed":0},
            {"phase":"candidate","exitCode":0,"passed":true,"executed":3,"failed":0},
            {"phase":"independent","exitCode":0,"passed":true,"executed":3,"failed":0}]""",
        extra: String = "\"approvedForMerge\":false,\"installed\":false,\"apk\":null,\"deviceVerified\":false",
        independent: Int = 3,
        failure: String = "null"
    ) = """{"schema":"continuum-mobile-coding-receipt","version":1,"job":"$jobId","status":"$status",
        "commit":"$commit","patch":{"sha256":"${"b".repeat(64)}"},
        "evidence":{"verificationSha256":"${"c".repeat(64)}","candidateExecuted":3,"independentExecuted":$independent},
        "commands":$runs,"failure":$failure,$extra}"""

    @Test fun orderBecomesFixedArgvWithoutShell() {
        val order = ContinuumMobileCodingJob.newOrder(".github/continuum/math-utils.mjs", "triple", "[{\"args\":[5],\"expected\":15}]")
        assertTrue(ContinuumMobileCodingJob.problems(order).isEmpty())
        val c = ContinuumMobileCodingJob.command(order)
        assertEquals(ContinuumMobileBuildPlan.NODE_EXECUTABLE, c.executable)
        assertEquals(listOf(ContinuumMobileBuildPlan.BUILDER_SCRIPT, "code-test", "--project", ContinuumMobileBuildPlan.PROJECT_DIR,
            "--job", order.jobId, "--module", ".github/continuum/math-utils.mjs", "--export", "triple",
            "--cases-json", "[{\"args\":[5],\"expected\":15}]"), c.args)
        val cancel = ContinuumMobileCodingJob.cancelCommand(order.jobId)
        assertEquals("code-cancel", cancel.args[1])
    }

    @Test fun unsafeOrdersAreRejectedBeforeTermux() {
        for (bad in listOf(
            ContinuumMobileCodingJob.Order("not-a-uuid", ".github/continuum/a.mjs", "a", "[]"),
            ContinuumMobileCodingJob.Order(job, "../../etc/passwd", "a", "[]"),
            ContinuumMobileCodingJob.Order(job, ".github/continuum/a.mjs", "a; rm -rf ~", "[]"),
            ContinuumMobileCodingJob.Order(job, ".github/continuum/a.mjs", "a", "x".repeat(301)),
            ContinuumMobileCodingJob.Order(job, ".github/continuum/a.mjs", "a", "[\n]")
        )) {
            assertTrue(ContinuumMobileCodingJob.problems(bad).isNotEmpty())
            assertTrue(runCatching { ContinuumMobileCodingJob.command(bad) }.isFailure)
        }
    }

    @Test fun onlyACompleteIndependentReceiptCountsAsVerifiedPatch() {
        val ok = ContinuumMobileCodingJob.evaluate(job, 0, -1, receipt())
        assertEquals(ContinuumMobileCodingJob.Outcome.PATCH_VERIFIED_PENDING_REVIEW, ok.outcome)
        assertEquals(3L, ok.independentExecuted)
        val invalid = ContinuumMobileCodingJob.Outcome.INVALID_RECEIPT
        assertEquals(invalid, ContinuumMobileCodingJob.evaluate(job, 0, 1, receipt()).outcome) // Termux error
        assertEquals(invalid, ContinuumMobileCodingJob.evaluate(job, null, -1, receipt()).outcome)
        assertEquals(invalid, ContinuumMobileCodingJob.evaluate(job, 0, -1, receipt(jobId = "3f2b8c1e-9a4d-4c2b-8e7f-000000000000")).outcome) // replayed foreign job
        assertEquals(invalid, ContinuumMobileCodingJob.evaluate(job, 0, -1, receipt(independent = 2)).outcome)
        assertEquals(invalid, ContinuumMobileCodingJob.evaluate(job, 0, -1, receipt(runs = "[]")).outcome)
        assertEquals(invalid, ContinuumMobileCodingJob.evaluate(job, 0, -1,
            receipt(extra = "\"approvedForMerge\":true,\"installed\":false,\"apk\":null,\"deviceVerified\":false")).outcome)
        assertEquals(invalid, ContinuumMobileCodingJob.evaluate(job, 0, -1, "not json").outcome)
    }

    @Test fun failuresAndBlocksAreShownHonestly() {
        val failed = ContinuumMobileCodingJob.evaluate(job, 2, -1,
            receipt(status = "FAILED_CODING_CHECKS", failure = "{\"code\":\"CONTRACT_CHECKS_FAILED\"}"))
        assertEquals(ContinuumMobileCodingJob.Outcome.CODING_FAILED, failed.outcome)
        assertEquals("CONTRACT_CHECKS_FAILED", failed.failureCode)
        assertEquals(ContinuumMobileCodingJob.Outcome.INVALID_RECEIPT,
            ContinuumMobileCodingJob.evaluate(job, 0, -1, receipt(status = "FAILED_CODING_CHECKS", failure = "{\"code\":\"X_Y\"}")).outcome)
        assertEquals(ContinuumMobileCodingJob.Outcome.BLOCKED,
            ContinuumMobileCodingJob.evaluate(job, 2, -1, receipt(status = "BLOCKED_STALE_BASE")).outcome)
    }

    /** Cross-runtime: a receipt written by the real Node job (CI/local) must pass the Kotlin gate. */
    @Test fun realNodeReceiptPassesWhenSupplied() {
        val file = System.getenv("CONTINUUM_MOBILE_CODING_RECEIPT") ?: return
        val text = File(file).readText()
        val jobId = Regex("\"job\":\\s*\"([0-9a-f-]{36})\"").find(text)!!.groupValues[1]
        val r = ContinuumMobileCodingJob.evaluate(jobId, 0, -1, text)
        assertEquals(ContinuumMobileCodingJob.Outcome.PATCH_VERIFIED_PENDING_REVIEW, r.outcome)
    }
}
