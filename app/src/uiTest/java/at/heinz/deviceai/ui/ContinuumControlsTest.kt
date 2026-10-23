package at.heinz.deviceai.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import at.heinz.deviceai.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Robolectric
import java.io.File
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Runs the actual Android controls; does not launch AgentActivity, models, jobs or permission flows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContinuumControlsTest {
    private fun context(font: Float = 1f): Context {
        val app = RuntimeEnvironment.getApplication()
        val config = Configuration(app.resources.configuration).apply { fontScale = font }
        return ContextThemeWrapper(app.createConfigurationContext(config), R.style.AppTheme)
    }

    private fun measure(view: View, dp: Int) {
        val px = (dp * view.resources.displayMetrics.density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    @Test fun actionHasButtonSemanticsAndRealStateChangesWithoutLosingItsCallback() {
        var clicks = 0
        val action = ContinuumBentoCockpit.action(context(), "Lokalen Status öffnen", primary = true) { clicks++ }
        measure(action, 280)
        val semantic = action.createAccessibilityNodeInfo()
        assertEquals("android.widget.Button", semantic.className)
        assertTrue(action.isFocusable)
        assertTrue(action.measuredHeight >= 48 * action.resources.displayMetrics.density)
        assertTrue(action.background.isStateful)
        action.isPressed = true
        assertTrue(action.background.state.contains(android.R.attr.state_pressed))
        action.isPressed = false
        action.isSelected = true
        assertTrue(action.background.state.contains(android.R.attr.state_selected))
        action.performClick(); assertEquals(1, clicks)
        action.isEnabled = false
        val disabled = action.currentTextColor
        for (event in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val touch = MotionEvent.obtain(0, 10, event, 20f, 20f, 0)
            action.dispatchTouchEvent(touch); touch.recycle()
        }
        assertEquals("disabled touch does not execute", 1, clicks)
        action.isEnabled = true
        assertNotEquals(disabled, action.currentTextColor)
        assertEquals("Lokalen Status öffnen", action.contentDescription)
        semantic.recycle()
    }

    @Test fun measuredGridReflowsAtLargeFontAndRetainsEveryAction() {
        val ctx = context(1.3f)
        val target = ContinuumBentoCockpit.column(ctx)
        val clicked = mutableListOf<Int>()
        val actions = (0..4).map { i -> ContinuumBentoCockpit.action(ctx,
            "Aktion $i: ausführlichen Trainingsnachweis anzeigen") { clicked += i } }
        ContinuumBentoCockpit.grid(ctx, target, actions, 3)
        measure(target, 320)
        val grid = target.getChildAt(0) as ViewGroup
        assertEquals("narrow dialog plus 130% font uses one column", 5, grid.childCount)
        for (action in actions) {
            assertTrue(action.measuredWidth <= target.measuredWidth)
            assertTrue(action.layout.lineCount >= 2)
            assertTrue((0 until action.layout.lineCount).all { action.layout.getEllipsisCount(it) == 0 })
        }
        measure(target, 720)
        assertEquals(2, grid.childCount)
        assertEquals(3, (grid.getChildAt(0) as ViewGroup).childCount)
        assertEquals(2, (grid.getChildAt(1) as ViewGroup).childCount)
        measure(target, 320)
        actions.forEach { it.performClick() }
        assertEquals((0..4).toList(), clicked)
    }

    @Test fun statusKeepsUnknownAndFailureTextAndDrawsAtLargeFont() {
        val ctx = context(1.3f)
        val pane = ContinuumBentoCockpit.statusPane(ctx)
        ContinuumBentoCockpit.updateStatus(pane, "Modell: unbekannt|Training: FAILED_SAVE_CYCLE|Gewichte: —")
        measure(pane, 320)
        fun texts(v: View): List<String> = if (v is TextView) listOf(v.text.toString())
            else if (v is ViewGroup) (0 until v.childCount).flatMap { texts(v.getChildAt(it)) } else emptyList()
        assertTrue(texts(pane).containsAll(listOf("unbekannt", "FAILED_SAVE_CYCLE", "—")))
        val bitmap = Bitmap.createBitmap(pane.width, pane.height, Bitmap.Config.ARGB_8888)
        pane.draw(Canvas(bitmap))
        bitmap.recycle()
    }

    @Test fun dialogThemePreservesExplicitApprovalAndCancellation() {
        val controller = Robolectric.buildActivity(android.app.Activity::class.java)
        controller.get().setTheme(R.style.AppTheme)
        val activity = controller.setup().get()
        try {
            var approved = 0
            fun dialog() = android.app.AlertDialog.Builder(activity).setTitle("Aktion freigeben")
                .setMessage("Widget-Test: Es wird keine reale Aktion ausgeführt.")
                .setPositiveButton("Freigeben") { _, _ -> approved++ }
                .setNegativeButton("Abbrechen", null).show()
            val cancelled = dialog()
            assertEquals(0, approved)
            assertEquals("Abbrechen", cancelled.getButton(-2).text)
            cancelled.getButton(-2).performClick()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(0, approved)
            assertFalse(cancelled.isShowing)
            val confirmed = dialog()
            confirmed.getButton(-1).performClick()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(1, approved)
            confirmed.dismiss()
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun glassHonoursDrawableAlphaAndControlsVisiblyChangeState() {
        val ctx = context()
        val glass = ContinuumBentoCockpit.statusPane(ctx).background
        glass.setBounds(0, 0, 240, 120)
        val hidden = Bitmap.createBitmap(240, 120, Bitmap.Config.ARGB_8888)
        glass.alpha = 0; glass.draw(Canvas(hidden))
        assertEquals(0, hidden.getPixel(120, 60))
        glass.alpha = 255; glass.draw(Canvas(hidden))
        assertTrue(android.graphics.Color.alpha(hidden.getPixel(120, 60)) > 200)
        hidden.recycle()
        val button = ContinuumBentoCockpit.action(ctx, "Trainingsnachweise öffnen", primary = true) {}
        measure(button, 280)
        val normal = capture(button, "action-normal")
        button.isPressed = true
        val pressed = capture(button, "action-pressed")
        assertFalse("pressed state must visibly differ", normal.sameAs(pressed))
        button.isPressed = false; button.isSelected = true
        val selected = capture(button, "action-selected")
        button.isSelected = false; button.isEnabled = false
        val disabled = capture(button, "action-disabled")
        assertFalse("disabled state must visibly differ", normal.sameAs(disabled))
        listOf(normal, pressed, selected, disabled).forEach { it.recycle() }
    }

    @Test fun nativePanelsAndGaugesRemainReadableAtPhoneAndFoldWidths() {
        for (width in listOf(320, 720)) {
            val ctx = context(1.3f)
            val body = ContinuumBentoCockpit.panel(ctx, "CONTINUUM · WIDGET-PRÜFUNG",
                "Lernen & Forschung", "Native Darstellung bei 130 % Schrift. Keine Geräteabnahme.")
            val status = ContinuumBentoCockpit.statusPane(ctx)
            ContinuumBentoCockpit.updateStatus(status, "Modell: unbekannt|Training: kein Messwert|Gewichte: —")
            body.addView(status)
            body.addView(ContinuumBentoCockpit.section(ctx, "AKTIONEN"))
            ContinuumBentoCockpit.grid(ctx, body, listOf(
                ContinuumBentoCockpit.action(ctx, "Trainingsnachweise öffnen", primary = true) {},
                ContinuumBentoCockpit.action(ctx, "Gespeicherte Quellen ansehen") {},
                ContinuumBentoCockpit.action(ctx, "Experiment prüfen") {},
                ContinuumBentoCockpit.action(ctx, "Auswahl löschen", critical = true) {}), 3)
            body.addView(ContinuumBentoCockpit.section(ctx, "SYSTEM · KEINE MESSWERTE"))
            val gauges = ContinuumSystemGaugesView(ctx, ContinuumDashboardState.SystemStatus())
            body.addView(gauges)
            measure(body, width)
            assertTrue(gauges.contentDescription.toString().contains("GPU: nicht messbar"))
            assertTrue(gauges.measuredHeight > 100 * ctx.resources.displayMetrics.density)
            capture(body, "panel-${width}dp-font130").recycle()
        }
    }

    private fun capture(view: View, name: String): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        System.getProperty("continuum.ui.evidenceDir")?.let { path ->
            val dir = File(path).apply { mkdirs() }
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return bitmap
    }
}
