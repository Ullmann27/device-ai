package at.heinz.deviceai.learning.synapse

import java.security.MessageDigest
import java.util.Locale
import java.util.Random
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * EXP-SYNAPSE-ZERO v2: a learned, proof-gated evidence reader. Research shadow only: JVM, in memory, nothing persisted,
 * no access to SWB1/CDX1/CSX1 weights or owner data, not wired into the chat.
 *
 * LEARNED (exact analytic gradients, softmax over all candidates plus "abstain"):
 * - hashed word and character-trigram embeddings for question words (Q) and evidence fillers (F);
 * - role vectors that bind each context filler to its position relative to the candidate by exact circular
 *   convolution (HRR binding, [Binding.HRR]); with [Binding.BAG] every role is the identity (plain bilinear model);
 * - two special fillers that keep the asked entity apart from every other identifier (entity binding);
 * - a bridge query vector and scalar feature weights for two-hop chains; abstention weights.
 *
 * FIXED (documented, not learned): tokenization, sentence and clause split, identifier shape, candidate generation,
 * a small grammatical stop-word list (TRAIN vocabulary only), and the structural proof check: every answer is an exact
 * token of the evidence at verified UTF-16 offsets under the SHA-256 of the evidence, its sentence names the asked
 * entity (one hop) or a bridge identifier that occurs verbatim in a second sentence naming the asked entity (two hops).
 * A score can rank candidates or choose to abstain; it can never produce text that is not in the evidence.
 */
class SynapseV2Reasoner(val config: Config, seed: Long = 20261022L) {
    enum class Binding { BAG, HRR }

    data class Config(
        val binding: Binding = Binding.HRR,
        val twoHop: Boolean = false,
        /** Inference-only competition between entities in a sentence (A3). */
        val sinkhorn: Boolean = false,
        val dim: Int = 16,
        val buckets: Int = 2048,
        /** Ablation: embeddings and roles frozen at zero; only scalar feature and abstention weights learn. */
        val scalarOnly: Boolean = false
    ) {
        init { require(dim in 4..64 && buckets in 64..65536) }
    }

    data class Example(val evidence: String, val question: String, val answer: String?, val bridge: String? = null)

    data class Token(val text: String, val lower: String, val start: Int, val end: Int, val sentence: Int,
                     val segment: Int, val position: Int, val isId: Boolean)

    data class Proof(
        val answer: String, val answerStart: Int, val answerEnd: Int, val sentence: Int,
        /** Entity the answer sentence is about: the asked entity (one hop) or the bridge identifier (two hops). */
        val anchor: String, val anchorStart: Int, val anchorEnd: Int,
        val hops: Int,
        /** Two hops: the asked entity and the bridge identifier in the bridge sentence; -1 otherwise. */
        val entityStart: Int, val bridgeStart: Int, val bridgeSentence: Int,
        val sourceSha256: String
    )

    data class Answer(val answer: String?, val confidence: Double, val proof: Proof?, val candidates: Int, val reason: String)

