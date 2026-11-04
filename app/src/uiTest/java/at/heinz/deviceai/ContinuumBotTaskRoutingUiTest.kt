package at.heinz.deviceai

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/** Actual Activity intake and existing coding review. No native model is installed in this fixture. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContinuumBotTaskRoutingUiTest {
    private fun get(a: AgentActivity, name: String): Any? = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(a)
    private fun set(a: AgentActivity, name: String, value: Any?) = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.set(a, value)
    private fun call(a: AgentActivity, name: String, vararg args: Any?): Any? =
        AgentActivity::class.java.declaredMethods.single { it.name == name && it.parameterCount == args.size }
            .apply { isAccessible = true }.invoke(a, *args)
    private fun descendants(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()

    private fun withActivity(body: (AgentActivity, ChiefBotProfileStore) -> Unit) {
        val activity = Robolectric.buildActivity(AgentActivity::class.java).get()
        set(activity, "bootstrapMemory", BootstrapMemoryStore(activity))
        set(activity, "chatContainer", LinearLayout(activity))
        set(activity, "activityText", TextView(activity))
        set(activity, "voiceEnabled", false)
        set(activity, "foregroundLearningActive", true)
        call(activity, "buildComposer")
        val store = ChiefBotProfileStore(java.io.File(activity.filesDir, "chief-bots"))
        store.seedDefaults(20)
        try { body(activity, store) }
        finally {
            ShadowAlertDialog.getLatestAlertDialog()?.dismiss()
            for (name in listOf("localAgentExecutor", "backlogExecutor", "learningIoExecutor"))
                (get(activity, name) as ExecutorService).shutdownNow()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun flush(a: AgentActivity) {
        (get(a, "backlogExecutor") as ExecutorService).submit {}.get(5, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun unknownNaturalSelectionsReachTheOrdinaryInputPath() {
        for (request in listOf("wechsle zu dunklem Modus", "sprich mit mir über den Plan")) withActivity { a, store ->
            // Observe intake only; the normal model lane is deliberately unavailable.
            (get(a, "localAgentExecutor") as ExecutorService).shutdownNow()
            val before = store.page(limit = 100).items
            val input = get(a, "input") as EditText
            input.setText(request)
            assertEquals(true, call(a, "routeBotInput", request))
            flush(a)
            assertEquals("", input.text.toString())
            assertEquals(1, descendants(get(a, "chatContainer") as View).filterIsInstance<TextView>()
                .count { it.text.toString() == request })
            assertNull(get(a, "selectedBot"))
            assertNull(get(a, "activeOrchestrator"))
            assertNull(get(a, "localRuntime"))
            assertEquals(before, store.page(limit = 100).items)
        }
    }

    @Test fun unknownNaturalSelectionKeepsAnExistingPrivateConversationSelected() = withActivity { a, store ->
        (get(a, "localAgentExecutor") as ExecutorService).shutdownNow()
        val profile = requireNotNull(store.get("bot-kora"))
        set(a, "selectedBot", profile)
        val request = "sprich mit mir über den Plan"
        val input = get(a, "input") as EditText
        input.setText(request)
        assertEquals(true, call(a, "routeBotInput", request))
        flush(a)
        assertEquals("", input.text.toString())
        assertSame(profile, get(a, "selectedBot"))
        assertEquals(0, (get(a, "chatContainer") as LinearLayout).childCount)
        assertEquals(profile, store.get(profile.agentId))
        assertTrue(store.loadConversation(profile.agentId, profile.agentId).turns.isEmpty())
        assertNull(get(a, "activeOrchestrator"))
        assertNull(get(a, "localRuntime"))
    }

    @Test fun unknownExplicitMentionNeverFallsBackToOrdinaryInput() {
        for (request in listOf("@Unbekannt sprich mit mir über den Plan", "wechsle zu @Unbekannt",
            "sprich mit @Unbekannt", "rufe @Unbekannt auf")) withActivity { a, _ ->
            val input = get(a, "input") as EditText
            input.setText(request)
            assertEquals(true, call(a, "routeBotInput", request))
            flush(a)
            assertEquals(request, input.text.toString())
            assertEquals(0, (get(a, "chatContainer") as LinearLayout).childCount)
            assertNull(get(a, "selectedBot"))
            assertNull(get(a, "activeOrchestrator"))
        }
    }

    @Test fun ambiguousNaturalSelectionNeverFallsBackToOrdinaryInput() = withActivity { a, store ->
        store.create("bot-duplicate", "KORA", "Review", "Präzise")
        val before = store.page(limit = 100).items
        val request = "wechsle zu Kora"
        val input = get(a, "input") as EditText
        input.setText(request)
        assertEquals(true, call(a, "routeBotInput", request))
        flush(a)
        assertEquals(request, input.text.toString())
        assertEquals(0, (get(a, "chatContainer") as LinearLayout).childCount)
        assertEquals(before, store.page(limit = 100).items)
        assertNull(get(a, "selectedBot"))
        assertNull(get(a, "activeOrchestrator"))
    }

    @Test fun unreadableRegistryNeverAuthorizesNaturalSelectionFallback() = withActivity { a, _ ->
        val registry = java.io.File(a.filesDir, "chief-bots/bot-profiles-v1.bin")
        val damaged = byteArrayOf(0)
        registry.writeBytes(damaged)
        val request = "wechsle zu dunklem Modus"
        val input = get(a, "input") as EditText
        input.setText(request)
        assertEquals(true, call(a, "routeBotInput", request))
        flush(a)
        assertEquals(request, input.text.toString())
        assertEquals(0, (get(a, "chatContainer") as LinearLayout).childCount)
        assertTrue(damaged.contentEquals(registry.readBytes()))
        assertNull(get(a, "selectedBot"))
        assertNull(get(a, "activeOrchestrator"))
    }

    @Test fun changedDraftCannotReceiveALateUnknownSelectionFallback() = withActivity { a, _ ->
        val request = "wechsle zu dunklem Modus"
        val replacement = "Diesen neuen Entwurf nicht absenden."
        val input = get(a, "input") as EditText
        input.setText(request)
        assertEquals(true, call(a, "routeBotInput", request))
        input.setText(replacement)
        flush(a)
        assertEquals(replacement, input.text.toString())
        assertEquals(0, (get(a, "chatContainer") as LinearLayout).childCount)
        assertNull(get(a, "activeOrchestrator"))
    }

    @Test fun missingModelRefusesTaskWithoutConsumingTheDraftOrChangingBot() = withActivity { a, store ->
        val profile = requireNotNull(store.get("bot-kora"))
        set(a, "selectedBot", profile)
        val input = get(a, "input") as EditText
        input.setText("Vorhandener Text bleibt erhalten.")
        val request = ChiefBotTaskRequest(profile.agentId, profile.profileVersion,
            "Prüfe den Status, ohne etwas zu öffnen oder zu senden.", ChiefBotTaskMode.LOCAL_TOOLS)
        assertEquals(false, call(a, "submitBotTask", request))
        assertEquals("Vorhandener Text bleibt erhalten.", input.text.toString())
        assertSame(profile, get(a, "selectedBot"))
        assertNull(get(a, "activeOrchestrator"))
        assertNull(get(a, "localRuntime"))
        assertEquals(profile, store.get(profile.agentId))
    }

    @Test fun busyTaskDoesNotAcceptAnotherAssignmentOrCancelTheCurrentWork() = withActivity { a, store ->
        val profile = requireNotNull(store.get("bot-atlas"))
        val input = get(a, "input") as EditText
        input.setText("Noch nicht gesendet.")
        set(a, "busy", true)
        val cancellation = at.heinz.deviceai.cognitive.DeliberationCancellation()
        set(a, "activeChatCancellation", cancellation)
        assertEquals(false, call(a, "submitBotTask", ChiefBotTaskRequest(profile.agentId,
            profile.profileVersion, "Plane meine Schritte.", ChiefBotTaskMode.PRIVATE_REPLY)))
        assertEquals("Noch nicht gesendet.", input.text.toString())
        assertSame(cancellation, get(a, "activeChatCancellation"))
        cancellation.throwIfCancelled()
    }

    @Test fun codingAssignmentPrefillsCompleteExistingReviewWithoutRunningAnything() = withActivity { a, store ->
        val profile = requireNotNull(store.get("bot-kora"))
        val goal = "Prüfe den Parser.\nÄndere keine Freigaben und veröffentliche nichts."
        assertEquals(true, call(a, "submitBotTask", ChiefBotTaskRequest(profile.agentId,
            profile.profileVersion, goal, ChiefBotTaskMode.CODING_DRAFT)))
        flush(a)
        val dialog = requireNotNull(ShadowAlertDialog.getLatestAlertDialog())
        val views = descendants(dialog.window!!.decorView)
        val editor = views.filterIsInstance<EditText>().single { it.hint?.toString() == "Welche konkrete Änderung soll entstehen?" }
        assertEquals(goal, editor.text.toString())
        assertFalse(views.filterIsInstance<Button>().single { it.text == "AUFTRAG AN TERMUX ÜBERGEBEN" }.isEnabled)
        assertNull(get(a, "activeOrchestrator"))
        assertNull(get(a, "localRuntime"))
        assertTrue(store.loadConversation(profile.agentId, profile.agentId).turns.isEmpty())
    }

    @Test fun staleProfileRefusalKeepsTheWholeTaskTextAvailable() = withActivity { a, store ->
        val old = requireNotNull(store.get("bot-kora"))
        store.rename(old.agentId, "Kora Neu", old.profileVersion)
        val text = "Analysiere nur. Kein Build, kein Upload."
        assertEquals(true, call(a, "submitBotTask", ChiefBotTaskRequest(old.agentId,
            old.profileVersion, text, ChiefBotTaskMode.CODING_DRAFT)))
        flush(a)
        assertEquals(text, (get(a, "input") as EditText).text.toString())
        assertNull(get(a, "activeOrchestrator"))
        assertNull(get(a, "localRuntime"))
        assertFalse(ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true)
    }

    @Test fun changedSessionCannotShowLateCodingReview() = withActivity { a, store ->
        val profile = requireNotNull(store.get("bot-kora"))
        val text = "Nur einen Entwurf vorbereiten, nichts ausführen."
        assertEquals(true, call(a, "submitBotTask", ChiefBotTaskRequest(profile.agentId,
            profile.profileVersion, text, ChiefBotTaskMode.CODING_DRAFT)))
        set(a, "sessionId", "replacement-session")
        flush(a)
        assertFalse(ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true)
        assertNull(get(a, "activeOrchestrator"))
        assertEquals(text, (get(a, "input") as EditText).text.toString())
    }
}
