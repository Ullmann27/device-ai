package at.heinz.deviceai.localmodel

import java.util.UUID

/**
 * Decides, turn by turn, whether the kept native conversation of the owner's plain text dialogue may be continued
 * (owner switch „Gespräch im Modell behalten“, see [LocalDialogueReuse]). Pure state machine: no Android, no
 * LiteRT-LM, no clock.
 *
 * Fail closed by construction:
 *  - a turn is continued only when the caller found no exclusion, the conversation was COMMITTED by the previous
 *    turn, and session, model file, backend, context window, context epoch (memory, identity, internet mode),
 *    owner revision and the dialogue the app itself holds are exactly what that turn left behind;
 *  - a planned turn that is not followed by [generationEnded] with a completed answer AND by [delivered] with
 *    exactly that answer shown and recorded leaves nothing to continue;
 *  - the runtime has the last word: its [LocalDialogueReceipt] says whether the follow-up was really appended.
 *
 * [seen] is the ordered text the kept conversation has received and produced; the tests use it to show that every
 * owner statement and every answer is in it exactly once.
 */
class LocalDialogueReuseController(
    private val maxTurns: Int = LocalDialogueReuse.MAX_KEPT_TURNS,
    private val newKey: () -> String = { UUID.randomUUID().toString() }
) {
    init { require(maxTurns in 1..16) }

    /** Why a turn was not continued. [ownerText] is the German wording of the model centre and the activity log. */
    enum class Reason(val ownerText: String) {
        SWITCH_OFF("Schalter aus"),
        CLOUD_BRAIN("Claude (Cloud) ist das gewählte Gehirn"),
        // Turn kinds that always run exactly as before.
        NOT_TEXT_ONLY("Zug mit Werkzeugen, Werkzeugbeobachtung oder Korrekturversuch"),
        BOT_OR_CODING("Fachbot- oder Coding-Auftrag"),
        DOCUMENT_DRAFT("Dokumententwurf"),
        CONTINUATION_SEGMENT("Fortsetzung einer abgeschnittenen Antwort"),
        GROUNDED_RECALL("Antwort aus belegten Erinnerungen"),
        DOCUMENT_REFERENCE("Bezug auf ein gelesenes Dokument"),
        OWNER_OBJECT_STATE("Besitzerangaben zu Gegenständen werden von der App aufgelöst"),
        OWNER_TIMELINE("Zeitlicher Bezug auf frühere Angaben wird von der App aufgelöst"),
        RESOLVED_BY_APP("Die App hat die Frage vor dem Modell umformuliert"),
        SCREEN_DATA("Bildschirmdaten im Zug"),
        RESEARCH_IN_SESSION("Recherchedaten in diesem Chat"),
        DECISION_DIGEST("Entscheidungsdaten im Zug"),
        HISTORY_INCOMPLETE("Gesprächsverlauf der App ist unvollständig"),
        CONTEXT_SHAPE_UNKNOWN("Kontextblock nicht eindeutig zerlegbar"),
        CONTEXT_WINDOW_UNKNOWN("Kontextfenster der Laufzeit unbekannt"),
        // No conversation to continue.
        NO_KEPT_CONVERSATION("noch kein behaltenes Gespräch"),
        SESSION_CHANGED("anderer Chat"),
        MODEL_CHANGED("anderes Modell"),
        BACKEND_CHANGED("anderer Beschleuniger oder anderes Kontextfenster"),
        CONTEXT_DATA_CHANGED("Erinnerungen, Identität oder Internetmodus geändert"),
        OWNER_INPUT_OUTSIDE_DIALOGUE("dazwischen lag eine Eingabe, die nicht über dieses Gespräch lief"),
        DIALOGUE_CHANGED_OUTSIDE_MODEL("der Gesprächsverlauf der App hat sich außerhalb des Modells geändert"),
        TURN_CAP("Höchstzahl behaltener Züge erreicht"),
        CONTEXT_NEAR_LIMIT("Kontextfenster fast voll"),
        // The previous turn left nothing to continue.
        GENERATION_CANCELLED("letzte Antwort abgebrochen"),
        GENERATION_FAILED("letzte Antwort fehlgeschlagen"),
        THERMAL("Hitzeschutz hat die letzte Antwort angehalten"),
        OUTPUT_LIMIT("letzte Antwort an der Ausgabegrenze abgeschnitten"),
        ANSWER_NOT_SHOWN_AS_GENERATED("letzte Antwort wurde zurückgehalten, ersetzt oder nicht unverändert gezeigt"),
        HISTORY_FIREWALL("letzter Zug enthält Text, den der Verlaufsfilter nicht weitergibt"),
        PREVIOUS_TURN_NOT_COMMITTED("letzter Zug wurde nicht abgeschlossen"),
        STOPPED("Stopp"),
        ACTIVITY_CLOSED("App-Fenster geschlossen"),
        // The runtime decided.
        RUNTIME_WITHOUT_REUSE("diese Laufzeit kann kein Gespräch behalten"),
        RUNTIME_NO_KEPT_CONVERSATION("die Laufzeit hält das Gespräch nicht mehr (z. B. Modell neu geladen)"),
        RUNTIME_CONTEXT_NEAR_LIMIT("Kontextfenster laut Laufzeit fast voll"),
        RUNTIME_NOT_RETAINED("die Laufzeit hat das Gespräch nach der Antwort nicht behalten")
    }

    /**
     * The facts of one model call that decide whether it is the owner's plain text dialogue turn. Everything that
     * is not – in this order – runs exactly as without the switch and ends a kept conversation.
     */
    data class TurnKind(
        val cloudBrain: Boolean,
        val botOrCoding: Boolean,
        val screenData: Boolean,
        /** The tool-free text route was chosen and no tool observation or correction retry is part of the call. */
        val plainTextRoute: Boolean,
        val documentDraft: Boolean,
        val continuation: Boolean,
        val groundedRecall: Boolean,
        val documentReference: Boolean,
        val ownerObjectState: Boolean,
        val resolvedByApp: Boolean,
        val historyComplete: Boolean,
        val ownerTimeline: Boolean,
        val research: Boolean,
        val decisionDigest: Boolean
    ) {
        fun exclusion(): Reason? = when {
            cloudBrain -> Reason.CLOUD_BRAIN
            botOrCoding -> Reason.BOT_OR_CODING
            screenData -> Reason.SCREEN_DATA
            !plainTextRoute -> Reason.NOT_TEXT_ONLY
            documentDraft -> Reason.DOCUMENT_DRAFT
            continuation -> Reason.CONTINUATION_SEGMENT
            groundedRecall -> Reason.GROUNDED_RECALL
            documentReference -> Reason.DOCUMENT_REFERENCE
            ownerObjectState -> Reason.OWNER_OBJECT_STATE
            resolvedByApp -> Reason.RESOLVED_BY_APP
            !historyComplete -> Reason.HISTORY_INCOMPLETE
            ownerTimeline -> Reason.OWNER_TIMELINE
            research -> Reason.RESEARCH_IN_SESSION
            decisionDigest -> Reason.DECISION_DIGEST
            else -> null
        }
    }

    /** Everything a kept conversation is bound to besides the dialogue itself. */
    data class Binding(
        val sessionId: String,
        val modelId: String,
        val modelVersion: String,
        val modelSha256: String,
        val backend: String?,
        val contextTokens: Int?,
        /** Fingerprint of memory store, identity and internet mode; any difference starts fresh. */
        val contextEpoch: String
    )

    /**
     * One model call of an owner turn as the caller is about to send it.
     * [exclusion]: the first reason this is not the owner's plain text dialogue turn, or null.
     * [dialogueBefore]: the complete dialogue the app holds for this chat before the turn.
     * [freshPrompt]: the complete prompt of this turn exactly as it is sent without the switch.
     * [evidence], [turnHint], [turnInstruction], [clockAnchor]: see [LocalDialogueReusePrompt.followUp].
     */
    data class Turn(
        val enabled: Boolean,
        val exclusion: Reason?,
        val binding: Binding,
        val ownerRevision: Long,
        val dialogueBefore: String,
        val ownerMessage: String,
        val freshPrompt: String,
        val evidence: String = "",
        val turnHint: String = "",
        val turnInstruction: String = "",
        val clockAnchor: String = ""
    )

    sealed class Decision {
        /** [request] null: run exactly as without the switch. Otherwise start a new kept conversation with it. */
        data class Fresh(val reason: Reason, val request: LocalDialogueRequest?) : Decision()
        /** Continue the kept conversation; [turnNumber] counts its owner turns including this one. */
        data class Reuse(val request: LocalDialogueRequest, val turnNumber: Int) : Decision()
    }

    enum class Outcome { COMPLETED, CANCELLED, FAILED, THERMAL, OUTPUT_LIMIT }
    enum class Role { OWNER_PROMPT, MODEL_ANSWER }
    data class Seen(val role: Role, val text: String)

    /** One decision as it really ran. [turnNumber] 0: not a kept turn. */
    data class LogEntry(val reused: Boolean, val reason: Reason?, val turnNumber: Int, val sentBytes: Int,
                        val tokensBefore: Int?, val tokensAfter: Int?) {
        fun code(): String = if (reused) "REUSED" else "FRESH:" + (reason?.name ?: "UNKNOWN")
        fun ownerLine(): String =
            if (reused) "weiterverwendet (Zug $turnNumber), $sentBytes Bytes gesendet"
            else "neu aufgebaut – " + (reason?.ownerText ?: "Grund unbekannt") +
                (if (sentBytes > 0) ", $sentBytes Bytes gesendet" else "")
    }

    private class Kept(val key: String, val binding: Binding) {
        val seen = mutableListOf<Seen>()
        var turns = 0
        var ownerRevision = 0L
        var dialogue = ""
        var evidence = ""
        var promptBytes = 0L
        var answerBytes = 0L
        var nativeTokens: Int? = null
        /** Three bytes per token is the planning estimate of the fresh path; the runtime's exact count wins. */
        fun tokens(): Int = nativeTokens ?: (LocalNativePromptBudget.TOOL_FREE_PREFACE_RESERVE +
            (promptBytes + 2) / 3 + (answerBytes + 2) / 3).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private class Pending(val request: LocalDialogueRequest, val turn: Turn, val base: Kept?, val reason: Reason?) {
        var answer: String? = null
    }

    private var kept: Kept? = null
    private var pending: Pending? = null
    private var dropped: Reason? = null
    private val log = ArrayDeque<LogEntry>()

    /** True while a committed conversation could be continued by the next eligible turn. */
    val holdsConversation: Boolean @Synchronized get() = kept != null && pending == null

    @Synchronized fun plan(turn: Turn): Decision {
        // A turn that was planned but never committed leaves a conversation in an unknown state.
        if (pending != null) drop(Reason.PREVIOUS_TURN_NOT_COMMITTED)
        if (!turn.enabled) return unchanged(Reason.SWITCH_OFF, record = false)
        turn.exclusion?.let { return unchanged(it, record = true) }
        val current = kept
        val window = turn.binding.contextTokens
        val followUp = current?.let {
            LocalDialogueReusePrompt.followUp(turn.ownerMessage, turn.evidence.takeIf { text -> text != it.evidence },
                turn.turnHint, turn.turnInstruction, turn.clockAnchor)
        }
        val reason = when {
            window == null || window <= 0 -> Reason.CONTEXT_WINDOW_UNKNOWN
            current == null || followUp == null -> dropped ?: Reason.NO_KEPT_CONVERSATION
            current.binding.sessionId != turn.binding.sessionId -> Reason.SESSION_CHANGED
            current.binding.modelSha256 != turn.binding.modelSha256 || current.binding.modelId != turn.binding.modelId ||
                current.binding.modelVersion != turn.binding.modelVersion -> Reason.MODEL_CHANGED
            current.binding.backend != turn.binding.backend ||
                current.binding.contextTokens != turn.binding.contextTokens -> Reason.BACKEND_CHANGED
            current.binding.contextEpoch != turn.binding.contextEpoch -> Reason.CONTEXT_DATA_CHANGED
            turn.ownerRevision != current.ownerRevision + 1 -> Reason.OWNER_INPUT_OUTSIDE_DIALOGUE
            turn.dialogueBefore != current.dialogue -> Reason.DIALOGUE_CHANGED_OUTSIDE_MODEL
            current.turns >= maxTurns -> Reason.TURN_CAP
            LocalDialogueReuse.outputTokens(bytes(followUp), current.tokens(), window,
                LocalDialogueReuse.ANSWER_RESERVE_TOKENS) == null -> Reason.CONTEXT_NEAR_LIMIT
            else -> null
        }
        dropped = null
        if (reason == Reason.CONTEXT_WINDOW_UNKNOWN) return unchanged(reason, record = true)
        if (reason != null || current == null || followUp == null) {
            kept = null
            val request = LocalDialogueRequest(newKey(), turn.freshPrompt, null)
            pending = Pending(request, turn, null, reason ?: Reason.NO_KEPT_CONVERSATION)
            return Decision.Fresh(reason ?: Reason.NO_KEPT_CONVERSATION, request)
        }
        val request = LocalDialogueRequest(current.key, turn.freshPrompt, followUp)
        pending = Pending(request, turn, current, null)
        return Decision.Reuse(request, current.turns + 1)
    }

    /**
     * The generation of [request] ended; anything but a completed, retained answer ends the conversation and
     * returns the log entry of that turn. null: the answer waits for [delivered] (or the request is unknown).
     */
    @Synchronized fun generationEnded(request: LocalDialogueRequest, outcome: Outcome, answer: String): LogEntry? {
        val planned = pending?.takeIf { it.request === request } ?: return null
        val receipt = request.receipt
        val failure = when {
            outcome == Outcome.CANCELLED -> Reason.GENERATION_CANCELLED
            outcome == Outcome.THERMAL -> Reason.THERMAL
            outcome == Outcome.OUTPUT_LIMIT -> Reason.OUTPUT_LIMIT
            outcome != Outcome.COMPLETED || answer.isBlank() -> Reason.GENERATION_FAILED
            !receipt.retained -> if (receipt.reason == LocalDialogueReuse.RUNTIME_WITHOUT_REUSE)
                Reason.RUNTIME_WITHOUT_REUSE else Reason.RUNTIME_NOT_RETAINED
            else -> null
        }
        if (failure != null) {
            val entry = record(planned, reused = receipt.reused,
                reason = if (receipt.reused) null else runtimeReason(planned))
            drop(failure)
            return entry
        }
        planned.answer = answer
        return null
    }

    /**
     * The turn of [request] was presented. [shown] is the text handed to the owner and [recorded] the answer the
     * app stored for this chat – each null when that did not happen. The conversation stays only when both are
     * exactly the generated answer; [dialogueAfter] is then the dialogue the next turn must still find.
     * Returns the log entry of the turn, or null when this request was not the pending one.
     */
    @Synchronized fun delivered(request: LocalDialogueRequest, shown: String?, recorded: String?,
                                dialogueAfter: String, historyAccepts: (String) -> Boolean = { true }): LogEntry? {
        val planned = pending?.takeIf { it.request === request } ?: return null
        val answer = planned.answer
        if (answer == null) { drop(Reason.PREVIOUS_TURN_NOT_COMMITTED); return null }
        val receipt = request.receipt
        val entry = record(planned, reused = receipt.reused, reason = if (receipt.reused) null else runtimeReason(planned))
        pending = null
        if (shown != answer || recorded != answer) { drop(Reason.ANSWER_NOT_SHOWN_AS_GENERATED, always = true); return entry }
        if (!historyAccepts(planned.turn.ownerMessage + "\n" + answer)) { drop(Reason.HISTORY_FIREWALL, always = true); return entry }
        val base = planned.base
        val conversation = if (receipt.reused && base != null && kept === base) base
            // The runtime answered from the complete prompt in a new conversation kept under the same key.
            else Kept(request.conversationKey, planned.turn.binding)
        val sent = if (conversation === base) request.followUp!! else request.freshPrompt
        conversation.seen += Seen(Role.OWNER_PROMPT, sent)
        conversation.seen += Seen(Role.MODEL_ANSWER, answer)
        conversation.turns++
        conversation.ownerRevision = planned.turn.ownerRevision
        conversation.dialogue = dialogueAfter
        // A fresh prompt carries this turn's evidence; a follow-up carries it or left the identical one in place.
        conversation.evidence = planned.turn.evidence
        conversation.promptBytes += bytes(sent)
        conversation.answerBytes += bytes(answer)
        conversation.nativeTokens = receipt.tokensAfter
        kept = conversation
        return entry
    }

    /** An event outside a turn ended the conversation (STOP, closed window, switch off). */
    @Synchronized fun invalidate(reason: Reason) = drop(reason)

    /** What the kept conversation has received and produced, oldest first; empty when none is kept. */
    @Synchronized fun seen(): List<Seen> = kept?.seen?.toList().orEmpty()

    @Synchronized fun recent(): List<LogEntry> = log.toList()

    private fun runtimeReason(planned: Pending): Reason = planned.reason ?: when (planned.request.receipt.reason) {
        LocalDialogueReuse.RUNTIME_WITHOUT_REUSE -> Reason.RUNTIME_WITHOUT_REUSE
        LocalDialogueReuse.RUNTIME_CONTEXT_NEAR_LIMIT -> Reason.RUNTIME_CONTEXT_NEAR_LIMIT
        else -> Reason.RUNTIME_NO_KEPT_CONVERSATION
    }

    private fun unchanged(reason: Reason, record: Boolean): Decision {
        drop(reason)
        // The reason a later eligible turn reports is why IT has nothing to continue, not this turn's kind.
        dropped = null
        if (record) append(LogEntry(false, reason, 0, 0, null, null))
        return Decision.Fresh(reason, null)
    }

    private fun record(planned: Pending, reused: Boolean, reason: Reason?): LogEntry {
        val request = planned.request
        val turnNumber = if (reused) (planned.base?.turns ?: 0) + 1 else 1
        val sent = if (reused) request.followUp.orEmpty() else request.freshPrompt
        return LogEntry(reused, reason, turnNumber, bytes(sent), request.receipt.tokensBefore, request.receipt.tokensAfter)
            .also(::append)
    }

    private fun append(entry: LogEntry) {
        log.addLast(entry)
        while (log.size > MAX_LOG) log.removeFirst()
    }

    private fun drop(reason: Reason, always: Boolean = false) {
        if (always || kept != null || pending != null) dropped = reason
        kept = null
        pending = null
    }

    private fun bytes(text: String): Int = text.toByteArray(Charsets.UTF_8).size

    private companion object {
        const val MAX_LOG = 12
    }
}
