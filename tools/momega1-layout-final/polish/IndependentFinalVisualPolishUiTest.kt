package at.heinz.deviceai

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.TextView
import at.heinz.deviceai.ui.ContinuumHomeView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w840dp-h1050dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IndependentFinalVisualPolishUiTest {
    private fun field(a: AgentActivity, name: String, value: Any?) = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.set(a, value)
    private fun refresh(a: AgentActivity) = AgentActivity::class.java
        .getDeclaredMethod("refreshWorkPresentation").apply { isAccessible = true }.invoke(a)
    @Test fun actualIdleWorkStatusDoesNotOccupyDashboardSpace() {
        val a = Robolectric.buildActivity(AgentActivity::class.java).get()
        val label = TextView(a)
        field(a, "workStatusView", label)
        refresh(a)
        assertEquals("Idle diagnostics must not add a band before navigation", View.GONE, label.visibility)
    }
    @Test fun actualActiveAndBlockedWorkStatusRemainVisible() {
        val a = Robolectric.buildActivity(AgentActivity::class.java).get()
        val label = TextView(a)
        field(a, "workStatusView", label)
        field(a, "busy", true); refresh(a)
        assertEquals(View.VISIBLE, label.visibility)
        assertTrue(label.text.toString().contains("Antwort wird erarbeitet"))
        field(a, "busy", false); field(a, "workOutcome", "BLOCKED"); refresh(a)
        assertEquals(View.VISIBLE, label.visibility)
        assertTrue(label.text.toString().contains("blockiert"))
        field(a, "workOutcome", null); refresh(a)
        field(a, "busy", true); refresh(a)
        assertEquals("Activity returns from idle to a visible real work state", View.VISIBLE, label.visibility)
    }
    @Test fun portraitPaintsOuterRingsWithoutMovingHeroOrControls() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val head = ContinuumHologramView(controller.get()).apply {
            setNativeHeadEnabled(false); setAnimationsEnabled(false)
        }
        val home = ContinuumHomeView(controller.get(), head) { _, _, _ -> }
        val frames = mutableListOf<Bitmap>()
        fun frame(name: String): Bitmap {
            val image = Bitmap.createBitmap(840, 1050, Bitmap.Config.ARGB_8888)
            home.draw(Canvas(image)); frames += image
            System.getProperty("continuum.polish.evidenceDir")?.let { path ->
                val out = File(path).apply { mkdirs() }
                File(out, "$name.png").outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            }
            return image
        }
        try {
            home.configure(840f, true, windowHeightDp = 1050f)
            controller.get().setContentView(home)
            fun exact(n: Int) = View.MeasureSpec.makeMeasureSpec(n, View.MeasureSpec.EXACTLY)
            home.measure(exact(840), exact(1050)); home.layout(0, 0, 840, 1050)
            assertEquals(240, head.left); assertEquals(600, head.right)
            assertEquals(60, head.top); assertEquals(343, head.height)
            val controls = (0 until home.childCount).map { home.getChildAt(it) }.filter { it !== head }
            val bounds = controls.map { listOf(it.left, it.top, it.right, it.bottom) }
            val visible = frame("portrait-orb-visible")
            head.visibility = View.INVISIBLE
            val hidden = frame("portrait-orb-hidden-control")
            var outerPixels = 0
            for (y in head.top until head.bottom) for (x in 0 until 840) {
                if (x >= head.left && x < head.right) continue
                if (visible.getPixel(x, y) != hidden.getPixel(x, y)) outerPixels++
            }
            println("NATIVE_OUTER_RING_PIXELS=$outerPixels hero=${head.left},${head.top},${head.width},${head.height}")
            assertTrue("Actual ring/glow pixels are clipped at the hero rectangle ($outerPixels)", outerPixels > 200)
            assertEquals(bounds, controls.map { listOf(it.left, it.top, it.right, it.bottom) })
        } finally {
            frames.forEach { it.recycle() }
            controller.pause().stop().destroy()
        }
    }
}
