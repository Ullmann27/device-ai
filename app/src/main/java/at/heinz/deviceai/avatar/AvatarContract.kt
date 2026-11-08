package at.heinz.deviceai.avatar

/**
 * M3: rendering-free contract for a future 3D avatar (docs/CONTINUUM_AVATAR_CONTRACT.md).
 * The app only EMITS events; a graphics implementation plugs in an [AvatarSink]. Nothing here
 * draws, loads models or touches the network. Events carry no owner text except the text the
 * app is about to speak aloud anyway (SpeechStarted), and never tool output or memory.
 */
enum class AvatarState { IDLE, LISTENING, THINKING, RESEARCHING, LEARNING, WAITING_FOR_APPROVAL, SPEAKING, BLOCKED }

enum class AvatarExpression { NEUTRAL, ATTENTIVE, FOCUSED, CURIOUS, CONCERNED }

/** Normalized gaze: x,y in -1..1, (0,0) = straight at the owner/camera. */
data class GazeTarget(val x: Float, val y: Float) {
    init { require(x in -1f..1f && y in -1f..1f) }
    companion object {
        val OWNER = GazeTarget(0f, 0f)
        val THINKING_AWAY = GazeTarget(0.35f, 0.25f)
        val READING_DOWN = GazeTarget(0f, -0.4f)
        val WORK_WINDOW = GazeTarget(.55f, -.18f)
    }
}

/** 15-class viseme set as used by common lip-sync pipelines (Oculus/Meta OVRLipSync naming). */
enum class Viseme { SIL, PP, FF, TH, DD, KK, CH, SS, NN, RR, AA, E, I, O, U }

/** PCM_ENVELOPE = measured synthesized audio; WORD_RANGE_FALLBACK = coarse motion when the TTS engine gives word ranges but
 * no PCM; TEXT_PACE_FALLBACK = coarse motion paced from the spoken text when the engine gives neither (never measured audio). */
enum class AudioLevelSource { PCM_ENVELOPE, WORD_RANGE_FALLBACK, TEXT_PACE_FALLBACK }

/** Compatibility names for canonical intents. */
enum class AvatarGestureIntent { WINDOW_FOCUS, CONFIRM_NOD, THINK_MICRO }

/** How a sentence of the spoken text ends: `.`/`!` closes a statement, `?` a question. */
enum class SpeechBoundary { STATEMENT, QUESTION }

sealed interface AvatarEvent {
    val atMillis: Long
    data class StateChanged(val state: AvatarState, val expression: AvatarExpression, val gaze: GazeTarget,
                            override val atMillis: Long) : AvatarEvent
    /** Owner-enabled hardware face-position approximation, not identity or eye tracking.
     * atMillis is observation time in the Director's existing clock domain. */
    data class GazeObserved(val gaze: GazeTarget, val confidence: Int, override val atMillis: Long) : AvatarEvent
    data class GazeCleared(override val atMillis: Long) : AvatarEvent
    /** Director-owned intent, never camera evidence. */
    data class AnimationIntentsChanged(val revision: Long, val windowGaze: GazeTarget?,
        val windowAtMillis: Long, val confirmationAtMillis: Long,
        override val atMillis: Long, val windowDurationMs: Long = AvatarPoseReducer.WINDOW_GAZE_TTL_MS,
        val thinkingAtMillis: Long = Long.MIN_VALUE, val windowYieldsToObservedGaze: Boolean = false,
        /** The model really works on the window's content: the gaze is held (reading) instead of a timed glance. */
        val windowReading: Boolean = false) : AvatarEvent
    /** Legacy input; same reducer state. */
    data class Gesture(val intent: AvatarGestureIntent, val target: GazeTarget,
        override val atMillis: Long) : AvatarEvent
    data class SpeechStarted(val utteranceId: String, val text: String, override val atMillis: Long) : AvatarEvent
    /** Approximate mouth shape for the word the TTS engine is speaking now (not phoneme-accurate). */
    data class VisemeCue(val utteranceId: String, val viseme: Viseme, val weight: Float, val durationMs: Long,
                         override val atMillis: Long) : AvatarEvent
    /** Real synthesized-audio envelope 0..1 (from the existing TTS signal), ~30 Hz. */
    data class AudioLevel(val utteranceId: String, val level: Float, override val atMillis: Long,
                          val source: AudioLevelSource = AudioLevelSource.PCM_ENVELOPE) : AvatarEvent
    /** The word the TTS engine has just started is the last one of a sentence in the spoken text (no text attached). */
    data class SentenceEnd(val utteranceId: String, val boundary: SpeechBoundary, override val atMillis: Long) : AvatarEvent
    data class SpeechEnded(val utteranceId: String, val interrupted: Boolean, override val atMillis: Long) : AvatarEvent
}

