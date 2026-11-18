package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryTimelineRecordedAtTest {
    private val facts = listOf("a" to "Ich wohne in Wien.", "b" to "Ich wohne jetzt in Graz.")

    @Test fun withoutTimesAnUnmarkedAndANowStatementRemainAConflict() {
        assertEquals(listOf(MemoryTimeline.Link("a", "b", MemoryTimeline.Relation.CONTRADICTS)), MemoryTimeline.links(facts))
        assertEquals(MemoryTimeline.links(facts), MemoryTimeline.linksAmong(setOf("a"), facts))
    }

    @Test fun aLaterNowStatementSupersedesTheEarlierUnmarkedOne() {
        assertEquals(listOf(MemoryTimeline.Link("b", "a", MemoryTimeline.Relation.SUPERSEDES)),
            MemoryTimeline.linksAmong(setOf("a"), facts, mapOf("a" to 1L, "b" to 2L)))
        assertEquals(listOf(MemoryTimeline.Relation.CONTRADICTS),
            MemoryTimeline.linksAmong(setOf("a"), facts, mapOf("a" to 2L, "b" to 1L)).map { it.relation })
        assertEquals(listOf(MemoryTimeline.Relation.CONTRADICTS),
            MemoryTimeline.linksAmong(setOf("a"), facts, mapOf("a" to 1L, "b" to 1L)).map { it.relation })
    }

    @Test fun onlyPairsTouchingTheFocusAreCompared() {
        val more = facts + ("c" to "Kurt fährt einen grünen Bus.") + ("d" to "Kurt fährt einen gelben Bus.")
        assertTrue(MemoryTimeline.linksAmong(setOf("a"), more).none { it.newer == "c" || it.older == "c" })
        assertEquals(1, MemoryTimeline.linksAmong(setOf("c"), more).size)
        assertTrue(MemoryTimeline.linksAmong(emptySet(), more).isEmpty())
    }
}
