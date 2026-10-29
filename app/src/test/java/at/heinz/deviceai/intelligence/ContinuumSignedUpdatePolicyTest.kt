package at.heinz.deviceai.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ContinuumSignedUpdatePolicyTest {
    private val host = "updates.example.org"
    private val sha = "b".repeat(64)
    private val cert = ContinuumSignedUpdatePolicy.ORIGINAL_SIGNER_SHA256
    private fun release(
        channel: String = "OWNER_RELEASE",
        packageName: String = ContinuumSignedUpdatePolicy.OWNER_PACKAGE,
        versionCode: Int = 29,
        apkUrl: String = "https://$host/continuum-local/Continuum-Lokal-${sha}.apk",
        signer: String = cert,
        approved: Boolean = true,
        byteLength: Long = 100_000_000L
    ) = ContinuumOwnerReleaseDescriptor(
        channel, packageName, versionCode, apkUrl, sha, byteLength, "a".repeat(40), signer, approved
    )
    private fun check(
        update: ContinuumOwnerReleaseDescriptor,
        version: Int = 28,
        installedPackage: String = ContinuumSignedUpdatePolicy.OWNER_PACKAGE,
        installedSigner: String = cert
    ) = ContinuumSignedUpdatePolicy.check(
        installedPackage, version, installedSigner, host, update
    )
    private fun rejected(
        result: ContinuumUpdatePreflight,
        reason: ContinuumUpdateRejection
    ) = assertEquals(ContinuumUpdatePreflight.Rejected(reason), result)

    @Test fun approvedHigherVersionIsOnlyReadyForByteVerification() {
        assertSame(ContinuumUpdatePreflight.ReadyForActualApkVerification, check(release()))
    }

    @Test fun refusesReinstallAndDowngrade() {
        rejected(check(release(versionCode = 28)), ContinuumUpdateRejection.OLD_OR_EQUAL_VERSION)
        rejected(check(release(versionCode = 27)), ContinuumUpdateRejection.OLD_OR_EQUAL_VERSION)
    }

    @Test fun refusesWrongInstalledPackageAndSigner() {
        rejected(check(release(), installedPackage = "at.heinz.deviceai.preview"),
            ContinuumUpdateRejection.WRONG_INSTALLED_APP)
        rejected(check(release(), installedSigner = "c".repeat(64)),
            ContinuumUpdateRejection.INSTALLED_CERT_MISMATCH)
    }

    @Test fun refusesQaAndDifferentPackage() {
        rejected(check(release(channel = "QA")), ContinuumUpdateRejection.WRONG_RELEASE_CHANNEL)
        rejected(check(release(packageName = "at.heinz.deviceai.preview")),
            ContinuumUpdateRejection.WRONG_PACKAGE)
    }

    @Test fun refusesMismatchedClaimedSignerAndUnapprovedRelease() {
        rejected(check(release(signer = "c".repeat(64))),
            ContinuumUpdateRejection.INVALID_RELEASE_METADATA)
        rejected(check(release(approved = false)),
            ContinuumUpdateRejection.UNAPPROVED_RELEASE)
    }

    @Test fun restrictsHostSchemeAndArchivePath() {
        val urls = listOf(
            "http://$host/continuum-local/Continuum-Lokal-${sha}.apk",
            "https://bad.example/continuum-local/Continuum-Lokal-${sha}.apk",
            "https://$host/other/Continuum-Lokal-${sha}.apk",
            "https://$host/continuum-local/Continuum-Lokal-${sha}.apk?redirect=true",
            "https://$host/continuum-local/Continuum-Lokal-${sha}.apk#fragment",
            "https://$host/continuum-local/Continuum-Lokal-${"d".repeat(64)}.apk"
        )
        urls.forEach { rejected(check(release(apkUrl = it)),
            ContinuumUpdateRejection.UNTRUSTED_HTTPS_URL) }
    }

    @Test fun rejectsUnsafeSize() {
        rejected(check(release(byteLength = 2_000_000_001L)),
            ContinuumUpdateRejection.INVALID_RELEASE_METADATA)
    }
}
