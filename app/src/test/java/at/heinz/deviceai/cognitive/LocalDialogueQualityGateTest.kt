package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDialogueQualityGateTest {
    private val originalQuestion =
        "Erkläre mir in drei einfachen Sätzen, wie Solarzellen aus Sonnenlicht Strom erzeugen."
    private val originalAnswer =
        "Solarzellen erzeugen Strom aus Sonnenlicht durch die Verwendung von Photovoltaik. " +
            "Diese Technik verwendet die Wirkung der Sonne auf die Materialien, um Energie zu erzeugen. " +
            "Es ist wichtig, die Effizienz der Solarzellen zu maximieren, um den Strompreis zu reduzieren."

    @Test
    fun repeatedClosingSentenceFromFold7FieldTestIsNotMarkedSuccessful() {
        val weakFollowUp =
            "Solarzellen funktionieren nicht, weil sie nicht vollständig aus Sonnenlicht nutzen. " +
                "Die Photovoltaik-Technologie ist ein Material, der die Sonnenlichtstrahlen durch die Oberfläche " +
                "absorbiert und in Strom umgewandelt. " +
                "Es ist wichtig, die Effizienz der Solarzellen zu maximieren, um den Strompreis zu reduzieren."
        val result = LocalDialogueQualityGate.evaluate(
            "Warum funktioniert das nachts nicht?", weakFollowUp, originalQuestion, originalAnswer
        )
        assertFalse(result.success)
        assertTrue(result.notes.contains("vorherigen Antwort"))
    }

    @Test
    fun correctDistinctFollowUpPassesOnlyAsStructurallyAcceptable() {
        val answer =
            "Nachts fehlt normalerweise das Sonnenlicht, das die Elektronen im Halbleiter der " +
                "Solarzelle anregt. Deshalb liefert das Solarmodul nachts praktisch keinen Strom."
        val result = LocalDialogueQualityGate.evaluate(
            "Warum funktioniert das nachts nicht?", answer, originalQuestion, originalAnswer
        )
        assertTrue(result.success)
        assertTrue(result.score < 0.8)
        assertTrue(result.notes.contains("NICHT bestätigt"))
    }

    @Test
    fun repeatingWholeAnswerOnDifferentQuestionTriggersRetry() {
        val check = LocalDialogueQualityGate.evaluate(
            "Was passiert bei Regen?", originalAnswer, originalQuestion, originalAnswer
        )
        assertFalse(check.success)
        assertTrue(check.notes.contains("wortgleich"))
    }

    @Test
    fun answeringSameQuestionTwiceMayBeIdentical() {
        val check = LocalDialogueQualityGate.evaluate(
            originalQuestion, originalAnswer, originalQuestion, originalAnswer
        )
        assertTrue(check.success)
    }

    @Test
    fun admittingUncertaintyIsNotPunishedOrMadeIntoAnOwnerFact() {
        val check = LocalDialogueQualityGate.evaluate(
            "Wie viele Moleküle sind genau im Raum?",
            "Das weiß ich nicht sicher und habe dazu keine Messwerte.",
            "Was kannst du?", "Ich erkläre Sachverhalte."
        )
        assertTrue(check.success)
        assertTrue(check.score <= 0.5)
    }

    @Test
    fun emptyAndPureEchoAreFailures() {
        assertFalse(LocalDialogueQualityGate.evaluate("Was ist der Plan?", "   ").success)
        val question = "Erkläre den photovoltaischen Effekt mit einem Beispiel."
        assertFalse(LocalDialogueQualityGate.evaluate(question, question).success)
    }

    @Test
    fun sessionIsolationMustBeDoneByCallerNotEvaluator() {
        val answer = "Solarzellen verwenden einen Halbleiter und einen elektrischen Kontakt."
        assertEquals(
            LocalDialogueQualityGate.evaluate("Wie funktionieren Solarzellen?", answer),
            LocalDialogueQualityGate.evaluate("Wie funktionieren Solarzellen?", answer, null, null)
        )
    }

    @Test
    fun qualityGateDoesNotClaimToVerifyPhysics() {
        val wrongAnswer = "Photovoltaik ist ein Material und die Sonne besteht aus Käse."
        val result = LocalDialogueQualityGate.evaluate("Was ist Photovoltaik?", wrongAnswer)
        assertTrue(result.success)
        assertTrue(result.notes.contains("NICHT bestätigt"))
    }
}
