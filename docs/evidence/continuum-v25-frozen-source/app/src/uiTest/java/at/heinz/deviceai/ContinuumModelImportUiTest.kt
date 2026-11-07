package at.heinz.deviceai

import android.app.AlertDialog
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContinuumModelImportUiTest {
    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) }
        else emptyList()

    @Test fun selectedGemmaImportKeepsCorrectionsVisibleAndOnlyOpensManualPicker() {
        // Attach the real activity without starting unrelated training/services.
        val activity = Robolectric.buildActivity(AgentActivity::class.java).get()
        AgentActivity::class.java.getDeclaredMethod("showModelImportDialog", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(activity, true)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        try {
            val children = views(dialog.findViewById(android.R.id.content))
            val fields = children.filterIsInstance<EditText>()
            assertEquals(3, fields.size)
            assertEquals("gemma4-e4b-it", fields[0].text.toString())
            assertEquals("official-0b2a8980", fields[1].text.toString())
            assertEquals("0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0", fields[2].text.toString())
            fields[2].setText("bad-sha")
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertTrue(dialog.isShowing)
            assertTrue(children.filterIsInstance<TextView>().any {
                it.text.toString().contains("Bitte fülle alle drei Felder korrekt aus")
            })
            assertNull(shadowOf(activity).nextStartedActivity)
            fields[2].setText("0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0")
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            val picker = shadowOf(activity).nextStartedActivity
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, picker.action)
            assertTrue(picker.categories.contains(Intent.CATEGORY_OPENABLE))
            assertEquals("application/octet-stream", picker.type)
            assertFalse(dialog.isShowing)
        } finally { dialog.dismiss() }
    }
}
