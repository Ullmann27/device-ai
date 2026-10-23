package at.heinz.deviceai.ui

import org.junit.Assert.*
import org.junit.Test

class ContinuumGridPolicyTest {
    @Test fun columnsUseAvailableContentAndFontRatherThanDeviceCategory() {
        assertEquals(1, ContinuumGridPolicy.columns(280f, 1f, 3))
        assertEquals(2, ContinuumGridPolicy.columns(320f, 1f, 3))
        assertEquals(1, ContinuumGridPolicy.columns(320f, 1.3f, 3))
        assertEquals(3, ContinuumGridPolicy.columns(720f, 1.3f, 3))
        assertEquals(2, ContinuumGridPolicy.columns(720f, 2f, 3))
        assertEquals(1, ContinuumGridPolicy.columns(Float.NaN, 1f, 3))
    }
    @Test fun gaugesUseSymmetricRowsAtLargeFont() {
        assertEquals(4, ContinuumGridPolicy.gaugeColumns(720f, 1.3f))
        assertEquals(2, ContinuumGridPolicy.gaugeColumns(320f, 1.3f))
        assertEquals(1, ContinuumGridPolicy.gaugeColumns(160f, 2f))
    }
}
