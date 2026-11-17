package at.heinz.deviceai.cognitive

import at.heinz.deviceai.*
import org.junit.Assert.*
import org.junit.Test

/** Real gate and engine; controlled source boundary, not a live internet acceptance. */
class ConversationResearchApprovalIntegrationTest {
    @Test fun unboundYesCannotGrantResearchOrReviveAStaleApproval() {
        val consent = InternetConsentPolicy(InternetAccessMode.ASK)
        val web = ApprovedWebToolGate(consent)
        val call = WebToolRequest.Search("Photosynthese")
        val stale = web.prepare(call)!!
        val current = web.prepare(call)!!
        val context = LocalConversationContext()
        context.record("s", "Recherchiere Photosynthese.", "Die Internetfreigabe steht noch aus.")

        assertNull(context.inspectRequest("s", "Ja, mach."))
        assertNull(WebToolRequest.fromChat("Ja, mach.", false))
        var effects = 0
        val toolGate = ToolGate(ToolPolicy(), ToolRunner { effects++; "unreachable" })
        assertEquals(ToolExecutionStatus.REJECTED,
            toolGate.execute(call.proposal().invocation("unbound-yes")).record.status)
        assertEquals(0, effects)
        assertFalse(web.completeApproval(stale, ResearchMode.QUICK, true) { fail("Stale approval") })

        var callbacks = 0
        assertTrue(web.completeApproval(current, ResearchMode.QUICK, true) { session ->
            callbacks++
            assertNotNull(session)
            assertEquals(call, session!!.call)
        })
        assertFalse(web.completeApproval(current, ResearchMode.QUICK, true) { callbacks++ })
        assertEquals(1, callbacks)
        assertEquals(0, effects)
    }

    @Test fun naturalConversationUsesTheExistingRevocableGateForAllInternetModes() {
        for (mode in InternetAccessMode.entries) {
            val consent = InternetConsentPolicy()
            consent.setMode(mode)
            val web = ApprovedWebToolGate(consent)
            val call = WebToolRequest.fromChat("Jarvis, recherchiere Photosynthese.", false)
            assertTrue(call is WebToolRequest.Search)
            call!!
            val approval = web.prepare(call)
            val session = when (mode) {
                InternetAccessMode.OFF -> {
                    assertNull(approval)
                    assertNull(web.authorizeAlwaysOn(call, ResearchMode.QUICK))
                    null
                }
                InternetAccessMode.ASK -> {
                    assertNotNull(approval)
                    assertNull(web.authorizeAlwaysOn(call, ResearchMode.QUICK))
                    // A replacement invalidates the earlier dialog, so prepare the exact new ticket.
                    web.approve(web.prepare(call)!!, ResearchMode.QUICK)
                }
                InternetAccessMode.ALWAYS_ON -> {
                    assertNull(approval)
                    web.authorizeAlwaysOn(call, ResearchMode.QUICK)
                }
            }
            if (session == null) continue
            var fetches = 0
            val discovery = object : ResearchDiscoveryProvider {
                override val isGeneralSearchAvailable = true
                override fun discover(query: ResearchQuery) =
                    listOf(ResearchSourceCandidate("https://www.nasa.gov/photosynthese", "Photosynthese"))
            }
            val provider = object : ResearchProvider {
                override fun retrieve(request: ResearchRequest): ResearchResult {
                    fetches++
                    val text = "Photosynthese wandelt Lichtenergie in chemische Energie um."
                    val document = ResearchDocument(request.url, "Photosynthese", 1234L,
                        sha256Hex(text), "text/plain", SourceIdentity("www.nasa.gov", "www.nasa.gov"), text)
                    return ResearchResult(listOf(document), listOf(document.citation()))
                }
            }
            val runner = ApprovedWebResearchToolRunner(session, discovery, provider)
            val gate = ToolGate(ToolPolicy(timeoutMillis = 20_000), runner)
            assertEquals(ToolExecutionStatus.REJECTED,
                gate.execute(call.proposal().invocation("unapproved-${mode.name}")).record.status)
            assertEquals(0, fetches)
            val result = gate.execute(call.proposal().invocation("approved-${mode.name}"), userConfirmed = true)
            assertEquals(result.record.decisionReason, ToolExecutionStatus.SUCCEEDED, result.record.status)
            assertTrue(fetches > 0)
            assertTrue(result.output!!.contains("https://www.nasa.gov/photosynthese"))
            assertEquals(1234L, runner.dossier!!.sources.first().retrievedAt)
            consent.setMode(InternetAccessMode.OFF)
            assertNull(runner.dossier)
            val before = fetches
            assertThrows(IllegalStateException::class.java) { session.execute(call) { fetches++ } }
            assertEquals(before, fetches)
        }
    }

    @Test fun privateNaturalRequestsAndSameTurnNetworkVetoNeverCreateAWebCall() {
        listOf("Jarvis, recherchiere mein Passwort.", "Such nach meinem PDF.",
            "Recherchiere Photosynthese ohne Internet.").forEach {
            assertNull(it, WebToolRequest.fromChat(it, false))
        }
    }
}
