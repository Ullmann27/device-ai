package at.heinz.deviceai.cognitive

/**
 * Non-executing memory and thermal budget gate. Bytes are measured, never inferred
 * from an advertised virtual-memory size or marketing claims about storage.
 * The caller supplies actual Android memory/thermal telemetry.
 */
internal object ConstrainedResourceFabric {
    enum class Plan { RUN_LOCAL, LAZY_LOAD, REFUSE_THERMAL, REFUSE_MEMORY, NO_MEASUREMENT }
    data class Snapshot(val freeRamBytes: Long?, val expectedPeakBytes: Long,
        val additionalModelBytes: Long, val thermalStatus: Int?, val hasOwnerConsent: Boolean = true) {
        init {
            require(expectedPeakBytes >= 0 && additionalModelBytes >= 0)
            require(freeRamBytes == null || freeRamBytes >= 0)
            require(thermalStatus == null || thermalStatus in 0..6)
        }
    }

    fun plan(x: Snapshot): Plan {
        if (x.freeRamBytes == null || x.thermalStatus == null) return Plan.NO_MEASUREMENT
        if (x.thermalStatus >= 3) return Plan.REFUSE_THERMAL
        // Keep one quarter of reported free RAM untouched, account for both
        // peak working set and loading cost, and reject overflow.
        val needed = runCatching { Math.addExact(x.expectedPeakBytes, x.additionalModelBytes) }
            .getOrElse { return Plan.REFUSE_MEMORY }
        if (needed > x.freeRamBytes - x.freeRamBytes / 4) return Plan.REFUSE_MEMORY
        if (!x.hasOwnerConsent) return Plan.LAZY_LOAD
        return Plan.RUN_LOCAL
    }
}
