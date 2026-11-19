package at.heinz.deviceai.gmail

import at.heinz.deviceai.OwnerLaneOutcome
import at.heinz.deviceai.OwnerWorkGeneration
import at.heinz.deviceai.cockpit.StepState
import at.heinz.deviceai.cognitive.InternetAccessMode
import at.heinz.deviceai.connections.ContinuumConnectionStore
import at.heinz.deviceai.connections.ContinuumConnectionUsage
import at.heinz.deviceai.sending.OwnerContactMail
import at.heinz.deviceai.sending.OwnerContactPhone
import at.heinz.deviceai.sending.OwnerSendContactSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.util.Base64

internal class MapStore : ContinuumConnectionStore {
    val values = HashMap<String, String>()
    override fun get(key: String): String? = values[key]
    override fun put(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
}

/** A token value that must never appear anywhere but in the Authorization header of a request. */
internal const val TOKEN_MARKER = "ya29.TOKEN-MARKER-7f3c9e"

internal class FakeAuthorizer(var grant: GmailGrant = GmailGrant.of(GmailCapability.READ)) : GmailAuthorizer {
    var consentNeeded = false
    var failure: GmailFailureKind? = null
    var issued = 0
    val invalidated = ArrayList<GmailAccessToken>()
    override fun silent(capabilities: Set<GmailCapability>): GmailAuthResult {
        failure?.let { return GmailAuthResult.Failed(it, 10) }
        if (consentNeeded || capabilities.any { !grant.covers(it) }) return GmailAuthResult.NeedsConsent(Any())
        issued++
        return GmailAuthResult.Granted(GmailAccessToken("$TOKEN_MARKER-$issued"), grant)
    }
    override fun invalidate(token: GmailAccessToken) { invalidated += token }
}

internal object Mails {
    fun b64(text: String, charset: java.nio.charset.Charset = Charsets.UTF_8): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray(charset))

    fun headers(vararg pairs: Pair<String, String>) = JSONArray().apply { pairs.forEach { put(JSONObject().put("name", it.first).put("value", it.second)) } }

    /** A message resource; [body] null = metadata only. */
    fun message(id: String, from: String, subject: String, millis: Long, unread: Boolean = true, body: String? = null,
                to: String = "heinz@example.org", thread: String = "t$id", messageId: String = "<$id@mail.example.org>",
                labels: List<String> = listOf("INBOX"), html: Boolean = false, extraHeaders: List<Pair<String, String>> = emptyList()): JSONObject {
        val headerList = listOf("From" to from, "To" to to, "Subject" to subject, "Message-ID" to messageId) + extraHeaders
        val payload = JSONObject().put("mimeType", if (body == null) "text/plain" else "multipart/alternative")
            .put("headers", headers(*headerList.toTypedArray()))
        if (body != null) {
            val parts = JSONArray()
            if (!html) parts.put(JSONObject().put("mimeType", "text/plain").put("headers", headers("Content-Type" to "text/plain; charset=UTF-8"))
                .put("body", JSONObject().put("data", b64(body)).put("size", body.length)))
            parts.put(JSONObject().put("mimeType", "text/html").put("body", JSONObject().put("data", b64("<p>" + body.replace("\n", "<br>") + "</p>"))))
            payload.put("parts", parts)
        }
        return JSONObject().put("id", id).put("threadId", thread).put("internalDate", millis.toString())
            .put("labelIds", JSONArray(labels + (if (unread) listOf("UNREAD") else emptyList())))
            .put("snippet", (body ?: subject).take(80)).put("payload", payload)
    }
}

/** Gmail as a fake REST service: list, get, thread, profile, drafts, send. Records every request. */
internal class FakeGmail : GmailHttpTransport {
    val requests = ArrayList<GmailHttpRequest>()
    val messages = LinkedHashMap<String, JSONObject>()
    var profile = "heinz@example.org"
    /** Status codes to answer before the real answer, per path fragment. */
    val failures = HashMap<String, ArrayDeque<Int>>()
    var offline = false
    var draftCount = 0

    fun add(message: JSONObject) { messages[message.getString("id")] = message }

    val sends: Int get() = requests.count { it.method == "POST" && it.url.endsWith("/messages/send") }
    val drafts: Int get() = requests.count { it.method == "POST" && it.url.endsWith("/drafts") }

