package at.heinz.deviceai.cognitive

import java.util.Locale

/**
 * Owner wish (19.11.2026): „… dass sie sich mit mir wirklich wie, wenn wir beste Freunde werden, unterhaltet und nicht
 * so förmlich“, „als würde sie mich schon seit 30 Jahren kennen“. The owner chooses how he is talked to:
 * [PERSONAL] (default – he asked for it, and „Sprich wieder sachlich“ is one sentence away) or [FACTUAL], which is
 * exactly the manner the app had before this setting existed.
 *
 * The tone is a manner of speaking and nothing else: it grants no tool, changes no routing and never is a source of
 * facts. Both brains – the on-device model and Claude – take it from [OwnerToneStyle], so switching the brain does
 * not switch the personality.
 */
enum class OwnerConversationTone(val label: String) {
    PERSONAL("Persönlich"), FACTUAL("Sachlich");

    companion object {
        val DEFAULT = PERSONAL
        fun parse(raw: String?): OwnerConversationTone? = entries.firstOrNull { it.name == raw }
    }
}

/** „Sprich persönlich mit mir“ / „Sprich wieder sachlich“. Closed vocabulary; anything else keeps its route. */
internal object OwnerToneCommand {
    private val personal = setOf("persönlich", "persönlicher", "locker", "lockerer", "freundschaftlich",
        "freundschaftlicher", "vertraut", "vertrauter")
    private val factual = setOf("sachlich", "sachlicher", "nüchtern", "nüchterner", "förmlich", "förmlicher",
        "formell", "formeller", "neutral", "neutraler", "distanziert", "distanzierter")
    private val stiff = setOf("steif", "gestelzt", "hölzern")
    private val chummy = setOf("kumpelhaft", "flapsig", "salopp")
    private val less = setOf("nicht", "weniger")
    private val talkVerbs = setOf("sprich", "sprech", "sprichst", "sprechen", "rede", "red", "redest", "reden",
        "unterhalte", "unterhalt", "unterhalten", "sei", "bleib", "bleibe", "werde", "werd")
    private val toneNouns = setOf("gesprächston", "ton", "tonfall", "umgangston")
    private val friend = Regex("(?<![\\p{L}])wie (?:ein |mit einem |einem )(?:gute[rn] |alte[rn] |beste[rn] |richtige[rn] )?(?:freund|kumpel|spezi)(?![\\p{L}])")
    private val fillers = setOf("bitte", "mit", "mir", "zu", "wieder", "ab", "jetzt", "sofort", "künftig", "immer",
        "mal", "doch", "ruhig", "kannst", "könntest", "darfst", "du", "einfach", "etwas", "ein", "bisschen", "wenig",
        "ganz", "so", "mehr", "den", "auf", "stell", "stelle", "setz", "setze", "schalt", "schalte", "mach", "mache",
        "um", "dich", "in", "zukunft", "von", "an", "nur", "noch", "lieber", "auch", "und", "ja", "dann", "wie",
        "einem", "einen", "guter", "guten", "alter", "alten", "bester", "besten", "richtiger", "richtigen", "freund",
        "kumpel", "spezi")

