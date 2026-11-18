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
    /**
     * Code69: how the owner really speaks. The speech recogniser delivers „Erklär mir …“, „Erzähl mir …“, „Sag mir …“,
     * „Kannst du mir erklären, …“, a greeting or thanks – mostly without a question mark. Until now only the written
     * forms above were tool-free; every such spoken turn carried all registered tool schemas into the prompt.
     * These forms are text-only as well, but under the stricter [deviceTopics] guard: anything that names a device
     * thing keeps the tool route exactly as before.
     */
    private val spokenStart = Regex(
        """(?iu)^\s*(?:(?:bitte|und|also|okay|ok|hey|sag\s+mal|kannst\s+du|könntest\s+du|würdest\s+du)[,\s]+)*
        (?:(?:mir|uns|mal|bitte|kurz|doch|noch|einmal|nochmal)\s+)*
        (?:erklär|erklärst|beschreib|beschreibst|fass|formulier|formulierst|erzähl|erzählst|vergleich|vergleichst|
        (?:schreib|schreibst|sag|sage|sagst)\s+(?:mir|uns|mal|doch|bitte)|definier|definiere|übersetz|übersetze|
        begründe?|erläuter|erläutere|
        wo|wann|wozu|woher|wohin|womit|wofür|wieviel|
        weißt\s+du|kennst\s+du|glaubst\s+du|meinst\s+du|denkst\s+du|
        ich\s+(?:möchte|will|würde\s+gern(?:e)?)\s+wissen|
        gib\s+mir\s+(?:(?:ein|eine|einen)\s+)?(?:beispiel\p{L}*|überblick|übersicht|zusammenfassung|tipp\p{L}*|
        rat|ratschlag|ratschläge|erklärung|definition)|
        hallo|hi|servus|grüß\s+dich|guten\s+(?:morgen|tag|abend)|gute\s+nacht|danke|dankeschön|vielen\s+dank|
        wie\s+geht)(?!\p{L})""".replace("\n", "").replace(Regex("""\s{2,}"""), "")
    )
    private val politeTextVerb = Regex(
        """(?iu)^\s*(?:bitte\s+)?(?:kannst|könntest|würdest|magst)\s+du\s+.{0,80}?(?<!\p{L})(?:erklären|beschreiben|
        sagen|erzählen|zusammenfassen|erläutern|vergleichen|übersetzen|definieren|begründen)(?!\p{L})"""
            .replace("\n", "").replace(Regex("""\s{2,}"""), "")
    )
    /** Things of this device and of the outside world a tool would be needed for. */
    private val deviceTopics = Regex(
        """(?iu)(?<!\p{L})(?:akku\p{L}*|batterie\p{L}*|ladezustand|speicher|speicherplatz|wlan|netzwerk\p{L}*|
        bluetooth|apps?|bildschirm\p{L}*|screenshot\p{L}*|kontakt\p{L}*|nachricht|nachrichten|mails?|e-mails?|gmail|
        sms|anruf\p{L}*|ruf\p{L}*|termin\p{L}*|kalender\p{L}*|wecker|timer|erinner\p{L}*|internet|web|online|
        google\p{L}*|lautstärke|helligkeit|fotos?|bilder?|ordner\p{L}*|pdfs?|dokument\p{L}*|word|install\p{L}*|
        wetter|uhrzeit|spät|datum|heute|gestern|morgen|aktuell\p{L}*|neueste\p{L}*|live|standort\p{L}*|navig\p{L}*|
        musik|video\p{L}*|kamera|taschenlampe|einstellung\p{L}*|update\p{L}*|version\p{L}*|modell\p{L}*|
        speicher\p{L}*|lies|lese|vorlesen|
        (?:und|dann|danach|anschließend)\s+(?:(?:dann|bitte|mir)\s+)*(?:mach|stell|zeig|spiel|merk|leg|ruf|hol)\p{L}*)(?!\p{L})"""
            .replace("\n", "").replace(Regex("""\s{2,}"""), "")
    )
    private val greetingWithMorning = Regex("(?iu)^\\s*guten\\s+morgen(?!\\p{L})")

    fun useToolFree(request: String, hasObservations: Boolean, hasReflection: Boolean,
                    assignedBot: Boolean, codingTask: Boolean, priorTextOnlyResponse: Boolean = false): Boolean {
        if (hasObservations || hasReflection || assignedBot || codingTask) return false
        val text = request.trim()
        if (text.length !in 4..LocalDialogueContextLimits.MAX_OWNER_CHARACTERS ||
            text.any { it.isISOControl() && it != '\n' }) return false
        if (deviceTerms.containsMatchIn(text)) return false
        if (LocalConversationFollowUpPolicy.requestsContinuation(text)) return priorTextOnlyResponse
        if (textStart.containsMatchIn(text) || text.endsWith('?')) return true
        if (!spokenStart.containsMatchIn(text) && !politeTextVerb.containsMatchIn(text)) return false
        // „Guten Morgen“ is a greeting, not the word „morgen“ of an appointment.
        return !deviceTopics.containsMatchIn(greetingWithMorning.replace(text, ""))
    }
}
