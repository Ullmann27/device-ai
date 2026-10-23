package at.heinz.deviceai.learning.lm

import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Random
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * LM1 step 7 (pre-registered in docs/CONTINUUM-LM1-OPUS-20261022.md, section 7b): text generation and instruction
 * processing of the TEST candidates on NEW data that no LM1 run has seen.
 * - NEW: Markdown documents that did not exist at the corpus commit, pinned by blob id in [MANIFEST]; same privacy
 *   filter and segmentation as the corpus; segments that share an [OVERLAP_BYTES]-byte window with any corpus segment
 *   or repeat an earlier NEW segment are dropped.
 * - Instruction pairs: two instructions over the same input and candidates with different answers, so a model that
 *   ignores the instruction cannot answer both. Candidates are ranked by their conditional bits.
 * `data` prints the frozen hashes and examples without scoring anything; `run` checks the pins and scores once.
 * Build: kotlinc with learning/LearningSnapshot.kt (CDX1 needs it), learning/TinyContextDecoder.kt and every .kt file
 * of learning/lm in app/src/main and app/src/test, -cp junit-4.13.2.jar:hamcrest-core-1.3.jar -include-runtime -d lm.jar.
 * Run: java -cp lm.jar at.heinz.deviceai.learning.lm.LmStep7 <repoRoot> data|run store=<dir> gm=<h,h,h> tm=<h,h,h>
 */
object LmStep7 {
    const val MANIFEST = "docs/evidence/lm1-20261022/step7-new-docs.tsv"
    const val OVERLAP_BYTES = 50
    /** Pinned in the pre-registration commit, before any model was scored on NEW or on the items. */
    const val NEW_SHA256 = ""
    const val ITEMS_SHA256 = ""
    const val POSITIONS = 2000
    const val PROMPTS = 40
    const val GENERATED_BYTES = 120
    const val CDX1_EPOCHS = 3
    private val WORD = Regex("\\p{L}+")

    // ------------------------------------------------------------------------------------------------ NEW documents

    class Filtered(val segments: List<String>, val droppedOverlap: Int, val droppedDuplicate: Int)

    fun manifest(root: File): List<Pair<String, String>> = File(root, MANIFEST).readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t').let { f -> f[0] to f[1] } }.sortedBy { it.first }

    fun newDocuments(root: File): List<LmCorpus.Document> =
        manifest(root).map { (path, blob) -> LmCorpus.document(path, LmExperiment.git(root, "cat-file", "blob", blob)) }

    /** Drops candidates sharing an [OVERLAP_BYTES]-byte window with [corpus] and exact repeats, keeping the order. */
    fun filter(candidates: List<String>, corpus: List<String>): Filtered {
        val seen = HashSet<Long>()
        for (s in corpus) windowHashes(s, OVERLAP_BYTES).forEach { seen += it }
        val kept = ArrayList<String>(); val unique = HashSet<String>()
        var overlap = 0; var duplicate = 0
        for (s in candidates) when {
            windowHashes(s, OVERLAP_BYTES).any { it in seen } -> overlap++
            !unique.add(s) -> duplicate++
            else -> kept += s
        }
        return Filtered(kept, overlap, duplicate)
    }

    /** 64-bit FNV-1a hash of every [n]-byte window of [text] (UTF-8). */
    fun windowHashes(text: String, n: Int): LongArray {
        val b = text.toByteArray(Charsets.UTF_8)
        if (b.size < n) return LongArray(0)
        return LongArray(b.size - n + 1) { i ->
            var h = -0x340d631b7bdddcdbL
            for (j in i until i + n) { h = h xor (b[j].toLong() and 255); h *= 0x100000001b3L }
            h
        }
    }

    fun sha256(parts: List<String>): String {
        val md = MessageDigest.getInstance("SHA-256")
        for (p in parts) { md.update(p.toByteArray(Charsets.UTF_8)); md.update(0) }
        return LmBpeTokenizer.hex(md.digest())
    }

    // ------------------------------------------------------------------------------------------- instruction pairs

