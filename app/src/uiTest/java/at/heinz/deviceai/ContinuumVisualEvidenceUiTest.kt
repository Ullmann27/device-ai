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
import android.widget.ScrollView
import at.heinz.deviceai.avatar.AvatarDirector
import at.heinz.deviceai.avatar.AvatarPoseReducer
import at.heinz.deviceai.avatar.AvatarRuntimeSignals
import at.heinz.deviceai.ui.ContinuumHomeView
import at.heinz.deviceai.ui.HollywoodFuiConstruct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/**
 * Visual evidence of the real AgentActivity cockpit on the host (Robolectric NATIVE canvas). The live 3D head
 * cannot run here: the 2D head preview draws in the head stage instead of Filament. TTS callbacks and the
 * owner's position are scripted through the real AvatarDirector; nothing is measured on a device.
 * Writes PNGs only with -PcontinuumUiEvidenceDir=…; otherwise it only checks that the scenes render.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContinuumVisualEvidenceUiTest {
    private var framePump: CockpitHostFramePump? = null
    @Before fun configureVirtualVsyncClock() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private fun advance(ms: Long) {
        var left = ms
        while (left > 0) {
            val step = minOf(16L, left)
            shadowOf(Looper.getMainLooper()).idleFor(step, TimeUnit.MILLISECONDS)
            framePump?.drawRequestedFrame()
            left -= step
        }
    }

    private fun field(a: AgentActivity, name: String): Any? =
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(a)

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun measure(root: View, w: Int, h: Int) {
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, w, h)
    }

    private fun capture(view: View, label: String, w: Int, h: Int) {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            view.draw(Canvas(bitmap))
            assertTrue("$label must not be blank", (0 until 200).any { bitmap.getPixel(w * it / 200, h / 3) != bitmap.getPixel(0, 0) })
            System.getProperty("continuum.ui.evidenceDir")?.let { dir ->
                val directory = File(dir).apply { mkdirs() }
                directory.resolve("$label.png").outputStream().use {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                directory.resolve("$label.views.txt").writeText(descendants(view).joinToString("\n") { node ->
                    val bounds = Rect().also { node.getGlobalVisibleRect(it) }
                    val value = (node as? TextView)?.text ?: node.contentDescription
                    "${node.javaClass.simpleName} tag=${node.tag} shown=${node.isShown} bounds=$bounds " +
                        "alpha=${node.alpha} scale=${node.scaleX}/${node.scaleY} text=${value?.toString().orEmpty().replace('\n', ' ').take(240)}"
                })
            }
            println("EVIDENCE_CAPTURE $label ${w}x$h HOST_ROBOLECTRIC_NATIVE_CANVAS_2D_HEAD_NOT_FILAMENT")
        } finally { bitmap.recycle() }
    }

    /** Scroll, then real decor touches. */
    private fun touch(root: View, target: View, release: Boolean): Boolean {
        JarvisHomeTestRoutes.scrollIntoView(target)
        advance(32L)
        measure(root, root.width, root.height)
        JarvisHomeTestRoutes.scrollIntoView(target)
        val r = JarvisHomeTestRoutes.visibleBounds(target)
        val position = IntArray(2).also(root::getLocationOnScreen)
        val at = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(at, at, MotionEvent.ACTION_DOWN,
            r.exactCenterX() - position[0], r.exactCenterY() - position[1], 0)
        try { assertTrue(root.dispatchTouchEvent(down)) } finally { down.recycle() }
        if (release) {
            val up = MotionEvent.obtain(at, at + 16, MotionEvent.ACTION_UP,
                r.exactCenterX() - position[0], r.exactCenterY() - position[1], 0)
            try { assertTrue(root.dispatchTouchEvent(up)) } finally { up.recycle() }
        }
        return true
    }

    private fun shown(root: View, tag: String): View {
        val matches = descendants(root).filter { it.tag == tag && it.isShown }
        assertEquals("Exactly one visible $tag", 1, matches.size)
        return matches.single()
    }

    private fun avatarRegion(head: ContinuumHologramView, name: String): Rect =
        Rect(ContinuumHologramView::class.java.getDeclaredField(name)
            .apply { isAccessible = true }.get(head) as Rect)

    private fun avatarPose(head: ContinuumHologramView) =
        (ContinuumHologramView::class.java.getDeclaredField("avatarPose")
            .apply { isAccessible = true }.get(head) as AvatarPoseReducer).pose()

    private fun assertProtectedRegions(work: View, head: ContinuumHologramView) {
        val headPosition = IntArray(2).also { head.getLocationOnScreen(it) }
        val workPosition = IntArray(2).also { work.getLocationOnScreen(it) }
        val bitmap = Bitmap.createBitmap(work.width, work.height, Bitmap.Config.ARGB_8888)
        try {
            work.draw(Canvas(bitmap))
            val home = head.parent as? at.heinz.deviceai.ui.ContinuumHomeView
            val shell = home?.jarvisShell?.currentShell()
            val face = shell?.protectedFaceBounds?.takeIf { shell.workstation }
            val regions = if (face != null && home != null) {
                val origin = IntArray(2).also { home.getLocationOnScreen(it) }
                val density = home.resources.displayMetrics.density
                listOf("protectedFaceBounds" to Rect((face.left * density).toInt(),
                    (face.top * density).toInt(), (face.right * density).toInt(), (face.bottom * density).toInt()).apply {
                    offset(origin[0] - workPosition[0], origin[1] - workPosition[1])
                })
            } else listOf("cockpitHeadBounds", "cockpitOrbBounds").map { name ->
                name to avatarRegion(head, name).apply {
                    offset(headPosition[0] - workPosition[0], headPosition[1] - workPosition[1])
                }
            }
            for ((name, region) in regions) {
                assertFalse("Protected region exists: $name", region.isEmpty)
                val viewport = Rect(0, 0, work.width, work.height)
                if (!region.intersect(viewport)) {
                    assertFalse("The scrolled region is outside the visible work viewport",
                        Rect.intersects(region, viewport))
                    continue
                }
                for (y in region.top + 1 until region.bottom - 1 step 7) {
                    for (x in region.left + 1 until region.right - 1 step 7) {
                        assertEquals("Work surface must leave $name transparent at $x/$y",
                            0, bitmap.getPixel(x, y).ushr(24))
                    }
                }
            }
        } finally { bitmap.recycle() }
    }

    private fun systemStatusScenes(a: AgentActivity, root: View, head: ContinuumHologramView,
                                   tag: String, w: Int, h: Int) {
        val originalHead = avatarRegion(head, "cockpitHeadBounds")
        val originalOrb = avatarRegion(head, "cockpitOrbBounds")
        val beforeDialog = ShadowAlertDialog.getLatestAlertDialog()
        val beforeMotion = head.sampleMotion()
        assertTrue("System tile receives a real touch", touch(root, JarvisHomeTestRoutes.destination(root, "system"), true))
        advance(80L); measure(root, w, h)
        val work = shown(root, "jarvis-work-window")
        val panel = shown(work, "jarvis-work-window:panel")
        assertSame("Work area uses the original window", root, work.rootView)
        assertSame("Systemstatus must not create AlertDialog", beforeDialog, ShadowAlertDialog.getLatestAlertDialog())
        fun phaseValue(name: String) = at.heinz.deviceai.ui.JarvisWorkWindow::class.java.getDeclaredField(name)
            .apply { isAccessible = true }.get(work) as Float
        val source = at.heinz.deviceai.ui.JarvisWorkWindow::class.java.getDeclaredField("origin")
            .apply { isAccessible = true }.get(work) as android.graphics.RectF
        val destination = at.heinz.deviceai.ui.JarvisWorkWindow::class.java.getDeclaredField("destination")
            .apply { isAccessible = true }.get(work) as android.graphics.RectF
        val anchor = phaseValue("trailProgress")
        assertTrue("Light anchor advances during its hold: $anchor", anchor > 0f && anchor < 1f)
        assertEquals("Flight waits for the anchor phase", 0f, phaseValue("openness"), .0001f)
        assertEquals("Anchor starts at the real source width", source.width() / panel.width, panel.scaleX, .001f)
        assertEquals("Anchor starts at the real source height", source.height() / panel.height, panel.scaleY, .001f)
        assertEquals("Anchor phase has no released data", 0f, panel.alpha, .001f)
        assertEquals("Frame waits for the anchor", 0f, phaseValue("stageOpacity"), .001f)
        assertProtectedRegions(work, head)
        capture(root, "$tag-6a-systemstatus-anchor", w, h)

        val bracketSampleMs = (HollywoodFuiConstruct.ANCHOR_END_MS + HollywoodFuiConstruct.BRACKETS_END_MS) / 2
        advance(bracketSampleMs - 80L); measure(root, w, h)
        val openness = phaseValue("openness")
        assertTrue("Opening advances during brackets: $openness", openness > 0f && openness < 1f)
        val brackets = phaseValue("stageOpacity")
        assertTrue("Brackets build before data release: $brackets", brackets > 0f && brackets < 1f)
        assertEquals("Width follows the actual source", (source.width() +
            (destination.width() - source.width()) * openness) / panel.width, panel.scaleX, .001f)
        assertEquals("Height follows the actual source", (source.height() +
            (destination.height() - source.height()) * openness) / panel.height, panel.scaleY, .001f)
        assertEquals("Content stays hidden until the cinematic data phase", 0f, panel.alpha, .001f)
        assertProtectedRegions(work, head)
        assertTrue("Opening reaches the existing avatar gaze", avatarPose(head).gazeDirected)
        assertFalse("Directed glance is not camera tracking", avatarPose(head).gazeObserved)
        capture(root, "$tag-6a-systemstatus-opening", w, h)

        val dataSampleMs = (HollywoodFuiConstruct.DATA_START_MS + HollywoodFuiConstruct.DATA_END_MS) / 2
        advance(dataSampleMs - bracketSampleMs); measure(root, w, h)
        assertTrue("Data releases only in its phase", panel.alpha > 0f && panel.alpha < 1f)
        assertTrue("Flight continues forward", phaseValue("openness") > openness)
        assertProtectedRegions(work, head)
        capture(root, "$tag-6a-systemstatus-data-release", w, h)

        advance(HollywoodFuiConstruct.DATA_END_MS - dataSampleMs + 140L)
        val io = field(a, "learningIoExecutor") as ExecutorService
        io.submit(Runnable {}).get(5L, TimeUnit.SECONDS)
        advance(64L); measure(root, w, h)
        assertTrue("Work area remains visible after opening", work.isShown)
        assertEquals(1f, panel.alpha, .001f)
        assertEquals(1f, panel.scaleX, .001f)
        assertEquals(1f, panel.scaleY, .001f)
        val panelBounds = Rect()
        assertTrue(panel.getGlobalVisibleRect(panelBounds))
        assertEquals("Open panel width is visible", panel.width, panelBounds.width())
        assertEquals("Open panel height is visible", panel.height, panelBounds.height())
        val labels = descendants(work).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue("Real title is present", labels.any { it.equals("Systemstatus", true) })
        assertFalse("Real status callback must complete", labels.any { it.contains("Status wird gelesen") })
        val status = labels.joinToString("\n")
        assertTrue("Actual build version must be displayed",
            status.contains("Device AI ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"))
        for (label in listOf("Shizuku", "Internetzugang", "Sprachmodell", "Werkzeugquittungen")) {
            assertTrue("Real status includes $label", status.contains(label))
        }
        assertFalse("Status inspection starts no model task", field(a, "busy") as Boolean)
        assertSame("Original Hologram instance survives opening", head, field(a, "hologramView"))
        assertEquals(1, descendants(root).count { it is ContinuumHologramView })
        assertTrue(head.isAttachedToWindow && head.isShown)
        assertTrue("Cockpit retains window focus", a.hasWindowFocus())
        assertTrue("Avatar keeps advancing", head.sampleMotion().callbackCount > beforeMotion.callbackCount)
        assertEquals(originalHead, avatarRegion(head, "cockpitHeadBounds"))
        assertEquals(originalOrb, avatarRegion(head, "cockpitOrbBounds"))
        assertProtectedRegions(work, head)
        capture(root, "$tag-6b-systemstatus-open", w, h)

        val scrolling = shown(work, "jarvis-work-window:scroll") as ScrollView
        val modelLabel = descendants(work).filterIsInstance<TextView>()
            .single { it.text.toString() == "Sprachmodell" }
        val modelCard = modelLabel.parent as View
        val shell = (field(a, "homeView") as ContinuumHomeView).jarvisShell
        val shellBefore = IntArray(2).also { shell.getLocationInWindow(it) }
        val cardPosition = IntArray(2).also { modelCard.getLocationInWindow(it) }
        val scrollPosition = IntArray(2).also { scrolling.getLocationInWindow(it) }
        val previousScroll = scrolling.scrollY
        scrolling.scrollTo(0, scrolling.scrollY + cardPosition[1] - scrollPosition[1])
        advance(32L); measure(root, w, h)
        assertTrue("Actual inner status scroll advances", scrolling.scrollY > previousScroll)
        assertTrue("Inner scroll keeps the work area open", work.isShown)
        assertTrue("Inner scroll preserves cockpit position",
            shellBefore.contentEquals(IntArray(2).also { shell.getLocationInWindow(it) }))
        val modelBounds = Rect()
        assertTrue("Actual model card is visible", modelCard.getGlobalVisibleRect(modelBounds))
        assertEquals("Model card is not vertically clipped", modelCard.height, modelBounds.height())
        assertEquals("Model card is not horizontally clipped", modelCard.width, modelBounds.width())
        val modelValues = descendants(modelCard).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue("Model card contains its actual label", modelValues.contains("Sprachmodell"))
        assertTrue("Model card contains an actual value", modelValues.any { it.isNotBlank() && it != "Sprachmodell" })
        assertSame(head, field(a, "hologramView"))
        assertTrue("Inner scrolling keeps focus", a.hasWindowFocus())
        assertProtectedRegions(work, head)
        capture(root, "$tag-6b-data-systemstatus-scrolled", w, h)
        scrolling.scrollTo(0, 0)
        advance(32L); measure(root, w, h)
        assertTrue("Returning inside content keeps work visible", work.isShown)

        val close = shown(work, "jarvis-work-window:close")
        assertTrue("Close remains accessible", close.isClickable && close.isFocusable && !close.contentDescription.isNullOrBlank())
        assertTrue("Close retains its minimum target", close.width / close.resources.displayMetrics.density >= 48f &&
            close.height / close.resources.displayMetrics.density >= 48f)
        assertTrue("Close receives a real touch", touch(root, close, true))
        advance(80L); measure(root, w, h)
        assertTrue("Closing remains visible during data fade", work.isShown)
        assertEquals("Data fades before the frame folds", 1f, panel.scaleX, .001f)
        assertTrue("Data starts fading during the first close phase", panel.alpha > 0f && panel.alpha < 1f)
        advance(160L); measure(root, w, h)
        val closingOpenness = phaseValue("openness")
        assertTrue("Closing frame returns toward its source: $closingOpenness",
            work.isShown && closingOpenness > 0f && closingOpenness < 1f)
        assertEquals("Data is hidden before return flight", 0f, panel.alpha, .001f)
        val closingFrame = phaseValue("stageOpacity")
        assertTrue("Frame remains during return flight: $closingFrame",
            closingFrame > 0f && closingFrame < 1f)
        assertEquals("Closing width follows the actual source", (source.width() +
            (destination.width() - source.width()) * closingOpenness) / panel.width, panel.scaleX, .001f)
        assertEquals("Closing height follows the actual source", (source.height() +
            (destination.height() - source.height()) * closingOpenness) / panel.height, panel.scaleY, .001f)
        assertEquals("Return flight approaches the source horizontally",
            source.left + (destination.left - source.left) * closingOpenness - panel.left,
            panel.translationX, .001f)
        assertTrue("Return flight changes actual geometry",
            kotlin.math.abs(panel.scaleY - 1f) > .001f || kotlin.math.abs(panel.translationX) > .1f ||
                kotlin.math.abs(panel.translationY) > .1f)
        assertProtectedRegions(work, head)
        capture(root, "$tag-6c-systemstatus-closing", w, h)

        advance(640L); measure(root, w, h)
        assertTrue("Close removes the shown work area", descendants(root).none { it.tag == "jarvis-work-window" && it.isShown })
        assertSame("Original Hologram instance survives closing", head, field(a, "hologramView"))
        assertTrue(head.isShown)
        assertEquals(originalHead, avatarRegion(head, "cockpitHeadBounds"))
        assertEquals(originalOrb, avatarRegion(head, "cockpitOrbBounds"))
        assertTrue(JarvisHomeTestRoutes.destination(root, "system").isClickable)
        assertEquals(ContinuumWorkArea.START, field(a, "activeWorkArea"))
        assertFalse("Closing clears the window glance", avatarPose(head).gazeDirected)
        capture(root, "$tag-6d-systemstatus-closed", w, h)

        if (w < 600) {
            assertTrue("Phone reopens through its real tile", touch(root, JarvisHomeTestRoutes.destination(root, "system"), true))
            advance(1_040L); measure(root, w, h)
            val reopened = shown(root, "jarvis-work-window")
            assertEquals("Closed work areas do not accumulate", 1,
                descendants(root).count { it.tag == "jarvis-work-window" })
            val home = field(a, "homeView") as ContinuumHomeView
            val viewport = generateSequence(home.parent as? View) { it.parent as? View }
                .filterIsInstance<ScrollView>().firstOrNull {
                    it.childCount > 0 && it.getChildAt(0).height > it.height
                }
            assertTrue("Phone retains a real scrolling cockpit", viewport != null)
            val scrolling = requireNotNull(viewport)
            val beforeScroll = scrolling.scrollY
            // The compact cockpit may already be at the lower scroll limit after opening
            // the system tile. Require a real scroll in either available direction.
            scrolling.scrollTo(scrolling.scrollX, beforeScroll + 32)
            if (scrolling.scrollY == beforeScroll) {
                scrolling.scrollTo(scrolling.scrollX, (beforeScroll - 32).coerceAtLeast(0))
            }
            assertTrue("The actual background scroll changed", scrolling.scrollY != beforeScroll)
            advance(32L)
            assertFalse("A moved background invalidates protected work bounds", reopened.isShown)
            assertTrue("Scroll closes the work area", descendants(root).none { it.tag == "jarvis-work-window" && it.isShown })
            assertFalse("Scroll clears the window glance", avatarPose(head).gazeDirected)
            assertSame(head, field(a, "hologramView"))
        }
    }

    private fun scenes(tag: String, w: Int, h: Int, font: Float) {
        val previousFont = RuntimeEnvironment.getFontScale()
        RuntimeEnvironment.setFontScale(font)
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit().putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit().clear().commit()
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        try {
            controller.setup().visible()
            advance(32)
            controller.windowFocusChanged(true)
            val a = controller.get()
            val root = a.window.decorView
            framePump = CockpitHostFramePump { controller.get().window.decorView }
            val head = field(a, "hologramView") as ContinuumHologramView
            val home = field(a, "homeView") as ContinuumHomeView
            val director = field(a, "avatarDirector") as AvatarDirector
            head.setAnimationsEnabled(true); head.setForegroundActive(true); home.setForegroundActive(true)
            measure(root, w, h); advance(1_200); measure(root, w, h)
            capture(root, "$tag-1-start", w, h)

            director.onRuntime(AvatarRuntimeSignals(listening = true)); head.setState(ContinuumHologramView.State.LISTENING)
            advance(900); capture(root, "$tag-2-listening", w, h)

            director.onRuntime(AvatarRuntimeSignals(busy = true)); head.setState(ContinuumHologramView.State.THINKING)
            advance(900); capture(root, "$tag-3-thinking", w, h)

            // Scripted TTS callbacks through the real Director (word ranges; orb level as the TTS signal would set it).
            director.onRuntime(AvatarRuntimeSignals(speaking = true)); head.setState(ContinuumHologramView.State.SPEAKING)
            val text = "Guten Abend Heinz, das Cockpit ist bereit."
            director.speechStarted("evidence-1", text)
            var cursor = 0
            for (word in Regex("\\S+").findAll(text).take(3)) {
                director.wordRange("evidence-1", word.range.first, word.range.last + 1)
                repeat(10) { director.audioLevel("evidence-1", 0f, measuredPcmAvailable = false); head.setAudioLevel(.55f + .3f * (it % 3) / 2f); advance(33) }
                cursor = word.range.last
            }
            assertTrue(cursor > 0)
            capture(root, "$tag-4-speaking", w, h)
            director.speechEnded("evidence-1", interrupted = false)
            director.onRuntime(AvatarRuntimeSignals()); head.setState(ContinuumHologramView.State.READY)
            advance(900)

            val memory = JarvisHomeTestRoutes.destination(root, "memory")
            assertTrue("Memory tile receives a real press", touch(root, memory, release = false))
            advance(120)
            capture(root, "$tag-5-tile-pressed", w, h)
            val at = SystemClock.uptimeMillis()
            val cancel = MotionEvent.obtain(at, at, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
            try { root.dispatchTouchEvent(cancel) } finally { cancel.recycle() }
            advance(300)
            systemStatusScenes(a, root, head, tag, w, h)
        } finally {
            framePump?.close()
            framePump = null
            ShadowAlertDialog.getLatestAlertDialog()?.takeIf { it.isShowing }?.dismiss()
            runCatching { controller.close() }
            RuntimeEnvironment.setFontScale(previousFont)
        }
    }

    /**
     * Motion clip of the real cockpit as numbered frames (15 fps) for ffmpeg: idle orb/rim motion, listening,
     * thinking, speaking (scripted TTS word ranges), a tile press and the Systemstatus window opening and closing.
     * Host canvas with the 2D head preview, not Filament. Runs only with -PcontinuumUiEvidenceDir=… .
     */
    private fun motionClip(tag: String, w: Int, h: Int) {
        val dir = System.getProperty("continuum.ui.evidenceDir")?.let { File(it, "motion-$tag").apply { mkdirs() } }
        org.junit.Assume.assumeTrue("clip frames are evidence only", dir != null)
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit().putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit().clear().commit()
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        var index = 0
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            controller.setup().visible()
            advance(32)
            controller.windowFocusChanged(true)
            val a = controller.get()
            val root = a.window.decorView
            framePump = CockpitHostFramePump { controller.get().window.decorView }
            val head = field(a, "hologramView") as ContinuumHologramView
            val home = field(a, "homeView") as ContinuumHomeView
            val director = field(a, "avatarDirector") as AvatarDirector
            head.setAnimationsEnabled(true); head.setForegroundActive(true); home.setForegroundActive(true)
            measure(root, w, h); advance(600); measure(root, w, h)
            fun record(ms: Long, each: (Long) -> Unit = {}) {
                var t = 0L
                while (t < ms) {
                    each(t); advance(66L); t += 66L; measure(root, w, h)
                    bitmap.eraseColor(0); root.draw(Canvas(bitmap))
                    File(dir, "frame-%04d.png".format(index++)).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
            val marks = StringBuilder()
            fun mark(label: String) { marks.append("%.2f".format(java.util.Locale.ROOT, index / 15.0)).append(" s ").append(label).append('\n') }
            mark("Ruhe: Kugel, Ringe, Kachel-Randlicht"); record(2_400)
            director.onRuntime(AvatarRuntimeSignals(listening = true)); head.setState(ContinuumHologramView.State.LISTENING)
            mark("Zuhören"); record(1_400)
            director.onRuntime(AvatarRuntimeSignals(busy = true)); head.setState(ContinuumHologramView.State.THINKING)
            mark("Denken"); record(1_800)
            director.onRuntime(AvatarRuntimeSignals(speaking = true)); head.setState(ContinuumHologramView.State.SPEAKING)
            val text = "Guten Abend Heinz, das Cockpit ist bereit und hört dir zu."
            director.speechStarted("clip-1", text)
            val words = Regex("\\S+").findAll(text).toList()
            mark("Sprechen (geskriptete TTS-Wortbereiche)")
            record(2_600) { t ->
                val word = (t / 330).toInt()
                if (t % 330 < 66 && word < words.size) director.wordRange("clip-1", words[word].range.first, words[word].range.last + 1)
                director.audioLevel("clip-1", 0f, measuredPcmAvailable = false)
                head.setAudioLevel(.45f + .4f * ((t / 66) % 3) / 2f)
            }
            director.speechEnded("clip-1", interrupted = false)
            director.onRuntime(AvatarRuntimeSignals()); head.setState(ContinuumHologramView.State.READY)
            mark("Ruhe nach dem Sprechen"); record(1_000)
            val memory = JarvisHomeTestRoutes.destination(root, "memory")
            if (touch(root, memory, release = false)) {
                mark("Kachel berührt"); record(400)
                val at = SystemClock.uptimeMillis()
                val cancel = MotionEvent.obtain(at, at, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
                try { root.dispatchTouchEvent(cancel) } finally { cancel.recycle() }
                record(330)
            }
            if (touch(root, JarvisHomeTestRoutes.destination(root, "system"), true)) {
                mark("Systemstatus öffnet (echte Statusabfrage)"); record(1_200)
                (field(a, "learningIoExecutor") as ExecutorService).submit(Runnable {}).get(5L, TimeUnit.SECONDS)
                record(900)
                val work = descendants(root).firstOrNull { it.tag == "jarvis-work-window" && it.isShown }
                val close = work?.let { w0 -> descendants(w0).firstOrNull { it.tag == "jarvis-work-window:close" && it.isShown } }
                if (close != null && touch(root, close, true)) { mark("Systemstatus schließt"); record(1_300) }
            }
            // V1 scene (SIMULATED_TOOL_INPUT): an installed app fixture in Robolectric's PackageManager; the scene is
            // invoked with the ToolGate runner's argument shape, no real tool runs and no app opens on the host.
            val pkg = "org.example.notes"
            val pm = org.robolectric.Shadows.shadowOf(app.packageManager)
            pm.installPackage(android.content.pm.PackageInfo().apply {
                packageName = pkg
                applicationInfo = android.content.pm.ApplicationInfo().apply { packageName = pkg; nonLocalizedLabel = "Notizen Beispiel" }
            })
            val main = android.content.ComponentName(pkg, "$pkg.Main")
            pm.addActivityIfNotPresent(main)
            pm.addIntentFilterForActivity(main, android.content.IntentFilter(android.content.Intent.ACTION_MAIN)
                .apply { addCategory(android.content.Intent.CATEGORY_LAUNCHER) })
            pm.setApplicationIcon(pkg, android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                colors = intArrayOf(0xFF2AEAFC.toInt(), 0xFF3D7BFF.toInt(), 0xFFC46BFF.toInt())
            })
            fun scene(name: String, vararg args: Any) = AgentActivity::class.java.declaredMethods.single { it.name == name }
                .apply { isAccessible = true }.invoke(a, *args)
            mark("App-Auftrag: echtes Symbol fliegt ein, Deck räumt (SIMULATED_TOOL_INPUT)")
            scene("presentToolScene", "clip-app", "launch_app", mapOf<String, Any?>("packageOrName" to pkg))
            record(1_500)
            director.onRuntime(AvatarRuntimeSignals(speaking = true)); head.setState(ContinuumHologramView.State.SPEAKING)
            val say = "Ich öffne Notizen Beispiel für dich."
            director.speechStarted("clip-2", say)
            val sayWords = Regex("\\S+").findAll(say).toList()
            mark("JARVIS spricht während der Arbeit")
            record(1_700) { t ->
                val word = (t / 330).toInt()
                if (t % 330 < 66 && word < sayWords.size) director.wordRange("clip-2", sayWords[word].range.first, sayWords[word].range.last + 1)
                director.audioLevel("clip-2", 0f, measuredPcmAvailable = false)
                head.setAudioLevel(.45f + .4f * ((t / 66) % 3) / 2f)
            }
            director.speechEnded("clip-2", interrupted = false)
            director.onRuntime(AvatarRuntimeSignals()); head.setState(ContinuumHologramView.State.READY)
            scene("finishToolScene", "clip-app", true)
            mark("Werkzeug hat geantwortet · Fenster faltet zurück, Deck kehrt zurück"); record(3_000)
            mark("Ende"); File(dir, "marks.txt").writeText(marks.toString())
            assertTrue("clip has frames", index > 100)
        } finally {
            bitmap.recycle()
            framePump?.close()
            framePump = null
            runCatching { controller.close() }
        }
    }

    @Test @Config(qualifiers = "w884dp-h760dp-land-mdpi")
    fun fold7InnerMotionClip() = motionClip("fold7-inner-884x760", 884, 760)

    @Test @Config(qualifiers = "w884dp-h760dp-land-mdpi")
    fun fold7Inner() = scenes("fold7-inner-884x760", 884, 760, 1f)

    @Test @Config(qualifiers = "w884dp-h760dp-land-mdpi")
    fun fold7InnerLargerFont() = scenes("fold7-inner-884x760-font130", 884, 760, 1.3f)

    @Test @Config(qualifiers = "w412dp-h915dp-mdpi")
    fun fold7CoverAndPhone() = scenes("fold7-cover-phone-412x915", 412, 915, 1f)

    @Test @Config(qualifiers = "w1536dp-h1024dp-land-mdpi")
    fun referenceTablet() = scenes("reference-1536x1024", 1536, 1024, 1f)
}
