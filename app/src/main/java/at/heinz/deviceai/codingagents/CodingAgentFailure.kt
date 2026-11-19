package at.heinz.deviceai.codingagents

/**
 * Stable problem codes of the cloud coding agents and their owner messages. A code never carries an API key, a
 * GitHub token, a request body or an upstream error text. Every message says what happened, what exists now and
 * what the owner can do. Nothing is retried in the background after any of them.
 */
object CodingAgentFailure {
    // Preconditions of a run (checked before anything leaves the device).
    const val NO_API_KEY = "AGENT_NO_API_KEY"
    const val NO_CLAUDE_CONSENT = "AGENT_NO_CLAUDE_CONSENT"
    const val NO_AGENT_CONSENT = "AGENT_NO_AGENT_CONSENT"
    const val NO_GITHUB_TOKEN = "AGENT_NO_GITHUB_TOKEN"
    const val NO_REPOSITORY = "AGENT_NO_REPOSITORY"
    const val REPOSITORY_NOT_ALLOWED = "AGENT_REPOSITORY_NOT_ALLOWED"
    const val BUDGET_OUT_OF_RANGE = "AGENT_BUDGET_OUT_OF_RANGE"
    const val MONTHLY_LIMIT = "AGENT_MONTHLY_LIMIT"
    const val RUN_ACTIVE = "AGENT_RUN_ACTIVE"
    const val TOO_MANY_RUNS = "AGENT_TOO_MANY_RUNS"
    const val NOT_CONFIRMED = "AGENT_NOT_CONFIRMED"
    const val EMPTY_TASK = "AGENT_EMPTY_TASK"
    const val STORAGE = "AGENT_STORAGE"

    // Anthropic API.
    const val INVALID_API_KEY = "AGENT_INVALID_API_KEY"
    const val PERMISSION_DENIED = "AGENT_PERMISSION_DENIED"
    const val BETA_UNAVAILABLE = "AGENT_BETA_UNAVAILABLE"
    const val NO_CREDIT = "AGENT_NO_CREDIT"
    const val RATE_LIMITED = "AGENT_RATE_LIMITED"
    const val OVERLOADED = "AGENT_OVERLOADED"
    const val OFFLINE = "AGENT_OFFLINE"
    const val TIMEOUT = "AGENT_TIMEOUT"
    const val BAD_REQUEST = "AGENT_BAD_REQUEST"
    const val MODEL_UNAVAILABLE = "AGENT_MODEL_UNAVAILABLE"
    const val BUDGET_REJECTED = "AGENT_BUDGET_REJECTED"
    const val CONFLICT = "AGENT_CONFLICT"
    const val RESOURCE_GONE = "AGENT_RESOURCE_GONE"
    const val SESSION_GONE = "AGENT_SESSION_GONE"
    const val VAULT = "AGENT_VAULT"
    const val UNEXPECTED = "AGENT_UNEXPECTED"
    const val STREAM_LOST = "AGENT_STREAM_LOST"

    // Reported by the session itself (`session.error`, `stop_reason`).
    const val SESSION_REPOSITORY_AUTH = "AGENT_SESSION_REPOSITORY_AUTH"
    const val SESSION_REPOSITORY_ACCESS = "AGENT_SESSION_REPOSITORY_ACCESS"
    const val SESSION_REPOSITORY_CLONE = "AGENT_SESSION_REPOSITORY_CLONE"
    const val SESSION_BILLING = "AGENT_SESSION_BILLING"
    const val SESSION_MODEL = "AGENT_SESSION_MODEL"
    const val SESSION_CREDENTIAL_HOST = "AGENT_SESSION_CREDENTIAL_HOST"
    const val SESSION_RETRIES_EXHAUSTED = "AGENT_SESSION_RETRIES_EXHAUSTED"
    const val SESSION_REFUSAL = "AGENT_SESSION_REFUSAL"
    const val SESSION_TERMINATED = "AGENT_SESSION_TERMINATED"
    const val SESSION_UNKNOWN_STOP = "AGENT_SESSION_UNKNOWN_STOP"
    const val APP_RESTARTED_BEFORE_START = "AGENT_APP_RESTARTED_BEFORE_START"
    const val SESSION_WITHOUT_ORDER = "AGENT_SESSION_WITHOUT_ORDER"

