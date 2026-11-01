package at.heinz.deviceai.cognitive

import at.heinz.deviceai.learning.span.EvidenceBoundNaturalLocationProbe
import at.heinz.deviceai.learning.span.EvidenceBoundNaturalLocationVerifier
import org.junit.Assert.*
import org.junit.Test

class SessionLocationRoutingTest {
    @Test fun explicitRejectedEvidenceHasATerminalClarificationInsteadOfAFallback() {
        for (raw in listOf("Emma ist nicht in Berg.", "Emma ist heute in Berg.",
            "Emma ist in Berg. Emma ist in Perg.", "Anna ist in Berg.")) {
            val route = CertifiedLocationReflex.routeExplicit("Beleg: $raw\nFrage: Wo ist Emma?")
            assertTrue(raw, route is CertifiedLocationReflex.Routing.Withheld)
            assertTrue((route as CertifiedLocationReflex.Routing.Withheld).text.contains("Bitte kläre"))
        }
    }

    @Test fun provenRouteCarriesExactSourceRevisionHashAndOffsetsForIndependentChecking() {
        val raw = "  Anna ist in Perg. Emma ist in Berg.  "
        val route = CertifiedLocationReflex.routeFromSession("Wo ist Emma?", listOf(raw))
            as CertifiedLocationReflex.Routing.Proven
        val support = route.proof.support
        assertEquals("session-turn-1", support.sourceId)
        assertEquals(1, support.revision)
        assertEquals(EvidenceBoundNaturalLocationProbe.digest(raw), support.sha256)
        assertEquals("Berg", raw.substring(support.start, support.endExclusive))
        val source = EvidenceBoundNaturalLocationProbe.Evidence(support.sourceId, support.revision, support.sha256, raw)
        assertTrue(EvidenceBoundNaturalLocationVerifier.verify("Wo ist Emma?", listOf(source), support))
        assertFalse(EvidenceBoundNaturalLocationVerifier.verify("Wo ist Anna?", listOf(source), support))
        assertFalse(EvidenceBoundNaturalLocationVerifier.verify("Wo ist Emma?", listOf(source.copy(revision=2)), support))
        assertFalse(EvidenceBoundNaturalLocationVerifier.verify("Wo ist Emma?", listOf(source), support.copy(start=support.start-1)))
    }

    @Test fun incompleteOrRetractedSessionGetsClarificationAndCannotReviveOldFacts() {
        assertTrue(CertifiedLocationReflex.routeFromSession("Wo ist Emma?", null) is CertifiedLocationReflex.Routing.Withheld)
        assertTrue(CertifiedLocationReflex.routeFromSession("Wo ist Emma?", listOf("Emma ist in Berg.",
            "Nein, sie ist nicht dort.")) is CertifiedLocationReflex.Routing.Withheld)
        assertNull(CertifiedLocationReflex.routeFromSession("Wo ist Emma?", emptyList()))
        assertNull(CertifiedLocationReflex.routeFromSession("Erkläre Quantenphysik", listOf("Emma ist in Berg.")))
    }

    @Test fun locationQuestionsDoNotBecomeNewAssertionsDuringRepeatedRecall() {
        val route = CertifiedLocationReflex.routeFromSession("Wo ist Emma?", listOf(
            "Emma ist in Berg.", "Wo ist Emma?")) as CertifiedLocationReflex.Routing.Proven
        assertEquals("Berg", route.proof.support.text)
    }

    @Test fun existingInternetModesAndRevocationCannotBeChangedByTheEvidenceRoute() {
        for (mode in InternetAccessMode.entries) {
            val policy = InternetConsentPolicy(mode)
            val context = LocalConversationContext()
            context.record("s", "Emma ist heute in Berg.", "Unbestätigte Antwort.")
            val revision = policy.revision()
            assertTrue(CertifiedLocationReflex.routeFromSession("Wo ist Emma?", context.sessionEvidence("s"))
                is CertifiedLocationReflex.Routing.Withheld)
            assertEquals(mode, policy.mode)
            assertEquals(revision, policy.revision())
            policy.cancel()
            assertTrue(CertifiedLocationReflex.routeFromSession("Wo ist Emma?", context.sessionEvidence("s"))
                is CertifiedLocationReflex.Routing.Withheld)
            assertEquals(revision+1, policy.revision())
            assertNull(CertifiedLocationReflex.routeFromSession("Wo ist Emma?", context.sessionEvidence("other")))
        }
    }
}
