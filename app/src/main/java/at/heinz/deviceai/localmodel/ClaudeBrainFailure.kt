package at.heinz.deviceai.localmodel

/**
 * Stable problem codes of the cloud brain and their owner messages. A code is what callers receive in
 * [LocalInferenceEvent.Failed]; it never carries an API key, a request body or an upstream error text.
 * Every message says what happened and what the owner can do; none of them starts another provider or the
 * local model on its own.
 */
object ClaudeBrainFailure {
    const val NO_CONSENT = "CLAUDE_NO_CONSENT"
    const val NO_API_KEY = "CLAUDE_NO_API_KEY"
    const val INVALID_API_KEY = "CLAUDE_INVALID_API_KEY"
    const val PERMISSION_DENIED = "CLAUDE_PERMISSION_DENIED"
    const val NO_CREDIT = "CLAUDE_NO_CREDIT"
    const val RATE_LIMITED = "CLAUDE_RATE_LIMITED"
    const val OVERLOADED = "CLAUDE_OVERLOADED"
    const val OFFLINE = "CLAUDE_OFFLINE"
    const val TIMEOUT = "CLAUDE_TIMEOUT"
    const val STREAM_INTERRUPTED = "CLAUDE_STREAM_INTERRUPTED"
    const val REFUSAL = "CLAUDE_REFUSAL"
    const val MODEL_UNAVAILABLE = "CLAUDE_MODEL_UNAVAILABLE"
    const val DATA_RETENTION_REQUIRED = "CLAUDE_DATA_RETENTION_REQUIRED"
    const val BAD_REQUEST = "CLAUDE_BAD_REQUEST"
    const val CONTEXT_TOO_LONG = "CLAUDE_CONTEXT_TOO_LONG"
    const val UNEXPECTED_RESPONSE = "CLAUDE_UNEXPECTED_RESPONSE"
    const val SPEND_LIMIT_REACHED = "CLAUDE_SPEND_LIMIT_REACHED"
    const val NO_TOOL_CONVERSATION = "CLAUDE_NO_TOOL_CONVERSATION"
    /** The turn contains data the app marks as device-local only; it was not sent. */
    const val LOCAL_ONLY_SCREEN_DATA = "CLAUDE_LOCAL_ONLY_SCREEN_DATA"
    const val LOCAL_ONLY_OWNER_VETO = "CLAUDE_LOCAL_ONLY_OWNER_VETO"
    const val LOCAL_ONLY_MEMORY = "CLAUDE_LOCAL_ONLY_MEMORY"
    /** Features that are labelled as local measurements of the on-device model. */
    const val LOCAL_ONLY_FEATURE = "CLAUDE_LOCAL_ONLY_FEATURE"
    /**
     * A turn that declared the cloud's web search or code execution was about to be continued, but the owner's
     * switch, the internet mode or the one-turn approval no longer allows it. It is not continued.
     */
    const val CLOUD_TOOL_WITHDRAWN = "CLAUDE_CLOUD_TOOL_WITHDRAWN"
    /** The per-turn cap on steps in the cloud's code sandbox was reached; the stream was closed. */
    const val CLOUD_CODE_LIMIT = "CLAUDE_CLOUD_CODE_LIMIT"

    private const val SWITCH_BACK = " Du kannst unter „Modelle → Gehirn“ jederzeit auf „Lokal (Gemma, auf dem Gerät)“ " +
        "zurückschalten. Ich wechsle nicht von selbst und sende nichts an einen anderen Dienst."

