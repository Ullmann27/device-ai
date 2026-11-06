package at.heinz.deviceai.cognitive

/**
 * The owner asks JARVIS to read a PDF („Lies diese PDF und erstelle mir eine Zusammenfassung als Entwurf.“). The only
 * consequence is Android's own document picker: the owner chooses the file, nothing is read without that choice, and
 * nothing is sent anywhere. Negations and destructive or sharing verbs never match.
 */
internal object OwnerDocumentIntent {
    /** [reuse]: the owner means the PDF chosen before („die ausgewählte/letzte/gleiche PDF“), not a new choice. */
    data class Request(val wantsDraft: Boolean, val reuse: Boolean = false)

    // Word edges as Unicode lookarounds: `\b` is ASCII-only on the JVM since JDK 19 („Öffne“) but Unicode on Android.
    private const val START = "(?<![\\p{L}\\p{N}_])"
    private const val END = "(?![\\p{L}\\p{N}_])"
    private val read = Regex(
        "(?iu)$START(lies|lese|öffne|zeige|analysiere|prüfe|fasse|nimm|verwende)$END[^.!?]{0,40}?$START(pdf|pdf-datei|pdf-dokument|dokument)$END"
    )
    private const val COURTESY = "(?:(?:bitte|mir|mal|kurz|eben|für\\s+mich)\\s+)*"
    private val politeRead = Regex(
        "(?iu)^(?:(?:kannst|könntest|würdest|magst)\\s+du|ich\\s+(?:möchte|will|würde\\s+gern(?:e)?))\\s+" +
            COURTESY + "(?:(?:die|das|ein|eine|mein|meine|diese|dieses)\\s+)?" +
            "(?:(?:ausgewählte[nrs]?|letzte[nrs]?|gleiche[nrs]?|dieselbe|selbe[nrs]?|vorige[nrs]?|gestrige[nrs]?)\\s+)?" +
            "(?:pdf(?:-datei|-dokument)?|dokument)\\s+" + COURTESY +
            "(?:öffnen|anzeigen|zeigen|lesen|vorlesen|analysieren|prüfen|zusammenfassen)" +
            "(?:\\s+(?:bitte|für\\s+mich))?[.!?]*$"
    )
    private val extraAction = Regex(
        "(?iu)(?:^|[.!?,;]\\s*|\\s+(?:und|danach|anschließend|dann)\\s+)" +
            "(?:(?:bitte|danach|anschließend|dann)\\s+)*" +
            "(öffne|öffnen|starte|starten|zeige|zeigen|suche|suchen|finde|finden|" +
            "schreib|schreibe|schreiben|tippe|installiere|kaufe|bezahle|überweise)$END"
    )
    private val joinedObject = Regex("(?iu)\\s+(?:und|sowie|oder)\\s+")
    private val earlier = Regex(
        "(?iu)$START(ausgewählte|ausgewählten|letzte|letzten|gleiche|gleichen|dieselbe|selbe|selben|vorige|vorigen|gestrige|gestrigen)$END" +
            "\\s+$START(pdf|pdf-datei|pdf-dokument|dokument)$END"
    )
    private val draft = Regex("(?iu)$START(zusammenfassung|zusammen|zusammenfassen|entwurf|kurzfassung)$END")
    private val prohibited = Regex(
        "(?iu)$START(nicht|niemals|kein|keine|keinen|lösche|löschen|sende|senden|schicke|teile|teilen|hochladen|drucke|signiere)$END"
    )

    @JvmOverloads
    fun parse(utterance: String, assistantName: String? = null): Request? {
        if (utterance.length !in 8..160 || utterance.any { it.isISOControl() }) return null
        val text = OwnerAppLookupIntent.stripAddress(utterance.trim(), assistantName)
            .replace(Regex("(?iu)^bitte\\s+"), "")
        val direct = read.find(text)
        if (prohibited.containsMatchIn(text) || (direct == null && !politeRead.matches(text))) return null
        if (direct != null && (joinedObject.containsMatchIn(direct.value) ||
                extraAction.findAll(text).any { it.groups[1]!!.range.first != direct.range.first })) return null
        return Request(wantsDraft = draft.containsMatchIn(text), reuse = earlier.containsMatchIn(text))
    }
}
