package at.heinz.deviceai

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MayaVoiceSettingsUiTest {
    private fun call(activity: AgentActivity, name: String) =
        AgentActivity::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(activity)

    private fun field(view: View): EditText? = when (view) {
        is EditText -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { field(view.getChildAt(it)) }
        else -> null
    }

    @Test fun mayaSetupIsReachableWithoutAndroidTtsAndNeverAutoGeneratesASample() {
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        val activity = controller.get()
        activity.getSharedPreferences("continuum-voice", Context.MODE_PRIVATE).edit().clear().commit()
        activity.getSharedPreferences("device_ai_secure", Context.MODE_PRIVATE).edit().clear().commit()
        try {
            call(activity, "showVoicePicker")
            val picker = ShadowAlertDialog.getLatestAlertDialog()
            assertTrue(picker.listView.adapter.getItem(0).toString().contains("Maya"))
            picker.listView.performItemClick(null, 0, 0L)
            val settings = ShadowAlertDialog.getLatestAlertDialog()
            val input = field(settings.window!!.decorView)!!
            assertFalse(input.isSaveEnabled)
            assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO, input.importantForAutofill)
            assertTrue(settings.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            assertFalse(activity.getSharedPreferences("continuum-voice", Context.MODE_PRIVATE)
                .getBoolean("maya_enabled", false))
            assertNull(SecureTokenStore.loadElevenLabsToken(activity))
            val speech = AgentActivity::class.java.getDeclaredField("mayaSpeech").apply { isAccessible = true }
            assertNull(speech.get(activity))
            input.setText("invalid")
            settings.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertTrue(settings.isShowing)
            assertNotNull(input.error)
            assertNull(speech.get(activity))
            settings.dismiss()
            assertEquals("", input.text.toString())
        } finally {
            ShadowAlertDialog.getLatestAlertDialog()?.dismiss()
            runCatching { controller.close() }
        }
    }
}
