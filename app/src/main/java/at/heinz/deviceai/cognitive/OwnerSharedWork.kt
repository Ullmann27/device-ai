package at.heinz.deviceai.cognitive

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** What kind of work an entry of the shared-work record is about. */
enum class SharedWorkKind { DOCUMENT, MISSION, RESEARCH, BRIEFING, SEND, STORAGE, CODING_AGENT, COMPUTER, CONVERSATION }

/** How far it really got. PAUSED: it waits or was cut off; STOPPED: the owner ended it; FAILED: the lane said so. */
enum class SharedWorkState { DONE, PAUSED, FAILED, STOPPED }

/** What the lane's typed result says was done – the verb of the sentence, never a guess about the content. */
enum class SharedWorkAction {
    OPENED, SAVED, DRAFTED, SUMMARISED, RESEARCHED, BRIEFED, SENT, PREPARED, CHECKED, CLEANED, SEQUENCE, CODED, OPERATED, TALKED
}

/**
 * One entry of the shared-work record: something substantial the owner and the app did together, as a lane's typed
 * outcome reported it. [title]: a short neutral name the app derived from that outcome – a file name exactly as the
 * lane shows it on the device, „3 Schritte“ – never model text; may be empty. [startedAt]/[endedAt]: device clock.
 * [provenance]: the lane that reported it. [resume]: the lane's own order sentence that takes the work up again, set
 * only where repeating it changes nothing outside the device and sends nothing; null = it can only be mentioned.
 * [pausedAt]: where it stopped („Schritt 2 von 3“), as the lane counted it.
 */
data class SharedWorkEntry(
    val id: String,
    val kind: SharedWorkKind,
    val action: SharedWorkAction,
    val title: String,
    val state: SharedWorkState,
    val startedAt: Long,
    val endedAt: Long,
    val provenance: String,
    val resume: String? = null,
    val pausedAt: String? = null
) {
    val unfinished: Boolean get() = state == SharedWorkState.PAUSED || state == SharedWorkState.STOPPED
}

/** App-private storage of the record as one bounded text. Fakes in host tests. */
interface OwnerSharedWorkStore {
    fun load(): String?
    fun save(text: String?)
}

class InMemoryOwnerSharedWorkStore : OwnerSharedWorkStore {
    private var text: String? = null
    override fun load(): String? = text
    override fun save(text: String?) { this.text = text }
}

/**
 * The shared-work record with time and provenance (owner wish 19.11.2026: „Wo waren wir gestern?“, a greeting that
 * knows where the work stopped). Bounded to the last [MAX_ENTRIES] entries, removable by the owner at any time, and
 * local: nothing here is sent anywhere by this class. It is a record of work, not a memory of facts about the owner –
 * and it only ever holds what a lane really reported, with the device clock's time.
 */
class OwnerSharedWorkRecord(private val store: OwnerSharedWorkStore, private val clock: () -> Long = System::currentTimeMillis) {
    private var cache: List<SharedWorkEntry>? = null

    /** All entries, newest first. */
    @Synchronized fun entries(): List<SharedWorkEntry> = (cache ?: read().also { cache = it }).sortedByDescending { it.endedAt }

    /**
     * Notes [entry]: a new one, or the new state of the entry with the same id (a mission that paused and then ended,
     * a draft that was saved). The first start time of an id is kept.
     */
    @Synchronized fun note(entry: SharedWorkEntry) = noteAll(listOf(entry))

    /** [note] for several entries with one write; an entry that is already there unchanged costs nothing. */
    @Synchronized fun noteAll(entries: List<SharedWorkEntry>) {
        val now = clock()
        var all = cache ?: read()
        val before = all
        entries.forEach { entry ->
            val old = all.firstOrNull { it.id == entry.id.take(MAX_ID) }
            val ended = entry.endedAt.takeIf { it > 0L } ?: now
            val noted = entry.copy(id = entry.id.take(MAX_ID), title = clean(entry.title, MAX_TITLE),
                provenance = clean(entry.provenance, MAX_PROVENANCE),
                resume = entry.resume?.let { clean(it, MAX_RESUME) }?.takeIf { it.isNotEmpty() },
                pausedAt = entry.pausedAt?.let { clean(it, MAX_TITLE) }?.takeIf { it.isNotEmpty() },
                startedAt = (old?.startedAt ?: entry.startedAt.takeIf { it > 0L } ?: ended).coerceAtMost(ended), endedAt = ended)
            if (noted != old) all = all.filter { it.id != noted.id } + noted
        }
        if (all != before) write(all.sortedBy { it.endedAt }.takeLast(MAX_ENTRIES)) else cache = all
    }

