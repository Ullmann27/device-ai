package at.heinz.deviceai.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuumGitHubSignedUpdatePolicyTest {
    private val sha="a".repeat(64)
    private val commit="b".repeat(40)

    private fun release(
        version:Int=50,
        url:String="https://github.com/Ullmann27/device-ai/releases/download/continuum-local-code50-$commit/Continuum-Lokal-$sha.apk"
    )=ContinuumOwnerReleaseDescriptor(
        channel="OWNER_RELEASE",
        packageName=ContinuumSignedUpdatePolicy.OWNER_PACKAGE,
        versionCode=version,
        apkUrl=url,
        apkSha256=sha,
        byteLength=271100761,
        sourceCommit=commit,
        signerSha256=ContinuumSignedUpdatePolicy.ORIGINAL_SIGNER_SHA256,
        explicitlyApproved=true
    )

    @Test fun exactOwnerRepoTagAndDigestPathIsAccepted() {
        assertTrue(ContinuumGitHubSignedUpdatePolicy.check(
            ContinuumSignedUpdatePolicy.OWNER_PACKAGE,
            49,
            ContinuumSignedUpdatePolicy.ORIGINAL_SIGNER_SHA256,
            release()
        ) is ContinuumUpdatePreflight.ReadyForActualApkVerification)
    }

    @Test fun wrongRepoTagFilenameQueryOrSignerIsRejected() {
        val badUrls=listOf(
            "https://github.com/Other/device-ai/releases/download/continuum-local-code50-$commit/Continuum-Lokal-$sha.apk",
            "https://github.com/Ullmann27/device-ai/releases/download/wrong/Continuum-Lokal-$sha.apk",
            "https://github.com/Ullmann27/device-ai/releases/download/continuum-local-code50-$commit/Continuum-Lokal-cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc.apk",
            "https://github.com/Ullmann27/device-ai/releases/download/continuum-local-code50-$commit/Continuum-Lokal-$sha.apk?x=1"
        )
        badUrls.forEach { url ->
            val result=ContinuumGitHubSignedUpdatePolicy.check(
                ContinuumSignedUpdatePolicy.OWNER_PACKAGE,49,
                ContinuumSignedUpdatePolicy.ORIGINAL_SIGNER_SHA256,release(url=url))
            assertEquals(
                ContinuumUpdateRejection.UNTRUSTED_HTTPS_URL,
                (result as ContinuumUpdatePreflight.Rejected).reason
            )
        }
        val signer=ContinuumGitHubSignedUpdatePolicy.check(
            ContinuumSignedUpdatePolicy.OWNER_PACKAGE,49,"0".repeat(64),release())
        assertEquals(ContinuumUpdateRejection.INSTALLED_CERT_MISMATCH,
            (signer as ContinuumUpdatePreflight.Rejected).reason)
    }

    @Test fun oldOrEqualVersionNeverBecomesInstallCandidate() {
        val r=ContinuumGitHubSignedUpdatePolicy.check(
            ContinuumSignedUpdatePolicy.OWNER_PACKAGE,50,
            ContinuumSignedUpdatePolicy.ORIGINAL_SIGNER_SHA256,release())
        assertEquals(ContinuumUpdateRejection.OLD_OR_EQUAL_VERSION,
            (r as ContinuumUpdatePreflight.Rejected).reason)
    }
}
