package at.heinz.deviceai

import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import android.os.Looper
import android.media.AudioFormat
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
import at.heinz.deviceai.cognitive.LatestLiveFrameHolder
import at.heinz.deviceai.cognitive.LiveFrameBinding
import at.heinz.deviceai.cognitive.LiveFrameOrigin
import at.heinz.deviceai.cognitive.LiveMonitorFeed
import at.heinz.deviceai.cognitive.OwnerComputerIntent
import at.heinz.deviceai.cognitive.ComputerApp
import at.heinz.deviceai.cognitive.ComputerGoal
import at.heinz.deviceai.cognitive.OwnerComputerWorkflow
import at.heinz.deviceai.localmodel.LocalModelRuntime
import at.heinz.deviceai.ui.Command03Binding
import at.heinz.deviceai.ui.Command03Draft
import at.heinz.deviceai.ui.Command03DraftLedger
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
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
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
    /** Controlled backend completion only; Activity callback, citations and route handlers are real. */
    @Implements(value = AgentBackendClient::class, isInAndroidSdk = false)
    class DeferredBackend {
        companion object { val replies = ArrayDeque<(Result<AgentReply>) -> Unit>() }
        @Implementation
        protected fun send(sessionId: String, message: String?, toolOutputs: List<ToolOutput>,
            callback: (Result<AgentReply>) -> Unit) { replies.addLast(callback) }
    }

    @Before fun prepare() {
        DeferredBackend.replies.clear()
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
            .putBoolean("original_html_main", true).putInt("neon_light", 70)
            .putBoolean("neon_motion", true).commit()
    }

    private fun field(a: AgentActivity, name: String): Any? =
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(a)
    private fun objectField(value: Any, name: String): Any? = value.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(value)
    private fun set(a: AgentActivity, name: String, value: Any?) =
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(a, value)
    private fun stageField(stage: WebView, name: String): Any? =
        ContinuumCommand03OriginalStage::class.java.getDeclaredField(name).apply { isAccessible = true }.get(stage)
    private fun call(a: AgentActivity, name: String, vararg args: Pair<Class<*>, Any?>): Any? =
        AgentActivity::class.java.getDeclaredMethod(name, *args.map { it.first }.toTypedArray())
            .apply { isAccessible = true }.invoke(a, *args.map { it.second }.toTypedArray())
    private fun stage(a: AgentActivity) = field(a, "command03MainStage") as WebView
    private fun binding(a: AgentActivity) = requireNotNull((stage(a) as ContinuumCommand03OriginalStage).binding)
    private fun request(uri: Uri, mainFrame: Boolean = true): WebResourceRequest = object : WebResourceRequest {
        override fun getUrl() = uri
        override fun isForMainFrame() = mainFrame
        override fun isRedirect() = false
        override fun hasGesture() = true
        override fun getMethod() = "GET"
        override fun getRequestHeaders() = emptyMap<String, String>()
    }
    private val commandUris = mutableMapOf<String, Uri>()
    private fun action(a: AgentActivity, kind: String, value: String, session: String? = null,
        id: String = "request-1", mainFrame: Boolean = true,
        selectionStart: Int? = null, selectionEnd: Int? = null,
        binding: Command03Binding = requireNotNull((stage(a) as ContinuumCommand03OriginalStage).binding),
        revision: Long? = null, draftText: String? = null): Boolean {
        val editor = field(a, "input") as EditText
        val deferred = field(a, "deferredCommand03Draft") as? Command03Draft
        val text = draftText ?: if (kind == "draft" || kind == "command") value
            else deferred?.text ?: editor.text.toString()
        val start = selectionStart ?: if (kind == "draft" || kind == "command") text.length
            else deferred?.selectionStart ?: editor.selectionStart.coerceIn(0, text.length)
        val end = selectionEnd ?: if (kind == "draft" || kind == "command") text.length
            else deferred?.selectionEnd ?: editor.selectionEnd.coerceIn(start, text.length)
        val next = revision ?: (field(a, "command03DraftLedger") as Command03DraftLedger).acknowledged + 1
        val uri = Uri.Builder().scheme("continuum-native").authority("action").appendPath(kind)
            .appendQueryParameter("value", value).appendQueryParameter("draftText", text)
            .appendQueryParameter("sessionId", session ?: field(a, "sessionId") as String)
            .appendQueryParameter("pageId", binding.pageId).appendQueryParameter("viewId", binding.viewId)
            .appendQueryParameter("viewRevision", binding.viewRevision.toString())
            .appendQueryParameter("clientEditRevision", next.toString())
            .appendQueryParameter("id", id)
            .appendQueryParameter("selectionStart", start.toString())
            .appendQueryParameter("selectionEnd", end.toString()).build()
        val key = "${binding.pageId}:${binding.viewId}:$id:$value"
        val sent = if (kind == "command" && revision == null) commandUris.getOrPut(key) { uri } else uri
        return stage(a).webViewClient.shouldOverrideUrlLoading(stage(a), request(sent, mainFrame))
    }
    private fun active(view: Any): Boolean = view.javaClass.getDeclaredField("foregroundActive")
        .apply { isAccessible = true }.getBoolean(view)
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    private fun messages(a: AgentActivity) = all(field(a, "chatContainer") as View)
        .filterIsInstance<TextView>().map { it.text.toString() }
    private fun ready(a: AgentActivity) = stage(a).webViewClient.onPageFinished(stage(a),
        ContinuumCommand03OriginalStage.INDEX)
    private fun computerPresentationState(a: AgentActivity): String {
        val web = stage(a)
        val rect = android.graphics.Rect()
        val globallyVisible = web.getGlobalVisibleRect(rect)
        return "foreground=${field(a, "foregroundLearningActive")}, viewVisible=${field(a, "computerViewVisible")}, " +
            "multiWindow=${a.isInMultiWindowMode}, sessionVisible=${call(a, "computerSessionVisible")}, " +
            "presentationVisible=${call(a, "command03ComputerVisible")}, attached=${web.isAttachedToWindow}, " +
            "shown=${web.isShown}, window=${web.windowVisibility}, size=${web.width}x${web.height}, " +
            "globalVisible=$globallyVisible, rect=$rect, active=${stageField(web, "livePresentationActive")}, " +
            "pageReady=${stageField(web, "pageReady")}, disposed=${stageField(web, "disposed")}, " +
            "frame=${stageField(web, "computerFrame")}, deadline=${stageField(web, "computerFrameDeadline") }"
    }
    private fun acknowledgement(a: AgentActivity): JSONObject {
        val script = shadowOf(stage(a)).lastEvaluatedJavascript
        val prefix = "window.ContinuumNative?.commandResult("
        assertTrue("Native result must be acknowledged through the actual WebView: $script", script.startsWith(prefix))
        return JSONObject(script.removePrefix(prefix).removeSuffix(");"))
    }
    private fun packet(a: AgentActivity, method: String): JSONObject {
        val script = shadowOf(stage(a)).lastEvaluatedJavascript
        val prefix = "window.ContinuumNative?.$method("
        assertTrue("Expected $method on the real WebView, got $script", script.startsWith(prefix))
        return JSONObject(script.removePrefix(prefix).removeSuffix(");"))
    }
    private fun profile(a: AgentActivity) = (call(a, "getBotProfiles") as at.heinz.deviceai.chief.ChiefBotProfileStore)
        .seedDefaults(20).single { it.agentId == "bot-atlas" }
    private fun enter(a: AgentActivity) = call(a, "enterBotConversation",
        at.heinz.deviceai.chief.ChiefBotProfile::class.java to profile(a))
    private fun speechPacket(a: AgentActivity): JSONObject {
        val script = shadowOf(stage(a)).lastEvaluatedJavascript
        val prefix = "window.ContinuumNative?.speech("
        assertTrue("The real Activity speech path must deliver a native pose: $script", script.startsWith(prefix))
        return JSONObject(script.removePrefix(prefix).removeSuffix(");"))
    }
    private fun startHtmlSpeech(a: AgentActivity, id: String): UtteranceProgressListener {
        call(a, "installVoiceProgressListener")
        val engine = field(a, "voiceEngine") as TextToSpeech
        val listener = requireNotNull(shadowOf(engine).utteranceProgressListener)
        // The accepted-request binding is fixture state; all PCM/range/progress work uses the installed listener.
        set(a, "voiceUtteranceId", id)
        set(a, "voiceAvatarText", "A")
        (field(a, "voiceSignal") as TtsHologramSignal).activate(id)
        listener.onBeginSynthesis(id, 8_000, AudioFormat.ENCODING_PCM_16BIT, 1)
        listener.onAudioAvailable(id, ByteArray(16_000) { if (it % 2 == 0) 0 else 0x40 })
        listener.onStart(id)
        listener.onRangeStart(id, 0, 1, 0)
        repeat(11) { advance() }
        return listener
    }
    private fun speechFrame(a: AgentActivity) {
        val frame = field(a, "voiceFrame") as Runnable
        (field(a, "mainHandler") as android.os.Handler).removeCallbacks(frame)
        frame.run()
    }
    private fun receipt(a: AgentActivity, id: String): JSONObject? {
        val receipts = stageField(stage(a), "commands") as Map<*, *>
        val record = receipts.entries.firstOrNull { it.key.toString().endsWith(":$id") }?.value ?: return null
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

    @Test fun settingsRouteExposesEveryGroupAndOpensTheExistingVoicePicker() = withActivity { _, a ->
        ready(a)
        assertTrue(action(a, "route", "settings"))
        val dialog = field(a, "centerDialog") as AlertDialog
        measure(dialog.window!!.decorView, 392, 780)
        val buttons = all(dialog.window!!.decorView).filter { it.tag?.toString()?.startsWith("continuum-setting:") == true }
        assertEquals(at.heinz.deviceai.ui.ContinuumSettingsHub.Entry.entries.size, buttons.size)
        val search = all(dialog.window!!.decorView).single { it.tag == "continuum-settings-search" } as EditText
        search.setText("Maya")
        measure(dialog.window!!.decorView, 392, 780)
        val voice = all(dialog.window!!.decorView).single { it.tag == "continuum-setting:VOICE" }
        assertTrue(voice.performClick())
        val picker = ShadowAlertDialog.getLatestAlertDialog()
        assertFalse(dialog.isShowing)
        assertEquals("Continuum · Stimme", shadowOf(picker).title.toString())
        // Maya, Android offline voice, the Code63 entry for conversation, drive mode and announcements, and (voice
        // profiles 20261119) the voice-profile entry appended last so the earlier indices stay unchanged.
        assertEquals(4, picker.listView.adapter.count)
        assertEquals("Gespräch, Fahrmodus und Ansagen", picker.listView.adapter.getItem(2).toString())
        assertTrue(picker.listView.adapter.getItem(3).toString().startsWith("Stimmprofil und Sprechweise · "))
        assertFalse(a.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).getBoolean("voice_enabled", true))
        picker.dismiss()
    }

    @Test fun settingsShortcutsOpenRealControlsAndPublishAppearanceWithoutGrantingInternet() = withActivity { _, a ->
        ready(a)
        assertTrue(action(a, "route", "settings-appearance"))
        val appearance = field(a, "centerDialog") as AlertDialog
        measure(appearance.window!!.decorView, 392, 780)
        val motion = all(appearance.window!!.decorView).single { it.tag == "command03-neon-motion" } as android.widget.Switch
        assertTrue(motion.isChecked)
        motion.performClick()
        call(a, "syncCommand03HtmlState")
        assertFalse(packet(a, "receive").getJSONObject("appearance").getBoolean("lightMotion"))
        appearance.dismiss()
        assertTrue(action(a, "route", "settings-internet"))
        val internet = ShadowAlertDialog.getLatestAlertDialog()
        assertEquals("Continuum · Internetzugang", shadowOf(internet).title.toString())
        assertEquals(3, internet.listView.adapter.count)
        assertEquals("OFF", a.getSharedPreferences("continuum-internet", Context.MODE_PRIVATE).getString("mode", null))
        internet.dismiss()
        assertTrue(action(a, "route", "settings-model"))
        val model = field(a, "modelCenterDialog") as AlertDialog
        assertTrue(model.isShowing)
        assertTrue(all(model.window!!.decorView).filterIsInstance<TextView>().any { it.text.toString() == "KI-Sprachmodell" })
        model.dismiss()
        assertTrue(action(a, "route", "settings-voice"))
        val voice = ShadowAlertDialog.getLatestAlertDialog()
        assertEquals("Continuum · Stimme", shadowOf(voice).title.toString())
        voice.dismiss()
        advance()
        assertNull(field(a, "centerDialog"))
        assertTrue(action(a, "route", "settings-export-secret"))
        assertSame(voice, ShadowAlertDialog.getLatestAlertDialog())
        assertFalse(voice.isShowing)
        assertNull(field(a, "centerDialog"))
    }

    @Test fun settingsPersonalizationShowsItsPreviouslyHiddenChoices() = withActivity { _, a ->
        ready(a)
        action(a, "route", "settings")
        val hub = field(a, "centerDialog") as AlertDialog
        measure(hub.window!!.decorView, 392, 780)
        all(hub.window!!.decorView).single { it.tag == "continuum-setting:PERSONAL" }.performClick()
        val personal = ShadowAlertDialog.getLatestAlertDialog()
        measure(personal.window!!.decorView, 392, 780)
        // The Nullpunkt repair (55a3fe015) moved title and privacy note into a custom header so the three
        // choices stay visible below it; a custom title leaves the platform title empty.
        val header = all(personal.window!!.decorView).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue("Personalisation title must be visible: $header", "Deine KI personalisieren" in header)
        assertTrue("Privacy note must stay visible: $header",
            header.any { it.contains("nicht automatisch trainiert oder ins Internet übertragen") })
        assertEquals(3, personal.listView.adapter.count)
        assertTrue("All personalisation choices must be visible", personal.listView.isShown)
        personal.dismiss()
        for (entry in listOf("CINEMA", "AUTONOMY")) {
            action(a, "route", "settings")
            val menu = field(a, "centerDialog") as AlertDialog
            measure(menu.window!!.decorView, 392, 780)
            all(menu.window!!.decorView).single { it.tag == "continuum-setting:$entry" }.performClick()
            val options = ShadowAlertDialog.getLatestAlertDialog()
            measure(options.window!!.decorView, 392, 780)
            assertTrue("$entry must expose its controls", options.listView.adapter.count > 0)
            assertTrue("$entry choices must be visible", options.listView.isShown)
            options.dismiss()
        }
    }

    private fun activeComputer(a: AgentActivity): Pair<OwnerComputerSessionController, LiveMonitorFeed<AndroidLiveFrame>> {
        val native = OwnerComputerSessionController(a,
            field(a, "cockpitBus") as at.heinz.deviceai.cockpit.CockpitAnimationBus,
            field(a, "avatarDirector") as at.heinz.deviceai.avatar.AvatarDirector,
            java.util.concurrent.Executor { error("Navigation must not start capture or workers") },
            setFeed = { call(a, "setComputerFeed", LiveMonitorFeed::class.java to it) },
            setBusy = { set(a, "activeLocalRequest", it); call(a, "setBusy", Boolean::class.javaPrimitiveType!! to it) },
            visible = { call(a, "computerSessionVisible") as Boolean }, playClick = {}, stopClick = {})
        // Synthetic authorized state; no capture.
        val planType = OwnerComputerSessionController::class.java.declaredClasses.single { it.simpleName == "Plan" }
        val reply: (OwnerComputerWorkflow.Result, Long?, Boolean) -> Unit = { _, _, _ -> }
        val plan = planType.declaredConstructors.single { it.parameterCount == 5 }.apply { isAccessible = true }
            .newInstance("computer-navigation-fixture", "com.whatsapp",
                OwnerComputerIntent(ComputerApp.WHATSAPP, ComputerGoal.OPEN), android.os.SystemClock.elapsedRealtime(), reply)
        OwnerComputerSessionController::class.java.getDeclaredField("plan").apply { isAccessible = true }.set(native, plan)
        set(a, "computerController", native)
        val feed = LatestLiveFrameHolder<AndroidLiveFrame>({ it.close() }).apply {
            bind(LiveFrameBinding("computer-navigation-fixture", 1, "com.whatsapp", a.packageName,
                LiveFrameOrigin.ACCESSIBILITY_WINDOW))
        }
        call(a, "setComputerFeed", LiveMonitorFeed::class.java to feed)
        set(a, "activeLocalRequest", true)
        call(a, "setBusy", Boolean::class.javaPrimitiveType!! to true)
        return native to feed
    }

    @Test
    @Config(shadows = [Command03HostWebView::class])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun computerNavigationKeepsTheActiveSessionInTheOriginalHtmlStage() = withActivity { lifecycle, a ->
        CockpitHostFramePump { a.window.decorView }.use { pump ->
            resizeWindow(a, 392, 852)
            repeat(3) { advance(); pump.drawRequestedFrame() }
            lifecycle.windowFocusChanged(true)
        }
        ready(a)
        val web = stage(a)
        val (native, feed) = activeComputer(a)
        assertTrue("The positive route fixture must satisfy the real HTML visibility gate",
            call(a, "command03ComputerVisible") as Boolean)
        repeat(2) { index ->
            assertTrue(action(a, "route", "device", id = "computer-route-$index"))
            assertTrue("Opening Computer must preserve the active session", native.active)
            assertSame(native, field(a, "computerController"))
            assertSame(feed, field(a, "command03ComputerFeed"))
            assertSame(web, stage(a))
            assertTrue(field(a, "activeLocalRequest") as Boolean)
            assertTrue(field(a, "busy") as Boolean)
            assertNull("Native controls must not cover the live HTML computer", field(a, "centerDialog"))
            val section = packet(a, "section")
            assertEquals("device", section.getString("name"))
            assertEquals(binding(a).pageId, section.getString("pageId"))
            assertEquals(binding(a).viewId, section.getString("viewId"))
            assertEquals(binding(a).viewRevision, section.getLong("viewRevision"))
            assertEquals(binding(a).sessionId, section.getString("sessionId"))
        }
    }

    @Test fun idleComputerNavigationKeepsTheExistingControlsWithoutStartingASession() = withActivity { _, a ->
        ready(a)
        assertTrue(action(a, "route", "device"))
        val dialog = field(a, "centerDialog") as AlertDialog
        assertTrue(dialog.isShowing)
        measure(dialog.window!!.decorView, 392, 852)
        val labels = all(dialog.window!!.decorView).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(labels.contains("Android-Computer"))
        assertTrue(labels.contains("COMPUTER-AUFTRAG SCHREIBEN"))
        assertTrue(labels.contains("SYSTEMSTATUS UND FREIGABEN"))
        assertNull(field(a, "computerController"))
        assertNull(field(a, "command03ComputerFeed"))
        assertFalse(field(a, "activeLocalRequest") as Boolean)
    }

    @Test fun leavingComputerOrPressingStopStillEndsTheActiveSession() {
        for (route in listOf("chat", "stop")) withActivity { _, a ->
            ready(a)
            val (native, _) = activeComputer(a)
            assertTrue(action(a, "route", route))
            assertFalse("$route must end the active computer session", native.active)
            assertNull(field(a, "command03ComputerFeed"))
            assertFalse(field(a, "activeLocalRequest") as Boolean)
            assertFalse(field(a, "busy") as Boolean)
        }
    }


    @Test fun computerStopDuringBotChangeDoesNotReenterOrLoseDraftRecoveryCause() = withActivity { _, a ->
        ready(a)
        project(a, document("Wechsel.pdf"))
        val (controller, _) = activeComputer(a)
        val scope = binding(a)
        val oldOwner = requireNotNull(field(a, "command03View"))
        val oldRevision = (objectField(oldOwner, "revision") as Number).toLong()
        val text = "Atlas: Prüfe die Datei"
        val command = at.heinz.deviceai.ui.Command03Command("bot-switch-proof", text,
            Command03Draft(scope, text, text.length, text.length, 1L))
        val pendingType = AgentActivity::class.java.declaredClasses
            .single { it.simpleName == "PendingCommand03Input" }
        val completion: (Boolean, String?) -> Unit = { _, _ -> }
        val pending = pendingType.declaredConstructors.single { it.parameterCount == 5 }
            .apply { isAccessible = true }
            .newInstance(command, android.os.SystemClock.elapsedRealtime() + 10_000L,
                completion, null, false)
        val newerText = "Ungesendeter zweiter Entwurf"
        set(a, "pendingCommand03Input", pending)
        set(a, "deferredCommand03Draft", Command03Draft(scope, newerText, 4, 9, 2L))
        set(a, "selectedBot", profile(a))
        assertFalse(call(a, "command03ViewCurrent", oldOwner.javaClass to oldOwner) as Boolean)
        assertSame("A stale callback must not change its view", oldOwner, field(a, "command03View"))
        call(a, "replaceCommand03View", pendingType to pending)
        val current = requireNotNull(field(a, "command03View"))
        assertEquals("Stopping Computer must not recursively replace the view",
            oldRevision + 1L, (objectField(current, "revision") as Number).toLong())
        assertFalse(controller.active)
        assertNull(field(a, "command03Document"))
        val cause = field(a, "command03TransitionCause") as JSONObject
        assertEquals("bot-switch-proof", cause.getString("commandId"))
        assertEquals(scope.viewId, cause.getString("fromViewId"))
        val carried = field(a, "deferredCommand03Draft") as Command03Draft
        assertEquals(newerText, carried.text)
        assertEquals(binding(a), carried.binding)
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

    @Test fun acceptedAddressedBotCommandCarriesOnlyItsConfirmedNewerDraftAcrossItsOwnViewChange() = withActivity { _, a ->
        ready(a)
        val original = binding(a)
        val executor = field(a, "backlogExecutor") as ExecutorService
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        executor.execute { entered.countDown(); release.await(8, TimeUnit.SECONDS) }
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        val submitted = "Atlas: Prüfe diesen synthetischen Text."
        val next = "Späterer Entwurf\nbleibt ungesendet."
        var lastEdit = -1L
        try {
            action(a, "command", submitted, id = "addressed-1")
            assertNotNull(field(a, "pendingCommand03Input"))
            action(a, "draft", next, selectionStart = 2, selectionEnd = 9)
            lastEdit = (field(a, "command03DraftLedger") as Command03DraftLedger).acknowledged
        } finally { release.countDown() }
        await("The existing addressed-bot router consumes this command") {
            receipt(a, "addressed-1") != null && field(a, "pendingCommand03Input") == null
        }
        assertTrue(requireNotNull(receipt(a, "addressed-1")).getBoolean("accepted"))
        assertNotEquals(original.viewId, binding(a).viewId)
        assertEquals(original.sessionId, binding(a).sessionId)
        assertEquals("bot-atlas", (field(a, "selectedBot") as at.heinz.deviceai.chief.ChiefBotProfile).agentId)
        val editor = field(a, "input") as EditText
        assertEquals(next, editor.text.toString())
        assertEquals(2, editor.selectionStart)
        assertEquals(9, editor.selectionEnd)
        assertNull(field(a, "deferredCommand03Draft"))
        assertEquals(0L, (field(a, "command03DraftLedger") as Command03DraftLedger).acknowledged)
        val cause = field(a, "command03TransitionCause") as JSONObject
        assertEquals("addressed-1", cause.getString("commandId"))
        assertEquals(original.pageId, cause.getString("fromPageId"))
        assertEquals(original.viewId, cause.getString("fromViewId"))
        assertEquals(lastEdit, cause.getLong("ackEditRevision"))
        assertFalse(messages(a).contains(next))
        assertFalse(field(a, "activeLocalRequest") as Boolean)
    }

    @Test fun botAndCentralViewsFenceOldDraftsAndAnAlreadyQueuedCentralHistoryRead() = withActivity { _, a ->
        ready(a)
        AgentActivity.chatTranscriptIo.submit {}.get(8, TimeUnit.SECONDS)
        advance()
        val old = binding(a)
        val held = CountDownLatch(1)
        val release = CountDownLatch(1)
        AgentActivity.chatTranscriptIo.execute { held.countDown(); release.await(8, TimeUnit.SECONDS) }
        assertTrue(held.await(2, TimeUnit.SECONDS))
        try {
            set(a, "command03HtmlSession", null)
            call(a, "syncCommand03HtmlState")
            enter(a)
            assertNotEquals(old.viewId, binding(a).viewId)
            assertTrue(binding(a).viewRevision > old.viewRevision)
            (field(a, "input") as EditText).setText("Aktueller Botentwurf")
            action(a, "draft", "Alter zentraler Entwurf", binding = old, revision = 100)
            assertEquals("Aktueller Botentwurf", (field(a, "input") as EditText).text.toString())
        } finally { release.countDown() }
        AgentActivity.chatTranscriptIo.submit {}.get(8, TimeUnit.SECONDS)
        repeat(3) { advance() }
        // A visible line is a typed record (user, text, kind) since „das“ may only mean a result; the text is read as before.
        val botRows = field(a, "command03VisibleMessages") as ArrayDeque<*>
        assertTrue(botRows.isNotEmpty())
        val firstRow = botRows.first()!!
        val firstText = firstRow.javaClass.getDeclaredField("text").apply { isAccessible = true }.get(firstRow) as String
        assertTrue(firstText.startsWith("Atlas ·"))
        call(a, "returnToJarvis")
        assertNotEquals(old.viewId, binding(a).viewId)
        assertEquals(old.sessionId, binding(a).sessionId)
        assertNull(field(a, "command03TransitionCause"))
        val current = (field(a, "input") as EditText).text.toString()
        action(a, "draft", "Veraltete Rückkehr", binding = old, revision = 101)
        assertEquals(current, (field(a, "input") as EditText).text.toString())
    }

    @Test fun microphoneLongPressUsesSilentDictationAndItsNativeEditCannotBeRolledBack() = withActivity { _, a ->
        ready(a)
        val editor = field(a, "input") as EditText
        val mode = field(a, "continuousConversation") as at.heinz.deviceai.cognitive.ContinuousConversationMode
        val before = messages(a)
        action(a, "draft", "Hallo Welt", selectionStart = 6, selectionEnd = 10)
        action(a, "route", "voice-input-long-press")
        assertFalse(mode.active)
        assertEquals(2207, field(a, "activeLocalSpeechRequest"))
        assertEquals(before, messages(a))
        val revision = (field(a, "command03DraftLedger") as Command03DraftLedger).acknowledged
        // Synthetic recognition result enters the existing delivery method; no microphone/audio claim.
        call(a, "applyLocalSpeechText", Int::class.javaPrimitiveType!! to 2207, String::class.java to "Team")
        assertEquals("Hallo Team", editor.text.toString())
        call(a, "syncCommand03HtmlState")
        val state = packet(a, "receive")
        assertEquals(revision, state.getLong("ackEditRevision"))
        assertEquals("Hallo Team", state.getJSONObject("draft").getString("text"))
        action(a, "draft", "Hallo Welt", revision = revision, selectionStart = 6, selectionEnd = 10)
        assertEquals("Hallo Team", editor.text.toString())
        assertTrue(mode.start(field(a, "sessionId") as String))
        action(a, "route", "voice-input-long-press", revision = revision)
        assertFalse("The same long press ends the existing hands-free mode", mode.active)
        assertEquals("Hallo Team", editor.text.toString())
        assertEquals(before, messages(a))
        assertFalse(field(a, "activeLocalRequest") as Boolean)
    }

    @Test
    @Config(shadows = [Command03HostWebView::class])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun nativeComposerFocusTargetsTheVisiblePageAndPauseRevokesTheQueuedKeyboardRequest() = withActivity { lifecycle, a ->
        resizeWindow(a, 392, 852)
        ready(a)
        call(a, "focusChatComposer")
        val section = packet(a, "section")
        assertEquals("chat", section.getString("name"))
        assertEquals(binding(a).viewId, section.getString("viewId"))
        assertTrue(section.getBoolean("focusComposer"))
        val web = stage(a)
        val focusState = "focusable=${web.isFocusable}, touchFocusable=${web.isFocusableInTouchMode}, " +
            "touchMode=${web.isInTouchMode}, enabled=${web.isEnabled}, attached=${web.isAttachedToWindow}, " +
            "shown=${web.isShown}, window=${web.windowVisibility}, size=${web.width}x${web.height}, " +
            "rootFocus=${a.window.decorView.findFocus()?.javaClass?.simpleName}, pageFocus=${web.hasFocus()}"
        assertTrue("The visible WebView owns focus: $focusState", web.hasFocus())
        assertFalse("The hidden native editor must not receive the IME request", (field(a, "input") as EditText).hasFocus())
        val token = stageField(stage(a), "focusRevision") as Long
        lifecycle.pause()
        assertTrue((stageField(stage(a), "focusRevision") as Long) > token)
        assertNull(stageField(stage(a), "queuedSection"))
        lifecycle.resume()
        (stage(a) as ContinuumCommand03OriginalStage).showSection("chat")
        assertFalse(packet(a, "section").getBoolean("focusComposer"))
    }

    @Test
    @Config(shadows = [DeferredBackend::class])
    fun actualCloudSourcesUseOpaqueViewBoundIdsAndOnlyNativeApprovedUrlsOpen() = withActivity { _, a ->
        ready(a)
        val scope = binding(a)
        call(a, "runCloud", String::class.java to "Synthetische Quellenfrage", List::class.java to emptyList<ToolOutput>())
        assertEquals(1, DeferredBackend.replies.size)
        DeferredBackend.replies.removeFirst().invoke(Result.success(AgentReply("fixture", "Synthetische Antwort",
            emptyList(), listOf(AgentCitation("Primärquelle", "https://example.org/evidence")), "complete")))
        assertTrue(messages(a).contains("Synthetische Antwort"))
        val sourcePacket = packet(a, "sources")
        assertEquals(scope.viewId, sourcePacket.getString("viewId"))
        val item = sourcePacket.getJSONArray("items").getJSONObject(0)
        val id = item.getString("id")
        assertTrue(at.heinz.deviceai.ui.Command03BridgeContract.validSession(id))
        assertEquals("Primärquelle", item.getString("title"))
        assertEquals("https://example.org/evidence", item.getString("url"))
        while (shadowOf(a).nextStartedActivity != null) { /* Drain startup intents, never dispatch them. */ }
        action(a, "open-source", "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
        assertNull(shadowOf(a).nextStartedActivity)
        action(a, "open-source", id)
        val opened = requireNotNull(shadowOf(a).nextStartedActivity)
        assertEquals(android.content.Intent.ACTION_VIEW, opened.action)
        assertEquals("https://example.org/evidence", opened.dataString)
        enter(a)
        action(a, "open-source", id, binding = scope)
        action(a, "open-source", id)
        assertNull("The old source cannot be reopened in a new bot view, even with a current envelope", shadowOf(a).nextStartedActivity)
        assertTrue((field(a, "command03Sources") as ArrayDeque<*>).isEmpty())
    }

    @Test
    @Config(shadows = [DeferredBackend::class])
    fun delayedCloudProducerCannotRelabelItsAnswerOrContinueToolsInAnotherView() = withActivity { _, a ->
        ready(a)
        call(a, "runCloud", String::class.java to "Alte Anfrage", List::class.java to emptyList<ToolOutput>())
        val late = DeferredBackend.replies.removeFirst()
        enter(a)
        val currentMessages = messages(a)
        set(a, "activeLocalRequest", true)
        call(a, "setBusy", Boolean::class.javaPrimitiveType!! to true)
        try {
            late(Result.success(AgentReply("old", "Diese alte Antwort darf nicht erscheinen",
                listOf(ToolCallRequest("old-tool", "device_info", emptyMap())),
                listOf(AgentCitation("Alt", "https://example.org/old")), "complete")))
            late(Result.failure(IllegalStateException("Alte fehlgeschlagene Antwort")))
            assertEquals(currentMessages, messages(a))
            assertTrue((field(a, "command03Sources") as ArrayDeque<*>).isEmpty())
            assertTrue(field(a, "busy") as Boolean)
            assertTrue(field(a, "activeLocalRequest") as Boolean)
            assertTrue(DeferredBackend.replies.isEmpty())
        } finally {
            set(a, "activeLocalRequest", false)
            call(a, "setBusy", Boolean::class.javaPrimitiveType!! to false)
        }
        call(a, "returnToJarvis")
        call(a, "switchCommand03MainDesign", Boolean::class.javaPrimitiveType!! to false)
        call(a, "runCloud", String::class.java to "Klassische Anfrage", List::class.java to emptyList<ToolOutput>())
        DeferredBackend.replies.removeFirst().invoke(Result.success(AgentReply("native", "Klassische Antwort bleibt erreichbar",
            emptyList(), emptyList(), "complete")))
        assertTrue(messages(a).contains("Klassische Antwort bleibt erreichbar"))
        assertFalse(field(a, "busy") as Boolean)
    }

    private fun document(name: String, text: String = "Synthetischer ausgelesener Dokumenttext.") =
        PdfDocumentIntake.Document(name, null, 1, null, text, PdfDocumentIntake.TextState.EXTRACTED, 1, listOf(text))

    private fun project(a: AgentActivity, document: PdfDocumentIntake.Document): JSONObject {
        call(a, "projectCommand03Document", PdfDocumentIntake.Document::class.java to document)
        return packet(a, "document")
    }

    private fun documentTurn(a: AgentActivity, document: PdfDocumentIntake.Document): Any {
        val prompt = requireNotNull(at.heinz.deviceai.cognitive.DocumentDraftPrompt.build(document.name, document.text))
        val owner = requireNotNull(field(a, "command03Document"))
        val type = AgentActivity::class.java.declaredClasses.single { it.simpleName == "DocumentDraftTurn" }
        return type.declaredConstructors.single { it.parameterCount == 7 }.apply { isAccessible = true }
            .newInstance(prompt, document.name, null, null, field(a, "sessionId"), false, owner)
    }

    @Test fun projectedPdfDraftUsesTheExistingNoModelAndBusyHandlersAndRejectsReplacedIds() = withActivity { _, a ->
        ready(a)
        val first = project(a, document("Erstes.pdf"))
        assertTrue(first.getBoolean("canDraft"))
        action(a, "document-draft", first.getString("documentId"))
        val missing = packet(a, "document")
        assertTrue(missing.getString("draftStatus").contains("Lokales Modell nicht geladen"))
        assertNull(field(a, "documentDraftTurn"))
        assertFalse(field(a, "activeLocalRequest") as Boolean)
        val second = project(a, document("Zweites.pdf"))
        assertNotEquals(first.getString("documentId"), second.getString("documentId"))
        val before = messages(a)
        action(a, "document-draft", first.getString("documentId"))
        assertEquals(before, messages(a))
        assertEquals(second.getString("documentId"), objectField(field(a, "command03Document")!!, "id"))
        call(a, "setBusy", Boolean::class.javaPrimitiveType!! to true)
        try {
            action(a, "document-draft", second.getString("documentId"))
            val busy = packet(a, "document")
            assertFalse(busy.getBoolean("canDraft"))
            assertEquals("Entwurf nicht gestartet – JARVIS arbeitet an einem anderen Auftrag.", busy.getString("draftStatus"))
            assertNull(field(a, "documentDraftTurn"))
        } finally { call(a, "setBusy", Boolean::class.javaPrimitiveType!! to false) }
    }

    @Test fun successfulPdfCompletionRevealsChatWithoutClosingAReplacedDocumentOrRequestingFocus() = withActivity { _, a ->
        ready(a)
        val first = document("Erstes.pdf")
        project(a, first)
        val oldTurn = documentTurn(a, first)
        val second = document("Zweites.pdf")
        val secondPacket = project(a, second)
        set(a, "workEpisodeId", "current-pdf-episode")
        val currentAction = View(a).apply { isEnabled = false }
        set(a, "documentDraftAction", currentAction)
        call(a, "onDocumentDraftReply", oldTurn.javaClass to oldTurn, String::class.java to "Synthetischer alter Entwurf")
        assertEquals(secondPacket.getString("documentId"), objectField(field(a, "command03Document")!!, "id"))
        assertEquals("An old result must not replace the new document's journal binding", "current-pdf-episode", field(a, "workEpisodeId"))
        assertFalse(currentAction.isEnabled)
        val beforeFailure = shadowOf(stage(a)).lastEvaluatedJavascript
        call(a, "onDocumentDraftFailed", oldTurn.javaClass to oldTurn, String::class.java to "Synthetischer alter Fehler")
        assertEquals("current-pdf-episode", field(a, "workEpisodeId"))
        assertEquals(secondPacket.getString("documentId"), objectField(field(a, "command03Document")!!, "id"))
        assertEquals("Old failure must not alter the new PDF's status or action", beforeFailure, shadowOf(stage(a)).lastEvaluatedJavascript)
        assertFalse(currentAction.isEnabled)
        val currentTurn = documentTurn(a, second)
        call(a, "onDocumentDraftReply", currentTurn.javaClass to currentTurn, String::class.java to "Synthetischer aktueller Entwurf")
        assertNull(field(a, "command03Document"))
        val shown = packet(a, "section")
        assertEquals("chat", shown.getString("name"))
        assertFalse("Automatic results must not take the keyboard", shown.getBoolean("focusComposer"))
        assertEquals(binding(a).viewId, shown.getString("viewId"))
    }

    @Test fun installedTtsPcmAndWordRangeReachHtmlAndStopRejectsLateCallbacks() = withActivity { _, a ->
        ready(a)
        val listener = startHtmlSpeech(a, "html-real-listener")
        speechFrame(a)
        val pose = speechPacket(a)
        assertTrue(pose.getBoolean("active"))
        assertEquals("html-real-listener", pose.getString("utteranceId"))
        assertEquals("PCM_ENVELOPE", pose.getString("source"))
        assertEquals("AA", pose.getString("viseme"))
        assertTrue(pose.getDouble("jawOpen") > 0)
        assertFalse("The covered native render loop stays paused", active(field(a, "hologramView")!!))
        listener.onStop("html-real-listener", true)
        assertFalse(speechPacket(a).getBoolean("active"))
        val stopped = shadowOf(stage(a)).lastEvaluatedJavascript
        listener.onStart("html-real-listener")
        listener.onRangeStart("html-real-listener", 0, 1, 0)
        assertEquals(stopped, shadowOf(stage(a)).lastEvaluatedJavascript)
        assertFalse(field(a, "voiceSpeaking") as Boolean)
    }

    @Test fun realSpeechSurvivesReparentWithANewGenerationAndPauseNeutralizesIt() = withActivity { controller, a ->
        resizeWindow(a, 392, 852)
        ready(a)
        startHtmlSpeech(a, "html-reparent-speech")
        speechFrame(a)
        val generation = speechPacket(a).getLong("generation")
        val web = stage(a)
        resizeWindow(a, 904, 768)
        speechFrame(a)
        assertSame(web, stage(a))
        val after = speechPacket(a)
        assertTrue(after.getBoolean("active"))
        assertTrue(after.getLong("generation") > generation)
        assertEquals("html-reparent-speech", after.getString("utteranceId"))
        controller.pause()
        assertFalse(speechPacket(a).getBoolean("active"))
        assertFalse(field(a, "voiceSpeaking") as Boolean)
        controller.resume()
    }

    @Test fun htmlOwnsTheOnlyFrameFeedAndTheClassicalSwitchRestoresTheNativeConsumer() = withActivity { _, a ->
        ready(a)
        val feed = LatestLiveFrameHolder<AndroidLiveFrame>({ it.close() }).apply {
            bind(LiveFrameBinding("computer-feed-fixture", 1, "com.example.target", a.packageName,
                LiveFrameOrigin.ACCESSIBILITY_WINDOW))
        }
        call(a, "setComputerFeed", LiveMonitorFeed::class.java to feed)
        val hiddenHome = field(a, "homeView")!!
        assertNull(hiddenHome.javaClass.getDeclaredField("liveComputerFeed").apply { isAccessible = true }.get(hiddenHome))
        call(a, "switchCommand03MainDesign", Boolean::class.javaPrimitiveType!! to false)
        val nativeHome = field(a, "homeView")!!
        assertSame(feed, nativeHome.javaClass.getDeclaredField("liveComputerFeed").apply { isAccessible = true }.get(nativeHome))
        assertNull(field(a, "command03MainStage"))
        feed.end()
    }

    @Test
    @Config(shadows = [Command03HostWebView::class])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun selectingActiveComputerKeepsItsSessionButChatStillStopsIt() = withActivity { lifecycle, a ->
        CockpitHostFramePump { a.window.decorView }.use { pump ->
            resizeWindow(a, 392, 852)
            repeat(3) { advance(); pump.drawRequestedFrame() }
            lifecycle.windowFocusChanged(true)
        }
        ready(a)
        val native = OwnerComputerSessionController(a,
            field(a, "cockpitBus") as at.heinz.deviceai.cockpit.CockpitAnimationBus,
            field(a, "avatarDirector") as at.heinz.deviceai.avatar.AvatarDirector,
            java.util.concurrent.Executor { error("This route fixture starts no capture or worker") },
            setFeed = { call(a, "setComputerFeed", LiveMonitorFeed::class.java to it) },
            setBusy = { set(a, "activeLocalRequest", it); call(a, "setBusy", Boolean::class.javaPrimitiveType!! to it) },
            visible = { call(a, "computerSessionVisible") as Boolean }, playClick = {}, stopClick = {})
        // Model only an existing approved session. Routing must not start a capture or grant a tool.
        val planType = OwnerComputerSessionController::class.java.declaredClasses.single { it.simpleName == "Plan" }
        val reply: (OwnerComputerWorkflow.Result, Long?, Boolean) -> Unit = { _, _, _ -> }
        val plan = planType.declaredConstructors.single { it.parameterCount == 5 }.apply { isAccessible = true }
            .newInstance("computer-route-fixture", "com.whatsapp", OwnerComputerIntent(ComputerApp.WHATSAPP, ComputerGoal.OPEN),
                android.os.SystemClock.elapsedRealtime(), reply)
        OwnerComputerSessionController::class.java.getDeclaredField("plan").apply { isAccessible = true }.set(native, plan)
        set(a, "computerController", native)
        val feed = LatestLiveFrameHolder<AndroidLiveFrame>({ it.close() }).apply {
            bind(LiveFrameBinding("computer-route-fixture", 1, "com.whatsapp", a.packageName,
                LiveFrameOrigin.ACCESSIBILITY_WINDOW))
        }
        call(a, "setComputerFeed", LiveMonitorFeed::class.java to feed)
        val web = stage(a)
        assertTrue(native.active)
        assertTrue("The route starts from an actually visible HTML host", call(a, "command03ComputerVisible") as Boolean)
        assertNull(field(a, "centerDialog"))
        repeat(2) {
            assertTrue(action(a, "route", "device"))
            assertTrue("Selecting Computer must preserve its active session", native.active)
            assertSame(feed, field(a, "command03ComputerFeed"))
            assertSame(web, stage(a))
            assertNull("No native dialog may cover the active computer", field(a, "centerDialog"))
            val section = JSONObject(shadowOf(web).lastEvaluatedJavascript
                .removePrefix("window.ContinuumNative?.section(").removeSuffix(");"))
            assertEquals("device", section.getString("name"))
            assertEquals((web as ContinuumCommand03OriginalStage).binding!!.viewId, section.getString("viewId"))
        }
        assertTrue(action(a, "route", "chat"))
        assertFalse("A covering route must still stop the existing session", native.active)
        assertNull(field(a, "command03ComputerFeed"))
        assertTrue(action(a, "route", "device"))
        assertFalse("Opening Computer must not restart the stopped session", native.active)
        assertNull(field(a, "command03ComputerFeed"))
        val center = field(a, "centerDialog") as AlertDialog
        assertTrue(center.isShowing)
        center.dismiss()
    }

    @Test
    @Config(shadows = [Command03HostWebView::class])
    // Use the same real host-window visibility contract as the existing native monitor fixtures.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun visibleSplitScreenKeepsHtmlComputerDrawingWhileFullStopEndsTheExistingSession() = withActivity { lifecycle, a ->
        CockpitHostFramePump { a.window.decorView }.use { pump ->
            resizeWindow(a, 392, 852)
            repeat(3) { advance(); pump.drawRequestedFrame() }
            lifecycle.windowFocusChanged(true)
        }
        ready(a)
        val native = OwnerComputerSessionController(a,
            field(a, "cockpitBus") as at.heinz.deviceai.cockpit.CockpitAnimationBus,
            field(a, "avatarDirector") as at.heinz.deviceai.avatar.AvatarDirector,
            java.util.concurrent.Executor { error("This lifecycle fixture starts no capture or worker") },
            setFeed = { call(a, "setComputerFeed", LiveMonitorFeed::class.java to it) },
            setBusy = { set(a, "activeLocalRequest", it); call(a, "setBusy", Boolean::class.javaPrimitiveType!! to it) },
            visible = { call(a, "computerSessionVisible") as Boolean }, playClick = {}, stopClick = {})
        // Model only the already-authorized active session. Android capture/permissions are not exercised by this test.
        val planType = OwnerComputerSessionController::class.java.declaredClasses.single { it.simpleName == "Plan" }
        val reply: (OwnerComputerWorkflow.Result, Long?, Boolean) -> Unit = { _, _, _ -> }
        val plan = planType.declaredConstructors.single { it.parameterCount == 5 }.apply { isAccessible = true }
            .newInstance("computer-split-fixture", "com.whatsapp", OwnerComputerIntent(ComputerApp.WHATSAPP, ComputerGoal.OPEN),
                android.os.SystemClock.elapsedRealtime(), reply)
        OwnerComputerSessionController::class.java.getDeclaredField("plan").apply { isAccessible = true }.set(native, plan)
        set(a, "computerController", native)
        val feed = LatestLiveFrameHolder<AndroidLiveFrame>({ it.close() }).apply {
            bind(LiveFrameBinding("computer-split-fixture", 1, "com.whatsapp", a.packageName,
                LiveFrameOrigin.ACCESSIBILITY_WINDOW))
        }
        call(a, "setComputerFeed", LiveMonitorFeed::class.java to feed)
        shadowOf(a).setInMultiWindowMode(true)
        val visibleState = computerPresentationState(a)
        assertTrue("The host must be actually visible before pausing: $visibleState",
            call(a, "command03ComputerVisible") as Boolean)
        assertTrue("The host must have a laid-out visible surface: $visibleState",
            stage(a).width > 0 && stage(a).height > 0 && stage(a).getGlobalVisibleRect(android.graphics.Rect()))
        (field(a, "command03ComputerTick") as Runnable).run()
        lifecycle.pause()
        val pausedState = computerPresentationState(a)
        assertTrue(native.active)
        assertFalse("Only a still-visible approved split-screen session keeps WebView drawing", shadowOf(stage(a)).wasOnPauseCalled())
        assertTrue("The approved visible session must retain its presentation: $pausedState",
            stageField(stage(a), "livePresentationActive") as Boolean)
        assertFalse(field(a, "foregroundLearningActive") as Boolean)
        lifecycle.stop()
        assertFalse(native.active)
        assertTrue(shadowOf(stage(a)).wasOnPauseCalled())
        assertFalse(stageField(stage(a), "livePresentationActive") as Boolean)
        assertNull(field(a, "command03ComputerFeed"))
        lifecycle.start().resume()
    }

    @Test fun realComputerInputsOnTheCockpitBusReachThePageSnapshotAndNothingIsReportedWithoutThem() = withActivity { _, a ->
        ready(a)
        call(a, "syncCommand03HtmlState")
        assertTrue("No approved task, no record", packet(a, "receive").isNull("computerWork"))
        // The same events the session controller's workflow emits for a real dispatched tap.
        val work = at.heinz.deviceai.cognitive.CockpitWorkEmitter("computer-snapshot-fixture", 7,
            field(a, "cockpitBus") as at.heinz.deviceai.cockpit.CockpitAnimationBus)
        work.emit(at.heinz.deviceai.cognitive.CockpitWorkEvent.Stage.ACCEPTED)
        work.emit(at.heinz.deviceai.cognitive.CockpitWorkEvent.Stage.DISPATCHED, "step-1",
            at.heinz.deviceai.cognitive.CockpitWorkEvent.Action.CLICK,
            at.heinz.deviceai.cognitive.CockpitWorkEvent.Target(.25f, .75f), observationRevision = 3, frameSequence = 12)
        shadowOf(Looper.getMainLooper()).idle()
        call(a, "syncCommand03HtmlState")
        val record = packet(a, "receive").getJSONObject("computerWork")
        assertEquals("computer-snapshot-fixture", record.getString("taskId"))
        assertEquals("7", record.getString("captureSession"))
        assertEquals("DISPATCHED", record.getString("stage"))
        assertFalse("No Android consent is open in this fixture", record.getBoolean("consentPending"))
        assertFalse("Without the cockpit's panel of this task no app name is made up", record.has("app"))
        val tap = record.getJSONArray("actions").getJSONObject(0)
        assertEquals(1, record.getJSONArray("actions").length())
        assertEquals("CLICK", tap.getString("kind"))
        assertEquals("DISPATCHED", tap.getString("state"))
        assertEquals(.25, tap.getDouble("x"), 1e-6)
        assertEquals(.75, tap.getDouble("y"), 1e-6)
        assertEquals(12L, tap.getLong("frameSequence"))
        // The workflow could not verify the effect: the page is told exactly that.
        work.emit(at.heinz.deviceai.cognitive.CockpitWorkEvent.Stage.PAUSED, "step-1")
        shadowOf(Looper.getMainLooper()).idle()
        call(a, "syncCommand03HtmlState")
        val paused = packet(a, "receive").getJSONObject("computerWork")
        assertEquals("PAUSED", paused.getString("stage"))
        assertEquals("UNCONFIRMED", paused.getJSONArray("actions").getJSONObject(0).getString("state"))
    }

    @Test fun thePageSnapshotCarriesTheOrderRailTheStatusStripAndTheRealStartUpCheck() = withActivity { _, a ->
        ready(a)
        call(a, "syncCommand03HtmlState")
        val idle = packet(a, "receive")
        assertTrue("No step was reported, so there is no order", idle.isNull("orders"))
        // The first snapshot of a ready page carries the briefing lane's real system check of this moment – the
        // stage's start-up sequence shows measured states, never a made-up "all systems ready".
        val measured = at.heinz.deviceai.briefing.ContinuumSystemCheck.snapshot()
        assertTrue("The start-up check ran on real device facts", measured.isNotEmpty())
        val shown = idle.getJSONArray("systemCheck")
        assertEquals(minOf(measured.size, at.heinz.deviceai.ui.Command03StageBoard.MAX_CHECKS), shown.length())
        for (index in 0 until shown.length()) {
            assertEquals(measured[index].key, shown.getJSONObject(index).getString("key"))
            assertEquals(measured[index].state.name, shown.getJSONObject(index).getString("state"))
        }
        val cards = idle.getJSONArray("briefingCards")
        assertEquals("Only what the lane published is a card", at.heinz.deviceai.briefing.ContinuumBriefingBoard.cards().size
            .coerceAtMost(at.heinz.deviceai.ui.Command03StageBoard.MAX_CARDS), cards.length())
        val strip = idle.getJSONObject("tactical")
        assertTrue(strip.getString("brain") in setOf("LOCAL", "CLAUDE"))
        assertTrue(strip.isNull("internet") || strip.getString("internet") in setOf("OFF", "ASK", "ALWAYS_ON"))
        for (key in listOf("thermal", "battery", "charging")) assertTrue("$key is a reading or null, never absent", strip.has(key))
        // A real turn on the activity's cockpit bus: its command and one running step.
        val bus = field(a, "cockpitBus") as at.heinz.deviceai.cockpit.CockpitAnimationBus
        bus.emit(at.heinz.deviceai.cockpit.CockpitAnimationEvent.CommandFlyIn("rail-fixture", 1L, "Wie wird das Wetter morgen?"))
        bus.emit(at.heinz.deviceai.cockpit.CockpitAnimationEvent.ProcessTimeline("rail-fixture", 2L, listOf(
            at.heinz.deviceai.cockpit.ProcessStep("ack", "Auftrag angenommen", at.heinz.deviceai.cockpit.StepState.DONE),
            at.heinz.deviceai.cockpit.ProcessStep("web", "Websuche", at.heinz.deviceai.cockpit.StepState.ACTIVE))))
        shadowOf(Looper.getMainLooper()).idle()
        call(a, "syncCommand03HtmlState")
        val orders = packet(a, "receive").getJSONObject("orders")
        assertEquals("TURN", orders.getString("source"))
        assertEquals("Wie wird das Wetter morgen?", orders.getString("title"))
        assertTrue(orders.getBoolean("active"))
        assertTrue(orders.isNull("outcome"))
        assertEquals(1, orders.getJSONArray("items").length())
        val step = orders.getJSONArray("items").getJSONObject(0)
        assertEquals("Websuche", step.getString("label"))
        assertEquals("ACTIVE", step.getString("state"))
        assertFalse("A turn id never leaves the app", orders.toString().contains("rail-fixture"))
        // Checks and cards a provider hands over reach the same snapshot.
        val board = field(a, "command03Board") as at.heinz.deviceai.ui.Command03StageBoard
        assertTrue(board.setSystemCheck(listOf(at.heinz.deviceai.ui.Command03StageBoard.Check("voice", "Stimme",
            at.heinz.deviceai.ui.Command03StageBoard.CheckState.WARN, "Sprachausgabe aus"))))
        assertTrue(board.setBriefingCards(listOf(at.heinz.deviceai.ui.Command03StageBoard.Card("weather",
            at.heinz.deviceai.ui.Command03StageBoard.CardKind.WEATHER, "Wetter", listOf("Linz · 4 °C")))))
        call(a, "syncCommand03HtmlState")
        val filled = packet(a, "receive")
        assertEquals("WARN", filled.getJSONArray("systemCheck").getJSONObject(0).getString("state"))
        assertEquals("weather", filled.getJSONArray("briefingCards").getJSONObject(0).getString("kind"))
        assertEquals("info", filled.getJSONArray("briefingCards").getJSONObject(0).getString("state"))
        // A new publication of the briefing lane replaces what the stage shows: the board follows the lane.
        a.systemStartReport()
        call(a, "syncCommand03HtmlState")
        val again = packet(a, "receive").getJSONArray("systemCheck")
        assertEquals(at.heinz.deviceai.briefing.ContinuumSystemCheck.snapshot().first().key, again.getJSONObject(0).getString("key"))
        assertEquals("The fixture row made way for the measured checks", minOf(at.heinz.deviceai.briefing.ContinuumSystemCheck
            .snapshot().size, at.heinz.deviceai.ui.Command03StageBoard.MAX_CHECKS), again.length())
    }
}
