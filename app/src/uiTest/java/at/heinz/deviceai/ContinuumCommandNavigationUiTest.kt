package at.heinz.deviceai

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import at.heinz.deviceai.cognitive.DeliberationCancellation
import at.heinz.deviceai.cognitive.LocalChatTranscriptStore
import at.heinz.deviceai.cognitive.LocalConversationContext
import at.heinz.deviceai.cognitive.LocalDialogueRetention
import at.heinz.deviceai.ui.ContinuumHomeView
import at.heinz.deviceai.ui.JarvisTiles
import org.junit.Assert.*
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

/** Real Activity/store/routing under Robolectric. No device, model-quality or Filament claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContinuumCommandNavigationUiTest {
    private val first = "11111111-1111-4111-8111-111111111111"
    private val second = "22222222-2222-4222-8222-222222222222"
    private val third = "33333333-3333-4333-8333-333333333333"
    private lateinit var store: LocalChatTranscriptStore

    @Before fun prepare() {
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
        store = LocalChatTranscriptStore(File(app.filesDir, "chat-history"))
        store.append(first, true, "Meine Kiste ist blau.", 1)
        store.appendContextTurn(first, "Meine Kiste ist blau.", "Verstanden.", 2)
        store.append(second, true, "Mein Ball ist rot.", 3)
        store.appendContextTurn(second, "Mein Ball ist rot.", "Verstanden.", 4)
        store.append(third, true, "Meine Tasche ist grün.", 5)
        store.appendContextTurn(third, "Meine Tasche ist grün.", "Verstanden.", 6)
        store.setActive(first)
    }

    private fun field(a: AgentActivity, name: String): Any? =
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(a)
    private fun set(a: AgentActivity, name: String, value: Any?) =
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(a, value)
    private fun call(a: AgentActivity, name: String, vararg args: Pair<Class<*>, Any?>): Any? =
        AgentActivity::class.java.getDeclaredMethod(name, *args.map { it.first }.toTypedArray())
            .apply { isAccessible = true }.invoke(a, *args.map { it.second }.toTypedArray())
    private fun select(a: AgentActivity, id: String) = call(a, "selectStoredConversation", String::class.java to id)
    private fun session(a: AgentActivity) = field(a, "sessionId") as String
    private fun context(a: AgentActivity) = field(a, "localConversationContext") as LocalConversationContext
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    private fun messages(a: AgentActivity) = all(field(a, "chatContainer") as LinearLayout)
        .filterIsInstance<TextView>().map { it.text.toString() }
    private fun advance() = shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
    private fun await(message: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
        while (!condition() && System.nanoTime() < deadline) { advance(); Thread.sleep(5) }
        assertTrue(message, condition())
    }
    private fun withActivity(block: (ActivityController<AgentActivity>, AgentActivity) -> Unit) {
        val controller = Robolectric.buildActivity(AgentActivity::class.java).setup().visible()
        try {
            val a = controller.get()
            await("Activity is foreground") { field(a, "foregroundLearningActive") == true }
            block(controller, a)
        } finally {
            runCatching { controller.pause().stop().destroy() }
            AgentActivity.chatTranscriptIo.submit {}.get(8, TimeUnit.SECONDS)
        }
    }

    @Test fun sevenRealToolsAndFilePickerKeepAndroidReadOnlyPermission() = withActivity { _, a ->
        assertEquals(listOf("Gespräch", "Dateien", "Recherche", "Agenten", "Gedächtnis", "Computer", "Einstellungen"),
            JarvisTiles.bottomNav.map { it.label })
        val home = field(a, "homeView") as ContinuumHomeView
        val files = requireNotNull(home.findViewWithTag<View>("jarvis-bottom:files"))
        assertTrue(files.isEnabled && files.isClickable && files.isFocusable)
        assertTrue(files.performClick())
        val started = shadowOf(a).nextStartedActivityForResult
        assertNotNull("Real Android picker was launched", started)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, started.intent.action)
        assertEquals("application/pdf", started.intent.type)
        assertTrue(started.intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, started.intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertEquals(first, session(a))
        assertFalse(field(a, "busy") as Boolean)
    }

    @Test fun realRailSelectionRestoresOnlyTheSelectedContextAndKeepsTheSameAvatar() = withActivity { _, a ->
        val home = field(a, "homeView") as ContinuumHomeView
        val avatar = field(a, "hologramView")
        await("Stored conversation has a real native row") {
            home.findViewWithTag<View>("continuum-chat:$second") != null
        }
        assertTrue(home.findViewWithTag<View>("continuum-chat:$second").performClick())
        await("Second conversation selected") { session(a) == second }
        assertEquals(second, store.activeConversation { error("No new conversation") })
        assertTrue(messages(a).contains("Mein Ball ist rot."))
        assertFalse(messages(a).contains("Meine Kiste ist blau."))
        assertEquals(listOf("Mein Ball ist rot."), context(a).priorUserTurns(second))
        assertTrue(context(a).priorUserTurns(first).isEmpty())
        assertSame(avatar, field(a, "hologramView"))
        assertEquals(1, store.loadContextHistory(first).size)
    }

    @Test fun rapidSelectionsRejectAnOlderQueuedReadAndItsPersistentPointer() = withActivity { _, a ->
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        AgentActivity.chatTranscriptIo.execute { entered.countDown(); release.await(8, TimeUnit.SECONDS) }
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        try {
            select(a, second)
            select(a, third)
            assertEquals(first, session(a))
        } finally { release.countDown() }
        await("Only latest selection is applied") { session(a) == third }
        AgentActivity.chatTranscriptIo.submit {}.get(4, TimeUnit.SECONDS)
        repeat(4) { advance() }
        assertEquals(third, store.activeConversation { error("No new conversation") })
        assertFalse(messages(a).contains("Mein Ball ist rot."))
        assertEquals(listOf("Meine Tasche ist grün."), context(a).priorUserTurns(third))
    }

    @Test fun missingConversationLeavesVisibleChatContextAndSelectionIntact() = withActivity { _, a ->
        assertTrue(File(File(a.filesDir, "chat-history"), "$second.jsonl").delete())
        select(a, second)
        await("Failed restore resolves") { field(a, "pendingConversationSelection") == null }
        assertEquals(first, session(a))
        assertEquals(first, store.activeConversation { error("No new conversation") })
        assertTrue(messages(a).contains("Meine Kiste ist blau."))
        assertEquals(listOf("Meine Kiste ist blau."), context(a).priorUserTurns(first))
    }

    @Test fun runningLocalRequestIsCancelledButTheChatWaitsForItsActualEnd() = withActivity { _, a ->
        val cancellation = DeliberationCancellation()
        set(a, "activeChatCancellation", cancellation)
        set(a, "activeLocalRequest", true)
        call(a, "setBusy", Boolean::class.javaPrimitiveType!! to true)
        select(a, second)
        assertTrue(runCatching { cancellation.throwIfCancelled() }.isFailure)
        repeat(5) { advance() }
        assertEquals("No early context switch while a cancelled worker still owns the lane", first, session(a))
        assertEquals(first, store.activeConversation { error("No new conversation") })
        set(a, "activeChatCancellation", null)
        call(a, "setBusy", Boolean::class.javaPrimitiveType!! to false)
        await("Selection resumes after actual completion callback") { session(a) == second }
        assertFalse(field(a, "busy") as Boolean)
    }

    @Test fun pauseInvalidatesQueuedSelectionWithoutChangingTheStoredActiveChat() = withActivity { controller, a ->
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        AgentActivity.chatTranscriptIo.execute { entered.countDown(); release.await(8, TimeUnit.SECONDS) }
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        try { select(a, second); controller.pause() } finally { release.countDown() }
        AgentActivity.chatTranscriptIo.submit {}.get(4, TimeUnit.SECONDS)
        repeat(5) { advance() }
        assertEquals(first, session(a))
        assertEquals(first, store.activeConversation { error("No new conversation") })
        assertNull(field(a, "pendingConversationSelection"))
        controller.resume()
    }

    @Test fun newChatRetainsThePreviousSavedContextAndCanReopenIt() = withActivity { _, a ->
        call(a, "newCentralConversation")
        await("New central conversation created") { session(a) != first }
        assertEquals(1, store.loadContextHistory(first).size)
        select(a, first)
        await("Previous conversation resumed") { session(a) == first }
        assertEquals(listOf("Meine Kiste ist blau."), context(a).priorUserTurns(first))
    }

    @Test fun theSingleRealComposerLivesOutsideBothScrollablePresentations() = withActivity { _, a ->
        val input = field(a, "input") as EditText
        val dashboard = field(a, "dashboardSection") as View
        val chat = field(a, "chatColumn") as View
        val parents = generateSequence(input.parent) { it.parent }.toList()
        assertFalse(parents.contains(dashboard))
        assertFalse(parents.contains(chat))
        assertEquals(1, all(a.window.decorView).filterIsInstance<EditText>().count { it === input })
        assertTrue(input.isShown)
        call(a, "focusChatComposer")
        assertSame(input, field(a, "input"))
        assertTrue(input.isShown)
    }

    @Test fun responsiveInstrumentsKeepTheirCanvasPresentationAfterStateAndRendererCallbacks() = withActivity { _, a ->
        val home = field(a, "homeView") as ContinuumHomeView
        await("Workstation geometry is available") { home.jarvisShell.shell?.workstation == true }
        home.state = home.state.copy()
        val hologram = field(a, "hologramView") as ContinuumHologramView
        val content = ContinuumHologramView::class.java.getDeclaredField("cockpitPanelContent")
            .apply { isAccessible = true }
        assertNull("Fixed legacy Filament side panels must not cover responsive native instruments", content.get(hologram))
        hologram.onCockpitStatus?.invoke("Bereit", true)
        val nativeVisible = ContinuumHomeView::class.java.getDeclaredField("nativePanelsVisible")
            .apply { isAccessible = true }
        assertFalse(nativeVisible.getBoolean(home))
    }
}
