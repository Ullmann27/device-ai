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

    @Test fun withoutAHeightTheWideLayoutIsTheScaledReference() {
        val a = ContinuumHomeLayout.wide(840f); val b = ContinuumHomeLayout.wide(756f)
        for ((slot, box) in a.boxes) {
            val s = b.boxes.getValue(slot)
            assertEquals("$slot", box.left * .9f, s.left, 1e-3f); assertEquals("$slot", box.bottom * .9f, s.bottom, 1e-3f)
        }
        assertEquals(649f * .9f, b.contentHeight, 1e-3f)
    }

    @Test fun theRailWidthIsTheRailSlotOfTheScaledReference() {
        for (w in listOf(656f, 750f, 832f, 840f)) {
            assertEquals("$w dp", ContinuumHomeLayout.wide(w).boxes.getValue(Slot.RAIL).width, ContinuumHomeLayout.railWidthDp(w), 1e-3f)
        }
        assertEquals(76f, ContinuumHomeLayout.railWidthDp(840f), 0f)
    }

    @Test fun unfoldedPortraitKeepsExactThreeByTwoCardAlignment() {
        for ((w,h) in listOf(750f to 790f, 656f to 690f, 840f to 1010f, 1000f to 1400f)) {
            assertTrue(ContinuumHomeLayout.isPortraitFold(w,h))
            val l=ContinuumHomeLayout.wide(w,h)
            val first=listOf(Slot.MODEL,Slot.LEARNING,Slot.STORAGE).map { l.boxes.getValue(it) }
            val second=listOf(Slot.DEVELOPMENT,Slot.SYSTEM_ACTIVITY,Slot.WORKERS).map { l.boxes.getValue(it) }
            val scale=l.scale
            // All six glass cards have EXACTLY the same size, gap and column boundaries.
            for(i in 0..2){
                assertEquals("column $i x",first[i].left,second[i].left,.001f)
                assertEquals("column $i width",first[i].width,second[i].width,.001f)
                assertEquals("column $i height",first[i].height,second[i].height,.001f)
            }
            for(box in first+second){
                assertEquals(first[0].width,box.width,.001f)
                assertEquals(first[0].height,box.height,.001f)
                assertTrue(box.left>=0f && box.right<=w+.001f)
                assertTrue(box.bottom<=l.contentHeight+.001f)
            }
            assertEquals(12f*scale,first[0].left,.001f)
            assertEquals(12f*scale,second[0].left,.001f)
            assertEquals(first[0].bottom+12f*scale,second[0].top,.001f)
            assertTrue(l.boxes[Slot.RAIL]==null)
            assertTrue(l.boxes[Slot.MODEL_PILL]==null)
            assertTrue(l.boxes.getValue(Slot.HERO).bottom<=first[0].top)
            for ((left,right) in listOf(
                Slot.LABEL_CONNECTED to Slot.LABEL_THINKING,
                Slot.LABEL_LEARNING to Slot.LABEL_ANALYSING,
                Slot.LABEL_DEVELOPING to Slot.LABEL_EVOLVING
            )) {
                val a=l.boxes.getValue(left);val b=l.boxes.getValue(right)
                assertEquals(a.top,b.top,.001f)
                assertEquals(a.height,b.height,.001f)
                assertTrue("portrait controls are larger than legacy wide controls",a.height>=75f*scale)
                assertTrue("left action exceeds 200 reference dp",a.width>=200f*scale)
            }
        }
    }

    @Test fun landscapeStillKeepsItsExistingRailAndLegacyCards() {
        for ((w,h) in listOf(832f to 708f,840f to 649f)) {
            assertFalse(ContinuumHomeLayout.isPortraitFold(w,h))
            val reference=ContinuumHomeLayout.wide(w)
            val full=ContinuumHomeLayout.wide(w,h)
            for(c in cards) {
                assertEquals(reference.boxes.getValue(c).width,full.boxes.getValue(c).width,.001f)
                assertEquals(reference.boxes.getValue(c).height,full.boxes.getValue(c).height,.001f)
            }
            assertTrue(full.boxes[Slot.RAIL]!=null)
            assertTrue(full.boxes[Slot.WORKERS]==null)
        }
    }
}
