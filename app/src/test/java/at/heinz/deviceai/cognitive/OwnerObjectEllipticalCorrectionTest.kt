package at.heinz.deviceai.cognitive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Code63, owner case „Werkzeugkiste“. Reproduced on 3613a23a7 with the compiled ledger:
 * „Meine Werkzeugkiste ist rot.“ → „Nein, ich meinte blau.“ → „Welche Farbe hat meine Werkzeugkiste?“
 * answered „Farbe: rot“. Only the fully spelled correction („… die Werkzeugkiste ist blau“) worked.
 */
class OwnerObjectEllipticalCorrectionTest {
    private fun ledger(vararg turns: String) = OwnerObjectStateLedger().apply { turns.forEach(::observeOwnerTurn) }
    private val question = "Welche Farbe hat meine Werkzeugkiste?"

    @Test fun aBareCorrectedColourReplacesTheStatedOneAndKeepsTheHistory() {
        listOf(
            "Nein, ich meinte blau.",
            "Nein, blau.",
            "Nein blau",
            "Korrektur: Sie ist blau.",
            "Ich meinte blau",
            "Falsch, sondern blau!",
            "Sorry, ich wollte sagen blau.",
            "Nee, die ist blau."
        ).forEach { correction ->
            val answer = ledger("Meine Werkzeugkiste ist rot.", correction).answer(question)
            assertNotNull(correction, answer)
            assertTrue("$correction → $answer", answer!!.contains("Farbe: blau"))
            assertFalse("$correction → $answer", answer.contains("Farbe: rot"))
            // The old statement stays recognisable as corrected, never as the current fact.
            assertTrue("$correction → $answer", answer.contains("rot → blau"))
        }
    }

    @Test fun theFirstStatementRemainsAnswerableAsTheOriginalOne() {
        val answer = ledger("Meine Werkzeugkiste ist rot.", "Nein, ich meinte blau.")
            .answer("Welche Farbe hatte meine Werkzeugkiste zuerst?")
        assertTrue(answer, answer!!.contains("rot"))
    }

    @Test fun aCorrectionAfterAnInterveningQuestionBindsToTheOnlyColouredObject() {
        val answer = ledger("Meine Werkzeugkiste ist rot.", "Wie spät ist es?", "Nein, ich meinte blau.").answer(question)
        assertTrue(answer, answer!!.contains("Farbe: blau"))
    }

    @Test fun withTwoObjectsInThePrecedingMessageNothingIsGuessed() {
        val ledger = ledger("Meine Werkzeugkiste ist rot und mein Fahrrad ist grün.", "Nein, ich meinte blau.")
        val box = ledger.answer(question)!!
        val bike = ledger.answer("Welche Farbe hat mein Fahrrad?")!!
        // Neither object silently becomes blue, and neither old colour is presented as certain.
        assertFalse(box, box.contains("Farbe: blau"))
        assertFalse(bike, bike.contains("Farbe: blau"))
        assertTrue(box, box.contains("unsicher") && box.contains("bitte nenne den Gegenstand"))
        assertTrue(bike, bike.contains("unsicher"))
        assertTrue(box, box.contains("Nicht zugeordnet"))
    }

    @Test fun withTwoColouredObjectsTheCorrectionBindsOnlyToTheOneJustStated() {
        val ledger = ledger("Meine Werkzeugkiste ist rot.", "Mein Fahrrad ist grün.", "Nein, ich meinte blau.")
        assertTrue(ledger.answer("Welche Farbe hat mein Fahrrad?")!!.contains("Farbe: blau"))
        assertTrue(ledger.answer(question)!!.contains("Farbe: rot"))
    }

    @Test fun twoObjectsAndNoAdjacentStatementLeaveTheCorrectionUnassigned() {
        val ledger = ledger("Meine Werkzeugkiste ist rot.", "Mein Fahrrad ist grün.", "Wie spät ist es?",
            "Nein, ich meinte blau.")
        val box = ledger.answer(question)!!
        assertTrue(box, box.contains("Farbe: rot"))
        assertTrue(box, box.contains("Nicht zugeordnet"))
    }

    @Test fun aLaterSpelledOutStatementResolvesTheUncertainty() {
        val ledger = ledger("Meine Werkzeugkiste ist rot und mein Fahrrad ist grün.", "Nein, ich meinte blau.",
            "Die Werkzeugkiste ist blau.")
        val box = ledger.answer(question)!!
        assertTrue(box, box.contains("Farbe: blau"))
        assertFalse(box, box.contains("unsicher"))
    }

    @Test fun textThatIsNotABareColourCorrectionIsLeftToTheExistingRules() {
        // No rejection or correction cue: a lone colour word is not a correction.
        assertTrue(ledger("Meine Werkzeugkiste ist rot.", "Blau.").answer(question)!!.contains("Farbe: rot"))
        // A question or a hedge never changes the state.
        assertTrue(ledger("Meine Werkzeugkiste ist rot.", "Nein, ich meinte blau?").answer(question)!!.contains("Farbe: rot"))
        assertTrue(ledger("Meine Werkzeugkiste ist rot.", "Nein, vielleicht blau.").answer(question)!!.contains("Farbe: rot"))
        // Not a colour: nothing happens, nothing is invented.
        assertTrue(ledger("Meine Werkzeugkiste ist rot.", "Nein, ich meinte morgen.").answer(question)!!.contains("Farbe: rot"))
        // Without any tracked object there is nothing to correct.
        assertNull(ledger("Nein, ich meinte blau.").answer(question))
    }
}
