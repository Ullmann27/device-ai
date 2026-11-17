package at.heinz.deviceai

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ToolDispatchObserverTest {
    @get:Rule val dir = TemporaryFolder()

    private fun invocation(id: String) =
        ToolProposal("get_storage_info", emptyMap()).invocation(id)

    @Test fun legacyTrailingObserverStillRunsBeforeCommitAndCanCancel() {
        val store = FileToolExecutionReceiptStore(dir.root)
        val backendCalls = AtomicInteger()
        var observedState: ToolReceiptState? = null
        lateinit var gate: ToolGate
        gate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner {
            backendCalls.incrementAndGet()
            "must not run"
        }, receiptStore = store)
        val result = gate.execute(invocation("legacy-observer")) {
            observedState = store.get("legacy-observer")?.state
            gate.cancel()
        }
        assertEquals(ToolReceiptState.START_PENDING, observedState)
        assertEquals(ToolExecutionStatus.CANCELLED, result.record.status)
        assertEquals(0, backendCalls.get())
        assertEquals(ToolReceiptState.CANCELLED, store.get("legacy-observer")?.state)
    }

    @Test fun throwingDispatchObserverDoesNotChangeBackendOrReceipt() {
        val store = FileToolExecutionReceiptStore(dir.root)
        val starts = AtomicInteger()
        val backendCalls = AtomicInteger()
        var observedState: ToolReceiptState? = null
        val gate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner {
            backendCalls.incrementAndGet()
            "fresh storage result"
        }, receiptStore = store)
        val result = gate.execute(invocation("throwing-observer"), false, false,
            onExecutionStarted = null, onDispatchStarted = {
                starts.incrementAndGet()
                observedState = store.get("throwing-observer")?.state
                error("Presentation unavailable")
            })
        assertEquals(1, starts.get())
        assertEquals(1, backendCalls.get())
        assertEquals(ToolReceiptState.DISPATCH_COMMITTED, observedState)
        assertEquals(ToolExecutionStatus.SUCCEEDED, result.record.status)
        assertEquals(ToolReceiptState.SUCCEEDED, store.get("throwing-observer")?.state)
    }

    @Test fun cancellationAfterDispatchCommitRemainsUnknown() {
        val store = FileToolExecutionReceiptStore(dir.root)
        val backendEntered = CountDownLatch(1)
        val backendCalls = AtomicInteger()
        val starts = AtomicInteger()
        lateinit var gate: ToolGate
        gate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner {
            backendCalls.incrementAndGet()
            backendEntered.countDown()
            "late storage result"
        }, receiptStore = store)
        val result = gate.execute(invocation("post-commit-cancel"), false, false,
            onExecutionStarted = null, onDispatchStarted = {
                starts.incrementAndGet()
                gate.cancel()
            })
        assertTrue(backendEntered.await(2, TimeUnit.SECONDS))
        assertEquals(1, starts.get())
        assertEquals(1, backendCalls.get())
        assertEquals(ToolExecutionStatus.CANCELLED, result.record.status)
        assertTrue(result.record.outcomeUncertain)
        assertEquals(ToolReceiptState.UNKNOWN_AFTER_DISPATCH, store.get("post-commit-cancel")?.state)
    }
}
