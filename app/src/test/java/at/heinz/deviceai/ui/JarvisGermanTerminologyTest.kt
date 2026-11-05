package at.heinz.deviceai.ui

import at.heinz.deviceai.chief.TurnPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner order 2026-11-05: every visible cockpit term is German; routing IDs stay unchanged. */
class JarvisGermanTerminologyTest {
    private val owner = mapOf("home" to "Start", "chat" to "Gespräch", "memory" to "Gedächtnis",
        "logic" to "Denken und Planen", "research" to "Forschung", "agents" to "Assistenten", "tasks" to "Aufgaben",
        "web" to "Internet", "learning" to "Lernen", "device" to "Gerätesteuerung", "system" to "Systemstatus")
    private val english = Regex("\\b(Home|Chat|Memory|Logic|Research|Agents|Tasks|Web|Learning|Device|Control|" +
        "Model|Mode|Local|Active|Current|Flow|Activity|Your|Camera|Brain|Reason|Evolve|Unified|Companion)\\b")

    @Test fun railAndTilesUseTheOwnerTermsForTheUnchangedTargets() {
        assertEquals(owner, JarvisTiles.railNav.associate { it.target to it.label })
        assertEquals(owner - "home", JarvisTiles.reference.associate { it.target to it.label })
        assertEquals(JarvisTiles.railNav.map { it.target }, JarvisMenu.railEntries.map { it.target })
    }

    @Test fun visibleCockpitTextContainsNoEnglishReferenceWords() {
        val texts = mutableListOf<String>()
        texts += JarvisTiles.railNav.map { it.label } + JarvisTiles.reference.map { it.label }
        texts += JarvisCockpitGridContent.tileSubtitles.values.flatten()
        val state = ContinuumDashboardState(cockpit = CockpitStatus(turnPhase = TurnPhase.WORKING))
        texts += JarvisCockpitGridContent.modules(state).map { it.label }
        texts += listOf(JarvisShellLayout.Slot.ROW_TASKS, JarvisShellLayout.Slot.ROW_MEMORY, JarvisShellLayout.Slot.ROW_MODEL)
            .map { JarvisCockpitGridContent.title(it, state) }
        texts += MemoryActivitySnapshot().rows().map { it.second } + JarvisMemoryActivityPanel.TITLE
        texts += LocalModelStatusPresentation.rows(state).map { it.label }
        texts += CognitiveWorkPresentation.Phase.entries.map { it.module }
        texts += JarvisCockpitGridContent.MODE_LABEL
        texts.forEach { assertFalse("English reference word in \"$it\"", english.containsMatchIn(it)) }
    }

    @Test fun longGermanLabelsShrinkBeforeTheyAreEllipsized() {
        val p = JarvisPresentationRecordingPainter()
        val style = JarvisDesign.Type.tileLabel
        val label = "Denken und Planen"
        val full = p.measureText(label, style)
        val shrunk = JarvisShellRenderer.shrinkToFit(p, label, style, full * .9f)
        assertTrue(shrunk.size < style.size)
        assertEquals(label, JarvisShellRenderer.fit(p, label, shrunk, full * .9f))
        val floor = JarvisShellRenderer.shrinkToFit(p, label, style, full * .5f)
        assertEquals(style.size * .82f, floor.size, 1e-4f)
        assertTrue(JarvisShellRenderer.fit(p, label, floor, full * .5f).endsWith("…"))
        assertEquals(style, JarvisShellRenderer.shrinkToFit(p, "Lernen", style, full))
    }

    @Test fun railBreaksGermanCompoundsInsteadOfCuttingThem() {
        val p = JarvisPresentationRecordingPainter()
        val style = JarvisDesign.Type.nav
        val room = p.measureText("Gerätesteu", style)
        assertEquals(listOf("Geräte-", "steuerung"), JarvisTiles.railLines(p, "Gerätesteuerung", style, room))
        assertEquals(listOf("System-", "status"), JarvisTiles.railLines(p, "Systemstatus", style, room))
        assertEquals(listOf("Denken und", "Planen"), JarvisTiles.railLines(p, "Denken und Planen", style, room))
        assertEquals(listOf("Lernen"), JarvisTiles.railLines(p, "Lernen", style, room))
    }
}
