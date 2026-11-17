package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

/**
 * Research-only counterfactuals. All examples are synthetic and generated
 * before execution. Passing does not measure general-language intelligence.
 */
class JarvisCounterfactualShadowMatrixTest {
    @Test fun oneHundredPreviouslyUnseenPersonPlaceBindingsAreExact() {
        val people = listOf("Anna","Emma","Ylva","Mira","Nora",
            "Lena","Mara","Greta","Tilda","Paula")
        val places = listOf("Perg","Berg","Bad Ischl","Graz","Linz",
            "Wien","Salzburg","Krems","Klagenfurt","Innsbruck")
        var successes = 0
        for (person in people) for (city in places) {
            val otherCity = if (city == "Graz") "Wien" else "Graz"
            val turn = "Beleg: " + person + " ist in " + city +
                ". Zeno ist in " + otherCity + ".\nFrage: Wo ist " + person + "?"
            val proof = CertifiedLocationReflex.answer(turn)
            assertNotNull(turn,proof)
            assertTrue(proof!!.text.contains("steht: " + city))
            assertTrue(proof.sourceHash.matches(Regex("[a-f0-9]{64}")))
            successes++
        }
        assertEquals(100,successes)
    }

    @Test fun contextualCitationsNeverProveFalseOrChangedFacts() {
        val baseline = CertifiedLocationReflex.answer(
            "Beleg: Anna ist in Perg. Emma ist in Berg.\nFrage: Wo ist Anna?")
        val swapped = CertifiedLocationReflex.answer(
            "Beleg: Anna ist in Berg. Emma ist in Perg.\nFrage: Wo ist Anna?")
        assertTrue(baseline!!.text.contains("steht: Perg"))
        assertTrue(swapped!!.text.contains("steht: Berg"))
        assertNotEquals(baseline.sourceHash,swapped.sourceHash)
        assertNull(CertifiedLocationReflex.answer(
            "Beleg: Anna ist in Perg. Anna ist jetzt in Berg.\nFrage: Wo ist Anna?"))
    }
}
