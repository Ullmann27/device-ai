package at.heinz.deviceai.localmodel

/**
 * Owner switch „Gespräch im Modell behalten“ (docs/research/LOCAL-MODEL-SPEED-20261118.md, section 9, stage 2).
 *
 * Without the switch every question closes the native LiteRT-LM conversation and a new one reads the complete
 * prompt again (instructions, context, history). With the switch the tool-free conversation of the owner's plain
 * text dialogue stays open between two turns, so a follow-up turn only reads what is new. LiteRT-LM 0.18.0 appends
 * a turn to a live conversation (host probe in the research folder); it has NOT been run on a device by this
 * project and every owner text says so.
 *
 * Off by default. This file holds the shared constants, the strict native budget and the request/receipt pair of
 * the runtime entry; [LocalDialogueReuseController] decides per turn, [LocalDialogueReusePrompt] derives the
 * follow-up text.
 */
object LocalDialogueReuse {
    const val PREFERENCES = "continuum.dialogue_reuse"
    const val KEY_ENABLED = "enabled"

    const val EXPERIMENTAL = LocalModelAcceleration.EXPERIMENTAL
    const val LABEL = "Gespräch im Modell behalten (schneller, $EXPERIMENTAL)"

    /** One kept conversation carries at most this many owner turns (the first one included); then it is rebuilt. */
    const val MAX_KEPT_TURNS = 6
    /**
     * Answer tokens a follow-up turn must leave free, twice the planning minimum of a fresh prompt
     * ([LocalNativePromptBudget.MIN_ANSWER_RESERVE]). Below it the turn is built fresh as before.
     */
    const val ANSWER_RESERVE_TOKENS = 1_024
    /** Turn markers of the chat template; the same reserve the fresh path plans with. */
    const val FRAMING_RESERVE_TOKENS = LocalNativePromptBudget.FRAMING_RESERVE

    /** Receipt reasons of the runtime side (the controller maps them to its own reason codes). */
    const val RUNTIME_WITHOUT_REUSE = "RUNTIME_WITHOUT_REUSE"
    const val RUNTIME_NO_KEPT_CONVERSATION = "RUNTIME_NO_KEPT_CONVERSATION"
    const val RUNTIME_CONTEXT_NEAR_LIMIT = "RUNTIME_CONTEXT_NEAR_LIMIT"

    /**
     * Output tokens for a follow-up of [followUpBytes] on a conversation that already holds [existingTokens], or
     * null when it does not fit with the answer reserve. One UTF-8 byte is counted as one token: a token is never
     * shorter than a byte, so this is an upper bound and the native prefill cannot overflow where this admits.
     * A missing or zero native count is never treated as "empty": it refuses.
     */
    fun outputTokens(followUpBytes: Int, existingTokens: Int?, contextTokens: Int, requestedOutput: Int): Int? {
        if (followUpBytes !in 1..LocalToolContinuation.MAX_INPUT_BYTES || existingTokens == null ||
            existingTokens <= 0 || contextTokens <= 0 || requestedOutput < 1) return null
        val available = contextTokens.toLong() - existingTokens - followUpBytes - FRAMING_RESERVE_TOKENS
        if (available < minOf(requestedOutput, ANSWER_RESERVE_TOKENS)) return null
        return minOf(requestedOutput.toLong(), available).toInt()
    }

    fun actionLabel(enabled: Boolean): String =
        if (enabled) "GESPRÄCH IM MODELL BEHALTEN · AUSSCHALTEN (STANDARD)"
        else "GESPRÄCH IM MODELL BEHALTEN · EINSCHALTEN (EXPERIMENTELL)"

