package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuumAutonomousSchedulerTest {
    /** Behaves like JobScheduler: schedule() replaces the pending job (and would stop a running one). */
    private class FakeJobs(var pendingSpec: ContinuumJobSpec? = null) : ContinuumJobPort {
        var scheduleCalls = 0
        var failWith: RuntimeException? = null
        var accept = true
        override fun pending() = pendingSpec
        override fun schedule(spec: ContinuumJobSpec): Boolean {
            scheduleCalls++
            failWith?.let { throw it }
            if (accept) pendingSpec = spec
            return accept
        }
    }

    private val records = mutableListOf<Pair<Boolean, String?>>()
    private fun scheduler(jobs: FakeJobs) = ContinuumAutonomousScheduler(jobs) { accepted, error -> records += accepted to error }

    @Test fun everyAppStartAfterTheFirstLeavesTheIdenticalPendingJobAlone() {
        val jobs = FakeJobs()
        val scheduler = scheduler(jobs)
        repeat(5) { assertTrue(scheduler.restore()) }
        assertEquals("a running job must not be replaced (and thereby stopped) on app start", 1, jobs.scheduleCalls)
        assertEquals(List(5) { true to null }, records)
    }

    @Test fun aPendingJobWithOtherConstraintsIsReplacedOnce() {
        val old = ContinuumAutonomousScheduler.WANTED.copy(requiresCharging = true, persisted = false)
        val jobs = FakeJobs(old)
        val scheduler = scheduler(jobs)
        assertTrue(scheduler.restore())
        assertTrue(scheduler.restore())
        assertEquals(1, jobs.scheduleCalls)
        assertEquals(ContinuumAutonomousScheduler.WANTED, jobs.pendingSpec)
    }

    @Test fun refusedOrThrowingSchedulesAreRecordedNotHidden() {
        val refused = FakeJobs().apply { accept = false }
        assertFalse(scheduler(refused).restore())
        val quota = FakeJobs().apply { failWith = IllegalStateException("Apps may not schedule more than 150 distinct jobs") }
        assertFalse(scheduler(quota).restore())
        assertEquals(listOf(false to null, false to "IllegalStateException"), records)
    }

    @Test fun plannedJobIsOfflinePersistedDailyAndWorksWithoutCharging() {
        val wanted = ContinuumAutonomousScheduler.WANTED
        assertEquals(ContinuumAutonomousScheduler.SERVICE_CLASS, wanted.serviceClass)
        assertEquals(24 * 60 * 60 * 1000L, wanted.periodMs)
        assertFalse(wanted.requiresCharging)
        assertTrue(wanted.persisted)
        assertTrue(wanted.requiresBatteryNotLow)
        assertTrue(wanted.requiresStorageNotLow)
        assertEquals(android.app.job.JobInfo.NETWORK_TYPE_NONE, wanted.networkType)
    }

    @Test fun scheduleErrorIsShownWithTheFailedSchedule() {
        val text = ContinuumOfflineJobSnapshot(false, "NOT_RUN", 0, 0, "", "IllegalArgumentException").display()
        assertTrue(text.contains("Einplanung fehlgeschlagen (IllegalArgumentException)"))
        val ok = ContinuumOfflineJobSnapshot(true, "NOT_RUN", 0, 0, "", "IllegalArgumentException").display()
        assertFalse("a stale error is not shown next to a successful schedule", ok.contains("IllegalArgumentException"))
    }
    @Test fun resourceDeferralsAndHistoryAreVisibleWithoutSourceData() {
        val status = ContinuumOfflineJobSnapshot(
            true,"DEFERRED_THERMAL_LIMIT",100L,200L,"","",
            "100:RUNNING|200:DEFERRED_THERMAL_LIMIT"
        ).display()
        assertTrue(status.contains("DEFERRED_THERMAL_LIMIT"))
        assertTrue(status.contains("100:RUNNING|200:DEFERRED_THERMAL_LIMIT"))
        assertFalse(status.contains("Letzter Fehler:"))
    }


}
