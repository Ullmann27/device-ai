package at.heinz.deviceai.learning.span

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Bounded, stand-alone, offline EXP-04/05/06 shadow model. No production checkpoint or network use.
 *
 * [Features] switches input-feature variants without changing the 4,110 trainable parameters:
 * `identifierPlaceholders` replaces tokens containing digits (entity ids, codes, numbers) in a
 * candidate's context by `<id>`/`<num>` so arbitrary identifiers do not hash into learned buckets;
 * `positionFeatures=false` zeroes the hand-coded "last token" and "absolute position" inputs;
 * `entityProximity` (requires `positionFeatures=false`) reuses those two input slots for the
 * closeness to the asked entity id inside the clause, counted in candidate tokens (entity before
 * the candidate, entity after the candidate) – a binding cue whose weight is learned.
 * The default reproduces PR #61 exactly. Checkpoint v2 stores the variant; v1 checkpoints restore
 * with the default variant.
 */
class ContinuumResidualSpanCore private constructor(
    private val seed: Int,
    val features: Features,
    private val questionDelta: DoubleArray,
    private val contextDelta: DoubleArray,
    private val spanWeights: DoubleArray,
    private var spanBias: Double,
    private val abstainWeights: DoubleArray,
    var updates: Int
) {
    data class Features(
        val identifierPlaceholders: Boolean = false,
        val positionFeatures: Boolean = true,
        val entityProximity: Boolean = false
    ) {
        init { require(!(entityProximity && positionFeatures)) { "entityProximity replaces the position inputs" } }
        internal val flags: Int get() = (if (identifierPlaceholders) 1 else 0) or (if (positionFeatures) 0 else 2) or
            (if (entityProximity) 4 else 0)
        companion object {
            internal fun fromFlags(flags: Int): Features {
                require(flags in 0..7 && flags != 5) { "unknown CSX1 feature flags " + flags }
                return Features(flags and 1 != 0, flags and 2 == 0, flags and 4 != 0)
            }
        }
    }
    data class Example(val evidence: String, val question: String, val answer: String?)
    data class Prediction(val answer: String?, val start: Int, val end: Int, val confidence: Double, val candidates: Int)
    data class Gradient(val loss: Double, val values: DoubleArray)
    private data class Token(val value: String, val start: Int, val end: Int, val clause: Int)
    private data class Candidate(
        val token: Token,
        val buckets: IntArray,
        val basis: DoubleArray,
        val delta: DoubleArray,
        val features: DoubleArray
    )
    private data class Pass(
        val candidates: List<Candidate>,
        val qBuckets: IntArray,
        val qBasis: DoubleArray,
        val qDelta: DoubleArray,
        val nullFeatures: DoubleArray,
        val logits: DoubleArray,
        val probabilities: DoubleArray,
        val logSumExp: Double
    )

    constructor(seed: Int = 20261022, features: Features = Features()) : this(
        seed,
        features,
        DoubleArray(EMBEDDING),
        DoubleArray(EMBEDDING),
        DoubleArray(SPAN_FEATURES),
        0.0,
        DoubleArray(NULL_FEATURES),
        0
    )

    init {
        require(questionDelta.size == EMBEDDING && contextDelta.size == EMBEDDING)
        require(spanWeights.size == SPAN_FEATURES && abstainWeights.size == NULL_FEATURES)
        require(updates in 0..MAX_UPDATES)
        require(parameters().all(Double::isFinite))
    }

    val parameterCount: Int get() = PARAMETERS

    @Synchronized fun parameters(): DoubleArray =
        questionDelta + contextDelta + spanWeights + doubleArrayOf(spanBias) + abstainWeights

    @Synchronized fun loadParameters(values: DoubleArray) {
        require(values.size == PARAMETERS && values.all {
            it.isFinite() && kotlin.math.abs(it) <= MAX_WEIGHT
        })
        var i = 0
        for (j in questionDelta.indices) questionDelta[j] = values[i++]
        for (j in contextDelta.indices) contextDelta[j] = values[i++]
        for (j in spanWeights.indices) spanWeights[j] = values[i++]
        spanBias = values[i++]
        for (j in abstainWeights.indices) abstainWeights[j] = values[i++]
    }

    @Synchronized fun predict(evidence: String, question: String): Prediction {
        val f = forward(evidence, question)
        val best = f.probabilities.indices.maxBy { f.probabilities[it] }
        if (best == 0) return Prediction(null, -1, -1, f.probabilities[0], f.candidates.size)
        val token = f.candidates[best - 1].token
        val answer = evidence.substring(token.start, token.end)
        check(answer == token.value)
        return Prediction(answer, token.start, token.end, f.probabilities[best], f.candidates.size)
    }

    /** Exact gradient for logSumExp - goldLogit + L2, with no model mutation. */
    @Synchronized fun gradient(example: Example, l2: Double = 0.0001): Gradient {
        require(l2.isFinite() && l2 in 0.0..0.1)
        val f = forward(example.evidence, example.question)
        val gold = resolveGold(f, example.answer)
        val params = parameters()
        var loss = f.logSumExp - f.logits[gold]
        val grad = DoubleArray(PARAMETERS) { l2 * params[it] }
        loss += l2 * params.sumOf { it * it } * 0.5
        val dl = f.probabilities.copyOf()
        dl[gold] -= 1.0
        val offContext = EMBEDDING
        val offSpan = EMBEDDING * 2
        val offBias = offSpan + SPAN_FEATURES
        val offNull = offBias + 1
        for (j in 0 until NULL_FEATURES) grad[offNull + j] += dl[0] * f.nullFeatures[j]
        val dQ = DoubleArray(DIM)
        val residualScale = 1.0 + spanWeights[0]
        for ((i, candidate) in f.candidates.withIndex()) {
            val ds = dl[i + 1]
            for (j in 0 until SPAN_FEATURES) grad[offSpan + j] += ds * candidate.features[j]
            grad[offBias] += ds
            val dk = DoubleArray(DIM)
            for (j in 0 until DIM) {
                dQ[j] += ds * residualScale * (candidate.basis[j] + candidate.delta[j])
                dk[j] = ds * residualScale * (f.qBasis[j] + f.qDelta[j])
            }
            val scale = 1.0 / sqrt(candidate.buckets.size.toDouble())
            for (bucket in candidate.buckets) for (j in 0 until DIM) {
                grad[offContext + bucket * DIM + j] += dk[j] * scale
            }
        }
        val qScale = 1.0 / sqrt(f.qBuckets.size.toDouble())
        for (bucket in f.qBuckets) for (j in 0 until DIM) grad[bucket * DIM + j] += dQ[j] * qScale
        check(loss.isFinite() && grad.all(Double::isFinite))
        return Gradient(loss, grad)
    }

    /** One atomic parameter update following full frozen-state gradient accumulation. */
    @Synchronized fun train(example: Example, rate: Double = 0.025, l2: Double = 0.0001): Double {
        require(rate.isFinite() && rate in 0.0..0.1 && updates < MAX_UPDATES)
        val g = gradient(example, l2)
        val old = parameters()
        val next = DoubleArray(PARAMETERS) { i ->
            old[i] - rate * g.values[i].coerceIn(-MAX_GRAD, MAX_GRAD)
        }
        require(next.all { it.isFinite() && kotlin.math.abs(it) <= MAX_WEIGHT })
        loadParameters(next)
        updates++
        return g.loss
    }

    /** Bounded, cancellable shadow training. Invalid gold spans fail before any update. */
    @Synchronized fun trainSession(
        examples: List<Example>,
        epochs: Int = 10,
        rate: Double = 0.025,
        maxMillis: Long = 4_000L,
        cancelled: () -> Boolean = { false }
    ): Session {
        require(examples.isNotEmpty() && examples.size <= 512)
        require(epochs in 1..100 && rate.isFinite() && rate in 0.000001..0.1)
        require(maxMillis in 1L..60_000L)
        for (example in examples) loss(example)
        val before = examples.map(::loss).average()
        val started = System.nanoTime()
        var steps = 0
        var completeEpochs = 0
        var interrupted = false
        epochLoop@ for (epoch in 0 until epochs) {
            for (example in examples) {
                val elapsed = (System.nanoTime() - started) / 1_000_000L
                if (cancelled() || Thread.currentThread().isInterrupted || elapsed >= maxMillis) {
                    interrupted = true
                    break@epochLoop
                }
                train(example, rate / (1.0 + 0.05 * epoch))
                steps++
            }
            completeEpochs++
        }
        return Session(steps, completeEpochs, interrupted, before, examples.map(::loss).average())
    }

    data class Session(
        val steps: Int,
        val completeEpochs: Int,
        val interrupted: Boolean,
        val lossBefore: Double,
        val lossAfter: Double
    )

    @Synchronized fun loss(example: Example, l2: Double = 0.0001): Double {
        require(l2.isFinite() && l2 in 0.0..0.1)
        val f = forward(example.evidence, example.question)
        val gold = resolveGold(f, example.answer)
        return f.logSumExp - f.logits[gold] + 0.5 * l2 * parameters().sumOf { it * it }
    }

    @Synchronized fun save(): ByteArray {
        val bos = ByteArrayOutputStream(SERIALIZED_BYTES)
        DataOutputStream(bos).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeInt(features.flags)
            out.writeInt(seed)
            out.writeInt(updates)
            for (v in parameters()) out.writeDouble(v)
        }
        val payload = bos.toByteArray()
        check(payload.size == PAYLOAD_BYTES)
        return payload + MessageDigest.getInstance("SHA-256").digest(payload)
    }

    private fun resolveGold(f: Pass, answer: String?): Int {
        if (answer == null) return 0
        require(answer.isNotBlank())
        val matches = f.candidates.withIndex().filter { it.value.token.value == answer }
        require(matches.size == 1) {
            "INVALID_GOLD_SPAN: expected unique literal token, found " + matches.size
        }
        return matches.single().index + 1
    }

    private fun forward(evidence: String, question: String): Pass {
        require(evidence.isNotBlank() && question.isNotBlank())
        require(evidence.toByteArray(Charsets.UTF_8).size <= MAX_EVIDENCE_BYTES)
        require(question.toByteArray(Charsets.UTF_8).size <= MAX_QUESTION_BYTES)
        val tokens = tokenize(evidence)
        require(tokens.size <= MAX_TOKENS)
        require(tokens.map { it.clause }.distinct().size <= MAX_CLAUSES)
        val qTokens = tokenize(question).map { normalize(it.value) }
        val ids = qTokens.filter { it.any(Char::isDigit) }.toSet()
        val questionWords = qTokens.filter { it !in ids && it.length >= 2 }
        val qBuckets = buckets(questionWords.flatMap(::grams) + questionWords.map { "q:" + it })
        val qb = embed(qBuckets, true, false)
        val qd = embed(qBuckets, true, true)
        val candidates = ArrayList<Candidate>()
        val isCandidate = tokens.map { t ->
            val w = normalize(t.value)
            !(w.length < 2 || w in STOP_WORDS || w in ids || w in QUESTION_WORDS || (w in questionWords && !w.any(Char::isDigit)))
        }
        /** 1 / (1 + candidate tokens between this token and the nearest asked-id mention on that side), 0 if none. */
        fun proximity(idx: Int, step: Int): Double {
            var between = 0
            var j = idx + step
            while (j in tokens.indices && tokens[j].clause == tokens[idx].clause) {
                if (normalize(tokens[j].value) in ids) return 1.0 / (1.0 + between)
                if (isCandidate[j]) between++
                j += step
            }
            return 0.0
        }
        for ((idx, t) in tokens.withIndex()) {
            val word = normalize(t.value)
            if (!isCandidate[idx]) continue
            val sameClause = tokens.filter { it.clause == t.clause }
            val index = sameClause.indexOf(t)
            val neighbours = listOfNotNull(
                sameClause.getOrNull(index - 2)?.let { "l2:" + contextWord(it.value) },
                sameClause.getOrNull(index - 1)?.let { "l1:" + contextWord(it.value) },
                sameClause.getOrNull(index + 1)?.let { "r1:" + contextWord(it.value) }
            )
            val other = sameClause.filter { it !== t }.map { contextWord(it.value) }
            val cb = buckets(neighbours + other.filter { it.length >= 3 }.flatMap(::grams))
            val basis = embed(cb, false, false)
            val delta = embed(cb, false, true)
            val residual = (0 until DIM).sumOf { qb[it] * delta[it] + qd[it] * basis[it] + qd[it] * delta[it] }
            val questionGrams = questionWords.flatMap(::grams).toSet()
            val overlap = questionGrams.intersect(sameClause.filter { it !== t }.map { normalize(it.value) }.flatMap(::grams).toSet()).size.toDouble() /
                (1.0 + questionGrams.size)
            val entityFound = if (ids.isEmpty()) 0.0 else if (sameClause.any {
                normalize(it.value) in ids
            }) 1.0 else -1.0
            val left = sameClause.take(index).takeLast(3).map { normalize(it.value) }
            val negation = if (left.any { it in NEGATIONS }) 1.0 else 0.0
            val contrast = if (left.any { it in CONTRAST }) 1.0 else 0.0
            val code = if (t.value.any(Char::isDigit) && t.value.any(Char::isLetter)) 1.0 else 0.0
            val capital = if (t.value.firstOrNull()?.isUpperCase() == true) 1.0 else 0.0
            val last = when {
                features.entityProximity -> proximity(idx, -1)
                features.positionFeatures && index == sameClause.lastIndex -> 1.0
                else -> 0.0
            }
            val position = when {
                features.entityProximity -> proximity(idx, 1)
                features.positionFeatures -> 1.0 / (1.0 + idx)
                else -> 0.0
            }
            val inputs = doubleArrayOf(
                residual, overlap, entityFound, negation, contrast, code, capital, last, position
            )
            candidates.add(Candidate(t, cb, basis, delta, inputs))
            require(candidates.size <= MAX_CANDIDATES)
        }
        val maxOverlap = candidates.maxOfOrNull { it.features[1] } ?: 0.0
        val maxBinding = candidates.maxOfOrNull { it.features[2] } ?: 0.0
        val nullFeatures = doubleArrayOf(1.0, maxOverlap, maxBinding, if (candidates.isEmpty()) 1.0 else 0.0)
        val logits = DoubleArray(candidates.size + 1)
        logits[0] = abstainWeights.indices.sumOf { abstainWeights[it] * nullFeatures[it] }
        for ((i, c) in candidates.withIndex()) logits[i + 1] = c.features[0] + spanBias +
            spanWeights.indices.sumOf { spanWeights[it] * c.features[it] }
        require(logits.all(Double::isFinite))
        val max = logits.max()
        val exps = DoubleArray(logits.size) { exp(logits[it] - max) }
        val denom = exps.sum()
        require(denom.isFinite() && denom > 0.0)
        val probs = DoubleArray(logits.size) { exps[it] / denom }
        return Pass(candidates, qBuckets, qb, qd, nullFeatures, logits, probs, max + ln(denom))
    }

    private fun embed(buckets: IntArray, question: Boolean, delta: Boolean): DoubleArray {
        val out = DoubleArray(DIM)
        val scale = 1.0 / sqrt(buckets.size.toDouble())
        for (bucket in buckets) for (j in 0 until DIM) {
            val idx = bucket * DIM + j
            out[j] += scale * when {
                delta && question -> questionDelta[idx]
                delta -> contextDelta[idx]
                question -> 0.30 * sin((idx + 1) * 0.317 + seed * 0.019)
                else -> 0.30 * cos((idx + 1) * 0.271 + seed * 0.023)
            }
        }
        return out
    }

    private fun tokenize(value: String): List<Token> {
        val out = ArrayList<Token>()
        var clause = 0
        var offset = 0
        val regex = Regex("[\\p{L}\\p{N}]+(?:-[\\p{L}\\p{N}]+)*")
        for (segment in value.split(Regex("(?<=[.;\\n])"))) {
            regex.findAll(segment).forEach { m ->
                out.add(Token(m.value, offset + m.range.first, offset + m.range.last + 1, clause))
            }
            offset += segment.length
            clause++
        }
        return out
    }

    private fun normalize(word: String): String = word.lowercase(java.util.Locale.ROOT)

    /** Context key of a neighbouring word; identifiers become a type placeholder when enabled. */
    private fun contextWord(word: String): String = when {
        !features.identifierPlaceholders || word.none(Char::isDigit) -> normalize(word)
        word.all(Char::isDigit) -> "<num>"
        else -> "<id>"
    }

    private fun grams(word: String): List<String> = buildList {
        for (n in 3..4) {
            if (word.length >= n) for (i in 0..word.length - n) add(word.substring(i, i + n))
        }
    }

    private fun buckets(words: List<String>): IntArray = words.map { word ->
        var h = 0x811c9dc5.toInt()
        for (c in word) {
            h = h xor c.code
            h *= 0x01000193
        }
        (h and Int.MAX_VALUE) % BUCKETS
    }.distinct().take(MAX_BUCKETS).ifEmpty { listOf(0) }.toIntArray()

    companion object {
        private const val BUCKETS = 128
        private const val DIM = 16
        private const val EMBEDDING = BUCKETS * DIM
        private const val SPAN_FEATURES = 9
        private const val NULL_FEATURES = 4
        const val PARAMETERS = 2 * EMBEDDING + SPAN_FEATURES + 1 + NULL_FEATURES
        const val PAYLOAD_BYTES = 20 + PARAMETERS * 8
        const val SERIALIZED_BYTES = PAYLOAD_BYTES + 32
        /** Checkpoint v1 (PR #61): no feature-flag field; restores with the default [Features]. */
        const val LEGACY_PAYLOAD_BYTES = 16 + PARAMETERS * 8
        const val LEGACY_SERIALIZED_BYTES = LEGACY_PAYLOAD_BYTES + 32
        private const val MAGIC = 0x43535833
        private const val VERSION = 2
        private const val MAX_UPDATES = 100000
        private const val MAX_WEIGHT = 1000.0
        private const val MAX_GRAD = 5.0
        private const val MAX_EVIDENCE_BYTES = 1024
        private const val MAX_QUESTION_BYTES = 256
        private const val MAX_TOKENS = 128
        private const val MAX_CLAUSES = 16
        private const val MAX_CANDIDATES = 64
        private const val MAX_BUCKETS = 128
        private val STOP_WORDS = setOf(
            "der", "die", "das", "den", "dem", "des", "ein", "eine", "einer", "einen",
            "einem", "und", "oder", "als", "in", "im", "am", "an", "auf", "mit",
            "für", "von", "ist", "war", "hat", "seine", "seinen", "sie", "er", "es"
        )
        private val QUESTION_WORDS = setOf("welche", "welcher", "welches", "welchen", "wo", "was", "wie", "wer")
        private val NEGATIONS = setOf("nicht", "kein", "keine", "keinen")
        private val CONTRAST = setOf("sondern", "stattdessen")

        fun restore(bytes: ByteArray): ContinuumResidualSpanCore {
            require(bytes.size == SERIALIZED_BYTES || bytes.size == LEGACY_SERIALIZED_BYTES)
            val payloadSize = bytes.size - 32
            val payload = bytes.copyOfRange(0, payloadSize)
            val actual = MessageDigest.getInstance("SHA-256").digest(payload)
            require(MessageDigest.isEqual(actual, bytes.copyOfRange(payloadSize, bytes.size)))
            return DataInputStream(ByteArrayInputStream(payload)).use { input ->
                require(input.readInt() == MAGIC)
                val version = input.readInt()
                require(version == if (payloadSize == PAYLOAD_BYTES) VERSION else 1)
                val features = if (version == 1) Features() else Features.fromFlags(input.readInt())
                val seed = input.readInt()
                val updates = input.readInt()
                require(updates in 0..MAX_UPDATES)
                val flat = DoubleArray(PARAMETERS) { input.readDouble() }
                require(input.available() == 0 && flat.all {
                    it.isFinite() && kotlin.math.abs(it) <= MAX_WEIGHT
                })
                ContinuumResidualSpanCore(seed, features).apply {
                    loadParameters(flat)
                    this.updates = updates
                }
            }
        }
    }
}
