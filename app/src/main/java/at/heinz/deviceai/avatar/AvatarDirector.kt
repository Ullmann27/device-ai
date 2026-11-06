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
    private data class WindowIntent(val id: String, val gaze: GazeTarget, val atMillis: Long, val durationMs: Long,
                                    val yieldsToObservedGaze: Boolean, val reading: Boolean = false)
    private var windowIntent: WindowIntent? = null
    /** The window whose content real work is on (windowWorkStarted); outlives an interruption by listening/speech. */
    private var workIntent: WindowIntent? = null
    private var confirmationAtMillis = Long.MIN_VALUE
    private var lastConfirmationAtMillis = Long.MIN_VALUE
    private var animationRevision = 0L
    private var legacyIntentSerial = 0L
    private var thinkingAtMillis = Long.MIN_VALUE
    private val retiredWindows = LinkedHashSet<String>()
    private val seenConfirmations = LinkedHashSet<String>()

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
        if (animationRevision > 0) {
            val at = clock()
            expireAnimationIntents(at)
            publishAnimationIntents(at)
        }
    }

    @Synchronized fun onRuntime(signals: AvatarRuntimeSignals) {
        if ((signals.listening || signals.speaking) && windowIntent != null) {
            windowIntent?.let { remember(retiredWindows, it.id) }
            windowIntent = null
            publishAnimationIntents(clock())
        }
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
        val hadThinking = thinkingAtMillis != Long.MIN_VALUE
        state = next
        thinkingAtMillis = if (next == AvatarState.THINKING) clock() else Long.MIN_VALUE
        emit(stateEvent(next))
        val resumed = resumeWork()
        if (next == AvatarState.THINKING || hadThinking || resumed) publishAnimationIntents(clock())
    }

    /**
     * After an interruption (the owner spoke, JARVIS answered in between) the work that still runs gets his eyes back:
     * reading resumes on the same window. Never while listening or speaking, never after the work ended or expired.
     */
    private fun resumeWork(): Boolean {
        val work = workIntent ?: return false
        if (windowIntent != null || utterance != null || state == AvatarState.LISTENING || state == AvatarState.SPEAKING) return false
        val age = clock().toDouble() - work.atMillis.toDouble()
        if (age < 0 || age >= work.durationMs) { workIntent = null; return false }
        windowIntent = work
        thinkingAtMillis = Long.MIN_VALUE
        return true
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

    /** One glance per unique window. */
    @Synchronized fun windowOpened(windowId: String, target: GazeTarget = GazeTarget.WORK_WINDOW): Boolean =
        requestWindow(windowId, target, AvatarPoseReducer.WINDOW_GAZE_TTL_MS)

    private fun requestWindow(windowId: String, target: GazeTarget, durationMs: Long, yieldsToObservedGaze: Boolean = false): Boolean {
        val at = clock()
        expireAnimationIntents(at)
        if (windowId.isBlank() || windowId == windowIntent?.id || windowId in retiredWindows) return false
        if (state == AvatarState.LISTENING || state == AvatarState.SPEAKING || utterance != null) {
            remember(retiredWindows, windowId)
            return false
        }
        windowIntent?.let { remember(retiredWindows, it.id) }
        windowIntent = WindowIntent(windowId, GazeTarget(
            target.x.coerceIn(-AvatarPoseReducer.MAX_DIRECTED_GAZE_X, AvatarPoseReducer.MAX_DIRECTED_GAZE_X),
            target.y.coerceIn(-AvatarPoseReducer.MAX_DIRECTED_GAZE_Y, AvatarPoseReducer.MAX_DIRECTED_GAZE_Y)), at, durationMs, yieldsToObservedGaze)
        thinkingAtMillis = Long.MIN_VALUE
        publishAnimationIntents(at)
        return true
    }

    /** Legacy presentation adapter, shared admission. */
    @Synchronized fun onWindowPresented() {
        requestWindow("legacy-window-${++legacyIntentSerial}", GazeTarget(.42f, -.18f),
            AvatarPoseReducer.LEGACY_WINDOW_GAZE_TTL_MS, yieldsToObservedGaze = true)
    }

    /** Legacy explicit acceptance, shared cooldown. */
    @Synchronized fun onActionConfirmed() {
        confirmationAccepted("legacy-confirmation-${++legacyIntentSerial}")
    }

    /**
     * The open window [windowId] was replanned on the real size (rotation, fold, keyboard): the gaze follows it to its
     * new place. Same intent – its start and lifetime stay; a closed, expired or other window changes nothing.
     */
    @Synchronized fun windowMoved(windowId: String, target: GazeTarget): Boolean {
        val at = clock()
        expireAnimationIntents(at)
        val clamped = GazeTarget(target.x.coerceIn(-AvatarPoseReducer.MAX_DIRECTED_GAZE_X, AvatarPoseReducer.MAX_DIRECTED_GAZE_X),
            target.y.coerceIn(-AvatarPoseReducer.MAX_DIRECTED_GAZE_Y, AvatarPoseReducer.MAX_DIRECTED_GAZE_Y))
        // A window moved during an interruption: the resumed reading looks at its new place.
        workIntent = workIntent?.takeIf { it.id == windowId }?.copy(gaze = clamped) ?: workIntent
        val current = windowIntent?.takeIf { it.id == windowId } ?: return false
        windowIntent = current.copy(gaze = clamped)
        publishAnimationIntents(at)
        return true
    }

    /** Stale windows cannot clear successors. */
    /**
     * The local model really works on the content of the open window [windowId] (owner brief 06.11.: „der Avatar richtet
     * seine Augen auf das Dokument“ while it is processed). Not a timed glance: the eyes stay on the window and read until
     * [windowWorkEnded], STOP, listening or speech; [AvatarPoseReducer.MAX_WINDOW_WORK_MS] only bounds a lost end event.
     * Admitted again for a window whose opening glance already expired – the work is a new real event.
     */
    @Synchronized fun windowWorkStarted(windowId: String, target: GazeTarget): Boolean {
        val at = clock()
        expireAnimationIntents(at)
        if (windowId.isBlank() || state == AvatarState.LISTENING || state == AvatarState.SPEAKING || utterance != null) return false
        windowIntent?.takeIf { it.id != windowId }?.let { remember(retiredWindows, it.id) }
        windowIntent = WindowIntent(windowId, GazeTarget(
            target.x.coerceIn(-AvatarPoseReducer.MAX_DIRECTED_GAZE_X, AvatarPoseReducer.MAX_DIRECTED_GAZE_X),
            target.y.coerceIn(-AvatarPoseReducer.MAX_DIRECTED_GAZE_Y, AvatarPoseReducer.MAX_DIRECTED_GAZE_Y)),
            at, AvatarPoseReducer.MAX_WINDOW_WORK_MS, yieldsToObservedGaze = false, reading = true)
        workIntent = windowIntent
        thinkingAtMillis = Long.MIN_VALUE
        publishAnimationIntents(at)
        return true
    }

    /** The real work on [windowId] ended (result, failure, STOP): the reading gaze is released at once. */
    @Synchronized fun windowWorkEnded(windowId: String): Boolean {
        val work = workIntent?.takeIf { it.id == windowId }
        val current = windowIntent?.takeIf { it.id == windowId && it.reading }
        if (work == null && current == null) return false
        workIntent = null
        remember(retiredWindows, windowId)
        if (current != null) {
            windowIntent = null
            publishAnimationIntents(clock())
        }
        return true
    }

    @Synchronized fun windowClosed(windowId: String): Boolean {
        if (workIntent?.id == windowId) workIntent = null
        if (windowIntent?.id != windowId) return false
        remember(retiredWindows, windowId)
        windowIntent = null
        publishAnimationIntents(clock())
        return true
    }

    /** Call only after explicit acceptance. */
    @Synchronized fun confirmationAccepted(confirmationId: String): Boolean {
        if (confirmationId.isBlank() || confirmationId in seenConfirmations) return false
        remember(seenConfirmations, confirmationId)
        val at = clock()
        val elapsed = at.toDouble() - lastConfirmationAtMillis.toDouble()
        if (lastConfirmationAtMillis != Long.MIN_VALUE && elapsed < CONFIRMATION_COOLDOWN_MS) return false
        expireAnimationIntents(at)
        confirmationAtMillis = at
        thinkingAtMillis = Long.MIN_VALUE
        lastConfirmationAtMillis = at
        publishAnimationIntents(at)
        return true
    }

    /** STOP/pause cancel presentation intents. */
    @Synchronized fun clearAnimationIntents() {
        workIntent = null
        clearTransientIntents()
    }

    /** Glance, confirmation and thinking cue end; the claim of real work that still runs (workIntent) stays. */
    private fun clearTransientIntents() {
        windowIntent?.let { remember(retiredWindows, it.id) }
        windowIntent = null
        confirmationAtMillis = Long.MIN_VALUE
        thinkingAtMillis = Long.MIN_VALUE
        publishAnimationIntents(clock())
    }

    private fun expireAnimationIntents(at: Long) {
        windowIntent?.let {
            val age = at.toDouble() - it.atMillis.toDouble()
            if (age < 0 || age >= it.durationMs) {
                remember(retiredWindows, it.id)
                windowIntent = null
            }
        }
        val confirmationAge = at.toDouble() - confirmationAtMillis.toDouble()
        if (confirmationAge < 0 || confirmationAge >= AvatarPoseReducer.CONFIRMATION_DURATION_MS) {
            confirmationAtMillis = Long.MIN_VALUE
        }
        val thinkingAge = at.toDouble() - thinkingAtMillis.toDouble()
        if (state != AvatarState.THINKING || thinkingAge < 0 || thinkingAge >= AvatarPoseReducer.THINKING_DURATION_MS) {
            thinkingAtMillis = Long.MIN_VALUE
        }
    }

    private fun publishAnimationIntents(at: Long) {
        animationRevision++
        emit(AvatarEvent.AnimationIntentsChanged(animationRevision, windowIntent?.gaze,
            windowIntent?.atMillis ?: Long.MIN_VALUE, confirmationAtMillis, at,
            windowIntent?.durationMs ?: AvatarPoseReducer.WINDOW_GAZE_TTL_MS, thinkingAtMillis,
            windowIntent?.yieldsToObservedGaze ?: false, windowIntent?.reading ?: false))
    }

    private fun remember(ids: LinkedHashSet<String>, id: String) {
        ids += id
        if (ids.size > MAX_RECENT_INTENTS) {
            val oldest = ids.iterator()
            oldest.next()
            oldest.remove()
        }
    }

    /** Returns admission independently of rendering, so the caller can share it with PCM state. */
    @Synchronized fun speechStarted(utteranceId: String, text: String): Boolean {
        if (utteranceId.isBlank() || utteranceId == utterance || utteranceId in retiredUtterances) return false
        // Speech takes the eyes for its own duration only; real work that still runs keeps its claim (workIntent).
        if (windowIntent != null || confirmationAtMillis != Long.MIN_VALUE || thinkingAtMillis != Long.MIN_VALUE) clearTransientIntents()
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
        val sentenceEnd = sentenceEndAfter(start, end)
        // Punctuation still advances the engine's admitted text/frame markers. It must
        // not invent mouth cues or extend the previous word's no-PCM fallback interval.
        if (visemes.isEmpty()) {
            sentenceEnd?.let { emit(AvatarEvent.SentenceEnd(utteranceId, it, clock())) }
            return true
        }
        val per = (WORD_MS / visemes.size).coerceAtLeast(MIN_VISEME_MS)
        val at = clock()
        lastRangeAt = at
        val wordCues = visemes.mapIndexed { i, v ->
            AvatarEvent.VisemeCue(utteranceId, v, if (v == Viseme.SIL) 0f else 1f, per, at + i * per)
        }
        lastWordCues = wordCues.takeLast(MAX_REATTACHED_CUES)
        wordCues.forEach(::emit)
        sentenceEnd?.let { emit(AvatarEvent.SentenceEnd(utteranceId, it, at)) }
        return true
    }

    /**
     * Whether the word in [start, end) of the spoken text closes a sentence: its letters are followed (optionally
     * after closing quotes or brackets, inside or just outside the range) by `.`, `!`, `?` or `…` and then a space or
     * the end of the text. Not a sentence end: a range without letters or digits (its word was already reported),
     * decimals and names ("3.5", "Datei.txt"), a period before a lowercase word ("ca. drei"), and a period after a
     * number inside the text ("am 6. November"). Reads only the text this Director already holds; emits no text.
     */
    private fun sentenceEndAfter(start: Int, end: Int): SpeechBoundary? {
        var last = end - 1
        while (last >= start && !text[last].isLetterOrDigit()) last--
        if (last < start) return null
        var i = last + 1
        while (i < text.length && text[i] in CLOSERS) i++
        var question = false
        val marks = i
        while (i < text.length && text[i] in SENTENCE_MARKS) { if (text[i] == '?') question = true; i++ }
        if (i == marks) return null
        while (i < text.length && text[i] in CLOSERS) i++
        if (i < text.length && !text[i].isWhitespace()) return null
        if (question) return SpeechBoundary.QUESTION
        var next = i
        while (next < text.length && text[next].isWhitespace()) next++
        if (next == text.length) return SpeechBoundary.STATEMENT
        if (text[next].isLowerCase()) return null
        var first = last
        while (first > 0 && text[first - 1].isLetterOrDigit()) first--
        for (k in first..last) if (!text[k].isDigit()) return SpeechBoundary.STATEMENT
        return null
    }

    @Synchronized fun audioLevel(utteranceId: String, level: Float, measuredPcmAvailable: Boolean = false) {
        if (utteranceId != utterance || !level.isFinite()) return
        val at = clock()
        val measured = level.coerceIn(0f, 1f)
        // Avatar lab (#338): engines that report word ranges but no PCM get coarse word motion,
        // explicitly flagged so a renderer never mistakes it for measured audio.
        val sinceRange = at - lastRangeAt
        if (!measuredPcmAvailable && measured == 0f && lastRangeAt != Long.MIN_VALUE && sinceRange in 0 until FALLBACK_MS) {
            val motion = 0.10f + 0.18f * kotlin.math.abs(kotlin.math.sin(sinceRange / 75f))
            emit(AvatarEvent.AudioLevel(utteranceId, motion, at, AudioLevelSource.WORD_RANGE_FALLBACK))
        } else emit(AvatarEvent.AudioLevel(utteranceId, measured, at, AudioLevelSource.PCM_ENVELOPE))
    }

    @Synchronized fun speechEnded(utteranceId: String, interrupted: Boolean) {
        if (utteranceId != utterance) return
        if (windowIntent != null || confirmationAtMillis != Long.MIN_VALUE || thinkingAtMillis != Long.MIN_VALUE) clearTransientIntents()
        retireUtterance(utteranceId)
        utterance = null; text = ""
        lastWordCues = emptyList()
        emit(AvatarEvent.SpeechEnded(utteranceId, interrupted, clock()))
        // A direct TTS start has no listening/speaking runtime transition: give the running work its eyes back here.
        if (resumeWork()) publishAnimationIntents(clock())
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
        const val MAX_RECENT_INTENTS = 128
        const val CONFIRMATION_COOLDOWN_MS = 4_000L
        const val MAX_TEXT = 4_000
        const val MAX_RETIRED_UTTERANCES = 128
        const val MAX_REATTACHED_CUES = 64
        const val WORD_MS = 320L
        const val MIN_VISEME_MS = 40L
        const val FALLBACK_MS = 500L
        const val SENTENCE_MARKS = ".!?…"
        const val CLOSERS = "\"'»«“”„‘’)]}"
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
