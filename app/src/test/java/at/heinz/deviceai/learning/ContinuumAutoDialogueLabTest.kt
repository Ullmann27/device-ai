package at.heinz.deviceai.learning

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ContinuumAutoDialogueLabTest {
    @Test fun aDanglingCursorSymlinkMustFailClosedInsteadOfResettingConsumedStages() {
        val dir = Files.createTempDirectory("dialogue-cursor-link").toFile()
        try {
            val cursor = File(dir, "learning-core-v2/auto-dialogue-v1-next.txt")
            cursor.parentFile!!.mkdirs()
            Files.createSymbolicLink(cursor.toPath(), File(dir, "missing-cursor").toPath())
            assertThrows(IllegalArgumentException::class.java) {
                ContinuumAutoDialogueLab(dir).attemptedStages()
            }
            assertTrue(Files.isSymbolicLink(cursor.toPath()))
        } finally { dir.deleteRecursively() }
    }

    @Test fun cancellationAfterDurableReservationConsumesOnlyThatStageAndNeverPublishesWeights() {
        val dir = Files.createTempDirectory("dialogue-cursor-interrupt").toFile()
        try {
            val core = ContinuumLearningCore(dir)
            core.confirmOwnerFact("Mein Prüfroboter heißt Sicher.")
            val facts = core.activeOwnerFacts()
            val weights = core.modelSnapshot()
            val cursor = File(dir, "learning-core-v2/auto-dialogue-v1-next.txt")
            val round = requireNotNull(ContinuumAutoDialogueLab(dir).next { cursor.exists() })
            assertEquals(1, round.stage)
            assertTrue(round.stoppedEarly)
            assertFalse(round.accepted)
            assertEquals(0, round.steps)
            assertNull(round.modelHash)
            assertEquals(1, ContinuumAutoDialogueLab(dir).attemptedStages())
            assertNull(ContinuumAutoDialogueLab(dir).next { true })
            assertEquals("1", cursor.readText())
            assertEquals(facts, core.activeOwnerFacts())
            assertArrayEquals(weights, core.modelSnapshot())
            assertFalse(core.cre1ResearchStatus().available)
        } finally { dir.deleteRecursively() }
    }

    @Test fun invalidCursorIsPreservedAndFiniteBudgetCannotRestart() {
        val dir = Files.createTempDirectory("dialogue-cursor-invalid").toFile()
        try {
            val cursor = File(dir, "learning-core-v2/auto-dialogue-v1-next.txt")
            cursor.parentFile!!.mkdirs()
            for (bad in listOf("-1", "65", "NaN", "")) {
                cursor.writeText(bad)
                assertThrows(RuntimeException::class.java) { ContinuumAutoDialogueLab(dir).next() }
                assertEquals(bad, cursor.readText())
            }
            cursor.writeText("64")
            assertNull(ContinuumAutoDialogueLab(dir).next())
            assertEquals("64", cursor.readText())
        } finally { dir.deleteRecursively() }
    }

    @Test fun automaticResearchRequiresOptInTimeAndAnUnusedStage() {
        assertFalse(ContinuumAutoDialoguePolicy.shouldStart(false, 15000, 0))
        assertFalse(ContinuumAutoDialoguePolicy.shouldStart(true, 3499, 0))
        assertFalse(ContinuumAutoDialoguePolicy.shouldStart(true, 15000, -1))
        assertFalse(ContinuumAutoDialoguePolicy.shouldStart(true, 15000, 64))
        assertTrue(ContinuumAutoDialoguePolicy.shouldStart(true, 3500, 63))
    }
}
