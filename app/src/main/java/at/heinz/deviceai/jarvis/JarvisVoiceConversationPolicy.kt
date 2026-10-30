package at.heinz.deviceai.jarvis

/**
 * A speech recognition hypothesis is untrusted data, NOT an authenticated owner command.
 * This gate permits an explicitly enabled, tap-initiated, tool-free dialogue turn only.
 * Everything else remains an editable draft for normal owner review.
 */
enum class JarvisVoiceRouting { DIALOGUE_ONLY, MANUAL_REVIEW, IGNORE }

data class JarvisVoiceDecision(val routing: JarvisVoiceRouting, val cleanText: String)

object JarvisVoiceConversationPolicy {
    private val speechPrefix = Regex("(?iu)^(?:(?:hey|hallo)\\s+)?jarvis[,!:]?\\s+")
    private val followUpPrefix = Regex("(?iu)^(?:und|aber)\\s+")
    private val knowledgeQuestion = Regex("(?iu)^(?:was|warum|wieso|weshalb|wer|wen|wem|wo|woher|wohin|wann|wie|welche|welcher|welches|wieviel|wie\\s+viel|wie\\s+viele)\\b")
    private val explanation = Regex("(?iu)^(?:erklär(?:e)?\\s+mir|erzähl(?:e)?\\s+mir)\\b")
    private val greeting = Regex("(?iu)^(?:hallo|(?:hallo|hey)\\s+jarvis|guten\\s+(?:morgen|tag|abend)|danke|vielen\\s+dank|wie\\s+geht\\s+es\\s+dir)[.!? ]*$")
    private val forbidden = Regex(
        "(?iu)\\b(?:löschen?|lösch(?:e|en)?|entfern(?:e|en)?|vergiss|vergessen|" +
            "merk(?:e|en)?|speicher(?:n|e)?|installier(?:e|en)?|aktivier(?:e|en)?|" +
            "deaktivier(?:e|en)?|start(?:e|en)?|stopp(?:e|en)?|abschalt(?:en|e)?|" +
            "einschalt(?:en|e)?|schalt(?:e|en)?|send(?:e|en)?|schreib(?:e|en)?|" +
            "öffn(?:e|en)?|überweis(?:e|en)?|bestell(?:e|en)?|buch(?:e|en)?|" +
            "kauf(?:e|en)?|ruf(?:e|en)?|anruf(?:e|en)?|führ(?:e|en)?|" +
            "push|commit|freigeben|freigabe|genehmig(?:e|en)?|" +
            "passwort|passwörter|pin|api[- ]?key|token|geheimnis|" +
            "herd|kochfeld|smartthings|home assistant|gerät(?:e|en)?|" +
            "schloss|alarm|kamera|mikrofon|datei(?:en)?|bankkonto|bankkonten|konto|bank|browser|internet|adresse|anschrift|iban|kontonummer|telefonnummer|geburtsdatum|sozialversicherungsnummer|wohnort)\\b"
    )

    fun evaluate(recognized: String?): JarvisVoiceDecision {
        if (recognized == null) return JarvisVoiceDecision(JarvisVoiceRouting.IGNORE, "")
        // Do not silently rewrite multi-line instructions or replace control characters.
        val text = recognized.trim()
        if (text.isBlank()) return JarvisVoiceDecision(JarvisVoiceRouting.IGNORE, "")
        if (text.length > 320 || text.any { it.isISOControl() } || text.contains('`') ||
            text.contains('{') || text.contains('}') || text.contains(';'))
            return JarvisVoiceDecision(JarvisVoiceRouting.MANUAL_REVIEW, text.take(320))
        val trimmed = text.replace(Regex("\\s+"), " ").trim()
        if (forbidden.containsMatchIn(trimmed))
            return JarvisVoiceDecision(JarvisVoiceRouting.MANUAL_REVIEW, trimmed)
        var question = speechPrefix.replaceFirst(trimmed, "")
        question = followUpPrefix.replaceFirst(question, "")
        val safe = greeting.matches(question) ||
            knowledgeQuestion.containsMatchIn(question) || explanation.containsMatchIn(question)
        return JarvisVoiceDecision(
            if (safe) JarvisVoiceRouting.DIALOGUE_ONLY else JarvisVoiceRouting.MANUAL_REVIEW,
            trimmed
        )
    }
}

/** What one recognizer result may do. Only AUTO_DIALOGUE starts a turn; DRAFT leaves editable text. */
enum class JarvisVoiceDispatch { AUTO_DIALOGUE, DRAFT, IGNORE }

object JarvisVoiceDispatchGate {
    /**
     * Android delivers onActivityResult immediately BEFORE onResume, so a foreground flag cleared in onPause is
     * always false here and must not be a condition (that made every DIALOGUE_ONLY result a draft). The answer's
     * speech is still gated on foreground later. [startedInSession] binds the result to the chat whose explicit
     * mic tap started it; a result for another (or unknown) chat never starts a turn.
     */
    fun decide(
        decision: JarvisVoiceDecision, busy: Boolean, executorShutdown: Boolean, activityAlive: Boolean,
        startedInSession: String?, currentSession: String
    ): JarvisVoiceDispatch = when {
        decision.routing == JarvisVoiceRouting.IGNORE -> JarvisVoiceDispatch.IGNORE
        // A result from a closed or unknown chat is discarded, not drafted into the next chat.
        startedInSession == null || startedInSession != currentSession -> JarvisVoiceDispatch.IGNORE
        decision.routing == JarvisVoiceRouting.DIALOGUE_ONLY && !busy && !executorShutdown && activityAlive &&
            startedInSession != null && startedInSession == currentSession -> JarvisVoiceDispatch.AUTO_DIALOGUE
        else -> JarvisVoiceDispatch.DRAFT
    }
}
