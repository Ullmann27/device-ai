package at.heinz.deviceai.ui

import at.heinz.deviceai.cockpit.DocumentDraftAction
import at.heinz.deviceai.cockpit.StepState
import at.heinz.deviceai.ui.JarvisCockpitGrid.Area
import at.heinz.deviceai.ui.JarvisShellLayout.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Packed Fold7-inner / tablet cockpit: one composed screen, honest document panels, no left rail. */
class JarvisCockpitFidelityTest {
    private val foldInner = 884f to 760f
    private val tablet = 1280f to 800f
    private val phone = 412f to 915f

    @Test fun foldInnerAndTabletFitTheReferenceCompositionWithoutScrolling() {
        for ((w, h) in listOf(foldInner, tablet, 692f to 700f, 840f to 560f)) {
            val shell = JarvisShellLayout.create(w, h)
            assertTrue("$w×$h uses the reference grid", shell.ref5Grid)
            assertFalse("$w×$h must not scroll", shell.requiresVerticalScroll)
            assertEquals("$w×$h packs into the viewport", h, shell.height, .01f)
            assertNull("$w×$h left rail", shell[Slot.NAV_RAIL])
            listOf(Slot.TOP_BAR, Slot.DIALOG, Slot.AVATAR, Slot.INPUT, Slot.CAMERA_STATUS,
                Slot.COCKPIT_SECONDARY, Slot.WORK_DOCUMENT, Slot.WORK_PROCESS, Slot.WORK_DRAFT,
                Slot.BOTTOM_NAV, Slot.STATUS, Slot.CONTINUUM_HEADER).forEach {
                assertNotNull("$w×$h $it", shell[it])
                assertTrue("$w×$h $it stays in the viewport", shell[it]!!.bottom <= h + .01f)
            }
            val live = shell[Slot.DIALOG]!!
            assertTrue("$w×$h live card left of head", live.right < shell.headBounds!!.left)
            assertTrue("$w×$h head left of orb", shell.headBounds!!.right < shell.orbBounds!!.left)
            assertTrue("$w×$h modules right of orb", shell[Slot.COCKPIT_SECONDARY]!!.left > shell.orbBounds!!.right)
            assertTrue("$w×$h mission under hero", shell[Slot.INPUT]!!.top > shell[Slot.AVATAR]!!.bottom)
            assertTrue("$w×$h work under mission", shell[Slot.WORK_DOCUMENT]!!.top > shell[Slot.INPUT]!!.bottom)
            assertTrue("$w×$h dock under work", shell[Slot.BOTTOM_NAV]!!.top > shell[Slot.WORK_DRAFT]!!.bottom)
            assertTrue("$w×$h status under dock", shell[Slot.STATUS]!!.top > shell[Slot.BOTTOM_NAV]!!.bottom)
            assertTrue(Area.DEVICE_STATUS in shell.grid!!.areas)
            assertFalse(Area.RAIL in shell.grid!!.areas)
            assertFalse(shell.hasFeatureTiles)
        }
    }

    @Test fun phoneFitsTheCinemaCompositionWithoutScrollingOrALeftRail() {
        val shell = JarvisShellLayout.create(phone.first, phone.second)
        assertFalse("412×915 must not overflow", shell.requiresVerticalScroll)
        assertEquals(phone.second, shell.height, .01f)
        assertFalse(shell.ref5Grid)
        assertNull(shell[Slot.NAV_RAIL])
        assertFalse(shell.hasFeatureTiles)
        assertEquals(0, shell.tileBoxes().size)
        listOf(Slot.TOP_BAR, Slot.DIALOG, Slot.CAMERA_STATUS, Slot.COCKPIT_SECONDARY, Slot.AVATAR,
            Slot.CONTINUUM_HEADER, Slot.INPUT, Slot.WORK_DOCUMENT, Slot.WORK_PROCESS, Slot.WORK_DRAFT,
            Slot.BOTTOM_NAV, Slot.STATUS).forEach {
            assertNotNull("phone $it", shell[it])
            assertTrue("phone $it stays in the viewport", shell[it]!!.bottom <= phone.second + .01f)
        }
        val live = shell[Slot.DIALOG]!!
        val camera = shell[Slot.CAMERA_STATUS]!!
        val modules = shell[Slot.COCKPIT_SECONDARY]!!
        assertEquals("three top boxes share a row", live.top, camera.top, .01f)
        assertEquals(camera.top, modules.top, .01f)
        assertTrue(live.right < camera.left)
        assertTrue(camera.right < modules.left)
        assertTrue("top boxes sit above the hero", live.bottom <= shell[Slot.AVATAR]!!.top + .01f)
        assertTrue(shell.headBounds!!.right < shell.orbBounds!!.left)
        assertTrue("orb stays below the Continuum title",
            shell[Slot.CONTINUUM_HEADER]!!.bottom <= shell.orbBounds!!.top + .01f)
        assertFalse(intersects(shell.headBounds!!, shell.orbBounds!!))
        assertFalse(intersects(shell[Slot.CONTINUUM_HEADER]!!, shell.orbBounds!!))
        assertTrue("mission under hero", shell[Slot.INPUT]!!.top > shell[Slot.AVATAR]!!.bottom)
        assertTrue("work under mission", shell[Slot.WORK_DOCUMENT]!!.top > shell[Slot.INPUT]!!.bottom)
        assertTrue("dock under work", shell[Slot.BOTTOM_NAV]!!.top > shell[Slot.WORK_DRAFT]!!.bottom)
        assertTrue("status under dock", shell[Slot.STATUS]!!.top > shell[Slot.BOTTOM_NAV]!!.bottom)
        val dock = shell[Slot.BOTTOM_NAV]!!
        val cells = JarvisTiles.bottomNavItemBoxes(dock)
        assertEquals(JarvisTiles.railNav.size, cells.size)
        cells.forEach { assertTrue("dock cell is a usable target $it", it.width >= 48f - .01f && it.height >= 48f - .01f) }
        assertEquals(JarvisTiles.railNav.map { it.target },
            cells.map { box -> JarvisShellContent.hitTarget(shell, JarvisTiles.reference, box.centerX, box.centerY) })
    }

