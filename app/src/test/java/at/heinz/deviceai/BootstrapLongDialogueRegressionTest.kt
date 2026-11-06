package at.heinz.deviceai

import at.heinz.deviceai.cognitive.LocalChatPromptBudget
import org.junit.Assert.*
import org.junit.Test

class BootstrapLongDialogueRegressionTest {
    @Test fun completeLongDialogueReachesTheOriginalLocalModelQuestion() {
        val request = "Erkläre diese Aufgabe. " + "Zusatz. ".repeat(350) +
            "Korrektur am Ende: nicht Wien, sondern Graz."
        assertTrue(request.length > 2_048)
        assertTrue(LocalChatPromptBudget.acceptsCompleteTask(request))
        val decision = BootstrapBrain.decide(request, emptyList())
        assertEquals(BootstrapKind.REPLY, decision.kind)
        assertTrue("Complete dialogue must reach the existing local route", decision.allowNeuralRecall)
        assertEquals(request, LocalFirstPolicy.localModelQuestion(request, request.take(512)))
    }

    @Test fun oversizedSpecialCommandsAreNotReinterpretedAsFreeDialogue() {
        for (prefix in listOf("Merke dir: ", "Merk dir: ", "Trainiere: ", "Korrigiere: ")) {
            val decision = BootstrapBrain.decide(prefix + "x".repeat(2_100), emptyList())
            assertEquals(prefix, BootstrapKind.REPLY, decision.kind)
            assertFalse(prefix, decision.allowNeuralRecall)
        }
    }

    @Test fun unicodeAndTotalTaskOverflowStayBlockedBeforeLocalInference() {
        for (request in listOf("x".repeat(7_901), "東京".repeat(1_600))) {
            val decision = BootstrapBrain.decide(request, emptyList())
            assertEquals(BootstrapKind.REPLY, decision.kind)
            assertFalse(decision.allowNeuralRecall)
        }
    }
}
