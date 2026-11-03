package at.heinz.deviceai

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import at.heinz.deviceai.ui.ContinuumHomeLayout
import at.heinz.deviceai.ui.*
import at.heinz.deviceai.ui.ContinuumHomeView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Real Android Canvas of the existing home widget. No device, model or GPU claims. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContinuumAdaptiveCockpitUiTest {
    @Test fun enlargedActionLabelsLeaveSpaceBelowTheirIcons() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val painter = AndroidContinuumPainter(controller.get().assets).apply { begin(Canvas(bitmap)) }
        try {
            for (font in listOf(1f, 1.3f, 1.6f, 2f)) {
                val layout = ContinuumAdaptiveHomeLayout.create(412f, 915f, false, font)
                val elements = ContinuumHomeScreen.elements(layout, font)
                for ((slot, element) in elements) {
                    if (element !is CockpitActionTile) continue
                    val box = requireNotNull(layout[slot])
                    var labelMeasured = false
                    val measured = object : ContinuumPainter by painter {
                        override fun drawText(text: String, x: Float, y: Float, style: TextStyle, fill: Fill,
                                              align: TextAlign, alpha: Float) {
                            if (!labelMeasured) {
                                // Existing 23dp icon, centered at y=23, including its 1.5dp stroke.
                                val iconBottom = 23f + 23f / 2f + 1.5f / 2f
                                val gap = y - painter.capHeight(style) - iconBottom
                                assertTrue("$slot at font $font leaves only $gap dp below its icon", gap >= 3f)
                                labelMeasured = true
                            }
                            painter.drawText(text, x, y, style, fill, align, alpha)
                        }
                    }
                    element.draw(measured, box.width, box.height, ContinuumDashboardState())
                    assertTrue("$slot must render its action label", labelMeasured)
                }
            }
        } finally {
            bitmap.recycle()
            controller.pause().stop().destroy()
        }
    }

    @Test fun renderPhoneFoldAndTabletAtNormalAndLargeFonts() {
        for ((width, height) in listOf(320 to 720, 412 to 915, 600 to 840, 840 to 960, 1280 to 800)) {
            for (font in listOf(1f, 1.3f, 1.6f)) {
                val controller = Robolectric.buildActivity(Activity::class.java).setup()
                val activity = controller.get()
                val configuration = android.content.res.Configuration(activity.resources.configuration).apply { fontScale = font }
                val context = activity.createConfigurationContext(configuration)
                val orb = ContinuumHologramView(context).apply {
                    setNativeHeadEnabled(false)
                    setAnimationsEnabled(false)
                }
                val clicked = mutableListOf<ContinuumHomeLayout.Slot>()
                val home = ContinuumHomeView(context, orb) { slot, _, _ -> clicked += slot }
                try {
                    home.configure(width.toFloat(), ContinuumHomeLayout.isWide(width.toFloat(), height.toFloat()),
                        windowHeightDp = height.toFloat())
                    activity.setContentView(home)
                    home.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST))
                    home.layout(0, 0, width, home.measuredHeight)
                    assertTrue(home.height > 0)
                    val bitmap = Bitmap.createBitmap(width, home.height, Bitmap.Config.ARGB_8888)
                    try {
                        home.draw(Canvas(bitmap))
                        System.getProperty("continuum.ui.evidenceDir")?.let { dir ->
                            File(dir).mkdirs()
                            File(dir, "cockpit-${width}x$height-font$font.png").outputStream().use {
                                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                            }
                        }
                    } finally { bitmap.recycle() }
                    for (index in 0 until home.childCount) {
                        val child = home.getChildAt(index)
                        if (child is ContinuumHomeView.ElementView) {
                            assertTrue("${child.slot} has spoken real state", child.contentDescription.isNotBlank())
                            child.performClick()
                            assertEquals(child.slot, clicked.last())
                        }
                    }
                } finally { controller.pause().stop().destroy() }
            }
        }
    }
    @Test fun largeTypeAndRealLongValuesStayInsideEachCardWithoutTextCollisions() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val painter = AndroidContinuumPainter(activity.get().assets).apply { begin(Canvas(bitmap)) }
        val state = ContinuumDashboardState(
            model = ContinuumDashboardState.ModelStatus(name = "Lokales Modell mit einem sehr langen Namen", online = true,
                stateLabel = "Bereit", qualityPercent = 73, latencyMs = 1987, context = "8 Gesprächszüge"),
            learning = ContinuumDashboardState.LearningStatus(today = 17, recent = listOf(
                ContinuumDashboardState.Event("Bestätigtes Lernergebnis mit längerem Titel", "vor 2 Std."))),
            development = ContinuumDashboardState.DevelopmentStatus(activeProjects = 4, runningTasks = 2, errors = 1,
                projects = listOf(ContinuumDashboardState.Project("Ausführlicher Auftragstitel", "Prüfung läuft",
                    ContinuumDashboardState.ProjectState.RUNNING, "gerade eben"))))
        try {
            for ((width, height) in listOf(320f to 720f, 412f to 915f, 600f to 840f, 840f to 960f, 1280f to 800f)) {
                for (font in listOf(1f, 1.3f, 1.6f, 2f)) {
                    val layout = ContinuumAdaptiveHomeLayout.create(width, height, width >= 600f, font)
                    for ((slot, element) in ContinuumHomeScreen.elements(layout, font)) {
                        if (slot == ContinuumHomeLayout.Slot.NAV || slot == ContinuumHomeLayout.Slot.RAIL) continue
                        val box = requireNotNull(layout[slot])
                        val recorded = mutableListOf<Pair<String, android.graphics.RectF>>()
                        val measured = object : ContinuumPainter by painter {
                            override fun drawText(text: String, x: Float, y: Float, style: TextStyle, fill: Fill,
                                                  align: TextAlign, alpha: Float) {
                                val textWidth = painter.measureText(text, style)
                                val left = when (align) { TextAlign.LEFT -> x; TextAlign.CENTER -> x - textWidth / 2f; TextAlign.RIGHT -> x - textWidth }
                                val bounds = android.graphics.RectF(left, y - painter.capHeight(style), left + textWidth, y + style.size * .25f)
                                assertTrue("$width/$font/$slot '$text' inside $box: $bounds",
                                    bounds.left >= -.1f && bounds.top >= -.1f && bounds.right <= box.width + .1f && bounds.bottom <= box.height + .1f)
                                for ((other, area) in recorded) {
                                    assertFalse("$width/$font/$slot '$text' collides with '$other'", android.graphics.RectF.intersects(bounds, area))
                                }
                                recorded += text to bounds
                                painter.drawText(text, x, y, style, fill, align, alpha)
                            }
                        }
                        element.draw(measured, box.width, box.height, state)
                    }
                }
            }
        } finally { bitmap.recycle(); activity.pause().stop().destroy() }
    }

}
