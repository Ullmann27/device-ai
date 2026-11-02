package at.heinz.deviceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ContinuumMobileBuildPlanTest {
    @Test fun approvedOperationsHaveFixedExecutableAndWorkingDirectory() {
        assertEquals(setOf("DOCTOR", "BUILD", "STATUS"),
            ContinuumMobileBuildPlan.Operation.entries.filterNot { it.name.startsWith("PROJECT_") }.map { it.name }.toSet())
        for (operation in ContinuumMobileBuildPlan.Operation.entries) {
            val request = ContinuumMobileBuildPlan.command(operation)
            assertEquals(ContinuumMobileBuildPlan.NODE_EXECUTABLE, request.executable)
            assertEquals(ContinuumMobileBuildPlan.PROJECT_DIR, request.workDir)
            assertEquals(if (operation.name.startsWith("PROJECT_"))
                "${ContinuumMobileBuildPlan.PROJECT_DIR}/scripts/continuum-owned-app.mjs"
                else ContinuumMobileBuildPlan.BUILDER_SCRIPT, request.args[0])
            assertEquals(operation.cli, request.args[1])
            assertFalse(request.args.any { it.contains(";") || it.contains("\n") || it.contains("&&") })
        }
    }

    @Test fun buildUsesOnlyPinnedProjectAndNoInstall() {
        val plan = ContinuumMobileBuildPlan.command(ContinuumMobileBuildPlan.Operation.BUILD)
        assertEquals(
            listOf(ContinuumMobileBuildPlan.BUILDER_SCRIPT, "build", "--project", ContinuumMobileBuildPlan.PROJECT_DIR),
            plan.args
        )
        assertFalse(plan.args.contains("install"))
    }

    @Test fun doctorAndStatusCannotRunArbitraryTasks() {
        for (operation in listOf(
            ContinuumMobileBuildPlan.Operation.DOCTOR,
            ContinuumMobileBuildPlan.Operation.STATUS
        )) {
            val request = ContinuumMobileBuildPlan.command(operation)
            assertEquals(listOf(ContinuumMobileBuildPlan.BUILDER_SCRIPT, operation.cli), request.args)
        }
    }
}
