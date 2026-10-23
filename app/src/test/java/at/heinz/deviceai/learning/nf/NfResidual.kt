package at.heinz.deviceai.learning.nf

import at.heinz.deviceai.learning.lm.KneserNeyBytes
import at.heinz.deviceai.learning.lm.LmCorpus
import at.heinz.deviceai.learning.lm.LmExperiment
import java.io.File
import java.security.MessageDigest

/**
 * NF-7 (pre-registered in docs/CONTINUUM-NEURAL-FRONTIER-LAB-20261022.md, section 8f): a byte minGRU trained on the
 * mixture loss with Kneser-Ney 7, so that it learns what the count model misses, instead of being trained alone and
 * mixed afterwards (NF-6b-lite).
 * NFC2 is the new data split for it: the project's Markdown documentation at 9fb4361c (the signed 0.3.16 source 6706da14
 * plus its release record), privacy-filtered and segmented like LM1/NFC1, split by SHA-256("nfc2" + path). Kept out:
 * process logs, licence texts, the experimenter's own reports, and every document that is TEST in LM1 (spent) or in
 * NFC1 (reserved).
 */
object NfCorpus2 {
    const val COMMIT = "9fb4361c896315e9e974dd5d8feab5ce1ce65803"
    /** Pinned in the pre-registration commit, before any NF-7 model is scored on NFC2. */
    const val VALIDATION_SHA256 = "73ec893c44549f039e2c4a281a60a62eec4df300060bd6e15b201561cdaf0146"
    const val TEST_SHA256 = "3deec29e97940625223b664f9180e54a605fa9225a7defa782183b43962a68bb"
    private val REPORTS = setOf("docs/CONTINUUM-LM1-OPUS-20261022.md", "docs/CONTINUUM-NEURAL-FRONTIER-LAB-20261022.md")

    fun splitOf(path: String): LmCorpus.Split {
        val b = MessageDigest.getInstance("SHA-256").digest(("nfc2\u0000" + path).toByteArray(Charsets.UTF_8))[0].toInt() and 255
        return when (b % 10) { 0 -> LmCorpus.Split.TEST; 1 -> LmCorpus.Split.VALIDATION; else -> LmCorpus.Split.TRAIN }
    }

    fun eligible(path: String) = path.endsWith(".md") && !path.startsWith("docs/evidence/") && path != "THIRD_PARTY_NOTICES.md" &&
        path !in REPORTS && LmCorpus.splitOf(path) != LmCorpus.Split.TEST && NfCorpus.splitOf(path) != LmCorpus.Split.TEST

    fun load(root: File): List<NfCorpus.Doc> =
        LmExperiment.git(root, "ls-tree", "-r", "--name-only", COMMIT).lines().filter { it.isNotBlank() && eligible(it) }.mapNotNull { path ->
            val segments = LmCorpus.segments(LmCorpus.clean(LmExperiment.git(root, "show", "$COMMIT:$path")))
            if (segments.isEmpty()) null else NfCorpus.Doc(path, NfCorpus.Domain.DOC, splitOf(path), segments)
        }.sortedBy { it.path }
}

object NfResidual {
    /**
     * The count model's probability of every target of a byte sequence: BOS + bytes + EOS predicts bytes + EOS, so the
     * byte targets get the count model's probability [p] of their byte and EOS gets 0 (the count model has no end symbol).
     */
    fun targetProbabilities(p: DoubleArray, targets: Int): DoubleArray {
        require(targets == p.size + 1) { "byte vocabulary expected: ${p.size} bytes, $targets targets" }
        return DoubleArray(targets) { if (it < p.size) p[it] else 0.0 }
    }

    fun targetProbabilities(kn: KneserNeyBytes, segment: String, targets: Int) = targetProbabilities(kn.detail(segment).prob, targets)

    class Overlap(val segments: Int, val verbatim: Int, val bytes: Int, val covered: Int)

    /**
     * Leakage check before TEST (section 9): how many [other] segments occur verbatim in [train], and how many of their
     * bytes lie in a [window]-byte window that also occurs in [train] (exact bytes, hashed with collisions checked).
     */
    fun overlap(train: List<String>, other: List<String>, window: Int = 50): Overlap {
        val seen = HashMap<String, Unit>()
        val trainSegments = train.toHashSet()
        for (s in train) {
            val b = s.toByteArray(Charsets.UTF_8)
            for (i in 0..b.size - window) seen[String(b, i, window, Charsets.ISO_8859_1)] = Unit
        }
        var bytes = 0; var covered = 0; var verbatim = 0
        for (s in other) {
            if (s in trainSegments) verbatim++
            val b = s.toByteArray(Charsets.UTF_8)
            val mark = BooleanArray(b.size)
            for (i in 0..b.size - window) if (String(b, i, window, Charsets.ISO_8859_1) in seen) for (k in i until i + window) mark[k] = true
            bytes += b.size; covered += mark.count { it }
        }
        return Overlap(other.size, verbatim, bytes, covered)
    }

    /**
     * Cross-fitted count-model probabilities for training (amendment to section 8f, before any NFC2 model run): KN-7 scored
     * on its own training text is inflated, because every 7-gram counts itself, so the mixture loss would teach the network
     * least exactly where KN-7 is weak on new text. Each document of [trainDocs] (in the given order; fold = index mod
     * [folds]) is scored by a KN-7 built from the other folds only. Returns one array per segment, in the order of
     * NfCorpus.segments(trainDocs, …).
     */
    fun crossFitted(trainDocs: List<NfCorpus.Doc>, folds: Int = 5, order: Int = 7): List<DoubleArray> {
        require(folds >= 2 && trainDocs.size >= folds)
        val fold = trainDocs.indices.associate { trainDocs[it].path to it % folds }
        val out = arrayOfNulls<List<DoubleArray>>(trainDocs.size)
        for (f in 0 until folds) {
            val kn = KneserNeyBytes(order, trainDocs.filter { fold.getValue(it.path) != f }.flatMap { it.segments })
            for ((i, d) in trainDocs.withIndex()) if (i % folds == f) out[i] = d.segments.map { kn.detail(it).prob }
        }
        return out.flatMap { it!! }
    }
}
