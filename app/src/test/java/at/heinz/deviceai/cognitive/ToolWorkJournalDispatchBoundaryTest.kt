package at.heinz.deviceai.cognitive

import at.heinz.deviceai.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class ToolWorkJournalDispatchBoundaryTest {
    @get:Rule val dir = TemporaryFolder()

    private data class Probe(
        val result: AgentRunResult,
        val journal: CockpitWorkJournal,
        val starts: Int,
        val backendCalls: Int,
        val statesAtStart: List<ToolReceiptState?>,
        val callsAtStart: List<Int>
    )

    private fun probe(
        allow: Boolean = true,
        beforeCommit: (ToolGate, AtomicLong) -> Unit = { _, _ -> },
        refuseCommit: Boolean = false,
        observerFails: Boolean = false,
        replay: Boolean = false
    ): Probe {
        val name = ToolWorkJournalBridge.FIRST_TOOL
        val prefix = "m2-dispatch-boundary"
        val durable = FileToolExecutionReceiptStore(dir.newFolder("receipts"))
        val store = if (!refuseCommit) durable else object : ToolExecutionReceiptStore by durable {
            override fun transition(callId: String, expected: ToolReceiptState, next: ToolReceiptState,
                                    atMillis: Long, outputLength: Int, outputSha256: String?): ToolExecutionReceipt? {
                if (next == ToolReceiptState.DISPATCH_COMMITTED) throw IOException("Commit unavailable")
                return durable.transition(callId, expected, next, atMillis, outputLength, outputSha256)
            }
        }
        if (replay) {
            val previous = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { "Earlier storage result" },
                receiptStore = durable).execute(ToolProposal(name, emptyMap()).invocation("$prefix-1"))
            assertEquals(ToolExecutionStatus.SUCCEEDED, previous.record.status)
        }
        val journal = CockpitWorkJournal(dir.newFolder("work"))
        val starts = AtomicInteger()
        val backendCalls = AtomicInteger()
        val clockReads = AtomicInteger()
        val nanos = AtomicLong()
        val statesAtStart = ConcurrentLinkedQueue<ToolReceiptState>()
        val callsAtStart = ConcurrentLinkedQueue<Int>()
        lateinit var gate: ToolGate
        gate = ToolGate(ToolPolicy(readOnlyEnabled = allow, timeoutMillis = 1_000), ToolRunner {
            backendCalls.incrementAndGet()
            "Fresh storage result"
        }, clock = {
            if (Thread.currentThread().name == "device-ai-tool" && clockReads.incrementAndGet() == 2)
                beforeCommit(gate, nanos)
            nanos.get()
        }, receiptStore = store)
        val plan = ReadOnlyToolPlan(ToolProposal(name, emptyMap()))
        val result = BoundedAgentOrchestrator(
            plan, { emptyList() }, gate, plan, InMemoryExperienceStore(),
            callIdPrefix = prefix,
            limits = AgentLimits(maxToolCalls = 1, maxRetries = 0),
            onToolStarted = { id, tool ->
                starts.incrementAndGet()
                statesAtStart += requireNotNull(durable.get(id)).state
                callsAtStart += backendCalls.get()
                if (observerFails) error("Journal observer unavailable")
                journal.beginTool(id, tool)
            }
        ).run("Prüfe den freien Speicher")
        return Probe(result, journal, starts.get(), backendCalls.get(),
            statesAtStart.toList(), callsAtStart.toList())
    }

    private fun assertNoDispatch(probe: Probe, status: ToolExecutionStatus) {
        assertEquals(status, requireNotNull(probe.result.taskEvidence).calls.single().status)
        assertEquals("Backend must remain untouched", 0, probe.backendCalls)
        assertEquals("No actual dispatch; no start signal", 0, probe.starts)
        assertTrue("No premature running journal entry", probe.journal.all().isEmpty())
    }

    @Test fun normalDispatchSignalsOnceAfterDurableCommitBeforeBackend() {
        val probe = probe()
        assertEquals(1, probe.starts)
        assertEquals(1, probe.backendCalls)
        assertEquals(listOf(ToolReceiptState.DISPATCH_COMMITTED), probe.statesAtStart)
        assertEquals(listOf(0), probe.callsAtStart)
        assertEquals(CockpitWorkJournal.ToolState.EXECUTING, probe.journal.all().single().toolState)
        ToolWorkJournalBridge.settle(probe.journal,
            requireNotNull(probe.result.taskEvidence).calls.single(), current = true)
        assertEquals(CockpitWorkJournal.ToolState.SUCCEEDED, probe.journal.all().single().toolState)
    }

    @Test fun deniedPolicyNeverSignalsActualDispatch() {
        assertNoDispatch(probe(allow = false), ToolExecutionStatus.REJECTED)
    }

    @Test fun stopAfterEarlyObserverNeverSignalsActualDispatch() {
        assertNoDispatch(probe(beforeCommit = { gate, _ -> gate.cancel() }), ToolExecutionStatus.CANCELLED)
    }

    @Test fun expiredDeadlineAfterEarlyObserverNeverSignalsActualDispatch() {
        assertNoDispatch(probe(beforeCommit = { _, nanos -> nanos.set(2_000_000_000L) }),
            ToolExecutionStatus.TIMED_OUT)
    }

    @Test fun failedDurableCommitNeverSignalsActualDispatch() {
        assertNoDispatch(probe(refuseCommit = true), ToolExecutionStatus.FAILED)
    }

    @Test fun replayedReceiptNeverSignalsANewDispatch() {
        val probe = probe(replay = true)
        assertNoDispatch(probe, ToolExecutionStatus.SUCCEEDED)
        assertTrue(requireNotNull(probe.result.taskEvidence).calls.single().replayed)
    }

    @Test fun observerFailureCannotPreventTheAuthorizedBackendCall() {
        val probe = probe(observerFails = true)
        assertEquals(1, probe.starts)
        assertEquals(1, probe.backendCalls)
        assertEquals(ToolExecutionStatus.SUCCEEDED,
            requireNotNull(probe.result.taskEvidence).calls.single().status)
    }
}
