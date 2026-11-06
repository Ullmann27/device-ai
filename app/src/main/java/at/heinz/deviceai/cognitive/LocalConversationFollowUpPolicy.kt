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
    private val definition = Regex("""(?iu)^(?:was\s+(?:bedeutet|heißt)|erkläre\s+(?:das\s+)?wort)\b""")
    // Owner P0 06.11.2026 – natural follow-ups that only make sense with the earlier turns (Unicode word edges):
    // a correction without a label („Nicht vier, sondern fünf.“, „Das stimmt nicht, …“, „Ich habe mich vertan …“),
    private val contrastive = Regex(
        """(?iu)(?:^|[.!?]\s*)(?:nein[,!]?\s+)?nicht\s+[^,.!?\n]{1,40},?\s+sondern\s+|(?<!\p{L})(?:das\s+stimmt\s+nicht|das\s+war\s+falsch|ich\s+habe\s+mich\s+(?:vertan|geirrt|versprochen))(?!\p{L})""")
    // a question or request that points back with a pronoun („Wo arbeitet er?“, „Fass sie mir zusammen.“). Pronouns
    // match lowercase only: the formal „Sie/Ihnen“ addresses JARVIS („Was können Sie für mich tun?“) and must not be
    // refused as a follow-up without history (#565 review); „es“ is left out („Wie spät ist es?“, „Was kostet es?“),
    private val anaphoric = Regex(
        """(?u)^(?:(?i:wo|wie|wann|was|wer|warum|wohin|woher|welche\p{L}*)\s+\p{L}+\s+(?:er|sie|ihn|ihm|ihnen|dort|dorthin)|(?i:fass|lies|zeig|zeige|erklär|erkläre|vergleich|vergleiche|übersetz|übersetze)\s+(?:sie|ihn|ihm|ihnen))(?!\p{L})""")
    // and asking again for something said before – only after a recall verb („Wie hieß der Handwerker nochmal?“), not
    // a polite repeat („Kannst du mir nochmal erklären …“) or a general question („Was war nochmal die Hauptstadt …“).
    private val again = Regex(
        """(?iu)(?<!\p{L})(?:hieß|hiess|heißt|sagtest|sagte|meintest|erwähnt\p{L}*)(?!\p{L})[^.!?\n]{0,40}(?<!\p{L})(?:nochmal|noch\s+einmal)(?!\p{L})[^.!?\n]{0,40}\?""")

    fun requiresPreviousOwnerTurn(request: String): Boolean {
        val text = request.trim()
        if (text.isEmpty() || text.length > 1_000 || text.any { it.isISOControl() }) return false
        if (definition.containsMatchIn(text)) return false
        return correction.containsMatchIn(text) || previous.containsMatchIn(text) ||
            replacement.containsMatchIn(text) || elliptical.containsMatchIn(text) ||
            contrastive.containsMatchIn(text) || anaphoric.containsMatchIn(text) || again.containsMatchIn(text)
    }
}
