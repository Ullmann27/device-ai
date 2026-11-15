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
 * WIP visual-parity renderer for the owner's exact Kommando-03 HTML source.
 *
 * The source files are byte-identical to PR #850 (verified by the lock manifest).
 * This WebView is for the explicitly opened visual review stage, NOT the default
 * Android scene until native event/display parity and on-device QA are complete.
 *
 * No JavascriptInterface, file:// origin, WebView internet access, secret or
 * model/tool API exposure. Native actions are top-level allowlisted navigations
 * and still pass through AgentActivity's existing owner-gated handlers.
 */
internal class ContinuumCommand03OriginalStage(
    context: Context,
    private val onNativeAction: (route: String, value: String?) -> Unit,
    private val onReady: () -> Unit = {}
) : WebView(context) {
    companion object {
        const val LOCAL_HOST = "appassets.androidplatform.net"
        const val ASSET_PATH = "/assets/continuum-command03/"
        const val INDEX = "https://" + LOCAL_HOST + ASSET_PATH + "index.android.html"
        private val SAFE_PATH = Regex("[A-Za-z0-9_./-]{1,200}")
        private val ROUTES = setOf("home", "chat", "chats", "new-chat", "files",
            "research", "agents", "memory", "device", "settings", "voice-input", "stop")
        private val CHAT_ID = Regex("[a-f0-9-]{36}")
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
    private var queuedState: String? = null
    private var queuedDocument: String? = null

    /** Native -> local page only. Never grant JavaScript direct Android capabilities. */
    fun present(state: JSONObject) {
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
        val data = document.toString()
        if (!pageReady) queuedDocument = data
        else evaluateJavascript("window.ContinuumNative?.document($data);", null)
    }

    fun clearDocument() {
        queuedDocument = null
        if (pageReady) evaluateJavascript("window.ContinuumNative?.clearDocument();", null)
    }

    fun presentTranscript(sessionId: String, entries: JSONArray) {
        if (!pageReady || !CHAT_ID.matches(sessionId)) return
        val data = JSONObject().put("sessionId", sessionId).put("entries", entries).toString()
        evaluateJavascript("window.ContinuumNative?.transcript($data);", null)
    }

    fun presentMessage(user: Boolean, text: String) {
        if (!pageReady || text.isBlank()) return
        val data = JSONObject().put("user", user).put("text", text.take(6000)).toString()
        evaluateJavascript("window.ContinuumNative?.message($data);", null)
    }

    fun showSection(section: String) {
        if (!pageReady || section !in ROUTES) return
        evaluateJavascript("window.ContinuumNative?.section(" + JSONObject.quote(section) + ");", null)
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
                val uri = request.url
                if (request.isForMainFrame && uri.scheme == "continuum-native" &&
                    uri.host == "action") {
                    val command = uri.pathSegments.singleOrNull().orEmpty()
                    val value = uri.getQueryParameter("value").orEmpty()
                    if (command == "route" && value in ROUTES) {
                        onNativeAction(value, null)
                    } else if (command == "select-chat" && CHAT_ID.matches(value)) {
                        onNativeAction("select-chat", value)
                    } else if (command == "command" && value.isNotBlank() &&
                        value.length <= 2_000 && value.none(Char::isISOControl)) {
                        onNativeAction("command", value)
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
                if (url != INDEX) return
                // The Android-only bridge was loaded BEFORE the unchanged
                // demo scripts by index.android.html. No post-load race.
                pageReady = true
                queuedState?.let { json ->
                    view.evaluateJavascript("window.ContinuumNative?.receive($json);", null)
                    queuedState = null
                }
                queuedDocument?.let { json ->
                    view.evaluateJavascript("window.ContinuumNative?.document($json);", null)
                    queuedDocument = null
                }
                onReady()
            }

        }
        loadUrl(INDEX)
    }
}
