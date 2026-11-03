package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.ZonedDateTime

/** M2: the temporal memory is a specialist of the existing EWM2 brain contract, not a second AI. */
class ContinuumTemporalMemorySpecialistTest {
    private fun t(y: Int, m: Int = 1, d: Int = 1) =
        ZonedDateTime.of(y, m, d, 12, 0, 0, 0, ContinuumTemporalMemory.VIENNA).toInstant().toEpochMilli()

    private val baseline = ContinuumChatOutcome("bisherige Antwort", "CONTINUUM · Modell", "trace", false,
        ContinuumBaselineSource.NONE)

    private fun withCore(mode: ContinuumBrainMode, block: (File, ContinuumLearningCore, ContinuumBrainSettings) -> Unit) {
        val dir = Files.createTempDirectory("continuum-temporal-specialist").toFile()
        var clock = t(2018, 3, 1)
        try {
            val core = ContinuumLearningCore(dir) { clock }
            core.confirmOwnerFact("Ich wohne in Wien.")
            clock = t(2022, 6, 1)
            core.confirmOwnerFact("Ich bin nach Graz gezogen.")
            clock = t(2026, 11, 3)
            val settings = ContinuumBrainSettings(dir).also { it.set(mode) }
            block(dir, core, settings)
        } finally { dir.deleteRecursively() }
    }

    @Test fun offAndShadowNeverChangeTheShownAnswer() {
        for (mode in listOf(ContinuumBrainMode.OFF, ContinuumBrainMode.SHADOW)) withCore(mode) { dir, core, settings ->
            val r = ContinuumTemporalMemorySpecialist.decide(dir, core, "Wo wohne ich?", baseline,
                settings = settings, nowMillis = t(2026, 11, 3))
            assertSame(baseline, r.outcome)
            assertEquals(mode, r.mode)
            assertEquals(if (mode == ContinuumBrainMode.OFF) ContinuumBrainStatus.OFF else ContinuumBrainStatus.SHADOW, r.status)
        }
    }

    @Test fun onAnswersFromTheCanonicalLedgerWithCitations() = withCore(ContinuumBrainMode.ON) { dir, core, settings ->
        val r = ContinuumTemporalMemorySpecialist.decide(dir, core, "Wo wohnte ich 2020?", baseline,
            settings = settings, nowMillis = t(2026, 11, 3))
        assertEquals(ContinuumBrainStatus.ON_MEMORY, r.status)
        assertTrue(r.outcome.answer, r.outcome.answer.contains("Wien"))
        assertTrue(r.outcome.grounded)
        assertEquals(ContinuumBaselineSource.MEMORY, r.outcome.source)
        val wienId = core.activeOwnerFacts().single { it.content == "Ich wohne in Wien." }.id
        assertEquals(listOf(wienId), r.outcome.memoryIds)
        assertTrue(r.outcome.evidence.contains("Zeitgedächtnis"))
    }

    @Test fun onKeepsTheBaselineOutsideItsGrammar() = withCore(ContinuumBrainMode.ON) { dir, core, settings ->
        val r = ContinuumTemporalMemorySpecialist.decide(dir, core, "Erzähl mir einen Witz.", baseline,
            settings = settings, nowMillis = t(2026, 11, 3))
        assertSame(baseline, r.outcome)
        assertEquals(ContinuumBrainStatus.ON_KEPT_BASELINE, r.status)
    }

    @Test fun onNeverPresentsAHypotheticalAsAFact() = withCore(ContinuumBrainMode.ON) { dir, core, settings ->
        core.revokeAllOwnerFacts()
        core.confirmOwnerFact("Vielleicht ziehe ich nach Linz.")
        val r = ContinuumTemporalMemorySpecialist.decide(dir, core, "Wo wohne ich?", baseline,
            settings = settings, nowMillis = t(2026, 11, 3))
        assertEquals(ContinuumBrainStatus.ON_ABSTAIN, r.status)
        assertFalse(r.outcome.grounded)
        assertTrue(r.outcome.answer, r.outcome.answer.contains("nicht bestätigt"))
        assertTrue(r.outcome.memoryIds.isEmpty())
    }

    @Test fun onClarifiesAnUnmarkedContradiction() = withCore(ContinuumBrainMode.ON) { dir, core, settings ->
        core.confirmOwnerFact("Ich wohne in Linz.")
        val r = ContinuumTemporalMemorySpecialist.decide(dir, core, "Wo wohne ich?", baseline,
            settings = settings, nowMillis = t(2026, 11, 3))
        assertEquals(ContinuumBrainStatus.ON_CLARIFY, r.status)
    }

    @Test fun workingMemoryIsUsedButNeverWrittenToTheLedger() = withCore(ContinuumBrainMode.ON) { dir, core, settings ->
        val before = core.status().experiences.total
        val r = ContinuumTemporalMemorySpecialist.decide(dir, core, "Wo wohne ich?", baseline,
            working = listOf(WorkingTurn("Ich wohne jetzt in Bregenz.", t(2026, 11, 3) - 1_000)),
            settings = settings, nowMillis = t(2026, 11, 3))
        assertTrue(r.outcome.answer.contains("Bregenz"))
        assertEquals(before, core.status().experiences.total)
        assertTrue(core.activeOwnerFacts().none { it.content.contains("Bregenz") })
    }
}
