import at.heinz.deviceai.*
import at.heinz.deviceai.cognitive.*
import java.nio.file.Files

fun main() {
 val root=Files.createTempDirectory("jarvis-completed-replay-race").toFile()
 try {
   val durable=FileToolExecutionReceiptStore(root);val experiences=InMemoryExperienceStore()
   var generations=0;var effects=0;var approvals=0
   val proposal=ToolProposal("stop_app",mapOf("packageOrName" to "com.example.app"))
   val run=BoundedAgentOrchestrator(AgentModel {_,_,_,observations->
     generations++
     if(generations==2)println("OBSERVATIONS_AFTER_COMPLETED_REPLAY=$observations")
     if(generations<=2)AgentModelOutput("host","",proposal)
     else AgentModelOutput("host","App successfully stopped.")
   },{emptyList()},ToolGate(ToolPolicy(),ToolRunner{effects++;"next id returned"},receiptStore=durable),
     AgentEvaluator{_,_->Evaluation(true,1.0)},experiences,callIdPrefix="completed-task",
     limits=AgentLimits(maxToolCalls=2,maxRetries=0),approvalProvider={_,_->
       approvals++
       if(approvals==1) {
         val winner=ToolGate(ToolPolicy(),ToolRunner{effects++;"original call completed"},receiptStore=durable)
           .execute(proposal.invocation("completed-task-1"),userConfirmed=true)
         check(winner.record.status==ToolExecutionStatus.SUCCEEDED)
       }
       AgentToolApproval(true)
     }).run("Stop the example app once")
   println("COMPLETED_REPLAY_RACE effects=$effects generations=$generations approvals=$approvals blocker=${run.blocker} calls=${run.taskEvidence!!.calls} response=${run.response} stored=${experiences.all().size} outcome=${run.experience?.outcome}")
 } finally {root.deleteRecursively()}
}
