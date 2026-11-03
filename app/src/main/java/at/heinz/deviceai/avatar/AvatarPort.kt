package at.heinz.deviceai.avatar

/**
 * Integration port for the later JARVIS avatar (graphics: "Jarvis Grafik" bot; speech/avatar
 * logic: speech bot). The app core only publishes dialogue/TTS state through this port; the
 * default is a no-op, so without an avatar implementation nothing changes.
 *
 * Contract for implementations: callbacks arrive on the main thread, must return quickly and
 * must not block; an exception is contained here and never reaches the dialogue. No audio,
 * transcript or owner data crosses the port, only state, a bounded 0..1 level and text ranges
 * of the current utterance (as reported by Android TTS onRangeStart).
 */
enum class AvatarActivity { IDLE, LISTENING, THINKING, RESEARCHING, LEARNING, SPEAKING }
enum class AvatarEmotion { NEUTRAL, CALM, HAPPY, CONCERNED, CONFUSED, FOCUSED }
data class AvatarState(val activity: AvatarActivity, val emotion: AvatarEmotion, val utteranceId: String?)

interface AvatarPort {
    fun onState(state: AvatarState) {}
    /** Lip-sync hook: playback-anchored amplitude (TtsHologramSignal) of the current utterance. */
    fun onLipSync(utteranceId: String, level: Float, atMillis: Long) {}
    /** Viseme/word hook: character range of the utterance text currently being spoken. */
    fun onSpeechRange(utteranceId: String, start: Int, end: Int) {}
}

object NoOpAvatarPort : AvatarPort

class AvatarController(@Volatile var port: AvatarPort = NoOpAvatarPort) {
    @Volatile var state: AvatarState = AvatarState(AvatarActivity.IDLE, AvatarEmotion.NEUTRAL, null)
        private set

    @Synchronized fun update(activity: AvatarActivity, utteranceId: String?) {
        val next = state.copy(activity = activity,
            utteranceId = if (activity == AvatarActivity.SPEAKING) utteranceId else null)
        if (next != state) { state = next; publish(next) }
    }

    @Synchronized fun emotion(emotion: AvatarEmotion) {
        val next = state.copy(emotion = emotion)
        if (next != state) { state = next; publish(next) }
    }

    fun lipSync(level: Float, atMillis: Long) {
        val s = state
        val id = s.utteranceId ?: return
        if (s.activity != AvatarActivity.SPEAKING) return
        val bounded = if (level.isNaN()) 0f else level.coerceIn(0f, 1f)
        runCatching { port.onLipSync(id, bounded, atMillis) }
    }

    fun speechRange(utteranceId: String, start: Int, end: Int) {
        val s = state
        if (s.activity != AvatarActivity.SPEAKING || s.utteranceId != utteranceId || start < 0 || end < start) return
        runCatching { port.onSpeechRange(utteranceId, start, end) }
    }

    private fun publish(next: AvatarState) { runCatching { port.onState(next) } }

    companion object {
        /** One-to-one with ContinuumHologramView.State names; unknown = IDLE. */
        fun activityOf(hologramState: String): AvatarActivity = when (hologramState) {
            "LISTENING" -> AvatarActivity.LISTENING
            "SPEAKING" -> AvatarActivity.SPEAKING
            "RESEARCH" -> AvatarActivity.RESEARCHING
            "LEARNING" -> AvatarActivity.LEARNING
            "THINKING" -> AvatarActivity.THINKING
            else -> AvatarActivity.IDLE
        }
    }
}