    // GitHub, asked by the app itself.
    const val GITHUB_TOKEN_REJECTED = "GITHUB_TOKEN_REJECTED"
    const val GITHUB_FORBIDDEN = "GITHUB_FORBIDDEN"
    const val GITHUB_RATE_LIMITED = "GITHUB_RATE_LIMITED"
    const val GITHUB_NOT_FOUND = "GITHUB_NOT_FOUND"
    const val GITHUB_MOVED = "GITHUB_MOVED"
    const val GITHUB_READ_ONLY = "GITHUB_READ_ONLY"
    const val GITHUB_OFFLINE = "GITHUB_OFFLINE"
    const val GITHUB_UNAVAILABLE = "GITHUB_UNAVAILABLE"
    const val GITHUB_UNEXPECTED = "GITHUB_UNEXPECTED"

    private const val SETUP = " Öffne „Agenten → Coding-Agenten (Claude, Cloud)“."

    private val MESSAGES: Map<String, String> = mapOf(
        NO_API_KEY to "Es ist kein Anthropic-API-Schlüssel gespeichert. Trage ihn unter „Gehirn“ ein; ohne ihn startet kein Coding-Agent.",
        NO_CLAUDE_CONSENT to "Die Zustimmung zur Datenübertragung an Claude fehlt („Gehirn → Zustimmung“). Ohne sie sende ich nichts an Anthropic.",
        NO_AGENT_CONSENT to "Du hast den Coding-Agenten noch nicht zugestimmt. Lies dort den Hinweis und bestätige ihn." + SETUP,
        NO_GITHUB_TOKEN to "Es ist kein GitHub-Token gespeichert. Ohne Token kann der Agent dein Repository weder lesen noch einen Pull Request anlegen." + SETUP,
        NO_REPOSITORY to "Du hast noch kein Repository freigegeben. Trage dort mindestens eines im Format Besitzer/Name ein." + SETUP,
        REPOSITORY_NOT_ALLOWED to "Dieses Repository steht nicht auf deiner Freigabeliste. Ich starte dafür keinen Lauf.",
        BUDGET_OUT_OF_RANGE to "Das Budget liegt außerhalb deiner Grenzen. Es wurde nichts gestartet.",
        MONTHLY_LIMIT to "Der Lauf würde dein Monatslimit für Claude überschreiten. Er startet nicht.",
        RUN_ACTIVE to "Es läuft bereits ein Coding-Agent. In dieser Version läuft immer nur einer; stoppe ihn oder warte auf sein Ergebnis.",
        TOO_MANY_RUNS to "Die Liste der Läufe ist voll. Lösche zuerst ältere Verläufe.",
        NOT_CONFIRMED to "Ohne deine ausdrückliche Bestätigung startet kein Lauf.",
        EMPTY_TASK to "Der Auftrag ist leer. Schreib oder sag, was der Agent tun soll.",
        STORAGE to "Die App konnte den Stand nicht speichern. Es wurde nichts gestartet.",
        INVALID_API_KEY to "Anthropic hat den gespeicherten API-Schlüssel abgelehnt (ungültig oder widerrufen). Prüfe ihn in der Anthropic-Konsole und trage ihn unter „Gehirn“ neu ein.",
        PERMISSION_DENIED to "Der API-Schlüssel darf das nicht: Anthropic hat die Anfrage mangels Berechtigung abgelehnt. Prüfe in der Anthropic-Konsole die Rechte des Schlüssels und ob Managed Agents für dein Konto freigeschaltet sind.",
        BETA_UNAVAILABLE to "Claude Managed Agents (Beta) ist für dein Anthropic-Konto nicht erreichbar. Prüfe in der Anthropic-Konsole, ob die Funktion für dein Konto freigeschaltet ist. Es wurde nichts gestartet.",
        NO_CREDIT to "Das API-Guthaben deines Anthropic-Kontos reicht nicht. Lade in der Anthropic-Konsole Guthaben auf.",
        RATE_LIMITED to "Anthropic nimmt gerade keine weitere Anfrage an (Anfrage- oder Ausgabengrenze des Kontos). Ich versuche es nicht von selbst noch einmal.",
        OVERLOADED to "Die Server von Anthropic sind gerade überlastet oder gestört. Ich versuche es nicht von selbst noch einmal.",
        OFFLINE to "Ich erreiche Anthropic nicht: keine Internetverbindung oder die Verbindung kam nicht zustande.",
        TIMEOUT to "Anthropic hat nicht rechtzeitig geantwortet (Zeitüberschreitung).",
        BAD_REQUEST to "Anthropic hat die Anfrage als ungültig abgelehnt. Das ist ein Fehler dieser App, nicht deines Auftrags.",
        MODEL_UNAVAILABLE to "Das gewählte Modell steht für Coding-Agenten mit deinem Konto nicht zur Verfügung. Wähle in den Einstellungen der Coding-Agenten ein anderes.",
        BUDGET_REJECTED to "Anthropic hat das Budget für diesen Lauf abgelehnt. Es wurde nichts gestartet.",
        CONFLICT to "Anthropic meldet einen Konflikt mit dem aktuellen Zustand (zum Beispiel läuft die Sitzung noch). Bitte gleich noch einmal versuchen.",
        RESOURCE_GONE to "Etwas, das die App in deinem Anthropic-Konto angelegt hatte, gibt es dort nicht mehr. Tippe in den Einstellungen auf „Bei Anthropic einrichten“.",
        SESSION_GONE to "Diese Sitzung gibt es bei Anthropic nicht mehr (gelöscht oder abgelaufen).",
        VAULT to "Der GitHub-Zugang konnte im Tresor deines Anthropic-Kontos nicht gespeichert oder gelöscht werden.",
        UNEXPECTED to "Anthropic hat in einer Form geantwortet, die diese App nicht verarbeitet. Ich übernehme davon nichts als Ergebnis.",
        STREAM_LOST to "Die Verbindung zum laufenden Agenten ist abgebrochen und ließ sich nicht wieder aufbauen. Die Sitzung kann bei Anthropic weiterarbeiten; ihr Budget bleibt die Obergrenze. Tippe auf „Aktualisieren“, sobald du wieder Netz hast.",
        SESSION_REPOSITORY_AUTH to "GitHub hat das Token für dieses Repository abgelehnt. Prüfe, ob das Token gültig ist und dieses Repository mit „Contents: Read and write“ einschließt.",
        SESSION_REPOSITORY_ACCESS to "Das Repository wurde nicht gefunden oder das Token hat keinen Zugriff darauf.",
        SESSION_REPOSITORY_CLONE to "Das Repository ließ sich in der Sandbox nicht bereitstellen (Klonen oder Auschecken fehlgeschlagen).",
        SESSION_BILLING to "Anthropic hat die Sitzung wegen des Guthabens oder der Abrechnung angehalten.",
        SESSION_MODEL to "Das Modell hat in der Sitzung wiederholt nicht geantwortet (überlastet, begrenzt oder Anfrage fehlgeschlagen).",
        SESSION_CREDENTIAL_HOST to "Der Tresor-Eintrag für GitHub ließ sich in der Sitzung nicht verwenden.",
        SESSION_RETRIES_EXHAUSTED to "Die Sitzung hat nach mehreren Versuchen aufgegeben.",
        SESSION_REFUSAL to "Claude hat diesen Auftrag abgelehnt (Sicherheitsprüfung von Anthropic).",
        SESSION_TERMINATED to "Die Sitzung wurde bei Anthropic beendet, ohne ihr Ergebnis zu melden.",
        SESSION_UNKNOWN_STOP to "Die Sitzung hat aus einem Grund angehalten, den diese App nicht kennt.",
        APP_RESTARTED_BEFORE_START to "Die App wurde beendet, bevor die Sitzung bei Anthropic angelegt war. Es wurde kein Auftrag gesendet.",
        SESSION_WITHOUT_ORDER to "Die Sitzung wurde bei Anthropic angelegt, aber der Auftrag ist dort nicht angekommen. Der Agent hat nicht gearbeitet. Ich sende ihn nicht von selbst noch einmal; gib den Auftrag bitte neu.",
        GITHUB_TOKEN_REJECTED to "GitHub hat das gespeicherte Token abgelehnt (ungültig, abgelaufen oder widerrufen). Erzeuge ein neues und trage es in den Einstellungen der Coding-Agenten ein.",
        GITHUB_FORBIDDEN to "GitHub verweigert dem Token diesen Zugriff. Prüfe die Rechte des Tokens (Contents und Pull requests: Read and write) und ob das Repository ausgewählt ist.",
        GITHUB_RATE_LIMITED to "GitHub nimmt gerade keine weitere Anfrage dieses Tokens an (Anfragegrenze). Bitte später noch einmal.",
        GITHUB_NOT_FOUND to "GitHub kennt dieses Repository nicht, oder das Token hat keinen Zugriff darauf. Prüfe den Namen und die Repository-Auswahl des Tokens.",
        GITHUB_MOVED to "Das Repository wurde umbenannt oder verschoben. Trage den neuen Namen in die Freigabeliste ein.",
        GITHUB_READ_ONLY to "Das Token darf in dieses Repository nicht schreiben, oder das Repository ist archiviert. Der Agent könnte keinen Zweig pushen; es wurde nichts gestartet.",
        GITHUB_OFFLINE to "Ich erreiche GitHub nicht: keine Internetverbindung oder die Verbindung kam nicht zustande.",
        GITHUB_UNAVAILABLE to "GitHub ist gerade gestört. Bitte später noch einmal versuchen.",
        GITHUB_UNEXPECTED to "GitHub hat in einer Form geantwortet, die diese App nicht verarbeitet.")

