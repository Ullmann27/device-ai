package at.heinz.deviceai.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContinuumOwnerReleaseSelectorTest {
    private fun release(
        version:Int,
        source:ContinuumOwnerUpdateSource,
        commitChar:Char
    )=ContinuumOwnerUpdateRelease(
        descriptor=ContinuumOwnerReleaseDescriptor(
            channel="OWNER_RELEASE",
            packageName=ContinuumSignedUpdatePolicy.OWNER_PACKAGE,
            versionCode=version,
            apkUrl="https://example.invalid/" + version,
            apkSha256="a".repeat(64),
            byteLength=1_000_000,
            sourceCommit=commitChar.toString().repeat(40),
            signerSha256=ContinuumSignedUpdatePolicy.ORIGINAL_SIGNER_SHA256,
            explicitlyApproved=true
        ),
        versionName="0.3." + (version-1),
        notes="",
        source=source
    )

    @Test fun highestReadyVersionWinsAcrossSources() {
        val selected=ContinuumOwnerReleaseSelector.select(listOf(
            ContinuumOwnerReleaseSelector.Candidate(
                release(49,ContinuumOwnerUpdateSource.RENDER,'a'),
                ContinuumUpdatePreflight.ReadyForActualApkVerification),
            ContinuumOwnerReleaseSelector.Candidate(
                release(50,ContinuumOwnerUpdateSource.GITHUB_RELEASE,'b'),
                ContinuumUpdatePreflight.ReadyForActualApkVerification)
        ))
        assertEquals(50,selected!!.descriptor.versionCode)
        assertEquals(ContinuumOwnerUpdateSource.GITHUB_RELEASE,selected.source)
    }

    @Test fun rejectedHigherVersionCannotBeatReadyLowerVersion() {
        val selected=ContinuumOwnerReleaseSelector.select(listOf(
            ContinuumOwnerReleaseSelector.Candidate(
                release(51,ContinuumOwnerUpdateSource.GITHUB_RELEASE,'c'),
                ContinuumUpdatePreflight.Rejected(ContinuumUpdateRejection.UNTRUSTED_HTTPS_URL)),
            ContinuumOwnerReleaseSelector.Candidate(
                release(50,ContinuumOwnerUpdateSource.RENDER,'d'),
                ContinuumUpdatePreflight.ReadyForActualApkVerification)
        ))
        assertEquals(50,selected!!.descriptor.versionCode)
        assertEquals(ContinuumOwnerUpdateSource.RENDER,selected.source)
    }

    @Test fun sameVersionPrefersExistingRenderChannelWhileBothAreValid() {
        val selected=ContinuumOwnerReleaseSelector.select(listOf(
            ContinuumOwnerReleaseSelector.Candidate(
                release(50,ContinuumOwnerUpdateSource.GITHUB_RELEASE,'a'),
                ContinuumUpdatePreflight.ReadyForActualApkVerification),
            ContinuumOwnerReleaseSelector.Candidate(
                release(50,ContinuumOwnerUpdateSource.RENDER,'b'),
                ContinuumUpdatePreflight.ReadyForActualApkVerification)
        ))
        assertEquals(ContinuumOwnerUpdateSource.RENDER,selected!!.source)
    }

    @Test fun noReadyCandidateReturnsNull() {
        assertNull(ContinuumOwnerReleaseSelector.select(listOf(
            ContinuumOwnerReleaseSelector.Candidate(
                release(50,ContinuumOwnerUpdateSource.GITHUB_RELEASE,'a'),
                ContinuumUpdatePreflight.Rejected(ContinuumUpdateRejection.OLD_OR_EQUAL_VERSION))
        )))
    }
}
