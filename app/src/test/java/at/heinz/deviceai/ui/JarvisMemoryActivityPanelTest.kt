package at.heinz.deviceai.ui

import at.heinz.deviceai.learning.ContinuumMemoryStats
import at.heinz.deviceai.learning.DocumentLearned
import at.heinz.deviceai.learning.MemoryEntryStatus
import at.heinz.deviceai.learning.MemoryStored
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisMemoryActivityPanelTest {
    private val zone = ZoneId.of("Europe/Vienna")
    private val now = Instant.parse("2026-11-04T09:30:00Z") // 10:30 Wien
    private val clock = Clock.fixed(now, zone)

    @Test fun withoutDataEveryValueIsDash() {
        val s = MemoryActivitySnapshot.from(null, null)
        assertFalse(s.online)
        assertEquals(List(4) { "—" }, s.rows().map { it.first })
    }

    @Test fun showsRealStatsAndTrackerValues() {
        val stats = ContinuumMemoryStats(totalEntries = 7, newToday = 2, lastActivity = now, confirmed = 5, unconfirmed = 1, hypothetical = 1, revoked = 0)
        val tr = MemoryActivityTracker(clock, storedEventsWired = true)
        tr.onMemoryEvent(MemoryStored("a", MemoryEntryStatus.CONFIRMED, now.toEpochMilli()))
        tr.onMemoryEvent(DocumentLearned("d", "pdf", 3, now.toEpochMilli()))
        tr.onMemoryEvent(MemoryStored("old", MemoryEntryStatus.CONFIRMED, now.minusSeconds(86_400).toEpochMilli()))
        val s = MemoryActivitySnapshot.from(stats, tr)
        assertEquals(listOf("+2", "1", "1", "7"), s.rows().map { it.first })
        assertEquals(2, s.hourly!![10]); assertEquals(2, s.hourly!!.sum())
    }

    @Test fun contextUpdatesAreDashWhenNoStoredProducerIsWired() {
        val tr = MemoryActivityTracker(clock)
        tr.onMemoryEvent(MemoryStored("a", MemoryEntryStatus.CONFIRMED, now.toEpochMilli()))
        val s = MemoryActivitySnapshot.from(ContinuumMemoryStats(3, 1, now, 3, 0, 0, 0), tr)
        assertEquals("—", s.rows()[1].first)
        assertEquals("Context updates", s.rows()[1].second)
        assertEquals(1, s.hourly!!.sum()) // the event itself still counts as real activity in the bars
    }

    @Test fun labelsAreRef5EnglishAndHypothesesKeepOwnLabel() {
        assertEquals(listOf("New memories (today)", "Context updates", "Hypotheses", "Total memories"),
            MemoryActivitySnapshot.OFFLINE.rows().map { it.second })
    }

    @Test fun neverShowsRef5Numbers() {
        val ref5 = setOf("+12", "3", "5", "128")
        val empty = MemoryActivitySnapshot.from(null, MemoryActivityTracker(clock, storedEventsWired = true))
        assertTrue(empty.rows().none { it.first in ref5 })
        assertEquals("0", empty.rows()[1].first); assertEquals("—", empty.rows()[0].first)
        val zero = MemoryActivitySnapshot.from(ContinuumMemoryStats(0, 0, null, 0, 0, 0, 0), null)
        assertTrue(zero.rows().none { it.first in ref5 })
    }
}
