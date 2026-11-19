package at.heinz.deviceai.cognitive

import java.util.Locale

/**
 * The tone the owner set with the current turn. It selects style and timing only; it never grants a
 * tool, changes routing or stores anything. Detection is lexical and deliberately conservative: when a
 * turn carries both a joke and a serious matter, the serious reading wins.
 */
enum class ConversationRegister { PLAYFUL, CASUAL, TASK, SERIOUS, DISTRESSED }

internal object ConversationStyle {
    /** Whole words only: a stem carries its own `\\p{L}*` where endings are meant („Klagenfurt“ is no lawsuit). */
    private fun words(vararg stems: String) = Regex(
        "(?iu)(?<![\\p{L}\\p{N}_])(?:" + stems.joinToString("|") + ")(?![\\p{L}\\p{N}_])")

    /** The owner signals strain. Humour and advice lists are out of place here. */
    private val distress = words("mir geht(?:'s|’s| es) (?:nicht gut|schlecht|mies|beschissen)", "ich kann nicht mehr",
        "ich schaff(?:e)? das nicht", "verzweifel\\p{L}*", "überforder\\p{L}*", "panik\\p{L}*", "ich hab(?:e)? angst",
        "ich bin am ende", "gestorben", "verstorben", "beerdigung", "trauer\\p{L}*", "weine\\p{L}*", "geweint",
        "einsam\\p{L}*", "schmerzen", "notfall", "notarzt", "unfall")
    /** Matters where a wrong or flippant answer costs something. */
    private val serious = words("kündigung\\p{L}*", "gekündigt", "arzt\\p{L}*", "ärztin", "diagnose\\p{L}*", "befund",
        "krankenhaus", "operation", "gericht", "gerichts\\p{L}*", "anwalt", "anwältin", "klage", "verklag\\p{L}*",
        "bescheid", "mahnung\\p{L}*", "inkasso\\p{L}*", "schulden", "kredit\\p{L}*", "überweisung\\p{L}*", "frist", "fristen",
        "vertrag", "verträge", "polizei", "strafanzeige", "anzeige erstatten", "versicherung\\p{L}*",
        "steuererklärung", "steuerbescheid", "finanzamt", "steuerberater\\p{L}*",
        "medikament\\p{L}*", "dosis", "scheidung", "sorgerecht", "testament")
    private val playful = Regex("(?iu)(?<![\\p{L}\\p{N}_])(?:haha\\p{L}*|hihi|hehe|lol|lach\\p{L}*|witz\\p{L}*|scherz\\p{L}*|" +
        "spa(?:ß|ss)\\p{L}*|bl(?:ö|oe)de frage|dumme frage|schm(?:ä|ae)h|du (?:nudel|vogel|held))|" +
        "[😀-😆😉😂🤣😜😝😛🙃😅😁]")
    /** A piece of work, not small talk: the existing detailed answer instruction stays in charge. */
    private val task = Regex("(?iu)^\\s*(?:bitte\\s+)?(?:schreib\\p{L}*|erstell\\p{L}*|verfass\\p{L}*|formulier\\p{L}*|" +
        "fass\\p{L}*|übersetz\\p{L}*|rechne\\p{L}*|berechne\\p{L}*|analysier\\p{L}*|vergleich\\p{L}*|liste\\p{L}*|" +
        "plane\\p{L}*|entwirf|entwerf\\p{L}*|korrigier\\p{L}*|recherchier\\p{L}*|such\\p{L}*|öffne\\p{L}*|zeig\\p{L}*|" +
        "prüf\\p{L}*|erklär\\p{L}* (?:mir )?(?:ausführlich|genau|im detail))(?![\\p{L}\\p{N}_])")

    fun detect(ownerTurn: String): ConversationRegister {
        val text = ownerTurn.trim()
        if (text.isEmpty()) return ConversationRegister.CASUAL
        return when {
            distress.containsMatchIn(text) -> ConversationRegister.DISTRESSED
            serious.containsMatchIn(text) -> ConversationRegister.SERIOUS
            text.length > LONG_TURN_CHARACTERS || task.containsMatchIn(text) -> ConversationRegister.TASK
            playful.containsMatchIn(text) -> ConversationRegister.PLAYFUL
            else -> ConversationRegister.CASUAL
        }
    }

