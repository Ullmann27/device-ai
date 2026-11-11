package at.heinz.deviceai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import at.heinz.deviceai.cognitive.CockpitWorkJournal
import at.heinz.deviceai.ui.ContinuumHomeView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/** Host lifecycle; no PDF decoding. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ContinuumPdfSceneAcceptanceUiTest {
    private var framePump: CockpitHostFramePump? = null

    @Before fun pauseFrames() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(owner)

    private fun call(activity: AgentActivity, name: String, vararg arguments: Any?) =
        AgentActivity::class.java.declaredMethods.single {
            it.name == name && it.parameterCount == arguments.size &&
                it.parameterTypes.zip(arguments).all { (type, value) ->
                    value == null || type.isInstance(value) || type.isPrimitive
                }
        }.apply { isAccessible = true }.invoke(activity, *arguments)

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun chat(activity: AgentActivity) = descendants(field(activity, "chatContainer") as View)
        .filterIsInstance<TextView>().map { it.text.toString() }

    private fun advance(ms: Long) {
        var remaining = ms
        while (remaining > 0L) {
            val step = minOf(16L, remaining)
            shadowOf(Looper.getMainLooper()).idleFor(step, TimeUnit.MILLISECONDS)
            framePump?.drawRequestedFrame()
            remaining -= step
        }
    }

    private fun flushDocumentLane(activity: AgentActivity) {
        val executor = field(activity, "documentIoExecutor") as ExecutorService
        repeat(2) {
            executor.submit {}.get(10, TimeUnit.SECONDS)
            advance(64L)
        }
    }

    private fun send(activity: AgentActivity, text: String) {
        (field(activity, "input") as EditText).setText(text)
        call(activity, "sendCurrentInput", false)
    }

    private data class Picker(val intent: Intent, val requestCode: Int)

    private fun picker(activity: AgentActivity, request: String = "Öffne diese PDF."): Picker {
        send(activity, request)
        val started = shadowOf(activity).nextStartedActivityForResult
        assertNotNull("The owner request opens Android's picker", started)
        requireNotNull(started).intent.also {
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, it.action)
            assertEquals("application/pdf", it.type)
            assertTrue(it.hasCategory(Intent.CATEGORY_OPENABLE))
        }
        return Picker(started.intent, started.requestCode)
    }

    private fun deliver(activity: AgentActivity, picker: Picker, result: Int, data: Intent?) {
        call(activity, "onActivityResult", picker.requestCode, result, data)
    }

    private fun deniedSelection() = Intent().apply {
        data = Uri.parse("content://at.heinz.test.unavailable/document/real-selection")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun assertNoWork(activity: AgentActivity, root: View) {
        assertFalse(field(activity, "busy") as Boolean)
        assertFalse(descendants(root).any { it.tag == "jarvis-work-window" && it.isShown })
        assertTrue(CockpitWorkJournal(activity.filesDir).all().isEmpty())
    }

    private fun withCockpit(block: (ActivityController<AgentActivity>, AgentActivity, View) -> Unit) {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit()
            .putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit().clear().commit()
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        try {
            controller.setup().visible()
            advance(32L)
            controller.windowFocusChanged(true)
            val activity = controller.get()
            val root = activity.window.decorView
            framePump = CockpitHostFramePump { root }
            (field(activity, "homeView") as ContinuumHomeView).setForegroundActive(true)
            root.measure(View.MeasureSpec.makeMeasureSpec(884, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(760, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 884, 760)
            advance(320L)
            block(controller, activity, root)
        } finally {
            framePump?.close()
            framePump = null
            ShadowAlertDialog.getLatestAlertDialog()?.takeIf { it.isShowing }?.dismiss()
            runCatching { controller.close() }
        }
    }

    @Test fun visibilityRequestUsesRealPickerAndCancellationIsConsumedOnce() = withCockpit { _, activity, root ->
        val request = picker(activity,
            "öffne meine PDF Argon 4 Forschungsexperiment und zeige mir jeden Arbeitsschritt")
        deliver(activity, request, Activity.RESULT_CANCELED, null)
        flushDocumentLane(activity)
        val afterCancel = chat(activity)
        assertEquals(1, afterCancel.count { it == "Keine PDF gewählt – es wurde nichts gelesen." })
        deliver(activity, request, Activity.RESULT_CANCELED, null)
        flushDocumentLane(activity)
        assertEquals("A repeated result is inert", afterCancel, chat(activity))
        assertNoWork(activity, root)
    }

    @Test fun stopInvalidatesAnOpenOnlyPickerBeforeItsLateUriArrives() = withCockpit { _, activity, root ->
        val request = picker(activity)
        call(activity, "cancelLocalRequest")
        flushDocumentLane(activity)
        val afterStop = chat(activity)
        deliver(activity, request, Activity.RESULT_OK, deniedSelection())
        flushDocumentLane(activity)
        assertEquals("STOP prevents even a new read attempt", afterStop, chat(activity))
        assertNoWork(activity, root)
    }

    @Test fun aNewOwnerTurnInvalidatesTheOlderPicker() = withCockpit { _, activity, root ->
        val request = picker(activity)
        send(activity, "Wie spät ist es?")
        flushDocumentLane(activity)
        val afterNewTurn = chat(activity)
        deliver(activity, request, Activity.RESULT_OK, deniedSelection())
        flushDocumentLane(activity)
        assertEquals("An old URI cannot interrupt the new turn", afterNewTurn, chat(activity))
        assertNoWork(activity, root)
    }

    @Test fun pausedPickerResultWaitsForResumeAndDeniedReadNeverClaimsSuccess() = withCockpit { controller, activity, root ->
        val request = picker(activity)
        controller.pause()
        val beforeResult = chat(activity)
        deliver(activity, request, Activity.RESULT_OK, deniedSelection())
        flushDocumentLane(activity)
        assertEquals("No background read or reply", beforeResult, chat(activity))
        controller.resume()
        flushDocumentLane(activity)
        val afterResume = chat(activity)
        assertEquals(1, afterResume.count { it.startsWith("Die PDF ließ sich nicht lesen (") })
        assertFalse(afterResume.any { it.startsWith("PDF gelesen:") })
        assertNoWork(activity, root)
        deliver(activity, request, Activity.RESULT_OK, deniedSelection())
        flushDocumentLane(activity)
        assertEquals("The consumed URI is never read again", afterResume, chat(activity))
    }
}