/** Implemented by the graphics side. Called on arbitrary threads; must return quickly. */
fun interface AvatarSink { fun onAvatarEvent(event: AvatarEvent) }

object NoOpAvatarSink : AvatarSink { override fun onAvatarEvent(event: AvatarEvent) = Unit }

/** Snapshot of the app's existing runtime flags (same inputs as the hologram state). */
data class AvatarRuntimeSignals(
    val listening: Boolean = false, val speaking: Boolean = false, val researching: Boolean = false,
    val learning: Boolean = false, val busy: Boolean = false, val awaitingApproval: Boolean = false,
    val blocked: Boolean = false
)

/**
 * Plug-in point for the graphics side: set [sink] before AgentActivity is created (e.g. in an
 * Application subclass or a later avatar module). Default is the no-op placeholder.
 */
object AvatarSinkRegistry { @Volatile var sink: AvatarSink = NoOpAvatarSink }

/** Renderer-facing snapshot (subset of the PR #350 AvatarPose plan). Blink, head motion and idle are renderer-owned. */
data class AvatarPose(val state: AvatarState, val expression: AvatarExpression, val gazeX: Float, val gazeY: Float,
                      val jawOpen: Float, val viseme: Viseme, val visemeWeight: Float, val speakEnergy: Float,
                      val gazeObserved: Boolean = false, val gesture: AvatarGestureIntent? = null,
                      val gestureProgress: Float = 0f, val gazeDirected: Boolean = false,
                      val fallbackGazeX: Float = 0f, val fallbackGazeY: Float = 0f,
                      /** Sentence end reached in the spoken text and its gesture progress 0..1 (−1 = none). */
                      val sentenceEnd: SpeechBoundary? = null, val sentenceEndProgress: Float = -1f,
                      /** The eyes read the directed window while real work on its content runs (never without it). */
                      val reading: Boolean = false) {
    val confirmationProgress: Float get() = if (gesture == AvatarGestureIntent.CONFIRM_NOD) gestureProgress else -1f
}

/**
 * Reference reducer: folds [AvatarEvent]s into one bounded [AvatarPose] so every renderer maps mouth/gaze
 * the same way. Jaw mapping from the avatar lab (#338): smoothing 0.4 per nominal 33 ms,
 * adjusted for actual sample time; dead zone 0.025, gain 1.5.
 * Not thread-safe by itself: call from the renderer thread after handing events over.
 */
