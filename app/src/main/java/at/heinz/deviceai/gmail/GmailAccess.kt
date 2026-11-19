package at.heinz.deviceai.gmail

/**
 * The three rights the owner grants separately in Google's own consent screen. One OAuth scope each, the narrowest
 * Google offers for the methods this app calls (Gmail API reference, 19.11.2026):
 *  - [READ] `gmail.readonly`: messages.list/get, threads.get, attachments.get, getProfile.
 *  - [DRAFTS] `gmail.compose`: drafts.create (gmail.send is not enough for drafts).
 *  - [SEND] `gmail.send`: messages.send.
 * Google's compose scope technically also allows sending. This app nevertheless sends only with [SEND] granted AND
 * the owner's confirmation of recipient, subject and full text in the app ([GmailGrant.maySend]).
 */
internal enum class GmailCapability(val scope: String, val label: String, val right: String) {
    READ("https://www.googleapis.com/auth/gmail.readonly", "Lesen", "Mails lesen und durchsuchen"),
    DRAFTS("https://www.googleapis.com/auth/gmail.compose", "Entwürfe", "Entwürfe anlegen"),
    SEND("https://www.googleapis.com/auth/gmail.send", "Senden", "Mails senden – nur nach deiner Bestätigung");

    companion object {
        /** Wider scopes another client of the same project could have been granted; they cover every capability. */
        val FULL_SCOPES = setOf("https://mail.google.com/", "https://www.googleapis.com/auth/gmail.modify")
    }
}

/**
 * What Google reported as granted ([scopes] from `AuthorizationResult.getGrantedScopes`). Only scopes are kept,
 * never a token. [covers] is what the API would accept; [maySend] is the app's own, stricter rule.
 */
internal class GmailGrant(scopes: Collection<String>) {
    val scopes: Set<String> = scopes.map { it.trim() }.filter { it.isNotEmpty() }.toSortedSet()
    private val full: Boolean get() = scopes.any { it in GmailCapability.FULL_SCOPES }

    /** The capability itself was granted (or a full scope that includes it). */
    fun granted(capability: GmailCapability): Boolean = full || capability.scope in scopes

    /** The API accepts the call with these scopes (Gmail API reference: compose also allows send). */
    fun covers(capability: GmailCapability): Boolean = when (capability) {
        GmailCapability.READ -> full || GmailCapability.READ.scope in scopes
        GmailCapability.DRAFTS -> full || GmailCapability.DRAFTS.scope in scopes
        GmailCapability.SEND -> full || GmailCapability.SEND.scope in scopes || GmailCapability.DRAFTS.scope in scopes
    }

    /** The app's rule: sending needs the separately granted send right – compose alone is not taken as consent to send. */
    val maySend: Boolean get() = granted(GmailCapability.SEND)

    val capabilities: Set<GmailCapability> get() = GmailCapability.entries.filter(::granted).toSet()

    fun plus(other: GmailGrant): GmailGrant = GmailGrant(scopes + other.scopes)

    override fun equals(other: Any?): Boolean = other is GmailGrant && other.scopes == scopes
    override fun hashCode(): Int = scopes.hashCode()
    override fun toString(): String = "GmailGrant(" + capabilities.joinToString { it.name } + ")"

    companion object {
        val NONE = GmailGrant(emptyList())
        fun of(vararg capabilities: GmailCapability) = GmailGrant(capabilities.map { it.scope })
    }
}

/**
 * A short-lived OAuth access token, held in memory only. It is never persisted, never logged and never part of a
 * prompt: [toString] is redacted, and the value is reachable only through [authorizationHeader], which only the
 * HTTP transport calls. Google does not report an expiry; tokens live about an hour.
 */
internal class GmailAccessToken(private val value: String) {
    init { require(value.isNotBlank() && value.none { it.isISOControl() || it == ' ' }) { "invalid token" } }

    fun authorizationHeader(): String = "Bearer $value"

    /** Same token without revealing it (for clearing exactly the cached one). */
    fun sameAs(other: GmailAccessToken?): Boolean = other != null && other.value == value

    /** For the Android adapter's clearToken call only. */
    internal fun reveal(): String = value

    override fun toString(): String = "GmailAccessToken(***)"
    override fun equals(other: Any?): Boolean = other is GmailAccessToken && other.value == value
    override fun hashCode(): Int = value.hashCode()
}

/** Why something with Gmail did not work, as the owner should hear it. */
internal enum class GmailFailureKind {
    /** No Gmail connection was made in this app. */
    NOT_CONNECTED,
    /** A right is missing or Google wants the owner to confirm again. */
    PERMISSION_REQUIRED,
    /** The owner (or Google) took the access back. */
    REVOKED,
    /** No network or the connection broke off. */
    INTERRUPTED,
    /** Google's limit for now; tried again with backoff. */
    QUOTA,
    /** Google's servers answered with an error several times. */
    SERVER,
    /** Google does not know this app's Android client (package name + SHA-1) – debug/QA build or setup missing. */
    CLIENT_NOT_REGISTERED,
    /** The Gmail API is not switched on in the owner's Google Cloud project. */
    API_DISABLED,
    /** Google Play services are missing or too old on this device. */
    PLAY_SERVICES,
    /** The owner closed Google's screen. */
    CANCELLED,
    NOT_FOUND,
    /** Google's answer was not what the API documents. */
    INVALID_RESPONSE,
    /** The internet setting of this app is OFF. */
    INTERNET_OFF,
    UNKNOWN;

