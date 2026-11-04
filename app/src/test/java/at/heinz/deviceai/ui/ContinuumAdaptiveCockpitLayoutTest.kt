package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.ContinuumHomeLayout.Slot
import org.junit.Assert.*
import org.junit.Test

class ContinuumAdaptiveCockpitLayoutTest {
    private val sizes = listOf(320f to 720f, 412f to 915f, 600f to 840f, 840f to 960f, 1280f to 800f)
    private fun layout(w: Float, h: Float) = if (ContinuumHomeLayout.isWide(w, h)) ContinuumHomeLayout.wide(w, h, 1.6f) else ContinuumHomeLayout.phone(w, 1.6f)

    /** New owner references apply at ordinary and enlarged type; there is no old-design fallback. */
    @Test fun everyFontKeepsTheNewCockpitAndEnlargedTypeReflowsWithoutShrinking() {
        for (font in listOf(1f, 1.3f, 1.6f, 2f)) {
            val phone = ContinuumHomeLayout.phone(412f, font)
            assertTrue(phone.referenceCockpit)
            assertEquals(font > 1.3f, phone.adaptive)
            assertEquals(1f, phone.scale, 0f)
            assertTrue(phone[Slot.LABEL_CONNECTED]!!.top > phone[Slot.HERO]!!.bottom)
            assertTrue(ContinuumHomeScreen.elements(phone, font)[Slot.LABEL_CONNECTED] is ReferenceActionElement)
            assertNotNull(phone[Slot.COMPOSER])
        }
        val large = ContinuumHomeLayout.phone(412f, 1.6f)
        assertEquals(large[Slot.COMPOSER]!!.width, large[Slot.ACTION_MEMORY]!!.width, .001f)
        val narrow = ContinuumHomeLayout.phone(280f)
        assertTrue(narrow.adaptive)
        assertEquals(48f, narrow[Slot.SEARCH]!!.width, 0f)
    }

    @Test fun everyHeaderAndActionHasUnscaledFortyEightDpTouchArea() {
        for ((w, h) in sizes) {
            val l = layout(w, h)
            assertEquals("Readable content must not shrink with the window", 1f, l.scale, 0f)
            for (slot in listOf(Slot.SEARCH, Slot.NOTIFICATIONS, Slot.PROFILE, Slot.LABEL_CONNECTED,
                Slot.LABEL_THINKING, Slot.LABEL_LEARNING, Slot.LABEL_ANALYSING, Slot.LABEL_DEVELOPING, Slot.LABEL_EVOLVING,
                Slot.ACTION_MEMORY, Slot.ACTION_AGENTS, Slot.ACTION_DEVICE, Slot.COMPOSER)) {
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
