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

    @Test fun phoneMayScrollAndKeepsDestinationTiles() {
        val shell = JarvisShellLayout.create(phone.first, phone.second)
        assertTrue(shell.requiresVerticalScroll)
        assertNull(shell[Slot.NAV_RAIL])
        assertNull(shell[Slot.BOTTOM_NAV])
        assertTrue(shell.hasFeatureTiles)
        assertEquals(10, shell.tileBoxes().size)
    }

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