    data class Item(val family: String, val prompt: String, val candidates: List<String>, val gold: Int)
    class InstructionPair(val a: Item, val b: Item)

    private val NOUNS = listOf("Haus", "Baum", "Tisch", "Stuhl", "Hund", "Katze", "Auto", "Buch", "Brot", "Apfel",
        "Fenster", "Garten", "Schule", "Weg", "Wasser", "Blume", "Vogel", "Lampe", "Tasse", "Brief", "Berg", "Fluss",
        "Stadt", "Wald", "Wolke", "Sonne", "Mond", "Stern", "Schiff", "Zug", "Glas", "Ball", "Bild", "Uhr", "Tür",
        "Kerze", "Schuh", "Hemd", "Mantel", "Ring")

    /** 5 families × 40 pairs, generated from a fixed seed. */
    fun instructionPairs(): List<InstructionPair> {
        val rnd = Random(20261023)
        val out = ArrayList<InstructionPair>()
        fun ask(text: String) = "$text Antwort:"
        repeat(40) {
            val w = NOUNS.shuffled(rnd).take(3)
            val list = w.joinToString(", ")
            out += InstructionPair(Item("position", ask("Nenne das erste Wort der Liste: $list."), w, 0),
                Item("position", ask("Nenne das letzte Wort der Liste: $list."), w, 2))
        }
        for (w in NOUNS) {
            val c = listOf(w.uppercase(), w.lowercase(), w)
            out += InstructionPair(Item("case", ask("Schreibe das Wort in Großbuchstaben: $w."), c, 0),
                Item("case", ask("Schreibe das Wort in Kleinbuchstaben: $w."), c, 1))
        }
        repeat(40) {
            val (m, n) = twoNumbers(rnd)
            val c = listOf("$m", "$n")
            out += InstructionPair(Item("compare", ask("Welche Zahl ist größer: $m oder $n?"), c, if (m > n) 0 else 1),
                Item("compare", ask("Welche Zahl ist kleiner: $m oder $n?"), c, if (m < n) 0 else 1))
        }
        repeat(40) {
            val (m, n) = twoNumbers(rnd)
            val c = listOf("ja", "nein")
            out += InstructionPair(Item("yesno", ask("Ist $m größer als $n?"), c, if (m > n) 0 else 1),
                Item("yesno", ask("Ist $m kleiner als $n?"), c, if (m < n) 0 else 1))
        }
        for (n in (2..98).shuffled(rnd).take(40)) {
            val c = if (rnd.nextBoolean()) listOf("${n + 1}", "${n - 1}") else listOf("${n - 1}", "${n + 1}")
            out += InstructionPair(Item("neighbour", ask("Welche Zahl kommt nach $n?"), c, c.indexOf("${n + 1}")),
                Item("neighbour", ask("Welche Zahl kommt vor $n?"), c, c.indexOf("${n - 1}")))
        }
        return out
    }

    private fun twoNumbers(rnd: Random): Pair<Int, Int> {
        val m = 1 + rnd.nextInt(99)
        var n: Int
        do n = 1 + rnd.nextInt(99) while (n == m)
        return m to n
    }

    fun itemsSha256(pairs: List<InstructionPair>) = sha256(pairs.flatMap { listOf(it.a, it.b) }
        .map { "${it.family}\t${it.prompt}\t${it.candidates.joinToString("\u001f")}\t${it.gold}" })

    /** The candidate with the fewest bits after the prompt, written as " candidate."; ties go to the first. */
    fun choose(bits: (String) -> Double, item: Item): Int {
        val base = bits(item.prompt)
        var best = 0; var bestBits = Double.MAX_VALUE
        for ((i, c) in item.candidates.withIndex()) {
            val v = bits("${item.prompt} $c.") - base
            if (v < bestBits) { bestBits = v; best = i }
        }
        return best
    }

    /** both: pairs answered right under both instructions; swapped: answered as if the instructions were exchanged. */
    class InstructionResult(val both: Int, val swapped: Int, val correct: Int, val items: Int, val perFamily: Map<String, IntArray>)

