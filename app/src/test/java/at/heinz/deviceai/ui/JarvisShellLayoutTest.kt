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

    @Test fun fold7OpenKeepsTheDarkReferenceStructure() {
        // One navigation surface per destination.
        val s = JarvisShellLayout.create(884f, 760f)
        assertEquals(FormFactor.FOLD_OPEN, s.formFactor)
        assertNull(s.rail)
        assertTrue(s.ref5Grid)
        assertTrue("compact badge belongs to hero", s.headBounds!!.contains(s[Slot.DIALOG]!!))
        assertEquals(56f, s[Slot.DIALOG]!!.height, .01f)
        assertNull("The obsolete owner card cannot cover the face", s[Slot.USER_CARD])
        assertEquals(5, s.tileColumns); assertEquals(2, s.tileRows)
        assertNotNull(s[Slot.TILES])
        assertEquals(10, s.tileBoxes().size)
        listOf(Slot.ROW_AGENTS, Slot.ROW_TASKS, Slot.ROW_MEMORY, Slot.ROW_MODEL).forEach { assertNotNull(s[it]) }
        val st = s[Slot.STATUS]!!
        assertTrue(s[Slot.ROW_MEMORY]!!.top > s[Slot.INPUT]!!.bottom)
        assertTrue(st.top > s[Slot.TILES]!!.bottom)
        assertTrue(s[Slot.ROW_TASKS]!!.top > st.bottom)
        assertTrue("status full width", st.left <= 12f && st.right >= 884f - 12f)
        assertNull(s[Slot.NAV_RAIL])
        assertEquals(12f, s[Slot.AVATAR]!!.left, .01f)
        assertTrue(s[Slot.COCKPIT_SECONDARY]!!.top > st.bottom)
        assertSane(s)
    }

    @Test fun expandedLargeFontReflowsAllTenCardsWithoutAHiddenRail() {
        val regular = JarvisShellLayout.create(840f, 760f)
        val large = JarvisShellLayout.create(840f, 760f, 1.5f)
        assertTrue(!large.ref5Grid)
        assertNull(large[Slot.NAV_RAIL])
        assertTrue(large.tileColumns < regular.tileColumns)
        assertEquals(4, large.tileColumns)
        assertEquals(3, large.tileRows)
        assertEquals(10, large.tileBoxes().size)
        assertTrue(large.tileBoxes().all { it.width >= 104f * 1.5f - .01f && it.height >= 158f * 1.5f - .01f })
        assertTrue(large.requiresVerticalScroll)
        assertTrue(large[Slot.ROW_MEMORY]!!.height >= 190f * 1.5f - .01f)
        assertTrue(large[Slot.ROW_MODEL]!!.height >= 190f * 1.5f - .01f)
        assertSane(large)
    }

    @Test fun legacyRailPreferenceCannotRestoreFixedHomeNavigation() {
        val s = JarvisShellLayout.create(884f, 760f, foldRail = Rail.ICONS)
        assertEquals(0f, s.profile.railWidthDp, .01f)
        assertNull(s[Slot.NAV_RAIL])
        assertEquals(10, s.tileBoxes().size)
        assertEquals(JarvisShellLayout.create(884f, 760f), s)
        assertSane(s)
    }

    @Test fun tabletRetainsTheSameHeadLeftSphereRightComposition() {
        val s = JarvisShellLayout.create(1280f, 800f)
        assertEquals(FormFactor.TABLET_LANDSCAPE, s.formFactor)
        assertTrue(s.ref5Grid)
        assertTrue(s.headBounds!!.right < s.orbBounds!!.left)
        assertNull(s[Slot.NAV_RAIL])
        assertEquals(12f, s.headBounds!!.left, .01f)
        assertEquals(.5f, s.headAnchorX, .001f)
        assertEquals(s.width - 12f, s.orbBounds!!.right, .001f)
        assertEquals(5, s.tileColumns)
        assertSane(s)
    }

    @Test fun largeFontStaysSane() {
        assertSane(JarvisShellLayout.create(360f, 780f, 1.5f))
        val f = JarvisShellLayout.create(884f, 760f, 1.5f); assertEquals(5, f.tileColumns); assertSane(f)
    }

    @Test fun referenceTilesFromRef5() {
        assertEquals(listOf("Gespräch", "Gedächtnis", "Denken und Planen", "Forschung", "Assistenten", "Aufgaben", "Internet", "Lernen", "Gerätesteuerung", "Systemstatus"),
            JarvisTiles.reference.map { it.label })
        assertTrue(JarvisTiles.reference.all { it.displayText == null })
        assertEquals(JarvisTiles.reference.size, JarvisShellLayout.create(412f, 915f).tileBoxes().size)
        assertEquals(JarvisTiles.reference.size, JarvisShellLayout.create(884f, 760f).tileBoxes().size)
    }

    @Test fun glowLevelsAreOrdered() {
        val g = JarvisDesign.Glow.values().map { it.haloAlpha }
        assertEquals(g.sorted(), g)
    }

    @Test fun jarvisColoursFollowVideoSpec() {
        assertEquals(Argb.rgb(0x00, 0xE5, 0xFF), JarvisDesign.Colors.cyan)
        assertEquals(Argb.rgb(0xA4, 0x3B, 0xFF), JarvisDesign.Colors.violet)
        assertEquals(Argb.rgb(0x02, 0x05, 0x10), JarvisDesign.Colors.background)
        assertEquals(Argb.of(250, 0x04, 0x0B, 0x1C), JarvisDesign.Colors.surface)
        assertEquals(Argb.rgb(0x17, 0x68, 0x7F), JarvisDesign.Colors.rim)
        assertEquals(Argb.rgb(0x03, 0x07, 0x15), JarvisDesign.Colors.backgroundBottom)
        // ref5 component styles (px x 0.642 = dp)
        assertEquals(51f, JarvisDesign.Styles.tile.height, 0f); assertEquals(14f, JarvisDesign.Styles.tile.radius, 0f)
        assertEquals(32f, JarvisDesign.Styles.button.height, 0f); assertEquals(22f, JarvisDesign.Styles.pill.height, 0f)
        assertEquals(13f, JarvisDesign.Type.tileLabel.size, 0f); assertEquals(10f, JarvisDesign.Type.subtitle.size, 0f)
    }
}
