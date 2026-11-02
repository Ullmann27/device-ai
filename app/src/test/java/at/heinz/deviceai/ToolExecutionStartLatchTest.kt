package at.heinz.deviceai

import at.heinz.deviceai.cognitive.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class ToolExecutionStartLatchTest {
    @Test fun pendingStartCanBeRevokedExactlyOnce() {
        val gate=ToolExecutionStartLatch()
        assertTrue(gate.revokeIfPending())
        assertFalse(gate.commitIfPending())
        assertTrue(gate.wasRevokedBeforeCommit)
        assertFalse(gate.wasCommitted)
        assertFalse(gate.revokeIfPending())
    }

    @Test fun committedStartCannotBeReclassifiedAsNeverDispatched() {
        val gate=ToolExecutionStartLatch()
        assertTrue(gate.commitIfPending())
        assertFalse(gate.commitIfPending())
        assertFalse(gate.revokeIfPending())
        assertTrue(gate.wasCommitted)
    }

    @Test fun racingCommitAndRevocationHaveOneLinearWinner() {
        repeat(128) {
            val gate=ToolExecutionStartLatch()
            val ready=CountDownLatch(2)
            val go=CountDownLatch(1)
            val starts=AtomicInteger()
            val blocks=AtomicInteger()
            val worker=Thread {
                ready.countDown()
                check(go.await(2,TimeUnit.SECONDS))
                if(gate.commitIfPending()) starts.incrementAndGet()
            }
            val owner=Thread {
                ready.countDown()
                check(go.await(2,TimeUnit.SECONDS))
                if(gate.revokeIfPending()) blocks.incrementAndGet()
            }
            worker.start();owner.start()
            assertTrue(ready.await(2,TimeUnit.SECONDS))
            go.countDown()
            worker.join(2000);owner.join(2000)
            assertFalse(worker.isAlive);assertFalse(owner.isAlive)
            assertEquals(1,starts.get()+blocks.get())
            assertEquals(starts.get()==1,gate.wasCommitted)
        }
    }

    /** GitHub Gitar review: FutureTask wraps worker CancellationException in ExecutionException. */
    @Test fun workerThrownCancellationCannotBecomeFailedReceipt() {
        val backendCalls = AtomicInteger(0)
        val clockReads = AtomicInteger(0)
        val gate = ToolGate(
            ToolPolicy(readOnlyEnabled = true, timeoutMillis = 2_000),
            ToolRunner { backendCalls.incrementAndGet(); "unexpected" },
            clock = {
                if (Thread.currentThread().name == "device-ai-tool" &&
                    clockReads.incrementAndGet() == 2
                ) throw java.util.concurrent.CancellationException("Worker revoked before dispatch")
                System.nanoTime()
            }
        )
        val result = gate.execute(ToolProposal("get_storage_info", emptyMap())
            .invocation("worker-cancel-exception"))
        assertEquals(ToolExecutionStatus.CANCELLED, result.record.status)
        assertTrue(result.record.decisionReason.contains("before backend dispatch"))
        assertEquals(0, backendCalls.get())
    }

    /** Reproduces the independent PR358 clock-stall counterexample, now fail-closed. */
    @Test fun timeoutCannotStartBackendAfterTheCallerAlreadyReturned() {
        delayedEligibilityRace(cancel=false)
    }

    @Test fun explicitCancelCannotStartBackendAfterTheCallerAlreadyReturned() {
        delayedEligibilityRace(cancel=true)
    }

    /** A dispatched operation cannot be labelled definitely unexecuted on timeout. */
    @Test fun postCommitTimeoutIsAmbiguousAndDoesNotRetrySideEffects() {
        val backendEntered=CountDownLatch(1)
        val releaseBackend=CountDownLatch(1)
        val backendCalls=AtomicInteger(0)
        val gate=ToolGate(ToolPolicy(readOnlyEnabled=true,timeoutMillis=250),ToolRunner {
            backendCalls.incrementAndGet()
            backendEntered.countDown()
            while(releaseBackend.count!=0L) {
                try { releaseBackend.await(2,TimeUnit.SECONDS) }
                catch (_:InterruptedException) { /* intentional non-cooperative backend */ }
            }
            "result after timeout"
        })
        val invocation=ToolProposal("get_storage_info",emptyMap()).invocation("post-commit-timeout")
        try {
            val outcome=AtomicReference<ToolExecutionResult>()
            val caller=Thread { outcome.set(gate.execute(invocation)) }
            caller.start()
            assertTrue(backendEntered.await(2,TimeUnit.SECONDS))
            caller.join(2_000)
            assertFalse(caller.isAlive)
            assertEquals(ToolExecutionStatus.TIMED_OUT,outcome.get().record.status)
            assertTrue(outcome.get().record.decisionReason.contains("UNKNOWN"))
            assertEquals(1,backendCalls.get())
            val replay=gate.execute(invocation)
            assertNotEquals(ToolExecutionStatus.SUCCEEDED,replay.record.status)
            assertEquals(1,backendCalls.get())
        } finally { releaseBackend.countDown() }
    }

    private fun delayedEligibilityRace(cancel:Boolean) {
        val clockEntered=CountDownLatch(1)
        val releaseClock=CountDownLatch(1)
        val backend=CountDownLatch(1)
        val clockReads=AtomicInteger()
        val outcome=AtomicReference<ToolExecutionResult>()
        val gate=ToolGate(ToolPolicy(readOnlyEnabled=true,timeoutMillis=80),ToolRunner {
            backend.countDown(); "late unexpected execution"
        },clock={
            if(Thread.currentThread().name=="device-ai-tool" && clockReads.incrementAndGet()==2) {
                clockEntered.countDown()
                while(releaseClock.count!=0L) {
                    try { releaseClock.await(2,TimeUnit.SECONDS) }
                    catch (_:InterruptedException) { /* simulate uncooperative clock */ }
                }
            }
            0L
        })
        val caller=Thread { outcome.set(gate.execute(ToolProposal("get_storage_info",emptyMap())
            .invocation(if(cancel)"cancel-race" else "timeout-race"))) }
        caller.start()
        try {
            assertTrue("Worker never reached eligibility barrier",clockEntered.await(2,TimeUnit.SECONDS))
            if(cancel)gate.cancel()
            caller.join(2000)
            assertFalse("Caller must terminate promptly",caller.isAlive)
            assertEquals(if(cancel)ToolExecutionStatus.CANCELLED else ToolExecutionStatus.TIMED_OUT,
                outcome.get().record.status)
        } finally { releaseClock.countDown() }
        assertFalse("A terminal pre-dispatch call must never start backend",
            backend.await(250,TimeUnit.MILLISECONDS))
    }
}

