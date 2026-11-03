package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.LocalDate

/** Chief pattern point 4: memory layers as a thin facade over the existing LEX2 ledger (no new store/format). */
class ContinuumMemoryLayersTest {
    private fun t(y: Int, m: Int = 6) = LocalDate.of(y, m, 1).atStartOfDay(ContinuumTemporalMemory.VIENNA).toInstant().toEpochMilli()
    private val now = t(2026, 11)
    private fun withLayers(block: (ContinuumMemoryLayers, File) -> Unit) {
        val dir = Files.createTempDirectory("continuum-layers").toFile()
        try {
            val clock = longArrayOf(0L)
            val core = ContinuumLearningCore(dir) { clock[0] }
            clock[0] = t(2018); core.confirmOwnerFact("Ich wohne in Steyr.")
            clock[0] = t(2020); core.confirmOwnerFact("Mein Hund heißt Balu.")
            clock[0] = t(2022); core.confirmOwnerFact("Vielleicht ziehe ich nach Barcelona.")
            clock[0] = t(2023); core.confirmOwnerFact("Ich arbeite bei Fronius.")
            clock[0] = t(2024); core.confirmOwnerFact("Ich bin nach Enns gezogen.")
            clock[0] = t(2025); core.revokeOwnerFact("Ich arbeite bei Fronius.")
            clock[0] = now
            block(ContinuumMemoryLayers(core), dir)
        } finally { dir.deleteRecursively() }
    }

    @Test fun profileHoldsOnlyDurableConfirmedFacts() = withLayers { layers, _ ->
        val texts = layers.profile().map { it.text }
        assertTrue(texts.containsAll(listOf("Ich wohne in Steyr.", "Mein Hund heißt Balu.", "Ich bin nach Enns gezogen.")))
        assertFalse("revoked", texts.any { "Fronius" in it })
        assertFalse("hypothetical", texts.any { "Barcelona" in it })
    }

    @Test fun journalIsDatedHistoryWithHonestStatus() = withLayers { layers, _ ->
        val j = layers.journal()
        assertEquals(j.sortedBy { it.atMillis }, j)
        assertEquals(JournalStatus.REVOKED, j.single { "Fronius" in it.text }.status)
        assertEquals(JournalStatus.UNCONFIRMED, j.single { "Barcelona" in it.text }.status)
        assertEquals(JournalStatus.CONFIRMED, j.single { "Steyr" in it.text }.status)
        assertEquals(listOf("Ich bin nach Enns gezogen."), layers.journal(t(2024), t(2025)).map { it.text })
    }

    @Test fun sharedKnowledgeIsReadOnlyAndAnswersOnlyFromConfirmedFacts() = withLayers { layers, _ ->
        val shared: SharedOwnerKnowledge = layers.shared()
        assertTrue(shared.answer("Wo wohne ich?", now)!!.text.contains("Enns"))
        assertFalse(shared.facts().any { "Barcelona" in it.text })
        // interface exposes no mutation: only facts() and answer()
        val methods = SharedOwnerKnowledge::class.java.declaredMethods.map { it.name }.toSet()
        assertEquals(setOf("facts", "answer"), methods)
    }

    @Test fun searchRanksOlderEntriesByKeywordAndTimeAndAbstains() = withLayers { layers, _ ->
        val hund = layers.search("Wie hieß nochmal mein Hund?", now)
        assertFalse(hund.abstained)
        assertTrue(hund.hits.first().entry.text.contains("Balu"))
        val year = layers.search("Was war 2018 mit Wohnen?", now)
        assertTrue(year.hits.first().entry.text.contains("Steyr"))
        val none = layers.search("Wie heißt meine Katze?", now)
        assertTrue(none.abstained); assertTrue(none.hits.isEmpty())
    }

    @Test fun hypotheticalsStayUnconfirmedInSearch() = withLayers { layers, _ ->
        val r = layers.search("Barcelona", now)
        assertFalse(r.abstained)
        assertFalse(r.hits.single().confirmed)
        assertEquals(JournalStatus.UNCONFIRMED, r.hits.single().entry.status)
    }
}
