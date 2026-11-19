package at.heinz.deviceai.ui

import at.heinz.deviceai.cockpit.ProcessStep
import at.heinz.deviceai.cockpit.StepState
import at.heinz.deviceai.ui.JarvisShellLayout.Box
import at.heinz.deviceai.ui.JarvisShellLayout.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Design-Lead 17.11.2026: honest standby hologram for empty work windows (Fold7 inner + phones). */
class JarvisVacantHologramTest {
    private val foldInner = 884f to 760f
    private val phone = 412f to 915f
    private val xiaomi = 393f to 873f

    @Test fun layoutKeepsEmblemProjectorAndCaptionInsideTheArea() {
        for (area in listOf(Box(0f, 0f, 300f, 220f), Box(10f, 40f, 140f, 200f), Box(0f, 0f, 120f, 90f))) {
            for (hintLines in 0..3) {
                val l = JarvisVacantHologram.layout(area, 12f, hintLines, 12f)
                assertTrue(l.emblem == 0f || l.emblem >= JarvisVacantHologram.MIN_EMBLEM_DP)
                assertTrue(l.emblem <= JarvisVacantHologram.MAX_EMBLEM_DP)
                if (l.emblem > 0f) {
                    assertTrue(l.emblemCy - l.emblem / 2f >= area.top - .5f)
                    assertTrue(l.projectorY < l.textTop)
                    assertTrue(l.emblem <= area.width * .40f + .01f)
                }
                assertEquals(area.centerX, l.emblemCx, .001f)
            }
        }
    }

    @Test fun tinyAreaFallsBackToTextOnly() {
        val l = JarvisVacantHologram.layout(Box(0f, 0f, 50f, 40f), 10f, 1, 10f)
        assertEquals(0f, l.emblem, 0f)
    }

    @Test fun copyNamesTheRealEntryPointAndInventsNoValues() {
        JarvisVacantHologram.Kind.values().forEach { kind ->
            val c = JarvisVacantHologram.copy(kind)
            listOf(c.title, c.hint, c.shortHint).forEach { text ->
                assertFalse(text, Regex("\\d").containsMatchIn(text))
                assertFalse(text, text.contains(".pdf", ignoreCase = true))
                assertFalse(text, text.contains('%'))
            }
        }
        assertTrue(JarvisVacantHologram.copy(JarvisVacantHologram.Kind.DOCUMENT).hint.contains("„Dateien“"))
        assertTrue(JarvisTiles.bottomNav.any { it.label == "Dateien" })
    }

    @Test fun captionsAreNeverCutMidSentenceOnPhonesOrFold() {
        for ((w, h) in listOf(foldInner, phone, xiaomi)) {
            val shell = JarvisShellLayout.create(w, h)
            val painter = JarvisPresentationRecordingPainter()
            val idle = ContinuumDashboardState()
            for (slot in listOf(Slot.WORK_DOCUMENT, Slot.WORK_PROCESS, Slot.WORK_DRAFT)) {
                val box = shell[slot] ?: continue
                JarvisCockpitGridContent.draw(painter, slot, box, shell.grid, idle, 0L, shell)
            }
            val captions = JarvisVacantHologram.Kind.values().map { JarvisVacantHologram.copy(it) }
            val words = captions.flatMap { (it.title + " " + it.hint + " " + it.shortHint).split(' ') }.toSet()
            painter.texts.filter { t -> t.isNotBlank() && t.split(' ').all { it in words } }.forEach { t ->
                assertFalse("ellipsized caption on ${w}x$h: $t", t.endsWith("\u2026"))
            }
            assertTrue("${w}x$h", painter.texts.any { it == "Kein Dokument geöffnet" || it == "Kein Dokument" })
        }
    }

    @Test fun realStepsReplaceTheStandbyHologram() {
        val shell = JarvisShellLayout.create(foldInner.first, foldInner.second)
        val painter = JarvisPresentationRecordingPainter()
        val live = ContinuumDashboardState(cockpit = CockpitStatus(
            steps = listOf(ProcessStep("read", "Datei lesen", StepState.ACTIVE))))
        JarvisCockpitGridContent.draw(painter, Slot.WORK_PROCESS, shell[Slot.WORK_PROCESS]!!, shell.grid, live, 0L, shell)
        assertTrue(painter.texts.any { it == "Datei lesen" })
        assertFalse(painter.texts.any { it == JarvisVacantHologram.copy(JarvisVacantHologram.Kind.PROCESS).hint })
    }

    @Test fun narrowWindowsKeepFullTitles() {
        assertEquals("Entwurf", JarvisCockpitPanels.draftTitle(130f))
        assertEquals("Zusammenfassung – Entwurf", JarvisCockpitPanels.draftTitle(280f))
        val painter = JarvisPresentationRecordingPainter()
        val (label, _) = JarvisCockpitGridContent.firstFitting(painter, JarvisCockpitGridContent.CAMERA_TITLES,
            JarvisDesign.Type.heading, 70f)
        assertFalse(label.endsWith("\u2026"))
    }

    @Test fun phoneDialogKeepsTheFullJarvisName() {
        for ((w, h) in listOf(phone, xiaomi, foldInner)) {
            val shell = JarvisShellLayout.create(w, h)
            val box = shell[Slot.DIALOG] ?: continue
            val painter = JarvisPresentationRecordingPainter()
            JarvisCockpitGridContent.draw(painter, Slot.DIALOG, box, shell.grid, ContinuumDashboardState(), 0L, shell)
            if (shell.workstation) continue
            assertTrue("${w}x$h ${painter.texts}", painter.texts.any { it == "JARVIS" })
        }
    }
}
