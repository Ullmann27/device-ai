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
}
