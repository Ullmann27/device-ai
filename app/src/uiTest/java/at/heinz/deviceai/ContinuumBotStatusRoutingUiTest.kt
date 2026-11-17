package at.heinz.deviceai

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import at.heinz.deviceai.chief.ChiefBotProfile
import at.heinz.deviceai.chief.ChiefBotProfileStore
import at.heinz.deviceai.chief.ChiefWorkerPool
import at.heinz.deviceai.cognitive.DeliberationCancellation
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.ExecutorService

/** Actual composer and diagnostic worker; no native model is installed or started by this fixture. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContinuumBotStatusRoutingUiTest {
    private fun get(activity: AgentActivity, name: String): Any? = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(activity)

    private fun set(activity: AgentActivity, name: String, value: Any?) {
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)
    }

    private fun call(activity: AgentActivity, name: String, vararg args: Any?): Any? =
        AgentActivity::class.java.declaredMethods.single { it.name == name && it.parameterCount == args.size }
            .apply { isAccessible = true }.invoke(activity, *args)

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) {
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
    } else emptyList()

    private data class Fixture(
        val activity: AgentActivity,
        val profile: ChiefBotProfile,
        val store: ChiefBotProfileStore,
        val input: EditText,
        val send: Button,
        val cancellation: DeliberationCancellation
    )

    private fun fixture(body: (Fixture) -> Unit) {
        val activity = Robolectric.buildActivity(AgentActivity::class.java).get()
        set(activity, "bootstrapMemory", BootstrapMemoryStore(activity))
        set(activity, "chatContainer", LinearLayout(activity))
        set(activity, "activityText", TextView(activity))
        set(activity, "voiceEnabled", false)
        set(activity, "foregroundLearningActive", true)
        call(activity, "buildComposer")
        val store = ChiefBotProfileStore(java.io.File(activity.filesDir, "chief-bots"))
        store.seedDefaults(20)
        val profile = requireNotNull(store.get("bot-kora"))
        set(activity, "selectedBot", profile)
        set(activity, "activeBotId", profile.agentId)
        set(activity, "busy", true)
        set(activity, "activeLocalRequest", true)
        val cancellation = DeliberationCancellation()
        set(activity, "activeChatCancellation", cancellation)
        val fixture = Fixture(activity, profile, store, get(activity, "input") as EditText,
            get(activity, "sendButton") as Button, cancellation)
        try {
            body(fixture)
        } finally {
            (get(activity, "chiefStatusPool") as? ChiefWorkerPool)?.shutdown()
            for (name in listOf("localAgentExecutor", "backlogExecutor", "learningIoExecutor")) {
                (get(activity, name) as ExecutorService).shutdownNow()
            }
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun assertBotUnchanged(fixture: Fixture) {
        val activity = fixture.activity
        assertSame(fixture.profile, get(activity, "selectedBot"))
        assertEquals(fixture.profile.agentId, get(activity, "activeBotId"))
        assertSame(fixture.cancellation, get(activity, "activeChatCancellation"))
        fixture.cancellation.throwIfCancelled()
        assertEquals(true, get(activity, "busy"))
        assertEquals(true, get(activity, "activeLocalRequest"))
        assertEquals(fixture.profile, fixture.store.get(fixture.profile.agentId))
        assertTrue(fixture.store.loadConversation(fixture.profile.agentId, fixture.profile.agentId).turns.isEmpty())
        // Accessing/cancelling/submitting a native generation would also fail: runtime is intentionally absent.
        assertNull(get(activity, "localRuntime"))
    }

    private fun checkStatus(request: String, lookupPending: Boolean = false) = fixture { fixture ->
        assertEquals(ContinuumWorkerChatCommand.ChiefStatus, ContinuumWorkerChatRouter.parse(request))
        val revision = get(fixture.activity, "botLookupRevision")
        set(fixture.activity, "botLookupPending", lookupPending)
        fixture.input.setText(request)
        assertTrue(fixture.send.performClick())
        val pool = get(fixture.activity, "chiefStatusPool") as? ChiefWorkerPool
        assertNotNull("The actual composer must reach the read-only Chief diagnostic", pool)
        assertTrue("Read-only diagnostic must complete independently of the busy bot", pool!!.awaitIdle(5_000))
        shadowOf(Looper.getMainLooper()).idle()
        val messages = descendants(get(fixture.activity, "chatContainer") as View)
            .filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(messages.any { it.startsWith("JARVIS · Mitarbeiterstatus (lokale Diagnose)") })
        assertTrue(messages.any { it.contains("Gespeicherte Bot-Profile: 20") })
        assertEquals("", fixture.input.text.toString())
        assertEquals(lookupPending, get(fixture.activity, "botLookupPending"))
        assertEquals(revision, get(fixture.activity, "botLookupRevision"))
        assertBotUnchanged(fixture)
    }

    @Test fun commaAddressedJarvisStatusKeepsActiveBotSelected() = checkStatus("JARVIS, Teamstatus")

    @Test fun spaceAddressedChiefStatusDoesNotEnterThePrivateBotRouter() = checkStatus("JARVIS Mitarbeiterstatus")

    @Test fun pendingBotLookupDoesNotBlockReadOnlyChiefStatus() =
        checkStatus("JARVIS: Botstatus", lookupPending = true)

    @Test fun ordinaryBotTextStaysPendingWhileTheExistingJobOwnsTheModel() = fixture { fixture ->
        val request = "Kora, erkläre mir bitte den nächsten Schritt."
        fixture.input.setText(request)
        // The send button remains the existing explicit STOP action during a normal busy turn.
        // Invoke its send entry directly to verify that this fix did not admit a second request.
        call(fixture.activity, "sendCurrentInput", false)
        assertEquals(request, fixture.input.text.toString())
        assertNull(get(fixture.activity, "chiefStatusPool"))
        assertEquals(0, (get(fixture.activity, "chatContainer") as LinearLayout).childCount)
        assertBotUnchanged(fixture)
    }
}
