package at.heinz.deviceai.cognitive

/** Presentation of current observations only. This object cannot request a
 * permission, launch a tool, activate a connector or certify a successful test. */
internal object ContinuumReadinessGuide {
    enum class Status { UNKNOWN, NEEDS_SETUP, AVAILABLE, UNAVAILABLE, NOT_IMPLEMENTED }
    enum class Action {
        NONE, OPEN_MODELS, ENABLE_VOICE, PLAY_VOICE_SAMPLE, SHOW_VOICE_HELP,
        REQUEST_MICROPHONE, START_SPEECH_TEST, SHOW_SPEECH_HELP,
        REQUEST_CAMERA, ENABLE_CAMERA_TEST, STOP_CAMERA, SHOW_CONNECTOR_INFO
    }
    data class Snapshot(
        val offlineVoiceAvailable: Boolean? = null,
        val voiceEnabled: Boolean = false,
        val onDeviceSpeechAvailable: Boolean? = null,
        val microphoneGranted: Boolean = false,
        val localModelIdentityBound: Boolean = false,
        val cameraHardwareSupported: Boolean? = null,
        val cameraPermissionGranted: Boolean = false,
        val cameraOwnerEnabled: Boolean = false,
        val cameraState: CameraTrackingState = CameraTrackingState.OFF
    )
    data class Step(val id: String, val title: String, val status: Status, val message: String, val action: Action)
    data class Connector(val name: String, val status: Status, val prerequisite: String)

    const val VOICE_SAMPLE = "Hallo. Ich begleite dich durch die Einrichtung. Hörst du meine Stimme klar und angenehm?"
    const val SPEECH_SAMPLE = "Continuum, ich bin bereit."

    fun rows(snapshot: Snapshot): List<Step> = listOf(voice(snapshot), speech(snapshot),
        if (snapshot.localModelIdentityBound)
            Step("model", "Lokales Modell", Status.AVAILABLE,
                "Ein lokales Modell mit geprüfter Identität ist geladen. Stelle eine kurze Frage; eine Antwort ist noch keine vollständige Abnahme.", Action.NONE)
        else Step("model", "Lokales Modell", Status.NEEDS_SETUP,
            "Für freien lokalen KI-Dialog fehlt ein geladenes, geprüftes Modell. Öffne Modelle und verwende den vorhandenen Import- und Ladetest.", Action.OPEN_MODELS),
        camera(snapshot), Step("connectors", "Konten und Dienste", Status.NOT_IMPLEMENTED,
            "Google/Gmail, Facebook und TikTok sind in dieser App noch nicht als Kontodienste verbunden. Eine installierte App oder ChatGPT-Verbindung erteilt keinen Kontozugriff.",
            Action.SHOW_CONNECTOR_INFO))

    private fun voice(s: Snapshot): Step = when {
        s.offlineVoiceAvailable == null -> Step("voice", "Stimme", Status.UNKNOWN,
            "Die lokale Sprachausgabe wird noch geprüft.", Action.NONE)
        !s.offlineVoiceAvailable -> Step("voice", "Stimme", Status.UNAVAILABLE,
            "Keine verfügbare deutsche Stimme für offline Sprachausgabe erkannt. Prüfe die installierte Android-Stimme; es erfolgt kein Cloud-Fallback.", Action.SHOW_VOICE_HELP)
        !s.voiceEnabled -> Step("voice", "Stimme", Status.NEEDS_SETUP,
            "Eine lokale Stimme ist verfügbar. Schalte den Ton ein und starte einen hörbaren Test.", Action.ENABLE_VOICE)
        else -> Step("voice", "Stimme", Status.AVAILABLE,
            "Die lokale Stimme ist verfügbar. Starte den Test und bestätige selbst, ob du sie klar hörst.", Action.PLAY_VOICE_SAMPLE)
    }

    private fun speech(s: Snapshot): Step = when {
        s.onDeviceSpeechAvailable == null -> Step("speech", "Verstehen", Status.UNKNOWN,
            "Die lokale Android-Spracherkennung wurde noch nicht geprüft.", Action.NONE)
        !s.onDeviceSpeechAvailable -> Step("speech", "Verstehen", Status.UNAVAILABLE,
            "Auf diesem Gerät ist derzeit keine lokale Spracherkennung verfügbar. Du kannst weiterhin tippen; Audio wird nicht an einen Ersatzdienst gesendet.", Action.SHOW_SPEECH_HELP)
        !s.microphoneGranted -> Step("speech", "Verstehen", Status.NEEDS_SETUP,
            "Für einen Sprachtest ist deine Mikrofonfreigabe erforderlich. Die Aufnahme beginnt erst nach deiner Aktion.", Action.REQUEST_MICROPHONE)
        else -> Step("speech", "Verstehen", Status.AVAILABLE,
            "Tippe auf den Sprachtest und sage: $SPEECH_SAMPLE Prüfe anschließend den erkannten Text. Dadurch wird kein Auftrag ausgeführt.", Action.START_SPEECH_TEST)
    }

