package at.heinz.deviceai.avatar

/** RED stub: emits nothing. */
class AvatarDirector(private var sink: AvatarSink = NoOpAvatarSink, private val clock: () -> Long = System::currentTimeMillis) {
    var state: AvatarState = AvatarState.IDLE; private set
    fun attach(newSink: AvatarSink) { sink = newSink }
    fun onRuntime(signals: AvatarRuntimeSignals) = Unit
    fun speechStarted(utteranceId: String, text: String) = Unit
    fun wordRange(utteranceId: String, start: Int, end: Int) = Unit
    fun audioLevel(utteranceId: String, level: Float) = Unit
    fun speechEnded(utteranceId: String, interrupted: Boolean) = Unit
}

object GermanVisemeApproximation { fun visemes(word: String): List<Viseme> = emptyList() }
