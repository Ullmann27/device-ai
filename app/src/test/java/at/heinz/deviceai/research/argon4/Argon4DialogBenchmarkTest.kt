package at.heinz.deviceai.research.argon4

import org.junit.Assert.assertEquals
import org.junit.Test

/** The benchmark harness itself: fixed size, deterministic, every probe counted once per system. */
class Argon4DialogBenchmarkTest {
    @Test fun theBenchmarkIsDeterministicAndCountsEveryProbeOncePerSystem() {
        val a = Argon4DialogBenchmark.run(); val b = Argon4DialogBenchmark.run()
        assertEquals(a.getJSONObject("kernel").toString(), b.getJSONObject("kernel").toString())
        val n = Argon4DialogBenchmark.CATEGORIES.size * Argon4DialogBenchmark.PER_CATEGORY
        for (system in listOf("kernelTotal", "ctrlA_total", "ctrlB_total")) {
            val t = a.getJSONObject(system)
            assertEquals(system, n, t.getInt("correct") + t.getInt("wrong") + t.getInt("clarify") + t.getInt("notApplicable"))
        }
        assertEquals(n, Argon4DialogBenchmark.dialogs().size)
    }
}
