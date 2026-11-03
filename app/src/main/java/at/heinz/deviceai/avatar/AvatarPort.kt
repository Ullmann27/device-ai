package at.heinz.deviceai.avatar

/** RED stub of the avatar integration port: nothing is forwarded yet. */
enum class AvatarActivity { IDLE, LISTENING, THINKING, RESEARCHING, LEARNING, SPEAKING }
enum class AvatarEmotion { NEUTRAL, CALM, HAPPY, CONCERNED, CONFUSED, FOCUSED }
data class AvatarState(val activity: AvatarActivity, val emotion: AvatarEmotion, val utteranceId: String?)

interface AvatarPort {
    fun onState(state: AvatarState) {}
    fun onLipSync(utteranceId: String, level: Float, atMillis: Long) {}
    fun onSpeechRange(utteranceId: String, start: Int, end: Int) {}
}
object NoOpAvatarPort : AvatarPort

class AvatarController(@Volatile var port: AvatarPort = NoOpAvatarPort) {
    var state: AvatarState = AvatarState(AvatarActivity.IDLE, AvatarEmotion.NEUTRAL, null); private set
    fun update(activity: AvatarActivity, utteranceId: String?) {}
    fun emotion(emotion: AvatarEmotion) {}
    fun lipSync(level: Float, atMillis: Long) {}
    fun speechRange(utteranceId: String, start: Int, end: Int) {}
    companion object { fun activityOf(hologramState: String): AvatarActivity = AvatarActivity.IDLE }
}
