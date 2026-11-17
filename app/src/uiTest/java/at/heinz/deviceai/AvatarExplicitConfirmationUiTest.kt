package at.heinz.deviceai

import android.app.AlertDialog
import android.content.Context
import android.os.Looper
import android.provider.Settings
import at.heinz.deviceai.avatar.AvatarDirector
import at.heinz.deviceai.avatar.AvatarEvent
import at.heinz.deviceai.avatar.AvatarSink
import at.heinz.deviceai.cockpit.JarvisAvatarSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
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

/** Actual dialogs; recording-only decision callbacks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi", instrumentedPackages = [
    "at.heinz.deviceai.avatar.AvatarDirector",
    "at.heinz.deviceai.ContinuumHologramView"
])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
class AvatarExplicitConfirmationUiTest {
    private data class Fixture(val activity: AgentActivity, val events: MutableList<AvatarEvent>) {
        fun confirmations() = events.filterIsInstance<AvatarEvent.AnimationIntentsChanged>()
            .filter { it.confirmationAtMillis != Long.MIN_VALUE }
    }

    private fun field(target: Any, name: String): Any? = target.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(target)

    private fun withActivity(check: (Fixture) -> Unit) {
        val app = RuntimeEnvironment.getApplication()
        val previousScale = Settings.Global.getFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
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
            val director = field(activity, "avatarDirector") as AvatarDirector
            val originalSink = field(director, "sink") as AvatarSink
            val events = mutableListOf<AvatarEvent>()
            director.attach(AvatarSink { event -> events += event; originalSink.onAvatarEvent(event) })
            shadowOf(Looper.getMainLooper()).idle()
            events.clear()
            check(Fixture(activity, events))
        } finally {
            ShadowAlertDialog.getLatestAlertDialog()?.takeIf { it.isShowing }?.dismiss()
            try {
                controller.pause().stop().destroy()
            } finally {
                JarvisAvatarSlot.register(null)
                Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, previousScale)
                app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit().clear().commit()
            }
        }
    }

    private fun open(fixture: Fixture, destructive: Boolean, decisions: MutableList<Boolean>): AlertDialog {
        val proposal = if (destructive) ToolProposal("delete_file", mapOf("path" to "/synthetic-confirmation-only.txt"))
            else ToolProposal("get_device_info", emptyMap())
        val recordOnly: (Boolean) -> Unit = { decisions += it }
        AgentActivity::class.java.declaredMethods.single {
            it.name == "showStructuredToolConfirmation" && it.parameterCount == 3
        }.apply { isAccessible = true }.invoke(fixture.activity, proposal, destructive, recordOnly)
        shadowOf(Looper.getMainLooper()).idle()
        return requireNotNull(ShadowAlertDialog.getLatestAlertDialog()).also { assertTrue(it.isShowing) }
    }

    private fun click(dialog: AlertDialog, button: Int, label: String) {
        assertEquals(label, dialog.getButton(button).text.toString())
        assertTrue(dialog.getButton(button).performClick())
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun destructiveApprovalNodsOnlyAfterTheLastExplicitPositiveButton() = withActivity { f ->
        val decisions = mutableListOf<Boolean>()
        click(open(f, true, decisions), AlertDialog.BUTTON_NEGATIVE, "Ablehnen")
        assertEquals(listOf(false), decisions)
        assertTrue("Rejection must not nod", f.confirmations().isEmpty())
        decisions.clear()
        f.events.clear()

        val initial = open(f, true, decisions)
        click(initial, AlertDialog.BUTTON_POSITIVE, "Weiter prüfen")
        assertTrue("Review is not approval", decisions.isEmpty())
        assertTrue("First review step must not nod", f.confirmations().isEmpty())
        val finalReview = requireNotNull(ShadowAlertDialog.getLatestAlertDialog())
        assertNotSame(initial, finalReview)
        assertTrue(finalReview.isShowing)
        click(finalReview, AlertDialog.BUTTON_POSITIVE, "Destruktiv ausführen")
        assertEquals("Only the supplied callback runs", listOf(true), decisions)
        assertEquals("Exactly one explicit confirmation intent", 1, f.confirmations().size)
        assertTrue(f.confirmations().single().confirmationAtMillis >= 0L)
        assertTrue("Confirmation cannot invent speech", f.events.none {
            it is AvatarEvent.SpeechStarted || it is AvatarEvent.AudioLevel || it is AvatarEvent.VisemeCue
        })
    }

    @Test fun ordinaryApprovalNodsOnlyForExplicitExecute() = withActivity { f ->
        val decisions = mutableListOf<Boolean>()
        click(open(f, false, decisions), AlertDialog.BUTTON_NEGATIVE, "Ablehnen")
        assertEquals(listOf(false), decisions)
        assertTrue(f.confirmations().isEmpty())
        decisions.clear()
        f.events.clear()

        click(open(f, false, decisions), AlertDialog.BUTTON_POSITIVE, "Ausführen")
        assertEquals(listOf(true), decisions)
        assertEquals("Exactly one explicit confirmation intent", 1, f.confirmations().size)
        assertTrue("Confirmation cannot invent speech", f.events.none {
            it is AvatarEvent.SpeechStarted || it is AvatarEvent.AudioLevel || it is AvatarEvent.VisemeCue
        })
    }
}
