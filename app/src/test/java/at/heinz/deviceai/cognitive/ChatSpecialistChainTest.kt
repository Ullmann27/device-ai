package at.heinz.deviceai.cognitive

import at.heinz.deviceai.learning.ContinuumBaselineSource
import at.heinz.deviceai.learning.ContinuumChatOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

/** H1 (GROK2-HANDOFF §5): EWM2-Brain → Zeitgedächtnis-Spezialist, jede Stufe mit Fallback. */
class ChatSpecialistChainTest {
    private fun outcome(a: String) = ContinuumChatOutcome(a, "e", "t", false, ContinuumBaselineSource.NONE)
    private val base = outcome("baseline")

    @Test fun temporalSpecialistReceivesTheBrainOutcomeAsItsBaseline() {
        var seen: String? = null
        val shown = ChatSpecialistChain.run(base, { outcome("brain") }, { seen = it.answer; outcome("zeit") })
        assertEquals("brain", seen)
        assertEquals("zeit", shown.answer)
    }

    @Test fun eachFailingStageFallsBackToItsInput() {
        var seen: String? = null
        assertEquals("baseline", ChatSpecialistChain.run(base, { error("brain") }, { seen = it.answer; it }).answer)
        assertEquals("baseline", seen)
        assertEquals("brain", ChatSpecialistChain.run(base, { outcome("brain") }, { error("zeit") }).answer)
    }

    @Test fun workingTurnsAreChronologicalBoundedAndNeverInTheFuture() {
        val turns = ChatSpecialistChain.workingTurns((1..70).map { "t$it" } + listOf("x".repeat(5000)), 1_000_000L)
        assertEquals(64, turns.size)
        assertEquals("t8", turns.first().text)
        assertEquals(4096, turns.last().text.length)
        assertEquals(turns.map { it.atMillis }.sorted(), turns.map { it.atMillis })
        assert(turns.all { it.atMillis < 1_000_000L })
    }
}