    /** „Vergiss, woran wir gearbeitet haben“: everything goes. Returns how many entries were removed. */
    @Synchronized fun clear(): Int {
        val count = (cache ?: read()).size
        cache = emptyList()
        store.save(null)
        return count
    }

    private fun write(entries: List<SharedWorkEntry>) {
        cache = entries
        store.save(JSONArray().apply { entries.forEach { put(encode(it)) } }.toString())
    }

    private fun read(): List<SharedWorkEntry> = runCatching {
        val rows = JSONArray(store.load() ?: return emptyList())
        (0 until rows.length()).mapNotNull { index -> runCatching { decode(rows.getJSONObject(index)) }.getOrNull() }
            .takeLast(MAX_ENTRIES)
    }.getOrDefault(emptyList())

    private fun encode(e: SharedWorkEntry) = JSONObject().apply {
        put("id", e.id); put("kind", e.kind.name); put("action", e.action.name); put("title", e.title)
        put("state", e.state.name); put("startedAt", e.startedAt); put("endedAt", e.endedAt); put("provenance", e.provenance)
        e.resume?.let { put("resume", it) }; e.pausedAt?.let { put("pausedAt", it) }
    }

    private fun decode(o: JSONObject) = SharedWorkEntry(o.getString("id"), SharedWorkKind.valueOf(o.getString("kind")),
        SharedWorkAction.valueOf(o.getString("action")), o.optString("title"), SharedWorkState.valueOf(o.getString("state")),
        o.getLong("startedAt"), o.getLong("endedAt"), o.optString("provenance"),
        o.optString("resume").ifEmpty { null }, o.optString("pausedAt").ifEmpty { null })

    companion object {
        const val MAX_ENTRIES = 40
        const val MAX_TITLE = 80
        const val MAX_RESUME = 240
        const val MAX_PROVENANCE = 40
        const val MAX_ID = 80

        /** One line, no control characters, no quotation marks of its own, bounded. */
        fun clean(text: String, max: Int): String = text.replace(Regex("[\\p{Cntrl}„“”\"«»]+"), " ").replace(Regex("\\s+"), " ")
            .trim().take(max).trim()
    }
}

/**
 * The entries as sentences. Every phrase is a fixed template around the entry's own fields: the verb comes from the
 * typed [SharedWorkAction], the name from [SharedWorkEntry.title], the time from the two clock values.
 */
internal object OwnerSharedWorkWording {
    private fun quoted(title: String): String = if (title.isBlank()) "" else " „$title“"

    /** What was done, to follow „haben wir“: „das PDF „Argon4.pdf“ geöffnet“. Only for a finished entry. */
    fun did(e: SharedWorkEntry): String = when (e.action) {
        SharedWorkAction.OPENED -> if (e.title.isBlank()) "ein PDF geöffnet" else "das PDF${quoted(e.title)} geöffnet"
        SharedWorkAction.SAVED -> if (e.title.isBlank()) "eine Datei gespeichert" else "die Datei${quoted(e.title)} gespeichert"
        SharedWorkAction.DRAFTED -> if (e.title.isBlank()) "eine Zusammenfassung erstellt" else "die Zusammenfassung von${quoted(e.title)} erstellt"
        SharedWorkAction.SUMMARISED -> "die Nachrichten zusammengefasst"
        SharedWorkAction.RESEARCHED -> if (e.title.isBlank()) "etwas recherchiert" else "zu${quoted(e.title)} recherchiert"
        SharedWorkAction.BRIEFED -> "dein Briefing durchgegangen"
        SharedWorkAction.SENT -> if (e.title.isBlank()) "eine Nachricht gesendet" else "eine Nachricht an ${e.title} gesendet"
        SharedWorkAction.PREPARED -> (if (e.title.isBlank()) "eine Nachricht vorbereitet" else "eine Nachricht an ${e.title} vorbereitet") +
            " – gesendet habe ich sie nicht"
        SharedWorkAction.CHECKED -> "den Speicher geprüft"
        SharedWorkAction.CLEANED -> "den Speicher aufgeräumt"
        SharedWorkAction.SEQUENCE -> "eine Auftragsfolge" + (if (e.title.isBlank()) "" else " (${e.title})") + " erledigt"
        SharedWorkAction.CODED -> "einen Programmierauftrag" + (if (e.title.isBlank()) "" else quoted(e.title)) + " abgeschlossen"
        SharedWorkAction.OPERATED -> if (e.title.isBlank()) "am Computer gearbeitet" else "am Computer mit ${e.title} gearbeitet"
        SharedWorkAction.TALKED -> "uns unterhalten"
    }