    fun parse(utterance: String, assistantName: String? = null): OwnerConversationTone? {
        if (utterance.length !in 6..120 || utterance.any { it.isISOControl() } || utterance.trimEnd().endsWith("?") &&
            !utterance.trimStart().lowercase(Locale.GERMAN).startsWith("kannst") &&
            !utterance.trimStart().lowercase(Locale.GERMAN).startsWith("könntest")) return null
        val text = OwnerAppLookupIntent.stripAddress(utterance.trim(), assistantName).lowercase(Locale.GERMAN)
            .replace(Regex("[^\\p{L}\\p{N} ]+"), " ").replace(Regex("\\s+"), " ").trim()
        val words = text.split(' ').filter { it.isNotEmpty() }
        if (words.size !in 2..14) return null
        if (words.none { it in talkVerbs } && words.none { it in toneNouns }) return null
        val known = personal + factual + stiff + chummy + less + talkVerbs + toneNouns + fillers
        if (words.any { it !in known }) return null
        val likeAFriend = friend.containsMatchIn(text)
        // „wie“ and the friend words are vocabulary of the friend phrase only.
        if (!likeAFriend && words.any { it == "wie" || it == "freund" || it == "kumpel" || it == "spezi" }) return null
        var towardPersonal = if (likeAFriend) 1 else 0
        var towardFactual = 0
        words.forEachIndexed { index, word ->
            val lessened = (index >= 1 && words[index - 1] in less) || (index >= 2 && words[index - 1] == "so" && words[index - 2] in less)
            when {
                word in personal || word in chummy -> if (lessened) towardFactual++ else if (word in personal) towardPersonal++ else return null
                word in factual || word in stiff -> if (lessened) towardPersonal++ else if (word in factual) towardFactual++ else return null
            }
        }
        return when {
            towardPersonal > 0 && towardFactual == 0 -> OwnerConversationTone.PERSONAL
            towardFactual > 0 && towardPersonal == 0 -> OwnerConversationTone.FACTUAL
            else -> null
        }
    }
}

/**
 * „Nenn mich Heinz“ – how the owner tells the app his name, and how he takes it back. The name is only ever what
 * he said himself; nothing here guesses one. A statement of his name („Ich heiße Heinz Ullmann“) addresses him by
 * the first name; an explicit wish („Nenn mich Chef“) is used as he said it.
 */
internal object OwnerNameCommand {
    sealed interface Command {
        /** [name]: how he wants to be addressed. [statement]: his own sentence, kept as provenance. */
        data class Set(val name: String, val statement: String) : Command
        object Forget : Command
    }

    private const val WORD = "[\\p{L}][\\p{L}'’-]{1,29}"
    private const val NAME = "($WORD(?:\\s+$WORD){0,2})"
    private const val SOFT = "(?:bitte\\s+|ab\\s+jetzt\\s+|einfach\\s+|ruhig\\s+|gern(?:e)?\\s+|künftig\\s+|doch\\s+)*"
    private val wishes = listOf(
        Regex("(?iu)^(?:bitte\\s+)?nenn(?:e|st)?\\s+mich\\s+$SOFT$NAME$"),
        Regex("(?iu)^du\\s+(?:kannst|darfst|sollst)\\s+mich\\s+$SOFT$NAME\\s+nennen$"),
        Regex("(?iu)^sag(?:e|st)?\\s+$SOFT$NAME\\s+zu\\s+mir$"))
    private val statements = listOf(
        Regex("(?iu)^ich\\s+hei(?:ß|ss)e\\s+$NAME$"),
        Regex("(?iu)^mein\\s+(?:vor|ruf)?name\\s+(?:ist|lautet)\\s+$NAME$"))
    private val forget = listOf(
        Regex("(?iu)^(?:bitte\\s+)?vergiss\\s+(?:bitte\\s+)?meinen\\s+namen(?:\\s+(?:wieder|bitte))*$"),
        Regex("(?iu)^(?:bitte\\s+)?l(?:ö|oe)sch(?:e)?\\s+(?:bitte\\s+)?meinen\\s+namen(?:\\s+(?:wieder|bitte))*$"),
        Regex("(?iu)^(?:bitte\\s+)?nenn(?:e)?\\s+mich\\s+(?:bitte\\s+)?nicht\\s+mehr\\s+$WORD(?:\\s+$WORD)?$"),
        Regex("(?iu)^(?:bitte\\s+)?sprich\\s+mich\\s+(?:bitte\\s+)?nicht\\s+mehr\\s+mit\\s+(?:meinem\\s+)?namen\\s+an$"))
    /** Words that are never a name: a sentence that only looks like the pattern keeps its route. */
    private val notAName = setOf("so", "das", "es", "nicht", "nichts", "wie", "was", "wer", "bitte", "mal", "anders",
        "wieder", "einfach", "ruhig", "gut", "willkommen", "dich", "dir", "du", "mir", "mich", "ich", "er", "sie",
        "wir", "ihr", "der", "die", "den", "dem", "ein", "eine", "einen", "kein", "keine", "mehr", "jetzt", "dann",
        "doch", "nur", "auch", "noch", "schon", "immer", "nie", "hier", "dort", "da", "und", "oder", "aber", "wenn",
        "weil", "dass", "ob", "zu", "mit", "von", "für", "auf", "an", "in", "im", "um", "bei", "nach", "vor", "über",
        "müde", "fertig", "bereit", "sicher", "froh", "krank", "gesund", "allein", "zuhause", "unterwegs", "heute",
        "morgen", "gestern", "irgendwie", "irgendwas", "egal", "beim", "namen", "name")