    fun known(code: String?): Boolean = code != null && code in MESSAGES

    fun message(code: String?): String = MESSAGES[code] ?: "Der Coding-Agent meldet einen unbekannten Fehler."

    /** Short line for the activity log; the code only. */
    fun logLine(code: String?): String = "CODING-AGENT · " + (code?.takeIf(::known) ?: "unbekannter Fehler")

    /** Codes after which trying to re-open the event stream is pointless or would hide the real problem. */
    val NOT_RECONNECTABLE = setOf(INVALID_API_KEY, PERMISSION_DENIED, BETA_UNAVAILABLE, NO_CREDIT, SESSION_GONE, BAD_REQUEST,
        NO_API_KEY)
}

/** A refused step: [code] is one of [CodingAgentFailure], [message] the complete owner text. */
class CodingAgentRefusal(val code: String, val message: String = CodingAgentFailure.message(code))

/** Thrown by the cloud and GitHub clients. Carries a stable code only; [toString] never shows more. */
class CodingAgentException(val code: String, cause: Throwable? = null) : RuntimeException(code, null, false, false) {
    /** The class of the original failure for diagnostics; its message is deliberately not kept. */
    val origin: String? = cause?.javaClass?.simpleName
    override fun toString(): String = "CodingAgentException($code)"
}

/**
 * Removes anything secret-shaped from a text before it is shown, spoken, logged or stored: the two stored secrets
 * themselves and everything that looks like an Anthropic key or a GitHub token. The sandbox never holds the real
 * GitHub token (the vault substitutes it at egress), so this is the second wall, not the first.
 */
class CodingAgentRedactor(private val secrets: () -> List<String> = { emptyList() }) {
    fun clean(text: String): String {
        var result = text
        secrets().filter { it.length >= 8 }.forEach { result = result.replace(it, REMOVED) }
        return SHAPES.fold(result) { current, shape -> shape.replace(current, REMOVED) }
    }

    companion object {
        const val REMOVED = "[entfernt]"
        private val SHAPES = listOf(
            Regex("sk-ant-[A-Za-z0-9_-]{8,}"),
            Regex("github_pat_[A-Za-z0-9_]{10,}"),
            Regex("gh[pousr]_[A-Za-z0-9]{20,}"),
            Regex("(?i)(authorization\\s*:\\s*(?:bearer|token|basic)\\s+)(?!\\$)[A-Za-z0-9._~+/=-]{12,}"))
    }
}
