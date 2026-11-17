package at.heinz.deviceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Falsches Werkzeugrouting durch Negation (gefunden per Routing-Sonde 03.11.2026 auf
 * PR-#368-Kopf 85602f73): Speicher/Akku waren klauselweise negationsfest, alle übrigen
 * read-only Gerätewerkzeuge reagierten noch auf bloße Teilstrings.
 */
class BootstrapBrainNegatedToolRoutingTest {
    private fun decide(s: String) = BootstrapBrain.decide(s, emptyList())

    @Test fun negatedOrIncidentalDeviceNounsNeverStartAReadOnlyTool() {
        listOf(
            "Zeig mir nicht meine Downloads",
            "Ich will nicht, dass du meine Downloads öffnest",
            "Ich habe keine Downloads gemacht, wie geht es dir?",
            "Prüfe nicht mein Gerät",
            "Kein Netzwerk prüfen, sag mir nur einen Witz",
            "Schließe alle Apps",
            "Zeige niemals die installierten Apps"
        ).forEach { utterance ->
            val decision = decide(utterance)
            assertNull(utterance, decision.tool)
            assertEquals(utterance, BootstrapKind.REPLY, decision.kind)
        }
    }

    @Test fun affirmativeReadOnlyRequestsStillWork() {
        assertEquals("list_files", decide("Zeig mir meine Downloads").tool)
        assertEquals("list_files", decide("Downloads").tool)
        assertEquals("get_device_info", decide("Prüfe mein Gerät").tool)
        assertEquals("get_network_summary", decide("Wie ist meine Netzwerkverbindung? Netzwerk prüfen").tool)
        assertEquals("list_installed_apps", decide("Zeige alle Apps").tool)
        assertEquals("list_installed_apps", decide("Welche installierten Apps habe ich?").tool)
        // Contrast clause: the negated part is ignored, the affirmative part is kept.
        assertEquals("get_battery_info", decide("Nicht die installierten Apps zeigen, sondern den Akku prüfen").tool)
        assertEquals("list_files", decide("Nicht das Netzwerk, sondern zeige die Downloads").tool)
    }
}
