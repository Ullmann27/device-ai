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
 * Explicit, owner-approved, one-question NVIDIA Nemotron 3 Ultra BYOK channel.
 *
 * No local conversation, user memory, device metadata, tool schemas or Shizuku
 * authority are passed to NVIDIA. No automatic cloud routing or fallback.
 * The API key is loaded only from the existing Android Keystore-backed store.
 */
class NvidiaNemotronDirectClient(private val context: Context) {
    companion object {
        private const val ENDPOINT = "https://integrate.api.nvidia.com/v1/chat/completions"
        private const val MODEL = "nvidia/nemotron-3-ultra-550b-a55b"
        private const val DAILY_LIMIT = 5
        private const val MAX_QUESTION_CHARS = 1800
        private const val MAX_RESPONSE_BYTES = 128 * 1024
        private const val PREFS = "continuum_nvidia_nemotron_budget"
        private const val KEY_DAY = "day"
        private const val KEY_COUNT = "count"
        private val budgetLock = Any()
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    fun answer(question: String, callback: (Result<String>) -> Unit) {
        val trimmed = question.trim()
        if (trimmed.length !in 3..MAX_QUESTION_CHARS ||
            trimmed.any { it.code < 32 && it != '\n' && it != '\t' }) {
            callback(Result.failure(IllegalArgumentException(
                "NVIDIA-Frage: 3 bis 1800 Zeichen ohne Steuerzeichen erforderlich."
            )))
            return
        }
        Thread({
            val result = runCatching {
                val key = SecureTokenStore.loadNvidiaToken(context)
                    ?: throw IllegalStateException(
                        "NVIDIA-Schlüssel fehlt. Bitte unter Cloud-Einstellungen sicher speichern."
                    )
                reserveAttempt()
                requestAnswer(key, trimmed)
            }
            mainHandler.post { callback(result) }
        }, "continuum-nvidia-nemotron-byok").apply { isDaemon = true; start() }
    }

    /** Local spending guard; this is not an NVIDIA account-level quota. */
    private fun reserveAttempt() = synchronized(budgetLock) {
        val calendar = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        val day = calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val used = if (prefs.getInt(KEY_DAY, -1) == day) prefs.getInt(KEY_COUNT, 0) else 0
        if (used >= DAILY_LIMIT) {
            throw IllegalStateException("NVIDIA-Tageslimit von $DAILY_LIMIT Versuchen erreicht.")
        }
        check(prefs.edit().putInt(KEY_DAY, day).putInt(KEY_COUNT, used + 1).commit()) {
            "NVIDIA-Tageslimit konnte nicht gespeichert werden."
        }
    }

    private fun requestAnswer(key: String, question: String): String {
        // NVIDIA's OpenAI-compatible chat/completions endpoint accepts the
        // chat_template_kwargs field at the JSON top level (OpenAI SDK extra_body).
        val payload = JSONObject().apply {
            put("model", MODEL)
            put("temperature", 0.7)
            put("max_tokens", 4096)
            put("stream", false)
            // Keep reasoning bounded for this five-requests/day smoke-test client.
            // NVIDIA recommends medium_effort as a starting point.
            put("chat_template_kwargs", JSONObject()
                .put("enable_thinking", true)
                .put("medium_effort", true)
                .put("force_nonempty_content", true))
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put(
                    "content",
                    "Du bist ein externer, nicht verifizierter KI-Textmentor. " +
                        "Antworte auf Deutsch. Du hast weder Zugriff auf Gerätedaten, " +
                        "Werkzeuge, privates Gedächtnis noch das GitHub-Repository. " +
                        "Erfinde keine Quellen und behaupte keine durchgeführten Aktionen."
                ))
                put(JSONObject().put("role", "user").put("content", question))
            })
        }

        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $key")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            useCaches = false
        }

        try {
            connection.outputStream.use {
                it.write(payload.toString().toByteArray(StandardCharsets.UTF_8))
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                // Upstream errors can echo secrets or user text: never read, log or display.
                val message = when (status) {
                    401, 403 -> "NVIDIA-Schlüssel ungültig oder nicht berechtigt."
                    402 -> "NVIDIA-Kontingent oder Guthaben nicht ausreichend."
                    404 -> "Nemotron-Modell an diesem Endpunkt nicht verfügbar."
                    408, 429 -> "NVIDIA hat die Anfrage begrenzt oder abgebrochen."
                    in 300..399 -> "NVIDIA-Weiterleitung aus Sicherheitsgründen blockiert."
                    else -> "NVIDIA-Endpunkt antwortete mit HTTP $status."
                }
                throw IllegalStateException(message)
            }

            if (connection.contentLengthLong > MAX_RESPONSE_BYTES) {
                throw IllegalStateException("NVIDIA-Antwort überschreitet die Größenbegrenzung.")
            }
            val bytes = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(4096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (bytes.size() + count > MAX_RESPONSE_BYTES) {
                        throw IllegalStateException("NVIDIA-Antwort überschreitet die Größenbegrenzung.")
                    }
                    bytes.write(buffer, 0, count)
                }
            }

            val response = JSONObject(bytes.toString("UTF-8"))
            val message = response.optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")
            // Private reasoning_content is deliberately neither shown nor saved.
            val answer = (message?.opt("content") as? String).orEmpty().trim()
            if (answer.isEmpty()) {
                throw IllegalStateException(
                    "Nemotron lieferte keinen sichtbaren Antworttext. Bitte kleinere Frage versuchen."
                )
            }
            return answer.take(4200)
        } finally {
            connection.disconnect()
        }
    }
}
