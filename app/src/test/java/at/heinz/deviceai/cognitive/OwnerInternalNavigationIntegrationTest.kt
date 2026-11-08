package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

/**
 * #639 owner sentences against the single canonical recognizer (OwnerInternalNavigationIntent, PR #654).
 * An internal screen must never fall through to the installed-app lookup.
 */
class OwnerInternalNavigationIntegrationTest {
    @Test fun exactOwnerMemoryCommandRetainsTheExistingInternalTarget() {
        assertScreen("Jarvis, öffne das Gedächtniszentrum.", "memory")
    }

    @Test fun naturalMemoryCommandsReachTheSameTarget() {
        listOf(
            "Zeig mir dein Gedächtniszentrum.",
            "Jarvis, mach bitte das Gedächtnis Zentrum auf.",
            "Bitte oeffne das Gedachtniszentrum.",
            "Kannst du mir deine Erinnerungen zeigen?"
        ).forEach { assertScreen(it, "memory") }
    }

    @Test fun logicConjunctionIsATitleNotAnAdditionalToolAction() {
        listOf("Zeig mir deine Logik und Planung.", "Öffne Logik und Planung.",
            "Kannst du mir die Planung zeigen?").forEach { assertScreen(it, "logic") }
    }

    @Test fun naturalWorkOrderOverviewIsNavigationOnly() {
        assertScreen("Zeig mir die Arbeitsaufträge.", "tasks")
    }

    @Test fun fileNamesAndCompoundActionsNeverBecomeInternalNavigation() {
        assertNull(OwnerInternalNavigationIntent.resolve("Öffne die Datei Gedächtniszentrum.pdf"))
        assertNull(OwnerAppLookupIntent.parse("Öffne die Datei Gedächtniszentrum.pdf"))
        assertNotNull(OwnerDocumentIntent.parse("Öffne die Datei Gedächtniszentrum.pdf"))
        listOf("Öffne Gedächtniszentrum und sende eine Nachricht.",
            "Öffne nicht Gedächtniszentrum.", "Öffne Logik und Planung und lösche Fakten.",
            "Öffne Gedächtniszentrum oder WhatsApp.").forEach {
            assertNull(it, OwnerInternalNavigationIntent.resolve(it))
            assertNull(it, OwnerAppLookupIntent.parse(it))
        }
    }

    @Test fun externalAppsAndQuestionsKeepTheirExistingMeaning() {
        assertNull(OwnerInternalNavigationIntent.resolve("Öffne WhatsApp."))
        assertEquals("WhatsApp", OwnerAppLookupIntent.parse("Öffne WhatsApp."))
        assertNull(OwnerInternalNavigationIntent.resolve("Wie funktioniert dein Gedächtniszentrum?"))
        assertNull(OwnerAppLookupIntent.inspect("Wie funktioniert dein Gedächtniszentrum?"))
        assertNull(OwnerInternalNavigationIntent.resolve("Schreib eine Nachricht."))
        assertNull(OwnerAppLookupIntent.parse("Schreib eine Nachricht."))
        assertNull(OwnerInternalNavigationIntent.resolve("Suche mein PDF."))
    }

    private fun assertScreen(request: String, expected: String) {
        assertEquals(request, expected, OwnerInternalNavigationIntent.resolve(request, "Jarvis")?.target)
        assertNull("internal screen leaked into app lookup: $request", OwnerAppLookupIntent.inspect(request, "Jarvis"))
    }
}
