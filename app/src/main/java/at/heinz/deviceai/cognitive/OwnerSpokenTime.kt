package at.heinz.deviceai.cognitive

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * A stored moment as people say it: „vorhin“, „heute früh“, „gestern Abend“, „vorgestern“, „am Dienstag“, „vor gut
 * einer Woche“. Computed from two real timestamps of the device clock and nothing else – never more precise than the
 * data, and nothing at all when the two values cannot be an honest pair (no value, a clock that went backwards, an
 * absurd gap). Owner wish (19.11.2026): „Keine erfundenen Aussagen darüber, wann der Besitzer zuletzt aktiv war.“
 */
internal object OwnerSpokenTime {
    /** Beyond this nothing is said about a time: the gap is no memory any more. */
    const val MAX_MILLIS = 45L * 24 * 60 * 60_000L

    private val numbers = listOf("null", "eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun", "zehn",
        "elf", "zwölf")

    /** „drei“ for 3; digits beyond twelve. */
    fun number(value: Int): String = numbers.getOrNull(value) ?: value.toString()

    /** „Dienstag“. */
    fun weekdayName(day: DayOfWeek): String = weekday(day)

    private fun weekday(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "Montag"
        DayOfWeek.TUESDAY -> "Dienstag"
        DayOfWeek.WEDNESDAY -> "Mittwoch"
        DayOfWeek.THURSDAY -> "Donnerstag"
        DayOfWeek.FRIDAY -> "Freitag"
        DayOfWeek.SATURDAY -> "Samstag"
        DayOfWeek.SUNDAY -> "Sonntag"
    }

    /** The part of the day of [hour] as it follows „heute“ or „gestern“; null for the small hours. */
    private fun partOfDay(hour: Int): String? = when (hour) {
        in 5..8 -> "früh"
        in 9..11 -> "Vormittag"
        in 12..13 -> "Mittag"
        in 14..17 -> "Nachmittag"
        in 18..21 -> "Abend"
        in 22..23 -> "am späten Abend"
        else -> null
    }

    /**
     * When [atMillis] was, seen from [nowMillis] – or null when nothing may be said (see the class comment).
     * Lower-case, to stand inside a sentence: „gerade eben“, „vorhin“, „heute Vormittag“, „gestern Abend“,
     * „vorgestern“, „am Dienstag“, „vor einer Woche“ …
     */
    fun at(atMillis: Long?, nowMillis: Long, zone: ZoneId): String? {
        if (atMillis == null || atMillis <= 0L) return null
        val gap = nowMillis - atMillis
        if (gap < 0L || gap > MAX_MILLIS) return null
        val then = Instant.ofEpochMilli(atMillis).atZone(zone)
        val days = ChronoUnit.DAYS.between(then.toLocalDate(), Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()).toInt()
        return when {
            days == 0 && gap < 5 * 60_000L -> "gerade eben"
            days == 0 && gap < 60 * 60_000L -> "vorhin"
            days == 0 -> partOfDay(then.hour)?.let { "heute $it" } ?: "heute Nacht"
            days == 1 -> partOfDay(then.hour)?.let { "gestern $it" } ?: "gestern in der Nacht"
            days == 2 -> "vorgestern"
            days in 3..6 -> "am " + weekday(then.dayOfWeek)
            days == 7 -> "vor einer Woche"
            days in 8..10 -> "vor gut einer Woche"
            days in 11..13 -> "vor knapp zwei Wochen"
            days in 14..20 -> "vor zwei Wochen"
            days in 21..27 -> "vor drei Wochen"
            else -> "vor etwa einem Monat"
        }
    }

    /**
     * [at] with the time of day rounded to the half hour for today and yesterday – „gestern Abend gegen halb zehn“ –
     * where a person would still know it. Older moments keep the day alone.
     */
    fun atAbout(atMillis: Long?, nowMillis: Long, zone: ZoneId): String? {
        val day = at(atMillis, nowMillis, zone) ?: return null
        if (!day.startsWith("heute ") && !day.startsWith("gestern ")) return day
        val then = Instant.ofEpochMilli(atMillis!!).atZone(zone)
        // The rounded clock must not leave the part of the day it is said with („heute früh gegen neun“ is fine,
        // a jump over midnight is not).
        val minutes = then.hour * 60 + then.minute
        val rounded = ((minutes + 15) / 30) * 30
        if (rounded >= 24 * 60) return day
        return "$day gegen " + clock(rounded / 60, rounded % 60 == 30)
    }

    /** „neun“, „halb zehn“ – the twelve-hour words people use after „gegen“. */
    private fun clock(hour: Int, half: Boolean): String {
        fun word(h: Int): String = number(((h + 11) % 12) + 1)
        return if (half) "halb " + word(hour + 1) else word(hour)
    }

    /** The day [atMillis] lies on, relative to [nowMillis]: 0 today, 1 yesterday …; null when it is no honest pair. */
    fun daysAgo(atMillis: Long, nowMillis: Long, zone: ZoneId): Int? {
        if (atMillis <= 0L || nowMillis < atMillis) return null
        return ChronoUnit.DAYS.between(Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate(),
            Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()).toInt()
    }

    /** The weekday name in [text] („am Dienstag“, „dienstags“), or null. */
    fun weekdayIn(text: String): DayOfWeek? = DayOfWeek.entries.firstOrNull { day ->
        Regex("(?iu)(?<![\\p{L}])" + weekday(day) + "s?(?![\\p{L}])").containsMatchIn(text)
    }

    /** How many days ago the last [day] before today was (1..7), seen from [nowMillis]. */
    fun daysSince(day: DayOfWeek, nowMillis: Long, zone: ZoneId): Int {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).dayOfWeek
        val back = (today.value - day.value + 7) % 7
        return if (back == 0) 7 else back
    }
}
