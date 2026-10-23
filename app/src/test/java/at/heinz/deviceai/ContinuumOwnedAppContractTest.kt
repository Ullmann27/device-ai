package at.heinz.deviceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** EXP-00/08/09: M4 owner actions never inherit preview APK success permissions. */
class ContinuumOwnedAppContractTest {
    private val sha = "a".repeat(64)
    private val signer = "b".repeat(64)
    private val pkg = "at.heinz.continuum.m4notes"

    @Test fun fixedCommandsCannotBecomeModelShell() {
        for ((op, verb) in listOf(
            ContinuumMobileBuildPlan.Operation.PROJECT_CREATE to "create",
            ContinuumMobileBuildPlan.Operation.PROJECT_VERIFY to "verify",
            ContinuumMobileBuildPlan.Operation.PROJECT_BUILD to "build"
        )) {
            val command = ContinuumMobileBuildPlan.command(op)
            assertEquals(ContinuumMobileBuildPlan.NODE_EXECUTABLE, command.executable)
            assertEquals(
                listOf("${ContinuumMobileBuildPlan.PROJECT_DIR}/scripts/continuum-owned-app.mjs", verb),
                command.args
            )
            assertEquals(ContinuumMobileBuildPlan.PROJECT_DIR, command.workDir)
            assertFalse(command.args.any { it.startsWith("--eval") || it == "-c" })
        }
    }

    @Test fun creationDoesNotImplyApkBuild() {
        val op = ContinuumMobileBuildPlan.Operation.PROJECT_CREATE
        assertEquals(ContinuumMobileResultPolicy.Outcome.PROJECT_CREATED,
            ContinuumMobileResultPolicy.evaluate(op, 0, -1, "PROJECT_CREATED_NOT_BUILT",
                packageName = pkg, sourceSha256 = sha))
        assertEquals(ContinuumMobileResultPolicy.Outcome.INVALID_RESULT,
            ContinuumMobileResultPolicy.evaluate(op, 0, -1, "EXPERIMENTAL_PROJECT_APK_BUILT_NOT_DEVICE_TESTED",
                packageName = pkg, sourceSha256 = sha))
        assertEquals(ContinuumMobileResultPolicy.Outcome.INVALID_RESULT,
            ContinuumMobileResultPolicy.evaluate(op, 0, -1, "PROJECT_CREATED_NOT_BUILT",
                packageName = pkg, sourceSha256 = "invalid"))
    }

    @Test fun independentlyVerifiedSourcesNeedBoundedHash() {
        val op = ContinuumMobileBuildPlan.Operation.PROJECT_VERIFY
        assertEquals(ContinuumMobileResultPolicy.Outcome.PROJECT_SOURCES_VERIFIED,
            ContinuumMobileResultPolicy.evaluate(op, 0, -1, "SOURCE_VERIFIED",
                packageName = pkg, sourceSha256 = sha))
        assertEquals(ContinuumMobileResultPolicy.Outcome.INVALID_RESULT,
            ContinuumMobileResultPolicy.evaluate(op, 0, -1, "SOURCE_VERIFIED",
                packageName = "at.heinz.deviceai.mentorpaired", sourceSha256 = sha))
    }

    @Test fun separateApkReceiptNeedsRealSignerAndExactIdentity() {
        val op = ContinuumMobileBuildPlan.Operation.PROJECT_BUILD
        fun eval(
            status: String = "EXPERIMENTAL_PROJECT_APK_BUILT_NOT_DEVICE_TESTED",
            packageName: String = pkg,
            signerHash: String? = signer,
            sourceHash: String? = sha,
            apkHash: String? = sha,
            version: Int = 1,
            exit: Int = 0,
            err: Int = -1
        ) = ContinuumMobileResultPolicy.evaluate(
            op, exit, err, status, apkHash, signerHash, null, packageName, version, sourceHash
        )

        assertEquals(ContinuumMobileResultPolicy.Outcome.PROJECT_BUILD_REPORTED_UNVERIFIED_ON_DEVICE, eval())
        assertEquals(ContinuumMobileResultPolicy.Outcome.INVALID_RESULT, eval(packageName="at.heinz.deviceai.mentorpaired"))
        assertEquals(ContinuumMobileResultPolicy.Outcome.INVALID_RESULT, eval(signerHash=null))
        assertEquals(ContinuumMobileResultPolicy.Outcome.INVALID_RESULT, eval(sourceHash=null))
        assertEquals(ContinuumMobileResultPolicy.Outcome.INVALID_RESULT, eval(apkHash="wrong"))
        assertEquals(ContinuumMobileResultPolicy.Outcome.INVALID_RESULT, eval(version=2))
        assertEquals(ContinuumMobileResultPolicy.Outcome.INVALID_RESULT, eval(status="DEVICE_INSTALLED"))
        assertEquals(ContinuumMobileResultPolicy.Outcome.INVALID_RESULT, eval(err=1))
        assertEquals(ContinuumMobileResultPolicy.Outcome.BUILD_FAILED, eval(exit=1))
        assertTrue(ContinuumMobileResultPolicy.explanation(eval()).contains("Weder Installation"))
    }
}
