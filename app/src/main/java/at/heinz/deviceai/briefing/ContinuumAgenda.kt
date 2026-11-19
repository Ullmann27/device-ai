package at.heinz.deviceai.briefing

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One calendar instance as the device calendar holds it. All-day instances carry UTC day bounds (Android). */
internal data class ContinuumCalendarEvent(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val location: String? = null
)

internal sealed interface ContinuumCalendarRead {
    data class Events(val events: List<ContinuumCalendarEvent>) : ContinuumCalendarRead
    /** READ_CALENDAR is not granted. */
    data object NotGranted : ContinuumCalendarRead
    data class Failed(val reason: String) : ContinuumCalendarRead
}

/** The device calendar, read only. Fakes in host tests; the phone reads `CalendarContract.Instances`. */
internal interface ContinuumCalendarSource {
    fun granted(): Boolean
    /** Instances of visible calendars that overlap [fromMillis, untilMillis). Never writes. */
    fun read(fromMillis: Long, untilMillis: Long): ContinuumCalendarRead
}

/**
 * The owner's appointments of a day as the briefing speaks and shows them. Pure: events come in as plain rows, the
 * clock as a parameter. Only what the calendar holds is said – an empty day is „nichts im Kalender“.
 */
internal object ContinuumAgenda {
    const val MAX_SPOKEN = 4
    const val MAX_TITLE = 60
    private val linkOrControl = Regex("(?i)https?://\\S+|www\\.\\S+|\\p{Cntrl}")

    /** One day's view. [timed]: events with a time that touch the day, in order. [ended]: how many of them are over. */
    data class Day(val date: LocalDate, val allDay: List<ContinuumCalendarEvent>, val timed: List<Entry>, val ended: Int) {
        /** Timed events that are not over yet. */
        val ahead: List<Entry> get() = timed.filter { !it.over }
        val empty: Boolean get() = allDay.isEmpty() && timed.isEmpty()
    }

    /** [start]/[end] in the owner's zone; [over]: ended before now; [running]: started and not ended. */
    data class Entry(val event: ContinuumCalendarEvent, val start: LocalDateTime, val end: LocalDateTime,
                     val over: Boolean, val running: Boolean)

    /** Start of [date] to noon of the following day: today plus tomorrow morning. */
    fun briefingWindow(date: LocalDate, zone: ZoneId): Pair<Long, Long> =
        date.atStartOfDay(zone).toInstant().toEpochMilli() to
            date.plusDays(1).atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()

