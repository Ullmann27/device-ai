package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.ContinuumHomeLayout.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** ref1/ref3 landscape cockpit on JarvisShellLayout: one screen, three columns, action bar, free avatar slot. */
class ContinuumLandscapeCockpitLayoutTest {
    private val windows = listOf(840f to 649f, 832f to 700f, 960f to 600f, 1280f to 800f, 1440f to 900f)

    @Test fun landscapeWindowsUseTheCockpitAndPortraitOrLargeTypeDoNot() {
        for ((w, h) in windows) assertTrue("$w x $h", ContinuumHomeLayout.wide(w, h, 1f).landscapeCockpit)
        assertFalse(ContinuumHomeLayout.wide(840f, 1050f, 1f).landscapeCockpit)
        assertFalse(ContinuumHomeLayout.wide(840f, 649f, 1.5f).landscapeCockpit)
        assertFalse(ContinuumHomeLayout.phone(412f).landscapeCockpit)
        assertFalse(ContinuumReferenceLayout.create(840f, 649f, true, 1f, compact = true).landscapeCockpit)
    }

    @Test fun everythingFitsOneScreenWithUsableTargetsAndNoOverlap() {
        for ((w, h) in windows) for (font in listOf(1f, 1.3f)) {
            val layout = ContinuumHomeLayout.wide(w, h, font)
            val ctx = "$w x $h font $font"
            assertEquals(ctx, h, layout.contentHeight, .01f)
            val rail = layout[Slot.RAIL]!!
            val named = ContinuumLandscapeCockpitLayout.topTabs + ContinuumLandscapeCockpitLayout.actionBar +
                ContinuumLandscapeCockpitLayout.cards + listOf(Slot.BRAND, Slot.SEARCH, Slot.NOTIFICATIONS, Slot.PROFILE,
                Slot.COMPOSER, Slot.VIEW_AVATAR, Slot.VIEW_ORB, Slot.VIEW_COMPACT, ContinuumLandscapeCockpitLayout.AVATAR_SLOT)
            val boxes = named.map { it to requireNotNull(layout[it]) { "$ctx lacks $it" } }
            for ((slot, b) in boxes) {
                assertTrue("$ctx $slot inside window", b.left >= rail.right - .01f && b.right <= w + .01f && b.top >= 0f && b.bottom <= h + .01f)
                assertTrue("$ctx $slot ≥ 48 dp", b.width >= 48f && b.height >= 48f)
            }
            for (i in boxes.indices) for (j in i + 1 until boxes.size) {
                val (sa, a) = boxes[i]; val (sb, b) = boxes[j]
                assertFalse("$ctx $sa overlaps $sb", a.left < b.right - .01f && b.left < a.right - .01f && a.top < b.bottom - .01f && b.top < a.bottom - .01f)
            }
        }
    }

    @Test fun compositionFollowsTheReferences() {
        val layout = ContinuumHomeLayout.wide(840f, 649f, 1f)
        val hero = layout[Slot.HERO]!!
        // left column: brain above processes above dialog; right: system above agents
        assertTrue(layout[Slot.MODEL]!!.right <= hero.left && layout[Slot.SYSTEM_STATUS]!!.left >= hero.right)
        assertTrue(layout[Slot.MODEL]!!.bottom <= layout[Slot.PROCESSES]!!.top && layout[Slot.PROCESSES]!!.bottom <= layout[Slot.LIVE_DIALOG]!!.top)
        assertTrue(layout[Slot.SYSTEM_STATUS]!!.bottom <= layout[Slot.WORKERS]!!.top)
        // action bar: one row at the bottom, in reference order
        val bar = ContinuumLandscapeCockpitLayout.actionBar.map { layout[it]!! }
        assertEquals(1, bar.map { it.top }.distinct().size)
        assertTrue(bar.zipWithNext().all { (a, b) -> a.right <= b.left })
        assertTrue(bar.first().top >= hero.bottom)
        // avatar slot is the largest single area and is not covered by any card
        assertTrue(ContinuumLandscapeCockpitLayout.cards.all { hero.height >= layout[it]!!.height })
        val elements = ContinuumHomeScreen.elements(layout, 1f)
        assertFalse("The avatar slot is free for the avatar view", Slot.HERO in elements)
        assertEquals(listOf("Denken", "Recherchieren", "Lernen", "Entwickeln", "Ausführen", "Überprüfen"),
            ContinuumLandscapeCockpitLayout.actionBar.map { (elements[it] as CockpitActionElement).label })
    }

    @Test fun freshStateDrawsNotAvailableNeverReferenceNumbers() {
        val layout = ContinuumHomeLayout.wide(840f, 649f, 1f)
        val text = ContinuumHomeScreen.elements(layout, 1f).values.joinToString("\n") { it.describe(ContinuumDashboardState()) }
        for (sample in listOf("42%", "68%", "52 GB", "87%", "8 aktiv", "6 / 20", "Gemma 3n", "37°C", "387.426"))
            assertFalse("copies reference artwork value $sample", text.contains(sample))
        assertTrue(text.contains(ContinuumCockpitPresentation.NOT_AVAILABLE))
        assertTrue(text.contains("Noch kein Auftrag"))
    }
}
