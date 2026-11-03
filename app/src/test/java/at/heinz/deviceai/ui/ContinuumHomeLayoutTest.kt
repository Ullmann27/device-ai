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

    @Test fun widthChangesColumnWidthsButNeverScaleTextOrTouchTargets() {
        for (width in listOf(320f, 412f, 756f, 840f, 1280f)) {
            val layout = if (width < 600f) ContinuumHomeLayout.phone(width) else ContinuumHomeLayout.wide(width)
            assertEquals(1f, layout.scale, 0f)
            assertEquals(48f, layout.boxes.getValue(Slot.SEARCH).width, 0f)
        }
    }

    @Test fun theRailWidthIsTheRailSlotOfTheScaledReference() {
        for (w in listOf(656f, 750f, 832f, 840f)) {
            assertEquals("$w dp", ContinuumHomeLayout.wide(w).boxes.getValue(Slot.RAIL).width, ContinuumHomeLayout.railWidthDp(w), 1e-3f)
        }
        assertEquals(76f, ContinuumHomeLayout.railWidthDp(840f), 0f)
    }

    @Test fun portraitFoldReflowsIntoTwoColumnsBelowUnobstructedStage() {
        for ((w, h) in listOf(600f to 840f, 750f to 790f, 840f to 1010f)) {
            val layout = ContinuumHomeLayout.wide(w, h)
            val model = layout.boxes.getValue(Slot.MODEL)
            val learning = layout.boxes.getValue(Slot.LEARNING)
            val storage = layout.boxes.getValue(Slot.STORAGE)
            assertEquals(model.top, learning.top, 0f)
            assertEquals(model.left, storage.left, 0f)
            assertEquals(model.bottom + 12f, storage.top, 0f)
            assertEquals(model.width, learning.width, .001f)
            assertTrue(layout[Slot.RAIL] == null)
            val hero = layout.boxes.getValue(Slot.HERO)
            for (slot in Slot.entries.filter { it.name.startsWith("LABEL_") }) {
                assertTrue("$slot remains below the head", layout.boxes.getValue(slot).top >= hero.bottom)
            }
        }
    }

    @Test fun landscapeKeepsExistingRailOffsetAndEveryDestination() {
        for ((w, h) in listOf(832f to 708f, 840f to 649f, 1280f to 800f)) {
            val layout = ContinuumHomeLayout.wide(w, h)
            val rail = requireNotNull(layout[Slot.RAIL])
            assertEquals(ContinuumHomeLayout.railWidthDp(w), rail.width, 0f)
            for (slot in cards + Slot.WORKERS + Slot.SYSTEM_STATUS) {
                val box = requireNotNull(layout[slot])
                assertTrue(box.left >= rail.right)
                assertTrue(box.bottom <= layout.contentHeight)
            }
        }
    }

    @Test fun largeFontsReflowInsteadOfShrinkingText() {
        val normal = ContinuumHomeLayout.wide(600f, 840f, 1f)
        val large = ContinuumHomeLayout.wide(600f, 840f, 1.6f)
        assertEquals(normal[Slot.MODEL]!!.top, normal[Slot.LEARNING]!!.top, 0f)
        assertTrue(large[Slot.LEARNING]!!.top > large[Slot.MODEL]!!.bottom)
        assertTrue(large[Slot.MODEL]!!.height > normal[Slot.MODEL]!!.height)
        assertEquals(1f, large.scale, 0f)
        for ((w, h) in listOf(320f to 720f, 412f to 915f, 600f to 840f, 840f to 960f, 1280f to 800f)) {
            for (font in listOf(1.3f, 1.6f, 2f)) {
                val l = if (w < 600f) ContinuumHomeLayout.phone(w, font) else ContinuumHomeLayout.wide(w, h, font)
                val boxes = l.boxes.filterKeys { it != Slot.NAV && it != Slot.RAIL }.values.toList()
                for (i in boxes.indices) for (j in i + 1 until boxes.size) {
                    val a = boxes[i]; val b = boxes[j]
                    assertFalse("$w/$font intersect", a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom)
                }
            }
        }
    }
}