    fun dayWindow(date: LocalDate, zone: ZoneId): Pair<Long, Long> =
        date.atStartOfDay(zone).toInstant().toEpochMilli() to date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    /**
     * The days [span] means on [today], first to last, both included (owner sentence 19.11.2026: „Welche Termine habe
     * ich diese Woche?“). „Diese Woche“ is what is still to come of it – today until Sunday; „nächste Woche“ is the
     * whole week from Monday to Sunday.
     */
    fun dates(span: AgendaSpan, today: LocalDate): ClosedRange<LocalDate> = when (span) {
        AgendaSpan.TODAY -> today..today
        AgendaSpan.TOMORROW -> today.plusDays(1)..today.plusDays(1)
        AgendaSpan.THIS_WEEK -> today..today.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY))
        AgendaSpan.NEXT_WEEK -> today.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY)).let { it..it.plusDays(6) }
    }

    /** Start of the first day to the start of the day after the last one: one read through the existing calendar path. */
    fun window(dates: ClosedRange<LocalDate>, zone: ZoneId): Pair<Long, Long> =
        dates.start.atStartOfDay(zone).toInstant().toEpochMilli() to
            dates.endInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    /** One [Day] per date of [dates], in order; an event over midnight is on both of its days. */
    fun days(events: List<ContinuumCalendarEvent>, dates: ClosedRange<LocalDate>, zone: ZoneId, now: Instant): List<Day> =
        generateSequence(dates.start) { it.plusDays(1) }.takeWhile { !it.isAfter(dates.endInclusive) }
            .map { day(events, it, zone, now) }.toList()

    /** „Donnerstag“, and with [today] given „heute“ / „morgen“ for those two days. */
    fun dayName(date: LocalDate, today: LocalDate? = null): String = when (date) {
        today -> "heute"
        today?.plusDays(1) -> "morgen"
        else -> date.format(DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN))
    }

    /** „am Donnerstag“, „heute“, „morgen“ – as part of a sentence. */
    fun onDay(date: LocalDate, today: LocalDate?): String = dayName(date, today).let { if (it == "heute" || it == "morgen") it else "am $it" }

    /**
     * The appointments of several days as they are spoken: what is still ahead, day by day, in order. [label]:
     * „Diese Woche“ or „Nächste Woche“. Events that are over are counted, not read out. Only what the calendar holds.
     */
    fun spokenWeek(days: List<Day>, label: String, today: LocalDate): String {
        val ahead = days.sumOf { it.ahead.size }
        val allDay = days.sumOf { it.allDay.size }
        val over = days.sumOf { it.ended }
        if (ahead == 0 && allDay == 0) return if (over == 0) "$label steht nichts im Kalender." else
            "$label steht nichts mehr an – " + (if (over == 1) "ein Termin ist" else ContinuumSystemCheck.count(over, "ein Termin", "Termine") + " sind") +
                " schon vorbei."
        return buildString {
            if (ahead > 0) {
                append(label).append(" hast du ").append(if (ahead == 1) "einen Termin" else ContinuumSystemCheck.count(ahead, "einen Termin", "Termine"))
                append(": ")
                append(days.filter { it.ahead.isNotEmpty() }.joinToString("; ") { day ->
                    onDay(day.date, today) + " " + ContinuumSystemCheck.list(day.ahead.map { spokenEntry(it, day.date, withPlace = true) })
                })
                append('.')
            }
            if (allDay > 0) {
                if (isNotEmpty()) append(' ')
                // An event over several days is named once, with its first day.
                append("Ganztägig: ").append(ContinuumSystemCheck.list(days.flatMap { day -> day.allDay.map { title(it) to day.date } }
                    .distinctBy { it.first }.take(MAX_WEEK_ALL_DAY).map { (name, date) -> name + " (" + dayName(date, today) + ")" })).append('.')
            }
            if (over > 0 && ahead > 0) append(if (over == 1) " Ein Termin ist schon vorbei." else
                " " + ContinuumSystemCheck.count(over, "Ein Termin", "Termine").replaceFirstChar { it.titlecase(Locale.GERMAN) } + " sind schon vorbei.")
        }
    }

    /** Card lines for several days: „Do 09:00 Zahnarzt“. */
    fun weekLines(days: List<Day>, limit: Int = 8): List<String> {
        val short = DateTimeFormatter.ofPattern("EE", Locale.GERMAN)
        val all = days.flatMap { day -> lines(day, Int.MAX_VALUE).map { day.date.format(short).trimEnd('.') + " " + it } }
        return if (all.size <= limit) all else all.take(limit - 1) + "+ ${all.size - (limit - 1)} weitere"
    }

    const val MAX_WEEK_ALL_DAY = 6

    fun title(event: ContinuumCalendarEvent): String = linkOrControl.replace(event.title, " ").replace(Regex("\\s+"), " ")
        .trim().trimEnd('.', ',', ';', ':').take(MAX_TITLE).trim().ifEmpty { "Termin ohne Titel" }

    private fun place(event: ContinuumCalendarEvent): String? = event.location?.let { linkOrControl.replace(it, " ") }
        ?.replace(Regex("\\s+"), " ")?.trim()?.take(MAX_TITLE)?.takeIf { it.isNotEmpty() }

    fun day(events: List<ContinuumCalendarEvent>, date: LocalDate, zone: ZoneId, now: Instant): Day {
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val allDay = events.filter { it.allDay }.filter { event ->
            // Android stores an all-day instance from UTC midnight to UTC midnight.
            val first = Instant.ofEpochMilli(event.startMillis).atZone(ZoneOffset.UTC).toLocalDate()
            val last = Instant.ofEpochMilli(maxOf(event.endMillis - 1, event.startMillis)).atZone(ZoneOffset.UTC).toLocalDate()
            !date.isBefore(first) && !date.isAfter(last)
        }.distinctBy { title(it) }.sortedBy { title(it).lowercase(Locale.GERMAN) }
        val nowMillis = now.toEpochMilli()
        val timed = events.filter { !it.allDay && it.endMillis >= it.startMillis }
            // A zero-length event belongs to the day of its start; any other must really overlap the day.
            .filter { if (it.endMillis == it.startMillis) it.startMillis in dayStart until dayEnd
                else it.startMillis < dayEnd && it.endMillis > dayStart }
            .distinctBy { Triple(title(it), it.startMillis, it.endMillis) }
            .sortedWith(compareBy({ it.startMillis }, { it.endMillis }, { title(it) }))
            .map { event ->
                Entry(event, Instant.ofEpochMilli(event.startMillis).atZone(zone).toLocalDateTime(),
                    Instant.ofEpochMilli(event.endMillis).atZone(zone).toLocalDateTime(),
                    over = event.endMillis <= nowMillis,
                    running = event.startMillis <= nowMillis && event.endMillis > nowMillis)
            }
        return Day(date, allDay, timed, timed.count { it.over })
    }

    /** „14 Uhr“, „9 Uhr 30“ – as it is spoken. */
    fun spokenTime(time: LocalTime): String = if (time.minute == 0) "${time.hour} Uhr" else "${time.hour} Uhr ${time.minute}"

    private val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)

    /** „Donnerstag, der 19. November“ */
    fun spokenDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEEE, 'der' d. MMMM", Locale.GERMAN))

    private fun spokenEntry(entry: Entry, date: LocalDate, withPlace: Boolean): String {
        val name = title(entry.event)
        val where = if (withPlace) place(entry.event)?.let { " ($it)" }.orEmpty() else ""
        val startsToday = entry.start.toLocalDate() == date
        return when {
            entry.running && entry.end.toLocalDate() == date -> "bis ${spokenTime(entry.end.toLocalTime())} läuft noch $name$where"
            entry.running -> "den ganzen Tag läuft noch $name$where"
            !startsToday -> "bis ${spokenTime(entry.end.toLocalTime())} $name$where"
            else -> "um ${spokenTime(entry.start.toLocalTime())} $name$where"
        }
    }

    /** The first two entries ahead whose times overlap, or null. */
    fun overlap(day: Day): Pair<Entry, Entry>? {
        val ahead = day.ahead.filter { it.event.endMillis > it.event.startMillis }
        for (i in ahead.indices) for (j in i + 1 until ahead.size)
            if (ahead[j].event.startMillis < ahead[i].event.endMillis) return ahead[i] to ahead[j]
        return null
    }

    /**
     * The day as one or two spoken sentences. [label]: „Heute“ or „Morgen“. [detailed]: with locations and without
     * the cap of [MAX_SPOKEN] – the answer to „Welche Termine habe ich heute?“.
     */
    fun spoken(day: Day, label: String, detailed: Boolean = false): String {
        if (day.empty) return "$label steht nichts im Kalender."
        val ahead = day.ahead
        return buildString {
            if (ahead.isEmpty() && day.timed.isNotEmpty()) {
                append("Für ").append(label.lowercase(Locale.GERMAN)).append(" steht nichts mehr an – ")
                append(if (day.ended == 1) "ein Termin ist" else ContinuumSystemCheck.count(day.ended, "ein Termin", "Termine") + " sind")
                append(" schon vorbei.")
            } else if (ahead.isNotEmpty()) {
                val shown = if (detailed) ahead else ahead.take(MAX_SPOKEN)
                append(label).append(" hast du ").append(if (ahead.size == 1) "einen Termin" else
                    ContinuumSystemCheck.count(ahead.size, "einen Termin", "Termine")).append(": ")
                append(ContinuumSystemCheck.list(shown.map { spokenEntry(it, day.date, detailed) }))
                if (shown.size < ahead.size) append(" – und ").append(
                    ContinuumSystemCheck.count(ahead.size - shown.size, "ein weiterer", "weitere"))
                append('.')
                if (day.ended > 0) append(if (day.ended == 1) " Ein Termin ist schon vorbei."
                    else " " + ContinuumSystemCheck.count(day.ended, "Ein Termin", "Termine").replaceFirstChar { it.titlecase(Locale.GERMAN) } +
                        " sind schon vorbei.")
                overlap(day)?.let { (first, second) ->
                    append(" Achtung: ").append(title(first.event)).append(" und ").append(title(second.event))
                        .append(" überschneiden sich.")
                }
            }
            if (day.allDay.isNotEmpty()) {
                if (isNotEmpty()) append(' ')
                append("Ganztägig: ").append(ContinuumSystemCheck.list(day.allDay.take(MAX_SPOKEN).map { title(it) })).append('.')
            }
        }
    }

    /** Timed events of [tomorrow] that start before noon, as one sentence; null when there is none. */
    fun spokenMorning(tomorrow: Day): String? {
        val early = tomorrow.timed.filter { it.start.toLocalDate() == tomorrow.date && it.start.toLocalTime().isBefore(LocalTime.NOON) }
        if (early.isEmpty()) return null
        val shown = early.take(2)
        return "Morgen früh: " + ContinuumSystemCheck.list(shown.map { "um ${spokenTime(it.start.toLocalTime())} ${title(it.event)}" }) +
            (if (early.size > shown.size) " – und " + ContinuumSystemCheck.count(early.size - shown.size, "ein weiterer Termin", "weitere Termine") else "") + "."
    }

    /** Up to [limit] card lines: „09:00 Zahnarzt“, „14:30–15:30 Besprechung“, „ganztägig Geburtstag“. */
    fun lines(day: Day, limit: Int = 4): List<String> {
        val all = day.allDay.map { "ganztägig " + title(it) } + day.timed.map { entry ->
            val start = if (entry.start.toLocalDate() == day.date) entry.start.toLocalTime().format(clock) else "…"
            val end = if (entry.end.toLocalDate() == day.date) entry.end.toLocalTime().format(clock) else "…"
            (if (entry.event.endMillis == entry.event.startMillis) start else "$start–$end") + " " + title(entry.event) +
                if (entry.over) " (vorbei)" else ""
        }
        return if (all.size <= limit) all else all.take(limit - 1) + "+ ${all.size - (limit - 1)} weitere"
    }
}
