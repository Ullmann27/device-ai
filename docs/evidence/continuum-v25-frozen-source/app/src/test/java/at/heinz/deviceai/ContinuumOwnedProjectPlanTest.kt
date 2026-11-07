package at.heinz.deviceai

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ContinuumOwnedProjectPlanTest {
    private fun catalog() = File("src/main/assets/continuum-owned-project-plans-v1.json").readText()

    @Test fun sharedCatalogHasExactlyTheTwoHashBoundedPlans() {
        val plans = ContinuumOwnedProjectPlan.readCatalog(catalog())
        assertEquals(listOf("m4-notes", "m4-counter"), plans.map { it.project.id })
        assertEquals(listOf(26, 26), plans.map { it.minSdk })
        assertTrue(plans.all { it.permissions.isEmpty() && it.targetSdk == 36 })
        assertTrue(plans.all { it.screenIds == listOf("main", "about") })
        for (bad in listOf(
            catalog().replace("\"version\": 1", "\"version\": 2"),
            catalog().replace("at.heinz.continuum.m4notes", "at.heinz.deviceai.mentorpaired"),
            catalog().replace("\"maxHeapMiB\": 1536", "\"maxHeapMiB\": 99999")
        )) assertThrows(IllegalArgumentException::class.java) { ContinuumOwnedProjectPlan.readCatalog(bad) }
        assertNull(ContinuumOwnedProject.ofId("../owner"))
    }

    @Test fun counterUsesTheExistingFixedScriptWithOnlyAnAllowlistedPlanId() {
        for (op in listOf(ContinuumMobileBuildPlan.Operation.PROJECT_CREATE,
            ContinuumMobileBuildPlan.Operation.PROJECT_VERIFY, ContinuumMobileBuildPlan.Operation.PROJECT_BUILD,
            ContinuumMobileBuildPlan.Operation.PROJECT_STATUS)) {
            val command = ContinuumMobileBuildPlan.command(op, ContinuumOwnedProject.COUNTER)
            assertEquals(listOf("${ContinuumMobileBuildPlan.PROJECT_DIR}/scripts/continuum-owned-app.mjs",
                op.cli, "--recipe", "m4-counter"), command.args)
            assertEquals(ContinuumMobileBuildPlan.NODE_EXECUTABLE, command.executable)
        }
    }

    @Test fun aCounterReceiptCannotAnswerANotesRequestOrPretendAHostBuildIsTermux() {
        val op = ContinuumMobileBuildPlan.Operation.PROJECT_BUILD
        fun receipt(expected: ContinuumOwnedProject, profile: String = "termux-arm64") =
            ContinuumMobileResultPolicy.evaluate(op, 0, -1, "EXPERIMENTAL_PROJECT_APK_BUILT_NOT_DEVICE_TESTED",
                apkSha256 = "a".repeat(64), signerSha256 = "b".repeat(64), sourceSha256 = "c".repeat(64),
                packageName = "at.heinz.continuum.m4counter", versionCode = 1,
                recipeId = "m4-counter", expectedProject = expected,
                planSha256 = ContinuumOwnedProject.COUNTER.planSha256, toolchainProfile = profile,
                verification = "CONFIRMED")
        assertEquals(ContinuumMobileResultPolicy.Outcome.PROJECT_BUILD_REPORTED_UNVERIFIED_ON_DEVICE,
            receipt(ContinuumOwnedProject.COUNTER))
        assertEquals(ContinuumMobileResultPolicy.Outcome.INVALID_RESULT, receipt(ContinuumOwnedProject.NOTES))
        assertEquals(ContinuumMobileResultPolicy.Outcome.INVALID_RESULT, receipt(ContinuumOwnedProject.COUNTER, "host-sdk"))
    }

    @Test fun historicalHostStatusRemainsSeparateFromANewMobileBuild() {
        val op = ContinuumMobileBuildPlan.Operation.PROJECT_STATUS
        val outcome = ContinuumMobileResultPolicy.evaluate(op, 0, -1, "PROJECT_BUILD_STATUS",
            apkSha256 = "a".repeat(64), signerSha256 = "b".repeat(64), sourceSha256 = "c".repeat(64),
            packageName = "at.heinz.continuum.m4counter", versionCode = 1, recipeId = "m4-counter",
            expectedProject = ContinuumOwnedProject.COUNTER, planSha256 = ContinuumOwnedProject.COUNTER.planSha256,
            toolchainProfile = "host-sdk", verification = "CONFIRMED",
            buildStatus = "EXPERIMENTAL_PROJECT_APK_BUILT_NOT_DEVICE_TESTED")
        assertEquals(ContinuumMobileResultPolicy.Outcome.PROJECT_STATUS_REPORTED, outcome)
        assertTrue(ContinuumMobileResultPolicy.projectStatusExplanation(
            "EXPERIMENTAL_PROJECT_APK_BUILT_NOT_DEVICE_TESTED", "CONFIRMED", "host-sdk").contains("Host-SDK"))
        assertEquals(ContinuumMobileResultPolicy.Outcome.INVALID_RESULT,
            ContinuumMobileResultPolicy.evaluate(op, 0, -1, "PROJECT_BUILD_STATUS",
                recipeId = "m4-counter", expectedProject = ContinuumOwnedProject.NOTES))
    }
}