    /** The work as a dative phrase, to follow „bei“ or „mit“: „dem PDF „Argon4.pdf““. */
    fun topic(e: SharedWorkEntry): String = when (e.action) {
        SharedWorkAction.OPENED -> if (e.title.isBlank()) "einem PDF" else "dem PDF${quoted(e.title)}"
        SharedWorkAction.SAVED -> if (e.title.isBlank()) "einer Datei" else "der Datei${quoted(e.title)}"
        SharedWorkAction.DRAFTED -> if (e.title.isBlank()) "einer Zusammenfassung" else "der Zusammenfassung von${quoted(e.title)}"
        SharedWorkAction.SUMMARISED -> "den Nachrichten"
        SharedWorkAction.RESEARCHED -> if (e.title.isBlank()) "einer Recherche" else "der Recherche zu${quoted(e.title)}"
        SharedWorkAction.BRIEFED -> "deinem Briefing"
        SharedWorkAction.SENT, SharedWorkAction.PREPARED -> if (e.title.isBlank()) "einer Nachricht" else "der Nachricht an ${e.title}"
        SharedWorkAction.CHECKED, SharedWorkAction.CLEANED -> "der Speicherprüfung"
        SharedWorkAction.SEQUENCE -> "der Auftragsfolge" + (if (e.title.isBlank()) "" else " (${e.title})")
        SharedWorkAction.CODED -> "dem Programmierauftrag" + quoted(e.title)
        SharedWorkAction.OPERATED -> if (e.title.isBlank()) "dem Computer-Auftrag" else "dem Computer-Auftrag mit ${e.title}"
        SharedWorkAction.TALKED -> "unserem Gespräch"
    }

    /** The whole clause that follows the time: „haben wir … geöffnet“, „haben wir bei … aufgehört“. */
    fun clause(e: SharedWorkEntry): String = when (e.state) {
        SharedWorkState.DONE -> "haben wir " + did(e)
        SharedWorkState.PAUSED -> "haben wir bei " + topic(e) + " aufgehört" + (e.pausedAt?.let { " – $it" } ?: "")
        SharedWorkState.STOPPED -> "haben wir bei " + topic(e) + " abgebrochen" + (e.pausedAt?.let { " – $it" } ?: "")
        SharedWorkState.FAILED -> "hat es mit " + topic(e) + " nicht geklappt"
    }

    /** The kind as one neutral German word, for lists and for the cloud block. */
    fun kindLabel(kind: SharedWorkKind): String = when (kind) {
        SharedWorkKind.DOCUMENT -> "Dokument"
        SharedWorkKind.MISSION -> "Auftragsfolge"
        SharedWorkKind.RESEARCH -> "Recherche"
        SharedWorkKind.BRIEFING -> "Briefing"
        SharedWorkKind.SEND -> "Nachricht"
        SharedWorkKind.STORAGE -> "Speicher"
        SharedWorkKind.CODING_AGENT -> "Programmierauftrag"
        SharedWorkKind.COMPUTER -> "Computer-Auftrag"
        SharedWorkKind.CONVERSATION -> "Gespräch"
    }

    fun stateLabel(state: SharedWorkState): String = when (state) {
        SharedWorkState.DONE -> "erledigt"
        SharedWorkState.PAUSED -> "unterbrochen"
        SharedWorkState.FAILED -> "nicht geklappt"
        SharedWorkState.STOPPED -> "abgebrochen"
    }
}

