package at.heinz.deviceai.learning.lm

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/**
 * Byte-level BPE. Ids 0..255 are raw bytes, 256 until [bos] are learned merges, then BOS and EOS.
 * Any UTF-8 text is encodable (no unknown token) and decoding is exact for every id sequence of real text.
 * Merges are learned deterministically (ties broken by the smaller pair) and only from the caller's TRAIN texts.
 */
class LmBpeTokenizer private constructor(private val merges: List<Pair<Int, Int>>) {
    val vocabSize: Int get() = 256 + merges.size + 2
    val bos: Int get() = 256 + merges.size
    val eos: Int get() = bos + 1
    private val rank = HashMap<Long, Int>().apply { merges.forEachIndexed { i, (a, b) -> put(key(a, b), i) } }
    private val pieces: Array<ByteArray> = buildPieces()
    private val cache = object : LinkedHashMap<String, IntArray>(4096, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, IntArray>?) = size > 20_000
    }

    /** SHA-256 of the serialized merge list: binds checkpoints to exactly this vocabulary. */
    val sha256: String by lazy { hex(MessageDigest.getInstance("SHA-256").digest(serialize())) }

    fun encode(text: String): IntArray {
        val out = ArrayList<Int>(text.length)
        for (word in pretokenize(text)) out.addAll(encodeWord(word).asList())
        return out.toIntArray()
    }

    fun decode(ids: IntArray): String {
        val bytes = ByteArrayOutputStream()
        for (id in ids) if (id in 0 until bos) bytes.write(pieces[id])
        return bytes.toByteArray().toString(Charsets.UTF_8)
    }

    fun pieceBytes(id: Int): ByteArray = if (id in 0 until bos) pieces[id] else ByteArray(0)

    private fun encodeWord(word: String): IntArray = synchronized(cache) { cache[word] } ?: run {
        var symbols = word.toByteArray(Charsets.UTF_8).map { it.toInt() and 255 }.toMutableList()
        while (symbols.size > 1) {
            var best = Int.MAX_VALUE; var at = -1
            for (i in 0 until symbols.size - 1) {
                val r = rank[key(symbols[i], symbols[i + 1])] ?: continue
                if (r < best) { best = r; at = i }
            }
            if (at < 0) break
            val merged = 256 + best
            val next = ArrayList<Int>(symbols.size)
            var i = 0
            val (a, b) = merges[best]
            while (i < symbols.size) {
                if (i < symbols.size - 1 && symbols[i] == a && symbols[i + 1] == b) { next += merged; i += 2 } else { next += symbols[i]; i++ }
            }
            symbols = next
        }
        symbols.toIntArray().also { synchronized(cache) { cache[word] = it } }
    }

    private fun buildPieces(): Array<ByteArray> {
        val out = arrayOfNulls<ByteArray>(256 + merges.size)
        for (b in 0 until 256) out[b] = byteArrayOf(b.toByte())
        merges.forEachIndexed { i, (a, b) -> out[256 + i] = out[a]!! + out[b]!! }
        @Suppress("UNCHECKED_CAST")
        return out as Array<ByteArray>
    }

    fun serialize(): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.writeInt(MAGIC); out.writeInt(merges.size)
            for ((a, b) in merges) { out.writeInt(a); out.writeInt(b) }
        }
        return buffer.toByteArray()
    }

    companion object {
        private const val MAGIC = 0x4C4D4250 // "LMBP"
        /** Leading space attaches to the following word; digits are kept single so numbers stay compositional. */
        private val PRETOKEN = Regex(" ?\\p{L}+| ?\\p{N}| ?[^\\s\\p{L}\\p{N}]+|\\s+")

        fun pretokenize(text: String): List<String> = PRETOKEN.findAll(text).map { it.value }.toList()

        private fun key(a: Int, b: Int) = (a.toLong() shl 32) or (b.toLong() and 0xffffffffL)

        /** Byte-only tokenizer (no merges): the byte-level ablation. */
        fun bytes(): LmBpeTokenizer = LmBpeTokenizer(emptyList())

        /** Learns `vocabTarget − 258` merges from [trainTexts] only. */
        fun train(trainTexts: List<String>, vocabTarget: Int): LmBpeTokenizer {
            require(vocabTarget in 258..8192)
            val counts = HashMap<String, Int>()
            for (text in trainTexts) for (w in pretokenize(text)) counts.merge(w, 1, Int::plus)
            val words = counts.keys.sorted().map { w -> w.toByteArray(Charsets.UTF_8).map { it.toInt() and 255 }.toIntArray() }.toMutableList()
            val freq = counts.keys.sorted().map { counts[it]!! }
            val merges = ArrayList<Pair<Int, Int>>()
            while (256 + merges.size + 2 < vocabTarget) {
                val pairs = HashMap<Long, Long>()
                for ((wi, w) in words.withIndex()) for (i in 0 until w.size - 1) pairs.merge(key(w[i], w[i + 1]), freq[wi].toLong(), Long::plus)
                var bestKey = 0L; var bestCount = 1L
                for ((k, c) in pairs) if (c > bestCount || c == bestCount && c > 1 && k < bestKey) { bestKey = k; bestCount = c }
                if (bestCount <= 1) break
                val a = (bestKey shr 32).toInt(); val b = (bestKey and 0xffffffffL).toInt()
                val id = 256 + merges.size
                merges += a to b
                for (wi in words.indices) {
                    val w = words[wi]
                    if (w.size < 2) continue
                    var hit = false
                    for (i in 0 until w.size - 1) if (w[i] == a && w[i + 1] == b) { hit = true; break }
                    if (!hit) continue
                    val next = ArrayList<Int>(w.size)
                    var i = 0
                    while (i < w.size) { if (i < w.size - 1 && w[i] == a && w[i + 1] == b) { next += id; i += 2 } else { next += w[i]; i++ } }
                    words[wi] = next.toIntArray()
                }
            }
            return LmBpeTokenizer(merges)
        }

        fun deserialize(bytes: ByteArray): LmBpeTokenizer = DataInputStream(bytes.inputStream()).use { input ->
            require(input.readInt() == MAGIC) { "not an LM BPE vocabulary" }
            val n = input.readInt()
            require(n in 0..8000)
            val merges = ArrayList<Pair<Int, Int>>(n)
            repeat(n) { i ->
                val a = input.readInt(); val b = input.readInt()
                require(a in 0 until 256 + i && b in 0 until 256 + i) { "merge $i refers to an unknown id" }
                merges += a to b
            }
            require(input.available() == 0)
            LmBpeTokenizer(merges)
        }

        fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    }
}
