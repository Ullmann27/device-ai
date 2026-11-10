package at.heinz.deviceai

import android.os.SystemClock
import at.heinz.deviceai.ui.JarvisShellView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class JarvisCinemaReplayUiTest {
    private fun field(view: JarvisShellView, name: String): Any? =
        JarvisShellView::class.java.getDeclaredField(name).apply { isAccessible = true }.get(view)

    private fun finish(view: JarvisShellView) {
        JarvisShellView::class.java.getDeclaredMethod("completeReturn", Long::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(view, SystemClock.uptimeMillis())
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
