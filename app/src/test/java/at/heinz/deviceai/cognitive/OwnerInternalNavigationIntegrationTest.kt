package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class OwnerInternalNavigationIntegrationTest {
    @Test fun exactOwnerMemoryCommandRetainsTheExistingInternalTarget() {
        assertOpening("Jarvis, öffne das Gedächtniszentrum.", "gedächtniszentrum")
    }

    @Test fun naturalMemoryCommandsReachTheSameTarget() {
        listOf(
            "Zeig mir dein Gedächtniszentrum.",
            "Jarvis, mach bitte das Gedächtnis Zentrum auf.",
            "Bitte oeffne das Gedachtniszentrum.",
            "Kannst du mir deine Erinnerungen zeigen?"
        ).forEach { assertOpening(it, "gedächtniszentrum") }
    }

    @Test fun logicConjunctionIsATitleNotAnAdditionalToolAction() {
        listOf("Zeig mir deine Logik und Planung.", "Öffne Logik und Planung.",
            "Kannst du mir die Planung zeigen?").forEach { assertOpening(it, "logik und planung") }
    }

    @Test fun naturalWorkOrderOverviewIsNavigationOnly() {
        assertOpening("Zeig mir die Arbeitsaufträge.", "entwicklungsaufgaben")
    }

    @Test fun fileNamesAndCompoundActionsNeverBecomeInternalNavigation() {
        assertNull(OwnerAppLookupIntent.parse("Öffne die Datei Gedächtniszentrum.pdf"))
        assertNotNull(OwnerDocumentIntent.parse("Öffne die Datei Gedächtniszentrum.pdf"))
        listOf("Öffne Gedächtniszentrum und sende eine Nachricht.",
            "Öffne nicht Gedächtniszentrum.", "Öffne Logik und Planung und lösche Fakten.",
            "Öffne Gedächtniszentrum oder WhatsApp.").forEach {
            assertNull(it, OwnerAppLookupIntent.parse(it))
        }
    }

    @Test fun externalAppsAndQuestionsKeepTheirExistingMeaning() {
        assertOpening("Öffne WhatsApp.", "WhatsApp")
        assertNull(OwnerAppLookupIntent.inspect("Wie funktioniert dein Gedächtniszentrum?"))
        assertNull(OwnerAppLookupIntent.parse("Schreib eine Nachricht."))
    }

    private fun assertOpening(request: String, expected: String) {
        val intake = OwnerAppLookupIntent.inspect(request) as? OwnerAppLookupIntent.Intake.Lookup
        assertNotNull(request, intake)
        assertEquals(request, expected.lowercase(), intake!!.query.lowercase())
        assertTrue(request, intake.opening)
        assertFalse(request, intake.requireInstalledCandidate)
    }
}
