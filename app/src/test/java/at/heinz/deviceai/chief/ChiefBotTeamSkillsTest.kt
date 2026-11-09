package at.heinz.deviceai.chief

import at.heinz.deviceai.cognitive.CrystallizedSkillCandidate
import at.heinz.deviceai.learning.ContinuumSkillLibrary
import at.heinz.deviceai.learning.OwnerSkillApproval
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.UUID

/** Synthetic library fixtures test context admission, not independently learned skills. */
class ChiefBotTeamSkillsTest {
    @get:Rule val folder = TemporaryFolder()
    private fun candidate() = CrystallizedSkillCandidate("synthetic-candidate", "kotlin", listOf("read_file"),
        3, 1.0, 1.0, listOf("synthetic-1", "synthetic-2", "synthetic-3"))
    private fun prompt(library: ContinuumSkillLibrary): String {
        val profiles = ChiefBotProfileStore(folder.newFolder())
        profiles.seedDefaults(20)
        val task = ChiefTeamTask("team-" + UUID.randomUUID(), "LOCAL_OWNER_UI", "bot-atlas", "bot-kora", "bot-lint",
            listOf(1, 1, 1), 1, "Kotlin Nullfehler im Text prüfen", "a".repeat(64), 1000, ChiefTeamStatus.ANALYZING)
        return ChiefBotTextHandoff.prompt(task, profiles.get("bot-kora")!!, 0, library)
    }

    @Test fun loadsOnlyRelevantApprovedInstructionWithoutGrantingTools() {
        val library = ContinuumSkillLibrary(folder.newFolder())
        library.propose(candidate(), "kotlin-review", "Kotlin Nullfehler", "UNIQUE_APPROVED_INSTRUCTION")
        library.propose(candidate(), "weather-review", "Wettervorhersage morgen", "UNRELATED_INSTRUCTION")
        library.approve("kotlin-review", OwnerSkillApproval("Ja, kotlin-review freigeben", 1))
        library.approve("weather-review", OwnerSkillApproval("Ja, weather-review freigeben", 1))
        val text = prompt(library)
        assertTrue(text.contains("UNIQUE_APPROVED_INSTRUCTION"))
        assertTrue(text.contains("synthetic-candidate"))
        assertTrue(text.contains(ChiefTeamResponse.hash("UNIQUE_APPROVED_INSTRUCTION")))
        assertFalse(text.contains("UNRELATED_INSTRUCTION"))
        assertTrue(text.contains("Keine Werkzeuge"))
    }

    @Test fun draftAndTamperedInstructionsNeverReachModelContext() {
        val root = folder.newFolder()
        val library = ContinuumSkillLibrary(root)
        library.propose(candidate(), "kotlin-review", "Kotlin Nullfehler", "DRAFT_INSTRUCTION")
        assertFalse(prompt(library).contains("DRAFT_INSTRUCTION"))
        library.approve("kotlin-review", OwnerSkillApproval("Ja, kotlin-review freigeben", 1))
        val file = java.io.File(root, "kotlin-review.skill.md")
        file.writeText(file.readText().replace("DRAFT_INSTRUCTION", "TAMPERED_INSTRUCTION"))
        assertFalse(prompt(library).contains("TAMPERED_INSTRUCTION"))
    }

    @Test fun oversizedInstructionIsOmittedWholeWithExplicitBudgetReason() {
        val library = ContinuumSkillLibrary(folder.newFolder())
        library.propose(candidate(), "kotlin-review", "Kotlin Nullfehler", "INSTRUCTION_HEAD" + "x".repeat(2100))
        library.approve("kotlin-review", OwnerSkillApproval("Ja, kotlin-review freigeben", 1))
        val text = prompt(library)
        assertFalse(text.contains("INSTRUCTION_HEAD"))
        assertTrue(text.contains("CONTEXT_BUDGET"))
    }
}
