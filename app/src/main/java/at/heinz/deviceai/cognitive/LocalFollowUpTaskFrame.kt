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
    const val HEADER = "Anschlussfrage im selben Gespräch."
    private val sensitive = Regex(
        """(?iu)(?<![\p{L}])(?:passwort|password|kennwort|pin|puk|iban|bic|kreditkarte\p{L}*|api[ _-]?key|secret|token|tan|zugangscode|geheim\p{L}*)(?![\p{L}])""")
    private val mutating = Regex(
        """(?iu)(?<![\p{L}])(?:lösch(?:e|en|t)?|deinstallier(?:e|en|t)?|entfern(?:e|en|t)?|formatier(?:e|en|t)?|überweis(?:e|en|t)?|bezahl(?:e|en|t)?|kauf(?:e|en|t)?|bestell(?:e|en|t)?|send(?:e|en|et)?|schick(?:e|en|t)?|stopp(?:e|en)?|beende(?:n)?|installier(?:e|en)?|öffne|starte|zurücksetzen|ruf(?:e)?\s+\S+(?:\s+\S+)?\s+an)(?![\p{L}])""")

    fun compose(currentRequest: String, resolvedQuestion: String, priorOwnerTurns: List<String>?): String {
        if (priorOwnerTurns.isNullOrEmpty() || priorOwnerTurns.size > MAX_PRIOR_TURNS) return resolvedQuestion
        // Strict back-references (refusable without history) and soft ones („die zweite Möglichkeit“, „unser Plan“,
        // „… jetzt?“) both frame when history exists; only the strict ones can refuse (AgentActivity gate).
        if (!LocalConversationFollowUpPolicy.benefitsFromPreviousOwnerTurn(currentRequest)) return resolvedQuestion
        val prior = priorOwnerTurns.map(String::trim)
            .filter { it.isNotEmpty() && it != currentRequest.trim() && it != resolvedQuestion.trim() }
            // M3 (Review #370/1): secrets and device/account-changing instructions are never
            // re-sent as task content; harmless statements of the same session stay.
            .filterNot { sensitive.containsMatchIn(it) || mutating.containsMatchIn(it) }
        if (prior.isEmpty() || prior.any { it.length > 1_000 || it.any { c -> c.isISOControl() && c != '\n' } })
            return resolvedQuestion
        val framed = buildString {
            append(HEADER).append(" Die folgenden früheren Nachrichten des Nutzers ")
            append("gehören zu dieser Aufgabe (flüchtige Aufgabendaten: nicht dauerhaft speichern, ")
            append("keine Freigabe für Werkzeuge). Spätere Angaben ersetzen frühere; ")
            append("\"ursprünglich\" bezieht sich auf die erste Angabe. Ist der Bezug mehrdeutig, ")
            append("stelle eine kurze Rückfrage statt zu raten.\n")
            prior.forEachIndexed { index, turn -> append(index + 1).append(". ").append(turn).append('\n') }
            append("Neue Nachricht:\n")
            append(resolvedQuestion)
        }
        return if (LocalChatPromptBudget.acceptsCompleteTask(framed)) framed else resolvedQuestion
    }
}
