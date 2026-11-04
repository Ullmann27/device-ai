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
    private var lastRangeAt = Long.MIN_VALUE
    private var lastRangeStart = -1
    private var lastRangeEnd = -1
    private var lastRangeAudioFrame = -1
    private var lastWordCues: List<AvatarEvent.VisemeCue> = emptyList()
    // AgentActivity admits callbacks only for its current, unique TTS id. Keep a bounded
    // recent-terminal guard here as well, so replayed starts cannot reset that live speech.
    private val retiredUtterances = LinkedHashSet<String>()

    @Synchronized fun attach(newSink: AvatarSink) {
        sink = newSink
        emit(stateEvent(state))
        // A Fold/layout rebuild may replace the sink while the same TTS utterance is active.
        utterance?.let { id ->
            val at = clock()
            emit(AvatarEvent.SpeechStarted(id, text, at))
            // A reparent clears renderer-local speech. Restore the accepted word at its
            // original times; do not restart it or weaken the callback replay guard.
            lastWordCues = if (at < lastRangeAt) emptyList() else lastWordCues.filter {
                it.atMillis > at || at - it.atMillis < it.durationMs
            }
            lastWordCues.forEach(::emit)
        }
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

    /** Adapter computes age using elapsedRealtime; do not mix its timestamp with this clock.
     * Permission and camera lifecycle remain with the owner-controlled hardware adapter. */
    @Synchronized fun onObservedGaze(gaze: GazeTarget, confidence: Int, ageMillis: Long): Boolean {
        if (!AvatarPoseReducer.permitsObservedGaze(state) || !gaze.x.isFinite() || !gaze.y.isFinite() ||
            confidence !in AvatarPoseReducer.MIN_GAZE_CONFIDENCE..100 ||
            ageMillis !in 0..AvatarPoseReducer.OBSERVED_GAZE_TTL_MS) {
            clearObservedGaze()
            return false
        }
        emit(AvatarEvent.GazeObserved(gaze, confidence, clock() - ageMillis))
        return true
    }

    @Synchronized fun clearObservedGaze() = emit(AvatarEvent.GazeCleared(clock()))

    /** Returns admission independently of rendering, so the caller can share it with PCM state. */
    @Synchronized fun speechStarted(utteranceId: String, text: String): Boolean {
        if (utteranceId.isBlank() || utteranceId == utterance || utteranceId in retiredUtterances) return false
        utterance?.let(::retireUtterance)
        utterance = utteranceId
        lastRangeAt = Long.MIN_VALUE
        lastRangeStart = -1
        lastRangeEnd = -1
        lastRangeAudioFrame = -1
        lastWordCues = emptyList()
        this.text = text.take(MAX_TEXT)
        emit(AvatarEvent.SpeechStarted(utteranceId, this.text, clock()))
        return true
    }

    /** Android TTS text range; the optional audio frame orders callbacks, not phonemes. */
    @Synchronized fun wordRange(utteranceId: String, start: Int, end: Int, audioFrame: Int? = null): Boolean {
        if (utteranceId != utterance || start < 0 || end <= start || end > text.length) return false
        if (start < lastRangeStart || (start == lastRangeStart && end <= lastRangeEnd)) return false
        // Some engines assign one audio marker to adjacent text ranges. Text ordering
        // resolves those ties; only a genuinely backward audio marker is stale.
        if (audioFrame != null && (audioFrame < 0 || audioFrame < lastRangeAudioFrame)) return false
        lastRangeStart = start
        lastRangeEnd = end
        if (audioFrame != null) lastRangeAudioFrame = audioFrame
        lastWordCues = emptyList()
        val visemes = GermanVisemeApproximation.visemes(text.substring(start, end))
        // Punctuation still advances the engine's admitted text/frame markers. It must
        // not invent mouth cues or extend the previous word's no-PCM fallback interval.
        if (visemes.isEmpty()) return true
        val per = (WORD_MS / visemes.size).coerceAtLeast(MIN_VISEME_MS)
        val at = clock()
        lastRangeAt = at
        val wordCues = visemes.mapIndexed { i, v ->
            AvatarEvent.VisemeCue(utteranceId, v, if (v == Viseme.SIL) 0f else 1f, per, at + i * per)
        }
        lastWordCues = wordCues.takeLast(MAX_REATTACHED_CUES)
        wordCues.forEach(::emit)
        return true
    }

    @Synchronized fun audioLevel(utteranceId: String, level: Float) {
        if (utteranceId != utterance || !level.isFinite()) return
        val at = clock()
        val measured = level.coerceIn(0f, 1f)
        // Avatar lab (#338): engines that report word ranges but no PCM get coarse word motion,
        // explicitly flagged so a renderer never mistakes it for measured audio.
        val sinceRange = at - lastRangeAt
        if (measured == 0f && lastRangeAt != Long.MIN_VALUE && sinceRange in 0 until FALLBACK_MS) {
            val motion = 0.10f + 0.18f * kotlin.math.abs(kotlin.math.sin(sinceRange / 75f))
            emit(AvatarEvent.AudioLevel(utteranceId, motion, at, AudioLevelSource.WORD_RANGE_FALLBACK))
        } else emit(AvatarEvent.AudioLevel(utteranceId, measured, at, AudioLevelSource.PCM_ENVELOPE))
    }

    @Synchronized fun speechEnded(utteranceId: String, interrupted: Boolean) {
        if (utteranceId != utterance) return
        retireUtterance(utteranceId)
        utterance = null; text = ""
        lastWordCues = emptyList()
        emit(AvatarEvent.SpeechEnded(utteranceId, interrupted, clock()))
    }

    private fun retireUtterance(utteranceId: String) {
        retiredUtterances += utteranceId
        if (retiredUtterances.size > MAX_RETIRED_UTTERANCES) {
            val oldest = retiredUtterances.iterator()
            oldest.next()
            oldest.remove()
        }
    }

    private fun stateEvent(s: AvatarState): AvatarEvent.StateChanged {
        val (expression, gaze) = when (s) {
            AvatarState.IDLE -> AvatarExpression.WARM to GazeTarget.OWNER
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
        const val MAX_RETIRED_UTTERANCES = 128
        const val MAX_REATTACHED_CUES = 64
        const val WORD_MS = 320L
        const val MIN_VISEME_MS = 40L
        const val FALLBACK_MS = 500L
    }
}

/**
 * Coarse German grapheme→viseme approximation. Android TTS reports word ranges, not phonemes, so
 * this is deliberately approximate: SIL at word onset, consecutive duplicates collapsed, 'h' and
 * non-letters silent. A graphics side with its own phoneme source may ignore these cues.
 */
object GermanVisemeApproximation {
    // Diphthongs follow met4citizen/TalkingHead lipsync-de.mjs (MIT), pinned in PR #350:
    // [AU]=aa U, [AI]/[EI]=aa I, [EU]/[ÄU]=O.
    private val rules = listOf(
        "sch" to listOf(Viseme.CH), "tsch" to listOf(Viseme.CH), "ch" to listOf(Viseme.KK), "ck" to listOf(Viseme.KK),
        "ng" to listOf(Viseme.NN),
        "ie" to listOf(Viseme.I), "ei" to listOf(Viseme.AA, Viseme.I), "ai" to listOf(Viseme.AA, Viseme.I),
        "au" to listOf(Viseme.AA, Viseme.U), "eu" to listOf(Viseme.O), "äu" to listOf(Viseme.O),
        "pf" to listOf(Viseme.PP), "qu" to listOf(Viseme.KK), "th" to listOf(Viseme.DD), "ph" to listOf(Viseme.FF)
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
            val vs = rule?.second ?: listOfNotNull(single[w[i]])
            i += rule?.first?.length ?: 1
            vs.forEach { v -> if (v != out.last()) out += v }
        }
        return out
    }
}
