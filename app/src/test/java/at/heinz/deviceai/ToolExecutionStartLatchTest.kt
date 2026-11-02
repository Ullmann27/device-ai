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

    /** Reproduces the independent PR358 clock-stall counterexample, now fail-closed. */
    @Test fun timeoutCannotStartBackendAfterTheCallerAlreadyReturned() {
        delayedEligibilityRace(cancel=false)
    }

    @Test fun explicitCancelCannotStartBackendAfterTheCallerAlreadyReturned() {
        delayedEligibilityRace(cancel=true)
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
