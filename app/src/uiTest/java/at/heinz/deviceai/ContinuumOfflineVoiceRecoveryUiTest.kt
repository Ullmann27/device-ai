package at.heinz.deviceai

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import at.heinz.deviceai.cognitive.JarvisVoiceSelection
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowToast
import java.util.Locale

// Controlled engine; no audio measurement.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContinuumOfflineVoiceRecoveryUiTest {
    private class Engine(context: Context, private val available: Set<Voice>, private val rejected: Set<String>) :
        TextToSpeech(context, null) {
        val attempts = mutableListOf<String>()
        private var selected: Voice? = null
        override fun getVoices(): Set<Voice> = available
        override fun getVoice(): Voice? = selected
        override fun setVoice(voice: Voice): Int {
            attempts += voice.name
            if (voice.name in rejected) return ERROR
            selected = voice
            return SUCCESS
        }
    }

    private fun voice(name: String, country: String = "DE", network: Boolean = false) =
        Voice(name, Locale("de", country), Voice.QUALITY_HIGH, Voice.LATENCY_NORMAL, network, emptySet())

    private fun read(activity: AgentActivity, name: String): Any? =
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(activity)

    private fun set(activity: AgentActivity, name: String, value: Any) =
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)

    private fun call(activity: AgentActivity, name: String) =
        AgentActivity::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(activity)

    private fun withEngine(owner: String?, rejected: Set<String>, block: (AgentActivity, Engine) -> Unit) {
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        val activity = controller.get()
        activity.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit().clear().commit()
        val memory = BootstrapMemoryStore(activity).apply { voiceName = owner; voiceLanguage = "de" }
        val engine = Engine(activity, linkedSetOf(voice("preferred", "AT"), voice("backup"), voice("network", network = true)), rejected)
        set(activity, "bootstrapMemory", memory)
        set(activity, "voiceEngine", engine)
        set(activity, "voiceInitialized", true)
        set(activity, "voiceEnabled", false)
        ShadowToast.reset()
        try { block(activity, engine) } finally {
            ShadowAlertDialog.getLatestAlertDialog()?.dismiss()
            engine.shutdown()
            runCatching { controller.close() }
        }
    }

    @Test fun failedOwnerVoiceFallsBackToAnActuallyLoadableOfflineVoice() =
        withEngine("preferred", setOf("preferred")) { activity, engine ->
            call(activity, "refreshOfflineVoice")
            assertEquals(true, read(activity, "voiceReady"))
            assertEquals("backup", engine.voice?.name)
            assertEquals(listOf("preferred", "backup"), engine.attempts)
            val choice = read(activity, "voiceChoice") as JarvisVoiceSelection.Choice
            assertTrue(choice.ownerChoiceDropped)
            assertEquals(JarvisVoiceSelection.Origin.AUTOMATIC, choice.origin)
        }

    @Test fun brokenAutomaticWinnerDoesNotPreventTheNextOfflineVoice() =
        withEngine(null, setOf("preferred")) { activity, engine ->
            call(activity, "refreshOfflineVoice")
            assertEquals(true, read(activity, "voiceReady"))
            assertEquals("backup", engine.voice?.name)
        }

    @Test fun allOfflineFailuresNeverSelectANetworkVoice() =
        withEngine("preferred", setOf("preferred", "backup")) { activity, engine ->
            call(activity, "refreshOfflineVoice")
            assertEquals(false, read(activity, "voiceReady"))
            assertNull(read(activity, "voiceChoice"))
            assertFalse(engine.attempts.contains("network"))
        }

    @Test fun selectingAWorkingVoiceWhileMutedDoesNotReportFailure() =
        withEngine(null, emptySet()) { activity, engine ->
            call(activity, "showOfflineVoicePicker")
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            dialog.listView.performItemClick(null, 1, 1L)
            assertEquals("preferred", engine.voice?.name)
            assertEquals(false, read(activity, "voiceEnabled"))
            assertNull(ShadowToast.getTextOfLatestToast())
        }
}
