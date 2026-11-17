package at.heinz.deviceai.cockpit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifiedHologramWorkGateTest {
    @Test fun noMailOrTerminalResultsWithoutRealStartEvidence() {
        val gate = VerifiedHologramWorkGate()
        assertFalse(gate.accept(VerifiedHologramWorkGate.Event.DataReady("mail1", 100, "data1")))
        assertFalse(gate.accept(VerifiedHologramWorkGate.Event.Completed("mail1", 100, "done1")))
        assertTrue(gate.snapshot().isEmpty())
    }

    @Test fun aRealStartAndReceiptAdvanceOneBoundedScene() {
        val gate = VerifiedHologramWorkGate()
        assertTrue(gate.accept(VerifiedHologramWorkGate.Event.Started(
            "mail1", 100, VerifiedHologramWorkGate.Kind.MAIL, "toolstart-1")))
        assertTrue(gate.accept(VerifiedHologramWorkGate.Event.DataReady("mail1", 110, "mailheader-1")))
        assertTrue(gate.accept(VerifiedHologramWorkGate.Event.Completed("mail1", 120, "provider-receipt-1")))
        val scene = gate.snapshot().single()
        assertEquals(VerifiedHologramWorkGate.Phase.COMPLETED, scene.phase)
        assertEquals("provider-receipt-1", scene.lastEvidenceId)
        assertFalse(gate.accept(VerifiedHologramWorkGate.Event.Failed("mail1", 125, "late-failure")))
        assertTrue(gate.accept(VerifiedHologramWorkGate.Event.Closed("mail1", 140)))
        assertFalse(gate.accept(VerifiedHologramWorkGate.Event.Started(
            "mail1", 150, VerifiedHologramWorkGate.Kind.MAIL, "replay")))
    }

    @Test fun rejectMissingReceiptStaleTimestampAndExtraWindows() {
        val gate = VerifiedHologramWorkGate(limit = 1)
        assertFalse(gate.accept(VerifiedHologramWorkGate.Event.Started(
            "mail1", 1, VerifiedHologramWorkGate.Kind.MAIL, "")))
        assertTrue(gate.accept(VerifiedHologramWorkGate.Event.Started(
            "mail1", 10, VerifiedHologramWorkGate.Kind.MAIL, "start-1")))
        assertFalse(gate.accept(VerifiedHologramWorkGate.Event.Started(
            "terminal2", 11, VerifiedHologramWorkGate.Kind.CODE, "start-2")))
        assertFalse(gate.accept(VerifiedHologramWorkGate.Event.DataReady("mail1", 9, "old")))
        assertFalse(gate.accept(VerifiedHologramWorkGate.Event.Completed("mail1", 11, "")))
        assertEquals(VerifiedHologramWorkGate.Phase.STARTED, gate.snapshot().single().phase)
    }

    @Test fun aFailedToolNeverPretendsToHaveSucceeded() {
        val gate = VerifiedHologramWorkGate()
        assertTrue(gate.accept(VerifiedHologramWorkGate.Event.Started(
            "scan1", 50, VerifiedHologramWorkGate.Kind.DEVICE_CHECK, "scan-start")))
        assertTrue(gate.accept(VerifiedHologramWorkGate.Event.Failed("scan1", 51, "scan-error")))
        assertFalse(gate.accept(VerifiedHologramWorkGate.Event.Completed("scan1", 52, "invented-success")))
        assertEquals(VerifiedHologramWorkGate.Phase.FAILED, gate.snapshot().single().phase)
    }
}
