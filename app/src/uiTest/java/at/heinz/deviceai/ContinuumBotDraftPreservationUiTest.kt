package at.heinz.deviceai

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import at.heinz.deviceai.chief.ChiefBotProfileStore
import at.heinz.deviceai.chief.ChiefBotTaskMode
import at.heinz.deviceai.chief.ChiefBotTaskRequest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/** Acceptance regression for #493's existing draft-loss finding; no model or owner task executes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContinuumBotDraftPreservationUiTest {
    private fun get(a: AgentActivity, name: String): Any? = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(a)
    private fun set(a: AgentActivity, name: String, value: Any?) = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.set(a, value)
    private fun call(a: AgentActivity, name: String, vararg args: Any?): Any? =
        AgentActivity::class.java.declaredMethods.single { it.name == name && it.parameterCount == args.size }
            .apply { isAccessible = true }.invoke(a, *args)
    private fun descendants(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()

    private fun fixture(body: (AgentActivity, ChiefBotProfileStore, EditText) -> Unit) {
        val a = Robolectric.buildActivity(AgentActivity::class.java).get()
        set(a, "bootstrapMemory", BootstrapMemoryStore(a))
        set(a, "chatContainer", LinearLayout(a))
        set(a, "activityText", TextView(a))
        set(a, "voiceEnabled", false)
        set(a, "foregroundLearningActive", true)
        call(a, "buildComposer")
        val store = ChiefBotProfileStore(File(a.filesDir, "chief-bots"))
        store.seedDefaults(20)
        try { body(a, store, get(a, "input") as EditText) }
        finally {
            ShadowAlertDialog.getLatestAlertDialog()?.dismiss()
            (get(a, "mainHandler") as Handler).removeCallbacksAndMessages(null)
            for (name in listOf("localAgentExecutor", "backlogExecutor", "learningIoExecutor"))
                (get(a, name) as ExecutorService).shutdownNow()
        }
    }

    private fun flush(a: AgentActivity) {
        (get(a, "backlogExecutor") as ExecutorService).submit {}.get(5, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun existingOwnerDraftSurvivesAcceptedOrSafelyRefusedCodingHandoff() = fixture { a, store, input ->
        val profile = requireNotNull(store.get("bot-kora"))
        val ownerDraft = "OWNER_DRAFT_SENTINEL\nNoch nicht absenden."
        val goal = "Nur einen Coding-Entwurf öffnen, nichts ausführen."
        input.setText(ownerDraft)
        val accepted = call(a, "submitBotTask", ChiefBotTaskRequest(profile.agentId,
            profile.profileVersion, goal, ChiefBotTaskMode.CODING_DRAFT)) as Boolean
        flush(a)

        // Either explicit refusal or a lossless handoff is valid; silently consuming the draft is not.
        assertEquals(ownerDraft, input.text.toString())
        if (accepted) {
            val dialog = requireNotNull(ShadowAlertDialog.getLatestAlertDialog())
            val editor = descendants(requireNotNull(dialog.window).decorView).filterIsInstance<EditText>()
                .single { it.hint?.toString() == "Welche konkrete Änderung soll entstehen?" }
            assertEquals(goal, editor.text.toString())
        } else assertFalse(ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true)
        assertNull(get(a, "activeOrchestrator"))
        assertNull(get(a, "localRuntime"))
        assertTrue(store.loadConversation(profile.agentId, profile.agentId).turns.isEmpty())
    }

    @Test fun staleProfileCannotReplaceAnExistingOwnerDraft() = fixture { a, store, input ->
        val profile = requireNotNull(store.get("bot-kora"))
        store.rename(profile.agentId, "Kora aktuell", profile.profileVersion)
        val ownerDraft = "OWNER_DRAFT_SENTINEL\nAuch bei abgelehntem Auftrag erhalten."
        input.setText(ownerDraft)
        call(a, "submitBotTask", ChiefBotTaskRequest(profile.agentId, profile.profileVersion,
            "Nur den Parser prüfen.", ChiefBotTaskMode.CODING_DRAFT))
        flush(a)
        assertEquals(ownerDraft, input.text.toString())
        assertFalse(ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true)
        assertNull(get(a, "activeOrchestrator"))
        assertNull(get(a, "localRuntime"))
    }
}
