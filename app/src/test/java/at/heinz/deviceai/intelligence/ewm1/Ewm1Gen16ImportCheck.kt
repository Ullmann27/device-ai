package at.heinz.deviceai.intelligence.ewm1

import at.heinz.deviceai.learning.ContinuumLearningCore
import at.heinz.deviceai.learning.ContinuumMemoryDialogue
import at.heinz.deviceai.learning.TrainableSubwordEncoder
import java.io.File
import java.nio.file.Files
import java.util.Base64
import org.json.JSONObject

/**
 * Owner-side dry run for the Generation 16 backups, reproducible on any JVM:
 *   java … Ewm1Gen16ImportCheck <Continuum_Gen16_SWB1.bin> <Continuum_Gen16_Quellen_556.zip> <new-report.json>
 * Both files are only read. Every import happens in a fresh temporary directory through the UNCHANGED production
 * code (ContinuumKnowledgeArchive.importZip, ContinuumLearningCore.importSnapshot) and is deleted afterwards.
 * The report holds hashes and aggregate counts only: no source text, no title, no weight.
 */
object Ewm1Gen16ImportCheck {
    const val SWB1_SHA256 = "5ba8ba7e22efc19a099148d224ab000dcd21953bb74b5d6504f49621fe56b2b6"

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 3) { "usage: <swb1.bin> <archive.zip> <new-report.json>" }
        val out = File(args[2]); require(!out.exists()) { "Do not overwrite a report" }
        val swb1 = File(args[0]).readBytes(); val zip = File(args[1])
        val report = JSONObject()

        val swbSha = ContinuumMemoryDialogue.hash(swb1)
        val model = TrainableSubwordEncoder.restore(swb1)
        val frozen = TrainableSubwordEncoder.restore(Base64.getDecoder().decode(
            Ewm1Gen16ImportCheck::class.java.getResourceAsStream("/continuum-neural-swb1.base64")!!.bufferedReader().readText().trim()))
        fun eval(m: TrainableSubwordEncoder) = JSONObject().apply {
            val ih = m.evaluateIntent(ContinuumLearningCore.INTENT_HOLDOUT); val sh = m.evaluateSemantic(ContinuumLearningCore.SEMANTIC_HOLDOUT)
            val it = m.evaluateIntent(ContinuumLearningCore.INTENT_BOOTSTRAP); val st = m.evaluateSemantic(ContinuumLearningCore.SEMANTIC_BOOTSTRAP)
            put("intentHoldout", "${ih.correct}/${ih.total}").put("intentHoldoutLoss", ih.loss)
            put("semanticHoldout", "${sh.correct}/${sh.total}").put("semanticHoldoutLoss", sh.loss)
            put("intentTrain", "${it.correct}/${it.total}").put("semanticTrain", "${st.correct}/${st.total}")
        }
        val gate = Files.createTempDirectory("gen16-core-").toFile().let { dir ->
            try { ContinuumLearningCore(dir).importSnapshot(swb1) } finally { dir.deleteRecursively() }
        }
        report.put("swb1", JSONObject().put("bytes", swb1.size).put("sha256", swbSha).put("expectedSha256Match", swbSha == SWB1_SHA256)
            .put("parameters", model.parameterCount).put("updates", model.updates)
            .put("gen16", eval(model)).put("frozenPublic", eval(frozen)).put("fresh", eval(TrainableSubwordEncoder()))
            .put("productionGateOnFreshCore", JSONObject().put("accepted", gate.accepted).put("reason", gate.reason)))

        val zipSha = ContinuumMemoryDialogue.hash(zip.readBytes())
        val records = Ewm1SourceExperiment.load(zip)          // production importZip, validated, temp dir
        val idx = Ewm1SourceIndex(records)
        val classes = idx.sources.groupingBy { it.sourceClass.name }.eachCount()
        report.put("archive", JSONObject().put("bytes", zip.length()).put("sha256", zipSha)
            .put("expectedSha256Match", zipSha == Ewm1SourceExperiment.GEN16_ARCHIVE_SHA256)
            .put("recordsValidatedByProductionImport", records.size)
            .put("kinds", JSONObject(records.groupingBy { it.kind.name }.eachCount() as Map<*, *>))
            .put("classes", JSONObject(classes as Map<*, *>))
            .put("identicalTextGroups", records.groupBy { it.sha256 }.count { it.value.size > 1 })
            .put("truncatedExcerpts", idx.sources.count { it.truncated })
            .put("withSourceDate", records.count { it.sourceDate != null })
            .put("licences", JSONObject(records.groupingBy { it.attribution }.eachCount() as Map<*, *>))
            .put("collectedFromMs", records.minOf { it.collectedAt }).put("collectedToMs", records.maxOf { it.collectedAt }))
        check(out.createNewFile()); out.writeText(report.toString(2) + "\n")
        println(report.toString())
    }
}
