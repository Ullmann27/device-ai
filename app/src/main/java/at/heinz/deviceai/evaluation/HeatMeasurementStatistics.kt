package at.heinz.deviceai.evaluation

import org.json.JSONArray
import org.json.JSONObject

/** Statistics of the heat measurement. The host tool tools/heat-report implements the same definitions. */
object HeatStatistics {
    /** Median with the mean of the two middle values for an even count; null for an empty list. */
    fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
    }

    fun medianOfLongs(values: List<Long>): Double? = median(values.map(Long::toDouble))
}

/**
 * Durations in fixed 0.5 ms classes up to 150 ms, everything longer in one overflow class with its exact maximum.
 * Used for native window frames and for the stage's delivered animation frames, so that a report stays small
 * however many frames a run had. A percentile is the UPPER edge of the class that holds its rank (nearest rank),
 * i.e. never better than measured; in the overflow class it is the measured maximum.
 */
class HeatHistogram(private val counts: IntArray = IntArray(BUCKETS), overflow: Int = 0, maxMs: Double = 0.0, sumMs: Double = 0.0) {
    var overflow: Int = overflow; private set
    var maxMs: Double = maxMs; private set
    var sumMs: Double = sumMs; private set

    init { require(counts.size == BUCKETS) }

    val count: Int get() = counts.sum() + overflow

    fun add(valueMs: Double) {
        if (!valueMs.isFinite() || valueMs < 0.0) return
        val index = Math.floor(valueMs / BUCKET_MS).toInt()
        if (index >= BUCKETS) overflow++ else counts[index]++
        if (valueMs > maxMs) maxMs = valueMs
        sumMs += valueMs
    }

    fun percentile(percent: Int): Double? {
        val total = count
        if (total == 0) return null
        val rank = Math.ceil(percent / 100.0 * total).toInt().coerceIn(1, total)
        var seen = 0
        for (index in 0 until BUCKETS) {
            seen += counts[index]
            if (seen >= rank) return (index + 1) * BUCKET_MS
        }
        return maxMs
    }

    /** Only classes that hold something: [[class index, count], …]. */
    fun toJson(): JSONObject {
        val sparse = JSONArray()
        counts.forEachIndexed { index, value -> if (value > 0) sparse.put(JSONArray().put(index).put(value)) }
        return JSONObject().put("bucketMs", BUCKET_MS).put("bucketCount", BUCKETS).put("counts", sparse)
            .put("overflow", overflow).put("maxMs", round(maxMs)).put("sumMs", round(sumMs))
    }

    companion object {
        const val BUCKET_MS = 0.5
        const val BUCKETS = 300

        /** Null when [json] is not a histogram of this class layout. */
        fun parse(json: JSONObject?): HeatHistogram? {
            if (json == null || json.optDouble("bucketMs") != BUCKET_MS || json.optInt("bucketCount") != BUCKETS) return null
            val sparse = json.optJSONArray("counts") ?: return null
            val counts = IntArray(BUCKETS)
            for (position in 0 until sparse.length()) {
                val entry = sparse.optJSONArray(position) ?: return null
                val index = entry.optInt(0, -1)
                val value = entry.optInt(1, -1)
                if (index !in 0 until BUCKETS || value < 0) return null
                counts[index] = value
            }
            return HeatHistogram(counts, json.optInt("overflow"), json.optDouble("maxMs", 0.0), json.optDouble("sumMs", 0.0))
        }

        internal fun round(value: Double): Double = Math.round(value * 1_000.0) / 1_000.0
    }
}

/**
 * Collects the frames of one turn. [vsyncMs] is the display's frame period as Android reported it at the start of
 * the turn (null: not reported – then no share of slow frames is stated). Thread-safe: frames arrive on the
 * thread Android delivers them on.
 */
class HeatFrameAccumulator(private val vsyncMs: Double?) {
    private val histogram = HeatHistogram()
    private var over2Vsync = 0
    private var dropped = 0
    private var firstAtMs: Long? = null
    private var lastAtMs: Long? = null
    private val perSecond = ArrayList<Int>()

    /** [atMs] on the measurement clock, [durationMs] the frame's total duration, [droppedReports] reports Android skipped. */
    @Synchronized fun frame(atMs: Long, durationMs: Double, droppedReports: Int = 0) {
        if (!durationMs.isFinite() || durationMs < 0.0) return
        val first = firstAtMs ?: atMs.also { firstAtMs = it }
        lastAtMs = atMs
        histogram.add(durationMs)
        if (vsyncMs != null && durationMs > 2 * vsyncMs) over2Vsync++
        dropped += droppedReports.coerceAtLeast(0)
        val second = ((atMs - first).coerceAtLeast(0L) / 1_000L).toInt()
        if (second < MAX_SECONDS) {
            while (perSecond.size <= second) perSecond.add(0)
            perSecond[second] = perSecond[second] + 1
        }
    }

