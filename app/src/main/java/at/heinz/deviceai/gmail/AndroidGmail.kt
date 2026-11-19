package at.heinz.deviceai.gmail

import android.accounts.Account
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import at.heinz.deviceai.connections.ContinuumConnectionStore
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.net.ssl.HttpsURLConnection

/** App-private preferences; nothing here is synced or exported, and no token is ever written. */
internal class AndroidConnectionStore(context: Context, name: String) : ContinuumConnectionStore {
    private val preferences = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    override fun get(key: String): String? = preferences.getString(key, null)
    override fun put(key: String, value: String?) {
        preferences.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }
}

/**
 * Google's recommended Android authorization (`Identity.getAuthorizationClient`, play-services-auth). No client id
 * and no secret are in the app: Google matches the Android OAuth client of the owner's Cloud project by package name
 * and the SHA-1 of the signing certificate – so only the original-signed app can connect; a debug or QA build gets
 * DEVELOPER_ERROR (10), which is reported as [GmailFailureKind.CLIENT_NOT_REGISTERED].
 *
 * Tokens stay in memory: Google's own cache keeps them, this class only hands the current one to the transport.
 * Nothing here logs; a failure is reported as a kind and a status code, never with Google's message text.
 */
internal class AndroidGmailAuthorizer(context: Context, private val account: () -> String?) : GmailAuthorizer {
    private val app = context.applicationContext
    private val client get() = Identity.getAuthorizationClient(app)

    fun request(capabilities: Set<GmailCapability>): AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(capabilities.sortedBy { it.ordinal }.map { Scope(it.scope) })
        .apply { account()?.takeIf { GmailMime.validAddress(it) }?.let { setAccount(Account(it, GOOGLE_ACCOUNT_TYPE)) } }
        .build()

