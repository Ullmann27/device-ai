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

    fun requiresPreviousOwnerTurn(request: String): Boolean {
        val text = request.trim()
        if (text.isEmpty() || text.length > 1_000 || text.any { it.isISOControl() }) return false
        if (definition.containsMatchIn(text)) return false
        return correction.containsMatchIn(text) || previous.containsMatchIn(text) ||
            replacement.containsMatchIn(text) || elliptical.containsMatchIn(text)
    }
}
