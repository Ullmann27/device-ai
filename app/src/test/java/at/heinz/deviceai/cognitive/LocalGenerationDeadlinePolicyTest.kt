package at.heinz.deviceai.cognitive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalGenerationDeadlinePolicyTest {
    @Test fun normalWindowRemainsBounded() {
        assertFalse(LocalGenerationDeadlinePolicy.expired(119_999, null, 0))
        assertTrue(LocalGenerationDeadlinePolicy.expired(120_000, null, 0))
    }

    @Test fun recentStreamingTextCanFinishWithinExtendedWindow() {
        assertFalse(LocalGenerationDeadlinePolicy.expired(120_000, 119_000, 2_611))
        assertFalse(LocalGenerationDeadlinePolicy.expired(154_000, 151_000, 3_400))
        assertTrue(LocalGenerationDeadlinePolicy.expired(180_000, 179_999, 5_000))
    }

    @Test fun stoppedProgressCannotHoldRuntimeOpen() {
        assertTrue(LocalGenerationDeadlinePolicy.expired(133_001, 121_000, 2_611))
        assertFalse(LocalGenerationDeadlinePolicy.expired(133_000, 121_000, 2_611))
        assertTrue(LocalGenerationDeadlinePolicy.expired(120_100, 120_100, 8_000))
        assertTrue(LocalGenerationDeadlinePolicy.expired(120_000, null, 0))
    }

    @Test fun capturesRemainBoundedForInvalidOrEmptyText() {
        assertTrue(LocalGenerationDeadlinePolicy.expired(121_000, 120_999, 0))
        assertTrue(LocalGenerationDeadlinePolicy.expired(121_000, 120_999, 8_001))
    }
}
