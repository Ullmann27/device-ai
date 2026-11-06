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
    // A standalone voice instruction often names the document before the verb.
    private val reverseRead = Regex(
        "(?iu)^(?:jarvis[,!:]?\\s*)?(?:bitte\\s+|ich\\s+(?:will|möchte)\\s+|kannst\\s+du\\s+(?:bitte\\s+)?)?" +
            "(?:(?:die|das|meine|eine|ein)\\s+)?" +
            "(?:pdf|pdfs|pdf-datei|pdf-dokument|dokument)" +
            "(?:\\s+(?:aus|in|im)\\s+(?:dem\\s+)?downloads?(?:-ordner)?)?" +
            "\\s+(?:öffnen|anzeigen|lesen|analysieren|zusammenfassen)(?:\\s+bitte)?[.!?]?$"
    )
    // „Öffne die PDF-App“ selects an app, not the owner's document.
    private val explicitPdfApp = Regex(
        "(?iu)$START(?:pdf[- ]?app|app[- ]?pdf|pdf[- ]?reader|pdf[- ]?viewer)$END"
    )
    private val earlier = Regex(
        "(?iu)$START(ausgewählte|ausgewählten|letzte|letzten|gleiche|gleichen|dieselbe|selbe|selben|vorige|vorigen|gestrige|gestrigen)$END" +
            "\\s+$START(pdf|pdf-datei|pdf-dokument|dokument)$END"
    )
    private val draft = Regex("(?iu)$START(zusammenfassung|zusammen|zusammenfassen|entwurf|kurzfassung)$END")
    private val prohibited = Regex(
        "(?iu)$START(nicht|niemals|kein|keine|keinen|lösche|löschen|sende|senden|schicke|teile|teilen|hochladen|drucke|signiere)$END"
    )

    fun parse(utterance: String): Request? {
        if (utterance.length !in 8..160 || utterance.any { it.isISOControl() }) return null
        if (prohibited.containsMatchIn(utterance) || explicitPdfApp.containsMatchIn(utterance) ||
            !(read.containsMatchIn(utterance) || reverseRead.matches(utterance.trim()))) return null
        return Request(wantsDraft = draft.containsMatchIn(utterance), reuse = earlier.containsMatchIn(utterance))
    }
}
