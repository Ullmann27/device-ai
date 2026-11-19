package at.heinz.deviceai

import android.content.Context
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.widget.EditText
import at.heinz.deviceai.voice.ElevenLabsSpec
import at.heinz.deviceai.voice.ProsodyPreset
import at.heinz.deviceai.voice.VoiceEngine
import at.heinz.deviceai.voice.VoiceProfileSelection
import at.heinz.deviceai.voice.VoiceProfiles
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowTextToSpeech
import org.robolectric.shadows.ShadowToast
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import java.security.cert.Certificate
import java.time.Duration
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

/**
 * Voice profiles through the real AgentActivity speech path. Robolectric's TTS shadow plays nothing and no
 * ElevenLabs request leaves the test (fake connection only). How anything SOUNDS is NOT_RUN here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w412dp-h915dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContinuumVoiceProfileUiTest {
    @Before fun configureVirtualVsyncClock() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private fun field(a: AgentActivity, name: String): Any? =
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(a)

    private fun set(a: AgentActivity, name: String, value: Any) =
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(a, value)

    private fun call(a: AgentActivity, name: String, vararg args: Any) {
        val types = args.map {
            when (it) { is String -> String::class.java; is Boolean -> Boolean::class.javaPrimitiveType!!; else -> it.javaClass }
        }.toTypedArray()
        AgentActivity::class.java.getDeclaredMethod(name, *types).apply { isAccessible = true }.invoke(a, *args)
    }

    private fun idle(ms: Long = 16) = shadowOf(Looper.getMainLooper()).idleFor(ms, TimeUnit.MILLISECONDS)

    private fun withSpeakingActivity(body: (AgentActivity, TextToSpeech, UtteranceProgressListener) -> Unit) {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("continuum-command03", Context.MODE_PRIVATE).edit()
            .putBoolean("original_html_main", false).commit()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit().putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        app.getSharedPreferences("continuum-voice", Context.MODE_PRIVATE).edit().clear().commit()
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        try {
            controller.setup().visible()
            repeat(2) { idle() }
            controller.windowFocusChanged(true)
            val a = controller.get()
            val tts = field(a, "voiceEngine") as TextToSpeech
            tts.setVoice(Voice("de-de-local", Locale.GERMANY, Voice.QUALITY_HIGH, Voice.LATENCY_NORMAL, false, emptySet()))
            set(a, "voiceInitialized", true); set(a, "voiceEngineFailed", false)
            set(a, "voiceEnabled", true); set(a, "voiceReady", true)
            AgentActivity::class.java.getDeclaredMethod("installVoiceProgressListener").apply { isAccessible = true }.invoke(a)
            val listener = requireNotNull(shadowOf(tts).utteranceProgressListener)
            tts.setOnUtteranceProgressListener(null)
            ShadowToast.reset()
            body(a, tts, listener)
        } finally {
            runCatching { controller.close() }
        }
    }

    private fun spoken(tts: TextToSpeech) = (shadowOf(tts) as ShadowTextToSpeech).spokenTextList
    private val longReply = (1..12).joinToString(" ") {
        "Abschnitt $it: Ich prüfe die betroffene Datei, starte den Test und erkläre das Ergebnis verständlich."
    }

    private fun send(a: AgentActivity, text: String) {
        (field(a, "input") as EditText).setText(text)
        call(a, "sendCurrentInput", false)
        idle()
    }

    @Test fun weiterResumesAnInterruptedReplyFromTheInterruptedPiece() = withSpeakingActivity { a, tts, listener ->
        call(a, "speakBootstrap", longReply)
        val first = field(a, "voiceUtteranceId") as String
        listener.onStart(first); listener.onDone(first)
        idle(at.heinz.deviceai.cognitive.HumanSpeechTiming.MAX_PAUSE_MS + 50)
        val second = field(a, "voiceUtteranceId") as String
        listener.onStart(second)
        val pieces = spoken(tts).toList()
        assertTrue("fixture has more than two pieces", pieces.size == 2)
        call(a, "interruptReplySpeech")
        assertEquals("the mouth closes on the interruption", false, field(a, "voiceSpeaking"))
        val before = spoken(tts).size
        send(a, "weiter")
        assertEquals("the input was consumed", "", (field(a, "input") as EditText).text.toString())
        val resumed = spoken(tts).drop(before)
        assertEquals("resumes with the interrupted piece, not from the start", pieces[1], resumed.first())
        // A second „weiter“ after a complete reply has nothing to resume and keeps its former meaning.
        var guard = 0
        while (true) {
            val id = field(a, "voiceUtteranceId") as String? ?: break
            listener.onStart(id); listener.onDone(id)
            idle(at.heinz.deviceai.cognitive.HumanSpeechTiming.MAX_PAUSE_MS + 50)
            assertTrue(++guard < 30)
        }
        assertFalse((field(a, "resumableSpeech") as at.heinz.deviceai.voice.ResumableReplySpeech)
            .canResume(field(a, "sessionId") as String))
    }

    @Test fun spokenStepChangesTheProfileAndConfirmsInOneSentence() = withSpeakingActivity { a, tts, _ ->
        send(a, "Sprich langsamer")
        val prefs = a.getSharedPreferences("continuum-voice", Context.MODE_PRIVATE)
        assertEquals(-1, prefs.getInt("voice_profile_rate_step", 0))
        assertTrue(spoken(tts).toString(), spoken(tts).any { it.contains("Ich spreche jetzt etwas langsamer") &&
            it.contains("96 Prozent auf 91 Prozent") })
        send(a, "Nimm wieder die bisherige Stimme")
        assertEquals(0, prefs.getInt("voice_profile_rate_step", 0))
        assertEquals("BISHERIG", prefs.getString("voice_profile_preset", null))
    }

    @Test fun repliesAreSpokenInShapedFormAndTheFallbackIsSaidOnce() = withSpeakingActivity { a, tts, _ ->
        a.getSharedPreferences("continuum-voice", Context.MODE_PRIVATE).edit().putBoolean("maya_enabled", true).commit()
        call(a, "speakReply", "Termin um 14:30 Uhr, frei sind 39,3 GB.", true)
        assertEquals(listOf("Termin um 14 Uhr 30, frei sind 39 Komma 3 Gigabyte."), spoken(tts))
        call(a, "speakReply", "Zweite Antwort.", true)
        assertEquals("Zweite Antwort.", spoken(tts).last())
        assertEquals(1, ShadowToast.shownToastCount())
        assertTrue(ShadowToast.getTextOfLatestToast().startsWith("Maya nicht verfügbar: Android-Offline-Stimme wird verwendet."))
    }

    @Test fun theDeviceVoiceUsesThePresetRate() = withSpeakingActivity { a, tts, _ ->
        at.heinz.deviceai.voice.VoiceProfileStore.android(a).save(VoiceProfileSelection(preset = ProsodyPreset.SOUVERAEN))
        call(a, "speakReply", "Ruhige Antwort.", true)
        assertEquals(listOf("Ruhige Antwort."), spoken(tts))
        assertEquals(VoiceProfileSelection(preset = ProsodyPreset.SOUVERAEN), field(a, "speechProfile"))
    }

    private class Connection(url: String) : HttpsURLConnection(URL(url)) {
        val body = ByteArrayOutputStream()
        override fun getOutputStream() = body
        override fun getInputStream() = ByteArrayInputStream("""{"audio_base64":"AAABAA=="}""".toByteArray())
        override fun getResponseCode() = 200
        override fun getContentType() = "application/json"
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy() = false
        override fun getCipherSuite() = "test-only"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }

    @Test fun aChosenVoiceAndPresetReachTheElevenLabsRequest() {
        val spec: ElevenLabsSpec = VoiceProfiles.elevenLabs(VoiceProfileSelection(VoiceEngine.ELEVENLABS,
            "AbCdEf1234567890", "Tiefe Stimme", ProsodyPreset.SOUVERAEN))
        assertEquals("https://api.elevenlabs.io/v1/text-to-speech/AbCdEf1234567890/with-timestamps?output_format=pcm_24000",
            ElevenLabsPcmRequest.endpoint(spec))
        val connection = Connection(ElevenLabsPcmRequest.endpoint(spec))
        ElevenLabsPcmRequest(connection, spec).use { assertEquals(4, it.synthesize("Hallo", "synthetic_test_key") { true }.size) }
        val body = JSONObject(connection.body.toString("UTF-8"))
        assertEquals("eleven_multilingual_v2", body.getString("model_id"))
        val settings = body.getJSONObject("voice_settings")
        assertEquals(0.70, settings.getDouble("stability"), 1e-9)
        assertEquals(0.94, settings.getDouble("speed"), 1e-9)
        assertEquals(ElevenLabsPcmRequest.ENDPOINT, ElevenLabsPcmRequest.endpoint(ElevenLabsPcmRequest.FORMER))
    }
}
