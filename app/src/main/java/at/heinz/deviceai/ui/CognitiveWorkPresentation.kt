package at.heinz.deviceai.ui

/** Presentation only. No prompts, tool arguments, permissions, confidence scores or memory writes. */
object CognitiveWorkPresentation {
    enum class Phase(val label: String, val module: String, val color: Int) {
        READY("Bereit", "—", 0xff69c9bd.toInt()),
        LISTENING("Zuhören", "Sprache", 0xff54d8ff.toInt()),
        SPEAKING("Sprechen", "Sprache", 0xff7fafff.toInt()),
        THINKING("Antwort wird erarbeitet", "Lokales Gehirn", 0xffae9aff.toInt()),
        RESEARCH("Recherche läuft", "Forschung", 0xff60bfff.toInt()),
        LEARNING("Lernprüfung läuft", "Lernen", 0xffe4b865.toInt()),
        TOOL("Werkzeug läuft", "Werkzeugausführung", 0xff59d4dd.toInt()),
        NAVIGATION("App-Start wird geprüft", "Gerätesteuerung", 0xff59d4dd.toInt()),
        ANALYSIS("Bildschirm wird gelesen", "Gerätesteuerung", 0xff59d4dd.toInt()),
        VERIFIED("Ergebnis bestätigt", "Prüfung", 0xff69dfa3.toInt()),
        UNCONFIRMED("Antwort nicht unabhängig bestätigt", "Prüfung", 0xffe4b865.toInt()),
        CLARIFY("Rückfrage erforderlich", "Dialog", 0xffe4b865.toInt()),
        BLOCKED("Vorgang blockiert", "Freigabeprüfung", 0xffe4b865.toInt()),
        FAILED("Vorgang fehlgeschlagen", "—", 0xffff7f8c.toInt()),
        CANCELLED("Vorgang abgebrochen", "—", 0xffa9b4c9.toInt())
    }
    fun phase(listening: Boolean, speaking: Boolean, research: Boolean, learning: Boolean,
              busy: Boolean, tool: String?, outcome: String?): Phase = when {
        listening -> Phase.LISTENING
        speaking -> Phase.SPEAKING
        busy && tool == "launch_app" -> Phase.NAVIGATION
        busy && tool == "read_app_screen" -> Phase.ANALYSIS
        busy && tool != null -> Phase.TOOL
        research -> Phase.RESEARCH
        busy && learning -> Phase.LEARNING
        busy -> Phase.THINKING
        outcome == "ACTION_VERIFIED" || outcome == "MEMORY_EXACT" -> Phase.VERIFIED
        outcome == "FAILED" -> Phase.FAILED
        outcome == "CANCELLED" -> Phase.CANCELLED
        outcome == "BLOCKED" -> Phase.BLOCKED
        outcome == "CLARIFY" -> Phase.CLARIFY
        outcome == "MODEL_INTERPRETATION" || outcome == "WORKING_CONTEXT" -> Phase.UNCONFIRMED
        else -> Phase.READY
    }
    fun elapsed(start: Long?, now: Long): Long = if(start == null) 0 else (now-start).coerceAtLeast(0)
    // No known denominator in a model/tool turn: time spent is never a completion percentage.
    fun summary(phase: Phase, elapsedMillis: Long): String =
        "${phase.label}\n${phase.module} · ${elapsedMillis.coerceAtLeast(0)/1000}s · Fortschritt: nicht messbar"
}
