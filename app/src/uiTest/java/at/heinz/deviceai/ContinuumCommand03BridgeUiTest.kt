package at.heinz.deviceai

import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.EditText
import android.widget.TextView
import at.heinz.deviceai.chief.ChiefWorkerPool
import at.heinz.deviceai.cognitive.ApprovedWebResearchSession
import at.heinz.deviceai.cognitive.DeliberationCancellation
import at.heinz.deviceai.cognitive.LocalDialogueRetention
import at.heinz.deviceai.cognitive.WebToolRequest
import at.heinz.deviceai.localmodel.LocalModelRuntime
import at.heinz.deviceai.ui.ContinuumCommand03OriginalStage
import org.json.JSONObject
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
import org.robolectric.shadows.ShadowDisplayManager
import org.robolectric.shadows.ShadowAlertDialog
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
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
    private fun set(a: AgentActivity, name: String, value: Any?) =
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(a, value)
    private fun stageField(stage: WebView, name: String): Any? =
        ContinuumCommand03OriginalStage::class.java.getDeclaredField(name).apply { isAccessible = true }.get(stage)
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
        id: String = "request-1", mainFrame: Boolean = true,
        selectionStart: Int = value.length, selectionEnd: Int = value.length): Boolean {
        val uri = Uri.Builder().scheme("continuum-native").authority("action").appendPath(kind)
            .appendQueryParameter("value", value)
            .appendQueryParameter("sessionId", session ?: field(a, "sessionId") as String)
            .appendQueryParameter("id", id)
            .appendQueryParameter("selectionStart", selectionStart.toString())
            .appendQueryParameter("selectionEnd", selectionEnd.toString()).build()
        return stage(a).webViewClient.shouldOverrideUrlLoading(stage(a), request(uri, mainFrame))
    }
    private fun active(view: Any): Boolean = view.javaClass.getDeclaredField("foregroundActive")
        .apply { isAccessible = true }.getBoolean(view)
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    private fun messages(a: AgentActivity) = all(field(a, "chatContainer") as View)
        .filterIsInstance<TextView>().map { it.text.toString() }
    private fun ready(a: AgentActivity) = stage(a).webViewClient.onPageFinished(stage(a),
        ContinuumCommand03OriginalStage.INDEX)
    private fun acknowledgement(a: AgentActivity): JSONObject {
        val script = shadowOf(stage(a)).lastEvaluatedJavascript
        val prefix = "window.ContinuumNative?.commandResult("
        assertTrue("Native result must be acknowledged through the actual WebView: $script", script.startsWith(prefix))
        return JSONObject(script.removePrefix(prefix).removeSuffix(");"))
    }
    private fun receipt(a: AgentActivity, id: String): JSONObject? {
        val receipts = stageField(stage(a), "commands") as Map<*, *>
        val record = receipts["${field(a, "sessionId")}:$id"] ?: return null
        return record.javaClass.getDeclaredField("result").apply { isAccessible = true }.get(record) as? JSONObject
    }
    private fun advance() = shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
    private fun await(description: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
        while (!condition() && System.nanoTime() < deadline) { advance(); Thread.sleep(5) }
        assertTrue(description, condition())
    }
    private fun measure(view: View, width: Int, height: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        repeat(3) { advance() }
    }
    private fun resizeWindow(a: AgentActivity, width: Int, height: Int) {
        ShadowDisplayManager.changeDisplay(0, "w${width}dp-h${height}dp-mdpi")
        a.window.setLayout(width, height)
        // Follow the real posted reflow; its root can change between measure passes.
        repeat(3) {
            measure(a.window.decorView, width, height)
            measure(field(a, "uiWindowRoot") as View, width, height)
        }
    }
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

    @Test fun actualPhoneFoldPhoneReflowKeepsTheSameReadyPageAndDraftSelection() = withActivity { _, a ->
        resizeWindow(a, 392, 852)
        ready(a)
        val web = stage(a)
        val url = web.url
        val draft = "Erster Absatz\nDer ungesendete Entwurf bleibt beim Falten erhalten."
        action(a, "draft", draft, selectionStart = 4, selectionEnd = 15)
        for ((width, height) in listOf(904 to 768, 392 to 852)) {
            val previousRoot = field(a, "uiWindowRoot")
            resizeWindow(a, width, height)
            assertNotSame("The fixture must exercise the real root replacement", previousRoot, field(a, "uiWindowRoot"))
            assertEquals(width.toFloat(), (field(a, "renderedWindowWidthDp") as Float), .1f)
            assertSame("Width-only reflow must keep the live document and request page", web, stage(a))
            assertEquals(url, web.url)
            assertEquals(false, stageField(web, "disposed"))
            assertEquals(true, stageField(web, "pageReady"))
            assertTrue(web.isAttachedToWindow)
            assertEquals(1, all(a.window.decorView).count { it is WebView })
            val editor = field(a, "input") as EditText
            assertEquals(draft, editor.text.toString())
            assertEquals(4, editor.selectionStart)
            assertEquals(15, editor.selectionEnd)
            assertFalse(active(field(a, "hologramView")!!))
            assertFalse(active(field(a, "homeView")!!))
        }
    }

    @Test fun realClockCommandConsumesTheComposerAndAcknowledgesOnlyOneSubmission() = withActivity { _, a ->
        ready(a)
        val text = "Wie spät ist es?"
        val before = messages(a).count { it == text }
        action(a, "draft", text)
        assertTrue(action(a, "command", text, id = "clock-1"))
        val result = acknowledgement(a)
        assertEquals("clock-1", result.getString("id"))
        assertEquals(field(a, "sessionId"), result.getString("sessionId"))
        assertTrue(result.getBoolean("accepted"))
        assertTrue((field(a, "input") as EditText).text.isEmpty())
        assertNull(field(a, "pendingCommand03Input"))
        assertEquals(before + 1, messages(a).count { it == text })
        assertTrue(messages(a).any { Regex("Es ist \\d{2}:\\d{2} Uhr\\. \\(Geräteuhr · .+\\)").matches(it) })
        assertFalse(field(a, "busy") as Boolean)
        assertFalse(field(a, "activeLocalRequest") as Boolean)
        assertTrue(action(a, "command", text, id = "clock-1"))
        assertEquals(result.toString(), acknowledgement(a).toString())
        assertEquals("An identical navigation must not create another real user message", before + 1,
            messages(a).count { it == text })
    }

    @Test fun busyRejectionPreservesTheActualNativeDraftAndDoesNotSubmit() = withActivity { _, a ->
        ready(a)
        val text = "Dieser Auftrag bleibt editierbar.\nZweite Zeile."
        action(a, "draft", text)
        val before = messages(a)
        set(a, "appLookupActive", true)
        set(a, "activeLocalRequest", true)
        call(a, "setBusy", Boolean::class.javaPrimitiveType!! to true)
        val generation = field(a, "appLookupGeneration")
        try {
            assertTrue(action(a, "command", text, id = "busy-1"))
            val result = acknowledgement(a)
            assertFalse(result.getBoolean("accepted"))
            assertEquals("busy", result.getString("reason"))
            assertEquals(text, (field(a, "input") as EditText).text.toString())
            assertEquals(before, messages(a))
            assertTrue(field(a, "appLookupActive") as Boolean)
            assertTrue(field(a, "activeLocalRequest") as Boolean)
            assertEquals(generation, field(a, "appLookupGeneration"))
            assertNull(field(a, "pendingCommand03Input"))
        } finally {
            set(a, "appLookupActive", false)
            set(a, "activeLocalRequest", false)
            call(a, "setBusy", Boolean::class.javaPrimitiveType!! to false)
        }
    }

    @Test fun busyChiefStatusReachesTheRealDiagnosticWithoutCancellingOrStartingModelWork() = withActivity { _, a ->
        ready(a)
        val cancellation = DeliberationCancellation()
        set(a, "activeChatCancellation", cancellation)
        set(a, "activeLocalRequest", true)
        call(a, "setBusy", Boolean::class.javaPrimitiveType!! to true)
        val runtime = field(a, "localRuntime") as LocalModelRuntime
        val runtimeActions = java.util.concurrent.CopyOnWriteArrayList<String>()
        // Lifecycle/status reads stay available; no inference or cancellation may reach the native engine.
        val readOnlyRuntime = java.lang.reflect.Proxy.newProxyInstance(LocalModelRuntime::class.java.classLoader,
            arrayOf(LocalModelRuntime::class.java)) { _, method, args ->
            if (method.declaringClass != Any::class.java && !method.name.startsWith("get")) {
                runtimeActions += method.name
                throw AssertionError("Read-only Chief status attempted runtime action: ${method.name}")
            }
            method.invoke(runtime, *(args ?: emptyArray()))
        } as LocalModelRuntime
        set(a, "localRuntime", readOnlyRuntime)
        try {
            val text = "JARVIS, Teamstatus"
            action(a, "draft", text)
            assertTrue(action(a, "command", text, id = "busy-status-1"))
            assertTrue(acknowledgement(a).getBoolean("accepted"))
            val pool = field(a, "chiefStatusPool") as? ChiefWorkerPool
            assertNotNull("HTML submission must reach the existing read-only diagnostic", pool)
            assertTrue(pool!!.awaitIdle(5_000))
            await("The real local diagnostic posts its result") {
                messages(a).any { it.startsWith("JARVIS · Mitarbeiterstatus (lokale Diagnose)") }
            }
            assertTrue((field(a, "input") as EditText).text.isEmpty())
            assertTrue(field(a, "busy") as Boolean)
            assertTrue(field(a, "activeLocalRequest") as Boolean)
            assertSame(cancellation, field(a, "activeChatCancellation"))
            cancellation.throwIfCancelled()
            assertSame(readOnlyRuntime, field(a, "localRuntime"))
            assertTrue("No model generation, tool continuation, load or cancellation: $runtimeActions", runtimeActions.isEmpty())
            assertNull(field(a, "pendingCommand03Input"))
        } finally {
            set(a, "localRuntime", runtime)
            set(a, "activeChatCancellation", null)
            set(a, "activeLocalRequest", false)
            call(a, "setBusy", Boolean::class.javaPrimitiveType!! to false)
        }
    }

    @Test fun busyOwnerCorrectionUsesTheExistingStopAndAcknowledgesOneClarification() = withActivity { _, a ->
        ready(a)
        set(a, "appLookupActive", true)
        set(a, "activeLocalRequest", true)
        call(a, "setBusy", Boolean::class.javaPrimitiveType!! to true)
        val generation = field(a, "appLookupGeneration") as Long
        val text = "Stopp, das meine ich nicht."
        val clarification = "Verstanden. Was soll ich anders machen?"
        val before = messages(a).count { it == clarification }
        action(a, "draft", text)
        assertTrue(action(a, "command", text, id = "busy-correction-1"))
        assertTrue(acknowledgement(a).getBoolean("accepted"))
        assertFalse(field(a, "appLookupActive") as Boolean)
        assertFalse(field(a, "activeLocalRequest") as Boolean)
        assertFalse(field(a, "busy") as Boolean)
        assertTrue((field(a, "appLookupGeneration") as Long) > generation)
        assertEquals(before + 1, messages(a).count { it == clarification })
        assertTrue((field(a, "input") as EditText).text.isEmpty())
        assertNull(field(a, "pendingCommand03Input"))
        assertTrue(action(a, "command", text, id = "busy-correction-1"))
        assertTrue(acknowledgement(a).getBoolean("accepted"))
        assertEquals(before + 1, messages(a).count { it == clarification })
    }

    @Test fun busyResearchReplyConsumesOnlyTheExistingDialogTicketWithoutExecutingIt() = withActivity { _, a ->
        ready(a)
        a.getSharedPreferences("continuum-internet", Context.MODE_PRIVATE).edit()
            .putString("mode", "ASK").commit()
        val cancellation = DeliberationCancellation()
        set(a, "activeChatCancellation", cancellation)
        set(a, "activeLocalRequest", true)
        call(a, "setBusy", Boolean::class.javaPrimitiveType!! to true)
        val decisions = mutableListOf<Boolean>()
        val recordOnly: (Boolean) -> Unit = { decisions += it }
        val query = "Synthetische Frage für die vorhandene Freigabe"
        try {
            AgentActivity::class.java.getDeclaredMethod("showStructuredToolConfirmation", ToolProposal::class.java,
                Boolean::class.javaPrimitiveType, Function1::class.java).apply { isAccessible = true }
                .invoke(a, ToolProposal("web_search", mapOf("query" to query)), false, recordOnly)
            val dialog = requireNotNull(ShadowAlertDialog.getLatestAlertDialog())
            assertTrue(dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick())
            advance()
            assertNotNull(field(a, "pendingResearchChatApproval"))
            assertTrue(messages(a).any { it.startsWith("Soll ich diese Recherche einmal ausführen?") })
            action(a, "draft", "Ja, mach.")
            assertTrue(action(a, "command", "Ja, mach.", id = "busy-consent-1"))
            assertTrue(acknowledgement(a).getBoolean("accepted"))
            assertEquals(listOf(true), decisions)
            assertNull(field(a, "pendingResearchChatApproval"))
            @Suppress("UNCHECKED_CAST")
            val approved = field(a, "approvedModelWebSession") as
                java.util.concurrent.atomic.AtomicReference<ApprovedWebResearchSession?>
            val grant = requireNotNull(approved.get())
            assertEquals(WebToolRequest.Search(query), grant.call)
            assertTrue(grant.isAuthorized())
            assertNull("The fixture records consent only; it never starts a network worker", field(a, "activeWebResearchSession"))
            assertSame(cancellation, field(a, "activeChatCancellation"))
            cancellation.throwIfCancelled()
            assertTrue(field(a, "busy") as Boolean)
            assertTrue(field(a, "activeLocalRequest") as Boolean)
            assertTrue(action(a, "command", "Ja, mach.", id = "busy-consent-1"))
            assertEquals(listOf(true), decisions)
            assertSame(grant, approved.get())
        } finally {
            ShadowAlertDialog.getLatestAlertDialog()?.dismiss()
            set(a, "activeChatCancellation", null)
            set(a, "activeLocalRequest", false)
            call(a, "setBusy", Boolean::class.javaPrimitiveType!! to false)
        }
    }

    @Test fun asynchronousBotLookupAcknowledgesConsumptionAndPreservesNewerTyping() = withActivity { _, a ->
        ready(a)
        val executor = field(a, "backlogExecutor") as ExecutorService
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        executor.execute { entered.countDown(); release.await(8, TimeUnit.SECONDS) }
        assertTrue("The existing serial lookup executor must really be held", entered.await(2, TimeUnit.SECONDS))
        val request = "wechsle zu Atlas"
        val nextDraft = "Ein neuer Entwurf\nbleibt während der Zuordnung erhalten."
        try {
            action(a, "draft", request)
            assertTrue(action(a, "command", request, id = "bot-select-1"))
            assertNotNull(field(a, "pendingCommand03Input"))
            assertTrue(field(a, "botLookupPending") as Boolean)
            assertNull("Dispatching a lookup is not yet acceptance", receipt(a, "bot-select-1"))
            action(a, "draft", nextDraft, selectionStart = 3, selectionEnd = 12)
            assertEquals("Typing must not invalidate the native router's in-flight text", request,
                (field(a, "input") as EditText).text.toString())
        } finally { release.countDown() }
        await("The actual local bot lookup consumes and acknowledges its input") {
            receipt(a, "bot-select-1") != null && field(a, "pendingCommand03Input") == null
        }
        assertTrue(requireNotNull(receipt(a, "bot-select-1")).getBoolean("accepted"))
        await("The existing bot-selection callback completes") {
            (field(a, "selectedBot") as? at.heinz.deviceai.chief.ChiefBotProfile)?.agentId == "bot-atlas"
        }
        val editor = field(a, "input") as EditText
        assertEquals(nextDraft, editor.text.toString())
        assertEquals(3, editor.selectionStart)
        assertEquals(12, editor.selectionEnd)
        assertNull(field(a, "deferredCommand03Draft"))
        assertFalse(field(a, "busy") as Boolean)
        assertFalse(field(a, "activeLocalRequest") as Boolean)
    }
}
