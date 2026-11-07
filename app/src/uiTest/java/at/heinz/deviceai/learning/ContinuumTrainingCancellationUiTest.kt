package at.heinz.deviceai.learning

import android.app.job.JobParameters
import android.content.Intent
import android.os.BatteryManager
import at.heinz.deviceai.cognitive.ContinuumAutonomyControl
import at.heinz.deviceai.cognitive.ContinuumAutonomyLevel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowStatFs
import org.robolectric.shadows.ShadowSystemClock
import java.util.concurrent.TimeUnit

/** Actual job; deterministic lock contention. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class ContinuumTrainingCancellationUiTest {
    private fun delayedCdxStart(
        stop: (ContinuumAutonomousJobService) -> Unit,
        expectedOutcome: String,
        expectedDetail: String
    ) {
        val controller = Robolectric.buildService(ContinuumAutonomousJobService::class.java).create()
        val service = controller.get()
        val core = ContinuumLearningCore(service.filesDir)
        core.confirmOwnerFact("Dieses synthetische Testprojekt heißt Morgenstern.")
        val before = core.modelSnapshot()
        val factsBefore = core.activeOwnerFacts()
        ShadowStatFs.registerStats(service.filesDir, 1_000_000, 900_000, 900_000)
        service.sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED)
            .putExtra(BatteryManager.EXTRA_LEVEL, 100).putExtra(BatteryManager.EXTRA_SCALE, 100))
        assertNull(ContinuumOfflineLearningBudget.deferralReason(service.continuumLearningResources()))
        assertTrue(ContinuumDualCoreAutoStudy.setEnabled(service, true))
        val lock = ContinuumCdxLargeLab::class.java.getDeclaredField("LOCK")
            .apply { isAccessible = true }.get(null)
        val workerField = ContinuumAutonomousJobService::class.java.getDeclaredField("worker")
            .apply { isAccessible = true }
        var worker: Thread? = null
        try {
            synchronized(lock) {
                assertTrue(service.onStartJob(Shadow.newInstanceOf(JobParameters::class.java)))
                worker = workerField.get(service) as Thread
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (worker!!.isAlive && System.nanoTime() < deadline &&
                    !(worker!!.state == Thread.State.BLOCKED && worker!!.stackTrace.any {
                        it.className == ContinuumCdxLargeLab::class.java.name && it.methodName == "status"
                    })) Thread.sleep(2)
                assertEquals("The real job waits before entering CDX", Thread.State.BLOCKED, worker!!.state)
                assertTrue(worker!!.stackTrace.any {
                    it.className == ContinuumCdxLargeLab::class.java.name && it.methodName == "status"
                })
                stop(service)
            }
            worker!!.join(5_000)
            assertFalse("Bounded job completes", worker!!.isAlive)
            val cdx = ContinuumTrainingMonitor.read(service, ContinuumTrainingMonitor.CDX)
            assertEquals("Budget/owner cancellation is not a training fault", expectedOutcome, cdx.outcome)
            assertEquals(expectedDetail, cdx.detail)
            assertFalse(cdx.active)
            val offline = ContinuumTrainingMonitor.read(service, ContinuumTrainingMonitor.OFFLINE)
            assertEquals(expectedOutcome, offline.outcome)
            assertEquals(expectedDetail, offline.detail)
            assertEquals(if (expectedOutcome == "DEFERRED") "DEFERRED_$expectedDetail" else "INTERRUPTED",
                ContinuumOfflineJobStatus.read(service).phase)
            assertArrayEquals(before, core.modelSnapshot())
            assertEquals(factsBefore, core.activeOwnerFacts())
            assertEquals(0, ContinuumCdxLargeLab(service.filesDir).status().completed)
            assertTrue(shadowOf(service).isJobFinished)
            assertFalse(shadowOf(service).isRescheduleNeeded)
        } finally {
            if (worker?.isAlive == true) {
                service.onStopJob(Shadow.newInstanceOf(JobParameters::class.java))
                worker!!.join(5_000)
            }
            controller.destroy()
        }
    }

    @Test fun expiredBudgetBeforeCdxStartIsDeferredWithoutFailure() = delayedCdxStart(
        { ShadowSystemClock.advanceBy(16, TimeUnit.SECONDS) }, "DEFERRED", "TIME_BUDGET")

    @Test fun ownerPauseBeforeCdxStartIsInterruptedWithoutFailure() = delayedCdxStart(
        { assertTrue(ContinuumAutonomyControl.setLevel(it, ContinuumAutonomyLevel.PAUSED)) },
        "INTERRUPTED", "Lernlauf abgebrochen")

    @Test fun realStorageFailureRemainsFailedAndRetryable() {
        val controller = Robolectric.buildService(ContinuumAutonomousJobService::class.java).create()
        val service = controller.get()
        ShadowStatFs.registerStats(service.filesDir, 1_000_000, 900_000, 900_000)
        service.sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED)
            .putExtra(BatteryManager.EXTRA_LEVEL, 100).putExtra(BatteryManager.EXTRA_SCALE, 100))
        val blockedDirectory = java.io.File(service.filesDir, "learning-core-v2")
        assertFalse(blockedDirectory.exists())
        blockedDirectory.writeText("synthetic directory obstruction")
        try {
            assertTrue(service.onStartJob(Shadow.newInstanceOf(JobParameters::class.java)))
            val worker = ContinuumAutonomousJobService::class.java.getDeclaredField("worker")
                .apply { isAccessible = true }.get(service) as Thread
            worker.join(5_000)
            assertFalse(worker.isAlive)
            val offline = ContinuumTrainingMonitor.read(service, ContinuumTrainingMonitor.OFFLINE)
            assertEquals("FAILED", offline.outcome)
            assertTrue(offline.detail.isNotBlank())
            assertEquals("FAILED", ContinuumOfflineJobStatus.read(service).phase)
            assertEquals("synthetic directory obstruction", blockedDirectory.readText())
            assertTrue(shadowOf(service).isJobFinished)
            assertTrue(shadowOf(service).isRescheduleNeeded)
        } finally { controller.destroy() }
    }
}
