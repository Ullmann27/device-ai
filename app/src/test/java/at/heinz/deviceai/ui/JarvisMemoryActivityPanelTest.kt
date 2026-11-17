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
        val tr = MemoryActivityTracker(clock)
        tr.onMemoryEvent(MemoryStored("a", MemoryEntryStatus.CONFIRMED, now.toEpochMilli()))
        tr.onMemoryEvent(DocumentLearned("d", "pdf", 3, now.toEpochMilli()))
        tr.onMemoryEvent(MemoryStored("old", MemoryEntryStatus.CONFIRMED, now.minusSeconds(86_400).toEpochMilli()))
        val s = MemoryActivitySnapshot.from(stats, tr)
        assertEquals(listOf("+2", "1", "1", "7"), s.rows().map { it.first })
        assertEquals(2, s.hourly!![10]); assertEquals(2, s.hourly!!.sum())
    }

    @Test fun neverShowsRef5Numbers() {
        val ref5 = setOf("+12", "3", "5", "128")
        val empty = MemoryActivitySnapshot.from(null, MemoryActivityTracker(clock))
        assertTrue(empty.rows().none { it.first in ref5 })
        assertEquals("0", empty.rows()[1].first); assertEquals("—", empty.rows()[0].first)
        val zero = MemoryActivitySnapshot.from(ContinuumMemoryStats(0, 0, null, 0, 0, 0, 0), null)
        assertTrue(zero.rows().none { it.first in ref5 })
    }
}
