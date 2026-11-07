package at.heinz.deviceai.ui

import at.heinz.deviceai.cockpit.KeyguardOwnerRecognitionSource
import at.heinz.deviceai.cockpit.OwnerRecognitionState
import at.heinz.deviceai.ui.JarvisCockpitGrid.Area
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Latest owner-reference geometry, evaluated against available window dp rather than a device-name guess. */
class JarvisCockpitGridTest {
    private val sizes = listOf(692f to 700f, 832f to 748f, 840f to 560f, 884f to 760f, 960f to 640f, 1280f to 800f, 1536f to 1024f, 1536f to 1224f)

    private fun overlaps(a: JarvisShellLayout.Box, b: JarvisShellLayout.Box) =
        a.left < b.right - .01f && b.left < a.right - .01f && a.top < b.bottom - .01f && b.top < a.bottom - .01f

    @Test fun allNamedAreasInsideAndNoOverlap() {
        for ((w, h) in sizes) {
            assertTrue("$w×$h is Fold7 open", JarvisCockpitGrid.applies(w, h, 1f))
            val g = JarvisCockpitGrid.create(w, h)
            assertEquals(Area.values().toSet() - setOf(Area.USER_CARD, Area.DEVICE_STATUS, Area.TAGLINE, Area.TILES),
                g.areas.keys - Area.TAGLINE)
            val e = g.areas.entries.toList()
            e.forEach { (k, b) ->
                assertTrue("$k inside $w×$h: $b", b.left >= 0f && b.top >= 0f && b.right <= w + .01f && b.bottom <= g.height + .01f)
                assertTrue("$k non-empty $w×$h", b.width > 4f && b.height > 4f)
            }
            for (i in e.indices) for (j in i + 1 until e.size) {
                val p = e[i].key to e[j].key
                // The transparent scene viewport intentionally extends under some HUD foreground cards.
                if (p !in JarvisCockpitGrid.overlays && p.second to p.first !in JarvisCockpitGrid.overlays) assertTrue("$p overlap at $w×$h", !overlaps(e[i].value, e[j].value))
            }
        }
    }

    @Test fun referenceHeroLeadsIntoProminentDiagnosticsWithoutDuplicateNavigation() {
        val g = JarvisCockpitGrid.create(692f, 700f)
        val head = g[Area.HEAD]; val orb = g[Area.ORB]
        assertTrue("rail left of content", g[Area.RAIL].right < head.left)
        assertTrue("orb right of head", orb.left > head.right)
        assertTrue("modules follow primary status", g[Area.MODULES].top > g[Area.STATUS].bottom)
        assertFalse(Area.USER_CARD in g.areas)
        assertTrue("badge stays inside its stage", head.contains(g[Area.DIALOG]))
        assertEquals(56f, g[Area.DIALOG].height, .01f)
        assertTrue("conversation beneath stage", g[Area.INPUT].top > head.bottom)
        assertFalse(Area.TILES in g.areas)
        assertTrue("charts beneath conversation", g[Area.ROW_MEMORY].top > g[Area.INPUT].bottom)
        assertEquals(g[Area.ROW_MEMORY].top, g[Area.ROW_MODEL].top, .01f)
        assertTrue("activity follows charts", g[Area.ROW_AGENTS].top > g[Area.ROW_MEMORY].bottom)
        for (area in listOf(Area.ROW_AGENTS, Area.ROW_TASKS, Area.ROW_MEMORY, Area.ROW_MODEL))
            assertTrue("$area precedes primary status", g[area].bottom < g[Area.STATUS].top)
        assertTrue("head keeps useful height", head.height >= 260f)
        assertEquals(190f, g[Area.ROW_MEMORY].height, .01f)
        assertEquals(132f, g[Area.ROW_AGENTS].height, .01f)
    }

    @Test fun bothIdentitiesShareTheFullWidthWithoutASidePanel() {
        for ((w, h) in sizes) {
            val g = JarvisCockpitGrid.create(w, h)
            val head = g[Area.HEAD]; val orb = g[Area.ORB]
            assertEquals(w - 12f, orb.right, .01f)
            assertTrue("broad head stage", head.width > (orb.right - head.left) * .48f)
            assertTrue("independent viewports", !overlaps(head, orb))
            assertTrue("heading above original orb", g[Area.CONTINUUM_HEADER].bottom < orb.top)
            assertTrue("camera no longer covers hero", g[Area.CAMERA_STATUS].top > g[Area.STATUS].bottom)
        }
    }

    @Test fun referenceCompositionRequiresAWideWindowAndReadableText() {
        assertTrue(JarvisShellLayout.create(884f, 760f).ref5Grid)
        assertTrue(!JarvisShellLayout.create(884f, 760f, 1.5f).ref5Grid)
        assertTrue(JarvisShellLayout.create(832f, 750f).ref5Grid)
        assertTrue(JarvisShellLayout.create(692f, 700f).ref5Grid)
        assertTrue(JarvisShellLayout.create(600f, 900f).ref5Grid)
        assertTrue(!JarvisShellLayout.create(412f, 915f).ref5Grid)
        assertTrue(JarvisShellLayout.create(1280f, 800f).ref5Grid)
        assertNull(JarvisShellLayout.create(412f, 915f)[JarvisShellLayout.Slot.USER_CARD])
    }

    @Test fun tenTilesCanCarryRealValuesAndTheirReferenceSubtitles() {
        val s = ContinuumDashboardState()
        assertTrue(JarvisShellContent.tiles(s, 0L, subtitles = true).all { it.subtitle.size == 2 })
        assertTrue(JarvisShellContent.tiles(s, 0L).all { it.subtitle.isEmpty() })
    }

    @Test fun noInventedValuesWithoutSources() {
        val s = ContinuumDashboardState()
        val m = JarvisCockpitGridContent.modules(s)
        assertEquals(listOf("Gedächtnis", "Denken", "Assistenten", "Lernen", "Lokales Modell"), m.map { it.label })
        assertEquals(listOf("—", "—", "—", "ruhend", s.model.stateLabel), m.map { it.value })
        assertTrue(m.none { it.ok })
        assertTrue(JarvisCockpitGridContent.statusSegments(s).last().first.contains("CPU —"))
    }

    @Test fun ownerRecognitionOnlyFromUnlock() {
        val unlocked = KeyguardOwnerRecognitionSource.map(false, 42L)
        assertEquals(OwnerRecognitionState.RECOGNIZED, unlocked.state)
        assertEquals("Geräte-Entsperrung", unlocked.method); assertEquals(42L, unlocked.sinceMillis)
        assertEquals(OwnerRecognitionState.UNKNOWN, KeyguardOwnerRecognitionSource.map(true, 42L).state)
        assertEquals(OwnerRecognitionState.UNKNOWN, KeyguardOwnerRecognitionSource.map(null, null).state)
        assertNull(KeyguardOwnerRecognitionSource.map(true, 42L).method)
    }
}
