package at.heinz.deviceai.cognitive

import at.heinz.deviceai.ToolExecutionStatus
import at.heinz.deviceai.learning.ContinuumRuntimeEventJournal
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class CognitiveTurnTraceTest {
    @Test fun metadataContainsNoRawConversationIdentifiersOrModelText() {
        var clock = 0L
        val trace = CognitiveTurnTrace("private-session-name", nowNanos = { clock })
        trace.reference(TraceStage.MEMORY_REF, "private-memory-secret")
        trace.event(TraceStage.MEMORY_EXACT)
        clock = 9_000_000
        val result = trace.finish(TraceOutcome.MEMORY_EXACT)
        val row = CognitiveTraceCodec.encode(result)
        assertFalse(row.contains("private"))
        assertEquals(9L, result.elapsedMillis)
        assertEquals(0, result.modelInvocations)
        assertEquals(result, CognitiveTraceCodec.decode(row))
    }

    @Test fun startedFailedAndCompletedCallsAreDistinguishedAndLateEventsIgnored() {
        val trace = CognitiveTurnTrace("session")
        trace.event(TraceStage.ROUTER_ADVISORY)
        trace.event(TraceStage.MODEL_ADVISORY_INVOKED)
        trace.event(TraceStage.MODEL_FAILED)
        trace.event(TraceStage.MODEL_PRIMARY_INVOKED)
        trace.event(TraceStage.MODEL_COMPLETED)
        trace.event(TraceStage.TOOL_EXECUTED, ToolExecutionStatus.REJECTED)
        val result = trace.finish(TraceOutcome.MODEL_INTERPRETATION)
        trace.event(TraceStage.MODEL_COMPLETED)
        assertEquals(2, result.modelInvocations)
        assertEquals(1, result.completedModelCalls)
        assertEquals(1, result.advisoryCalls)
        assertEquals(result, trace.finish(TraceOutcome.ACTION_VERIFIED))
        assertEquals(TraceOutcome.MODEL_INTERPRETATION, result.outcome)
    }

    @Test fun boundedJournalSurvivesRestartAndReviewChangesOnlyTheAnnotation() {
        val dir = Files.createTempDirectory("content-free-trace").toFile()
        try {
            val trace = CognitiveTurnTrace("local-session")
            trace.event(TraceStage.MODEL_NOT_READY)
            val original = trace.finish(TraceOutcome.BLOCKED)
            ContinuumRuntimeEventJournal(dir).recordTrace(original)
            val restarted = ContinuumRuntimeEventJournal(dir)
            assertEquals(original, restarted.traces().single())
            assertTrue(restarted.reviewTrace(original.id, TraceFeedback.CORRECTED))
            val reviewed = restarted.traces().single()
            assertEquals(original.events, reviewed.events)
            assertEquals(original.outcome, reviewed.outcome)
            assertEquals(TraceFeedback.CORRECTED, reviewed.feedback)
            assertFalse(restarted.reviewTrace("00000000-0000-0000-0000-000000000000", TraceFeedback.CONFIRMED))
            repeat(40) { restarted.recordTrace(CognitiveTurnTrace("same").finish(TraceOutcome.BLOCKED)) }
            assertEquals(32, restarted.traces().size)
        } finally { dir.deleteRecursively() }
    }

    @Test fun excessMetadataIsMarkedIncompleteAndCorruptRowsCannotInventEvidence() {
        val trace = CognitiveTurnTrace("session")
        repeat(100) { trace.event(TraceStage.MEMORY_LOOKUP) }
        val result = trace.finish(TraceOutcome.BLOCKED)
        assertTrue(result.incomplete)
        assertEquals(64, result.events.size)
        assertThrows(IllegalArgumentException::class.java) { CognitiveTraceCodec.decode("user-text\twhatever") }
    }
}
