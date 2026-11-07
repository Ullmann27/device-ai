package at.heinz.deviceai

import android.app.Activity
import android.app.AlertDialog
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], shadows = [ShizukuBridgeLifecycleTest.Server::class])
class ShizukuConnectionDialogTest {
    private fun text(view: View): String = when (view) {
        is TextView -> view.text.toString()
        is ViewGroup -> (0 until view.childCount).joinToString("\n") { text(view.getChildAt(it)) }
        else -> ""
    }

    @Test fun connectKeepsStatusVisibleAndReportsTimeoutInTheSameDialog() {
        val server = ShizukuBridgeLifecycleTest.Server
        server.running = true; server.granted = true; server.connections.clear(); server.unbound.clear()
        val controller = Robolectric.buildActivity(Activity::class.java)
        controller.get().setTheme(R.style.AppTheme)
        val activity = controller.setup().get()
        var screen: ShizukuConnectionDialog? = null
        val bridge = ShizukuBridge(activity) { screen?.updateStatus(it) }
        try {
            bridge.start()
            screen = ShizukuConnectionDialog(activity, bridge)
            val dialog = screen.show()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertTrue("Connect must not close the status screen", dialog.isShowing)
            assertTrue(text(dialog.window!!.decorView).contains("Verbinde Shell-Dienst"))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(16))
            assertTrue(dialog.isShowing)
            assertTrue(text(dialog.window!!.decorView).contains("Zeitüberschreitung"))
            assertFalse(bridge.isReady())
        } finally {
            screen?.dismiss(); bridge.stop(); controller.pause().stop().destroy()
        }
    }

    @Test fun missingManagerReportsActionableErrorWithoutClosingTheScreen() {
        val server = ShizukuBridgeLifecycleTest.Server
        server.running = false; server.connections.clear(); server.unbound.clear()
        val controller = Robolectric.buildActivity(Activity::class.java)
        controller.get().setTheme(R.style.AppTheme)
        val activity = controller.setup().get()
        var screen: ShizukuConnectionDialog? = null
        val bridge = ShizukuBridge(activity) { screen?.updateStatus(it) }
        try {
            bridge.start(); screen = ShizukuConnectionDialog(activity, bridge)
            val dialog = screen.show()
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
            assertTrue(dialog.isShowing)
            assertTrue(text(dialog.window!!.decorView).contains("nicht installiert"))
        } finally {
            screen?.dismiss(); bridge.stop(); controller.pause().stop().destroy()
        }
    }
}
