package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisCockpitGrid.Area
import at.heinz.deviceai.ui.JarvisShellLayout.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisExternalRailLayoutTest {
    @Test fun aRealActivityRailDoesNotReserveOrDrawAnotherRailInsideTheHome() {
        val shell = JarvisShellLayout.forContent(754f, 760f, externalRailWidthDp = 130f)
        assertNull(shell[Slot.NAV_RAIL])
        assertEquals(JarvisShellLayout.FormFactor.FOLD_OPEN, shell.formFactor)
        assertTrue(shell.ref5Grid)
        val grid = requireNotNull(shell.grid)
        assertEquals(548f / 1536f * 884f - 130f, grid[Area.HEAD].left, .01f)
        assertTrue("The released rail space belongs to the main content", grid[Area.DIALOG].left < 20f)
        assertTrue(grid[Area.ORB].left > grid[Area.HEAD].right)
        assertTrue(grid[Area.MODULES].left > grid[Area.ORB].right)
        assertFalse("Rail has no competing visual or hit region", Area.RAIL in grid.areas)
        shell.boxes.forEach { (slot, box) ->
            assertTrue("$slot remains inside the actual pane", box.left >= 0f && box.right <= 754f && box.width > 0f)
        }
        assertEquals(10, shell.tileBoxes().size)
    }

    @Test fun contentCoordinatesStillMatchTheFullWindowReferenceAfterTheSingleOffset() {
        val full = JarvisShellLayout.create(884f, 760f)
        val content = JarvisShellLayout.forContent(754f, 760f, externalRailWidthDp = 130f)
        for (slot in listOf(Slot.AVATAR, Slot.DIALOG, Slot.TILES, Slot.ROW_MEMORY, Slot.ROW_MODEL)) {
            val before = requireNotNull(full[slot])
            val after = requireNotNull(content[slot])
            assertEquals(before.left, after.left + 130f, .001f)
            assertEquals(before.right, after.right + 130f, .001f)
            assertEquals(before.top, after.top, .001f)
            assertEquals(before.bottom, after.bottom, .001f)
        }
    }

    @Test fun aStandaloneShellRetainsItsOwnNavigation() {
        assertEquals(JarvisShellLayout.create(884f, 760f), JarvisShellLayout.forContent(884f, 760f))
        assertEquals(JarvisShellLayout.create(412f, 915f), JarvisShellLayout.forContent(412f, 915f))
    }
}