    private val MESSAGES: Map<String, String> = mapOf(
        NO_CONSENT to "Claude ist als Gehirn gewählt, aber die Zustimmung zur Datenübertragung fehlt. " +
            "Ohne deine Bestätigung sende ich nichts an Claude.",
        NO_API_KEY to "Claude ist als Gehirn gewählt, aber es ist kein API-Schlüssel gespeichert. " +
            "Trage unter „Modelle → Gehirn“ deinen eigenen Anthropic-API-Schlüssel ein.",
        INVALID_API_KEY to "Anthropic hat den gespeicherten API-Schlüssel abgelehnt (ungültig oder widerrufen). " +
            "Bitte prüfe den Schlüssel in der Anthropic-Konsole und trage ihn unter „Modelle → Gehirn“ neu ein.",
        PERMISSION_DENIED to "Der API-Schlüssel darf dieses Modell oder diese Anfrage nicht verwenden. " +
            "Bitte prüfe die Berechtigungen des Schlüssels in der Anthropic-Konsole oder wähle ein anderes Claude-Modell.",
        NO_CREDIT to "Das API-Guthaben deines Anthropic-Kontos ist aufgebraucht. Über die Cloud kann ich erst wieder " +
            "antworten, wenn du in der Anthropic-Konsole Guthaben aufgeladen hast.",
        RATE_LIMITED to "Anthropic nimmt gerade keine weitere Anfrage an: Das Anfragelimit oder die monatliche " +
            "Ausgabengrenze deines API-Kontos ist erreicht. Bitte warte einen Moment und frag mich dann noch einmal.",
        OVERLOADED to "Die Server meines Cloud-Gehirns (Anthropic) sind gerade überlastet oder gestört. Deine " +
            "Nachricht wurde nicht beantwortet. " +
            "Bitte versuche es in ein paar Minuten noch einmal.",
        OFFLINE to "Ich erreiche mein Cloud-Gehirn nicht: Das Gerät hat keine Internetverbindung oder die Verbindung zu " +
            "Anthropic kam nicht zustande.",
        TIMEOUT to "Mein Cloud-Gehirn hat nicht rechtzeitig geantwortet (Zeitüberschreitung). Die Antwort ist unvollständig " +
            "oder fehlt. Bitte frag mich noch einmal.",
        STREAM_INTERRUPTED to "Die Verbindung zu meinem Cloud-Gehirn ist mitten in der Antwort abgebrochen. Was bis dahin ankam, " +
            "ist unvollständig; ich habe nichts ergänzt.",
        REFUSAL to "Mein Cloud-Gehirn hat diese Anfrage abgelehnt (Sicherheitsprüfung von Anthropic). Es gibt dazu keine " +
            "Antwort. Formuliere die Frage anders oder stelle sie dem lokalen Modell.",
        MODEL_UNAVAILABLE to "Das gewählte Claude-Modell ist für deinen API-Schlüssel nicht verfügbar. " +
            "Wähle unter „Modelle → Gehirn“ ein anderes Claude-Modell.",
        DATA_RETENTION_REQUIRED to "Dieses Claude-Modell verlangt, dass in deinem Anthropic-Konto die " +
            "Datenaufbewahrung (30 Tage) eingeschaltet ist. Wähle ein anderes Claude-Modell oder ändere die " +
            "Einstellung in der Anthropic-Konsole.",
        BAD_REQUEST to "Anthropic hat die Anfrage als ungültig abgelehnt. Das ist ein Fehler dieser App, " +
            "nicht deiner Frage. Es wurde nichts beantwortet.",
        CONTEXT_TOO_LONG to "Die Anfrage war für das gewählte Claude-Modell zu lang. Ich habe nichts abgeschnitten " +
            "und nichts gesendet, was unvollständig wäre. Bitte beginne ein neues Gespräch oder kürze den Auftrag.",
        UNEXPECTED_RESPONSE to "Mein Cloud-Gehirn hat die Antwort in einer Form beendet, die diese App nicht verarbeitet. " +
            "Ich übernehme davon nichts als Ergebnis.",
        SPEND_LIMIT_REACHED to "Dein selbst gesetztes Monatslimit für Claude ist nach der Schätzung dieser App " +
            "erreicht. Ich sende keine weitere Anfrage. Du kannst das Limit unter „Modelle → Gehirn“ ändern.",
        NO_TOOL_CONVERSATION to "Die Werkzeugrückmeldung gehört zu keinem laufenden Cloud-Gespräch mehr. " +
            "Bitte stelle den Auftrag noch einmal.",
        LOCAL_ONLY_SCREEN_DATA to "Bildschirminhalte anderer Apps sind in dieser App als „nur auf dem Gerät“ " +
            "markiert. Ich sende sie nicht in die Cloud; diese Anfrage wurde nicht gesendet.",
        LOCAL_ONLY_OWNER_VETO to "Du hast für diese Nachricht Internet oder Cloud ausgeschlossen. Mein Gehirn läuft " +
            "gerade in der Cloud, deshalb habe ich die Nachricht nicht gesendet.",
        LOCAL_ONLY_MEMORY to "Für diese Frage bräuchte mein Cloud-Gehirn eine bestätigte Erinnerung, die als „nur auf dem " +
            "Gerät“ markiert ist. Ich habe sie nicht gesendet. Du kannst das Mitsenden solcher Erinnerungen unter " +
            "„Modelle → Gehirn“ ausdrücklich erlauben.",
        LOCAL_ONLY_FEATURE to "Diese Funktion misst das lokale Modell auf dem Gerät. Solange Claude als Gehirn " +
            "gewählt ist, starte ich sie nicht, damit nichts unbemerkt in die Cloud geht und kein Guthaben verbraucht wird.",
        CLOUD_TOOL_WITHDRAWN to "Die Freigabe für die Websuche oder die Code-Ausführung über die Cloud gilt nicht mehr " +
            "(Schalter, Internetmodus oder die einmalige Freigabe wurden geändert). Ich habe diese Antwort nicht " +
            "fortgesetzt und nichts weiter gesendet. Stell die Frage bitte noch einmal.",
        CLOUD_CODE_LIMIT to "Die Grenze von ${ClaudeServerToolPolicy.CODE_MAX_STEPS} Schritten der Code-Ausführung " +
            "je Auftrag ist erreicht. Ich habe abgebrochen, damit keine weiteren Kosten entstehen; ein fertiges " +
            "Ergebnis gibt es nicht. Stell die Aufgabe in kleineren Teilen noch einmal."
    )

