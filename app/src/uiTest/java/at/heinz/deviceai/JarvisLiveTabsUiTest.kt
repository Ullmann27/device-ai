package at.heinz.deviceai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import at.heinz.deviceai.avatar.JarvisHoloAvatar
import at.heinz.deviceai.chief.ChiefWorkerPool
import at.heinz.deviceai.chief.TurnPhase
import at.heinz.deviceai.cockpit.JarvisAvatarSlot
import at.heinz.deviceai.ui.CockpitStatus
import at.heinz.deviceai.ui.ContinuumDashboardState
import at.heinz.deviceai.ui.ContinuumHomeView
import at.heinz.deviceai.ui.JarvisShellLayout
import at.heinz.deviceai.ui.MemoryActivitySnapshot
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
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Native input and shared-clock effects. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w692dp-h768dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class JarvisLiveTabsUiTest {
    @Before fun pauseVsync() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private inner class Fixture(val controller: ActivityController<Activity>, val viewport: ScrollView,
                                val home: ContinuumHomeView, val calls: MutableList<String>) : AutoCloseable {
        val shell get() = home.jarvisShell
        private var bitmap: Bitmap? = null
        private val width = home.resources.displayMetrics.widthPixels
        private val height = home.resources.displayMetrics.heightPixels

        fun node(tag: String): View = descendants(home).single { it.tag == tag }
        fun panel(): View = node("jarvis-live-panel")
        fun values(): String = (node("jarvis-live-panel:values") as TextView).text.toString()

        fun layout() {
            viewport.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            viewport.layout(0, 0, width, height)
        }

        fun drawRequestedFrame() {
            if (!shell.isAttachedToWindow || !shell.isShown) return
            if (home.isLayoutRequested || viewport.isLayoutRequested) layout()
            val shadow = shadowOf(shell)
            if (!shadow.wasInvalidated()) return
            shadow.clearWasInvalidated()
            val surface = bitmap ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                .also { bitmap = it }
            shell.draw(Canvas(surface))
        }

        fun advance(ms: Long) {
            var remaining = ms
            while (remaining > 0L) {
                val step = minOf(16L, remaining)
                shadowOf(Looper.getMainLooper()).idleFor(step, TimeUnit.MILLISECONDS)
                drawRequestedFrame()
                remaining -= step
            }
        }

        fun tap(tag: String) {
            JarvisHomeTestRoutes.touch(viewport, node(tag))
            advance(1L)
            layout()
            drawRequestedFrame()
        }

        fun open(key: String) {
            tap("jarvis-live-tab:$key")
            advance(400L)
            assertEquals(View.VISIBLE, panel().visibility)
            assertEquals(1f, panel().alpha, .001f)
            assertEquals(0f, panel().translationX, .001f)
        }

        fun assertClosed() {
            val panel = panel()
            assertEquals(View.GONE, panel.visibility)
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, panel.importantForAccessibility)
            val offered = ArrayList<View>().also(home::addChildrenForAccessibility)
            assertTrue("Closed contents leave accessibility", offered.none { it in descendants(panel) })
            assertTrue("Closed tabs are unselected", descendants(home).filter {
                it.tag?.toString()?.startsWith("jarvis-live-tab:") == true
            }.none { it.isSelected })
        }

        fun assertQuiet() {
            advance(64L)
            val before = shell.sampleFrames().draws
            advance(480L)
            assertEquals("Settled tabs request no extra shell frames", before, shell.sampleFrames().draws)
        }

        override fun close() { bitmap?.recycle(); bitmap = null }
    }

    private fun withHome(scale: Float = 0f, check: (Fixture) -> Unit) {
        val app = RuntimeEnvironment.getApplication()
        val resolver = app.contentResolver
        val previousScale = Settings.Global.getString(resolver, Settings.Global.ANIMATOR_DURATION_SCALE)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit().clear().commit()
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val live = ContinuumHologramView(controller.get())
        val calls = mutableListOf<String>()
        val home = ContinuumHomeView(controller.get(), live) { _, _, _ -> calls += "legacy" }
        JarvisAvatarSlot.register(JarvisHoloAvatar(live))
        home.setJarvisShellStage(true)
        home.jarvisShell.ambientMotionEnabled = false
        home.jarvisShell.frameLogging = true
        home.onNavigateTarget = { calls += it }
        val viewport = ScrollView(controller.get()).apply {
            isFillViewport = true
            addView(home, ViewGroup.LayoutParams(-1, -2))
        }
        val fixture = Fixture(controller, viewport, home, calls)
        try {
            val config = home.resources.configuration
            home.configure(config.screenWidthDp.toFloat(), config.screenWidthDp >= 600,
                windowHeightDp = config.screenHeightDp.toFloat())
            controller.get().setContentView(viewport)
            controller.visible()
            fixture.layout()
            fixture.advance(32L)
            controller.windowFocusChanged(true)
            live.setForegroundActive(true)
            home.setForegroundActive(true)
            fixture.advance(400L)
            assertTrue("Home owns a real window", home.isAttachedToWindow && home.isShown)
            assertTrue("The shared clock has focus", home.jarvisShell.hasWindowFocus())
            check(fixture)
        } finally {
            home.setForegroundActive(false)
            live.setForegroundActive(false)
            controller.pause().stop().destroy()
            fixture.close()
            JarvisAvatarSlot.register(null)
            Settings.Global.putString(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, previousScale)
            app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun assertHandles(f: Fixture) {
        val density = f.home.resources.displayMetrics.density
        assertEquals(10, JarvisHomeTestRoutes.controls(f.home).size)
        assertNull(requireNotNull(f.shell.currentShell())[JarvisShellLayout.Slot.NAV_RAIL])
        val tabs = descendants(f.home).filter { it.tag?.toString()?.startsWith("jarvis-live-tab:") == true }
        assertEquals(listOf("system", "memory", "agents"), tabs.map { it.tag.toString().substringAfter(':') })
        for (tab in tabs) {
            assertTrue("${tab.tag} retains a 48dp hit target", tab.width / density >= 48f && tab.height / density >= 48f)
            assertTrue(tab.isClickable && tab.isFocusable && tab.isEnabled)
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, tab.importantForAccessibility)
            val info = tab.createAccessibilityNodeInfo()
            try {
                assertEquals(Button::class.java.name, info.className.toString())
                assertTrue(info.isCheckable)
                assertFalse(info.contentDescription.isNullOrBlank())
            } finally { info.recycle() }
        }
        for (i in 1 until tabs.size) assertTrue("Native handles do not overlap", tabs[i - 1].bottom <= tabs[i].top)
        f.assertClosed()
        f.open("system")
        assertTrue(tabs.first().isSelected)
        f.tap("jarvis-live-panel:close")
        f.assertClosed()
        JarvisHomeTestRoutes.touch(f.viewport, JarvisHomeTestRoutes.destination(f.home, "chat"))
        f.advance(1L)
        assertEquals("Closed drawers allow underlying feature touches", listOf("chat"), f.calls)
    }

    @Test fun foldHandlesOpenAndCloseWithoutReplacingFeatureCards() = withHome { assertHandles(it) }

    @Test @Config(qualifiers = "w412dp-h915dp-port-mdpi")
    fun phoneHandlesOpenAndCloseWithoutReplacingFeatureCards() = withHome { assertHandles(it) }

    @Test fun systemValuesOnlyFollowSuppliedMeasurementsAndUnknownNeverBecomesZero() = withHome { f ->
        f.open("system")
        assertTrue(f.values().contains("App-CPU  —"))
        assertTrue(f.values().contains("Geräte-RAM  —"))
        assertTrue(f.values().contains("Akku-Temperatur  —"))
        val measured = ContinuumDashboardState(
            system = ContinuumDashboardState.SystemStatus(appCpuPercent = 37, memoryPercent = 61, temperatureC = 31.5f),
            model = ContinuumDashboardState.ModelStatus(stateLabel = "Lädt"))
        f.home.state = measured
        assertTrue(f.values().contains("App-CPU  37 %"))
        assertTrue(f.values().contains("Geräte-RAM  61 %"))
        assertTrue(f.values().contains("Akku-Temperatur  31,5 °C"))
        assertTrue(f.values().contains("Modell  Lädt"))
        val actual = f.values()
        f.advance(2_000L)
        assertEquals("Time cannot fabricate new telemetry", actual, f.values())
        assertEquals(measured, f.home.state)
        for (temperature in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            f.home.state = measured.copy(system = ContinuumDashboardState.SystemStatus(
                appCpuPercent = -1, memoryPercent = 101, temperatureC = temperature))
            assertTrue(f.values().contains("App-CPU  —"))
            assertTrue(f.values().contains("Geräte-RAM  —"))
            assertTrue(f.values().contains("Akku-Temperatur  —"))
        }
        f.home.state = measured.copy(system = ContinuumDashboardState.SystemStatus(appCpuPercent = 0, memoryPercent = 100))
        assertTrue("Measured zero differs from missing", f.values().contains("App-CPU  0 %"))
        assertTrue(f.values().contains("Geräte-RAM  100 %"))
        assertTrue("Reading values starts no action", f.calls.isEmpty())
    }

    @Test fun memoryAndAgentsUseTheirActualSnapshotAndDoNotInventActivity() = withHome { f ->
        f.open("memory")
        assertEquals(4, f.values().lineSequence().count { it.endsWith("—") })
        f.home.state = f.home.state.copy(memoryActivity = MemoryActivitySnapshot(
            newToday = 3, contextUpdates = 2, hypotheses = 1, total = 19))
        assertTrue(f.values().contains("Neue Erinnerungen (heute)  +3"))
        assertTrue(f.values().contains("Erinnerungen gesamt  19"))
        f.open("agents")
        assertTrue(f.values().contains("Aktive Assistenten  —"))
        assertTrue(f.values().contains("Kapazität  —"))
        assertTrue(f.values().contains("Ablauf  Noch kein Auftrag"))
        f.home.state = f.home.state.copy(cockpit = CockpitStatus(turnPhase = TurnPhase.WORKING,
            workerCapacity = 3, workers = listOf(
                ChiefWorkerPool.WorkerSnapshot("active", "Testauftrag", true, false, 100L, 5_000L),
                ChiefWorkerPool.WorkerSnapshot("queued", "Wartender Auftrag", false, false, 0L, 5_000L))))
        assertTrue(f.values().contains("Aktive Assistenten  1"))
        assertTrue(f.values().contains("Kapazität  3"))
        assertTrue(f.values().contains("Ablauf  Arbeitet"))
        val supplied = f.values()
        f.advance(2_000L)
        assertEquals("No clock advances work state", supplied, f.values())
        f.home.state = f.home.state.copy(cockpit = CockpitStatus(workers = emptyList(), workerCapacity = 3))
        assertTrue("Observed empty differs from unknown", f.values().contains("Aktive Assistenten  0"))
        assertTrue(f.calls.isEmpty())
    }

    @Test fun nativeDetailsAndAppearanceButtonsReachOnlyTheirDeclaredRoute() = withHome { f ->
        for (key in listOf("system", "memory", "agents")) {
            f.open(key)
            assertEquals("Appearance belongs to system", if (key == "system") View.VISIBLE else View.GONE,
                f.node("jarvis-live-panel:appearance").visibility)
            f.tap("jarvis-live-panel:open")
            f.assertClosed()
            assertEquals(key, f.calls.last())
        }
        f.open("system")
        f.tap("jarvis-live-panel:appearance")
        f.assertClosed()
        assertEquals(listOf("system", "memory", "agents", "avatar-appearance"), f.calls)
    }

    @Test fun animationAndMeasuredUpdateSettleOnTheExistingClock() = withHome(1f) { f ->
        f.assertQuiet()
        f.tap("jarvis-live-tab:system")
        f.advance(96L)
        assertTrue("Opening really moves", f.panel().alpha > 0f && f.panel().alpha < 1f)
        assertTrue(f.panel().translationX < 0f)
        f.advance(400L)
        assertEquals(1f, f.panel().alpha, .001f)
        f.assertQuiet()
        f.shell.invalidate()
        f.advance(32L)
        f.assertQuiet()
        f.home.state = f.home.state.copy(system = ContinuumDashboardState.SystemStatus(appCpuPercent = 44),
            presentation = ContinuumDashboardState.PresentationStatus(processing = true))
        val before = f.shell.sampleFrames().draws
        f.advance(128L)
        assertTrue("Changed measurements receive bounded motion", f.shell.sampleFrames().draws > before + 1)
        assertTrue(f.values().contains("App-CPU  44 %"))
        f.advance(800L)
        f.assertQuiet()
        f.home.state = f.home.state.copy(system = ContinuumDashboardState.SystemStatus(appCpuPercent = 45))
        f.advance(96L)
        f.home.state = f.home.state.copy(presentation = ContinuumDashboardState.PresentationStatus())
        f.assertQuiet()
        assertTrue("Stopping cannot invent another sample", f.values().contains("App-CPU  45 %"))
        f.home.state = f.home.state.copy()
        f.advance(32L)
        f.assertQuiet()
        f.tap("jarvis-live-panel:close")
        f.advance(96L)
        assertTrue("Closing really moves", f.panel().alpha > 0f && f.panel().alpha < 1f)
        f.advance(400L)
        f.assertClosed()
        f.assertQuiet()
    }

    @Test fun animatorScaleZeroSnapsAndDoesNotQueueAReplay() = withHome(1f) { f ->
        f.tap("jarvis-live-tab:system")
        f.advance(64L)
        val resolver = f.home.context.contentResolver
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        resolver.notifyChange(uri, null)
        f.advance(32L)
        assertEquals(1f, f.panel().alpha, .001f)
        assertEquals(0f, f.panel().translationX, .001f)
        f.assertQuiet()
        f.tap("jarvis-live-panel:close")
        f.assertClosed()
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        resolver.notifyChange(uri, null)
        f.assertQuiet()
        f.assertClosed()
    }

    @Test fun powerAndReducedMotionSettleWhileBackgroundAndDetachClose() = withHome(1f) { f ->
        val activity = f.controller.get()
        val power = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
        try {
            shadowOf(power).setIsPowerSaveMode(true)
            activity.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
            f.open("system")
            f.assertQuiet()
            f.tap("jarvis-live-panel:close")
            f.assertClosed()
            shadowOf(power).setIsPowerSaveMode(false)
            activity.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
            f.home.hologram.setAnimationsEnabled(false)
            f.open("memory")
            f.assertQuiet()
            f.home.setForegroundActive(false)
            f.home.hologram.setForegroundActive(false)
            f.controller.pause().stop()
            f.assertClosed()
            f.controller.start().resume().visible()
            f.controller.windowFocusChanged(true)
            f.home.hologram.setForegroundActive(true)
            f.home.setForegroundActive(true)
            f.layout()
            f.advance(64L)
            f.assertClosed()
            f.assertQuiet()
            f.open("agents")
            f.viewport.removeView(f.home)
            f.assertClosed()
            assertFalse(f.home.isAttachedToWindow)
            f.advance(160L)
            f.assertClosed()
        } finally {
            shadowOf(power).setIsPowerSaveMode(false)
            activity.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        }
    }
}
