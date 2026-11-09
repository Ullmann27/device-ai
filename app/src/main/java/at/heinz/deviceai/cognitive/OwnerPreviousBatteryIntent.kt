package at.heinz.deviceai.cognitive

/** A prior-reading question is never authorization for a fresh hardware probe. */
internal object OwnerPreviousBatteryIntent {
    private val battery = Regex("""(?iu)(?<![\p{L}\p{N}_])(?:akku(?:stand|prozent)?|batterie(?:stand)?|ladezustand)(?![\p{L}\p{N}_])""")
    private val noNewReading = Regex(
        """(?iu)(?:(?:keine|keinen|kein|ohne)\s+(?:(?:neue\p{L}*|erneut\p{L}*|nochmal\p{L}*|wieder|weitere\p{L}*)\s+){0,2}|nicht\s+(?:(?:neue\p{L}*|erneut\p{L}*|nochmal\p{L}*|wieder|weitere\p{L}*)\s+){1,2})(?:messung\p{L}*|prüfung\p{L}*|abfrage\p{L}*|messen|prüfen|abfragen)"""
    )
    private val previous = Regex(
        """(?iu)(?:\b(?:laut|gemäß)\s+(?:deiner|der)\b.{0,80}\b(?:messung|geräteprüfung|prüfung|abfrage)\b|\b(?:letzte\p{L}*|vorherige\p{L}*|vorige\p{L}*|gerade|bereits)\s+(?:ausgeführte\p{L}*\s+)?(?:messung|geräteprüfung|prüfung|abfrage)\b|\b(?:vorhin|zuvor|eben)\b.{0,80}\b(?:gemessen|geprüft|abgefragt)\b)"""
    )

    private val explicitFresh = Regex(
        """(?iu)(?<![\p{L}\p{N}_])(?:miss|messe|mess|messen|prüf(?:e)?|überprüf(?:e)?|check(?:e)?|kontrollier(?:e)?)\s+(?:[\p{L}]+\s+){0,7}(?:akku(?:stand|prozent)?|batterie(?:stand)?|ladezustand)\b"""
    )
    private val comparison = Regex("""(?iu)\b(?:vergleich\p{L}*|unterschied|gegenüberstell\p{L}*)\b""")
    private val negatedFresh = Regex(
        """(?iu)\b(?:miss|messe|mess|messen|prüf(?:e)?|überprüf(?:e)?|check(?:e)?|kontrollier(?:e)?)\s+(?:bitte\s+)?(?:nicht|niemals|kein\p{L}*|ohne)\b"""
    )

    private fun valid(request: String) =
        request.length in 1..700 && request.none { it.isISOControl() && it != '\n' }

    fun isFreshComparisonRequest(request: String): Boolean =
        valid(request) && battery.containsMatchIn(request) &&
            explicitFresh.containsMatchIn(request) && comparison.containsMatchIn(request) &&
            previous.containsMatchIn(request) && !noNewReading.containsMatchIn(request) &&
            !negatedFresh.containsMatchIn(request)

    fun isPreviousReadingRequest(request: String): Boolean {
        if (!valid(request) || !battery.containsMatchIn(request)) return false
        if (noNewReading.containsMatchIn(request)) return true
        return previous.containsMatchIn(request) && !explicitFresh.containsMatchIn(request)
    }
}
