package at.heinz.deviceai

import android.content.Context
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import at.heinz.deviceai.cognitive.JarvisVoiceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
import java.time.Duration
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Fold7 report #566: long answers were visible but read aloud only up to 480 characters. The real AgentActivity
 * speech path and its UtteranceProgressListener; the Android TTS engine is Robolectric's shadow, no audio is played.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w412dp-h915dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContinuumLongReplySpeechUiTest {
    @Before fun configureVirtualVsyncClock() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private fun field(a: AgentActivity, name: String): Any? =
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(a)

    private fun set(a: AgentActivity, name: String, value: Any) =
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(a, value)

    private fun call(a: AgentActivity, name: String, vararg args: Any) {
        val types = args.map { if (it is String) String::class.java else it.javaClass }.toTypedArray()
        AgentActivity::class.java.getDeclaredMethod(name, *types).apply { isAccessible = true }.invoke(a, *args)
    }

    private fun withSpeakingActivity(body: (AgentActivity, TextToSpeech, UtteranceProgressListener) -> Unit) {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit().putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        try {
            controller.setup().visible()
            repeat(2) { shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS) }
            controller.windowFocusChanged(true)
            val a = controller.get()
            val tts = field(a, "voiceEngine") as TextToSpeech
            tts.setVoice(Voice("de-de-local", Locale.GERMANY, Voice.QUALITY_HIGH, Voice.LATENCY_NORMAL, false, emptySet()))
            set(a, "voiceEnabled", true); set(a, "voiceReady", true)
            assertTrue("speech requires the foreground activity", field(a, "foregroundLearningActive") as Boolean)
            AgentActivity::class.java.getDeclaredMethod("installVoiceProgressListener").apply { isAccessible = true }.invoke(a)
            val listener = requireNotNull(shadowOf(tts).utteranceProgressListener)
            body(a, tts, listener)
        } finally {
            runCatching { controller.close() }
        }
    }

    private val longReply = (1..32).joinToString(" ") {
        "Schritt $it: Ich prüfe die betroffene Datei, starte den Test und erkläre das Ergebnis verständlich."
    }

    private fun spoken(tts: TextToSpeech) = (shadowOf(tts) as ShadowTextToSpeech).spokenTextList

    @Test fun longReplyIsReadCompletelyInOrderAcrossPieces() = withSpeakingActivity { a, tts, listener ->
        assertTrue("fixture must exceed the former 480-character limit", longReply.length > 2_000)
        call(a, "speakBootstrap", longReply)
        val ids = HashSet<String>()
        var guard = 0
        while (true) {
            val id = field(a, "voiceUtteranceId") as String? ?: break
            assertTrue("each piece has its own utterance id", ids.add(id))
            listener.onStart(id)
            assertEquals("the avatar speaks this piece", true, field(a, "voiceSpeaking"))
            listener.onDone(id)
            assertTrue(++guard < 40)
        }
        val pieces = spoken(tts)
        val expected = JarvisVoiceProfile.speakableChunks(longReply).pieces
        assertTrue("a long reply needs several pieces: ${pieces.size}", pieces.size >= 4)
        assertEquals("every piece is spoken once, in order", expected, pieces)
        assertTrue(pieces.all { it.length <= 480 })
        assertEquals("nothing of the reply is lost", longReply.replace(Regex("\\s+"), " "), pieces.joinToString(" "))
        assertEquals("speech ended cleanly after the last piece", false, field(a, "voiceSpeaking"))
        assertNull(field(a, "speechTurnId"))
    }

    @Test fun stopEndsTheReplyAndLateCallbacksCannotRestartIt() = withSpeakingActivity { a, tts, listener ->
        call(a, "speakBootstrap", longReply)
        val first = field(a, "voiceUtteranceId") as String
        listener.onStart(first)
        listener.onStop(first, true)
        assertNull("STOP clears the active utterance", field(a, "voiceUtteranceId"))
        assertEquals("mouth/voice state is closed", false, field(a, "voiceSpeaking"))
        val afterStop = spoken(tts).size
        listener.onDone(first) // late engine callback for the stopped piece
        assertEquals("no further piece after STOP", afterStop, spoken(tts).size)
        // A new short answer starts cleanly; a stale callback of the old reply cannot advance it.
        call(a, "speakBootstrap", "Kurze neue Antwort.")
        val next = field(a, "voiceUtteranceId") as String
        listener.onDone(first)
        assertEquals("stale callback ignored", next, field(a, "voiceUtteranceId"))
        listener.onStart(next); listener.onDone(next)
        assertEquals("Kurze neue Antwort.", spoken(tts).last())
        assertNull(field(a, "voiceUtteranceId"))
    }

    @Test fun fiveConsecutiveRepliesEachFinishWithClosedMouth() = withSpeakingActivity { a, tts, listener ->
        repeat(5) { turn ->
            call(a, "speakBootstrap", "Antwort ${turn + 1}. " + longReply.take(700))
            while (true) {
                val id = field(a, "voiceUtteranceId") as String? ?: break
                listener.onStart(id); listener.onDone(id)
            }
            assertEquals("reply ${turn + 1} ends with a closed mouth", false, field(a, "voiceSpeaking"))
        }
        assertTrue(spoken(tts).count { it.startsWith("Antwort") } == 5)
    }
}
