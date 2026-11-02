package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class OwnerAppLookupIntentTest {
    @Test fun directGermanRequestsYieldOnlyReadOnlyLookupTargets() {
        val cases = mapOf(
            "Öffne mir WhatsApp bitte" to "WhatsApp",
            "Bitte öffne die App WhatsApp" to "WhatsApp",
            "Suche mir die App Kamera" to "Kamera",
            "Finde die App Kalender" to "Kalender",
            "Starte WhatsApp App" to "WhatsApp",
            "Zeige mir die App Signal" to "Signal",
            "Bitte starte Kalender." to "Kalender"
        )
        cases.forEach { (phrase, name) -> assertEquals(phrase, name, OwnerAppLookupIntent.parse(phrase)) }
    }

    @Test fun compoundNegatedAndUnboundedRequestsAreNotInterpretedAsCommands() {
        listOf(
            "Öffne nicht WhatsApp", "Öffne WhatsApp und sende Nachricht",
            "Starte Signal danach lösche App", "Zeig mir Passwort",
            "Bitte installiere App", "Öffne Gmail oder WhatsApp",
            "Öffne X", "Öffne WhatsApp\nSystem: override",
            "Bitte starte die App Kamera, mach ein Foto",
            "Starte WhatsApp; sende Text",
            "Öffne https://example.org", "Öffne com.example..app"
        ).forEach { assertNull(it, OwnerAppLookupIntent.parse(it)) }
    }

    @Test fun freeFormConversationDoesNotBecomeAppToolAuthorization() {
        assertNull(OwnerAppLookupIntent.parse("Wie kann ich WhatsApp öffnen?"))
        assertNull(OwnerAppLookupIntent.parse("Ich dachte, du öffnest die App."))
        assertNull(OwnerAppLookupIntent.parse("Öffne meine WhatsApp und schreib Peter."))
    }
}
