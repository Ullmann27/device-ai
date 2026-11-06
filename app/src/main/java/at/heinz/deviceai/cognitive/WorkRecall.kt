package at.heinz.deviceai.cognitive

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * „JARVIS, wo waren wir gestern bei unserem Projekt?“ – answered from the [CockpitWorkJournal]: the time of each task,
 * the owner's request, the source, the state actually reached and what is still open. A cut-off or stopped run is never
 * reported as done; an empty period gets one short, targeted question instead of a guess. The answer names its origin:
 * remembering the work record – not model learning and not a confirmed owner fact.
 */
internal object WorkRecall {
    enum class Period { TODAY, YESTERDAY, RECENT }
    data class Query(val period: Period, val openOnly: Boolean)

    private const val S = "(?<![\\p{L}\\p{N}_])"
    private const val E = "(?![\\p{L}\\p{N}_])"
    private val ask = Regex(
        "(?iu)$S(wo\\s+waren\\s+wir|wo\\s+sind\\s+wir(\\s+stehen)?\\s+geblieben|woran\\s+(haben|hatten)\\s+wir\\s+gearbeitet|" +
            "was\\s+(haben|hatten)\\s+wir\\s+(gestern|heute|zuletzt|vorhin)\\s+gemacht|was\\s+ist\\s+(noch\\s+)?offen|" +
            "wie\\s+ist\\s+der\\s+(aktuelle\\s+)?stand)$E"
    )
    private val yesterday = Regex("(?iu)${S}gestern$E")
    private val today = Regex("(?iu)$S(heute|vorhin)$E")
    private val open = Regex("(?iu)${S}offen$E")

    fun parse(utterance: String): Query? {
        if (utterance.length !in 6..300 || utterance.any { it.isISOControl() }) return null
        if (!ask.containsMatchIn(utterance)) return null
        val period = when {
            yesterday.containsMatchIn(utterance) -> Period.YESTERDAY
            today.containsMatchIn(utterance) -> Period.TODAY
            else -> Period.RECENT
        }
        return Query(period, openOnly = open.containsMatchIn(utterance))
    }

    /** Episodes of [query]'s period, newest first, at most [limit]. */
    fun select(episodes: List<CockpitWorkJournal.Episode>, query: Query, now: Long, zone: TimeZone,
               limit: Int = 3): List<CockpitWorkJournal.Episode> {
        val (from, to) = window(query.period, now, zone)
        return episodes.filter { it.startedAt in from until to }
            .filter { !query.openOnly || CockpitWorkJournal.nextStep(it, now) != null }
            .sortedByDescending { it.startedAt }.take(limit)
    }

    fun answer(episodes: List<CockpitWorkJournal.Episode>, query: Query, now: Long, zone: TimeZone): String {
        val hits = select(episodes, query, now, zone)
        if (hits.isEmpty()) return when (query.period) {
            Period.YESTERDAY -> "Für gestern finde ich im Arbeitsverlauf keinen Auftrag. Meinst du einen anderen Tag – " +
                "oder soll ich die letzten Arbeiten zeigen?"
            Period.TODAY -> "Heute steht noch nichts im Arbeitsverlauf. Woran sollen wir anknüpfen?"
            Period.RECENT -> if (query.openOnly) "Im Arbeitsverlauf ist nichts mehr offen."
                else "Im Arbeitsverlauf steht noch nichts. Woran sollen wir anknüpfen?"
        }
        val head = when (query.period) {
            Period.YESTERDAY -> "Gestern"
            Period.TODAY -> "Heute"
            Period.RECENT -> "Zuletzt"
        }
        return "$head:\n" + hits.joinToString("\n") { line(it, now, zone) } +
            "\n(Aus dem Arbeitsverlauf dieses Geräts – Erinnerung, kein Modelllernen.)"
    }

    /** The same facts as [line], split for a surface: heading, source, state, correction, open step. */
    data class Block(val heading: String, val source: String?, val state: String, val correction: String?, val next: String?)

    fun block(e: CockpitWorkJournal.Episode, now: Long, zone: TimeZone) = Block(
        "${time(e.startedAt, now, zone)} · „${e.request}“",
        e.source?.let { name -> "Quelle: " + if (e.sourcePages > 0) "$name, ${pages(e.sourcePages)}" else name },
        "Stand: ${CockpitWorkJournal.stateText(e, now)}",
        e.correction?.let { "Deine Korrektur: „$it“" },
        CockpitWorkJournal.nextStep(e, now)?.let { "Offen: $it" })

    fun line(e: CockpitWorkJournal.Episode, now: Long, zone: TimeZone): String {
        val source = e.source?.let { name -> if (e.sourcePages > 0) "$name, ${pages(e.sourcePages)}" else name }
        val next = CockpitWorkJournal.nextStep(e, now)
        return "• ${time(e.startedAt, now, zone)}: „${e.request}“" +
            (source?.let { " – Quelle: $it" } ?: "") +
            " – Stand: ${CockpitWorkJournal.stateText(e, now)}" +
            (e.correction?.let { " – deine Korrektur: „$it“" } ?: "") +
            (next?.let { " – offen: $it" } ?: "")
    }

    private fun pages(n: Int) = if (n == 1) "1 Seite" else "$n Seiten"

    /** „heute 08:12“, „gestern 14:12“ or „5. Nov., 09:30“ – the same wording as the recall lines. */
    fun whenText(at: Long, now: Long, zone: TimeZone): String = time(at, now, zone)

    private fun time(at: Long, now: Long, zone: TimeZone): String {
        val day = dayStart(now, zone)
        val clock = SimpleDateFormat("HH:mm", Locale.GERMANY).apply { timeZone = zone }.format(Date(at))
        return when {
            at >= day -> "heute $clock"
            at >= dayStart(day - 1, zone) -> "gestern $clock"
            else -> SimpleDateFormat("d. MMM, HH:mm", Locale.GERMANY).apply { timeZone = zone }.format(Date(at))
        }
    }

    private fun window(period: Period, now: Long, zone: TimeZone): Pair<Long, Long> {
        val day = dayStart(now, zone)
        return when (period) {
            Period.TODAY -> day to Long.MAX_VALUE
            Period.YESTERDAY -> dayStart(day - 1, zone) to day
            Period.RECENT -> Long.MIN_VALUE to Long.MAX_VALUE
        }
    }

    /** Local midnight of the day containing [at] (handles daylight-saving days via the calendar, not 24 h math). */
    private fun dayStart(at: Long, zone: TimeZone): Long = Calendar.getInstance(zone, Locale.GERMANY).apply {
        timeInMillis = at
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