/**
 * „Wo waren wir?“, „Was haben wir gestern gemacht?“, „Mach da weiter“, „Vergiss, woran wir gearbeitet haben“ – the
 * owner's sentences about the shared work. Whole-utterance and fail closed: a sentence with anything else in it („Wo
 * waren wir, öffne dann das PDF“) is not this and keeps its route.
 */
internal object OwnerSharedWorkCommand {
    enum class Period { TODAY, YESTERDAY, BEFORE_YESTERDAY, WEEKDAY, RECENT }

    sealed interface Command {
        /** [day]: only for [Period.WEEKDAY]. [list]: he asked what was done, not only where it stopped. */
        data class Recall(val period: Period, val day: java.time.DayOfWeek? = null, val list: Boolean = false) : Command
        data class Resume(val period: Period, val day: java.time.DayOfWeek? = null) : Command
        /** „Nein, das davor“ after a recall answer. */
        data object Earlier : Command
        /** „Nein, ich meinte heute“ after a recall answer. */
        data class Instead(val period: Period, val day: java.time.DayOfWeek? = null) : Command
        data object Forget : Command
    }

    // Spoken forms are folded first: „warn“ → „waren“, „ham“ → „haben“, „nix“ → „nichts“.
    private val folds = listOf("warn" to "waren", "warma" to "waren wir", "ham" to "haben", "hamma" to "haben wir",
        "nix" to "nichts", "gmacht" to "gemacht", "weitermachen" to "weiter machen", "stehengeblieben" to "stehen geblieben")
    private const val LEAD = "(?:(?:na|also|so|ok|okay|sag|sag mal|hey|und|dann|bitte|jetzt|nochmal|noch mal|kurz|mal) )*"
    private const val WHEN = "(?:gestern|heute|vorhin|zuletzt|vorgestern|letztes mal|beim letzten mal|" +
        "am (?:montag|dienstag|mittwoch|donnerstag|freitag|samstag|sonntag))"
    private const val TAIL = "(?: (?:nochmal|noch mal|eigentlich|gleich|bitte|denn|jetzt|überhaupt))*"
    /** Little words people put after „wir“: „wo waren wir denn stehen geblieben“. */
    private const val MID = "(?: (?:denn|eigentlich|nochmal|noch mal|jetzt|gerade|grad|da|so))*"
    private const val SOFT = "(?:(?:mal|bitte|einfach|jetzt|gleich|dann) )*"
    private val where = listOf(
        Regex("^${LEAD}wo waren wir$MID(?: $WHEN)?$MID(?: (?:stehen geblieben|stehen|dran|gewesen))?$TAIL$"),
        Regex("^${LEAD}wo sind wir$MID(?: $WHEN)?$MID(?: stehen)? geblieben$TAIL$"),
        Regex("^${LEAD}wo haben wir$MID(?: $WHEN)?$MID aufgehört$TAIL$"),
        Regex("^${LEAD}wo (?:stehen|stehn) wir$MID$TAIL$"),
        Regex("^${LEAD}wobei waren wir$MID(?: $WHEN)?$TAIL$"))
    private val what = listOf(
        Regex("^${LEAD}was haben wir$MID(?: $WHEN)?$MID(?: alles)? gemacht$TAIL$"),
        Regex("^${LEAD}was hatten wir$MID(?: $WHEN)?$MID(?: alles)? gemacht$TAIL$"),
        Regex("^${LEAD}woran haben wir$MID(?: $WHEN)?$MID gearbeitet$TAIL$"),
        Regex("^${LEAD}woran hatten wir$MID(?: $WHEN)?$MID gearbeitet$TAIL$"),
        Regex("^${LEAD}was war(?: $WHEN)?(?: so)? los$TAIL$"))
    private val resume = listOf(
        Regex("^${LEAD}mach(?:e)? $SOFT(?:da|dort|damit|daran)(?: bitte)? weiter$TAIL$"),
        Regex("^${LEAD}(?:lass uns )?(?:da|dort) weiter machen$TAIL$"),
        Regex("^${LEAD}weiter mit dem von $WHEN$TAIL$"),
        Regex("^${LEAD}mach(?:e)?(?: bitte)?(?: da| dort)? weiter(?: mit dem von $WHEN|,? wo wir(?: $WHEN)? (?:waren|aufgehört haben|stehen geblieben sind))$TAIL$"),
        Regex("^${LEAD}weiter,? wo wir(?: $WHEN)? (?:waren|aufgehört haben|stehen geblieben sind)$TAIL$"),
        Regex("^${LEAD}(?:lass uns|machen wir) (?:da|dort) weiter,? wo wir(?: $WHEN)? (?:waren|aufgehört haben|stehen geblieben sind)$TAIL$"),
        Regex("^${LEAD}mach(?:e)? weiter,? wo wir(?: $WHEN)? (?:waren|aufgehört haben|stehen geblieben sind)$TAIL$"))
    private val earlier = listOf(
        Regex("^(?:nein |nee |ne |nicht das |nicht die |nicht den )?(?:ich meinte |ich meine )?(?:das|die|den|der) (?:davor|vorher|andere|anderen)$"),
        Regex("^(?:und )?(?:was war )?davor$"),
        Regex("^(?:nein |nee |ne )?nicht das,? (?:sondern )?das (?:davor|andere)$"))
    private val instead = Regex("^(?:nein |nee |ne )?(?:ich meinte |ich meine |sondern )($WHEN)$")
    private val forget = listOf(
        Regex("^${LEAD}vergiss(?: bitte)?,? woran wir(?: $WHEN)? gearbeitet haben$"),
        Regex("^${LEAD}vergiss(?: bitte)?,? was wir(?: $WHEN)?(?: zusammen)? gemacht haben$"),
        Regex("^${LEAD}lösch(?:e)?(?: bitte)? (?:den|unseren|meinen) (?:arbeitsstand|arbeitsverlauf der gemeinsamen arbeit)$"),
        Regex("^${LEAD}vergiss(?: bitte)? (?:unseren|den) arbeitsstand$"),
        Regex("^${LEAD}vergiss(?: bitte)? unsere gemeinsame arbeit$"))

