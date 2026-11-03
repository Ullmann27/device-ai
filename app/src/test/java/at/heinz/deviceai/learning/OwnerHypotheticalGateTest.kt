package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * M2: hypothetical, uncertain, planned, questioned or reported statements must never become
 * confirmed personal facts, on every write path of the canonical learning ledger.
 */
class OwnerHypotheticalGateTest {
    private fun withCore(block: (ContinuumLearningCore, File) -> Unit) {
        val dir = Files.createTempDirectory("continuum-hypothetical-gate").toFile()
        var now = 1_000L
        try { block(ContinuumLearningCore(dir) { now++ }, dir) } finally { dir.deleteRecursively() }
    }

    private val nonFacts = listOf(
        "Wenn ich im Lotto gewinne, kaufe ich ein Haus in Kitzbühel.",
        "Vielleicht ziehe ich nach Linz.",
        "Ich überlege, ein rotes Auto zu kaufen.",
        "Wohne ich in Graz?",
        "Anna sagt, ich wohne in Salzburg."
    )

    @Test fun explicitRememberOfANonAssertionIsHeldUnconfirmed() = withCore { core, _ ->
        for (text in nonFacts) assertEquals(text, OwnerFactChange.HELD_UNCONFIRMED, core.confirmOwnerFact(text))
        assertTrue("no non-assertion may be an active owner fact", core.activeOwnerFacts().isEmpty())
        assertTrue(core.brainView().second.isEmpty())
        // It is still stored, visibly, as an unconfirmed owner statement (not silently dropped).
        val held = core.unconfirmedOwnerStatements()
        assertEquals(nonFacts.toSet(), held.map { it.content }.toSet())
        assertTrue(held.all { it.status == LearningStatus.OBSERVED && it.annotation!!.startsWith("unbestaetigt:") })
        assertFalse(core.replyFromMemory("Wo wohne ich?", "gate").answer.contains("Salzburg"))
        assertFalse(core.replyFromMemory("Wo kaufe ich ein Haus?", "gate").answer.contains("Kitzbühel"))
    }

    @Test fun syncAndPlainRecordAlsoNeverConfirmANonAssertion() = withCore { core, _ ->
        assertFalse(core.recordOwnerFact("Falls ich nach Berlin ziehe, nehme ich den Hund mit.") &&
            core.activeOwnerFacts().isNotEmpty())
        core.syncOwnerFacts(listOf("Mein Hund heißt Bello.", "Vielleicht heißt mein Hund Rex."))
        assertEquals(listOf("Mein Hund heißt Bello."), core.activeOwnerFacts().map { it.content })
    }

    @Test fun legacyConfirmedHypotheticalsAreQuarantinedOnSync() = withCore { core, dir ->
        // Ledger written by an older build without the gate.
        val store = FileLearningExperienceStore(File(dir, "learning-core-v2"))
        val legacy = LearningExperienceFactory.create(LearningExperienceType.USER_FACT,
            LearningProvenance.USER_CONFIRMED, LearningTrustClass.OWNER_CONFIRMED, LearningStatus.CONFIRMED,
            "Wenn ich in Wien wohne, kaufe ich ein Rad.", now = 10L)
        store.append(legacy)
        core.syncOwnerFacts(emptyList())
        assertTrue(core.activeOwnerFacts().isEmpty())
        assertEquals(LearningStatus.OBSERVED, store.all().single { it.id == legacy.id }.status)
    }

    @Test fun assertionsAndNegationBarriersKeepWorking() = withCore { core, _ ->
        assertEquals(OwnerFactChange.ADDED, core.confirmOwnerFact("Meine Tochter Klara ist 29."))
        assertEquals(OwnerFactChange.ADDED, core.confirmOwnerFact("Meine Tochter Klara ist nicht 29."))
        assertEquals(2, core.activeOwnerFacts().size)
        // the negation is a remove-only barrier: it never yields the old positive value
        assertFalse(core.replyFromMemory("Wie alt ist Klara?", "neg").answer.contains("Altersangabe: 29"))
    }

    @Test fun aHeldStatementIsNotConfirmedByRepeatingIt() = withCore { core, _ ->
        val text = "Vielleicht ziehe ich nach Linz."
        assertEquals(OwnerFactChange.HELD_UNCONFIRMED, core.confirmOwnerFact(text))
        assertEquals(OwnerFactChange.HELD_UNCONFIRMED, core.confirmOwnerFact(text))
        assertTrue(core.activeOwnerFacts().isEmpty())
    }
}