    private fun camera(s: Snapshot): Step = when {
        s.cameraHardwareSupported == null -> Step("camera", "Kamerablick", Status.UNKNOWN,
            "Die Unterstützung für lokale Gesichtspositionsdaten wurde noch nicht geprüft.", Action.NONE)
        !s.cameraHardwareSupported -> Step("camera", "Kamerablick", Status.UNAVAILABLE,
            "Die Frontkamera stellt die benötigten lokalen Positionsdaten nicht bereit. Die normale Avatarbewegung bleibt verfügbar.", Action.NONE)
        !s.cameraPermissionGranted -> Step("camera", "Kamerablick", Status.NEEDS_SETUP,
            "Die optionale Blickausrichtung braucht deine Kamerafreigabe. Es werden keine Bilder gespeichert oder übertragen.", Action.REQUEST_CAMERA)
        !s.cameraOwnerEnabled -> Step("camera", "Kamerablick", Status.NEEDS_SETUP,
            "Starte den Kameratest ausdrücklich. Eine vorhandene Android-Berechtigung schaltet die Kamera nicht von selbst ein.", Action.ENABLE_CAMERA_TEST)
        s.cameraState == CameraTrackingState.TRACKING -> Step("camera", "Kamerablick", Status.AVAILABLE,
            "Eine aktuelle Gesichtsposition wird beobachtet. Bewege den Kopf langsam und prüfe die Blickausrichtung. Dies ist keine Identitäts- oder Augenbewegungserkennung.", Action.STOP_CAMERA)
        s.cameraState == CameraTrackingState.NO_FACE -> Step("camera", "Kamerablick", Status.NEEDS_SETUP,
            "Aktuell ist keine eindeutige Gesichtsposition sichtbar. Schau in die Frontkamera; die Blickausrichtung bleibt bis dahin neutral.", Action.STOP_CAMERA)
        s.cameraState == CameraTrackingState.AMBIGUOUS -> Step("camera", "Kamerablick", Status.NEEDS_SETUP,
            "Mehrere Gesichter sind sichtbar. Die App wählt keine Person als Besitzer aus. Teste die Ausrichtung mit einer Person im Bild.", Action.STOP_CAMERA)
        s.cameraState == CameraTrackingState.STARTING -> Step("camera", "Kamerablick", Status.UNKNOWN,
            "Die freigegebene Frontkamera wird gestartet.", Action.STOP_CAMERA)
        else -> Step("camera", "Kamerablick", Status.UNAVAILABLE,
            "Die Kameraverbindung ist beendet oder nicht verfügbar. Prüfe die Freigabe und starte den Test bei Bedarf erneut.", Action.ENABLE_CAMERA_TEST)
    }

    fun matchesSpeechSample(transcript: String): Boolean {
        if (transcript.length > 2_000) return false
        fun words(value: String) = value.lowercase(java.util.Locale.GERMAN)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        return words(transcript) == words(SPEECH_SAMPLE)
    }

    fun connectors(): List<Connector> = listOf(
        Connector("Google / Gmail", Status.NOT_IMPLEMENTED,
            "App-Registrierung und an Paket/Signatur gebundener Android-Client; getrennte, ausdrücklich erlaubte Gmail-Berechtigungen und ein geprüfter Kontoadapter fehlen."),
        Connector("Facebook", Status.NOT_IMPLEMENTED,
            "Meta-App-Registrierung, passende Login-/API-Berechtigungen und ein geprüfter Kontoadapter fehlen. Entwicklungstest und öffentliche Freigabe sind getrennte Schritte."),
        Connector("TikTok", Status.NOT_IMPLEMENTED,
            "TikTok-App-Registrierung, Login Kit, registrierte Weiterleitung und passende Berechtigungen fehlen. Ein Anbieter-Sandbox-Test ist keine öffentliche Freigabe.")
    )
}
