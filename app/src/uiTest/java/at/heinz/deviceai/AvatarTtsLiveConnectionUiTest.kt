package at.heinz.deviceai

import android.content.Context
import android.media.AudioFormat
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import at.heinz.deviceai.avatar.AvatarDirector
import at.heinz.deviceai.avatar.AvatarPose
import at.heinz.deviceai.avatar.AvatarPoseReducer
import at.heinz.deviceai.avatar.AvatarState
import at.heinz.deviceai.avatar.JarvisHoloAvatar
import at.heinz.deviceai.avatar.Viseme
import at.heinz.deviceai.cockpit.JarvisAvatarSlot
import at.heinz.deviceai.ui.ContinuumHomeView
import at.heinz.deviceai.ui.HollywoodFuiConstruct
import at.heinz.deviceai.ui.JarvisWorkWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
import org.robolectric.shadows.ShadowSystem
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/**
 * Real Activity-installed TTS listener, PCM envelope and scheduled voiceFrame -> live View reducer.
 * Callback IDs/PCM are synthetic. LEGACY host graphics proves neither audible output nor GPU pixels.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi", instrumentedPackages = [
    "at.heinz.deviceai.avatar.AvatarDirector",
    "at.heinz.deviceai.ContinuumHologramView"
])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
class AvatarTtsLiveConnectionUiTest {
    private var framePump: CockpitHostFramePump? = null
    @Before fun pauseVsync() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }
    private data class SpeechFixture(
        val activity: AgentActivity,
        val home: ContinuumHomeView,
        val live: ContinuumHologramView,
        val listener: UtteranceProgressListener,
        val pause: () -> Unit
    )

    private fun field(target: Any, name: String): Any? = target.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(target)

    private fun set(target: Any, name: String, value: Any?) {
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
    }

    private fun invoke(activity: AgentActivity, name: String) {
        AgentActivity::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(activity)
    }

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) {
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
    } else emptyList()

    private fun advance(millis: Long) {
        var remaining = millis
        while (remaining > 0L) {
            val step = minOf(16L, remaining)
            shadowOf(Looper.getMainLooper()).idleFor(step, TimeUnit.MILLISECONDS)
            framePump?.drawRequestedFrame()
            remaining -= step
        }
    }

    private fun measure(root: View) {
        root.measure(View.MeasureSpec.makeMeasureSpec(884, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(760, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 884, 760)
        advance(40)
    }

    private fun withSpeechShell(check: (SpeechFixture) -> Unit) {
        val app = RuntimeEnvironment.getApplication()
        val animatorScale = Settings.Global.getFloat(app.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        // Match the existing functional shell fixture; no native frame or pixel assertion is made.
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
        var paused = false
        try {
            controller.setup().visible()
            advance(32L)
            controller.windowFocusChanged(true)
            val activity = controller.get()
            framePump = CockpitHostFramePump { controller.get().window.decorView }
            measure(activity.window.decorView)
            measure(activity.window.decorView)
            val home = field(activity, "homeView") as ContinuumHomeView
            val live = field(activity, "hologramView") as ContinuumHologramView
            assertSame(live, (home.avatarRenderer as JarvisHoloAvatar).hologram)
            assertTrue(live.isShown)
            assertEquals(1, descendants(activity.window.decorView).count { it is ContinuumHologramView })

            // PAUSED idleFor advances Android's virtual time, not the host JVM wall clock.
            // Instrument the existing Director (including its default clock function) and View
            // so their System.currentTimeMillis calls share Robolectric's render/event clock.
            // @RunWith test classes are not instrumented, so pose() reads ShadowSystem explicitly.
            val director = field(activity, "avatarDirector") as AvatarDirector
            @Suppress("UNCHECKED_CAST")
            val eventClock = field(director, "clock") as () -> Long
            val beforeAdvance = ShadowSystem.currentTimeMillis()
            assertEquals("The existing Director and render fixture must use the same controlled clock",
                beforeAdvance, eventClock())
            advance(33)
            assertEquals("idleFor must advance the render fixture clock deterministically",
                beforeAdvance + 33, ShadowSystem.currentTimeMillis())
            assertEquals("The existing Director clock must follow the same idleFor advance",
                ShadowSystem.currentTimeMillis(), eventClock())

            val engine = field(activity, "voiceEngine") as TextToSpeech
            val shadowTts = shadowOf(engine)
            // Robolectric 4.14.1 exposes both getters. Deliver the constructor's real init callback
            // so onLocalVoiceInitialized installs the actual Activity-owned progress listener.
            shadowTts.onInitListener.onInit(TextToSpeech.SUCCESS)
            shadowOf(Looper.getMainLooper()).idle()
            val listener = shadowTts.utteranceProgressListener
            assertNotNull("The Activity's real TTS engine must hold its progress listener", listener)
            check(SpeechFixture(activity, home, live, requireNotNull(listener)) {
                controller.pause()
                paused = true
            })
        } finally {
            framePump?.close()
            framePump = null
            try {
                if (!paused) controller.pause()
                controller.stop()
                controller.destroy()
            } finally {
                JarvisAvatarSlot.register(null)
                Settings.Global.putFloat(app.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE, animatorScale)
                app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE)
                    .edit().clear().commit()
            }
        }
    }

    private fun pose(fixture: SpeechFixture): AvatarPose =
        (field(fixture.live, "avatarPose") as AvatarPoseReducer).pose(ShadowSystem.currentTimeMillis())

    private fun pcm16(frames: Int = 8_000): ByteArray = ByteArray(frames * 2) { index ->
        // Synthetic +0.5 full-scale PCM16, mono, one second at 8 kHz.
        if (index % 2 == 0) 0.toByte() else 0x40.toByte()
    }

    private fun startPendingSpeech(fixture: SpeechFixture, id: String, text: String,
        reportRange: Boolean = true, rangeEnd: Int = text.length, chunks: List<ByteArray> = listOf(pcm16())) {
        // Only the already-accepted synthesis-request context is fixture state. Calling the
        // shadow's speak() would automatically post onDone before controlled PCM/range callbacks.
        // These are the three assignments made by speakBootstrap; no Director/View event is faked.
        set(fixture.activity, "voiceUtteranceId", id)
        set(fixture.activity, "voiceAvatarText", text)
        (field(fixture.activity, "voiceSignal") as TtsHologramSignal).activate(id)
        fixture.listener.onBeginSynthesis(id, 8_000, AudioFormat.ENCODING_PCM_16BIT, 1)
        chunks.forEach { fixture.listener.onAudioAvailable(id, it) }
        fixture.listener.onStart(id)
        if (reportRange) fixture.listener.onRangeStart(id, 0, rangeEnd, 0)
        // The real voiceFrame schedules itself every 33 ms. A one-letter word starts with SIL
        // and reaches its non-silent viseme after 160 ms, using the production word-range mapper.
        advance(170)
    }

    private fun assertSilent(fixture: SpeechFixture) {
        val value = pose(fixture)
        assertEquals(0f, value.jawOpen, 0f)
        assertEquals(0f, value.speakEnergy, 0f)
        assertEquals(Viseme.SIL, value.viseme)
        assertFalse(field(fixture.activity, "voiceSpeaking") as Boolean)
        assertNull(field(fixture.activity, "voiceUtteranceId"))
    }

    @Test fun installedListenerAndScheduledPcmFramesDriveTheVisibleMouthAndFinish() = withSpeechShell { f ->
        startPendingSpeech(f, "current", "A", reportRange = false)

        val speaking = pose(f)
        assertEquals(AvatarState.SPEAKING, speaking.state)
        assertEquals("No word-range fallback is involved in the PCM assertion", Viseme.SIL, speaking.viseme)
        assertTrue(speaking.jawOpen > 0f)
        // The word-range fallback is at most .28. This assertion requires real PCM to have
        // crossed voiceFrame -> Director -> the live reducer, not merely the range approximation.
        assertTrue("PCM must reach the visible reducer through the Activity's scheduled voiceFrame",
            speaking.speakEnergy > .35f)
        assertSame(f.live, (f.home.avatarRenderer as JarvisHoloAvatar).hologram)

        f.listener.onRangeStart("current", 0, 1, 0)
        advance(170)
        assertEquals("The actual range callback must also reach the same live reducer", Viseme.AA, pose(f).viseme)

        f.listener.onDone("current")
        shadowOf(Looper.getMainLooper()).idle()
        assertSilent(f)
    }

    @Test fun measuredSilenceClosesTheVisibleMouthBeforeTheUtteranceEnds() {
        for (chunks in listOf(listOf(pcm16(2_000), ByteArray(1_600)), listOf(pcm16(2_000)))) {
            withSpeechShell { f ->
                startPendingSpeech(f, "silence", "A", chunks = chunks)
                assertEquals(Viseme.AA, pose(f).viseme)
                assertTrue(pose(f).jawOpen > 0f)
                advance(110)
                assertEquals(0f, pose(f).jawOpen, 0f)
                assertEquals(0f, pose(f).speakEnergy, 0f)
                assertEquals(Viseme.SIL, pose(f).viseme)
                assertTrue(field(f.activity, "voiceSpeaking") as Boolean)
                assertEquals("silence", field(f.activity, "voiceUtteranceId"))
            }
        }
    }

    @Test fun aTtsEngineWithoutPcmRetainsWordRangeMotion() = withSpeechShell { f ->
        startPendingSpeech(f, "no-pcm", "A", chunks = emptyList())
        assertEquals(Viseme.AA, pose(f).viseme)
        assertTrue(pose(f).speakEnergy > 0f)
        assertTrue(pose(f).speakEnergy <= .28f)
        assertTrue(pose(f).jawOpen > 0f)
    }

    @Test fun previousAndFinishedUtteranceCallbacksCannotMutateTheCurrentSurface() = withSpeechShell { f ->
        startPendingSpeech(f, "previous", "A")
        f.listener.onDone("previous")
        shadowOf(Looper.getMainLooper()).idle()
        startPendingSpeech(f, "current", "O")
        val before = pose(f)
        assertEquals(Viseme.O, before.viseme)

        f.listener.onStart("previous")
        f.listener.onBeginSynthesis("previous", 8_000, AudioFormat.ENCODING_PCM_16BIT, 1)
        f.listener.onAudioAvailable("previous", ByteArray(16_000))
        f.listener.onRangeStart("previous", 0, 1, 0)
        f.listener.onDone("previous")
        f.listener.onStop("previous", true)
        f.listener.onError("previous", TextToSpeech.ERROR)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Foreign callbacks must not rewrite the active pose", before, pose(f))
        assertEquals("current", field(f.activity, "voiceUtteranceId"))
        assertTrue(field(f.activity, "voiceSpeaking") as Boolean)

        f.listener.onDone("current")
        shadowOf(Looper.getMainLooper()).idle()
        f.listener.onStart("current")
        f.listener.onAudioAvailable("current", pcm16())
        f.listener.onRangeStart("current", 0, 1, 0)
        advance(70)
        assertSilent(f)
    }

    @Test fun chatReparentKeepsTheSameSpeechSurfaceAndPauseRejectsLateCallbacks() = withSpeechShell { f ->
        startPendingSpeech(f, "moving", "A O", rangeEnd = 1)
        assertTrue(pose(f).speakEnergy > .35f)

        set(f.activity, "conversationActive", true)
        invoke(f.activity, "refreshConversationLayout")
        measure(f.activity.window.decorView)
        assertSame(field(f.activity, "chatColumn"), f.live.parent)
        assertSame(f.live, field(f.activity, "hologramView"))
        assertTrue(f.live.isShown)
        assertEquals(1, descendants(f.activity.window.decorView).count { it is ContinuumHologramView })
        // A genuinely later word must reach the reattached live view in Chat. Replaying
        // the original range cannot extend its lifetime; a separate regression covers that.
        f.listener.onRangeStart("moving", 2, 3, 3_200)
        advance(170)
        assertEquals(Viseme.O, pose(f).viseme)
        assertTrue(pose(f).speakEnergy > .35f)

        f.pause()
        assertSilent(f)
        f.listener.onStart("moving")
        f.listener.onBeginSynthesis("moving", 8_000, AudioFormat.ENCODING_PCM_16BIT, 1)
        f.listener.onAudioAvailable("moving", pcm16())
        f.listener.onRangeStart("moving", 0, 1, 0)
        (field(f.activity, "voiceFrame") as Runnable).run()
        advance(330)
        assertSilent(f)
        assertEquals(0f, (field(f.activity, "voiceSignal") as TtsHologramSignal)
            .level(SystemClock.elapsedRealtime()), 0f)
    }

    @Test fun appLookupCancellationStopsSpeechBeforeReturning() = withSpeechShell { f ->
        startPendingSpeech(f, "cancel-lookup", "A")
        assertTrue("Real listener drives speech", field(f.activity, "voiceSpeaking") as Boolean)
        assertTrue("Scheduled PCM opens the mouth", pose(f).jawOpen > 0f)
        val generation = field(f.activity, "appLookupGeneration") as Long
        set(f.activity, "appLookupActive", true)
        set(f.activity, "activeLocalRequest", true)
        set(f.activity, "busy", true)

        invoke(f.activity, "cancelLocalRequest")
        assertFalse("Early lookup return must silence TTS immediately", field(f.activity, "voiceSpeaking") as Boolean)
        assertNull(field(f.activity, "voiceUtteranceId"))
        assertFalse(field(f.activity, "appLookupActive") as Boolean)
        assertFalse(field(f.activity, "activeLocalRequest") as Boolean)
        assertEquals(generation + 1L, field(f.activity, "appLookupGeneration"))
        shadowOf(Looper.getMainLooper()).idle()
        assertSilent(f)
        assertEquals(0f, (field(f.activity, "voiceSignal") as TtsHologramSignal)
            .level(SystemClock.elapsedRealtime()), 0f)

        f.listener.onStart("cancel-lookup")
        f.listener.onAudioAvailable("cancel-lookup", pcm16())
        f.listener.onRangeStart("cancel-lookup", 0, 1, 0)
        (field(f.activity, "voiceFrame") as Runnable).run()
        advance(170L)
        assertSilent(f)
        assertSame(f.live, field(f.activity, "hologramView"))
    }

    @Test fun lookupCancellationClosesWorkWindowAndLateStatusCannotReopenIt() = withSpeechShell { f ->
        val root = f.activity.window.decorView
        val io = field(f.activity, "learningIoExecutor") as ExecutorService
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val held = io.submit(Runnable {
            entered.countDown()
            check(release.await(10L, TimeUnit.SECONDS)) { "Test status gate timed out" }
        })
        try {
            assertTrue("Hold the actual status worker", entered.await(5L, TimeUnit.SECONDS))
            f.home.setForegroundActive(true)
            assertEquals(ContinuumWorkArea.START, field(f.activity, "activeWorkArea"))
            assertNotNull("Real initial draw creates shell geometry", f.home.jarvisShell.shell)
            invoke(f.activity, "showSystemCenter")
            measure(root)
            val work = descendants(root).singleOrNull { it.tag == "jarvis-work-window" && it.isShown }
            assertNotNull("Systemstatus is a visible cockpit work area", work)
            val labels = descendants(requireNotNull(work)).filterIsInstance<TextView>().map { it.text.toString() }
            assertTrue("Real status is still pending", labels.any { it.contains("Status wird gelesen") })
            startPendingSpeech(f, "cancel-window", "A")
            assertTrue(pose(f).jawOpen > 0f)
            set(f.activity, "appLookupActive", true)
            set(f.activity, "activeLocalRequest", true)
            set(f.activity, "busy", true)
            val lookupGeneration = field(f.activity, "appLookupGeneration") as Long
            val presentationGeneration = field(f.activity, "workPresentationGeneration") as Long

            invoke(f.activity, "cancelLocalRequest")
            assertEquals("Cancel invalidates pending lookup immediately", lookupGeneration + 1L,
                field(f.activity, "appLookupGeneration"))
            assertEquals("Cancel invalidates pending presentation immediately", presentationGeneration + 1L,
                field(f.activity, "workPresentationGeneration"))
            assertFalse(field(f.activity, "appLookupActive") as Boolean)
            assertFalse(field(f.activity, "activeLocalRequest") as Boolean)
            assertFalse(field(f.activity, "busy") as Boolean)
            assertNull(field(f.activity, "currentTaskEffectContext"))
            assertNull(field(field(f.activity, "workWindowHost")!!, "workingId"))
            val window = work as JarvisWorkWindow
            if (work.isShown) assertEquals("Only the cancelled return remains",
                at.heinz.deviceai.cockpit.WindowProjectedMotion.Phase.CLOSING, window.projectedMotionPhase())
            assertFalse("Cancel silences before the lookup return", field(f.activity, "voiceSpeaking") as Boolean)
            shadowOf(Looper.getMainLooper()).idle()
            assertSilent(f)
            assertSame(f.live, field(f.activity, "hologramView"))

            release.countDown()
            held.get(5L, TimeUnit.SECONDS)
            io.submit(Runnable {}).get(5L, TimeUnit.SECONDS)
            advance(80L)
            val visible = descendants(root).filter { it.tag == "jarvis-work-window" && it.isShown }
            assertTrue("Late status creates no replacement window", visible.all { it === work })
            if (work.isShown) assertEquals("Late status cannot restart the cancelled window",
                at.heinz.deviceai.cockpit.WindowProjectedMotion.Phase.CLOSING, window.projectedMotionPhase())
            assertFalse("Late status cannot reactivate work", field(f.activity, "activeLocalRequest") as Boolean)
            advance(HollywoodFuiConstruct.CLOSE_END_MS + 32L)
            assertFalse("Return removes the cancelled window", work.isShown)
            assertTrue("No work window survives its return",
                descendants(root).none { it.tag == "jarvis-work-window" && it.isShown })
            f.listener.onStart("cancel-window")
            f.listener.onRangeStart("cancel-window", 0, 1, 0)
            f.listener.onAudioAvailable("cancel-window", pcm16())
            advance(170L)
            assertSilent(f)
            assertTrue("Late speech cannot reopen cancelled work",
                descendants(root).none { it.tag == "jarvis-work-window" && it.isShown })
            assertSame(f.live, field(f.activity, "hologramView"))
            assertTrue(f.live.isShown)
        } finally {
            release.countDown()
            held.get(5L, TimeUnit.SECONDS)
        }
    }

}
