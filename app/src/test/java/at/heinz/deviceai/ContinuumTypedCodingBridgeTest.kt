package at.heinz.deviceai

import org.junit.Assert.*
import org.junit.Test
import at.heinz.deviceai.cognitive.DevelopmentBacklog

class ContinuumTypedCodingBridgeTest {
    private fun task()=ContinuumCodingTask(
        id="job-typed-android-bridge",sourceCommit="a".repeat(40),
        kind=ContinuumCodingTask.Kind.BUGFIX,
        goal="Repariere eine reine JavaScript-Funktion",
        allowedPaths=listOf("lib/distance.mjs"),
        acceptance=listOf("Negativen Abstand korrekt berechnen"),
        chain=ContinuumCodingTask.Chain.NODE,
        limits=ContinuumCodingTask.Limits(2,8192)
    )
    private fun auth()=ContinuumCodingTask.Authorization(task(),task().fingerprint())
    private fun report(operation:String,status:String,extra:String="",id:String=task().id,hash:String=task().fingerprint())=
        """{"schema":"continuum-typed-coding-bridge","version":1,"operation":"$operation","id":"$id","status":"$status","taskSha256":"$hash","approvedForMerge":false,"installed":false,"deviceVerified":false,"sourceCommit":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"$extra}"""

    private fun evaluateBound(
        operation: ContinuumTypedCodingBridge.Operation,
        id: String, hash: String, exitCode: Int?, termuxError: Int?, stdout: String?
    ): ContinuumTypedCodingBridge.Result =
        ContinuumTypedCodingBridge.evaluate(
            operation, id, hash, exitCode, termuxError, stdout, expectedSourceCommit = task().sourceCommit
        )

    @Test fun statusIsBoundedReadOnlyAndRequiresRealPhaseData() {
        val a=auth()
        val command=ContinuumTypedCodingBridge.commandStatus(a.task.id,a.taskSha256)
        assertEquals("status",command.args[1])
        assertEquals(ContinuumMobileBuildPlan.NODE_EXECUTABLE,command.executable)
        val extra=""","phase":"CHECK_FAILED","observationOnly":true,"reviewReady":false,"patchSha256":null,"progress":[{"phase":"CHECK_FAILED","at":"2026-10-27T14:20:00.000Z","attempt":1}]"""
        val payload=report("status","PROGRESS_REPORTED",extra)
        val result=evaluateBound(ContinuumTypedCodingBridge.Operation.STATUS,a.task.id,a.taskSha256,0,-1,payload)
        assertEquals("PROGRESS_REPORTED",result.status)
        assertTrue(result.message.contains("Prüfung fehlgeschlagen"))
        assertTrue(result.message.contains("kein Live-Heartbeat"))
        assertNull(result.patchSha256)
        for(bad in listOf(payload.replace("CHECK_FAILED","FAKE_SUCCESS"),payload.replace("\"observationOnly\":true","\"observationOnly\":false"),
            payload.replace("\"attempt\":1","\"attempt\":99"),payload.replace("\"reviewReady\":false","\"reviewReady\":true")))
            assertEquals("INVALID",evaluateBound(ContinuumTypedCodingBridge.Operation.STATUS,a.task.id,a.taskSha256,0,-1,bad).status)
        assertEquals("INVALID",evaluateBound(ContinuumTypedCodingBridge.Operation.RUN,a.task.id,a.taskSha256,0,-1,payload).status)
    }

