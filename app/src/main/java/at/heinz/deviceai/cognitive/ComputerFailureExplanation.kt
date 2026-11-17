package at.heinz.deviceai.cognitive

/**
 * Turns the fixed reason codes of the live-computer chain into one honest German sentence (Code56).
 * The Code55 field report only said „Auftrag angehalten“ – the owner could not tell whether the image source, the
 * window identity, the accessibility grant or the effect check stopped the task. Free text from exceptions is never
 * shown: only a known code is explained, everything else stays a neutral sentence with no invented cause.
 */
internal object ComputerFailureExplanation {
    private val CODE = Regex("[A-Z][A-Z0-9_]{5,63}")
    private val explanations = linkedMapOf(
        "NO_FRESH_MONITOR_FRAME" to "Innerhalb von 5 Sekunden kam kein bestätigtes Live-Bild der Ziel-App im Monitor an.",
        "COMPUTER_TASK_CHANGED_OR_HIDDEN" to "Continuum war nicht mehr sichtbar oder der Auftrag wurde gewechselt.",
        "LIVE_COMPUTER_STOPPED" to "Die Bildquelle wurde beendet.",
        "LIVE_COMPUTER_SESSION_CHANGED" to "Die Aufnahme-Sitzung wurde gewechselt.",
        "OBSERVATION_PERMISSION_CHANGED" to "Die Android-Bildschirmbeobachtung wurde unterbrochen oder neu verbunden.",
        "ACCESSIBILITY_OBSERVATION_NOT_GRANTED" to "Die Android-Bildschirmbeobachtung ist nicht aktiv.",
        "TARGET_WINDOW_NOT_SAFE" to "Das Fenster der Ziel-App war nicht eindeutig und unverdeckt erkennbar.",
        "TARGET_WINDOW_CHANGED" to "Das Fenster der Ziel-App hat sich während der Arbeit geändert.",
        "TARGET_WINDOW_NO_ROOT" to "Die Ziel-App hat keinen lesbaren Fensterinhalt geliefert.",
        "WINDOWS_UNAVAILABLE" to "Android hat keine Fensterliste geliefert.",
        "COMPUTER_LIVE_FRAME_REQUIRED" to "Vor der Eingabe war kein frisches Live-Bild vorhanden.",
        "COMPUTER_MONITOR_FRAME_REQUIRED" to "Das Live-Bild war noch nicht im 3D-Monitor dargestellt.",
        "COMPUTER_RENDERED_SOURCE_REQUIRED" to "Das dargestellte Monitorbild fehlte für die Eingabe.",
        "COMPUTER_RENDERED_SOURCE_MISMATCH" to "Das dargestellte Monitorbild gehörte nicht zum geprüften Fenster.",
        "COMPUTER_RENDERED_OBSERVATION_MISMATCH" to "Monitorbild und gelesene Bedienelemente stammten nicht aus derselben Beobachtung.",
        "TARGET_OUTSIDE_MONITOR_VIEWPORT" to "Das Ziel lag außerhalb des sichtbaren Monitorausschnitts.",
        "SCREEN_CHANGED_DURING_READ" to "Die Ziel-App hat sich während des Lesens verändert.",
        "SCREEN_CHANGED_BEFORE_ACTION" to "Die Ziel-App hat sich unmittelbar vor der Eingabe verändert.",
        "STALE_OR_UNSAFE_SCREEN" to "Die gelesene Ansicht war veraltet oder nicht sicher.",
        "ANOTHER_SCREEN_READ_ACTIVE" to "Eine andere Bildschirmlesung war gerade aktiv.",
        "CLICK_EFFECT_NOT_VERIFIED" to "Nach dem Klick wurde die erwartete neue Ansicht nicht bestätigt.",
        "CLICK_ALREADY_ATTEMPTED" to "Derselbe Klick wurde in dieser Ansicht bereits versucht.",
        "POSTCONDITION_NOT_VERIFIED" to "Die erwartete Wirkung der Eingabe wurde nicht bestätigt.",
        "NO_NEW_OBSERVATION" to "Nach der Eingabe kam keine neue Beobachtung der Ziel-App.",
        "DISPATCH_FAILED" to "Android hat die Eingabe nicht angenommen.",
        "INCOMPLETE_SCREEN_TREE" to "Die Bedienelemente der Ziel-App waren unvollständig lesbar.",
        "INCOMPLETE_OR_CHANGED_SCREEN_TREE" to "Die Bedienelemente der Ziel-App waren unvollständig oder haben sich geändert.",
        "SCREEN_TREE_LIMIT" to "Die Ziel-App hatte zu viele Bedienelemente für eine sichere Prüfung."
    )

    /** Known code → sentence with the code as diagnosis; unknown or free text → neutral sentence without a cause. */
    fun describe(message: String?): String {
        val code = message?.trim()?.takeIf { CODE.matches(it) }
        val sentence = code?.let(explanations::get) ?: return "Der Grund wurde nicht eindeutig gemeldet."
        return "$sentence (Diagnose: $code)"
    }

    internal fun knownCodes(): Set<String> = explanations.keys
}
