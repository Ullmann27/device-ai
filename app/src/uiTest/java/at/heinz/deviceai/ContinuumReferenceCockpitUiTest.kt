package at.heinz.deviceai

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
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
import org.robolectric.android.controller.ActivityController
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowSystemClock
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

    private val tileTargets = listOf(
        "chat", "memory", "logic", "research", "agents", "tasks", "web", "learning", "device", "system"
    )
    private val railTargets = listOf("home") + tileTargets
    private val appearanceChoices = listOf(
        "Kugel · Original", "Kopf · Native 2D-Vorschau", "Kopf · Native 3D-Vorschau", "Kompakt · Kugel"
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

    private fun recreateWithBoundedTraversal(controller: ActivityController<AgentActivity>) {
        val previous = controller.get()
        val application = previous.application
        val handler = Handler.createAsync(Looper.getMainLooper())
        var pulseScheduled = false
        var pulses = 0
        val traversalPulse = Runnable {
            assertEquals("Recreation permits exactly one bounded VSync pulse", 0, pulses)
            val recreated = controller.get()
            assertTrue("The pulse belongs to the new Activity", recreated !== previous)
            assertNotNull("The new decor must be added before VSync", recreated.window.decorView.parent)
            pulses = 1
            // Keep VSync paused. The outer visible() drain dispatches the actual
            // async frame posted by this one clock advance; no nested idle loop.
            ShadowSystemClock.advanceBy(Duration.ofMillis(16))
        }
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity !== previous && activity === controller.get()) {
                    assertFalse("Schedule a traversal pulse only once", pulseScheduled)
                    pulseScheduled = true
                    assertTrue("Queue the bounded pulse past traversal sync barriers", handler.post(traversalPulse))
                }
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        application.registerActivityLifecycleCallbacks(callbacks)
        try {
            // 4.14.1 resume/postResume do not drain; visible() drains after
            // makeVisible. Keep its real flags, retained instances and lifecycle.
            controller.recreate()
            assertTrue("The recreated Activity must schedule its pulse", pulseScheduled)
            assertEquals("The original recreate path consumes one pulse", 1, pulses)
            assertTrue("Original recreation preserves configuration-change teardown", previous.isChangingConfigurations)
            assertTrue("Original recreation destroys the previous Activity", previous.isDestroyed)
            assertTrue("Original recreation constructs a new Activity", controller.get() !== previous)
            assertTrue("Real traversal attaches the recreated decor", controller.get().window.decorView.isAttachedToWindow)
            assertTrue("Original recreation restores real window focus", controller.get().hasWindowFocus())
        } finally {
            try { application.unregisterActivityLifecycleCallbacks(callbacks) }
            finally { handler.removeCallbacks(traversalPulse) }
        }
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
            controller.setup().visible()
            advanceFrames(16)
            assertTrue("The real window traversal must attach decor before focus", controller.get().window.decorView.isAttachedToWindow)
            controller.windowFocusChanged(true)
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

    private fun currentHome(activity: AgentActivity): ContinuumHomeView {
        val home = field(activity, "homeView") as ContinuumHomeView
        assertTrue("Inspect the currently attached Activity home", home.isAttachedToWindow)
        assertTrue("The actual Activity enables the current shell", home.jarvisShellStage)
        assertTrue("HOME must be visibly owned by the current window", home.isShown)
        assertTrue(descendants(activity.window.decorView).any { it === home })
        return home
    }

    private fun tagged(root: View, tag: String): View {
        val matches = descendants(root).filter { it.tag == tag }
        assertEquals("Exactly one current native control for $tag", 1, matches.size)
        return matches.single().also {
            assertTrue("$tag belongs to the attached visible hierarchy", it.isAttachedToWindow && it.isShown)
        }
    }

    private fun shellControl(activity: AgentActivity, tag: String): View = tagged(currentHome(activity), tag)

    private fun shellControls(root: View): List<View> = descendants(root).filter { view ->
        val tag = view.tag as? String ?: return@filter false
        listOf("jarvis-tile:", "jarvis-dialog:", "jarvis-input:", "jarvis-camera:", "jarvis-panel:",
            "jarvis-header:", "jarvis-bottom:", "jarvis-rail:").any(tag::startsWith) && view.isShown
    }

    private fun assertMinimumTarget(view: View, name: String) {
        val density = view.resources.displayMetrics.density
        assertTrue("$name actual width ${view.width / density}dp", view.width / density >= 48f)
        assertTrue("$name actual height ${view.height / density}dp", view.height / density >= 48f)
    }

    private fun assertVisibleShellControlsDoNotOverlap(root: View) {
        val visible = shellControls(root).mapNotNull { view ->
            val rect = Rect()
            if (view.getGlobalVisibleRect(rect)) view to rect else null
        }
        for (i in visible.indices) for (j in i + 1 until visible.size) {
            assertFalse("Actual visible touch bounds overlap: ${visible[i].first.tag}/${visible[j].first.tag}",
                Rect.intersects(visible[i].second, visible[j].second))
        }
    }

    private fun assertRealTargets(activity: AgentActivity) {
        val home = currentHome(activity)
        val targets = shellControls(home)
        assertEquals("All ten current destinations must exist", tileTargets.toSet(),
            targets.mapNotNull { (it.tag as? String)?.takeIf { tag -> tag.startsWith("jarvis-tile:") }
                ?.removePrefix("jarvis-tile:") }.toSet())
        for (tag in listOf("jarvis-dialog:conversation", "jarvis-dialog:voice-input", "jarvis-dialog:camera-settings",
            "jarvis-dialog:avatar-appearance", "jarvis-header:notifications", "jarvis-header:settings",
            "jarvis-panel:ROW_AGENTS", "jarvis-panel:ROW_TASKS", "jarvis-panel:ROW_MEMORY", "jarvis-panel:ROW_MODEL")) {
            tagged(home, tag)
        }
        for (target in tileTargets) tagged(home, "jarvis-tile:$target")
        targets.forEach { view ->
            val name = view.tag.toString()
            assertTrue("$name must be a real clickable, focusable control", view.isClickable && view.isFocusable)
            assertTrue("$name needs an accessible description", !view.contentDescription.isNullOrBlank())
            assertMinimumTarget(view, name)
            val parent = view.parent as View
            assertTrue("$name stays inside its actual content width", view.left >= 0 && view.right <= parent.width)
            assertTrue("$name stays inside its actual scrollable content", view.top >= 0 && view.bottom <= parent.height)
        }
        // Compare content siblings before scrolling, then compare all clipped window coordinates after every real tap.
        for (i in targets.indices) for (j in i + 1 until targets.size) {
            val first = targets[i]; val second = targets[j]
            if (first.parent === second.parent) {
                assertFalse("Actual sibling touch bounds overlap: ${first.tag}/${second.tag}",
                    first.left < second.right && second.left < first.right &&
                        first.top < second.bottom && second.top < first.bottom)
            }
        }
        assertVisibleShellControlsDoNotOverlap(activity.window.decorView)
    }

    private fun tapDialogItem(dialog: AlertDialog, label: String) {
        val root = requireNotNull(dialog.window).decorView
        val list = dialog.listView
        if (list != null) {
            val positions = (0 until list.adapter.count).filter { list.adapter.getItem(it).toString() == label }
            assertEquals("The native dialog must expose exactly one $label", 1, positions.size)
            val position = positions.single()
            list.setSelection(position)
            settle()
            val row = list.getChildAt(position - list.firstVisiblePosition)
            assertNotNull("The selected native list row must be laid out", row)
            tap(root, requireNotNull(row))
        } else {
            val actions = descendants(root).filterIsInstance<TextView>()
                .filter { it.isClickable && it.isShown && it.text.toString() == label }
            assertEquals("The visible destination must expose exactly one $label", 1, actions.size)
            tap(root, actions.single())
        }
    }

    private fun closeDialogThroughDecor(dialog: AlertDialog) {
        val root = requireNotNull(dialog.window).decorView
        val close = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.takeIf { it.isShown }
            ?: descendants(root).filterIsInstance<TextView>().firstOrNull {
                it.isShown && it.isClickable && it.text.toString().uppercase() in listOf("SCHLIESSEN", "ABBRECHEN", "ZURÜCK")
            }
        assertNotNull("The opened destination needs a visible close/back control", close)
        tap(root, requireNotNull(close))
        assertFalse("The real close control must dismiss its dialog", dialog.isShowing)
    }

    private fun openMoreThroughDecor(activity: AgentActivity): AlertDialog {
        val root = activity.window.decorView
        val direct = descendants(root).firstOrNull {
            it.isShown && it.isClickable && (it.tag == "jarvis-header:more" || it.tag == ContinuumWorkArea.MEHR)
        }
        if (direct != null) tap(root, direct) else {
            // Wide reflow has no category dock or spare header cell; reuse the existing System centre.
            tap(root, shellControl(activity, "jarvis-tile:system"))
            tapDialogItem(assertDialog("Systemstatus"), "MEHR · ARBEITSBEREICHE")
        }
        return assertDialog("Mehr")
    }

    private fun openCodingDraftThroughDecor(activity: AgentActivity): AlertDialog {
        tapDialogItem(openMoreThroughDecor(activity), "Entwicklerwerkzeuge")
        tapDialogItem(assertDialog("Entwicklungszentrum"), "CODING-AUFTRAG ENTWERFEN")
        assertFalse("Navigation to a draft grants no execution authority", field(activity, "busy") as Boolean)
        return assertDialog("CODING-AUFTRAG ENTWERFEN")
    }

    private fun appearanceDialog(activity: AgentActivity): AlertDialog {
        tap(activity.window.decorView, shellControl(activity, "jarvis-dialog:avatar-appearance"))
        val dialog = assertDialog("Continuum · Darstellung")
        val adapter = requireNotNull(dialog.listView).adapter
        assertEquals("Retain all original choices and add the explicit compact choice", appearanceChoices,
            (0 until adapter.count).map { adapter.getItem(it).toString() })
        assertEquals("Bewegung prüfen", dialog.getButton(AlertDialog.BUTTON_NEUTRAL).text.toString())
        assertEquals("Schließen", dialog.getButton(AlertDialog.BUTTON_NEGATIVE).text.toString())
        return dialog
    }

    private fun chooseAppearance(activity: AgentActivity, label: String) {
        tapDialogItem(appearanceDialog(activity), label)
        settle()
        val root = activity.window.decorView
        measure(root, root.width, root.height)
        settle()
        measure(root, root.width, root.height)
    }

    private fun returnHomeThroughDecor(activity: AgentActivity) {
        val root = activity.window.decorView
        // Size the newly visible native chat rail in this manually sized window.
        val width = root.width
        val height = root.height
        measure(root, width, height)
        settle()
        measure(root, width, height)
        val back = descendants(root).firstOrNull {
            it.isShown && it.isClickable && (it.tag == ContinuumWorkArea.START || it.tag == "home" ||
                it.contentDescription?.toString() == "Zur Startseite mit allen KI-Bereichen")
        }
        assertNotNull("Chat needs a real reachable route back to the cockpit", back)
        tap(root, requireNotNull(back))
        measure(root, root.width, root.height)
        assertEquals(ContinuumWorkArea.START, field(activity, "activeWorkArea"))
        currentHome(activity)
    }

    /** Scroll the actual ancestor, then dispatch through the decor tree so a
     * foreground overlay or wrong hit target cannot be hidden by performClick. */
    private fun tap(root: View, target: View) {
        val label = target.contentDescription?.toString() ?: target.tag?.toString()
            ?: (target as? TextView)?.text?.toString() ?: target.javaClass.simpleName
        assertMinimumTarget(target, label)
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
        assertVisibleShellControlsDoNotOverlap(root)
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
            val home = currentHome(activity)
            val avatar = field(activity, "hologramView") as ContinuumHologramView
            assertTrue("The existing avatar slot must be held", home.avatarSlotShown)
            assertEquals(1, descendants(root).count { it is ContinuumHologramView })
            assertRealTargets(activity)
            capture(root, "$tag-home", width, height)

            for ((target, title) in listOf(
                "jarvis-tile:memory" to "Gedächtniszentrum",
                "jarvis-tile:agents" to "Team · Bots",
                "jarvis-panel:ROW_AGENTS" to "Team · Bots",
                "jarvis-tile:tasks" to "Entwicklungsaufgaben",
                "jarvis-tile:device" to "Systemstatus"
            )) {
                tap(activity.window.decorView, shellControl(activity, target))
                closeDialogThroughDecor(assertDialog(title))
                assertFalse("Opening a destination must not start a model task", field(activity, "busy") as Boolean)
            }
            // Preserve the old development/draft requirement independently of today's Tasks backlog destination.
            closeDialogThroughDecor(openCodingDraftThroughDecor(activity))
            tap(root, shellControl(activity, "jarvis-header:settings"))
            closeDialogThroughDecor(assertDialog("Deine KI personalisieren"))
            tap(root, shellControl(activity, "jarvis-tile:learning"))
            val learning = assertDialog("Lernzentrum")
            val details = descendants(requireNotNull(learning.window).decorView).filterIsInstance<TextView>()
                .single { it.isClickable && it.text.toString() == "LERNKERN · DETAILS" }
            assertTrue("The previous evolving destination remains a real focusable action", details.isFocusable)
            assertMinimumTarget(details, "LERNKERN · DETAILS")
            tapDialogItem(learning, "WISSENSARCHIV · QUELLEN SUCHEN")
            closeDialogThroughDecor(assertDialog("Continuum · lokales Wissensarchiv"))
            assertFalse(field(activity, "busy") as Boolean)
            capture(root, "$tag-actions", width, height)

            tap(root, shellControl(activity, "jarvis-dialog:conversation"))
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
                assertMinimumTarget(control, description)
                val rect = Rect()
                assertTrue("$description visible", control.getGlobalVisibleRect(rect))
                assertEquals("$description fully on screen", control.height, rect.height())
                assertEquals("$description fully within width", control.width, rect.width())
            }
            capture(root, "$tag-chat", width, height)

            returnHomeThroughDecor(activity)
            assertSame("Return to the current home keeps the same live avatar", currentHome(activity), avatar.parent)
            assertEquals("Navigation must preserve an unsent draft", draft, (field(activity, "input") as EditText).text.toString())
            assertEquals(1, descendants(activity.window.decorView).count { it is ContinuumHologramView })
            assertFalse(field(activity, "busy") as Boolean)
            assertRealTargets(activity)
        }
    }

    @Test @Config(qualifiers = "w412dp-h915dp-mdpi")
    fun presentationControlsReuseTheLiveAvatarAndPersistCompactChoice() = withActivity(412, 915, 1f) { activity, root, controller ->
        val home = currentHome(activity)
        val avatar = field(activity, "hologramView") as ContinuumHologramView
        val originalHeight = avatar.height
        val originalContentHeight = home.height
        val originalConversationTop = shellControl(activity, "jarvis-dialog:conversation").top
        assertTrue(avatar.isNativeHeadEnabled())
        assertFalse(avatar.isNative3dEnabled())
        chooseAppearance(activity, "Kugel · Original")
        assertFalse("Orb selection must render the preserved original orb", avatar.isNativeHeadEnabled())
        assertEquals("Normal orb selection keeps the original hero geometry", originalHeight, avatar.height)
        appearanceDialog(activity).also {
            assertEquals(0, it.listView.checkedItemPosition)
            closeDialogThroughDecor(it)
        }
        capture(root, "phone-412x915-original-orb", 412, 915)
        chooseAppearance(activity, "Kompakt · Kugel")
        assertFalse(avatar.isNativeHeadEnabled())
        assertTrue("Compact frees vertical room for conversation", avatar.height < originalHeight)
        assertTrue("The active shell must move conversation upward", shellControl(activity, "jarvis-dialog:conversation").top < originalConversationTop)
        assertTrue("Compact reduces actual scrollable content", currentHome(activity).height < originalContentHeight)
        assertEquals("The renderer follows the same smaller slot", avatar.height, currentHome(activity).avatarSlotRect.height())
        assertTrue(activity.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE)
            .getBoolean("compact", false))
        appearanceDialog(activity).also {
            assertEquals("The real native chooser exposes the saved compact selection", 3, it.listView.checkedItemPosition)
            closeDialogThroughDecor(it)
        }
        assertRealTargets(activity)
        capture(root, "phone-412x915-compact", 412, 915)
        chooseAppearance(activity, "Kopf · Native 2D-Vorschau")
        assertTrue(avatar.isNativeHeadEnabled())
        assertFalse("Restore the owner's 2D fallback preference", avatar.isNative3dEnabled())
        assertEquals(originalHeight, avatar.height)
        assertEquals(originalContentHeight, currentHome(activity).height)
        assertEquals(originalConversationTop, shellControl(activity, "jarvis-dialog:conversation").top)
        assertFalse(activity.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE)
            .getBoolean("compact", true))
        appearanceDialog(activity).also {
            assertEquals(1, it.listView.checkedItemPosition)
            closeDialogThroughDecor(it)
        }
        assertSame(avatar, field(activity, "hologramView"))
        assertSame(currentHome(activity), avatar.parent)
        assertEquals(1, descendants(root).count { it is ContinuumHologramView })
        assertFalse("Presentation selection must never submit a task", field(activity, "busy") as Boolean)

        chooseAppearance(activity, "Kompakt · Kugel")
        recreateWithBoundedTraversal(controller)
        advanceFrames(16)
        assertTrue(controller.get().window.decorView.isAttachedToWindow)
        controller.windowFocusChanged(true)
        val resumed = controller.get()
        val resumedRoot = resumed.window.decorView
        val resumedAvatar = field(resumed, "hologramView") as ContinuumHologramView
        resumedAvatar.setAnimationsEnabled(false)
        (field(resumed, "homeView") as ContinuumHomeView).setForegroundActive(false)
        settle(); measure(resumedRoot, 412, 915); settle(); measure(resumedRoot, 412, 915)
        assertFalse("Restored compact preference keeps the original orb", resumedAvatar.isNativeHeadEnabled())
        assertTrue("A recreated active shell retains the smaller avatar", resumedAvatar.height < originalHeight)
        assertTrue(resumed.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).getBoolean("compact", false))
        appearanceDialog(resumed).also {
            assertEquals(3, it.listView.checkedItemPosition)
            closeDialogThroughDecor(it)
        }
        chooseAppearance(resumed, "Kopf · Native 2D-Vorschau")
        assertEquals(originalHeight, resumedAvatar.height)
        assertTrue(resumedAvatar.isNativeHeadEnabled())
        assertFalse(resumedAvatar.isNative3dEnabled())
        assertSame(resumedAvatar, field(resumed, "hologramView"))
        assertEquals(1, descendants(resumedRoot).count { it is ContinuumHologramView })
        assertFalse(field(resumed, "busy") as Boolean)
    }

    @Test @Config(qualifiers = "w412dp-h915dp-mdpi")
    fun composerDraftSurvivesActualWindowReflowAndActivityRecreation() = withActivity(412, 915, 1f) { activity, root, controller ->
        tap(root, shellControl(activity, "jarvis-dialog:conversation"))
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
        recreateWithBoundedTraversal(controller)
        settle()
        val resumed = controller.get()
        assertEquals(draft, (field(resumed, "input") as EditText).text.toString())
        assertEquals(ContinuumWorkArea.CHAT, field(resumed, "activeWorkArea"))
        assertFalse("Rotation cannot submit the retained draft", field(resumed, "busy") as Boolean)
    }

    @Test @Config(qualifiers = "w280dp-h720dp-mdpi")
    fun narrowLargeTypeSendTargetSubmitsReadOnlyCapabilitiesThroughDecor() = withActivity(280, 720, 2f) { activity, root, _ ->
        tap(root, shellControl(activity, "jarvis-dialog:conversation"))
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

    private fun assertRailGeometry(controls: List<View>, expectedTags: List<String>) {
        assertEquals("Every current rail destination is retained", expectedTags, controls.map { it.tag.toString() })
        for (target in controls) {
            assertTrue("Rail targets remain real clickable/focusable controls", target.isClickable && target.isFocusable)
            assertMinimumTarget(target, target.tag.toString())
            val parent = target.parent as View
            assertTrue("Rail child stays inside content width", target.left >= 0 && target.right <= parent.width)
            assertTrue("Rail child stays inside scrollable content", target.top >= 0 && target.bottom <= parent.height)
        }
        for (i in 1 until controls.size) assertTrue("Rail targets cannot overlap", controls[i - 1].bottom <= controls[i].top)
        var parent = controls.last().parent
        var canScroll = false
        while (parent is View) {
            if (parent is ScrollView && parent.childCount == 1) {
                canScroll = canScroll || parent.getChildAt(0).height > parent.height
            }
            parent = parent.parent
        }
        assertTrue("A short window must scroll in its actual ancestor instead of shrinking targets", canScroll)
    }

    private fun assertRailDestination(target: String): AlertDialog {
        if (target != "research") return assertDialog(requireNotNull(mapOf(
            "memory" to "Gedächtniszentrum", "logic" to "Logik und Planung", "agents" to "Team · Bots",
            "tasks" to "Entwicklungsaufgaben", "web" to "Web und Quellen", "learning" to "Lernzentrum",
            "device" to "Systemstatus", "system" to "Systemstatus"
        )[target]))
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull("Research must open the existing gated destination", dialog)
        requireNotNull(dialog)
        assertTrue(dialog.isShowing)
        val labels = descendants(requireNotNull(dialog.window).decorView).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue("Research keeps the existing consent boundary", "Internet ist ausgeschaltet" in labels || "WEB RECHERCHE" in labels)
        return dialog
    }

    @Test @Config(qualifiers = "w840dp-h649dp-land-mdpi")
    fun shortenedWideWindowKeepsEveryRailTargetScrollableAndClickable() = withActivity(840, 649, 2f) { activity, root, _ ->
        val avatar = field(activity, "hologramView") as ContinuumHologramView
        activity.window.setLayout(840, 320)
        measure(root, 840, 320)
        settle()
        measure(root, 840, 320)
        assertSame("A height-only window change must not rebuild the avatar", avatar, field(activity, "hologramView"))
        val shellRail = descendants(currentHome(activity)).filter { (it.tag as? String)?.startsWith("jarvis-rail:") == true }
        assertRailGeometry(shellRail, railTargets.map { "jarvis-rail:$it" })
        for (target in railTargets) {
            tap(activity.window.decorView, shellControl(activity, "jarvis-rail:$target"))
            when (target) {
                "home" -> assertEquals(ContinuumWorkArea.START, field(activity, "activeWorkArea"))
                "chat" -> {
                    assertEquals(ContinuumWorkArea.CHAT, field(activity, "activeWorkArea"))
                    returnHomeThroughDecor(activity)
                }
                else -> closeDialogThroughDecor(assertRailDestination(target))
            }
            assertFalse("A rail destination must not submit a task", field(activity, "busy") as Boolean)
            assertSame(avatar, field(activity, "hologramView"))
        }
        // Two real taps reach More even without a wide header entry; then enter an actual work area and return.
        closeDialogThroughDecor(openCodingDraftThroughDecor(activity))
        assertEquals(ContinuumWorkArea.START, field(activity, "activeWorkArea"))
        assertSame(currentHome(activity), avatar.parent)

        tap(activity.window.decorView, shellControl(activity, "jarvis-rail:chat"))
        val draft = "Kurzes Fenster: ungesendeter Entwurf bleibt erhalten."
        (field(activity, "input") as EditText).setText(draft)
        measure(activity.window.decorView, 840, 320)
        val rail = field(activity, "railView") as View
        assertTrue("Conversation owns its separate visible rail", rail.isShown)
        val frame = rail.parent as ViewGroup
        val controls = (0 until frame.childCount).map { frame.getChildAt(it) }.filter { it.isClickable }
        assertRailGeometry(controls, railTargets)
        val system = controls.single { it.tag == "system" }
        tap(activity.window.decorView, system)
        tapDialogItem(assertDialog("Systemstatus"), "MEHR · ARBEITSBEREICHE")
        tapDialogItem(assertDialog("Mehr"), "Entwicklerwerkzeuge")
        closeDialogThroughDecor(assertDialog("Entwicklungszentrum"))
        assertEquals(ContinuumWorkArea.CHAT, field(activity, "activeWorkArea"))
        returnHomeThroughDecor(activity)
        assertEquals(draft, (field(activity, "input") as EditText).text.toString())
        assertSame(avatar, field(activity, "hologramView"))
        assertSame(currentHome(activity), avatar.parent)
        assertEquals(1, descendants(activity.window.decorView).count { it is ContinuumHologramView })
        assertFalse(field(activity, "busy") as Boolean)
        capture(activity.window.decorView, "fold-short-840x320-font200-rail", 840, 320)
    }
}
