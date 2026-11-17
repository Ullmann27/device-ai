package at.heinz.deviceai.learning

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.math.exp
import kotlin.math.ln
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.sqrt

/**
 * Research-only CRE1 inference, not a production replacement for SWB1.
 * Reads the independent Node CRE1 checkpoint (4,210,736 bytes). The encoder
 * never writes memories, trains autonomously, or selects chat answers.
 */
class Cre1InferenceEncoder private constructor(
    val seed: Long,
    var steps: Int,
    private val embeddings: DoubleArray,
    private val queryProjection: DoubleArray,
    private val memoryProjection: DoubleArray
) {
    data class Feature(val id: Int, val weight: Double)

    fun embed(text: String, side: String = "m"): DoubleArray {
        require(side == "q" || side == "m") { "CRE1 side must be q or m" }
        val pooled = DoubleArray(D)
        for (feature in features(text)) {
            val offset = feature.id * D
            for (i in 0 until D) pooled[i] += feature.weight * embeddings[offset + i]
        }
        val projection = if (side == "q") queryProjection else memoryProjection
        val vector = DoubleArray(D)
        for (r in 0 until D) {
            var value = 0.0
            for (c in 0 until D) value += projection[r * D + c] * pooled[c]
            vector[r] = value
        }
        var normSquared = 0.0
        for (x in vector) normSquared += x * x
        val norm = sqrt(normSquared)
        require(norm > 0.0 && norm.isFinite()) { "Degenerate CRE1 representation" }
        for (i in vector.indices) vector[i] /= norm
        return vector
    }

    fun score(query: String, memory: String): Double {
        val q = embed(query, "q")
        val m = embed(memory, "m")
        var dot = 0.0
        for (i in 0 until D) dot += q[i] * m[i]
        return dot
    }


    /** Research-only single InfoNCE SGD step, with one verified positive in
     * candidates[0] and independent negatives after it. No state change until
     * all analytical gradients have been computed and validated.
     * This algorithm is intentionally distinct from Node's main Adam trainer;
     * cre-sgd-parity.mjs is its exact Node twin. */
    @Synchronized fun trainSgdStep(
        question: String,
        candidates: List<String>,
        learningRate: Double = 0.0001,
        temperature: Double = 0.2
    ): Double {
        require(candidates.size in 2..12 && candidates.distinct().size == candidates.size)
        require(learningRate.isFinite() && learningRate > 0 && learningRate <= 0.001)
        require(temperature.isFinite() && temperature in 0.05..1.0)
        require(steps < 1_000_000)

        data class Forward(
            val f: List<Feature>, val h: DoubleArray, val e: DoubleArray, val norm: Double
        )
        fun forward(text: String, projection: DoubleArray): Forward {
            val f = features(text)
            val h = DoubleArray(D)
            for (feature in f) {
                val start = feature.id * D
                for (i in 0 until D) h[i] += feature.weight * embeddings[start + i]
            }
            val z = DoubleArray(D)
            for (i in 0 until D) for (j in 0 until D) z[i] += projection[i * D + j] * h[j]
            var sum = 0.0
            for (v in z) sum += v * v
            val norm = sqrt(sum)
            require(norm > 0 && norm.isFinite()) { "Degenerate CRE1 representation" }
            val e = DoubleArray(D) { z[it] / norm }
            return Forward(f, h, e, norm)
        }
        val query = forward(question, queryProjection)
        val answers = candidates.map { forward(it, memoryProjection) }
        val logits = answers.map { answer ->
            var dot = 0.0
            for (i in 0 until D) dot += query.e[i] * answer.e[i]
            dot / temperature
        }
        val maximum = logits.maxOrNull()!!
        val exponentials = logits.map { exp(it - maximum) }
        val total = exponentials.sum()
        val loss = ln(total) + maximum - logits[0]
        require(loss.isFinite())
        val dQ = DoubleArray(D)
        val dpq = DoubleArray(D * D)
        val dpm = DoubleArray(D * D)
        val de = HashMap<Int, DoubleArray>()
        fun backward(
            forward: Forward,
            dE: DoubleArray,
            projection: DoubleArray,
            dP: DoubleArray
        ) {
            var dot = 0.0
            for (i in 0 until D) dot += dE[i] * forward.e[i]
            val dz = DoubleArray(D) { i -> (dE[i] - dot * forward.e[i]) / forward.norm }
            val dh = DoubleArray(D)
            for (i in 0 until D) {
                val g = dz[i]
                if (g == 0.0) continue
                for (j in 0 until D) {
                    dP[i * D + j] += g * forward.h[j]
                    dh[j] += projection[i * D + j] * g
                }
            }
            for (feature in forward.f) {
                val row = de.getOrPut(feature.id) { DoubleArray(D) }
                for (i in 0 until D) row[i] += feature.weight * dh[i]
            }
        }
        for (j in answers.indices) {
            val g = exponentials[j] / total - if (j == 0) 1.0 else 0.0
            if (g == 0.0) continue
            val dm = DoubleArray(D)
            for (i in 0 until D) {
                dQ[i] += g * answers[j].e[i] / temperature
                dm[i] = g * query.e[i] / temperature
            }
            backward(answers[j], dm, memoryProjection, dpm)
        }
        backward(query, dQ, queryProjection, dpq)
        require(dpq.all(Double::isFinite) && dpm.all(Double::isFinite) &&
            de.values.all { row -> row.all(Double::isFinite) }) { "Unstable CRE1 training step" }
        for (id in de.keys.sorted()) {
            val row = de.getValue(id)
            for (i in 0 until D) {
                val offset = id * D + i
                val next = embeddings[offset] - learningRate * row[i]
                require(next.isFinite())
                embeddings[offset] = next
            }
        }
        for (i in dpq.indices) {
            queryProjection[i] -= learningRate * dpq[i]
            memoryProjection[i] -= learningRate * dpm[i]
        }
        steps++
        return loss
    }

    /** CRC-independent SHA-256 checkpoint, binary-compatible with Node CRE1.
     * Used only for opt-in training parity and explicitly exported experiments. */
    @Synchronized fun save(): ByteArray {
        require(steps in 0..1_000_000)
        val body = ByteArrayOutputStream(SNAPSHOT_LENGTH - 32)
        DataOutputStream(body).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(seed.toInt())
            out.writeInt(steps)
            out.writeInt(D)
            for (array in arrayOf(embeddings, queryProjection, memoryProjection)) {
                for (v in array) {
                    require(v.isFinite()) { "Nonfinite CRE1 weights" }
                    out.writeDouble(v)
                }
            }
        }
        val payload = body.toByteArray()
        require(payload.size == SNAPSHOT_LENGTH - 32)
        val digest = MessageDigest.getInstance("SHA-256").digest(payload)
        return payload + digest
    }

    companion object {
        const val DIMENSION: Int = 32
        const val VOCABULARY: Int = 16384
        const val SNAPSHOT_LENGTH: Int = 16 + (16384 * 32 + 2 * 32 * 32) * 8 + 32
        private const val D = DIMENSION
        private const val V = VOCABULARY
        private const val MAGIC = 0x43524531
        private val SEPARATOR = Regex("[^\\p{L}\\p{N}]+")
        private val FUNCTION_WORDS = (
            "der die das den dem des ein eine einer eines einem einen " +
            "und oder ich du er sie es wir ihr mein meine meinen meinem meiner sein seine " +
            "ist sind hat haben in im am an zu zum zur von vom mit fuer auf bei als " +
            "auch noch sehr kann soll was wie wo wer wann welche welcher welches welchen"
        ).split(" ").toSet()

        fun tokens(text: String): List<String> {
            require(text.isNotEmpty() && text.length <= 2048) { "Invalid CRE1 text" }
            return text.lowercase(Locale.ROOT)
                .replace("ä", "ae").replace("ö", "oe")
                .replace("ü", "ue").replace("ß", "ss")
                .split(SEPARATOR).filter(String::isNotEmpty).take(96)
        }

        fun bucket(feature: String): Int {
            var hash = 0x811c9dc5.toInt()
            for (byte in feature.toByteArray(Charsets.UTF_8)) {
                hash = (hash xor (byte.toInt() and 255)) * 0x01000193
            }
            return hash and (V - 1)
        }

        fun features(text: String): List<Feature> {
            val acc = HashMap<Int, Double>()
            fun add(id: Int, weight: Double) {
                acc[id] = (acc[id] ?: 0.0) + weight
            }
            for (token in tokens(text)) {
                val wordWeight = if (token in FUNCTION_WORDS) 0.3 else 1.0
                add(bucket("w:$token"), wordWeight)
                if (token.length < 3 || token.all(Char::isDigit)) continue
                val padded = "<$token>"
                val grams = ArrayList<String>()
                for (n in 3..5) {
                    for (i in 0..(padded.length - n)) {
                        grams.add(padded.substring(i, i + n))
                    }
                }
                if (grams.isNotEmpty()) for (gram in grams) {
                    add(bucket("g:$gram"), wordWeight / grams.size)
                }
            }
            require(acc.isNotEmpty()) { "No CRE1 features" }
            var total = 0.0
            for (weight in acc.values) total += weight
            // Match JS Map insertion order when normalizing: independent hash map
            // iteration order differs, but double rounding is checked with 1e-9.
            return acc.entries.sortedBy { it.key }.map { Feature(it.key, it.value / total) }
        }

        /**
         * Creates a brand-new CRE1 research model on Android without importing
         * pretrained weights. Mirrors RelationalEncoder(seed)'s 32-bit LCG and
         * initialization order exactly; does not touch productive SWB1 files.
         */
        fun createResearch(seed: Long = 73L): Cre1InferenceEncoder {
            require(seed in 0..0xffffffffL) { "CRE1 seed must be uint32" }
            var state = seed.toInt()
            fun next(): Double {
                state = state * 1_664_525 + 1_013_904_223
                return (state.toLong() and 0xffffffffL) / 4_294_967_296.0
            }
            val e = DoubleArray(V * D) { (next() * 2.0 - 1.0) * 0.1 }
            val q = DoubleArray(D * D) { i ->
                (if (i / D == i % D) 1.0 else 0.0) +
                    (next() * 2.0 - 1.0) * 0.01
            }
            val m = DoubleArray(D * D) { i ->
                (if (i / D == i % D) 1.0 else 0.0) +
                    (next() * 2.0 - 1.0) * 0.01
            }
            return Cre1InferenceEncoder(seed, 0, e, q, m)
        }

        fun restore(snapshot: ByteArray): Cre1InferenceEncoder {
            require(snapshot.size == SNAPSHOT_LENGTH) { "Invalid CRE1 snapshot size" }
            val payloadSize = SNAPSHOT_LENGTH - 32
            val hash = MessageDigest.getInstance("SHA-256")
            hash.update(snapshot, 0, payloadSize)
            require(MessageDigest.isEqual(hash.digest(), snapshot.copyOfRange(payloadSize, snapshot.size))) {
                "Invalid CRE1 checkpoint checksum"
            }
            DataInputStream(ByteArrayInputStream(snapshot, 0, payloadSize)).use { input ->
                require(input.readInt() == MAGIC) { "Not a CRE1 checkpoint" }
                val seed = input.readInt().toLong() and 0xffffffffL
                val steps = input.readInt()
                require(steps in 0..1_000_000)
                require(input.readInt() == D)
                fun array(size: Int): DoubleArray = DoubleArray(size) {
                    input.readDouble().also { value -> require(value.isFinite()) { "Nonfinite CRE1 weights" } }
                }
                val embeddings = array(V * D)
                val pq = array(D * D)
                val pm = array(D * D)
                require(input.available() == 0) { "Trailing CRE1 checkpoint payload" }
                return Cre1InferenceEncoder(seed, steps, embeddings, pq, pm)
            }
        }
    }
}
