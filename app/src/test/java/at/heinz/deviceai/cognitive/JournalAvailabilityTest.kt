package at.heinz.deviceai.cognitive
import at.heinz.deviceai.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.nio.channels.FileChannel
class JournalAvailabilityTest {
 private val proposal=ToolProposal("stop_app",mapOf("packageOrName" to "com.example.app"))
 @Test fun busyJournalDoesNotClaimCertainRejection() {
  val root=Files.createTempDirectory("continuum-journal-busy").toFile()
  try {
   val store=FileToolExecutionReceiptStore(root)
   val gate=ToolGate(ToolPolicy(),ToolRunner {error("blocked")},receiptStore=store)
   val call=proposal.invocation("busy")
   gate.recordProposal(call)
   FileChannel.open(root.toPath().resolve(".tool-receipts.lock"),StandardOpenOption.WRITE).use {channel->
    channel.lock().use {
     val result=gate.execute(call,userConfirmed=true)
     assertEquals(ToolExecutionStatus.REJECTED,result.record.status)
     assertTrue(result.record.outcomeUncertain)
    }
   }
  } finally {root.deleteRecursively()}
 }
 @Test fun replayRacingApprovalCannotCreateAFreshCallId() {
  val root=Files.createTempDirectory("continuum-success-replay-race").toFile()
  try {
   val store=FileToolExecutionReceiptStore(root);val experiences=InMemoryExperienceStore()
   var effects=0;var generations=0;var approvals=0
   val model=AgentModel {_,_,_,observations->
    generations++
    if(observations.size<2)AgentModelOutput("HOST_SIMULATED","",proposal)
    else AgentModelOutput("HOST_SIMULATED","done")
   }
   val result=BoundedAgentOrchestrator(model,{emptyList()},
    ToolGate(ToolPolicy(),ToolRunner {effects++;"fresh id"},receiptStore=store),
    AgentEvaluator {_,_->Evaluation(true,1.0)},experiences,callIdPrefix="replay-race",
    limits=AgentLimits(maxRetries=0),approvalProvider={_,_->
     approvals++
     if(approvals==1)ToolGate(ToolPolicy(),ToolRunner {effects++;"winner returned"},receiptStore=store)
      .execute(proposal.invocation("replay-race-1"),userConfirmed=true)
     AgentToolApproval(true)
    }).run("Authorized synthetic host task")
   assertEquals(1,effects);assertEquals(1,generations);assertNotNull(result.blocker)
   assertNull(result.experience);assertTrue(experiences.all().isEmpty())
  } finally {root.deleteRecursively()}
 }
}
