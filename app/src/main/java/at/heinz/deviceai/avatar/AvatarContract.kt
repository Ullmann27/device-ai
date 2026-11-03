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
                      val jawOpen: Float, val viseme: Viseme, val visemeWeight: Float, val speakEnergy: Float)

/**
 * Reference reducer: folds [AvatarEvent]s into one bounded [AvatarPose] so every renderer maps mouth/gaze
 * the same way. Jaw mapping from the avatar lab (#338): smoothing 0.4, dead zone 0.025, gain 1.5.
 * Not thread-safe by itself: call from the renderer thread after handing events over.
 */
class AvatarPoseReducer {
    private var state = AvatarState.IDLE
    private var expression = AvatarExpression.NEUTRAL
    private var gaze = GazeTarget.OWNER
    private var energy = 0f
    private var viseme = Viseme.SIL
    private var visemeWeight = 0f
    private var utterance: String? = null
    private var lastEventMillis = 0L
    private var lastAudioMillis = Long.MIN_VALUE
    private val cues = ArrayList<AvatarEvent.VisemeCue>()

    fun apply(event: AvatarEvent) {
        lastEventMillis = event.atMillis
        when (event) {
            is AvatarEvent.StateChanged -> { state = event.state; expression = event.expression; gaze = event.gaze }
            is AvatarEvent.SpeechStarted -> {
                clearSpeech()
                utterance = event.utteranceId
            }
            is AvatarEvent.AudioLevel -> if (event.utteranceId == utterance && event.level.isFinite()) {
                energy += (event.level.coerceIn(0f, 1f) - energy) * 0.4f
                lastAudioMillis = event.atMillis
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
        viseme = Viseme.SIL
        visemeWeight = 0f
        cues.clear()
    }

    /** Supply the event clock at render time; future word cues must never play immediately. */
    fun pose(atMillis: Long = lastEventMillis): AvatarPose {
        cues.removeAll { atMillis >= it.atMillis && atMillis - it.atMillis >= it.durationMs }
        val cue = cues.lastOrNull { it.atMillis <= atMillis }
        viseme = cue?.viseme ?: Viseme.SIL
        visemeWeight = cue?.weight ?: 0f
        // Missing/paused TTS callbacks cannot leave a mouth stuck open indefinitely.
        val age = if (lastAudioMillis == Long.MIN_VALUE) Long.MAX_VALUE else atMillis - lastAudioMillis
        val liveEnergy = if (age in 0..250) energy else 0f
        val jaw = if (utterance == null) 0f else ((liveEnergy - 0.025f) * 1.5f).coerceIn(0f, 1f)
        return AvatarPose(state, expression, gaze.x, gaze.y, jaw, viseme, visemeWeight, liveEnergy)
    }
}
