package at.heinz.deviceai.cognitive

import java.time.ZonedDateTime
import java.util.Locale

/**
 * Device-clock grounding for time/date questions.
 *
 * Code39 had no reference time anywhere in the local prompt, so "heute",
 * "morgen" or "wie spät" were pure model guesses, and in ASK/ALWAYS_ON mode a
 * plain clock question could be treated as a current-web question. This object
 * answers only an exact, whole-sentence local clock question from the device
 * clock and provides a compact anchor line for the local prompt. No network,
 * no location, no tool, no persistence. Other places ("in Tokio"), negations,
 * arithmetic or compound tasks are deliberately NOT matched.
 */
internal object LocalClockIntent {
    private val weekdays = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")
    private val months = listOf("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli",
        "August", "September", "Oktober", "November", "Dezember")
    private val prefix = """(?:(?:sag|sage)\s+(?:mir\s+)?(?:bitte\s+)?,?\s*|weißt\s+du\s*,?\s*|kannst\s+du\s+mir\s+sagen\s*,?\s*|bitte\s*,?\s*)?"""
    private val now = """(?:\s+(?:jetzt|gerade|eigentlich|denn|bitte|heute))*"""
    private val questions = listOf(
        """wie\s+spät\s+ist\s+es$now""",
        """wie\s+spät\s+es\s+(?:jetzt\s+|gerade\s+)?ist""",
        """wie\s?viel\s+uhr\s+(?:ist\s+es|haben\s+wir)$now""",
        """welche\s+uhrzeit\s+(?:ist\s+es|haben\s+wir)$now""",
        """(?:die\s+)?uhrzeit""",
        """den\s+wievielten\s+haben\s+wir$now""",
        """welcher\s+tag\s+ist\s+heute""",
        """welches\s+datum\s+(?:ist\s+heute|haben\s+wir)$now""",
        """der\s+wievielte\s+ist\s+heute""",
        """welchen\s+wochentag\s+haben\s+wir$now""",
        """welcher\s+wochentag\s+ist\s+heute""",
        """was\s+ist\s+heute\s+für\s+ein\s+tag""",
        """welches\s+datum\s+ist\s+es$now"""
    ).map { Regex("""(?iu)^$prefix(?:$it)$""") }

    fun isClockQuestion(message: String): Boolean {
        if (message.length !in 1..120 || message.any { it.isISOControl() }) return false
        val normalized = message.trim().replace(Regex("""\s+"""), " ")
            .trimEnd(' ', '?', '!', '.')
        // Exactly one sentence: "Wie spät ist es? Und lösche ..." is a compound task.
        if (normalized.any { it == '?' || it == '!' || it == '.' || it == ';' }) return false
        return questions.any { it.matches(normalized) }
    }

    private fun two(value: Int) = value.toString().padStart(2, '0')

    private fun germanDate(now: ZonedDateTime) =
        "${weekdays[now.dayOfWeek.value - 1]}, ${now.dayOfMonth}. ${months[now.monthValue - 1]} ${now.year}"

    fun answer(now: ZonedDateTime): String =
        "Laut Geräteuhr ist es ${two(now.hour)}:${two(now.minute)} Uhr, ${germanDate(now)} " +
            "(Zeitzone ${now.zone.id}, UTC${now.offset.id.replace("Z", "+00:00")})."

    fun promptAnchor(now: ZonedDateTime): String =
        String.format(Locale.ROOT, "Referenzzeit laut Geräteuhr: %04d-%02d-%02d (%s) %02d:%02d, Zeitzone %s, UTC%s. " +
            "Beziehe heute/morgen/gestern/jetzt darauf; für andere Orte keine Zeit raten.\n",
            now.year, now.monthValue, now.dayOfMonth, weekdays[now.dayOfWeek.value - 1],
            now.hour, now.minute, now.zone.id, now.offset.id.replace("Z", "+00:00"))
}
