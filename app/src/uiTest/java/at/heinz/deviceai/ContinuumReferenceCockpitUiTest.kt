package at.heinz.deviceai

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ScrollView
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
import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Independent real-Activity navigation and native host-Canvas captures.
 * Synthetic app storage, muted voice and explicit 2D fallback: no inference,
 * camera, device action or Android Filament/GPU acceptance is implied. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w412dp-h915dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ContinuumReferenceCockpitUiTest {
    @Before fun configureVirtualVsyncClock() {
        // Activity setup can start the real avatar loop before fixture code can pause it.
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private fun advanceFrames(totalMillis: Long) {
        var remaining = totalMillis
        while (remaining > 0) {
            val step = minOf(16L, remaining)
            shadowOf(Looper.getMainLooper()).idleFor(step, TimeUnit.MILLISECONDS)
            remaining -= step
        }
    }

    private val actionNames = listOf(
        "LABEL_THINKING", "ACTION_MEMORY", "LABEL_ANALYSING", "LABEL_DEVELOPING",
        "ACTION_AGENTS", "ACTION_BOTS", "ACTION_TASK", "LABEL_LEARNING", "ACTION_DEVICE", "LABEL_CONNECTED"
    )
    private val targetNames = actionNames + listOf(
        "COMPOSER", "LABEL_EVOLVING", "SEARCH", "NOTIFICATIONS", "PROFILE",
        "VIEW_AVATAR", "VIEW_ORB", "VIEW_COMPACT"
    )

    private fun field(activity: AgentActivity, name: String): Any? = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(activity)

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) {
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
    } else emptyList()

    private fun settle() = advanceFrames(40)

    private fun measure(root: View, width: Int, height: Int) {
        fun exact(value: Int) = View.MeasureSpec.makeMeasureSpec(value, View.MeasureSpec.EXACTLY)
        root.measure(exact(width), exact(height))
        root.layout(0, 0, width, height)
    }

    private fun withActivity(width: Int, height: Int, font: Float, body: (AgentActivity, View, org.robolectric.android.controller.ActivityController<AgentActivity>) -> Unit) {
        val previousFont = RuntimeEnvironment.getFontScale()
        RuntimeEnvironment.setFontScale(font)
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit()
            .putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit().clear().commit()
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        try {
            controller.setup().visible().windowFocusChanged(true)
            val activity = controller.get()
            val root = activity.window.decorView
            val avatar = field(activity, "hologramView") as ContinuumHologramView
            avatar.setNativeHeadEnabled(true)
            avatar.setAnimationsEnabled(false)
            (field(activity, "homeView") as ContinuumHomeView).setForegroundActive(false)
            settle()
            measure(root, width, height)
            settle()
            measure(root, width, height)
            assertEquals("A hidden test window is not a screenshot", View.VISIBLE, root.windowVisibility)
            body(activity, root, controller)
        } finally {
            ShadowAlertDialog.getLatestAlertDialog()?.takeIf { it.isShowing }?.dismiss()
            runCatching { controller.pause() }
            runCatching { controller.stop() }
            runCatching { controller.destroy() }
            RuntimeEnvironment.setFontScale(previousFont)
        }
    }

    private fun slot(home: ContinuumHomeView, name: String): ContinuumHomeView.ElementView {
        val view = (0 until home.childCount).map { home.getChildAt(it) }
            .filterIsInstance<ContinuumHomeView.ElementView>().singleOrNull { it.slot.name == name }
        assertNotNull("The actual Activity must expose $name", view)
        return requireNotNull(view)
    }

    private fun assertRealTargets(home: ContinuumHomeView) {
        val density = home.resources.displayMetrics.density
        val targets = targetNames.map { name -> slot(home, name).also { view ->
            assertTrue("$name must be a real clickable, focusable control", view.isClickable && view.isFocusable)
            assertTrue("$name needs an accessible description", !view.contentDescription.isNullOrBlank())
            assertTrue("$name actual width ${view.width / density}dp", view.width / density >= 48f)
            assertTrue("$name actual height ${view.height / density}dp", view.height / density >= 48f)
            assertTrue("$name is outside the content width", view.left >= 0 && view.right <= home.width)
            assertTrue("$name is outside scrollable content", view.top >= 0 && view.bottom <= home.height)
        } }
        for (i in targets.indices) for (j in i + 1 until targets.size) {
            val first = targets[i]
            val second = targets[j]
            assertFalse("Actual touch bounds overlap: ${first.slot}/${second.slot}",
                first.left < second.right && second.left < first.right &&
                    first.top < second.bottom && second.top < first.bottom)
        }
    }

    /** Scroll the actual ancestor, then dispatch through the decor tree so a
     * foreground overlay or wrong hit target cannot be hidden by performClick. */
    private fun tap(root: View, target: View) {
        var parent = target.parent
        while (parent is View) {
            if (parent is ScrollView) {
                val relative = Rect(0, 0, target.width, target.height)
                parent.offsetDescendantRectToMyCoords(target, relative)
                parent.scrollTo(parent.scrollX, (relative.top - (parent.height - target.height) / 2).coerceAtLeast(0))
            }
            parent = parent.parent
        }
        target.requestRectangleOnScreen(Rect(0, 0, target.width, target.height), true)
        settle()
        val visible = Rect()
        assertTrue("Target must be visibly scroll reachable: ${target.contentDescription}", target.getGlobalVisibleRect(visible))
        assertTrue("Target must not be a clipped sliver", visible.width() >= target.width - 1 && visible.height() >= target.height - 1)
        val rootPosition = IntArray(2).also { root.getLocationOnScreen(it) }
        val x = visible.exactCenterX() - rootPosition[0]
        val y = visible.exactCenterY() - rootPosition[1]
        val at = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(at, at, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(at, at + 16, MotionEvent.ACTION_UP, x, y, 0)
        try {
            assertTrue("Decor must receive the actual DOWN", root.dispatchTouchEvent(down))
            assertTrue("Decor must receive the actual UP", root.dispatchTouchEvent(up))
        } finally {
            down.recycle()
            up.recycle()
        }
        settle()
    }

    private fun assertDialog(title: String): AlertDialog {
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull("Tap must open $title", dialog)
        requireNotNull(dialog)
        assertTrue("$title must actually be showing", dialog.isShowing)
        val texts = descendants(requireNotNull(dialog.window).decorView).filterIsInstance<TextView>()
            .map { it.text.toString() }
        assertTrue("Wrong destination for $title: $texts", texts.any { it.equals(title, ignoreCase = true) })
        return dialog
    }

    private fun capture(root: View, label: String, width: Int, height: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            root.draw(Canvas(bitmap))
            val directory = System.getProperty("continuum.reference.evidenceDir")
                ?: System.getProperty("continuum.layout.evidenceDir")
                ?: System.getProperty("continuum.ui.evidenceDir")
            if (directory != null) {
                val output = File(directory).apply { mkdirs() }
                File(output, "$label.png").outputStream().use {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                File(output, "$label.views.txt").writeText(descendants(root).joinToString("\n") { view ->
                    val position = IntArray(2).also { view.getLocationOnScreen(it) }
                    val text = if (view is TextView) view.text.toString() else view.contentDescription?.toString().orEmpty()
                    "${view.javaClass.simpleName} shown=${view.isShown} clickable=${view.isClickable} " +
                        "x=${position[0]} y=${position[1]} w=${view.width} h=${view.height} text=${text.replace('\n', ' ').take(220)}"
                })
            }
            println("REFERENCE_CAPTURE $label ${width}x$height NATIVE_HOST_CANVAS_2D_FALLBACK_NOT_FILAMENT")
        } finally { bitmap.recycle() }
    }

    private fun checkProfile(label: String, width: Int, height: Int) {
        for (font in listOf(1f, 1.5f, 2f)) withActivity(width, height, font) { activity, root, _ ->
            val tag = "$label-font${(font * 100).toInt()}"
            val home = field(activity, "homeView") as ContinuumHomeView
            val avatar = field(activity, "hologramView") as ContinuumHologramView
            // 2026-11-04 (Kreativdirektor, "ein Kopf"): in the landscape cockpit the ONE avatar slot is held by the
            // docked JarvisAvatarRenderer or its named placeholder; elsewhere the real home avatar stays visible.
            if (home.ownsAvatarSlot) assertTrue("The avatar slot must be held", home.avatarSlotShown)
            else assertTrue("The real home avatar must be visible", avatar.isShown)
            assertRealTargets(home)
            capture(root, "$tag-home", width, height)

            for ((name, title) in listOf(
                "ACTION_MEMORY" to "Gedächtniszentrum",
                "ACTION_AGENTS" to "Team · Bots",
                "ACTION_BOTS" to "Team · Bots",
                "ACTION_TASK" to "CODING-AUFTRAG ENTWERFEN",
                "ACTION_DEVICE" to "Systemstatus"
            )) {
                tap(root, slot(home, name))
                assertDialog(title).dismiss()
                settle()
                assertFalse("Opening a destination must not start a model task", field(activity, "busy") as Boolean)
            }
            capture(root, "$tag-actions", width, height)
            tap(root, slot(home, "COMPOSER"))
            measure(root, width, height)
            val input = field(activity, "input") as EditText
            assertTrue("Composer must reach the real visible editor", input.isShown && input.hasFocus())
            assertEquals(ContinuumWorkArea.CHAT, field(activity, "activeWorkArea"))
            assertSame("Opening chat must preserve the existing avatar", avatar, field(activity, "hologramView"))
            assertTrue("The same avatar remains visible in chat", avatar.isShown)
            assertEquals(1, descendants(root).count { it is ContinuumHologramView })
            val draft = "Unveröffentlichter Testentwurf; keine Ausführung."
            input.setText(draft)
            measure(root, width, height)
            val density = root.resources.displayMetrics.density
            assertTrue("Editor must have usable width", input.width / density >= 120f)
            for (description in listOf("Neue Chat-Sitzung", "Spracheingabe starten", "Nachricht senden")) {
                val control = descendants(root).single { it.contentDescription?.toString() == description }
                assertTrue("$description touch width", control.width / density >= 48f)
                assertTrue("$description touch height", control.height / density >= 48f)
                val rect = Rect()
                assertTrue("$description visible", control.getGlobalVisibleRect(rect))
                assertEquals("$description fully on screen", control.height, rect.height())
                assertEquals("$description fully within width", control.width, rect.width())
            }
            capture(root, "$tag-chat", width, height)

            val back = descendants(root).firstOrNull {
                it.tag == ContinuumWorkArea.START && it.isShown && it.isClickable
            } ?: descendants(root).firstOrNull {
                it.isShown && it.isClickable && it.contentDescription?.toString() == "Zur Startseite mit allen KI-Bereichen"
            }
            assertNotNull("Chat needs a reachable route back to the cockpit", back)
            tap(root, requireNotNull(back))
            measure(root, width, height)
            assertEquals(ContinuumWorkArea.START, field(activity, "activeWorkArea"))
            assertSame(home, avatar.parent)
            assertEquals("Navigation must preserve an unsent draft", draft, input.text.toString())
            assertEquals(1, descendants(root).count { it is ContinuumHologramView })
            assertRealTargets(home)
        }
    }

    @Test @Config(qualifiers = "w412dp-h915dp-mdpi")
    fun presentationControlsReuseTheLiveAvatarAndPersistCompactChoice() = withActivity(412, 915, 1f) { activity, root, _ ->
        val home = field(activity, "homeView") as ContinuumHomeView
        val avatar = field(activity, "hologramView") as ContinuumHologramView
        val originalHeight = avatar.height
        assertTrue(avatar.isNativeHeadEnabled())
        assertFalse(avatar.isNative3dEnabled())
        tap(root, slot(home, "VIEW_ORB"))
        measure(root, 412, 915)
        assertFalse("Orb selection must render the preserved original orb", avatar.isNativeHeadEnabled())
        assertTrue(slot(home, "VIEW_ORB").isSelected)
        capture(root, "phone-412x915-original-orb", 412, 915)
        tap(root, slot(home, "VIEW_COMPACT"))
        measure(root, 412, 915)
        assertTrue("Compact frees vertical room for conversation", avatar.height < originalHeight)
        assertTrue(slot(home, "VIEW_COMPACT").isSelected)
        assertTrue(activity.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE)
            .getBoolean("compact", false))
        capture(root, "phone-412x915-compact", 412, 915)
        tap(root, slot(home, "VIEW_AVATAR"))
        measure(root, 412, 915)
        assertTrue(avatar.isNativeHeadEnabled())
        assertFalse("Restore the owner's 2D fallback preference", avatar.isNative3dEnabled())
        assertEquals(originalHeight, avatar.height)
        assertTrue(slot(home, "VIEW_AVATAR").isSelected)
        assertSame(avatar, field(activity, "hologramView"))
        assertSame(home, avatar.parent)
        assertEquals(1, descendants(root).count { it is ContinuumHologramView })
        assertFalse("Presentation selection must never submit a task", field(activity, "busy") as Boolean)
    }

    @Test @Config(qualifiers = "w412dp-h915dp-mdpi")
    fun composerDraftSurvivesActualWindowReflowAndActivityRecreation() = withActivity(412, 915, 1f) { activity, root, controller ->
        tap(root, slot(field(activity, "homeView") as ContinuumHomeView, "COMPOSER"))
        val draft = "Nicht gesendeter Entwurf\nZweite Zeile bleibt erhalten."
        val input = field(activity, "input") as EditText
        input.setText(draft)
        input.setSelection(4, 15)
        org.robolectric.shadows.ShadowDisplayManager.changeDisplay(0, "w280dp-h720dp-mdpi")
        activity.window.setLayout(280, 720)
        settle()
        val windowRoot = field(activity, "uiWindowRoot") as View
        measure(windowRoot, 280, 720)
        settle()
        println("REFLOW_OBSERVATION old=${windowRoot.width} current=${(field(activity, "uiWindowRoot") as View).width} measured=${field(activity, "renderedWindowWidthDp")}")
        val rebuilt = field(activity, "input") as EditText
        assertEquals(draft, rebuilt.text.toString())
        assertEquals(4, rebuilt.selectionStart)
        assertEquals(15, rebuilt.selectionEnd)
        assertTrue(rebuilt.hasFocus())
        assertEquals(2, rebuilt.maxLines)
        assertFalse(field(activity, "busy") as Boolean)
        controller.recreate()
        settle()
        val resumed = controller.get()
        assertEquals(draft, (field(resumed, "input") as EditText).text.toString())
        assertEquals(ContinuumWorkArea.CHAT, field(resumed, "activeWorkArea"))
        assertFalse("Rotation cannot submit the retained draft", field(resumed, "busy") as Boolean)
    }

    @Test @Config(qualifiers = "w280dp-h720dp-mdpi")
    fun narrowLargeTypeSendTargetSubmitsReadOnlyCapabilitiesThroughDecor() = withActivity(280, 720, 2f) { activity, root, _ ->
        tap(root, slot(field(activity, "homeView") as ContinuumHomeView, "COMPOSER"))
        val input = field(activity, "input") as EditText
        input.setText("Was kannst du?")
        measure(root, 280, 720)
        tap(root, field(activity, "sendButton") as View)
        assertEquals("", input.text.toString())
        assertTrue(descendants(field(activity, "chatContainer") as View).filterIsInstance<TextView>()
            .any { it.text.toString().contains("Continuum · Aktuelle App-Fähigkeiten") })
        assertFalse(field(activity, "busy") as Boolean)
        AgentActivity::class.java.getDeclaredField("activeLocalRequest").apply { isAccessible = true }.set(activity, true)
        AgentActivity::class.java.getDeclaredMethod("setBusy", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(activity, true)
        measure(root, 280, 720)
        val stop = field(activity, "sendButton") as TextView
        capture(root, "narrow-280x720-font200-stop", 280, 720)
        assertEquals("Lokale Anfrage abbrechen", stop.contentDescription)
        assertEquals("Stop label must not wrap outside the primary control", 1, stop.layout.lineCount)
    }

    @Test @Config(qualifiers = "w280dp-h720dp-mdpi")
    fun narrowPhoneHasRealDestinationsAndReadableLargeType() = checkProfile("narrow-280x720", 280, 720)

    @Test @Config(qualifiers = "w412dp-h915dp-mdpi")
    fun phoneHasRealDestinationsAndReadableLargeType() = checkProfile("phone-412x915", 412, 915)

    @Test @Config(qualifiers = "w840dp-h649dp-land-mdpi")
    fun foldLandscapeHasRealDestinationsAndReadableLargeType() = checkProfile("fold-landscape-840x649", 840, 649)

    @Test @Config(qualifiers = "w840dp-h1050dp-port-mdpi")
    fun foldPortraitHasRealDestinationsAndReadableLargeType() = checkProfile("fold-portrait-840x1050", 840, 1050)

    @Test @Config(qualifiers = "w840dp-h649dp-land-mdpi")
    fun shortenedWideWindowKeepsEveryRailTargetScrollableAndClickable() = withActivity(840, 649, 2f) { activity, root, _ ->
        val avatar = field(activity, "hologramView") as ContinuumHologramView
        val rail = field(activity, "railView") as View
        val frame = rail.parent as ViewGroup
        val scroll = frame.parent as ScrollView
        measure(root, 840, 320)
        settle()
        measure(root, 840, 320)
        assertSame("A height-only IME change must not rebuild the avatar", avatar, field(activity, "hologramView"))
        val controls = (0 until frame.childCount).map { frame.getChildAt(it) }.filter { it.isClickable }
        assertEquals("No rail destination may disappear under the IME", 8, controls.size)
        val density = root.resources.displayMetrics.density
        controls.forEach { target ->
            assertTrue("Actual rail touch width", target.width / density >= 48f)
            assertTrue("Actual rail touch height", target.height / density >= 48f)
            assertTrue("Rail child stays inside its content", target.top >= 0 && target.bottom <= frame.height)
        }
        for (i in 1 until controls.size) assertTrue("Rail targets cannot overlap", controls[i - 1].bottom <= controls[i].top)
        assertTrue("Short window must scroll instead of shrinking targets", frame.height > scroll.height)
        val more = controls.single { it.contentDescription?.toString() == "Mehr" }
        tap(root, more)
        assertDialog("Weitere Arbeitsbereiche").dismiss()
        capture(root, "fold-short-840x320-font200-rail", 840, 320)
    }
}
