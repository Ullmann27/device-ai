package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class ProjectionCropGeometryTest {
    private val display = LivePixelRect(0, 0, 2184, 1968)
    private val own = LiveWindowRecord(3, "at.heinz.deviceai", LiveWindowKind.APPLICATION,
        LivePixelRect(0, 0, 1088, 1968), 3, true, true)
    private val app = LiveWindowRecord(7, "com.whatsapp", LiveWindowKind.APPLICATION,
        LivePixelRect(1096, 0, 2184, 1968), 2, false, false)
    private val selection = LiveWindowSafety.Selection(app, emptyList())

    @Test fun cropMapsSplitScreenInwardWithoutAnyOwnPixel() {
        val crop = ProjectionCropGeometry.crop(display, 2184, 1968, 1920, 1730, selection, own)!!
        assertEquals(964, crop.left)
        assertEquals(1920, crop.right)
        assertEquals(0, crop.top)
        assertEquals(1730, crop.bottom)
        assertTrue(crop.left.toDouble() / 1920 * 2184 >= app.bounds.left)
    }

    @Test fun singleAppOrStaleRotationDimensionsCannotPretendFullDisplay() {
        assertNull(ProjectionCropGeometry.crop(display, 1088, 1968, 1920, 1730, selection, own))
        assertNull(ProjectionCropGeometry.crop(display, 1968, 2184, 1920, 1730, selection, own))
        assertNull(ProjectionCropGeometry.crop(display, 0, 0, 1920, 1730, selection, own))
    }

    @Test fun ownOverlappingAndFullscreenTargetAreRejected() {
        assertNull(ProjectionCropGeometry.crop(display, 2184, 1968, 1920, 1730,
            selection.copy(target = app.copy(bounds = display)), own))
        assertNull(ProjectionCropGeometry.crop(display, 2184, 1968, 1920, 1730,
            selection, own.copy(bounds = app.bounds)))
    }

    @Test fun keyboardAndSystemBarPixelsStayOutsideCrop() {
        val keyboard = own.copy(id = 4, kind = LiveWindowKind.INPUT_METHOD,
            bounds = LivePixelRect(0, 1300, 2184, 1968))
        val status = own.copy(id = 5, kind = LiveWindowKind.SYSTEM,
            bounds = LivePixelRect(0, 0, 2184, 50))
        val crop = ProjectionCropGeometry.crop(display, 2184, 1968, 1920, 1730,
            selection.copy(obstructions = listOf(keyboard, status)), own)!!
        assertTrue(crop.top.toDouble() / 1730 * 1968 >= 50)
        assertTrue(crop.bottom.toDouble() / 1730 * 1968 <= 1300)
    }

    @Test fun invalidAndOffDisplayGeometryFailsClosed() {
        assertNull(ProjectionCropGeometry.crop(display, 2184, 1968, 0, 1730, selection, own))
        assertNull(ProjectionCropGeometry.crop(display, 2184, 1968, 1920, 1730,
            selection.copy(target = app.copy(bounds = LivePixelRect(1096, -1, 2184, 1968))), own))
    }
}
