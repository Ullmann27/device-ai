package at.heinz.deviceai.cognitive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkTurnConstraintTest {
    private val negative = listOf(
        "Mara hat drei Würfel. Antworte ohne Internetrecherche.",
        "Erkläre mir maschinelles Lernen ohne Internet.",
        "Beantworte diese Frage ohne Websuche.",
        "Bitte antworte ohne externe Websuche.",
        "Suche nicht im Internet.",
        "Recherchiere nicht online.",
        "Bitte recherchiere jetzt nicht im Web.",
        "Ich möchte keinen Internetzugriff.",
        "Keine Internetrecherche für diese Frage.",
        "Niemals ins Internet.",
        "Nicht im Browser recherchieren.",
        "Nutze kein Internet.",
        "Nur lokal antworten.",
        "Nutze nur lokal vorhandene Informationen.",
        "Beantworte die Frage offline.",
        "Nutze ausschließlich offline gespeicherte Belege.",
        "Bitte keine Webrecherche.",
        "Nicht online suchen.",
        "Ohne Netzwerkzugriff arbeiten.",
        "Beantworte mit den Fakten ohne Cloudzugriff.",
        "Suche im Internet nach einer Quelle, aber antworte ohne Internetrecherche.",
        "Suche nicht im Internet, sondern in meinem Archiv."
    )

    private val positive = listOf(
        "Recherchiere bitte das Thema maschinelles Lernen.",
        "Suche im Internet nach Fachliteratur.",
        "Bitte nutze die Websuche für diese Frage.",
        "Lies die Seite https://www.wikipedia.org.",
        "Suche online nach aktuellen Quellen.",
        "Nutze Internetrecherche.",
        "Recherchiere nicht nur online, sondern auch in Büchern."
    )

    @Test fun prohibitsTwentyTwoContrastingTurnsEvenIfWebUiWasArmed() {
        for (text in negative) {
            assertTrue(text, NetworkTurnConstraint.forbidsExternalResearch(text))
            assertNull(text, WebToolRequest.fromChat(text, true))
        }
    }

    @Test fun legitimateWebRequestsAreNotBlocked() {
        for (text in positive) {
            assertFalse(text, NetworkTurnConstraint.forbidsExternalResearch(text))
        }
        assertNotNull(WebToolRequest.fromChat("Recherchiere Klimawandel", false))
        assertNotNull(WebToolRequest.fromChat("Suche im Internet nach Wasser", false))
    }

    @Test fun askAndAlwaysOnNeverMintNetworkTicketForNegatedTurn() {
        val ask = ApprovedWebToolGate(InternetConsentPolicy(InternetAccessMode.ASK))
        val always = ApprovedWebToolGate(InternetConsentPolicy(InternetAccessMode.ALWAYS_ON))
        for (text in negative) {
            val call = WebToolRequest.Search(text)
            assertNull(text, ask.prepare(call))
            assertNull(text, always.authorizeAlwaysOn(call, ResearchMode.QUICK))
        }
    }

    @Test fun ownerCanStillExplicitlyAuthorizeNonNegatedSearch() {
        val consent = InternetConsentPolicy(InternetAccessMode.ASK)
        val gate = ApprovedWebToolGate(consent)
        val call = WebToolRequest.Search("Aktuelle Forschung zu Batterietechnik")
        val ticket = gate.prepare(call)
        assertNotNull(ticket)
        assertNotNull(gate.approve(ticket!!, ResearchMode.QUICK))
        val always = ApprovedWebToolGate(InternetConsentPolicy(InternetAccessMode.ALWAYS_ON))
        assertNotNull(always.authorizeAlwaysOn(call, ResearchMode.QUICK))
    }

    @Test fun mixedNegativePositiveFailsClosedAndCannotReusePriorApproval() {
        val gate = ApprovedWebToolGate(InternetConsentPolicy(InternetAccessMode.ASK))
        val old = gate.prepare(WebToolRequest.Search("Energieeffizienz"))
        assertNotNull(old)
        assertNull(gate.prepare(WebToolRequest.Search(
            "Recherchiere Energieeffizienz, aber ohne Internetrecherche")))
        assertNull(gate.approve(old!!, ResearchMode.QUICK))
    }
}
