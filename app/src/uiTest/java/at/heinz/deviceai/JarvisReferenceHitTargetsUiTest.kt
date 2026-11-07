package at.heinz.deviceai

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import at.heinz.deviceai.avatar.JarvisHoloAvatar
import at.heinz.deviceai.cockpit.JarvisAvatarSlot
import at.heinz.deviceai.ui.ContinuumHomeView
import at.heinz.deviceai.ui.JarvisControlState
import at.heinz.deviceai.ui.JarvisShellLayout
import at.heinz.deviceai.ui.JarvisTiles
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
import java.util.concurrent.TimeUnit

/** Native input, accessibility and resize contracts. No native Canvas, GPU or physical-device claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w1536dp-h1024dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
class JarvisReferenceHitTargetsUiTest {
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun control(home: ContinuumHomeView, key: String): View =
        descendants(home).single { it.tag == "jarvis-$key" }

    private fun size(home: ContinuumHomeView, w: Int, h: Int) {
        home.configure(w.toFloat(), w >= 600, windowHeightDp = h.toFloat())
        measureViewport(home, w, h)
    }

    private fun measureViewport(home: ContinuumHomeView, w: Int, h: Int) {
        val viewport = home.parent as android.widget.ScrollView
        viewport.layoutParams = viewport.layoutParams.apply { width = w; height = h }
        repeat(2) {
            viewport.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            viewport.layout(0, 0, w, h)
        }
        shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.MILLISECONDS)
        assertEquals("Framework parent preserves the resized window width", w, home.width)
    }

    private fun tapThroughViewport(home: ContinuumHomeView, target: View) {
        val viewport = home.parent as android.widget.ScrollView
        assertSame("Input controls remain native siblings", home, target.parent)
        assertEquals(View.VISIBLE, target.visibility)
        val contentY = home.top + target.top + target.height / 2f
        viewport.scrollTo(0, (contentY - viewport.height / 2f).toInt().coerceAtLeast(0))
        val x = home.left + target.left + target.width / 2f - viewport.scrollX
        val y = contentY - viewport.scrollY
        assertTrue("Touch must be inside the viewport: $x/$y", x >= 0f && x < viewport.width && y >= 0f && y < viewport.height)
        val downTime = SystemClock.uptimeMillis()
        for ((action, elapsed) in listOf(MotionEvent.ACTION_DOWN to 0L, MotionEvent.ACTION_UP to 24L)) {
            val event = MotionEvent.obtain(downTime, downTime + elapsed, action, x, y, 0)
            try {
                assertTrue("The actual viewport must consume ${target.tag}", viewport.dispatchTouchEvent(event))
            } finally {
                event.recycle()
            }
        }
        shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
    }

    private fun withHome(check: (ContinuumHomeView, MutableList<String>) -> Unit) {
        val app = RuntimeEnvironment.getApplication()
        val resolver = app.contentResolver
        val before = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        // Fixture-only deterministic frame scheduling; production still honours the owner's setting.
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val calls = mutableListOf<String>()
        try {
            val live = ContinuumHologramView(controller.get())
            live.setForegroundActive(false)
            val home = ContinuumHomeView(controller.get(), live) { _, _, _ -> calls += "legacy-slot" }
            JarvisAvatarSlot.register(JarvisHoloAvatar(live))
            home.setJarvisShellStage(true)
            home.jarvisShell.ambientMotionEnabled = false
            home.onNavigateTarget = { calls += it }
            val viewport = android.widget.ScrollView(controller.get()).apply {
                isFillViewport = true
                isVerticalScrollBarEnabled = false
                addView(home, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            controller.get().setContentView(viewport, ViewGroup.LayoutParams(1536, 1024))
            size(home, 1536, 1024)
            check(home, calls)
        } finally {
            controller.pause().stop().destroy()
            JarvisAvatarSlot.register(null)
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, before)
            app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test fun tenNativeTileButtonsRouteAndExposeTheirRealState() = withHome { home, calls ->
        val targets = listOf("chat", "memory", "logic", "research", "agents", "tasks", "web", "learning", "device", "system")
        assertEquals(targets, JarvisTiles.reference.map { it.target })
        for (target in targets) {
            val button = control(home, "tile:$target")
            assertTrue(button.isClickable && button.isFocusable && button.isEnabled)
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, button.importantForAccessibility)
            assertTrue(button.contentDescription.isNotEmpty())
            assertTrue("$target touch width", button.width >= 48)
            assertTrue("$target touch height", button.height >= 48)
            button.performClick()
            assertTrue(button.isSelected)
        }
        assertEquals(targets, calls)
        assertEquals(10, descendants(home).count { it.tag?.toString()?.startsWith("jarvis-tile:") == true })
    }

    @Test fun elevenRailDestinationsAndDialogHeaderActionsHaveSeparateNativeControls() = withHome { home, calls ->
        for (item in JarvisTiles.railNav) {
            val button = control(home, "rail:${item.target}")
            assertTrue(button.height >= 48)
            button.performClick()
        }
        assertEquals(JarvisTiles.railNav.map { it.target }, calls)
        calls.clear()
        val actions = listOf("voice-input", "camera-settings", "avatar-appearance")
        actions.forEach { action ->
            val button = control(home, "dialog:$action")
            assertTrue("$action size ${button.width} × ${button.height}", button.width >= 48 && button.height >= 48)
            button.performClick()
        }
        control(home, "header:notifications").performClick()
        control(home, "header:settings").performClick()
        control(home, "camera:settings").performClick()
        val modelPanel = control(home, "panel:ROW_MODEL")
        assertEquals("Lokales Modell und Gerät", modelPanel.contentDescription)
        modelPanel.performClick()
        assertEquals(actions + listOf("notifications", "settings", "camera-settings", "model-management"), calls)
        val foreground = control(home, "foreground")
        assertFalse(foreground.isClickable)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, foreground.importantForAccessibility)
        assertTrue("Live renderer below readable HUD", foreground.z > home.hologram.z)
        assertTrue("Actions above HUD", control(home, "dialog:voice-input").z > foreground.z)
    }

    @Test fun inputActionsAndConversationRouteRealTouchesAcrossWindowReflow() = withHome { home, calls ->
        val routes = linkedMapOf(
            "dialog:voice-input" to "voice-input", "dialog:camera-settings" to "camera-settings",
            "dialog:avatar-appearance" to "avatar-appearance", "input:conversation" to "chat"
        )
        val identities = routes.keys.associateWith { control(home, it).id }
        val live = home.hologram
        val fontBefore = RuntimeEnvironment.getFontScale()
        try {
            for ((width, height, font) in listOf(Triple(412, 915, 1f), Triple(884, 760, 1f),
                Triple(840, 1050, 1f), Triple(412, 915, 1.5f))) {
                RuntimeEnvironment.setFontScale(font)
                size(home, width, height)
                val shell = requireNotNull(home.jarvisShell.currentShell())
                val input = requireNotNull(shell[JarvisShellLayout.Slot.INPUT])
                val density = home.resources.displayMetrics.density
                val controls = routes.keys.map { control(home, it) }
                controls.forEach { button ->
                    assertEquals("Reflow preserves control identity", identities[button.tag.toString().removePrefix("jarvis-")], button.id)
                    assertTrue("${button.tag} remains a readable touch target", button.width / density >= 48f && button.height / density >= 48f)
                    assertTrue("${button.tag} stays inside the drawn input bar",
                        button.left >= (input.left * density).toInt() && button.right <= (input.right * density).toInt() &&
                            button.top >= (input.top * density).toInt() && button.bottom <= (input.bottom * density).toInt())
                }
                for (i in controls.indices) for (j in i + 1 until controls.size) {
                    val first = controls[i]; val second = controls[j]
                    assertFalse("Input hit targets overlap: ${first.tag}/${second.tag}",
                        first.left < second.right && second.left < first.right &&
                            first.top < second.bottom && second.top < first.bottom)
                }
                for ((key, expected) in routes) {
                    calls.clear()
                    tapThroughViewport(home, control(home, key))
                    assertEquals("$width/$height font$font $key reaches only its own route", listOf(expected), calls)
                }
                calls.clear()
                val badge = control(home, "dialog:conversation")
                assertTrue("Companion badge stays usable", badge.width / density >= 48f && badge.height / density >= 48f)
                tapThroughViewport(home, badge)
                assertEquals("Companion badge opens the real conversation", listOf("chat"), calls)
                assertSame("Input navigation preserves the live avatar", live, home.hologram)
            }
        } finally {
            RuntimeEnvironment.setFontScale(fontBefore)
        }
    }

    @Test fun movingOutsideOrCancellingTheGestureNeverNavigates() = withHome { home, calls ->
        val button = control(home, "tile:memory")
        var time = 100L
        fun event(action: Int, x: Float, y: Float) {
            val e = MotionEvent.obtain(100L, time++, action, x, y, 0)
            button.dispatchTouchEvent(e)
            e.recycle()
        }
        event(MotionEvent.ACTION_DOWN, button.width / 2f, button.height / 2f)
        event(MotionEvent.ACTION_MOVE, -100f, -100f)
        event(MotionEvent.ACTION_CANCEL, -100f, -100f)
        event(MotionEvent.ACTION_UP, -100f, -100f)
        shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.MILLISECONDS)
        assertTrue(calls.isEmpty())
        assertFalse(button.isPressed)
        assertNotEquals(JarvisControlState.PRESSED, home.jarvisShell.tiles.single { it.target == "memory" }.controlState)
    }

    @Test fun responsiveRemeasureKeepsTheSameLiveSurfaceAndReachableTenRoutes() = withHome { home, _ ->
        val live = home.hologram
        val identities = JarvisTiles.reference.associate { it.target to control(home, "tile:${it.target}").id }
        for ((w, h) in listOf(412 to 915, 760 to 884, 884 to 760, 1536 to 1024)) {
            size(home, w, h)
            val firstHeight = home.measuredHeight
            size(home, w, h)
            assertEquals("No recursive growth from scroll content", firstHeight, home.measuredHeight)
            assertSame(live, home.hologram)
            assertSame(home, live.parent)
            assertSame(live, (home.avatarRenderer as JarvisHoloAvatar).hologram)
            for ((target, id) in identities) {
                assertEquals(id, control(home, "tile:$target").id)
                assertTrue(control(home, "tile:$target").isEnabled)
            }
            if (w < 600) {
                assertTrue(home.measuredHeight > h)
                for (target in listOf("notifications", "settings")) {
                    val header = control(home, "header:$target")
                    assertEquals(48, header.width)
                    assertEquals(48, header.height)
                }
            }
        }
    }

    @Test fun heightOnlyViewportChangesPreserveChildrenAndAreIdempotent() = withHome { home, _ ->
        size(home, 884, 760)
        val surface = home.hologram
        val renderer = home.avatarRenderer
        val originalSlot = home.avatarSlotRect
        val legacyViews = at.heinz.deviceai.ui.ContinuumHomeLayout.Slot.values().mapNotNull { home.viewFor(it) }
        val tileIds = JarvisTiles.reference.associate { it.target to control(home, "tile:${it.target}").id }
        home.updateAvailableViewport(420f)
        assertEquals(420f, home.jarvisShell.viewportHeightDp, 0f)
        assertEquals(legacyViews, at.heinz.deviceai.ui.ContinuumHomeLayout.Slot.values().mapNotNull { home.viewFor(it) })
        measureViewport(home, 884, 420)
        assertTrue("Short viewport reflows into usable scrolling content", home.measuredHeight > 420)
        assertSame(surface, home.hologram)
        assertSame(renderer, home.avatarRenderer)
        for ((target, id) in tileIds) assertEquals(id, control(home, "tile:$target").id)
        assertFalse("A completed layout must settle", home.isLayoutRequested)
        home.updateAvailableViewport(420f)
        assertFalse("The same viewport must not request another layout", home.isLayoutRequested)
        home.updateAvailableViewport(760f)
        measureViewport(home, 884, 760)
        assertEquals(originalSlot, home.avatarSlotRect)
        assertSame(surface, home.hologram)
        assertSame(renderer, home.avatarRenderer)
    }

    @Test fun heightOnlyUpdateReservesTheExternalBottomBarExactlyOnce() = withHome { home, _ ->
        home.configure(412f, false, windowHeightDp = 915f, externalBottomBarHeightDp = 74f)
        measureViewport(home, 412, 841)
        assertEquals(841f, home.jarvisShell.viewportHeightDp, 0f)
        home.updateAvailableViewport(800f)
        assertEquals(726f, home.jarvisShell.viewportHeightDp, 0f)
        measureViewport(home, 412, 726)
        assertEquals(726f, home.jarvisShell.viewportHeightDp, 0f)
        home.updateAvailableViewport(800f)
        assertFalse("Repeating an inset update is idempotent", home.isLayoutRequested)
        home.updateAvailableViewport(700f)
        measureViewport(home, 412, 626)
        assertEquals(626f, home.jarvisShell.viewportHeightDp, 0f)
    }

    @Test fun unavailableSpecialActionsAreDisabledInsteadOfPretendingToStartCapture() = withHome { home, calls ->
        home.onNavigateTarget = null
        for (target in listOf("voice-input", "camera-settings", "avatar-appearance")) {
            val button = control(home, "dialog:$target")
            assertFalse(button.isEnabled)
            assertTrue(button.contentDescription.toString().contains("nicht verfügbar"))
            tapThroughViewport(home, button)
            assertTrue("A disabled action cannot fall through to conversation", calls.isEmpty())
        }
        assertTrue(control(home, "tile:chat").isEnabled)
        control(home, "tile:chat").performClick()
        assertEquals(listOf("legacy-slot"), calls)
    }
}
