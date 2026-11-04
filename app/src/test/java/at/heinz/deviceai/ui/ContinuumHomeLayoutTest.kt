package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.ContinuumHomeLayout.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Geometry of the start screen on the owner's devices (Galaxy Z Fold7 cover/main display, Samsung phones). */
class ContinuumHomeLayoutTest {
    private val cards = listOf(Slot.MODEL, Slot.LEARNING, Slot.STORAGE, Slot.DEVELOPMENT, Slot.SYSTEM_ACTIVITY)

    @Test fun unfoldingTriggersActualWindowReflowWithoutChangingLearningState() {
        // Stale cover-display Configuration can persist while the root now fills
        // the unfolded display. Layout must be based on the measured width.
        assertTrue(ContinuumHomeLayout.needsWindowWidthReflow(411f, 750f))
        assertTrue(ContinuumHomeLayout.isWide(750f, 790f))
        // Folding back must reflow to the compact phone composition.
        assertTrue(ContinuumHomeLayout.needsWindowWidthReflow(750f, 411f))
        assertFalse(ContinuumHomeLayout.isWide(411f, 900f))
        // IME, system bars and minor dp rounding are not fold events.
        assertFalse(ContinuumHomeLayout.needsWindowWidthReflow(750f, 750f))
        assertFalse(ContinuumHomeLayout.needsWindowWidthReflow(750f, 739f))
        assertFalse(ContinuumHomeLayout.needsWindowWidthReflow(750f, Float.NaN))
        assertFalse(ContinuumHomeLayout.needsWindowWidthReflow(750f, 0f))
    }

    @Test fun compositionFollowsTheWindowSize() {
        assertFalse("Fold7 cover display (411 dp)", ContinuumHomeLayout.isWide(411f, 900f))
        assertFalse("360 dp Samsung phone", ContinuumHomeLayout.isWide(360f, 740f))
        assertTrue("Fold7 main display upright", ContinuumHomeLayout.isWide(750f, 790f))
        assertTrue("Fold7 main display with larger zoom", ContinuumHomeLayout.isWide(656f, 690f))
        assertTrue("Fold7 main display landscape", ContinuumHomeLayout.isWide(832f, 708f))
        assertFalse("phone in landscape stays compact", ContinuumHomeLayout.isWide(800f, 360f))
    }

    /** The November owner references replace the old uniformly scaled October coordinates. */
    @Test fun currentCockpitPreservesPhysicalTouchSizesAcrossWindowWidths() {
        for (width in listOf(320f, 360f, 412f, 656f, 750f, 840f, 1280f)) {
            val layout = if (width >= 600f) ContinuumHomeLayout.wide(width) else ContinuumHomeLayout.phone(width)
            assertTrue(layout.referenceCockpit)
            assertEquals(1f, layout.scale, 0f)
            for (slot in listOf(Slot.SEARCH, Slot.NOTIFICATIONS, Slot.PROFILE, Slot.COMPOSER)) {
                val box = layout.boxes.getValue(slot)
                assertTrue("$width $slot usable target", box.width >= 48f && box.height >= 48f)
            }
        }
    }

    @Test fun bothFoldOrientationsReserveTheActualNavigationRail() {
        for ((width, height) in listOf(656f to 690f, 750f to 790f, 832f to 708f, 840f to 1050f, 1280f to 800f)) {
            val layout = ContinuumHomeLayout.wide(width, height)
            val rail = layout.boxes.getValue(Slot.RAIL)
            assertEquals(ContinuumHomeLayout.railWidthDp(width), rail.width, .001f)
            assertTrue("Readable, bounded navigation rail", rail.width in 80f..120f)
            for ((slot, box) in layout.boxes) {
                if (slot != Slot.RAIL) assertTrue("$slot clears rail", box.left >= rail.right)
            }
        }
    }

    @Test fun unfoldedPortraitRetainsEveryDestinationAndClearAvatarStage() {
        for ((width, height) in listOf(750f to 790f, 656f to 690f, 840f to 1050f, 1000f to 1400f)) {
            val layout = ContinuumHomeLayout.wide(width, height)
            assertTrue(layout.portrait)
            val hero = layout.boxes.getValue(Slot.HERO)
            val composer = layout.boxes.getValue(Slot.COMPOSER)
            assertTrue(hero.bottom < composer.top)
            val actions = ContinuumReferenceLayout.actions.map { layout.boxes.getValue(it) }
            for (box in actions) {
                assertEquals(actions[0].width, box.width, .001f)
                assertEquals(actions[0].height, box.height, .001f)
                assertTrue(box.top > composer.bottom)
            }
            for (slot in cards + listOf(Slot.SYSTEM_STATUS, Slot.WORKERS, Slot.LABEL_EVOLVING)) {
                val box = layout.boxes.getValue(slot)
                assertTrue("$width $slot stays scroll reachable", box.bottom <= layout.contentHeight)
                assertTrue(box.left >= 0f && box.right <= width)
            }
        }
    }

    @Test fun landscapePlacesActualDeviceAndTaskCardsBesideTheStage() {
        // 2026-11-04: landscape uses the ref1/ref3 cockpit (details in ContinuumLandscapeCockpitLayoutTest):
        // system status above the agents in the right column, beside the avatar stage; brain card left of it.
        for ((width, height) in listOf(832f to 708f, 840f to 649f, 1280f to 800f)) {
            val layout = ContinuumHomeLayout.wide(width, height)
            assertFalse(layout.portrait)
            val hero = layout.boxes.getValue(Slot.HERO)
            val system = layout.boxes.getValue(Slot.SYSTEM_STATUS)
            val tasks = layout.boxes.getValue(Slot.WORKERS)
            assertTrue(system.left > hero.right)
            assertEquals(system.left, tasks.left, .001f)
            assertTrue(system.bottom < tasks.top)
            assertTrue(layout.boxes.getValue(Slot.MODEL).right < hero.left)
        }
    }
}