    private fun normal(utterance: String, assistantName: String?): String {
        val text = OwnerAppLookupIntent.stripAddress(utterance.trim(), assistantName).lowercase(Locale.GERMAN)
            .replace(Regex("[^\\p{L}\\p{N} ]+"), " ").replace(Regex("\\s+"), " ").trim()
        return text.split(' ').joinToString(" ") { word -> folds.firstOrNull { it.first == word }?.second ?: word }
    }

    private fun period(text: String): Pair<Period, java.time.DayOfWeek?> {
        OwnerSpokenTime.weekdayIn(text)?.let { return Period.WEEKDAY to it }
        return when {
            Regex("(?<![\\p{L}])vorgestern(?![\\p{L}])").containsMatchIn(text) -> Period.BEFORE_YESTERDAY to null
            Regex("(?<![\\p{L}])gestern(?![\\p{L}])").containsMatchIn(text) -> Period.YESTERDAY to null
            Regex("(?<![\\p{L}])(?:heute|vorhin)(?![\\p{L}])").containsMatchIn(text) -> Period.TODAY to null
            else -> Period.RECENT to null
        }
    }

    /** [afterRecall]: the app's last answer was a recall – only then „das davor“ and „ich meinte heute“ are these. */
    fun parse(utterance: String, assistantName: String? = null, afterRecall: Boolean = false): Command? {
        if (utterance.length !in 4..90 || utterance.any { it.isISOControl() }) return null
        val text = normal(utterance, assistantName)
        if (text.isEmpty() || text.split(' ').size > 14) return null
        if (forget.any { it.matches(text) }) return Command.Forget
        val (period, day) = period(text)
        if (where.any { it.matches(text) }) return Command.Recall(period, day, list = false)
        if (what.any { it.matches(text) }) return Command.Recall(period, day, list = true)
        if (resume.any { it.matches(text) }) return Command.Resume(period, day)
        if (afterRecall) {
            if (earlier.any { it.matches(text) }) return Command.Earlier
            instead.matchEntire(text)?.let { return period(it.groupValues[1]).let { (p, d) -> Command.Instead(p, d) } }
        }
        return null
    }
}

/**
 * The answers to [OwnerSharedWorkCommand] from the record – deterministic, no model. Every sentence about a time or
 * a piece of work is built from one entry; with no entry there is no such sentence.
 */
internal object OwnerSharedWorkRecall {
    const val NOTHING = "Dazu habe ich nichts festgehalten."
    /** How old an unfinished entry may be to be offered again at a greeting. */
    const val RESUME_OFFER_MILLIS = 7L * 24 * 60 * 60_000L

