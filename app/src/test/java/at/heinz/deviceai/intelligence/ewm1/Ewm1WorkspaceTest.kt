package at.heinz.deviceai.intelligence.ewm1

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** EWM1 rules on own small fact sets (none taken from TEST4); B0 is a stub that keeps memory order. */
class Ewm1WorkspaceTest {
    private fun ws(vararg facts: String) = Ewm1Workspace(facts.mapIndexed { i, f -> "f$i" to f }) { _, subset -> subset.toList() }
    private fun Ewm1Workspace.cite(q: String) = decide(q).cited.map { id -> memory.byId.getValue(id).text }

    private val family = arrayOf("Mein Onkel heißt Leopold.", "Leopold wohnt in Freistadt.", "Leopold arbeitet als Schlosser.",
        "Meine Cousine Hanna wohnt in Melk.", "Hanna ist Försterin.")

    @Test fun pronounsFollowTheReferentAndAnExplicitNameSwitchesIt() {
        val w = ws(*family)
        assertEquals("Leopold wohnt in Freistadt.", w.cite("Wo wohnt Leopold?").first())
        assertEquals("Leopold arbeitet als Schlosser.", w.cite("Und was arbeitet er?").first())
        assertEquals("Meine Cousine Hanna wohnt in Melk.", w.cite("Und Hanna, wo lebt die?").first())
        assertEquals("Hanna ist Försterin.", w.cite("Was macht sie beruflich?").first())
    }

    @Test fun aRoleWordReachesThePersonAndCitesTheLinkingFact() {
        val cited = ws(*family).cite("Wo wohnt mein Onkel?")
        assertEquals("Leopold wohnt in Freistadt.", cited.first())
        assertTrue(cited.contains("Mein Onkel heißt Leopold."))
    }

    @Test fun anUnknownNameOrAMissingAttributeAbstainsInsteadOfCitingSomething() {
        val w = ws(*family)
        assertEquals("ABSTAIN", w.decide("Wo wohnt eigentlich Theodor?").decision)
        assertEquals("ABSTAIN", w.decide("Wie alt ist Hanna?").decision)
        assertEquals(emptyList<String>(), w.decide("Spielt Leopold ein Instrument?").cited)
    }

    @Test fun twoEquallyRecentPeopleMakeAPronounAmbiguousAndACorrectionResolvesIt() {
        val w = ws("Greta wohnt in Ried.", "Greta arbeitet in einer Gärtnerei.", "Vera wohnt in Zwettl.", "Vera arbeitet in einer Apotheke.")
        assertEquals(2, w.cite("Wo wohnen Greta und Vera?").size)
        assertEquals("CLARIFY", w.decide("Wo arbeitet sie?").decision)
        assertEquals("Vera arbeitet in einer Apotheke.", w.cite("Ich meine Vera, nicht Greta. Wo arbeitet sie?").first())
    }

    @Test fun aLaterStatementOutranksTheOlderOneWhichStaysMarkedNotDeleted() {
        val w = ws("Der Elternabend ist am Montag um 18 Uhr.", "Die Schule heißt Volksschule Au.",
            "Der Elternabend wurde auf Mittwoch um 19 Uhr verschoben.")
        val pack = w.decide("Wann ist der Elternabend?")
        assertEquals("f2", pack.cited.first())
        assertEquals(listOf("f0"), pack.outdatedFacts)
        assertTrue(w.memory.byId.containsKey("f0"))
    }

    @Test fun sameNameForTwoThingsIsKeptApartByItsClassWord() {
        val w = ws("Die App Wolkenfeld hat die Version 4.1.", "Das Projekt Wolkenfeld wird von Ida geleitet.")
        assertEquals("Das Projekt Wolkenfeld wird von Ida geleitet.", w.cite("Wer leitet das Projekt Wolkenfeld?").first())
        assertEquals("Die App Wolkenfeld hat die Version 4.1.", w.cite("Welche Version hat die App Wolkenfeld?").first())
        // the class word also carries gender: "es" after "das Projekt" is the project, not the app ("die App")
        val v = ws("Die App Wolkenfeld hat den Status Beta.", "Das Projekt Wolkenfeld hat den Status aktiv.")
        v.decide("Wer kennt das Projekt Wolkenfeld?")
        assertEquals("Das Projekt Wolkenfeld hat den Status aktiv.", v.cite("Welchen Status hat es?").first())
    }

    @Test fun aCompoundIsNamedByItsHead() {
        val w = ws("Der Kurs Aquarell beginnt am 3. März.")
        assertEquals("ABSTAIN", w.decide("Wie hoch ist die Kursgebühr?").decision)
    }

    @Test fun thePackIsDataWithRolesAndOutdatedEvidence() {
        val w = ws("Der Elternabend ist am Montag um 18 Uhr.", "Der Elternabend wurde auf Mittwoch um 19 Uhr verschoben.")
        val json = w.decide("Wann ist der Elternabend?").render { id -> w.memory.byId.getValue(id).text }
        assertTrue(json.contains("\"role\":\"answer\"") && json.contains("possibly_outdated") && json.contains("Mittwoch"))
    }

    @Test fun claimCheckFlagsChangedDetailsButPassesParaphrases() {
        val ev = listOf("Leopold hat drei Enkel und wohnt in Freistadt.", "Die Werkstatt hat am Sonntag geschlossen.")
        assertFalse(Ewm1ClaimCheck.check("Leopold lebt in Freistadt und hat drei Enkel.", ev).flagged)
        assertTrue(Ewm1ClaimCheck.check("Leopold hat vier Enkel.", ev).flagged)
        assertTrue(Ewm1ClaimCheck.check("Leopold wohnt in Linz.", ev).flagged)
        assertTrue(Ewm1ClaimCheck.check("Ja, die Werkstatt hat am Sonntag offen.", ev).flagged)
        assertTrue(Ewm1ClaimCheck.check("Leopold hat keine Enkel.", ev).flagged)
    }
}
