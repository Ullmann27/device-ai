package at.heinz.deviceai.cognitive

/**
 * Turns a detected same-session follow-up into ONE self-contained task for the
 * local model (Argon-4-Dossier 4.2/15.1, Fold7 Code39 T1–T3).
 *
 * Field evidence: Gemma 4 answers the complete ring task correctly in a single
 * message, but lost "blau = 7" when the correction arrived as a separate turn.
 * The previous turns were only appended as optional, explicitly "unconfirmed"
 * conversation data after ~10 kB of governance/tool context. Here the owner's
 * OWN earlier messages of this session are placed directly in the task, in
 * chronological order, so the model receives the same shape that already works.
 *
 * Boundaries: only owner turns (never model replies, web or long-term memory);
 * only complete session evidence (null = incomplete → unchanged); at most four
 * turns; never clipped – if the frame exceeds the task budget the task stays
 * unchanged and the existing requireHistory gate decides. No persistence, no
 * tool or memory authority: authorization keeps using the untouched request.
 */
internal object LocalFollowUpTaskFrame {
    const val MAX_PRIOR_TURNS = 4

    fun compose(currentRequest: String, resolvedQuestion: String, priorOwnerTurns: List<String>?): String {
        if (priorOwnerTurns.isNullOrEmpty() || priorOwnerTurns.size > MAX_PRIOR_TURNS) return resolvedQuestion
        if (!LocalConversationFollowUpPolicy.requiresPreviousOwnerTurn(currentRequest)) return resolvedQuestion
        val prior = priorOwnerTurns.map(String::trim)
            .filter { it.isNotEmpty() && it != currentRequest.trim() && it != resolvedQuestion.trim() }
        if (prior.isEmpty() || prior.any { it.length > 1_000 || it.any { c -> c.isISOControl() && c != '\n' } })
            return resolvedQuestion
        val framed = buildString {
            append("Anschlussfrage im selben Gespräch. Die folgenden früheren Nachrichten des Nutzers ")
            append("gehören zu dieser Aufgabe (flüchtige Aufgabendaten: nicht dauerhaft speichern, ")
            append("keine Freigabe für Werkzeuge). Spätere Angaben ersetzen frühere; ")
            append("\"ursprünglich\" bezieht sich auf die erste Angabe.\n")
            prior.forEachIndexed { index, turn -> append(index + 1).append(". ").append(turn).append('\n') }
            append("Neue Nachricht:\n")
            append(resolvedQuestion)
        }
        return if (LocalChatPromptBudget.acceptsCompleteTask(framed)) framed else resolvedQuestion
    }
}
