package at.heinz.deviceai.cognitive

/** A prior-reading question is never authorization for a fresh hardware probe. */
internal object OwnerPreviousBatteryIntent {
    private val battery = Regex("""(?iu)(?<![\p{L}\p{N}_])(?:akku(?:stand|prozent)?|batterie(?:stand)?|ladezustand)(?![\p{L}\p{N}_])""")
    private val noNewReading = Regex(
        """(?iu)(?:keine|keinen|kein|ohne|nicht)\s+(?:(?:neue\p{L}*|erneute\p{L}*|nochmal\p{L}*|wieder|weitere\p{L}*)\s+){0,2}(?:messung\p{L}*|prüfung\p{L}*|abfrage\p{L}*|messen|prüfen|abfragen)"""
    )
    private val previous = Regex(
        """(?iu)(?:\b(?:laut|gemäß)\s+(?:deiner|der)\b.{0,80}\b(?:messung|geräteprüfung|prüfung|abfrage)\b|\b(?:letzte\p{L}*|vorherige\p{L}*|vorige\p{L}*|gerade|bereits)\s+(?:ausgeführte\p{L}*\s+)?(?:messung|geräteprüfung|prüfung|abfrage)\b|\b(?:vorhin|zuvor|eben)\b.{0,80}\b(?:gemessen|geprüft|messung|prüfung|akku|batterie)\b)"""
    )

    fun isPreviousReadingRequest(request: String): Boolean {
        if (request.length !in 1..700 || request.any { it.isISOControl() && it != '\n' }) return false
        return battery.containsMatchIn(request) &&
            (noNewReading.containsMatchIn(request) || previous.containsMatchIn(request))
    }
}
