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
import at.heinz.deviceai.cognitive.LocalSpeechEngine
import at.heinz.deviceai.cognitive.LocalSpeechInputPolicy
import at.heinz.deviceai.cognitive.LocalSpeechSession

/** Owner-gesture, main-thread, bounded on-device recognition. No service launch,
 * download, transcript persistence, retry or automatic tool execution. */
internal class AndroidOnDeviceSpeechInput(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var warmRecognizer: SpeechRecognizer? = null
    private var warmEngine: LocalSpeechEngine = LocalSpeechEngine.NONE

    private fun destroyWarmRecognizer() {
        val recognizer = warmRecognizer
        warmRecognizer = null
        warmEngine = LocalSpeechEngine.NONE
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
    }

    private fun acquireWarmRecognizer(): SpeechRecognizer {
        val selected = engine()
        check(selected != LocalSpeechEngine.NONE) { "SPEECH_RECOGNITION_UNAVAILABLE" }
        warmRecognizer?.takeIf { warmEngine == selected }?.let { return it }
        destroyWarmRecognizer()
        return when (selected) {
            LocalSpeechEngine.ON_DEVICE -> if (Build.VERSION.SDK_INT >= 31) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else error("ON_DEVICE_RECOGNITION_REQUIRES_API_31")
            LocalSpeechEngine.NONE -> error("SPEECH_RECOGNITION_UNAVAILABLE")
        }.also {
            warmRecognizer = it
            warmEngine = selected
        }
    }

    private val session = LocalSpeechSession(object : LocalSpeechSession.Platform {
        override fun available() = this@AndroidOnDeviceSpeechInput.available()
        override fun microphoneGranted() = this@AndroidOnDeviceSpeechInput.microphoneGranted()
        override fun scheduleTimeout(task: () -> Unit): () -> Unit {
            val runnable = Runnable(task)
            handler.postDelayed(runnable, 30_000L)
            return { handler.removeCallbacks(runnable) }
        }
        override fun create(callbacks: LocalSpeechSession.Callbacks): LocalSpeechSession.Recognizer {
            val recognizer = acquireWarmRecognizer()
            var closed = false
            var warmRetryUsed = false
            var retryRunnable: Runnable? = null

            fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-AT")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SENTENCE_END_SILENCE_MS)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, SENTENCE_END_SILENCE_MS)
            }

            fun startNative() {
                if (!closed) recognizer.startListening(intent())
            }

            try {
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = callbacks.ready()
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        if (closed) return
                        if (!warmRetryUsed && isWarmRetryableSpeechError(error)) {
                            warmRetryUsed = true
                            val retry = Runnable {
                                retryRunnable = null
                                if (closed) return@Runnable
                                runCatching { startNative() }.onFailure {
                                    callbacks.failed(LocalSpeechInputPolicy.Failure.START_FAILED)
                                }
                            }
                            retryRunnable = retry
                            handler.postDelayed(retry, WARM_RECOGNIZER_RETRY_MS)
                            return
                        }
                        if (isWarmRetryableSpeechError(error)) destroyWarmRecognizer()
                        callbacks.failed(classifySpeechError(error))
                    }
                    override fun onResults(results: Bundle?) = callbacks.result(
                        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            } catch (error: RuntimeException) {
                destroyWarmRecognizer()
                throw error
            }

            return object : LocalSpeechSession.Recognizer {
                override fun start() = startNative()
                override fun cancel() {
                    if (closed) return
                    closed = true
                    retryRunnable?.let(handler::removeCallbacks)
                    retryRunnable = null
                    runCatching { recognizer.cancel() }
                }
                override fun destroy() {
                    // End this turn lease only. The native recognizer stays warm until Platform.shutdown().
                    closed = true
                    retryRunnable?.let(handler::removeCallbacks)
                    retryRunnable = null
                }
            }
        }
        override fun shutdown() = destroyWarmRecognizer()
    })

    fun available(): Boolean = engine() != LocalSpeechEngine.NONE

    private fun engine(): LocalSpeechEngine {
        val onDevice = Build.VERSION.SDK_INT >= 31 &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)
        return LocalSpeechEngine.choose(Build.VERSION.SDK_INT, onDevice)
    }
    fun microphoneGranted() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    fun cancel() { checkMainThread(); session.cancel() }
    fun shutdown() { checkMainThread(); session.shutdown() }
    fun start(onState: (Boolean) -> Unit, onText: (String) -> Unit, onError: (String) -> Unit) {
        checkMainThread()
        session.start(onState, onText, onError)
    }
    private fun checkMainThread() = check(Looper.myLooper() == Looper.getMainLooper())
}

/** Pause after which a spoken sentence counts as finished (hands-free turn and dictation alike). */
internal const val SENTENCE_END_SILENCE_MS = 3_000L

/** Android constants remain at the adapter boundary; the lifecycle policy has no Android dependency. */
internal fun classifySpeechError(error: Int): LocalSpeechInputPolicy.Failure = when (error) {
    SpeechRecognizer.ERROR_NO_MATCH -> LocalSpeechInputPolicy.Failure.NO_MATCH
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> LocalSpeechInputPolicy.Failure.NO_INPUT
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> LocalSpeechInputPolicy.Failure.PERMISSION
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> LocalSpeechInputPolicy.Failure.LANGUAGE_UNSUPPORTED
    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> LocalSpeechInputPolicy.Failure.LANGUAGE_UNAVAILABLE
    SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> LocalSpeechInputPolicy.Failure.BUSY
    SpeechRecognizer.ERROR_AUDIO -> LocalSpeechInputPolicy.Failure.AUDIO
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> LocalSpeechInputPolicy.Failure.NETWORK
    SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> LocalSpeechInputPolicy.Failure.SERVICE_FAILURE
    else -> LocalSpeechInputPolicy.Failure.SERVICE_FAILURE
}

internal const val WARM_RECOGNIZER_RETRY_MS = 250L
internal fun isWarmRetryableSpeechError(error: Int): Boolean =
    error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
