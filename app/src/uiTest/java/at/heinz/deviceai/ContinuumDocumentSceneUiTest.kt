package at.heinz.deviceai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import at.heinz.deviceai.ui.ContinuumHomeView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Owner flow „Lies diese PDF …“ (06.11.2026). HOST + SIMULATED_DOCUMENT_INPUT, REAL_DEVICE_NOT_RUN: the picker wiring
 * runs for real; the document scene gets a [PdfDocumentIntake.Document] built here, because Android's PDF renderer is
 * native and does not run on the host. The Fold7 run with a real PDF is still open.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ContinuumDocumentSceneUiTest {
    private var framePump: CockpitHostFramePump? = null
    @Before fun pauseVsync() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun advance(ms: Long) {
        var remaining = ms
        while (remaining > 0L) {
            val step = minOf(16L, remaining)
            shadowOf(Looper.getMainLooper()).idleFor(step, TimeUnit.MILLISECONDS)
            framePump?.drawRequestedFrame()
            remaining -= step
        }
    }
    private fun measure(root: View) {
        root.measure(View.MeasureSpec.makeMeasureSpec(884, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(760, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 884, 760)
    }
    private fun draw(root: View) {
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        try { root.draw(Canvas(bitmap)) } finally { bitmap.recycle() }
    }

    private fun <T> withCockpit(block: (AgentActivity, View) -> T): T {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit().putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit().clear().commit()
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        try {
            controller.setup().visible(); advance(32L); controller.windowFocusChanged(true)
            val activity = controller.get()
            val root = activity.window.decorView
            framePump = CockpitHostFramePump { root }
            (field(activity, "homeView") as ContinuumHomeView).setForegroundActive(true)
            measure(root); advance(320L); measure(root); draw(root)
            return block(activity, root)
        } finally {
            framePump?.close(); framePump = null
            ShadowAlertDialog.getLatestAlertDialog()?.takeIf { it.isShowing }?.dismiss()
            runCatching { controller.close() }
        }
    }

    private fun chatTexts(activity: AgentActivity) =
        descendants(field(activity, "chatContainer") as View).filterIsInstance<TextView>().map { it.text.toString() }

    @Test fun theOwnersPdfRequestOpensOnlyThePickerAndACancelReadsNothing() = withCockpit { activity, root ->
        (field(activity, "input") as EditText).setText("Lies diese PDF und erstelle mir eine Zusammenfassung als Entwurf.")
        AgentActivity::class.java.getDeclaredMethod("sendCurrentInput", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(activity, false)
        val started = shadowOf(activity).nextStartedActivityForResult
        assertNotNull("Android's document picker is the only thing that opens", started)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, started.intent.action)
        assertEquals("application/pdf", started.intent.type)
        assertTrue(started.intent.hasCategory(Intent.CATEGORY_OPENABLE))
        assertEquals(true, field(activity, "pendingDocumentDraft"))
        assertEquals("no model turn starts for the request itself", false, field(activity, "busy"))
        shadowOf(activity).receiveResult(started.intent, Activity.RESULT_CANCELED, null)
        advance(64L); measure(root); draw(root)
        assertTrue("a cancel is said honestly: ${chatTexts(activity)}", chatTexts(activity).any { it.contains("Keine PDF gewählt – es wurde nichts gelesen.") })
        assertEquals(false, field(activity, "pendingDocumentDraft"))
        assertTrue("nothing opens without a file", descendants(root).none { it.tag == "jarvis-work-window" && it.isShown })
    }

    @Test fun theDocumentArrivesAsItsOwnPageOnTheTrailAndJarvisLooksAtIt() = withCockpit { activity, root ->
        val page = Bitmap.createBitmap(360, 480, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            Canvas(this).drawRect(40f, 60f, 320f, 90f, android.graphics.Paint().apply { color = Color.BLACK })
        }
        val text = "Projektübersicht Continuum. Ziel: ein lokales Cockpit auf dem Fold7."
        val doc = PdfDocumentIntake.Document("Projektübersicht.pdf", 12_288L, 3, page, text,
            PdfDocumentIntake.TextState.EXTRACTED, 3)
        val director = field(activity, "avatarDirector")!!
        AgentActivity::class.java.getDeclaredMethod("presentDocumentScene", PdfDocumentIntake.Document::class.java)
            .apply { isAccessible = true }.invoke(activity, doc)
        assertNotNull("JARVIS looks at the arriving document", field(director, "windowIntent"))
        advance(120L); measure(root); draw(root)
        val window = descendants(root).single { it.tag == "jarvis-work-window" && it.isShown }
        val emblem = field(window, "emblem") as? BitmapDrawable
        assertSame("the trail carries the document's own first page", page, emblem?.bitmap)
        advance(640L); measure(root); draw(root)
        val texts = descendants(window).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue("titled with the file: $texts", texts.contains("DOKUMENT · Projektübersicht.pdf"))
        val image = descendants(window).filterIsInstance<ImageView>().single { it.tag == "jarvis-document-page" }
        assertSame("the page image is the document's own page", page, (image.drawable as BitmapDrawable).bitmap)
        val meta = descendants(window).single { it.tag == "jarvis-document-meta" } as TextView
        assertTrue(meta.text.toString(), meta.text.startsWith("3 Seiten · ") && meta.text.endsWith(" · Projektübersicht.pdf"))
        assertEquals("the excerpt is the document's text", text, (descendants(window).single { it.tag == "jarvis-document-excerpt" } as TextView).text.toString())
        assertEquals("the deck made room", 0, descendants(root).count { v -> v.isShown && v.tag?.toString()?.startsWith("jarvis-tile:") == true })
        assertTrue("chat names the same facts", chatTexts(activity).any { it.startsWith("PDF gelesen: 3 Seiten") && it.contains("${text.length} Zeichen von Seite 1–3") })
    }

    @Test fun withoutTextTheSceneSaysWhyAndShowsNoInventedExcerpt() = withCockpit { activity, root ->
        val page = Bitmap.createBitmap(200, 280, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        AgentActivity::class.java.getDeclaredField("pendingDocumentDraft").apply { isAccessible = true }.set(activity, true)
        val doc = PdfDocumentIntake.Document("Scan.pdf", null, 1, page, "", PdfDocumentIntake.TextState.NO_TEXT_LAYER, 1)
        AgentActivity::class.java.getDeclaredMethod("presentDocumentScene", PdfDocumentIntake.Document::class.java)
            .apply { isAccessible = true }.invoke(activity, doc)
        advance(760L); measure(root); draw(root)
        val window = descendants(root).single { it.tag == "jarvis-work-window" && it.isShown }
        assertTrue(descendants(window).none { it.tag == "jarvis-document-excerpt" })
        val status = (descendants(window).single { it.tag == "jarvis-document-status" } as TextView).text.toString()
        assertTrue(status, status.startsWith("Kein auslesbarer Text"))
        assertEquals("Für einen Entwurf fehlt der Dokumenttext.",
            (descendants(window).single { it.tag == "jarvis-document-draft" } as TextView).text.toString())
        assertFalse("a missing size is not invented", (descendants(window).single { it.tag == "jarvis-document-meta" } as TextView).text.contains("B"))
    }
}