    fun sentRaw(): List<String> = requests.filter { it.method == "POST" && it.url.endsWith("/messages/send") }
        .map { JSONObject(String(it.body!!, Charsets.UTF_8)).getString("raw") }

    override fun execute(request: GmailHttpRequest): GmailHttpResponse {
        requests += request
        if (offline) throw IOException("offline")
        val path = request.url.removePrefix(GmailApi.BASE).substringBefore('?')
        failures.entries.firstOrNull { path.contains(it.key) && it.value.isNotEmpty() }?.let { (_, codes) ->
            val code = codes.removeFirst()
            val body = when (code) {
                403 -> JSONObject().put("error", JSONObject().put("code", 403).put("errors", JSONArray().put(JSONObject().put("reason", "insufficientPermissions")))).toString()
                else -> "{\"error\":{\"code\":$code}}"
            }
            return GmailHttpResponse(code, body)
        }
        val query = request.url.substringAfter('?', "")
        return when {
            path == "users/me/profile" -> ok(JSONObject().put("emailAddress", profile))
            path == "users/me/messages" && request.method == "GET" -> {
                val max = Regex("maxResults=(\\d+)").find(query)?.groupValues?.get(1)?.toInt() ?: 10
                val rows = JSONArray()
                messages.values.sortedByDescending { it.getString("internalDate").toLong() }.take(max)
                    .forEach { rows.put(JSONObject().put("id", it.getString("id")).put("threadId", it.getString("threadId"))) }
                ok(JSONObject().put("messages", rows).put("resultSizeEstimate", rows.length()))
            }
            path.startsWith("users/me/messages/") && path.contains("/attachments/") -> ok(JSONObject().put("size", 11).put("data", Mails.b64("Anhangtext.")))
            path == "users/me/messages/send" -> ok(JSONObject().put("id", "sent1").put("threadId", "tx"))
            path.startsWith("users/me/messages/") -> {
                val id = path.removePrefix("users/me/messages/")
                val message = messages[id] ?: return GmailHttpResponse(404, "{}")
                if (query.contains("format=metadata")) {
                    val copy = JSONObject(message.toString())
                    copy.getJSONObject("payload").remove("parts")
                    ok(copy)
                } else ok(message)
            }
            path.startsWith("users/me/threads/") -> {
                val id = path.removePrefix("users/me/threads/")
                ok(JSONObject().put("id", id).put("messages", JSONArray(messages.values.filter { it.getString("threadId") == id })))
            }
            path == "users/me/drafts" -> ok(JSONObject().put("id", "draft" + (++draftCount)))
            else -> GmailHttpResponse(404, "{}")
        }
    }

    private fun ok(json: JSONObject) = GmailHttpResponse(200, json.toString())
}

