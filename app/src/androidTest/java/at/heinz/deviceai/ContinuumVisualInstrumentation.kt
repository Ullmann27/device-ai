package at.heinz.deviceai

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/** Captures the actual app and renderer. No production launcher or debug hooks. */
class ContinuumVisualInstrumentation : Instrumentation() {
    private var runLabel = "default"
    private var captureMode = "display"

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        runLabel = arguments?.getString("run_label")?.takeIf {
            it.matches(Regex("[a-zA-Z0-9_-]{1,40}"))
        } ?: "default"
        captureMode = if (arguments?.getString("capture_mode") == "view") "view" else "display"
        start()
    }

    // Instrumentation.start dispatches onStart on its worker thread.
    override fun onStart() {
        super.onStart()
        val paths = arrayListOf<String>()
        var activity: Activity? = null
        var orb: ContinuumHologramView? = null
        var label: TextView? = null
        var originalLabel: CharSequence? = null
        var homeOrbHeight = 0
        var homeRoundTripPassed = false
        var failure: Throwable? = null
        try {
            val outputDirectory = File(checkNotNull(targetContext.getExternalFilesDir(null)),
                "visual-review/$runLabel")
            check(outputDirectory.isDirectory || outputDirectory.mkdirs()) {
                "Could not create screenshot directory"
            }
            activity = startActivitySync(Intent(targetContext, AgentActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            waitForIdleSync()
            onMain {
                orb = findOrb(activity.window.decorView)
                val actualOrb = checkNotNull(orb) { "The real dashboard has no native hologram" }
                check(actualOrb.isShown && actualOrb.width > 0 && actualOrb.height > 0) {
                    "The real dashboard hologram is not visible or measured"
                }
                assertOrbIsVisible(actualOrb)
                homeOrbHeight = actualOrb.height
                val parent = actualOrb.parent as? ViewGroup
                label = parent?.getChildAt(parent.indexOfChild(actualOrb) + 1) as? TextView
                originalLabel = label?.text
            }
            SystemClock.sleep(420)
            paths += capture(outputDirectory, "dashboard", activity)

            for (state in ContinuumHologramView.State.entries) {
                onMain {
                    checkNotNull(orb).setState(state)
                    label?.text = if (state == ContinuumHologramView.State.SPEAKING)
                        "VISUELLER TEST · SPEAKING · PCM-Testpegel"
                    else "VISUELLER TEST · ${state.name}"
                }
                if (state == ContinuumHologramView.State.SPEAKING) {
                    // Synthetic, constant test input proves amplitude rendering, not TTS playback.
                    repeat(10) {
                        onMain { checkNotNull(orb).setAudioLevel(.56f) }
                        SystemClock.sleep(42)
                    }
                } else {
                    SystemClock.sleep(420)
                }
                paths += capture(outputDirectory, "state-${state.name.lowercase(Locale.ROOT)}", activity)
            }

            val testResult = "Darstellungstest: Diese Beispielantwort prüft nur die sichtbare " +
                "Unterhaltung. Es wurde keine KI-Antwort erzeugt."
            onMain {
                checkNotNull(orb).setState(ContinuumHologramView.State.READY)
                label?.text = "VISUELLER TEST · Beispielantwort"
                AgentActivity::class.java.getDeclaredMethod("addAssistantMessage", String::class.java)
                    .apply { isAccessible = true }.invoke(activity, testResult)
            }
            SystemClock.sleep(420)
            var conversationScroll: ScrollView? = null
            onMain {
                val message = checkNotNull(findView(activity.window.decorView) {
                    it is TextView && it.text.toString() == testResult
                }) { "The assistant test result is missing from the real view tree" }
                conversationScroll = checkNotNull(scrollParent(message)) {
                    "The assistant test result has no chat ScrollView"
                }
                val chatContainer = checkNotNull(conversationScroll).getChildAt(0)
                assertVisible(chatContainer, "Chat container")
                assertVisible(message, "Assistant test result", 95)
                assertOrbIsVisible(checkNotNull(orb))
                check(checkNotNull(orb).height <= homeOrbHeight) { "Conversation orb grew beyond the home orb" }
            }
            paths += capture(outputDirectory, "conversation-test", activity)
            onMain {
                val start = checkNotNull(findView(activity.window.decorView) {
                    it is TextView && it.contentDescription?.toString() ==
                        "Zur Startseite mit allen KI-Bereichen"
                }) { "The conversation has no START control" }
                assertVisible(start, "START control")
                check(start.isClickable && start.performClick()) { "START could not be clicked" }
            }
            SystemClock.sleep(420)
            onMain {
                val actualOrb = checkNotNull(orb)
                assertOrbIsVisible(actualOrb)
                check(actualOrb.height == homeOrbHeight) { "START did not restore the full home orb" }
                val tile = checkNotNull(findView(activity.window.decorView) {
                    it.isClickable && it.contentDescription?.toString()?.startsWith("Chat. ") == true
                }) { "START did not restore the home Chat tile" }
                assertVisible(tile, "Home Chat tile")
                val homeScroll = checkNotNull(scrollParent(tile)) { "Home Chat tile has no home ScrollView" }
                check(homeScroll !== conversationScroll && scrollParent(actualOrb) === homeScroll) {
                    "The restored Chat tile is not in the home dashboard scroll tree"
                }
                homeRoundTripPassed = true
            }
        } catch (error: Throwable) {
            failure = error
        } finally {
            try {
                onMain {
                    orb?.setState(ContinuumHologramView.State.READY)
                    label?.text = originalLabel
                    activity?.finish()
                }
                waitForIdleSync()
            } catch (cleanupError: Throwable) {
                if (failure == null) failure = cleanupError
                else failure.addSuppressed(cleanupError)
            }
        }
        val results = Bundle().apply {
            putStringArrayList("captures", paths)
            if (captureMode == "display") putStringArrayList("screenshots", paths)
            putInt("capture_count", paths.size)
            putString("run_label", runLabel)
            putString("capture_mode", captureMode)
            putString("evidence_type", if (captureMode == "view")
                "Native measured decor rendered into a bitmap; not a display screenshot"
            else "UiAutomation display screenshot")
            putFloat("font_scale", targetContext.resources.configuration.fontScale)
            putString("speaking_input", "Synthetic normalized PCM RMS 0.56; no real TTS claim")
            putString("conversation_input", "Synthetic Darstellungstest message via existing UI method; no generation")
            putString("home_roundtrip", if (homeRoundTripPassed) "passed" else "not completed")
            putString("status", if (failure == null) "ok" else "failed")
            putString("stream", if (failure == null)
                "Captured dashboard, six states and visible conversation; START restored the home dashboard. capture_mode=$captureMode " +
                    "(${if (captureMode == "view") "native view rendering, not display screenshots" else "display screenshots"}): " +
                    "${paths.joinToString()}\n"
            else "Visual capture failed: ${failure.stackTraceToString()}\n")
        }
        finish(if (failure == null) Activity.RESULT_OK else Activity.RESULT_CANCELED, results)
    }

    private fun capture(directory: File, name: String, activity: Activity): String {
        onMain {
            assertOrbIsVisible(checkNotNull(findOrb(activity.window.decorView)))
        }
        val bitmap = if (captureMode == "view") {
            var rendered: Bitmap? = null
            onMain {
                val decor = activity.window.decorView
                check(decor.width > 0 && decor.height > 0) { "The actual decor has no measured size" }
                val target = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
                rendered = target
                target.density = decor.resources.displayMetrics.densityDpi
                try {
                    decor.draw(Canvas(target))
                } catch (error: Throwable) {
                    target.recycle()
                    throw error
                }
            }
            checkNotNull(rendered) { "The measured native decor was not rendered" }
        } else {
            checkNotNull(uiAutomation.takeScreenshot()) { "UiAutomation returned no screenshot" }
        }
        val file = File(directory, if (captureMode == "view") "$name-native-view.png" else "$name.png")
        try {
            FileOutputStream(file).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Could not encode capture $name"
                }
            }
        } finally {
            bitmap.recycle()
        }
        check(file.length() > 0L) { "Capture $name is empty" }
        return file.absolutePath
    }

    private fun assertOrbIsVisible(orb: ContinuumHologramView) {
        val visibleBounds = Rect()
        val visible = orb.getGlobalVisibleRect(visibleBounds)
        check(orb.height > 0 && visible && visibleBounds.height() * 100 >= orb.height * 95) {
            "Native hologram is clipped: visible height=${visibleBounds.height()}px, " +
                "measured height=${orb.height}px; at least 95% must be visible"
        }
    }

    private fun findOrb(view: View): ContinuumHologramView? {
        if (view is ContinuumHologramView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findOrb(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun findView(view: View, predicate: (View) -> Boolean): View? {
        if (predicate(view)) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findView(view.getChildAt(index), predicate)?.let { return it }
            }
        }
        return null
    }

    private fun scrollParent(view: View): ScrollView? {
        var parent = view.parent
        while (parent != null) {
            if (parent is ScrollView) return parent
            parent = parent.parent
        }
        return null
    }

    private fun assertVisible(view: View, description: String, percent: Int = 1) {
        val bounds = Rect()
        check(view.isShown && view.height > 0 && view.getGlobalVisibleRect(bounds) &&
            bounds.height() * 100 >= view.height * percent) {
            "$description is hidden or clipped: visible=${bounds.height()}px, measured=${view.height}px"
        }
    }

    /** Carry UI failures back to the worker so finish reports failure instead of hanging. */
    private fun onMain(action: () -> Unit) {
        var failure: Throwable? = null
        runOnMainSync {
            try {
                action()
            } catch (error: Throwable) {
                failure = error
            }
        }
        failure?.let { throw it }
    }
}
