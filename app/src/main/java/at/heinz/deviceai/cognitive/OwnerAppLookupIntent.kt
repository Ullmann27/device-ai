package at.heinz.deviceai.cognitive

/** Owner-initiated app name discovery only. Never converts words into a launch grant. */
internal object OwnerAppLookupIntent {
    private val request = Regex(
        "(?iu)^(?:bitte\\s+)?(suche|finde|öffne|starte|zeige\\s+mir)\\s+" +
            "(?:mir\\s+)?(?:(?:die|den|das)\\s+)?(?:app\\s+)?" +
            "([\\p{L}\\p{N}][\\p{L}\\p{N} ._+-]{1,67}?)" +
            "(?:\\s+app)?(?:\\s+bitte)?[.!]?$"
    )
    private val prohibited = Regex(
        "(?iu)\\b(nicht|niemals|kein|keine|keinen|ohne|und|oder|danach|anschließend|sende|schreibe|tippe|lösche|installiere|kaufe|bezahle|überweise)\\b"
    )

    fun parse(utterance: String): String? {
        if (utterance.length !in 5..100 || utterance.any { it.isISOControl() }) return null
        if (prohibited.containsMatchIn(utterance)) return null
        val match = request.matchEntire(utterance.trim()) ?: return null
        val name = match.groupValues[2].trim().trimEnd('.', '!').trim()
        if (name.length !in 2..68 || name.contains("..")) return null
        return name
    }
}
