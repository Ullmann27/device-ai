package at.heinz.deviceai.learning.span

import java.io.File
import java.util.Random
import org.json.JSONArray
import org.json.JSONObject

/** Experimental representation change, not learned syntax or a second verifier.
 * Extends the EXISTING positional options with one exact syntactic slot and two
 * residual features. All existing thirteen weights stay bit-identical.
 * The original verifier still receives the complete original sources afterward. */
object GroundedSlotResidual {
    private val query = Regex("""(?iu)^\s*wo\s+(ist|wohnt|lebt|steht)\s+([\p{L}][\p{L}\p{M}-]{1,31})\s*\?\s*$""")
    private val placeWord = """[\p{Lu}][\p{L}\p{M}’'-]{1,40}"""
    private val place = Regex("""$placeWord(?:\s+(?:(?:am|an|der|de|la)\s+)?$placeWord){0,3}""")
    private val risk = Regex("""(?iu)\b(?:nicht|kein|keine|sondern|stattdessen|gestern|heute|morgen|früher|damals|aktuell|seit|vorher|später|sie|er|es|das|dies|diese|dieser|dieses|jene|jener|korrektur|widerruf|widerrufen|falsch|nein|ungültig)\b|["„“”«»?]""")

    fun options(question: String, sources: List<EvidenceBoundNaturalLocationProbe.Evidence>): List<Cdx2PointerCandidate.Option> {
        val old = Cdx2CounterfactualShadow.options(question, sources)
        fun unchanged() = old.map { Cdx2PointerCandidate.Option(it.found, it.features.copyOf(15)) }
        if (!Cdx2PointerCandidate.valid(question, sources)) return unchanged()
        val q = query.matchEntire(question) ?: return unchanged()
        if (sources.any { risk.containsMatchIn(it.text) }) return unchanged()
        val subject = Regex.escape(q.groupValues[2]); val relation = Regex.escape(q.groupValues[1])
        val direct = Regex("""(?iu)^\s*$subject\s+$relation\s+in\s+(.+?)\s*$""")
        val inverted = Regex("""(?iu)^\s*in\s+(.+?)\s+$relation\s+$subject\s*$""")
        val slots = mutableListOf<EvidenceBoundNaturalLocationProbe.Decision.Found>()
        for (source in sources) {
            var start = 0
            for (end in 0..source.text.length) {
                if (end < source.text.length && source.text[end] !in ".!?;,") continue
                val clause = source.text.substring(start, end)
                val match = direct.matchEntire(clause) ?: inverted.matchEntire(clause)
                val group = match?.groups?.get(1)
                if (group != null && place.matches(group.value)) slots += EvidenceBoundNaturalLocationProbe.Decision.Found(
                    group.value, source.sourceId, source.revision, source.sha256,
                    start + group.range.first, start + group.range.last + 1)
                start = end + 1
            }
        }
        // Multiple bindings do not acquire a new feature; original model retains its decision.
        val slot = slots.singleOrNull() ?: return unchanged()
        // Extend only representations the old grammar cannot mark as an exact whole binding.
        if (old.any { it.found == slot && it.features[3] == 1.0 && it.features[4] == 1.0 && it.features[5] == 0.0 }) return unchanged()
        val result = old.map { option -> Cdx2PointerCandidate.Option(option.found, option.features.copyOf(15).also {
            it[13] = if (option.found == slot) 1.0 else 0.0
            it[14] = if (option.found == null) 1.0 else 0.0
        }) }.toMutableList()
        if (old.none { it.found == slot }) result += Cdx2PointerCandidate.Option(slot, DoubleArray(15).also {
            it[0] = 1.0; it[13] = 1.0
        })
        return result
    }

    fun probabilities(weights: DoubleArray, options: List<Cdx2PointerCandidate.Option>): DoubleArray {
        require(weights.size == 15 && weights.all { it.isFinite() && kotlin.math.abs(it) <= 100 })
        val scores = options.map { o -> weights.indices.sumOf { weights[it] * o.features[it] } }
        val maximum = scores.max()
        val values = scores.map { kotlin.math.exp(it - maximum) }
        return DoubleArray(values.size) { values[it] / values.sum() }
    }

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 3) { "CURRENT13 TRAIN_CORPUS FRESH_OUTPUT_CHECKPOINT" }
        val checkpoint = JSONObject(File(args[0]).readText())
        require(checkpoint.getString("schema") == "CDX2_FROZEN_BASE_ADAPTER_V1")
        val saved = checkpoint.getJSONArray("weights"); require(saved.length() == 13)
        val frozen = DoubleArray(13) { saved.getDouble(it) }; val weights = frozen.copyOf(15)
        val train = Cdx2PointerCandidate.readExamples(args[1]); require(train.size in 1..64)
        val examples = train.map { options(it.question, it.sources) to it.expected }
        val random = Random(17); val order = examples.indices.toMutableList()
        repeat(60) {
            java.util.Collections.shuffle(order, random)
            for (i in order) {
                val (options, gold) = examples[i]
                val target = options.indexOfFirst { it.found == gold }; require(target >= 0)
                val p = probabilities(weights, options)
                for (j in 13..14) weights[j] -= .05 * (options.indices.sumOf { p[it] * options[it].features[j] } - options[target].features[j])
            }
        }
        check((0..12).all { weights[it].toBits() == frozen[it].toBits() })
        val out = File(args[2]); require(!out.exists())
        out.writeText(JSONObject().put("schema", "BREAKTHROUGH_GROUNDED_SLOT_RESIDUAL_V1")
            .put("weights", JSONArray(weights.toList())).put("frozenFirstThirteen", true)
            .put("seed", 17).put("epochs", 60).put("rate", .05).put("trainExamples", train.size)
            .put("trainingSha256", EvidenceBoundNaturalLocationProbe.digest(File(args[1]).readText()))
            .put("baseSha256", EvidenceBoundNaturalLocationProbe.digest(File(args[0]).readText()))
            .put("promotion", "FORBIDDEN_RESEARCH_ONLY").toString(2) + "\n")
        println("GROUND_SLOT_TRAINED train=${train.size} frozen=13 learned=2 epochs=60 seed=17")
    }
}
