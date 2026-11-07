import at.heinz.deviceai.*
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors

fun main() {
 val root=Files.createTempDirectory("jarvis-receipt-snapshot-race").toFile()
 val workers=Executors.newFixedThreadPool(2)
 val release=CountDownLatch(1)
 try {
   val snapshot=CountDownLatch(1);val dispatched=CountDownLatch(1)
   val durable=FileToolExecutionReceiptStore(root)
   val staleSnapshotStore=object:ToolExecutionReceiptStore by durable {
     override fun propose(receipt:ToolExecutionReceipt):ToolExecutionReceipt {
       val result=durable.propose(receipt)
       snapshot.countDown()
       check(dispatched.await(2,TimeUnit.SECONDS))
       return result
     }
   }
   val request=ToolProposal("stop_app",mapOf("packageOrName" to "com.example.app")).invocation("same-call")
   val loser=workers.submit<ToolExecutionResult> {
     ToolGate(ToolPolicy(),ToolRunner {error("loser must never dispatch")},receiptStore=staleSnapshotStore)
       .execute(request,userConfirmed=true)
   }
   check(snapshot.await(2,TimeUnit.SECONDS))
   val winner=workers.submit<ToolExecutionResult> {
     ToolGate(ToolPolicy(),ToolRunner {
       dispatched.countDown()
       while(release.count>0)try {release.await()}catch(_:InterruptedException){}
       "original call finished"
     },receiptStore=durable).execute(request,userConfirmed=true)
   }
   val result=loser.get(2,TimeUnit.SECONDS)
   println("STALE_SNAPSHOT_RESULT status=${result.record.status} uncertain=${result.record.outcomeUncertain} disk=${durable.get(request.id)!!.state} reason=${result.record.decisionReason}")
   val cancelled=ToolGate(ToolPolicy(),ToolRunner{error("must not dispatch")},receiptStore=durable)
   cancelled.cancel()
   val early=cancelled.execute(request,userConfirmed=true)
   println("CANCELLED_GATE_RESULT status=${early.record.status} uncertain=${early.record.outcomeUncertain} disk=${durable.get(request.id)!!.state} reason=${early.record.decisionReason}")
   release.countDown()
   winner.get(2,TimeUnit.SECONDS)
 } finally {release.countDown();workers.shutdownNow();root.deleteRecursively()}
}
