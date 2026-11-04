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
        // §11: Kern's ref5 grid (JarvisCockpitGrid) – everything of ref5 at once, compact.
        val s = JarvisShellLayout.create(884f, 760f)
        assertEquals(FormFactor.FOLD_OPEN, s.formFactor)
        assertEquals(Rail.LABELLED, s.rail)
        assertTrue(s.ref5Grid)
        assertTrue("dialog and user card over the head stage",
            s[Slot.AVATAR]!!.contains(s[Slot.DIALOG]!!) && s[Slot.AVATAR]!!.contains(s[Slot.USER_CARD]!!))
        assertEquals(4, s.tileColumns); assertEquals(2, s.tileRows)
        listOf(Slot.ROW_AGENTS, Slot.ROW_TASKS, Slot.ROW_MEMORY, Slot.ROW_MODEL).forEach { assertNotNull(s[it]) }
        val st = s[Slot.STATUS]!!
        assertTrue("status full width", st.left <= 10f && st.right >= 884f - 10f)
        assertTrue(s[Slot.NAV_RAIL]!!.right < s[Slot.AVATAR]!!.left && s[Slot.AVATAR]!!.right < s[Slot.COCKPIT_SECONDARY]!!.left)
        assertSane(s)
    }

    @Test fun fold7LargeFontKeepsLegacyColumns() {
        val s = JarvisShellLayout.create(884f, 760f, 1.5f)
        val inner = 884f - 2 * JarvisDesign.Metrics.margin - 2 * JarvisDesign.Metrics.gap
        assertTrue(!s.ref5Grid)
        assertEquals(inner * .20f, s[Slot.NAV_RAIL]!!.width, .01f)
        assertEquals(inner * .55f, s[Slot.AVATAR]!!.width, .01f)
        assertEquals(inner * .25f, s[Slot.COCKPIT_SECONDARY]!!.width, .01f)
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

    @Test fun referenceTilesFromRef5() {
        assertEquals(listOf("Chat", "Gedächtnis", "Logik", "Recherche", "Agenten", "Lernen", "Gerätesteuerung", "Systemstatus"),
            JarvisTiles.reference.map { it.label })
        assertTrue(JarvisTiles.reference.all { it.displayText == null })
        assertEquals(JarvisTiles.reference.size, JarvisShellLayout.create(884f, 760f).tileBoxes().size)
    }

    @Test fun glowLevelsAreOrdered() {
        val g = JarvisDesign.Glow.values().map { it.haloAlpha }
        assertEquals(g.sorted(), g)
    }

    @Test fun jarvisColoursFollowVideoSpec() {
        assertEquals(Argb.rgb(0x2F, 0xF9, 0xFE), JarvisDesign.Colors.cyan)
        assertEquals(Argb.rgb(0x91, 0x4C, 0xEF), JarvisDesign.Colors.violet)
        assertEquals(Argb.rgb(0x00, 0x0C, 0x16), JarvisDesign.Colors.background)
        assertEquals(Argb.of(204, 0x01, 0x1D, 0x2F), JarvisDesign.Colors.surface)
        assertEquals(Argb.rgb(0x17, 0x68, 0x7F), JarvisDesign.Colors.rim)
        assertEquals(Argb.rgb(0x01, 0x0C, 0x17), JarvisDesign.Colors.backgroundBottom)
        // ref5 component styles (px x 0.642 = dp)
        assertEquals(51f, JarvisDesign.Styles.tile.height, 0f); assertEquals(8f, JarvisDesign.Styles.tile.radius, 0f)
        assertEquals(32f, JarvisDesign.Styles.button.height, 0f); assertEquals(22f, JarvisDesign.Styles.pill.height, 0f)
        assertEquals(11f, JarvisDesign.Type.tileLabel.size, 0f); assertEquals(9f, JarvisDesign.Type.subtitle.size, 0f)
    }
}