    fun instructionResult(bits: (String) -> Double, pairs: List<InstructionPair>): InstructionResult {
        var both = 0; var swapped = 0; var correct = 0
        val families = LinkedHashMap<String, IntArray>()
        for (p in pairs) {
            val ca = choose(bits, p.a); val cb = choose(bits, p.b)
            val f = families.getOrPut(p.a.family) { IntArray(4) }
            if (ca == p.a.gold && cb == p.b.gold) { both++; f[0]++ }
            if (ca == p.b.gold && cb == p.a.gold) { swapped++; f[1]++ }
            val k = (if (ca == p.a.gold) 1 else 0) + (if (cb == p.b.gold) 1 else 0)
            correct += k; f[2] += k; f[3] += 2
        }
        return InstructionResult(both, swapped, correct, 2 * pairs.size, families)
    }

    // ------------------------------------------------------------------------------------------------ statistics

    private fun lnChoose(n: Int, k: Int): Double { var s = 0.0; for (i in 1..k) s += ln((n - k + i).toDouble() / i); return s }

    /** One-sided P(X ≥ k) for X ~ Binomial(n, 1/2). */
    fun signTestP(k: Int, n: Int): Double =
        if (n == 0) 1.0 else (k..n).sumOf { exp(lnChoose(n, it) - n * ln(2.0)) }.coerceAtMost(1.0)

    /** Exact two-sided McNemar test for b and c discordant pairs. */
    fun mcnemarP(b: Int, c: Int): Double {
        val n = b + c
        if (n == 0) return 1.0
        return (2 * (0..minOf(b, c)).sumOf { exp(lnChoose(n, it) - n * ln(2.0)) }).coerceAtMost(1.0)
    }

    fun wilson(k: Int, n: Int): Pair<Double, Double> {
        val z = 1.959964; val p = k.toDouble() / n; val d = 1 + z * z / n
        val c = (p + z * z / (2 * n)) / d; val h = z * sqrt(p * (1 - p) / n + z * z / (4.0 * n * n)) / d
        return (c - h) to (c + h)
    }

    // --------------------------------------------------------------------------------------------- next word (A2)

    /** prefix ends right before the space; the target is " word" followed by a non-letter. */
    class Position(val prefix: String, val word: String)

    /** Word starts after a single space with ≥ 16 bytes of context; the [count] positions with the smallest hash. */
    fun positions(segments: List<String>, count: Int = POSITIONS): List<Position> {
        val all = ArrayList<Pair<String, Position>>()
        for ((si, s) in segments.withIndex()) for (i in 2 until s.length) {
            if (s[i - 1] != ' ' || s[i - 2].isWhitespace() || !s[i].isLetter()) continue
            val prefix = s.substring(0, i - 1)
            if (prefix.toByteArray(Charsets.UTF_8).size < 16) continue
            var j = i
            while (j < s.length && s[j].isLetter()) j++
            if (j - i < 2) continue
            all += sha256(listOf("lm1-step7-position", "$si:$i")) to Position(prefix, s.substring(i, j))
        }
        return all.sortedBy { it.first }.take(count).map { it.second }
    }

    private fun argmax(p: FloatArray): Int { var best = 0; for (i in p.indices) if (p[i] > p[best]) best = i; return best }
    private fun argmax(p: DoubleArray): Int { var best = 0; for (i in p.indices) if (p[i] > p[best]) best = i; return best }

    private fun agrees(out: ByteArray, target: ByteArray): Boolean {
        for (i in 0 until minOf(out.size, target.size)) if (out[i] != target[i]) return false
        return true
    }

    private fun wordEnds(out: ByteArray, target: ByteArray, ended: Boolean): Boolean {
        if (out.size < target.size || !agrees(out, target)) return false
        val rest = out.copyOfRange(target.size, out.size).toString(Charsets.UTF_8)
        return if (rest.isEmpty()) ended else !rest[0].isLetter()
    }