    /** The connection state a failure leaves behind; null = the connection itself is fine. */
    val leaves: at.heinz.deviceai.connections.ContinuumConnectionState? get() = when (this) {
        NOT_CONNECTED, CLIENT_NOT_REGISTERED -> at.heinz.deviceai.connections.ContinuumConnectionState.NOT_CONNECTED
        PERMISSION_REQUIRED -> at.heinz.deviceai.connections.ContinuumConnectionState.PERMISSION_REQUIRED
        REVOKED -> at.heinz.deviceai.connections.ContinuumConnectionState.REVOKED
        INTERRUPTED, SERVER, QUOTA, PLAY_SERVICES, API_DISABLED -> at.heinz.deviceai.connections.ContinuumConnectionState.INTERRUPTED
        CANCELLED, NOT_FOUND, INVALID_RESPONSE, INTERNET_OFF, UNKNOWN -> null
    }
}

/**
 * A Gmail failure. Its message is one of the fixed German sentences of [GmailText] – never a token, a header, a
 * response body or mail content.
 */
internal class GmailException(val kind: GmailFailureKind, val capability: GmailCapability? = null, val httpCode: Int? = null) :
    Exception(GmailText.failure(kind, capability)) {
    override fun toString(): String = "GmailException(" + kind.name + (capability?.let { ", " + it.name } ?: "") +
        (httpCode?.let { ", HTTP $it" } ?: "") + ")"
}

/** The outcome of asking Google for access. */
internal sealed interface GmailAuthResult {
    class Granted(val token: GmailAccessToken, val grant: GmailGrant) : GmailAuthResult {
        override fun toString(): String = "Granted($grant)"
    }
    /** Google must ask the owner (first consent, a new right, or after a revocation). [resolution]: the platform's PendingIntent. */
    class NeedsConsent(val resolution: Any) : GmailAuthResult {
        override fun toString(): String = "NeedsConsent"
    }
    data class Failed(val kind: GmailFailureKind, val statusCode: Int? = null) : GmailAuthResult
}

/**
 * Google's authorization as the core sees it. The Android adapter implements it with
 * `Identity.getAuthorizationClient(...).authorize(...)`; no client id and no secret are in the app – Google matches
 * the Android OAuth client by package name and certificate SHA-1.
 */
internal interface GmailAuthorizer {
    /**
     * Blocking, never on the UI thread, never shows anything: the token for [capabilities] when they are granted;
     * [GmailAuthResult.NeedsConsent] when Google would have to ask the owner.
     */
    fun silent(capabilities: Set<GmailCapability>): GmailAuthResult

    /** Blocking: forget [token] in Google's cache (after a 401), so the next [silent] fetches a fresh one. */
    fun invalidate(token: GmailAccessToken)
}

/**
 * The token of one job: asked from the authorizer, reused while it works, replaced exactly once after a 401
 * ([GmailApi]). The connection store says whether a refusal is „never granted“ or „taken back“.
 */
internal class GmailTokenSource(private val authorizer: GmailAuthorizer,
                                /** The right was granted before (the connection store): a refusal now means it was taken back. */
                                private val hadGranted: (GmailCapability) -> Boolean,
                                private val onGrant: (GmailGrant) -> Unit = {}) {
    private var current: GmailAccessToken? = null
    private var currentGrant: GmailGrant = GmailGrant.NONE

    @Synchronized fun token(capability: GmailCapability): GmailAccessToken {
        current?.takeIf { currentGrant.covers(capability) }?.let { return it }
        return when (val result = authorizer.silent(setOf(capability))) {
            is GmailAuthResult.Granted -> {
                if (!result.grant.covers(capability)) throw GmailException(GmailFailureKind.PERMISSION_REQUIRED, capability)
                current = result.token
                currentGrant = result.grant
                onGrant(result.grant)
                result.token
            }
            is GmailAuthResult.NeedsConsent -> throw GmailException(
                if (hadGranted(capability)) GmailFailureKind.REVOKED else GmailFailureKind.PERMISSION_REQUIRED,
                capability)
            is GmailAuthResult.Failed -> throw GmailException(result.kind, capability)
        }
    }

    /** After a 401: Google's cached token is cleared and the next [token] asks again (silently). */
    @Synchronized fun invalidate(token: GmailAccessToken) {
        runCatching { authorizer.invalidate(token) }
        if (token.sameAs(current)) { current = null; currentGrant = GmailGrant.NONE }
    }

    override fun toString(): String = "GmailTokenSource(" + (if (current == null) "leer" else "Token im Speicher") + ")"
}
