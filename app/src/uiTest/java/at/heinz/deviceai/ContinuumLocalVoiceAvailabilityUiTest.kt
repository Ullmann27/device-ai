package at.heinz.deviceai

import android.content.Context
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.widget.Button
import at.heinz.deviceai.cognitive.InternetAccessMode
import at.heinz.deviceai.cognitive.InternetConsentPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowTextToSpeech
import org.robolectric.shadows.ShadowToast
import java.time.Duration
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * The local (offline) speech output must work, or say why it cannot, independent of the network voice Maya.
 * Real AgentActivity speech path; the Android engine is controlled or Robolectric's shadow. No audio is played
 * and no network voice is contacted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w412dp-h915dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContinuumLocalVoiceAvailabilityUiTest {
    @Before fun configureVirtualVsyncClock() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private class Engine(context: Context, private val available: Set<Voice>) : TextToSpeech(context, null) {
        private var selected: Voice? = null
        override fun getVoices(): Set<Voice> = available
        override fun getVoice(): Voice? = selected
        override fun setVoice(voice: Voice): Int { selected = voice; return SUCCESS }
    }

    private fun voice(name: String, network: Boolean = false) =
        Voice(name, Locale("de", "AT"), Voice.QUALITY_HIGH, Voice.LATENCY_NORMAL, network, emptySet())

    private fun read(a: AgentActivity, name: String): Any? =
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(a)

    private fun set(a: AgentActivity, name: String, value: Any?) =
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(a, value)

    private fun call(a: AgentActivity, name: String) =
        AgentActivity::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(a)

    private fun engineReports(a: AgentActivity, status: Int) = AgentActivity::class.java
        .getDeclaredMethod("onLocalVoiceInitialized", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
        .apply { isAccessible = true }.invoke(a, status, false)

    /** Empty when nothing was shown; otherwise exactly one toast was shown and this is its text. */
    private fun toasts(): List<String> = ShadowToast.shownToastCount().let { count ->
        assertTrue("at most one notice per event, not $count", count <= 1)
        if (count == 0) emptyList() else listOf(ShadowToast.getTextOfLatestToast())
    }

    /** An Activity whose Android engine has not reported yet; [voices] is what that engine has installed. */
    private fun beforeEngineReport(voices: Set<Voice>, voiceOn: Boolean, block: (AgentActivity) -> Unit) {
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        val a = controller.get()
        a.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit().clear().commit()
        a.getSharedPreferences("continuum-voice", Context.MODE_PRIVATE).edit().clear().commit()
        val engine = Engine(a, voices)
        set(a, "bootstrapMemory", BootstrapMemoryStore(a).apply { voiceName = null; voiceLanguage = "de" })
        set(a, "voiceEngine", engine)
        set(a, "voiceInitialized", false)
        set(a, "voiceEnabled", voiceOn)
        ShadowToast.reset()
        try { block(a) } finally {
            ShadowAlertDialog.getLatestAlertDialog()?.dismiss()
            engine.shutdown()
            runCatching { controller.close() }
        }
    }

    @Test fun engineStartFailureIsToldToAnOwnerWhoHasSoundOn() =
        beforeEngineReport(setOf(voice("offline")), voiceOn = true) { a ->
            engineReports(a, TextToSpeech.ERROR)
            assertEquals(false, read(a, "voiceReady"))
            val notice = toasts().single()
            assertTrue(notice, notice.contains("konnte nicht gestartet werden"))
            assertTrue("the reply itself is not lost: $notice", notice.contains("Antworten bleiben im Chat lesbar"))
            // The voice picker of a failed engine names the cause instead of offering an empty list.
            ShadowToast.reset()
            call(a, "showOfflineVoicePicker")
            assertNull("no picker without an engine", ShadowAlertDialog.getLatestAlertDialog())
            assertEquals(notice, toasts().single())
        }

    @Test fun missingOfflineVoiceIsToldAndTheNetworkVoiceOfTheEngineIsNeverSelected() =
        beforeEngineReport(setOf(voice("network", network = true)), voiceOn = true) { a ->
            engineReports(a, TextToSpeech.SUCCESS)
            assertEquals(false, read(a, "voiceReady"))
            assertNull("no engine voice was selected", (read(a, "voiceEngine") as TextToSpeech).voice)
            val notice = toasts().single()
            assertTrue(notice, notice.contains("Keine Offline-Stimme für Deutsch installiert"))
            assertTrue(notice, notice.contains("Text-in-Sprache"))
        }

    @Test fun aWorkingOfflineVoiceOrSwitchedOffSoundCausesNoNotice() {
        beforeEngineReport(setOf(voice("offline")), voiceOn = true) { a ->
            engineReports(a, TextToSpeech.SUCCESS)
            assertEquals(true, read(a, "voiceReady"))
            assertEquals("offline", (read(a, "voiceEngine") as TextToSpeech).voice?.name)
            assertTrue(toasts().toString(), toasts().isEmpty())
        }
        for (status in listOf(TextToSpeech.ERROR, TextToSpeech.SUCCESS))
            beforeEngineReport(emptySet(), voiceOn = false) { a ->
                engineReports(a, status)
                assertTrue("sound is off: nothing to explain", toasts().isEmpty())
            }
    }

    /** The real started Activity with Robolectric's engine and one installed offline voice. */
    private fun withSpeakingActivity(body: (AgentActivity, TextToSpeech) -> Unit) {
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
            repeat(2) { shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS) }
            controller.windowFocusChanged(true)
            val a = controller.get()
            val tts = read(a, "voiceEngine") as TextToSpeech
            tts.setVoice(voice("de-at-local"))
            set(a, "voiceInitialized", true); set(a, "voiceEngineFailed", false)
            set(a, "voiceEnabled", true); set(a, "voiceReady", true)
            assertTrue("speech requires the foreground activity", read(a, "foregroundLearningActive") as Boolean)
            AgentActivity::class.java.getDeclaredMethod("installVoiceProgressListener").apply { isAccessible = true }.invoke(a)
            // The tests play the engine themselves; the shadow's automatic callbacks would mix in.
            tts.setOnUtteranceProgressListener(null)
            ShadowToast.reset()
            body(a, tts)
        } finally {
            runCatching { controller.close() }
        }
    }

    private fun spoken(tts: TextToSpeech): List<String> = (shadowOf(tts) as ShadowTextToSpeech).spokenTextList

    private fun speakReply(a: AgentActivity, text: String) = AgentActivity::class.java
        .getDeclaredMethod("speakReply", String::class.java, Boolean::class.javaPrimitiveType)
        .apply { isAccessible = true }.invoke(a, text, true)

    @Test fun mayaSelectedWithoutUsableNetworkVoiceReallySpeaksThroughTheOfflineVoice() {
        for (mode in InternetAccessMode.entries) withSpeakingActivity { a, tts ->
            a.getSharedPreferences("continuum-voice", Context.MODE_PRIVATE).edit().putBoolean("maya_enabled", true).commit()
            (read(a, "internetConsent") as InternetConsentPolicy).setMode(mode)
            // No ElevenLabs key is stored, so Maya is unavailable in every internet mode.
            assertEquals(false, call(a, "mayaAvailable"))
            speakReply(a, "Das ist die lokale Antwort.")
            assertEquals("$mode: the offline voice speaks the reply", listOf("Das ist die lokale Antwort."), spoken(tts))
            assertEquals(false, read(a, "speechUsesMaya"))
            assertNull("no network voice was created", read(a, "mayaSpeech"))
            // Voice profiles (20261119): the once-only notice also names the reason.
            assertEquals("Maya nicht verfügbar: Android-Offline-Stimme wird verwendet. Grund: kein ElevenLabs-Schlüssel gespeichert.",
                toasts().single())
        }
    }

    @Test fun aPieceMayaCouldNotSpeakIsContinuedByTheReadyOfflineVoice() = withSpeakingActivity { a, tts ->
        set(a, "speechUsesMaya", true)
        set(a, "voiceUtteranceId", "device-ai-maya-piece")
        set(a, "voiceAvatarText", "Diesen Teil konnte Maya nicht sprechen.")
        AgentActivity::class.java.getDeclaredMethod("onMayaPieceFailed", String::class.java)
            .apply { isAccessible = true }.invoke(a, "device-ai-maya-piece")
        assertEquals(listOf("Diesen Teil konnte Maya nicht sprechen."), spoken(tts))
        assertEquals("the rest of the reply stays with the offline voice", false, read(a, "speechUsesMaya"))
        assertEquals("the piece keeps its utterance", "device-ai-maya-piece", read(a, "voiceUtteranceId"))
        assertEquals("Maya konnte nicht sprechen. Die Android-Offline-Stimme spricht weiter.", toasts().single())
    }

    @Test fun withoutAReadyOfflineVoiceAFailedMayaPieceStaysTextAndIsNeverSentToAnotherNetworkVoice() {
        val failed = "Maya konnte nicht sprechen. Die Antwort bleibt im Chat. Prüfe Schlüssel, Credits und Internet."
        fun fail(a: AgentActivity, id: String) = AgentActivity::class.java
            .getDeclaredMethod("onMayaPieceFailed", String::class.java).apply { isAccessible = true }.invoke(a, id)
        // No installed offline voice.
        withSpeakingActivity { a, tts ->
            set(a, "voiceReady", false); set(a, "speechUsesMaya", true)
            set(a, "voiceUtteranceId", "device-ai-maya-piece"); set(a, "voiceAvatarText", "Nur Text.")
            fail(a, "device-ai-maya-piece")
            assertTrue(spoken(tts).isEmpty())
            assertEquals(failed, toasts().single())
            assertNull("the failed utterance has ended", read(a, "voiceUtteranceId"))
        }
        // The engine's selected voice needs the network itself.
        withSpeakingActivity { a, tts ->
            tts.setVoice(voice("de-network", network = true))
            set(a, "speechUsesMaya", true)
            set(a, "voiceUtteranceId", "device-ai-maya-piece"); set(a, "voiceAvatarText", "Nur Text.")
            fail(a, "device-ai-maya-piece")
            assertTrue(spoken(tts).isEmpty())
            assertEquals(failed, toasts().single())
        }
        // A late failure of an older piece does not speak over the current one.
        withSpeakingActivity { a, tts ->
            set(a, "speechUsesMaya", true)
            set(a, "voiceUtteranceId", "device-ai-current"); set(a, "voiceAvatarText", "Aktueller Teil.")
            fail(a, "device-ai-older")
            assertTrue(spoken(tts).isEmpty())
            assertEquals("device-ai-current", read(a, "voiceUtteranceId"))
        }
    }

    @Test fun theSoundButtonNamesTheRealCauseInsteadOfNotReadyYet() = withSpeakingActivity { a, _ ->
        val button = read(a, "voiceButton") as Button
        fun switchOn(): String {
            set(a, "voiceEnabled", false)
            ShadowToast.reset()
            assertTrue(button.performClick())
            assertEquals(true, read(a, "voiceEnabled"))
            return toasts().single()
        }
        set(a, "voiceReady", false); set(a, "voiceEngineFailed", true)
        assertTrue(switchOn().contains("konnte nicht gestartet werden"))
        set(a, "voiceEngineFailed", false)
        assertTrue(switchOn().contains("Keine Offline-Stimme für Deutsch installiert"))
        set(a, "voiceInitialized", false)
        assertEquals("Android-Sprachausgabe ist noch nicht bereit.", switchOn())
    }
}
