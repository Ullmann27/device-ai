package at.heinz.deviceai.cognitive

/**
 * Code55 field finding F (Fold7, Code54): the local model answered in dialect („I hab“, „ka“, „ned“, „für di“) and
 * switched address forms („Ihr habt“, „das Sie“). The former instruction asked for a "dezentes österreichisches
 * Sprachgefühl", which a small on-device model over-applies. Owner rule: natural, grammatically correct standard
 * German, consistent "du", no dialect; explicit format requests are binding.
 */
internal object OwnerLanguageStyle {
    const val INSTRUCTION =
        "Antworte in natürlichem, grammatikalisch korrektem Standarddeutsch. Verwende keinen Dialekt und keine " +
            "Mundartformen (also nicht „i hab“, „ka“, „ned“, „di“). Sprich den Besitzer durchgehend mit „du“ an, " +
            "nie mit „Sie“ oder „ihr“, und wechsle nicht die Person. Halte ausdrückliche Formatvorgaben wie eine " +
            "verlangte Satzanzahl oder Länge exakt ein. "

    private val dialect = Regex(
        "(?iu)(?<![\\p{L}\\p{N}_])(?:i\\s+(?:hab|hob|bin|werd)|" +
            "(?:für|fuer)\\s+di|ka\\s+(?:ahnung|problem)|des\\s+(?:is|ist)|" +
            "(?:is|ist|geht|kann|funktioniert)\\s+ned)(?![\\p{L}\\p{N}_])")
    private val quoted = Regex("(?s)```.*?```|`[^`]*`|„[^“]*“|»[^«]*«|“[^”]*”|\"[^\"]*\"")
    private val requestedDialect = Regex(
        "(?iu)\\b(?:antworte|sprich|schreib(?:e)?|formulier(?:e)?|erzähl(?:e)?)\\s+" +
            "(?:bitte\\s+)?(?:im|in|auf)\\s+(?:(?:österreichischem|österreichischen|wiener|wienerischem)\\s+)?" +
            "(?:dialekt|mundart|wienerisch|österreichisch)\\b")

    fun outputFeedback(request: String, response: String): String? {
        if (requestedDialect.containsMatchIn(request)) return null
        val prose = quoted.replace(response, " ")
        if (!dialect.containsMatchIn(prose)) return null
        return "Die Antwort enthält ungefragte Mundart. Formuliere dieselben belegten Inhalte " +
            "in natürlichem Standarddeutsch mit durchgehendem du neu; keine neuen Behauptungen."
    }
}
