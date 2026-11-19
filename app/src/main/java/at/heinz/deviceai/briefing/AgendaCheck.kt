package at.heinz.deviceai.briefing

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

/**
 * „Sag mir, ob ich etwas vergessen könnte“ (owner sentence 19.11.2026) – answered from fixed rules over the real
 * calendar entries and from nothing else: overlaps, a change of place without time for the way, entries without a
 * place or without a time, very early and very late entries, what starts soon, days with nothing, free time.
 *
 * Pure and deterministic: the same days and the same clock always give the same findings and the same sentences. No
 * model takes part – calendar entries are private and stay on the device – and nothing is guessed: what is not in the
 * calendar cannot be found here, and the answer says so.
 */
internal object AgendaCheck {
    enum class Kind { OVERLAP, TIGHT_CHANGE, STARTS_SOON, EARLY, LATE, NO_PLACE, NO_TIME }

    /** One thing a rule noticed. [text]: a clause in the owner's language, without a full stop. */
    data class Finding(val kind: Kind, val date: LocalDate, val text: String)

    /** A stretch without any entry between [DAY_START] and [DAY_END] of at least [MIN_FREE]. */
    data class FreeBlock(val date: LocalDate, val from: LocalTime, val until: LocalTime) {
        val minutes: Long get() = Duration.between(from, until).toMinutes()
    }

    /**
     * [events]: timed entries that are not over, counted once. [emptyDays]: days of the span on which the calendar
     * holds nothing at all (today only when nothing was entered for it, not when everything is over).
     */
    data class Report(val events: Int, val allDay: Int, val findings: List<Finding>, val free: List<FreeBlock>,
                      val emptyDays: List<LocalDate>)

    val DAY_START: LocalTime = LocalTime.of(8, 0)
    val DAY_END: LocalTime = LocalTime.of(20, 0)
    val MIN_FREE: Duration = Duration.ofHours(2)
    val EARLY_BEFORE: LocalTime = LocalTime.of(7, 0)
    val LATE_FROM: LocalTime = LocalTime.of(20, 0)
    /** No more than this between two entries at different places counts as „no time for the way“. */
    const val TIGHT_MINUTES = 15L
    /** An entry of today that starts within this time is named first. */
    const val SOON_MINUTES = 120L
    const val MAX_NAMED = 3

    fun check(days: List<ContinuumAgenda.Day>, now: LocalDateTime): Report {
        val findings = ArrayList<Finding>()
        val free = ArrayList<FreeBlock>()
        val empty = ArrayList<LocalDate>()
        val today = now.toLocalDate()
        val counted = HashSet<Triple<String, Long, Long>>()
        val allDayCounted = HashSet<String>()
        for (day in days) {
            val date = day.date
            if (day.empty) empty += date
            // Every rule looks at an entry on the day it starts, so one over midnight is not named twice.
            val ahead = day.ahead.filter { it.start.toLocalDate() == date || date == days.first().date }
            ahead.forEach { counted += Triple(ContinuumAgenda.title(it.event), it.event.startMillis, it.event.endMillis) }
            day.allDay.forEach { allDayCounted += ContinuumAgenda.title(it) }
            val timed = ahead.filter { it.event.endMillis > it.event.startMillis }
            val own = ahead.filter { it.start.toLocalDate() == date }
            for (i in timed.indices) for (j in i + 1 until timed.size) {
                val first = timed[i]
                val second = timed[j]
                if (second.event.startMillis < first.event.endMillis) {
                    if (second.start.toLocalDate() == date || first.start.toLocalDate() == date) findings += Finding(Kind.OVERLAP, date,
                        "${day(date, today, lower = true)} überschneiden sich ${named(first, date)} und ${named(second, date)}")
                    continue
                }
                if (j != i + 1) continue
                val gap = Duration.between(first.end, second.start).toMinutes()
                val from = place(first)
                val to = place(second)
                if (gap in 0..TIGHT_MINUTES && from != null && to != null && !from.equals(to, ignoreCase = true)) findings += Finding(
                    Kind.TIGHT_CHANGE, date, "${day(date, today, lower = true)} " + (if (gap == 0L) "bleibt keine Zeit" else "bleiben nur $gap Minuten") +
                        " für den Weg von ${ContinuumAgenda.title(first.event)} ($from) zu ${ContinuumAgenda.title(second.event)} ($to)")
            }
            for (entry in own) {
                val name = ContinuumAgenda.title(entry.event)
                val start = entry.start.toLocalTime()
                val until = Duration.between(now, entry.start).toMinutes()
                if (date == today && !entry.running && until in 0..SOON_MINUTES) findings += Finding(Kind.STARTS_SOON, date,
                    "$name beginnt " + when {
                        until < 2 -> "jetzt"
                        until < 60 -> "in $until Minuten"
                        until < 90 -> "in gut einer Stunde"
                        until < 120 -> "in knapp zwei Stunden"
                        else -> "in zwei Stunden"
                    } + " (${ContinuumAgenda.spokenTime(start)})")
                if (start.isBefore(EARLY_BEFORE)) findings += Finding(Kind.EARLY, date,
                    "${day(date, today, lower = true)} beginnt $name schon um ${ContinuumAgenda.spokenTime(start)}")
                else if (!start.isBefore(LATE_FROM)) findings += Finding(Kind.LATE, date,
                    "${day(date, today, lower = true)} beginnt $name erst um ${ContinuumAgenda.spokenTime(start)}")
                if (place(entry) == null) findings += Finding(Kind.NO_PLACE, date,
                    "$name ${day(date, today, lower = true)} um ${ContinuumAgenda.spokenTime(start)}")
                if (entry.event.endMillis == entry.event.startMillis) findings += Finding(Kind.NO_TIME, date,
                    "$name ${day(date, today, lower = true)}")
            }
            free += freeBlocks(day, if (date == today) now.toLocalTime() else null)
        }
        return Report(counted.size, allDayCounted.size, findings.distinct(), free, empty)
    }