class AvatarPoseReducer {
    private var state = AvatarState.IDLE
    private var expression = AvatarExpression.NEUTRAL
    private var gaze = GazeTarget.OWNER
    private var observedGaze: GazeTarget? = null
    private var observedAtMillis = Long.MIN_VALUE
    private var newestFreshGazeAtMillis = Long.MIN_VALUE
    private var gazeClearedAtMillis = Long.MIN_VALUE
    private var animationRevision = 0L
    private var directedGaze: GazeTarget? = null
    private var directedAtMillis = Long.MIN_VALUE
    private var confirmationAtMillis = Long.MIN_VALUE
    private var directedDurationMs = WINDOW_GAZE_TTL_MS
    private var directedYieldsToObservedGaze = false
    private var directedReading = false
    private var thinkingAtMillis = Long.MIN_VALUE
    private var animationClearedAtMillis = Long.MIN_VALUE
    private var lastLegacyGestureAtMillis = Long.MIN_VALUE
    private var energy = 0f
    private var viseme = Viseme.SIL
    private var visemeWeight = 0f
    private var utterance: String? = null
    private var lastEventMillis = 0L
    private var lastAudioMillis = Long.MIN_VALUE
    private var lastSmoothedAudioMillis = Long.MIN_VALUE
    private var lastAudioSource: AudioLevelSource? = null
    private var lastMeasuredLevel = 0f
    private val cues = ArrayList<AvatarEvent.VisemeCue>()
    private var sentenceEnd: SpeechBoundary? = null
    private var sentenceEndAtMillis = Long.MIN_VALUE

