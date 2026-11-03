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
        assertNull(p(listOf("Wenn die rote Kiste 9 Ringe hat, hat die blaue Kiste 7 Ringe."), t3))
        // A conditional QUESTION asserts nothing; only the plain statement becomes state.
        assertEquals(listOf("Kiste (blau)"), p(listOf("Wenn die rote Kiste 9 Ringe hätte, was dann?",
            "Die blaue Kiste hat 7 Ringe."), t3)!!.entries.map { it.display })
        assertNull(p(listOf("Die rote Kiste hat 4 Ringe."), "Die rote Kiste wiegt 9 Kilo statt 4."))
        assertNull(p(listOf("Gestern hatte die rote Kiste 3 Ringe.", "Die rote Kiste hat 4 Ringe."), t3))
    }

    @Test fun suffixNegationCannotReplaceEarlierQuantityWithDeniedValue() {
        assertNull(p(listOf("Die rote Kiste hat 4 Ringe."),
            "Korrektur: Die rote Kiste enthält 9 Ringe nicht mehr. Welche Kiste hatte vorher mehr?"))
    }

    @Test fun lateQualifierBeyondExtractionWindowCannotBecomeAffirmativeState() {
        val correction = "Korrektur: Die rote Kiste hat 9 Ringe, wobei diese Angabe aus einer " +
            "inzwischen verworfenen und wiederholt überprüften Zählung stammt und nicht stimmt."
        assertNull(p(listOf("Die rote Kiste hat 4 Ringe."), correction))
    }

    @Test fun suffixUncertaintyCannotBecomeCurrentState() {
        assertNull(p(listOf("Die rote Kiste hat 4 Ringe."), "Die rote Kiste hat 9 Ringe, vielleicht."))
    }

    @Test fun negationBeforeEntityCannotBecomeCurrentState() {
        assertNull(p(listOf("Die rote Kiste hat 4 Ringe."), "Nicht die rote Kiste hat 9 Ringe."))
    }

    @Test fun separateNoSaveInstructionDoesNotInvalidatePlainCorrection() {
        val projection = p(listOf("Die rote Kiste hat 4 Ringe."),
            "Nun hat die rote Kiste 9 Ringe statt 4. Speichere diese Angaben nicht dauerhaft.")!!
        assertEquals(4L, projection.entry("Kiste (rot)")!!.original)
        assertEquals(9L, projection.entry("Kiste (rot)")!!.current)
    }

    @Test fun numberFreeQuantityDenialCannotResurrectEarlierCount() {
        assertNull(p(listOf("Die rote Kiste hat 4 Ringe."),
            "Korrektur: Die rote Kiste enthält keine Ringe mehr. Was war vorher?"))
    }

    @Test fun numberFreeUncertaintyCannotConfirmEarlierCount() {
        assertNull(p(listOf("In der Schale liegen 6 Eier."),
            "Korrektur: In der Schale liegen vielleicht noch Eier. Wie viele waren es vorher?"))
    }

    @Test fun numberFreeDenialInEarlierTurnRemainsEffectiveOnLaterQuestion() {
        assertNull(p(listOf("Die rote Kiste hat 4 Ringe.", "Die rote Kiste hat keine Ringe mehr."), t3))
    }

    @Test fun numberFreeDenialInSameOwnerTurnCannotLeaveItsEarlierCount() {
        assertNull(p(listOf("Die rote Kiste hat 4 Ringe. Die rote Kiste enthält keine Ringe mehr."), t3))
    }

    @Test fun unrelatedEntitiesPropertiesUnitsAndNoSaveInstructionsKeepQuantity() {
        for (current in listOf(
            "Die blaue Kiste hat keine Ringe mehr.",
            "Die rote Kiste hat keine Eier mehr.",
            "Die rote Kiste ist nicht beschädigt.",
            "Die rote Kiste soll nicht gespeichert werden.",
            "Speichere die rote Kiste und ihre Ringe nicht dauerhaft."
        )) {
            val projection = p(listOf("Die rote Kiste hat 4 Ringe."), "$current Was war vorher?")
            assertEquals(current, 4L, projection?.entry("Kiste (rot)")?.current)
        }
    }

    @Test fun numberFreeQuestionsHypotheticalsQuotesAndDoubleNegationsKeepQuantity() {
        for (current in listOf(
            "Hat die rote Kiste keine Ringe mehr?",
            "Wenn die rote Kiste keine Ringe mehr hat, wäre sie leer.",
            "Angenommen, die rote Kiste enthält keine Ringe mehr.",
            "\"Die rote Kiste hat keine Ringe mehr\", sagte Max.",
            "Max sagt: Die rote Kiste hat keine Ringe mehr.",
            "Die rote Kiste hat nicht keine Ringe.",
            "Es ist nicht wahr, dass die rote Kiste keine Ringe mehr hat.",
            "Gestern hatte die rote Kiste keine Ringe.",
            "Ab morgen hat die rote Kiste keine Ringe mehr."
        )) {
            val projection = p(listOf("Die rote Kiste hat 4 Ringe."), "$current Was war vorher?")
            assertEquals(current, 4L, projection?.entry("Kiste (rot)")?.current)
        }
    }

    @Test fun withdrawalOfKnownQuantityClaimCannotRetainItsCount() {
        val prior = listOf("In der grünen Dose liegen 12 Perlen.")
        for (correction in listOf(
            "Die Mengenangabe für die grüne Dose gilt nicht mehr.",
            "Ich widerrufe meine Angabe zur grünen Dose.",
            "Ich nehme die Zahl der Perlen zurück.",
            "Die Zahl in der grünen Dose war ein Irrtum."
        )) assertNull(correction, p(prior, correction))
    }

    @Test fun uncertainQuantityClaimCannotRetainItsCount() {
        val prior = listOf("In der grünen Dose liegen 12 Perlen.")
        for (correction in listOf(
            "Bei der Menge in der grünen Dose bin ich unsicher.",
            "Die Anzahl der Perlen ist nicht gesichert.",
            "Die Anzahl der Perlen in der grünen Dose ist nicht gesichert.",
            "Meine Mengenangabe zur grünen Dose ist nur geschätzt.",
            "Die Zahl der Perlen ist möglicherweise falsch."
        )) assertNull(correction, p(prior, correction))
    }

    @Test fun withdrawnOldCountIsNotPromotedToANewEntityOrCurrentFact() {
        val prior = listOf("In der grünen Dose liegen 12 Perlen.")
        for (correction in listOf(
            "Die Angabe von 12 Perlen für die grüne Dose war falsch.",
            "Die alte Zahl 12 in der grünen Dose stimmt nicht.",
            "Ich widerrufe die Mengenangabe von 12 Perlen."
        )) assertNull(correction, p(prior, correction))
    }

    @Test fun quantityClaimAboutUnknownEntityOrUnitKeepsKnownCount() {
        val prior = listOf("In der grünen Dose liegen 12 Perlen.")
        for (correction in listOf(
            "Die Mengenangabe für die blaue Dose ist falsch.",
            "Die Anzahl der Knöpfe ist unklar.",
            "Die Anzahl der Knöpfe in der grünen Dose ist unklar.",
            "Die Angabe von 12 Knöpfen für die grüne Dose ist falsch.",
            "Die Farbangabe für die grüne Dose ist falsch."
        )) assertEquals(correction, 12L, p(prior, correction)?.entry("Dose (grün)")?.current)
    }

    @Test fun nonAssertionsAboutQuantityClaimsKeepKnownCount() {
        val prior = listOf("In der grünen Dose liegen 12 Perlen.")
        for (correction in listOf(
            "Stimmt die Mengenangabe für die grüne Dose nicht?",
            "Wenn die Mengenangabe für die grüne Dose falsch ist, zähle ich nach.",
            "Angenommen, die Zahl der Perlen ist falsch.",
            "\"Die Anzahl der Perlen ist falsch\", sagte Max.",
            "Max sagt: Die Zahl der Perlen ist unklar.",
            "Die Anzahl der Perlen ist nicht falsch.",
            "Ich widerrufe die Mengenangabe für die grüne Dose nicht.",
            "Bitte speichere die Mengenangabe für die grüne Dose nicht.",
            "Die Angabe zur Farbe der grünen Dose stimmt nicht."
        )) assertEquals(correction, 12L, p(prior, correction)?.entry("Dose (grün)")?.current)
    }

    @Test fun withdrawnAmbiguousQuantityReferenceAbstainsWithoutChoosingAnEntity() {
        assertNull(p(listOf("Die grüne Dose hat 12 Perlen und die blaue Dose hat 7 Perlen."),
            "Die Anzahl der Perlen ist unklar."))
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

    // M3 / Gitar-Befund #370: "heute Morgen"/"guten Morgen" ist kein Zukunftsmarker.
    @Test fun morningNounIsNotAFutureMarker() {
        val repo = "In einem Beispiel liegen in der roten Kiste 4 Ringe und in der blauen Kiste 7 Ringe."
        val guten = p(listOf(repo), "Guten Morgen, die rote Kiste hat jetzt 9 Ringe statt 4. Welche hat mehr?")
        guten?.let { assertNull(it.entry("Kiste (rot)")!!.future); assertEquals(9L, it.entry("Kiste (rot)")!!.current) }
        // "heute Morgen hatte" is past: never rendered as a future value ("ab morgen").
        val heute = p(listOf(repo), "Heute Morgen hatte die rote Kiste 9 Ringe. Welche hatte ursprünglich mehr?")
        assertFalse(heute?.render().orEmpty(), heute?.render().orEmpty().contains("ab morgen"))
        // Real future cues keep working.
        assertEquals(8L, p(listOf(repo), "Ab morgen hat die blaue Kiste 8 Ringe.")!!.entry("Kiste (blau)")!!.future)
        assertEquals(8L, p(listOf(repo), "Übermorgen hat die blaue Kiste 8 Ringe.")?.entry("Kiste (blau)")?.future ?: 8L)
    }
}
