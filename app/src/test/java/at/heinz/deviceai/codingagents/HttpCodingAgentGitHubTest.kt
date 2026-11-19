package at.heinz.deviceai.codingagents

import at.heinz.deviceai.localmodel.OwnerSecret
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The app's own read-only questions to GitHub against a local stand-in for api.github.com (REST shapes of the
 * endpoints „Get a repository“, „Get a reference“, „List pull requests“ and „Compare two commits“). No request
 * leaves 127.0.0.1; what the real GitHub answers for the owner's token is NOT_RUN.
 */
class HttpCodingAgentGitHubTest {
    private class Reply(val status: Int, val body: String, val headers: Map<String, String> = emptyMap())

    private val seen = CopyOnWriteArrayList<Pair<String, Map<String, String>>>()
    private val replies = ConcurrentHashMap<String, Reply>()
    private val server = LoopbackHttpServer { exchange ->
        seen += (exchange.method + " " + exchange.target) to exchange.headers
        val reply = replies[exchange.path] ?: Reply(404, "{\"message\":\"Not Found\"}")
        exchange.reply(reply.status, reply.body, reply.headers)
    }
    private val github = HttpCodingAgentGitHub(server.baseUrl, 3_000, 5_000)
    private val token = OwnerSecret(FAKE_GITHUB_TOKEN)

    @After fun tearDown() = server.close()

    private fun code(block: () -> Unit): String = try { block(); "NO_FAILURE" } catch (failure: CodingAgentException) { failure.code }

    @Test fun theDefaultBranchAndTheHeadsAreReadWithTheTokenInTheHeaderOnly() {
        replies["/repos/Ullmann27/device-ai"] = Reply(200, """{"default_branch":"main","archived":false,"permissions":{"push":true,"pull":true}}""")
        replies["/repos/Ullmann27/device-ai/git/ref/heads/main"] = Reply(200, """{"ref":"refs/heads/main","object":{"sha":"a1b2c3d4","type":"commit"}}""")
        replies["/repos/Ullmann27/device-ai/git/ref/heads/continuum-agent/run0001"] =
            Reply(200, """{"ref":"refs/heads/continuum-agent/run0001","object":{"sha":"f00dfeed","type":"commit"}}""")
        val repository = github.repository("Ullmann27/device-ai", token)
        assertEquals("main", repository.defaultBranch)
        assertTrue(repository.canPush)
        assertFalse(repository.archived)
        assertEquals("a1b2c3d4", github.branchHead("Ullmann27/device-ai", "main", token))
        assertEquals("f00dfeed", github.branchHead("Ullmann27/device-ai", "continuum-agent/run0001", token))
        // A branch that does not exist is a fact, not a failure.
        assertNull(github.branchHead("Ullmann27/device-ai", "continuum-agent/gibt-es-nicht", token))

        seen.forEach { (line, headers) ->
            assertTrue(line, line.startsWith("GET /repos/Ullmann27/device-ai"))
            assertEquals("Bearer $FAKE_GITHUB_TOKEN", headers["authorization"])
            assertEquals("application/vnd.github+json", headers["accept"])
            assertFalse("token in the URL: $line", line.contains(FAKE_GITHUB_TOKEN))
        }
        assertEquals("HttpCodingAgentGitHub(credentials=redacted)", github.toString())
    }

