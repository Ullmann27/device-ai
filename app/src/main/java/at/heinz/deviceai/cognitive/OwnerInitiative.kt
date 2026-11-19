package at.heinz.deviceai.cognitive

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * How much the app may suggest by itself (owner wish 19.11.2026: „Gesprächsinitiative in drei Stufen“).
 * [RESERVED]: no offer and no suggestion that was not asked for. [NORMAL]: at most one offer in a greeting and one
 * follow-up suggestion after a finished result. [ACTIVE]: also suggestions from the context (an appointment that
 * comes up). The level is a manner, like the tone: it grants no tool and starts nothing – every suggestion is a
 * sentence the owner answers.
 */
enum class OwnerInitiativeLevel(val label: String) {
    RESERVED("Zurückhaltend"), NORMAL("Normal"), ACTIVE("Aktiv");

    companion object {
        val DEFAULT = NORMAL
        fun parse(raw: String?): OwnerInitiativeLevel? = entries.firstOrNull { it.name == raw }
    }
}

/** Where a suggestion would be said. */
enum class OwnerSuggestionSlot {
    /** The one offer of a greeting. */
    GREETING,
    /** Right after a finished result, about that result. */
    FOLLOW_UP,
    /** From the context, not asked for by the turn before. */
    CONTEXT
}

/**
 * Every kind of suggestion the app can make, with the lowest level that allows it. A kind whose data the app does not
 * have yet ([CALENDAR_ENTRY], [OPEN_MAPS]) is a slot of the policy only: nothing provides it in the app today.
 */
enum class OwnerSuggestionKind(val minimum: OwnerInitiativeLevel, val slot: OwnerSuggestionSlot) {
    /** „Wir haben gestern Abend bei … aufgehört. Möchtest du dort weitermachen?“ */
    RESUME_WORK(OwnerInitiativeLevel.NORMAL, OwnerSuggestionSlot.GREETING),
    /** „Es sind drei Nachrichten offen. Soll ich sie mit dir durchgehen?“ */
    OPEN_MESSAGES(OwnerInitiativeLevel.NORMAL, OwnerSuggestionSlot.GREETING),
    /** The briefing lane's daily offer. */
    BRIEFING(OwnerInitiativeLevel.NORMAL, OwnerSuggestionSlot.GREETING),
    /** The briefing lane's „Soll ich A, B oder C?“ after a briefing, a system check or a warning. */
    NEXT_STEPS(OwnerInitiativeLevel.NORMAL, OwnerSuggestionSlot.FOLLOW_UP),
    /** „Soll ich die Datei öffnen?“ after a save. */
    OPEN_FILE(OwnerInitiativeLevel.NORMAL, OwnerSuggestionSlot.FOLLOW_UP),
    /** „Soll ich daraus einen PDF-Bericht machen?“ after a research result with several sources. */
    PDF_REPORT(OwnerInitiativeLevel.NORMAL, OwnerSuggestionSlot.FOLLOW_UP),
    /** An appointment of today that starts within the next hours, mentioned once. */
    APPOINTMENT(OwnerInitiativeLevel.ACTIVE, OwnerSuggestionSlot.CONTEXT),
    /** A deadline in a mail → a calendar entry. No provider in the app yet. */
    CALENDAR_ENTRY(OwnerInitiativeLevel.ACTIVE, OwnerSuggestionSlot.CONTEXT),
    /** An address in a message → the maps app. No provider in the app yet. */
    OPEN_MAPS(OwnerInitiativeLevel.ACTIVE, OwnerSuggestionSlot.CONTEXT)
}

/**
 * One suggestion a provider has for this moment. [text]: one sentence. [order]: the order an existing lane takes when
 * the owner says yes – null for a mere mention. [subject]: what it is about (an entry id, an appointment), so the same
 * thing is not suggested again and again.
 */
data class OwnerSuggestion(val kind: OwnerSuggestionKind, val text: String, val order: String? = null, val subject: String? = null)

/** A source of context suggestions. It reads real data of the app or returns null – it never invents an occasion. */
fun interface OwnerSuggestionProvider {
    fun suggestion(nowMillis: Long): OwnerSuggestion?
}

/** App-private storage of the level and of what was offered and declined. Fakes in host tests. */
interface OwnerInitiativeStore {
    var level: OwnerInitiativeLevel
    /** Bounded JSON of the policy's memory; null = none. */
    var memory: String?
}

class InMemoryOwnerInitiativeStore : OwnerInitiativeStore {
    override var level = OwnerInitiativeLevel.DEFAULT
    override var memory: String? = null
}

