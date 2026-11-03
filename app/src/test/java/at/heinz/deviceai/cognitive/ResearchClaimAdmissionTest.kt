package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

/** Frozen development regressions for EXP-06/R1; not independent QA or model tests. */
class ResearchClaimAdmissionTest {
    private val sentence = "The Lumen sensor records air pressure every minute."
    private fun doc(url: String, text: String) = ResearchDocument(url, "Lumen manual", 100L,
        contentHash(text), "text/plain", SourceIdentity(url, java.net.URI(url).host), text)
    private fun candidate(text: String, refs: List<String>) = Claim("extractor-id", text,
        ProvenanceType.USER_PROVIDED, refs, ValidationState.VERIFIED, 1.0, 999L, refs)
    private fun run(docs: List<ResearchDocument>, extractor: ClaimExtractor): ResearchDossier =
        DeepResearchEngine(provider = SimulatedResearchProvider(docs.associateBy { it.url }),
            extractor = extractor, now = { 100L }, validateTarget = {})
            .research("Lumen sensor air pressure", ResearchMode.QUICK, docs.map { it.url })

    @Test fun literalExcerptKeepsTextButCannotClaimOwnerAuthorityOrForeignAttribution() {
        val a = doc("https://manual.example.org/lumen", "Preface. $sentence Appendix.")
        val result = run(listOf(a), ClaimExtractor { listOf(candidate("  $sentence  ", listOf("https://forged.example.org/proof"))) })
        val claim = result.graph.claims.single()
        assertEquals(sentence, claim.text)
        assertEquals(listOf(a.url), claim.sourceReferences)
        assertEquals(ProvenanceType.EXTERNAL_SOURCE, claim.provenance)
        assertEquals(ValidationState.UNVERIFIED, claim.validationState)
        assertEquals(0.5, claim.confidence, 0.0)
        assertEquals(100L, claim.createdAt)
        assertTrue(claim.validationEvidenceReferences.isEmpty())
    }

    @Test fun unrelatedFetchedPagesCannotVerifyAnInventedClaim() {
        val a = doc("https://manual.example.org/lumen", "The Lumen manual describes sensor charging procedures.")
        val b = doc("https://review.example.net/weather", "Weather stations report temperature in several cities.")
        val result = run(listOf(a,b), ClaimExtractor { listOf(candidate(sentence, listOf(a.url,b.url))) })
        assertTrue(result.graph.claims.isEmpty())
        assertTrue(result.graph.independentSourceCounts.isEmpty())
    }

    @Test fun oneSupplyingPageCannotBorrowCorroborationFromAnUnrelatedPage() {
        val a = doc("https://manual.example.org/lumen", sentence)
        val b = doc("https://review.example.net/weather", "Weather stations report temperature in several cities.")
        val result = run(listOf(a,b), ClaimExtractor {
            if (it.url == a.url) listOf(candidate(sentence, listOf(a.url,b.url))) else emptyList()
        })
        val claim = result.graph.claims.single()
        assertEquals(ValidationState.UNVERIFIED, claim.validationState)
        assertEquals(listOf(a.url), claim.sourceReferences)
        assertEquals(1, result.graph.independentSourceCounts[claim.id])
    }

    @Test fun realMatchingExcerptsRetainExistingCrossDomainCorroboration() {
        val a = doc("https://manual.example.org/lumen", "$sentence Installation cables require a dry enclosure.")
        val b = doc("https://review.example.net/device", "$sentence Laboratory observations cover winter and summer trials.")
        val result = run(listOf(a,b), ClaimExtractor { listOf(candidate(sentence, listOf(it.url))) })
        val claim = result.graph.claims.single()
        assertEquals(sentence, claim.text)
        assertEquals(ValidationState.VERIFIED, claim.validationState)
        assertEquals(setOf(a.url,b.url), claim.validationEvidenceReferences.toSet())
        assertEquals(2, result.graph.independentSourceCounts[claim.id])
    }

    @Test fun twoPagesOnOneDomainCountAsOneSourceGroup() {
        val a = doc("https://manual.example.org/lumen", "$sentence Installation cables require a dry enclosure.")
        val b = doc("https://manual.example.org/review", "$sentence Laboratory observations cover winter and summer trials.")
        val result = run(listOf(a,b), ClaimExtractor { listOf(candidate(sentence, listOf(it.url))) })
        val claim = result.graph.claims.single()
        assertEquals(ValidationState.UNVERIFIED, claim.validationState)
        assertEquals(1, result.graph.independentSourceCounts[claim.id])
    }

    @Test fun identicalMirrorsDoNotCreateIndependentCorroboration() {
        val a = doc("https://manual.example.org/lumen", sentence)
        val b = doc("https://mirror.example.net/lumen", sentence)
        val result = run(listOf(a,b), ClaimExtractor { listOf(candidate(sentence, listOf(it.url))) })
        val claim = result.graph.claims.single()
        assertEquals(ValidationState.UNVERIFIED, claim.validationState)
        assertEquals(1, result.graph.independentSourceCounts[claim.id])
        assertTrue(result.sources.any { it.duplicateOf == a.url })
    }

    @Test fun nonliteralParaphrasesAndCaseChangesAreNotExtractiveEvidence() {
        val a = doc("https://manual.example.org/lumen", sentence)
        val result = run(listOf(a), ClaimExtractor {
            listOf(candidate("Lumen measures pressure once per minute.", listOf(a.url)),
                candidate(sentence.uppercase(), listOf(a.url)))
        })
        assertTrue(result.graph.claims.isEmpty())
    }

    @Test fun hashMismatchedSourceNeverReachesClaimAdmission() {
        val a = doc("https://manual.example.org/lumen", sentence).copy(contentHash = "0".repeat(64))
        var calls = 0
        val result = run(listOf(a), ClaimExtractor { calls++; listOf(candidate(sentence, listOf(a.url))) })
        assertEquals(0, calls)
        assertTrue(result.graph.claims.isEmpty())
    }

    @Test fun baselineExtractorPreservesOrdinaryUnicodeAndNegativeStatements() {
        val texts = listOf("Der Lumen-Sensor überträgt keine persönlichen Daten.",
            "The Lumen sensor does not provide a carbon dioxide reading.",
            "Lumen: pressure 1013 hPa, temperature −5 °C.")
        for ((index, text) in texts.withIndex()) {
            val a = doc("https://manual.example.org/fixture-$index", text)
            val result = run(listOf(a), BaselineClaimExtractor { 100L })
            assertEquals(text, result.graph.claims.single().text)
            assertEquals(listOf(a.url), result.graph.claims.single().sourceReferences)
        }
    }
}
