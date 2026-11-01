package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class CertifiedLocationReflexTest {
    @Test fun fold7ResearchCasesNowHaveAnExplicitZeroTokenChatPath() {
        val a = CertifiedLocationReflex.answer("Beleg: Ylva ist in Bad Ischl.\nFrage: Wo ist Ylva?")
        assertNotNull(a)
        assertEquals(12, a!!.spanStart)
        assertEquals(21, a.spanEnd)
        assertTrue(a.text.contains("Bad Ischl"))
        val b = CertifiedLocationReflex.answer("Beleg: Anna ist in Perg. Emma ist in Berg.\nFrage: Wo ist Anna?")
        assertTrue(b!!.text.contains("steht: Perg"))
    }
    @Test fun misleadingIncompleteOrAmbiguousEvidenceNeverBecomesFact() {
        for (turn in listOf("Wo ist Anna?", "Beleg: Anna ist in Graz.\nFrage: Wo ist Emma?",
            "Beleg: Anna ist in Graz. Anna ist in Wien.\nFrage: Wo ist Anna?",
            "Beleg: Anna ist nicht in Graz.\nFrage: Wo ist Anna?",
            "Beleg: Anna ist gestern in Graz.\nFrage: Wo ist Anna?",
            "Beleg: Anna wohnt in Graz.\nFrage: Wo ist Anna?")) {
            assertNull(turn, CertifiedLocationReflex.answer(turn))
        }
    }
    @Test fun counterfactualRolesGenerateDistinctProvenance() {
        val a = CertifiedLocationReflex.answer("Beleg: Anna ist in Perg. Emma ist in Berg.\nFrage: Wo ist Anna?")
        val b = CertifiedLocationReflex.answer("Beleg: Anna ist in Berg. Emma ist in Perg.\nFrage: Wo ist Anna?")
        assertNotEquals(a!!.sourceHash, b!!.sourceHash)
        assertTrue(a.text.contains("steht: Perg"))
        assertTrue(b.text.contains("steht: Berg"))
    }
}
