package at.heinz.deviceai

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AgentBackendClient(private val context: Context) {
    companion object {
        private const val PREFS = "device_ai_cloud"
        private const val KEY_URL = "backend_url"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    fun backendUrl(): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_URL, "")
            .orEmpty()
            .trim()

    fun saveBackendUrl(value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_URL, value.trim().trimEnd('/'))
            .apply()
    }

    fun hasToken(): Boolean = !SecureTokenStore.load(context).isNullOrBlank()

    fun isConfigured(): Boolean =
        backendUrl().startsWith("https://", ignoreCase = true) && hasToken()

    fun health(callback: (Result<String>) -> Unit) {
        val base = backendUrl()
        if (!base.startsWith("https://", ignoreCase = true)) {
            callback(Result.failure(IllegalStateException("Backend-URL fehlt oder ist nicht HTTPS.")))
            return
        }
        Thread {
            val result = runCatching {
                request("GET", "$base/health", null, authenticated = false)
            }
            mainHandler.post { callback(result) }
        }.start()
    }

    fun send(
        sessionId: String,
        message: String?,
        shizukuReady: Boolean,
        toolOutputs: List<ToolOutput> = emptyList(),
        callback: (Result<AgentReply>) -> Unit
    ) {
        val base = backendUrl()
        val token = SecureTokenStore.load(context)
        if (!base.startsWith("https://", ignoreCase = true) || token.isNullOrBlank()) {
            callback(Result.failure(IllegalStateException("Cloud-KI ist noch nicht eingerichtet.")))
            return
        }

        Thread {
            val result = runCatching {
                val body = JSONObject().apply {
                    put("session_id", sessionId)
                    if (!message.isNullOrBlank()) put("message", message)
                    put("device", JSONObject().apply {
                        put("manufacturer", Build.MANUFACTURER)
                        put("model", Build.MODEL)
                        put("android", Build.VERSION.RELEASE)
                        put("sdk", Build.VERSION.SDK_INT)
                        put("app_version", BuildConfig.VERSION_NAME)
                        put("shizuku_ready", shizukuReady)
                    })
                    if (toolOutputs.isNotEmpty()) {
                        put("tool_outputs", JSONArray().apply {
                            toolOutputs.forEach { output ->
                                put(JSONObject().apply {
                                    put("call_id", output.callId)
                                    put("output", output.output)
                                })
                            }
                        })
                    }
                }

                val raw = request(
                    "POST",
                    "$base/v1/agent/turn",
                    body.toString(),
                    authenticated = true,
                    token = token
                )
                parseReply(JSONObject(raw))
            }
            mainHandler.post { callback(result) }
        }.start()
    }

    private fun request(
        method: String,
        url: String,
        body: String?,
        authenticated: Boolean,
        token: String? = null
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 90_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "DeviceAI/${BuildConfig.VERSION_NAME}")
            if (authenticated) {
                setRequestProperty("Authorization", "Bearer ${token.orEmpty()}")
            }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        if (body != null) {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }

        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()

        if (code !in 200..299) {
            val message = runCatching { JSONObject(text).optString("error") }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: "HTTP $code"
            throw IllegalStateException(message)
        }
        return text
    }

    private fun parseReply(json: JSONObject): AgentReply {
        val calls = mutableListOf<ToolCallRequest>()
        val array = json.optJSONArray("tool_calls") ?: JSONArray()
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            calls += ToolCallRequest(
                callId = item.getString("call_id"),
                name = item.getString("name"),
                arguments = jsonObjectToMap(item.optJSONObject("arguments") ?: JSONObject())
            )
        }

        val citations = mutableListOf<AgentCitation>()
        val citationArray = json.optJSONArray("citations") ?: JSONArray()
        for (i in 0 until citationArray.length()) {
            val item = citationArray.getJSONObject(i)
            val url = item.optString("url")
            if (url.isNotBlank()) {
                citations += AgentCitation(
                    title = item.optString("title").ifBlank { url },
                    url = url
                )
            }
        }

        return AgentReply(
            responseId = json.optString("response_id").takeIf { it.isNotBlank() },
            message = json.optString("message").takeIf { it.isNotBlank() },
            toolCalls = calls,
            citations = citations,
            status = json.optString("status", "completed")
        )
    }

    private fun jsonObjectToMap(json: JSONObject): Map<String, Any?> {
        val out = linkedMapOf<String, Any?>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            out[key] = when (val value = json.opt(key)) {
                JSONObject.NULL -> null
                is JSONArray -> {
                    val list = mutableListOf<Any?>()
                    for (i in 0 until value.length()) {
                        val item = value.opt(i)
                        list += if (item == JSONObject.NULL) null else item
                    }
                    list
                }
                else -> value
            }
        }
        return out
    }
}
