package at.heinz.deviceai.gmail

import at.heinz.deviceai.connections.ContinuumConnectionState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/** Scopes, token handling and every error mapping of the Gmail API client, against a fake transport. */
class GmailCoreTest {
    // ── scopes and grants ─────────────────────────────────────────────────────────────────────────────────

    @Test fun threeSeparateRightsEachWithItsNarrowestScope() {
        assertEquals("https://www.googleapis.com/auth/gmail.readonly", GmailCapability.READ.scope)
        assertEquals("https://www.googleapis.com/auth/gmail.compose", GmailCapability.DRAFTS.scope)
        assertEquals("https://www.googleapis.com/auth/gmail.send", GmailCapability.SEND.scope)
        val read = GmailGrant.of(GmailCapability.READ)
        assertTrue(read.granted(GmailCapability.READ))
        assertFalse(read.granted(GmailCapability.DRAFTS))
        assertFalse(read.covers(GmailCapability.SEND))
        assertFalse(read.maySend)
    }

    @Test fun composeTechnicallyCoversSendingButTheAppDoesNotSendWithoutTheSendRight() {
        val compose = GmailGrant.of(GmailCapability.DRAFTS)
        assertTrue(compose.covers(GmailCapability.SEND))
        assertFalse(compose.granted(GmailCapability.SEND))
        assertFalse(compose.maySend)
        assertTrue(GmailGrant.of(GmailCapability.SEND).maySend)
        // gmail.send alone does not allow drafts (drafts.create needs compose).
        assertFalse(GmailGrant.of(GmailCapability.SEND).covers(GmailCapability.DRAFTS))
    }

    @Test fun aFullScopeCoversEveryCapabilityAndUnknownScopesNone() {
        val full = GmailGrant(listOf("https://mail.google.com/"))
        assertEquals(GmailCapability.entries.toSet(), full.capabilities)
        assertTrue(GmailGrant(listOf("openid", "email")).capabilities.isEmpty())
        assertEquals(GmailGrant.of(GmailCapability.READ, GmailCapability.SEND), GmailGrant.of(GmailCapability.READ).plus(GmailGrant.of(GmailCapability.SEND)))
    }

    @Test fun theTokenNeverShowsItselfInTextsAndExceptions() {
        val token = GmailAccessToken(TOKEN_MARKER)
        assertFalse(token.toString().contains(TOKEN_MARKER))
        assertEquals("Bearer $TOKEN_MARKER", token.authorizationHeader())
        val request = GmailHttpRequest("GET", GmailApi.BASE + "users/me/profile", mapOf("Authorization" to token.authorizationHeader()), null)
        assertFalse(request.toString().contains(TOKEN_MARKER))
        assertFalse(GmailAuthResult.Granted(token, GmailGrant.NONE).toString().contains(TOKEN_MARKER))
        val source = GmailTokenSource(FakeAuthorizer(), { false })
        source.token(GmailCapability.READ)
        assertFalse(source.toString().contains(TOKEN_MARKER))
        GmailFailureKind.entries.forEach { kind ->
            val error = GmailException(kind, GmailCapability.READ, 401)
            assertFalse(error.message!!.contains(TOKEN_MARKER))
            assertFalse(error.toString().contains("Bearer"))
        }
    }

    // ── error mapping ─────────────────────────────────────────────────────────────────────────────────────

    private fun api(gmail: FakeGmail, authorizer: FakeAuthorizer = FakeAuthorizer(), slept: MutableList<Long> = ArrayList(),
                    had: (GmailCapability) -> Boolean = { true }) =
        GmailApi(gmail, GmailTokenSource(authorizer, had), sleep = { slept += it })

    private fun expect(kind: GmailFailureKind, block: () -> Unit) {
        try { block(); fail("expected $kind") } catch (error: GmailException) { assertEquals(kind, error.kind) }
    }

