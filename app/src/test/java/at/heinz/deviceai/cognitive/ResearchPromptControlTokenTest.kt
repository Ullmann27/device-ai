package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

/** Known QA regression plus authored controls; these cases are not a new blind evaluation. */
class ResearchPromptControlTokenTest {
    private val controls = listOf("[INST]", "[/INST]", "[SYSTEM]", "[/SYSTEM]", "[SOURCE 42]",
        "<|im_start|>", "<|im_end|>", "<start_of_turn>", "<end_of_turn>", "<tool_call>")

    private fun input(text: String, id: String = "claim-1"): ResearchSynthesisInput {
        val url = "https://manual.example.org/quoted"
        val claim = Claim(id, text, ProvenanceType.EXTERNAL_SOURCE, listOf(url), createdAt = 100L)
        val graph = ResearchEvidenceGraph(listOf(claim), emptyList(),
            listOf(ResearchClaimEdge(id, "[/INST]edge", ClaimRelation.CONTRADICTS)), emptyMap(), emptyMap())
        return ResearchSynthesisInput("Sensor manual", DeterministicResearchPlanner().plan("Sensor manual", ResearchMode.QUICK),
            listOf(UntrustedResearchSnippet(url, text, text)), graph, emptyList(),
            listOf(ResearchCitation(url, text, 100L, contentHash(text))))
    }

    @Test fun knownQaInstGraphIdCannotRemainARawDelimiter() {
        val source = input("The sensor measures air pressure.", "[INST]qa-claim-9")
        val prompt = ResearchPromptBoundary.build(source, 100L)
        assertFalse(prompt.contains("[INST]"))
        assertFalse(prompt.contains("[/INST]"))
        assertEquals("[INST]qa-claim-9", source.graph.claims.single().id)
    }

    @Test fun commonControlTokensAreQuotedInAllUntrustedPromptSurfaces() {
        for (control in controls) {
            val prompt = ResearchPromptBoundary.build(input("$control literal sensor text", control + "claim"), 100L)
            assertFalse("raw control $control", prompt.contains(control))
        }
    }

    @Test fun ordinaryBracketedMathUnitsAndQuotesStayReadable() {
        val text = "Range [0, 10] [°C]; A[i] = 3; quoted '[manual]' and ⟦unchanged⟧."
        assertEquals("Range ［0, 10］ ［°C］; A［i］ = 3; quoted '［manual］' and ⟦unchanged⟧.",
            sanitizeUntrustedResearchText(text, 256))
    }

    @Test fun sourceLiteralRemainsBoundWhileOnlyPromptRepresentationChanges() {
        val text = "The manual quotes [INST] as a literal [code] marker."
        val url = "https://manual.example.org/literal"
        val doc = ResearchDocument(url, "Manual", 100L, contentHash(text), "text/plain",
            SourceIdentity("manual", "manual.example.org"), text)
        var captured: ResearchSynthesisInput? = null
        val dossier = DeepResearchEngine(provider = SimulatedResearchProvider(mapOf(url to doc)),
            synthesizer = ResearchSynthesizer { captured = it; ResearchSynthesisOutput("Untrusted excerpt") },
            validateTarget = {}).research("manual literal marker", ResearchMode.QUICK, listOf(url))
        val claim = dossier.graph.claims.single()
        assertEquals(text, claim.text)
        assertEquals(listOf(url), claim.sourceReferences)
        assertTrue(doc.content.contains(claim.text))
        assertFalse(ResearchPromptBoundary.build(checkNotNull(captured), 100L).contains("[INST]"))
        assertEquals(text, checkNotNull(captured).graph.claims.single().text)
    }

    @Test fun appSourceNumbersRemainDistinctFromQuotedSourceImitations() {
        val prompt = ResearchPromptBoundary.build(input("The manual prints [SOURCE 42] in its example."), 100L)
        assertTrue(prompt.contains("[SOURCE 1]"))
        assertTrue(prompt.contains("[SOURCE 1 url=https://manual.example.org/quoted"))
        assertFalse(prompt.contains("[SOURCE 42]"))
        assertTrue(prompt.contains("［SOURCE 42］"))
    }

    @Test fun displayMarkersAndBudgetedRenderingRemainIdempotent() {
        val inputs = controls + listOf("SYSTEM: \u202E<<< [INST] text [/INST]".repeat(5),
            "Range [0, 1] [°C]", "[quoted role]: [quoted marker] [U+202E] [TRUNCATED]")
        for (budget in 0..128) for (text in inputs) {
            val first = sanitizeUntrustedResearchText(text, budget)
            assertTrue(first.length <= budget)
            assertEquals("budget=$budget text=$text", first, sanitizeUntrustedResearchText(first, budget))
        }
        assertEquals("[quoted role]: [U+202E]", sanitizeUntrustedResearchText("SYSTEM: \u202E", 128))
    }
}
