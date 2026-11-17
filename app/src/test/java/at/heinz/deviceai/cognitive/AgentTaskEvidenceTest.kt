package at.heinz.deviceai.cognitive

import at.heinz.deviceai.*
import java.nio.file.Files
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class AgentTaskEvidenceTest {
    @Test fun actualReadOnlyPlanHasTaskBoundEventsReceiptPostconditionAndReviewOnlyCandidate() {
        val dir = Files.createTempDirectory("sovereign-real-observation").toFile()
        try {
            val store = InMemoryExperienceStore()
            val receipts = FileToolExecutionReceiptStore(File(dir,"receipts"))
            val reports = mutableListOf<AgentTaskEvidence>()
            repeat(3) { index ->
                val namespace = "authorized-storage-$index"
                val request = "Continuum, prüfe bitte den freien Speicher."
                val decision = BootstrapBrain.decide(request, emptyList())
                assertEquals("get_storage_info", decision.tool)
                val plan = ReadOnlyToolPlan.forRequest(request, ToolProposal(decision.tool!!, decision.arguments))
                val delivered = mutableListOf<AgentCognitiveEvent>()
                var actualOutput = ""
                var runs = 0
                var executionsWhenStarted = -1
                val trace = CognitiveTurnTrace("storage-session-$index")
                val started = System.currentTimeMillis()
                val result = BoundedAgentOrchestrator(plan, { emptyList() },
                    ToolGate(ToolPolicy(readOnlyEnabled=true), ToolRunner {
                        runs++; DeviceStorageProbe.read(dir).also { actualOutput=it }
                    }, receiptStore=receipts), plan, store, callIdPrefix=namespace,
                    turnTrace = trace,
                    eventObserver = { event ->
                        // EXECUTING must precede the actual backend result, not be fabricated afterward.
                        if(event.state==CognitiveWorkState.EXECUTING) executionsWhenStarted = runs
                        delivered.add(event)
                    }, limits=AgentLimits(maxToolCalls=1,maxRetries=0)).run(request)
                val ended = System.currentTimeMillis()
                val proof = result.taskEvidence!!
                reports += proof
                assertEquals(namespace,proof.taskId)
                assertEquals(sha256Hex(request),proof.requestSha256)
                assertEquals(listOf("get_storage_info"),proof.plannedToolNames)
                assertEquals(1,runs)
                assertEquals(0,executionsWhenStarted)
                assertEquals(delivered,proof.events)
                assertEquals(listOf(CognitiveWorkState.UNDERSTANDING,CognitiveWorkState.PLANNING,
                    CognitiveWorkState.EXECUTING,CognitiveWorkState.VERIFYING,CognitiveWorkState.COMPLETED),
                    proof.events.map { it.state })
                assertTrue(proof.events.all { it.taskId==namespace && it.attempt==0 })
                val call = proof.calls.single()
                assertFalse(call.approvalRequested) // existing read-only policy, not a model-granted approval
                assertNull(call.ownerApproved)
                assertEquals("$namespace-1",call.callId)
                assertTrue(call.freshOutput)
                val receipt = FileToolExecutionReceiptStore(File(dir,"receipts")).get(call.callId)!!
                assertEquals(ToolReceiptState.SUCCEEDED,receipt.state)
                assertEquals(receipt.outputSha256,call.outputSha256)
                assertEquals(sha256Hex(actualOutput),call.outputSha256)
                val total = Regex("Gesamt: (\\d+) Byte").find(actualOutput)!!.groupValues[1].toLong()
                val oracle=Files.getFileStore(dir.toPath())
                assertEquals(oracle.totalSpace,total)
                val available=Regex("Für die App nutzbar: (\\d+) Byte").find(actualOutput)!!.groupValues[1].toLong()
                val measuredAt=Regex("Zeitpunkt: (\\d+)").find(actualOutput)!!.groupValues[1].toLong()
                assertTrue(available in 0..total)
                assertTrue(measuredAt in started..ended)
                assertEquals(CognitiveWorkState.COMPLETED,trace.finish(TraceOutcome.ACTION_VERIFIED).workState)
                System.getProperty("continuum.sovereign.evidenceDir")?.let { output ->
                    File(output).mkdirs()
                    File(output,"storage-$index.txt").writeText(actualOutput+"\n"+receipt+"\n"+
                        "independentFileStoreTotal=${oracle.totalSpace}\ntimeBounds=$started..$ended\n")
                }
                assertTrue(proof.outcomeVerified)
                assertEquals(result.experience!!.id,proof.learningCandidateExperienceId)
                assertFalse(proof.toString().contains(actualOutput))
                assertTrue(result.response.contains(actualOutput))
            }
            assertEquals(3,reports.map { it.learningCandidateExperienceId }.distinct().size)
            val candidate=ExperienceCompiler().compile(store.all()).single()
            assertTrue(candidate.eligibleForOwnerReview)
            assertTrue(candidate.requiresOwnerApproval)
            assertEquals(3,candidate.occurrenceCount)
            System.getProperty("continuum.sovereign.evidenceDir")?.let { dest ->
                File(dest).mkdirs()
                File(dest,"actual-task-proof.txt").writeText(reports.joinToString("\n")+"\n"+candidate+"\n")
            }
        } finally {dir.deleteRecursively()}
    }

    @Test fun replayPolicyDenialAndFormOnlyEvaluationCannotEmitCompleted() {
        val dir = Files.createTempDirectory("sovereign-negative-proof").toFile()
        try {
            val receipts=FileToolExecutionReceiptStore(dir)
            fun run(enabled:Boolean,id:String,outcomeScope:Boolean=true):AgentRunResult {
                val plan=ReadOnlyToolPlan(ToolProposal("get_storage_info",emptyMap()))
                return BoundedAgentOrchestrator(plan,{emptyList()},ToolGate(ToolPolicy(readOnlyEnabled=enabled),
                    ToolRunner { DeviceStorageProbe.read(dir) },receiptStore=receipts),plan,InMemoryExperienceStore(),
                    callIdPrefix=id,evaluationConfirmsOutcome=outcomeScope,limits=AgentLimits(maxRetries=0)).run("Prüfe Speicher")
            }
            val success=run(true,"repeat")
            assertTrue(success.taskEvidence!!.outcomeVerified)
            val replay=run(true,"repeat")
            val denied=run(false,"denied")
            val formOnly=run(true,"form-only",false)
            for(result in listOf(replay,denied,formOnly)) {
                val proof=result.taskEvidence!!
                assertFalse(proof.outcomeVerified)
                assertNull(proof.learningCandidateExperienceId)
                assertFalse(proof.events.any {it.state==CognitiveWorkState.COMPLETED})
                assertEquals(CognitiveWorkState.BLOCKED,proof.events.last().state)
            }
            assertFalse(replay.taskEvidence!!.calls.single().freshOutput)
            assertFalse(denied.taskEvidence!!.events.any {it.state==CognitiveWorkState.EXECUTING})
        } finally {dir.deleteRecursively()}
    }

    @Test fun explicitApprovalIsASeparateEventAndObserverFailureCannotGrantAuthority() {
        var executions=0
        val proposal=ToolProposal("stop_app",mapOf("packageOrName" to "com.example.app"))
        val result=BoundedAgentOrchestrator(AgentModel {_,_,_,observations ->
            if(observations.isEmpty())AgentModelOutput("model","",proposal)
            else AgentModelOutput("model","No success is established.")
        },{emptyList()},ToolGate(ToolPolicy(),ToolRunner {executions++;"not allowed"}),
            AgentEvaluator {_,_->Evaluation(true,1.0)},InMemoryExperienceStore(),
            approvalProvider={_,_->AgentToolApproval(false)},eventObserver={throw IllegalStateException("HUD failed")},
            limits=AgentLimits(maxRetries=0)).run("Vorschlag zur Besitzerprüfung")
        assertEquals(0,executions)
        val proof=result.taskEvidence!!
        assertTrue(proof.calls.single().approvalRequested)
        assertEquals(false,proof.calls.single().ownerApproved)
        assertTrue(proof.events.any {it.state==CognitiveWorkState.WAITING_FOR_APPROVAL})
        assertFalse(proof.outcomeVerified)
        assertFalse(proof.events.any {it.state==CognitiveWorkState.COMPLETED})
    }
    @Test fun cancellationIsObservedWithoutACompletionOrLearningCandidate() {
        val events=mutableListOf<AgentCognitiveEvent>()
        val episodes=InMemoryExperienceStore()
        val cancellation=DeliberationCancellation()
        val plan=ReadOnlyToolPlan(ToolProposal("get_storage_info",emptyMap()))
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            BoundedAgentOrchestrator(plan,{emptyList()},ToolGate(ToolPolicy(readOnlyEnabled=true),
                ToolRunner {cancellation.cancel();"cancelled measurement"}),plan,episodes,
                cancellation=cancellation,eventObserver={events.add(it)}).run("Prüfe Speicher")
        }
        assertTrue(episodes.all().isEmpty())
        assertFalse(events.any {it.state==CognitiveWorkState.COMPLETED})
        assertEquals(CognitiveWorkState.BLOCKED,events.last().state)
    }

    @Test fun terminalTraceCancellationOverridesAPreviousExecutionPhase() {
        val trace=CognitiveTurnTrace("test-session")
        trace.event(TraceStage.TOOL_EXECUTION_STARTED)
        assertEquals(CognitiveWorkState.BLOCKED,trace.finish(TraceOutcome.CANCELLED).workState)
    }

    @Test fun timeoutNeverBecomesAVerifiedPostcondition() {
        val plan=ReadOnlyToolPlan(ToolProposal("get_storage_info",emptyMap()))
        val result=BoundedAgentOrchestrator(plan,{emptyList()},ToolGate(
            ToolPolicy(readOnlyEnabled=true,timeoutMillis=10),ToolRunner {Thread.sleep(1000);"too late"}),
            plan,InMemoryExperienceStore(),limits=AgentLimits(maxRetries=0)).run("Prüfe Speicher")
        val proof=result.taskEvidence!!
        assertEquals(ToolExecutionStatus.TIMED_OUT,proof.calls.single().status)
        assertFalse(proof.outcomeVerified)
        assertFalse(proof.calls.single().freshOutput)
        assertEquals(CognitiveWorkState.BLOCKED,proof.events.last().state)
    }

}
