package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

/** E7 on EWM2: the certified rule core answers location questions from SWB1-selected confirmed owner facts. */
class OwnerMemoryLocationSpecialistTest {
    private fun withCore(block: (ContinuumLearningCore) -> Unit) {
        val dir = Files.createTempDirectory("continuum-e7-ewm2").toFile()
        try { block(ContinuumLearningCore(dir)) } finally { dir.deleteRecursively() }
    }

    @Test fun confirmedResidenceIsAnsweredExactlyWithFactProvenance() = withCore { core ->
        core.confirmOwnerFact("Mama wohnt in Graz.")
        core.confirmOwnerFact("Papa wohnt in Wien.")
        core.confirmOwnerFact("Mama mag Kaffee.")
        val factsBefore = core.activeOwnerFacts().map { it.id to it.status }
        val weights = core.modelSnapshot()
        val mama = core.replyFromMemory("Wo wohnt Mama?", "e7-chat")
        assertTrue(mama.grounded)
        assertTrue(mama.answer, mama.answer.startsWith("Aus deiner bestätigten Erinnerung: Graz\n"))
        val mamaId = core.activeOwnerFacts().single { it.content == "Mama wohnt in Graz." }.id
        assertTrue(mama.answer.contains("Beleg: Erinnerung $mamaId"))
        assertTrue(mama.answer.contains("Dazu finde ich in meinem Gedächtnis:"))
        val papa = core.replyFromMemory("Wo wohnt Papa?", "e7-chat")
        assertTrue(papa.answer, papa.answer.startsWith("Aus deiner bestätigten Erinnerung: Wien\n"))
        // Read-only: no fact status and no weights changed.
        assertEquals(factsBefore, core.activeOwnerFacts().map { it.id to it.status })
        assertArrayEquals(weights, core.modelSnapshot())
    }

    @Test fun unknownPersonRelationMismatchAndConflictsFallBackToPlainRetrieval() = withCore { core ->
        core.confirmOwnerFact("Mama wohnt in Graz.")
        // Unknown person: no exact line is invented from Mama's fact.
        assertFalse(core.replyFromMemory("Wo wohnt Oma?", "e7-a").answer.contains("Aus deiner bestätigten Erinnerung"))
        // "ist" (presence) is not proven by a residence fact.
        assertFalse(core.replyFromMemory("Wo ist Mama?", "e7-b").answer.contains("Aus deiner bestätigten Erinnerung"))
        // Two unrevoked, conflicting residences: abstain and show both.
        core.confirmOwnerFact("Mama wohnt in Linz.")
        val both = core.replyFromMemory("Wo wohnt Mama?", "e7-c").answer
        assertFalse(both, both.contains("Aus deiner bestätigten Erinnerung"))
        // The ranker may surface only one of them; the specialist must not pick a winner.
        assertTrue(both, both.contains("Dazu finde ich in meinem Gedächtnis:"))
        // After revoking the stale residence the newer one is proven (temporal change via revision).
        assertEquals(1, core.revokeOwnerFact("Mama wohnt in Graz."))
        val current = core.replyFromMemory("Wo wohnt Mama?", "e7-d").answer
        assertTrue(current, current.startsWith("Aus deiner bestätigten Erinnerung: Linz\n"))
        assertFalse(current.contains("Graz"))
    }

    @Test fun negatedTemporalOrMultiClauseFactsNeverProduceAnExactLine() = withCore { core ->
        core.confirmOwnerFact("Anna wohnt nicht in Graz.")
        core.confirmOwnerFact("Emma wohnt seit 2024 in Wien.")
        core.confirmOwnerFact("Leo wohnt in Graz. Leo ist in Wien.")
        core.confirmOwnerFact("Tom wohnt in Graz.")
        core.confirmOwnerFact("Tom arbeitet in Wien.")
        for (q in listOf("Wo wohnt Anna?", "Wo wohnt Emma?", "Wo wohnt Leo?", "Wo ist Leo?", "Wo wohnt Tom?"))
            assertFalse(q, core.replyFromMemory(q, "e7-neg").answer.contains("Aus deiner bestätigten Erinnerung"))
    }

    @Test fun moreThanFourFactsAboutAPersonExceedTheRuleCoreBoundAndAbstain() = withCore { core ->
        core.confirmOwnerFact("Mama wohnt in Graz.")
        for (f in listOf("Mama arbeitet in Linz.", "Mama war in Rom.", "Mama kauft in Wien ein.", "Mama turnt in Steyr."))
            core.confirmOwnerFact(f)
        val answer = core.replyFromMemory("Wo wohnt Mama?", "e7-bound").answer
        assertFalse(answer, answer.contains("Aus deiner bestätigten Erinnerung"))
        assertTrue(answer.contains("Dazu finde ich in meinem Gedächtnis:") || answer.contains("Dazu habe ich noch keine"))
    }
}
