package at.heinz.deviceai.cognitive

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * How long the owner has not been in touch, as code computes it from two real timestamps of the device clock –
 * never a model, never a guess. Owner wish (19.11.2026): „… oder misst die Zeit, ich habe mich schon 2 Stunden nicht
 * mehr gerührt.“
 */
internal sealed interface OwnerAbsence {
    /** At least [OwnerReturn.BRIEF_MILLIS] and less than an hour: „da bist du ja wieder“, no duration. */
    object Brief : OwnerAbsence
    /** An hour or more on the same day (or across midnight for less than [OwnerReturn.OVERNIGHT_HOURS] hours). */
    data class Hours(val minutes: Long) : OwnerAbsence
    /** Last in touch on the calendar day before. */
    object SinceYesterday : OwnerAbsence
    /** Last in touch [days] calendar days ago (2 or more). */
    data class Days(val days: Int) : OwnerAbsence
}

internal object OwnerReturn {
    /** Below this the owner was never away. */
    const val BRIEF_MILLIS = 5 * 60_000L
    /** From this pause on the app greets by itself when he comes back, or puts a clause in front of his first order. */
    const val RETURN_MILLIS = 30 * 60_000L
    /** A gap beyond this is no pause any more: no duration is mentioned. */
    const val MAX_PLAUSIBLE_MILLIS = 45L * 24 * 60 * 60_000L
    const val OVERNIGHT_HOURS = 6

    /**
     * The absence between [lastMillis] and [nowMillis] (wall clock), or null when nothing may be said about it:
     * no earlier interaction, a clock that went backwards, an absurd gap, or a pause too short to be one.
     */
    fun absence(lastMillis: Long?, nowMillis: Long, zone: ZoneId): OwnerAbsence? {
        if (lastMillis == null || lastMillis <= 0L) return null
        val gap = nowMillis - lastMillis
        if (gap < BRIEF_MILLIS || gap > MAX_PLAUSIBLE_MILLIS) return null
        val minutes = gap / 60_000L
        if (minutes < 60) return OwnerAbsence.Brief
        val days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(lastMillis).atZone(zone).toLocalDate(),
            Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()).toInt()
        return when {
            days >= 2 -> OwnerAbsence.Days(days)
            days == 1 && minutes >= OVERNIGHT_HOURS * 60L -> OwnerAbsence.SinceYesterday
            else -> OwnerAbsence.Hours(minutes)
        }
    }

    /**
     * The absence as it is said, rounded the way people round: „zwei Stunden“ (up to a quarter over), „gut zwei
     * Stunden“, „fast drei Stunden“ (from a quarter before), „seit gestern“, „seit drei Tagen“. Null for [OwnerAbsence.Brief].
     */
    fun spoken(absence: OwnerAbsence): String? = when (absence) {
        OwnerAbsence.Brief -> null
        is OwnerAbsence.Hours -> {
            val hours = (absence.minutes / 60).toInt()
            val rest = (absence.minutes % 60).toInt()
            when {
                rest < 15 -> hours(hours)
                rest < 45 -> "gut " + hours(hours)
                else -> "fast " + hours(hours + 1)
            }
        }
        OwnerAbsence.SinceYesterday -> "seit gestern"
        is OwnerAbsence.Days -> "seit " + number(absence.days) + " Tagen"
    }

    private fun hours(count: Int): String = if (count == 1) "eine Stunde" else number(count) + " Stunden"

    private val numbers = listOf("null", "eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun", "zehn",
        "elf", "zwölf")

    private fun number(value: Int): String = numbers.getOrNull(value) ?: value.toString()
}

/**
 * The owner greets („Hallo“, „Guten Morgen, Jarvis“, „Ich bin wieder da“) and nothing else: recognised here so the
 * reply is a personal greeting from the app itself – no model turn, no tool, no plan. A greeting with a question or
 * an order in it („Hallo, wie spät ist es?“) is not this: it keeps its route and is answered.
 *
 * The wording is deterministic and bounded: a fixed pool, chosen by a counter, never the same twice in a row. Every
 * sentence is true by construction – the time of day comes from the device clock, a duration only from
 * [OwnerReturn.absence], the name only from what the owner said himself.
 */