    fun parse(utterance: String, assistantName: String? = null): Command? {
        if (utterance.length !in 8..120 || utterance.any { it.isISOControl() }) return null
        val text = OwnerAppLookupIntent.stripAddress(utterance.trim(), assistantName).trim().trimEnd('.', '!', ' ').trim()
        if (text.endsWith("?")) return null
        if (forget.any { it.matches(text) }) return Command.Forget
        wishes.firstNotNullOfOrNull { it.matchEntire(text) }?.let { match ->
            return name(match.groupValues[1], firstOnly = false)?.let { Command.Set(it, utterance.trim().take(160)) }
        }
        statements.firstNotNullOfOrNull { it.matchEntire(text) }?.let { match ->
            return name(match.groupValues[1], firstOnly = true)?.let { Command.Set(it, utterance.trim().take(160)) }
        }
        return null
    }

    /** The name a confirmed owner fact states („Ich heiße Heinz“), or null. [facts] are the owner's own sentences. */
    fun fromFacts(facts: List<String>): String? = facts.asSequence().take(200).mapNotNull { fact ->
        val text = fact.trim().trimEnd('.', '!', ' ').trim()
        statements.firstNotNullOfOrNull { it.matchEntire(text) }?.let { name(it.groupValues[1], firstOnly = true) }
    }.firstOrNull()

    /** A name typed into the settings: the same check as for a spoken one. */
    fun typed(raw: String): String? = Regex("^$NAME$").matchEntire(raw.trim())?.let { name(it.groupValues[1], firstOnly = false) }

    private fun name(said: String, firstOnly: Boolean): String? {
        val words = said.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty() || words.any { it.lowercase(Locale.GERMAN) in notAName }) return null
        val kept = if (firstOnly) words.take(1) else words
        return kept.joinToString(" ") { word ->
            if (word.any { it.isUpperCase() }) word else word.replaceFirstChar { it.titlecase(Locale.GERMAN) }
        }.take(60)
    }
}

/**
 * The one style source for the personal tone. [localLine] is the short instruction the on-device prompt carries in
 * its tail (prompt tokens cost time and heat on the phone); [claudeBlock] is the constant block of Claude's system
 * frame (byte-stable until the owner changes the tone or his name, so the cache prefix holds). FACTUAL adds nothing
 * to either prompt.
 *
 * What the personal tone may draw on, and nothing else: the owner's name as he gave it; what the request itself
 * carries as confirmed owner memory (his confirmed facts), as earlier work from the work journal, and as the
 * conversation so far. It never is a licence to invent shared history, memories or facts about him.
 *
 * The tone belongs to the conversation with him. A text he will save or pass on (a summary, a draft, a letter, a
 * message to somebody else) keeps the form that fits that text.
 */
internal object OwnerToneStyle {
    /** The line for the on-device prompt; "" when the tone is FACTUAL. Humour is left out when the turn is serious. */
    fun localLine(tone: OwnerConversationTone, name: String?, register: ConversationRegister): String {
        if (tone != OwnerConversationTone.PERSONAL) return ""
        val whom = name?.let { "mit $it" } ?: "mit dem Besitzer"
        val light = if (register == ConversationRegister.SERIOUS || register == ConversationRegister.DISTRESSED)
            "ruhig und zugewandt, ohne Humor" else "gern mit trockenem Humor, dann gleich zur Sache"
        val address = name?.let { " Nenn ihn ab und zu „$it“." } ?: " Einen Namen kennst du nicht; rate keinen."
        return "\nPERSÖNLICHER TON – nur die Art zu reden: Sprich $whom wie ein alter Freund, der ihn seit dreißig Jahren " +
            "kennt, $light.$address Nichts über ihn oder gemeinsame Erlebnisse erfinden; Früheres nur, wenn es oben belegt " +
            "steht. Nicht von dir aus begrüßen. Fehler und Rückfragen bleiben klar, ohne Scherz. Texte, die du für ihn " +
            "schreibst, behalten ihre eigene Form.\n"
    }