    /** Greedy LM1 continuation equals " word" followed by a non-letter, EOS or the context end. */
    fun lmPredictsWord(model: LmModel, tok: LmBpeTokenizer, p: Position): Boolean {
        val target = " ${p.word}".toByteArray(Charsets.UTF_8)
        var ids = intArrayOf(tok.bos) + tok.encode(p.prefix)
        val out = ByteArrayOutputStream()
        var ended = false
        while (out.size() < target.size + 4) {
            if (ids.size >= model.config.context) { ended = true; break }
            val dist = model.nextDistribution(ids)
            dist[tok.bos] = 0f
            val next = argmax(dist)
            if (next == tok.eos) { ended = true; break }
            out.write(tok.pieceBytes(next)); ids += next
            if (!agrees(out.toByteArray(), target)) return false
        }
        return wordEnds(out.toByteArray(), target, ended)
    }

    /** Greedy byte continuation of the n-gram model equals " word" followed by a non-letter. */
    fun knPredictsWord(kn: KneserNeyBytes, p: Position): Boolean {
        val target = " ${p.word}".toByteArray(Charsets.UTF_8)
        var b = p.prefix.toByteArray(Charsets.UTF_8)
        val start = b.size
        while (b.size - start < target.size + 4) {
            b += argmax(kn.nextByteProbabilities(b)).toByte()
            if (!agrees(b.copyOfRange(start, b.size), target)) return false
        }
        return wordEnds(b.copyOfRange(start, b.size), target, false)
    }