    @Test fun a401ClearsTheTokenAuthorisesSilentlyOnceAndRetriesOnce() {
        val gmail = FakeGmail()
        val authorizer = FakeAuthorizer()
        gmail.failures["users/me/profile"] = ArrayDeque(listOf(401))
        assertEquals("heinz@example.org", api(gmail, authorizer).profileAddress(GmailCapability.READ))
        assertEquals(1, authorizer.invalidated.size)
        assertEquals(2, authorizer.issued)
        assertEquals(2, gmail.requests.size)
        // The retried request carries the new token, the first the old one.
        assertTrue(gmail.requests[0].headers.getValue("Authorization").endsWith("-1"))
        assertTrue(gmail.requests[1].headers.getValue("Authorization").endsWith("-2"))
    }

    @Test fun aSecond401IsReportedAsPermissionRequiredWithoutAThirdAttempt() {
        val gmail = FakeGmail()
        gmail.failures["users/me/profile"] = ArrayDeque(listOf(401, 401, 401))
        expect(GmailFailureKind.PERMISSION_REQUIRED) { api(gmail).profileAddress(GmailCapability.READ) }
        assertEquals(2, gmail.requests.size)
    }

    @Test fun insufficientScopeIsPermissionRequiredForThatRight() {
        val gmail = FakeGmail()
        gmail.failures["users/me/drafts"] = ArrayDeque(listOf(403))
        try { api(gmail, FakeAuthorizer(GmailGrant.of(GmailCapability.DRAFTS))).createDraft("x", null); fail() }
        catch (error: GmailException) {
            assertEquals(GmailFailureKind.PERMISSION_REQUIRED, error.kind)
            assertEquals(GmailCapability.DRAFTS, error.capability)
            assertEquals(ContinuumConnectionState.PERMISSION_REQUIRED, error.kind.leaves)
        }
    }

    @Test fun aDisabledApiAndARevokedGrantAreNamed() {
        val disabled = object : GmailHttpTransport {
            override fun execute(request: GmailHttpRequest) = GmailHttpResponse(403,
                "{\"error\":{\"code\":403,\"status\":\"PERMISSION_DENIED\",\"details\":[{\"reason\":\"SERVICE_DISABLED\"}]}}")
        }
        expect(GmailFailureKind.API_DISABLED) { GmailApi(disabled, GmailTokenSource(FakeAuthorizer(), { true })).profileAddress(GmailCapability.READ) }
        val revoked = GmailHttpTransport { GmailHttpResponse(400, "{\"error\":\"invalid_grant\",\"error_description\":\"Token has been expired or revoked.\"}") }
        expect(GmailFailureKind.REVOKED) { GmailApi(revoked, GmailTokenSource(FakeAuthorizer(), { true })).profileAddress(GmailCapability.READ) }
        assertEquals(ContinuumConnectionState.REVOKED, GmailFailureKind.REVOKED.leaves)
    }

    @Test fun consentNeededAfterAGrantMeansRevokedBeforeAGrantPermissionRequired() {
        val authorizer = FakeAuthorizer().apply { consentNeeded = true }
        expect(GmailFailureKind.REVOKED) { api(FakeGmail(), authorizer, had = { true }).profileAddress(GmailCapability.READ) }
        expect(GmailFailureKind.PERMISSION_REQUIRED) { api(FakeGmail(), authorizer, had = { false }).profileAddress(GmailCapability.READ) }
    }

    @Test fun aRequestThatDidNotCompleteIsInterruptedAndNeverRepeated() {
        val gmail = FakeGmail().apply { offline = true }
        expect(GmailFailureKind.INTERRUPTED) { api(gmail).listMessages("in:inbox", 5) }
        assertEquals(1, gmail.requests.size)
        assertEquals(ContinuumConnectionState.INTERRUPTED, GmailFailureKind.INTERRUPTED.leaves)
    }

    @Test fun quotaAndServerErrorsAreRetriedTwiceWithGrowingPauseThenReported() {
        val gmail = FakeGmail()
        val slept = ArrayList<Long>()
        gmail.failures["users/me/messages"] = ArrayDeque(listOf(503, 429))
        assertTrue(api(gmail, slept = slept).listMessages("in:inbox", 5).isEmpty())
        assertEquals(listOf(500L, 2_000L), slept)
        gmail.failures["users/me/messages"] = ArrayDeque(listOf(500, 500, 500, 500))
        expect(GmailFailureKind.SERVER) { api(gmail).listMessages("in:inbox", 5) }
        gmail.failures["users/me/messages"] = ArrayDeque(listOf(429, 429, 429))
        expect(GmailFailureKind.QUOTA) { api(gmail).listMessages("in:inbox", 5) }
    }

