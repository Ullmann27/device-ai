package at.heinz.deviceai

import at.heinz.deviceai.ContinuumCcc1Bridge.Chain
import at.heinz.deviceai.ContinuumCcc1Bridge.Operation
import at.heinz.deviceai.ContinuumCcc1Bridge.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fixtures under resources/ccc1-bridge are real answers of `continuum-mobile-builder.mjs ccc1-*` from the Node
 * bridge test (ccc1-bridge.test.mjs keeps their shape in sync with the script).
 */
class ContinuumCcc1BridgeTest {
    private val order = "3f2b8c1e-9a4d-4c2b-8e7f-1a2b3c4d5e6f"
    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/ccc1-bridge/$name.json")) { "missing fixture $name" }.readText()
    private fun eval(op: Operation, name: String, exit: Int = 0) = ContinuumCcc1Bridge.evaluate(op, exit, -1, fixture(name))

    @Test fun commandsAreFixedArgvWithValidatedValues() {
        val submit = ContinuumCcc1Bridge.command(Operation.SUBMIT_REPAIR, Chain.NODE, orderId = order)
        assertEquals(ContinuumMobileBuildPlan.NODE_EXECUTABLE, submit.executable)
        assertEquals(ContinuumMobileBuildPlan.PROJECT_DIR, submit.workDir)
        assertEquals(listOf(ContinuumMobileBuildPlan.BUILDER_SCRIPT, "ccc1-submit-repair", "--project", ContinuumMobileBuildPlan.PROJECT_DIR,
            "--chain", "node", "--order", order), submit.args)
        assertEquals(listOf(ContinuumMobileBuildPlan.BUILDER_SCRIPT, "ccc1-run-once", "--project", ContinuumMobileBuildPlan.PROJECT_DIR),
            ContinuumCcc1Bridge.command(Operation.RUN_ONCE).args)
        assertEquals(listOf("--task", "t-0123456789abcdef"),
            ContinuumCcc1Bridge.command(Operation.ABORT, taskId = "t-0123456789abcdef").args.takeLast(2))
        for (bad in listOf<() -> Any>(
            { ContinuumCcc1Bridge.command(Operation.SUBMIT_REPAIR, Chain.NODE, orderId = "not-a-uuid") },
            { ContinuumCcc1Bridge.command(Operation.SUBMIT_REPAIR, null, orderId = order) },
            { ContinuumCcc1Bridge.command(Operation.ABORT, taskId = "t-1; rm -rf ~") },
            { ContinuumCcc1Bridge.command(Operation.COMPILE_REPAIR) }
        )) assertTrue(runCatching { bad() }.isFailure)
        assertEquals(Operation.ABORT, Operation.ofJobOperation("CCC1_ABORT"))
        assertNull(Operation.ofJobOperation("CODE_TEST"))
    }

    @Test fun realBridgeAnswersKeepTheirMeaning() {
        assertEquals("READY", eval(Operation.COMPILE_REPAIR, "compile-ready").status)
        val submitted = eval(Operation.SUBMIT_REPAIR, "submit-ok")
        assertEquals(Outcome.OK, submitted.outcome)
        assertEquals("SUBMITTED", submitted.status)
        assertTrue(Regex("t-[0-9a-f]{16}").matches(submitted.taskId ?: ""))
        val ran = eval(Operation.RUN_ONCE, "run-verified")
        assertEquals("VERIFIED_PATCH_PENDING_REVIEW", ran.status)
        assertNull("a run claim alone is not a verified patch", ran.verifiedPatchSha256)
        val status = eval(Operation.STATUS, "status-verified")
        assertEquals(64, status.verifiedPatchSha256?.length)
        assertTrue(status.message.contains("NICHT übernommen"))
        val tampered = eval(Operation.STATUS, "status-tampered")
        assertNull(tampered.verifiedPatchSha256)
        assertTrue(tampered.message.contains("NICHT als geprüft"))
        val ended = eval(Operation.ABORT, "abort-ended")
        assertEquals("ALREADY_ENDED", ended.status)
        assertNull("nothing left to cancel", ended.taskId)
        assertEquals("ABORTED", eval(Operation.ABORT, "abort-aborted").status)
        val blocked = eval(Operation.SUBMIT_REPAIR, "submit-blocked", exit = 2)
        assertEquals(Outcome.BLOCKED, blocked.outcome)
        assertTrue(blocked.message.contains("CCC1_BRANCH_NOT_ISOLATED"))
    }

