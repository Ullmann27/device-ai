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
 * NFC2 is the new data split for it: the project's Markdown documentation at the signed 0.3.16 base, privacy-filtered
 * and segmented like LM1/NFC1, split by SHA-256("nfc2" + path). Kept out: process logs, licence texts, the
 * experimenter's own reports, and every document that is TEST in LM1 (spent) or in NFC1 (reserved).
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
     * first [n] targets get KN-7's probability of their byte and EOS gets 0 (the count model has no end symbol).
     */
    fun targetProbabilities(kn: KneserNeyBytes, segment: String, targets: Int): DoubleArray {
        val p = kn.detail(segment).prob
        require(targets == p.size + 1) { "byte vocabulary expected: ${p.size} bytes, $targets targets" }
        return DoubleArray(targets) { if (it < p.size) p[it] else 0.0 }
    }
}
