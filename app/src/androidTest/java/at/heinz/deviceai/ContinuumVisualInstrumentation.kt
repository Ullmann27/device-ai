package at.heinz.deviceai

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
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
            putString("status", if (failure == null) "ok" else "failed")
            putString("stream", if (failure == null)
                "Captured dashboard and six states using capture_mode=$captureMode " +
                    "(${if (captureMode == "view") "native view rendering, not display screenshots" else "display screenshots"}): " +
                    "${paths.joinToString()}\n"
            else "Visual capture failed: ${failure.stackTraceToString()}\n")
        }
        finish(if (failure == null) Activity.RESULT_OK else Activity.RESULT_CANCELED, results)
    }

    private fun capture(directory: File, name: String, activity: Activity): String {
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

    private fun findOrb(view: View): ContinuumHologramView? {
        if (view is ContinuumHologramView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findOrb(view.getChildAt(index))?.let { return it }
            }
        }
        return null
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
