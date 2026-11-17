package at.heinz.deviceai.cognitive

import at.heinz.deviceai.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class UnknownOutcomeSafetyTest {
    private val proposal=ToolProposal("stop_app",mapOf("packageOrName" to "com.example.app"))

    @Test fun unknownImmediatelyBlocksFreshIdsInventedSuccessAndLearning() {
        val root=Files.createTempDirectory("continuum-unknown-stop").toFile()
        try {
            var effects=0;var modelCalls=0
            val experiences=InMemoryExperienceStore()
            val model=AgentModel {_,_,_,observations->
                modelCalls++
                if(observations.size<2)AgentModelOutput("HOST_SIMULATED","",proposal)
                else AgentModelOutput("HOST_SIMULATED","App successfully stopped.")
            }
            val result=BoundedAgentOrchestrator(model,{emptyList()},
                ToolGate(ToolPolicy(),ToolRunner {effects++;error("result lost after possible effect")},
                    receiptStore=FileToolExecutionReceiptStore(root)),
                AgentEvaluator {_,_->Evaluation(true,1.0)},experiences,
                approvalProvider={_,_->AgentToolApproval(true)},callIdPrefix="uncertain-task",
                limits=AgentLimits(maxRetries=0)).run("Prüfe diesen freigegebenen Host-Testauftrag.")
            assertEquals(1,effects)
            assertEquals(1,modelCalls)
            assertNotNull(result.blocker)
            assertTrue(result.response.contains("UNKNOWN"))
            assertFalse(result.response.contains("successfully stopped"))
            assertNull(result.experience)
            assertTrue(experiences.all().isEmpty())
            assertTrue(ExperienceCompiler().compile(experiences.all()).isEmpty())
            assertTrue(result.taskEvidence!!.calls.single().outcomeUncertain)
            assertNull(result.taskEvidence!!.learningCandidateExperienceId)
            assertEquals(CognitiveWorkState.BLOCKED,result.taskEvidence!!.events.last().state)
        } finally {root.deleteRecursively()}
    }

    @Test fun cancelledGatePreservesPreviouslyCommittedUncertainty() {
        val root=Files.createTempDirectory("continuum-cancelled-replay").toFile()
        try {
            val store=FileToolExecutionReceiptStore(root)
            val gate=ToolGate(ToolPolicy(),ToolRunner {error("must not run")},receiptStore=store)
            val request=proposal.invocation("committed")
            gate.recordProposal(request)
            store.transition(request.id,ToolReceiptState.PROPOSED,ToolReceiptState.APPROVED,Long.MAX_VALUE-3)
            store.transition(request.id,ToolReceiptState.APPROVED,ToolReceiptState.START_PENDING,Long.MAX_VALUE-2)
            store.transition(request.id,ToolReceiptState.START_PENDING,ToolReceiptState.DISPATCH_COMMITTED,Long.MAX_VALUE-1)
            gate.cancel()
            val result=gate.execute(request,userConfirmed=true)
            assertEquals(ToolExecutionStatus.CANCELLED,result.record.status)
            assertTrue(result.record.outcomeUncertain)
            assertTrue(result.record.decisionReason.contains("UNKNOWN"))
            assertEquals(ToolReceiptState.DISPATCH_COMMITTED,store.get(request.id)!!.state)
        } finally {root.deleteRecursively()}
    }

    @Test fun losingAReceiptTransitionCannotClaimADefinitelyUnrunCall() {
        val root=Files.createTempDirectory("continuum-receipt-conflict").toFile()
        try {
            val durable=FileToolExecutionReceiptStore(root)
            val raced=object: ToolExecutionReceiptStore by durable {
                override fun transition(callId:String,expected:ToolReceiptState,next:ToolReceiptState,
                    atMillis:Long,outputLength:Int,outputSha256:String?):ToolExecutionReceipt? {
                    if(next==ToolReceiptState.APPROVED) {
                        durable.transition(callId,expected,next,atMillis)
                        durable.transition(callId,next,ToolReceiptState.START_PENDING,atMillis)
                        durable.transition(callId,ToolReceiptState.START_PENDING,ToolReceiptState.DISPATCH_COMMITTED,atMillis)
                        return null // Another gate won; all journal I/O above is real.
                    }
                    return durable.transition(callId,expected,next,atMillis,outputLength,outputSha256)
                }
            }
            val result=ToolGate(ToolPolicy(),ToolRunner {error("loser must not run")},receiptStore=raced)
                .execute(proposal.invocation("race"),userConfirmed=true)
            assertEquals(ToolExecutionStatus.REJECTED,result.record.status)
            assertTrue(result.record.outcomeUncertain)
            assertEquals(ToolReceiptState.DISPATCH_COMMITTED,durable.get("race")!!.state)
        } finally {root.deleteRecursively()}
    }
}
