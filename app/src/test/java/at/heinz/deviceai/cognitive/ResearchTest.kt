package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class ResearchTest {
    @Test
    fun urlPolicyRejectsUnsafeSchemesPrivateTargetsAndRedirects() {
        assertThrows(IllegalArgumentException::class.java) {
            HttpReadOnlyResearchProvider.ResearchUrlValidator.validate(URI("file:///etc/passwd"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            HttpReadOnlyResearchProvider.ResearchUrlValidator.validate(URI("http://example.com"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            HttpReadOnlyResearchProvider.ResearchUrlValidator.validate(URI("https://localhost/private"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            HttpReadOnlyResearchProvider.ResearchUrlValidator.validate(URI("https://127.0.0.1/"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            HttpReadOnlyResearchProvider.ResearchUrlValidator.validate(URI("https://192.168.1.1/"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            HttpReadOnlyResearchProvider.ResearchUrlValidator.validate(URI("https://router.home.arpa/"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            HttpReadOnlyResearchProvider.ResearchUrlValidator.validate(URI("https://example.com:8443/"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            HttpReadOnlyResearchProvider.ResearchUrlValidator.validate(URI("https://user@example.com/"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            HttpReadOnlyResearchProvider.ResearchUrlValidator.validate(URI("https://example.com/?access_token=secret"))
        }
        val redirect = URI("https://public.example/").resolve("http://127.0.0.1/admin")
        assertThrows(IllegalArgumentException::class.java) {
            HttpReadOnlyResearchProvider.ResearchUrlValidator.validate(redirect)
        }
    }

    @Test
    fun simulatedResearchPreservesProvenanceCitationsAndOnlyCorroboratesIndependentSources() {
        var clock = 100L
        val content = "The device battery lasts about ten hours under normal use."
        val first = document("https://alpha.example/report", "alpha.example", content, clock++)
        val second = document("https://beta.example/report", "beta.example", content, clock++)
        val provider = SimulatedResearchProvider(mapOf(first.url to first, second.url to second))
        val pipeline = ResearchPipeline(provider, BaselineClaimExtractor { clock++ })
        val synthesis = pipeline.research(ResearchRequest("battery life", first.url))
        assertEquals(1, synthesis.documents.size)
        assertEquals(1, synthesis.citations.size)
        assertEquals(first.url, synthesis.claims.first().sourceReferences.first())
        assertEquals(ValidationState.UNVERIFIED, synthesis.claims.first().validationState)

        val both = ResearchPipeline(
            SimulatedResearchProvider(
                mapOf(first.url to first, second.url to second),
                mapOf(first.url to listOf(first.url, second.url))
            ),
            BaselineClaimExtractor { clock++ }
        )
        val result = both.research(ResearchRequest("battery life", first.url))
        assertEquals(2, result.documents.size)
        assertTrue(result.claims.all { it.validationState == ValidationState.VERIFIED })

        val claims = listOf(
            claim("one", content, first.url, 100L),
            claim("two", content, second.url, 101L)
        )
        val support = listOf(ClaimRelationship("one", "two", ClaimRelation.SUPPORTS))
        val verified = CorroborationPolicy().verifiedClaims(
            claims,
            mapOf(first.url to first.source, second.url to second.source),
            support
        )
        assertTrue(verified.all { it.validationState == ValidationState.VERIFIED })
        assertTrue(verified.all { it.validationEvidenceReferences.size == 2 })

        val contradicted = CorroborationPolicy().verifiedClaims(
            claims,
            mapOf(first.url to first.source),
            emptyList()
        )
        assertFalse(contradicted.any { it.validationState == ValidationState.VERIFIED })
    }

    @Test
    fun contradictionAndMemoryPolicyAndEvaluatedSourceTrustAreIntegrated() {
        val positive = "The device has an active battery saving feature."
        val negative = "The device does not have an active battery saving feature."
        val first = document("https://one.example/a", "one.example", positive, 1L)
        val second = document("https://two.example/b", "two.example", negative, 1L)
        val trust = SourceTrustStore()
        val pipeline = ResearchPipeline(
            SimulatedResearchProvider(
                mapOf(first.url to first, second.url to second),
                mapOf(first.url to listOf(first.url, second.url))
            ),
            BaselineClaimExtractor { 2L },
            sourceTrust = trust
        )
        val synthesis = pipeline.research(ResearchRequest("battery", first.url))
        assertTrue(synthesis.relationships.any { it.relation == ClaimRelation.CONTRADICTS })
        assertTrue(synthesis.claims.none { it.validationState == ValidationState.VERIFIED })

        val store = InMemoryMemoryStore()
        val record = pipeline.storeClaimMemory(
            claim("durable", positive, first.url, 2L),
            store,
            MemoryWritePolicy(),
            durableKnowledge = true
        )
        assertNotNull(record)
        assertEquals(PrivacyClass.PUBLIC, record?.privacyClass)
        assertEquals(first.url, store.get("research-durable")?.sourceReference)
        assertEquals(0, trust.get(first.source).evaluatedClaims)
        pipeline.recordEvaluatedSource(first, accuracy = 0.9, corroborated = true, contradicted = false)
        assertEquals(1, trust.get(first.source).evaluatedClaims)
        assertThrows(IllegalArgumentException::class.java) {
            trust.observe(SourceObservation(first.source, evaluated = false, accuracy = 0.0, freshness = 0.0))
        }
    }

    @Test
    fun responseSizesAreBoundedByProviderRequest() {
        val oversized = document(
            "https://example.test/large",
            "example.test",
            "x".repeat(128),
            1L
        )
        assertThrows(IllegalArgumentException::class.java) {
            SimulatedResearchProvider(mapOf(oversized.url to oversized))
                .retrieve(ResearchRequest("size", oversized.url, maxBytes = 16))
        }
    }

    private fun document(url: String, domain: String, content: String, time: Long) = ResearchDocument(
        url = url,
        title = "Research",
        retrievedAt = time,
        contentHash = contentHash(content),
        mimeType = "text/plain",
        source = SourceIdentity(domain, domain),
        content = content
    )

    private fun claim(id: String, text: String, url: String, time: Long) = Claim(
        id, text, ProvenanceType.EXTERNAL_SOURCE, listOf(url), createdAt = time
    )
}

private fun <T : Throwable> assertThrows(
    exceptionClass: Class<T>,
    block: () -> Unit
) {
    org.junit.Assert.assertThrows(exceptionClass) { block() }
}
