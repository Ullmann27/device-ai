package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalGenerationDeadlinePolicyTest {
    private val policy = LocalGenerationDeadlinePolicy

    @Test fun nativeModelHasTimeToProduceItsFirstText() {
        assertNull(policy.stalled(119_999, null, 0))
        assertNull(policy.stalled(179_999, null, 0))
        assertEquals(LocalGenerationDeadlinePolicy.StallReason.NO_FIRST_TEXT, policy.stalled(180_000, null, 0))
    }

    @Test fun continuouslyStreamingModelHasNoFixedWallClockDeadline() {
        assertNull(policy.stalled(120_000, 119_000, 2_611))
        assertNull(policy.stalled(300_000, 299_000, 3_800))
        assertNull(policy.stalled(1_800_000, 1_799_990, 12_000))
    }

    @Test fun stalledModelIsCancelledOnlyAfterRealInactivity() {
        assertNull(policy.stalled(260_999, 141_000, 2_611))
        assertEquals(LocalGenerationDeadlinePolicy.StallReason.NO_RECENT_TEXT, policy.stalled(261_000, 141_000, 2_611))
    }

    @Test fun outputBufferIsExplicitlyBounded() {
        assertNull(policy.stalled(120_000, 120_000, 63_999))
        assertNull(policy.stalled(120_000, 120_000, 64_000))
        assertEquals(LocalGenerationDeadlinePolicy.StallReason.OUTPUT_LIMIT, policy.stalled(120_000, 120_000, 64_001))
    }
}
