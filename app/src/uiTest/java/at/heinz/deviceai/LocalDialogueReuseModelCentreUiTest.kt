package at.heinz.deviceai

import android.app.AlertDialog
import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import at.heinz.deviceai.localmodel.LocalDialogueReuse
import at.heinz.deviceai.localmodel.LocalDialogueReuseHost
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
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * The owner switch „Gespräch im Modell behalten“ and the dialogue latency probe button in the real model centre
 * under Robolectric: off by default, switched on only after the owner confirmed the experimental notice, stored
 * across restarts, and the probe refuses to start without a loaded model. HOST; nothing here ran a model.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LocalDialogueReuseModelCentreUiTest {
    private val app: Context get() = RuntimeEnvironment.getApplication()
    private val untested = "experimentell – am Gerät ungeprüft"

    @Before fun prepare() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        app.getSharedPreferences("continuum-command03", Context.MODE_PRIVATE).edit()
            .putBoolean("original_html_main", false).commit()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit().putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-internet", Context.MODE_PRIVATE).edit().putString("mode", "OFF").commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
    }

    private fun field(a: AgentActivity, name: String): Any? =
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(a)
    private fun call(a: AgentActivity, name: String): Any? =
        AgentActivity::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(a)
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    private fun textViews(view: View) = all(view).filterIsInstance<TextView>()
    private fun advance() = shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
    private fun await(message: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
        while (!condition() && System.nanoTime() < deadline) { advance(); Thread.sleep(5) }
        assertTrue(message, condition())
    }
    private fun withActivity(block: (AgentActivity) -> Unit) {
        val controller: ActivityController<AgentActivity> =
            Robolectric.buildActivity(AgentActivity::class.java).setup().visible()
        try {
            val a = controller.get()
            await("Activity is foreground") { field(a, "foregroundLearningActive") == true }
            block(a)
        } finally {
            runCatching { controller.pause().stop().destroy() }
            AgentActivity.chatTranscriptIo.submit {}.get(8, TimeUnit.SECONDS)
        }
    }
    private fun centre(a: AgentActivity): AlertDialog {
        call(a, "showModelCenter")
        advance()
        return ShadowAlertDialog.getLatestAlertDialog()
    }
    private fun texts(dialog: AlertDialog) = textViews(dialog.window!!.decorView).map { it.text.toString() }
    private fun action(dialog: AlertDialog, label: String) =
        textViews(dialog.window!!.decorView).single { it.text.toString() == label }

    @Test fun theSwitchIsOffByDefaultNeedsTheOwnersConfirmationAndIsStored() = withActivity { a ->
        assertFalse(LocalDialogueReuseHost.enabled(app))
        val dialog = centre(a)
        val shown = texts(dialog)
        assertTrue(shown.toString(), shown.contains("GESPRÄCH IM MODELL BEHALTEN · EXPERIMENTELL"))
        assertTrue(shown.toString(), shown.any { it.startsWith("Gespräch im Modell behalten (schneller, $untested): AUS (Standard).") &&
            it.contains("hat noch niemand gemessen") })
        assertTrue(shown.contains("GESPRÄCHS-ZEITPROBE · 6 ZÜGE OHNE UND MIT BEHALTEN (EXPERIMENTELL)"))

        // Declining the notice changes nothing.
        action(dialog, LocalDialogueReuse.actionLabel(false)).performClick()
        advance()
        val declined = ShadowAlertDialog.getLatestAlertDialog()
        assertNotSame(dialog, declined)
        assertTrue(texts(declined).toString(), texts(declined).any { it.contains("ist auf deinem Gerät noch nie gelaufen") })
        declined.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        advance()
        assertFalse(LocalDialogueReuseHost.enabled(app))
        assertEquals(false, call(a, "dialogueReuseOn"))

        // Confirming switches it on, updates label and status and stores the choice.
        action(dialog, LocalDialogueReuse.actionLabel(false)).performClick()
        advance()
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        advance()
        assertTrue(LocalDialogueReuseHost.enabled(app))
        assertEquals(true, call(a, "dialogueReuseOn"))
        assertTrue(texts(dialog).any { it.startsWith("Gespräch im Modell behalten (schneller, $untested): EIN.") })

        // Switching off needs no confirmation.
        action(dialog, LocalDialogueReuse.actionLabel(true)).performClick()
        advance()
        assertFalse(LocalDialogueReuseHost.enabled(app))
        assertEquals(false, call(a, "dialogueReuseOn"))
        assertTrue(texts(dialog).contains(LocalDialogueReuse.actionLabel(false)))
        dialog.dismiss()
        advance()
    }

    @Test fun aStoredChoiceIsReadByTheNextWindow() {
        assertTrue(LocalDialogueReuseHost.setEnabled(app, true))
        withActivity { a ->
            assertEquals(true, call(a, "dialogueReuseOn"))
            val dialog = centre(a)
            assertTrue(texts(dialog).contains(LocalDialogueReuse.actionLabel(true)))
            dialog.dismiss()
            advance()
        }
    }

    @Test fun theProbeButtonDoesNotStartWithoutALoadedModel() = withActivity { a ->
        val dialog = centre(a)
        action(dialog, "GESPRÄCHS-ZEITPROBE · 6 ZÜGE OHNE UND MIT BEHALTEN (EXPERIMENTELL)").performClick()
        advance()
        val chat = textViews(field(a, "chatContainer") as LinearLayout).map { it.text.toString() }
        assertTrue(chat.toString(), chat.any { it.contains("GESPRÄCHS-ZEITPROBE · Kein geladenes Modell im Zustand BEREIT") })
        assertEquals(false, field(a, "busy"))
    }
}
