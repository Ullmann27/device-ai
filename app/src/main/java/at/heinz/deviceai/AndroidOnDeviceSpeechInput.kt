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
import at.heinz.deviceai.cognitive.LocalSpeechInputPolicy
import at.heinz.deviceai.cognitive.LocalSpeechSession

/** Owner-gesture, main-thread, bounded on-device recognition. No service launch,
 * download, transcript persistence, cloud fallback, retry or automatic tool execution. */
internal class AndroidOnDeviceSpeechInput(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val session = LocalSpeechSession(object : LocalSpeechSession.Platform {
        override fun available() = this@AndroidOnDeviceSpeechInput.available()
        override fun microphoneGranted() = this@AndroidOnDeviceSpeechInput.microphoneGranted()
        override fun scheduleTimeout(task: () -> Unit): () -> Unit {
            val runnable = Runnable(task)
            handler.postDelayed(runnable, 30_000L)
            return { handler.removeCallbacks(runnable) }
        }
        override fun create(callbacks: LocalSpeechSession.Callbacks): LocalSpeechSession.Recognizer {
            // Guard in the expression itself so lint sees the API check (same behaviour as before).
            val recognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            else error("ON_DEVICE_RECOGNITION_REQUIRES_API_31")
            try {
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = callbacks.ready()
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {} // audio is never retained
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) = callbacks.failed(classifySpeechError(error))
                    override fun onResults(results: Bundle?) = callbacks.result(
                        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            } catch (error: RuntimeException) {
                runCatching { recognizer.destroy() }
                throw error
            }
            return object : LocalSpeechSession.Recognizer {
                override fun start() = recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-AT")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                })
                override fun cancel() = recognizer.cancel()
                override fun destroy() = recognizer.destroy()
            }
        }
    })

    fun available(): Boolean = Build.VERSION.SDK_INT >= 31 &&
        runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)
    fun microphoneGranted() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    fun cancel() { checkMainThread(); session.cancel() }
    fun start(onState: (Boolean) -> Unit, onText: (String) -> Unit, onError: (String) -> Unit) {
        checkMainThread()
        session.start(onState, onText, onError)
    }
    private fun checkMainThread() = check(Looper.myLooper() == Looper.getMainLooper())
}

/** Android constants remain at the adapter boundary; the lifecycle policy has no Android dependency. */
internal fun classifySpeechError(error: Int): LocalSpeechInputPolicy.Failure = when (error) {
    SpeechRecognizer.ERROR_NO_MATCH -> LocalSpeechInputPolicy.Failure.NO_MATCH
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> LocalSpeechInputPolicy.Failure.NO_INPUT
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> LocalSpeechInputPolicy.Failure.PERMISSION
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> LocalSpeechInputPolicy.Failure.LANGUAGE_UNSUPPORTED
    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> LocalSpeechInputPolicy.Failure.LANGUAGE_UNAVAILABLE
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> LocalSpeechInputPolicy.Failure.BUSY
    SpeechRecognizer.ERROR_AUDIO -> LocalSpeechInputPolicy.Failure.AUDIO
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> LocalSpeechInputPolicy.Failure.NETWORK
    SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> LocalSpeechInputPolicy.Failure.SERVICE_FAILURE
    else -> LocalSpeechInputPolicy.Failure.SERVICE_FAILURE
}
