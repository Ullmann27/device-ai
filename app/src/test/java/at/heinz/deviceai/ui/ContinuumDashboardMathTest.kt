package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.TimeZone

class ContinuumDashboardMathTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val now = 1_761_134_400_000L // 2025-10-22 12:00:00 UTC
    private val h = ContinuumDashboardMath.HOUR

    @Test fun hourlySeriesCountsOnlyTheLastDayAndKeepsOrder() {
        val series = ContinuumDashboardMath.hourlySeries(listOf(now - 10, now - h - 1, now - 23 * h - 5, now - 30 * h, now + 5), now)
        assertEquals(24, series.size)
        assertEquals(1f, series[23]); assertEquals(1f, series[22]); assertEquals(1f, series[0])
        assertEquals(3f, series.sum())
    }

    @Test fun dailySeriesAndTodayYesterdayUseCalendarDays() {
        val times = listOf(now - h, now - 11 * h, now - 13 * h, now - 30 * h, now - 15 * 24 * h)
        assertEquals(2, ContinuumDashboardMath.countToday(times, now, utc))
        assertEquals(2, ContinuumDashboardMath.countYesterday(times, now, utc))
        val daily = ContinuumDashboardMath.dailySeries(times, now, 14, utc)
        assertEquals(2f, daily[13]); assertEquals(2f, daily[12]); assertEquals(4f, daily.sum())
    }

    @Test fun changeQualityMedianAndUsageAreHonest() {
        assertNull("no base, no percentage", ContinuumDashboardMath.changePercent(5, 0))
        assertEquals(-50, ContinuumDashboardMath.changePercent(5, 10))
        assertEquals(25, ContinuumDashboardMath.changePercent(5, 4))
        assertNull(ContinuumDashboardMath.qualityPercent(0, 0))
        assertEquals(67, ContinuumDashboardMath.qualityPercent(2, 3))
        assertNull(ContinuumDashboardMath.median(emptyList()))
        assertEquals(300L, ContinuumDashboardMath.median(listOf(500L, 100L, 300L)))
        assertEquals(78, ContinuumDashboardMath.usedPercent(100, 22))
        assertNull(ContinuumDashboardMath.usedPercent(0, 0))
    }

    @Test fun delta24hNeedsARealMeasurementFromADayAgo() {
        assertNull(ContinuumDashboardMath.delta24h(emptyList(), now, 80))
        assertNull(ContinuumDashboardMath.delta24h(listOf(now - 2 * h to 70), now, 80))
        assertEquals(-5, ContinuumDashboardMath.delta24h(listOf(now - 25 * h to 85, now - 26 * h to 60), now, 80))
        assertNull("stale history is not a 24 h comparison", ContinuumDashboardMath.delta24h(listOf(now - 100 * h to 70), now, 80))
        assertNull(ContinuumDashboardMath.delta24h(listOf(now - 25 * h to 85), now, null))
    }

    @Test fun metricHistoryPersistsThrottlesAndFeedsTheDelta() {
        val dir = Files.createTempDirectory("metrics").toFile()
        val history = ContinuumMetricHistory(File(dir, "q.tsv"))
        history.record(now - 25 * h, 70)
        history.record(now - 25 * h + 60_000, 70) // same value within 30 min: no new entry
        history.record(now, 76)
        val read = ContinuumMetricHistory(File(dir, "q.tsv")).read()
        assertEquals(2, read.size)
        assertEquals(6, ContinuumDashboardMath.delta24h(read, now, 76))
        assertEquals("24 h: +6 Pkt.", ContinuumDashboardState.delta24h(6))
        assertEquals("24 h: −3 Pkt.", ContinuumDashboardState.delta24h(-3))
        assertEquals("24 h: noch kein Vergleich", ContinuumDashboardState.delta24h(null))
    }
}
