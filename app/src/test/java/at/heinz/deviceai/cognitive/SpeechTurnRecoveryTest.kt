package at.heinz.deviceai.cognitive

import at.heinz.deviceai.cognitive.LocalSpeechInputPolicy.Failure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Replays the Fold7 ERROR_CLIENT / ERROR_RECOGNIZER_BUSY sequences against the pure turn driver. */
class SpeechTurnRecoveryTest {
    private class Harness(policy: SpeechRecoveryPolicy = SpeechRecoveryPolicy()) {
        val scheduled = mutableListOf<Pair<Long, () -> Unit>>()
        var warmStarts = 0
        var coldRestarts = 0
        var warmThrows = false
        var coldThrows = false
        val failures = mutableListOf<Failure>()
        val recovery = SpeechTurnRecovery(
            policy = policy,
            schedule = { delay, task ->
                val entry = delay to task
                scheduled += entry
                val cancel: () -> Unit = { scheduled.remove(entry) }
                cancel
            },
            startWarm = { warmStarts++; if (warmThrows) error("warm start failed") },
            restartCold = { coldRestarts++; if (coldThrows) error("cold restart failed") },
            giveUp = { failures += it }
        )
        fun fire() { val (_, task) = scheduled.removeAt(0); task() }
    }

    @Test fun busyBusyBusyIsRecoveredByWarmWarmColdWithoutReportingFailure() {
        val h = Harness()
        assertTrue(h.recovery.recoverableError(Failure.BUSY))
        assertEquals(250L, h.scheduled.single().first)
        h.fire(); assertEquals(1, h.warmStarts)
        assertTrue(h.recovery.recoverableError(Failure.BUSY))
        assertEquals(700L, h.scheduled.single().first)
        h.fire(); assertEquals(2, h.warmStarts)
        assertTrue(h.recovery.recoverableError(Failure.BUSY))
        assertEquals(1_200L, h.scheduled.single().first)
        h.fire(); assertEquals(1, h.coldRestarts)
        assertTrue(h.recovery.coldRestartUsed)
        assertTrue(h.failures.isEmpty())
    }

    @Test fun fourthBusyGivesUpSoTheTurnFailsExactlyOnce() {
        val h = Harness()
        repeat(3) { assertTrue(h.recovery.recoverableError(Failure.BUSY)); h.fire() }
        assertFalse("budget exhausted: caller must report the failure", h.recovery.recoverableError(Failure.BUSY))
        assertFalse(h.recovery.recoverableError(Failure.BUSY))
        assertTrue(h.scheduled.isEmpty())
    }

    @Test fun duplicateErrorWhileRetryPendingIsSwallowed() {
        val h = Harness()
        assertTrue(h.recovery.recoverableError(Failure.BUSY))
        assertTrue(h.recovery.recoverableError(Failure.BUSY))
        assertEquals(1, h.scheduled.size)
        assertEquals(1, h.recovery.attemptsUsed)
    }

    @Test fun closeCancelsPendingRetryAndLaterErrorsDoNothing() {
        val h = Harness()
        assertTrue(h.recovery.recoverableError(Failure.BUSY))
        h.recovery.close()
        assertTrue(h.scheduled.isEmpty())
        assertEquals(0, h.warmStarts)
        assertTrue(h.recovery.recoverableError(Failure.BUSY))
        assertTrue(h.scheduled.isEmpty())
        assertTrue(h.failures.isEmpty())
    }

    @Test fun retryFiringAfterCloseIsIgnored() {
        val h = Harness()
        assertTrue(h.recovery.recoverableError(Failure.BUSY))
        val (_, task) = h.scheduled.first()
        h.recovery.close()
        task()
        assertEquals(0, h.warmStarts)
    }

    @Test fun throwingColdRestartReportsStartFailedOnce() {
        val h = Harness(SpeechRecoveryPolicy(warmDelaysMs = emptyList()))
        h.coldThrows = true
        assertTrue(h.recovery.recoverableError(Failure.BUSY))
        h.fire()
        assertEquals(listOf(Failure.START_FAILED), h.failures)
        // Nothing else may be scheduled or reported afterwards.
        assertTrue(h.recovery.recoverableError(Failure.BUSY))
        assertTrue(h.scheduled.isEmpty())
        assertEquals(1, h.failures.size)
    }

    @Test fun throwingWarmStartKeepsTheOriginalNonBusyReason() {
        val h = Harness()
        h.warmThrows = true
        assertTrue(h.recovery.recoverableError(Failure.AUDIO))
        h.fire()
        assertEquals(listOf(Failure.AUDIO), h.failures)
    }
}
