package at.heinz.deviceai.ui

import at.heinz.deviceai.cockpit.KeyguardOwnerRecognitionSource
import at.heinz.deviceai.cockpit.OwnerRecognitionState
import at.heinz.deviceai.ui.JarvisCockpitGrid.Area
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Latest owner-reference geometry, evaluated against available window dp rather than a device-name guess. */
class JarvisCockpitGridTest {
    private val sizes = listOf(840f to 560f, 884f to 760f, 960f to 640f, 1280f to 800f, 1536f to 1024f, 1536f to 1224f)

    private fun overlaps(a: JarvisShellLayout.Box, b: JarvisShellLayout.Box) =
        a.left < b.right - .01f && b.left < a.right - .01f && a.top < b.bottom - .01f && b.top < a.bottom - .01f

    @Test fun allNamedAreasInsideAndNoOverlap() {
        for ((w, h) in sizes) {
            assertTrue("$w×$h is Fold7 open", JarvisCockpitGrid.applies(w, h, 1f))
            val g = JarvisCockpitGrid.create(w, h)
            assertEquals(Area.values().toSet() - Area.USER_CARD, g.areas.keys)
            val e = g.areas.entries.toList()
            e.forEach { (k, b) ->
                assertTrue("$k inside $w×$h: $b", b.left >= 0f && b.top >= 0f && b.right <= w + .01f && b.bottom <= h + .01f)
                assertTrue("$k non-empty $w×$h", b.width > 4f && b.height > 4f)
            }
            for (i in e.indices) for (j in i + 1 until e.size) {
                val p = e[i].key to e[j].key
                // The transparent scene viewport intentionally extends under some HUD foreground cards.
                if (p !in JarvisCockpitGrid.overlays && p.second to p.first !in JarvisCockpitGrid.overlays) assertTrue("$p overlap at $w×$h", !overlaps(e[i].value, e[j].value))
            }
        }
    }

    @Test fun ref5Arrangement() {
        val g = JarvisCockpitGrid.create(884f, 760f)
        val head = g[Area.HEAD]; val orb = g[Area.ORB]
        assertTrue("rail left of content", g[Area.RAIL].right < head.left)
        assertTrue("orb right of head", orb.left >= head.right)
        assertTrue("modules right of orb", g[Area.MODULES].left > orb.right)
        assertTrue("latest reference has no owner card shrinking the face", Area.USER_CARD !in g.areas)
        assertTrue("dialog intentionally overlaps the lower head stage", overlaps(g[Area.DIALOG], head))
        assertTrue("foreground dialogue leaves upper face visible", g[Area.DIALOG].top > head.top + head.height / 2f)
        assertTrue("tiles under the stage", g[Area.TILES].top > head.bottom)
        listOf(Area.ROW_AGENTS, Area.ROW_TASKS, Area.ROW_MEMORY, Area.ROW_MODEL).zipWithNext().forEach { (a, b) ->
            assertTrue("$a left of $b", g[a].right < g[b].left); assertEquals(g[a].top, g[b].top, .01f)
        }
        assertTrue("status at the very bottom", g[Area.STATUS].top > g[Area.ROW_MODEL].bottom)
        // compact sizes as in ref5
        assertTrue("tiles compact", g[Area.TILES].height < 130f)
        assertTrue("head stage dominant", head.height > 300f)
    }

    @Test fun independentSceneViewportsUseTheLatestMeasuredCoordinates() {
        val g = JarvisCockpitGrid.create(1536f, 1024f)
        assertEquals(JarvisShellLayout.Box(250f, 70f, 800f, 497f), g[Area.HEAD])
        assertEquals(JarvisShellLayout.Box(805f, 183f, 1212f, 497f), g[Area.ORB])
        assertEquals(JarvisShellLayout.Box(1199f, 83f, 1520f, 141f), g[Area.CAMERA_STATUS])
        assertTrue(g[Area.HEAD].width > g[Area.ORB].width)
    }

    @Test fun referenceCompositionRequiresExpandedReadableWindow() {
        assertTrue(JarvisShellLayout.create(884f, 760f).ref5Grid)
        assertTrue(!JarvisShellLayout.create(884f, 760f, 1.5f).ref5Grid)
        assertTrue(!JarvisShellLayout.create(832f, 750f).ref5Grid)
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
        assertEquals(listOf("Memory", "Logic", "Agents", "Learning", "Local Model"), m.map { it.label })
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