    /**
     * Style block for the local model prompt, or "" when the existing instructions stay untouched
     * (a written work order). [spoken] is true while the hands-free conversation is active;
     * [maxSentences] is a hard spoken length hint (for example two while driving).
     *
     * The block describes how to talk, never what is true: it explicitly forbids invented facts for a
     * punchline and claimed feelings, and it yields to every safety and evidence rule in the prompt.
     */
    fun promptBlock(register: ConversationRegister, spoken: Boolean, maxSentences: Int? = null): String {
        if (!spoken && register == ConversationRegister.TASK) return ""
        val length = when {
            maxSentences != null -> "Höchstens ${maxSentences.coerceIn(1, 6)} kurze Sätze."
            spoken && register == ConversationRegister.TASK -> "Erst das Ergebnis in ein bis zwei Sätzen; Einzelheiten nur auf Nachfrage."
            spoken -> "Für diese gesprochene Runde gilt statt „ausführlich“: ein bis drei kurze Sätze, außer es wird ausdrücklich mehr verlangt."
            else -> "So kurz wie möglich, so lang wie nötig."
        }
        val tone = when (register) {
            ConversationRegister.PLAYFUL -> "Der Ton ist gerade locker: Eine kurze freche oder witzige Reaktion ist willkommen, " +
                "danach kommt die echte Antwort. Nie auf Kosten des Nutzers, und keine erfundenen Fakten für eine Pointe."
            ConversationRegister.CASUAL -> "Locker und direkt. Humor nur, wenn er sich von selbst ergibt."
            ConversationRegister.TASK -> "Sachlich und knapp."
            ConversationRegister.SERIOUS -> "Das Thema ist ernst: kein Humor. Ruhig und klar, zuerst die wichtigste Auskunft, " +
                "dann höchstens eine Rückfrage. Sag offen, was du nicht sicher weißt."
            ConversationRegister.DISTRESSED -> "Der Nutzer wirkt belastet: kein Humor, keine Liste mit Ratschlägen. " +
                "Geh zuerst in einem Satz auf das Gesagte ein und frag dann einmal offen nach."
        }
        val medium = if (spoken) "Du wirst vorgelesen: keine Aufzählungszeichen, keine Überschriften, keine Klammern, " +
            "Zahlen und Abkürzungen so, wie man sie spricht. " else ""
        return "\nGESPRÄCHSSTIL — betrifft nur die Form, nie Fakten, Belege oder Freigaben:\n" +
            "Rede per Du in natürlichem, gesprochenem Deutsch wie ein guter Freund am Telefon. " +
            "Keine Floskeln wie „Gerne helfe ich dir“, keine Wiederholung der Frage, wechselnde Satzanfänge. " +
            medium + tone + " " + length +
            " Du bist eine KI: Behaupte keine eigenen Gefühle, Erlebnisse oder Erinnerungen an ein Leben.\n"
    }

    private const val LONG_TURN_CHARACTERS = 400
}

/**
 * Short spoken signs of life while the model is still working (see [HumanSpeechTiming.TROUBLE_GAP_MS]).
 * People fill such a delay with a brief word instead of silence. Each cue is true by construction –
 * it is only spoken while a model turn is really running – and carries no content of the answer.
 */
internal class ConversationalLatencyCue {
    private var lastFirst = -1
    private var lastSecond = -1

    /** Cue number [index] (0 or 1) for this turn; the same wording is never used twice in a row. */
    fun next(index: Int, register: ConversationRegister, variation: Int): String? {
        val pool = when (index) {
            0 -> when (register) {
                ConversationRegister.PLAYFUL -> FIRST_PLAYFUL
                ConversationRegister.SERIOUS, ConversationRegister.DISTRESSED -> FIRST_CALM
                else -> FIRST_NEUTRAL
            }
            1 -> if (register == ConversationRegister.SERIOUS || register == ConversationRegister.DISTRESSED)
                SECOND_CALM else SECOND_NEUTRAL
            else -> return null
        }
        val previous = if (index == 0) lastFirst else lastSecond
        var pick = ((variation % pool.size) + pool.size) % pool.size
        if (pool.size > 1 && pick == previous) pick = (pick + 1) % pool.size
        if (index == 0) lastFirst = pick else lastSecond = pick
        return pool[pick]
    }

    companion object {
        val FIRST_NEUTRAL = listOf("Mhm, Moment.", "Kurz überlegen.", "Sekunde.", "Lass mich kurz schauen.")
        val FIRST_PLAYFUL = listOf("Hm, Moment.", "Oha, kurz überlegen.", "Wart, gleich.", "Na gut, Sekunde.")
        val FIRST_CALM = listOf("Einen Moment.", "Ich überlege kurz.", "Moment bitte.")
        val SECOND_NEUTRAL = listOf("Bin gleich so weit.", "Dauert noch einen Augenblick.", "Fast fertig.")
        val SECOND_CALM = listOf("Ich bin noch dran.", "Einen Augenblick noch.")

        /** Every cue wording, for checks that a cue is never mistaken for answer content. */
        val ALL: Set<String> = (FIRST_NEUTRAL + FIRST_PLAYFUL + FIRST_CALM + SECOND_NEUTRAL + SECOND_CALM).toSet()
    }
}
