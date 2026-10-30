package at.heinz.deviceai.intelligence

import java.net.URI
import java.util.Locale

/**
 * Pre-download policy only. The downloader must still verify the actual APK bytes,
 * Android package metadata, version and the installed app's original signer.
 * No filesystem, network, installer or credential operations occur here.
 */
internal data class ContinuumOwnerReleaseDescriptor(
    val channel: String,
    val packageName: String,
    val versionCode: Int,
    val apkUrl: String,
    val apkSha256: String,
    val byteLength: Long,
    val sourceCommit: String,
    val signerSha256: String,
    val explicitlyApproved: Boolean
)

internal enum class ContinuumUpdateRejection {
    WRONG_INSTALLED_APP, INSTALLED_CERT_MISMATCH, WRONG_RELEASE_CHANNEL,
    WRONG_PACKAGE, OLD_OR_EQUAL_VERSION, INVALID_RELEASE_METADATA,
    UNTRUSTED_HTTPS_URL, UNAPPROVED_RELEASE
}

internal sealed interface ContinuumUpdatePreflight {
    data object ReadyForActualApkVerification : ContinuumUpdatePreflight
    data class Rejected(val reason: ContinuumUpdateRejection) : ContinuumUpdatePreflight
}

internal object ContinuumSignedUpdatePolicy {
    const val OWNER_PACKAGE = "at.heinz.deviceai.continuumlocal"
    const val ORIGINAL_SIGNER_SHA256 =
        "6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23"
    private const val MAX_APK_BYTES = 2_000_000_000L
    private val filePattern = Regex("^/continuum-local/[A-Za-z0-9._-]+\\.apk$")

    /**
     * trustedHost is a compile-time pinned owner-release channel host supplied
     * by the caller, never taken from downloaded JSON or a user-provided URL.
     */
    fun check(
        installedPackage: String,
        installedVersionCode: Int,
        installedSignerSha256: String,
        trustedHost: String,
        release: ContinuumOwnerReleaseDescriptor
    ): ContinuumUpdatePreflight {
        fun reject(reason: ContinuumUpdateRejection) = ContinuumUpdatePreflight.Rejected(reason)
        if (installedPackage != OWNER_PACKAGE) return reject(ContinuumUpdateRejection.WRONG_INSTALLED_APP)
        if (installedSignerSha256.lowercase(Locale.ROOT) != ORIGINAL_SIGNER_SHA256) {
            return reject(ContinuumUpdateRejection.INSTALLED_CERT_MISMATCH)
        }
        if (release.channel != "OWNER_RELEASE") return reject(ContinuumUpdateRejection.WRONG_RELEASE_CHANNEL)
        if (release.packageName != OWNER_PACKAGE) return reject(ContinuumUpdateRejection.WRONG_PACKAGE)
        // An old release is only "current" if the WHOLE manifest is authentic-looking,
        // approved and pinned. Never mask malformed or unapproved metadata as current.
        if (!release.explicitlyApproved) return reject(ContinuumUpdateRejection.UNAPPROVED_RELEASE)
        // Preserve OLD_OR_EQUAL_VERSION for older valid direct-policy inputs
        // (existing regression contract); the manifest parser itself requires >=29.
        if (release.versionCode !in 1..9999 ||
            release.byteLength !in 1_000_000L..MAX_APK_BYTES ||
            !release.apkSha256.matches(Regex("[a-f0-9]{64}")) ||
            !release.sourceCommit.matches(Regex("[a-f0-9]{40}")) ||
            release.signerSha256.lowercase(Locale.ROOT) != ORIGINAL_SIGNER_SHA256
        ) return reject(ContinuumUpdateRejection.INVALID_RELEASE_METADATA)

        val uri = runCatching { URI(release.apkUrl) }.getOrNull()
            ?: return reject(ContinuumUpdateRejection.UNTRUSTED_HTTPS_URL)
        val path = uri.rawPath.orEmpty()
        if (trustedHost.isBlank() ||
            !uri.scheme.equals("https", ignoreCase = true) ||
            uri.host?.lowercase(Locale.ROOT) != trustedHost.lowercase(Locale.ROOT) ||
            uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null ||
            (uri.port != -1 && uri.port != 443) || path.contains("..") ||
            !filePattern.matches(path) || !path.endsWith("-${release.apkSha256}.apk")
        ) return reject(ContinuumUpdateRejection.UNTRUSTED_HTTPS_URL)
        if (release.versionCode <= installedVersionCode) {
            return reject(ContinuumUpdateRejection.OLD_OR_EQUAL_VERSION)
        }
        if (release.versionCode < 29) {
            return reject(ContinuumUpdateRejection.INVALID_RELEASE_METADATA)
        }
        return ContinuumUpdatePreflight.ReadyForActualApkVerification
    }
}
