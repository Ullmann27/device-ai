package at.heinz.deviceai.gmail

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/** One HTTPS request to the Gmail REST API. [toString] never shows the Authorization header. */
internal class GmailHttpRequest(val method: String, val url: String, val headers: Map<String, String>, val body: ByteArray?) {
    override fun toString(): String = "$method ${url.substringBefore('?')}" + (body?.let { " (${it.size} Bytes)" } ?: "")
}

internal class GmailHttpResponse(val code: Int, val body: String) {
    override fun toString(): String = "HTTP $code (${body.length} Zeichen)"
}

/**
 * The network boundary. Production: HttpURLConnection over HTTPS (no new library); host tests: a fake that records
 * requests. An [IOException] means the request did not complete (no network, broken connection, timeout).
 */
internal fun interface GmailHttpTransport {
    @Throws(IOException::class)
    fun execute(request: GmailHttpRequest): GmailHttpResponse
}

/**
 * The Gmail API calls this app makes, with Google's documented error handling:
 *  - 401: the cached token is cleared, a new one is fetched silently once, the call is repeated once;
 *  - 403 insufficient scope → [GmailFailureKind.PERMISSION_REQUIRED] for that right; API not enabled → API_DISABLED;
 *    rate limit → bounded retry like 429;
 *  - `invalid_grant` → REVOKED; 404 → NOT_FOUND;
 *  - 429 and 5xx: at most [MAX_RETRIES] retries with growing pause, then an honest failure;
 *  - a request that did not complete → INTERRUPTED (never retried: a POST might have arrived).
 * Responses are read up to [MAX_RESPONSE_CHARS]; nothing of a response body enters an exception or a log line.
 */
