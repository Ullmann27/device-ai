package at.heinz.deviceai.ui

import at.heinz.deviceai.cockpit.KeyguardOwnerRecognitionSource
import at.heinz.deviceai.cockpit.OwnerRecognitionState
import at.heinz.deviceai.ui.JarvisCockpitGrid.Area
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kern's ref5 grid on the open Fold7 (2184 × 1968 px ≈ 832 × 750 dp at 2.625; with bars, zoomed, landscape). */
class JarvisCockpitGridTest {
    private val sizes = listOf(832f to 750f, 832f to 708f, 884f to 760f, 750f to 790f, 656f to 690f, 700f to 600f)

    private fun overlaps(a: JarvisShellLayout.Box, b: JarvisShellLayout.Box) =
        a.left < b.right - .01f && b.left < a.right - .01f && a.top < b.bottom - .01f && b.top < a.bottom - .01f

    @Test fun allNamedAreasInsideAndNoOverlap() {
        for ((w, h) in sizes) {
            assertTrue("$w×$h is Fold7 open", JarvisCockpitGrid.applies(w, h, 1f))
            val g = JarvisCockpitGrid.create(w, h)
            assertEquals(Area.values().toSet(), g.areas.keys)
            val e = g.areas.entries.toList()
            e.forEach { (k, b) ->
                assertTrue("$k inside $w×$h: $b", b.left >= 0f && b.top >= 0f && b.right <= w + .01f && b.bottom <= h + .01f)
                assertTrue("$k non-empty $w×$h", b.width > 4f && b.height > 4f)
            }
            for (i in e.indices) for (j in i + 1 until e.size) {
                val p = e[i].key to e[j].key
                if (p in JarvisCockpitGrid.overlays || p.second to p.first in JarvisCockpitGrid.overlays)
                    assertTrue("$p contained", e[i].value.contains(e[j].value) || e[j].value.contains(e[i].value))
                else assertTrue("$p overlap at $w×$h", !overlaps(e[i].value, e[j].value))
            }
        }
    }

    @Test fun ref5Arrangement() {
        val g = JarvisCockpitGrid.create(832f, 750f)
        val head = g[Area.HEAD]; val orb = g[Area.ORB]
        assertTrue("rail left of content", g[Area.RAIL].right < head.left)
        assertTrue("orb right of head", orb.left >= head.right)
        assertTrue("modules right of orb", g[Area.MODULES].left > orb.right)
        assertTrue("user card left part of head stage", g[Area.USER_CARD].right < head.centerX)
        assertTrue("dialog under the user card", g[Area.DIALOG].top > g[Area.USER_CARD].bottom)
        assertTrue("tiles under the stage", g[Area.TILES].top > head.bottom)
        listOf(Area.ROW_AGENTS, Area.ROW_TASKS, Area.ROW_MEMORY, Area.ROW_MODEL).zipWithNext().forEach { (a, b) ->
            assertTrue("$a left of $b", g[a].right < g[b].left); assertEquals(g[a].top, g[b].top, .01f)
        }
        assertTrue("status at the very bottom", g[Area.STATUS].top > g[Area.ROW_MODEL].bottom)
        // compact sizes as in ref5
        assertTrue("tiles compact", g[Area.TILES].height < 130f)
        assertTrue("head stage dominant", head.height > 300f)
    }

    @Test fun userCardUsesMeasuredFractions() {
        val g = JarvisCockpitGrid.create(832f, 750f)
        val c = g[Area.USER_CARD]
        assertEquals(.157f * 832f, c.left, .5f)
        assertEquals(.123f * 750f, c.top, .5f)
        assertEquals(.149f * 832f, c.width, .5f)
        assertEquals(.194f * 750f, c.height, .5f)
    }

    @Test fun shellUsesGridOnlyOnFoldOpenNormalFont() {
        assertTrue(JarvisShellLayout.create(832f, 750f).ref5Grid)
        assertTrue(!JarvisShellLayout.create(832f, 750f, 1.5f).ref5Grid)
        assertTrue(!JarvisShellLayout.create(412f, 915f).ref5Grid)
        assertTrue(!JarvisShellLayout.create(1280f, 800f).ref5Grid)
        assertNull(JarvisShellLayout.create(412f, 915f)[JarvisShellLayout.Slot.USER_CARD])
    }

    @Test fun tilesCarrySubtitlesOnlyInGrid() {
        val s = ContinuumDashboardState()
        assertTrue(JarvisShellContent.tiles(s, 0L, subtitles = true).all { it.subtitle.size == 2 })
        assertTrue(JarvisShellContent.tiles(s, 0L).all { it.subtitle.isEmpty() })
    }

    @Test fun noInventedValuesWithoutSources() {
        val s = ContinuumDashboardState()
        val m = JarvisCockpitGridContent.modules(s)
        assertEquals(listOf("Gedächtnis", "Logik", "Agenten", "Lernen", "Lokales Modell"), m.map { it.label })
        assertTrue(m.all { it.value == "offline" || it.value == JarvisCockpitGridContent.NONE })
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
