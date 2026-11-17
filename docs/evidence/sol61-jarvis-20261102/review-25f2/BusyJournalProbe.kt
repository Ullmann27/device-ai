import at.heinz.deviceai.*
import at.heinz.deviceai.cognitive.*
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

fun main() {
 val root=Files.createTempDirectory("jarvis-busy-journal").toFile()
 val worker=Executors.newSingleThreadExecutor()
 val release=CountDownLatch(1)
 var fileLock:FileLock?=null;var channel:FileChannel?=null
 try {
   val durable=FileToolExecutionReceiptStore(root)
   val entered=CountDownLatch(1);val effects=AtomicInteger();val experiences=InMemoryExperienceStore()
   var generations=0;var approvals=0
   val proposal=ToolProposal("stop_app",mapOf("packageOrName" to "com.example.app"))
   var winner:java.util.concurrent.Future<ToolExecutionResult>?=null
   val model=AgentModel {_,_,_,observations ->
     generations++
     if(generations==2) {
       println("OBSERVATIONS_AFTER_BUSY=$observations")
       fileLock!!.release();channel!!.close();fileLock=null;channel=null
     }
     if(generations<=2)AgentModelOutput("host","",proposal)
     else AgentModelOutput("host","App successfully stopped.")
   }
   val gate=ToolGate(ToolPolicy(),ToolRunner {effects.incrementAndGet();"new id dispatched"},receiptStore=durable)
   val run=BoundedAgentOrchestrator(model,{emptyList()},gate,AgentEvaluator{_,_->Evaluation(true,1.0)},experiences,
     callIdPrefix="busy-task",limits=AgentLimits(maxToolCalls=2,maxRetries=0),
     approvalProvider={_,_->
       approvals++
       if(approvals==1) {
         winner=worker.submit<ToolExecutionResult> {
           ToolGate(ToolPolicy(),ToolRunner {
             effects.incrementAndGet();entered.countDown()
             while(release.count>0)try{release.await()}catch(_:InterruptedException){}
             "original id returned"
           },receiptStore=durable).execute(proposal.invocation("busy-task-1"),userConfirmed=true)
         }
         check(entered.await(2,TimeUnit.SECONDS))
         channel=FileChannel.open(root.toPath().resolve(".tool-receipts.lock"),StandardOpenOption.WRITE)
         fileLock=channel!!.lock()
       }
       AgentToolApproval(true)
     }).run("Stop the example app once")
   println("BUSY_JOURNAL_RESULT effects=${effects.get()} generations=$generations approvals=$approvals blocker=${run.blocker} uncertainty=${run.taskEvidence!!.calls.map{it.outcomeUncertain}} diskFirst=${durable.get("busy-task-1")!!.state} response=${run.response} stored=${experiences.all().size} outcome=${run.experience?.outcome}")
   val unknown=run.experience
   if(unknown!=null) {
     val mixed=(1..4).map {i->unknown.copy(id="known-success-$i",outcome=ExperienceOutcome.SUCCESS,evaluation=Evaluation(true,1.0))}+unknown
     val candidate=ExperienceCompiler().compile(mixed).single()
     println("BUSY_UNKNOWN_IN_SKILL=${unknown.id in candidate.sourceExperienceIds} successRate=${candidate.successRate}")
   }
   release.countDown();winner?.get(2,TimeUnit.SECONDS)
 } finally {fileLock?.release();channel?.close();release.countDown();worker.shutdownNow();root.deleteRecursively()}
}
