package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisShellLayout.FormFactor
import at.heinz.deviceai.ui.JarvisShellLayout.Rail
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
        val e = s.boxes.entries.toList()
        e.forEach { (k, it) ->
            assertTrue("$k inside", it.left >= 0f && it.top >= 0f && it.right <= s.width + .01f && it.bottom <= s.height + .01f)
            assertTrue("$k non-empty", it.width > 0f && it.height > 0f)
        }
        for (i in e.indices) for (j in i + 1 until e.size) {
            val pair = e[i].key to e[j].key
            if (pair in s.overlays || pair.second to pair.first in s.overlays) assertTrue(e[i].value.contains(e[j].value) || e[j].value.contains(e[i].value))
            else assertTrue("$pair overlap", !overlaps(e[i].value, e[j].value))
        }
    }

    @Test fun phonePortraitMatchesRef4() {
        val s = JarvisShellLayout.create(412f, 915f)
        assertEquals(FormFactor.PHONE_PORTRAIT, s.formFactor)
        listOf(Slot.TOP_BAR, Slot.AVATAR, Slot.DIALOG, Slot.INPUT, Slot.TILES, Slot.STATUS, Slot.BOTTOM_NAV).forEach { assertNotNull(it.name, s[it]) }
        assertNull(s[Slot.NAV_RAIL])
        assertEquals(2, s.tileColumns); assertEquals(4, s.tileRows); assertEquals(8, s.tileBoxes().size)
        assertTrue("input below hero, above tiles", s[Slot.INPUT]!!.top >= s[Slot.AVATAR]!!.bottom && s[Slot.INPUT]!!.bottom <= s[Slot.TILES]!!.top)
        assertTrue("hero dominates", s[Slot.AVATAR]!!.height > 300f)
        assertSane(s)
    }

    @Test fun fold7OpenMatchesRef5() {
        val s = JarvisShellLayout.create(884f, 760f)
        assertEquals(FormFactor.FOLD_OPEN, s.formFactor)
        assertEquals(Rail.LABELLED, s.rail)
        assertEquals(JarvisDesign.Metrics.railFold, s[Slot.NAV_RAIL]!!.width, .01f)
        assertEquals(4, s.tileColumns); assertEquals(2, s.tileRows)
        listOf(Slot.ROW_AGENTS, Slot.ROW_TASKS, Slot.ROW_MEMORY, Slot.ROW_MODEL).forEach { assertNotNull(s[it]) }
        val st = s[Slot.STATUS]!!
        assertTrue("status full width", st.left <= JarvisDesign.Metrics.margin + .01f && st.right >= 884f - JarvisDesign.Metrics.margin - .01f)
        assertTrue(s[Slot.DIALOG]!!.right < s[Slot.AVATAR]!!.left && s[Slot.AVATAR]!!.right < s[Slot.COCKPIT_SECONDARY]!!.left)
        assertSane(s)
    }

    @Test fun fold7IconRailIsOptional() {
        val s = JarvisShellLayout.create(884f, 760f, foldRail = Rail.ICONS)
        assertEquals(JarvisDesign.Metrics.railCompact, s[Slot.NAV_RAIL]!!.width, .01f); assertSane(s)
    }

    @Test fun tabletHeadCentredAndDominant() {
        val s = JarvisShellLayout.create(1280f, 800f)
        assertEquals(FormFactor.TABLET_LANDSCAPE, s.formFactor)
        val a = s[Slot.AVATAR]!!
        val contentCentre = (s[Slot.NAV_RAIL]!!.right + 1280f) / 2f
        assertTrue("centred", kotlin.math.abs(a.centerX - contentCentre) < 40f)
        assertTrue("dominant", s.boxes.values.all { it === a || it.width * it.height < a.width * a.height })
        assertSane(s)
    }

    @Test fun largeFontStaysSane() {
        assertSane(JarvisShellLayout.create(360f, 780f, 1.5f))
        val f = JarvisShellLayout.create(884f, 760f, 1.5f); assertEquals(2, f.tileColumns); assertSane(f)
    }

    @Test fun glowLevelsAreOrdered() {
        val g = JarvisDesign.Glow.values().map { it.haloAlpha }
        assertEquals(g.sorted(), g)
    }

    @Test fun jarvisColoursFollowVideoSpec() {
        assertEquals(Argb.rgb(0, 255, 255), JarvisDesign.Colors.cyan)
        assertEquals(Argb.rgb(0x8A, 0x2B, 0xE2), JarvisDesign.Colors.violet)
        assertEquals(Argb.rgb(5, 9, 20), JarvisDesign.Colors.background)
        assertEquals(Argb.rgb(10, 17, 40), JarvisDesign.Colors.backgroundBottom)
    }
}
