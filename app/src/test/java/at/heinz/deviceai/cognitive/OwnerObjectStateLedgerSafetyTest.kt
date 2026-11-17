package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class OwnerObjectStateLedgerSafetyTest {
    private val initial = "Ich habe eine blaue Werkzeugkiste mit zwölf Schrauben in der Garage."
    private fun ledger(vararg turns: String) = OwnerObjectStateLedger().apply {
        observeOwnerTurn(initial)
        turns.forEach(::observeOwnerTurn)
    }
    private fun assertOriginal(state: OwnerObjectStateLedger) {
        val answer = state.answer("Welche Farbe hat meine Werkzeugkiste, wie viele Schrauben sind darin und wo steht sie?")
        assertNotNull(answer)
        assertTrue(answer, answer!!.contains("Farbe: blau"))
        assertTrue(answer, answer.contains("Anzahl Schrauben: 12"))
        assertTrue(answer, answer.contains("Ort: in der Garage"))
    }

    @Test fun punctuationFreeQuestionsDoNotAssertTheirSuggestedValues() {
        assertOriginal(ledger("Welche Farbe hat meine rote Werkzeugkiste"))
        assertOriginal(ledger("Sind es neun Schrauben in der Werkzeugkiste"))
        assertOriginal(ledger("Wo steht meine Werkzeugkiste im Garten"))
    }

    @Test fun hypotheticalOrQuotedInstructionsDoNotBecomeOwnerObjectState() {
        for (text in listOf(
            "Schreibe ein Beispiel über eine rote Werkzeugkiste mit neun Schrauben im Garten.",
            "Im Buch steht: „Meine Werkzeugkiste ist rot mit neun Schrauben im Garten.“",
            "Angenommen, meine Werkzeugkiste ist rot und enthält neun Schrauben im Garten.",
            "Wenn meine Werkzeugkiste rot wäre, hätte sie neun Schrauben im Garten."
        )) assertOriginal(ledger(text))
    }

    @Test fun anotherPersonsSameNamedObjectNeverOverwritesTheOwnerObject() {
        assertOriginal(ledger("Mein Nachbar hat eine rote Werkzeugkiste mit neun Schrauben im Garten."))
        assertOriginal(ledger("Meine Schwester sagt, ihre Werkzeugkiste sei rot mit neun Schrauben im Garten."))
    }

    @Test fun unrelatedNumbersAndColorWordsDoNotBindByBeingTheOnlySlot() {
        for (text in listOf("Das war mein 2. Versuch.", "Blau ist schön, Rot mag ich auch.",
            "Das Jahr 2026 gefällt mir.", "Ich brauche zwei Minuten.")) assertOriginal(ledger(text))
    }

    @Test fun sameNounForAnotherObjectRequiresClarification() {
        val state = ledger("Ich habe noch eine rote Werkzeugkiste mit neun Schrauben im Garten.")
        assertNull(state.answer("Welche Farbe hat die Werkzeugkiste?"))
    }

    @Test fun anUnparsedRelevantChangeNeverResurrectsThePriorLocation() {
        val state = ledger("Ich habe die Werkzeugkiste verkauft.")
        assertNull(state.answer("Wo steht meine Werkzeugkiste?"))
    }

    @Test fun boundedHistoryNeverMislabelsTheOldestRetainedValueAsTheFirst() {
        val state = ledger()
        for (count in 13..50) state.observeOwnerTurn("In der Werkzeugkiste sind jetzt $count Schrauben.")
        val answer = state.answer("Wie viele Schrauben waren es ursprünglich?")
        assertTrue(answer == null || answer.contains("nicht mehr vollständig"))
    }

    @Test fun queryValuesDoNotTurnIntoNegativeQuestionAnswers() {
        val state = ledger()
        assertNull(state.answer("Welche Farbe hat meine rote Werkzeugkiste?"))
        assertOriginal(state)
    }

    @Test fun sourceAttributionDoesNotDenyIndependentPersistentStorage() {
        val state = ledger()
        val answer = state.answer("Welche Farbe hat meine Werkzeugkiste?")!!
        assertFalse(answer.contains("keine dauerhaft gespeicherte Erinnerung"))
        assertFalse(state.promptBlock("Welche Farbe hat meine Werkzeugkiste?").contains("nicht gespeichert"))
    }
    @Test fun tentativeQuestionsAndConditionalPronounsKeepTheLastAssertion() {
        assertOriginal(ledger("Ist die Kiste vielleicht grün?"))
        assertOriginal(ledger("Wenn sie rot wäre, hätte sie neun Schrauben."))
        assertOriginal(ledger("Meine Schwester hat ihre Werkzeugkiste rot gestrichen."))
    }

    @Test fun contrastBetweenTwoSameNamedObjectsNeverBecomesAColorCorrection() {
        val state = ledger("Nicht die rote, sondern die blaue Werkzeugkiste.")
        assertNull(state.answer("Welche Farbe hat die Werkzeugkiste?"))
        assertFalse(state.promptBlock("Wo steht die Werkzeugkiste?").contains("Ort in der Garage"))
    }

    @Test fun actualSessionExitForgetsTheObjectState() {
        val context = LocalConversationContext()
        context.record("session", initial, "Rot, sieben, Garten.")
        assertTrue(context.objectStateAnswer("session", "Welche Farbe hat die Werkzeugkiste?")!!.contains("Farbe: blau"))
        context.forgetForExit()
        assertNull(context.objectStateAnswer("session", "Welche Farbe hat die Werkzeugkiste?"))
        context.record("session", "Hallo", "Hallo!")
        assertNull(context.objectStateAnswer("session", "Welche Farbe hat die Werkzeugkiste?"))
    }

    @Test fun objectAndRevisionLimitsFailClosed() {
        val state = ledger()
        for (noun in listOf("Tasche", "Box", "Truhe", "Mappe", "Dose", "Koffer", "Rucksack"))
            state.observeOwnerTurn("Ich habe eine rote $noun im Garten.")
        assertNull(state.answer("Welche Farbe hat die Werkzeugkiste?"))
        assertTrue(state.render().contains("unvollständig oder mehrdeutig"))
        assertTrue(state.render().length < 500)
    }

    @Test fun replacementWithDifferentUnitDoesNotChangeAnotherObject() {
        val state = ledger("Ich habe eine rote Tasche mit drei Ringen im Keller.",
            "Nicht zwölf, sondern sieben Ringe.")
        val bag = state.answer("Wie viele Ringe sind in der Tasche?")!!
        assertTrue(bag, bag.contains("Anzahl Ringen: 3"))
        assertFalse(bag, bag.contains("Anzahl Ringen: 7"))
    }

}
