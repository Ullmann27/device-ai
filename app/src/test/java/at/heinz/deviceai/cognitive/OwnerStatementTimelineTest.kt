package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M2: domänenneutrale, flüchtige Zeitachse der Nutzeraussagen (Dossier 6.2) statt Ring-Spezialfall. */
class OwnerStatementTimelineTest {
    private val repoT1 = "In einem Beispiel liegen in der roten Kiste 4 Ringe und in der blauen Kiste 7 Ringe. Welche Kiste enthält mehr?"
    private val dossierT1 = "In der roten Kiste liegen 4 Kupferringe, in der blauen 7. Welche Kiste hat mehr?"
    private val t2 = "Nun hat die rote Kiste 9 Ringe statt 4. Welche Kiste enthält jetzt mehr?"
    private val t3 = "Welche Kiste hatte ursprünglich mehr Ringe, bevor sich die Anzahl verändert hat?"

    private fun p(prior: List<String>, now: String) = OwnerStatementTimeline.project(prior, now)

    @Test fun fieldTestT2AndT3KeepOriginalAndCurrentValues() {
        for (t1 in listOf(repoT1, dossierT1)) {
            val t2p = assertNotNull(p(listOf(t1), t2)).let { p(listOf(t1), t2)!! }
            assertEquals(Pair(4L, 9L), t2p.entry("Kiste (rot)")!!.let { it.original to it.current })
            assertEquals(Pair(7L, 7L), t2p.entry("Kiste (blau)")!!.let { it.original to it.current })
            val t3p = p(listOf(t1, t2), t3)!!
            assertTrue(t3p.entry("Kiste (rot)")!!.changed)
            assertFalse(t3p.entry("Kiste (blau)")!!.changed)
            val text = t3p.render()
            assertTrue(text, text.contains("Kiste (rot): ursprünglich 4"))
            assertTrue(text, text.contains("aktuell 9"))
            assertTrue(text, text.contains("Kiste (blau): 7"))
            assertTrue(text, text.contains("nicht speichern"))
        }
    }

    @Test fun worksForOtherDomainsAndCorrectionPhrasings() {
        val eggs = p(listOf("In der Schale liegen 6 Eier."), "Nun sind in der Schale 4 Eier statt 6. Wie viele fehlen?")!!
        assertEquals(6L, eggs.entry("Schale")!!.original); assertEquals(4L, eggs.entry("Schale")!!.current)
        val statt = p(listOf("Die rote Kiste hat 4 Ringe."), "Jetzt hat die rote Kiste statt 4 nun 9 Ringe.")!!
        assertEquals(9L, statt.entry("Kiste (rot)")!!.current)
        val vonAuf = p(listOf("Die rote Kiste hat 4 Ringe."), "Die rote Kiste geht von 4 auf 9 Ringe.")!!
        assertEquals(9L, vonAuf.entry("Kiste (rot)")!!.current)
    }

    @Test fun negationHedgeAlternativeAndConditionalNeverBecomeState() {
        assertNull(p(listOf("Die rote Kiste hat nicht 4 sondern 5 Ringe."), t3))
        assertNull(p(listOf("Die rote Kiste hat vielleicht 4 Ringe."), t3))
        assertNull(p(listOf("Die rote Kiste hat 4 oder 5 Ringe."), t3))
        assertNull(p(listOf("Wenn die rote Kiste 9 Ringe hätte, was dann?", "Die blaue Kiste hat 7 Ringe."), t3))
        assertNull(p(listOf("Gestern hatte die rote Kiste 3 Ringe.", "Die rote Kiste hat 4 Ringe."), t3))
    }

    @Test fun futureIsSeparatedAndMixedFutureFailsClosed() {
        val f = p(listOf(repoT1), "Ab morgen hat die blaue Kiste 8 Ringe. Welche hat dann ursprünglich mehr?")!!
        assertEquals(7L, f.entry("Kiste (blau)")!!.current)
        assertEquals(8L, f.entry("Kiste (blau)")!!.future)
        assertTrue(f.render().contains("ab morgen 8"))
        assertNull(p(listOf(repoT1), "Ab morgen hat die blaue Kiste 8 Ringe und die rote Kiste 2 Ringe."))
    }

    @Test fun questionsDoNotAssertAndConflictsFailClosed() {
        val q = p(listOf(repoT1), "Hat die rote Kiste vorher 9 Ringe gehabt?")!!
        assertEquals(4L, q.entry("Kiste (rot)")!!.current)
        assertNull(p(listOf("Die rote Kiste wiegt 4 Kilo."), "Nun hat die rote Kiste 9 Ringe statt 4."))
        assertNull(p(listOf("Die rote Kiste hat 4 Ringe und die rote Kiste hat 5 Ringe."), t3))
    }

    @Test fun newScenarioResetsAndBoundsAreEnforced() {
        val reset = p(listOf(repoT1), "Neues Beispiel: Die rote Kiste hat 2 Ringe und die blaue Kiste 3 Ringe. Was war vorher?")!!
        assertEquals(2L, reset.entry("Kiste (rot)")!!.original)
        assertNull(p(List(5) { repoT1 }, t3))
        assertNull(p(listOf(repoT1 + "x".repeat(1_000)), t3))
        assertNull(p(emptyList(), t3))
        assertNull(p(listOf("Hallo, wie geht es dir?"), t3))
        val many = listOf("Kiste", "Schale", "Tasche", "Dose", "Truhe", "Vase", "Tonne", "Wanne", "Kanne")
            .mapIndexed { i, noun -> "Die $noun hat ${i + 1} Ringe." }.joinToString(" ")
        assertNull(p(listOf(many), t3))
    }
}