    @Test fun pullRequestAndDifferenceAreReadAsTheyAre() {
        replies["/repos/Ullmann27/device-ai/pulls"] = Reply(200, """[{"number":912,"html_url":"https://github.com/Ullmann27/device-ai/pull/912",
            "draft":true,"state":"open","merged_at":null}]""")
        replies["/repos/Ullmann27/device-ai/compare/a1b2...f00d"] = Reply(200, """{"ahead_by":2,"behind_by":0,"files":[{"filename":"a"},{"filename":"b"},{"filename":"c"}]}""")
        val pull = github.pullRequest("Ullmann27/device-ai", "continuum-agent/run0001", token)!!
        assertEquals(listOf(912, "https://github.com/Ullmann27/device-ai/pull/912", true, "open", false),
            listOf(pull.number, pull.url, pull.draft, pull.state, pull.merged))
        // Only pull requests of exactly this branch of the owner's repository are asked for.
        assertTrue(seen.last().first, seen.last().first.contains("head=Ullmann27%3Acontinuum-agent%2Frun0001") && seen.last().first.contains("state=all"))
        val comparison = github.compare("Ullmann27/device-ai", "a1b2", "f00d", token)
        assertEquals(2 to 3, comparison.aheadBy to comparison.changedFiles)

        replies["/repos/Ullmann27/device-ai/pulls"] = Reply(200, """[{"number":7,"html_url":"https://github.com/Ullmann27/device-ai/pull/7",
            "draft":false,"state":"closed","merged_at":"2026-11-19T10:00:00Z"}]""")
        val merged = github.pullRequest("Ullmann27/device-ai", "continuum-agent/run0001", token)!!
        assertTrue(merged.merged && !merged.draft)
        replies["/repos/Ullmann27/device-ai/pulls"] = Reply(200, "[]")
        assertNull(github.pullRequest("Ullmann27/device-ai", "continuum-agent/run0001", token))
    }

    @Test fun everyRefusalOfGitHubIsAStableCodeAndCarriesNoText() {
        val path = "/repos/Ullmann27/device-ai"
        mapOf(Reply(401, """{"message":"Bad credentials $FAKE_GITHUB_TOKEN"}""") to CodingAgentFailure.GITHUB_TOKEN_REJECTED,
            Reply(403, """{"message":"Resource not accessible by personal access token"}""") to CodingAgentFailure.GITHUB_FORBIDDEN,
            Reply(403, """{"message":"API rate limit exceeded"}""", mapOf("x-ratelimit-remaining" to "0")) to CodingAgentFailure.GITHUB_RATE_LIMITED,
            Reply(429, """{"message":"secondary rate limit"}""", mapOf("retry-after" to "60")) to CodingAgentFailure.GITHUB_RATE_LIMITED,
            Reply(404, """{"message":"Not Found"}""") to CodingAgentFailure.GITHUB_NOT_FOUND,
            Reply(301, """{"message":"Moved Permanently"}""", mapOf("Location" to "http://127.0.0.1:1/repositories/1")) to CodingAgentFailure.GITHUB_MOVED,
            Reply(503, "nicht erreichbar") to CodingAgentFailure.GITHUB_UNAVAILABLE,
            Reply(200, "kein json") to CodingAgentFailure.GITHUB_UNEXPECTED,
            Reply(200, "{}") to CodingAgentFailure.GITHUB_UNEXPECTED).forEach { (reply, expected) ->
            replies[path] = reply
            val failure = try { github.repository("Ullmann27/device-ai", token); null } catch (caught: CodingAgentException) { caught }
            assertEquals("${reply.status} ${reply.body}", expected, failure?.code)
            assertFalse(failure.toString().contains(FAKE_GITHUB_TOKEN) || failure!!.message.orEmpty().contains("Bad credentials"))
        }
        // One request per question: nothing is retried, and the redirect was reported instead of followed – the token
        // is never sent to another host.
        assertEquals(9, seen.size)
        val closedPort = java.net.ServerSocket(0).use { it.localPort }
        assertEquals(CodingAgentFailure.GITHUB_OFFLINE, code { HttpCodingAgentGitHub("http://127.0.0.1:$closedPort", 1_000, 1_000).repository("a/b", token) })
    }

    @Test fun aRepositoryTheAccountCannotPushToOrThatIsArchivedIsReportedAsSuch() {
        replies["/repos/Ullmann27/device-ai"] = Reply(200, """{"default_branch":"trunk","archived":true,"permissions":{"push":false}}""")
        val repository = github.repository("Ullmann27/device-ai", token)
        assertEquals("trunk", repository.defaultBranch)
        assertFalse(repository.canPush)
        assertTrue(repository.archived)
    }
}