    private fun intersects(a: JarvisShellLayout.Box, b: JarvisShellLayout.Box) =
        a.left < b.right - .01f && b.left < a.right - .01f && a.top < b.bottom - .01f && b.top < a.bottom - .01f

    @Test fun dockExposesEveryRealRailDestination() {
        for ((w, h) in listOf(foldInner, tablet)) {
            val shell = JarvisShellLayout.create(w, h)
            val dock = requireNotNull(shell[Slot.BOTTOM_NAV])
            assertEquals(JarvisTiles.railNav.map { it.target },
                JarvisTiles.bottomNavItemBoxes(dock).map { box ->
                    JarvisShellContent.hitTarget(shell, JarvisTiles.reference, box.centerX, box.centerY)
                })
            assertFalse(JarvisTiles.bottomNav.any { it.label == "Dateien" })
        }
    }

    @Test fun emptyDocumentPanelsInventNeitherPdfNorButtons() {
        val shell = JarvisShellLayout.create(foldInner.first, foldInner.second)
        val painter = JarvisPresentationRecordingPainter()
        val idle = ContinuumDashboardState()
        JarvisCockpitGridContent.draw(painter, Slot.WORK_DOCUMENT, shell[Slot.WORK_DOCUMENT]!!, shell.grid, idle, 0L, shell)
        JarvisCockpitGridContent.draw(painter, Slot.WORK_PROCESS, shell[Slot.WORK_PROCESS]!!, shell.grid, idle, 0L, shell)
        JarvisCockpitGridContent.draw(painter, Slot.WORK_DRAFT, shell[Slot.WORK_DRAFT]!!, shell.grid, idle, 0L, shell)
        assertTrue(painter.texts.any { it == "Dokument" })
        assertTrue(painter.texts.any { it == "Verarbeitung" })
        assertTrue(painter.texts.any { it == "Zusammenfassung – Entwurf" })
        assertTrue(painter.texts.any { it == JarvisCockpitPanels.NONE })
        assertTrue(painter.texts.any { it == "Keine laufenden Prozesse" })
        assertFalse(painter.texts.any { it.contains(".pdf", ignoreCase = true) })
        assertFalse(painter.texts.any { it == "1/4" || it == "1 / 4" })
        assertFalse(painter.texts.any { it == "Entwurf öffnen" })
        assertFalse(painter.texts.any { it == "Weiterbearbeiten" })
        assertFalse(painter.texts.any { it == "Speichern" })
        assertFalse(painter.texts.any { it.contains("Projektübersicht") })
    }

    @Test fun documentPanelsShowOnlyFactsFromTheBlueprint() {
        val named = ContinuumDashboardState(document = HollywoodFuiLiveData.document(
            "Notiz.txt", "text/plain", "Fasse den Abschnitt zusammen"))
        val visible = DocumentBlueprintView.visible(named.document)
        assertNotNull(visible)
        assertEquals("Notiz.txt", visible!!.documentName)
        assertEquals("Fasse den Abschnitt zusammen", visible.question)
        assertNull(visible.pageLabel)
        assertFalse(visible.showsDraft)
        assertTrue(visible.buttons.isEmpty())

        val complete = ContinuumDashboardState(document = HollywoodFuiLiveData.document(
            "Brief.md", "text/markdown", "Was steht im Ziel?",
            pageCount = 3, currentPage = 2,
            steps = listOf(HollywoodFuiLiveData.Document.PipelineStep("open", "Öffnen", StepState.DONE)),
            draft = HollywoodFuiLiveData.Document.Draft("Kurz fassen", null, null, "Erster Entwurf"),
            actions = setOf(DocumentDraftAction.OPEN)))
        val shown = requireNotNull(DocumentBlueprintView.visible(complete.document))
        assertEquals("2/3", shown.pageLabel)
        assertTrue(shown.showsDraft)
        assertEquals(listOf("Entwurf öffnen"), shown.buttons)

        val shell = JarvisShellLayout.create(foldInner.first, foldInner.second)
        val painter = JarvisPresentationRecordingPainter()
        JarvisCockpitGridContent.draw(painter, Slot.WORK_DOCUMENT, shell[Slot.WORK_DOCUMENT]!!, shell.grid, complete, 0L, shell)
        JarvisCockpitGridContent.draw(painter, Slot.WORK_PROCESS, shell[Slot.WORK_PROCESS]!!, shell.grid, complete, 0L, shell)
        JarvisCockpitGridContent.draw(painter, Slot.WORK_DRAFT, shell[Slot.WORK_DRAFT]!!, shell.grid, complete, 0L, shell)
        assertTrue(painter.texts.any { it == "Brief.md" })
        assertTrue(painter.texts.any { it == "2/3" })
        assertTrue(painter.texts.any { it.contains("Was steht im Ziel?") })
        assertTrue(painter.texts.any { it == "Öffnen" })
        assertTrue(painter.texts.any { it.contains("Kurz fassen") })
        assertTrue(painter.texts.any { it == "Entwurf öffnen" })
        assertFalse(painter.texts.any { it == "Weiterbearbeiten" })
    }
}
