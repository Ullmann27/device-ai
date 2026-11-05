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
            if (pair !in s.overlays && pair.second to pair.first !in s.overlays) assertTrue("$pair overlap", !overlaps(e[i].value, e[j].value))
        }
    }

    @Test fun compactPhoneStacksReadableComponentsAndAllTenTiles() {
        val s = JarvisShellLayout.create(412f, 915f)
        assertEquals(FormFactor.PHONE_PORTRAIT, s.formFactor)
        listOf(Slot.TOP_BAR, Slot.AVATAR, Slot.DIALOG, Slot.INPUT, Slot.TILES, Slot.STATUS).forEach { assertNotNull(it.name, s[it]) }
        assertNull(s[Slot.NAV_RAIL])
        assertEquals(2, s.tileColumns); assertEquals(5, s.tileRows); assertEquals(10, s.tileBoxes().size)
        assertNull("External Activity owns the phone category bar", s[Slot.BOTTOM_NAV])
        assertTrue(s.requiresVerticalScroll)
        assertTrue("input below hero, above tiles", s[Slot.INPUT]!!.top >= s[Slot.AVATAR]!!.bottom && s[Slot.INPUT]!!.bottom <= s[Slot.TILES]!!.top)
        assertTrue("hero dominates", s[Slot.AVATAR]!!.height > 300f)
        assertSane(s)
    }

    @Test fun fold7OpenMatchesRef5() {
        // Latest canonical composition when five columns remain readable.
        val s = JarvisShellLayout.create(884f, 760f)
        assertEquals(FormFactor.FOLD_OPEN, s.formFactor)
        assertEquals(Rail.LABELLED, s.rail)
        assertTrue(s.ref5Grid)
        assertTrue("dialog overlays the live stage", s[Slot.AVATAR]!!.contains(s[Slot.DIALOG]!!))
        assertNull("The obsolete owner card cannot cover the face", s[Slot.USER_CARD])
        assertEquals(5, s.tileColumns); assertEquals(2, s.tileRows)
        assertEquals(10, s.tileBoxes().size)
        listOf(Slot.ROW_AGENTS, Slot.ROW_TASKS, Slot.ROW_MEMORY, Slot.ROW_MODEL).forEach { assertNotNull(s[it]) }
        val st = s[Slot.STATUS]!!
        assertTrue("status full width", st.left <= 10f && st.right >= 884f - 10f)
        assertTrue(s[Slot.NAV_RAIL]!!.right < s[Slot.AVATAR]!!.left && s[Slot.AVATAR]!!.right < s[Slot.COCKPIT_SECONDARY]!!.left)
        assertSane(s)
    }

    @Test fun expandedLargeFontReflowsAndReservesReadableLabels() {
        val regular = JarvisShellLayout.create(884f, 760f)
        val large = JarvisShellLayout.create(884f, 760f, 1.5f)
        assertTrue(!large.ref5Grid)
        assertTrue(large[Slot.NAV_RAIL]!!.width > regular[Slot.NAV_RAIL]!!.width)
        assertTrue(large.tileColumns < 5)
        assertEquals(10, large.tileBoxes().size)
        assertTrue(large.requiresVerticalScroll)
        assertTrue(large.tileBoxes().all { it.height >= 96f })
        assertSane(large)
    }

    @Test fun iconRailHasASeparateReadableSixtyFourDpColumn() {
        val s = JarvisShellLayout.create(884f, 760f, foldRail = Rail.ICONS)
        assertEquals(64f, s.profile.railWidthDp, .01f)
        assertTrue(s[Slot.NAV_RAIL]!!.width >= 48f)
        assertEquals(10, s.tileBoxes().size)
        assertSane(s)
    }

    @Test fun tabletRetainsTheSameHeadLeftSphereRightComposition() {
        val s = JarvisShellLayout.create(1280f, 800f)
        assertEquals(FormFactor.TABLET_LANDSCAPE, s.formFactor)
        assertTrue(s.ref5Grid)
        assertTrue(s.headBounds!!.right < s.orbBounds!!.left)
        assertEquals(250f / 1536f * s.width, s.headBounds!!.left, .001f)
        assertEquals(1212f / 1536f * s.width, s.orbBounds!!.right, .001f)
        assertEquals(5, s.tileColumns)
        assertSane(s)
    }

    @Test fun largeFontStaysSane() {
        assertSane(JarvisShellLayout.create(360f, 780f, 1.5f))
        val f = JarvisShellLayout.create(884f, 760f, 1.5f); assertEquals(3, f.tileColumns); assertSane(f)
    }

    @Test fun referenceTilesFromRef5() {
        assertEquals(listOf("Gespräch", "Gedächtnis", "Denken und Planen", "Forschung", "Assistenten", "Aufgaben", "Internet", "Lernen", "Gerätesteuerung", "Systemstatus"),
            JarvisTiles.reference.map { it.label })
        assertTrue(JarvisTiles.reference.all { it.displayText == null })
        assertEquals(JarvisTiles.reference.size, JarvisShellLayout.create(884f, 760f).tileBoxes().size)
    }

    @Test fun glowLevelsAreOrdered() {
        val g = JarvisDesign.Glow.values().map { it.haloAlpha }
        assertEquals(g.sorted(), g)
    }

    @Test fun jarvisColoursFollowVideoSpec() {
        assertEquals(Argb.rgb(0x00, 0xE5, 0xFF), JarvisDesign.Colors.cyan)
        assertEquals(Argb.rgb(0xA4, 0x3B, 0xFF), JarvisDesign.Colors.violet)
        assertEquals(Argb.rgb(0x02, 0x07, 0x13), JarvisDesign.Colors.background)
        assertEquals(Argb.of(204, 0x01, 0x1D, 0x2F), JarvisDesign.Colors.surface)
        assertEquals(Argb.rgb(0x17, 0x68, 0x7F), JarvisDesign.Colors.rim)
        assertEquals(Argb.rgb(0x03, 0x0B, 0x21), JarvisDesign.Colors.backgroundBottom)
        // ref5 component styles (px x 0.642 = dp)
        assertEquals(51f, JarvisDesign.Styles.tile.height, 0f); assertEquals(8f, JarvisDesign.Styles.tile.radius, 0f)
        assertEquals(32f, JarvisDesign.Styles.button.height, 0f); assertEquals(22f, JarvisDesign.Styles.pill.height, 0f)
        assertEquals(13f, JarvisDesign.Type.tileLabel.size, 0f); assertEquals(10f, JarvisDesign.Type.subtitle.size, 0f)
    }
}
