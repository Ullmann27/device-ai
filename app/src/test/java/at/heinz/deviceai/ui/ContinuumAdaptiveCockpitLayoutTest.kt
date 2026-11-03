package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.ContinuumHomeLayout.Slot
import org.junit.Assert.*
import org.junit.Test

class ContinuumAdaptiveCockpitLayoutTest {
    private val sizes = listOf(320f to 720f, 412f to 915f, 600f to 840f, 840f to 960f, 1280f to 800f)
    private fun layout(w: Float, h: Float) = if (ContinuumHomeLayout.isWide(w, h)) ContinuumHomeLayout.wide(w, h) else ContinuumHomeLayout.phone(w)

    @Test fun everyHeaderAndActionHasUnscaledFortyEightDpTouchArea() {
        for ((w, h) in sizes) {
            val l = layout(w, h)
            assertEquals("Readable content must not shrink with the window", 1f, l.scale, 0f)
            for (slot in listOf(Slot.SEARCH, Slot.NOTIFICATIONS, Slot.PROFILE, Slot.LABEL_CONNECTED,
                Slot.LABEL_THINKING, Slot.LABEL_LEARNING, Slot.LABEL_ANALYSING, Slot.LABEL_DEVELOPING, Slot.LABEL_EVOLVING)) {
                val b = requireNotNull(l[slot])
                assertTrue("$w $slot target ${b.width}x${b.height}", b.width >= 48f && b.height >= 48f)
            }
        }
    }

    @Test fun heroAndClickableCardsNeverIntersectAndAllRemainReachable() {
        for ((w, h) in sizes) {
            val l = layout(w, h)
            val boxes = l.boxes.filterKeys { it != Slot.RAIL && it != Slot.NAV }
            for ((slot, b) in boxes) {
                assertTrue("$w $slot bounded", b.left >= 0f && b.top >= 0f && b.right <= w + .001f && b.bottom <= l.contentHeight + .001f)
                assertTrue(b.width > 0f && b.height > 0f)
            }
            val entries = boxes.entries.toList()
            for (i in entries.indices) for (j in i + 1 until entries.size) {
                val a = entries[i].value; val b = entries[j].value
                assertFalse("$w overlap ${entries[i].key}/${entries[j].key}", a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom)
            }
            for (slot in listOf(Slot.MODEL, Slot.LEARNING, Slot.STORAGE, Slot.DEVELOPMENT, Slot.WORKERS, Slot.SYSTEM_STATUS, Slot.SYSTEM_ACTIVITY)) {
                assertNotNull("$w retains $slot", l[slot])
            }
        }
    }
}
