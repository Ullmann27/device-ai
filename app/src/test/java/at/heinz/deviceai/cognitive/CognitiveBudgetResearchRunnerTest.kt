package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class CognitiveBudgetResearchRunnerTest {
    private val sha = "a".repeat(40)
    private val deep = CognitiveBudgetController.decide(CognitiveBudgetSignals(thermalStatus = 0), CognitiveBudgetMode.DEEP)
    private val on = CognitiveBudgetResearchConfig(enabled = true)
    private fun runner(adapter: ContinuumReasoningAdapter, config: CognitiveBudgetResearchConfig = on) =
        CognitiveBudgetResearchRunner(adapter, sha, config)
    private fun runner(block: (ReasoningPassRequest) -> ReasoningPassResult) = runner(ContinuumReasoningAdapter(block))
    private fun run(runner: CognitiveBudgetResearchRunner, decision: CognitiveBudgetDecision = deep,
                    task: String = "synthetic-task", context: String = "synthetic-context") =
        runner.run("fixture", task, context, decision)
    private fun answer(text: String = "candidate", model: String = "scripted-fixture", tools: Int = 0) =
        ReasoningPassResult(model, text, 0, tools)

    @Test fun defaultOffHasNoModelSideEffects() {
        var calls = 0
        val adapter = object : ContinuumReasoningAdapter {
            override fun execute(request: ReasoningPassRequest): ReasoningPassResult { calls++; return answer() }
            override fun cancel() { calls++ }
        }
        val r = runner(adapter, CognitiveBudgetResearchConfig())
        r.cancel()
        val result = run(r)
        assertEquals(CognitiveBudgetStop.DISABLED, result.receipt.stop)
        assertEquals(0, calls)
        assertNull(result.candidate)
    }
    @Test fun fastCallsOnce() {
        val result = run(runner { answer() }, CognitiveBudgetController.decide(CognitiveBudgetSignals(thermalStatus = 0)))
        assertEquals(1, result.receipt.attemptedCalls)
        assertEquals(CognitiveBudgetStop.CALL_LIMIT, result.receipt.stop)
    }
    @Test fun identicalOutputsStopAfterSecondCall() {
        val result = run(runner { answer() })
        assertEquals(2, result.receipt.attemptedCalls)
        assertEquals(CognitiveBudgetStop.STABLE_OUTPUT, result.receipt.stop)
        assertEquals("candidate", result.candidate)
    }
    @Test fun cycleStopsAtFirstRepeat() {
        var calls = 0
        val result = run(runner { answer(if (calls++ % 2 == 0) "A" else "B") })
        assertEquals(3, result.receipt.attemptedCalls)
        assertEquals(CognitiveBudgetStop.REPEATED_OUTPUT, result.receipt.stop)
    }
    @Test fun changingOutputsCannotExceedFourCalls() {
        var calls = 0
        val result = run(runner { answer("candidate-${calls++}") })
        assertEquals(4, calls)
        assertEquals(4, result.receipt.completedCalls)
        assertEquals(CognitiveBudgetStop.CALL_LIMIT, result.receipt.stop)
    }
    @Test fun fixedControlUsesSameBudgetWithoutAdaptiveStopping() {
        val result = run(runner(ContinuumReasoningAdapter { answer() }, on.copy(adaptiveStop = false)))
        assertEquals(4, result.receipt.attemptedCalls)
    }
    @Test fun configCanOnlyReduceCalls() {
        val result = run(runner(ContinuumReasoningAdapter { answer() }, on.copy(maxModelCalls = 1)))
        assertEquals(1, result.receipt.attemptedCalls)
    }
    @Test fun specializedPathsDoNotCallAdapter() {
        for (mode in listOf(CognitiveBudgetMode.TOOL, CognitiveBudgetMode.MEMORY, CognitiveBudgetMode.RESEARCH)) {
            val result = run(runner { error("must not execute") },
                CognitiveBudgetController.decide(CognitiveBudgetSignals(thermalStatus = 0), mode))
            assertEquals(CognitiveBudgetStop.EXISTING_PATH, result.receipt.stop)
            assertEquals(0, result.receipt.attemptedCalls)
        }
    }
    @Test fun thermalStopDoesNotCallAdapter() {
        val result = run(runner { error("must not execute") },
            CognitiveBudgetController.decide(CognitiveBudgetSignals(thermalStatus = 3), CognitiveBudgetMode.DEEP))
        assertEquals(CognitiveBudgetStop.THERMAL_STOP, result.receipt.stop)
    }
    @Test fun cancelledBeforeStartHasZeroCalls() {
        val r = runner { error("must not execute") }
        r.cancel()
        val result = run(r)
        assertEquals(CognitiveBudgetStop.CANCELLED, result.receipt.stop)
        assertEquals(0, result.receipt.attemptedCalls)
    }
    @Test fun cancellationDuringCallReachesAdapterAndRejectsLateOutput() {
        val entered = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        var result: CognitiveBudgetResearchResult? = null
        val r = runner(object : ContinuumReasoningAdapter {
            override fun execute(request: ReasoningPassRequest): ReasoningPassResult {
                entered.countDown()
                check(cancelled.await(2, TimeUnit.SECONDS))
                return answer("late")
            }
            override fun cancel() { cancelled.countDown() }
        })
        val worker = thread { result = run(r) }
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        r.cancel()
        worker.join(3_000)
        assertFalse(worker.isAlive)
        val observed = requireNotNull(result)
        assertEquals(CognitiveBudgetStop.CANCELLED, observed.receipt.stop)
        assertEquals(1, observed.receipt.attemptedCalls)
        assertNull(observed.candidate)
    }
    @Test fun interruptionDoesNotClearThreadInterrupt() {
        Thread.currentThread().interrupt()
        try {
            assertEquals(CognitiveBudgetStop.CANCELLED, run(runner { error("no call") }).receipt.stop)
            assertTrue(Thread.currentThread().isInterrupted)
        } finally { Thread.interrupted() }
    }
    @Test fun deadlineAfterFirstCallRejectsOutputAndStops() {
        var nanos = 0L
        val r = CognitiveBudgetResearchRunner(ContinuumReasoningAdapter {
            nanos = 21_000_000_000L
            answer()
        }, sha, on, { nanos }, { 100 })
        val result = run(r)
        assertEquals(CognitiveBudgetStop.DEADLINE, result.receipt.stop)
        assertEquals(1, result.receipt.attemptedCalls)
        assertNull(result.candidate)
    }
    @Test fun rollbackOfMonotonicClockFailsClosed() {
        var nanos = 100L
        val r = CognitiveBudgetResearchRunner(ContinuumReasoningAdapter { nanos = 0L; answer() }, sha, on, { nanos }, { 100 })
        assertEquals(CognitiveBudgetStop.CLOCK_INVALID, run(r).receipt.stop)
    }
    @Test fun wallClockOverflowFailsBeforeCall() {
        val r = CognitiveBudgetResearchRunner(ContinuumReasoningAdapter { error("no call") }, sha, on, { 0L }, { Long.MAX_VALUE })
        assertEquals(CognitiveBudgetStop.CLOCK_INVALID, run(r).receipt.stop)
    }
    @Test fun unicodeInputIsBoundedByBytesWithoutClipping() {
        val result = run(runner { error("no call") }, task = "é".repeat(8_000))
        assertEquals(CognitiveBudgetStop.INPUT_LIMIT, result.receipt.stop)
        assertEquals(0, result.receipt.attemptedCalls)
    }
    @Test fun eachRevisionRetainsWholeTaskAndContext() {
        val requests = mutableListOf<ReasoningPassRequest>()
        run(runner { requests += it; answer() }, task = "request-end-§", context = "context-end-😀")
        assertEquals(2, requests.size)
        assertTrue(requests.all { "request-end-§" in it.prompt && "context-end-😀" in it.prompt })
        assertTrue("PREVIOUS_UNVERIFIED" in requests.last().prompt)
    }
    @Test fun outputBytesCannotBypassCharacterLimit() {
        val result = run(runner { answer("😀".repeat(600)) })
        assertEquals(CognitiveBudgetStop.OUTPUT_LIMIT, result.receipt.stop)
        assertEquals(2_400L, result.receipt.totalOutputBytes)
        assertNull(result.candidate)
    }
    @Test fun aggregateOutputBudgetStopsBeforeAnotherCall() {
        val result = run(runner(ContinuumReasoningAdapter { answer("a".repeat(256)) },
            on.copy(maxTotalOutputBytes = 256, adaptiveStop = false)))
        assertEquals(CognitiveBudgetStop.OUTPUT_LIMIT, result.receipt.stop)
        assertEquals(1, result.receipt.attemptedCalls)
    }
    @Test fun modelChangeRejectsAllCandidates() {
        var index = 0
        val result = run(runner { answer(model = "model-${index++}") })
        assertEquals(CognitiveBudgetStop.MODEL_CHANGED, result.receipt.stop)
        assertNull(result.candidate)
    }
    @Test fun toolProposalIsRejectedWithoutExecution() {
        val result = run(runner { answer(tools = 1) })
        assertEquals(CognitiveBudgetStop.TOOL_PROPOSAL, result.receipt.stop)
        assertNull(result.candidate)
    }
    @Test fun failureReceiptCountsAttemptAndNeverCopiesException() {
        val result = run(runner { error("private-model-output") })
        assertEquals(CognitiveBudgetStop.ADAPTER_FAILURE, result.receipt.stop)
        assertEquals(1, result.receipt.attemptedCalls)
        assertEquals(0, result.receipt.completedCalls)
        assertFalse(result.receipt.toString().contains("private-model-output"))
    }
    @Test fun failedCallElapsedIsRecorded() {
        var nanos = 0L
        val r = CognitiveBudgetResearchRunner(ContinuumReasoningAdapter {
            nanos = 5_000_000L
            error("fixture")
        }, sha, on, { nanos }, { 100 })
        assertEquals(5, run(r).receipt.passes.single().elapsedMillis)
    }
    @Test fun sourceAndHashesAreBoundWithoutRawPayloads() {
        val result = run(runner { answer("private-output") }, task = "private-task", context = "private-context")
        assertEquals(sha, result.receipt.sourceRevision)
        assertEquals(64, result.receipt.taskSha256.length)
        assertFalse(result.receipt.toString().contains("private-task"))
        assertFalse(result.receipt.toString().contains("private-context"))
        assertFalse(result.receipt.toString().contains("private-output"))
        assertEquals(result.receipt.passes.sumOf { it.outputBytes.toLong() }, result.receipt.totalOutputBytes)
        assertEquals(result.receipt.passes.sumOf { it.promptBytes.toLong() }, result.receipt.totalPromptBytes)
    }
    @Test(expected = IllegalStateException::class) fun runnerCannotReplayOrRunConcurrently() {
        val r = runner { answer() }
        run(r)
        run(r)
    }
    @Test(expected = IllegalArgumentException::class) fun invalidSourceIdentityRejected() {
        CognitiveBudgetResearchRunner(ContinuumReasoningAdapter { answer() }, "unknown", on)
    }
    @Test(expected = IllegalArgumentException::class) fun oversizedLoopConfigRejected() { on.copy(maxModelCalls = 5) }
}