internal object OwnerGreeting {
    private const val HELLO = "(?:hallo|hallöchen|hallihallo|hi|hey|servus|moin|mahlzeit|grüß dich|grüss dich|" +
        "grüß gott|grüss gott|guten morgen|guten tag|guten abend)"
    private const val BACK = "(?:(?:ich )?bin (?:wieder |jetzt )*(?:da|zurück|hier)|da bin ich(?: wieder)?|ich bin wieder zurück)"
    private val greeting = Regex("^(?:$HELLO(?: $HELLO)?(?: $BACK)?|$BACK)$")

    fun isGreeting(utterance: String, assistantName: String? = null): Boolean {
        if (utterance.length !in 2..60 || utterance.any { it.isISOControl() }) return false
        val names = (listOf("jarvis", "continuum") + listOfNotNull(assistantName?.lowercase(Locale.GERMAN)))
            .flatMap { it.split(' ') }.filter { it.isNotBlank() }.toSet()
        val words = utterance.lowercase(Locale.GERMAN).replace(Regex("[^\\p{L}\\p{N} ]+"), " ").split(' ')
            .filter { it.isNotEmpty() && it !in names && it != "du" }
        return words.size in 1..8 && greeting.matches(words.joinToString(" "))
    }

    /** „Guten Morgen“ (5–10), „Guten Abend“ (18–22), otherwise „Hallo“. */
    fun timeOfDay(hour: Int): String = when (hour) {
        in 5..10 -> "Guten Morgen"
        in 18..22 -> "Guten Abend"
        else -> "Hallo"
    }

    /**
     * One greeting. [opening]: the greeting itself with everything that is a fact (name, pause). [closing]: its own
     * short question at the end – replaced when another module supplies a clause of its own. [index]: the wording used.
     */
    data class Said(val opening: String, val closing: String, val index: Int) {
        val text: String get() = listOf(opening, closing).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /**
     * The personal greeting when the owner gets in touch. [name]: as he gave it, or null. [absence]: how long he was
     * away, or null when nothing is known. [variation]: any counter; [previous]: the index used last time (-1 = none).
     */
    fun reply(name: String?, hour: Int, absence: OwnerAbsence?, variation: Int, previous: Int = -1): Said {
        val day = timeOfDay(hour)
        val away = absence?.let(OwnerReturn::spoken)
        // „Hallo Heinz“ / „Hallo“ – the same sentences with and without the name.
        val to = name?.let { " $it" }.orEmpty()
        val called = name?.let { ", $it" }.orEmpty()
        val pool: List<Pair<String, String>> = when {
            away != null -> listOf(
                "Hallo$to, wie geht's dir? Du hast dich ja $away nicht gerührt." to "",
                "Na$to, da bist du ja wieder. ${cap(away)} habe ich nichts von dir gehört." to "Was liegt an?",
                "$day$called! ${cap(away)} war Funkstille." to "Alles gut bei dir?")
            absence == OwnerAbsence.Brief -> listOf(
                "Da bist du ja wieder$called." to "Was gibt's?",
                "Na$to, wieder da?" to "Dann los.",
                "Hallo$to, schön, dass du wieder da bist." to "Was liegt an?")
            else -> listOf(
                "Hallo$to! Schön, dass du da bist." to "Was liegt an?",
                "Na$to, da bist du ja." to "Was gibt's?",
                "$day$called!" to "Womit fangen wir an?",
                "Grüß dich$called!" to "Schieß los.",
                (if (name != null) "Ah, $name!" else "Ah, da bist du!") to "Was hast du auf dem Herzen?")
        }
        var pick = ((variation % pool.size) + pool.size) % pool.size
        if (pool.size > 1 && pick == previous) pick = (pick + 1) % pool.size
        return Said(pool[pick].first, pool[pick].second, pick)
    }

    /**
     * One short clause in front of the answer when his first turn after a pause is already an order: the order is
     * not delayed by a round of small talk, and no question is asked that could take its place.
     */
    fun returnClause(name: String?, absence: OwnerAbsence?): String {
        val to = name?.let { " $it" }.orEmpty()
        val away = absence?.let(OwnerReturn::spoken)
        return when {
            away != null -> "Hallo$to – $away nichts von dir gehört."
            absence == OwnerAbsence.Brief -> "Hallo$to, da bist du ja wieder."
            else -> "Hallo$to!"
        }
    }

