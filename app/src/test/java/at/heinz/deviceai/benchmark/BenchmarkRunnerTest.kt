package at.heinz.deviceai.benchmark

import org.junit.Assert.*
import org.junit.Test

class BenchmarkRunnerTest {
    private class Fake(override val name: String, val f: (BenchmarkTask) -> TaskOutcome) : BenchmarkSystem {
        override fun run(task: BenchmarkTask) = f(task)
    }

    private fun tick(vararg times: Long): BenchmarkClock {
        var i = 0
        return BenchmarkClock { times[i++ % times.size] }
    }

    private val tasks = listOf(BenchmarkTask("t1", "a"), BenchmarkTask("t2", "b"))
    private val ok = Fake("baseline") { TaskOutcome(true, modelCalls = 1, contextBytes = 10) }

    @Test fun emptyTaskSetYieldsEmptyReport() {
        val run = BenchmarkRunner().run(emptyList(), listOf(ok))
        val report = BenchmarkReportAggregator.aggregate(run)
        assertTrue(report.summaries.isEmpty())
        assertFalse(report.cancelled)
        assertEquals(BenchmarkReport.CSV_HEADER + "\n", report.toCsv())
        assertTrue(report.toJson().contains("\"summaries\":[]"))
    }

    @Test fun negativeLatencyIsRecordedAsError() {
        val run = BenchmarkRunner(tick(100, 50)).run(listOf(tasks[0]), listOf(ok))
        val m = run.measurements.single()
        assertFalse(m.success)
        assertEquals(0L, m.latencyMillis)
        assertTrue(m.error!!.contains("negative latency"))
    }

    @Test(expected = IllegalArgumentException::class) fun negativeLatencyRejectedByModel() {
        TaskMeasurement("s", "t", true, null, null, 0, 0, 0, -1, 0, null)
    }

    @Test fun cancellationStopsBeforeNextTask() {
        val flag = CancellationFlag()
        val sys = Fake("baseline") { flag.cancel(); TaskOutcome(true) }
        val run = BenchmarkRunner(tick(0, 1)).run(tasks, listOf(sys), flag)
        assertTrue(run.cancelled)
        assertEquals(1, run.measurements.size)
        assertEquals(1, run.skippedTasks)
    }

    @Test fun cancellationStopsOtherSystemsDuringSameTask() {
        val flag = CancellationFlag()
        var shouldNotRun = 0
        val first = Fake("first") { flag.cancel(); TaskOutcome(true) }
        val second = Fake("second") { shouldNotRun++; TaskOutcome(true) }
        val run = BenchmarkRunner(tick(0, 1)).run(tasks, listOf(first, second), flag)
        assertTrue(run.cancelled)
        assertEquals(1, run.measurements.size)
        assertEquals(0, shouldNotRun)
        assertEquals(2, run.skippedTasks)
    }

    @Test fun benchmarkSystemCountIsBounded() {
        val many = (0..BenchmarkRunner.MAX_SYSTEMS).map { i ->
            Fake("system-" + i) { TaskOutcome(true) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            BenchmarkRunner().run(tasks, many)
        }
    }

    @Test fun preCancelledRunsNothing() {
        val flag = CancellationFlag().also { it.cancel() }
        val run = BenchmarkRunner().run(tasks, listOf(ok), flag)
        assertTrue(run.measurements.isEmpty())
        assertEquals(2, run.skippedTasks)
    }

    @Test fun taskCountBounded() {
        try {
            BenchmarkRunner(maxTasks = 1).run(tasks, listOf(ok))
            fail()
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun counterOverflowSaturates() {
        val big = Fake("baseline") { TaskOutcome(true, modelCalls = Long.MAX_VALUE) }
        val run = BenchmarkRunner(tick(0, 1)).run(tasks, listOf(big))
        assertEquals(Long.MAX_VALUE, BenchmarkReportAggregator.aggregate(run).summaries.single().modelCalls)
    }

    @Test fun negativeCountersRejected() {
        try { TaskOutcome(true, modelCalls = -1); fail() } catch (_: IllegalArgumentException) {}
    }

    @Test fun systemExceptionRecordedAndRunContinues() {
        var n = 0L
        val bad = Fake("continuum") { throw IllegalStateException("boom") }
        val run = BenchmarkRunner(BenchmarkClock { n += 1; n }).run(tasks, listOf(ok, bad))
        assertEquals(4, run.measurements.size)
        assertEquals(2, run.measurements.count { it.error != null })
    }

    @Test fun unmeasuredFieldsAreNullAndOutputDeterministic() {
        val a = BenchmarkReportAggregator.aggregate(BenchmarkRunner(tick(0, 5)).run(tasks, listOf(ok)))
        val b = BenchmarkReportAggregator.aggregate(BenchmarkRunner(tick(0, 5)).run(tasks, listOf(ok)))
        assertEquals(a.toJson(), b.toJson())
        assertEquals(a.toCsv(), b.toCsv())
        val s = a.summaries.single()
        assertNull(s.meanFactualSupport)
        assertNull(s.recoveries)
        assertTrue(a.toJson().contains("\"meanFactualSupport\":null"))
        assertTrue(a.toCsv().lines()[1].contains("null"))
        assertEquals(10L, s.totalLatencyMillis)
    }

    @Test fun measuredValuesAggregate() {
        val sys = Fake("continuum") {
            TaskOutcome(true, 0.5, true, 1, 3, 100, 2, true)
        }
        val s = BenchmarkReportAggregator.aggregate(BenchmarkRunner(tick(0, 2)).run(tasks, listOf(sys))).summaries.single()
        assertEquals(0.5, s.meanFactualSupport!!, 0.0)
        assertEquals(2, s.contradictionsDetected)
        assertEquals(6L, s.modelCalls)
        assertEquals(2, s.recoveries)
    }

    @Test fun csvEscapesAndJsonEscapes() {
        val sys = Fake("a,b") { TaskOutcome(true) }
        val r = BenchmarkReportAggregator.aggregate(BenchmarkRunner(tick(0, 1)).run(listOf(BenchmarkTask("q\"1", "x")), listOf(sys)))
        assertTrue(r.toCsv().contains("\"a,b\",\"q\"\"1\""))
        assertTrue(r.toJson().contains("q\\\"1"))
    }
}
