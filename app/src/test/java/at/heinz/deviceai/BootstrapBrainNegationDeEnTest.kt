package at.heinz.deviceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Negation de/en (Routing-Sonde 03.11.2026 auf 7316b1f5): Die Klausel-Negation kannte nur
 * "nicht/niemals/ohne/unterlasse/kein…". Englische Verneinungen und "nie/weder" starteten
 * trotzdem ein read-only Gerätewerkzeug; "große Dateien" war gar nicht negationsfest.
 */
class BootstrapBrainNegationDeEnTest {
    private fun decide(s: String) = BootstrapBrain.decide(s, emptyList())

    @Test fun englishAndShortGermanNegationsNeverStartADeviceTool() {
        listOf(
            "Don't show my downloads",
            "Don\u2019t show my downloads",
            "Do not open my downloads, just say hi",
            "Never list my downloads",
            "Please don't check the Lumo version",
            "Zeig mir nie meine Downloads",
            "Weder Downloads noch Apps zeigen",
            "Zeige keine große Dateien",
            "Ich will nicht, dass du große Dateien suchst"
        ).forEach { utterance ->
            val decision = decide(utterance)
            assertNull(utterance, decision.tool)
            assertEquals(utterance, BootstrapKind.REPLY, decision.kind)
        }
    }

    @Test fun affirmativeRequestsStillReachTheirReadOnlyTool() {
        assertEquals("list_files", decide("Show my downloads").tool)
        assertEquals("list_files", decide("Zeig mir meine Downloads").tool)
        assertEquals("get_storage_info", decide("Zeige große Dateien").tool)
        assertEquals("get_app_info", decide("Prüfe die Lumo Version").tool)
        // "Notiz" / "Nietzsche" / "nobody" must not be mistaken for a negation word.
        assertEquals("list_files", decide("Zeig die Downloads mit der Notiz").tool)
        assertEquals("list_files", decide("Downloads von Nietzsche zeigen").tool)
        // Contrast clause stays independent.
        assertEquals("list_files", decide("Nicht das Netzwerk, sondern zeige die Downloads").tool)
    }
}
