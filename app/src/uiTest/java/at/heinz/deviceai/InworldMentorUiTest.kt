package at.heinz.deviceai

import android.app.AlertDialog
import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class InworldMentorUiTest {
    private fun field(activity: AgentActivity, name: String) = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }
    private fun set(activity: AgentActivity, name: String, value: Any?) = field(activity, name).set(activity, value)
    private fun get(activity: AgentActivity, name: String): Any? = field(activity, name).get(activity)
    private fun call(activity: AgentActivity, name: String, vararg args: Any?): Any? =
        AgentActivity::class.java.declaredMethods.single { it.name == name && it.parameterCount == args.size }
            .apply { isAccessible = true }.invoke(activity, *args)
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()

    private fun fixture(body: (AgentActivity) -> Unit) {
        val activity = Robolectric.buildActivity(AgentActivity::class.java).get()
        set(activity, "backend", AgentBackendClient(activity))
        set(activity, "bootstrapMemory", BootstrapMemoryStore(activity))
        set(activity, "chatContainer", LinearLayout(activity))
        set(activity, "activityText", TextView(activity))
        set(activity, "voiceEnabled", false)
        call(activity, "buildComposer")
        try { body(activity) } finally {
            ShadowAlertDialog.getLatestAlertDialog()?.dismiss()
            for (name in listOf("localAgentExecutor", "backlogExecutor", "learningIoExecutor")) {
                (get(activity, name) as ExecutorService).shutdownNow()
            }
        }
    }

    @Test fun settingsEntryOpensInworldPairingWithoutAnApiKeyField() = fixture { activity ->
        call(activity, "showCloudSettings")
        val settings = ShadowAlertDialog.getLatestAlertDialog()
        val entry = views(settings.findViewById(android.R.id.content)).filterIsInstance<TextView>()
            .single { it.text.toString() == "INWORLD · EINZELFRAGE" }
        entry.performClick()
        val pairing = ShadowAlertDialog.getLatestAlertDialog()
        val content = views(pairing.findViewById(android.R.id.content))
        assertTrue(content.filterIsInstance<TextView>().any { it.text.toString().contains("Inworld-Mentor · Gerät koppeln") })
        assertTrue(content.filterIsInstance<EditText>().isEmpty())
        assertFalse((get(activity, "backend") as AgentBackendClient).hasMentorAccess())
        assertEquals(false, get(activity, "busy"))
        settings.dismiss()
    }

    @Test fun longQuestionKeepsItsTailVisibleAndCannotBeSilentlyTruncatedOrSent() = fixture { activity ->
        val question = "x".repeat(1795) + " ohne private Daten"
        call(activity, "showInworldQuestionDialog", question)
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        val input = views(dialog.findViewById(android.R.id.content)).filterIsInstance<EditText>().single()
        assertEquals(question, input.text.toString())
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertTrue(dialog.isShowing)
        assertNotNull(input.error)
        assertEquals(question, input.text.toString())
        assertEquals(false, get(activity, "busy"))
        assertNull(get(activity, "activeInworldRequestId"))
    }

    @Test fun cancellingQuestionDoesNotGrantAccessOrStartARequest() = fixture { activity ->
        call(activity, "showInworldQuestionDialog", "Erkläre einen Motor.")
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(dialog.isShowing)
        assertEquals(false, get(activity, "busy"))
        assertNull(get(activity, "activeInworldRequestId"))
    }

    @Test fun changedChatInvalidatesAnOpenConsentDialog() = fixture { activity ->
        call(activity, "showInworldQuestionDialog", "Erkläre einen Motor.")
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        set(activity, "sessionId", "another-session")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertEquals(false, get(activity, "busy"))
        assertNull(get(activity, "activeInworldRequestId"))
        assertTrue(views(get(activity, "chatContainer") as View).filterIsInstance<TextView>().isEmpty())
    }

    @Test fun oneQuestionDoesNotEnrolTopicsInAlreadyEnabledBackgroundResearch() = fixture { activity ->
        val consent = get(activity, "internetConsent") as at.heinz.deviceai.cognitive.InternetConsentPolicy
        consent.setMode(at.heinz.deviceai.cognitive.InternetAccessMode.ALWAYS_ON)
        val prefs = activity.getSharedPreferences("continuum-public-study-v1", Context.MODE_PRIVATE)
        prefs.edit().clear().putBoolean("enabled", true).commit()
        val before = prefs.all.toMap()
        val question = "Was ist Quantenmechanik?"
        assertNotNull(at.heinz.deviceai.cognitive.PublicKnowledgeTopicRouter.topic(question))
        // The fixture has no backend token, so this exercises confirmation and rendering,
        // then fails locally before a network connection can be opened.
        call(activity, "showInworldQuestionDialog", question)
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        (get(activity, "learningIoExecutor") as ExecutorService).submit {}.get(3, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(before, prefs.all)
        assertEquals(false, get(activity, "busy"))
        assertNull(get(activity, "activeInworldRequestId"))
    }

    @Test fun lateResponseCannotClearAnotherSessionOrRequest() = fixture { activity ->
        set(activity, "sessionId", "current-session")
        set(activity, "activeInworldRequestId", "current-request")
        set(activity, "busy", true)
        call(activity, "completeInworldMentor", "current-request", "old-session", "old answer", null)
        call(activity, "completeInworldMentor", "old-request", "current-session", "old answer", null)
        assertEquals(true, get(activity, "busy"))
        assertEquals("current-request", get(activity, "activeInworldRequestId"))
        assertTrue(views(get(activity, "chatContainer") as View).filterIsInstance<TextView>().isEmpty())
    }

    @Test fun currentReplyIsLabelledExternalAndReleasesOnlyItsOwnRequest() = fixture { activity ->
        set(activity, "sessionId", "current-session")
        set(activity, "activeInworldRequestId", "current-request")
        set(activity, "busy", true)
        call(activity, "completeInworldMentor", "current-request", "current-session", "Fixture-Antwort.", null)
        assertEquals(false, get(activity, "busy"))
        assertNull(get(activity, "activeInworldRequestId"))
        val texts = views(get(activity, "chatContainer") as View).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(texts.any { it.contains("INWORLD · EXTERN · UNBESTÄTIGT") && it.contains("Fixture-Antwort.") })
        assertNull(get(activity, "localRuntime"))
    }
}
