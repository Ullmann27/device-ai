package at.heinz.deviceai.cognitive

/** Android EXTRA_PREFER_OFFLINE is advisory, never a network permission or offline proof.
 * The actual UI uses on-device ASR only; cloud audio needs a separate explicit per-use grant. */
internal object LocalSpeechInputPolicy {
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
