package at.heinz.deviceai.cognitive

/**
 * The owner asks JARVIS to read a PDF („Lies diese PDF und erstelle mir eine Zusammenfassung als Entwurf.“). The only
 * consequence is Android's own document picker: the owner chooses the file, nothing is read without that choice, and
 * nothing is sent anywhere. Negations and destructive or sharing verbs never match.
 */
internal object OwnerDocumentIntent {
    data class Request(val wantsDraft: Boolean)

    // Word edges as Unicode lookarounds: `\b` is ASCII-only on the JVM since JDK 19 („Öffne“) but Unicode on Android.
    private const val START = "(?<![\\p{L}\\p{N}_])"
    private const val END = "(?![\\p{L}\\p{N}_])"
    private val read = Regex(
        "(?iu)$START(lies|lese|öffne|zeige|analysiere|prüfe|fasse)$END[^.!?]{0,40}?$START(pdf|pdf-datei|pdf-dokument|dokument)$END"
    )
    private val draft = Regex("(?iu)$START(zusammenfassung|zusammen|zusammenfassen|entwurf|kurzfassung)$END")
    private val prohibited = Regex(
        "(?iu)$START(nicht|niemals|kein|keine|keinen|lösche|löschen|sende|senden|schicke|teile|teilen|hochladen|drucke|signiere)$END"
    )

    fun parse(utterance: String): Request? {
        if (utterance.length !in 8..160 || utterance.any { it.isISOControl() }) return null
        if (prohibited.containsMatchIn(utterance) || !read.containsMatchIn(utterance)) return null
        return Request(wantsDraft = draft.containsMatchIn(utterance))
    }
}
