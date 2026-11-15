package at.heinz.deviceai

import android.content.Context
import android.net.Uri
import android.os.Looper
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.EditText
import at.heinz.deviceai.cognitive.LocalDialogueRetention
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Actual Activity/WebView navigation and lifecycle. No WebGL or Fold-device claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w392dp-h852dp-port-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class ContinuumCommand03BridgeUiTest {
    @Before fun prepare() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        LocalDialogueRetention.context.forgetForExit()
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit()
            .putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-internet", Context.MODE_PRIVATE).edit()
            .putString("mode", "OFF").commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        app.getSharedPreferences("continuum-command03", Context.MODE_PRIVATE).edit()
            .putBoolean("original_html_main", true).commit()
    }

    private fun field(a: AgentActivity, name: String): Any? =
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(a)
    private fun call(a: AgentActivity, name: String, vararg args: Pair<Class<*>, Any?>): Any? =
        AgentActivity::class.java.getDeclaredMethod(name, *args.map { it.first }.toTypedArray())
            .apply { isAccessible = true }.invoke(a, *args.map { it.second }.toTypedArray())
    private fun stage(a: AgentActivity) = field(a, "command03MainStage") as WebView
    private fun request(uri: Uri, mainFrame: Boolean = true): WebResourceRequest = object : WebResourceRequest {
        override fun getUrl() = uri
        override fun isForMainFrame() = mainFrame
        override fun isRedirect() = false
        override fun hasGesture() = true
        override fun getMethod() = "GET"
        override fun getRequestHeaders() = emptyMap<String, String>()
    }
    private fun action(a: AgentActivity, kind: String, value: String, session: String? = null,
        id: String = "request-1", mainFrame: Boolean = true): Boolean {
        val uri = Uri.Builder().scheme("continuum-native").authority("action").appendPath(kind)
            .appendQueryParameter("value", value)
            .appendQueryParameter("sessionId", session ?: field(a, "sessionId") as String)
            .appendQueryParameter("id", id)
            .appendQueryParameter("selectionStart", value.length.toString())
            .appendQueryParameter("selectionEnd", value.length.toString()).build()
        return stage(a).webViewClient.shouldOverrideUrlLoading(stage(a), request(uri, mainFrame))
    }
    private fun active(view: Any): Boolean = view.javaClass.getDeclaredField("foregroundActive")
        .apply { isAccessible = true }.getBoolean(view)
    private fun withActivity(block: (org.robolectric.android.controller.ActivityController<AgentActivity>, AgentActivity) -> Unit) {
        val controller = Robolectric.buildActivity(AgentActivity::class.java).setup().visible()
        try {
            shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
            block(controller, controller.get())
        } finally {
            runCatching { controller.pause().stop().destroy() }
            AgentActivity.chatTranscriptIo.submit {}.get(8, TimeUnit.SECONDS)
        }
    }

    @Test fun htmlDraftSurvivesTheActualSwitchBackToTheNativeComposer() = withActivity { _, a ->
        val draft = "Erste Zeile\nZweite Zeile mit ä und 😀\tEnde"
        assertTrue(stage(a).isAttachedToWindow)
        assertTrue(action(a, "draft", draft))
        assertEquals("The visible HTML draft must reach the existing saved-state composer", draft,
            (field(a, "input") as EditText).text.toString())
        call(a, "switchCommand03MainDesign", Boolean::class.javaPrimitiveType!! to false)
        assertNull(field(a, "command03MainStage"))
        assertEquals(draft, (field(a, "input") as EditText).text.toString())
    }

    @Test fun hiddenNativeStageStaysPausedAcrossConversationLayoutAndResume() = withActivity { controller, a ->
        val hologram = field(a, "hologramView")!!
        val home = field(a, "homeView")!!
        call(a, "refreshConversationLayout")
        assertFalse("Conversation layout must not wake the covered native head", active(hologram))
        assertFalse(active(home))
        assertFalse("The native controls under the page must be hidden", (field(a, "dashboardSection") as View).isShown)
        controller.pause().resume()
        call(a, "refreshConversationLayout")
        assertFalse(active(hologram))
        assertFalse(active(home))
        assertTrue(stage(a).isAttachedToWindow)
    }

    @Test fun staleAndSubframeDraftsCannotReplaceTheCurrentConversationDraft() = withActivity { _, a ->
        val editor = field(a, "input") as EditText
        editor.setText("Aktueller Entwurf")
        action(a, "draft", "Fremder Chat", session = "11111111-1111-4111-8111-111111111111")
        assertEquals("Aktueller Entwurf", editor.text.toString())
        action(a, "draft", "Unterframe", mainFrame = false)
        assertEquals("Aktueller Entwurf", editor.text.toString())
    }
}
