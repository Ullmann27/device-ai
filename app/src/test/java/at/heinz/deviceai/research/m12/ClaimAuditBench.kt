package at.heinz.deviceai.research.m12

import at.heinz.deviceai.cognitive.LocalGroundedRecall
import at.heinz.deviceai.cognitive.LocalModelClaimAudit
import at.heinz.deviceai.cognitive.TraceStage
import java.io.File

/**
 * M12 · measures an audit on the labelled cases: detection per error category, false alarms on faithful answers,
 * and four answer policies (answer as is · flag · targeted deep check · clarify on conflicting evidence).
 * Run: java -cp <harness classpath> at.heinz.deviceai.research.m12.ClaimAuditBench <dev|holdout> [out.json]
 */
object ClaimAuditBench {
    private fun recall(e: String) = LocalGroundedRecall(e, "Besitzererinnerung")
    val audits: Map<String, (String, LocalGroundedRecall) -> TraceStage> = linkedMapOf(
        "v1Frozen" to { a, r -> ClaimAuditV1Frozen.stage(a, r) },
        "current" to { a, r -> LocalModelClaimAudit.stage(a, r) })

    fun evaluate(split: String): String {
        val cases = ClaimAuditCases.cases(split)
        val sb = StringBuilder("{\"split\":\"$split\",\"seal\":\"${ClaimAuditCases.seal(split)}\",\"cases\":${cases.size},\"audits\":{")
        audits.entries.forEachIndexed { ai, (name, audit) ->
            repeat(200) { cases.forEach { audit(it.answer, recall(it.evidence)) } }      // JIT warm-up
            val t0 = System.nanoTime()
            val stages = cases.associate { it.id to audit(it.answer, recall(it.evidence)) }
            val micros = (System.nanoTime() - t0) / 1000.0 / cases.size
            fun flagged(c: ClaimCase) = stages[c.id] == TraceStage.CLAIM_AUDIT_UNSUPPORTED || stages[c.id] == TraceStage.CLAIM_AUDIT_CONFLICT
            val errors = cases.filter { it.isError }; val correct = cases.filter { !it.isError }
            val caught = errors.count(::flagged); val falseAlarms = correct.count(::flagged)
            val perCat = (ClaimAuditCases.ERROR_CATEGORIES + ClaimAuditCases.CORRECT_CATEGORIES).joinToString(",") { cat ->
                val c = cases.filter { it.category == cat }; "\"$cat\":\"${c.count(::flagged)}/${c.size}\"" }
            val conflicts = cases.count { stages[it.id] == TraceStage.CLAIM_AUDIT_CONFLICT }
            if (ai > 0) sb.append(',')
            sb.append("\"$name\":{\"errorsCaught\":$caught,\"errors\":${errors.size},\"falseAlarms\":$falseAlarms,\"correct\":${correct.size},")
            sb.append("\"decisionsRight\":${caught + correct.size - falseAlarms},\"conflictStages\":$conflicts,")
            sb.append("\"microsPerCase\":%.1f,\"flaggedPerCategory\":{%s}}".format(micros, perCat))
        }
        return sb.append("}}").toString()
    }

    @JvmStatic fun main(args: Array<String>) {
        val json = evaluate(args.firstOrNull() ?: "dev")
        println(json)
        args.getOrNull(1)?.let { File(it).writeText(json + "\n") }
    }
}
