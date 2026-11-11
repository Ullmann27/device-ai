package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OwnerAppLookupNaturalRegressionTest {
    @Test fun addressedCommandsResolveGeneralAppNames() {
        mapOf(
            "Jarvis öffne mir WhatsApp" to "WhatsApp",
            "Hallo Jarvis, starte bitte Kalender" to "Kalender",
            "Continuum, öffne die App Signal bitte" to "Signal"
        ).forEach { (input, app) -> assertEquals(input, app, OwnerAppLookupIntent.parse(input)) }
    }

    @Test fun politeAndReorderedRequestsResolveGeneralApps() {
        mapOf(
            "Kannst du mir bitte WhatsApp öffnen?" to "WhatsApp",
            "Würdest du bitte Kalender starten?" to "Kalender",
            "Könntest du bitte Microsoft Teams öffnen?" to "Microsoft Teams",
            "Ich möchte Google Maps öffnen" to "Google Maps",
            "Mach bitte die App Signal auf." to "Signal",
            "Öffne bitte für mich den Kalender." to "Kalender"
        ).forEach { (input, app) -> assertEquals(input, app, OwnerAppLookupIntent.parse(input)) }
    }

    @Test fun compoundNegatedQuotedAndVagueRequestsAreNeverTruncatedIntoLookup() {
        listOf(
            "Jarvis kannst du mir bitte WhatsApp öffnen? Und meinem Vater schreiben?",
            "Jarvis öffne WhatsApp nicht",
            "Bitte erkläre den Satz: öffne Signal",
            "\"Öffne Signal\"",
            "Jarvis ich bräuchte etwas von Kalender",
            "Öffne Signal oder Kalender",
            "Öffne WhatsApp, sende alles"
        ).forEach { assertNull(it, OwnerAppLookupIntent.parse(it)) }
    }
}
