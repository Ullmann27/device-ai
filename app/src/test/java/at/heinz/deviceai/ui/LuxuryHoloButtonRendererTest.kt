package at.heinz.deviceai.ui

import org.junit.Assert.*
import org.junit.Test

class LuxuryHoloButtonRendererTest {
    private val box = JarvisShellLayout.Box(100f, 40f, 250f, 140f)

    @Test fun lightIsDirectionalBoundedAndDeterministic() {
        val closer = LuxuryHoloButtonRenderer.highlightProximity(box, 175f, 90f)
        val far = LuxuryHoloButtonRenderer.highlightProximity(box, 4_000f, 2_000f)
        assertTrue(closer > far)
        assertTrue(closer in .12f..72f)
        assertTrue(far in .12f..72f)
        assertEquals(closer, LuxuryHoloButtonRenderer.highlightProximity(box, 175f, 90f), 0f)
    }

    @Test fun invalidAndEmptyLightInputsCannotChangeTheCockpit() {
        assertEquals(0f, LuxuryHoloButtonRenderer.highlightProximity(box, Float.NaN, 0f), 0f)
        assertEquals(0f, LuxuryHoloButtonRenderer.highlightProximity(box, Float.POSITIVE_INFINITY, 0f), 0f)
        assertEquals(0f, LuxuryHoloButtonRenderer.highlightProximity(
            JarvisShellLayout.Box(0f, 0f, 0f, 50f), 1f, 1f), 0f)
    }
}