    @Test fun foreignGitCommitCannotBecomeApprovedCodingEvidence() {
        val id = task().id
        val hash = task().fingerprint()
        val prepared = report("prepare", "PREPARED_NOT_EXECUTED",
            ""","ccc1TaskId":"t-aaaaaaaaaaaaaaaa","replayed":false""")
        val verified = report("run", "PATCH_PENDING_OWNER_REVIEW",
            ""","resultStatus":"COMPLETED","patchSha256":"${"c".repeat(64)}"""")
        for (payload in listOf(prepared, verified)) {
            val mismatched = payload.replace(task().sourceCommit, "b".repeat(40))
            val operation = if (payload == prepared) ContinuumTypedCodingBridge.Operation.PREPARE
                else ContinuumTypedCodingBridge.Operation.RUN
            val rejected = evaluateBound(operation, id, hash, 0, -1, mismatched)
            assertEquals("INVALID", rejected.status)
            assertFalse(rejected.prepared)
            assertNull(rejected.patchSha256)
            assertEquals("INVALID", ContinuumTypedCodingBridge.evaluate(
                operation, id, hash, 0, -1, payload, expectedSourceCommit = "b".repeat(40)).status)
            assertEquals("INVALID", ContinuumTypedCodingBridge.evaluate(
                operation, id, hash, 0, -1, payload, expectedSourceCommit = "not-a-commit").status)
        }
    }

    @Test fun canonicalTaskIsRequiredAndCanNeverBeReboundToChangedFields() {
        val a=auth()
        assertNotNull(ContinuumTypedCodingBridge.parseAuthorization(a.task.canonicalJson(),a.taskSha256))
        assertNull(ContinuumTypedCodingBridge.parseAuthorization(a.task.canonicalJson(),"b".repeat(64)))
        assertNull(ContinuumTypedCodingBridge.parseAuthorization(a.task.canonicalJson().replace("BUGFIX","FEATURE"),a.taskSha256))
        assertNull(ContinuumTypedCodingBridge.parseAuthorization(a.task.canonicalJson()+" ",a.taskSha256))
        assertNull(ContinuumTypedCodingBridge.parseAuthorization(
            """{"schema":"continuum-coding-task","schema":"different"}""",a.taskSha256))
        assertEquals(a.task.canonicalJson(),DevelopmentBacklog.canonical(DevelopmentBacklog.parseStrict(a.task.canonicalJson())))
    }
    @Test fun approvedCommandCannotBecomeShellTextOrSecondApp() {
        val a=auth()
        val command=ContinuumTypedCodingBridge.commandPrepare(a)
        assertEquals(ContinuumMobileBuildPlan.NODE_EXECUTABLE,command.executable)
        assertEquals(ContinuumMobileBuildPlan.PROJECT_DIR,command.workDir)
        assertTrue(command.args[0].endsWith("/scripts/continuum-typed-coding-bridge.mjs"))
        assertEquals("prepare",command.args[1])
        assertEquals(a.taskSha256,command.args[3])
        assertFalse(command.args.any { it == "sh" || it=="-c" || it=="main" })
        val run=ContinuumTypedCodingBridge.commandRun(a.task.id,a.taskSha256)
        assertEquals(listOf(command.args[0],"run",a.task.id,a.taskSha256),run.args)
    }
    @Test fun preparationRequiresUntruncatedCommittedTermuxCallbackAndOriginalHash() {
        val id=task().id;val hash=task().fingerprint()
        val payload=report("prepare","PREPARED_NOT_EXECUTED", ""","ccc1TaskId":"t-aaaaaaaaaaaaaaaa","replayed":false""")
        val verified=evaluateBound(ContinuumTypedCodingBridge.Operation.PREPARE,id,hash,0,-1,payload)
        assertTrue(verified.prepared)
        assertEquals("PREPARED_NOT_EXECUTED",verified.status)
        assertFalse(evaluateBound(ContinuumTypedCodingBridge.Operation.PREPARE,id,hash,0,1,payload).prepared)
        assertFalse(evaluateBound(ContinuumTypedCodingBridge.Operation.PREPARE,id,"b".repeat(64),0,-1,payload).prepared)
        assertFalse(evaluateBound(ContinuumTypedCodingBridge.Operation.PREPARE,id,hash,0,-1,payload.dropLast(7)).prepared)
        assertFalse(evaluateBound(ContinuumTypedCodingBridge.Operation.RUN,id,hash,0,-1,payload).prepared)
    }
    @Test fun runReceiptIsNotInstallationAndNeverPromotesUnverifiedEvidence() {
        val a=auth();val id=a.task.id;val hash=a.taskSha256
        val unverified=report("run","EXECUTION_REPORTED_UNVERIFIED", ""","resultStatus":"ABSTAINED","patchSha256":null""")
        val v=evaluateBound(ContinuumTypedCodingBridge.Operation.RUN,id,hash,0,-1,unverified)
        assertEquals("EXECUTION_REPORTED_UNVERIFIED",v.status)
        assertNull(v.patchSha256)
        val goodPatch="c".repeat(64)
        val verified=report("run","PATCH_PENDING_OWNER_REVIEW", ""","resultStatus":"COMPLETED","patchSha256":"$goodPatch"""")
        val receipt=evaluateBound(ContinuumTypedCodingBridge.Operation.RUN,id,hash,0,-1,verified)
        assertEquals(goodPatch,receipt.patchSha256)
        assertEquals("INVALID",evaluateBound(ContinuumTypedCodingBridge.Operation.RUN,id,hash,0,-1,
            verified.replace("\"installed\":false","\"installed\":true")).status)
        assertEquals("INVALID",evaluateBound(ContinuumTypedCodingBridge.Operation.RUN,id,hash,0,-1,
            verified.replace("\"patchSha256\":\"$goodPatch\"","\"patchSha256\":null")).status)
    }
    @Test fun blockedFailureIsNotAConfirmedPatch() {
        val id=task().id;val hash=task().fingerprint()
        val r=report("run","BLOCKED", ""","failure":{"code":"CODING_TASK_STALE_SOURCE"}""")
        val blocked=evaluateBound(ContinuumTypedCodingBridge.Operation.RUN,id,hash,2,-1,r)
        assertEquals("BLOCKED",blocked.status)
        assertNull(blocked.patchSha256)
        assertEquals("INVALID",evaluateBound(ContinuumTypedCodingBridge.Operation.RUN,id,hash,0,-1,r).status)
    }
}
