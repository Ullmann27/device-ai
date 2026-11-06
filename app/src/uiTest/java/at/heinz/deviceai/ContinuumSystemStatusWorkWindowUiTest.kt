package at.heinz.deviceai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/** Host Canvas; no Filament acceptance. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ContinuumSystemStatusWorkWindowUiTest {
    private var framePump: CockpitHostFramePump? = null
    @Before fun pauseVsync() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private fun field(owner: Any, name: String): Any? = owner.javaClass
        .getDeclaredField(name).apply { isAccessible = true }.get(owner)

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
        root.measure(View.MeasureSpec.makeMeasureSpec(884, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(760, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 884, 760)
    }

    private fun draw(root: View) {
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        try { root.draw(Canvas(bitmap)) } finally { bitmap.recycle() }
    }

    private fun shown(root: View, tag: String): View {
        val matches = descendants(root).filter { it.tag == tag && it.isShown }
        assertEquals("One visible $tag belongs to the Activity", 1, matches.size)
        return matches.single()
    }

    private fun tap(root: View, view: View) {
        val bounds = Rect()
        assertTrue("Tap target is visible: ${view.tag}", view.getGlobalVisibleRect(bounds))
        assertEquals("Tap target is not vertically clipped", view.height, bounds.height())
        assertEquals("Tap target is not horizontally clipped", view.width, bounds.width())
        val at = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(at, at, MotionEvent.ACTION_DOWN,
            bounds.exactCenterX(), bounds.exactCenterY(), 0)
        val up = MotionEvent.obtain(at, at + 16L, MotionEvent.ACTION_UP,
            bounds.exactCenterX(), bounds.exactCenterY(), 0)
        try {
            assertTrue(root.dispatchTouchEvent(down))
            assertTrue(root.dispatchTouchEvent(up))
        } finally { down.recycle(); up.recycle() }
    }

    @Test fun systemTileOpensInsideCockpitWithRealStatusAndSameAvatar() {
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
            framePump = CockpitHostFramePump { controller.get().window.decorView }
            val avatar = field(activity, "hologramView") as ContinuumHologramView
            val home = field(activity, "homeView") as ContinuumHomeView
            avatar.setAnimationsEnabled(true)
            avatar.setForegroundActive(true)
            home.setForegroundActive(true)
            measure(root); advance(320L); measure(root); draw(root)
            assertTrue(home.jarvisShellStage)
            assertEquals(ContinuumWorkArea.START, field(activity, "activeWorkArea"))
            val headBounds = Rect(field(avatar, "cockpitHeadBounds") as Rect)
            val orbBounds = Rect(field(avatar, "cockpitOrbBounds") as Rect)
            assertFalse(headBounds.isEmpty)
            assertFalse(orbBounds.isEmpty)
            val beforeDialog = ShadowAlertDialog.getLatestAlertDialog()
            val beforeMotion = avatar.sampleMotion()

            tap(root, shown(root, "jarvis-tile:system"))
            advance(640L); measure(root); draw(root)
            val work = shown(root, "jarvis-work-window")
            assertSame("Opening uses the existing Activity window", root, work.rootView)
            assertSame("Systemstatus creates no AlertDialog", beforeDialog, ShadowAlertDialog.getLatestAlertDialog())
            assertSame("Opening retains the original avatar", avatar, field(activity, "hologramView"))
            assertEquals(1, descendants(root).count { it is ContinuumHologramView })
            assertTrue("Avatar stays attached and visible", avatar.isAttachedToWindow && avatar.isShown)
            assertTrue("Cockpit retains window focus", activity.hasWindowFocus())
            assertEquals(headBounds, field(avatar, "cockpitHeadBounds"))
            assertEquals(orbBounds, field(avatar, "cockpitOrbBounds"))
            assertTrue("Head and orb continue advancing", avatar.sampleMotion().callbackCount > beforeMotion.callbackCount)

            val io = field(activity, "learningIoExecutor") as ExecutorService
            io.submit {}.get(5L, TimeUnit.SECONDS)
            advance(64L); measure(root); draw(root)
            val labels = descendants(work).filterIsInstance<TextView>().map { it.text.toString() }
            assertTrue("Visible title identifies the work area", labels.any { it.equals("Systemstatus", true) })
            assertFalse("Status callback must replace loading", labels.any { it.contains("Status wird gelesen") })
            val status = labels.joinToString("\n")
            assertTrue("Real build version is present", status.contains("Device AI ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"))
            for (label in listOf("Shizuku", "Internetzugang", "Sprachmodell", "Werkzeugquittungen")) {
                assertTrue("Actual status includes $label", status.contains(label))
            }
            assertTrue("Offline model state remains truthful", status.contains("keines geladen"))
            assertFalse("Inspection starts no model task", field(activity, "busy") as Boolean)
            val close = shown(work, "jarvis-work-window:close")
            assertTrue(close.isClickable && close.isFocusable)
            assertNotNull(close.contentDescription)
            tap(root, close)
            advance(640L); measure(root); draw(root)
            assertTrue("Close removes the visible work area", descendants(root).none { it.tag == "jarvis-work-window" && it.isShown })
            assertSame(avatar, field(activity, "hologramView"))
            assertTrue(avatar.isShown)
            assertEquals(headBounds, field(avatar, "cockpitHeadBounds"))
            assertEquals(orbBounds, field(avatar, "cockpitOrbBounds"))
            assertTrue("System tile is reachable again", shown(root, "jarvis-tile:system").isClickable)
            assertEquals(ContinuumWorkArea.START, field(activity, "activeWorkArea"))
        } finally {
            framePump?.close()
            framePump = null
            ShadowAlertDialog.getLatestAlertDialog()?.takeIf { it.isShowing }?.dismiss()
            runCatching { controller.close() }
        }
    }

    /** Owner brief 05.11.2026: the same holographic window for every cockpit centre, from the tile really touched. */
    @Test fun everyCentreTileUnfoldsTheSameWorkWindowAndTheAvatarLooksAtIt() {
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
            framePump = CockpitHostFramePump { controller.get().window.decorView }
            val avatar = field(activity, "hologramView") as ContinuumHologramView
            val home = field(activity, "homeView") as ContinuumHomeView
            val director = field(activity, "avatarDirector")!!
            fun windowIntent() = director.javaClass.getDeclaredField("windowIntent").apply { isAccessible = true }.get(director)
            avatar.setAnimationsEnabled(true); avatar.setForegroundActive(true); home.setForegroundActive(true)
            measure(root); advance(320L); measure(root); draw(root)
            val headBounds = Rect(field(avatar, "cockpitHeadBounds") as Rect)
            val beforeDialog = ShadowAlertDialog.getLatestAlertDialog()
            val expected = mapOf("memory" to "Gedächtniszentrum", "web" to "Web und Quellen",
                "learning" to "Lernzentrum", "device" to "Systemstatus")
            for ((tileTarget, title) in expected) {
                val tile = shown(root, "jarvis-tile:$tileTarget")
                val tileBox = Rect().also { tile.getGlobalVisibleRect(it) }
                tap(root, tile)
                advance(640L); measure(root); draw(root)
                val work = shown(root, "jarvis-work-window")
                val labels = descendants(work).filterIsInstance<TextView>().map { it.text.toString() }
                assertTrue("$tileTarget opens $title: $labels", labels.any { it.equals(title, true) })
                assertSame("$title creates no AlertDialog", beforeDialog, ShadowAlertDialog.getLatestAlertDialog())
                val origin = field(work, "origin") as android.graphics.RectF
                assertEquals("$title emerges from the touched tile", tileBox.exactCenterX(), origin.centerX(), 1.5f)
                assertEquals(tileBox.exactCenterY(), origin.centerY(), 1.5f)
                assertTrue("the avatar glances at the window", windowIntent() != null)
                assertEquals("head stays in place", headBounds, field(avatar, "cockpitHeadBounds"))
                tap(root, shown(work, "jarvis-work-window:close"))
                advance(640L); measure(root); draw(root)
                assertTrue("$title closes", descendants(root).none { it.tag == "jarvis-work-window" && it.isShown })
                assertEquals("gaze returns to the owner", null, windowIntent())
                assertEquals(ContinuumWorkArea.START, field(activity, "activeWorkArea"))
            }
            // A centre without a tile (voice, rail) emerges beside the face, clear of it.
            AgentActivity::class.java.getDeclaredMethod("showDevelopmentCenter").apply { isAccessible = true }.invoke(activity)
            advance(640L); measure(root); draw(root)
            val work = shown(root, "jarvis-work-window")
            val origin = field(work, "origin") as android.graphics.RectF
            assertFalse("seed beside, not inside the face", android.graphics.RectF(headBounds).let { face ->
                val local = IntArray(2).also { avatar.getLocationInWindow(it) }
                face.offset(local[0].toFloat(), local[1].toFloat()); android.graphics.RectF.intersects(face, origin) })
            assertSame(beforeDialog, ShadowAlertDialog.getLatestAlertDialog())
        } finally {
            framePump?.close()
            framePump = null
            ShadowAlertDialog.getLatestAlertDialog()?.takeIf { it.isShowing }?.dismiss()
            runCatching { controller.close() }
        }
    }

    /** Owner brief: a real tool run flies in beside the face, shows only the runner's state, then folds back. */
    @Test fun toolRunUnfoldsBesideTheFaceShowsRealStateAndReturnsTheGaze() {
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
            framePump = CockpitHostFramePump { controller.get().window.decorView }
            val avatar = field(activity, "hologramView") as ContinuumHologramView
            val home = field(activity, "homeView") as ContinuumHomeView
            val director = field(activity, "avatarDirector")!!
            fun windowIntent() = director.javaClass.getDeclaredField("windowIntent").apply { isAccessible = true }.get(director)
            avatar.setAnimationsEnabled(true); avatar.setForegroundActive(true); home.setForegroundActive(true)
            measure(root); advance(320L); measure(root); draw(root)
            fun call(name: String, vararg args: Any) = AgentActivity::class.java.declaredMethods.single { it.name == name }
                .apply { isAccessible = true }.invoke(activity, *args)
            for ((answered, expected) in listOf(true to "Das Werkzeug hat geantwortet", false to "Werkzeug nicht abgeschlossen")) {
                call("presentToolScene", "call-$answered", "get_storage_info")
                advance(640L); measure(root); draw(root)
                val work = shown(root, "jarvis-work-window")
                val labels = { descendants(work).filterIsInstance<TextView>().map { it.text.toString() } }
                assertTrue(labels().toString(), labels().any { it.startsWith("WERKZEUG · ") })
                assertTrue("only the runner state, no result claim", labels().any { it.startsWith("Läuft") })
                assertTrue("the avatar glances at it", windowIntent() != null)
                call("finishToolScene", "stale-call", true)
                assertTrue("a stale call changes nothing", labels().any { it.startsWith("Läuft") })
                call("finishToolScene", "call-$answered", answered)
                assertTrue(labels().toString(), labels().any { it.startsWith(expected) })
                advance(1_600L + 640L); measure(root); draw(root)
                assertTrue("folds back after the hold", descendants(root).none { it.tag == "jarvis-work-window" && it.isShown })
                assertEquals("gaze returns to the owner", null, windowIntent())
            }
            assertFalse("inspection starts no model task", field(activity, "busy") as Boolean)
        } finally {
            framePump?.close(); framePump = null
            runCatching { controller.close() }
        }
    }

    /** Owner brief 06.11.2026 (Airbus flight deck): the deck makes room for the work window instead of being covered. */
    @Test fun openWindowClearsTheDeckAndItReturnsAfterClosing() {
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
            framePump = CockpitHostFramePump { controller.get().window.decorView }
            val avatar = field(activity, "hologramView") as ContinuumHologramView
            val home = field(activity, "homeView") as ContinuumHomeView
            avatar.setAnimationsEnabled(true); avatar.setForegroundActive(true); home.setForegroundActive(true)
            measure(root); advance(320L); measure(root); draw(root)
            fun deck() = descendants(root).count { v -> v.isShown && v.tag?.toString()?.let {
                it.startsWith("jarvis-tile:") || it.startsWith("jarvis-panel:ROW_") } == true }
            val before = deck()
            assertTrue("tiles and rows are on the deck: $before", before >= 12)
            tap(root, shown(root, "jarvis-tile:memory"))
            advance(640L); measure(root); draw(root)
            shown(root, "jarvis-work-window")
            assertEquals("no tile or row stays under the open window", 0, deck())
            assertTrue("avatar stays", avatar.isShown)
            assertTrue("dialog stays", shown(root, "jarvis-dialog:conversation").isShown)
            tap(root, shown(shown(root, "jarvis-work-window"), "jarvis-work-window:close"))
            advance(640L + 480L); measure(root); draw(root)
            assertTrue(descendants(root).none { it.tag == "jarvis-work-window" && it.isShown })
            assertEquals("the deck returns after the window folded back", before, deck())
        } finally {
            framePump?.close(); framePump = null
            ShadowAlertDialog.getLatestAlertDialog()?.takeIf { it.isShowing }?.dismiss()
            runCatching { controller.close() }
        }
    }
}
