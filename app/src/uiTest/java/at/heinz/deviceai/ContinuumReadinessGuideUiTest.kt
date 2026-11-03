package at.heinz.deviceai

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
class ContinuumReadinessGuideUiTest {
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun field(a: AgentActivity, name: String, value: Any?) =
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(a, value)
    private fun read(a: AgentActivity, name: String): Any? =
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(a)
    private fun open(): Pair<AgentActivity, AlertDialog> {
        val a = Robolectric.buildActivity(AgentActivity::class.java).get()
        field(a, "foregroundLearningActive", true)
        val method = AgentActivity::class.java.declaredMethods.firstOrNull { it.name == "showReadinessGuide" }
        assertNotNull("The app has no connected spoken setup/test guide", method)
        method!!.apply { isAccessible = true }.invoke(a)
        return a to ShadowAlertDialog.getLatestAlertDialog()
    }
    @Test fun openingGuideShowsSetupWithoutStartingAnyDeviceAction() {
        val (a, d) = open()
        try {
            val texts = views(d.window!!.decorView).filterIsInstance<TextView>().joinToString("\n") { it.text }
            for (label in listOf("Stimme", "Verstehen", "Lokales Modell", "Kamerablick", "Konten und Dienste"))
                assertTrue("Missing actual setup step: $label", texts.contains(label))
            assertNull(shadowOf(a).nextStartedActivity)
            assertNull(shadowOf(a.application).nextStartedService)
            assertEquals(false, read(a, "readinessCameraEnabled"))
            assertEquals(false, read(a, "voiceListening"))
        } finally { d.dismiss() }
    }
    @Test fun openingGuidePreservesStoredMutePreference() {
        val (a, d) = open()
        try {
            val prefs = a.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE)
            assertFalse(prefs.getBoolean("voice_enabled", false))
            assertEquals(false, read(a, "voiceEnabled"))
        } finally { d.dismiss() }
    }
    @Test fun refreshCannotStartCameraOrRecognition() {
        val (a, d) = open()
        try {
            views(d.window!!.decorView).filterIsInstance<Button>().single { it.text == "AKTUALISIEREN" }.performClick()
            assertEquals(false, read(a, "readinessCameraEnabled"))
            assertEquals(false, read(a, "voiceListening"))
            assertNull(shadowOf(a.application).nextStartedService)
        } finally { d.dismiss() }
    }
    @Test fun dismissEndsTheCameraOptInScope() {
        val (a, d) = open()
        field(a, "readinessCameraEnabled", true)
        d.dismiss()
        shadowOf(android.os.Looper.getMainLooper()).idle() // Android delivers OnDismissListener on its queue.
        assertEquals(false, read(a, "readinessCameraEnabled"))
        assertNull(read(a, "readinessDialog"))
    }
    @Test fun speechSampleNeverChangesOrSubmitsExistingComposer() {
        val (a, d) = open()
        try {
            val input = EditText(a).apply { setText("Mein unveränderter Entwurf") }
            field(a, "input", input)
            val code = AgentActivity::class.java.getDeclaredField("READINESS_SPEECH_REQUEST").apply { isAccessible = true }.getInt(null)
            AgentActivity::class.java.getDeclaredMethod("applyLocalSpeechText", Int::class.javaPrimitiveType, String::class.java)
                .apply { isAccessible = true }.invoke(a, code, "Continuum, ich bin bereit.")
            assertEquals("Mein unveränderter Entwurf", input.text.toString())
            assertTrue(read(a, "readinessSpeechResult").toString().contains("erkannt"))
            assertNull(shadowOf(a.application).nextStartedService)
        } finally { d.dismiss() }
    }
    @Test fun dismissedGuideIgnoresLateSpeechSample() {
        val (a, d) = open()
        val code = AgentActivity::class.java.getDeclaredField("READINESS_SPEECH_REQUEST").apply { isAccessible = true }.getInt(null)
        d.dismiss()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val before = read(a, "readinessSpeechResult")
        AgentActivity::class.java.getDeclaredMethod("applyLocalSpeechText", Int::class.javaPrimitiveType, String::class.java)
            .apply { isAccessible = true }.invoke(a, code, "Continuum, ich bin bereit.")
        assertEquals(before, read(a, "readinessSpeechResult"))
        assertNull(read(a, "readinessDialog"))
    }
}
