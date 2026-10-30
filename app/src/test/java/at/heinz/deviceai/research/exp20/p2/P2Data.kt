package at.heinz.deviceai.research.exp20.p2

import java.io.File
import java.security.MessageDigest

enum class Split { TRAIN, VALIDATION, SEALED_HOLDOUT }
enum class Gold { PROCEED, CLARIFY }
enum class Cause { NONE, LOCATION_SWITCH, STALE_GAP, AMBIGUOUS_ENTITY, PRESENCE_DRIFT, NO_REFERENT }
enum class Scenario { S1_LOCATION, S2_CORRECTION, S3_INTERRUPTION, S4_NESTED_ENTITIES, S5_SAME_SIGNALS_DT, S6_PRESENCE_DRIFT, S7_AMBIGUOUS }
enum class ErrorType {
    FALSE_CONTEXT_SWITCH, MISSED_CONTEXT_SWITCH, STALE_FACT_LEAKAGE, WRONG_ENTITY_BINDING, UNNECESSARY_CLARIFICATION,
    UNAUTHORIZED_ACTION_SUGGESTION
}

/** One user turn on the session's monotonic clock (seconds since session start, never calendar time). */
data class Turn(
    val tSec: Double, val text: String, val decision: Boolean = false, val gold: Gold? = null,
    val cause: Cause = Cause.NONE, val negControl: Boolean = false
)

/** One variant of one dialog stem. All variants of a stem share a cluster id and a split and count as ONE sample. */
data class Dialog(val clusterId: String, val variant: Int, val scenario: Scenario, val split: Split, val turns: List<Turn>) {
    val decision: Turn get() = turns.last()

    fun validate() {
        require(turns.isNotEmpty() && turns.last().decision && turns.last().gold != null) { "$clusterId/$variant: last turn must be the gold decision" }
        require(turns.dropLast(1).none { it.decision }) { "$clusterId/$variant: one decision per variant" }
        require(turns.all { it.tSec.isFinite() && it.tSec >= 0.0 }) { "$clusterId/$variant: finite non-negative times" }
        require(turns.zipWithNext().all { (a, b) -> b.tSec >= a.tSec }) { "$clusterId/$variant: monotonic session clock" }
        require(turns.all { it.text.isNotBlank() && '\t' !in it.text && '\n' !in it.text }) { "$clusterId/$variant: text" }
    }
}

/** Plain TSV so that an independent author can supply a holdout without this generator. */
object P2Tsv {
    const val HEADER = "cluster\tvariant\tscenario\tsplit\tt_sec\tdecision\tgold\tcause\tneg_control\ttext"

    fun write(dialogs: List<Dialog>, file: File) {
        file.parentFile?.mkdirs()
        val sb = StringBuilder(HEADER).append('\n')
        for (d in dialogs) { d.validate(); for (t in d.turns) {
            sb.append(d.clusterId).append('\t').append(d.variant).append('\t').append(d.scenario.name).append('\t')
                .append(d.split.name).append('\t').append(t.tSec.toString()).append('\t').append(if (t.decision) 1 else 0).append('\t')
                .append(t.gold?.name ?: "-").append('\t').append(t.cause.name).append('\t').append(if (t.negControl) 1 else 0)
                .append('\t').append(t.text).append('\n')
        } }
        file.writeText(sb.toString(), Charsets.UTF_8)
    }

    fun read(file: File): List<Dialog> {
        val lines = file.readLines(Charsets.UTF_8)
        require(lines.firstOrNull() == HEADER) { "bad header in $file" }
        val out = ArrayList<Dialog>()
        var key: Pair<String, Int>? = null
        var scenario: Scenario? = null; var split: Split? = null
        var turns = ArrayList<Turn>()
        fun flush() { val k = key ?: return; out += Dialog(k.first, k.second, scenario!!, split!!, turns).also { it.validate() } }
        for (line in lines.drop(1)) {
            if (line.isEmpty()) continue
            val c = line.split('\t', limit = 10)
            require(c.size == 10) { "bad row: $line" }
            val k = c[0] to c[1].toInt()
            if (k != key) { flush(); key = k; scenario = Scenario.valueOf(c[2]); split = Split.valueOf(c[3]); turns = ArrayList() }
            turns += Turn(c[4].toDouble(), c[9], c[5] == "1", if (c[6] == "-") null else Gold.valueOf(c[6]), Cause.valueOf(c[7]), c[8] == "1")
        }
        flush()
        return out
    }

    fun sha256(file: File): String = sha256(file.readBytes())
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
