package at.heinz.deviceai.cognitive

import at.heinz.deviceai.BootstrapBrain
import org.junit.Assert.*
import org.junit.Test

/** Code55 field P0 #1 (Fold7, Code54 offline): a local battery request must never become a web search. */
class FieldP0BatteryRoutingTest {
    private val field = "Prüfe nicht das WLAN, sondern nur den Akku. Zeige mir den aktuellen Akkustand, die " +
        "Akkutemperatur und den Android-Thermalstatus, soweit verfügbar. Verändere keine Einstellungen."

    @Test fun theFieldSentenceStaysOnTheLocalBatteryTool() {
        assertFalse(PublicKnowledgeTopicRouter.isResearchRequest(field))
        assertNull(PublicKnowledgeTopicRouter.explicitTopic(field))
        assertFalse(OwnerDeviceRequestSafety.inspect(field).blocked)
        assertEquals("get_battery_info", BootstrapBrain.decide(field, emptyList()).tool)
        listOf("Prüfe den aktuellen Akkustand", "Prüfe meinen Speicherplatz aktuell",
            "Suche den aktuellen Thermalstatus").forEach {
            assertFalse(it, PublicKnowledgeTopicRouter.isResearchRequest(it))
        }
    }

    @Test fun genuineResearchAboutBatteriesStaysResearch() {
        listOf("Recherchiere aktuelle Akku-Technologie", "Suche aktuelle Infos zu Akkus von Samsung",
            "Suche im Internet nach dem aktuellen Akkustand-Rekord", "Prüfe aktuelle Informationen zur Inflation").forEach {
            assertTrue(it, PublicKnowledgeTopicRouter.isResearchRequest(it))
        }
    }
}
