package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDialogueQualityGateTest {
    @Test fun detailedAnswerIsNotRejectedOnlyForPassingTheOldTwentyThousandLimit() {
        val answer = (1..420).joinToString("\n\n") {
            "Abschnitt $it: Diese Erklärung enthält einen eigenen nachvollziehbaren Schritt mit Begründung."
        } + "\nAbschließende Einschränkung: Die vorgeschlagene Aktion NICHT AUSFÜHREN."
        assertTrue(answer.length in 32_001..64_000)
        val result = LocalDialogueQualityGate.evaluate("Erkläre das ausführlich in vielen Abschnitten.", answer)
        assertTrue(result.notes, result.success)
        assertTrue(result.notes.contains("NICHT bestätigt"))
        assertFalse(LocalDialogueQualityGate.evaluate("Erkläre das ausführlich.", "x".repeat(64_001)).success)
    }

    @Test fun longAnswerStillCannotExposeInternalFrames() {
        val answer = "Einordnung. ".repeat(2_000) + " UNTRUSTED EVIDENCE DATA "
        val result = LocalDialogueQualityGate.evaluate("Erkläre das ausführlich.", answer)
        assertFalse(result.success)
        assertTrue(result.notes.contains("Interner Recherche-Platzhalter"))
    }

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
    fun explicitRequestToRepeatPriorAnswerIsRespected() {
        val result = LocalDialogueQualityGate.evaluate(
            "Wiederhole deine letzte Antwort.", originalAnswer, originalQuestion, originalAnswer
        )
        assertTrue(result.success)
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
    @Test fun leakedInternalResearchInstructionsAreNotAcceptedAsChatAnswers() {
        val prompt = "Erkläre mir den Unterschied zwischen Wissen speichern und logisch schlussfolgern."
        val leaked = "Bitte stellen Sie mir die \"UNTRUSTED EVIDENCE DATA\" zur Verfügung, " +
            "damit ich die Synthese durchführen kann."
        val result = LocalDialogueQualityGate.evaluate(prompt, leaked)
        assertFalse(result.success)
        assertTrue(result.notes.contains("Recherche-Platzhalter"))
    }

    @Test fun ownerMayExplicitlyAskAboutResearchBoundaryTerminology() {
        val result = LocalDialogueQualityGate.evaluate(
            "Was bedeutet der interne Marker UNTRUSTED EVIDENCE DATA?",
            "UNTRUSTED EVIDENCE DATA bezeichnet eine Kennzeichnung nicht vertrauenswürdiger Quellentexte.")
        assertTrue(result.success)
    }

}
