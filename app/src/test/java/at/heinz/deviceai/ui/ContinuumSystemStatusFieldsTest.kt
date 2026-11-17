package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContinuumSystemStatusFieldsTest {
    @Test
    fun unmeasuredFieldsStayNull() {
        val status = ContinuumDashboardState.SystemStatus()
        assertNull(status.freeRamBytes)
        assertNull(status.thermalStatus)
        assertNull(status.charging)
    }

    @Test
    fun measuredFieldsArePassedThroughUnchanged() {
        val status = ContinuumDashboardState.SystemStatus(freeRamBytes = 3_221_225_472L, thermalStatus = 1, charging = true)
        assertEquals(3_221_225_472L, status.freeRamBytes)
        assertEquals(1, status.thermalStatus)
        assertEquals(true, status.charging)
    }
}
