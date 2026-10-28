package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalChatPromptBudgetTest {
    private fun budget(
        task: String,
        memories: Boolean = false,
        conversation: Boolean = false,
        evidence: Boolean = false,
        observations: Boolean = false,
        reflection: Boolean = false
    ) = LocalChatPromptBudget.limit(
        task, memories, conversation, evidence, observations, reflection
    )

    @Test fun ordinaryFirstTurnUsesBoundedCpuPrompt() {
        for (question in listOf(
            "Wie würdest du mich bei einem kleinen Coding-Projekt unterstützen?",
            "Erkläre mir in zwei Sätzen, wie du mir beim Programmieren einer Android-App helfen kannst.",
            "Was kannst du?",
            "Wie kann ich einen Unit-Test schreiben?"
        )) {
            assertEquals(question, LocalChatPromptBudget.BRIEF_FIRST_TURN_CHARACTERS,
                budget(question))
        }
    }

    @Test fun memoryEvidenceAndContinuationsKeepFullPrompt() {
        val question = "Wie würdest du mich bei einem kleinen Coding-Projekt unterstützen?"
        assertEquals(LocalChatPromptBudget.FULL_CHARACTERS, budget(question, memories = true))
        assertEquals(LocalChatPromptBudget.FULL_CHARACTERS, budget(question, conversation = true))
        assertEquals(LocalChatPromptBudget.FULL_CHARACTERS, budget(question, evidence = true))
        assertEquals(LocalChatPromptBudget.FULL_CHARACTERS, budget(question, observations = true))
        assertEquals(LocalChatPromptBudget.FULL_CHARACTERS, budget(question, reflection = true))
    }

    @Test fun toolResearchVerificationAndLongRequestsKeepFullPrompt() {
        for (question in listOf(
            "Prüfe die installierte App und zeige mir ihren Status.",
            "Recherchiere die aktuellen Android-Sicherheitsupdates.",
            "Kontrolliere diesen Patch und prüfe die gemessenen Ergebnisse.",
            "Erkläre die Architektur im Detail. " + "Quelle und Test. ".repeat(40)
        )) {
            assertEquals(question, LocalChatPromptBudget.FULL_CHARACTERS, budget(question))
        }
        assertEquals(LocalChatPromptBudget.FULL_CHARACTERS, budget(" "))
    }
}
