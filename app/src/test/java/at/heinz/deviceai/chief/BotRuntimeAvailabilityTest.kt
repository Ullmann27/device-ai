package at.heinz.deviceai.chief

import at.heinz.deviceai.localmodel.LocalModelState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner Fold7 report (06.11.2026): bot cards said „Lokales Modell nicht geladen“ although Gemma had answered before.
 * After an app restart the original model is restored asynchronously (LOADING, loadedModel still null), and a failed
 * load also has no model: both were shown as „nicht geladen“. One shared runtime; no READY is ever invented.
 */
class BotRuntimeAvailabilityTest {
    private fun text(loaded: Boolean, state: LocalModelState, active: Boolean = false, busy: Boolean = false) =
        BotRuntimeAvailability.text(loaded, state, active, busy)

    @Test fun loadingAndFailedAreNotShownAsNotLoaded() {
        assertTrue(text(false, LocalModelState.LOADING), text(false, LocalModelState.LOADING).startsWith("Lokales Modell wird geladen"))
        val failed = text(false, LocalModelState.FAILED)
        assertTrue(failed, failed.startsWith("Laden des lokalen Modells fehlgeschlagen") && failed.contains("Modellverwaltung"))
        val empty = text(false, LocalModelState.EMPTY)
        assertTrue("explicit bridge, no automatic load: $empty", empty.startsWith("Lokales Modell nicht geladen") && empty.contains("Modellverwaltung"))
        assertTrue(text(false, LocalModelState.UNLOADING).startsWith("Lokales Modell wird entladen"))
    }

    @Test fun readyBusyAndActiveStatesStayTruthful() {
        assertEquals("Für einen Auftrag erreichbar", text(true, LocalModelState.READY))
        assertEquals("Lokales Modell arbeitet", text(true, LocalModelState.GENERATING, active = true))
        assertEquals("Lokaler Auftrag läuft", text(true, LocalModelState.READY, active = true, busy = true))
        assertEquals("another bot's work occupies the one runtime", "Gemeinsame Ausführung belegt", text(true, LocalModelState.GENERATING))
        for (state in LocalModelState.entries) assertFalse("never READY without a model: $state",
            text(false, state) == "Für einen Auftrag erreichbar")
    }
}
