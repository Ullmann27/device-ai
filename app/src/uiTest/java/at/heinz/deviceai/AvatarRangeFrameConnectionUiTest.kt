package at.heinz.deviceai

import android.content.Context
import android.media.AudioFormat
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import at.heinz.deviceai.avatar.AvatarDirector
import at.heinz.deviceai.avatar.AvatarEvent
import at.heinz.deviceai.avatar.AvatarSink
import at.heinz.deviceai.avatar.Viseme
import at.heinz.deviceai.cockpit.JarvisAvatarSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/** The Activity-created TTS listener must forward the actual audio marker to its existing Director.
 * Synthetic accepted speech/callbacks, host Robolectric; no audible/native-GPU/device claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
class AvatarRangeFrameConnectionUiTest {
    private fun field(target: Any, name: String): Any? = target.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(target)

    private fun set(target: Any, name: String, value: Any?) {
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
    }

    private fun drain() = shadowOf(Looper.getMainLooper()).idle()

    private fun withListener(text: String,
        check: (UtteranceProgressListener, MutableList<AvatarEvent.VisemeCue>, TtsHologramSignal) -> Unit) {
        val app = RuntimeEnvironment.getApplication()
        val scale = Settings.Global.getFloat(app.contentResolver,
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
            val engine = field(activity, "voiceEngine") as TextToSpeech
            val shadowTts = shadowOf(engine)
            shadowTts.onInitListener.onInit(TextToSpeech.SUCCESS)
            drain()
            val listener = shadowTts.utteranceProgressListener
            assertNotNull("The real Activity must install the TTS callback", listener)
            val director = field(activity, "avatarDirector") as AvatarDirector
            val originalSink = field(director, "sink") as AvatarSink
            val cues = mutableListOf<AvatarEvent.VisemeCue>()
            // Observe the real fanout without replacing its live-view delivery.
            director.attach(AvatarSink { event ->
                originalSink.onAvatarEvent(event)
                if (event is AvatarEvent.VisemeCue) cues += event
            })
            try {
                // Same accepted-request fields as speakBootstrap; no fabricated Director event.
                set(activity, "voiceUtteranceId", CURRENT)
                set(activity, "voiceAvatarText", text)
                val signal = field(activity, "voiceSignal") as TtsHologramSignal
                signal.activate(CURRENT)
                requireNotNull(listener).onBeginSynthesis(CURRENT, 8_000, AudioFormat.ENCODING_PCM_16BIT, 1)
                listener.onAudioAvailable(CURRENT, pcm16(2_000, 16_384))
                listener.onAudioAvailable(CURRENT, pcm16(2_000, 8_192))
                listener.onStart(CURRENT)
                drain()
                cues.clear()
                check(listener, cues, signal)
            } finally {
                director.attach(originalSink)
            }
        } finally {
            try {
                controller.pause().stop().destroy()
            } finally {
                JarvisAvatarSlot.register(null)
                Settings.Global.putFloat(app.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE, scale)
                app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE)
                    .edit().clear().commit()
            }
        }
    }

    private fun pcm16(frames: Int, amplitude: Int) = ByteArray(frames * 2) { index ->
        (if (index % 2 == 0) amplitude and 0xff else amplitude ushr 8).toByte()
    }

    @Test fun installedCallbackRejectsBackwardAudioMarkerAndAcceptsTheNextValidWord() =
        withListener("A O E") { listener, cues, _ ->
            listener.onRangeStart(CURRENT, 0, 1, 240)
            drain()
            assertEquals(listOf(Viseme.SIL, Viseme.AA), cues.map { it.viseme })
            val first = cues.toList()
            listener.onRangeStart(CURRENT, 2, 3, 160)
            drain()
            assertEquals("A newer text range with an older audio marker is stale", first, cues)
            listener.onRangeStart(CURRENT, 2, 3, 320)
            drain()
            assertEquals(listOf(Viseme.SIL, Viseme.O), cues.drop(first.size).map { it.viseme })
        }

    @Test fun tiedAudioMarkersKeepNewTextButDoNotReplayTheSameOrEarlierWord() =
        withListener("A O") { listener, cues, _ ->
            listener.onRangeStart(CURRENT, 0, 1, 0)
            listener.onRangeStart(CURRENT, 2, 3, 0)
            drain()
            assertEquals(listOf(Viseme.SIL, Viseme.AA, Viseme.SIL, Viseme.O),
                cues.map { it.viseme })
            val accepted = cues.toList()
            listener.onRangeStart(CURRENT, 2, 3, 0)
            listener.onRangeStart(CURRENT, 0, 1, 0)
            drain()
            assertEquals("Replay must not replace a newer valid word", accepted, cues)
        }

    @Test fun invalidAudioMarkerCannotStartAVisibleWordThroughTheActualCallback() =
        withListener("A") { listener, cues, _ ->
            listener.onRangeStart(CURRENT, 0, 1, -1)
            drain()
            assertEquals(emptyList<AvatarEvent.VisemeCue>(), cues)
            listener.onRangeStart(CURRENT, 0, 1, 0)
            drain()
            assertEquals(listOf(Viseme.SIL, Viseme.AA), cues.map { it.viseme })
        }

    @Test fun aRejectedRangeCannotRewindTheMeasuredPcmPlaybackPosition() =
        withListener("A O") { listener, _, signal ->
            listener.onRangeStart(CURRENT, 0, 1, 2_400)
            drain()
            val accepted = signal.level(SystemClock.elapsedRealtime())
            assertEquals(.25f, accepted, .001f)
            listener.onRangeStart(CURRENT, 2, 3, 1_600)
            drain()
            assertEquals("Rejected range must preserve the actual audio envelope position",
                accepted, signal.level(SystemClock.elapsedRealtime()), .000001f)
        }

    @Test fun aDuplicateStartCannotRewindTheAcceptedPcmPosition() =
        withListener("A O") { listener, _, signal ->
            listener.onRangeStart(CURRENT, 0, 1, 2_400)
            drain()
            val accepted = signal.level(SystemClock.elapsedRealtime())
            assertEquals(.25f, accepted, .001f)
            listener.onStart(CURRENT)
            drain()
            assertEquals("Rejected duplicate start must not anchor playback at frame zero again",
                accepted, signal.level(SystemClock.elapsedRealtime()), .000001f)
        }

    @Test fun orderedPunctuationAnchorsPcmWithoutInventingVisemesOrAdmittingOlderFrames() =
        withListener("A ! O") { listener, cues, signal ->
            listener.onRangeStart(CURRENT, 0, 1, 0)
            drain()
            val first = cues.toList()
            listener.onRangeStart(CURRENT, 2, 3, 2_400)
            drain()
            assertEquals(first, cues)
            assertEquals("A valid punctuation range still carries an audio position",
                .25f, signal.level(SystemClock.elapsedRealtime()), .001f)
            listener.onRangeStart(CURRENT, 4, 5, 1_600)
            drain()
            assertEquals("Punctuation must advance the ordering watermark", first, cues)
            assertEquals(.25f, signal.level(SystemClock.elapsedRealtime()), .001f)
            listener.onRangeStart(CURRENT, 4, 5, 3_200)
            drain()
            assertEquals(listOf(Viseme.SIL, Viseme.O), cues.drop(first.size).map { it.viseme })
        }

    private companion object { const val CURRENT = "range-frame-current" }
}
