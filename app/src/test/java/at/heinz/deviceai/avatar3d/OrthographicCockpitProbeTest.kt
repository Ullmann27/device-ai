package at.heinz.deviceai.avatar3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrthographicCockpitProbeTest {
    @Test fun seventyPercentAnchorLeavesOnlyThirtyPercentOnRight() {
        val w = OrthographicCockpitProbe.fromCurrentFilamentMath(
            widthPx = 252, heightPx = 427,
            focusHalfWidthWorld = 0.10, focusHalfHeightWorld = 0.20,
            closeUp = 0.92, anchorX = 0.70
        )
        assertEquals(0.70, w.projectedCenterFraction, 0.000001)
        assertEquals(0.30, w.availableRightFraction, 0.000001)
        assertEquals(176.4, w.pixelX(0.0), 0.001)
        assertEquals(252.0, w.pixelX(w.rightWorld), 0.001)
    }

    @Test fun symmetricAnchorAloneDoesNotProveTheHeadFits() {
        val w = OrthographicCockpitProbe.fromCurrentFilamentMath(
            252, 427, 0.10, 0.20, 0.92, 0.50
        )
        assertEquals(0.50, w.availableRightFraction, 0.000001)
        val clip = w.clipping(w.leftWorld - 0.01, w.rightWorld + 0.01)
        assertFalse(clip.fits)
        assertTrue(clip.leftOverhangPx > 0.0)
        assertTrue(clip.rightOverhangPx > 0.0)
    }

    @Test fun actualProjectedMeshBoundsAreRequiredToDeclareNoClipping() {
        val w = OrthographicCockpitProbe.fromCurrentFilamentMath(
            252, 427, 0.10, 0.20, 0.92, 0.70
        )
        assertTrue(w.clipping(w.leftWorld + 0.01, w.rightWorld - 0.01).fits)
        assertFalse(w.clipping(w.leftWorld + 0.01, w.rightWorld + 0.01).fits)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectZeroSizeViewport() {
        OrthographicCockpitProbe.fromCurrentFilamentMath(0, 427, 0.1, 0.2, 0.92, 0.70)
    }
}
