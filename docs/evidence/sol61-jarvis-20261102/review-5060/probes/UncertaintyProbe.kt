import at.heinz.deviceai.*
import at.heinz.deviceai.cognitive.*
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

fun main() {
  val root=Files.createTempDirectory("unknown-continuation-probe").toFile()
  try {
    val effects=AtomicInteger()
    val approvals=AtomicInteger()
    val store=InMemoryExperienceStore()
    var generation=0
    val model=AgentModel {_,_,_,observations ->
      generation++
      if(generation<=2) {
        if(observations.isNotEmpty()) println("MODEL_OBSERVATIONS_AFTER_UNKNOWN=$observations")
        AgentModelOutput("host", "", ToolProposal("stop_app", mapOf("packageOrName" to "com.example.app")))
      } else AgentModelOutput("host", "App successfully stopped.")
    }
    val run=BoundedAgentOrchestrator(model,{emptyList()},
      ToolGate(ToolPolicy(),ToolRunner { effects.incrementAndGet(); throw ToolBackendFailure(ToolExecutionStatus.FAILED,"effect may already have occurred") },
        receiptStore=FileToolExecutionReceiptStore(root)),
      AgentEvaluator {_,_->Evaluation(true,1.0)},store,
      approvalProvider=AgentToolApprovalProvider {_,_->approvals.incrementAndGet();AgentToolApproval(true)},
      limits=AgentLimits(maxSteps=32,maxToolCalls=2,maxRetries=0),callIdPrefix="ambiguous-task").run("Stop the example app once")
    println("UNKNOWN_AUTORETRY effects=${effects.get()} approvals=${approvals.get()} callIds=${run.taskEvidence!!.calls.map{it.callId}} flags=${run.taskEvidence!!.calls.map{it.outcomeUncertain}} blocker=${run.blocker} response=${run.response} experience=${run.experience!!.outcome} verified=${run.taskEvidence!!.outcomeVerified}")
    val unknown=run.experience!!
    val mixed=(1..4).map {i->unknown.copy(id="known-success-$i",outcome=ExperienceOutcome.SUCCESS,
      sourceIds=listOf("tool-call:known-$i:sha256:"+"a".repeat(64)),evaluation=Evaluation(true,1.0))}+unknown
    val candidate=ExperienceCompiler().compile(mixed).single()
    println("UNKNOWN_COMPILER_INCLUDED=${unknown.id in candidate.sourceExperienceIds} occurrenceCount=${candidate.occurrenceCount} successRate=${candidate.successRate} eligible=${candidate.eligibleForOwnerReview} sources=${candidate.sourceExperienceIds}")
  } finally {root.deleteRecursively()}
}
