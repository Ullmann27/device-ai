package at.heinz.deviceai

import android.content.Context
import android.media.AudioFormat
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import at.heinz.deviceai.avatar.AvatarPoseReducer
import at.heinz.deviceai.avatar.Viseme
import at.heinz.deviceai.cockpit.JarvisAvatarSlot
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSystem
import java.util.concurrent.TimeUnit

/** Synthetic callbacks through the actual Activity listener and live reducer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi", instrumentedPackages = [
    "at.heinz.deviceai.avatar.AvatarDirector",
    "at.heinz.deviceai.ContinuumHologramView"
])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
class AvatarStopNextTurnUiTest {
    private fun field(target: Any, name: String): Any? = target.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(target)

    private fun set(target: Any, name: String, value: Any?) {
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
    }

    private fun advance(millis: Long) =
        shadowOf(Looper.getMainLooper()).idleFor(millis, TimeUnit.MILLISECONDS)

    private fun checkStopAndNextTurn(interrupted: Boolean) {
        val app = RuntimeEnvironment.getApplication()
        val originalScale = Settings.Global.getFloat(app.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit()
            .putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit()
            .clear().putBoolean("jarvis-shell", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        try {
            controller.setup().visible().windowFocusChanged(true)
            val activity = controller.get()
            val root = activity.window.decorView
            root.measure(android.view.View.MeasureSpec.makeMeasureSpec(884,
                android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(760,
                    android.view.View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 884, 760)
            advance(40)
            val shadowTts = shadowOf(field(activity, "voiceEngine") as TextToSpeech)
            shadowTts.onInitListener.onInit(TextToSpeech.SUCCESS)
            shadowOf(Looper.getMainLooper()).idle()
            val listener = requireNotNull(shadowTts.utteranceProgressListener)
            val signal = field(activity, "voiceSignal") as TtsHologramSignal
            val live = field(activity, "hologramView") as ContinuumHologramView
            val reducer = field(live, "avatarPose") as AvatarPoseReducer
            fun pose() = reducer.pose(ShadowSystem.currentTimeMillis())
            fun start(id: String, word: String) {
                set(activity, "voiceUtteranceId", id)
                set(activity, "voiceAvatarText", word)
                signal.activate(id)
                listener.onBeginSynthesis(id, 8_000, AudioFormat.ENCODING_PCM_16BIT, 1)
                listener.onAudioAvailable(id, ByteArray(16_000) {
                    if (it % 2 == 0) 0.toByte() else 0x40.toByte()
                })
                listener.onStart(id)
                listener.onRangeStart(id, 0, word.length, 0)
                advance(170)
            }

            start("first", "A")
            assertTrue(pose().speakEnergy > .35f)
            listener.onStop("first", interrupted)
            shadowOf(Looper.getMainLooper()).idle()
            assertNull(field(activity, "voiceUtteranceId"))
            assertEquals(0f, pose().jawOpen, 0f)
            assertEquals(Viseme.SIL, pose().viseme)

            start("second", "O")
            assertEquals("second", field(activity, "voiceUtteranceId"))
            assertEquals(Viseme.O, pose().viseme)
            assertTrue(pose().speakEnergy > .35f)

            listener.onStart("first")
            listener.onBeginSynthesis("first", 8_000, AudioFormat.ENCODING_PCM_16BIT, 1)
            listener.onAudioAvailable("first", ByteArray(16_000))
            listener.onRangeStart("first", 0, 1, 0)
            listener.onStop("first", !interrupted)
            listener.onDone("first")
            listener.onError("first", TextToSpeech.ERROR)
            shadowOf(Looper.getMainLooper()).idle()
            advance(33)
            assertEquals("An old callback must not end the new voice", "second",
                field(activity, "voiceUtteranceId"))
            assertTrue(field(activity, "voiceSpeaking") as Boolean)
            assertTrue(pose().speakEnergy > .35f)
            assertEquals(Viseme.O, pose().viseme)

            listener.onStop("second", true)
            shadowOf(Looper.getMainLooper()).idle()
            assertNull(field(activity, "voiceUtteranceId"))
            assertEquals(0f, pose().speakEnergy, 0f)
            assertEquals(0f, pose().jawOpen, 0f)
            assertEquals(Viseme.SIL, pose().viseme)
        } finally {
            try {
                controller.pause().stop().destroy()
            } finally {
                JarvisAvatarSlot.register(null)
                Settings.Global.putFloat(app.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE, originalScale)
                app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE)
                    .edit().clear().commit()
            }
        }
    }

    @Test fun interruptedCurrentSpeechCannotDamageTheNextVoiceTurn() =
        checkStopAndNextTurn(true)

    @Test fun engineStopWithoutInterruptedFlagStillRetiresOnlyThatVoiceTurn() =
        checkStopAndNextTurn(false)
}
