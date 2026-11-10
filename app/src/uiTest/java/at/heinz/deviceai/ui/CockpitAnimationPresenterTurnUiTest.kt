package at.heinz.deviceai.ui

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.View
import at.heinz.deviceai.ContinuumHologramView
import at.heinz.deviceai.cockpit.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class CockpitAnimationPresenterTurnUiTest {
    private val controller = Robolectric.buildActivity(Activity::class.java)
    private lateinit var home: ContinuumHomeView
    private lateinit var presenter: CockpitAnimationPresenter
    private val bus = CockpitAnimationBus()
    private val projected = mutableListOf<WindowProjectedMotion>()
    private var subscription: AutoCloseable? = null

    @Before fun attach() {
        CockpitTransitions.hook = null
        CueOwner.values().forEach { CockpitCues.register(it, null) }
        controller.setup().visible()
        val activity = controller.get()
        val hologram = ContinuumHologramView(activity).apply {
            setNativeHeadEnabled(false)
            setAnimationsEnabled(false)
            setForegroundActive(false)
        }
        home = ContinuumHomeView(activity, hologram) { _, _, _ -> }.apply {
            configure(884f, true, windowHeightDp = 760f)
            setJarvisShellStage(true)
            jarvisShell.ambientMotionEnabled = false
        }
        activity.setContentView(home)
        home.measure(View.MeasureSpec.makeMeasureSpec(884, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(760, View.MeasureSpec.EXACTLY))
        home.layout(0, 0, 884, 760)
        home.setForegroundActive(true)
        idle()
        assertTrue(home.isPresentationActive())
        presenter = CockpitAnimationPresenter(home, bus, "presenter-test")
        subscription = bus.subscribe { if (it is WindowProjectedMotion) projected += it }
    }

    @After fun detach() {
        subscription?.close()
        controller.close()
        CockpitTransitions.hook = null
        CueOwner.values().forEach { CockpitCues.register(it, null) }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun emit(event: CockpitAnimationEvent) { presenter.render(event); idle() }
    private fun start(id: String) = emit(CockpitAnimationEvent.CommandFlyIn(id, 1_700_000_000_000L, "Prüfauftrag"))
    private fun end(id: String, delivered: Boolean = true) =
        emit(CockpitAnimationEvent.TurnEnded(id, 1_700_000_000_100L, delivered))
    private fun field(name: String): Any? = JarvisShellView::class.java.getDeclaredField(name)
        .apply { isAccessible = true }.get(home.jarvisShell)
    private fun sample(id: String = "window") = home.jarvisShell.offerProjectedMotion(
        id, 200f, 480f, 440f, 200f, 884f, 760f, WindowProjectedMotion.Phase.FLYING,
        null, reducedMotion = false, moving = true)

    @Test fun oldAndForeignEndsCannotCloseTheNewRequest() {
        start("a")
        start("b")
        sample()
        end("a")
        end("foreign", delivered = false)
        assertEquals("b", field("workStageTurnId"))
        assertEquals(-1L, field("exitMs"))
        assertFalse(projected.any { it.phase == WindowProjectedMotion.Phase.CLOSED })
        end("b", delivered = false)
        assertTrue(field("exitMs") as Long >= 0L)
        assertEquals("b", projected.last().turnId)
        assertEquals(WindowProjectedMotion.Phase.CLOSED, projected.last().phase)
        assertTrue(projected.last().atMillis <= SystemClock.uptimeMillis())
    }

    @Test fun duplicateStartDoesNotRestartTheFlight() {
        start("a")
        val started = field("startMs")
        shadowOf(Looper.getMainLooper()).idleFor(32, TimeUnit.MILLISECONDS)
        start("a")
        assertEquals(started, field("startMs"))
    }

    @Test fun lateStartAndStepCannotReviveTheRetiredTurn() {
        start("a")
        start("b")
        start("a")
        emit(CockpitAnimationEvent.ProcessTimeline("a", 10L,
            listOf(ProcessStep("done", "Fertig", StepState.DONE))))
        assertEquals("b", field("workStageTurnId"))
        assertEquals(false, field("pendingResultAdvance"))
    }

    @Test fun foreignWorkerCannotStealTheCurrentScene() {
        start("owner")
        emit(CockpitAnimationEvent.ActionStarted("worker", 2L, CockpitAction.WORKER, "Prüfung"))
        end("worker")
        assertEquals("owner", field("workStageTurnId"))
        assertEquals(-1L, field("exitMs"))
    }

    @Test fun cancelledPendingRequestCannotRestartAfterReturn() {
        start("a")
        end("a")
        start("b")
        assertEquals("b", field("pendingWorkStageTurnId"))
        end("a")
        assertEquals("b", field("pendingWorkStageTurnId"))
        end("b", delivered = false)
        assertNull(field("pendingWorkStageTurnId"))
    }

    @Test fun globalStopClosesTheTrackedOwner() {
        start("a")
        sample()
        emit(CockpitAnimationEvent.AreaChanged(NAV_TURN, 1_700_000_000_100L,
            "home", JarvisCockpitPanels.STOP_TARGET, TransitionPattern.B_FLY_IN))
        assertEquals("a", projected.last().turnId)
        assertEquals(WindowProjectedMotion.Phase.CLOSED, projected.last().phase)
        assertTrue(field("exitMs") as Long >= 0L)
    }

    @Test fun oldBootDeadlineCannotResetANewerMission() {
        emit(CockpitAnimationEvent.BootStarted(BOOT_TURN, 0L, "Bereit"))
        start("a")
        val started = field("startMs")
        shadowOf(Looper.getMainLooper()).idleFor(CockpitTimeline.BOOT_COCKPIT_AT_MS + 1, TimeUnit.MILLISECONDS)
        assertEquals("a", field("workStageTurnId"))
        assertEquals(started, field("startMs"))
    }

    @Test fun backgroundCompletionStillClosesItsProjection() {
        start("a")
        sample()
        home.setForegroundActive(false)
        end("a")
        assertEquals(WindowProjectedMotion.Phase.CLOSED, projected.last().phase)
        home.setForegroundActive(true)
        start("b")
        end("a")
        assertEquals("b", field("workStageTurnId"))
        assertEquals(-1L, field("exitMs"))
    }
}
