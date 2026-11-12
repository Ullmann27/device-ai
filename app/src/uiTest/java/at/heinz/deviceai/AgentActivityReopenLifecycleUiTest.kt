package at.heinz.deviceai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import at.heinz.deviceai.cognitive.ConversationStartupNotice
import at.heinz.deviceai.cognitive.LocalConversationContext
import at.heinz.deviceai.cognitive.LocalDialogueRetention
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
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowChoreographer
import java.io.File
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Code55 field bug E (Fold7, Code54): the owner leaves and reopens the app often. Every new AgentActivity instance
 * appended the generic first-start greeting below the restored conversation, the model/object-state context was
 * restored from the last four turns only, and a 2 s restore timeout fell back to an empty context without a word.
 * Real AgentActivity, real transcript store; „leaving“ = pause/stop/destroy without a configuration change.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w412dp-h915dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AgentActivityReopenLifecycleUiTest {
    private val statement = "Meine Werkzeugkiste ist blau, steht im Keller und enthält zwölf Schrauben."
    private val recall = "Welche Farbe hat meine Werkzeugkiste, wo steht sie und wie viele Schrauben sind darin?"

    @Before fun configure() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        LocalDialogueRetention.context.forgetForExit()
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit().putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-internet", Context.MODE_PRIVATE).edit().putString("mode", "OFF").commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
    }

    private fun field(a: AgentActivity, name: String): Any? =
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(a)
    private fun context(a: AgentActivity) = field(a, "localConversationContext") as LocalConversationContext
    private fun session(a: AgentActivity) = field(a, "sessionId") as String
    private fun advance() = shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)

    private fun texts(view: View): List<String> = when (view) {
        is TextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun chat(a: AgentActivity) = texts(field(a, "chatContainer") as LinearLayout)

    private fun open(): ActivityController<AgentActivity> = Robolectric.buildActivity(AgentActivity::class.java).also {
        it.setup().visible()
        repeat(2) { advance() }
    }

    /** Leaving the app: no configuration change, the Activity is destroyed and the RAM dialogue is forgotten. */
    private fun leave(controller: ActivityController<AgentActivity>) {
        runCatching { controller.pause().stop().destroy() }
        AgentActivity.chatTranscriptIo.submit {}.get(5, TimeUnit.SECONDS)
    }

    private fun <T> session(body: (AgentActivity) -> T): T {
        val controller = open()
        try { return body(controller.get()) } finally { leave(controller) }
    }

    private fun invoke(a: AgentActivity, name: String, vararg args: Pair<Class<*>, Any?>) {
        AgentActivity::class.java.getDeclaredMethod(name, *args.map { it.first }.toTypedArray())
            .apply { isAccessible = true }.invoke(a, *args.map { it.second }.toTypedArray())
    }

    /** One successful local-model turn as the Activity records it: both bubbles plus the admitted context turn. */
    private fun modelTurn(a: AgentActivity, request: String, answer: String) {
        invoke(a, "addUserMessage", String::class.java to request)
        invoke(a, "addAssistantMessage", String::class.java to answer, Boolean::class.javaPrimitiveType!! to true)
        val context = context(a)
        assertNotNull("Activity recorder is connected", context.onTurnRecorded)
        context.record(session(a), request, answer)
    }

    private fun submit(a: AgentActivity, request: String) {
        if (field(a, "activeWorkArea") != ContinuumWorkArea.CHAT) {
            val chatRoute = JarvisHomeTestRoutes.destination(a.window.decorView, "chat")
            assertNotNull("Chat entry exists", chatRoute)
            JarvisHomeTestRoutes.touch(a.window.decorView, chatRoute)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (field(a, "activeWorkArea") != ContinuumWorkArea.CHAT && System.nanoTime() < deadline) {
                advance(); Thread.sleep(5)
            }
        }
        assertEquals(ContinuumWorkArea.CHAT, field(a, "activeWorkArea"))
        (field(a, "input") as EditText).setText(request)
        assertTrue((field(a, "sendButton") as Button).performClick())
        repeat(4) { advance() }
    }

    /** Context-turn lines in the conversation file (format unchanged by Code55). */
    private fun storedContextLines(id: String) = File(File(RuntimeEnvironment.getApplication().filesDir, "chat-history"),
        "$id.jsonl").readLines().count { it.contains("\"k\":\"ctx\"") }

    @Test fun firstOpenOfAnEmptyConversationShowsTheGreetingOnce() = session { a ->
        assertEquals(chat(a).toString(), 1, chat(a).count { it == ConversationStartupNotice.GREETING })
        assertFalse(chat(a).any { it.startsWith("Hinweis:") })
    }

    @Test fun reopenedConversationContinuesWithoutTheGenericGreeting() {
        val first = session { a ->
            modelTurn(a, statement, "Alles klar.")
            session(a)
        }
        session { a ->
            val chat = chat(a)
            assertEquals("the same conversation continues", first, session(a))
            assertTrue("owner statement restored: $chat", chat.contains(statement))
            assertEquals("each message once: $chat", 1, chat.count { it == statement })
            assertFalse("no first-start greeting below a restored conversation: $chat",
                chat.any { it.contains("Mein eigener Startkern") })
            assertFalse(chat.toString(), chat.any { it.startsWith("Hinweis:") })
            assertEquals("restoring never starts work", false, field(a, "busy"))
        }
    }

    @Test fun reopenedAppKeepsTheToolboxStateBeyondTheFourTurnModelWindow() {
        val first = session { a ->
            modelTurn(a, statement, "Alles klar.")
            modelTurn(a, "Nicht zwölf, sondern siebzehn Schrauben.", "Verstanden.")
            for (other in listOf("Erzähl mir einen kurzen Witz.", "Was ist die Hauptstadt von Frankreich?",
                    "Erkläre mir kurz die Photosynthese.", "Nenne mir ein Synonym für schnell."))
                modelTurn(a, other, "Antwort.")
            session(a)
        }
        assertEquals("all six turns are on disk", 6, storedContextLines(first))
        session { a ->
            val context = context(a)
            assertEquals(first, session(a))
            assertEquals("the model window keeps its four-turn bound", 4, context.priorUserTurns(first).size)
            assertFalse(context.priorUserTurns(first).contains(statement))
            val answer = context.objectStateAnswer(first, recall)
            assertNotNull("object-state memory of the reopened session", answer)
            assertTrue(answer, answer!!.contains("Farbe: blau"))
            assertTrue(answer, answer.contains("Ort: im Keller"))
            assertTrue(answer, answer.contains("Anzahl Schrauben: 17"))
            // The real chat entry answers it without a model turn (no model is loaded in this test).
            submit(a, recall)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (chat(a).none { it.startsWith("Laut deinen Angaben") } && System.nanoTime() < deadline) {
                advance(); Thread.sleep(5)
            }
            val reply = chat(a).last { it.startsWith("Laut deinen Angaben") }
            assertTrue(reply, reply.contains("Farbe: blau"))
            assertTrue(reply, reply.contains("Ort: im Keller"))
            assertTrue(reply, reply.contains("Anzahl Schrauben: 17"))
            assertFalse(reply, reply.contains("Zugriff"))
        }
    }

    @Test fun configurationRecreateKeepsConversationAndObjectStateWithoutReplay() {
        val first = open()
        val a = first.get()
        modelTurn(a, statement, "Alles klar.")
        val id = session(a)
        AgentActivity.chatTranscriptIo.submit {}.get(5, TimeUnit.SECONDS)
        // Robolectric 4.14.1 ActivityController.recreate() fails in windowFocusChanged (see ContinuumPdfSceneAcceptanceUiTest):
        // the same configuration-change lifecycle by hand – saved state, isChangingConfigurations, destroy, new instance.
        val saved = Bundle()
        first.saveInstanceState(saved)
        Activity::class.java.getDeclaredField("mChangingConfigurations").apply { isAccessible = true }.setBoolean(a, true)
        assertTrue(a.isChangingConfigurations)
        runCatching { first.pause().stop().destroy() }
        val controller = Robolectric.buildActivity(AgentActivity::class.java).setup(saved).visible()
        try {
            repeat(2) { advance() }
            val recreated = controller.get()
            assertEquals(id, session(recreated))
            val chat = chat(recreated)
            assertEquals("each message once: $chat", 1, chat.count { it == statement })
            assertFalse("no greeting after a recreation: $chat", chat.any { it.contains("Mein eigener Startkern") })
            val answer = context(recreated).objectStateAnswer(id, recall)!!
            assertTrue(answer, answer.contains("Anzahl Schrauben: 12"))
            assertFalse("the kept RAM state is not replayed a second time: $answer", answer.contains("unverändert"))
            assertEquals(listOf(statement), context(recreated).priorUserTurns(id))
        } finally { leave(controller) }
    }

    /**
     * Fold7 Code54 screenshot: after „Öffne eine PDF.“ and the return from Android's document picker the generic
     * greeting appeared in the middle of the conversation, followed by the PDF reply – the picker had led to a new
     * AgentActivity instance (onCreate with saved state). Both lifecycles are covered: the system destroys the
     * Activity behind the picker (RAM dialogue forgotten, restored from disk; same as a process death), or a
     * configuration change (fold/unfold) recreates it. The picker result is then delivered to the new instance.
     */
    private fun pickerRecreation(configurationChange: Boolean) {
        val first = open()
        val a = first.get()
        modelTurn(a, statement, "Alles klar.")
        val id = session(a)
        (field(a, "input") as EditText).setText("Öffne eine PDF.")
        invoke(a, "sendCurrentInput", Boolean::class.javaPrimitiveType!! to false)
        val started = shadowOf(a).nextStartedActivityForResult
        assertNotNull("the owner request opens Android's document picker", started)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, started!!.intent.action)
        AgentActivity.chatTranscriptIo.submit {}.get(5, TimeUnit.SECONDS)
        val saved = Bundle()
        first.saveInstanceState(saved)
        if (configurationChange)
            Activity::class.java.getDeclaredField("mChangingConfigurations").apply { isAccessible = true }.setBoolean(a, true)
        runCatching { first.pause().stop().destroy() }
        assertEquals("RAM dialogue kept only for a configuration change", configurationChange,
            LocalDialogueRetention.context.holdsSession(id))
        val controller = Robolectric.buildActivity(AgentActivity::class.java).setup(saved).visible()
        try {
            repeat(2) { advance() }
            val recreated = controller.get()
            assertEquals("the same conversation continues", id, session(recreated))
            val afterCreate = chat(recreated)
            assertFalse("no greeting in the middle of a restored conversation: $afterCreate",
                afterCreate.any { it.contains("Mein eigener Startkern") })
            assertFalse(afterCreate.toString(), afterCreate.any { it.startsWith("Hinweis:") })
            assertEquals("each message once: $afterCreate", 1, afterCreate.count { it == statement })
            assertEquals(afterCreate.toString(), 1, afterCreate.count { it == "Öffne eine PDF." })
            // The picker result reaches the new instance (an unreadable selection keeps this test file-format free).
            AgentActivity::class.java.getDeclaredMethod("onActivityResult", Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Intent::class.java).apply { isAccessible = true }
                .invoke(recreated, started.requestCode, Activity.RESULT_OK, Intent().apply {
                    data = Uri.parse("content://at.heinz.test.unavailable/document/real-selection")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            val lane = field(recreated, "documentIoExecutor") as java.util.concurrent.ExecutorService
            repeat(2) { lane.submit {}.get(10, TimeUnit.SECONDS); repeat(4) { advance() } }
            val afterResult = chat(recreated)
            assertTrue("the document reply arrived after the return: $afterResult", afterResult.size > afterCreate.size)
            assertFalse("still no greeting after the picker result: $afterResult",
                afterResult.any { it.contains("Mein eigener Startkern") })
            val answer = context(recreated).objectStateAnswer(id, recall)
            assertNotNull("object-state memory survives the picker round trip", answer)
            assertTrue(answer, answer!!.contains("Farbe: blau"))
            assertTrue(answer, answer.contains("Ort: im Keller"))
            assertTrue(answer, answer.contains("Anzahl Schrauben: 12"))
            assertFalse("restored once, not replayed twice: $answer", answer.contains("unverändert"))
        } finally { leave(controller) }
    }

    @Test fun documentPickerReturnAfterTheSystemDestroyedTheActivityShowsNoGreeting() =
        pickerRecreation(configurationChange = false)

    @Test fun documentPickerReturnAfterAConfigurationChangeShowsNoGreeting() =
        pickerRecreation(configurationChange = true)

    @Test fun restoreTimeoutIsVisibleInsteadOfASilentlyEmptyContext() {
        session { a -> modelTurn(a, statement, "Alles klar.") }
        // A slow store: the transcript writer is busy longer than the 2 s startup bounds.
        val release = CountDownLatch(1)
        AgentActivity.chatTranscriptIo.execute { release.await(30, TimeUnit.SECONDS) }
        val controller = try { open() } finally { release.countDown() }
        try {
            val chat = chat(controller.get())
            assertTrue("the incomplete restore is announced: $chat",
                chat.contains(ConversationStartupNotice.CONVERSATION_NOT_LOADED))
            assertFalse("no fresh-start greeting pretends nothing happened: $chat",
                chat.any { it.contains("Mein eigener Startkern") })
            assertFalse("the notice is no stored conversation", File(RuntimeEnvironment.getApplication().filesDir,
                "chat-history").listFiles().orEmpty().any { it.readText().contains("Hinweis:") })
        } finally { leave(controller) }
    }
}
