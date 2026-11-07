package at.heinz.deviceai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import at.heinz.deviceai.avatar.JarvisHoloAvatar
import at.heinz.deviceai.cockpit.CockpitAnimationBus
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.MEMORY_TURN
import at.heinz.deviceai.ui.ContinuumHomeView
import at.heinz.deviceai.ui.ContinuumHologramRenderer
import at.heinz.deviceai.ui.JarvisSequenceView
import at.heinz.deviceai.ui.video3.Video3MemoryScene
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration

/** Actual Activity; synthetic memory metadata. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class FullStageMemoryReviewUiTest {
    private data class Fixture(val activity: AgentActivity, val root: View, val home: ContinuumHomeView,
                               val live: ContinuumHologramView, val bus: CockpitAnimationBus) {
        val sequence get() = (home.avatarRenderer as JarvisHoloAvatar).sequence!!
        val stage get() = home.findViewWithTag<View>("continuum-memory-fullstage")!!
    }

    private var pump: CockpitHostFramePump? = null

    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(owner)

    private fun call(activity: AgentActivity, name: String) = AgentActivity::class.java.getDeclaredMethod(name)
        .apply { isAccessible = true }.invoke(activity)

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun advance(ms: Long) {
        var remaining = ms
        while (remaining > 0L) {
            val step = minOf(16L, remaining)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(step))
            pump?.drawRequestedFrame()
            remaining -= step
        }
    }

    private fun frame(view: View): Bitmap {
        view.viewTreeObserver.dispatchOnPreDraw()
        return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    private fun emit(f: Fixture, event: CockpitAnimationEvent) {
        f.bus.emit(event)
        shadowOf(Looper.getMainLooper()).idle()
        advance(32L)
    }

    private fun recall(count: Int = 3, turn: String = MEMORY_TURN) =
        CockpitAnimationEvent.RecallHits(turn, System.currentTimeMillis(), "a".repeat(64), count, if (count > 0) .8f else null)

    private fun document(count: Int = 2, turn: String = MEMORY_TURN) =
        CockpitAnimationEvent.DocumentLearned(turn, System.currentTimeMillis(), "synthetic-review", "PDF", count)

    private fun withActivity(check: (Fixture) -> Unit) {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
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
            controller.setup().visible(); advance(32L); controller.windowFocusChanged(true)
            val activity = controller.get()
            val root = activity.window.decorView
            val density = activity.resources.displayMetrics.density
            root.measure(View.MeasureSpec.makeMeasureSpec((884 * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((760 * density).toInt(), View.MeasureSpec.EXACTLY))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            root.dispatchWindowFocusChanged(true)
            frame(root).recycle()
            advance(6_000L)
            val home = field(activity, "homeView") as ContinuumHomeView
            val live = field(activity, "hologramView") as ContinuumHologramView
            assertTrue(home.ownsAvatarSlot && home.isShown && live.parent === home)
            assertNotNull(home.avatarRenderer)
            call(activity, "cancelLocalRequest")
            frame(root).recycle()
            check(Fixture(activity, root, home, live, field(activity, "cockpitBus") as CockpitAnimationBus))
        } finally {
            pump?.close(); pump = null
            controller.pause().stop().destroy()
        }
    }

    private fun tap(root: View, view: View) {
        JarvisHomeTestRoutes.scrollIntoView(view)
        advance(32L)
        JarvisHomeTestRoutes.scrollIntoView(view)
        val bounds = JarvisHomeTestRoutes.visibleBounds(view)
        val position = IntArray(2).also(root::getLocationOnScreen)
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN,
            bounds.exactCenterX() - position[0], bounds.exactCenterY() - position[1], 0)
        val up = MotionEvent.obtain(now, now + 16L, MotionEvent.ACTION_UP,
            bounds.exactCenterX() - position[0], bounds.exactCenterY() - position[1], 0)
        try { assertTrue(root.dispatchTouchEvent(down)); assertTrue(root.dispatchTouchEvent(up)) }
        finally { down.recycle(); up.recycle() }
    }

    private fun exportEpisode(f: Fixture, productionClock: () -> Long) {
        val directory = System.getProperty("continuum.ui.evidenceDir") ?: return
        f.sequence.clock = productionClock
        emit(f, document())
        val out = java.io.File(directory, "fullstage-native-activity").apply { mkdirs() }
        repeat(24) { index ->
            if (index > 0) advance(220L)
            frame(f.root).also { bitmap ->
                out.resolve("frame-${index.toString().padStart(2, '0')}.png").outputStream().use {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                bitmap.recycle()
            }
        }
        out.resolve("capture.json").writeText("""{"kind":"ANDROID_NATIVE_CANVAS_HOST","syntheticMemoryMetadata":true,"physicalDeviceTested":false,"filamentGpuPixelsTested":false,"frames":24,"sampleIntervalMs":220,"durationSampledMs":5060}""")
        assertFalse("The exported episode finishes", f.live.memoryScene.isActive)
    }

    @Test fun actualActivityMemoryBusEnlargesAndReturnsTheOriginalOrb() = withActivity { f ->
        val original = field(f.live, "renderer") as ContinuumHologramRenderer
        val geometry = original.geometry
        val local = RectF(field(f.live, "orbDrawBounds") as RectF)
        val productionClock = f.sequence.clock
        var now = 1_000L
        f.sequence.clock = { now }
        val rest = frame(f.root)
        emit(f, document())
        assertTrue("The Activity bus reaches the full scene", f.live.memoryScene.isActive)
        assertEquals(f.home.width, f.stage.width)
        assertTrue("The scene exceeds the old avatar slot", f.stage.height > f.live.height)
        now += 900L
        val flight = frame(f.root)
        assertFalse("The actual Android surface changes", rest.sameAs(flight))
        now = 3_800L
        frame(f.root).recycle()
        val large = RectF(f.live.memorySceneOrbBounds)
        assertTrue("The original orb is substantially enlarged", large.width() > local.width() * 1.5f)
        assertTrue("The sphere reaches the central stage", large.centerX() in f.home.width * .4f..f.home.width * .6f)
        assertSame(original, field(f.live, "renderer"))
        assertEquals(geometry, original.geometry)
        now = 1_000L + Video3MemoryScene.DURATION_MS + 1L
        frame(f.root).recycle()
        assertFalse(f.live.memoryScene.isActive)
        assertTrue("No stale large-orb bounds remain", f.live.memorySceneOrbBounds.isEmpty)
        val density = f.live.resources.displayMetrics.density
        assertEquals("Original local width is restored", local.width() / density, field(original, "w") as Float, .01f)
        assertEquals("Original local height is restored", local.height() / density, field(original, "h") as Float, .01f)
        rest.recycle(); flight.recycle()
        exportEpisode(f, productionClock)
    }

    @Test fun falseSignalsAndAnEventFloodCannotInventOrExtendTheEpisode() = withActivity { f ->
        var now = 1_000L
        f.sequence.clock = { now }
        listOf(document(0), recall(0), document(2, "wrong-turn"), recall(3, "wrong-turn"),
            CockpitAnimationEvent.MemoryStored(MEMORY_TURN, 1L, "synthetic-entry", "CONFIRMED", 4))
            .forEach { emit(f, it); assertFalse("No false fullstage for $it", f.live.memoryScene.isActive) }
        emit(f, recall())
        assertTrue(f.live.memoryScene.isActive)
        repeat(6) {
            now += 600L
            emit(f, if (it % 2 == 0) document() else recall())
            frame(f.root).recycle()
        }
        now = 1_000L + Video3MemoryScene.DURATION_MS + 1L
        frame(f.root).recycle()
        assertFalse("Incoming events never extend the active episode", f.live.memoryScene.isActive)
        emit(f, recall())
        assertTrue("A fresh later fact may start anew", f.live.memoryScene.isActive)
    }

    @Test fun realStopRejectsQueuedMemoryAndChatReparentingCannotReplay() = withActivity { f ->
        Thread { f.bus.emit(document()) }.apply { start(); join() }
        call(f.activity, "cancelLocalRequest")
        shadowOf(Looper.getMainLooper()).idle()
        advance(32L)
        assertFalse("STOP retires already queued presentation", f.live.memoryScene.isActive)
        emit(f, recall())
        assertTrue(f.live.memoryScene.isActive)
        val oldSequence = f.sequence
        call(f.activity, "focusChatComposer")
        assertFalse("Moving the live head to CHAT cancels the scene", f.live.memoryScene.isActive)
        assertFalse(f.live.parent === f.home)
        oldSequence.render(document())
        shadowOf(Looper.getMainLooper()).idle()
        call(f.activity, "showHomeDashboard")
        frame(f.root).recycle()
        assertFalse("Old home callbacks cannot replay", f.live.memoryScene.isActive)
        assertSame(f.home, f.live.parent)
        emit(f, document())
        assertTrue("The re-adopted live surface remains usable", f.live.memoryScene.isActive)
        f.live.setAnimationsEnabled(false)
        assertFalse(f.live.memoryScene.isActive)
        f.live.setAnimationsEnabled(true)
        advance(5_000L)
        assertFalse("Reduced motion does not defer a replay", f.live.memoryScene.isActive)
    }

    @Test fun actualWindowCloseAndSystemTapRemainUsableAboveTheScene() = withActivity { f ->
        pump = CockpitHostFramePump { f.root }
        fun system() = JarvisHomeTestRoutes.destination(f.root, "system")
        tap(f.root, system())
        advance(1_800L)
        val window = descendants(f.root).single { it.tag == "jarvis-work-window" && it.isShown }
        emit(f, recall())
        advance(2_600L)
        assertTrue("Actual memory scene runs behind the window", f.live.memoryScene.isActive)
        assertTrue(window.isShown)
        val close = descendants(window).single { it.contentDescription == "Arbeitsfenster schließen" }
        tap(f.root, close)
        advance(850L)
        assertFalse("Actual close target receives its tap", window.isShown)
        tap(f.root, system())
        advance(100L)
        assertTrue("Actual system control still opens its window", descendants(f.root)
            .any { it.tag == "jarvis-work-window" && it.isShown })
    }

    @Test
    @Config(qualifiers = "w884dp-h760dp-land-hdpi")
    fun actualActivityNoticeApiStaysVisibleAndDismissibleDuringMemoryFlight() = withActivity { f ->
        var now = 1_000L
        f.sequence.clock = { now }
        emit(f, document())
        now += 2_800L
        frame(f.root).recycle()
        assertTrue(f.live.memoryScene.isActive)
        f.home.showTransientCard("Synthetischer Hinweis", listOf("2 + 2 = 4"), animate = true)
        advance(32L)
        val delivered = field(f.home, "transientCard") as? View
        assertNotNull("The public notice API adds its view", delivered)
        val card = delivered!!
        f.root.measure(View.MeasureSpec.makeMeasureSpec(f.root.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(f.root.height, View.MeasureSpec.EXACTLY))
        f.root.layout(0, 0, f.root.measuredWidth, f.root.measuredHeight)
        assertTrue("The actual shell lays out its notice", card.width > 0 && card.height > 0)

        advance(80L)
        assertTrue("The real entrance is still moving", card.translationX > 0f)
        assertTrue("The real entrance is fading in", card.alpha > 0f && card.alpha < 1f)
        val animated = frame(f.root)
        card.visibility = View.INVISIBLE
        val withoutCard = frame(f.root)
        card.visibility = View.VISIBLE
        val visible = Rect()
        assertTrue(card.getGlobalVisibleRect(visible))
        val rootPosition = IntArray(2).also(f.root::getLocationOnScreen)
        visible.offset(-rootPosition[0], -rootPosition[1])
        assertTrue(visible.intersect(0, 0, animated.width, animated.height))
        var changed = 0
        for (y in visible.top until visible.bottom) for (x in visible.left until visible.right) {
            if (animated.getPixel(x, y) != withoutCard.getPixel(x, y)) changed++
        }
        animated.recycle(); withoutCard.recycle()
        assertTrue("The moving notice contributes visible native pixels", changed > 0)

        advance(320L)
        assertEquals(0f, card.translationX, .001f)
        assertEquals(1f, card.alpha, .001f)
        val landed = frame(f.root)
        val sceneAlpha = f.stage.alpha
        f.stage.alpha = 0f
        val unobscured = try { frame(f.root) } finally { f.stage.alpha = sceneAlpha }
        assertTrue("Reference capture preserves the active scene", f.live.memoryScene.isActive)
        val notice = frame(card)
        val origin = IntArray(2).also(card::getLocationOnScreen)
        origin[0] -= rootPosition[0]; origin[1] -= rootPosition[1]
        var opaqueTextPixels = 0
        try {
            for (y in 0 until notice.height) for (x in 0 until notice.width) {
                val pixel = notice.getPixel(x, y)
                if (pixel != at.heinz.deviceai.ui.JarvisDesign.Colors.cyan &&
                    pixel != at.heinz.deviceai.ui.JarvisDesign.Colors.text) continue
                val screenX = origin[0] + x; val screenY = origin[1] + y
                if (screenX !in 0 until landed.width || screenY !in 0 until landed.height) continue
                if (unobscured.getPixel(screenX, screenY) != pixel) continue
                opaqueTextPixels++
                assertEquals("Notice text remains visible above the memory scene at $x,$y", pixel,
                    landed.getPixel(screenX, screenY))
            }
            assertTrue("The native notice contains actual text", opaqueTextPixels > 0)
        } finally { landed.recycle(); unobscured.recycle(); notice.recycle() }
        assertTrue(f.live.memoryScene.isActive)
        tap(f.root, card)
        advance(16L)
        assertNull("The actual notice receives its dismissal tap", field(f.home, "transientCard"))
        assertTrue("Dismissing the notice preserves the scene", f.live.memoryScene.isActive)
    }

    @Test
    @Config(qualifiers = "w884dp-h760dp-land-hdpi")
    fun theEntireActualControlRectangleStaysOutsideTheCompositor() = withActivity { f ->
        var now = 1_000L
        f.sequence.clock = { now }
        emit(f, document())
        now += 2_800L
        val bitmap = frame(f.stage)
        @Suppress("UNCHECKED_CAST")
        val controls = (field(f.home, "shellActions") as Map<String, View>).values
        var inspected = 0
        try {
            for (control in controls.filter { it.isShown && it.width > 0 && it.height > 0 }) {
                val box = Rect(control.left, control.top, control.right, control.bottom)
                if (!box.intersect(0, 0, bitmap.width, bitmap.height)) continue
                inspected++
                for (y in box.top until box.bottom) for (x in box.left until box.right) {
                    assertEquals("Full target remains clear: ${control.tag} at $x,$y", 0,
                        Color.alpha(bitmap.getPixel(x, y)))
                }
                assertTrue("Hit target draws above the cinematic layer", control.z > f.stage.z)
            }
            assertTrue("Real action controls were inspected", inspected >= 3)
            assertFalse(f.stage.isClickable || f.stage.isFocusable)
        } finally { bitmap.recycle() }
    }
}
