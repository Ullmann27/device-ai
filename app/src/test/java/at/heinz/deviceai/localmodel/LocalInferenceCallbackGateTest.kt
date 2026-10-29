package at.heinz.deviceai.localmodel

import org.junit.Assert.*
import org.junit.Test

class LocalInferenceCallbackGateTest {
    private fun completed() = LocalInferenceEvent.Completed(
        LocalInferenceMetrics(1, 0, 0, null, null, null, 1)
    )

    @Test fun incrementalWhitespaceSurvivesWithoutInventingEmptyChunks() {
        val terminals = mutableListOf<LocalInferenceEvent>()
        val gate = LocalInferenceCallbackGate { terminals += it }
        val chunks = mutableListOf<String>()
        gate.start {
            listOf("Hallo", " ", "Welt", "\n", "    ", "return 1", "\n", "").forEach {
                gate.text(it) { chunk -> chunks += chunk }
            }
        }
        gate.terminal { completed() }
        assertEquals(listOf("Hallo", " ", "Welt", "\n", "    ", "return 1", "\n"), chunks)
        assertEquals("Hallo Welt\n    return 1\n", chunks.joinToString(""))
        assertTrue(terminals.single() is LocalInferenceEvent.Completed)
    }

    @Test fun requestedCancellationSuppressesLateTextAndWaitsForNativeTerminal() {
        val terminals = mutableListOf<LocalInferenceEvent>()
        val gate = LocalInferenceCallbackGate { terminals += it }
        val chunks = mutableListOf<String>()
        gate.start { gate.text("before") { chunks += it } }
        gate.cancel(LocalInferenceEvent.Cancelled) {}
        assertTrue("Native inference still owns the conversation", terminals.isEmpty())
        assertFalse(gate.allowsToolContinuation)
        gate.text("late") { chunks += it }
        gate.terminal { completed() } // Native completion raced the owner's cancel.
        gate.terminal { LocalInferenceEvent.Failed("duplicate terminal") }
        assertEquals(listOf("before"), chunks)
        assertEquals(listOf(LocalInferenceEvent.Cancelled), terminals)
        assertFalse(gate.allowsToolContinuation)
    }

    @Test fun failureCauseSurvivesReentrantNativeCancellation() {
        val terminals = mutableListOf<LocalInferenceEvent>()
        val gate = LocalInferenceCallbackGate { terminals += it }
        gate.start {}
        gate.message {
            gate.cancel(LocalInferenceEvent.Failed("output limit")) {
                gate.terminal { LocalInferenceEvent.Cancelled }
            }
        }
        gate.terminal { completed() }
        assertEquals(listOf(LocalInferenceEvent.Failed("output limit")), terminals)
        assertFalse(gate.allowsToolContinuation)
    }

    @Test fun cancellationBeforeDispatchPreventsAnyNativeWork() {
        val terminals = mutableListOf<LocalInferenceEvent>()
        val gate = LocalInferenceCallbackGate { terminals += it }
        var started = false
        var nativeCancelled = false
        gate.cancel(LocalInferenceEvent.Cancelled) { nativeCancelled = true }
        gate.start { started = true }
        assertFalse(started)
        assertFalse(nativeCancelled)
        assertEquals(listOf(LocalInferenceEvent.Cancelled), terminals)
    }

    @Test fun completedTurnIgnoresLateCancelTextAndDuplicateTerminal() {
        val terminals = mutableListOf<LocalInferenceEvent>()
        val gate = LocalInferenceCallbackGate { terminals += it }
        val done = completed()
        var cancellationSent = false
        var lateMessageDelivered = false
        gate.start {}
        gate.terminal { done }
        gate.cancel(LocalInferenceEvent.Cancelled) { cancellationSent = true }
        gate.message { lateMessageDelivered = true }
        gate.text("late") { lateMessageDelivered = true }
        gate.terminal { LocalInferenceEvent.Failed("late") }
        assertFalse(cancellationSent)
        assertFalse(lateMessageDelivered)
        assertEquals(listOf(done), terminals)
        assertTrue(gate.allowsToolContinuation)
    }

    @Test fun repeatedCancellationCannotReplaceTheFirstTerminalCause() {
        val terminals = mutableListOf<LocalInferenceEvent>()
        val gate = LocalInferenceCallbackGate { terminals += it }
        var cancels = 0
        gate.start {}
        gate.cancel(LocalInferenceEvent.Failed("first")) { cancels++ }
        gate.cancel(LocalInferenceEvent.Cancelled) { cancels++ }
        gate.terminal { LocalInferenceEvent.Cancelled }
        assertEquals(1, cancels)
        assertEquals(listOf(LocalInferenceEvent.Failed("first")), terminals)
    }
}
