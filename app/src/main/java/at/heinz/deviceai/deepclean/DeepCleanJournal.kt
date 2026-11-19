package at.heinz.deviceai.deepclean

import org.json.JSONArray
import org.json.JSONObject

/** App-private text storage of the lane (journal, learned protections, storage samples). Fakes in host tests. */
internal interface DeepCleanStore {
    fun read(name: String): String?
    /** Writes atomically; null removes. */
    fun write(name: String, text: String?)
}

internal class MemoryDeepCleanStore : DeepCleanStore {
    val values = LinkedHashMap<String, String>()
    var writes = 0
    override fun read(name: String): String? = values[name]
    override fun write(name: String, text: String?) { writes++; if (text == null) values.remove(name) else values[name] = text }
}

/** The state of one item of a run. Only [REMOVED] and [TRASHED] count as freed by this app. */
internal enum class DeepCleanItemState {
    PLANNED,
    /** Written right before the removal call: after a restart this item is checked, never removed a second time. */
    INTENT,
    REMOVED,
    TRASHED,
    /** Changed since the preview (size, time, content, place): not touched. */
    CHANGED,
    FAILED,
    /** Android (or the owner in Android's dialog) refused. Nothing happened. */
    REFUSED,
    /** After a restart the file was gone, but this app cannot prove that it removed it. Not counted. */
    GONE_UNCONFIRMED,
    /** Not carried out (stop, restart, deselected). */
    SKIPPED,
    RESTORED
}

/** One file of a run – everything the pre-delete check compares against. */
internal data class DeepCleanItem(
    val id: Int,
    val root: DeepCleanRoot,
    val relative: String,
    val sizeBytes: Long,
    val modifiedMillis: Long,
    val level: DeepCleanLevel,
    val category: DeepCleanCategory,
    val recovery: DeepCleanRecovery,
    val sha256: String? = null,
    val keeperRelative: String? = null,
    var state: DeepCleanItemState = DeepCleanItemState.PLANNED,
    var note: String? = null,
    /** Android's handle for the trash (a MediaStore content URI) once one was found. */
    var handle: String? = null,
    var doneAtMillis: Long? = null
) {
    val key: String get() = root.name + ":" + relative
    val name: String get() = relative.substringAfterLast('/')

    companion object {
        fun of(id: Int, finding: DeepCleanFinding) = DeepCleanItem(id, finding.entry.root, finding.entry.relative, finding.entry.sizeBytes,
            finding.entry.modifiedMillis, finding.level, finding.category, finding.recovery, finding.sha256, finding.keeper?.relative)
    }
}

/** One run of the lane as the journal keeps it. */
internal class DeepCleanRun(
    val id: String,
    val mode: DeepCleanMode,
    val createdAtMillis: Long,
    val items: MutableList<DeepCleanItem> = ArrayList(),
    var freeBeforeBytes: Long? = null,
    var freeAfterBytes: Long? = null,
    var finished: Boolean = false,
    var stopped: Boolean = false,
    /** The owner was told how this run ended – after a restart it is never reported again. */
    var reported: Boolean = false
) {
    val removed: List<DeepCleanItem> get() = items.filter { it.state == DeepCleanItemState.REMOVED || it.state == DeepCleanItemState.TRASHED }
    /** Sum of the verified sizes of the files this run really removed or trashed. */
    val removedBytes: Long get() = removed.sumOf { it.sizeBytes }
}

/**
 * The persisted run journal. Every change of an item's state is written before the next file is touched, so a run
 * interrupted by a crash or restart is found in [recover]: an item caught between INTENT and its result is checked
 * against the file system and never removed again, and the run's end is reported once.
 */
internal class DeepCleanJournal(private val store: DeepCleanStore, private val now: () -> Long) {
    private val runs: MutableList<DeepCleanRun> = load()

    fun runs(): List<DeepCleanRun> = runs.toList()
    fun latest(): DeepCleanRun? = runs.lastOrNull()
    fun run(id: String): DeepCleanRun? = runs.firstOrNull { it.id == id }

    fun begin(mode: DeepCleanMode, findings: List<DeepCleanFinding>, freeBefore: Long?): DeepCleanRun {
        val run = DeepCleanRun("dc-" + now() + "-" + (runs.size + 1), mode, now(),
            findings.mapIndexed { index, finding -> DeepCleanItem.of(index + 1, finding) }.toMutableList(), freeBefore)
        runs += run
        while (runs.size > MAX_RUNS) runs.removeAt(0)
        save()
        return run
    }

