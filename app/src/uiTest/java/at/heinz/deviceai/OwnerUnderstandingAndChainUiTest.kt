package at.heinz.deviceai

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import at.heinz.deviceai.cognitive.LocalChatTranscriptStore
import at.heinz.deviceai.cognitive.LocalDialogueRetention
import at.heinz.deviceai.localmodel.ImportedLocalModel
import at.heinz.deviceai.localmodel.LocalInferenceCallback
import at.heinz.deviceai.localmodel.LocalInferenceEvent
import at.heinz.deviceai.localmodel.LocalInferenceMetrics
import at.heinz.deviceai.localmodel.LocalModelBackend
import at.heinz.deviceai.localmodel.LocalModelCapabilities
import at.heinz.deviceai.localmodel.LocalModelRuntime
import at.heinz.deviceai.localmodel.LocalModelState
import at.heinz.deviceai.localmodel.LocalToolResponse
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The two hooks of the owner-turn intake in the real Activity: a misrecognised name is repaired before routing and
 * named once in the answer, and a recognised multi-step request runs as a verified chain through the Activity's own
 * ToolGate and device executor (Robolectric's package manager is the device). The model boundary is controlled.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class OwnerUnderstandingAndChainUiTest {
    private class Runtime : LocalModelRuntime {
        @Volatile override var state = LocalModelState.READY
        override val loadedModel = ImportedLocalModel("understanding-fixture", "v1", "a".repeat(64), 1,
            LocalModelCapabilities(true, false, false, false, true, 32_768, null, setOf(LocalModelBackend.CPU)))
        val prompts = CopyOnWriteArrayList<String>()
        private val pending = AtomicReference<LocalInferenceCallback?>()
        val running: Boolean get() = pending.get() != null
        override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) = error("Unexpected model load")
        override fun unload(callback: (Result<Unit>) -> Unit) = error("Unexpected model unload")
        override fun stream(prompt: String, callback: LocalInferenceCallback) {
            prompts += prompt
            check(pending.compareAndSet(null, callback)) { "Concurrent inference" }
            state = LocalModelState.GENERATING
        }
        override fun respondToTools(responses: List<LocalToolResponse>, callback: LocalInferenceCallback) = error("Unexpected tool continuation")
        fun answer(text: String) {
            val callback = checkNotNull(pending.getAndSet(null))
            state = LocalModelState.READY
            callback.onEvent(LocalInferenceEvent.TextDelta(text))
            callback.onEvent(LocalInferenceEvent.Completed(LocalInferenceMetrics(0, 0, 0, null, null, null, 1)))
        }
        override fun cancel() {
            val callback = pending.getAndSet(null) ?: return
            state = LocalModelState.READY
            callback.onEvent(LocalInferenceEvent.Cancelled)
        }
    }

    @Before fun configure() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        LocalDialogueRetention.context.clear()
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("continuum-command03", Context.MODE_PRIVATE).edit().putBoolean("original_html_main", false).commit()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit().putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-internet", Context.MODE_PRIVATE).edit().putString("mode", "OFF").commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("continuum-task-chain", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun field(activity: AgentActivity, name: String): Any? = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(activity)
    private fun session(activity: AgentActivity) = field(activity, "sessionId") as String
    private fun advance() = shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
    private fun until(label: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!condition() && System.nanoTime() < deadline) { advance(); Thread.sleep(5) }
        assertTrue(label, condition())
    }
    private fun withActivity(body: (AgentActivity, Runtime) -> Unit) {
        val runtime = Runtime()
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        try {
            controller.setup().visible()
            repeat(2) { advance() }
            controller.windowFocusChanged(true)
            val activity = controller.get()
            AgentActivity::class.java.getDeclaredField("localRuntime").apply { isAccessible = true }.set(activity, runtime)
            repeat(2) { advance() }
            body(activity, runtime)
        } finally {
            runtime.cancel()
            runCatching { until("request released") { !(field(controller.get(), "busy") as Boolean) } }
            runCatching { controller.close() }
            AgentActivity.chatTranscriptIo.submit {}.get(2, TimeUnit.SECONDS)
            LocalDialogueRetention.context.clear()
        }
    }
    private fun submit(activity: AgentActivity, request: String) {
        if (field(activity, "activeWorkArea") != ContinuumWorkArea.CHAT) {
            val chat = JarvisHomeTestRoutes.destination(activity.window.decorView, "chat")
            assertNotNull("Chat entry exists", chat)
            JarvisHomeTestRoutes.touch(activity.window.decorView, chat)
            until("Chat transition completed") {
                field(activity, "activeWorkArea") == ContinuumWorkArea.CHAT && field(activity, "conversationActive") == true
            }
        }
        (field(activity, "input") as EditText).setText(request)
        assertTrue((field(activity, "sendButton") as Button).performClick())
    }
    private fun transcript(activity: AgentActivity): List<LocalChatTranscriptStore.Entry> {
        AgentActivity.chatTranscriptIo.submit {}.get(2, TimeUnit.SECONDS)
        return LocalChatTranscriptStore(File(activity.filesDir, "chat-history")).load(session(activity))
    }
    private fun lastReply(activity: AgentActivity) = transcript(activity).last { !it.user }.text
    private fun lastOwner(activity: AgentActivity) = transcript(activity).last { it.user }.text
    private fun idle(activity: AgentActivity) = until("turn finished") { !(field(activity, "busy") as Boolean) }

    private fun installTermux() {
        val app = RuntimeEnvironment.getApplication()
        val application = ApplicationInfo().apply { packageName = "com.termux"; name = "Termux"; nonLocalizedLabel = "Termux" }
        @Suppress("DEPRECATION")
        val info = PackageInfo().apply {
            packageName = "com.termux"; versionName = "0.118.1"; versionCode = 1020; applicationInfo = application
        }
        shadowOf(app.packageManager).installPackage(info)
        shadowOf(app.packageManager).addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply { packageName = "com.termux"; name = "com.termux.app.TermuxActivity"; applicationInfo = application }
                nonLocalizedLabel = "Termux"
            })
    }

    @Test fun aMisheardNameIsRepairedBeforeTheModelSeesItAndTheAnswerSaysSoOnce() = withActivity { activity, runtime ->
        submit(activity, "Wie richte ich Schisuku ein?")
        until("model received the turn") { runtime.running }
        assertTrue(runtime.prompts.last(), runtime.prompts.last().contains("Shizuku"))
        assertFalse(runtime.prompts.last().contains("Schisuku"))
        runtime.answer("Öffne die App und starte den Dienst.")
        idle(activity)
        assertEquals("Wie richte ich Shizuku ein?", lastOwner(activity))
        assertTrue(lastReply(activity), lastReply(activity).startsWith("Ich habe „Schisuku“ als Shizuku verstanden."))
        assertTrue(lastReply(activity).contains("starte den Dienst"))
        // The next, correctly recognised turn carries no notice and is passed on untouched.
        submit(activity, "Und was mache ich danach?")
        until("model received the follow-up") { runtime.running }
        runtime.answer("Danach erteilst du die Berechtigung.")
        idle(activity)
        assertEquals("Und was mache ich danach?", lastOwner(activity))
        assertFalse(lastReply(activity), lastReply(activity).contains("verstanden"))
    }

    @Test fun aMisheardAppInAMultiStepRequestRunsAsAVerifiedChainThroughTheRealGate() = withActivity { activity, runtime ->
        installTermux()
        submit(activity, "Welche Version von Thermux habe ich")
        idle(activity)
        assertEquals("no model turn for a recognised chain", 0, runtime.prompts.size)
        assertEquals("Welche Version von Termux habe ich", lastOwner(activity))
        val reply = lastReply(activity)
        assertTrue(reply, reply.startsWith("Ich habe „Thermux“ als Termux verstanden."))
        assertTrue(reply, reply.contains("Erledigt. Termux (com.termux) ist installiert: Version 0.118.1"))
        assertTrue(reply, reply.contains("1. App „Termux“ suchen") && reply.contains("2. Version lesen"))
        // The stage record of the Command03 page holds the two real steps of this chain, both verified.
        val stage = (field(activity, "command03Activity") as at.heinz.deviceai.ui.Command03ActivityProjection).snapshot()
        assertNotNull(stage)
        val steps = stage!!.getJSONArray("steps")
        assertEquals(2, steps.length())
        assertEquals(listOf("DONE", "DONE"), (0 until 2).map { steps.getJSONObject(it).getString("state") })
        assertFalse(stage.getBoolean("active"))
    }

    @Test fun aChainThatCannotFindTheAppReportsTheFailedStepAndClaimsNothing() = withActivity { activity, runtime ->
        submit(activity, "Welche Version von Nirgendwo habe ich")
        idle(activity)
        assertEquals(0, runtime.prompts.size)
        val reply = lastReply(activity)
        assertTrue(reply, reply.startsWith("Nicht abgeschlossen: Schritt 1 von 2"))
        assertTrue(reply, reply.contains("keine installierte App"))
        assertFalse(reply, reply.contains("Erledigt."))
    }
}
