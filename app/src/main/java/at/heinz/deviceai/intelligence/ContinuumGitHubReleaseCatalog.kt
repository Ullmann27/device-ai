package at.heinz.deviceai.intelligence

import org.json.JSONObject

/**
 * Parses the owner's public GitHub "latest release" API response into the same bounded
 * release descriptor used by the existing signed updater. It performs no network access.
 */
internal object ContinuumGitHubReleaseCatalog {
    const val API_HOST = "api.github.com"
    const val DOWNLOAD_HOST = "github.com"
    const val API_PATH = "/repos/Ullmann27/device-ai/releases/latest"
    const val API_URL = "https://" + API_HOST + API_PATH
    const val MAX_JSON_BYTES = 65_536

    private val tag = Regex("^continuum-local-code([0-9]{2,4})-([a-f0-9]{40})$")
    private val apk = Regex("^Continuum-Lokal-([a-f0-9]{64})\\.apk$")

    fun parseLatest(json: String): ContinuumOwnerUpdateRelease {
        try {
            require(json.toByteArray(Charsets.UTF_8).size in 1..MAX_JSON_BYTES) {
                "GitHub-Release-Metadaten zu groß"
            }
            val root = JSONObject(json)
            require(root.get("draft") is Boolean && !root.getBoolean("draft")) {
                "GitHub-Release ist noch Entwurf"
            }
            require(root.get("prerelease") is Boolean && !root.getBoolean("prerelease")) {
                "GitHub-Release ist nur Vorabversion"
            }
            val tagMatch = tag.matchEntire(root.getString("tag_name"))
                ?: throw IllegalArgumentException("GitHub-Release-Tag nicht freigegeben")
            val versionCode = tagMatch.groupValues[1].toInt()
            val sourceCommit = tagMatch.groupValues[2]
            require(versionCode in 29..9999)

            val assets = root.getJSONArray("assets")
            require(assets.length() in 1..64) { "GitHub-Release-Artefakte ungültig" }
            val candidates = buildList {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")
                    val match = apk.matchEntire(name) ?: continue
                    val rawSize = asset.get("size")
                    require(rawSize is Int || rawSize is Long) { "GitHub-APK-Größe ungültig" }
                    val size = (rawSize as Number).toLong()
                    val sha = match.groupValues[1]
                    val digest = asset.optString("digest", "")
                    require(digest == "sha256:" + sha) { "GitHub-APK-Digest fehlt oder weicht ab" }
                    val url = asset.getString("browser_download_url")
                    add(Triple(url, size, sha))
                }
            }
            require(candidates.size == 1) { "GitHub-Release muss genau eine freigegebene APK enthalten" }
            val (url, bytes, sha) = candidates.single()
            require(bytes in 1_000_000L..2_000_000_000L) { "GitHub-APK-Größe außerhalb des Limits" }

            val notes = root.optString("body", "")
                .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), " ")
                .take(800)

            return ContinuumOwnerUpdateRelease(
                descriptor = ContinuumOwnerReleaseDescriptor(
                    channel = "OWNER_RELEASE",
                    packageName = ContinuumSignedUpdatePolicy.OWNER_PACKAGE,
                    versionCode = versionCode,
                    apkUrl = url,
                    apkSha256 = sha,
                    byteLength = bytes,
                    sourceCommit = sourceCommit,
                    signerSha256 = ContinuumSignedUpdatePolicy.ORIGINAL_SIGNER_SHA256,
                    explicitlyApproved = true
                ),
                versionName = "0.3." + (versionCode - 1),
                notes = notes,
                source = ContinuumOwnerUpdateSource.GITHUB_RELEASE
            )
        } catch (error: org.json.JSONException) {
            throw IllegalArgumentException("GitHub-Release-Metadaten unvollständig oder ungültig", error)
        }
    }
}