/**
 * The one policy every unsolicited sentence of the app passes: the greeting's offer, a follow-up suggestion after a
 * result, a context suggestion and the briefing lane's own offers. It decides from the owner's level, the caps per
 * hour and day, and what he declined – and it only ever answers „may this be said now?“; it says and starts nothing.
 *
 * Rules: nothing in [OwnerInitiativeLevel.RESERVED]; nothing while something runs; a kind he declined is not offered
 * again for [DECLINE_MILLIS], the very thing he declined not for [SUBJECT_DECLINE_MILLIS]; the same thing is offered at
 * most [MAX_REPEATS] times and not twice within [REPEAT_MILLIS]; and there is a cap per hour and per day.
 */
class OwnerInitiative(private val store: OwnerInitiativeStore, private val clock: () -> Long = System::currentTimeMillis) {
    private class Offered(val kind: String, val subject: String?, val at: Long, var accepted: Boolean)

    val level: OwnerInitiativeLevel get() = store.level

    fun setLevel(level: OwnerInitiativeLevel) { store.level = level }

    private fun load(): Pair<MutableList<Offered>, MutableMap<String, Long>> = runCatching {
        val root = JSONObject(store.memory ?: return@runCatching null)
        val offers = root.optJSONArray("offers") ?: JSONArray()
        val declines = root.optJSONObject("declines") ?: JSONObject()
        Pair((0 until offers.length()).map { offers.getJSONObject(it) }.map { row ->
            Offered(row.getString("k"), row.optString("s").ifEmpty { null }, row.getLong("t"), row.optBoolean("a"))
        }.toMutableList(), declines.keys().asSequence().associateWith { declines.getLong(it) }.toMutableMap())
    }.getOrNull() ?: Pair(mutableListOf(), mutableMapOf())

    private fun save(offers: List<Offered>, declines: Map<String, Long>) {
        val now = clock()
        store.memory = JSONObject().apply {
            put("offers", JSONArray().apply {
                offers.filter { now - it.at in 0..MEMORY_MILLIS }.takeLast(MAX_MEMORY).forEach { row ->
                    put(JSONObject().put("k", row.kind).put("t", row.at).put("a", row.accepted).apply { row.subject?.let { put("s", it) } })
                }
            })
            put("declines", JSONObject().apply { declines.filter { it.value > now }.entries.take(MAX_MEMORY).forEach { put(it.key, it.value) } })
        }.toString()
    }

    private fun key(kind: OwnerSuggestionKind, subject: String?): String = if (subject == null) kind.name else kind.name + ":" + subject.take(60)

    private fun caps(slot: OwnerSuggestionSlot): Pair<Int, Int> = when (slot) {
        OwnerSuggestionSlot.FOLLOW_UP -> if (level == OwnerInitiativeLevel.ACTIVE) 6 to 20 else 4 to 12
        else -> if (level == OwnerInitiativeLevel.ACTIVE) 3 to 10 else 2 to 6
    }

    /**
     * true = a suggestion of [kind] about [subject] may be said now. [running]: something is running or open – then
     * never. Reads only; [offered] is what counts it.
     */
    fun allows(kind: OwnerSuggestionKind, subject: String? = null, running: Boolean = false): Boolean {
        if (running || level == OwnerInitiativeLevel.RESERVED || level.ordinal < kind.minimum.ordinal) return false
        val now = clock()
        val (offers, declines) = load()
        if ((declines[kind.name] ?: 0L) > now) return false
        if (subject != null && (declines[key(kind, subject)] ?: 0L) > now) return false
        val recent = offers.filter { now - it.at in 0..DAY_MILLIS }
        if (subject != null) {
            // The same thing: not twice within a day's rhythm, and not again once he has let it pass twice.
            val same = offers.filter { it.kind == kind.name && it.subject == subject.take(60) }
            if (same.any { now - it.at in 0..REPEAT_MILLIS } || same.count { !it.accepted } >= MAX_REPEATS) return false
        }
        val sameSlot = recent.filter { runCatching { OwnerSuggestionKind.valueOf(it.kind).slot }.getOrNull().let { slot ->
            (slot == OwnerSuggestionSlot.FOLLOW_UP) == (kind.slot == OwnerSuggestionSlot.FOLLOW_UP) } }
        val (perHour, perDay) = caps(kind.slot)
        return sameSlot.count { now - it.at in 0..HOUR_MILLIS } < perHour && sameSlot.size < perDay
    }

    /** A suggestion of [kind] was said just now. */
    fun offered(kind: OwnerSuggestionKind, subject: String? = null) {
        val (offers, declines) = load()
        offers += Offered(kind.name, subject?.take(60), clock(), false)
        save(offers, declines)
    }

