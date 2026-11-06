package at.heinz.deviceai.cognitive

/**
 * Narrow, tool-free recognition of follow-up wording that needs the owner's
 * previous turns. This does not infer factual truth or grant memory authority.
 * A false positive can only require context or a clarifying question.
 */
internal object LocalConversationFollowUpPolicy {
    private val correction = Regex("""(?iu)^(?:korrektur|berichtigung|richtigstellung|änderung)\s*:""")
    private val previous = Regex("""(?iu)\b(?:ursprünglich|vorher|vorherigen|zuvor|davor|anfangs|vorhin|damals)\b""")
    private val replacement = Regex("""(?iu)\b(?:nun|jetzt|mittlerweile)\b[^.!?\n]{0,160}\b(?:statt|anstelle)\b""")
    private val elliptical = Regex("""(?iu)^(?:und\s+(?:was|wie|die|der|das|welche[rsnm]?|wer|wo|wann)|aber\s+(?:was|wie|die|der|das)|was ist mit|wie ist es mit|wie sieht es mit)\b""")
    // A word definition stands alone – unless the thing to be explained is a demonstrative („Was bedeutet das für …?“),
    // which points back at what was said (owner brief 06.11.2026, Priorität 2).
    private val definition = Regex("""(?iu)^(?:was\s+(?:bedeutet|heißt)\s+(?!(?:das|dies|dieses|es|daraus|davon|dann)(?!\p{L}))|erkläre\s+(?:das\s+)?wort)\b""")
    private val namedDefinition = Regex("""(?iu)^was\s+(?:bedeutet|heißt)\s+das\s+(?:wort|akronym|symbol|kürzel|zeichen)\s+""" +
        """(?!(?:im|in|auf|für|hier|dort|jetzt|nun|eigentlich|genau)(?!\p{L}))[„"«']?[\p{L}\p{N}]+""")
    private const val consequenceReferent = """(?:das(?=\s*(?:[.!?]|$)|\s+(?:für|jetzt|nun|dann|konkret|genau|eigentlich|also|denn|überhaupt|hier|praktisch)(?!\p{L})|\s*,\s*was(?!\p{L})|""" +
        """\s+(?:wort|akronym|symbol|kürzel|zeichen)\s*[.!?]?\s*$)|dies|dieses|daraus|davon|dadurch|dann)(?!\p{L})"""
    // A consequence question that opens the message has nothing in it to draw the consequence from:
    // „Was bedeutet das für unseren Plan?“, „Was folgt daraus?“, „Was heißt das jetzt?“.
    private val consequence = Regex(
        """(?iu)^(?:und\s+|aber\s+|also\s+)?was\s+(?:bedeutet|heißt|folgt|ergibt\s+sich|ändert)\s+$consequenceReferent""")
    // Owner P0 06.11.2026 – natural follow-ups that only make sense with the earlier turns (Unicode word edges):
    // a correction without a label („Nicht vier, sondern fünf.“, „Das stimmt nicht, …“, „Ich habe mich vertan …“),
    private val contrastive = Regex(
        """(?iu)(?:^|[.!?]\s*)(?:nein[,!]?\s+)?nicht\s+[^,.!?\n]{1,40},?\s+sondern\s+|(?<!\p{L})(?:das\s+stimmt\s+nicht|das\s+war\s+falsch|ich\s+habe\s+mich\s+(?:vertan|geirrt|versprochen))(?!\p{L})""")
    // a question or request that points back with a pronoun („Wo arbeitet er?“, „Fass sie mir zusammen.“). Pronouns
    // match lowercase only: the formal „Sie/Ihnen“ addresses JARVIS („Was können Sie für mich tun?“) and must not be
    // refused as a follow-up without history (#565 review); „es“ is left out („Wie spät ist es?“, „Was kostet es?“),
    private val anaphoric = Regex(
        """(?u)^(?:(?i:wo|wie|wann|was|wer|warum|wohin|woher|welche\p{L}*)\s+\p{L}+\s+(?:er|sie|ihn|ihm|ihnen|dort|dorthin)|(?i:kann|könnte|soll|sollte|darf|muss|müsste|wird|würde|hat|hatte|ist|war)\s+(?:er|sie|ihn|ihm|ihnen)(?!\p{L})|(?i:fass|lies|zeig|zeige|erklär|erkläre|vergleich|vergleiche|übersetz|übersetze)\s+(?:sie|ihn|ihm|ihnen))(?!\p{L})""")
    // and asking again for something said before – only after a recall verb („Wie hieß der Handwerker nochmal?“), not
    // a polite repeat („Kannst du mir nochmal erklären …“) or a general question („Was war nochmal die Hauptstadt …“).
    private val again = Regex(
        """(?iu)(?<!\p{L})(?:hieß|hiess|heißt|sagtest|sagte|meintest|erwähnt\p{L}*)(?!\p{L})[^.!?\n]{0,40}(?<!\p{L})(?:nochmal|noch\s+einmal)(?!\p{L})[^.!?\n]{0,40}\?""")

