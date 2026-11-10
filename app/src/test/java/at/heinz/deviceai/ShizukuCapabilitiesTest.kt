package at.heinz.deviceai

import org.junit.Assert.*
import org.junit.Test

class ShizukuCapabilitiesTest {
    private fun frame(uid: String = "2000", sdk: String = "37", flags: String = "DUMPSYS=1\nPM=1\nCMD=1") =
        "CONTINUUM_SHIZUKU_CAPABILITIES_V1\n$uid\n$sdk\n$flags\n"

    @Test fun shellIdentityDoesNotClaimRootOrActionPermissions() {
        val result = ShizukuCapabilityProbe.parse(frame(), 2000, 37, 13)
        assertEquals(ShizukuIdentity.ADB_SHELL, result.identity)
        assertTrue(result.standardShellEligible)
        assertFalse(result.rootExpertEnabled)
        assertEquals(37, result.sdkInt)
        assertEquals(2000, result.userServiceUid)
    }

    @Test fun rootDetectionDoesNotEnableRootExpertMode() {
        val result = ShizukuCapabilityProbe.parse(frame(uid = "0"), 0, 37, 13)
        assertEquals(ShizukuIdentity.ROOT, result.identity)
        assertFalse(result.standardShellEligible)
        assertFalse(result.rootExpertEnabled)
    }

    @Test fun missingExecutablesRemainUnavailable() {
        val result = ShizukuCapabilityProbe.parse(frame(flags = "DUMPSYS=0\nPM=0\nCMD=0"), 2000, 37, 13)
        assertFalse(result.dumpsysExecutable)
        assertFalse(result.packageManagerExecutable)
        assertFalse(result.commandServiceExecutable)
    }

    @Test fun unknownUidNeverInheritsShellCapabilities() {
        val result = ShizukuCapabilityProbe.parse(frame(uid = "10123"), 10123, 37, 13)
        assertEquals(ShizukuIdentity.UNSUPPORTED, result.identity)
        assertFalse(result.standardShellEligible)
    }

    @Test fun serverAndUserServiceMismatchIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { ShizukuCapabilityProbe.parse(frame(uid = "0"), 2000, 37, 13) }
    }

    @Test fun androidVersionMismatchIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { ShizukuCapabilityProbe.parse(frame(sdk = "36"), 2000, 37, 13) }
    }

    @Test fun malformedMissingExtraAndDuplicateFieldsAreRejected() {
        val bad = listOf(frame() + "EXTRA=1\n", frame().substringBefore("CMD="),
            frame(flags = "DUMPSYS=1\nPM=1\nPM=1"), frame(uid = "02000"), frame(uid = "-1"),
            frame(sdk = "37.0"), frame().replace("V1", "V2"), frame().replace("PM=1", "PM=1 "),
            frame().replace("DUMPSYS=1", "DUMPSYS=true"), "x".repeat(257))
        bad.forEach { assertTrue("must reject malformed receipt", runCatching { ShizukuCapabilityProbe.parse(it, 2000, 37, 13) }.isFailure) }
    }

    @Test fun unknownServerVersionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { ShizukuCapabilityProbe.parse(frame(), 2000, 37, 0) }
    }
}