    /** The owner said yes to the last suggestion of [kind]. */
    fun accepted(kind: OwnerSuggestionKind, subject: String? = null) {
        val (offers, declines) = load()
        offers.lastOrNull { it.kind == kind.name && it.subject == subject?.take(60) }?.accepted = true
        save(offers, declines)
    }

    /**
     * The owner said no: the kind rests for a while, and this very thing for much longer. [restKind] = false: the
     * question was part of an answer he had asked for – only the thing itself is not offered again.
     */
    fun declined(kind: OwnerSuggestionKind, subject: String? = null, restKind: Boolean = true) {
        val (offers, declines) = load()
        val now = clock()
        if (restKind || subject == null) declines[kind.name] = now + DECLINE_MILLIS
        if (subject != null) declines[key(kind, subject)] = now + SUBJECT_DECLINE_MILLIS
        save(offers, declines)
    }

    /** true while the owner's no to [kind] still counts. */
    fun resting(kind: OwnerSuggestionKind): Boolean = (load().second[kind.name] ?: 0L) > clock()

    /** The policy's memory goes (level change to RESERVED and back does not need it; the settings offer it). */
    fun forget() { store.memory = null }

    companion object {
        const val HOUR_MILLIS = 60 * 60_000L
        const val DAY_MILLIS = 24 * HOUR_MILLIS
        const val DECLINE_MILLIS = DAY_MILLIS
        const val SUBJECT_DECLINE_MILLIS = 30 * DAY_MILLIS
        const val REPEAT_MILLIS = 20 * HOUR_MILLIS
        const val MAX_REPEATS = 2
        private const val MEMORY_MILLIS = 31 * DAY_MILLIS
        private const val MAX_MEMORY = 60
    }
}

/**
 * „Sei zurückhaltender“, „Du darfst mehr vorschlagen“, „Schlag mir nichts mehr vor“ – the owner's spoken switch for
 * [OwnerInitiativeLevel]. Closed vocabulary, whole utterance; anything else keeps its route.
 */
internal object OwnerInitiativeCommand {
    sealed interface Command {
        data class Set(val level: OwnerInitiativeLevel) : Command
        /** One step down or up from the current level. */
        data class Step(val up: Boolean) : Command
    }

    private const val SOFT = "(?:(?:bitte|ab jetzt|künftig|in zukunft|einfach|doch|mal|ruhig|auch|gern|gerne|ein|bisschen|bissl|" +
        "etwas|wenig|wieder|jetzt|von dir aus|von selbst|ungefragt|so|viel|ständig|dauernd) )*"
    private const val SUGGEST = "(?:vorschlagen|anbieten|vorschläge machen|von dir aus anbieten)"
    private val reserved = listOf(
        Regex("^${SOFT}schlag(?:e)? mir ${SOFT}nichts(?: mehr)? vor$"),
        Regex("^${SOFT}(?:mach|mache) mir ${SOFT}keine vorschläge(?: mehr)?$"),
        Regex("^${SOFT}keine vorschläge(?: mehr)?$"),
        Regex("^${SOFT}biet(?:e)? mir ${SOFT}nichts(?: mehr)? an$"),
        Regex("^${SOFT}(?:hör|höre) auf(?: mir)?(?: ständig| dauernd)?(?: was| etwas| dinge)? (?:vorzuschlagen|anzubieten)$"),
        Regex("^${SOFT}(?:sei|bleib|bleibe) ${SOFT}(?:ganz )?zurückhaltend$"),
        Regex("^${SOFT}(?:halt|halte) dich ${SOFT}(?:ganz )?zurück$"),
        Regex("^${SOFT}(?:nerv|nerve) (?:mich )?${SOFT}nicht(?: mehr)?(?: mit vorschlägen)?$"),
        Regex("^${SOFT}initiative ${SOFT}(?:auf )?zurückhaltend$"),
        Regex("^${SOFT}du (?:brauchst|musst|sollst) mir ${SOFT}nichts(?: mehr)? $SUGGEST$"))
    private val down = listOf(
        Regex("^${SOFT}(?:sei|bleib|bleibe|werde|werd) ${SOFT}zurückhaltender$"),
        Regex("^${SOFT}schlag(?:e)? mir ${SOFT}weniger vor$"),
        Regex("^${SOFT}(?:mach|mache) mir ${SOFT}weniger vorschläge$"),
        Regex("^${SOFT}weniger vorschläge$"),
        Regex("^${SOFT}(?:halt|halte) dich ${SOFT}mehr zurück$"),
        Regex("^${SOFT}nicht so viele vorschläge$"))
    private val normal = listOf(
        Regex("^${SOFT}initiative ${SOFT}(?:auf )?normal$"),
        Regex("^${SOFT}(?:schlag|schlage) mir ${SOFT}normal(?: viel)? vor$"),
        Regex("^${SOFT}vorschläge ${SOFT}(?:auf )?normal$"),
        Regex("^${SOFT}du (?:kannst|darfst) mir ${SOFT}wieder(?: was| etwas)? $SUGGEST$"),
        Regex("^${SOFT}schlag(?:e)? mir ${SOFT}wieder(?: was| etwas)? vor$"))
    private val up = listOf(
        Regex("^${SOFT}du (?:kannst|darfst|sollst) ${SOFT}(?:mir )?${SOFT}mehr $SUGGEST$"),
        Regex("^${SOFT}schlag(?:e)? ${SOFT}(?:mir )?${SOFT}mehr vor$"),
        Regex("^${SOFT}(?:mach|mache) ${SOFT}(?:mir )?${SOFT}mehr vorschläge$"),
        Regex("^${SOFT}mehr vorschläge$"),
        Regex("^${SOFT}(?:sei|werde|werd) ${SOFT}(?:aktiver|initiativer|proaktiver)$"),
        Regex("^${SOFT}(?:zeig|zeige) ${SOFT}mehr initiative$"))
    private val active = listOf(
        Regex("^${SOFT}initiative ${SOFT}(?:auf )?aktiv$"),
        Regex("^${SOFT}(?:sei|bleib|bleibe) ${SOFT}(?:aktiv|proaktiv)$"),
        Regex("^${SOFT}schlag(?:e)? mir ${SOFT}alles vor(?:,? was dir (?:auffällt|einfällt))?$"),
        Regex("^${SOFT}du (?:kannst|darfst) mir ${SOFT}alles $SUGGEST$"))

