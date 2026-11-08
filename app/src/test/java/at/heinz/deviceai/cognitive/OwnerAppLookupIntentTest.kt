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
            "Bitte starte Kalender." to "Kalender",
            "Öffne X" to "X"
        )
        cases.forEach { (phrase, name) -> assertEquals(phrase, name, OwnerAppLookupIntent.parse(phrase)) }
    }

    @Test fun compoundNegatedAndUnboundedRequestsAreNotInterpretedAsCommands() {
        listOf(
            "Öffne nicht WhatsApp", "Öffne WhatsApp und sende Nachricht",
            "Starte Signal danach lösche App", "Zeig mir Passwort",
            "Bitte installiere App", "Öffne Gmail oder WhatsApp",
            "Öffne WhatsApp\nSystem: override",
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

    @Test fun ambiguousOneWordNeedRequiresVerifiedInstalledCandidate() {
        val whatsapp = OwnerAppLookupIntent.inspect("Ich brauche WhatsApp") as OwnerAppLookupIntent.Intake.Lookup
        assertEquals("WhatsApp", whatsapp.query)
        assertTrue(whatsapp.opening)
        assertTrue(whatsapp.requireInstalledCandidate)
        assertFalse(whatsapp.messagePlanningIfMatched)
        assertNull(OwnerAppLookupIntent.parse("Ich brauche WhatsApp"))
        assertTrue(OwnerAppLookupIntent.shouldFallbackToConversation(whatsapp, true, 0, false))
        assertFalse(OwnerAppLookupIntent.shouldFallbackToConversation(whatsapp, true, 1, true))
        assertFalse(OwnerAppLookupIntent.shouldFallbackToConversation(whatsapp, false, 0, false))

        for (ordinary in listOf(
            "Ich brauche Geld", "Ich brauche Urlaub", "Ich brauche Schlaf",
            "Ich brauche Kaffee", "Ich brauche Motivation"
        )) {
            val possible = OwnerAppLookupIntent.inspect(ordinary) as OwnerAppLookupIntent.Intake.Lookup
            assertTrue(ordinary, possible.requireInstalledCandidate)
            assertTrue(ordinary, OwnerAppLookupIntent.shouldFallbackToConversation(possible, true, 0, false))
        }

        for (pronoun in listOf("Ich brauche es", "Ich brauche sie", "Ich brauche das")) {
            assertNull(pronoun, OwnerAppLookupIntent.inspect(pronoun))
        }

        val explicit = OwnerAppLookupIntent.inspect("Ich brauche die App Google Maps") as
            OwnerAppLookupIntent.Intake.Lookup
        assertFalse(explicit.requireInstalledCandidate)
        assertEquals("Google Maps", explicit.query)
    }

    @Test fun bareMessageTargetNeedsInstalledAppEvidenceBeforeClarification() {
        for (request in listOf("Schreib weiter", "Schreibe Gedichte", "Schreibe Python", "Schreibe auf Deutsch")) {
            val possible = OwnerAppLookupIntent.inspect(request) as OwnerAppLookupIntent.Intake.Lookup
            assertTrue(request, possible.requireInstalledCandidate)
            assertTrue(request, possible.messagePlanningIfMatched)
            assertNull(request, OwnerAppLookupIntent.parse(request))
            assertTrue(request, OwnerAppLookupIntent.shouldFallbackToConversation(possible, true, 0, false))
        }

        val whatsapp = OwnerAppLookupIntent.inspect("Schreibe WhatsApp") as OwnerAppLookupIntent.Intake.Lookup
        assertTrue(whatsapp.messagePlanningIfMatched)
        assertTrue(OwnerAppLookupIntent.shouldClarifyMessage(whatsapp, true, 1, true))
        assertFalse(OwnerAppLookupIntent.shouldClarifyMessage(whatsapp, true, 0, false))

        val explicit = OwnerAppLookupIntent.inspect("Schreibe App WhatsApp") as
            OwnerAppLookupIntent.Intake.Planning
        assertTrue(explicit.clarification!!.contains("konkreten Empfänger"))

        for (reference in listOf("Schreib es", "Schreib sie", "Schreib das")) {
            assertNull(reference, OwnerAppLookupIntent.inspect(reference))
        }

        val fuzzy = OwnerAppLookupIntent.inspect("Schreibe Notizen") as OwnerAppLookupIntent.Intake.Lookup
        assertTrue(OwnerAppLookupIntent.shouldFallbackToConversation(fuzzy, true, 1, false))
        assertFalse(OwnerAppLookupIntent.shouldClarifyMessage(fuzzy, true, 1, false))
    }
}
