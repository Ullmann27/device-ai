package at.heinz.deviceai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import at.heinz.deviceai.cockpit.WindowProjectedMotion
import at.heinz.deviceai.ui.JarvisShellView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class JarvisCinemaReplayUiTest {
    private fun field(view: JarvisShellView, name: String): Any? =
        JarvisShellView::class.java.getDeclaredField(name).apply { isAccessible = true }.get(view)

    private fun finish(view: JarvisShellView) {
        JarvisShellView::class.java.getDeclaredMethod("completeReturn", Long::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(view, SystemClock.uptimeMillis())
    }

    private fun returningWithQueuedOrder(): JarvisShellView =
        JarvisShellView(RuntimeEnvironment.getApplication()).apply {
            bindCinemaTurn("a")
            playWorkStage()
            playExit()
            bindCinemaTurn("b")
            playWorkStage()
        }

    private fun settle(view: JarvisShellView): List<WindowProjectedMotion> {
        val events = mutableListOf<WindowProjectedMotion>()
        view.onProjectedMotion = { events += it }
        view.layout(0, 0, 884, 760)
        val bitmap = Bitmap.createBitmap(884, 760, Bitmap.Config.ARGB_8888)
        try {
            ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
            view.draw(Canvas(bitmap))
            ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
            view.draw(Canvas(bitmap))
        } finally {
            bitmap.recycle()
        }
        return events
    }

    @Test fun queuedEarlyResultAdvancesAfterReturnAndConstruction() {
        val view = returningWithQueuedOrder()
        view.playWorkWindowEntrance()
        finish(view)
        assertEquals("b", field(view, "workStageTurnId"))
        val events = settle(view)
        assertTrue("The result must move forward after construction", events.any {
            it.turnId == "b" && it.windowId == "work-draft" &&
                it.phase == WindowProjectedMotion.Phase.FLYING
        })
        assertEquals(true, field(view, "resultForward"))
    }

    @Test fun repeatedQueuedOrderKeepsItsEarlyResult() {
        val view = returningWithQueuedOrder()
        view.playWorkWindowEntrance()
        view.playWorkStage()
        finish(view)
        settle(view)
        assertEquals(true, field(view, "resultForward"))
    }

    @Test fun replacedQueuedOrderDoesNotInheritAnEarlierResult() {
        val view = returningWithQueuedOrder()
        view.playWorkWindowEntrance()
        view.bindCinemaTurn("c")
        view.playWorkStage()
        view.bindCinemaTurn("b")
        view.playWorkWindowEntrance()
        finish(view)
        settle(view)
        assertEquals("c", field(view, "workStageTurnId"))
        assertEquals(false, field(view, "resultForward"))
    }

    @Test fun endedQueuedOrderCannotTransferItsResultToTheNextOrder() {
        val view = returningWithQueuedOrder()
        view.playWorkWindowEntrance()
        view.closeProjectedMotion("b", SystemClock.uptimeMillis())
        view.bindCinemaTurn("c")
        view.playWorkStage()
        finish(view)
        settle(view)
        assertEquals("c", field(view, "workStageTurnId"))
        assertEquals(false, field(view, "resultForward"))
    }

    @Test fun oldTurnResultCannotAdvanceTheQueuedOrder() {
        val view = returningWithQueuedOrder()
        view.bindCinemaTurn("a")
        view.playWorkWindowEntrance()
        finish(view)
        settle(view)
        assertEquals("b", field(view, "workStageTurnId"))
        assertEquals(false, field(view, "resultForward"))
    }

    @Test fun newOrderWaitsForTheCurrentReturn() {
        val view = JarvisShellView(RuntimeEnvironment.getApplication())
        view.bindCinemaTurn("a")
        view.playWorkStage()
        view.playExit()
        val exit = field(view, "exitMs")
        view.bindCinemaTurn("b")
        view.playWorkStage()
        assertEquals(exit, field(view, "exitMs"))
        assertEquals("a", field(view, "workStageTurnId"))
        assertEquals("b", field(view, "pendingWorkStageTurnId"))
        finish(view)
        assertEquals("b", field(view, "workStageTurnId"))
        assertEquals(-1L, field(view, "exitMs"))
        assertNull(field(view, "pendingWorkStageTurnId"))
    }

    @Test fun endedPendingOrderCannotRestartAfterReturn() {
        val view = JarvisShellView(RuntimeEnvironment.getApplication())
        view.bindCinemaTurn("a")
        view.playWorkStage()
        view.playExit()
        view.bindCinemaTurn("b")
        view.playWorkStage()
        view.closeProjectedMotion("b", SystemClock.uptimeMillis())
        finish(view)
        assertEquals(false, field(view, "workClear"))
        assertNull(field(view, "pendingWorkStageTurnId"))
    }

    @Test fun earlyResultWaitsForConstructionAndStopCancelsIt() {
        val view = JarvisShellView(RuntimeEnvironment.getApplication())
        view.bindCinemaTurn("a")
        view.playWorkStage()
        view.playWorkWindowEntrance()
        assertEquals(false, field(view, "resultForward"))
        assertEquals(true, field(view, "pendingResultAdvance"))
        view.playExit()
        assertEquals(false, field(view, "pendingResultAdvance"))
        val exit = field(view, "exitMs")
        view.playWorkWindowEntrance()
        assertEquals(exit, field(view, "exitMs"))
    }
}
