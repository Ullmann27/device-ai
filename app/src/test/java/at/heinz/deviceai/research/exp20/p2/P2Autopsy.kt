package at.heinz.deviceai.research.exp20.p2

import java.io.File

/**
 * EXPLORATORY error autopsy of the already opened SEALED_HOLDOUT (no re-selection, no refit, no new decision).
 * Each holdout turn is matched to the holdout-bank template it was built from; a turn is "encoder-blind" when the frozen
 * encoder does not see that template's intended cue in the concrete text (unknown phrasing or unknown room).
 * A variant is "time out of TRAIN range" when its last pause or its referent age exceeds the DEV maximum of 3 h.
 *   autopsy <dataDir> <runDir> <outFile>
 */
object P2Autopsy {
    private fun rx(template: String) = Regex(Regex.escape(template)
        .replace("{N}", "\\E.+?\\Q").replace("{A}", "\\E.+?\\Q").replace("{B}", "\\E.+?\\Q").replace("{ROOM}", "\\E.+?\\Q"))

    private fun classes(b: Bank): List<Pair<Regex, String>> {
        val out = ArrayList<Pair<Regex, String>>()
        fun add(ts: List<String>, f: String) = ts.forEach { out += rx(it) to f }
        add(b.shortReferents, "DEVICE_OR_PLACE"); add(b.shortDecisions, "SHORT_REF")
        add(b.longPairs.flatMap { listOf(it.first, it.second) }, "LONG_REF")
        add(b.longCorrectionTriples.map { it.first } + b.longCorrectionTriples.map { it.third }, "LONG_REF")
        add(b.longCorrectionTriples.map { it.second } + b.corrections + b.maleCorrection + b.femaleCorrection, "CORRECTION")
        add(b.switches, "SWITCH"); add(b.presence, "PRESENCE_UNCERTAIN")
        add(b.devicePairs.map { it.first }, "DEVICE_OR_PLACE"); add(b.devicePairs.map { it.second }, "SHORT_REF")
        add(b.maleIntro + b.femaleIntro + b.maleTwo + b.femaleTwo + b.mechanicMale, "PERSON_INTRO")
        add(b.maleDecisions + b.femaleDecisions, "PERSON_PRONOUN"); add(b.repairStatements, "LONG_REF")
        return out
    }

    @JvmStatic fun main(args: Array<String>) {
        val hold = P2Tsv.read(File(args[0], "holdout.tsv")); val cls = classes(P2HoldoutBank.bank)
        val blindTurns = HashMap<String, Int>(); val seenTurns = HashMap<String, Int>()
        val flags = hold.associate { d ->
            var blind = false
            for (t in d.turns) for ((r, f) in cls) if (r.matches(t.text)) {
                val sees = P2Encoder.encode(t.text)[P2Encoder.NAMES.indexOf(f)] > 0.0
                (if (sees) seenTurns else blindTurns).merge(f, 1, Int::plus); if (!sees) blind = true
            }
            val gaps = d.turns.zipWithNext { a, b -> b.tSec - a.tSec }
            val oor = (gaps.maxOrNull() ?: 0.0) > 10_800.0 || (d.decision.tSec - d.turns.first().tSec) > 10_800.0
            (d.clusterId to d.variant) to (blind to oor)
        }
        val rows = File(args[1], "holdout-decisions.tsv").readLines().drop(1).map { it.split('\t') }
        val sb = StringBuilder("EXP-20 P2 EXPLORATORY holdout autopsy (opened holdout, frozen arms, no re-selection)\n")
        sb.append("turn-level cue visibility per intended feature (seen/blind):\n")
        for (f in P2Encoder.NAMES) sb.append("  %-19s seen=%d blind=%d\n".format(f, seenTurns[f] ?: 0, blindTurns[f] ?: 0))
        sb.append("variants: encoder-blind=${flags.values.count { it.first }}/${flags.size}, time out of TRAIN range=${flags.values.count { it.second }}/${flags.size}\n")
        sb.append("\nturn accuracy by condition (correct/total)\n")
        for (arm in ArmId.values()) {
            val rs = rows.filter { it[0] == arm.name }
            fun acc(p: (Pair<Boolean, Boolean>) -> Boolean) = rs.filter { p(flags.getValue(it[1] to it[2].toInt())) }.let { x -> "${x.count { it[10] == "1" }}/${x.size}" }
            sb.append("  %-6s clean=%-9s encoderBlind=%-9s timeOOR=%-9s both=%s\n".format(arm.name,
                acc { !it.first && !it.second }, acc { it.first && !it.second }, acc { !it.first && it.second }, acc { it.first && it.second }))
        }
        File(args[2]).writeText(sb.toString()); print(sb)
    }
}