    companion object {
        const val ROLES = 9 // FAR_L, L3, L2, L1, SELF, R1, R2, R3, FAR_R
        const val SELF_ROLE = 4
        const val SPECIALS = 2 // anchor entity, other identifier
        const val SCALARS = 10
        const val NULL_FEATURES = 4
        private val WORD = Regex("[\\p{L}\\p{N}]+(?:-[\\p{L}\\p{N}]+)*")
        private val IDENTIFIER = Regex("[A-Za-z]{1,12}-?[0-9]+[A-Za-z0-9-]*")
        private const val SENTENCE_END = ".!?;"
        /** Grammatical words that can never be an answer (TRAIN vocabulary only; unseen words stay candidates). */
        val STOP = setOf("der", "die", "das", "den", "dem", "des", "ein", "eine", "einen", "einem", "einer", "und", "oder",
            "als", "in", "im", "am", "an", "auf", "aus", "mit", "von", "vom", "zu", "zur", "zum", "für", "ist", "sind", "war",
            "wird", "wurde", "hat", "per", "über", "bei", "nach", "dass", "sich", "es", "teil")
        /** Negation cues that occur in TRAIN. Unseen cues (nie, keinesfalls, …) are deliberately NOT listed. */
        val CUES = setOf("nicht", "kein", "keine", "keinen", "keinem", "keiner")
        private const val CONTRAST = "sondern"

        fun sha256(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }

        fun tokenize(text: String): List<Token> {
            val out = ArrayList<Token>()
            var sentence = 0; var segment = 0; var position = 0; var cursor = 0
            for (m in WORD.findAll(text)) {
                for (i in cursor until m.range.first) when (text[i]) {
                    in SENTENCE_END -> { sentence++; segment++; position = 0 }
                    ',' , ':' -> segment++
                }
                cursor = m.range.last + 1
                out += Token(m.value, m.value.lowercase(Locale.ROOT), m.range.first, m.range.last + 1, sentence, segment,
                    position++, IDENTIFIER.matches(m.value))
            }
            return out
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Parameters: one flat vector, so that finite-difference tests can perturb every single weight.
    // ---------------------------------------------------------------------------------------------
    private val d = config.dim
    private val qOffset = 0
    private val fOffset = qOffset + config.buckets * d
    private val roleOffset = fOffset + (config.buckets + SPECIALS) * d
    private val bridgeQueryOffset = roleOffset + ROLES * d
    private val scalarOffset = bridgeQueryOffset + d
    private val bridgeScalarOffset = scalarOffset + SCALARS
    private val hopOffset = bridgeScalarOffset + SCALARS
    private val nullOffset = hopOffset + 1
    val parameterCount = nullOffset + NULL_FEATURES
    private val theta = DoubleArray(parameterCount)
    var updates = 0
        private set

    init {
        val rnd = Random(seed)
        if (!config.scalarOnly) {
            for (i in qOffset until roleOffset) theta[i] = rnd.nextGaussian() * 0.1
            for (i in bridgeQueryOffset until scalarOffset) theta[i] = rnd.nextGaussian() * 0.1
        }
        // roles start as the identity of circular convolution (e_0): HRR starts exactly where BAG stays
        if (!config.scalarOnly) for (r in 0 until ROLES) theta[roleOffset + r * d] = 1.0
    }

    fun parameters(): DoubleArray = theta.copyOf()
    fun loadParameters(values: DoubleArray) { require(values.size == parameterCount); values.copyInto(theta) }

    private fun frozen(index: Int): Boolean = when {
        config.scalarOnly && index < scalarOffset -> true
        config.binding == Binding.BAG && index in roleOffset until bridgeQueryOffset -> true
        else -> false
    }

    // ---------------------------------------------------------------------------------------------
    // Features
    // ---------------------------------------------------------------------------------------------
    private fun buckets(word: String): IntArray {
        val lower = word.lowercase(Locale.ROOT)
        val keys = ArrayList<String>()
        keys += "w:$lower"
        val padded = "^$lower$"
        for (i in 0..padded.length - 3) keys += "g:" + padded.substring(i, i + 3)
        return keys.map { k -> ((k.hashCode() * -0x61c88647).ushr(1)) % config.buckets }.distinct().toIntArray()
    }

    /** Sparse weighted sum of embedding rows: (row index, weight) pairs. */
    private class Bag(val rows: IntArray, val weights: DoubleArray)

    private fun bag(entries: List<Pair<Int, Double>>): Bag {
        val merged = LinkedHashMap<Int, Double>()
        for ((r, w) in entries) merged[r] = (merged[r] ?: 0.0) + w
        return Bag(merged.keys.toIntArray(), merged.values.toDoubleArray())
    }

    private fun wordRows(word: String, tableOffset: Int, scale: Double): List<Pair<Int, Double>> {
        val b = buckets(word)
        val w = scale / sqrt(b.size.toDouble())
        return b.map { (tableOffset + it * d) to w }
    }

    /** View of one candidate relative to one anchor entity mention: role fillers + scalar features. */
    private class View(val fillers: Array<Bag>, val scalars: DoubleArray)

    private data class Candidate(val answer: Token, val anchor: Token, val hops: Int,
                                 val bridge: Token?, val entity: Token?, val tail: View, val head: View?)

    private fun questionEntity(question: String): String? =
        tokenize(question).filter { it.isId }.map { it.text }.distinct().singleOrNull()

    private fun questionWords(question: String, entity: String?): List<Token> = tokenize(question).filter { it.text != entity }

    private fun questionBag(question: String, entity: String?): Bag {
        val words = questionWords(question, entity)
        if (words.isEmpty()) return Bag(IntArray(0), DoubleArray(0))
        val scale = 1.0 / sqrt(words.size.toDouble())
        return bag(words.flatMap { wordRows(it.text, qOffset, scale) })
    }

    private fun eligible(t: Token, anchorText: String, entity: String?): Boolean =
        t.text != anchorText && t.text != entity && t.lower !in STOP && t.lower !in CUES && t.lower != CONTRAST && t.text.length >= 2

    private fun roleOf(offset: Int): Int = when {
        offset <= -4 -> 0
        offset >= 4 -> 8
        else -> offset + SELF_ROLE
    }

    private fun view(tokens: List<Token>, candidate: Token, anchor: Token, questionLower: Set<String>): View {
        val sentence = tokens.filter { it.sentence == candidate.sentence }
        val perRole = Array(ROLES) { ArrayList<Token>() }
        for (t in sentence) perRole[roleOf(t.position - candidate.position)] += t
        val fillers = Array(ROLES) { r ->
            val members = perRole[r]
            if (members.isEmpty()) Bag(IntArray(0), DoubleArray(0)) else {
                val scale = 1.0 / sqrt(members.size.toDouble())
                bag(members.flatMap { t ->
                    when {
                        t.text == anchor.text && t !== candidate -> listOf((fOffset + config.buckets * d) to scale)
                        t.isId -> listOf((fOffset + (config.buckets + 1) * d) to scale)
                        else -> wordRows(t.text, fOffset, scale)
                    }
                })
            }
        }
        val lo = minOf(candidate.position, anchor.position); val hi = maxOf(candidate.position, anchor.position)
        val between = sentence.filter { it.position in (lo + 1) until hi }
        val segment = sentence.filter { it.segment == candidate.segment }
        val before = segment.filter { it.position < candidate.position }
        val contentQuestion = questionLower.filter { it !in STOP }
        val overlap = if (contentQuestion.isEmpty()) 0.0 else contentQuestion.count { q -> segment.any { it.lower == q } }.toDouble() / contentQuestion.size
        val scalars = doubleArrayOf(
            1.0 / (1 + abs(candidate.position - anchor.position)),
            if (candidate.position > anchor.position) 1.0 else 0.0,
            if (between.any { it.isId && it.text != anchor.text }) 1.0 else 0.0,
            if (before.any { it.lower in CUES }) 1.0 else 0.0,
            if (before.any { it.lower == CONTRAST }) 1.0 else 0.0,
            overlap,
            if (candidate.isId) 1.0 else 0.0,
            if (candidate.text[0].isUpperCase()) 1.0 else 0.0,
            if (segment.any { it.text == anchor.text }) 1.0 else 0.0,
            if (sentence.any { it.lower in CUES }) 1.0 else 0.0
        )
        return View(fillers, scalars)
    }

    private fun candidates(evidence: String, question: String): Pair<List<Token>, List<Candidate>> {
        val tokens = tokenize(evidence)
        val entity = questionEntity(question) ?: return tokens to emptyList()
        val questionLower = questionWords(question, entity).map { it.lower }.toSet()
        val out = ArrayList<Candidate>()
        val entitySentences = tokens.filter { it.text == entity }.map { it.sentence }.toSet()
        fun nearest(sentence: Int, text: String, to: Token) =
            tokens.filter { it.sentence == sentence && it.text == text }.minBy { abs(it.position - to.position) }
        for (t in tokens) if (t.sentence in entitySentences && eligible(t, entity, entity)) {
            val anchor = nearest(t.sentence, entity, t)
            out += Candidate(t, anchor, 1, null, null, view(tokens, t, anchor, questionLower), null)
        }
        if (config.twoHop) {
            for (s in entitySentences) for (b in tokens.filter { it.sentence == s && it.isId && it.text != entity }) {
                val entityMention = nearest(s, entity, b)
                val head = view(tokens, b, entityMention, questionLower)
                val tailSentences = tokens.filter { it.text == b.text && it.sentence != s && it.sentence !in entitySentences }
                    .map { it.sentence }.toSet()
                for (t in tokens) if (t.sentence in tailSentences && eligible(t, b.text, entity)) {
                    val anchor = nearest(t.sentence, b.text, t)
                    out += Candidate(t, anchor, 2, b, entityMention, view(tokens, t, anchor, questionLower), head)
                    if (out.size > 600) break
                }
            }
        }
        return tokens to out
    }

    // ---------------------------------------------------------------------------------------------
    // Scoring: HRR binding  s = Σ_role < role ⊛ filler , g >,  BAG: s = Σ_role < filler , g >
    // ---------------------------------------------------------------------------------------------
    private fun dense(b: Bag): DoubleArray {
        val v = DoubleArray(d)
        for (i in b.rows.indices) { val row = b.rows[i]; val w = b.weights[i]; for (k in 0 until d) v[k] += theta[row + k] * w }
        return v
    }
    private fun conv(a: DoubleArray, b: DoubleArray) = DoubleArray(d) { k -> var s = 0.0; for (j in 0 until d) s += a[j] * b[(k - j + d) % d]; s }
    private fun corr(a: DoubleArray, b: DoubleArray) = DoubleArray(d) { k -> var s = 0.0; for (j in 0 until d) s += a[j] * b[(j + k) % d]; s }
    private fun role(r: Int) = DoubleArray(d) { theta[roleOffset + r * d + it] }

    private fun semantic(v: View, g: DoubleArray): Double {
        var s = 0.0
        for (r in 0 until ROLES) {
            val f = dense(v.fillers[r])
            val bound = if (config.binding == Binding.HRR) conv(role(r), f) else f
            for (k in 0 until d) s += bound[k] * g[k]
        }
        return s
    }
    private fun scalar(v: View, offset: Int): Double = (0 until SCALARS).sumOf { theta[offset + it] * v.scalars[it] }

    private fun nullFeatures(cands: List<Candidate>, evidenceLower: Set<String>, question: String): DoubleArray {
        val entity = questionEntity(question)
        val q = questionWords(question, entity).map { it.lower }.filter { it !in STOP }
        return doubleArrayOf(1.0,
            cands.maxOfOrNull { it.tail.scalars[5] } ?: 0.0,
            if (cands.isEmpty()) 1.0 else 0.0,
            if (q.isEmpty()) 0.0 else q.count { it in evidenceLower }.toDouble() / q.size)
    }

    private class Forward(val tokens: List<Token>, val candidates: List<Candidate>, val logits: DoubleArray,
                          val probabilities: DoubleArray, val g: DoubleArray, val qBag: Bag, val nullFeatures: DoubleArray,
                          val questionLower: Set<String>)

    private fun forward(evidence: String, question: String): Forward {
        val (tokens, cands) = candidates(evidence, question)
        val entity = questionEntity(question)
        val qBag = questionBag(question, entity)
        val g = dense(qBag)
        val gBridge = DoubleArray(d) { theta[bridgeQueryOffset + it] }
        val nf = nullFeatures(cands, tokens.map { it.lower }.toSet(), question)
        val logits = DoubleArray(cands.size + 1)
        logits[0] = (0 until NULL_FEATURES).sumOf { theta[nullOffset + it] * nf[it] }
        for ((i, c) in cands.withIndex()) {
            var z = semantic(c.tail, g) + scalar(c.tail, scalarOffset)
            if (c.hops == 2) z += semantic(c.head!!, gBridge) + scalar(c.head, bridgeScalarOffset) + theta[hopOffset]
            logits[i + 1] = z
        }
        val max = logits.max()
        val e = DoubleArray(logits.size) { exp(logits[it] - max) }
        val sum = e.sum()
        return Forward(tokens, cands, logits, DoubleArray(e.size) { e[it] / sum }, g, qBag, nf,
            questionWords(question, entity).map { it.lower }.toSet())
    }

    private fun goldIndices(f: Forward, example: Example): Set<Int> {
        if (example.answer == null) return setOf(0)
        val gold = f.candidates.withIndex().filter { (_, c) ->
            c.answer.text == example.answer && (example.bridge == null && c.hops == 1 ||
                example.bridge != null && c.hops == 2 && c.bridge?.text == example.bridge)
        }.map { it.index + 1 }.toSet()
        return gold
    }

    /** Loss = -log Σ_{gold} p (marginal likelihood over equal gold spans) + L2; exact gradient, no mutation. */
    fun lossAndGradient(example: Example, l2: Double = 1e-5): Pair<Double, Map<Int, Double>>? {
        val f = forward(example.evidence, example.question)
        val gold = goldIndices(f, example)
        if (gold.isEmpty()) return null
        val pGold = gold.sumOf { f.probabilities[it] }
        val grad = HashMap<Int, Double>()
        fun add(i: Int, v: Double) { if (v != 0.0 && !frozen(i)) grad[i] = (grad[i] ?: 0.0) + v }
        val gBridge = DoubleArray(d) { theta[bridgeQueryOffset + it] }
        val dg = DoubleArray(d); val dgBridge = DoubleArray(d)
        for (i in f.logits.indices) {
            val dz = f.probabilities[i] - (if (i in gold) f.probabilities[i] / pGold else 0.0)
            if (dz == 0.0) continue
            if (i == 0) { for (k in 0 until NULL_FEATURES) add(nullOffset + k, dz * f.nullFeatures[k]); continue }
            val c = f.candidates[i - 1]
            backSemantic(c.tail, f.g, dz, dg, ::add)
            for (k in 0 until SCALARS) add(scalarOffset + k, dz * c.tail.scalars[k])
            if (c.hops == 2) {
                backSemantic(c.head!!, gBridge, dz, dgBridge, ::add)
                for (k in 0 until SCALARS) add(bridgeScalarOffset + k, dz * c.head.scalars[k])
                add(hopOffset, dz)
            }
        }
        for (i in f.qBag.rows.indices) for (k in 0 until d) add(f.qBag.rows[i] + k, dg[k] * f.qBag.weights[i])
        for (k in 0 until d) add(bridgeQueryOffset + k, dgBridge[k])
        var loss = -ln(pGold)
        if (l2 > 0) for (i in grad.keys.toList()) { loss += 0.5 * l2 * theta[i] * theta[i]; grad[i] = grad.getValue(i) + l2 * theta[i] }
        return loss to grad
    }

    /** Accumulates d(dz * s)/d(params) for s = Σ_r <role_r ⊛ f_r, g>; dg collects d/dg. */
    private fun backSemantic(v: View, g: DoubleArray, dz: Double, dg: DoubleArray, add: (Int, Double) -> Unit) {
        for (r in 0 until ROLES) {
            val bag = v.fillers[r]
            if (bag.rows.isEmpty()) continue
            val f = dense(bag)
            val dFiller: DoubleArray
            if (config.binding == Binding.HRR) {
                val ro = role(r)
                dFiller = corr(ro, g)
                val dRole = corr(f, g)
                for (k in 0 until d) add(roleOffset + r * d + k, dz * dRole[k])
                val bound = conv(ro, f)
                for (k in 0 until d) dg[k] += dz * bound[k]
            } else {
                dFiller = g
                for (k in 0 until d) dg[k] += dz * f[k]
            }
            for (i in bag.rows.indices) for (k in 0 until d) add(bag.rows[i] + k, dz * dFiller[k] * bag.weights[i])
        }
    }

    /** Data loss only (l2 = 0), used by the finite-difference tests. */
    fun loss(example: Example): Double? = lossAndGradient(example, 0.0)?.first

    fun train(example: Example, rate: Double = 0.05, l2: Double = 1e-5): Double? {
        val (loss, grad) = lossAndGradient(example, l2) ?: return null
        for ((i, gv) in grad) theta[i] -= rate * gv
        updates++
        return loss
    }

    data class Session(val steps: Int, val skipped: Int, val lossFirstEpoch: Double, val lossLastEpoch: Double)

    fun train(examples: List<Example>, epochs: Int, rate: Double = 0.05, seed: Long = 1L): Session {
        val rnd = Random(seed)
        var skipped = 0; var first = 0.0; var last = 0.0; var steps = 0
        for (epoch in 0 until epochs) {
            var total = 0.0; var n = 0
            for (e in examples.shuffled(rnd)) {
                val l = train(e, rate)
                if (l == null) { if (epoch == 0) skipped++; continue }
                total += l; n++; steps++
            }
            if (epoch == 0) first = total / maxOf(1, n)
            last = total / maxOf(1, n)
        }
        return Session(steps, skipped, first, last)
    }

    // ---------------------------------------------------------------------------------------------
    // Inference with proof
    // ---------------------------------------------------------------------------------------------
    fun answer(evidence: String, question: String, threshold: Double = 0.0): Answer {
        val digest = sha256(evidence)
        if (evidence.isBlank() || question.isBlank() || evidence.length > 16384 || question.length > 512)
            return Answer(null, 0.0, null, 0, "INVALID_INPUT")
        val entity = questionEntity(question) ?: return Answer(null, 0.0, null, 0, "NO_SINGLE_ENTITY")
        val f = forward(evidence, question)
        if (f.candidates.isEmpty()) return Answer(null, f.probabilities[0], null, 0, "NO_CANDIDATE")
        val probabilities = if (config.sinkhorn) sinkhornAdjusted(f, entity) else f.probabilities
        // merge equal answer strings: P(answer = text)
        val merged = LinkedHashMap<String, Double>()
        for ((i, c) in f.candidates.withIndex()) merged[c.answer.text] = (merged[c.answer.text] ?: 0.0) + probabilities[i + 1]
        val (bestText, bestP) = merged.maxBy { it.value }
        if (probabilities[0] >= bestP) return Answer(null, probabilities[0], null, f.candidates.size, "ABSTAIN_LEARNED")
        if (bestP < threshold) return Answer(null, bestP, null, f.candidates.size, "ABSTAIN_THRESHOLD")
        val best = f.candidates.withIndex().filter { it.value.answer.text == bestText }.maxBy { probabilities[it.index + 1] }.value
        val proof = Proof(best.answer.text, best.answer.start, best.answer.end, best.answer.sentence,
            best.anchor.text, best.anchor.start, best.anchor.end, best.hops,
            best.entity?.start ?: -1, best.bridge?.start ?: -1, best.bridge?.sentence ?: -1, digest)
        if (!verify(evidence, question, proof)) return Answer(null, bestP, null, f.candidates.size, "ABSTAIN_PROOF")
        return Answer(best.answer.text, bestP, proof, f.candidates.size, if (best.hops == 2) "TWO_HOP" else "DIRECT")
    }

    /**
     * A3: entities named in the asked entity's sentences compete for candidate values (square Sinkhorn over the top
     * candidates). The abstention probability is kept from the plain softmax. Inference only, no extra parameters.
     */
    private fun sinkhornAdjusted(f: Forward, entity: String): DoubleArray {
        val oneHop = f.candidates.withIndex().filter { it.value.hops == 1 && !it.value.answer.isId }
        val sentences = oneHop.map { it.value.answer.sentence }.toSet()
        val entities = f.tokens.filter { it.isId && it.sentence in sentences }.map { it.text }.distinct()
        if (entities.size < 2 || entity !in entities) return f.probabilities
        fun z(entityText: String, c: Candidate): Double {
            if (entityText == c.answer.text) return -30.0
            val anchors = f.tokens.filter { it.sentence == c.answer.sentence && it.text == entityText }
            if (anchors.isEmpty()) return -30.0
            val anchor = anchors.minBy { abs(it.position - c.answer.position) }
            val v = view(f.tokens, c.answer, anchor, f.questionLower)
            return semantic(v, f.g) + scalar(v, scalarOffset)
        }
        val scores = entities.map { e -> oneHop.map { (_, c) -> z(e, c) } }
        // columns: the best distinct candidates, as many as there are entities (square problem)
        val columnOrder = oneHop.indices.sortedByDescending { j -> entities.indices.maxOf { scores[it][j] } }
        val columns = ArrayList<Int>(); val seen = HashSet<String>()
        for (j in columnOrder) if (seen.add(oneHop[j].value.answer.text)) { columns += j; if (columns.size == entities.size) break }
        if (columns.size < 2) return f.probabilities
        val n = entities.size; val m = columns.size
        val matrix = Array(n) { r -> val mx = columns.maxOf { scores[r][it] }; DoubleArray(m) { c -> exp(scores[r][columns[c]] - mx) + 1e-12 } }
        repeat(30) {
            for (r in 0 until n) { val s = matrix[r].sum(); for (c in 0 until m) matrix[r][c] /= s }
            for (c in 0 until m) { val s = (0 until n).sumOf { matrix[it][c] } / (n.toDouble() / m); for (r in 0 until n) matrix[r][c] /= s }
        }
        val row = matrix[entities.indexOf(entity)]; val rowSum = row.sum()
        val out = DoubleArray(f.probabilities.size)
        out[0] = f.probabilities[0]
        for ((c, j) in columns.withIndex()) out[oneHop[j].index + 1] = (1 - out[0]) * row[c] / rowSum
        return out
    }

    /** Structural proof check: exact token at the cited offsets, SHA-256 of the evidence, entity/bridge identity. */
    fun verify(evidence: String, question: String, proof: Proof): Boolean {
        if (proof.sourceSha256 != sha256(evidence)) return false
        val entity = questionEntity(question) ?: return false
        val tokens = tokenize(evidence)
        val answer = tokens.firstOrNull { it.start == proof.answerStart && it.end == proof.answerEnd } ?: return false
        val anchor = tokens.firstOrNull { it.start == proof.anchorStart && it.end == proof.anchorEnd } ?: return false
        if (answer.text != proof.answer || anchor.text != proof.anchor) return false
        if (answer.sentence != proof.sentence || anchor.sentence != answer.sentence) return false
        if (answer.text == anchor.text || answer.text == entity || answer.lower in STOP || answer.lower in CUES) return false
        return when (proof.hops) {
            1 -> anchor.text == entity && proof.entityStart == -1 && proof.bridgeStart == -1
            2 -> {
                val e = tokens.firstOrNull { it.start == proof.entityStart } ?: return false
                val b = tokens.firstOrNull { it.start == proof.bridgeStart } ?: return false
                e.text == entity && b.text == anchor.text && b.isId && e.sentence == b.sentence &&
                    b.sentence == proof.bridgeSentence && b.sentence != answer.sentence
            }
            else -> false
        }
    }
}
