package at.heinz.deviceai

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
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

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        runLabel = arguments?.getString("run_label")?.takeIf {
            it.matches(Regex("[a-zA-Z0-9_-]{1,40}"))
        } ?: "default"
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
            runOnMainSync {
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
            paths += capture(outputDirectory, "dashboard.png")

            for (state in ContinuumHologramView.State.entries) {
                runOnMainSync {
                    checkNotNull(orb).setState(state)
                    label?.text = if (state == ContinuumHologramView.State.SPEAKING)
                        "VISUELLER TEST · SPEAKING · PCM-Testpegel"
                    else "VISUELLER TEST · ${state.name}"
                }
                if (state == ContinuumHologramView.State.SPEAKING) {
                    // Synthetic, constant test input proves amplitude rendering, not TTS playback.
                    repeat(10) {
                        runOnMainSync { checkNotNull(orb).setAudioLevel(.56f) }
                        SystemClock.sleep(42)
                    }
                } else {
                    SystemClock.sleep(420)
                }
                paths += capture(outputDirectory, "state-${state.name.lowercase(Locale.ROOT)}.png")
            }
        } catch (error: Throwable) {
            failure = error
        } finally {
            try {
                runOnMainSync {
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
            putStringArrayList("screenshots", paths)
            putInt("capture_count", paths.size)
            putString("run_label", runLabel)
            putFloat("font_scale", targetContext.resources.configuration.fontScale)
            putString("speaking_input", "Synthetic normalized PCM RMS 0.56; no real TTS claim")
            putString("status", if (failure == null) "ok" else "failed")
            putString("stream", if (failure == null)
                "Captured actual dashboard and six native renderer states: ${paths.joinToString()}\n"
            else "Visual capture failed: ${failure.stackTraceToString()}\n")
        }
        finish(if (failure == null) Activity.RESULT_OK else Activity.RESULT_CANCELED, results)
    }

    private fun capture(directory: File, name: String): String {
        val bitmap = checkNotNull(uiAutomation.takeScreenshot()) { "UiAutomation returned no screenshot" }
        val file = File(directory, name)
        try {
            FileOutputStream(file).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Could not encode screenshot $name"
                }
            }
        } finally {
            bitmap.recycle()
        }
        check(file.length() > 0L) { "Screenshot $name is empty" }
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
}