    /** [observedMs]: how long frames were collected. No frame in that time is a measured zero, not a gap. */
    @Synchronized fun toJson(observedMs: Long): JSONObject {
        val frames = histogram.count
        val json = JSONObject().put("status", MEASURED).put("frames", frames).put("observedMs", observedMs)
            .put("framesPerSecond", if (observedMs > 0) HeatHistogram.round(frames * 1_000.0 / observedMs) else JSONObject.NULL)
            .put("vsyncMs", vsyncMs?.let(HeatHistogram::round) ?: JSONObject.NULL)
            .put("p50Ms", histogram.percentile(50) ?: JSONObject.NULL)
            .put("p90Ms", histogram.percentile(90) ?: JSONObject.NULL)
            .put("p99Ms", histogram.percentile(99) ?: JSONObject.NULL)
            .put("maxMs", if (frames > 0) HeatHistogram.round(histogram.maxMs) else JSONObject.NULL)
            .put("over2VsyncFrames", if (vsyncMs != null) over2Vsync else JSONObject.NULL)
            .put("over2VsyncShare", if (vsyncMs != null && frames > 0) HeatHistogram.round(over2Vsync.toDouble() / frames) else JSONObject.NULL)
            .put("droppedReports", dropped)
            .put("perSecond", JSONArray(perSecond))
            .put("histogram", histogram.toJson())
        return json
    }

    companion object {
        const val MEASURED = "MEASURED"
        const val MAX_SECONDS = 900

        fun notAvailable(reason: String): JSONObject =
            JSONObject().put("status", HeatMeasurement.NOT_AVAILABLE).put("reason", reason.take(200))
    }
}

/**
 * The stage's own frame counter (native-bridge.js, `ContinuumNative.frameProbe`) as the page returned it. The page
 * measures where its frame governor hands animation frames to the scene, orb and avatar. Anything that is not a
 * well-formed answer of that counter becomes NOT_AVAILABLE with the reason; nothing is filled in.
 */
object HeatStageFrames {
    const val PROBE_SCHEMA = "continuum-stage-frame-probe-v1"

    fun parse(raw: String?, observedMs: Long): JSONObject {
        if (raw == null || raw == "null" || raw.isBlank())
            return HeatFrameAccumulator.notAvailable("Die Bühne hat keinen Bildzähler gemeldet (Seite nicht bereit oder ohne Messpunkt).")
        val probe = runCatching { JSONObject(raw) }.getOrNull()
        if (probe == null || probe.optString("schema") != PROBE_SCHEMA)
            return HeatFrameAccumulator.notAvailable("Antwort des Bildzählers der Bühne nicht lesbar.")
        val histogram = HeatHistogram.parse(probe.optJSONObject("histogram"))
            ?: return HeatFrameAccumulator.notAvailable("Bildabstände der Bühne in unbekanntem Format.")
        val delivered = probe.optInt("delivered", -1)
        if (delivered < 0) return HeatFrameAccumulator.notAvailable("Bildzähler der Bühne ohne Anzahl.")
        return JSONObject().put("status", HeatFrameAccumulator.MEASURED)
            .put("delivered", delivered).put("observedMs", observedMs)
            .put("deliveredPerSecond", if (observedMs > 0) HeatHistogram.round(delivered * 1_000.0 / observedMs) else JSONObject.NULL)
            .put("displayTicks", probe.optInt("ticks"))
            .put("pageObservedMs", if (probe.isNull("observedMs")) JSONObject.NULL else probe.optLong("observedMs"))
            .put("intervalP50Ms", histogram.percentile(50) ?: JSONObject.NULL)
            .put("intervalP90Ms", histogram.percentile(90) ?: JSONObject.NULL)
            .put("intervalP99Ms", histogram.percentile(99) ?: JSONObject.NULL)
            .put("intervalMaxMs", if (histogram.count > 0) HeatHistogram.round(histogram.maxMs) else JSONObject.NULL)
            .put("frameLimit", if (probe.isNull("limit")) JSONObject.NULL else probe.optInt("limit"))
            .put("frameLimitChanges", probe.optJSONArray("limitChanges") ?: JSONArray())
            .put("pageVisibility", probe.optString("visibility", "unknown"))
            .put("perSecond", probe.optJSONArray("perSecond") ?: JSONArray())
            .put("histogram", histogram.toJson())
    }
}
