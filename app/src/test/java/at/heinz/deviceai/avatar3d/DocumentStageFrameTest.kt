package at.heinz.deviceai.avatar3d

import org.junit.Assert.*
import org.junit.Test

class DocumentStageFrameTest {
    private val frame = DocumentStageFrame("window-1", 2208, 1840, 160f, 620f,
        1700f, 960f, -18f, 0f, .7f, false)

    @Test fun rejectsInvalidGeometryBeforeNativeCalls() {
        assertTrue(frame.valid())
        assertFalse(frame.copy(width = Float.NaN).valid())
        assertFalse(frame.copy(height = 0f).valid())
        assertFalse(frame.copy(left = Float.POSITIVE_INFINITY).valid())
        assertFalse(frame.copy(cameraDistance = 0f).valid())
        assertFalse(frame.copy(opacity = 1.1f).valid())
        assertFalse(frame.copy(yawDegrees = 90f).valid())
        assertFalse(frame.copy(viewportHeight = Int.MAX_VALUE).valid())
    }

    @Test fun visibleFlightMayStartBeyondEitherEdge() {
        assertTrue(frame.copy(left = -frame.width).valid())
        assertTrue(frame.copy(left = frame.viewportWidth.toFloat()).valid())
        assertFalse(frame.copy(left = frame.viewportWidth * 9f).valid())
    }

    @Test fun capsBothBuffersWhilePreservingAspect() {
        for ((width, height) in listOf(2208 to 1840, 1080 to 2520, 884 to 760, 412 to 915)) {
            val f = frame.copy(viewportWidth = width, viewportHeight = height, width = width * .8f, height = height * .5f)
            val (w, h) = f.bufferSize()
            assertTrue(w in 1..1280 && h in 1..1280)
            assertTrue(w <= width && h <= height)
            assertEquals(width.toDouble() / height, w.toDouble() / h, .003)
        }
    }

    @Test fun acceptsOnlyBoundedNativePageRectangles() {
        val page = frame.copy(pageLeft = .1f, pageTop = .2f, pageWidth = .4f, pageHeight = .7f,
            pageRectValid = true)
        assertTrue(page.valid())
        assertFalse(page.copy(pageWidth = 0f).valid())
        assertFalse(page.copy(pageLeft = -.1f).valid())
        assertFalse(page.copy(pageTop = .8f).valid())
        assertFalse(page.copy(pageHeight = Float.NaN).valid())
        assertTrue(frame.copy(pageRectValid = false).valid())
    }
}