    /** The stretches of [day] between [DAY_START] and [DAY_END] without a timed entry; [from]: nothing before it counts. */
    fun freeBlocks(day: ContinuumAgenda.Day, from: LocalTime? = null): List<FreeBlock> {
        val date = day.date
        var cursor = maxOf(DAY_START, from?.let(::roundUp) ?: DAY_START)
        val blocks = ArrayList<FreeBlock>()
        val busy = day.timed.filter { it.event.endMillis > it.event.startMillis }.map { entry ->
            (if (entry.start.toLocalDate().isBefore(date)) LocalTime.MIN else entry.start.toLocalTime()) to
                (if (entry.end.toLocalDate().isAfter(date)) LocalTime.MAX else entry.end.toLocalTime())
        }.sortedBy { it.first }
        for ((start, end) in busy) {
            if (start > cursor && Duration.between(cursor, minOf(start, DAY_END)) >= MIN_FREE) blocks += FreeBlock(date, cursor, minOf(start, DAY_END))
            if (end > cursor) cursor = end
            if (cursor >= DAY_END) break
        }
        if (cursor < DAY_END && Duration.between(cursor, DAY_END) >= MIN_FREE) blocks += FreeBlock(date, cursor, DAY_END)
        return blocks
    }

    /** The next quarter of an hour: free time is offered from a time one can say. */
    private fun roundUp(time: LocalTime): LocalTime {
        val minutes = time.hour * 60 + time.minute + if (time.second > 0 || time.nano > 0) 1 else 0
        val rounded = (minutes + 14) / 15 * 15
        return if (rounded >= 24 * 60) LocalTime.MAX else LocalTime.of(rounded / 60, rounded % 60)
    }

    private fun place(entry: ContinuumAgenda.Entry): String? = entry.event.location?.replace(Regex("(?i)https?://\\S+|www\\.\\S+|\\p{Cntrl}"), " ")
        ?.replace(Regex("\\s+"), " ")?.trim()?.take(ContinuumAgenda.MAX_TITLE)?.takeIf { it.isNotEmpty() }

    private fun named(entry: ContinuumAgenda.Entry, date: LocalDate): String = ContinuumAgenda.title(entry.event) + " (" +
        (if (entry.start.toLocalDate() == date) ContinuumAgenda.spokenTime(entry.start.toLocalTime()) else "seit dem Vortag") + " bis " +
        (if (entry.end.toLocalDate() == date) ContinuumAgenda.spokenTime(entry.end.toLocalTime()) else "über Mitternacht") + ")"

    private fun day(date: LocalDate, today: LocalDate, lower: Boolean = false): String =
        ContinuumAgenda.onDay(date, today).let { if (lower) it else it.replaceFirstChar { first -> first.titlecase(Locale.GERMAN) } }

    // ── Wording ─────────────────────────────────────────────────────────────────────────────────────────────

    const val ONLY_CALENDAR = "Mehr kann ich dazu nicht sagen: Ich sehe nur, was im Kalender steht – was dort fehlt, kann ich nicht wissen."

