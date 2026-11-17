package at.heinz.deviceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuumNexusHomeSpecTest {
    @Test fun beginnerNavigationHasDistinctDestinations() {
        val items = ContinuumNexusHomeSpec.essentials
        assertEquals(4, items.size)
        assertEquals(4, items.map { it.destination }.toSet().size)
        assertEquals(ContinuumNexusDestination.CHAT, ContinuumNexusHomeSpec.voice.destination)
        assertTrue(items.none { it.destination == ContinuumNexusDestination.CHAT })
    }

    @Test fun beginnerLabelsCommunicateRealFunctions() {
        val labels = ContinuumNexusHomeSpec.essentials.map { it.title }
        assertEquals(listOf("App erstellen", "Meine Dateien & Wissen",
            "KI personalisieren", "Meine Aufgaben"), labels)
        assertTrue(ContinuumNexusHomeSpec.voice.title.contains("sprechen"))
        assertFalse(ContinuumNexusHomeSpec.essentials.any {
            it.title.isBlank() || it.description.isBlank()
        })
    }

    @Test fun narrowAndEnlargedTextLayoutsStayReadable() {
        assertEquals(1, ContinuumNexusHomeSpec.columns(270, 1f))
        assertEquals(2, ContinuumNexusHomeSpec.columns(320, 1f))
        assertEquals(2, ContinuumNexusHomeSpec.columns(620, 1.25f))
        assertEquals(1, ContinuumNexusHomeSpec.columns(620, 1.5f))
        assertEquals(1, ContinuumNexusHomeSpec.columns(620, Float.NaN))
        assertEquals(1, ContinuumNexusHomeSpec.columns(620, Float.POSITIVE_INFINITY))
    }
}
