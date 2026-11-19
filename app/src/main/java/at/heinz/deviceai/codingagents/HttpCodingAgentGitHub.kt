package at.heinz.deviceai.codingagents

import at.heinz.deviceai.localmodel.OwnerSecret
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * The few read-only questions the app itself asks GitHub's REST API with the owner's token: the default branch for
 * the confirmation, and after a run what really exists (branch, pull request, difference, base branch). The token
 * goes only to [baseUrl] in the `Authorization` header; redirects are not followed, so it never reaches another
 * host. Failures are codes of [CodingAgentFailure]; no response text and no header leaves this class.
 *
 * [baseUrl] is api.github.com in production and a local fake endpoint in the JVM tests.
 */
class HttpCodingAgentGitHub(
    private val baseUrl: String = "https://api.github.com",
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 20_000
) : CodingAgentGitHub {
    private class Response(val status: Int, val body: String, val rateLimited: Boolean)

    override fun repository(repository: String, token: OwnerSecret): CodingAgentGitHub.Repository {
        val json = objectOf(expect(get("/repos/${path(repository)}", token)))
        val branch = json.optString("default_branch").takeIf { it.isNotEmpty() } ?: throw CodingAgentException(CodingAgentFailure.GITHUB_UNEXPECTED)
        // „permissions“ is the account's right on the repository; what the token itself may write shows at the push.
        return CodingAgentGitHub.Repository(branch, json.optJSONObject("permissions")?.optBoolean("push", true) ?: true,
            json.optBoolean("archived", false))
    }

    override fun branchHead(repository: String, branch: String, token: OwnerSecret): String? {
        val response = get("/repos/${path(repository)}/git/ref/heads/${path(branch)}", token)
        if (response.status == 404) return null
        return objectOf(expect(response)).optJSONObject("object")?.optString("sha")?.takeIf { it.isNotEmpty() }
            ?: throw CodingAgentException(CodingAgentFailure.GITHUB_UNEXPECTED)
    }

    override fun pullRequest(repository: String, headBranch: String, token: OwnerSecret): CodingAgentGitHub.PullRequest? {
        val head = URLEncoder.encode(repository.substringBefore('/') + ":" + headBranch, "UTF-8")
        val body = expect(get("/repos/${path(repository)}/pulls?state=all&per_page=5&head=$head", token))
        val pulls = runCatching { JSONArray(body) }.getOrNull() ?: throw CodingAgentException(CodingAgentFailure.GITHUB_UNEXPECTED)
        val pull = pulls.optJSONObject(0) ?: return null
        return CodingAgentGitHub.PullRequest(pull.optInt("number"), pull.optString("html_url"), pull.optBoolean("draft", false),
            pull.optString("state"), !pull.isNull("merged_at") && pull.optString("merged_at").isNotEmpty())
    }

    override fun compare(repository: String, baseSha: String, headSha: String, token: OwnerSecret): CodingAgentGitHub.Comparison {
        val json = objectOf(expect(get("/repos/${path(repository)}/compare/${path(baseSha)}...${path(headSha)}?per_page=1", token)))
        return CodingAgentGitHub.Comparison(json.optInt("ahead_by"), json.optJSONArray("files")?.length() ?: 0)
    }

    private fun path(value: String): String = value.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    private fun objectOf(body: String): JSONObject =
        runCatching { JSONObject(body) }.getOrNull() ?: throw CodingAgentException(CodingAgentFailure.GITHUB_UNEXPECTED)

    private fun expect(response: Response): String {
        if (response.status == 200) return response.body
        throw CodingAgentException(when {
            response.status == 401 -> CodingAgentFailure.GITHUB_TOKEN_REJECTED
            response.status == 429 || (response.status == 403 && response.rateLimited) -> CodingAgentFailure.GITHUB_RATE_LIMITED
            response.status == 403 -> CodingAgentFailure.GITHUB_FORBIDDEN
            response.status == 404 -> CodingAgentFailure.GITHUB_NOT_FOUND
            response.status in setOf(301, 302, 307, 308) -> CodingAgentFailure.GITHUB_MOVED
            response.status in 500..599 -> CodingAgentFailure.GITHUB_UNAVAILABLE
            else -> CodingAgentFailure.GITHUB_UNEXPECTED
        })
    }

    private fun get(pathAndQuery: String, token: OwnerSecret): Response {
        val connection = try {
            URL(baseUrl + pathAndQuery).openConnection() as HttpURLConnection
        } catch (failure: Exception) {
            throw CodingAgentException(CodingAgentFailure.GITHUB_OFFLINE, failure)
        }
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.useCaches = false
            connection.setRequestProperty("Authorization", "Bearer " + token.reveal())
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "continuum-lokal-coding-agents")
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(8_192)
                while (buffer.size() < MAX_BODY_BYTES) {
                    val count = input.read(chunk)
                    if (count < 0) break
                    buffer.write(chunk, 0, count)
                }
                buffer.toString("UTF-8")
            }.orEmpty()
            return Response(status, body, connection.getHeaderField("x-ratelimit-remaining") == "0" ||
                connection.getHeaderField("retry-after") != null)
        } catch (failure: IOException) {
            throw CodingAgentException(CodingAgentFailure.GITHUB_OFFLINE, failure)
        } finally {
            connection.disconnect()
        }
    }

    override fun toString(): String = "HttpCodingAgentGitHub(credentials=redacted)"

    private companion object {
        const val MAX_BODY_BYTES = 2_000_000
    }
}