    @Test fun aSendIsNeverRepeatedAfterAServerError() {
        val gmail = FakeGmail()
        gmail.failures["users/me/messages/send"] = ArrayDeque(listOf(500))
        expect(GmailFailureKind.SERVER) { api(gmail, FakeAuthorizer(GmailGrant.of(GmailCapability.SEND))).send("cmF3", null) }
        assertEquals(1, gmail.sends)
    }

    @Test fun aMissingMailIsNotFoundAndIdsAreChecked() {
        expect(GmailFailureKind.NOT_FOUND) { api(FakeGmail()).messageFull("missing1") }
        try { api(FakeGmail()).messageFull("../users/other"); fail() } catch (_: IllegalArgumentException) {}
    }

    @Test fun theListAsksForIdsWithTheSearchInGmailSyntax() {
        val gmail = FakeGmail()
        gmail.add(Mails.message("m1", "Anna <anna@example.org>", "Hallo", 1_000L))
        val ids = api(gmail).listMessages("in:inbox from:\"Anna\"", 5)
        assertEquals(listOf("m1" to "tm1"), ids)
        val url = gmail.requests.single().url
        assertTrue(url, url.startsWith("https://gmail.googleapis.com/gmail/v1/users/me/messages?q=in%3Ainbox%20from%3A%22Anna%22&maxResults=5"))
    }

    @Test fun aClientUnknownToGoogleIsExplainedWithTheOriginalSignature() {
        val text = GmailText.failure(GmailFailureKind.CLIENT_NOT_REGISTERED, null)
        assertTrue(text.contains("original signierten"))
        assertTrue(text.contains("at.heinz.deviceai.continuumlocal"))
        assertTrue(text.contains("SHA-1"))
        assertEquals(ContinuumConnectionState.NOT_CONNECTED, GmailFailureKind.CLIENT_NOT_REGISTERED.leaves)
    }

    @Test fun theConnectionStoreKeepsScopesAndAccountButNeverAToken() {
        val store = MapStore()
        val connection = GmailConnection(store)
        assertFalse(connection.connected)
        connection.granted(GmailGrant.of(GmailCapability.READ), "Heinz@Example.org")
        connection.granted(GmailGrant.of(GmailCapability.DRAFTS))
        assertTrue(connection.connected)
        assertEquals("heinz@example.org", connection.account)
        assertEquals(setOf(GmailCapability.READ, GmailCapability.DRAFTS), connection.grant.capabilities)
        assertFalse(connection.cloudContent)
        connection.ended(GmailFailureKind.REVOKED)
        assertEquals(ContinuumConnectionState.REVOKED, connection.view().failure)
        connection.ended(GmailFailureKind.NOT_FOUND)
        assertEquals(GmailFailureKind.REVOKED, connection.failure)
        connection.ended(null)
        assertNull(connection.failure)
        connection.cloudContent = true
        connection.clear()
        assertFalse(connection.connected)
        assertNull(connection.account)
        assertTrue(connection.cloudContent)
        assertFalse(store.values.values.any { it.contains("ya29") || it.contains("Bearer") })
    }

    @Test fun metadataAndFullFormatsAreRequestedAsDocumented() {
        val gmail = FakeGmail()
        gmail.add(Mails.message("m1", "Anna <anna@example.org>", "Hallo", 1_000L, body = "Text"))
        api(gmail).messageMetadata("m1")
        api(gmail).messageFull("m1")
        assertTrue(gmail.requests[0].url.contains("format=metadata"))
        assertTrue(gmail.requests[0].url.contains("metadataHeaders=From"))
        assertTrue(gmail.requests[1].url.contains("format=full"))
        assertNotNull(JSONObject(gmail.execute(gmail.requests[1]).body).optJSONObject("payload"))
    }

    @Test fun theTransportBoundaryIsAnIoException() {
        val broken = GmailHttpTransport { throw IOException("reset") }
        expect(GmailFailureKind.INTERRUPTED) { GmailApi(broken, GmailTokenSource(FakeAuthorizer(), { true })).thread("t1") }
    }
}
