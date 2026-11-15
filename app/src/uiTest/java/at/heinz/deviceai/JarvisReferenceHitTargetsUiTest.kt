package at.heinz.deviceai

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.Button
import android.widget.EditText
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
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowDisplayManager
import org.robolectric.shadows.ShadowToast
import org.robolectric.shadows.ShadowViewRootImpl
import java.util.concurrent.ExecutorService
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
        val viewport = home.parent as ScrollView
        assertTrue("The target belongs to this Home", descendants(home).contains(target))
        assertEquals(View.VISIBLE, target.visibility)
        JarvisHomeTestRoutes.touch(viewport, target)
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
            home.onNavigateTarget = { target ->
                calls += target
                // Native-only files/settings are selected by AgentActivity's navigator, not a legacy slot.
                if (JarvisTiles.bottomNav.any { it.target == target }) home.selectJarvisTarget(target)
            }
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

    @Test fun compactCinemaDockRoutesAndExposesRealState() = withHome { home, calls ->
        size(home, 412, 915)
        val targets = JarvisHomeTestRoutes.dockTargets
        assertEquals(JarvisHomeTestRoutes.features, JarvisTiles.reference.map { it.target })
        assertFalse("Compact Cinema uses a tool dock, not the retired tile grid",
            requireNotNull(home.jarvisShell.currentShell()).hasFeatureTiles)
        assertEquals(JarvisHomeTestRoutes.expectedDirectTargets(home),
            JarvisHomeTestRoutes.controls(home).map { it.tag.toString().substringAfter(':') })
        for (target in targets) {
            val button = control(home, "bottom:$target")
            assertTrue(button.isClickable && button.isFocusable && button.isEnabled)
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, button.importantForAccessibility)
            assertTrue(button.contentDescription.isNotEmpty())
            assertTrue("$target touch width", button.width >= 48)
            assertTrue("$target touch height", button.height >= 48)
            tapThroughViewport(home, button)
            assertTrue(button.isSelected)
        }
        assertEquals(targets, calls)
        assertEquals(0, descendants(home).count { it.tag?.toString()?.startsWith("jarvis-tile:") == true })
        assertEquals(JarvisTiles.bottomNav.size,
            descendants(home).count { it.tag?.toString()?.startsWith("jarvis-bottom:") == true })
    }

    @Test fun wideCardsAndInputHeaderActionsHaveSeparateNativeControls() = withHome { home, calls ->
        assertEquals(JarvisHomeTestRoutes.expectedDirectTargets(home),
            JarvisHomeTestRoutes.controls(home).map { it.tag.toString().substringAfter(':') })
        for (target in JarvisHomeTestRoutes.expectedDirectTargets(home)) {
            val button = JarvisHomeTestRoutes.destination(home, target)
            assertTrue(button.width >= 48 && button.height >= 48)
            tapThroughViewport(home, button)
            assertTrue(button.isSelected)
        }
        assertEquals(JarvisHomeTestRoutes.expectedDirectTargets(home), calls)
        calls.clear()
        val actions = linkedMapOf("header:chats" to "chats", "header:settings" to "settings",
            "header:more" to "more")
        actions.forEach { (action, _) ->
            val button = control(home, action)
            assertTrue("$action size ${button.width} × ${button.height}", button.width >= 48 && button.height >= 48)
            tapThroughViewport(home, button)
        }
        val modelPanel = descendants(home).singleOrNull { it.tag == "jarvis-panel:ROW_MODEL" }
        if (modelPanel != null) {
            assertEquals("Lokales Modell und Gerät", modelPanel.contentDescription)
            tapThroughViewport(home, modelPanel)
            assertEquals(actions.values.toList() + "model-management", calls)
        } else {
            assertEquals(actions.values.toList(), calls)
        }
        assertTrue("Camera settings remain in the existing More navigation",
            ContinuumWorkAreaNavigationSpec.moreEntries.any { it.target == "camera-settings" })
        assertTrue("The real Activity composer has no duplicate Home hit targets", descendants(home).none {
            it.tag?.toString() in setOf("jarvis-dialog:voice-input", "jarvis-dialog:send", "jarvis-input:conversation")
        })
        val foreground = control(home, "foreground")
        assertFalse(foreground.isClickable)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, foreground.importantForAccessibility)
        assertTrue("Live renderer below readable HUD", foreground.z > home.hologram.z)
        assertTrue("Actions above HUD", control(home, "header:chats").z > foreground.z)
    }

    private fun field(a: AgentActivity, name: String): Any? = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(a)

    private fun set(a: AgentActivity, name: String, value: Any?) = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.set(a, value)

    private fun call(a: AgentActivity, name: String, vararg args: Any?): Any? =
        AgentActivity::class.java.declaredMethods.single { it.name == name && it.parameterCount == args.size }
            .apply { isAccessible = true }.invoke(a, *args)

    /** Same real-pane host as CompactComposerUiTest; every production listener stays installed. */
    private fun withComposer(check: (AgentActivity, Activity, View, ContinuumHomeView, EditText, Button, Button) -> Unit) {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("continuum-internet", Context.MODE_PRIVATE).edit().putString("mode", "OFF").commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", false).putBoolean("native-head-3d", false).commit()
        val a = Robolectric.buildActivity(AgentActivity::class.java).get()
        set(a, "bootstrapMemory", BootstrapMemoryStore(a))
        set(a, "voiceEnabled", false)
        val pane = call(a, "buildConversationPane", true) as View
        val home = field(a, "homeView") as ContinuumHomeView
        home.hologram.setNativeHeadEnabled(false)
        home.hologram.setAnimationsEnabled(false)
        home.jarvisShell.ambientMotionEnabled = false
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible()
        try {
            host.get().setContentView(pane)
            shadowOf(Looper.getMainLooper()).idle()
            check(a, host.get(), pane, home, field(a, "input") as EditText,
                field(a, "chatMicButton") as Button, field(a, "sendButton") as Button)
        } finally {
            (field(a, "onDeviceSpeech") as? AndroidOnDeviceSpeechInput)?.shutdown()
            (field(a, "mainHandler") as Handler).removeCallbacksAndMessages(null)
            home.hologram.setForegroundActive(false)
            home.setForegroundActive(false)
            (field(a, "cockpitSubscriptions") as List<*>).forEach { (it as AutoCloseable).close() }
            JarvisAvatarSlot.register(null)
            for (name in listOf("localAgentExecutor", "backlogExecutor", "learningIoExecutor", "documentIoExecutor"))
                (field(a, name) as ExecutorService).shutdownNow()
            host.pause().stop().destroy()
        }
    }

    private var composerProfile = "unmeasured"

    private fun measureComposer(a: AgentActivity, host: Activity, pane: View, width: Int, height: Int) {
        composerProfile = "${width}x${height}dp font=${RuntimeEnvironment.getFontScale()} conversation=${field(a, "conversationActive")}"
        // Resize the real host frame too: measuring only Decor leaves ViewRootImpl clipped to
        // @Config's original 1024dp display when the unfolded portrait profile is 1050dp tall.
        ShadowDisplayManager.changeDisplay(0, "w${width}dp-h${height}dp-mdpi")
        host.window.setLayout(width, height)
        val window = host.window.decorView
        assertSame("Reflow retains the mounted pane", window, pane.rootView)
        Shadow.extract<ShadowViewRootImpl>(window.parent).callDispatchResized()
        shadowOf(Looper.getMainLooper()).idleFor(40, TimeUnit.MILLISECONDS)
        set(a, "renderedWindowWidthDp", width.toFloat())
        set(a, "renderedWindowHeightDp", height.toFloat())
        call(a, "refreshConversationLayout")
        repeat(2) {
            window.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            window.layout(0, 0, width, height)
            shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
        }
    }

    private fun visibleControl(pane: View, target: View): Rect {
        val outer = Rect()
        val bounds = Rect()
        val paneVisible = pane.getGlobalVisibleRect(outer)
        val targetVisible = target.getGlobalVisibleRect(bounds)
        val location = IntArray(2).also(target::getLocationOnScreen)
        val full = Rect(location[0], location[1], location[0] + target.width, location[1] + target.height)
        val identity = when (target) {
            is EditText -> "editor"
            is Button -> "button '${target.text}'"
            else -> target.javaClass.simpleName
        }
        val ancestry = generateSequence(target as View?) { it.parent as? View }.joinToString(" <- ") {
            "${it.javaClass.simpleName}[${it.left},${it.top},${it.right},${it.bottom}; scroll=${it.scrollX},${it.scrollY}]"
        }
        val diagnostic = "$composerProfile control=$identity description=${target.contentDescription} tag=${target.tag} " +
            "full=$full visible=$bounds pane=$outer ancestry=$ancestry"
        assertTrue("The real pane is visible: $diagnostic", paneVisible)
        assertTrue("The real control is visible: $diagnostic", targetVisible)
        assertEquals("The complete control width is reachable: $diagnostic", target.width, bounds.width())
        assertEquals("The complete control height is reachable: $diagnostic", target.height, bounds.height())
        assertTrue("The control is inside the real pane: $diagnostic", outer.contains(bounds))
        val density = target.resources.displayMetrics.density
        assertTrue("Native touch width: $diagnostic", bounds.width() / density >= 48f)
        assertTrue("Native touch height: $diagnostic", bounds.height() / density >= 48f)
        return bounds
    }

    private fun touchComposer(pane: View, target: View) {
        val bounds = visibleControl(pane, target)
        val origin = IntArray(2).also(pane::getLocationOnScreen)
        val now = SystemClock.uptimeMillis()
        for ((action, time) in listOf(MotionEvent.ACTION_DOWN to now, MotionEvent.ACTION_UP to now + 16L)) {
            val event = MotionEvent.obtain(now, time, action,
                bounds.exactCenterX() - origin[0], bounds.exactCenterY() - origin[1], 0)
            try { assertTrue("The pane dispatches the real control touch", pane.dispatchTouchEvent(event)) }
            finally { event.recycle() }
        }
        shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
    }

    @Test fun inputActionsAndConversationRouteRealTouchesAcrossWindowReflow() = withComposer { a, host, pane, home, editor, mic, send ->
        val controls = listOf(editor, mic, send)
        val parents = controls.map { it.parent }
        val live = home.hologram
        val fontBefore = RuntimeEnvironment.getFontScale()
        try {
            for ((width, height, font) in listOf(Triple(412, 915, 1f), Triple(884, 760, 1f),
                Triple(840, 1050, 1f), Triple(412, 915, 1.5f))) {
                RuntimeEnvironment.setFontScale(font)
                measureComposer(a, host, pane, width, height)
                assertSame(editor, field(a, "input"))
                assertSame(mic, field(a, "chatMicButton"))
                assertSame(send, field(a, "sendButton"))
                controls.forEachIndexed { index, control -> assertSame(parents[index], control.parent) }
                assertEquals("One real editor", listOf(editor), descendants(pane).filterIsInstance<EditText>())
                val bounds = controls.map { visibleControl(pane, it) }
                for (i in bounds.indices) for (j in i + 1 until bounds.size)
                    assertFalse("Real composer targets do not overlap", Rect.intersects(bounds[i], bounds[j]))
                assertNull("Workstation contains no surrogate input bar",
                    requireNotNull(home.jarvisShell.currentShell())[JarvisShellLayout.Slot.INPUT])
                assertTrue("Home does not duplicate native composer controls", descendants(home).none {
                    it.tag?.toString() in setOf("jarvis-dialog:voice-input", "jarvis-dialog:send", "jarvis-input:conversation")
                })

                val speechBeforeEditor = field(a, "speechScopeGeneration") as Long
                touchComposer(pane, editor)
                assertEquals("Touching the actual editor opens the conversation", true, field(a, "conversationActive"))
                assertTrue(editor.hasFocus())
                assertEquals("Editor touch is not microphone capture", speechBeforeEditor, field(a, "speechScopeGeneration"))
                measureComposer(a, host, pane, width, height)

                editor.text.clear()
                val speechBeforeSend = field(a, "speechScopeGeneration") as Long
                ShadowToast.reset()
                touchComposer(pane, send)
                assertEquals("The real Send listener receives precisely this touch",
                    "Schreib zuerst einen Auftrag.", ShadowToast.getTextOfLatestToast())
                assertEquals("Send does not invoke microphone input", speechBeforeSend, field(a, "speechScopeGeneration"))
                assertEquals(false, field(a, "busy"))

                val speechBeforeMic = field(a, "speechScopeGeneration") as Long
                touchComposer(pane, mic)
                assertEquals("Only the microphone advances its actual speech scope", speechBeforeMic + 1,
                    field(a, "speechScopeGeneration"))
                assertEquals("SDK28 cannot pretend to start on-device audio", false, field(a, "voiceListening"))
                assertEquals("Unavailable speech must not send a turn", false, field(a, "busy"))
                assertEquals("", editor.text.toString())
                assertSame("Composer actions preserve the one mounted avatar", live, home.hologram)
                assertSame(home, live.parent)
            }
        } finally {
            RuntimeEnvironment.setFontScale(fontBefore)
        }
    }

    @Test fun movingOutsideOrCancellingTheGestureNeverNavigates() = withHome { home, calls ->
        for ((width, height) in listOf(412 to 915, 884 to 760)) {
            size(home, width, height)
            val button = JarvisHomeTestRoutes.destination(home, "memory")
            JarvisHomeTestRoutes.scrollIntoView(button)
            JarvisHomeTestRoutes.visibleBounds(button)
            var time = SystemClock.uptimeMillis()
            val downTime = time
            fun event(action: Int, x: Float, y: Float) {
                val e = MotionEvent.obtain(downTime, time++, action, x, y, 0)
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
            assertFalse(button.isSelected)
            assertNotEquals(JarvisControlState.PRESSED, home.jarvisShell.tiles.single { it.target == "memory" }.controlState)
        }
    }

    @Test fun responsiveRemeasureKeepsOneNavigationSurfaceAndEveryRealRoute() = withHome { home, calls ->
        val live = home.hologram
        val previousFont = RuntimeEnvironment.getFontScale()
        try {
            for ((w, h, font) in listOf(Triple(412, 915, 1f), Triple(692, 768, 1f),
                Triple(884, 760, 1f), Triple(884, 420, 2f), Triple(412, 915, 1.5f))) {
                RuntimeEnvironment.setFontScale(font)
                size(home, w, h)
                val firstHeight = home.measuredHeight
                val first = JarvisHomeTestRoutes.controls(home)
                val identities = first.associate { it.tag.toString() to it.id }
                size(home, w, h)
                assertEquals("No recursive growth from scroll content", firstHeight, home.measuredHeight)
                val current = JarvisHomeTestRoutes.controls(home)
                assertEquals("Remeasuring preserves current controls", identities, current.associate { it.tag.toString() to it.id })
                assertSame(live, home.hologram)
                assertSame(home, live.parent)
                assertSame(live, (home.avatarRenderer as JarvisHoloAvatar).hologram)
                assertEquals("Every destination remains reachable after reflow",
                    JarvisHomeTestRoutes.expectedDirectTargets(home).toSet(),
                    identities.keys.map { it.substringAfter(':') }.toSet())
                val shell = requireNotNull(home.jarvisShell.currentShell())
                if (shell.requiresVerticalScroll) {
                    assertTrue("Reflow remains scrollable", home.measuredHeight > h)
                } else {
                    assertEquals("Packed cockpit fills the viewport without inventing scroll", h, home.measuredHeight)
                }
                for (target in listOf("chats", "settings", "more")) {
                    val header = control(home, "header:$target")
                    assertTrue(header.width >= 48 && header.height >= 48)
                }
                calls.clear()
                current.forEach { button ->
                    assertTrue(button.width >= 48 && button.height >= 48)
                    tapThroughViewport(home, button)
                }
                assertEquals("Every current destination receives one real touch",
                    current.map { it.tag.toString().substringAfter(':') }, calls)
            }
        } finally {
            RuntimeEnvironment.setFontScale(previousFont)
        }
    }

    @Test fun heightOnlyViewportChangesPreserveChildrenAndAreIdempotent() = withHome { home, _ ->
        size(home, 884, 760)
        val surface = home.hologram
        val renderer = home.avatarRenderer
        val originalSlot = home.avatarSlotRect
        val legacyViews = at.heinz.deviceai.ui.ContinuumHomeLayout.Slot.values().mapNotNull { home.viewFor(it) }
        assertEquals(JarvisHomeTestRoutes.expectedDirectTargets(home),
            JarvisHomeTestRoutes.controls(home).map { it.tag.toString().substringAfter(':') })
        home.updateAvailableViewport(420f)
        assertEquals(420f, home.jarvisShell.viewportHeightDp, 0f)
        assertEquals(legacyViews, at.heinz.deviceai.ui.ContinuumHomeLayout.Slot.values().mapNotNull { home.viewFor(it) })
        measureViewport(home, 884, 420)
        assertTrue("Short viewport reflows into usable scrolling content", home.measuredHeight > 420)
        assertSame(surface, home.hologram)
        assertSame(renderer, home.avatarRenderer)
        val after = JarvisHomeTestRoutes.controls(home)
        assertEquals("Destinations survive a height-only composition change",
            JarvisHomeTestRoutes.expectedDirectTargets(home), after.map { it.tag.toString().substringAfter(':') })
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
        assertNotNull("The wide workstation retains its real camera settings slot",
            requireNotNull(home.jarvisShell.currentShell())[JarvisShellLayout.Slot.CAMERA_STATUS])
        for (target in listOf("bottom:files", "header:chats", "header:settings", "header:more", "camera:settings")) {
            val button = control(home, target)
            assertFalse(button.isEnabled)
            assertTrue(button.contentDescription.toString().contains("nicht verfügbar"))
            tapThroughViewport(home, button)
            assertTrue("A disabled action cannot fall through to conversation", calls.isEmpty())
        }
        val surrogates = descendants(home).mapNotNull { it.tag?.toString() }.filter {
            it in setOf("jarvis-dialog:voice-input", "jarvis-dialog:send", "jarvis-input:conversation", "jarvis-header:mode")
        }
        assertTrue("Unavailable Home must not recreate removed input or header targets: $surrogates", surrogates.isEmpty())
        val chat = control(home, "bottom:chat")
        assertTrue(chat.isEnabled)
        tapThroughViewport(home, chat)
        assertEquals(listOf("legacy-slot"), calls)
    }
}
