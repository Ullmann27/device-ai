package at.heinz.deviceai.voice

/**
 * Says ONCE, plainly, why the local voice speaks instead of the selected ElevenLabs voice — not with every reply.
 * The notice repeats only when the cause changes, or after ElevenLabs spoke successfully again and then fails anew.
 * When no voice at all can speak, the caller always names that (it is never silence without a reason).
 */
class VoiceFallbackNotice {
    enum class Cause { NO_KEY, INTERNET_NOT_ALWAYS_ON, REQUEST_FAILED, EXTERNAL_SPEECH_FORBIDDEN }

    private var announced: Cause? = null

    /** Before a reply: the network voice cannot be used; the offline voice speaks. Null when already said. */
    fun unavailable(voiceName: String, cause: Cause): String? {
        if (announced == cause) return null
        announced = cause
        return "${short(voiceName)} nicht verfügbar: Android-Offline-Stimme wird verwendet. Grund: " + reason(cause)
    }

    /** During a reply: a piece failed and the offline voice continues. Null when already said. */
    fun continuedLocally(voiceName: String): String? {
        if (announced == Cause.REQUEST_FAILED) return null
        announced = Cause.REQUEST_FAILED
        return "${short(voiceName)} konnte nicht sprechen. Die Android-Offline-Stimme spricht weiter."
    }

    /** Nothing can speak the piece: always said, it is the reason for the silence. */
    fun silent(voiceName: String): String =
        "${short(voiceName)} konnte nicht sprechen. Die Antwort bleibt im Chat. Prüfe Schlüssel, Credits und Internet."

    /** ElevenLabs spoke a piece: a later failure is news again. */
    fun onElevenLabsSpoke() { announced = null }

    companion object {
        /** Why the selected network voice cannot be used for this reply; null when it can. */
        fun cause(hasKey: Boolean, internetAlwaysOn: Boolean, replyAllowsNetworkVoice: Boolean): Cause? = when {
            !replyAllowsNetworkVoice -> Cause.EXTERNAL_SPEECH_FORBIDDEN
            !hasKey -> Cause.NO_KEY
            !internetAlwaysOn -> Cause.INTERNET_NOT_ALWAYS_ON
            else -> null
        }

        fun reason(cause: Cause): String = when (cause) {
            Cause.NO_KEY -> "kein ElevenLabs-Schlüssel gespeichert."
            Cause.INTERNET_NOT_ALWAYS_ON -> "Internetmodus steht nicht auf ‚Immer an‘."
            Cause.REQUEST_FAILED -> "ElevenLabs hat nicht geantwortet (Internet, Schlüssel oder Credits)."
            Cause.EXTERNAL_SPEECH_FORBIDDEN -> "diese Antwort soll das Gerät nicht verlassen."
        }

        /** „Maya – Warm Californian“ → „Maya“: the name the owner hears in sentences. */
        fun short(voiceName: String): String = voiceName.substringBefore(" – ").substringBefore(" - ").trim()
            .ifEmpty { "Die ElevenLabs-Stimme" }
    }
}