    fun save() = store.write(NAME, encode(runs))

    /**
     * After a restart: every unfinished run is closed. INTENT items are looked at: gone → [DeepCleanItemState.GONE_UNCONFIRMED]
     * (not counted as freed), still there → [DeepCleanItemState.SKIPPED]. Returns the runs whose end the owner has not
     * been told yet, and marks them as told – the same interruption is never reported twice.
     */
    fun recover(stat: (DeepCleanItem) -> DeepCleanStamp?): List<DeepCleanRun> {
        val open = runs.filter { !it.finished }
        for (run in open) {
            for (item in run.items) when (item.state) {
                DeepCleanItemState.INTENT -> {
                    item.state = if (stat(item) == null) DeepCleanItemState.GONE_UNCONFIRMED else DeepCleanItemState.SKIPPED
                    item.note = "nach einem Neustart nicht fortgesetzt"
                }
                DeepCleanItemState.PLANNED -> if (run.mode.mayRemove && item.level != DeepCleanLevel.RED) {
                    item.state = DeepCleanItemState.SKIPPED
                    item.note = "nach einem Neustart nicht fortgesetzt"
                }
                else -> Unit
            }
            run.finished = true
            run.stopped = true
        }
        val untold = runs.filter { it.finished && !it.reported && it in open }
        untold.forEach { it.reported = true }
        if (open.isNotEmpty()) save()
        return untold
    }

    /** Self-maintenance (E4): runs older than [MAX_AGE_MILLIS] are dropped, the newest are always kept. Returns the count. */
    fun prune(): Int {
        val limit = now() - MAX_AGE_MILLIS
        val old = runs.dropLast(KEEP_ALWAYS).filter { it.finished && it.createdAtMillis < limit }
        if (old.isEmpty()) return 0
        runs.removeAll(old.toSet())
        save()
        return old.size
    }

    private fun load(): MutableList<DeepCleanRun> = runCatching {
        val array = JSONArray(store.read(NAME) ?: return ArrayList())
        (0 until array.length()).map { decode(array.getJSONObject(it)) }.toMutableList()
    }.getOrDefault(ArrayList())

    companion object {
        const val NAME = "journal"
        const val MAX_RUNS = 12
        const val KEEP_ALWAYS = 3
        const val MAX_AGE_MILLIS = 30L * 24 * 3_600_000L

        fun encode(runs: List<DeepCleanRun>): String = JSONArray().apply {
            runs.forEach { run ->
                put(JSONObject().put("id", run.id).put("mode", run.mode.name).put("at", run.createdAtMillis)
                    .put("before", run.freeBeforeBytes ?: JSONObject.NULL).put("after", run.freeAfterBytes ?: JSONObject.NULL)
                    .put("finished", run.finished).put("stopped", run.stopped).put("reported", run.reported)
                    .put("items", JSONArray().apply {
                        run.items.forEach { item ->
                            put(JSONObject().put("id", item.id).put("root", item.root.name).put("path", item.relative)
                                .put("size", item.sizeBytes).put("mtime", item.modifiedMillis).put("level", item.level.name)
                                .put("category", item.category.name).put("recovery", item.recovery.name)
                                .put("sha", item.sha256 ?: JSONObject.NULL).put("keeper", item.keeperRelative ?: JSONObject.NULL)
                                .put("state", item.state.name).put("note", item.note ?: JSONObject.NULL)
                                .put("handle", item.handle ?: JSONObject.NULL).put("done", item.doneAtMillis ?: JSONObject.NULL))
                        }
                    }))
            }
        }.toString()

        private fun JSONObject.text(key: String): String? = if (isNull(key)) null else optString(key)
        private fun JSONObject.long(key: String): Long? = if (!has(key) || isNull(key)) null else getLong(key)

        fun decode(row: JSONObject): DeepCleanRun {
            val items = row.getJSONArray("items")
            return DeepCleanRun(row.getString("id"), DeepCleanMode.valueOf(row.getString("mode")), row.getLong("at"),
                (0 until items.length()).map { index ->
                    val item = items.getJSONObject(index)
                    DeepCleanItem(item.getInt("id"), DeepCleanRoot.valueOf(item.getString("root")), item.getString("path"),
                        item.getLong("size"), item.getLong("mtime"), DeepCleanLevel.valueOf(item.getString("level")),
                        DeepCleanCategory.valueOf(item.getString("category")), DeepCleanRecovery.valueOf(item.getString("recovery")),
                        item.text("sha"), item.text("keeper"), DeepCleanItemState.valueOf(item.getString("state")), item.text("note"),
                        item.text("handle"), item.long("done"))
                }.toMutableList(), row.long("before"), row.long("after"), row.getBoolean("finished"), row.getBoolean("stopped"),
                row.getBoolean("reported"))
        }
    }
}

