package at.heinz.deviceai.cognitive

/**
 * Why the offline Android voice can or cannot speak, and what the owner is told about it.
 * The local voice is the one speech output that must work without any network voice.
 */
internal object LocalVoiceAvailability {
    enum class State {
        /** The Android engine has not reported its start result yet. */
        STARTING,
        /** An installed offline voice is selected. */
        READY,
        /** The Android engine reported that it could not start. */
        ENGINE_FAILED,
        /** The engine runs, but no installed offline voice of the chosen language could be selected. */
        NO_OFFLINE_VOICE
    }

    fun state(engineReported: Boolean, engineStarted: Boolean, offlineVoiceSelected: Boolean): State = when {
        !engineReported -> State.STARTING
        !engineStarted -> State.ENGINE_FAILED
        offlineVoiceSelected -> State.READY
        else -> State.NO_OFFLINE_VOICE
    }

    /** What the owner reads when replies cannot be spoken locally; null when the voice is ready. */
    fun ownerNotice(state: State, languageLabel: String): String? = when (state) {
        State.READY -> null
        State.STARTING -> "Android-Sprachausgabe ist noch nicht bereit."
        State.ENGINE_FAILED -> "Die Android-Sprachausgabe konnte nicht gestartet werden. Antworten bleiben im Chat " +
            "lesbar. Prüfe in den Android-Einstellungen unter Text-in-Sprache, ob ein Sprachmodul installiert und " +
            "ausgewählt ist."
        State.NO_OFFLINE_VOICE -> "Keine Offline-Stimme für $languageLabel installiert. Antworten bleiben im Chat " +
            "lesbar. Lade in den Android-Einstellungen unter Text-in-Sprache eine Stimme für $languageLabel."
    }

    /**
     * A network voice failed in the middle of a reply. Only a ready, installed offline voice may continue
     * it; a voice that itself needs the network never takes over.
     */
    fun mayContinueFailedNetworkReply(state: State, selectedVoiceNeedsNetwork: Boolean?): Boolean =
        state == State.READY && selectedVoiceNeedsNetwork == false
}
