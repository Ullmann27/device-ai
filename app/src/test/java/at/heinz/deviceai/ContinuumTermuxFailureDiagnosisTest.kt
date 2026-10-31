package at.heinz.deviceai

import org.junit.Assert.*
import org.junit.Test

class ContinuumTermuxFailureDiagnosisTest {
    @Test fun missingGeneratorGetsActionableMessageWithoutRawStderr() {
        val raw = "Error: Cannot find module '/data/data/com.termux/files/home/device-ai/scripts/continuum-owned-app.mjs'\n" +
            "code: 'MODULE_NOT_FOUND'\nSecret: private value should not appear"
        val message = ContinuumTermuxFailureDiagnosis.explanation(
            ContinuumMobileBuildPlan.Operation.PROJECT_CREATE, raw
        )
        assertNotNull(message)
        assertTrue(message!!.contains("continuum-owned-app.mjs"))
        assertTrue(message.contains("kein", ignoreCase = true))
        assertFalse(message.contains("/data/data/"))
        assertFalse(message.contains("private value"))
        assertEquals(message, ContinuumTermuxFailureDiagnosis.explanation(
            ContinuumMobileBuildPlan.Operation.PROJECT_VERIFY, raw
        ))
    }

    @Test fun unrelatedErrorsCannotBecomeFalseDiagnosticsOrSuccess() {
        assertNull(ContinuumTermuxFailureDiagnosis.explanation(
            ContinuumMobileBuildPlan.Operation.PROJECT_CREATE,
            "MODULE_NOT_FOUND: some unrelated node dependency"
        ))
        assertNull(ContinuumTermuxFailureDiagnosis.explanation(
            ContinuumMobileBuildPlan.Operation.DOCTOR,
            "MODULE_NOT_FOUND /device-ai/scripts/continuum-owned-app.mjs"
        ))
        assertNull(ContinuumTermuxFailureDiagnosis.explanation(
            ContinuumMobileBuildPlan.Operation.PROJECT_CREATE, null
        ))
    }

    @Test fun invalidGitCheckoutProducesBoundedExplanation() {
        val message = ContinuumTermuxFailureDiagnosis.explanation(
            ContinuumMobileBuildPlan.Operation.PROJECT_BUILD,
            "fatal: not a git repository (or any of the parent directories): .git"
        )
        assertTrue(message!!.contains("Git-Checkout"))
        assertFalse(message.contains("erfolgreich"))
    }
}