    fun apply(event: AvatarEvent) {
        // Rejected/replayed audio must not resmooth the envelope or change the default pose clock.
        if (event is AvatarEvent.AudioLevel && (event.utteranceId != utterance ||
                !event.level.isFinite() || event.atMillis <= lastAudioMillis)) return
        if (event is AvatarEvent.VisemeCue && (event.utteranceId != utterance ||
                !event.weight.isFinite() || event.durationMs <= 0)) return
        // Reattaching a still-live sink can resend its original word schedule. Identical
        // cues neither multiply the bounded queue nor change the default pose clock.
        if (event is AvatarEvent.VisemeCue && event in cues) return
        if (event is AvatarEvent.SpeechEnded && event.utteranceId != utterance) return
        if (event is AvatarEvent.SentenceEnd && (event.utteranceId != utterance || event.atMillis < sentenceEndAtMillis)) return
        if (event is AvatarEvent.SpeechStarted && (event.utteranceId.isBlank() || event.utteranceId == utterance)) return
        if (event is AvatarEvent.AnimationIntentsChanged && event.revision <= animationRevision) return
        if (event is AvatarEvent.Gesture && (event.atMillis <= animationClearedAtMillis ||
                event.atMillis <= lastLegacyGestureAtMillis)) return
        lastEventMillis = event.atMillis
        when (event) {
            is AvatarEvent.StateChanged -> {
                state = event.state; expression = event.expression; gaze = event.gaze
                if (!permitsObservedGaze(state)) clearObservedGaze(event.atMillis)
                if (state == AvatarState.LISTENING || state == AvatarState.SPEAKING) directedGaze = null
                if (state != AvatarState.THINKING) thinkingAtMillis = Long.MIN_VALUE
            }
            is AvatarEvent.GazeObserved -> {
                if (permitsObservedGaze(state) && event.confidence in MIN_GAZE_CONFIDENCE..100 &&
                    event.atMillis > gazeClearedAtMillis && event.atMillis >= observedAtMillis) {
                    observedGaze = event.gaze; observedAtMillis = event.atMillis
                }
            }
            is AvatarEvent.GazeCleared -> clearObservedGaze(event.atMillis)
            is AvatarEvent.AnimationIntentsChanged -> {
                animationRevision = event.revision
                lastLegacyGestureAtMillis = maxOf(lastLegacyGestureAtMillis, event.atMillis)
                directedGaze = event.windowGaze?.let {
                    GazeTarget(it.x.coerceIn(-MAX_DIRECTED_GAZE_X, MAX_DIRECTED_GAZE_X),
                        it.y.coerceIn(-MAX_DIRECTED_GAZE_Y, MAX_DIRECTED_GAZE_Y))
                }
                if (state == AvatarState.LISTENING || state == AvatarState.SPEAKING || utterance != null) directedGaze = null
                directedAtMillis = event.windowAtMillis
                directedReading = event.windowReading && directedGaze != null
                directedDurationMs = event.windowDurationMs.coerceIn(1L,
                    if (directedReading) MAX_WINDOW_WORK_MS else WINDOW_GAZE_TTL_MS)
                directedYieldsToObservedGaze = event.windowYieldsToObservedGaze
                confirmationAtMillis = event.confirmationAtMillis
                thinkingAtMillis = if (state == AvatarState.THINKING) event.thinkingAtMillis else Long.MIN_VALUE
                if (event.windowGaze == null && event.confirmationAtMillis == Long.MIN_VALUE &&
                    event.thinkingAtMillis == Long.MIN_VALUE) animationClearedAtMillis = maxOf(animationClearedAtMillis, event.atMillis)
            }
            is AvatarEvent.Gesture -> {
                lastLegacyGestureAtMillis = event.atMillis
                when (event.intent) {
                    AvatarGestureIntent.WINDOW_FOCUS -> {
                        if (state != AvatarState.LISTENING && state != AvatarState.SPEAKING && utterance == null) {
                            directedGaze = GazeTarget(event.target.x.coerceIn(-MAX_DIRECTED_GAZE_X, MAX_DIRECTED_GAZE_X),
                                event.target.y.coerceIn(-MAX_DIRECTED_GAZE_Y, MAX_DIRECTED_GAZE_Y))
                            directedAtMillis = event.atMillis
                            directedDurationMs = LEGACY_WINDOW_GAZE_TTL_MS
                            directedYieldsToObservedGaze = true
                            directedReading = false
                            thinkingAtMillis = Long.MIN_VALUE
                        }
                    }
                    AvatarGestureIntent.CONFIRM_NOD -> {
                        confirmationAtMillis = event.atMillis
                        thinkingAtMillis = Long.MIN_VALUE
                    }
                    AvatarGestureIntent.THINK_MICRO -> if (state == AvatarState.THINKING) thinkingAtMillis = event.atMillis
                }
            }
            is AvatarEvent.SpeechStarted -> {
                // The next piece of a reply may begin while the previous sentence still settles.
                clearUtterance()
                utterance = event.utteranceId
            }
            is AvatarEvent.AudioLevel -> {
                // Preserve the calibrated first/33 ms response without depending on the
                // number of callbacks delivered within the same elapsed time.
                val smoothing = if (lastSmoothedAudioMillis == Long.MIN_VALUE) 0.4f else {
                    val elapsed = event.atMillis.toDouble() - lastSmoothedAudioMillis.toDouble()
                    (1.0 - kotlin.math.exp(kotlin.math.ln(0.6) * elapsed / 33.0)).toFloat()
                }
                energy += (event.level.coerceIn(0f, 1f) - energy) * smoothing
                lastAudioSource = event.source
                lastMeasuredLevel = event.level.coerceIn(0f, 1f)
                if (event.source == AudioLevelSource.PCM_ENVELOPE && lastMeasuredLevel == 0f) energy = 0f
                lastAudioMillis = event.atMillis
                lastSmoothedAudioMillis = event.atMillis
            }
            is AvatarEvent.VisemeCue -> if (event.utteranceId == utterance && event.weight.isFinite() && event.durationMs > 0) {
                // A new word starts with SIL; discard the previous word's unplayed approximation.
                if (event.viseme == Viseme.SIL) cues.removeAll { it.atMillis >= event.atMillis }
                cues.add(event.copy(weight = event.weight.coerceIn(0f, 1f), durationMs = event.durationMs.coerceAtMost(1_000)))
                cues.sortBy { it.atMillis }
                while (cues.size > 64) cues.removeAt(0)
            }
            is AvatarEvent.SentenceEnd -> { sentenceEnd = event.boundary; sentenceEndAtMillis = event.atMillis }
            // A sentence that ends with the utterance still settles; STOP/errors cancel its gesture with the speech.
            is AvatarEvent.SpeechEnded -> if (event.utteranceId == utterance) {
                if (event.interrupted) clearSpeech() else clearUtterance()
            }
        }
    }

