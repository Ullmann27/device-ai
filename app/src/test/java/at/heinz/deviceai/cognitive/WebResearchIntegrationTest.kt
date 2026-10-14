package at.heinz.deviceai.cognitive

import at.heinz.deviceai.*
import org.junit.Assert.*
import org.junit.Test
import java.net.URI

class WebResearchIntegrationTest {
    @Test fun staleSameQuestionDialogCannotApproveNewRequestAndApprovalExpires() {
        var time=0L
        val policy=InternetConsentPolicy(nowMillis={time})
        val old=policy.requestTicket("same")!!
        policy.requestTicket("same")!!
        assertFalse(policy.consume(old))
        val current=policy.requestTicket("same")!!
        time=60001
        assertFalse(policy.consume(current))
    }

    @Test fun realToolGateAndEngineNeedOneShotOwnerPermissionForSearchAndRead() {
        val policy=InternetConsentPolicy()
        val web=ApprovedWebToolGate(policy)
        var searches=0;var reads=0
        val discovery=object:ResearchDiscoveryProvider {
            override val isGeneralSearchAvailable=true
            override fun discover(query:ResearchQuery):List<ResearchSourceCandidate> {
                searches++;return listOf(ResearchSourceCandidate("https://www.nasa.gov/solar","Solar energy"))
            }
        }
        val provider=object:ResearchProvider {
            override fun retrieve(request:ResearchRequest):ResearchResult {
                reads++
                val text="Solar energy converts sunlight into electricity. Solar panels provide renewable energy."
                val d=ResearchDocument(request.url,"Solar energy",1,sha256Hex(text),"text/plain",SourceIdentity("www.nasa.gov","www.nasa.gov"),text)
                return ResearchResult(listOf(d),listOf(d.citation()))
            }
        }
        for(call in listOf(WebToolRequest.Search("solar energy"),WebToolRequest.Read("https://www.nasa.gov/solar"))) {
            val approval=web.prepare(call)!!
            val session=web.approve(approval,ResearchMode.QUICK)!!
            val runner=ApprovedWebResearchToolRunner(session,discovery,provider,engineFactory={ d,p ->
                DeepResearchEngine(discovery=d,provider=p,validateTarget={HttpReadOnlyResearchProvider.ResearchUrlValidator.validateSyntax(it)})
            })
            val gate=ToolGate(ToolPolicy(timeoutMillis=20000),runner)
            assertEquals(ToolExecutionStatus.REJECTED,gate.execute(call.proposal().invocation("denied-${call.operation}")).record.status)
            val result=gate.execute(call.proposal().invocation("approved-${call.operation}"),userConfirmed=true)
            assertEquals(result.record.decisionReason,ToolExecutionStatus.SUCCEEDED,result.record.status)
            assertTrue(result.output!!.contains("https://www.nasa.gov/solar"))
            assertTrue(runner.dossier!!.sources.isNotEmpty())
            val count=reads
            val replay=ToolGate(ToolPolicy(timeoutMillis=20000),runner).execute(call.proposal().invocation("replay-${call.operation}"),userConfirmed=true)
            assertEquals(ToolExecutionStatus.FAILED,replay.record.status)
            assertEquals(count,reads)
        }
        assertTrue(searches>0);assertTrue(reads>0)
    }

    @Test fun switchingOffRevokesAlreadyApprovedQueuedRequest() {
        val policy=InternetConsentPolicy();val gate=ApprovedWebToolGate(policy)
        val call=WebToolRequest.Search("topic")
        val session=gate.approve(gate.prepare(call)!!,ResearchMode.QUICK)!!
        policy.setMode(InternetAccessMode.OFF);policy.setMode(InternetAccessMode.ASK)
        assertThrows(IllegalStateException::class.java) { session.execute(call) { error("must not execute") } }
    }

    @Test fun expiredNetworkBudgetCanReturnCollectedEvidenceButCannotFetchAgain() {
        var time=0L
        val policy=InternetConsentPolicy(nowMillis={time});val gate=ApprovedWebToolGate(policy)
        val call=WebToolRequest.Search("topic")
        val session=gate.approve(gate.prepare(call)!!,ResearchMode.QUICK)!!
        assertEquals("partial",session.execute(call) {
            time=15001L
            assertThrows(IllegalStateException::class.java) { session.checkActive() }
            "partial"
        })
    }

    @Test fun stoppingDuringPrimarySearchRevokesFallbackAndAnyLaterNetworkAccess() {
        val gate=ApprovedWebToolGate(InternetConsentPolicy())
        val call=WebToolRequest.Search("topic")
        val session=gate.approve(gate.prepare(call)!!,ResearchMode.QUICK)!!
        var fallbacks=0
        val discovery=GeneralWebDiscoveryProvider(object:ResearchDiscoveryProvider {
            override val isGeneralSearchAvailable=false
            override fun discover(query:ResearchQuery):List<ResearchSourceCandidate> { fallbacks++;return emptyList() }
        },checkAccess=session::checkActive,fetchHtml={session.cancel();"<rss><channel/></rss>"})
        assertThrows(IllegalStateException::class.java) {
            session.execute(call) { discovery.discover(ResearchQuery("topic","q")) }
        }
        assertEquals(0,fallbacks)
        assertThrows(IllegalStateException::class.java) { session.execute(call) { error("must not run") } }
    }

    @Test fun ordinaryChatNeverRequestsNetworkAndExplicitToolsDo() {
        assertNull(WebToolRequest.fromChat("Wie funktionieren Solarzellen?",false))
        assertEquals(WebToolRequest.Search("Solarzellen"),WebToolRequest.fromChat("web_search: Solarzellen",false))
        assertEquals(WebToolRequest.Read("https://www.nasa.gov/solar"),WebToolRequest.fromChat("web_read https://www.nasa.gov/solar",false))
    }

    @Test fun rawDiscoveryMarkupKeepsLinksAndRejectsUnsafeResultsAndRedirects() {
        val raw="""<html><a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fwww.nasa.gov%2Fsolar">Solar energy</a></html>"""
        val content=HttpReadOnlyResearchProvider.responseText(raw,"text/html",true,10000)
        assertEquals("https://www.nasa.gov/solar",GeneralWebDiscoveryProvider.parseSearchResults(content).single().url)
        assertTrue(GeneralWebDiscoveryProvider.parseSearchResults("""<a class="result__a" href="https://127.0.0.1/private">Local</a>""").isEmpty())
        for(url in listOf("http://www.nasa.gov/","https://127.0.0.1/","https://[::1]/","https://user@www.nasa.gov/","https://10.0.0.1/","https://www.nasa.gov/?%74oken=secret"))
            assertThrows(url,IllegalArgumentException::class.java) { HttpReadOnlyResearchProvider.ResearchUrlValidator.validateSyntax(URI(url)) }
        assertEquals("https://www.nasa.gov/a%20b?x=%2F",ResearchUrlCanonicalizer.canonicalize("https://www.nasa.gov/a%20b?x=%2F"))
    }
}