class ReadOnlyToolSizeRegressionTest {
    @Test fun rawAllowedOutputPlusLabelCannotThrowOrBecomeVerifiedSuccess() {
        val plan=ReadOnlyToolPlan(ToolProposal("get_storage_info",emptyMap()))
        val raw="x".repeat(8_000)
        val proposed=plan.generate("Prüfe Speicher",emptyList(),null,emptyList())
        assertEquals("get_storage_info",proposed.proposal?.toolName)
        val response=plan.generate("Prüfe Speicher",emptyList(),null,
            listOf(AgentToolObservation("get_storage_info",ToolExecutionStatus.SUCCEEDED,raw)))
        assertTrue(response.response.length<=8_000)
        assertTrue(response.response.contains("nicht bestätigt"))
        assertFalse(plan.evaluate(response.response,ToolExecutionRecord("call","get_storage_info",
            Risk.READ_ONLY,emptyMap(),"",ToolExecutionStatus.SUCCEEDED,1,raw.length)).success)
    }

    @Test fun shortFullOutputRemainsVerifiable() {
        val plan=ReadOnlyToolPlan(ToolProposal("get_storage_info",emptyMap()))
        val raw="Gesamt: 123 Byte"
        val response=plan.generate("Prüfe Speicher",emptyList(),null,
            listOf(AgentToolObservation("get_storage_info",ToolExecutionStatus.SUCCEEDED,raw)))
        assertTrue(response.response.contains(raw))
        assertTrue(plan.evaluate(response.response,ToolExecutionRecord("call","get_storage_info",
            Risk.READ_ONLY,emptyMap(),"",ToolExecutionStatus.SUCCEEDED,1,raw.length)).success)
    }
}
