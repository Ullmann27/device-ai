package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** M2.7: read-only cockpit stats, computed from the real ledger only. */
class ContinuumMemoryStatsTest {
    private val vienna=ZoneId.of("Europe/Vienna")
    private fun withDir(block:(File)->Unit) {
        val dir=Files.createTempDirectory("m2-stats").toFile()
        try { block(dir) } finally { dir.deleteRecursively() }
    }

    @Test fun emptyMemoryIsZeroAndNull() = withDir { dir ->
        val s=ContinuumMemoryStatsReader(dir,Clock.fixed(Instant.parse("2026-11-04T08:00:00Z"),vienna)).read()
        assertEquals(ContinuumMemoryStats(0,0,null,0,0,0,0),s)
    }

    @Test fun countsComeFromLedgerAndNewTodayUsesViennaMidnight() = withDir { dir ->
        val t=longArrayOf(0L)
        val core=ContinuumLearningCore(dir) { t[0] }
        t[0]=Instant.parse("2026-11-03T22:30:00Z").toEpochMilli() // 23:30 Vienna, 3 Nov -> yesterday
        core.confirmOwnerFact("Ich wohne in Steyr.")
        t[0]=Instant.parse("2026-11-03T23:30:00Z").toEpochMilli() // 00:30 Vienna, 4 Nov -> today
        core.confirmOwnerFact("Merke dir: Wenn ich im Lotto gewinne, kaufe ich ein Haus.")
        t[0]=Instant.parse("2026-11-04T07:00:00Z").toEpochMilli()
        core.confirmOwnerFact("Vielleicht ziehe ich nach Linz.")
        core.confirmOwnerFact("Mein Hund heißt Bello.")
        core.revokeOwnerFact("Mein Hund heißt Bello.")
        val s=ContinuumMemoryStatsReader(dir,Clock.fixed(Instant.parse("2026-11-04T08:00:00Z"),vienna)).read()
        val ledger=FileLearningExperienceStore(File(dir,"learning-core-v2")).all()
        assertEquals(ledger.size,s.totalEntries)
        assertEquals(ledger.count { it.createdAt>=Instant.parse("2026-11-03T23:00:00Z").toEpochMilli() },s.newToday)
        assertEquals(Instant.ofEpochMilli(ledger.maxOf { it.updatedAt }),s.lastActivity)
        assertEquals(1,s.confirmed)
        assertEquals(1,s.hypothetical)
        assertEquals(1,s.unconfirmed)
        assertEquals(1,s.revoked)
    }
}
