package at.heinz.deviceai.ui

import android.content.Context
import android.graphics.Color
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
    private val onNativeDraft: (Command03Draft) -> Unit,
    private val onReady: () -> Unit = {}
) : WebView(context) {
    companion object {
        const val LOCAL_HOST = "appassets.androidplatform.net"
        const val ASSET_PATH = "/assets/continuum-command03/"
        const val INDEX = "https://" + LOCAL_HOST + ASSET_PATH + "index.android.html"
        private val SAFE_PATH = Regex("[A-Za-z0-9_./-]{1,200}")
        private val ROUTES = setOf("home", "chat", "chats", "new-chat", "files",
            "research", "agents", "memory", "device", "settings", "voice-input", "stop")
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
    private var disposed = false
    private var queuedState: String? = null
    private var queuedDocument: String? = null
    private var queuedTranscript: Pair<String, JSONArray>? = null
    private val queuedMessages = ArrayDeque<JSONObject>()
    private data class CommandReceipt(val request: Command03Command, var result: JSONObject? = null)
    private val commands = linkedMapOf<String, CommandReceipt>()
    private val queuedResults = linkedMapOf<String, JSONObject>()

    /** Native -> local page only. Never grant JavaScript direct Android capabilities. */
    fun present(state: JSONObject) {
        if (disposed) return
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
        if (disposed) return
        val data = document.toString()
        if (!pageReady) queuedDocument = data
        else evaluateJavascript("window.ContinuumNative?.document($data);", null)
    }

    fun clearDocument() {
        if (disposed) return
        queuedDocument = null
        if (pageReady) evaluateJavascript("window.ContinuumNative?.clearDocument();", null)
    }

    fun presentTranscript(sessionId: String, entries: JSONArray) {
        if (disposed || !Command03BridgeContract.validSession(sessionId)) return
        if (!pageReady) {
            queuedTranscript = sessionId to entries
            queuedMessages.removeAll { it.optString("sessionId") == sessionId }
            return
        }
        // Full stored messages can exceed the former 6,000-character preview.
        // Bound each Binder/evaluateJavascript call instead of cutting their text.
        var batch = JSONArray()
        var characters = 0
        var append = false
        fun flush() {
            val data = JSONObject().put("sessionId", sessionId).put("entries", batch)
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

    fun presentMessage(sessionId: String, user: Boolean, text: String) {
        if (disposed || text.isBlank() || !Command03BridgeContract.validSession(sessionId)) return
        val message = JSONObject().put("sessionId", sessionId).put("user", user)
            .put("text", Command03BridgeContract.messageText(text))
        if (!pageReady) {
            queuedMessages.addLast(message)
            while (queuedMessages.size > 60) queuedMessages.removeFirst()
            return
        }
        val data = message.toString()
        evaluateJavascript("window.ContinuumNative?.message($data);", null)
    }

    fun showSection(section: String) {
        if (disposed || !pageReady || section !in ROUTES) return
        evaluateJavascript("window.ContinuumNative?.section(" + JSONObject.quote(section) + ");", null)
    }

    private fun commandResult(request: Command03Command, accepted: Boolean, reason: String?) {
        if (disposed) return
        val result = JSONObject().put("id", request.id).put("sessionId", request.sessionId)
            .put("accepted", accepted).put("reason", reason ?: JSONObject.NULL)
        if (!pageReady) queuedResults[request.id] = result
        else evaluateJavascript("window.ContinuumNative?.commandResult($result);", null)
    }

    private fun submitCommand(uri: Uri) {
        val request = Command03Command(uri.getQueryParameter("id").orEmpty(),
            uri.getQueryParameter("sessionId").orEmpty(), uri.getQueryParameter("value").orEmpty())
        if (!Command03BridgeContract.validRequest(request.id) ||
            !Command03BridgeContract.validSession(request.sessionId)) {
            commandResult(request, false, "invalid-input")
            return
        }
        Command03BridgeContract.commandFailure(request.text)?.let {
            commandResult(request, false, it)
            return
        }
        val key = request.sessionId + ":" + request.id
        commands[key]?.let { previous ->
            if (previous.request != request) commandResult(request, false, "invalid-input")
            else previous.result?.let { result ->
                commandResult(request, result.optBoolean("accepted"), result.optString("reason").takeUnless { it == "null" })
            }
            return
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

    override fun destroy() {
        disposed = true
        pageReady = false
        queuedState = null
        queuedDocument = null
        queuedTranscript = null
        queuedMessages.clear()
        commands.clear()
        queuedResults.clear()
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
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (disposed) return true
                val uri = request.url
                if (request.isForMainFrame && uri.scheme == "continuum-native" &&
                    uri.host == "action" && uri.port == -1 && uri.userInfo == null && request.method == "GET") {
                    val command = uri.pathSegments.singleOrNull().orEmpty()
                    val value = uri.getQueryParameter("value").orEmpty()
                    if (command == "route" && value in ROUTES) {
                        onNativeAction(value, null)
                    } else if (command == "select-chat" && Command03BridgeContract.validSession(value)) {
                        onNativeAction("select-chat", value)
                    } else if (command == "command") {
                        submitCommand(uri)
                    } else if (command == "draft") {
                        val session = uri.getQueryParameter("sessionId").orEmpty()
                        val start = uri.getQueryParameter("selectionStart")?.toIntOrNull()
                        val end = uri.getQueryParameter("selectionEnd")?.toIntOrNull()
                        if (Command03BridgeContract.validSession(session) && Command03BridgeContract.validDraft(value) &&
                            start != null && end != null && start in 0..value.length && end in start..value.length) {
                            onNativeDraft(Command03Draft(session, value, start, end))
                        }
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
                queuedTranscript?.let { (session, entries) ->
                    queuedTranscript = null
                    presentTranscript(session, entries)
                }
                queuedMessages.toList().forEach { message ->
                    view.evaluateJavascript("window.ContinuumNative?.message($message);", null)
                }
                queuedMessages.clear()
                queuedDocument?.let { json ->
                    view.evaluateJavascript("window.ContinuumNative?.document($json);", null)
                    queuedDocument = null
                }
                queuedResults.values.forEach { result ->
                    view.evaluateJavascript("window.ContinuumNative?.commandResult($result);", null)
                }
                queuedResults.clear()
                onReady()
            }

        }
        loadUrl(INDEX)
    }
}
