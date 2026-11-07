package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test

class ContinuumDualCoreAutoStudyTest {
    @Test fun explicitConsentAndTimeBudgetAreRequired() {
        val policy=ContinuumDualCoreAutoStudy
        assertFalse(policy.shouldStart(false,15_000L,0))
        assertFalse(policy.shouldStart(true,policy.MIN_REMAINING_MILLIS-1,0))
        assertTrue(policy.shouldStart(true,policy.MIN_REMAINING_MILLIS,0))
        assertTrue(policy.shouldStart(true,15_000L,11))
        assertFalse(policy.shouldStart(true,15_000L,12))
        assertFalse(policy.shouldStart(true,15_000L,-1))
        assertFalse(policy.shouldStart(true,-1L,0))
    }
}
