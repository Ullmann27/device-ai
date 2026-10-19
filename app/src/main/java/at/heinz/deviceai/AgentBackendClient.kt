package at.heinz.deviceai

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class MentorPairingResult(val approved: Boolean, val fingerprint: String)

data class RemoteContinuumStatus(
    val autonomousResearchEnabled: Boolean,
    val brainEvolutionEnabled: Boolean,
    val researchGeneration: Long?,
    val observations: Int?,
    val openGaps: Int?,
    val cycles: Int?,
    val successfulCycles: Int?,
    val failedCycles: Int?,
    val citationCount: Int?,
    val brainGeneration: Int?,
    val championId: String?,
    val championScore: Double?,
    val candidates: Int?,
    val reflections: Int?,
    val hypothesisTournamentEnabled: Boolean,
    val hypothesisGeneration: Int?,
    val hypotheses: Int?,
    val tournaments: Int?,
    val hypothesisLeaderElo: Int?
)

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

    fun hasMentorAccess(): Boolean = !SecureTokenStore.loadMentorToken(context).isNullOrBlank() || hasToken()

    fun ensureMentorBackend() {
        if (backendUrl().isBlank()) saveBackendUrl("https://device-ai-continuum-mentor.onrender.com")
    }

    /**
     * Operator-approved, mentor-only device pairing. Never stores the global Device token,
     * never sends local conversations, files, memory or Android identifiers.
     */
    fun pairMentor(callback: (Result<MentorPairingResult>) -> Unit) {
        ensureMentorBackend()
        val base = backendUrl()
        if (!base.startsWith("https://", ignoreCase = true)) {
            callback(Result.failure(IllegalStateException("HTTPS-Backend erforderlich.")))
            return
        }
        Thread({
            val outcome = runCatching {
                val publicKey = MentorDeviceIdentity.publicKeyBase64()
                val fingerprint = MentorDeviceIdentity.fingerprint()
                val challenge = JSONObject(
                    request("GET", "$base/v1/mentor/pairing/challenge", null, authenticated = false)
                )
                val issuedAt = challenge.getLong("issuedAt")
                val nonce = challenge.getString("nonce")
                val signed = MentorDeviceIdentity.signChallenge(issuedAt, nonce)
                val payload = JSONObject().apply {
                    put("publicKey", publicKey)
                    put("signature", signed)
                    put("challenge", challenge)
                }
                val response = try {
                    request("POST", "$base/v1/mentor/pairing/redeem",
                        payload.toString(), authenticated = false)
                } catch (e: IllegalStateException) {
                    if (e.message == "PAIRING_PENDING") return@runCatching MentorPairingResult(false, fingerprint)
                    throw e
                }
                val result = JSONObject(response)
                require(result.getBoolean("ok") &&
                    result.getString("scope") == "MENTOR_ONLY" &&
                    result.getString("fingerprint") == fingerprint) {
                    "Gerätefreigabe konnte nicht verifiziert werden."
                }
                val token = result.getString("token")
                SecureTokenStore.saveMentorToken(context, token)
                MentorPairingResult(true, fingerprint)
            }
            mainHandler.post { callback(outcome) }
        }, "continuum-mentor-pairing").apply { isDaemon = true; start() }
    }

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

    fun continuumStatus(callback: (Result<RemoteContinuumStatus>) -> Unit) {
        val base = backendUrl()
        val token = SecureTokenStore.load(context)
        if (!base.startsWith("https://", ignoreCase = true) || token.isNullOrBlank()) {
            callback(Result.failure(IllegalStateException("Cloud-KI ist noch nicht eingerichtet.")))
            return
        }
        Thread {
            val result = runCatching {
                val raw = request(
                    "GET",
                    "$base/v1/continuum/status",
                    null,
                    authenticated = true,
                    token = token
                )
                parseContinuumStatus(JSONObject(raw))
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

    fun mentorStatus(callback: (Result<Pair<Boolean, Int>>) -> Unit) {
        val base = backendUrl()
        val token = SecureTokenStore.loadMentorToken(context) ?: SecureTokenStore.load(context)
        if (!base.startsWith("https://", ignoreCase = true) || token.isNullOrBlank()) {
            callback(Result.failure(IllegalStateException("Groq-Gerätefreigabe oder Device-Token fehlt.")))
            return
        }
        Thread({
            val outcome = runCatching {
                val body = request(
                    "GET", "$base/v1/mentor/status",
                    null, authenticated = true, token = token
                )
                val json = JSONObject(body)
                val available = json.optBoolean("available", false)
                val remaining = json.optInt("remainingToday", 0).coerceAtLeast(0)
                available to remaining
            }
            mainHandler.post { callback(outcome) }
        }, "continuum-mentor-status").apply { isDaemon = true; start() }
    }

    /**
     * An owner-confirmed one-shot remote request.
     * Does not transmit device identifiers, previous chat, tool outputs or memory.
     * The Groq key exists exclusively in the owner's backend environment.
     */
    fun groqMentor(question: String, callback: (Result<String>) -> Unit) {
        val base = backendUrl()
        val token = SecureTokenStore.loadMentorToken(context) ?: SecureTokenStore.load(context)
        if (!base.startsWith("https://", ignoreCase = true) || token.isNullOrBlank()) {
            callback(Result.failure(IllegalStateException("Groq-Gerätefreigabe oder Device-Token fehlt.")))
            return
        }
        if (question.trim().length !in 3..1800) {
            callback(Result.failure(IllegalArgumentException("Mentorfrage muss zwischen 3 und 1800 Zeichen lang sein.")))
            return
        }
        Thread({
            val outcome = runCatching {
                val payload = JSONObject().apply {
                    put("consent", true)
                    put("provider", "GROQ")
                    put("question", question.trim())
                }
                val raw = request(
                    "POST", "$base/v1/mentor/answer",
                    payload.toString(), authenticated = true, token = token
                )
                val json = JSONObject(raw)
                require(json.optString("provider") == "GROQ" &&
                    json.optString("trust") == "UNVERIFIED_EXTERNAL" &&
                    !json.optBoolean("memory_written", true) &&
                    json.optJSONArray("tool_calls")?.length() == 0) {
                    "Ungültige oder nicht isolierte Mentorantwort."
                }
                json.optString("message").trim().take(1800)
                    .takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Leere Mentorantwort.")
            }
            mainHandler.post { callback(outcome) }
        }, "continuum-groq-mentor").apply { isDaemon = true; start() }
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

    private fun parseContinuumStatus(json: JSONObject): RemoteContinuumStatus {
        val daemon = json.optJSONObject("daemon")
        val brain = json.optJSONObject("brain")
        val hypotheses = json.optJSONObject("hypotheses")
        fun JSONObject?.intOrNull(key: String): Int? =
            this?.takeIf { it.has(key) && !it.isNull(key) }?.optInt(key)
        fun JSONObject?.longOrNull(key: String): Long? =
            this?.takeIf { it.has(key) && !it.isNull(key) }?.optLong(key)
        fun JSONObject?.doubleOrNull(key: String): Double? =
            this?.takeIf { it.has(key) && !it.isNull(key) }?.optDouble(key)
                ?.takeIf { it.isFinite() }
        fun JSONObject?.stringOrNull(key: String): String? =
            this?.optString(key)?.takeIf { it.isNotBlank() && it != "null" }
        return RemoteContinuumStatus(
            autonomousResearchEnabled = json.optBoolean("autonomousResearchEnabled", false),
            brainEvolutionEnabled = json.optBoolean("brainEvolutionEnabled", false),
            researchGeneration = daemon.longOrNull("generation"),
            observations = daemon.intOrNull("observations"),
            openGaps = daemon.intOrNull("openGaps"),
            cycles = daemon.intOrNull("cycles"),
            successfulCycles = daemon.intOrNull("successfulCycles"),
            failedCycles = daemon.intOrNull("failedCycles"),
            citationCount = daemon.intOrNull("citationCount"),
            brainGeneration = brain.intOrNull("generation"),
            championId = brain.stringOrNull("championId"),
            championScore = brain.doubleOrNull("championScore"),
            candidates = brain.intOrNull("candidates"),
            reflections = brain.intOrNull("reflections"),
            hypothesisTournamentEnabled = json.optBoolean("hypothesisTournamentEnabled", false),
            hypothesisGeneration = hypotheses.intOrNull("generation"),
            hypotheses = hypotheses.intOrNull("hypotheses"),
            tournaments = hypotheses.intOrNull("tournaments"),
            hypothesisLeaderElo = hypotheses.intOrNull("leaderElo")
        )
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
