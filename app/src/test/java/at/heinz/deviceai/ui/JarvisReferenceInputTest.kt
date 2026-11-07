package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisShellLayout.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisReferenceInputTest {
    @Test fun referenceComposerHasIndependentReachableSpace() {
        for ((w, h) in listOf(884f to 760f, 864f to 1100f, 1280f to 800f)) {
            for (compact in listOf(false, true)) {
                val shell = JarvisShellLayout.create(w, h, compactPresentation = compact)
                assertTrue(shell.ref5Grid)
                assertNotNull(shell[Slot.INPUT])
                val input = shell[Slot.INPUT]!!
                assertTrue(input.height >= 48f)
                assertTrue(input.top >= shell[Slot.AVATAR]!!.bottom)
                assertTrue(input.top >= shell[Slot.DIALOG]!!.bottom)
                assertTrue(input.bottom + 4f <= shell[Slot.TILES]!!.top)
                assertEquals(10, shell.tileBoxes().size)
                assertEquals(h, shell.viewportHeight, 0f)
                assertTrue(shell.height >= shell[Slot.STATUS]!!.bottom)
            }
        }
    }

    @Test fun inputPreservesOriginalAvatarAndSphereBounds() {
        val original = JarvisCockpitGrid.create(884f, 760f)
        val shell = JarvisShellLayout.create(884f, 760f)
        assertEquals(original[JarvisCockpitGrid.Area.HEAD], shell.headBounds)
        assertEquals(original[JarvisCockpitGrid.Area.ORB], shell.orbBounds)
        assertEquals(original[JarvisCockpitGrid.Area.DIALOG], shell[Slot.DIALOG])
        assertEquals(original[JarvisCockpitGrid.Area.TILES].height,
            shell[Slot.TILES]!!.height, .01f)
        assertEquals(original[JarvisCockpitGrid.Area.ROW_MODEL].height,
            shell[Slot.ROW_MODEL]!!.height, .01f)
    }

    @Test fun remeasurementDoesNotAccumulateContentHeight() {
        val first = JarvisShellLayout.create(884f, 760f)
        repeat(10) {
            assertEquals(first, JarvisShellLayout.create(first.width, first.viewportHeight))
        }
        for (font in listOf(1f, 1.3f, 2f)) {
            val phone = JarvisShellLayout.create(360f, 740f, font)
            assertTrue(!phone.ref5Grid)
            assertNotNull(phone[Slot.INPUT])
            assertEquals(52f * font, phone[Slot.INPUT]!!.height, .01f)
        }
    }
}
