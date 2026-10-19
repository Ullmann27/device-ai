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
    @Test fun storedStatusExplainsHistoricalFailureWithoutReclassifyingCurrentTools() {
        val missing = ContinuumMobileResultPolicy.statusExplanation("BLOCKED_MISSING_TOOLCHAIN")
        assertEquals(true, missing.contains("damalige"))
        assertEquals(true, missing.contains("aktuelle Werkzeugzustand"))
        assertEquals(true, ContinuumMobileResultPolicy.statusExplanation("FAILED_APK_SIGNATURE").contains("nicht freigegeben"))
        assertEquals(true, ContinuumMobileResultPolicy.statusExplanation("FAILED_BUILD").contains("kein neuer Build-Erfolg"))
    }

    @Test fun storedStatusNeverTurnsInvalidOrAbsentEvidenceIntoSuccessfulInstall() {
        for (value in listOf(
            "NO_BUILD_REPORT",
            "BLOCKED_UNCLEAN_SOURCE",
            "FAILED_BUILD",
            "FAILED_NO_APK",
            "FAILED_APK_SIGNATURE",
            "EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED",
            "UNKNOWN",
            ""
        )) {
            val explanation = ContinuumMobileResultPolicy.statusExplanation(value)
            assertEquals(false, explanation.contains("Installation erfolgreich"))
            assertEquals(false, explanation.contains("produktionsreif"))
        }
        assertEquals(
            "Buildbericht unbekannt oder ungültig. Kein Erfolg nachgewiesen.",
            ContinuumMobileResultPolicy.statusExplanation("USER_SUPPLIED_SUCCESS")
        )
    }

}
