package at.heinz.deviceai.cognitive

/**
 * Use a native conversation without tools for unambiguous text-only owner questions.
 * This is only a capability restriction, never an authorization for any action.
 */
internal object LocalTextOnlyInferenceRoute {
    private val deviceTerms = Regex(
        """(?iu)(?<!\p{L})(?:öffn\p{L}*|start\p{L}*|klick\p{L}*|tipp\p{L}*|scroll\p{L}*|send\p{L}*|schick\p{L}*|
        lösch\p{L}*|bereinig\p{L}*|installier\p{L}*|deinstallier\p{L}*|überweis\p{L}*|bezahl\p{L}*|
        recherchier\p{L}*|such\p{L}*|find\p{L}*|download\p{L}*|whatsapp|gerät\p{L}*|handy|mikrofon|shizuku|
        kamera|datei\p{L}*|systemdiagnose|aktienkurs\p{L}*|aktuell\p{L}*\s+(?:nachrichten|wetter|kurse))(?!\p{L})"""
            .replace("\n", "").replace(Regex("""\s{2,}"""), "")
    )
    private val textStart = Regex(
        """(?iu)^\s*(?:(?:bitte|kannst\s+du|könntest\s+du)\s+)?(?:erkläre|beschreibe|fasse|formuliere|
        erzähle|vergleiche|nenn\p{L}*|schreibe|was|wer|wie|warum|wieso|weshalb|welche\p{L}*|
        entwurf\s*:)(?!\p{L})""".replace("\n", "").replace(Regex("""\s{2,}"""), "")
    )
    fun useToolFree(request: String, hasObservations: Boolean, hasReflection: Boolean,
                    assignedBot: Boolean, codingTask: Boolean): Boolean {
        if (hasObservations || hasReflection || assignedBot || codingTask) return false
        val text = request.trim()
        if (text.length !in 4..LocalDialogueContextLimits.MAX_OWNER_CHARACTERS ||
            text.any { it.isISOControl() && it != '\n' }) return false
        if (deviceTerms.containsMatchIn(text)) return false
        return textStart.containsMatchIn(text) || text.endsWith('?')
    }
}
