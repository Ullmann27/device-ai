package at.heinz.deviceai

import android.app.AlertDialog
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

/**
 * Import prefill for the smaller Gemma 4 E2B: the real dialog carries the published identity, promises nothing
 * about speed or quality on the device, and still only opens the manual file picker. The existing one-argument
 * entry point (used by ContinuumModelImportUiTest) keeps working next to it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContinuumFastModelImportUiTest {
    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) }
        else emptyList()

    private fun openDialog(vararg arguments: Boolean): Pair<AgentActivity, AlertDialog> {
        val activity = Robolectric.buildActivity(AgentActivity::class.java).get()
        val types = Array<Class<*>>(arguments.size) { Boolean::class.javaPrimitiveType!! }
        AgentActivity::class.java.getDeclaredMethod("showModelImportDialog", *types)
            .apply { isAccessible = true }.invoke(activity, *arguments.toTypedArray())
        shadowOf(android.os.Looper.getMainLooper()).idle()
        return activity to ShadowAlertDialog.getLatestAlertDialog()
    }

    @Test fun fastGemmaImportIsPrefilledWithThePublishedE2bIdentityAndOnlyOpensManualPicker() {
        val (activity, dialog) = openDialog(false, true)
        try {
            val children = views(dialog.findViewById(android.R.id.content))
            val fields = children.filterIsInstance<EditText>()
            assertEquals(3, fields.size)
            assertEquals("gemma4-e2b-it", fields[0].text.toString())
            assertEquals("official-18193810", fields[1].text.toString())
            assertEquals("181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c", fields[2].text.toString())
            val texts = children.filterIsInstance<TextView>().map { it.text.toString() }
            assertTrue(texts.any { it.contains("Gemma 4 E2B") && it.contains("nicht gemessen") })
            assertFalse("the E4B note belongs to the other prefill", texts.any { it.contains("Gemma-Angaben sind vorausgefüllt") })
            assertNull(shadowOf(activity).nextStartedActivity)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            val picker = shadowOf(activity).nextStartedActivity
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, picker.action)
            assertEquals("application/octet-stream", picker.type)
            assertFalse(dialog.isShowing)
        } finally { dialog.dismiss() }
    }

    @Test fun theOneArgumentEntryPointStillPrefillsTheSelectedE4bOrNothing() {
        val (_, selected) = openDialog(true)
        try {
            val fields = views(selected.findViewById(android.R.id.content)).filterIsInstance<EditText>()
            assertEquals(ContinuumSelectedModels.LOCAL_ID, fields[0].text.toString())
            assertEquals(ContinuumSelectedModels.LOCAL_SHA256, fields[2].text.toString())
        } finally { selected.dismiss() }
        val (_, manual) = openDialog(false)
        try {
            val fields = views(manual.findViewById(android.R.id.content)).filterIsInstance<EditText>()
            assertEquals(listOf("", "", ""), fields.map { it.text.toString() })
        } finally { manual.dismiss() }
    }
}
