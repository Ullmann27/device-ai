package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

/** Caller-only refusal for an unresolved place correction; no inferred person or new fact. */
class UnscopedLocationCorrectionTest {
    private val corrections = listOf(
        "seit gestern ist der Ort unbekannt.",
        "Der Wohnort ist aktuell unklar.",
        "Aufenthaltsort unbekannt."
    )

    @Test fun explicitUnknownPlaceCorrectionIsTerminalEvenWithoutRepeatingThePerson() {
        for (correction in corrections) {
            val turn = "Beleg: Anna ist in Graz; $correction\nFrage: Wo ist Anna?"
            assertTrue(turn, CertifiedLocationReflex.routeExplicit(turn) is CertifiedLocationReflex.Routing.Withheld)
            assertNull(CertifiedLocationReflex.answer(turn))
        }
    }

    @Test fun correctionInTheSameSessionSourceCannotLeaveTheOldPlaceProven() {
        for (correction in corrections) {
            val prior = listOf("Nora lebt in Krems. $correction")
            assertTrue(correction, CertifiedLocationReflex.routeFromSession("Wo lebt Nora?", prior)
                is CertifiedLocationReflex.Routing.Withheld)
            assertNull(CertifiedLocationReflex.answerFromSession("Wo lebt Nora?", prior))
        }
    }

    @Test fun separateAnonymousCorrectionSurvivesTheSessionPersonFilter() {
        for (correction in corrections) {
            val prior = listOf("Selin wohnt in Linz.", correction)
            assertTrue(correction, CertifiedLocationReflex.routeFromSession("Wo wohnt Selin?", prior)
                is CertifiedLocationReflex.Routing.Withheld)
            assertNull(CertifiedLocationReflex.answerFromSession("Wo wohnt Selin?", prior))
        }
    }

    @Test fun otherNamedPlacesAndUnrelatedBenignTurnsKeepTheirExactProof() {
        val prior = listOf("Nora lebt in Krems.", "Ben lebt in Wien.", "Ich trinke Kaffee.")
        val before = prior.toList()
        val route = CertifiedLocationReflex.routeFromSession("Wo lebt Nora?", prior)
        assertTrue(route is CertifiedLocationReflex.Routing.Proven)
        assertEquals("Krems", (route as CertifiedLocationReflex.Routing.Proven).proof.support.text)
        assertEquals(before, prior)
        val explicit = CertifiedLocationReflex.routeExplicit(
            "Beleg: Nora lebt in Krems. Ben lebt in Wien.\nFrage: Wo lebt Nora?")
        assertTrue(explicit is CertifiedLocationReflex.Routing.Proven)
        assertEquals("Krems", (explicit as CertifiedLocationReflex.Routing.Proven).proof.support.text)
    }
}
