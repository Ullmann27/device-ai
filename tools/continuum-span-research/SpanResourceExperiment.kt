package at.heinz.deviceai.learning.span

import java.io.File
import java.lang.management.ManagementFactory
import org.json.JSONObject

/** Host-only timing/allocation probe on consumed DEV input; not a new accuracy benchmark. */
object SpanResourceExperiment {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 2)
        val checkpoint = File(args[0]).inputStream().use(NaturalLocationLabComparison::readCheckpoint)
        val cases = listOf("Eva ist in Bern. Otto ist in Kiel." to "Wo ist Eva?",
            "Otto ist in Bern. Eva ist in Kiel." to "Wo ist Eva?",
            "Eva ist nicht in Bern." to "Wo ist Eva?",
            "Eva ist in Bern. Eva ist in Kiel." to "Wo ist Eva?",
            "Otto ist in Kiel." to "Wo ist Eva?")
        repeat(25) { val c=cases[it%cases.size]; NaturalLocationLabComparison.compare(c.first,c.second,checkpoint) }
        val allocation = (ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean)
            ?.takeIf { it.isThreadAllocatedMemorySupported }
        if (allocation != null) allocation.isThreadAllocatedMemoryEnabled = true
        val allocatedBefore = allocation?.getThreadAllocatedBytes(Thread.currentThread().id)
        val heapBefore = ManagementFactory.getMemoryMXBean().heapMemoryUsage.used
        val times = (0 until 200).map { i ->
            val c = cases[i % cases.size]
            val started = System.nanoTime()
            NaturalLocationLabComparison.compare(c.first,c.second,checkpoint)
            (System.nanoTime()-started)/1_000_000.0
        }.sorted()
        val allocatedAfter = allocation?.getThreadAllocatedBytes(Thread.currentThread().id)
        val result = JSONObject().put("scope","HOST_ONLY, consumed DEV, explicit lab compare incl snapshot/model restore/verification")
            .put("java",System.getProperty("java.version")).put("osArch",System.getProperty("os.arch"))
            .put("warmup",25).put("samples",200).put("distinctInputs",cases.size)
            .put("p50Ms",times[99]).put("p95Ms",times[189]).put("maxMs",times.last())
            .put("threadAllocatedBytesPerCompare",if (allocatedBefore != null && allocatedAfter != null) (allocatedAfter-allocatedBefore)/200 else JSONObject.NULL)
            .put("heapUsedBefore",heapBefore).put("heapUsedAfter",ManagementFactory.getMemoryMXBean().heapMemoryUsage.used)
            .put("checkpointBytes",checkpoint.size).put("parameters",4110)
            .put("qualityBenchmark",false).put("energyTemperatureAndroidNativeMemory","UNAVAILABLE_NO_CONNECTED_FOLD7")
        File(args[1]).writeText(result.toString(2)); println(result)
    }
}
