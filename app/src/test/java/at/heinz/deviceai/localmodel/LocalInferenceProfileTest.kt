package at.heinz.deviceai.localmodel

import org.junit.Assert.*
import org.junit.Test

class LocalInferenceProfileTest {
    private var now = 1_000_000_000L
    private var heap = LocalInferenceJvmHeap(10, 100)
    private fun profiles(capacity: Int = 8) = LocalInferenceProfiles(
        nowNanos = { now }, heap = { heap }, capacity = capacity
    )

    @Test fun dispatchAndCallbackTimingsUseTheirActualMonotonicOrder() {
        val profiles = profiles()
        val run = profiles.begin(LocalInferenceKind.CHAT)
        now += 10
        run.mark(LocalInferenceStage.CONVERSATION_CREATE_ENTER)
        now += 20
        run.mark(LocalInferenceStage.CONVERSATION_CREATE_EXIT)
        now += 30
        run.mark(LocalInferenceStage.NATIVE_DISPATCH_ENTER)
        now += 40
        run.nativeText(1)
        now += 50
        run.mark(LocalInferenceStage.NATIVE_DISPATCH_RETURN)
        val result = profiles.snapshots().single()
        assertEquals(10L, result.stagesNanos[LocalInferenceStage.CONVERSATION_CREATE_ENTER])
        assertEquals(30L, result.stagesNanos[LocalInferenceStage.CONVERSATION_CREATE_EXIT])
        assertEquals(60L, result.stagesNanos[LocalInferenceStage.NATIVE_DISPATCH_ENTER])
        assertEquals(100L, result.stagesNanos[LocalInferenceStage.FIRST_NATIVE_TEXT_CALLBACK])
        assertEquals(150L, result.stagesNanos[LocalInferenceStage.NATIVE_DISPATCH_RETURN])
        assertEquals(LocalInferencePrefill.UNKNOWN_NOT_EXPOSED, result.prefill)
    }

    @Test fun textCountsAreNumericAndDoNotInventAFirstTokenOrPrefillMeasurement() {
        val profiles = profiles()
        val run = profiles.begin(LocalInferenceKind.ISOLATED_THINKING)
        run.nativeText(0)
        assertFalse(run.snapshot().stagesNanos.containsKey(LocalInferenceStage.FIRST_NATIVE_TEXT_CALLBACK))
        now += 1
        run.nativeText(1) // A standalone space/newline is still nonempty content.
        now += 3
        run.nativeText(3)
        val result = run.snapshot()
        assertEquals(2, result.nativeTextChunks)
        assertEquals(4, result.nativeTextCharacters)
        assertEquals(1L, result.stagesNanos[LocalInferenceStage.FIRST_NATIVE_TEXT_CALLBACK])
        assertEquals(LocalInferencePrefill.UNKNOWN_NOT_EXPOSED, result.prefill)
    }

    @Test fun cancellationNativeTerminalAndRuntimeDeliveryRemainSeparateMeasurements() {
        val profiles = profiles()
        val run = profiles.begin(LocalInferenceKind.TOOL_CONTINUATION)
        now += 10
        run.cancel(LocalInferenceCancelOrigin.CALLER) { now += 20 }
        now += 30
        heap = LocalInferenceJvmHeap(25, 100)
        run.nativeTerminal(LocalInferenceTerminal.CANCELLED)
        now += 10
        run.runtimeTerminal(LocalInferenceTerminal.CANCELLED)
        now += 20
        run.mark(LocalInferenceStage.NATIVE_TERMINAL_CALLBACK_EXIT)
        val result = run.snapshot()
        assertEquals(10L, result.stagesNanos[LocalInferenceStage.CANCEL_REQUEST])
        assertEquals(30L, result.stagesNanos[LocalInferenceStage.CANCEL_CALL_EXIT])
        assertEquals(60L, result.stagesNanos[LocalInferenceStage.NATIVE_TERMINAL_CALLBACK_ENTER])
        assertEquals(90L, result.stagesNanos[LocalInferenceStage.NATIVE_TERMINAL_CALLBACK_EXIT])
        assertEquals(1, result.cancellationRequests)
        assertEquals(LocalInferenceCancelOrigin.CALLER, result.firstCancelOrigin)
        assertEquals(LocalInferenceTerminal.CANCELLED, result.nativeTerminal)
        assertEquals(LocalInferenceTerminal.CANCELLED, result.runtimeTerminal)
        assertEquals(LocalInferenceJvmHeap(10, 100), result.invocationJvmHeap)
        assertEquals(LocalInferenceJvmHeap(25, 100), result.terminalJvmHeap)
        assertEquals(LocalInferenceStage.NATIVE_TERMINAL_CALLBACK_ENTER, result.terminalHeapStage)
        assertEquals(LocalInferenceHeapScope.JVM_HEAP_ONLY, result.heapScope)
    }

