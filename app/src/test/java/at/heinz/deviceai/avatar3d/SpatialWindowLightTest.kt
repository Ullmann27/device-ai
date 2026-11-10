package at.heinz.deviceai.avatar3d

import org.junit.Assert.*
import org.junit.Test

class SpatialWindowLightTest {
    @Test fun verifiedWindowAccentTintsExistingLightWithoutChangingFaceGeometry() {
        val base = SpatialWindowLight.tint(null, .75f, false, true)
        val window = SpatialWindowLight.tint(0xFFAA40FF.toInt(), .75f, true, true)
        assertTrue(window.red > base.red)
        assertTrue(window.green < base.green)
        assertTrue(window.blue in 0f..1f)
        assertTrue(window.lumens >= base.lumens)
        assertTrue(window.lumens <= 36_000f)
        assertEquals(base, SpatialWindowLight.tint(0xFFAA40FF.toInt(), .75f, false, true))
    }

    @Test fun thermalAndPermissionFallbackRestoreOriginalCyanFill() {
        val disabled = SpatialWindowLight.tint(0xFFFF0000.toInt(), 1f, true, false)
        val idle = SpatialWindowLight.tint(null, 1f, false, false)
        assertEquals(idle, disabled)
        assertEquals(AvatarCoreLighting.BASE_LUMENS, disabled.lumens, 0f)
        assertEquals(1f, disabled.blue, 0f)
        assertEquals(0.18f, disabled.red, 0f)
    }

    @Test fun rightWindowShiftsCatchlightToTheMatchingFaceSideAndRestoresOnClose() {
        val violet = 0xFFAA40FF.toInt()
        val left = SpatialWindowLight.tint(violet, .75f, true, true, gazeX = -1f)
        val right = SpatialWindowLight.tint(violet, .75f, true, true, gazeX = 1f)
        val closed = SpatialWindowLight.tint(violet, .75f, false, true, gazeX = 1f)
        val idle = SpatialWindowLight.tint(null, .75f, false, true)
        assertTrue("viewer-right glass moves the fill toward +X", right.positionX > left.positionX)
        assertTrue(right.side > 0f && left.side < 0f)
        assertEquals(SpatialWindowLight.REST_X + SpatialWindowLight.SIDE_SPAN, right.positionX, 0f)
        assertEquals(SpatialWindowLight.REST_X - SpatialWindowLight.SIDE_SPAN, left.positionX, 0f)
        assertEquals(SpatialWindowLight.WINDOW_ROUGHNESS, right.roughness, 0f)
        assertEquals(idle, closed)
        assertEquals(SpatialWindowLight.AUTHORED_SKIN_ROUGHNESS, closed.roughness, 0f)
        assertEquals(SpatialWindowLight.REST_X, closed.positionX, 0f)
        assertTrue("violet accent raises red versus the cyan fill", right.red > idle.red)
        assertTrue(right.green < idle.green)
    }
}
