package at.heinz.deviceai.briefing

import at.heinz.deviceai.cognitive.OwnerAppLookupIntent
import java.util.Locale

/**
 * What the last briefing and system check left for the stage: cards and, when the owner may choose a next step, the
 * offers. Immutable snapshots; the stage only reads. Nothing here is rendered by this package beyond [chat].
 */
internal object ContinuumBriefingBoard {
    enum class Kind(val code: String) {
        CALENDAR("calendar"), WEATHER("weather"), MESSAGES("messages"), TASKS("tasks"), NEWS("news"), SYSTEM("system")
    }
    enum class State(val code: String) { INFO("info"), ATTENTION("attention") }

    /** One card: at most [MAX_LINES] short lines of real data. */
    data class Card(val id: String, val kind: Kind, val title: String, val lines: List<String>, val state: State) {
        init { require(lines.size <= MAX_LINES) }
    }

    /**
     * A next step the owner may choose by voice or tap. [order] is an order an existing lane takes; choosing the
     * offer dispatches it through the owner input path as if he had said it.
     */
    data class Offer(val id: String, val kind: OfferKind, val label: String, val order: String)

    enum class OfferKind { APPOINTMENTS, MESSAGE, WORK, NEWS, DIAGNOSIS, WEATHER }

    const val MAX_LINES = 4
    const val MAX_OFFERS = 3

    @Volatile private var latestCards: List<Card> = emptyList()
    @Volatile private var latestOffers: List<Offer> = emptyList()
    private val changes = java.util.concurrent.atomic.AtomicLong()

    fun cards(): List<Card> = latestCards
    /** The offers that are open right now; empty once the owner chose, declined or went on to something else. */
    fun offers(): List<Offer> = latestOffers
    /** Grows with every change of [cards] or [offers]: a reader that saw this number has the current state. */
    fun revision(): Long = changes.get()

    internal fun publish(cards: List<Card>) { latestCards = cards.toList(); changes.incrementAndGet() }
    internal fun publishOffers(offers: List<Offer>) {
        val next = offers.take(MAX_OFFERS).toList()
        if (next != latestOffers) { latestOffers = next; changes.incrementAndGet() }
    }
    internal fun clear() { latestCards = emptyList(); latestOffers = emptyList(); changes.incrementAndGet() }

    /** Replaces the card of [card]'s kind, or adds it; the other cards stay. */
    internal fun put(card: Card) { latestCards = latestCards.filter { it.kind != card.kind } + card; changes.incrementAndGet() }

    /** The cards as plain chat text: a title line and its lines, one block per card. */
    fun chat(cards: List<Card>): String = cards.filter { it.lines.isNotEmpty() }.joinToString("\n\n") { card ->
        (if (card.state == State.ATTENTION) "! " else "") + card.title + "\n" + card.lines.joinToString("\n") { "· $it" }
    }
}

/** The question after a briefing or a warning, and the owner's answer to it („Entscheide du die Aktion“). */
internal object ContinuumNextSteps {
    sealed interface Choice {
        /** [index]: 0-based position among the offers. */
        data class Pick(val index: Int) : Choice
        /** „Ja“ to several offers: which one? */
        data object Ask : Choice
        data object Decline : Choice
    }

    /** „Soll ich A, B oder C?“ – null when there is nothing to offer. */
    fun question(offers: List<ContinuumBriefingBoard.Offer>): String? = when (offers.size) {
        0 -> null
        1 -> "Soll ich ${offers[0].label}?"
        else -> "Soll ich " + offers.dropLast(1).joinToString(", ") { it.label } + " oder " + offers.last().label + "?"
    }

    fun askWhich(offers: List<ContinuumBriefingBoard.Offer>): String = "Welchen Schritt meinst du – " +
        listOf("den ersten", "den zweiten", "den dritten").take(offers.size).let { ContinuumSystemCheck.list(it).replace(" und ", " oder ") } + "?"

