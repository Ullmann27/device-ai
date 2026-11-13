package at.heinz.deviceai

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.ColorSpace
import android.graphics.Rect
import android.hardware.HardwareBuffer
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.TextView
import at.heinz.deviceai.avatar.AvatarDirector
import at.heinz.deviceai.cockpit.CockpitAnimationBus
import at.heinz.deviceai.cognitive.ComputerApp
import at.heinz.deviceai.cognitive.ComputerGoal
import at.heinz.deviceai.cognitive.ContinuousConversationMode
import at.heinz.deviceai.cognitive.LiveMonitorFeed
import at.heinz.deviceai.cognitive.LiveMonitorState
import at.heinz.deviceai.cognitive.LocalDialogueRetention
import at.heinz.deviceai.cognitive.OwnerComputerIntent
import at.heinz.deviceai.cognitive.OwnerComputerTaskGrant
import at.heinz.deviceai.cognitive.OwnerComputerWorkflow
import at.heinz.deviceai.ui.ComputerPanelState
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.time.Duration
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w884dp-h760dp-land-mdpi", shadows = [
    AndroidWindowCaptureTest.CaptureServiceShadow::class,
    AndroidWindowCaptureTest.InfoShadow::class,
    AndroidLiveNodePortTest.NodeShadow::class,
    AndroidLiveNodePortTest.WindowShadow::class
])
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class OwnerComputerVisibilityUiTest {
    private lateinit var service: ContinuumScreenObservationService
    private val intent = OwnerComputerIntent(ComputerApp.WHATSAPP, ComputerGoal.OPEN)
    private val pendingType = AgentActivity::class.java.declaredClasses.single {
        it.simpleName == "PendingComputerLaunch"
    }

    @Before fun configure() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        LocalDialogueRetention.context.forgetForExit()
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit()
            .putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-internet", Context.MODE_PRIVATE).edit()
            .putString("mode", "OFF").commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        service = Robolectric.buildService(ContinuumScreenObservationService::class.java).get()
        AndroidLiveNodePortTest.ServiceShadow.ownPackage = service.packageName
        AndroidLiveNodePortTest.ServiceShadow.overlay = false
        AndroidLiveNodePortTest.ServiceShadow.resetGeometry()
        AndroidLiveNodePortTest.ServiceShadow.target = AccessibilityNodeInfo.obtain().apply {
            packageName = "com.whatsapp"; isVisibleToUser = true; isEnabled = true
            setBoundsInScreen(Rect(620, 60, 1180, 900))
        }
        ScreenObservationBus.connect(service)
    }

    @After fun cleanup() {
        AndroidLiveComputer.stop()
        ScreenObservationBus.disconnect(service)
        AndroidLiveNodePortTest.ServiceShadow.target = null
        AndroidLiveNodePortTest.ServiceShadow.resetGeometry()
        AndroidLiveNodePortTest.NodeShadow.onAction = null
        AndroidLiveNodePortTest.NodeShadow.onRefresh = null
        AndroidWindowCaptureTest.CaptureServiceShadow.callback = null
    }

    private fun field(target: Any, name: String): Any? = target.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(target)

    private fun set(target: Any, name: String, value: Any?) = target.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.set(target, value)

    private fun call(a: AgentActivity, name: String, vararg args: Pair<Class<*>, Any?>): Any? =
        AgentActivity::class.java.getDeclaredMethod(name, *args.map { it.first }.toTypedArray())
            .apply { isAccessible = true }.invoke(a, *args.map { it.second }.toTypedArray())

    private fun texts(view: View): List<String> = when (view) {
        is TextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun chat(a: AgentActivity) = texts(field(a, "chatContainer") as View)
    private fun notices(a: AgentActivity) = chat(a).filter { it.contains("weil Continuum nicht mehr sichtbar ist") }
    private fun advance(millis: Long = 16L) = shadowOf(Looper.getMainLooper()).idleFor(millis, TimeUnit.MILLISECONDS)

    private data class Fixture(
        val lifecycle: ActivityController<AgentActivity>,
        val activity: AgentActivity,
        val computer: OwnerComputerSessionController,
        val work: ArrayDeque<Runnable>,
        val pending: Any,
        val tts: TextToSpeech,
        val feeds: MutableList<LiveMonitorFeed<AndroidLiveFrame>?>,
        val panels: MutableList<ComputerPanelState?>
    )

    private fun start(f: Fixture) = call(f.activity, "startComputerSession", pendingType to f.pending)

    private fun withFixture(startTask: Boolean = true, body: (Fixture) -> Unit) {
        val lifecycle = Robolectric.buildActivity(AgentActivity::class.java).setup().visible()
        val a = lifecycle.get()
        try {
            advance(32)
            val tts = field(a, "voiceEngine") as TextToSpeech
            tts.setVoice(Voice("de-de-local", Locale.GERMANY, Voice.QUALITY_HIGH, Voice.LATENCY_NORMAL, false, emptySet()))
            set(a, "voiceEnabled", true)
            set(a, "voiceReady", true)
            val work = ArrayDeque<Runnable>()
            val feeds = mutableListOf<LiveMonitorFeed<AndroidLiveFrame>?>()
            val panels = mutableListOf<ComputerPanelState?>()
            val computer = OwnerComputerSessionController(a,
                field(a, "cockpitBus") as CockpitAnimationBus,
                field(a, "avatarDirector") as AvatarDirector,
                Executor { work.addLast(it) },
                setFeed = { feeds.add(it) },
                setBusy = { busy ->
                    set(a, "activeLocalRequest", busy)
                    call(a, "setBusy", Boolean::class.javaPrimitiveType!! to busy)
                },
                visible = { field(a, "foregroundLearningActive") == true ||
                    (field(a, "computerViewVisible") == true && a.isInMultiWindowMode) },
                playClick = {}, stopClick = {}, setPanels = { panels.add(it) })
            set(a, "computerController", computer)
            val pending = pendingType.declaredConstructors.single().apply { isAccessible = true }
                .newInstance(field(a, "sessionId"), field(a, "appLookupGeneration"), "com.whatsapp",
                    intent, SystemClock.elapsedRealtime(), "Öffne WhatsApp.", true, field(a, "ownerReplyRevision"))
            val f = Fixture(lifecycle, a, computer, work, pending, tts, feeds, panels)
            if (startTask) {
                start(f)
                assertTrue(computer.active)
                assertEquals(1, work.size)
            }
            body(f)
        } finally {
            (field(a, "computerController") as? OwnerComputerSessionController)?.close()
            runCatching { lifecycle.close() }
        }
    }

    @Test fun fullScreenPauseReportsOnceAndRevokesBeforeLateWork() = withFixture { f ->
        val permission = field(f.computer, "grant") as OwnerComputerTaskGrant
        val runner = field(f.computer, "workflow")!!
        val mode = field(f.activity, "continuousConversation") as ContinuousConversationMode
        val session = field(f.activity, "sessionId") as String
        assertTrue(mode.start(session))
        assertEquals(ContinuousConversationMode.Decision.SEND,
            mode.recognized(mode.turn, session, "Öffne WhatsApp."))
        call(f.activity, "scheduleConversationIdleCheck")
        val spoken = shadowOf(f.tts).spokenTextList.toList()
        assertEquals(true, field(f.activity, "foregroundLearningActive"))

        f.lifecycle.pause().stop()
        advance(1_000)

        assertFalse(f.computer.active)
        assertTrue(runCatching { permission.checkActive() }.isFailure)
        assertTrue((field(runner, "cancelled") as AtomicBoolean).get())
        assertNull(field(f.computer, "captureSession"))
        assertEquals(LiveMonitorState.ENDED, AndroidLiveComputer.feed.status(System.nanoTime()).state)
        assertNull(f.feeds.last())
        assertNull(f.panels.last())
        assertEquals(false, field(f.activity, "busy"))
        val message = notices(f.activity).single()
        assertTrue(message.contains("gestoppt"))
        assertTrue(message.contains("geteilten Bildschirm"))
        assertTrue(message.contains("starte den Auftrag erneut"))
        assertFalse(message.contains("pausiert"))
        assertEquals(spoken, shadowOf(f.tts).spokenTextList)
        assertNull(field(f.activity, "speechTurnId"))
        assertNull(field(f.activity, "mayaSpeech"))
        assertEquals(false, field(f.activity, "voiceListening"))
        assertNull(field(f.activity, "activeLocalSpeechRequest"))
        assertEquals(ContinuousConversationMode.State.PAUSED, mode.state)
        assertEquals(false, field(f.activity, "activeLocalRequest"))
        assertNull(field(f.activity, "activeLocalStreamToken"))
        assertEquals(1, f.work.size)

        val beforeLate = chat(f.activity)
        f.work.removeFirst().run()
        advance()
        assertEquals(beforeLate, chat(f.activity))
        assertEquals(spoken, shadowOf(f.tts).spokenTextList)
    }

    @Test fun queuedCompletionCannotReplaceTheVisibilityReason() = withFixture { f ->
        (field(f.computer, "grant") as OwnerComputerTaskGrant).revoke()
        f.work.removeFirst().run()
        assertTrue(f.computer.active)
        assertTrue(notices(f.activity).isEmpty())
        f.lifecycle.pause().stop()
        val afterStop = chat(f.activity)
        advance()
        assertEquals(afterStop, chat(f.activity))
        assertEquals(1, notices(f.activity).size)
    }

    @Test fun ordinaryPauseWithoutAnActiveTaskAddsNothing() = withFixture(startTask = false) { f ->
        val before = chat(f.activity)
        f.lifecycle.pause().stop()
        advance()
        assertEquals(before, chat(f.activity))
        assertTrue(notices(f.activity).isEmpty())
    }

    @Test fun splitScreenPauseKeepsWorkButStopEndsItOnce() = withFixture { f ->
        shadowOf(f.activity).setInMultiWindowMode(true)
        f.lifecycle.pause()
        assertTrue(f.computer.active)
        assertTrue(notices(f.activity).isEmpty())
        f.lifecycle.stop()
        assertFalse(f.computer.active)
        assertEquals(1, notices(f.activity).size)
    }

    @Test fun pendingAndDeferredAndroidConsentAreNotVisibilityStops() = withFixture(startTask = false) { f ->
        shadowOf(f.activity).setInMultiWindowMode(true)
        start(f)
        assertTrue(f.computer.awaitingConsent)
        assertTrue(f.work.isEmpty())
        val request = shadowOf(f.activity).nextStartedActivityForResult
        assertNotNull(request)
        f.lifecycle.pause().stop()
        assertTrue(f.computer.active)
        assertTrue(notices(f.activity).isEmpty())
        f.computer.projectionResult(request!!.requestCode, Activity.RESULT_OK, Intent())
        assertTrue(f.computer.awaitingConsent)
        f.computer.stopForVisibilityLoss()
        assertTrue(f.computer.active)
        assertTrue(notices(f.activity).isEmpty())
        assertTrue(f.work.isEmpty())
    }

    private fun ownerCancellation(request: String, expected: String) = withFixture { f ->
        (field(f.activity, "input") as EditText).setText(request)
        call(f.activity, "sendCurrentInput", Boolean::class.javaPrimitiveType!! to false)
        assertFalse(f.computer.active)
        assertTrue(chat(f.activity).contains(expected))
        assertTrue(notices(f.activity).isEmpty())
        f.lifecycle.pause().stop()
        f.work.removeFirst().run()
        advance()
        assertTrue(notices(f.activity).isEmpty())
        assertEquals(1, chat(f.activity).count { it == expected })
    }

    @Test fun ownerStopKeepsItsExistingReply() = ownerCancellation(
        "Stopp", "Computer-Auftrag und Live-Bild gestoppt.")

    @Test fun ownerCorrectionKeepsItsExistingReply() = ownerCancellation(
        "Stopp, das meine ich nicht.", "Verstanden. Was soll ich anders machen?")

    @Test fun completedWorkIsNotCancelledWhenTheRetainedCaptureCloses() = withFixture { f ->
        advance()
        val capture = AndroidWindowCaptureTest.CaptureServiceShadow.callback
        assertNotNull(capture)
        val buffer = HardwareBuffer.create(8, 8, HardwareBuffer.RGBA_8888, 1,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE)
        val screenshot = ReflectionHelpers.callConstructor(AccessibilityService.ScreenshotResult::class.java,
            ClassParameter.from(HardwareBuffer::class.java, buffer),
            ClassParameter.from(ColorSpace::class.java, ColorSpace.get(ColorSpace.Named.SRGB)),
            ClassParameter.from(Long::class.javaPrimitiveType, SystemClock.uptimeMillis()))
        capture!!.onSuccess(screenshot)
        val feed = AndroidLiveComputer.feed
        val now = System.nanoTime()
        val status = feed.status(now)
        assertEquals(LiveMonitorState.LIVE, status.state)
        val imported = requireNotNull(feed.monitorSubmission.imported(status, now))
        assertTrue(feed.monitorSubmission.submitted(imported, status, now))

        f.work.removeFirst().run()
        advance()

        val message = "WhatsApp ist im gebundenen App-Fenster sichtbar."
        assertEquals(1, chat(f.activity).count { it == message })
        assertTrue(f.computer.active)
        assertNotNull(field(f.computer, "plan"))
        assertNull(field(f.computer, "workflow"))
        assertNotNull(field(f.computer, "captureSession"))
        assertSame(feed, f.feeds.last())
        assertNotNull(f.panels.last())
        val completed = chat(f.activity)

        f.lifecycle.pause().stop()
        advance()

        assertEquals(completed, chat(f.activity))
        assertTrue(notices(f.activity).isEmpty())
        assertFalse(f.computer.active)
        assertEquals(LiveMonitorState.ENDED, feed.status(System.nanoTime()).state)
        assertTrue(buffer.isClosed)
        assertNull(f.feeds.last())
        assertNull(f.panels.last())
    }

    @Test fun ordinaryVerifiedReplyStillUsesTheExistingSpeechPolicy() = withFixture { f ->
        val message = "Die App ist sichtbar."
        call(f.activity, "deliverComputerReply",
            pendingType to f.pending,
            OwnerComputerWorkflow.Result::class.java to OwnerComputerWorkflow.Result(
                OwnerComputerWorkflow.Status.VERIFIED, message),
            java.lang.Long::class.java to ScreenObservationBus.grantEpoch(),
            Boolean::class.javaPrimitiveType!! to true)
        assertTrue(chat(f.activity).contains(message))
        assertEquals(message, shadowOf(f.tts).spokenTextList.last())
        assertNotNull(field(f.activity, "speechTurnId"))
    }
}
