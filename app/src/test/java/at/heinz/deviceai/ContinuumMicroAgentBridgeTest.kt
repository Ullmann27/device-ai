package at.heinz.deviceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuumMicroAgentBridgeTest {
    private val roles = listOf(
        "gemma" to "GEMMA_IMPORT_AUDITOR",
        "deepseek" to "DEEPSEEK_PROTOCOL_AUDITOR",
        "coding" to "CCC1_CODING_AUDITOR",
        "transfer" to "EXPERIENCE_TRANSFER_AUDITOR",
        "update" to "FOLD7_UPDATE_AUDITOR"
    )

    private fun report(
        states:List<String> = List(5) { "CONTRACT_TESTS_PASS" },
        source:String = "a".repeat(40),
        modelConnected:Boolean = false,
        codeGeneration:Boolean = false,
        completed:Boolean = states.size == 5 && states.all { it == "CONTRACT_TESTS_PASS" }
    ):String {
        val rows=states.mapIndexed { index,state ->
            val (id,role)=roles[index]
            val exit=if(state=="CONTRACT_TESTS_PASS") "0" else if(state=="INTERRUPTED") "null" else "1"
            """{"id":"$id","role":"$role","status":"$state","durationMs":13,"exitCode":$exit,"outputSha256":"${"b".repeat(64)}","executedNativeModelInference":false,"verifiedCodingRepair":false,"androidDeviceTestsExecuted":false}"""
        }.joinToString(",")
        return """{"schema":"continuum-local-microagents","version":1,"mode":"DETERMINISTIC_OFFLINE_CONTRACT_CHECKS","deepSeekCodingConnected":$modelConnected,"gemmaNativeInferenceVerified":false,"noApiCostRequired":true,"codeGenerationAvailable":$codeGeneration,"ownerPrivilegesGranted":false,"completed":$completed,"results":[$rows],"sourceCommit":"$source","branch":"research/continuum-local-microagents-20261028"}"""
    }

    @Test fun commandRunsExactFixedAuditorAtFold7CandidatePath() {
        val cmd=ContinuumMicroAgentBridge.command()
        assertEquals(ContinuumMobileBuildPlan.NODE_EXECUTABLE,cmd.executable)
        assertEquals("/data/data/com.termux/files/home/device-ai-fold7-candidate",cmd.workDir)
        assertEquals(listOf(
            cmd.workDir+"/scripts/continuum-microagents.mjs","--run","all"
        ),cmd.args)
        assertFalse(cmd.args.any { it.contains(";") || it.contains("&&") || it.contains("\n") })
    }

    @Test fun fiveValidOfflineContractResultsCanBeReportedWithoutModelClaim() {
        val r=ContinuumMicroAgentBridge.evaluate(0,-1,report())
        assertTrue(r.passed)
        assertTrue(r.summary.contains("5/5"))
        assertTrue(r.summary.contains("keine Modell-Inferenz"))
        assertFalse(r.summary.contains("Patch übernommen"))
    }

    @Test fun failedThirdRoleIsShownButNeverCertified() {
        val r=ContinuumMicroAgentBridge.evaluate(2,-1,report(
            listOf("CONTRACT_TESTS_PASS","CONTRACT_TESTS_PASS","CONTRACT_TESTS_FAIL")
        ))
        assertFalse(r.passed)
        assertTrue(r.summary.contains("coding"))
        assertTrue(r.summary.contains("Kein Modell"))
    }

    @Test fun missingOrTruncatedTermuxCallbackIsNotAValidProof() {
        for (s in listOf(null,"","{",report().take(100))) {
            assertFalse(ContinuumMicroAgentBridge.evaluate(0,-1,s).passed)
        }
        assertFalse(ContinuumMicroAgentBridge.evaluate(0,0,report()).passed)
        assertFalse(ContinuumMicroAgentBridge.evaluate(null,-1,report()).passed)
    }

    @Test fun fakeDeepSeekConnectionOrCodeGenerationIsRejected() {
        assertFalse(ContinuumMicroAgentBridge.evaluate(0,-1,
            report(modelConnected=true)).passed)
        assertFalse(ContinuumMicroAgentBridge.evaluate(0,-1,
            report(codeGeneration=true)).passed)
    }

    @Test fun cannotReportPartialExecutionAsComplete() {
        assertFalse(ContinuumMicroAgentBridge.evaluate(0,-1,
            report(states=listOf("CONTRACT_TESTS_PASS"),completed=true)).passed)
        assertFalse(ContinuumMicroAgentBridge.evaluate(0,-1,
            report(completed=false)).passed)
    }

    @Test fun arbitraryShaCannotBePresentedAsAuditedSource() {
        assertFalse(ContinuumMicroAgentBridge.evaluate(0,-1,
            report(source="../other-branch")).passed)
    }

    @Test fun failClosedOnUnsupportedStatus() {
        assertFalse(ContinuumMicroAgentBridge.evaluate(2,-1,
            report(states=listOf("CONTRACT_TESTS_PASS","MODEL_EXECUTED"))).passed)
    }
}