    /** Drop transient mouth state on pause/detach; never retain spoken text in a renderer. */
    fun clearSpeech() {
        clearUtterance()
        sentenceEnd = null
        sentenceEndAtMillis = Long.MIN_VALUE
    }

    private fun clearUtterance() {
        clearAnimationIntents()
        utterance = null
        energy = 0f
        lastAudioMillis = Long.MIN_VALUE
        lastSmoothedAudioMillis = Long.MIN_VALUE
        lastAudioSource = null
        lastMeasuredLevel = 0f
        viseme = Viseme.SIL
        visemeWeight = 0f
        cues.clear()
    }

    /** Pause, detach, revoked consent and lost/ambiguous observations clear immediately. */
    fun clearObservedGaze(atMillis: Long) {
        observedGaze = null
        if (observedAtMillis > atMillis) observedAtMillis = newestFreshGazeAtMillis
        gazeClearedAtMillis = maxOf(gazeClearedAtMillis, atMillis)
    }

    private fun clearAnimationIntents() {
        directedGaze = null
        directedReading = false
        directedAtMillis = Long.MIN_VALUE
        confirmationAtMillis = Long.MIN_VALUE
        thinkingAtMillis = Long.MIN_VALUE
        animationClearedAtMillis = maxOf(animationClearedAtMillis, lastEventMillis)
    }

