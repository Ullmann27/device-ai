package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AgslHologramEdgeCoordinatesTest {
    @Test fun overlayPixelBoundsMapToUnitUv() {
        val left = 120f; val top = 80f; val width = 400f; val height = 240f
        val origin = AgslHologramEdgeCoordinates.uv(left, top, left, top, width, height)
        assertEquals(0f, origin.first, 1e-5f)
        assertEquals(0f, origin.second, 1e-5f)
        val far = AgslHologramEdgeCoordinates.uv(left + width, top + height, left, top, width, height)
        assertEquals(1f, far.first, 1e-5f)
        assertEquals(1f, far.second, 1e-5f)
        val center = AgslHologramEdgeCoordinates.uv(left + width / 2f, top + height / 2f, left, top, width, height)
        assertEquals(.5f, center.first, 1e-5f)
        assertEquals(.5f, center.second, 1e-5f)
    }

    @Test fun dpConvertedBoundsWouldMissThePixelCentre() {
        val left = 120f; val top = 80f; val width = 400f; val height = 240f
        val density = 2.5f
        val wrong = AgslHologramEdgeCoordinates.uv(
            left + width / 2f, top + height / 2f,
            left / density, top / density, width / density, height / density)
        assertTrueOffCenter(wrong.first)
        assertTrueOffCenter(wrong.second)
    }

    private fun assertTrueOffCenter(uv: Float) {
        org.junit.Assert.assertTrue("dp-scaled uniforms must not land on 0.5, was $uv", kotlin.math.abs(uv - .5f) > .2f)
    }
}
