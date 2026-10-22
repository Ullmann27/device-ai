package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.ContinuumHomeLayout.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Geometry of the start screen on the owner's devices (Galaxy Z Fold7 cover/main display, Samsung phones). */
class ContinuumHomeLayoutTest {
    private val cards = listOf(Slot.MODEL, Slot.LEARNING, Slot.STORAGE, Slot.DEVELOPMENT, Slot.SYSTEM_ACTIVITY)

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

    @Test fun anUprightFoldIsFilledAndTheCardsKeepTheirShape() {
        for ((w, h) in listOf(750f to 790f, 656f to 690f, 832f to 708f, 840f to 649f, 1000f to 1400f)) {
            val ref = ContinuumHomeLayout.wide(w)
            val l = ContinuumHomeLayout.wide(w, h)
            for ((slot, box) in l.boxes) assertTrue("$w x $h $slot inside the width", box.left >= -1e-3f && box.right <= w + 1e-3f)
            for (c in cards) {
                assertEquals("$c width", ref.boxes.getValue(c).width, l.boxes.getValue(c).width, 1e-3f)
                assertEquals("$c height", ref.boxes.getValue(c).height, l.boxes.getValue(c).height, 1e-3f)
            }
            val rows = listOf(Slot.MODEL, Slot.DEVELOPMENT).map { l.boxes.getValue(it) }
            assertTrue("rows do not overlap", rows[0].bottom <= rows[1].top)
            assertTrue("hero ends above the cards (reference overlap 0.1 dp)", l.boxes.getValue(Slot.HERO).bottom <= l.boxes.getValue(Slot.MODEL).top + .5f)
            if (h / l.scale <= 649f + 420f) assertTrue("$w x $h fills the window", l.contentHeight >= h - 1f)
            assertTrue("$w x $h never taller than the window when the window is tall enough", l.contentHeight <= maxOf(h, 649f * l.scale) + 1e-3f)
            assertTrue("hologram never smaller", l.boxes.getValue(Slot.HERO).width >= ref.boxes.getValue(Slot.HERO).width - 1e-3f)
        }
    }
}
