package at.heinz.deviceai.cognitive

import java.util.Locale

/**
 * The owner talks about Continuum's own internet access – „Wie mache ich das, dass du den Zugriff kriegst?“, „Du darfst
 * immer ins Internet“, „Internet aus“. Field finding 19.11.2026: such a sentence was answered by an old fixed line
 * („Tippe WEB RECHERCHE und nenne eine direkte HTTPS-Quelle … untrusted data“) that neither said how the access is set
 * nor how to change it.
 *
 * This object only recognises and words; nothing here acts. Widening the access is offered and happens only after the
 * owner's own „Ja“ in the next turn (a permission change needs an explicit confirmation); narrowing it is done at once.
 * A sentence that asks for something FROM the internet („Such im Internet nach …“) is not this and keeps its route.
 */
internal object OwnerInternetAccess {
    enum class Ask { ALLOW, ASK_EACH_TIME, TURN_OFF }

    /** What is true right now, as the app knows it. */
    data class State(val mode: InternetAccessMode, val cloudBrain: Boolean, val cloudWebSearch: Boolean)

    /** [offer] = the reply ends with the question whether to switch to „Immer an“; [set] = a narrowing done at once. */
    data class Reply(val text: String, val offer: Boolean = false, val set: InternetAccessMode? = null)

    private const val MAX_CHARS = 400
    private val net = Regex("internet|\\bweb\\b|\\bonline\\b|\\bnetz\\b")
    private val compound = Regex("internetrecherche|web-?recherche|internetzugriff|internetzugang|webzugriff|internetsuche")
    private val you = Regex("\\b(?:du|dir|dich|dein\\w*|continuum|jarvis)\\b")
    private val access = Regex("zugriff|zugang|freigabe|freigeb|freigib|erlaub|freischalt|darfst")
    private val switchOn = Regex("aktivier|einschalt|anschalt|erlaub|freigeb|freigib|freischalt|\\b(?:an|ein)\\b")
    private val refused = Regex("nicht (?:mehr )?ins internet|kein(?:en)? internet|ohne internet|nicht online|offline")
    private val off = Regex("^(?:bitte )?(?:(?:schalt\\w*|stell\\w*|mach\\w*)\\s+)?(?:das |dein\\w* |den |die )?" +
        "(?:internet\\w*|web\\w*)\\s+(?:bitte )?(?:aus|ab|ausschalten|abschalten)$")
    private val askEach = Regex("^(?:bitte )?(?:(?:schalt\\w*|stell\\w*|mach\\w*)\\s+)?(?:das |dein\\w* |den |die )?" +
        "(?:internet\\w*|web\\w*)\\s+(?:bitte )?(?:nur )?auf nachfrage$|" +
        "^(?:bitte )?frag(?:e)? (?:mich )?(?:bitte )?(?:vorher|immer|jedes mal),? bevor du ins internet gehst$")

    private fun normal(utterance: String): String = utterance.lowercase(Locale.GERMAN)
        .replace(Regex("[^\\p{L}\\p{N}\\- ]+"), " ").replace(Regex("\\s+"), " ").trim()

    /** What the owner wants about the access, or null when the sentence is about something else. */
    fun parse(utterance: String): Ask? {
        if (utterance.length !in 3..MAX_CHARS || utterance.any { it.isISOControl() && it != '\n' }) return null
        val said = normal(utterance)
        if (off.matches(said)) return Ask.TURN_OFF
        if (askEach.matches(said)) return Ask.ASK_EACH_TIME
        if (refused.containsMatchIn(said)) return null
        val aboutAccess = (net.containsMatchIn(said) && you.containsMatchIn(said) && access.containsMatchIn(said)) ||
            (compound.containsMatchIn(said) && switchOn.containsMatchIn(said))
        return if (aboutAccess) Ask.ALLOW else null
    }

    private fun name(mode: InternetAccessMode): String = when (mode) {
        InternetAccessMode.ALWAYS_ON -> "Immer an"
        InternetAccessMode.ASK -> "Auf Nachfrage"
        InternetAccessMode.OFF -> "Aus"
    }

    /** What still stands between „Immer an“ and looking things up unasked – depends on the selected brain. */
    private fun withAccess(state: State): String = when {
        state.cloudBrain && state.cloudWebSearch ->
            "Die Websuche über die Cloud ist auch eingeschaltet – ich sehe im Gespräch selbst nach, wenn ich etwas brauche."
        state.cloudBrain ->
            "Damit ich im Gespräch von mir aus nachsehe, fehlt noch ein Schalter: die Websuche über die Cloud. Sag " +
                "„Cloud-Websuche ein“ – jede Suche kostet dann einen kleinen Betrag aus deinem Claude-Guthaben. Ohne den " +
                "Schalter suche ich, sobald du es verlangst, zum Beispiel „Such im Internet nach …“."
        else -> "Bei Fragen zu aktuellen Themen sehe ich selbst nach; sonst sag einfach „Such im Internet nach …“."
    }

    fun reply(ask: Ask, state: State): Reply = when (ask) {
        Ask.TURN_OFF -> if (state.mode == InternetAccessMode.OFF) Reply("Mein Internetzugang ist schon aus.")
            else Reply("In Ordnung, mein Internetzugang ist jetzt aus. Mit „Du darfst wieder ins Internet“ schaltest du ihn wieder frei.",
                set = InternetAccessMode.OFF)
        Ask.ASK_EACH_TIME -> if (state.mode == InternetAccessMode.ASK) Reply("So ist es schon eingestellt: Ich frage vor jedem Blick ins Internet.")
            else Reply("In Ordnung, ich frage ab jetzt vor jedem Blick ins Internet.", set = InternetAccessMode.ASK)
        Ask.ALLOW -> if (state.mode == InternetAccessMode.ALWAYS_ON)
            Reply("Mein Internetzugang steht schon auf „Immer an“ – du musst nichts mehr freigeben. " + withAccess(state))
        else Reply("Im Moment steht mein Internetzugang auf „${name(state.mode)}“. Soll ich ihn auf „Immer an“ stellen? Dann sehe " +
            "ich bei Bedarf selbst nach, ohne jedes Mal zu fragen. Zurück geht es jederzeit mit „Internet auf Nachfrage“ oder " +
            "„Internet aus“.", offer = true)
    }

    /** After the owner's „Ja“ to the offer; [state] is the state after the switch. */
    fun allowed(state: State): String = "Erledigt – mein Internetzugang steht jetzt auf „Immer an“. " + withAccess(state)

    const val KEPT = "In Ordnung, dann bleibt es, wie es ist."

    private val yes = setOf("ja", "ja bitte", "ja mach", "ja mach das", "mach das", "mach", "gerne", "ja gerne", "okay", "ok",
        "in ordnung", "ja stell um", "stell um", "ja immer an", "immer an", "genau", "bitte", "jawohl", "ja klar", "klar")
    private val no = setOf("nein", "nein danke", "lieber nicht", "nicht", "lass", "lass es", "lass das", "nee", "ne", "abbrechen")

    /** true = yes, false = no, null = neither (the offer lapses and the turn keeps its ordinary route). */
    fun answer(utterance: String): Boolean? = normal(utterance).let { said ->
        when (said) { in yes -> true; in no -> false; else -> null }
    }
}
