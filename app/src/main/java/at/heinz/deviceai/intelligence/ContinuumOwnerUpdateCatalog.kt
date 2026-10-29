package at.heinz.deviceai.intelligence

import org.json.JSONObject

/**
 * Bounded owner-approved public release metadata. The original APK signing key
 * never travels with the metadata or the Android application.
 *
 * This file describes an update; the actual APK bytes, package/version and
 * original Android signing certificate must be verified independently.
 */
internal data class ContinuumOwnerUpdateRelease(
    val descriptor: ContinuumOwnerReleaseDescriptor,
    val versionName: String,
    val notes: String
)

internal object ContinuumOwnerUpdateCatalog {
    const val HOST = "continuum-owner-v23-release-preflight.onrender.com"
    const val MANIFEST_PATH = "/continuum-local/latest.json"
    const val MANIFEST_URL = "https://$HOST$MANIFEST_PATH"
    const val MAX_JSON_BYTES = 8192

    private val namePattern = Regex("[0-9A-Za-z._-]{1,40}")
    private val allowedKeys = setOf(
        "schema", "channel", "packageName", "versionCode", "versionName",
        "apkUrl", "apkSha256", "byteLength", "sourceCommit",
        "signerSha256", "approved", "notes"
    )

    /**
     * A published placeholder means there is no signed, approved APK yet.
     * Never treat an incomplete release manifest as an installable update.
     */
    fun parseAvailable(json: String): ContinuumOwnerUpdateRelease? {
        if (json.toByteArray(Charsets.UTF_8).size !in 1..MAX_JSON_BYTES)
            throw IllegalArgumentException("Manifestgröße ungültig")
        val objectValue = JSONObject(json)
        val props = mutableSetOf<String>()
        val iterator = objectValue.keys()
        while (iterator.hasNext()) props += iterator.next()
        if (props == setOf("schema", "available") &&
            objectValue.get("schema") is Number &&
            objectValue.getInt("schema") == 1 &&
            objectValue.get("available") is Boolean &&
            !objectValue.getBoolean("available")) return null
        return parse(json)
    }

    fun parse(json: String): ContinuumOwnerUpdateRelease {
        require(json.toByteArray(Charsets.UTF_8).size in 1..MAX_JSON_BYTES) {
            "Manifestgröße ungültig"
        }
        val obj = JSONObject(json)
        val keys = mutableSetOf<String>()
        val names = obj.keys()
        while (names.hasNext()) keys += names.next()
        require(keys == allowedKeys) { "Manifeststruktur unbekannt oder unvollständig" }
        require(obj.get("schema") is Number && obj.getInt("schema") == 1) {
            "Manifestformat nicht unterstützt"
        }
        require(obj.get("versionCode") is Number && obj.get("byteLength") is Number &&
            obj.get("approved") is Boolean) { "Release-Feldtypen ungültig" }

        val versionName = obj.getString("versionName")
        require(namePattern.matches(versionName)) { "Versionsname ungültig" }
        val notes = obj.getString("notes")
        require(notes.length <= 800 && notes.none { it.code < 32 && it != '\n' }) {
            "Releasehinweise ungültig"
        }

        return ContinuumOwnerUpdateRelease(
            ContinuumOwnerReleaseDescriptor(
                channel = obj.getString("channel"),
                packageName = obj.getString("packageName"),
                versionCode = obj.getInt("versionCode"),
                apkUrl = obj.getString("apkUrl"),
                apkSha256 = obj.getString("apkSha256"),
                byteLength = obj.getLong("byteLength"),
                sourceCommit = obj.getString("sourceCommit"),
                signerSha256 = obj.getString("signerSha256"),
                explicitlyApproved = obj.getBoolean("approved")
            ),
            versionName,
            notes
        )
    }
}