    data class Answer(val text: String, val entries: List<SharedWorkEntry>, val resumable: SharedWorkEntry?)

    /** The entries [period] asks for, newest first. */
    fun select(entries: List<SharedWorkEntry>, period: OwnerSharedWorkCommand.Period, day: java.time.DayOfWeek?, now: Long,
               zone: ZoneId): List<SharedWorkEntry> {
        val known = entries.filter { OwnerSpokenTime.at(it.endedAt, now, zone) != null }.sortedByDescending { it.endedAt }
        fun daysAgo(e: SharedWorkEntry) = OwnerSpokenTime.daysAgo(e.endedAt, now, zone)
        return when (period) {
            OwnerSharedWorkCommand.Period.TODAY -> known.filter { daysAgo(it) == 0 }
            OwnerSharedWorkCommand.Period.YESTERDAY -> known.filter { daysAgo(it) == 1 }
            OwnerSharedWorkCommand.Period.BEFORE_YESTERDAY -> known.filter { daysAgo(it) == 2 }
            OwnerSharedWorkCommand.Period.WEEKDAY -> day?.let { wanted ->
                val back = OwnerSpokenTime.daysSince(wanted, now, zone)
                known.filter { daysAgo(it) == back || (back == 7 && daysAgo(it) == 0 &&
                    Instant.ofEpochMilli(now).atZone(zone).dayOfWeek == wanted) }
            } ?: emptyList()
            OwnerSharedWorkCommand.Period.RECENT -> known
        }
    }

    private fun cap(text: String): String = text.replaceFirstChar { it.titlecase(Locale.GERMAN) }

    private fun periodWord(period: OwnerSharedWorkCommand.Period, day: java.time.DayOfWeek?): String = when (period) {
        OwnerSharedWorkCommand.Period.TODAY -> "heute"
        OwnerSharedWorkCommand.Period.YESTERDAY -> "gestern"
        OwnerSharedWorkCommand.Period.BEFORE_YESTERDAY -> "vorgestern"
        OwnerSharedWorkCommand.Period.WEEKDAY -> day?.let(OwnerSpokenTime::weekdayName) ?: "diesen Tag"
        OwnerSharedWorkCommand.Period.RECENT -> "die letzte Zeit"
    }

    /** One entry as a sentence with its real time. [variation] changes the frame, never the facts. */
    fun sentence(e: SharedWorkEntry, now: Long, zone: ZoneId, variation: Int = 0): String {
        val time = OwnerSpokenTime.atAbout(e.endedAt, now, zone) ?: return ""
        val clause = OwnerSharedWorkWording.clause(e)
        return when (((variation % 3) + 3) % 3) {
            0 -> cap(time) + " " + clause + "."
            1 -> "Das Letzte war $time: Da " + clause + "."
            else -> "Wir waren zuletzt $time dran – da " + clause + "."
        }
    }

    /** The entry „Mach da weiter“ means among [hits]: the newest one that can be taken up again, unfinished first. */
    fun resumable(hits: List<SharedWorkEntry>): SharedWorkEntry? =
        hits.firstOrNull { it.unfinished && it.resume != null } ?: hits.firstOrNull()?.takeIf { it.resume != null }

