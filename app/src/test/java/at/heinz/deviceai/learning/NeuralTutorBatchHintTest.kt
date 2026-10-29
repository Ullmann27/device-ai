package at.heinz.deviceai.learning

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeuralTutorBatchHintTest {
    @Test fun recognizesStructuredPastedQuestionBatch() {
        assertTrue(NeuralTutorBatchHint.isInactiveTutorPaste(
            "Frage:„Wie verarbeitet ein Computer Informationen?“\n" +
            "„Warum benötigt ein Prozessor Arbeitsspeicher?“"
        ))
    }

    @Test fun recognizesStructuredPastedGreetingBatch() {
        assertTrue(NeuralTutorBatchHint.isInactiveTutorPaste(
            "Gruß: „Servus und willkommen.“ „Einen schönen guten Morgen wünsche ich.“"
        ))
    }

    @Test fun doesNotHijackNormalQuestionsOrSingleExamples() {
        listOf(
            "Frage: Was ist Arbeitsspeicher?",
            "Frage: „Was ist Arbeitsspeicher?“",
            "Bitte erkläre diese zwei Zitate: „Hallo Welt“ und „Servus Welt“.",
            "Neuron Tutor",
            "Neuron Demo",
            "Prüfe meinen Speicher"
        ).forEach {
            assertFalse(it, NeuralTutorBatchHint.isInactiveTutorPaste(it))
        }
    }
}
