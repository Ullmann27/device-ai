package at.heinz.deviceai.notifications

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import at.heinz.deviceai.cognitive.JarvisVoiceProfile
import java.util.Locale

/**
 * One offline German voice for a sentence spoken while Continuum's own screen is not in front (drive announcements,
 * the short hint about an incoming message). It only speaks: no microphone, no dialogue, no action. Only an offline
 * voice is used – private notification content never leaves the device for synthesis; if the device has none,
 * nothing is said. Main thread only.
 *
 * Not verified on a device: audio focus and routing from a bound background service (speaker, Bluetooth, car)
 * depend on the Android version and vendor.
 */
internal class BackgroundGermanVoice(context: Context, private val usage: Int, private val utterancePrefix: String) {
    private val appContext: Context = context.applicationContext ?: context
    private val handler = Handler(Looper.getMainLooper())
    private var speech: TextToSpeech? = null
    private var ready = false
    private var utterance = 0L

    /** Queues [text]; a sentence that arrives while the engine is still starting is dropped, not delayed. */
    fun say(text: String) {
        val engine = speech
        if (engine != null && ready) { speak(engine, text); return }
        if (engine == null) speech = TextToSpeech(appContext) { status ->
            handler.post {
                val created = speech
                if (status != TextToSpeech.SUCCESS || created == null || !configure(created)) { release(); return@post }
                ready = true
                speak(created, text)
            }
        }
    }

    fun release() {
        runCatching { speech?.stop(); speech?.shutdown() }
        speech = null
        ready = false
    }

    private fun configure(engine: TextToSpeech): Boolean {
        val voice = runCatching {
            engine.voices?.filter { it.locale?.language == Locale.GERMAN.language && !it.isNetworkConnectionRequired }
                ?.minByOrNull { if (it.locale?.country == "DE") 0 else 1 }
        }.getOrNull() ?: return false
        if (engine.setVoice(voice) != TextToSpeech.SUCCESS) return false
        engine.setSpeechRate(JarvisVoiceProfile.speechRate)
        engine.setPitch(JarvisVoiceProfile.pitch)
        engine.setAudioAttributes(AudioAttributes.Builder().setUsage(usage)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        return true
    }

    private fun speak(engine: TextToSpeech, text: String) {
        engine.speak(text, TextToSpeech.QUEUE_ADD, null, utterancePrefix + ++utterance)
    }
}
