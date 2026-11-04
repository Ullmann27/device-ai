package at.heinz.deviceai.avatar

/**
 * M3: rendering-free contract for a future 3D avatar (docs/CONTINUUM_AVATAR_CONTRACT.md).
 * The app only EMITS events; a graphics implementation plugs in an [AvatarSink]. Nothing here
 * draws, loads models or touches the network. Events carry no owner text except the text the
 * app is about to speak aloud anyway (SpeechStarted), and never tool output or memory.
 */
enum class AvatarState { IDLE, LISTENING, THINKING, RESEARCHING, LEARNING, WAITING_FOR_APPROVAL, SPEAKING, BLOCKED }

enum class AvatarExpression { NEUTRAL, ATTENTIVE, FOCUSED, CURIOUS, CONCERNED, WARM }

/** Normalized gaze: x,y in -1..1, (0,0) = straight at the owner/camera. */
data class GazeTarget(val x: Float, val y: Float) {
    init { require(x in -1f..1f && y in -1f..1f) }
    companion object {
        val OWNER = GazeTarget(0f, 0f)
        val THINKING_AWAY = GazeTarget(0.35f, 0.25f)
        val READING_DOWN = GazeTarget(0f, -0.4f)
    }
}

/** 15-class viseme set as used by common lip-sync pipelines (Oculus/Meta OVRLipSync naming). */
enum class Viseme { SIL, PP, FF, TH, DD, KK, CH, SS, NN, RR, AA, E, I, O, U }

/** PCM_ENVELOPE = measured synthesized audio; WORD_RANGE_FALLBACK = coarse motion when the TTS engine gives word ranges but no PCM. */
enum class AudioLevelSource { PCM_ENVELOPE, WORD_RANGE_FALLBACK }

sealed interface AvatarEvent {
    val atMillis: Long
    data class StateChanged(val state: AvatarState, val expression: AvatarExpression, val gaze: GazeTarget,
                            override val atMillis: Long) : AvatarEvent
    /** Owner-enabled hardware face-position approximation, not identity or eye tracking.
     * atMillis is observation time in the Director's existing clock domain. */
    data class GazeObserved(val gaze: GazeTarget, val confidence: Int, override val atMillis: Long) : AvatarEvent
    data class GazeCleared(override val atMillis: Long) : AvatarEvent
    data class SpeechStarted(val utteranceId: String, val text: String, override val atMillis: Long) : AvatarEvent
    /** Approximate mouth shape for the word the TTS engine is speaking now (not phoneme-accurate). */
    data class VisemeCue(val utteranceId: String, val viseme: Viseme, val weight: Float, val durationMs: Long,
                         override val atMillis: Long) : AvatarEvent
    /** Real synthesized-audio envelope 0..1 (from the existing TTS signal), ~30 Hz. */
    data class AudioLevel(val utteranceId: String, val level: Float, override val atMillis: Long,
                          val source: AudioLevelSource = AudioLevelSource.PCM_ENVELOPE) : AvatarEvent
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
                      val gazeObserved: Boolean = false)

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
    private var energy = 0f
    private var viseme = Viseme.SIL
    private var visemeWeight = 0f
    private var utterance: String? = null
    private var lastEventMillis = 0L
    private var lastAudioMillis = Long.MIN_VALUE
    private var lastSmoothedAudioMillis = Long.MIN_VALUE
    private val cues = ArrayList<AvatarEvent.VisemeCue>()

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
        if (event is AvatarEvent.SpeechStarted && (event.utteranceId.isBlank() || event.utteranceId == utterance)) return
        lastEventMillis = event.atMillis
        when (event) {
            is AvatarEvent.StateChanged -> {
                state = event.state; expression = event.expression; gaze = event.gaze
                if (!permitsObservedGaze(state)) clearObservedGaze(event.atMillis)
            }
            is AvatarEvent.GazeObserved -> {
                if (permitsObservedGaze(state) && event.confidence in MIN_GAZE_CONFIDENCE..100 &&
                    event.atMillis > gazeClearedAtMillis && event.atMillis >= observedAtMillis) {
                    observedGaze = event.gaze; observedAtMillis = event.atMillis
                }
            }
            is AvatarEvent.GazeCleared -> clearObservedGaze(event.atMillis)
            is AvatarEvent.SpeechStarted -> {
                clearSpeech()
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
            is AvatarEvent.SpeechEnded -> if (event.utteranceId == utterance) clearSpeech()
        }
    }

    /** Drop transient mouth state on pause/detach; never retain spoken text in a renderer. */
    fun clearSpeech() {
        utterance = null
        energy = 0f
        lastAudioMillis = Long.MIN_VALUE
        lastSmoothedAudioMillis = Long.MIN_VALUE
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

    /** Supply the event clock at render time; future word cues must never play immediately. */
    fun pose(atMillis: Long = lastEventMillis): AvatarPose {
        if (lastAudioMillis > atMillis) {
            // The render clock exposes invalid future audio. Drop its smoothing contribution and
            // reject queued callbacks through this frame, even before any frame confirmed audio.
            if (lastEventMillis == lastAudioMillis) lastEventMillis = atMillis
            energy = 0f
            lastAudioMillis = atMillis
            lastSmoothedAudioMillis = Long.MIN_VALUE
        }
        cues.removeAll { atMillis >= it.atMillis && atMillis - it.atMillis >= it.durationMs }
        val cue = cues.lastOrNull { it.atMillis <= atMillis }
        viseme = cue?.viseme ?: Viseme.SIL
        visemeWeight = cue?.weight ?: 0f
        // Missing/paused TTS callbacks cannot leave a mouth stuck open indefinitely.
        val age = if (lastAudioMillis == Long.MIN_VALUE) Long.MAX_VALUE else atMillis - lastAudioMillis
        val liveEnergy = if (age in 0..250) energy else 0f
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
        val shownGaze = observedGaze ?: gaze
        val shownExpression = if (utterance != null && expression == AvatarExpression.WARM)
            AvatarExpression.NEUTRAL else expression
        return AvatarPose(state, shownExpression, shownGaze.x, shownGaze.y, jaw, viseme, visemeWeight, liveEnergy,
            gazeObserved = observedGaze != null)
    }

    companion object {
        const val OBSERVED_GAZE_TTL_MS = 750L
        const val MIN_GAZE_CONFIDENCE = 60
        fun permitsObservedGaze(state: AvatarState) = state == AvatarState.IDLE ||
            state == AvatarState.LISTENING || state == AvatarState.SPEAKING
    }
}
