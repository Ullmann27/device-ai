package at.heinz.deviceai.ui

import java.io.File
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * Pure calculations behind the start screen. Every figure is derived from timestamps and measurements the app
 * already stores; nothing is smoothed into a trend that the data does not contain.
 */
object ContinuumDashboardMath {
    const val HOUR = 3_600_000L
    const val DAY = 24 * HOUR

    /** Counts per hour for the last [hours] hours (oldest first). */
    fun hourlySeries(times: List<Long>, now: Long, hours: Int = 24): List<Float> {
        val out = FloatArray(hours)
        for (t in times) {
            val age = now - t
            if (age < 0 || age >= hours * HOUR) continue
            out[hours - 1 - (age / HOUR).toInt()] += 1f
        }
        return out.toList()
    }

    /** Counts per calendar day for the last [days] days (oldest first), in the device time zone. */
    fun dailySeries(times: List<Long>, now: Long, days: Int = 14, zone: TimeZone = TimeZone.getDefault()): List<Float> {
        val today = dayStart(now, zone)
        val out = FloatArray(days)
        for (t in times) {
            if (t > now) continue
            val index = days - 1 - ((today - dayStart(t, zone)) / DAY).toInt()
            if (index in 0 until days) out[index] += 1f
        }
        return out.toList()
    }

    fun dayStart(t: Long, zone: TimeZone = TimeZone.getDefault()): Long = Calendar.getInstance(zone).apply {
        timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun countToday(times: List<Long>, now: Long, zone: TimeZone = TimeZone.getDefault()): Int {
        val start = dayStart(now, zone)
        return times.count { it in start..now }
    }

    fun countYesterday(times: List<Long>, now: Long, zone: TimeZone = TimeZone.getDefault()): Int {
        val start = dayStart(now, zone)
        val yesterday = dayStart(start - 1, zone)
        return times.count { it in yesterday until start }
    }

    /** Relative change in percent; null when the comparison base is zero (no honest percentage exists). */
    fun changePercent(current: Int, previous: Int): Int? =
        if (previous <= 0) null else (((current - previous) * 100.0) / previous).roundToInt()

    /** Holdout accuracy over all evaluated cases; null when nothing was evaluated. */
    fun qualityPercent(correct: Int, total: Int): Int? =
        if (total <= 0) null else ((correct * 100.0) / total).roundToInt().coerceIn(0, 100)

    fun median(values: List<Long>): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    /**
     * Change of a measured quality over the last 24 h in percentage points: current value minus the newest
     * stored value that is at least 24 h old. Null when no measurement from 24 h ago exists yet.
     */
    fun delta24h(history: List<Pair<Long, Int>>, now: Long, current: Int?): Int? {
        if (current == null) return null
        val reference = history.filter { now - it.first >= DAY }.maxByOrNull { it.first } ?: return null
        if (now - reference.first > 3 * DAY) return null
        return current - reference.second
    }

    /** Share of used space in percent. */
    fun usedPercent(total: Long, available: Long): Int? =
        if (total <= 0L || available < 0L || available > total) null else (((total - available) * 100.0) / total).roundToInt()
}

/**
 * Small persistent history of measured quality values (one line "epochMillis<TAB>percent"), so that the start screen
 * can show the real change over 24 h. At most one entry per 30 minutes, 30 days kept.
 */
class ContinuumMetricHistory(private val file: File) {
    @Synchronized fun read(): List<Pair<Long, Int>> {
        if (!file.isFile) return emptyList()
        return file.readLines(Charsets.UTF_8).mapNotNull { line ->
            val parts = line.split('\t')
            val t = parts.getOrNull(0)?.toLongOrNull(); val v = parts.getOrNull(1)?.toIntOrNull()
            if (t != null && v != null && v in 0..100) t to v else null
        }
    }

    @Synchronized fun record(now: Long, value: Int?): List<Pair<Long, Int>> {
        val old = read()
        if (value == null || value !in 0..100) return old
        if (old.isNotEmpty() && now - old.last().first < 30 * 60_000L && old.last().second == value) return old
        val kept = (old.filter { now - it.first <= 30 * ContinuumDashboardMath.DAY } + (now to value)).takeLast(1500)
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(kept.joinToString("\n") { "${it.first}\t${it.second}" } + "\n", Charsets.UTF_8)
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        return kept
    }
}