    /** Blocking; called inside a job, never on the UI thread. */
    override fun silent(capabilities: Set<GmailCapability>): GmailAuthResult {
        playServicesProblem(app)?.let { return it }
        return try {
            granted(Tasks.await(client.authorize(request(capabilities)), AUTH_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        } catch (error: ExecutionException) {
            failed(error.cause ?: error)
        } catch (_: TimeoutException) {
            GmailAuthResult.Failed(GmailFailureKind.INTERRUPTED)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            GmailAuthResult.Failed(GmailFailureKind.CANCELLED)
        }
    }

    override fun invalidate(token: GmailAccessToken) {
        runCatching {
            Tasks.await(client.clearToken(ClearTokenRequest.builder().setToken(token.reveal()).build()), AUTH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
    }

    /** Revokes every scope this app holds for [address]; [done] on the main thread with null or the failure kind. */
    fun revoke(address: String?, grant: GmailGrant, done: (GmailFailureKind?) -> Unit) {
        if (address == null || grant.scopes.isEmpty()) { done(GmailFailureKind.NOT_CONNECTED); return }
        val request = RevokeAccessRequest.builder().setAccount(Account(address, GOOGLE_ACCOUNT_TYPE))
            .setScopes(grant.scopes.map { Scope(it) }).build()
        runCatching { client.revokeAccess(request) }.fold({ task ->
            task.addOnSuccessListener { done(null) }.addOnFailureListener { done((failed(it) as GmailAuthResult.Failed).kind) }
        }, { done((failed(it) as GmailAuthResult.Failed).kind) })
    }

    companion object {
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
        const val AUTH_TIMEOUT_SECONDS = 30L

        fun granted(result: AuthorizationResult): GmailAuthResult {
            if (result.hasResolution()) return result.pendingIntent?.let { GmailAuthResult.NeedsConsent(it) }
                ?: GmailAuthResult.Failed(GmailFailureKind.UNKNOWN)
            val token = result.accessToken?.takeIf { it.isNotBlank() } ?: return GmailAuthResult.Failed(GmailFailureKind.INVALID_RESPONSE)
            return GmailAuthResult.Granted(GmailAccessToken(token), GmailGrant(result.grantedScopes.orEmpty()))
        }

        /** The status codes Google documents for these calls, in plain kinds. */
        fun failed(error: Throwable): GmailAuthResult {
            val api = error as? ApiException ?: return GmailAuthResult.Failed(
                if (error is IOException) GmailFailureKind.INTERRUPTED else GmailFailureKind.UNKNOWN)
            return GmailAuthResult.Failed(kind(api.statusCode, api.message), api.statusCode)
        }

        fun kind(code: Int, message: String?): GmailFailureKind = when {
            code == CommonStatusCodes.DEVELOPER_ERROR || message?.contains("UNREGISTERED_ON_API_CONSOLE") == true ->
                GmailFailureKind.CLIENT_NOT_REGISTERED
            code == CommonStatusCodes.NETWORK_ERROR || code == CommonStatusCodes.TIMEOUT -> GmailFailureKind.INTERRUPTED
            code == CommonStatusCodes.CANCELED || code == SIGN_IN_CANCELLED -> GmailFailureKind.CANCELLED
            code == CommonStatusCodes.SIGN_IN_REQUIRED -> GmailFailureKind.PERMISSION_REQUIRED
            code == CommonStatusCodes.API_NOT_CONNECTED || code == ConnectionResult.SERVICE_MISSING ||
                code == ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED || code == ConnectionResult.SERVICE_DISABLED ||
                code == ConnectionResult.SERVICE_INVALID -> GmailFailureKind.PLAY_SERVICES
            else -> GmailFailureKind.UNKNOWN
        }

        /** GoogleSignInStatusCodes.SIGN_IN_CANCELLED. */
        const val SIGN_IN_CANCELLED = 12501

        fun playServicesProblem(context: Context): GmailAuthResult.Failed? {
            val status = runCatching { GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) }
                .getOrDefault(ConnectionResult.SERVICE_MISSING)
            return if (status == ConnectionResult.SUCCESS) null else GmailAuthResult.Failed(GmailFailureKind.PLAY_SERVICES, status)
        }
    }
}

/**
 * The interactive part: Google's own consent screen, launched from the Activity with startIntentSenderForResult
 * (the Activity is a plain android.app.Activity; its onActivityResult delegates to [onActivityResult]).
 */
internal class AndroidGmailConsent(private val activity: Activity, private val authorizer: AndroidGmailAuthorizer) {
    private var pending: ((GmailAuthResult) -> Unit)? = null

    /** [done] on the main thread: Granted, or Failed (CANCELLED when the owner closed Google's screen). */
    fun begin(capabilities: Set<GmailCapability>, done: (GmailAuthResult) -> Unit) {
        AndroidGmailAuthorizer.playServicesProblem(activity)?.let { done(it); return }
        if (pending != null) { done(GmailAuthResult.Failed(GmailFailureKind.CANCELLED)); return }
        val task = runCatching { Identity.getAuthorizationClient(activity).authorize(authorizer.request(capabilities)) }
            .getOrElse { done(AndroidGmailAuthorizer.failed(it)); return }
        task.addOnSuccessListener { result ->
            when (val mapped = AndroidGmailAuthorizer.granted(result)) {
                is GmailAuthResult.NeedsConsent -> {
                    val intent = mapped.resolution as PendingIntent
                    pending = done
                    try {
                        activity.startIntentSenderForResult(intent.intentSender, REQUEST_CODE, null, 0, 0, 0)
                    } catch (_: Exception) {
                        pending = null
                        done(GmailAuthResult.Failed(GmailFailureKind.UNKNOWN))
                    }
                }
                else -> done(mapped)
            }
        }.addOnFailureListener { done(AndroidGmailAuthorizer.failed(it)) }
    }

    /** true = [requestCode] was Google's consent screen (and [pending] was answered). */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_CODE) return false
        val done = pending
        pending = null
        val result = if (resultCode != Activity.RESULT_OK) GmailAuthResult.Failed(GmailFailureKind.CANCELLED) else try {
            AndroidGmailAuthorizer.granted(Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data))
                .let { if (it is GmailAuthResult.NeedsConsent) GmailAuthResult.Failed(GmailFailureKind.PERMISSION_REQUIRED) else it }
        } catch (error: ApiException) {
            AndroidGmailAuthorizer.failed(error)
        }
        done?.invoke(result)
        return true
    }

    companion object { const val REQUEST_CODE = 2284 }
}

/** HTTPS through HttpURLConnection (no new library). Bounded read; the response body never leaves the Gmail core. */
internal class AndroidGmailTransport : GmailHttpTransport {
    override fun execute(request: GmailHttpRequest): GmailHttpResponse {
        val url = URL(request.url)
        require(url.protocol == "https" && url.host == "gmail.googleapis.com") { "only the Gmail API" }
        val connection = url.openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = request.method
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            request.headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            request.body?.let { body ->
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            val stream = if (code >= HttpURLConnection.HTTP_BAD_REQUEST) connection.errorStream else connection.inputStream
            val text = stream?.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(16_384)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_BYTES) throw IOException("Antwort zu groß")
                    out.write(buffer, 0, read)
                }
                out.toString("UTF-8")
            }.orEmpty()
            return GmailHttpResponse(code, text)
        } finally {
            connection.disconnect()
        }
    }

    companion object { const val MAX_BYTES = 4_000_000 }
}
