package at.heinz.deviceai

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowChoreographer
import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Fold7 report #568: the chat was gone after leaving and reopening the app. Real AgentActivity, two instances. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w412dp-h915dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContinuumChatPersistenceUiTest {
    @Before fun configure() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit().putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
    }

    private fun field(a: AgentActivity, name: String): Any? =
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(a)

    private fun texts(view: View): List<String> = when (view) {
        is TextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun waitForTranscript(text: String): Boolean {
        val dir = File(RuntimeEnvironment.getApplication().filesDir, "chat-history")
        repeat(60) {
            if (dir.listFiles().orEmpty().any { it.name.endsWith(".jsonl") && it.readText().contains(text) }) return true
            Thread.sleep(50)
        }
        return false
    }

    private fun <T> withActivity(body: (AgentActivity) -> T): T {
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        try {
            controller.setup().visible()
            repeat(2) { shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS) }
            return body(controller.get())
        } finally {
            // Leaving the app: the Activity is finished and destroyed, its views are gone.
            runCatching { controller.pause().stop().destroy() }
        }
    }

    private fun send(a: AgentActivity, user: String, answer: String) {
        AgentActivity::class.java.getDeclaredMethod("addUserMessage", String::class.java).apply { isAccessible = true }.invoke(a, user)
        AgentActivity::class.java.getDeclaredMethod("addAssistantMessage", String::class.java, Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(a, answer, false)
    }

    @Test fun reopenedAppShowsTheSameConversationWithoutRerunningIt() {
        val firstSession = withActivity { a ->
            send(a, "Merke dir: Mein Testwort lautet Nebel 731.", "Ich habe mir das Testwort gemerkt.")
            field(a, "sessionId") as String
        }
        waitForTranscript("Ich habe mir das Testwort gemerkt.")
        withActivity { a ->
            val chat = texts(field(a, "chatContainer") as LinearLayout)
            assertTrue("user message restored: $chat", chat.contains("Merke dir: Mein Testwort lautet Nebel 731."))
            assertTrue("answer restored: $chat", chat.contains("Ich habe mir das Testwort gemerkt."))
            assertEquals("the same conversation continues", firstSession, field(a, "sessionId"))
            assertEquals("restoring never starts work", false, field(a, "busy"))
            assertEquals("each message once", 1, chat.count { it == "Ich habe mir das Testwort gemerkt." })
        }
    }

    @Test fun startupGreetingIsNotStoredAsConversation() {
        withActivity { }
        val dir = File(RuntimeEnvironment.getApplication().filesDir, "chat-history")
        assertFalse(dir.listFiles().orEmpty().any { it.name.endsWith(".jsonl") && it.readText().contains("Ich bin Device AI") })
    }

    /** Owner review on #565: after a real exit the same chat must also keep the model's dialogue context. */
    @Test fun reopenedAppRestoresTheModelDialogueContextOfThatChat() {
        val request = "Erfinde einen Namen für einen Mond."
        val answer = "Der erfundene Mond heißt Aetheria."
        val session = withActivity { a ->
            val id = field(a, "sessionId") as String
            // The successful local-model path admits the turn to the context and stores it (Gemma itself is not run here).
            val context = field(a, "localConversationContext") as at.heinz.deviceai.cognitive.LocalConversationContext
            assertNotNull("Activity recorder is connected", context.onTurnRecorded)
            context.record(id, request, answer)
            id
        }
        assertTrue("Activity recorder persisted the turn", waitForContextTurn(request))
        val stored = storedContextTurns(session)
        assertEquals("recorded exactly once", 1, stored.size)
        assertTrue(stored.single().requestComplete)
        assertTrue(stored.single().responseComplete)
        withActivity { a ->
            val context = field(a, "localConversationContext") as at.heinz.deviceai.cognitive.LocalConversationContext
            assertEquals(session, field(a, "sessionId"))
            assertEquals("the owner turn is back in the model context", listOf(request), context.priorUserTurns(session))
            assertTrue("the earlier answer is available as dialogue data", context.render(session).contains("Aetheria"))
            assertEquals("restoring never starts work", false, field(a, "busy"))
        }
        AgentActivity.chatTranscriptIo.submit {}.get(2, TimeUnit.SECONDS)
        assertEquals("restore does not write duplicates", stored, storedContextTurns(session))
    }

    /** Gitar review on #565: context the app dropped on purpose (non-model answer) must stay dropped after a restart. */
    @Test fun restartDoesNotBringBackContextTheAppHadCleared() {
        val request = "Erfinde einen Namen für einen Mond."
        val toolAnswer = "Werkzeugergebnis: Systemstatus angezeigt."
        val session = withActivity { a ->
            val id = field(a, "sessionId") as String
            val context = field(a, "localConversationContext") as at.heinz.deviceai.cognitive.LocalConversationContext
            assertNotNull("Activity recorder is connected", context.onTurnRecorded)
            context.record(id, request, "Der erfundene Mond heißt Aetheria.")
            assertTrue("turn exists before invalidation", waitForContextTurn(request))
            // A non-model answer (tool result, memory reply, error) invalidates the model context in RAM.
            AgentActivity::class.java.getDeclaredMethod("addAssistantMessage", String::class.java, Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(a, toolAnswer, false)
            assertEquals("cleared in RAM", emptyList<String>(), context.priorUserTurns(id))
            id
        }
        assertTrue(waitForTranscript(toolAnswer))
        assertTrue("reset invalidates persisted context", storedContextTurns(session).isEmpty())
        withActivity { a ->
            val context = field(a, "localConversationContext") as at.heinz.deviceai.cognitive.LocalConversationContext
            assertEquals(session, field(a, "sessionId"))
            assertEquals("the restart must not resurrect the cleared turn", emptyList<String>(), context.priorUserTurns(session))
            assertTrue("the visible chat is unaffected", texts(field(a, "chatContainer") as LinearLayout).contains(toolAnswer))
        }
    }

    private fun storedContextTurns(id: String) = at.heinz.deviceai.cognitive.LocalChatTranscriptStore(
        File(RuntimeEnvironment.getApplication().filesDir, "chat-history")).loadContextTurns(id)

    private fun waitForContextTurn(text: String): Boolean {
        val dir = File(RuntimeEnvironment.getApplication().filesDir, "chat-history")
        repeat(60) {
            if (dir.listFiles().orEmpty().any { it.name.endsWith(".jsonl") && it.readText().contains("\"k\":\"ctx\"") &&
                    it.readText().contains(text) }) return true
            Thread.sleep(50)
        }
        return false
    }
}
