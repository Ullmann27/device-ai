package at.heinz.deviceai

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Switch
import at.heinz.deviceai.ui.Command03AppearanceSettings
import at.heinz.deviceai.ui.ContinuumSettingsHub
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w320dp-h740dp-port-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class ContinuumSettingsHubUiTest {
    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup)
        (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()

    private fun layout(root: View, width: Int = 320) {
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(740, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, 740)
        shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
    }

    @Test fun menuKeepsEverySettingReadableAndSearchSelectsOnlyTheChosenEntry() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val selected = mutableListOf<ContinuumSettingsHub.Entry>()
        val dialog = ContinuumSettingsHub.show(activity.get()) { selected.add(it) }
        try {
            layout(dialog.window!!.decorView)
            val buttons = views(dialog.window!!.decorView).filter { it.tag?.toString()?.startsWith("continuum-setting:") == true }
            assertEquals(ContinuumSettingsHub.Entry.entries.size, buttons.size)
            for (button in buttons) {
                assertTrue(button.width > 100)
                assertTrue(button.height >= 44)
                assertTrue(button.isClickable && button.isFocusable)
                assertTrue(button.contentDescription.isNotBlank())
            }
            val search = views(dialog.window!!.decorView).single { it.tag == "continuum-settings-search" } as EditText
            search.setText("OpenRouter")
            layout(dialog.window!!.decorView)
            val results = views(dialog.window!!.decorView).filter { it.tag?.toString()?.startsWith("continuum-setting:") == true }
            assertEquals(1, results.size)
            results.single().performClick()
            assertEquals(listOf(ContinuumSettingsHub.Entry.OPENROUTER), selected)
            assertFalse(dialog.isShowing)
        } finally {
            dialog.dismiss()
            activity.pause().stop().destroy()
        }
    }

    @Test fun appearanceControlsPersistActualOwnerChangesAndRestoreDefaults() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val activity = controller.get()
        val prefs = activity.getSharedPreferences("continuum-command03", Context.MODE_PRIVATE)
        prefs.edit().putInt("neon_light", 25).putBoolean("neon_motion", true).commit()
        var updates = 0
        val dialog = Command03AppearanceSettings.show(activity) { updates++ }
        try {
            layout(dialog.window!!.decorView)
            val slider = views(dialog.window!!.decorView).single { it.tag == "command03-neon-intensity" } as SeekBar
            assertEquals(25, slider.progress)
            val x = slider.width * .85f
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(0, 20, action, x, slider.height / 2f, 0)
                slider.dispatchTouchEvent(event)
                event.recycle()
            }
            val saved = Command03AppearanceSettings.snapshot(activity)
            assertTrue("Touch changes the persisted light level", saved.getInt("neon") > 70)
            val motion = views(dialog.window!!.decorView).single { it.tag == "command03-neon-motion" } as Switch
            motion.performClick()
            assertFalse(Command03AppearanceSettings.snapshot(activity).getBoolean("lightMotion"))
            assertTrue(updates >= 2)
            dialog.dismiss()
            val reopened = Command03AppearanceSettings.show(activity) { updates++ }
            try {
                layout(reopened.window!!.decorView)
                val restored = views(reopened.window!!.decorView).single { it.tag == "command03-neon-intensity" } as SeekBar
                assertEquals(saved.getInt("neon"), restored.progress)
                views(reopened.window!!.decorView).filterIsInstance<android.widget.TextView>()
                    .single { it.text.toString() == "STANDARD WIEDERHERSTELLEN" }.performClick()
                assertEquals(70, Command03AppearanceSettings.snapshot(activity).getInt("neon"))
                assertTrue(Command03AppearanceSettings.snapshot(activity).getBoolean("lightMotion"))
            } finally { reopened.dismiss() }
        } finally {
            dialog.dismiss()
            controller.pause().stop().destroy()
        }
    }
}