    /**
     * The answer to „habe ich etwas vergessen?“: what the rules noticed, most urgent first, then what has no place or
     * no time, then the days with nothing – and plainly that this is all a calendar can tell. [label]: „diese Woche“.
     */
    fun spoken(report: Report, label: String, today: LocalDate): String = buildString {
        val entries = report.events + report.allDay
        if (entries == 0) {
            append("Für ").append(label).append(" steht nichts im Kalender – da gibt es nichts, das ich prüfen könnte. ").append(ONLY_CALENDAR)
            return@buildString
        }
        fun of(vararg kinds: Kind) = report.findings.filter { it.kind in kinds }
        val urgent = of(Kind.STARTS_SOON)
        val clashes = of(Kind.OVERLAP, Kind.TIGHT_CHANGE)
        val hours = of(Kind.EARLY, Kind.LATE)
        val noPlace = of(Kind.NO_PLACE)
        val noTime = of(Kind.NO_TIME)
        append("Geprüft habe ich ").append(if (entries == 1) "den einen Eintrag" else "die " +
            ContinuumSystemCheck.count(entries, "eine", "Einträge")).append(" für ").append(label).append('.')
        if (urgent.isNotEmpty()) append(" Gleich zuerst: ").append(ContinuumSystemCheck.list(urgent.take(MAX_NAMED).map { it.text })).append('.')
        if (clashes.isNotEmpty()) append(" Achtung: ").append(clashes.take(MAX_NAMED).joinToString("; ") { it.text }).append(more(clashes.size)).append('.')
        if (hours.isNotEmpty()) append(" Ungewöhnliche Zeiten: ").append(ContinuumSystemCheck.list(hours.take(MAX_NAMED).map { it.text }))
            .append(more(hours.size)).append('.')
        if (noPlace.isNotEmpty()) append(if (noPlace.size == 1) " Kein Ort ist eingetragen bei " else " Kein Ort ist eingetragen bei: ")
            .append(ContinuumSystemCheck.list(noPlace.take(MAX_NAMED).map { it.text })).append(more(noPlace.size)).append('.')
        if (noTime.isNotEmpty()) append(" Ohne Dauer eingetragen: ").append(ContinuumSystemCheck.list(noTime.take(MAX_NAMED).map { it.text })).append('.')
        if (report.allDay > 0) append(if (report.allDay == 1) " Ein Eintrag ist ganztägig und hat deshalb keine Uhrzeit." else
            " " + ContinuumSystemCheck.count(report.allDay, "Ein Eintrag", "Einträge").replaceFirstChar { it.titlecase(Locale.GERMAN) } +
                " sind ganztägig und haben deshalb keine Uhrzeit.")
        if (urgent.isEmpty() && clashes.isEmpty() && hours.isEmpty() && noPlace.isEmpty() && noTime.isEmpty())
            append(" Aufgefallen ist mir nichts: keine Überschneidung, kein enger Ortswechsel, und jeder Termin hat Ort und Uhrzeit.")
        if (report.emptyDays.isNotEmpty()) append(" Nichts eingetragen ist ")
            .append(ContinuumSystemCheck.list(report.emptyDays.map { ContinuumAgenda.onDay(it, today) })).append('.')
        append(' ').append(ONLY_CALENDAR)
    }

    private fun more(total: Int): String = if (total > MAX_NAMED) " – und " +
        ContinuumSystemCheck.count(total - MAX_NAMED, "ein weiterer Fall", "weitere Fälle") else ""

    /** The answer to „wo habe ich noch Zeit?“: the free stretches day by day, and the days with nothing at all. */
    fun spokenFree(report: Report, label: String, today: LocalDate): String {
        if (report.free.isEmpty()) return "Zwischen ${ContinuumAgenda.spokenTime(DAY_START)} und ${ContinuumAgenda.spokenTime(DAY_END)} " +
            "finde ich für $label keinen freien Block von mindestens zwei Stunden."
        val byDay = report.free.groupBy { it.date }
        val shown = byDay.entries.take(MAX_FREE_DAYS)
        return "Frei – mindestens zwei Stunden am Stück zwischen ${ContinuumAgenda.spokenTime(DAY_START)} und " +
            "${ContinuumAgenda.spokenTime(DAY_END)} – bist du " + shown.joinToString("; ") { (date, blocks) ->
            ContinuumAgenda.onDay(date, today) + " " + (if (date in report.emptyDays && blocks.size == 1 && blocks[0].from == DAY_START &&
                blocks[0].until == DAY_END) "den ganzen Tag" else ContinuumSystemCheck.list(blocks.map {
                "von ${ContinuumAgenda.spokenTime(it.from)} bis ${ContinuumAgenda.spokenTime(it.until)}" }))
        } + (if (byDay.size > shown.size) " – und an " + ContinuumSystemCheck.count(byDay.size - shown.size, "einem weiteren Tag", "weiteren Tagen")
            else "") + ". Das gilt für das, was im Kalender steht."
    }

    const val MAX_FREE_DAYS = 7

    /** Card lines for the findings: the most urgent first, at most [limit]. */
    fun lines(report: Report, limit: Int = ContinuumBriefingBoard.MAX_LINES): List<String> {
        val order = listOf(Kind.STARTS_SOON, Kind.OVERLAP, Kind.TIGHT_CHANGE, Kind.EARLY, Kind.LATE, Kind.NO_PLACE, Kind.NO_TIME)
        val all = report.findings.sortedBy { order.indexOf(it.kind) }.map { finding ->
            when (finding.kind) {
                Kind.NO_PLACE -> "ohne Ort: " + finding.text
                Kind.NO_TIME -> finding.text
                else -> finding.text
            }.take(90)
        }
        if (all.isEmpty()) return listOf("nichts aufgefallen")
        return if (all.size <= limit) all else all.take(limit - 1) + "+ ${all.size - (limit - 1)} weitere"
    }
}
