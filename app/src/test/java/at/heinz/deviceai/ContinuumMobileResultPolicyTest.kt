package at.heinz.deviceai

import org.junit.Assert.assertEquals
import org.junit.Test

class ContinuumMobileResultPolicyTest {
    private val sha = "a".repeat(64)
    private val signer = "b".repeat(64)

    @Test fun doctorNeedsTrueExitAndRecognizedReport() {
        val op = ContinuumMobileBuildPlan.Operation.DOCTOR
        val expected = ContinuumMobileResultPolicy.Outcome.TOOLCHAIN_READY
        assertEquals(expected, ContinuumMobileResultPolicy.evaluate(op, 0, -1, "READY_FOR_EXPERIMENTAL_BUILD"))
        assertEquals(
            ContinuumMobileResultPolicy.Outcome.INVALID_RESULT,
            ContinuumMobileResultPolicy.evaluate(op, 0, -1, "SYNTHETIC_SUCCESS")
        )
        assertEquals(
            ContinuumMobileResultPolicy.Outcome.TOOLCHAIN_UNAVAILABLE,
            ContinuumMobileResultPolicy.evaluate(op, 2, -1, "UNAVAILABLE")
        )
        assertEquals(
            ContinuumMobileResultPolicy.Outcome.INVALID_RESULT,
            ContinuumMobileResultPolicy.evaluate(op, null, null, "READY_FOR_EXPERIMENTAL_BUILD")
        )
    }

    @Test fun buildNeedsSignatureEvidenceAndCompletedTermuxStatus() {
        val op = ContinuumMobileBuildPlan.Operation.BUILD
        val expected = ContinuumMobileResultPolicy.Outcome.BUILD_REPORTED_UNVERIFIED_ON_DEVICE
        assertEquals(expected, ContinuumMobileResultPolicy.evaluate(
            op, 0, -1, "EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED", sha, signer))
        assertEquals(ContinuumMobileResultPolicy.Outcome.INVALID_RESULT,
            ContinuumMobileResultPolicy.evaluate(
                op, 0, -1, "EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED", sha, null))
        assertEquals(ContinuumMobileResultPolicy.Outcome.INVALID_RESULT,
            ContinuumMobileResultPolicy.evaluate(
                op, 0, -1, "EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED", "bad-hash", signer))
        assertEquals(ContinuumMobileResultPolicy.Outcome.INVALID_RESULT,
            ContinuumMobileResultPolicy.evaluate(
                op, 0, -1, "EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED", sha, "x".repeat(64)))
    }

    @Test fun exitCodesAndTermuxErrorsCannotBePromotedToSuccess() {
        val op = ContinuumMobileBuildPlan.Operation.BUILD
        assertEquals(ContinuumMobileResultPolicy.Outcome.BUILD_FAILED,
            ContinuumMobileResultPolicy.evaluate(op, 2, -1,
                "EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED", sha, signer))
        assertEquals(ContinuumMobileResultPolicy.Outcome.INVALID_RESULT,
            ContinuumMobileResultPolicy.evaluate(op, 0, 1,
                "EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED", sha, signer))
        assertEquals(ContinuumMobileResultPolicy.Outcome.INVALID_RESULT,
            ContinuumMobileResultPolicy.evaluate(op, 0, null,
                "EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED", sha, signer))
    }

    @Test fun statusNeverClaimsDeviceInstallation() {
        val op = ContinuumMobileBuildPlan.Operation.STATUS
        assertEquals(ContinuumMobileResultPolicy.Outcome.STATUS_REPORTED,
            ContinuumMobileResultPolicy.evaluate(op, 0, -1, "NO_BUILD_REPORT"))
        assertEquals(ContinuumMobileResultPolicy.Outcome.STATUS_REPORTED,
            ContinuumMobileResultPolicy.evaluate(op, 0, -1, "FAILED_BUILD"))
        assertEquals(ContinuumMobileResultPolicy.Outcome.INVALID_RESULT,
            ContinuumMobileResultPolicy.evaluate(op, 0, -1, "DEVICE_INSTALLED"))
    }
}
