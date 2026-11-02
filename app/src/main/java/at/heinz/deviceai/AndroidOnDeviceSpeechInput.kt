package at.heinz.deviceai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import at.heinz.deviceai.cognitive.EphemeralSpeechTurn

/** Main-thread, owner-gesture, bounded on-device Android recognizer. No service,
 * model download, transcript persistence, cloud fallback or automatic tool execution. */
internal class AndroidOnDeviceSpeechInput(private val context:Context){
    private val handler=Handler(Looper.getMainLooper())
    private val turn=EphemeralSpeechTurn()
    private var recognizer:SpeechRecognizer?=null
    private var timeout:Runnable?=null
    private var state:((Boolean)->Unit)?=null
    fun available():Boolean=Build.VERSION.SDK_INT>=31&&runCatching{SpeechRecognizer.isOnDeviceRecognitionAvailable(context)}.getOrDefault(false)
    fun microphoneGranted()=context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
    fun cancel(){
        turn.cancel();timeout?.let(handler::removeCallbacks);timeout=null
        val r=recognizer;recognizer=null
        runCatching{r?.cancel()};runCatching{r?.destroy()};state?.invoke(false);state=null
    }
    fun start(onState:(Boolean)->Unit,onText:(String)->Unit,onError:(String)->Unit){
        check(Looper.myLooper()==Looper.getMainLooper())
        cancel();check(available()&&microphoneGranted()){"ON_DEVICE_SPEECH_NOT_AVAILABLE_OR_NOT_GRANTED"}
        if(Build.VERSION.SDK_INT<31){onError("Lokale Spracherkennung benötigt Android 12 oder neuer.");return}
        val token=turn.start();state=onState
        fun finish(){timeout?.let(handler::removeCallbacks);timeout=null
            val r=recognizer;recognizer=null;runCatching{r?.destroy()};state?.invoke(false);state=null}
        val r=SpeechRecognizer.createOnDeviceSpeechRecognizer(context);recognizer=r
        r.setRecognitionListener(object:RecognitionListener{
            override fun onReadyForSpeech(params:Bundle?){if(turn.isActive(token))onState(true)}
            override fun onBeginningOfSpeech(){}
            override fun onRmsChanged(rmsdB:Float){}
            override fun onBufferReceived(buffer:ByteArray?){} // audio never retained
            override fun onEndOfSpeech(){}
            override fun onError(error:Int){if(turn.isCurrent(token)){turn.cancel();finish();onError("Lokale Spracherkennung fehlgeschlagen (Code $error). Kein Cloud-Fallback.")}}
            override fun onResults(results:Bundle?){
                if(!turn.isCurrent(token))return
                if(!microphoneGranted()){cancel();return}
                val text=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                val accepted=turn.accept(token,text)
                turn.cancel();finish();if(accepted!=null)onText(accepted)
            }
            override fun onPartialResults(partialResults:Bundle?){}
            override fun onEvent(eventType:Int,params:Bundle?){}
        })
        timeout=Runnable{if(turn.isCurrent(token)){cancel();onError("Lokale Spracheingabe nach 30 Sekunden beendet.")}}
            .also{handler.postDelayed(it,30_000)}
        try{onState(true);r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE,"de-AT");putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false)
        })}catch(error:RuntimeException){cancel();onError("Lokale Spracherkennung konnte nicht gestartet werden. Kein Cloud-Fallback.")}
    }
}
