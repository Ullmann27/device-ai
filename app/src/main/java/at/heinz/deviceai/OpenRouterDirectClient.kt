package at.heinz.deviceai

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Calendar

/**
 * Explicit one-question OpenRouter BYOK channel. Does not inherit chat history,
 * local memory, device data, research sources, tool permissions or Shizuku access.
 *
 * No cloud fallback: only the owner pressing SEND calls this client.
 * Android's app-private AES-GCM/Keystore store holds the key, never source code.
 */
class OpenRouterDirectClient(private val context: Context) {
    companion object {
        private const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
        private const val MODEL = "openai/gpt-4o"
        private const val DAILY_LIMIT = 5
        private const val MAX_RESPONSE_BYTES = 65536
        private const val MAX_QUESTION_CHARS = 1800
        private const val PREFS = "continuum_openrouter_budget"
        private const val KEY_DAY = "day"
        private const val KEY_COUNT = "count"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    fun answer(question: String, callback: (Result<String>) -> Unit) {
        val trimmed = question.trim()
        if (trimmed.length !in 3..MAX_QUESTION_CHARS) {
            callback(Result.failure(IllegalArgumentException("Frage: 3 bis 1800 Zeichen erforderlich.")))
            return
        }
        Thread({
            val result = runCatching {
                val key = SecureTokenStore.loadOpenRouterToken(context)
                    ?: throw IllegalStateException("OpenRouter-Schlüssel fehlt in den Cloud-Einstellungen.")
                // Local spending cap counts attempted requests, including rejected ones.
                // This is not an account-wide spending limit; configure that at OpenRouter.
                reserveAttempt()
                requestAnswer(key, trimmed)
            }
            mainHandler.post { callback(result) }
        }, "continuum-openrouter-byok").apply { isDaemon = true; start() }
    }

    @Synchronized
    private fun reserveAttempt() {
        val calendar = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        val day = calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val used = if (prefs.getInt(KEY_DAY, -1) == day) prefs.getInt(KEY_COUNT, 0) else 0
        if (used >= DAILY_LIMIT) {
            throw IllegalStateException("OpenRouter-Tageslimit erreicht ($DAILY_LIMIT Versuche).")
        }
        check(prefs.edit().putInt(KEY_DAY, day).putInt(KEY_COUNT, used + 1).commit()) {
            "Sicherheitslimit konnte nicht gespeichert werden."
        }
    }

    private fun requestAnswer(key: String, question: String): String {
        val payload = JSONObject().apply {
            put("model", MODEL)
            put("max_tokens", 900)
            put("temperature", 0.3)
            put("stream", false)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "Du bist ein externer Textmentor. Antworte auf Deutsch. " +
                        "Erfinde keine Quellen. Du besitzt weder Gerätezugriff noch Langzeitgedächtnis.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", question)
                })
            })
        }
        val url = URL(ENDPOINT)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            requestMethod = "POST"
            connectTimeout = 12000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $key")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.use {
                it.write(payload.toString().toByteArray(StandardCharsets.UTF_8))
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                // Never show or log upstream error bodies: they can echo sensitive values.
                val reason = when (status) {
                    401, 403 -> "Schlüssel ungültig oder nicht berechtigt."
                    402 -> "Nicht genügend OpenRouter-Guthaben."
                    404 -> "Modell nicht verfügbar."
                    429 -> "OpenRouter-Anfragelimit erreicht."
                    in 300..399 -> "Unerwartete Weiterleitung blockiert."
                    else -> "OpenRouter HTTP $status."
                }
                throw IllegalStateException(reason)
            }
            if (connection.contentLengthLong > MAX_RESPONSE_BYTES) {
                throw IllegalStateException("Antwort zu groß.")
            }
            val bytes = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(4096)
                while (true) {
                    val length = input.read(buffer)
                    if (length < 0) break
                    if (bytes.size() + length > MAX_RESPONSE_BYTES) {
                        throw IllegalStateException("Antwort zu groß.")
                    }
                    bytes.write(buffer, 0, length)
                }
            }
            val json = JSONObject(bytes.toString("UTF-8"))
            val text = json.optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content").orEmpty().trim()
            if (text.isEmpty()) throw IllegalStateException("Das Modell lieferte keinen Antworttext.")
            return text.take(4200)
        } finally {
            connection.disconnect()
        }
    }
}