internal class GmailApi(
    private val transport: GmailHttpTransport,
    private val tokens: GmailTokenSource,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val cancelled: () -> Boolean = { false },
    private val onRequest: (GmailHttpRequest) -> Unit = {}
) {
    /** messages.list: ids only. [query] in Gmail search syntax. */
    fun listMessages(query: String?, max: Int, labelIds: List<String> = emptyList()): List<Pair<String, String>> {
        val params = buildList {
            query?.takeIf { it.isNotBlank() }?.let { add("q" to it) }
            add("maxResults" to max.coerceIn(1, MAX_LIST).toString())
            labelIds.forEach { add("labelIds" to it) }
        }
        val json = get(GmailCapability.READ, "users/me/messages", params)
        val rows = json.optJSONArray("messages") ?: return emptyList()
        return (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val id = row.optString("id").takeIf(::validId) ?: return@mapNotNull null
            id to (row.optString("threadId").takeIf(::validId) ?: id)
        }
    }

    /** messages.get with only the headers the lane shows. */
    fun messageMetadata(id: String): JSONObject = get(GmailCapability.READ, "users/me/messages/${checkedId(id)}",
        listOf("format" to "metadata") + METADATA_HEADERS.map { "metadataHeaders" to it })

    /** messages.get with the whole payload tree. */
    fun messageFull(id: String): JSONObject = get(GmailCapability.READ, "users/me/messages/${checkedId(id)}", listOf("format" to "full"))

    fun thread(id: String): JSONObject = get(GmailCapability.READ, "users/me/threads/${checkedId(id)}", listOf("format" to "full"))

    /** messages.attachments.get → the decoded bytes; refused above [maxBytes] by the size Gmail reports. */
    fun attachment(messageId: String, attachmentId: String, maxBytes: Int): ByteArray {
        val json = get(GmailCapability.READ, "users/me/messages/${checkedId(messageId)}/attachments/${checkedId(attachmentId)}", emptyList())
        if (json.optInt("size", 0) > maxBytes) throw GmailException(GmailFailureKind.INVALID_RESPONSE, GmailCapability.READ)
        return GmailMime.decodeBase64Url(json.optString("data")) ?: throw GmailException(GmailFailureKind.INVALID_RESPONSE, GmailCapability.READ)
    }

    /** users.getProfile → the account's address. */
    fun profileAddress(capability: GmailCapability): String {
        val address = get(capability, "users/me/profile", emptyList()).optString("emailAddress").trim()
        if (!GmailMime.validAddress(address)) throw GmailException(GmailFailureKind.INVALID_RESPONSE, capability)
        return address
    }

    /** drafts.create → the draft id. Needs [GmailCapability.DRAFTS]. */
    fun createDraft(raw: String, threadId: String?): String {
        val message = JSONObject().put("raw", raw)
        threadId?.let { message.put("threadId", checkedId(it)) }
        val json = post(GmailCapability.DRAFTS, "users/me/drafts", JSONObject().put("message", message))
        return json.optString("id").takeIf(::validId) ?: throw GmailException(GmailFailureKind.INVALID_RESPONSE, GmailCapability.DRAFTS)
    }

    /** messages.send → the sent message id. Called only by [OwnerMailLane] after the owner's confirmation. */
    fun send(raw: String, threadId: String?): String {
        val body = JSONObject().put("raw", raw)
        threadId?.let { body.put("threadId", checkedId(it)) }
        // A 5xx answer to a send may still have sent the mail: it is never repeated (no second mail).
        val json = post(GmailCapability.SEND, "users/me/messages/send", body, mayRepeat = false)
        return json.optString("id").takeIf(::validId) ?: throw GmailException(GmailFailureKind.INVALID_RESPONSE, GmailCapability.SEND)
    }

    private fun get(capability: GmailCapability, path: String, params: List<Pair<String, String>>): JSONObject =
        call(capability, "GET", BASE + path + query(params), null)

    private fun post(capability: GmailCapability, path: String, body: JSONObject, mayRepeat: Boolean = true): JSONObject =
        call(capability, "POST", BASE + path, body.toString().toByteArray(Charsets.UTF_8), mayRepeat)

    /** [mayRepeat]: a server error may be answered by sending the same request again (false for messages.send). */
    private fun call(capability: GmailCapability, method: String, url: String, body: ByteArray?, mayRepeat: Boolean = true): JSONObject {
        var reauthorized = false
        var retries = 0
        while (true) {
            if (cancelled()) throw java.util.concurrent.CancellationException("Gmail-Abruf gestoppt")
            val token = tokens.token(capability)
            val headers = linkedMapOf("Authorization" to token.authorizationHeader(), "Accept" to "application/json")
            if (body != null) headers["Content-Type"] = "application/json; charset=UTF-8"
            val request = GmailHttpRequest(method, url, headers, body)
            onRequest(request)
            val response = try { transport.execute(request) } catch (_: IOException) {
                throw GmailException(GmailFailureKind.INTERRUPTED, capability)
            }
            val text = response.body.take(MAX_RESPONSE_CHARS + 1)
            when (response.code) {
                in 200..299 -> {
                    if (text.length > MAX_RESPONSE_CHARS) throw GmailException(GmailFailureKind.INVALID_RESPONSE, capability, response.code)
                    return runCatching { JSONObject(text.ifBlank { "{}" }) }.getOrNull()
                        ?: throw GmailException(GmailFailureKind.INVALID_RESPONSE, capability, response.code)
                }
                401 -> {
                    if (grantGone(text)) throw GmailException(GmailFailureKind.REVOKED, capability, 401)
                    if (reauthorized) throw GmailException(GmailFailureKind.PERMISSION_REQUIRED, capability, 401)
                    reauthorized = true
                    tokens.invalidate(token)
                }
                400 -> throw GmailException(if (grantGone(text)) GmailFailureKind.REVOKED else GmailFailureKind.INVALID_RESPONSE, capability, 400)
                403 -> when (reason(text)) {
                    Reason.SCOPE -> throw GmailException(GmailFailureKind.PERMISSION_REQUIRED, capability, 403)
                    Reason.DISABLED -> throw GmailException(GmailFailureKind.API_DISABLED, capability, 403)
                    Reason.RATE -> retries = retry(retries, GmailFailureKind.QUOTA, capability, 403)
                    Reason.OTHER -> throw GmailException(GmailFailureKind.PERMISSION_REQUIRED, capability, 403)
                }
                404 -> throw GmailException(GmailFailureKind.NOT_FOUND, capability, 404)
                429 -> retries = retry(retries, GmailFailureKind.QUOTA, capability, 429)
                in 500..599 -> if (mayRepeat) retries = retry(retries, GmailFailureKind.SERVER, capability, response.code)
                    else throw GmailException(GmailFailureKind.SERVER, capability, response.code)
                else -> throw GmailException(GmailFailureKind.UNKNOWN, capability, response.code)
            }
        }
    }

    /** One more attempt after a pause, or the failure once [MAX_RETRIES] are used up. */
    private fun retry(done: Int, kind: GmailFailureKind, capability: GmailCapability, code: Int): Int {
        if (done >= MAX_RETRIES) throw GmailException(kind, capability, code)
        sleep(BACKOFF_MILLIS * (1L shl (done * 2)))
        return done + 1
    }

    private enum class Reason { SCOPE, DISABLED, RATE, OTHER }

    private fun reason(body: String): Reason {
        val error = runCatching { JSONObject(body).optJSONObject("error") }.getOrNull()
        val texts = buildList {
            error?.optString("status")?.let(::add)
            error?.optJSONArray("errors")?.let { rows -> (0 until rows.length()).forEach { add(rows.optJSONObject(it)?.optString("reason").orEmpty()) } }
            error?.optJSONArray("details")?.let { rows -> (0 until rows.length()).forEach { add(rows.optJSONObject(it)?.optString("reason").orEmpty()) } }
        }.map { it.lowercase() }
        return when {
            texts.any { it in setOf("insufficientpermissions", "access_token_scope_insufficient") } -> Reason.SCOPE
            texts.any { it in setOf("accessnotconfigured", "service_disabled") } -> Reason.DISABLED
            texts.any { it in setOf("ratelimitexceeded", "userratelimitexceeded", "dailylimitexceeded", "quotaexceeded", "rate_limit_exceeded") } -> Reason.RATE
            else -> Reason.OTHER
        }
    }

    private fun grantGone(body: String): Boolean = body.contains("invalid_grant")

    private fun query(params: List<Pair<String, String>>): String = if (params.isEmpty()) "" else
        "?" + params.joinToString("&") { (key, value) -> key + "=" + URLEncoder.encode(value, "UTF-8").replace("+", "%20") }

    private fun checkedId(id: String): String { require(validId(id)) { "invalid id" }; return id }

    companion object {
        const val BASE = "https://gmail.googleapis.com/gmail/v1/"
        const val MAX_LIST = 25
        const val MAX_RETRIES = 2
        const val BACKOFF_MILLIS = 500L
        const val MAX_RESPONSE_CHARS = 2_000_000
        val METADATA_HEADERS = listOf("From", "To", "Cc", "Subject", "Date", "Message-ID", "List-Unsubscribe")
        private val ID = Regex("^[A-Za-z0-9_-]{1,256}$")
        fun validId(id: String?): Boolean = id != null && ID.matches(id)

        /** The message objects of a thread response, oldest first. */
        fun threadMessages(thread: JSONObject): List<JSONObject> {
            val rows: JSONArray = thread.optJSONArray("messages") ?: return emptyList()
            return (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }
        }
    }
}