    private val failure = Regex("(?iu)(?<![\\p{L}])(?:fehler\\p{L}*|fehlgeschlagen|gescheitert|abgebrochen|abgelehnt|gestoppt|" +
        "verweigert|nicht\\s+(?:möglich|gefunden|bereit|gesendet|gespeichert|erreichbar|geklappt|ausgeführt|gestartet|erlaubt|" +
        "freigegeben|vorhanden|verfügbar|geladen|lesbar)|nichts\\s+(?:gefunden|gespeichert|gesendet|ausgeführt|geändert|gesucht)|" +
        "(?:gibt|gab)\\s+es\\s+nicht|(?:konnte|kann|darf)\\s+(?:ich\\s+)?(?:das\\s+|es\\s+|sie\\s+|dir\\s+)?(?:leider\\s+)?nicht|" +
        "leider|kein(?:e|en)?\\s+(?:treffer|zugriff|verbindung|internet\\p{L}*|modell\\p{L}*|pdf))(?![\\p{L}])")

    /**
     * false = [answer] asks the owner something (an approval, a choice, a question back) or reports a failure: such a
     * message stays exactly as it is, without a greeting in front of it.
     */
    fun clauseFits(answer: String): Boolean {
        val text = answer.trim()
        return text.length >= 2 && !text.endsWith("?") && !failure.containsMatchIn(text.take(400))
    }

    private fun cap(text: String): String = text.replaceFirstChar { it.titlecase(Locale.GERMAN) }
}

/**
 * „Begrüß mich nicht mehr“ / „Begrüß mich wieder“: the owner's own switch for the greeting after a pause, separate
 * from the tone. Closed forms only; „Begrüß Anna von mir“ or „Mach die Begrüßung kürzer“ keep their routes.
 */
internal object OwnerGreetingSwitchCommand {
    private const val SOFT = "(?:(?:bitte|ab jetzt|künftig|in zukunft|einfach|doch|mal|ruhig|auch) )*"
    private const val GREET = "begr(?:ü|ue)(?:ß|ss)(?:e|en)?"
    private const val NOUN = "begr(?:ü|ue)(?:ß|ss)ung"
    private val off = listOf(
        Regex("^$SOFT$GREET mich ${SOFT}nicht(?: mehr)?(?: von dir aus)?$"),
        Regex("^${SOFT}keine $NOUN(?: mehr)?$"),
        Regex("^$NOUN (?:${SOFT})?(?:aus|ab|weg)(?:schalten|stellen)?$"),
        Regex("^$NOUN (?:${SOFT})?(?:ausschalten|abschalten|abstellen|weglassen)$"),
        Regex("^$SOFT(?:schalt|schalte|stell|stelle|mach|mache) (?:die|deine) $NOUN ${SOFT}(?:aus|ab|weg)$"),
        Regex("^$SOFT(?:lass|lasse) (?:die|deine) $NOUN ${SOFT}(?:weg|sein|bleiben)$"),
        Regex("^du (?:brauchst|musst|sollst) mich ${SOFT}nicht(?: mehr)?(?: zu)? begr(?:ü|ue)(?:ß|ss)en$"),
        Regex("^${SOFT}h(?:ö|oe)r auf mich zu begr(?:ü|ue)(?:ß|ss)en$"))
    private val on = listOf(
        Regex("^$SOFT$GREET mich ${SOFT}wieder$"),
        Regex("^$NOUN (?:${SOFT})?(?:wieder )?(?:an|ein)(?:schalten|stellen)?$"),
        Regex("^$NOUN (?:${SOFT})?(?:wieder )?(?:anschalten|einschalten|anstellen)$"),
        Regex("^$SOFT(?:schalt|schalte|stell|stelle|mach|mache) (?:die|deine) $NOUN ${SOFT}(?:wieder )?(?:an|ein)$"),
        Regex("^du (?:kannst|darfst|sollst) mich ${SOFT}wieder begr(?:ü|ue)(?:ß|ss)en$"))

    /** true = on, false = off, null = not this command. */
    fun parse(utterance: String, assistantName: String? = null): Boolean? {
        if (utterance.length !in 8..80 || utterance.any { it.isISOControl() } || utterance.trimEnd().endsWith("?")) return null
        val text = OwnerAppLookupIntent.stripAddress(utterance.trim(), assistantName).lowercase(Locale.GERMAN)
            .replace(Regex("[^\\p{L}\\p{N} ]+"), " ").replace(Regex("\\s+"), " ").trim()
        return when {
            off.any { it.matches(text) } -> false
            on.any { it.matches(text) } -> true
            else -> null
        }
    }
}
