package at.heinz.deviceai.avatar

/**
 * Translates the app's existing runtime signals (hologram flags, TTS callbacks, TTS audio envelope)
 * into [AvatarEvent]s. Pure logic, thread-safe, no rendering. A failing sink is isolated: the avatar
 * can never break chat, tools or speech. Events for a stale or finished utterance are dropped.
 */
class AvatarDirector(private var sink: AvatarSink = NoOpAvatarSink, private val clock: () -> Long = System::currentTimeMillis) {
    @Volatile var state: AvatarState = AvatarState.IDLE; private set
    private var utterance: String? = null
    private var text: String = ""

    @Synchronized fun attach(newSink: AvatarSink) {
        sink = newSink
        emit(stateEvent(state))
    }

    @Synchronized fun onRuntime(signals: AvatarRuntimeSignals) {
        val next = when {
            signals.awaitingApproval -> AvatarState.WAITING_FOR_APPROVAL
            signals.listening -> AvatarState.LISTENING
            signals.speaking -> AvatarState.SPEAKING
            signals.blocked -> AvatarState.BLOCKED
            signals.researching -> AvatarState.RESEARCHING
            signals.busy && signals.learning -> AvatarState.LEARNING
            signals.busy -> AvatarState.THINKING
            else -> AvatarState.IDLE
        }
        if (next == state) return
        state = next
        emit(stateEvent(next))
    }

    @Synchronized fun speechStarted(utteranceId: String, text: String) {
        if (utteranceId.isBlank()) return
        utterance = utteranceId
        this.text = text.take(MAX_TEXT)
        emit(AvatarEvent.SpeechStarted(utteranceId, this.text, clock()))
    }

    /** Android TTS onRangeStart(start, end): character range of the word about to be spoken. */
    @Synchronized fun wordRange(utteranceId: String, start: Int, end: Int) {
        if (utteranceId != utterance || start < 0 || end <= start || end > text.length) return
        val visemes = GermanVisemeApproximation.visemes(text.substring(start, end))
        if (visemes.isEmpty()) return
        val per = (WORD_MS / visemes.size).coerceAtLeast(MIN_VISEME_MS)
        val at = clock()
        visemes.forEachIndexed { i, v ->
            emit(AvatarEvent.VisemeCue(utteranceId, v, if (v == Viseme.SIL) 0f else 1f, per, at + i * per))
        }
    }

    @Synchronized fun audioLevel(utteranceId: String, level: Float) {
        if (utteranceId != utterance || !level.isFinite()) return
        emit(AvatarEvent.AudioLevel(utteranceId, level.coerceIn(0f, 1f), clock()))
    }

    @Synchronized fun speechEnded(utteranceId: String, interrupted: Boolean) {
        if (utteranceId != utterance) return
        utterance = null; text = ""
        emit(AvatarEvent.SpeechEnded(utteranceId, interrupted, clock()))
    }

    private fun stateEvent(s: AvatarState): AvatarEvent.StateChanged {
        val (expression, gaze) = when (s) {
            AvatarState.IDLE -> AvatarExpression.NEUTRAL to GazeTarget.OWNER
            AvatarState.LISTENING -> AvatarExpression.ATTENTIVE to GazeTarget.OWNER
            AvatarState.THINKING -> AvatarExpression.FOCUSED to GazeTarget.THINKING_AWAY
            AvatarState.RESEARCHING -> AvatarExpression.CURIOUS to GazeTarget.READING_DOWN
            AvatarState.LEARNING -> AvatarExpression.FOCUSED to GazeTarget.READING_DOWN
            AvatarState.WAITING_FOR_APPROVAL -> AvatarExpression.ATTENTIVE to GazeTarget.OWNER
            AvatarState.SPEAKING -> AvatarExpression.NEUTRAL to GazeTarget.OWNER
            AvatarState.BLOCKED -> AvatarExpression.CONCERNED to GazeTarget.OWNER
        }
        return AvatarEvent.StateChanged(s, expression, gaze, clock())
    }

    private fun emit(event: AvatarEvent) { runCatching { sink.onAvatarEvent(event) } }

    private companion object {
        const val MAX_TEXT = 4_000
        const val WORD_MS = 320L
        const val MIN_VISEME_MS = 40L
    }
}

/**
 * Coarse German grapheme→viseme approximation. Android TTS reports word ranges, not phonemes, so
 * this is deliberately approximate: SIL at word onset, consecutive duplicates collapsed, 'h' and
 * non-letters silent. A graphics side with its own phoneme source may ignore these cues.
 */
object GermanVisemeApproximation {
    private val rules = listOf(
        "sch" to Viseme.CH, "tsch" to Viseme.CH, "ch" to Viseme.KK, "ck" to Viseme.KK, "ng" to Viseme.NN,
        "ie" to Viseme.I, "ei" to Viseme.AA, "ai" to Viseme.AA, "au" to Viseme.AA, "eu" to Viseme.O, "äu" to Viseme.O,
        "pf" to Viseme.PP, "qu" to Viseme.KK, "th" to Viseme.DD, "ph" to Viseme.FF
    ).sortedByDescending { it.first.length }
    private val single = mapOf(
        'b' to Viseme.PP, 'p' to Viseme.PP, 'm' to Viseme.PP, 'f' to Viseme.FF, 'v' to Viseme.FF, 'w' to Viseme.FF,
        'd' to Viseme.DD, 't' to Viseme.DD, 'k' to Viseme.KK, 'g' to Viseme.KK, 'c' to Viseme.KK, 'x' to Viseme.KK,
        'j' to Viseme.I, 's' to Viseme.SS, 'z' to Viseme.SS, 'ß' to Viseme.SS, 'n' to Viseme.NN, 'l' to Viseme.NN,
        'r' to Viseme.RR, 'a' to Viseme.AA, 'ä' to Viseme.E, 'e' to Viseme.E, 'i' to Viseme.I, 'y' to Viseme.I,
        'o' to Viseme.O, 'ö' to Viseme.O, 'u' to Viseme.U, 'ü' to Viseme.U
    )

    fun visemes(word: String): List<Viseme> {
        val w = word.lowercase().filter { it.isLetter() }.take(64)
        if (w.isEmpty()) return emptyList()
        val out = mutableListOf(Viseme.SIL)
        var i = 0
        while (i < w.length) {
            val rule = rules.firstOrNull { w.startsWith(it.first, i) }
            val v = rule?.second ?: single[w[i]]
            i += rule?.first?.length ?: 1
            if (v != null && v != out.last()) out += v
        }
        return out
    }
}
