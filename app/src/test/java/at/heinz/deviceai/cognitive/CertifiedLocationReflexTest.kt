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

    @Test fun sessionSpecialistAnswersOnlyFromOwnUnambiguousEarlierMessages() {
        val prior = listOf("Hallo, wie geht es dir?", "Mama ist in Graz.", "Danke für die Antwort.")
        val a = CertifiedLocationReflex.answerFromSession("Wo ist Mama?", prior)
        assertNotNull(a)
        assertTrue(a!!.text.contains("gesagt: Graz"))
        assertTrue(a.text.contains("session-turn-2"))
        assertTrue(a.text.contains("keine bestätigte Erinnerung"))
        // Coordinated collision form inside one earlier message keeps role binding.
        val b = CertifiedLocationReflex.answerFromSession("Wo ist Emma?", listOf("Anna ist in Perg, Emma ist in Berg."))
        assertTrue(b!!.text.contains("gesagt: Berg"))
        // Nothing said, other person, conflicting or negated statements, mixed relation,
        // explicit-source turns and non-location questions never short-circuit the chain.
        assertNull(CertifiedLocationReflex.answerFromSession("Wo ist Mama?", emptyList()))
        assertNull(CertifiedLocationReflex.answerFromSession("Wo ist Papa?", prior))
        assertNull(CertifiedLocationReflex.answerFromSession("Wo ist Mama?", listOf("Mama ist in Graz.", "Mama ist in Wien.")))
        assertNull(CertifiedLocationReflex.answerFromSession("Wo ist Mama?", listOf("Mama ist nicht in Graz.")))
        assertNull(CertifiedLocationReflex.answerFromSession("Wo wohnt Mama?", listOf("Mama ist in Graz.")))
        assertNull(CertifiedLocationReflex.answerFromSession("Wo ist Mama?", listOf("Beleg: Mama ist in Graz.\nFrage: Wo ist Mama?")))
        assertNull(CertifiedLocationReflex.answerFromSession("Wie alt ist Mama?", prior))
        assertNull(CertifiedLocationReflex.answerFromSession("Wo ist Mama?", listOf("Mama ist in Graz. Sie ist nicht mehr dort.")))
        // Only the last four turns count, like the dialogue context itself.
        val old = listOf("Mama ist in Graz.", "a", "b", "c", "d")
        assertNull(CertifiedLocationReflex.answerFromSession("Wo ist Mama?", old))
    }
}
