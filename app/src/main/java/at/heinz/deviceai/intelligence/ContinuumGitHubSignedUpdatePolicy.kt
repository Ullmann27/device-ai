package at.heinz.deviceai.intelligence

import java.net.URI
import java.util.Locale

/** Pre-download policy for the compile-time pinned GitHub fallback channel only. */
internal object ContinuumGitHubSignedUpdatePolicy {
    fun check(
        installedPackage: String,
        installedVersionCode: Int,
        installedSignerSha256: String,
        release: ContinuumOwnerReleaseDescriptor
    ): ContinuumUpdatePreflight {
        fun reject(reason: ContinuumUpdateRejection) = ContinuumUpdatePreflight.Rejected(reason)
        if (installedPackage != ContinuumSignedUpdatePolicy.OWNER_PACKAGE)
            return reject(ContinuumUpdateRejection.WRONG_INSTALLED_APP)
        if (installedSignerSha256.lowercase(Locale.ROOT) !=
            ContinuumSignedUpdatePolicy.ORIGINAL_SIGNER_SHA256)
            return reject(ContinuumUpdateRejection.INSTALLED_CERT_MISMATCH)
        if (release.channel != "OWNER_RELEASE")
            return reject(ContinuumUpdateRejection.WRONG_RELEASE_CHANNEL)
        if (release.packageName != ContinuumSignedUpdatePolicy.OWNER_PACKAGE)
            return reject(ContinuumUpdateRejection.WRONG_PACKAGE)
        if (!release.explicitlyApproved)
            return reject(ContinuumUpdateRejection.UNAPPROVED_RELEASE)
        if (release.versionCode !in 29..9999 ||
            release.byteLength !in 1_000_000L..2_000_000_000L ||
            !release.apkSha256.matches(Regex("[a-f0-9]{64}")) ||
            !release.sourceCommit.matches(Regex("[a-f0-9]{40}")) ||
            release.signerSha256.lowercase(Locale.ROOT) !=
                ContinuumSignedUpdatePolicy.ORIGINAL_SIGNER_SHA256
        ) return reject(ContinuumUpdateRejection.INVALID_RELEASE_METADATA)

        val uri = runCatching { URI(release.apkUrl) }.getOrNull()
            ?: return reject(ContinuumUpdateRejection.UNTRUSTED_HTTPS_URL)
        val expectedTag = "continuum-local-code" + release.versionCode + "-" + release.sourceCommit
        val expectedPath = "/Ullmann27/device-ai/releases/download/" + expectedTag +
            "/Continuum-Lokal-" + release.apkSha256 + ".apk"
        if (!uri.scheme.equals("https", ignoreCase = true) ||
            !uri.host.equals(ContinuumGitHubReleaseCatalog.DOWNLOAD_HOST, ignoreCase = true) ||
            uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null ||
            (uri.port != -1 && uri.port != 443) ||
            uri.rawPath != expectedPath
        ) return reject(ContinuumUpdateRejection.UNTRUSTED_HTTPS_URL)

        if (release.versionCode <= installedVersionCode)
            return reject(ContinuumUpdateRejection.OLD_OR_EQUAL_VERSION)
        return ContinuumUpdatePreflight.ReadyForActualApkVerification
    }
}
