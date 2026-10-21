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
}