    fun parse(utterance: String, assistantName: String? = null): Command? {
        if (utterance.length !in 8..90 || utterance.any { it.isISOControl() } || utterance.trimEnd().endsWith("?")) return null
        val text = OwnerAppLookupIntent.stripAddress(utterance.trim(), assistantName).lowercase(Locale.GERMAN)
            .replace(Regex("[^\\p{L}\\p{N} ]+"), " ").replace(Regex("\\s+"), " ").trim()
            .split(' ').joinToString(" ") { if (it == "nix") "nichts" else it }
            // A closing „bitte“ or „danke“ changes nothing about the wish.
            .replace(Regex("(?: (?:bitte|danke))+$"), "")
        return when {
            reserved.any { it.matches(text) } -> Command.Set(OwnerInitiativeLevel.RESERVED)
            active.any { it.matches(text) } -> Command.Set(OwnerInitiativeLevel.ACTIVE)
            normal.any { it.matches(text) } -> Command.Set(OwnerInitiativeLevel.NORMAL)
            down.any { it.matches(text) } -> Command.Step(up = false)
            up.any { it.matches(text) } -> Command.Step(up = true)
            else -> null
        }
    }

    /** The level [command] leads to from [current]. */
    fun target(command: Command, current: OwnerInitiativeLevel): OwnerInitiativeLevel = when (command) {
        is Command.Set -> command.level
        is Command.Step -> OwnerInitiativeLevel.entries[(current.ordinal + if (command.up) 1 else -1)
            .coerceIn(0, OwnerInitiativeLevel.entries.lastIndex)]
    }

    /** What is said after the switch – what the level means, in one or two sentences. */
    fun reply(level: OwnerInitiativeLevel, changed: Boolean): String = when (level) {
        OwnerInitiativeLevel.RESERVED -> (if (changed) "In Ordnung, ich halte mich zurück" else "Ich halte mich schon zurück") +
            ": keine Angebote und keine Vorschläge von mir aus. Was du mir aufträgst, erledige ich wie immer. " +
            "Mit „Du darfst mehr vorschlagen“ änderst du das."
        OwnerInitiativeLevel.NORMAL -> (if (changed) "Gut, ich schlage wieder im normalen Maß vor" else "Das ist schon so eingestellt") +
            ": höchstens ein Angebot bei der Begrüßung und ein Vorschlag nach einem fertigen Ergebnis."
        OwnerInitiativeLevel.ACTIVE -> (if (changed) "Gern, ich schlage ab jetzt mehr vor" else "Mehr geht nicht – ich schlage schon aktiv vor") +
            ": auch Hinweise aus dem Zusammenhang, etwa einen Termin, der bald ansteht. Ich frage immer erst und mache nichts " +
            "von selbst. Mit „Sei zurückhaltender“ nimmst du es zurück."
    }
}
