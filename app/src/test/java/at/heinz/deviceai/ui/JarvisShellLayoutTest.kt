package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisShellLayout.FormFactor
import at.heinz.deviceai.ui.JarvisShellLayout.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisShellLayoutTest {
    private fun overlaps(a: JarvisShellLayout.Box, b: JarvisShellLayout.Box) =
        a.left < b.right - .01f && b.left < a.right - .01f && a.top < b.bottom - .01f && b.top < a.bottom - .01f

    private fun assertSane(s: JarvisShellLayout.Shell) {
        val boxes = s.boxes.values.toList()
        boxes.forEach {
            assertTrue("$it inside", it.left >= 0f && it.top >= 0f && it.right <= s.width + .01f && it.bottom <= s.height + .01f)
            assertTrue("$it non-empty", it.width > 0f && it.height > 0f)
        }
        for (i in boxes.indices) for (j in i + 1 until boxes.size) assertTrue("${boxes[i]} vs ${boxes[j]}", !overlaps(boxes[i], boxes[j]))
    }

    @Test fun phonePortraitMatchesRef4() {
        val s = JarvisShellLayout.create(412f, 915f)
        assertEquals(FormFactor.PHONE_PORTRAIT, s.formFactor)
        listOf(Slot.TOP_BAR, Slot.AVATAR, Slot.TILES, Slot.STATUS, Slot.BOTTOM_NAV).forEach { assertNotNull(s[it]) }
        assertNull(s[Slot.NAV_RAIL]); assertEquals(3, s.tileColumns)
        assertTrue("hero dominates", s[Slot.AVATAR]!!.height > 350f)
        assertSane(s)
    }

    @Test fun fold7OpenMatchesRef5() {
        val s = JarvisShellLayout.create(884f, 904f)
        assertEquals(FormFactor.FOLD_OPEN, s.formFactor)
        val a = s[Slot.AVATAR]!!; val p = s[Slot.COCKPIT_PRIMARY]!!; val c = s[Slot.COCKPIT_SECONDARY]!!
        assertTrue(p.right < a.left && a.right < c.left)
        assertEquals(JarvisDesign.Metrics.railCompact, s[Slot.NAV_RAIL]!!.width, .01f)
        assertSane(s)
    }

    @Test fun tabletLandscapeMatchesRef2() {
        val s = JarvisShellLayout.create(1280f, 800f)
        assertEquals(FormFactor.TABLET_LANDSCAPE, s.formFactor)
        assertEquals(JarvisDesign.Metrics.railLabelled, s[Slot.NAV_RAIL]!!.width, .01f)
        assertTrue(s[Slot.AVATAR]!!.right < s[Slot.COCKPIT_PRIMARY]!!.left)
        assertSane(s)
    }

    @Test fun largeFontPhoneUsesTwoColumnsAndStaysSane() {
        val s = JarvisShellLayout.create(360f, 780f, 1.5f)
        assertEquals(2, s.tileColumns); assertSane(s)
    }

    @Test fun glowLevelsAreOrdered() {
        val g = JarvisDesign.Glow.values().map { it.haloAlpha }
        assertEquals(g.sorted(), g)
    }
}