    /**
     * Codes after which switching back to the local brain is the obvious next step. A refusal is not among them:
     * Claude works, it declined one request, and its message already names the alternatives.
     */
    private val OFFER_SWITCH_BACK = setOf(NO_CONSENT, NO_API_KEY, INVALID_API_KEY, PERMISSION_DENIED, NO_CREDIT,
        RATE_LIMITED, OVERLOADED, OFFLINE, TIMEOUT, MODEL_UNAVAILABLE, DATA_RETENTION_REQUIRED, SPEND_LIMIT_REACHED,
        LOCAL_ONLY_SCREEN_DATA, LOCAL_ONLY_OWNER_VETO, LOCAL_ONLY_MEMORY, LOCAL_ONLY_FEATURE)

    /** A cut connection leaves real answer text behind; after every other failure streamed text is no answer. */
    private val KEEPS_PARTIAL_OUTPUT = setOf(STREAM_INTERRUPTED, TIMEOUT, OVERLOADED)

    fun isClaude(problem: String?): Boolean = problem != null && problem in MESSAGES

    /**
     * True when text that was already streamed must not be shown as a (partial) answer, for example after a
     * refusal. The caller then reports the failure itself.
     */
    fun discardsPartialOutput(problem: String?): Boolean = isClaude(problem) && problem !in KEEPS_PARTIAL_OUTPUT

    fun offersSwitchBack(problem: String?): Boolean = problem in OFFER_SWITCH_BACK

    fun ownerMessage(problem: String?): String {
        val message = MESSAGES[problem] ?: return "Ich konnte über die Cloud nicht antworten." + SWITCH_BACK
        return if (offersSwitchBack(problem)) message + SWITCH_BACK else message
    }

    /**
     * Short line for the activity log – a status surface next to the conversation, so it names the cloud, not the
     * provider: the code only, without its internal `CLAUDE_` prefix.
     */
    fun logLine(problem: String?): String =
        "CLOUD · " + (problem?.takeIf(::isClaude)?.removePrefix("CLAUDE_") ?: "unbekannter Fehler")
}
