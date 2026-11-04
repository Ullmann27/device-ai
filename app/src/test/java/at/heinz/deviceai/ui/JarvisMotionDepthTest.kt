package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisMotionDepthTest {
    @Test fun deeperLevelsAreSmallerBlurrierAndDriftNeverStops() {
        val near = JarvisMotion.depth(1, 100f, 100f, 200f, 200f, 0L)
        val far = JarvisMotion.depth(3, 100f, 100f, 200f, 200f, 0L)
        assertTrue(far.scale < near.scale && far.blurDp > near.blurDp && far.alpha < near.alpha)
        assertNotEquals(JarvisMotion.cameraDrift(1000L), JarvisMotion.cameraDrift(5000L))
        assertEquals(JarvisMotion.Camera(0f, 0f, 0f), JarvisMotion.cameraDrift(5000L, animatorScale = 0f))
        assertEquals(1f, JarvisMotion.depth(0, 200f, 200f, 200f, 200f, 0L, animatorScale = 0f).scale, 1e-6f)
    }
}
