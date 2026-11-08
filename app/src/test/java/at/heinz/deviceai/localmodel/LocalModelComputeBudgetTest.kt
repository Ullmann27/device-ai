package at.heinz.deviceai.localmodel

import at.heinz.deviceai.localmodel.LocalModelComputeBudget.CoolingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fold7 heat finding (#642): inference must not use every core by default. */
class LocalModelComputeBudgetTest {
    @Test fun fold7EightCoresUseFourThreadsBalancedAndTwoInCoolMode() {
        assertEquals(4, LocalModelComputeBudget.inferenceThreads(8, 0, CoolingMode.BALANCED))
        assertEquals(2, LocalModelComputeBudget.inferenceThreads(8, 0, CoolingMode.COOL))
    }

    @Test fun alreadyWarmDeviceLoadsWithAtMostTwoThreads() {
        assertEquals(2, LocalModelComputeBudget.inferenceThreads(8, LocalModelComputeBudget.MODERATE, CoolingMode.BALANCED))
        assertEquals(4, LocalModelComputeBudget.inferenceThreads(8, 1, CoolingMode.BALANCED))
        assertEquals(2, LocalModelComputeBudget.inferenceThreads(8, 3, CoolingMode.BALANCED))
    }

    @Test fun missingThermalStatusIsNotTreatedAsCoolButKeepsTheBalancedDefault() {
        assertEquals(4, LocalModelComputeBudget.inferenceThreads(8, null, CoolingMode.BALANCED))
    }

    @Test fun neverZeroAndNeverMoreThanHalfTheCores() {
        for (cores in 1..16) for (mode in CoolingMode.entries) for (status in listOf(null, 0, 2, 3)) {
            val threads = LocalModelComputeBudget.inferenceThreads(cores, status, mode)
            assertTrue("cores=$cores mode=$mode status=$status -> $threads", threads >= 1)
            assertTrue("cores=$cores mode=$mode status=$status -> $threads", threads <= maxOf(1, cores / 2))
            assertTrue(threads <= 4)
        }
        assertEquals(1, LocalModelComputeBudget.inferenceThreads(1, 0, CoolingMode.BALANCED))
        assertEquals(1, LocalModelComputeBudget.inferenceThreads(2, 0, CoolingMode.COOL))
    }

    @Test fun preferenceParsingIsLenientAndDefaultsToBalanced() {
        assertEquals(CoolingMode.COOL, LocalModelComputeBudget.parseMode("cool"))
        assertEquals(CoolingMode.BALANCED, LocalModelComputeBudget.parseMode(null))
        assertEquals(CoolingMode.BALANCED, LocalModelComputeBudget.parseMode("unbekannt"))
    }
}
