package at.heinz.deviceai.learning

import java.io.ByteArrayInputStream
import java.io.DataInputStream
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
    val steps: Int,
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