    const val CONFIRM_TITLE = "Gespräch im Modell behalten ($EXPERIMENTAL)"
    const val CONFIRM_MESSAGE = "Bei aufeinanderfolgenden reinen Textfragen im selben Chat bleibt das Gespräch im " +
        "lokalen Modell geöffnet; eine Anschlussfrage liest dann nur noch die neue Nachricht ein statt den ganzen " +
        "Verlauf. Dieser Weg ist auf deinem Gerät noch nie gelaufen: Geschwindigkeit, Wärme, Speicherbedarf und " +
        "Antwortqualität sind unbekannt. Frühere Fragen und Antworten stehen dabei ungekürzt im Modellkontext und " +
        "werden nicht mehr je Zug neu gefiltert. Bei Stopp, Fehler, Hitzeschutz, Chatwechsel, Werkzeug-, Dokument- " +
        "oder Erinnerungszügen, geänderten Erinnerungen und nach höchstens $MAX_KEPT_TURNS Zügen wird das Gespräch " +
        "wie bisher neu aufgebaut. Hitzeschutz und Speicherprüfung bleiben aktiv."

    /** Model-centre status: switch position, where it applies and the last decisions of this app session. */
    fun statusText(enabled: Boolean, claudeSelected: Boolean, recent: List<LocalDialogueReuseController.LogEntry>): String =
        buildString {
            append(LABEL).append(": ")
            append(if (enabled) "EIN" else "AUS (Standard)")
            append(". ")
            append(when {
                claudeSelected -> "Claude (Cloud) ist als Gehirn gewählt: der Schalter wirkt nur auf das lokale Modell."
                enabled -> "Nur reine Textfragen im selben Chat lesen in einer Anschlussfrage bloß die neue Nachricht ein; " +
                    "alles andere läuft wie bisher in einem frischen Gespräch."
                else -> "Jede Frage liest den vollständigen Prompt neu ein (bisheriges Verhalten)."
            })
            if (recent.isNotEmpty()) {
                append("\nLetzte Züge (neueste zuerst):")
                recent.asReversed().take(6).forEach { append("\n· ").append(it.ownerLine()) }
            }
            append("\nOb das auf diesem Gerät schneller oder kühler ist, hat noch niemand gemessen; " +
                "die Gesprächs-Zeitprobe misst beide Wege nacheinander.")
        }
}

/**
 * What the runtime really did with one [LocalDialogueRequest]. Written by the runtime before the terminal event
 * of that generation is delivered, read by the caller after it. The default of a runtime that cannot keep a
 * conversation is "not reused, not retained".
 */
class LocalDialogueReceipt {
    /** True when the follow-up was appended to the kept conversation; false when the complete prompt was sent. */
    @Volatile var reused: Boolean = false
        private set
    /** Why a requested continuation was not possible; null when it was reused or none was requested. */
    @Volatile var reason: String? = null
        private set
    /** Native token count of the kept conversation before this turn; null when unknown or not reused. */
    @Volatile var tokensBefore: Int? = null
        private set
    /** True when the conversation of this turn stays open under the request's key. */
    @Volatile var retained: Boolean = false
        private set
    /** Native token count after the answer; null when unknown or not retained. */
    @Volatile var tokensAfter: Int? = null
        private set

    fun dispatched(reused: Boolean, reason: String?, tokensBefore: Int?) {
        this.reused = reused
        this.reason = reason
        this.tokensBefore = tokensBefore
    }

    fun retained(tokensAfter: Int?) {
        this.tokensAfter = tokensAfter
        retained = true
    }
}

/**
 * One plain text dialogue turn for [LocalModelRuntime.streamDialogue].
 *
 * [freshPrompt] is always the complete prompt the app sends today. [followUp] is only the new part and is used
 * when – and only when – the runtime still holds the live conversation kept under [conversationKey] and the
 * follow-up fits; otherwise the same call answers from [freshPrompt] in a new conversation. A null [followUp]
 * asks for a new conversation. On a completed answer the conversation of this turn is kept under the key.
 */
class LocalDialogueRequest(
    val conversationKey: String,
    val freshPrompt: String,
    val followUp: String?,
    val receipt: LocalDialogueReceipt = LocalDialogueReceipt()
) {
    init {
        require(conversationKey.isNotBlank() && conversationKey.length <= 64)
        require(freshPrompt.isNotBlank() && (followUp == null || followUp.isNotBlank()))
    }
}