    /** Supply the event clock at render time; future word cues must never play immediately. */
    fun pose(atMillis: Long = lastEventMillis, motion: Boolean = true): AvatarPose {
        if (lastAudioMillis > atMillis) {
            // The render clock exposes invalid future audio. Drop its smoothing contribution and
            // reject queued callbacks through this frame, even before any frame confirmed audio.
            if (lastEventMillis == lastAudioMillis) lastEventMillis = atMillis
            energy = 0f
            lastMeasuredLevel = 0f
            lastAudioMillis = atMillis
            lastSmoothedAudioMillis = Long.MIN_VALUE
        }
        cues.removeAll { atMillis >= it.atMillis && atMillis - it.atMillis >= it.durationMs }
        val cue = cues.lastOrNull { it.atMillis <= atMillis }
        // Missing/paused TTS callbacks cannot leave a mouth stuck open indefinitely.
        val age = if (lastAudioMillis == Long.MIN_VALUE) Long.MAX_VALUE else atMillis - lastAudioMillis
        val measuredSilence = lastAudioSource == AudioLevelSource.PCM_ENVELOPE &&
            (lastMeasuredLevel == 0f || age !in 0..250)
        viseme = if (measuredSilence) Viseme.SIL else cue?.viseme ?: Viseme.SIL
        visemeWeight = if (measuredSilence) 0f else cue?.weight ?: 0f
        val liveEnergy = if (!measuredSilence && age in 0..250) energy else 0f
        val jaw = if (utterance == null) 0f else ((liveEnergy - 0.025f) * 1.5f).coerceIn(0f, 1f)
        val gazeAge = if (observedAtMillis == Long.MIN_VALUE) Long.MAX_VALUE else atMillis - observedAtMillis
        if (observedAtMillis > atMillis) {
            // A rejected future sample must not poison ordering for later valid observations.
            // Keep the last actually fresh watermark, not the invalid future timestamp.
            observedGaze = null
            observedAtMillis = newestFreshGazeAtMillis
        } else if (!permitsObservedGaze(state) || gazeAge !in 0..OBSERVED_GAZE_TTL_MS) {
            observedGaze = null
        } else if (observedGaze != null) {
            newestFreshGazeAtMillis = maxOf(newestFreshGazeAtMillis, observedAtMillis)
        }
        val directedAge = atMillis.toDouble() - directedAtMillis.toDouble()
        if (directedAtMillis == Long.MIN_VALUE || directedAge < 0 || directedAge >= directedDurationMs) directedGaze = null
        val confirmationAge = atMillis.toDouble() - confirmationAtMillis.toDouble()
        val confirmationProgress = if (confirmationAtMillis != Long.MIN_VALUE &&
            confirmationAge >= 0 && confirmationAge < CONFIRMATION_DURATION_MS) {
            (confirmationAge / CONFIRMATION_DURATION_MS).toFloat()
        } else {
            confirmationAtMillis = Long.MIN_VALUE
            -1f
        }
        val thinkingAge = atMillis.toDouble() - thinkingAtMillis.toDouble()
        val thinkingProgress = if (state == AvatarState.THINKING && thinkingAtMillis != Long.MIN_VALUE &&
            thinkingAge >= 0 && thinkingAge < THINKING_DURATION_MS) {
            (thinkingAge / THINKING_DURATION_MS).toFloat()
        } else {
            thinkingAtMillis = Long.MIN_VALUE
            -1f
        }
        val sentenceAge = atMillis.toDouble() - sentenceEndAtMillis.toDouble()
        if (sentenceEnd != null && sentenceAge >= SENTENCE_END_DURATION_MS) {
            sentenceEnd = null
            sentenceEndAtMillis = Long.MIN_VALUE
        }
        val sentenceProgress = if (motion && sentenceEnd != null && sentenceAge >= 0) {
            (sentenceAge / SENTENCE_END_DURATION_MS).toFloat()
        } else -1f
        val activeWindow = directedGaze.takeIf { motion }
        val shownDirected = activeWindow.takeUnless { directedYieldsToObservedGaze && observedGaze != null }
        val underlyingGaze = observedGaze ?: gaze
        val shownGaze = shownDirected ?: underlyingGaze
        val gesture = when {
            !motion -> null
            confirmationProgress >= 0f -> AvatarGestureIntent.CONFIRM_NOD
            activeWindow != null -> AvatarGestureIntent.WINDOW_FOCUS
            thinkingProgress >= 0f -> AvatarGestureIntent.THINK_MICRO
            else -> null
        }
        val progress = when (gesture) {
            AvatarGestureIntent.CONFIRM_NOD -> confirmationProgress
            AvatarGestureIntent.WINDOW_FOCUS -> (directedAge / directedDurationMs).toFloat()
            AvatarGestureIntent.THINK_MICRO -> thinkingProgress
            null -> 0f
        }
        return AvatarPose(state, expression, shownGaze.x, shownGaze.y, jaw, viseme, visemeWeight, liveEnergy,
            gazeObserved = shownDirected == null && observedGaze != null, gesture = gesture, gestureProgress = progress,
            gazeDirected = shownDirected != null, fallbackGazeX = underlyingGaze.x, fallbackGazeY = underlyingGaze.y,
            sentenceEnd = if (sentenceProgress >= 0f) sentenceEnd else null, sentenceEndProgress = sentenceProgress,
            reading = shownDirected != null && directedReading)
    }

    companion object {
        /** Last word of the sentence (about 320 ms) plus the settle or brow release after it. */
        const val SENTENCE_END_DURATION_MS = 1_200L
        const val WINDOW_GAZE_TTL_MS = 2_500L
        /** Safety bound of a held reading gaze; the real end of the work releases it earlier. */
        const val MAX_WINDOW_WORK_MS = 10 * 60_000L
        const val LEGACY_WINDOW_GAZE_TTL_MS = 1_100L
        const val THINKING_DURATION_MS = 900L
        const val CONFIRMATION_DURATION_MS = 650L
        const val MAX_DIRECTED_GAZE_X = .95f
        const val MAX_DIRECTED_GAZE_Y = .35f
        const val OBSERVED_GAZE_TTL_MS = 750L
        const val MIN_GAZE_CONFIDENCE = 60
        fun permitsObservedGaze(state: AvatarState) = state == AvatarState.IDLE ||
            state == AvatarState.LISTENING || state == AvatarState.SPEAKING
    }
}