    fun requiresPreviousOwnerTurn(request: String): Boolean {
        val text = request.trim()
        if (text.isEmpty() || text.length > 1_000 || text.any { it.isISOControl() }) return false
        if (definition.containsMatchIn(text) || namedDefinition.containsMatchIn(text)) return false
        return correction.containsMatchIn(text) || previous.containsMatchIn(text) ||
            replacement.containsMatchIn(text) || elliptical.containsMatchIn(text) ||
            contrastive.containsMatchIn(text) || anaphoric.containsMatchIn(text) || again.containsMatchIn(text) ||
            consequence.containsMatchIn(text)
    }

    // Softer back-references (owner brief 06.11.2026, Priorität 2). They are NOT enough to refuse a first message –
    // „Wann ist der Termin jetzt?“ or „die zweite Möglichkeit“ may be complete on their own – but when earlier owner
    // turns of this session exist, they belong into the one task the model receives:
    // an ordinal or comparative reference to something enumerated before,
    private val enumerated = Regex(
        """(?iu)(?<!\p{L})(?:die|der|das|den|dem)\s+(?:erste|zweite|dritte|vierte|fünfte|letzte|andere|vorige|obere|untere)[nrsm]?\s+(?:möglichkeit|option|variante|vorschlag|punkt|weg|alternative|lösung|idee|termin|angebot)(?!\p{L})""")
    // something the owner and JARVIS share („unser Plan“, „unsere Entscheidung“),
    private val shared = Regex(
        """(?iu)(?<!\p{L})unser(?:e|en|em|er|es)?\s+(?:plan|projekt|vorhaben|termin|entscheidung|absprache|liste|aufgabe|ziel|lösung|idee)(?!\p{L})""")
    // a question about the current state after a change („… jetzt?“, „… inzwischen?“),
    private val current = Regex("""(?iu)(?<!\p{L})(?:jetzt|inzwischen|mittlerweile|aktuell|nun|noch)(?!\p{L})[^.!?\n]{0,40}\?\s*$""")
    // or a consequence question anywhere in the message („…. Was bedeutet das für unseren Plan?“).
    private val consequenceAnywhere = Regex(
        """(?iu)(?:^|[.!?]\s+)(?:und\s+|aber\s+|also\s+)?was\s+(?:bedeutet|heißt|folgt|ergibt\s+sich|ändert)\s+$consequenceReferent""")
    private val sentenceBreak = Regex("""(?<=[.!?])\s+""")

    /** True when earlier owner turns, if any exist, should travel with this request; never a reason to refuse it. */
    fun benefitsFromPreviousOwnerTurn(request: String): Boolean {
        val text = request.trim()
        if (text.isEmpty() || text.length > 1_000 || text.any { it.isISOControl() }) return false
        return sentenceBreak.splitToSequence(text).any { sentence ->
            !definition.containsMatchIn(sentence) && !namedDefinition.containsMatchIn(sentence) &&
                (requiresPreviousOwnerTurn(sentence) || enumerated.containsMatchIn(sentence) ||
                    shared.containsMatchIn(sentence) || current.containsMatchIn(sentence) ||
                    consequenceAnywhere.containsMatchIn(sentence))
        }
    }
}
