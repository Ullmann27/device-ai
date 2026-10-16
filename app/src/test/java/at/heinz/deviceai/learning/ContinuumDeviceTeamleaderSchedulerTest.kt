package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test

class ContinuumDeviceTeamleaderSchedulerTest {
    private class Jobs(var old: DeviceTeamleaderJobSpec? = null): DeviceTeamleaderJobPort {
        var called = 0
        var acceptance = true
        override fun pending(): DeviceTeamleaderJobSpec? = old
        override fun schedule(spec: DeviceTeamleaderJobSpec): Boolean {
            called++
            if (acceptance) old = spec
            return acceptance
        }
    }

    @Test fun schedulingSameJobNeverInterruptsBackgroundWork() {
        val jobs = Jobs()
        val scheduler = ContinuumDeviceTeamleaderScheduler(jobs)
        repeat(6) { assertTrue(scheduler.restore()) }
        assertEquals(1, jobs.called)
        assertEquals(ContinuumDeviceTeamleaderScheduler.WANTED, jobs.old)
    }

    @Test fun androidFirstSchedulingRunsUnpluggedWithNoInternetDependency() {
        val s = ContinuumDeviceTeamleaderScheduler.WANTED
        assertEquals(60L * 60L * 1000L, s.periodMs)
        assertFalse("do not require charger", s.charging)
        assertTrue(s.persisted)
        assertTrue(s.batteryNotLow)
        assertTrue(s.storageNotLow)
        assertEquals(android.app.job.JobInfo.NETWORK_TYPE_NONE, s.networkType)
    }

    @Test fun mismatchedJobIsReplacedOnceAndFailedSchedulesStayFailed() {
        val jobs = Jobs(ContinuumDeviceTeamleaderScheduler.WANTED.copy(charging=true))
        assertTrue(ContinuumDeviceTeamleaderScheduler(jobs).restore())
        assertEquals(1, jobs.called)
        jobs.old = null
        jobs.acceptance = false
        assertFalse(ContinuumDeviceTeamleaderScheduler(jobs).restore())
        assertEquals(2, jobs.called)
    }
}