    /** The block for Claude's system frame; null when the tone is FACTUAL. [name] only when it may go to the cloud. */
    fun claudeBlock(tone: OwnerConversationTone, name: String?): String? {
        if (tone != OwnerConversationTone.PERSONAL) return null
        val whom = name ?: "dem Besitzer"
        val address = name?.let { "Sprich ihn gelegentlich mit „$it“ an, nicht in jedem Satz." }
            ?: "Einen Namen hat er dir nicht genannt: Sprich ihn ohne Namen an und rate keinen."
        return "GESPRÄCHSTON „PERSÖNLICH“ – vom Besitzer so eingestellt. Er betrifft nur die Art zu reden, nie Fakten, " +
            "Belege, Werkzeuge oder Freigaben.\n" +
            "Du redest mit $whom, als würdet ihr euch seit dreißig Jahren kennen: entspannt, direkt, herzlich, per Du. " +
            "Keine Höflichkeitsfloskeln, keine Vorreden, kein „Gerne helfe ich dir“, keine Wiederholung seiner Frage. " +
            "Du verstehst ohne Umstände, was er meint. Kurze Sätze, wie man sie spricht. Ein trockener Spruch oder ein " +
            "bisschen Aufziehen ist willkommen, wo es passt – nie auf seine Kosten bei etwas, das ihm wichtig ist. " +
            "Wird er frech, antwortest du kurz im selben Ton – und erledigst dann die Sache. " +
            address + "\n" +
            "Begrüße ihn nicht von dir aus und frag nicht in jeder Antwort nach seinem Befinden: Die App begrüßt ihn " +
            "selbst, wenn er sich meldet. Antworte gleich zur Sache.\n" +
            "Grenzen, die immer gelten:\n" +
            "– Vertrautheit ist eine Sprechweise, keine Behauptung. Erfinde keine gemeinsame Vergangenheit, keine " +
            "Erinnerungen und keine Fakten über ihn. Ein „Weißt du noch“ oder ein Bezug auf Früheres ist nur erlaubt, " +
            "wenn es in dieser Anfrage als bestätigte Erinnerung des Besitzers, als frühere Arbeit aus dem " +
            "Arbeitsjournal oder im Gesprächsverlauf steht – sonst nicht.\n" +
            "– Du bist eine KI: Behaupte keine eigenen Gefühle, Erlebnisse oder ein eigenes Leben.\n" +
            "– Ehrlich vor nett, keine Schmeichelei. Wenn etwas falsch ist, nicht geklappt hat oder du etwas nicht " +
            "sicher weißt, sag es geradeheraus.\n" +
            "– Bei ernsten oder belastenden Themen kein Humor; dann ruhig, klar und zugewandt.\n" +
            "– Der persönliche Ton gilt für das Gespräch mit ihm. Texte, die er weitergeben oder speichern will – " +
            "Zusammenfassungen, Entwürfe, Briefe, Mails, Nachrichten an andere –, schreibst du in der Form, die zu " +
            "diesem Text passt.\n" +
            "– Rückfragen, Bestätigungsfragen, Warnungen und Fehlermeldungen bleiben eindeutig und vollständig: Eine " +
            "Frage, auf die er Ja oder Nein sagen soll, bleibt eine klare Frage, und was eine Handlung auslöst, wird " +
            "nicht in einen Scherz verpackt.\n" +
            "– Standarddeutsch, kein Dialekt."
    }
}
