package at.heinz.deviceai.cognitive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkTurnConstraintTest {
    @Test fun ownerLocalQuestionsCannotBecomeRewrittenWebSearchesEvenWithAlwaysOn() {
        for (ownerTurn in listOf("Hallo?", "Was kannst du?", "Wie viel Speicher habe ich?")) {
            org.junit.Assert.assertEquals(ownerTurn, 0, modelProposedNetworkExecutions(ownerTurn))
        }
    }

    @Test fun publicQuestionStillUsesTheExistingOwnerAuthorizedWebPath() {
        org.junit.Assert.assertEquals(1, modelProposedNetworkExecutions("Was ist Photosynthese?"))
    }

    private fun modelProposedNetworkExecutions(ownerTurn: String): Int {
        // This is a behavior fixture, not real model inference or real internet.
        val rewrittenQuery = "Funktionen eines Android-Assistenten"
        val webConsent = ApprovedWebToolGate(InternetConsentPolicy(InternetAccessMode.ALWAYS_ON))
        var networkExecutions = 0
        var turns = 0
        val agent = BoundedAgentOrchestrator(
            model = AgentModel { _, _, _, _ ->
                if (turns++ == 0) AgentModelOutput("fixture-model", "Werkzeugvorschlag",
                    at.heinz.deviceai.ToolProposal("web_search", mapOf("query" to rewrittenQuery)))
                else AgentModelOutput("fixture-model", "Lokale Antwort")
            },
            memories = { emptyList() },
            toolGate = at.heinz.deviceai.ToolGate(
                at.heinz.deviceai.ToolPolicy(readOnlyEnabled = true),
                at.heinz.deviceai.ToolRunner { request ->
                    NetworkTurnConstraint.withOriginalTurn(ownerTurn, request.name) {
                        networkExecutions++
                        "Fixture-Ergebnis"
                    }
                }
            ),
            evaluator = AgentEvaluator { _, _ -> Evaluation(true, 1.0) },
            experienceStore = InMemoryExperienceStore(),
            approvalProvider = AgentToolApprovalProvider { _, _ ->
                AgentToolApproval(webConsent.authorizeAlwaysOn(
                    WebToolRequest.Search(rewrittenQuery), ResearchMode.QUICK) != null)
            }
        )
        agent.run(ownerTurn)
        return networkExecutions
    }

    @Test fun originalOfflineTurnCannotBeBypassedByARewrittenModelQuery() {
        var executions = 0
        val delegate = at.heinz.deviceai.ToolRunner { executions++; "executed" }
        for (original in listOf("Antworte ohne Internetrecherche", "Nur lokal antworten")) {
            for (tool in listOf("web_search", "web_read")) {
                val rewritten = at.heinz.deviceai.ToolCallRequest("rewritten-$tool", tool,
                    if (tool == "web_search") mapOf("query" to "Wetter Wien") else mapOf("url" to "https://example.com/weather"))
                org.junit.Assert.assertThrows(IllegalStateException::class.java) {
                    NetworkTurnConstraint.withOriginalTurn(original, rewritten.name) { delegate.execute(rewritten) }
                }
            }
        }
        org.junit.Assert.assertEquals(0, executions)
        val local = at.heinz.deviceai.ToolCallRequest("local", "get_battery_info", emptyMap())
        org.junit.Assert.assertEquals("executed", NetworkTurnConstraint.withOriginalTurn("Nur lokal antworten", local.name) {
            delegate.execute(local)
        })
        val web = at.heinz.deviceai.ToolCallRequest("web", "web_search", mapOf("query" to "Wetter Wien"))
        org.junit.Assert.assertEquals("executed", NetworkTurnConstraint.withOriginalTurn("Bitte recherchiere das Wetter", web.name) {
            delegate.execute(web)
        })
        org.junit.Assert.assertEquals(2, executions)
    }
    private val negative = listOf(
        "Mara hat drei Würfel. Antworte ohne Internetrecherche.",
        // Owner acceptance phrase (APK42): a bare follow-up constraint must also stay offline.
        "Ohne Internetrecherche.",
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
        "Suche nicht im Internet, sondern in meinem Archiv.",
        "Suche nur lokal nach Quellen.",
        "Recherchiere offline mit gespeicherten Quellen.",
        "Internetrecherche ist verboten.",
        "Webzugriff bleibt deaktiviert.",
        "Verbinde dich nicht mit dem Internet."
    )

    private val positive = listOf(
        "Recherchiere bitte das Thema maschinelles Lernen.",
        "Suche im Internet nach Fachliteratur.",
        "Bitte nutze die Websuche für diese Frage.",
        "Lies die Seite https://www.wikipedia.org.",
        "Suche online nach aktuellen Quellen.",
        "Nutze Internetrecherche.",
        "Recherchiere nicht nur online, sondern auch in Büchern.",
        "Suche nicht nur lokal, sondern auch im Internet.",
        "Verwende nicht ausschließlich lokale Quellen; recherchiere online."
    )

    @Test fun prohibitsExplicitOfflineTurnsEvenIfWebUiWasArmed() {
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
        assertNotNull(WebToolRequest.fromChat(
            "Suche nicht nur lokal, sondern auch im Internet.", true))
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
