package at.heinz.deviceai

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class ToolDurableOutcomeTest {
    private fun invocation(id: String)=ToolProposal("stop_app",mapOf("packageOrName" to "com.example.app")).invocation(id)

    @Test fun dispatchCommitIsDurableBeforeTheBackendIsEntered() {
        val dir=Files.createTempDirectory("continuum-durable-commit").toFile()
        try {
            val store=FileToolExecutionReceiptStore(dir)
            val result=ToolGate(ToolPolicy(),ToolRunner {
                assertEquals("DISPATCH_COMMITTED",FileToolExecutionReceiptStore(dir).get(it.callId)!!.state.name)
                "backend returned"
            },receiptStore=store).execute(invocation("durable-commit"),userConfirmed=true)
            assertEquals(ToolExecutionStatus.SUCCEEDED,result.record.status)
        } finally {dir.deleteRecursively()}
    }

    @Test fun postDispatchTimeoutAndLateResultRemainUnknownAcrossRestart() {
        val dir=Files.createTempDirectory("continuum-durable-timeout").toFile()
        val entered=CountDownLatch(1);val release=CountDownLatch(1);val left=CountDownLatch(1)
        val calls=AtomicInteger()
        try {
            val request=invocation("timeout")
            val result=ToolGate(ToolPolicy(timeoutMillis=100),ToolRunner {
                calls.incrementAndGet();entered.countDown()
                try {while(release.count>0)try {release.await()} catch(_:InterruptedException) {}}
                finally {left.countDown()}
                "late side effect result"
            },receiptStore=FileToolExecutionReceiptStore(dir)).execute(request,userConfirmed=true)
            assertTrue(entered.await(2,TimeUnit.SECONDS))
            assertEquals(ToolExecutionStatus.TIMED_OUT,result.record.status)
            assertTrue(result.record.decisionReason.contains("UNKNOWN"))
            release.countDown();assertTrue(left.await(2,TimeUnit.SECONDS))
            val restarted=FileToolExecutionReceiptStore(dir)
            assertEquals("UNKNOWN_AFTER_DISPATCH",restarted.get(request.id)!!.state.name)
            val replay=ToolGate(ToolPolicy(),ToolRunner {calls.incrementAndGet();"must not run"},
                receiptStore=restarted).execute(request,userConfirmed=true)
            assertEquals(ToolExecutionStatus.REJECTED,replay.record.status)
            assertTrue(replay.record.decisionReason.contains("UNKNOWN"))
            assertEquals(1,calls.get())
            assertNull(replay.output)
        } finally {release.countDown();dir.deleteRecursively()}
    }

    @Test fun failureAfterPossibleSideEffectsNeverBecomesADefiniteRollback() {
        val dir=Files.createTempDirectory("continuum-durable-failure").toFile()
        try {
            val result=ToolGate(ToolPolicy(),ToolRunner {error("failed after the OS may have acted")},
                receiptStore=FileToolExecutionReceiptStore(dir)).execute(invocation("failure"),userConfirmed=true)
            assertEquals(ToolExecutionStatus.FAILED,result.record.status)
            assertTrue(result.record.decisionReason.contains("UNKNOWN"))
            assertEquals("UNKNOWN_AFTER_DISPATCH",FileToolExecutionReceiptStore(dir).get("failure")!!.state.name)
        } finally {dir.deleteRecursively()}
    }

    @Test fun cancellationBeforeDispatchNeverCommitsOrRuns() {
        val dir=Files.createTempDirectory("continuum-durable-cancel").toFile()
        try {
            var calls=0
            val gate=ToolGate(ToolPolicy(),ToolRunner {calls++;"forbidden"},receiptStore=FileToolExecutionReceiptStore(dir))
            val result=gate.execute(invocation("cancelled"),userConfirmed=true,onExecutionStarted={gate.cancel()})
            assertEquals(ToolExecutionStatus.CANCELLED,result.record.status)
            assertEquals(0,calls)
            assertEquals(ToolReceiptState.CANCELLED,FileToolExecutionReceiptStore(dir).get("cancelled")!!.state)
        } finally {dir.deleteRecursively()}
    }

    @Test fun separateStoreInstancesCannotLoseParallelReceipts() {
        val dir=Files.createTempDirectory("continuum-parallel-receipts").toFile()
        val workers=Executors.newFixedThreadPool(8)
        try {
            val ready=CountDownLatch(1)
            val jobs=(1..32).map { id->workers.submit {
                ready.await()
                ToolGate(ToolPolicy(readOnlyEnabled=true),ToolRunner {"real host value $id"},
                    receiptStore=FileToolExecutionReceiptStore(dir))
                    .execute(ToolProposal("get_device_info",emptyMap()).invocation("parallel-$id"))
            }}
            ready.countDown();jobs.forEach {it.get(10,TimeUnit.SECONDS)}
            assertEquals(32,FileToolExecutionReceiptStore(dir).all().size)
            assertTrue(FileToolExecutionReceiptStore(dir).all().all {it.state==ToolReceiptState.SUCCEEDED})
        } finally {workers.shutdownNow();dir.deleteRecursively()}
    }

    @Test fun failedDispatchPersistenceBlocksTheBackendAndReplay() {
        val dir=Files.createTempDirectory("continuum-failed-commit").toFile()
        try {
            val durable=FileToolExecutionReceiptStore(dir)
            val failing=object: ToolExecutionReceiptStore by durable {
                override fun transition(callId: String,expected: ToolReceiptState,next: ToolReceiptState,
                    atMillis: Long,outputLength: Int,outputSha256: String?): ToolExecutionReceipt? {
                    if(next.name=="DISPATCH_COMMITTED")throw IOException("deliberate unavailable persistence")
                    return durable.transition(callId,expected,next,atMillis,outputLength,outputSha256)
                }
            }
            var calls=0
            val result=ToolGate(ToolPolicy(),ToolRunner {calls++;"forbidden"},receiptStore=failing)
                .execute(invocation("failed-commit"),userConfirmed=true)
            assertEquals(ToolExecutionStatus.FAILED,result.record.status)
            assertTrue(result.record.outcomeUncertain)
            assertEquals(0,calls)
            val replay=ToolGate(ToolPolicy(),ToolRunner {calls++;"forbidden"},receiptStore=durable)
                .execute(invocation("failed-commit"),userConfirmed=true)
            assertTrue(replay.record.outcomeUncertain)
            assertEquals(0,calls)
        } finally {dir.deleteRecursively()}
    }

    @Test fun postCommitCancellationStaysUnknownAfterTheRunnerReturnsLate() {
        val dir=Files.createTempDirectory("continuum-durable-revocation").toFile()
        val entered=CountDownLatch(1);val release=CountDownLatch(1);val ended=CountDownLatch(1)
        val worker=Executors.newSingleThreadExecutor()
        try {
            val gate=ToolGate(ToolPolicy(),ToolRunner {
                entered.countDown()
                try {while(release.count>0)try {release.await()}catch(_:InterruptedException){}}
                finally {ended.countDown()}
                "late output"
            },receiptStore=FileToolExecutionReceiptStore(dir))
            val result=worker.submit<ToolExecutionResult> {gate.execute(invocation("revoke"),userConfirmed=true)}
            assertTrue(entered.await(2,TimeUnit.SECONDS));gate.cancel()
            val cancelled=result.get(2,TimeUnit.SECONDS)
            assertEquals(ToolExecutionStatus.CANCELLED,cancelled.record.status)
            assertTrue(cancelled.record.outcomeUncertain)
            release.countDown();assertTrue(ended.await(2,TimeUnit.SECONDS))
            assertEquals("UNKNOWN_AFTER_DISPATCH",FileToolExecutionReceiptStore(dir).get("revoke")!!.state.name)
        } finally {release.countDown();worker.shutdownNow();dir.deleteRecursively()}
    }

    @Test fun duplicateConcurrentGatesCannotDispatchTheSameCallTwice() {
        val dir=Files.createTempDirectory("continuum-shared-call").toFile()
        val workers=Executors.newFixedThreadPool(2)
        try {
            val start=CountDownLatch(1);val calls=AtomicInteger()
            val jobs=(1..2).map {workers.submit {
                start.await()
                ToolGate(ToolPolicy(),ToolRunner {calls.incrementAndGet();"one actual call"},
                    receiptStore=FileToolExecutionReceiptStore(dir)).execute(invocation("same-id"),userConfirmed=true)
            }}
            start.countDown();jobs.forEach {it.get(3,TimeUnit.SECONDS)}
            assertEquals(1,calls.get())
        } finally {workers.shutdownNow();dir.deleteRecursively()}
    }
}