    @Test fun termuxProblemsAreExplainedWithoutShowingTermuxText() {
        val oldCheckout = ContinuumCcc1Bridge.evaluate(Operation.STATUS, 2, -1, "",
            "Mobile Builder: Usage: doctor|build|status|code-test|code-repair|code-cancel [--project PATH] [--state-dir PATH]")
        assertEquals(Outcome.INVALID_RESULT, oldCheckout.outcome)
        assertTrue(oldCheckout.message.contains("älter als die App"))
        assertTrue("Termux text is never shown", !oldCheckout.message.contains("Usage"))
        val currentUsage = ContinuumCcc1Bridge.evaluate(Operation.STATUS, 2, -1, "",
            "Mobile Builder: Usage: doctor|build|status|code-test|code-repair|code-cancel|ccc1-compile-repair|ccc1-status")
        assertEquals("Keine verlässliche CCC1-Rückmeldung; nichts bestätigt.", currentUsage.message)
        assertTrue(ContinuumCcc1Bridge.evaluate(Operation.STATUS, null, 2, null, null).message.contains("konnte den Befehl nicht starten"))
        assertTrue(ContinuumCcc1Bridge.evaluate(Operation.RUN_ONCE, 1, -1, "",
            "Error: Cannot find module '/data/data/com.termux/files/home/device-ai/scripts/continuum-mobile-builder.mjs'")
            .message.contains("fehlt das Builder-Skript"))
        val blocked = eval(Operation.SUBMIT_REPAIR, "submit-blocked", exit = 2)
        assertTrue(blocked.message.contains("ccc1-prepare"))
        // a readable bridge answer is judged on its own; stderr never changes a result
        assertEquals("READY", ContinuumCcc1Bridge.evaluate(Operation.COMPILE_REPAIR, 0, -1, fixture("compile-ready"),
            "Mobile Builder: Usage: doctor").status)
    }

    @Test fun nothingUnverifiedOrForeignIsAccepted() {
        val ok = fixture("status-verified")
        val invalid = Outcome.INVALID_RESULT
        assertEquals(invalid, ContinuumCcc1Bridge.evaluate(Operation.STATUS, 0, 1, ok).outcome)           // Termux error
        assertEquals(invalid, ContinuumCcc1Bridge.evaluate(Operation.STATUS, null, -1, ok).outcome)       // no exit code
        assertEquals(invalid, ContinuumCcc1Bridge.evaluate(Operation.STATUS, 0, -1, null).outcome)        // truncated or lost
        assertEquals(invalid, ContinuumCcc1Bridge.evaluate(Operation.RUN_ONCE, 0, -1, ok).outcome)        // foreign operation
        assertEquals(invalid, ContinuumCcc1Bridge.evaluate(Operation.STATUS, 2, -1, ok).outcome)          // OK needs exit 0
        assertEquals(invalid, eval(Operation.SUBMIT_REPAIR, "submit-blocked", exit = 0).outcome)          // refusal needs exit != 0
        assertEquals(invalid, ContinuumCcc1Bridge.evaluate(Operation.STATUS, 0, -1, "log line\n$ok").outcome)
        assertEquals(invalid, ContinuumCcc1Bridge.evaluate(Operation.STATUS, 0, -1, ok + "x").outcome)
        assertEquals(invalid, ContinuumCcc1Bridge.evaluate(Operation.STATUS, 0, -1,
            " ".repeat(ContinuumCcc1Bridge.MAX_RESULT_CHARS) + ok).outcome)
        assertEquals(invalid, ContinuumCcc1Bridge.evaluate(Operation.STATUS, 0, -1,
            ok.replace(".github/continuum/text-utils.mjs", "../../etc/passwd")).outcome)
        assertEquals(invalid, ContinuumCcc1Bridge.evaluate(Operation.RUN_ONCE, 0, -1,
            fixture("run-verified").replace("\"COMPLETED\"", "\"ABSTAINED\"")).outcome)
        // a confirmation for another patch is never shown as verified
        val confirmed = Regex("repair:node@([0-9a-f]{12})").find(ok)!!.groupValues[1]
        val other = ContinuumCcc1Bridge.evaluate(Operation.STATUS, 0, -1, ok.replace("@$confirmed", "@000000000000"))
        assertEquals(Outcome.OK, other.outcome)
        assertNull(other.verifiedPatchSha256)
    }
}
