package at.heinz.deviceai.cognitive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Code55 field evidence (owner, Fold7): a long sentence like „ich habe 17 Schrauben in der Garage … 5 Schrauben dazu
 * bekommen … blaue und zwei rote …“. An addition or removal is a change RELATIVE to the stated count – never a new
 * total of 5 – and „drei blaue und zwei rote“ describes a part of the counted things, never the colour of the object.
 * A clear single change is folded (17 + 5 = 22); anything incomplete is recorded as unclear and asked back, never
 * guessed. The exact long field sentence is not available verbatim; these are its reported parts.
 */
class OwnerObjectStateCountChangeTest {
    private fun ledger(vararg turns: String) = OwnerObjectStateLedger().apply { turns.forEach(::observeOwnerTurn) }
    private val start = "Ich habe 17 Schrauben in der Garage."
    private val count = "Wie viele Schrauben habe ich und wo sind sie?"

    @Test fun aClearAdditionIsAddedToTheStatedCount() {
        for (addition in listOf("Ich habe 5 Schrauben dazu bekommen.", "Es sind 5 Schrauben dazugekommen.",
                "5 Schrauben sind hinzugekommen.", "Ich habe zusätzlich fünf Schrauben bekommen.")) {
            val answer = ledger(start, addition).answer(count)
            assertNotNull(addition, answer)
            assertTrue("$addition → $answer", answer!!.contains("Anzahl Schrauben: 22"))
            assertTrue("$addition → $answer", answer.contains("Ort: in der Garage"))
            assertTrue("$addition → $answer", answer.contains("17 → 22"))
        }
        // The same within one long message.
        val one = ledger("$start Ich habe 5 Schrauben dazu bekommen.").answer(count)!!
        assertTrue(one, one.contains("Anzahl Schrauben: 22"))
    }

    @Test fun aClearRemovalIsSubtractedFromTheStatedCount() {
        for (removal in listOf("Ich habe 3 Schrauben verbraucht.", "3 Schrauben habe ich weggenommen.")) {
            val answer = ledger(start, removal).answer(count)!!
            assertTrue("$removal → $answer", answer.contains("Anzahl Schrauben: 14"))
        }
        val tooMany = ledger(start, "Ich habe 20 Schrauben verbraucht.").answer(count)!!
        assertTrue("never below zero, asked back instead: $tooMany", tooMany.contains("Anzahl Schrauben: unklar"))
    }

    @Test fun theReportedFieldSentenceNeverTurnsAPartIntoTheTotalOrTheObjectColour() {
        for (turns in listOf(
            listOf(start, "Ich habe 5 Schrauben dazu bekommen, drei blaue und zwei rote."),
            listOf("$start Ich habe 5 Schrauben dazu bekommen, drei blaue und zwei rote.")
        )) {
            val state = ledger(*turns.toTypedArray())
            val answer = state.answer(count)!!
            assertTrue("$turns → $answer", answer.contains("Anzahl Schrauben: 22"))
            assertFalse("$turns → $answer", answer.contains("Anzahl Schrauben: 2\n"))
            assertFalse("$turns → $answer", answer.contains("Anzahl Schrauben: 3\n"))
            val colour = state.answer("Welche Farbe haben die Schrauben?")!!
            assertFalse("a part's colour is not the colour of all screws: $colour", colour.contains("Farbe: rot"))
            assertFalse(colour, colour.contains("Farbe: blau"))
            assertTrue("the colour split is reported as not assigned: $colour", colour.contains("Nicht zugeordnet"))
        }
        // A toolbox stays blue when screws of other colours are added to it.
        val box = ledger("Meine Werkzeugkiste ist blau, steht im Keller und enthält zwölf Schrauben.",
            "Ich habe 5 Schrauben dazu bekommen, drei blaue und zwei rote.")
        val boxAnswer = box.answer("Welche Farbe hat meine Werkzeugkiste und wie viele Schrauben sind darin?")!!
        assertTrue(boxAnswer, boxAnswer.contains("Farbe: blau"))
        assertTrue(boxAnswer, boxAnswer.contains("Anzahl Schrauben: 17"))
    }

    @Test fun anIncompleteChangeIsAskedBackInsteadOfGuessed() {
        for (unclear in listOf(
            "Ich habe ein paar Schrauben dazu bekommen.",          // no number
            "Ich habe 5 Schrauben bekommen.",                     // direction unknown: new total or addition?
            "Ich habe 5 und 3 Schrauben dazu bekommen.",          // two numbers for one change
            "Ich habe 5 Schrauben dazu bekommen, die liegen im Keller." // possibly another set at another place
        )) {
            val state = ledger(start, unclear)
            val answer = state.answer(count)
            assertNotNull(unclear, answer)
            assertTrue("$unclear → $answer", answer!!.contains("Anzahl Schrauben: unklar"))
            assertTrue("$unclear → $answer", answer.contains("bitte"))
            assertFalse("$unclear → $answer", answer.contains("Anzahl Schrauben: 17\n"))
            assertFalse("$unclear → $answer", answer.contains("Anzahl Schrauben: 22"))
            assertFalse("$unclear → $answer", answer.contains("Anzahl Schrauben: 5"))
        }
    }

    @Test fun aLaterTotalInTheSameMessageWins() {
        val answer = ledger(start, "Ich habe 5 Schrauben dazu bekommen. Es sind jetzt 23 Schrauben.").answer(count)!!
        assertTrue(answer, answer.contains("Anzahl Schrauben: 23"))
    }

    @Test fun thePlaceOfAnAddedPartNeverMovesTheWholeSet() {
        val answer = ledger(start, "Ich habe 5 Schrauben dazu bekommen, die liegen im Keller.").answer(count)!!
        assertTrue(answer, answer.contains("Ort: in der Garage"))
        assertFalse(answer, answer.contains("Ort: im Keller"))
    }
}