    @Test fun snapshotsStayBoundedAndLateOldCallbacksCannotOverwriteANewerRun() {
        val profiles = profiles(capacity = 2)
        val old = profiles.begin(LocalInferenceKind.CHAT)
        val middle = profiles.begin(LocalInferenceKind.ISOLATED_THINKING)
        val latest = profiles.begin(LocalInferenceKind.TOOL_CONTINUATION)
        old.nativeTerminal(LocalInferenceTerminal.CANCELLED)
        middle.nativeText(2)
        latest.nativeText(5)
        val result = profiles.snapshots()
        assertEquals(listOf(2L, 3L), result.map { it.id })
        assertEquals(2, result.first().nativeTextCharacters)
        assertEquals(5, result.last().nativeTextCharacters)
        assertEquals(LocalInferenceTerminal.NONE, result.last().nativeTerminal)
    }

    @Test fun measuredFailurePropagatesAndStillRecordsTheRealExitBoundary() {
        val run = profiles().begin(LocalInferenceKind.CHAT)
        val expected = IllegalStateException("test-only exception, never retained")
        val actual = assertThrows(IllegalStateException::class.java) {
            run.measure(LocalInferenceStage.CONVERSATION_CREATE_ENTER,
                LocalInferenceStage.CONVERSATION_CREATE_EXIT) {
                now += 30
                throw expected
            }
        }
        assertSame(expected, actual)
        assertEquals(30L, run.snapshot().stagesNanos[LocalInferenceStage.CONVERSATION_CREATE_EXIT])
        assertFalse(run.snapshot().safeSummary().contains("test-only"))
    }

    @Test fun unavailableHeapAndClockRegressionAreReportedWithoutFabricatedDurations() {
        val profiles = LocalInferenceProfiles(nowNanos = { now }, heap = { error("unavailable") })
        val run = profiles.begin(LocalInferenceKind.CHAT)
        now -= 1
        run.mark(LocalInferenceStage.NATIVE_DISPATCH_ENTER)
        val result = run.snapshot()
        assertNull(result.invocationJvmHeap)
        assertTrue(result.clockAnomaly)
        assertFalse(result.stagesNanos.containsKey(LocalInferenceStage.NATIVE_DISPATCH_ENTER))
        assertTrue(result.safeSummary().contains("UNKNOWN_NOT_EXPOSED"))
        assertTrue(result.safeSummary().contains("JVM_HEAP_ONLY"))
        assertTrue(result.safeSummary().length <= 2_500)
    }

    @Test fun countersSaturateInsteadOfWrappingAndSummaryRemainsBounded() {
        val run = profiles().begin(LocalInferenceKind.CHAT)
        run.nativeText(Int.MAX_VALUE)
        run.nativeText(2)
        now += 1
        LocalInferenceStage.values().forEach { run.mark(it) }
        val result = run.snapshot()
        assertEquals(Int.MAX_VALUE, result.nativeTextCharacters)
        assertEquals(2, result.nativeTextChunks)
        assertTrue(result.safeSummary().length <= 2_500)
    }
}