    /** Most frequent TRAIN word, and the most frequent successor of the previous word (ties: smallest word). */
    class WordBaselines(train: List<String>) {
        private val counts = HashMap<String, Int>()
        private val next = HashMap<String, HashMap<String, Int>>()
        init {
            for (s in train) {
                val w = WORD.findAll(s).map { it.value }.toList()
                for (i in w.indices) {
                    counts.merge(w[i], 1, Int::plus)
                    if (i > 0) next.getOrPut(w[i - 1]) { HashMap() }.merge(w[i], 1, Int::plus)
                }
            }
        }
        private fun best(m: Map<String, Int>) =
            m.entries.maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key })?.key
        val frequent: String = best(counts) ?: ""
        fun bigram(prefix: String): String = WORD.findAll(prefix).lastOrNull()?.value?.let { next[it] }?.let { best(it) } ?: frequent
    }

    // ---------------------------------------------------------------------------------------------- generation (A3)

    class Prompt(val prompt: String, val reference: String)

    /** Segment starts cut before a space between 20 and 60 characters; the [count] with the smallest hash. */
    fun prompts(segments: List<String>, count: Int = PROMPTS): List<Prompt> {
        val all = ArrayList<Pair<String, Prompt>>()
        for ((si, s) in segments.withIndex()) {
            var k = minOf(60, s.length - 1)
            while (k >= 20 && !(s[k] == ' ' && !s[k - 1].isWhitespace())) k--
            if (k < 20) continue
            all += sha256(listOf("lm1-step7-prompt", "$si")) to Prompt(s.substring(0, k), clipBytes(s.substring(k), GENERATED_BYTES))
        }
        return all.sortedBy { it.first }.take(count).map { it.second }
    }

    fun clipBytes(s: String, n: Int): String {
        val sb = StringBuilder(); var bytes = 0; var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val len = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).size
            if (bytes + len > n) break
            sb.appendCodePoint(cp); bytes += len; i += Character.charCount(cp)
        }
        return sb.toString()
    }

    fun knGenerate(kn: KneserNeyBytes, prompt: String, temperature: Double, topK: Int, rnd: Random): String {
        var b = prompt.toByteArray(Charsets.UTF_8)
        val start = b.size
        while (b.size - start < GENERATED_BYTES) {
            val p = kn.nextByteProbabilities(b)
            b += LmEval.sample(FloatArray(256) { p[it].toFloat() }, temperature, topK, rnd).toByte()
        }
        return clipBytes(b.copyOfRange(start, b.size).toString(Charsets.UTF_8), GENERATED_BYTES)
    }

    /** realWords: words (≥ 3 letters) known from TRAIN; copy24: 24-byte windows found in TRAIN; repeat3: repeated word 3-grams. */
    class GenStats(val realWords: Double, val copy24: Double, val repeat3: Double, val meanBytes: Double)

    fun genStats(texts: List<String>, trainWords: Set<String>, trainWindows: Set<Long>): GenStats {
        var words = 0; var real = 0; var windows = 0; var copied = 0; var grams = 0; var repeated = 0; var bytes = 0L
        for (t in texts) {
            bytes += t.toByteArray(Charsets.UTF_8).size
            val all = WORD.findAll(t).map { it.value.lowercase() }.toList()
            val long = all.filter { it.length >= 3 }
            words += long.size; real += long.count { it in trainWords }
            val w = windowHashes(t, 24)
            windows += w.size; copied += w.count { it in trainWindows }
            val seen = HashSet<String>()
            for (i in 2 until all.size) { grams++; if (!seen.add(all[i - 2] + " " + all[i - 1] + " " + all[i])) repeated++ }
        }
        fun share(a: Int, n: Int) = if (n == 0) 0.0 else a.toDouble() / n
        return GenStats(share(real, words), share(copied, windows), share(repeated, grams), bytes.toDouble() / texts.size.coerceAtLeast(1))
    }

    // --------------------------------------------------------------------------------------------------------- run

    private fun <T> parallel(n: Int, threads: Int, f: (Int) -> T): List<T> {
        val pool = Executors.newFixedThreadPool(threads)
        try { return pool.invokeAll((0 until n).map { i -> Callable { f(i) } }).map { it.get() } } finally { pool.shutdown() }
    }

    private fun bpb(bits: (String) -> Double, segments: List<String>): Double =
        segments.sumOf { bits(it) } / segments.sumOf { it.toByteArray(Charsets.UTF_8).size }

    private fun f4(x: Double) = "%.4f".format(x)

    @JvmStatic fun main(args: Array<String>) {
        val root = File(args.getOrElse(0) { "." })
        val mode = args.getOrElse(1) { "data" }
        fun arg(name: String) = args.firstOrNull { it.startsWith("$name=") }?.substringAfter('=')
        val corpus = LmExperiment.corpus(root)
        val train = corpus.filter { it.split == LmCorpus.Split.TRAIN }.sortedBy { it.path }.flatMap { it.segments }
        val docs = newDocuments(root)
        val data = filter(docs.flatMap { it.segments }, corpus.flatMap { it.segments })
        val segments = data.segments
        val pairs = instructionPairs()
        val newSha = sha256(segments); val itemSha = itemsSha256(pairs)
        println("NEW docs=${docs.count { it.segments.isNotEmpty() }} candidates=${docs.sumOf { it.segments.size }} segments=${segments.size} " +
            "bytes=${segments.sumOf { it.toByteArray(Charsets.UTF_8).size }} droppedOverlap=${data.droppedOverlap} " +
            "droppedDuplicate=${data.droppedDuplicate} sha=$newSha pinned=$NEW_SHA256")
        println("ITEMS pairs=${pairs.size} sha=$itemSha pinned=$ITEMS_SHA256 families=${pairs.groupingBy { it.a.family }.eachCount()}")
        if (mode == "data") {
            for (s in segments.shuffled(Random(1)).take(3)) println("SAMPLE NEW | " + s.replace("\n", " ⏎ ").take(160))
            for (p in pairs.filterIndexed { i, _ -> i % 40 == 0 })
                println("SAMPLE PAIR ${p.a.family} | ${p.a.prompt} → ${p.a.candidates[p.a.gold]} | ${p.b.prompt} → ${p.b.candidates[p.b.gold]}")
            return
        }
        check(newSha == NEW_SHA256) { "NEW differs from the pre-registration: $newSha" }
        check(itemSha == ITEMS_SHA256) { "instruction items differ from the pre-registration: $itemSha" }
        val threads = arg("threads")?.toInt() ?: 4
        val store = LmCheckpointStore(File(arg("store") ?: "lm-checkpoints"))
        val tok = LmBpeTokenizer.train(train, 512)
        println("TOKENIZER v512 sha=${tok.sha256}")
        class Lm(val name: String, val seed: String, val model: LmModel)
        val lms = listOf("LM1-GM-v512" to arg("gm"), "LM1-TM-v512" to arg("tm")).flatMap { (family, hashes) ->
            requireNotNull(hashes) { "missing checkpoints for $family" }.split(',').map { hash ->
                val restored = LmCheckpoint.decode(store.get(hash), tok)
                check(restored.meta["family"] == family) { "$hash is not $family" }
                Lm(family, restored.meta["seed"].toString(), LmModel(restored.params))
            }
        }
        val unigram = UnigramBytes(train)
        val kn = KneserNeyBytes(7, train)
        val cdx = (1L..3L).map { seed -> Cdx1Baseline(seed).also { c -> repeat(CDX1_EPOCHS) { c.trainEpoch(train) } } }
        fun lmBits(lm: Lm): (String) -> Double = { s -> LmEval.segmentBits(lm.model, tok, s).first }

        // A1: bits per byte on NEW
        val a1 = LinkedHashMap<String, Double>()
        a1["unigram"] = bpb(unigram::bits, segments)
        a1["kn7"] = bpb(kn::bits, segments)
        cdx.forEachIndexed { i, c -> a1["CDX1 seed=${i + 1}"] = bpb(c::bits, segments) }
        for (lm in lms) a1["${lm.name} seed=${lm.seed}"] = parallel(segments.size, threads) { lmBits(lm)(segments[it]) }.sum() /
            segments.sumOf { it.toByteArray(Charsets.UTF_8).size }
        for ((k, v) in a1) println("A1 BPB $k newBPB=${f4(v)}")

        // A2: greedy next word at POSITIONS word starts
        val pos = positions(segments)
        val words = WordBaselines(train)
        val a2 = LinkedHashMap<String, BooleanArray>()
        a2["frequent-word"] = BooleanArray(pos.size) { pos[it].word == words.frequent }
        a2["word-bigram"] = BooleanArray(pos.size) { pos[it].word == words.bigram(pos[it].prefix) }
        a2["kn7"] = parallel(pos.size, threads) { knPredictsWord(kn, pos[it]) }.toBooleanArray()
        for (lm in lms) a2["${lm.name} seed=${lm.seed}"] = parallel(pos.size, threads) { lmPredictsWord(lm.model, tok, pos[it]) }.toBooleanArray()
        for ((k, v) in a2) {
            val n = v.count { it }; val (lo, hi) = wilson(n, v.size)
            println("A2 NEXTWORD $k correct=$n/${v.size} acc=${f4(n.toDouble() / v.size)} ci95=[${f4(lo)}, ${f4(hi)}]")
        }
        fun mcnemar(a: BooleanArray, b: BooleanArray): Triple<Int, Int, Double> {
            val x = a.indices.count { a[it] && !b[it] }; val y = a.indices.count { !a[it] && b[it] }
            return Triple(x, y, mcnemarP(x, y))
        }
        for (lm in lms) for (base in listOf("kn7", "word-bigram")) {
            val (x, y, p) = mcnemar(a2.getValue("${lm.name} seed=${lm.seed}"), a2.getValue(base))
            println("A2 MCNEMAR ${lm.name} seed=${lm.seed} vs $base onlyLm=$x onlyBase=$y p=${"%.3g".format(p)}")
        }

        // A3: generation from PROMPTS segment starts (greedy and t = 0.8, top-k 40, per-prompt seed)
        val prm = prompts(segments)
        val trainWords = train.flatMap { s -> WORD.findAll(s).map { it.value.lowercase() }.filter { it.length >= 3 }.toList() }.toHashSet()
        val trainWindows = HashSet<Long>().apply { for (s in train) windowHashes(s, 24).forEach { add(it) } }
        val gen = LinkedHashMap<String, List<String>>()
        gen["reference (real text)"] = prm.map { it.reference }
        gen["kn7 greedy"] = parallel(prm.size, threads) { knGenerate(kn, prm[it].prompt, 0.0, 1, Random(7000L + it)) }
        gen["kn7 t=0.8"] = parallel(prm.size, threads) { knGenerate(kn, prm[it].prompt, .8, 40, Random(7000L + it)) }
        for (lm in lms) {
            gen["${lm.name} seed=${lm.seed} greedy"] = parallel(prm.size, threads) {
                clipBytes(LmEval.generate(lm.model, tok, prm[it].prompt, GENERATED_BYTES, 0.0, 1, Random(7000L + it)), GENERATED_BYTES)
            }
            gen["${lm.name} seed=${lm.seed} t=0.8"] = parallel(prm.size, threads) {
                clipBytes(LmEval.generate(lm.model, tok, prm[it].prompt, GENERATED_BYTES, .8, 40, Random(7000L + it)), GENERATED_BYTES)
            }
        }
        for ((k, texts) in gen) {
            val g = genStats(texts, trainWords, trainWindows)
            println("A3 GEN $k realWords=${f4(g.realWords)} copy24=${f4(g.copy24)} repeat3=${f4(g.repeat3)} meanBytes=${"%.1f".format(g.meanBytes)}")
        }
        for (i in 0 until 5) {
            println("A3 SAMPLE $i prompt | ${prm[i].prompt.replace("\n", " ⏎ ")}")
            for (k in gen.keys.filter { !it.contains("seed=2") && !it.contains("seed=3") })
                println("A3 SAMPLE $i $k | ${gen.getValue(k)[i].replace("\n", " ⏎ ")}")
        }

        // B: instruction pairs
        val b = LinkedHashMap<String, InstructionResult>()
        b["unigram"] = instructionResult(unigram::bits, pairs)
        b["kn7"] = instructionResult(kn::bits, pairs)
        cdx.forEachIndexed { i, c -> b["CDX1 seed=${i + 1}"] = instructionResult(c::bits, pairs) }
        for (lm in lms) b["${lm.name} seed=${lm.seed}"] = instructionResult(lmBits(lm), pairs)
        for ((k, r) in b) println("B INSTR $k both=${r.both} swapped=${r.swapped} p=${"%.3g".format(signTestP(r.both, r.both + r.swapped))} " +
            "itemAcc=${f4(r.correct.toDouble() / r.items)} " + r.perFamily.entries.joinToString(" ") { (f, v) -> "$f=${v[0]}/${v[1]}/${v[2]}of${v[3]}" })

        // verdicts by the pre-registered rules (section 7b)
        val gm = lms.filter { it.name == "LM1-GM-v512" }.map { "${it.name} seed=${it.seed}" }
        val cdxNames = (1..3).map { "CDX1 seed=$it" }
        println("VERDICT A1 GM beats CDX1 on NEW: ${if (gm.all { g -> cdxNames.all { a1.getValue(g) < a1.getValue(it) } }) "SUPPORTED" else "NOT SUPPORTED"}")
        println("VERDICT A1 GM beats KN-7 on NEW: ${if (gm.all { a1.getValue(it) < a1.getValue("kn7") }) "SUPPORTED" else "NOT SUPPORTED (negative result)"}")
        for (base in listOf("kn7", "word-bigram")) {
            val ok = gm.all { g ->
                val (x, y, p) = mcnemar(a2.getValue(g), a2.getValue(base))
                x > y && p < .01
            }
            println("VERDICT A2 GM predicts the next word better than $base: ${if (ok) "SUPPORTED" else "NOT SUPPORTED"}")
        }
        for (family in listOf("LM1-GM-v512", "LM1-TM-v512")) {
            val ok = lms.filter { it.name == family }.all { lm ->
                val r = b.getValue("${lm.name} seed=${lm.seed}")
                r.both - r.swapped >= 20 && signTestP(r.both, r.both + r.swapped) < .01
            }
            println("VERDICT B $family processes these instructions: ${if (ok) "SUPPORTED" else "NOT SUPPORTED"}")
        }
        val blind = listOf("unigram", "kn7") + cdxNames
        println("CHECK B context-blind models answer both instructions alike (both = swapped = 0): " +
            if (blind.all { b.getValue(it).both == 0 && b.getValue(it).swapped == 0 }) "ok" else "FAILED")
    }
}
