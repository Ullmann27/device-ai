package at.heinz.deviceai.cognitive

import at.heinz.deviceai.learning.span.EvidenceBoundNaturalLocationProbe
import org.junit.Assert.*
import org.junit.Test

/** Regression at existing main-chat evidence and confirmed-memory gates; no model or network. */
class ExplicitEvidenceAndRevocationTest {
    @Test fun explicitSourceKeepsOriginalTrailingWhitespaceAndExactOffsets() {
        val literal = "Emma ist in Berg.  "
        val result = CertifiedLocationReflex.routeExplicit("Beleg: $literal\nFrage: Wo ist Emma?")
        assertTrue(result is CertifiedLocationReflex.Routing.Proven)
        val proof = (result as CertifiedLocationReflex.Routing.Proven).proof
        assertEquals("Berg", literal.substring(proof.spanStart, proof.spanEnd))
        assertEquals(EvidenceBoundNaturalLocationProbe.digest(literal), proof.support.sha256)
        assertEquals(1, proof.support.revision)
        assertEquals("explicit-turn", proof.support.sourceId)
        assertTrue(proof.sourceHash.length == 64)
    }

    @Test fun laterNamedRevocationCannotLeaveAnOlderConfirmedLocationCertified() {
        for (correction in listOf(
            "Emma ist weggezogen.",
            "Emma ist umgezogen.",
            "Emma: Ort unbekannt.",
            "Emma hat die Angabe widerrufen."
        )) {
            val facts = listOf("old" to "Emma ist in Berg.", "new" to correction)
            assertNull(correction, CertifiedLocationReflex.answerFromConfirmedFacts("Wo ist Emma?", facts))
        }
    }

    @Test fun unrelatedBenignNoteDoesNotVetoConfirmedLocationProof() {
        val facts = listOf("place" to "Emma ist in Berg.", "unrelated" to "Anna ist weggezogen.")
        val proof = CertifiedLocationReflex.answerFromConfirmedFacts("Wo ist Emma?", facts)
        assertNotNull(proof)
        assertEquals("Berg", proof!!.support.text)
        assertEquals("place", proof.support.sourceId)
    }

    @Test fun directMemoryAndSessionEvidenceRemainReadOnly() {
        val raw = "Lara ist in Graz. Emma ist in Berg."
        val answer = CertifiedLocationReflex.answerFromSession("Wo ist Emma?", listOf(raw))
        assertNotNull(answer)
        assertEquals("Berg", answer!!.support.text)
        assertEquals(EvidenceBoundNaturalLocationProbe.digest(raw), answer.support.sha256)
    }
}
