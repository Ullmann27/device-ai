package at.heinz.deviceai.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Canvas
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local presentation of the owner's exact Kommando-03 HTML source.
 *
 * The source files are byte-identical to PR #850 (verified by the lock manifest).
 * The current integration mounts this as the default Activity presentation, with
 * an explicit return to the existing native view. Device/WebGL acceptance remains
 * separate from source and host tests.
 *
 * No JavascriptInterface, file:// origin, WebView internet access, secret or
 * model/tool API exposure. Native actions are top-level allowlisted navigations
 * and still pass through AgentActivity's existing owner-gated handlers.
 */
internal class ContinuumCommand03OriginalStage(
    context: Context,
    private val onNativeAction: (route: String, value: String?) -> Unit,
    private val onNativeCommand: (Command03Command, (Boolean, String?) -> Unit) -> Unit,
    private val onNativeDraft: (Command03Draft) -> Boolean,
    private val onReady: () -> Unit = {},
    private val onComputerFrameDrawn: (Command03FrameAck) -> Unit = {},
    private val onComputerFrameHidden: (Command03FrameAck) -> Unit = {},
    private val onPresentationLost: () -> Unit = {},
    private val canFocusComposer: () -> Boolean = { true }
) : WebView(context) {
    companion object {
        const val LOCAL_HOST = "appassets.androidplatform.net"
        const val ASSET_PATH = "/assets/continuum-command03/"
        const val INDEX = "https://" + LOCAL_HOST + ASSET_PATH + "index.android.html"
        private val SAFE_PATH = Regex("[A-Za-z0-9_./-]{1,200}")
        private val FRAME_PROBE_COMMANDS = setOf("start", "read", "stop")
        /** The tail of a running answer the page shows as its draft row (native-bridge.js keeps the same bound). */
        const val MAX_DRAFT_CHARS = 6_000
        private val ROUTES = setOf("home", "chat", "chats", "new-chat", "files",
            "research", "agents", "memory", "device", "settings", "settings-voice", "settings-model",
            "settings-internet", "settings-appearance", "voice-input", "voice-input-long-press", "stop")
        private fun denied(): WebResourceResponse = WebResourceResponse(
            "text/plain", "UTF-8", 403, "Forbidden", emptyMap(),
            ByteArrayInputStream(ByteArray(0))
        )
        private fun local(uri: Uri): Boolean =
            uri.scheme == "https" && uri.host == LOCAL_HOST &&
                uri.path?.startsWith(ASSET_PATH) == true
        private fun mime(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
            "html" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "json" -> "application/json"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "ttf" -> "font/ttf"
            "glb" -> "model/gltf-binary"
            "txt", "md" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    private var pageReady = false
    private var pageStarted = false
    var pageId: String = java.util.UUID.randomUUID().toString(); private set
    var binding: Command03Binding? = null; private set
    private var disposed = false
    private var queuedState: String? = null
    private var queuedDocument: String? = null
    private var queuedTranscript: Pair<Command03Binding, JSONArray>? = null
    private val queuedMessages = ArrayDeque<JSONObject>()
    private data class CommandReceipt(val request: Command03Command, var result: JSONObject? = null)
    private val commands = linkedMapOf<String, CommandReceipt>()
    private val queuedResults = linkedMapOf<String, JSONObject>()
    private var queuedComputer: Triple<Command03Binding, Command03ComputerPacket, Long>? = null
    private var queuedSpeech: Pair<Command03Binding, Command03SpeechPacket>? = null
    private val queuedSources = linkedMapOf<String, JSONObject>()
    private var queuedSection: Pair<JSONObject, Boolean>? = null
    private var focusRevision = 0L
    private var livePresentationActive = false
    private var computerFrame: Command03FrameAck? = null
    private var computerFrameDeadline = 0L
    private var computerDrawn: Command03FrameAck? = null
    private var visualFence = 0L
    private var visualRequest = 0L
    private var drawSerial = 0L
    private var drawAfter = Long.MAX_VALUE
    private var readyToDraw: Command03FrameAck? = null

    val mediaReady: Boolean get() = pageReady && !disposed

    fun bind(next: Command03Binding): Boolean {
        if (disposed || next.pageId != pageId || !Command03BridgeContract.validBinding(next)) return false
        val previous = binding
        if (previous == next) return true
        // Keep the exact same binding admission rule as the original HTML
        // adapter: a change requires a higher revision AND a new view ID.
        if (previous != null && (next.viewRevision <= previous.viewRevision ||
                next.viewId == previous.viewId)) return false
        cancelComposerFocus()
        invalidateComputerPresentation()
        if (previous != null) onPresentationLost()
        binding = next
        queuedState = null; queuedDocument = null; queuedTranscript = null
        queuedMessages.clear(); queuedResults.clear(); queuedSources.clear(); queuedSpeech = null
        return true
    }

    private fun envelope(scope: Command03Binding): JSONObject = JSONObject()
        .put("pageId", scope.pageId).put("viewId", scope.viewId)
        .put("viewRevision", scope.viewRevision).put("sessionId", scope.sessionId)

    private fun readBinding(data: JSONObject): Command03Binding? = Command03Binding(
        data.optString("pageId"), data.optString("viewId"), data.optLong("viewRevision", -1), data.optString("sessionId")
    ).takeIf(Command03BridgeContract::validBinding)

    private fun readBinding(uri: Uri): Command03Binding? = Command03Binding(
        uri.getQueryParameter("pageId").orEmpty(), uri.getQueryParameter("viewId").orEmpty(),
        uri.getQueryParameter("viewRevision")?.toLongOrNull() ?: -1, uri.getQueryParameter("sessionId").orEmpty()
    ).takeIf(Command03BridgeContract::validBinding)

    private fun current(data: JSONObject): Boolean = readBinding(data)?.let { it == binding && it.pageId == pageId } == true

    fun setLivePresentationActive(active: Boolean) {
        livePresentationActive = active
        if (!active) invalidateComputerPresentation()
    }

    fun invalidateComputerPresentation() {
        visualFence++
        computerFrame = null
        computerDrawn = null
        readyToDraw = null
        drawAfter = Long.MAX_VALUE
        queuedComputer = null
    }

    fun presentComputer(packet: Command03ComputerPacket) {
        val scope = binding ?: return
        if (disposed || packet.sessionId != scope.sessionId) return
        if (!pageReady) { queuedComputer = Triple(scope, packet, System.nanoTime()); return }
        invalidateComputerPresentation()
        val image = packet.frame?.takeIf { it.valid() }
        val remaining = packet.staleAfterMs?.minus(packet.ageMs ?: Long.MAX_VALUE) ?: 0L
        val live = packet.active && packet.status == "LIVE" && image != null && remaining in 1..1_500
        val data = envelope(scope).put("generation", packet.generation)
            .put("sequence", packet.sequence).put("active", packet.active)
            .put("status", if (packet.frame != null && !live) "PAUSED" else packet.status)
            .put("reason", packet.reason)
        // The monitor's title bar names the bound target: the package Android captures, nothing inferred.
        packet.binding?.let { data.put("taskId", it.taskId).put("captureSession", it.captureSession.toString())
            .put("targetPackage", it.targetPackage) }
        packet.sourceEpoch?.let { data.put("sourceEpoch", it) }
        if (live) {
            val encoded = requireNotNull(image)
            data.put("frameDataUri", "data:image/jpeg;base64," +
                android.util.Base64.encodeToString(encoded.jpeg, android.util.Base64.NO_WRAP))
                .put("width", encoded.width).put("height", encoded.height)
                .put("ageMs", packet.ageMs).put("staleAfterMs", packet.staleAfterMs)
            // Measured geometry of this very frame, so a real dispatched action can be marked on the shown image.
            packet.sourceSequence?.takeIf { it >= 0 }?.let { data.put("sourceSequence", it) }
            packet.observationRevision?.takeIf { it >= 0 }?.let { data.put("observationRevision", it) }
            packet.viewport?.let { data.put("viewport", JSONArray().put(it.left.toDouble()).put(it.top.toDouble())
                .put(it.right.toDouble()).put(it.bottom.toDouble())) }
            computerFrame = packet.acknowledgement
            computerFrameDeadline = System.nanoTime() + remaining * 1_000_000
        }
        evaluateJavascript("window.ContinuumNative?.computer($data);", null)
    }

    fun presentSpeech(packet: Command03SpeechPacket) {
        val scope = binding ?: return
        if (disposed || packet.sessionId != scope.sessionId) return
        // Speech frames are ephemeral: never replay a queued mouth pose after page load.
        if (!pageReady) { queuedSpeech = packet.takeUnless { it.active }?.let { scope to it }; return }
        val data = envelope(scope).put("generation", packet.generation)
            .put("sequence", packet.sequence).put("active", packet.active)
            .put("utteranceId", packet.utteranceId ?: JSONObject.NULL)
            .put("viseme", packet.viseme).put("weight", packet.weight).put("jawOpen", packet.jawOpen)
            .put("durationMs", packet.durationMs).put("source", packet.source?.name ?: JSONObject.NULL)
        evaluateJavascript("window.ContinuumNative?.speech($data);", null)
    }

    private fun canDrawComputer(ack: Command03FrameAck): Boolean = !disposed && pageReady &&
        livePresentationActive && isAttachedToWindow && isShown && windowVisibility == VISIBLE &&
        width > 0 && height > 0 && getGlobalVisibleRect(android.graphics.Rect()) &&
        ack == computerFrame && System.nanoTime() < computerFrameDeadline

    private fun decodedComputerFrame(ack: Command03FrameAck) {
        if (ack == computerDrawn || !canDrawComputer(ack)) return
        val fence = visualFence
        val request = ++visualRequest
        postVisualStateCallback(request, object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) {
                if (requestId != request || request != visualRequest || fence != visualFence || !canDrawComputer(ack)) return
                readyToDraw = ack
                drawAfter = drawSerial + 1
                invalidate()
            }
        })
    }

    override fun onDraw(canvas: Canvas) {
        drawSerial++
        super.onDraw(canvas)
        val ack = readyToDraw ?: return
        if (!canvas.isHardwareAccelerated || drawSerial < drawAfter || !canDrawComputer(ack)) return
        readyToDraw = null
        computerDrawn = ack
        onComputerFrameDrawn(ack)
    }

    override fun onDetachedFromWindow() {
        cancelComposerFocus()
        invalidateComputerPresentation()
        onPresentationLost()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) { cancelComposerFocus(); invalidateComputerPresentation(); onPresentationLost() }
    }

    /** Native -> local page only. Never grant JavaScript direct Android capabilities. */
    fun present(state: JSONObject) {
        val scope = readBinding(state) ?: return
        if (!bind(scope)) return
        val data = state.toString()
        if (!pageReady) queuedState = data
        else evaluateJavascript("window.ContinuumNative?.receive($data);", null)
    }

    /**
     * Show the read-only first page and extracted text of the actual owner-selected PDF.
     * This is native-to-page presentation only; no file/content URI or tool permission
     * becomes available to JavaScript, and nothing is sent to the network.
     */
    fun presentDocument(document: JSONObject) {
        if (disposed || !current(document)) return
        cancelComposerFocus()
        invalidateComputerPresentation()
        onPresentationLost()
        val data = document.toString()
        if (!pageReady) queuedDocument = data
        else evaluateJavascript("window.ContinuumNative?.document($data);", null)
    }

    fun clearDocument() {
        val scope = binding ?: return
        if (disposed) return
        queuedDocument = null
        if (pageReady) evaluateJavascript("window.ContinuumNative?.clearDocument(${envelope(scope)});", null)
    }

    fun presentTranscript(scope: Command03Binding, entries: JSONArray) {
        if (disposed || scope != binding) return
        if (!pageReady) {
            queuedTranscript = scope to entries
            queuedMessages.clear()
            return
        }
        // Full stored messages can exceed the former 6,000-character preview.
        // Bound each Binder/evaluateJavascript call instead of cutting their text.
        var batch = JSONArray()
        var characters = 0
        var append = false
        fun flush() {
            val data = envelope(scope).put("entries", batch)
                .put("append", append).toString()
            evaluateJavascript("window.ContinuumNative?.transcript($data);", null)
            append = true
            batch = JSONArray()
            characters = 0
        }
        for (index in maxOf(0, entries.length() - 60) until entries.length()) {
            val entry = entries.optJSONObject(index) ?: continue
            val clean = JSONObject().put("user", entry.optBoolean("user"))
                .put("text", Command03BridgeContract.messageText(entry.optString("text")))
            val length = clean.toString().length
            if (batch.length() > 0 && characters + length > 120_000) flush()
            batch.put(clean)
            characters += length
        }
        if (batch.length() > 0 || !append) flush()
    }

    fun presentMessage(scope: Command03Binding, user: Boolean, text: String) {
        if (disposed || text.isBlank() || scope != binding) return
        val message = envelope(scope).put("user", user)
            .put("text", Command03BridgeContract.messageText(text))
        if (!pageReady) {
            queuedMessages.addLast(message)
            while (queuedMessages.size > 60) queuedMessages.removeFirst()
            return
        }
        val data = message.toString()
        evaluateJavascript("window.ContinuumNative?.message($data);", null)
    }

    /**
     * The answer while the on-device model is still writing it: one provisional row in the page's feed. "" removes
     * it. Never queued – a draft for a page that is not ready is simply not shown; the checked answer follows as a
     * [presentMessage]. true = handed to the page.
     */
    fun presentDraft(scope: Command03Binding, text: String): Boolean {
        if (disposed || scope != binding || !pageReady) return false
        val data = envelope(scope).put("text", Command03BridgeContract.messageText(text.takeLast(MAX_DRAFT_CHARS)))
        evaluateJavascript("window.ContinuumNative?.draft($data);", null)
        return true
    }

    fun presentSources(sources: JSONObject) {
        if (disposed || !current(sources)) return
        if (!pageReady) {
            queuedSources[sources.optString("sourceSetId")] = sources
            while (queuedSources.size > 60) queuedSources.remove(queuedSources.keys.first())
        } else evaluateJavascript("window.ContinuumNative?.sources($sources);", null)
    }

    /**
     * Owner's „Wärme- und Zeitmessung“ only: starts, reads or stops the page's frame counter (native-bridge.js,
     * frame governor). Native asks, the page answers with numbers; no new channel from JavaScript to Android.
     * [result] receives the page's JSON answer, or null when the page is not ready or has no such counter.
     */
    fun frameProbe(command: String, result: (String?) -> Unit) {
        if (disposed || !pageReady || command !in FRAME_PROBE_COMMANDS) { result(null); return }
        evaluateJavascript("(window.ContinuumNative && window.ContinuumNative.frameProbe) ? " +
            "window.ContinuumNative.frameProbe('$command') : null;") { answer ->
            result(answer?.takeUnless { it == "null" || it.isBlank() })
        }
    }

    fun cancelComposerFocus() { focusRevision++; queuedSection = null }

    fun showSection(section: String, keyboard: Boolean = false) {
        val scope = binding ?: return
        if (disposed || section !in ROUTES) return
        cancelComposerFocus()
        val focus = keyboard && canFocusComposer() && isAttachedToWindow && isShown && windowVisibility == VISIBLE
        val data = envelope(scope).put("name", section).put("focusComposer", focus)
        if (!pageReady) { queuedSection = data to focus; return }
        val revision = focusRevision
        if (focus) requestFocus()
        evaluateJavascript("window.ContinuumNative?.section($data);") {
            if (focus && revision == focusRevision && current(data) && !disposed && pageReady &&
                canFocusComposer() && isAttachedToWindow && isShown && windowVisibility == VISIBLE) {
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                    ?.showSoftInput(this, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun commandResult(request: Command03Command, accepted: Boolean, reason: String?) {
        if (disposed || request.binding != binding) return
        val result = envelope(request.binding).put("id", request.id)
            .put("clientEditRevision", request.draft.clientEditRevision)
            .put("accepted", accepted).put("reason", reason ?: JSONObject.NULL)
        if (!pageReady) queuedResults[request.id] = result
        else evaluateJavascript("window.ContinuumNative?.commandResult($result);", null)
    }

    private fun submitCommand(uri: Uri) {
        val draft = readDraft(uri, "draftText") ?: return
        val request = Command03Command(uri.getQueryParameter("id").orEmpty(), uri.getQueryParameter("value").orEmpty(), draft)
        if (request.binding != binding || !Command03BridgeContract.validRequest(request.id)) return
        Command03BridgeContract.commandFailure(request.text)?.let {
            commandResult(request, false, it)
            return
        }
        val key = request.binding.pageId + ":" + request.binding.viewId + ":" + request.id
        commands[key]?.let { previous ->
            if (previous.request != request) commandResult(request, false, "invalid-input")
            else previous.result?.let { result ->
                commandResult(request, result.optBoolean("accepted"), result.optString("reason").takeUnless { it == "null" })
            }
            return
        }
        if (request.text != request.draft.text.trim() || !onNativeDraft(draft)) {
            commandResult(request, false, "stale-edit"); return
        }
        while (commands.size >= 64) {
            val completed = commands.entries.firstOrNull { it.value.result != null } ?: break
            commands.remove(completed.key)
        }
        if (commands.size >= 64) { commandResult(request, false, "busy"); return }
        val receipt = CommandReceipt(request)
        commands[key] = receipt
        onNativeCommand(request) { accepted, reason ->
            if (disposed || receipt.result != null) return@onNativeCommand
            receipt.result = JSONObject().put("accepted", accepted).put("reason", reason ?: JSONObject.NULL)
            commandResult(request, accepted, reason)
        }
    }

    private fun readDraft(uri: Uri, textKey: String): Command03Draft? {
        val scope = readBinding(uri)?.takeIf { it == binding } ?: return null
        val text = uri.getQueryParameter(textKey) ?: return null
        val start = uri.getQueryParameter("selectionStart")?.toIntOrNull() ?: return null
        val end = uri.getQueryParameter("selectionEnd")?.toIntOrNull() ?: return null
        val revision = uri.getQueryParameter("clientEditRevision")?.toLongOrNull() ?: return null
        if (!Command03BridgeContract.validRevision(revision) || !Command03BridgeContract.validDraft(text) ||
            start !in 0..text.length || end !in start..text.length) return null
        return Command03Draft(scope, text, start, end, revision)
    }

    override fun destroy() {
        disposed = true
        pageReady = false
        queuedState = null
        queuedDocument = null
        queuedTranscript = null
        queuedMessages.clear()
        commands.clear()
        queuedResults.clear()
        queuedSources.clear()
        cancelComposerFocus()
        invalidateComputerPresentation()
        queuedSpeech = null
        super.destroy()
    }

    init {
        setBackgroundColor(Color.rgb(3, 9, 18))
        @Suppress("DEPRECATION")
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            blockNetworkLoads = true
        }
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                if (disposed || url != INDEX) return
                val reload = pageStarted || pageReady
                pageStarted = true
                pageReady = false
                if (reload) {
                    cancelComposerFocus()
                    invalidateComputerPresentation()
                    onPresentationLost()
                    pageId = java.util.UUID.randomUUID().toString()
                    binding = null
                    queuedState = null; queuedDocument = null; queuedTranscript = null
                    queuedMessages.clear(); queuedResults.clear(); queuedSources.clear(); queuedSpeech = null
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (disposed) return true
                val uri = request.url
                if (request.isForMainFrame && uri.scheme == "continuum-native" &&
                    uri.host == "action" && uri.port == -1 && uri.userInfo == null && request.method == "GET") {
                    if (readBinding(uri) != binding || binding == null) return true
                    val command = uri.pathSegments.singleOrNull().orEmpty()
                    val value = uri.getQueryParameter("value").orEmpty()
                    if (command == "route" && value in ROUTES) {
                        val draft = readDraft(uri, "draftText")
                        val admitted = draft != null && onNativeDraft(draft)
                        // An owner stop stays available even when an oversized/stale draft cannot be transported.
                        if (admitted || value == "stop") onNativeAction(value, null)
                    } else if (command == "select-chat" && Command03BridgeContract.validSession(value)) {
                        readDraft(uri, "draftText")?.takeIf(onNativeDraft)?.let { onNativeAction("select-chat", value) }
                    } else if (command in setOf("open-source", "document-draft") && Command03BridgeContract.validSession(value)) {
                        readDraft(uri, "draftText")?.takeIf(onNativeDraft)?.let { onNativeAction(command, value) }
                    } else if (command == "command") {
                        submitCommand(uri)
                    } else if (command == "frame-presented" || command == "frame-hidden") {
                        val session = uri.getQueryParameter("sessionId").orEmpty()
                        val generation = uri.getQueryParameter("generation")?.toLongOrNull()
                        val sequence = uri.getQueryParameter("sequence")?.toLongOrNull()
                        if (Command03BridgeContract.validSession(session) && generation != null && generation > 0 &&
                            sequence != null && sequence > 0) {
                            val ack = Command03FrameAck(session, generation, sequence)
                            if (command == "frame-presented") decodedComputerFrame(ack)
                            else onComputerFrameHidden(ack)
                        }
                    } else if (command == "draft") {
                        readDraft(uri, "value")?.let(onNativeDraft)
                    }
                    return true
                }
                // Only the local Android-enhanced original page is navigable.
                return uri.toString() != INDEX
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                val uri = request.url
                if (!local(uri)) return denied()
                val relative = uri.path.orEmpty().removePrefix(ASSET_PATH)
                if (!SAFE_PATH.matches(relative) || relative.split('/').any { it == "." || it == ".." })
                    return denied()
                return try {
                    WebResourceResponse(mime(relative),
                        if (relative.endsWith(".glb") || relative.endsWith(".ttf") ||
                            relative.endsWith(".png") || relative.endsWith(".jpg")) null else "UTF-8",
                        context.assets.open("continuum-command03/" + relative))
                } catch (_: FileNotFoundException) {
                    denied()
                } catch (_: java.io.IOException) {
                    denied()
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (disposed || url != INDEX) return
                // The Android-only bridge was loaded BEFORE the unchanged
                // demo scripts by index.android.html. No post-load race.
                pageReady = true
                queuedState?.let { json ->
                    view.evaluateJavascript("window.ContinuumNative?.receive($json);", null)
                    queuedState = null
                }
                queuedTranscript?.let { (scope, entries) ->
                    queuedTranscript = null
                    presentTranscript(scope, entries)
                }
                queuedMessages.toList().forEach { message ->
                    view.evaluateJavascript("window.ContinuumNative?.message($message);", null)
                }
                queuedMessages.clear()
                queuedSources.values.toList().forEach { sources ->
                    if (current(sources)) view.evaluateJavascript("window.ContinuumNative?.sources($sources);", null)
                }
                queuedSources.clear()
                queuedDocument?.let { json ->
                    view.evaluateJavascript("window.ContinuumNative?.document($json);", null)
                    queuedDocument = null
                }
                queuedResults.values.forEach { result ->
                    view.evaluateJavascript("window.ContinuumNative?.commandResult($result);", null)
                }
                queuedResults.clear()
                queuedComputer?.let { (scope, packet, queuedAt) ->
                    queuedComputer = null
                    val elapsed = (System.nanoTime() - queuedAt).coerceAtLeast(0L) / 1_000_000
                    if (scope == binding) presentComputer(packet.copy(ageMs = packet.ageMs?.let { it + elapsed }))
                }
                queuedSpeech?.let { (scope, packet) -> queuedSpeech = null; if (scope == binding) presentSpeech(packet) }
                onReady()
                queuedSection?.let { (section, keyboard) ->
                    queuedSection = null
                    if (current(section)) showSection(section.optString("name"), keyboard)
                }
            }

        }
        loadUrl(INDEX)
    }
}