    fun recall(entries: List<SharedWorkEntry>, command: OwnerSharedWorkCommand.Command.Recall, now: Long, zone: ZoneId,
               variation: Int = 0, skip: Int = 0): Answer {
        val hits = select(entries, command.period, command.day, now, zone).drop(skip)
        if (hits.isEmpty()) {
            if (skip > 0) return Answer("Davor habe ich nichts festgehalten.", emptyList(), null)
            // Nothing in the asked period: say so, and name the newest entry there is – with its own real time.
            val last = select(entries, OwnerSharedWorkCommand.Period.RECENT, null, now, zone).firstOrNull()
            if (last == null || command.period == OwnerSharedWorkCommand.Period.RECENT) return Answer(NOTHING, emptyList(), null)
            val time = OwnerSpokenTime.atAbout(last.endedAt, now, zone)
            return Answer("Für ${periodWord(command.period, command.day)} habe ich nichts festgehalten. Das Letzte war $time: Da " +
                OwnerSharedWorkWording.clause(last) + ".", emptyList(), null)
        }
        val first = hits.first()
        val shown = if (command.list) hits.take(3) else listOf(first)
        val text = buildString {
            append(sentence(first, now, zone, variation))
            shown.drop(1).forEachIndexed { index, e ->
                val time = OwnerSpokenTime.atAbout(e.endedAt, now, zone)
                append(if (index == 0) " Davor, $time, " else " Und $time ").append(OwnerSharedWorkWording.clause(e)).append('.')
            }
            if (command.list && hits.size > shown.size) append(" Davor war noch mehr; frag einfach nach.")
            // Unfinished work that is not the entry just named is mentioned once, so it is not lost.
            if (!command.list) hits.drop(1).firstOrNull { it.unfinished }?.let { open ->
                append(" Offen ist außerdem noch etwas: ").append(cap(OwnerSpokenTime.at(open.endedAt, now, zone) ?: "zuletzt"))
                    .append(' ').append(OwnerSharedWorkWording.clause(open)).append('.')
            }
        }
        val resumable = first.takeIf { it.resume != null && (it.unfinished || !command.list) }
        return Answer(if (resumable != null && first.unfinished) "$text Soll ich dort weitermachen?" else text, shown, resumable)
    }

    /** What is said when work is taken up again: where it stopped, then that it goes on. */
    fun resuming(e: SharedWorkEntry, now: Long, zone: ZoneId): String {
        val time = OwnerSpokenTime.at(e.endedAt, now, zone)
        val where = when {
            e.state == SharedWorkState.DONE -> "Das war ${time ?: "zuletzt"}: Da " + OwnerSharedWorkWording.clause(e) + "."
            else -> cap(time ?: "zuletzt") + " " + OwnerSharedWorkWording.clause(e) + "."
        }
        return "$where Ich mache dort weiter."
    }

    /** A piece of work that can only be mentioned: it is said, and that nothing is repeated. */
    fun notResumable(e: SharedWorkEntry, now: Long, zone: ZoneId): String {
        val time = OwnerSpokenTime.atAbout(e.endedAt, now, zone)
        val why = if (e.kind == SharedWorkKind.SEND) "Eine Nachricht schicke ich nicht von mir aus noch einmal." else
            "Das kann ich nicht von selbst wieder aufnehmen."
        return cap(time ?: "zuletzt") + " " + OwnerSharedWorkWording.clause(e) + ". $why Sag mir einfach, was ich jetzt tun soll."
    }

    /** The newest unfinished entry that may be offered at a greeting, or null. */
    fun offerable(entries: List<SharedWorkEntry>, now: Long, zone: ZoneId): SharedWorkEntry? {
        val sorted = entries.sortedByDescending { it.endedAt }
        val open = sorted.firstOrNull { it.unfinished && it.resume != null && it.kind != SharedWorkKind.SEND } ?: return null
        if (now - open.endedAt !in 0..RESUME_OFFER_MILLIS || OwnerSpokenTime.at(open.endedAt, now, zone) == null) return null
        // The same work finished later: nothing is left to take up.
        val finishedLater = sorted.any { it.endedAt > open.endedAt && it.state == SharedWorkState.DONE && it.kind == open.kind &&
            it.title.equals(open.title, ignoreCase = true) && it.title.isNotBlank() }
        return open.takeUnless { finishedLater }
    }

    /** The resume offer of a greeting: „Wir haben gestern Abend bei … aufgehört. Möchtest du dort weitermachen?“ */
    fun offer(e: SharedWorkEntry, now: Long, zone: ZoneId, variation: Int = 0): String? {
        val time = OwnerSpokenTime.at(e.endedAt, now, zone) ?: return null
        val topic = OwnerSharedWorkWording.topic(e)
        val stop = if (e.state == SharedWorkState.STOPPED) "abgebrochen" else "aufgehört"
        val where = e.pausedAt?.let { " – $it" } ?: ""
        return when (((variation % 3) + 3) % 3) {
            0 -> "Wir haben $time bei $topic $stop$where. Möchtest du dort weitermachen?"
            1 -> "${cap(time)} haben wir bei $topic $stop$where. Soll ich dort wieder ansetzen?"
            else -> "Offen ist noch etwas von $time: Wir haben bei $topic $stop$where. Machen wir da weiter?"
        }
    }
}
