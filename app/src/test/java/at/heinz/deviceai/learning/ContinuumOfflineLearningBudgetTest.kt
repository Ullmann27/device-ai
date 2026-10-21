package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test

class ContinuumOfflineLearningBudgetTest {
    private val safe = ContinuumLearningResources(82, false, 0, 1_000_000_000L)

    @Test fun acceptsHealthyDeviceResources() {
        assertNull(ContinuumOfflineLearningBudget.deferralReason(safe))
        assertEquals("BATTERY_UNKNOWN", ContinuumOfflineLearningBudget.deferralReason(
            ContinuumLearningResources(null, false, null, null)))
    }

    @Test fun rejectsLowBatteryHeatAndStorage() {
        val gate = ContinuumOfflineLearningBudget
        assertEquals("BATTERY_LOW", gate.deferralReason(safe.copy(batteryLow = true)))
        assertNull(gate.deferralReason(
            safe.copy(batteryPercent = gate.MIN_BATTERY_PERCENT)))
        assertEquals("BATTERY_LOW", gate.deferralReason(
            safe.copy(batteryPercent = gate.MIN_BATTERY_PERCENT - 1)))
        assertEquals("THERMAL_LIMIT", gate.deferralReason(
            safe.copy(thermalStatus = 2)))
        assertEquals("STORAGE_LOW", gate.deferralReason(
            safe.copy(storageBytesAvailable = gate.MIN_STORAGE_BYTES - 1)))
        assertNull(gate.deferralReason(safe.copy(
            batteryPercent = gate.MIN_BATTERY_PERCENT + 1,
            thermalStatus = 1, storageBytesAvailable = gate.MIN_STORAGE_BYTES)))
    }

    @Test fun learningDeadlineDoesNotResetWhenAnotherStageStarts() {
        val gate = ContinuumOfflineLearningBudget
        assertFalse(gate.deadlineReached(100L, 100L))
        assertFalse(gate.deadlineReached(100L, 100L + gate.MAX_WALL_MILLIS - 1))
        assertTrue(gate.deadlineReached(100L, 100L + gate.MAX_WALL_MILLIS))
        assertTrue(gate.deadlineReached(100L, 99L))
    }

    @Test fun observedBatteryIsOverflowSafeAndUnknownFailsClosed() {
        val gate=ContinuumOfflineLearningBudget
        assertEquals(20,gate.batteryPercent(20,100))
        assertEquals(100,gate.batteryPercent(Int.MAX_VALUE,Int.MAX_VALUE))
        for((level,scale) in listOf(-1 to 100,20 to 0,101 to 100))
            assertNull(gate.batteryPercent(level,scale))
    }

    @Test fun runningResearchStopsOnResourceDropAndCannotResumeTheSameGeneration() {
        var now=0L
        var observation=safe.copy(batteryPercent=20)
        var reads=0
        val budget=ContinuumLearningRunBudget(0,15_000,{now},{reads++;observation})
        assertFalse(budget.cancelled())
        observation=observation.copy(batteryPercent=19)
        now=1499
        assertFalse(budget.cancelled())
        assertEquals(1,reads)
        now=1500
        assertTrue(budget.cancelled())
        assertEquals("BATTERY_LOW",budget.deferredReason)
        observation=safe
        now=3000
        assertTrue(budget.cancelled())
        assertEquals(2,reads)
    }

    @Test fun researchRetainsHeatStorageDeadlineAndSourceOutcomeGates() {
        for((observation,reason) in listOf(safe.copy(thermalStatus=2) to "THERMAL_LIMIT",
            safe.copy(storageBytesAvailable=0) to "STORAGE_LOW",safe.copy(batteryPercent=null) to "BATTERY_UNKNOWN")) {
            val b=ContinuumLearningRunBudget(0,15_000,{0},{observation})
            assertTrue(b.cancelled());assertEquals(reason,b.deferredReason)
        }
        val expired=ContinuumLearningRunBudget(0,15_000,{15_000},{safe})
        assertTrue(expired.cancelled());assertEquals("TIME_BUDGET",expired.deferredReason)
        for(phase in listOf("SOURCE_UNAVAILABLE_MISSING_PAGE","FAILED_ARCHIVE_SOURCE_IOException","TRAINING_ACCEPTED"))
            assertEquals(phase,ContinuumPublicStudyCycleResult.withDeferral(phase,"TIME_BUDGET"))
        val phase=ContinuumPublicStudyCycleResult.withDeferral("INTERRUPTED","THERMAL_LIMIT")
        assertEquals("DEFERRED_THERMAL_LIMIT",phase)
        assertEquals("DEFERRED",ContinuumPublicStudyCycleResult.outcome(phase))
        assertFalse(ContinuumPublicStudyCycleResult.markTopicCompleted(phase))
    }
}