/**
 * E6: what the owner protected – groups he named („Fotos behalten“) and folders whose proposals he turned down.
 * Something refused [THRESHOLD] times is no longer proposed; „Schlag mir wieder alles vor“ forgets it.
 */
internal class DeepCleanLearning(private val store: DeepCleanStore) {
    private val counts: MutableMap<String, Int> = runCatching {
        val row = JSONObject(store.read(NAME) ?: return@runCatching LinkedHashMap())
        row.keys().asSequence().associateWith { row.getInt(it) }.toMutableMap()
    }.getOrDefault(LinkedHashMap())

    fun refused(keys: Collection<String>) {
        if (keys.isEmpty()) return
        keys.distinct().forEach { counts[it] = (counts[it] ?: 0) + 1 }
        store.write(NAME, JSONObject(counts as Map<*, *>).toString())
    }

    fun protectedKeys(): Set<String> = counts.filterValues { it >= THRESHOLD }.keys
    fun protects(key: String): Boolean = (counts[key] ?: 0) >= THRESHOLD
    fun forget(): Int { val n = protectedKeys().size; counts.clear(); store.write(NAME, null); return n }

    companion object {
        const val NAME = "learning"
        const val THRESHOLD = 2
        fun groupKey(group: DeepCleanGroup) = "GROUP:" + group.name
    }
}

/** One measurement of the data volume: when, how much was free, of how much. */
internal data class DeepCleanSample(val atMillis: Long, val freeBytes: Long, val totalBytes: Long)

/**
 * E5: the storage forecast from really measured samples. At least [MIN_SAMPLES] samples over at least [MIN_DAYS]
 * days, else there is no forecast. The result is a linear trend and is always called an estimate.
 */
internal class DeepCleanForecast(private val store: DeepCleanStore) {
    sealed interface Outlook {
        data class TooFew(val samples: Int, val days: Double) : Outlook
        data class NotShrinking(val samples: Int, val days: Double) : Outlook
        data class FullIn(val days: Double, val bytesPerDay: Double, val samples: Int, val spanDays: Double) : Outlook
    }

    fun samples(): List<DeepCleanSample> = runCatching {
        val array = JSONArray(store.read(NAME) ?: return emptyList())
        (0 until array.length()).map { array.getJSONObject(it).let { row -> DeepCleanSample(row.getLong("at"), row.getLong("free"), row.getLong("total")) } }
    }.getOrDefault(emptyList())

    /** Adds a sample unless one was taken within [MIN_GAP_MILLIS]; true = stored. */
    fun add(sample: DeepCleanSample): Boolean {
        if (sample.freeBytes < 0 || sample.totalBytes <= 0 || sample.freeBytes > sample.totalBytes) return false
        val all = samples()
        if (all.any { kotlin.math.abs(it.atMillis - sample.atMillis) < MIN_GAP_MILLIS }) return false
        val kept = (all + sample).sortedBy { it.atMillis }.takeLast(MAX_SAMPLES)
        store.write(NAME, JSONArray().apply { kept.forEach { put(JSONObject().put("at", it.atMillis).put("free", it.freeBytes).put("total", it.totalBytes)) } }.toString())
        return true
    }

    fun outlook(): Outlook {
        val all = samples()
        val span = if (all.size < 2) 0.0 else (all.last().atMillis - all.first().atMillis) / DAY
        if (all.size < MIN_SAMPLES || span < MIN_DAYS) return Outlook.TooFew(all.size, span)
        val xs = all.map { (it.atMillis - all.first().atMillis) / DAY }
        val ys = all.map { it.freeBytes.toDouble() }
        val mx = xs.average()
        val my = ys.average()
        val slope = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / xs.indices.sumOf { (xs[it] - mx) * (xs[it] - mx) }
        if (slope >= 0 || slope.isNaN()) return Outlook.NotShrinking(all.size, span)
        return Outlook.FullIn(all.last().freeBytes / -slope, -slope, all.size, span)
    }

    companion object {
        const val NAME = "samples"
        const val MIN_SAMPLES = 3
        const val MIN_DAYS = 3.0
        const val MAX_SAMPLES = 120
        const val MIN_GAP_MILLIS = 3_600_000L
        private const val DAY = 24 * 3_600_000.0
    }
}
