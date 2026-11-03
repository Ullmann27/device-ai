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

sealed interface AvatarEvent {
    val atMillis: Long
    data class StateChanged(val state: AvatarState, val expression: AvatarExpression, val gaze: GazeTarget,
                            override val atMillis: Long) : AvatarEvent
    data class SpeechStarted(val utteranceId: String, val text: String, override val atMillis: Long) : AvatarEvent
    /** Approximate mouth shape for the word the TTS engine is speaking now (not phoneme-accurate). */
    data class VisemeCue(val utteranceId: String, val viseme: Viseme, val weight: Float, val durationMs: Long,
                         override val atMillis: Long) : AvatarEvent
    /** Real synthesized-audio envelope 0..1 (from the existing TTS signal), ~30 Hz. */
    data class AudioLevel(val utteranceId: String, val level: Float, override val atMillis: Long) : AvatarEvent
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