/** The Activity as the mail lane sees it. Jobs run at once (synchronously); every answer and log line is kept. */
internal class MailHarness(
    val gmail: FakeGmail = FakeGmail(),
    val authorizer: FakeAuthorizer = FakeAuthorizer(),
    var now: Instant = Instant.parse("2026-11-19T09:00:00Z")
) : OwnerMailHost {
    val store = MapStore()
    private val connectionStore = MapStore()
    val connectionState = GmailConnection(connectionStore)
    val usageStore = MapStore()
    val usageTracker = ContinuumConnectionUsage(usageStore) { now.toEpochMilli() }
    val boardState = MailWorkBoard { now.toEpochMilli() }
    val director = OwnerMailDirector(this)

    override var sessionId = "chat-1"
    override var ownerTurn = 0L
    override val assistantName: String? = "Jarvis"
    val chat = ArrayList<String>()
    val privateAnswers = ArrayList<String>()
    val persisted = ArrayList<String>()
    val spoken = ArrayList<String>()
    val activities = ArrayList<String>()
    val outcomes = ArrayList<OwnerLaneOutcome>()
    val mailOutcomes = ArrayList<MailLaneOutcome>()
    val prompts = ArrayList<Pair<String, Boolean>>()
    val deliverables = ArrayList<Triple<String, String, Boolean>>()
    var internet = InternetAccessMode.ASK
    var cloud = false
    var localModel = true
    var modelAnswer: (String) -> OwnerWorkGeneration = { OwnerWorkGeneration("Anna bittet um die Unterlagen bis Freitag und fragt nach einem Termin.", null, true) }
    var contactsAllowed = true
    var contactRows = listOf(OwnerContactMail(1, "Anna Beispiel", "anna@example.org"), OwnerContactMail(2, "Peter Muster", "peter@example.org"))
    var consentAnswer: ((Set<GmailCapability>) -> GmailAuthResult)? = null
    var authorizeCalls = ArrayList<Set<GmailCapability>>()
    var revoked = 0
    var inUseSeen = false

    fun connected(vararg capabilities: GmailCapability = arrayOf(GmailCapability.READ)) {
        val grant = GmailGrant(capabilities.map { it.scope })
        authorizer.grant = grant
        connectionState.granted(grant, "heinz@example.org")
    }

    /** An owner turn through the lane; false = not this lane's. */
    fun say(text: String): Boolean = director.handles(text) && director.handle(text)

    override fun acceptOwnerTurn(request: String) { ownerTurn++; chat += "> $request" }
    override fun answer(request: String?, spoken: String, shown: String, private: Boolean) {
        chat += shown
        this.spoken += spoken
        if (private) privateAnswers += shown else persisted += shown
    }
    override fun activity(line: String) { activities += line }
    override fun internetMode() = internet
    override fun cloudBrain() = cloud
    override fun onDeviceModelReady() = localModel && !cloud
    override fun promptBudgetBytes() = 6_000
    override fun connection() = connectionState
    override fun usage() = usageTracker
    override fun board() = boardState
    override fun transport(): GmailHttpTransport = GmailHttpTransport { request ->
        inUseSeen = inUseSeen || usageTracker.inUse(at.heinz.deviceai.connections.ContinuumConnectionKind.GMAIL)
        gmail.execute(request)
    }
    override fun authorizer(): GmailAuthorizer = authorizer
    override fun authorize(capabilities: Set<GmailCapability>, done: (GmailAuthResult) -> Unit) {
        authorizeCalls += capabilities
        val answer = consentAnswer?.invoke(capabilities) ?: run {
            authorizer.grant = authorizer.grant.plus(GmailGrant(capabilities.map { it.scope }))
            GmailAuthResult.Granted(GmailAccessToken("$TOKEN_MARKER-consent"), authorizer.grant)
        }
        done(answer)
    }
    override fun revoke(account: String?, grant: GmailGrant, done: (GmailFailureKind?) -> Unit) { revoked++; done(null) }
    override fun <T> runJob(request: String, label: String, work: (OwnerMailJob) -> T, done: (Result<T>) -> Unit) {
        val job = object : OwnerMailJob {
            override fun step(key: String, label: String, state: StepState) { activities += "STEP $key $label $state" }
            override fun generate(prompt: String, cloud: Boolean): OwnerWorkGeneration { prompts += prompt to cloud; return modelAnswer(prompt) }
            override fun cancelled() = false
        }
        done(runCatching { work(job) })
    }
    override fun contactsReadable() = contactsAllowed
    override fun requestContacts(explanation: String, done: (Boolean) -> Unit) = done(contactsAllowed)
    override fun contacts(): OwnerSendContactSource = object : OwnerSendContactSource {
        override fun phones(): List<OwnerContactPhone> = emptyList()
        override fun mails(): List<OwnerContactMail> = contactRows
    }
    override fun nowMillis() = now.toEpochMilli()
    override fun zone(): ZoneId = ZoneId.of("Europe/Vienna")
    override fun deliverable(title: String, text: String, cloud: Boolean) { deliverables += Triple(title, text, cloud) }
    override fun outcome(outcome: OwnerLaneOutcome) { outcomes += outcome }
    override fun mailOutcome(outcome: MailLaneOutcome) { mailOutcomes += outcome }

    /** Every string the lane produced that is not a network request: chat, speech, log, outcomes, prompts, board. */
    fun everythingSaid(): String = (chat + spoken + activities + outcomes.map { it.toString() } + mailOutcomes.map { it.toString() } +
        prompts.map { it.first } + deliverables.map { it.toString() } + listOfNotNull(boardState.snapshot(sessionId)?.toString()) +
        store.values.values + usageStore.values.values + connectionStore.values.values).joinToString("\n")
}