    private val decline = setOf("nein", "nein danke", "nö", "noe", "ne", "nee", "nichts", "nichts davon", "keins", "keines",
        "keine", "keinen", "später", "spaeter", "jetzt nicht", "nicht jetzt", "lass", "lass gut sein", "danke nein",
        "passt schon", "nicht nötig", "nicht noetig", "gerade nicht", "erstmal nicht", "erst mal nicht")
    private val accept = setOf("ja", "ja bitte", "ja gern", "ja gerne", "gern", "gerne", "bitte", "mach das", "mach",
        "mach das bitte", "ok", "okay", "klar", "ja klar", "ja mach", "ja mach das", "unbedingt", "sehr gern", "sehr gerne",
        "jo", "joa", "ja genau", "genau")
    private val fillers = setOf("die", "der", "den", "das", "dem", "bitte", "nimm", "mach", "mal", "ja", "gern", "gerne",
        "dann", "zuerst", "nummer", "punkt", "option", "schritt", "ich", "will", "möchte", "moechte", "hätte", "haette",
        "lieber", "nur", "erstmal", "doch")
    private val verbs = setOf("vorlesen", "lesen", "öffnen", "oeffnen", "zeigen", "anzeigen", "zusammenfassen", "machen",
        "starten", "fertig", "weitermachen", "genauer", "einzeln", "im", "einzelnen", "von", "heute", "offene", "offenen",
        "offen", "lies", "öffne", "oeffne", "zeig", "zeige", "fass", "zusammen", "starte", "vor", "mir")
    private val ordinals = mapOf(
        0 to setOf("erste", "erster", "ersten", "erstes", "1", "eins", "erstens"),
        1 to setOf("zweite", "zweiter", "zweiten", "zweites", "2", "zwei", "zweitens"),
        2 to setOf("dritte", "dritter", "dritten", "drittes", "3", "drei", "drittens"),
        3 to setOf("vierte", "vierter", "vierten", "viertes", "4", "vier"),
        4 to setOf("fünfte", "fünfter", "fünften", "fünftes", "5", "fünf"))
    private val keywords: Map<String, List<ContinuumBriefingBoard.OfferKind>> = buildMap {
        fun words(kinds: List<ContinuumBriefingBoard.OfferKind>, vararg words: String) = words.forEach { this[it] = kinds }
        words(listOf(ContinuumBriefingBoard.OfferKind.APPOINTMENTS), "termin", "termine", "kalender")
        words(listOf(ContinuumBriefingBoard.OfferKind.MESSAGE), "nachricht", "mitteilung", "message")
        words(listOf(ContinuumBriefingBoard.OfferKind.WORK), "arbeit", "arbeiten", "entwurf", "zusammenfassung")
        // „Die Nachrichten“ is the news overview when that is offered, else the open messages.
        words(listOf(ContinuumBriefingBoard.OfferKind.NEWS, ContinuumBriefingBoard.OfferKind.MESSAGE), "nachrichten")
        words(listOf(ContinuumBriefingBoard.OfferKind.NEWS), "schlagzeilen", "news")
        words(listOf(ContinuumBriefingBoard.OfferKind.DIAGNOSIS), "diagnose", "systemdiagnose")
        words(listOf(ContinuumBriefingBoard.OfferKind.WEATHER), "wetter")
    }

    private fun normal(answer: String, assistantName: String?): String = OwnerAppLookupIntent.stripAddress(answer.trim(), assistantName)
        .lowercase(Locale.GERMAN).replace(Regex("[,;:]"), " ").replace(Regex("\\s+"), " ").trim().trimEnd('.', '!', '?').trim()

    /** true for a plain yes („ja“, „gern“, „mach das“). */
    fun isYes(answer: String, assistantName: String? = null): Boolean = normal(answer, assistantName) in accept

    /** true for a plain no („nein“, „später“, „jetzt nicht“). */
    fun isNo(answer: String, assistantName: String? = null): Boolean = normal(answer, assistantName) in decline

    /**
     * The owner's answer to the offered [kinds]. Null = not an answer to the offer at all: the sentence is whatever
     * it would be without the offer. Fail closed – one unknown word and nothing is chosen.
     */
    fun choose(answer: String, kinds: List<ContinuumBriefingBoard.OfferKind>, assistantName: String? = null): Choice? {
        if (kinds.isEmpty() || answer.length > 80) return null
        val text = normal(answer, assistantName)
        if (text.isEmpty()) return null
        if (text in decline) return Choice.Decline
        if (text in accept) return if (kinds.size == 1) Choice.Pick(0) else Choice.Ask
        val tokens = text.split(' ')
        val content = tokens.filter { it !in fillers }
        if (content.size == 1) ordinals.entries.firstOrNull { content[0] in it.value }?.let { (index, _) ->
            return if (index < kinds.size) Choice.Pick(index) else null
        }
        val named = content.filter { it in keywords }
        if (named.isEmpty() || content.any { it !in keywords && it !in verbs }) return null
        val picked = named.map { word -> keywords.getValue(word).firstOrNull { it in kinds } ?: return null }.distinct()
        return picked.singleOrNull()?.let { Choice.Pick(kinds.indexOf(it)) }
    }
}
