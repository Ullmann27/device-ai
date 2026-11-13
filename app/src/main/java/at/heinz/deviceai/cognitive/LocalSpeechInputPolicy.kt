package at.heinz.deviceai.cognitive

/** Android EXTRA_PREFER_OFFLINE is advisory, never a network permission or offline proof, so the
 * legacy SpeechRecognizer is never used. The UI uses only real on-device ASR (API 31+, see
 * LocalSpeechEngine); below API 31 speech input is unavailable. There is no automatic cloud
 * substitute on any API level; cloud audio would need a separate explicit per-use grant. */
internal object LocalSpeechInputPolicy {
    enum class Failure { NO_MATCH, NO_INPUT, PERMISSION, SERVICE_UNAVAILABLE, LANGUAGE_UNSUPPORTED,
        LANGUAGE_UNAVAILABLE, BUSY, AUDIO, NETWORK, SERVICE_FAILURE, START_FAILED, TIMEOUT, CANCELLED }

    /** Clear reason when no on-device recognizer exists; below API 31 there is none by design. */
    fun unavailableReason(sdkInt: Int): String = if (sdkInt < 31)
        "Spracheingabe braucht Android 12 oder neuer: Nur dort gibt es echte Erkennung auf dem Gerät. Bitte tippen; keine Audioübertragung gestartet."
    else "Kein lokaler Android-Spracherkenner verfügbar. Bitte tippen; keine Audioübertragung gestartet."

    fun message(failure: Failure): String = when (failure) {
        Failure.NO_MATCH -> "Keine verständlichen Wörter erkannt. Bitte erneut auf das Mikrofon tippen und deutlich sprechen."
        Failure.NO_INPUT -> "Keine Sprache gehört. Bitte erneut auf das Mikrofon tippen und nach dem Start sprechen."
        Failure.PERMISSION -> "Mikrofonberechtigung fehlt oder wurde entzogen. In Android unter App-Berechtigungen das Mikrofon freigeben und erneut starten."
        Failure.SERVICE_UNAVAILABLE -> "Lokaler Android-Spracherkenner nicht verfügbar. Lokale Sprachunterstützung in Android prüfen oder tippen."
        Failure.LANGUAGE_UNSUPPORTED -> "Die lokalen Spracherkennungsvarianten Deutsch (Österreich und Deutschland) wurden nicht unterstützt. Prüfe die lokale deutsche Spracherkennung in Android oder verwende Texteingabe."
        Failure.LANGUAGE_UNAVAILABLE -> "Die lokalen Sprachdaten für Deutsch (Österreich und Deutschland) sind nicht verfügbar. Prüfe in Android die deutschen Offline-Sprachdaten; installiere sie bei Bedarf ausdrücklich und starte dann erneut."
        Failure.BUSY -> "Der lokale Spracherkenner ist gerade belegt. Kurz warten und erneut auf das Mikrofon tippen."
        Failure.AUDIO -> "Das Mikrofon konnte kein Audio liefern. Andere Mikrofon-Apps und die Android-Mikrofonsperre prüfen; danach erneut starten."
        Failure.NETWORK -> "Der lokale Spracherkenner meldet einen Netzwerkfehler. Bitte tippen oder die lokale Android-Sprachunterstützung prüfen. Es wird kein Cloud-Ersatz gestartet."
        Failure.SERVICE_FAILURE -> "Der lokale Spracherkennungsdienst meldet einen Fehler. Bitte erneut starten oder die lokale Android-Sprachunterstützung prüfen."
        Failure.START_FAILED -> "Die lokale Spracherkennung konnte nicht gestartet werden. Bitte erneut versuchen oder tippen. Es wird kein Cloud-Ersatz gestartet."
        Failure.TIMEOUT -> "Lokale Spracheingabe nach 30 Sekunden beendet. Für einen neuen Versuch erneut auf das Mikrofon tippen."
        Failure.CANCELLED -> "Lokale Spracheingabe abgebrochen."
    }

    enum class Route { ON_DEVICE, REQUEST_MICROPHONE, EXTERNAL_WITH_EXPLICIT_GRANT, UNAVAILABLE }
    fun route(mode:InternetAccessMode,offlineVeto:Boolean,onDeviceAvailable:Boolean,microphoneGranted:Boolean,
              externalAudioGrant:Boolean=false):Route = when {
        onDeviceAvailable&&!microphoneGranted -> Route.REQUEST_MICROPHONE
        onDeviceAvailable -> Route.ON_DEVICE
        mode!=InternetAccessMode.OFF&&!offlineVeto&&externalAudioGrant -> Route.EXTERNAL_WITH_EXPLICIT_GRANT
        else -> Route.UNAVAILABLE
    }
    /** Half-duplex: never start TTS into an active on-device microphone turn, otherwise the
     * recognizer transcribes the assistant's own voice into the owner's composer (Argon 4, 7.1). */
    fun maySpeak(networkVoiceRequired:Boolean?,externalAudioGrant:Boolean=false,
                 mode:InternetAccessMode=InternetAccessMode.OFF,offlineVeto:Boolean=false,
                 microphoneActive:Boolean=false):Boolean = !microphoneActive && (
        networkVoiceRequired==false || networkVoiceRequired==true&&externalAudioGrant&&mode!=InternetAccessMode.OFF&&!offlineVeto)

    data class DictationMerge(val text:String, val cursor:Int)

    /** Speech -> composer handoff: insert the transcript at the owner's cursor (replacing only an
     * explicit selection) instead of overwriting a typed or restored draft. Preview only; never sends. */
    fun mergeDictation(draft:String, selectionStart:Int, selectionEnd:Int, spoken:String):DictationMerge {
        val known=selectionStart in 0..draft.length && selectionEnd in 0..draft.length
        val from=if(known)minOf(selectionStart,selectionEnd) else draft.length
        val to=if(known)maxOf(selectionStart,selectionEnd) else draft.length
        val before=draft.substring(0,from); val after=draft.substring(to)
        val lead=if(before.isNotEmpty()&&!before.last().isWhitespace()&&spoken.isNotEmpty())" " else ""
        val trail=if(after.isNotEmpty()&&!after.first().isWhitespace()&&spoken.isNotEmpty())" " else ""
        val head=before+lead+spoken
        return DictationMerge(head+trail+after, head.length)
    }
}

/** Only token/time state is retained, never microphone audio or a transcript. */
internal class EphemeralSpeechTurn(private val now:()->Long=System::nanoTime){
    private var generation=0L;private var active:Long?=null;private var started=0L
    @Synchronized fun start():Long {started=now();return (++generation).also{active=it}}
    @Synchronized fun cancel(){generation++;active=null}
    @Synchronized fun isCurrent(token:Long)=active==token
    @Synchronized fun isActive(token:Long)=active==token&&(now()-started).coerceAtLeast(0)<=30_000_000_000L
    @Synchronized fun accept(token:Long,text:String):String? {
        if(!isActive(token))return null
        active=null
        return text.take(2_000).map{if(it.isISOControl())' ' else it}.joinToString("").trim().takeIf{it.isNotEmpty()}
    }
}
