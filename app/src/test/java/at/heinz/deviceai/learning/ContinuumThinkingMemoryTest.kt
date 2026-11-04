package at.heinz.deviceai.learning

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Sprint 98: memory side of the thinking loop (Goal→Plan→Recall→Act→Check→Learn). The loop is owned elsewhere
 * (HAUPT ChiefWorkerPool / Lumo job); this component is only called per step. */
class ContinuumThinkingMemoryTest {
    private fun withMem(block: (ContinuumThinkingMemory, ContinuumLearningCore, File, LongArray) -> Unit) {
        val dir = Files.createTempDirectory("continuum-thinking").toFile()
        try {
            val clock = longArrayOf(1_700_000_000_000L)
            val core = ContinuumLearningCore(dir) { clock[0] }
            core.confirmOwnerFact("Ich wohne in Wien.")
            core.confirmOwnerFact("Mein Zahnarzt heißt Dr. Huber.")
            core.confirmOwnerFact("Vielleicht ziehe ich nach Graz.")
            clock[0] += 86_400_000L
            block(ContinuumThinkingMemory(dir, core) { clock[0] }, core, dir, clock)
        } finally { dir.deleteRecursively() }
    }

    @Test fun recallIsBoundedLabelledAndAbstains() = withMem { mem, _, _, _ ->
        val r = mem.recall(ThinkingStep("run-1", 1, ThinkingPhase.RECALL, "Termin beim Zahnarzt vereinbaren", "Zahnarzt Termin"))
        assertFalse(r.abstained)
        assertTrue(r.items.size <= ContinuumThinkingMemory.MAX_RECALL)
        assertTrue(r.items.first { "Huber" in it.text }.confirmed)
        val wohnen = mem.recall(ThinkingStep("run-1", 2, ThinkingPhase.RECALL, "Wetter daheim", "Wo wohne ich?"))
        assertTrue(wohnen.items.any { it.source == RecallSource.SHARED_ANSWER && "Wien" in it.text && it.confirmed })
        assertTrue("hypothesis labelled", wohnen.items.filter { "Graz" in it.text }.all { !it.confirmed })
        val none = mem.recall(ThinkingStep("run-1", 3, ThinkingPhase.RECALL, "Steuererklärung", "Steuernummer Finanzamt"))
        assertTrue(none.abstained); assertTrue(none.items.isEmpty())
    }

    @Test fun fullMiniRunJournalsEveryStepAndLearnsOnlyHypotheses() = withMem { mem, core, dir, clock ->
        val goal = "Zahnarzttermin bei Dr. Huber vorbereiten"
        val factsBefore = core.activeOwnerFacts()
        val phases = listOf(ThinkingPhase.GOAL, ThinkingPhase.PLAN, ThinkingPhase.RECALL, ThinkingPhase.ACT, ThinkingPhase.CHECK)
        phases.forEachIndexed { i, p ->
            clock[0] += 1_000L
            val step = ThinkingStep("run-7", i + 1, p, goal, if (p == ThinkingPhase.RECALL) "Zahnarzt" else goal)
            if (p == ThinkingPhase.RECALL) assertFalse(mem.recall(step).abstained)
            val outcome = when (p) {
                ThinkingPhase.ACT -> StepOutcome(StepStatus.OK, "fake: Kalender-Entwurf erstellt (nicht gesendet)")
                ThinkingPhase.CHECK -> StepOutcome(StepStatus.FAILED, "Uhrzeit fehlt im Entwurf")
                else -> StepOutcome(StepStatus.OK, "${p.name} erledigt")
            }
            mem.journal(step, outcome)
        }
        clock[0] += 1_000L
        val learned = mem.reflect(ThinkingRun("run-7", goal, listOf("Heinz bevorzugt Vormittagstermine.")))
        assertTrue(learned.isNotEmpty())
        assertTrue(learned.all { it.status == ThinkingEntryStatus.HYPOTHESIS })
        assertTrue(learned.any { "Uhrzeit fehlt" in it.text })
        val j = mem.runJournal("run-7")
        assertEquals(5, j.count { it.kind == ThinkingEntryKind.STEP })
        assertEquals(j.sortedBy { it.atMillis }, j)
        assertEquals(listOf(1, 2, 3, 4, 5), j.filter { it.kind == ThinkingEntryKind.STEP }.map { it.stepIndex })
        assertEquals(factsBefore, core.activeOwnerFacts())
        assertTrue(dir.walkTopDown().none { it.name.endsWith(".skill.md") })
        // a later run recalls the earlier hypothesis, still unconfirmed
        val later = mem.recall(ThinkingStep("run-8", 1, ThinkingPhase.RECALL, "neuer Termin", "Vormittagstermine"))
        assertTrue(later.items.any { it.source == RecallSource.HYPOTHESIS && !it.confirmed })
    }

    @Test fun aReflectionCannotOverwriteAConfirmedOwnerFact() = withMem { mem, core, dir, _ ->
        val ledger = File(dir, "learning-core-v2/experiences-v2.bin")
        val before = ledger.readBytes()
        mem.reflect(ThinkingRun("run-9", "Umzug prüfen", listOf("Ich wohne in Graz.", "Korrektur: Ich wohne in Linz.")))
        assertArrayEquals(before, ledger.readBytes())
        val answer = core.temporalMemoryView().answer("Wo wohne ich?", System.currentTimeMillis())!!
        assertTrue(answer.text.contains("Wien"))
        assertFalse(answer.text.contains("Graz") || answer.text.contains("Linz"))
        val recalled = mem.recall(ThinkingStep("run-10", 1, ThinkingPhase.RECALL, "x", "Wo wohne ich?"))
        assertTrue(recalled.items.filter { "Linz" in it.text || "Graz" in it.text }.all { !it.confirmed })
    }

    @Test fun journalIsBoundedAndSurvivesRestart() = withMem { mem, core, dir, clock ->
        repeat(ContinuumThinkingMemory.MAX_ENTRIES + 20) { i ->
            clock[0] += 1L
            mem.journal(ThinkingStep("run-b", i + 1, ThinkingPhase.ACT, "g", "q"), StepOutcome(StepStatus.OK, "s$i"))
        }
        val fresh = ContinuumThinkingMemory(dir, core) { clock[0] }
        val all = fresh.runJournal("run-b")
        assertEquals(ContinuumThinkingMemory.MAX_ENTRIES, all.size)
        assertEquals(ContinuumThinkingMemory.MAX_ENTRIES + 20, all.last().stepIndex)
    }
}
