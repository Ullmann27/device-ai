package at.heinz.deviceai

internal class OwnerFaceConversation {
    private var comparisonToken: Long? = null
    private var greetingAttempted = false

    fun beginComparison(sessionToken: Long) {
        comparisonToken = sessionToken
        greetingAttempted = false
    }

    fun stop() {
        comparisonToken = null
        greetingAttempted = false
    }

    private fun current(sessionToken: Long, state: OwnerFaceStatus) =
        comparisonToken == sessionToken && state.sessionActive && state.hasProfile

    fun canContinue(sessionToken: Long, state: OwnerFaceStatus): Boolean = current(sessionToken, state) &&
        state.phase in setOf(OwnerFacePhase.RECOGNIZING, OwnerFacePhase.MATCH, OwnerFacePhase.NO_MATCH,
            OwnerFacePhase.NO_FACE, OwnerFacePhase.MULTIPLE_FACES, OwnerFacePhase.LOW_QUALITY)

    fun greeting(sessionToken: Long, state: OwnerFaceStatus, voiceAvailable: Boolean): String? {
        if (!current(sessionToken, state) || state.phase != OwnerFacePhase.MATCH || greetingAttempted) return null
        greetingAttempted = true
        if (!voiceAvailable) return null
        val name = state.displayName?.takeIf(OwnerFaceProfile::validName) ?: return null
        return "Hallo, $name. Schön, dich zu sehen."
    }

    fun statusLine(sessionToken: Long, state: OwnerFaceStatus): String? {
        if (!current(sessionToken, state)) return null
        return when (state.phase) {
            OwnerFacePhase.MATCH -> "Vorlage passt: ${state.displayName?.take(24) ?: "—"}"
            OwnerFacePhase.NO_MATCH -> "Keine Übereinstimmung"
            OwnerFacePhase.NO_FACE -> "Kein Gesicht im Bild"
            OwnerFacePhase.MULTIPLE_FACES -> "Mehrere Gesichter im Bild"
            OwnerFacePhase.LOW_QUALITY -> "Bild noch nicht eindeutig"
            OwnerFacePhase.LOADING -> "Lokaler Vergleich startet"
            OwnerFacePhase.RECOGNIZING -> "Lokaler Vergleich läuft"
            else -> null
        }
    }
}
