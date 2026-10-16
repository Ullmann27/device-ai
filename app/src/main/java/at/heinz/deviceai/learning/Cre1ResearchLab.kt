package at.heinz.deviceai.learning

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.math.exp
import kotlin.math.ln

/** Isolated CRE1 experimental examples; candidates[0] must be the grounded
 * positive fact. This is never an owner-memory assertion or a device action. */
data class Cre1ResearchExample(val query:String, val candidates:List<String>) {
    init {
        require(query.length in 1..512)
        require(candidates.size in 2..12 && candidates.distinct().size==candidates.size)
        require(candidates.all { it.length in 1..512 })
    }
}

data class Cre1ResearchTrainingReport(
    val accepted:Boolean,
    val steps:Int,
    val trainingBeforeLoss:Double,
    val trainingAfterLoss:Double,
    val beforeLoss:Double,
    val afterLoss:Double,
    val stoppedEarly:Boolean,
    val modelSha256:String?
)
data class Cre1ResearchStatus(val available:Boolean,val steps:Int,val modelSha256:String?)

/**
 * On-device CRE1 training in a separate, checksum-verifiable research
 * namespace. The productive SWB1 model and all owner facts remain untouched.
 *
 * Audit queries AND memory candidates must be disjoint from training; the
 * candidate starts from the last accepted checkpoint and is discarded unless
 * independent audit loss strictly improves. Interrupted rounds NEVER commit.
 */
class Cre1ResearchLab(private val directory:File) {
    private val checkpoint=File(directory,"cre1-research-v1.cre1")

    @Synchronized fun status():Cre1ResearchStatus {
        if(!checkpoint.exists())return Cre1ResearchStatus(false,0,null)
        val bytes=checkpoint.readBytes()
        val model=Cre1InferenceEncoder.restore(bytes)
        return Cre1ResearchStatus(true,model.steps,hash(bytes))
    }

    @Synchronized fun train(
        training:List<Cre1ResearchExample>,
        audit:List<Cre1ResearchExample>,
        steps:Int=32,
        rate:Double=0.0001,
        cancelled:()->Boolean={false},
        maxMillis:Long=2000
    ):Cre1ResearchTrainingReport {
        require(training.size in 2..128 && audit.size in 2..64)
        require(steps in 1..500 && maxMillis in 50..2500)
        require(rate.isFinite() && rate>0.0 && rate<=0.001)
        val trainQueries=training.map { it.query }.toSet()
        val trainMemories=training.flatMap { it.candidates }.toSet()
        require(trainQueries.size==training.size && audit.map { it.query }.distinct().size==audit.size)
        require(audit.none { it.query in trainQueries || it.candidates.any(trainMemories::contains) }) {
            "Training/audit leakage"
        }
        val baseline=if(checkpoint.exists())
            Cre1InferenceEncoder.restore(checkpoint.readBytes())
        else Cre1InferenceEncoder.createResearch(73)
        val before=evaluate(baseline,audit)
        val trainBefore=evaluate(baseline,training)
        val candidate=Cre1InferenceEncoder.restore(baseline.save())
        val started=System.nanoTime()
        var completed=0
        fun interrupted():Boolean = cancelled() ||
            (System.nanoTime()-started)/1_000_000>=maxMillis
        while(completed<steps && !interrupted()) {
            val pair=training[completed%training.size]
            candidate.trainSgdStep(pair.query,pair.candidates,rate)
            completed++
        }
        val stopped=completed!=steps || interrupted()
        val after=evaluate(candidate,audit)
        val trainAfter=evaluate(candidate,training)
        val accept=!stopped && trainAfter+1e-9 < trainBefore &&
            after+1e-9 < before
        if(accept)commit(candidate.save())
        val report=Cre1ResearchTrainingReport(accept,completed,
            trainBefore,trainAfter,before,after,stopped,if(accept)hash(candidate.save())else
                if(checkpoint.exists())hash(checkpoint.readBytes())else null)
        appendAudit(report)
        return report
    }

    /** Ground-truth-aware loss of positive memory vs provided negatives.
     * This is evaluation-only and never called for ranking unknown user facts. */
    private fun evaluate(model:Cre1InferenceEncoder,items:List<Cre1ResearchExample>):Double {
        var sum=0.0
        for(item in items) {
            val logits=item.candidates.map { model.score(item.query,it)/0.2 }
            val max=logits.maxOrNull()!!
            val normalizer=logits.sumOf { exp(it-max) }
            sum+=ln(normalizer)+max-logits.first()
        }
        return sum/items.size
    }

    private fun commit(bytes:ByteArray) {
        directory.mkdirs()
        // The source is serialized to a same-directory temporary file.
        // Fail closed if this filesystem cannot provide an atomic replacement.
        val pending=File.createTempFile("cre1-pending-", ".tmp",directory)
        try {
            pending.writeBytes(bytes)
            Cre1InferenceEncoder.restore(pending.readBytes())
            Files.move(pending.toPath(),checkpoint.toPath(),
                StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        } finally { pending.delete() }
    }

    private fun appendAudit(report:Cre1ResearchTrainingReport) {
        directory.mkdirs()
        val log=File(directory,"cre1-research-audit.tsv")
        val fields=listOf(System.currentTimeMillis().toString(),
            if(report.accepted)"ACCEPTED" else if(report.stoppedEarly)"INTERRUPTED" else "REJECTED",
            report.steps.toString(),report.trainingBeforeLoss.toString(),
            report.trainingAfterLoss.toString(),report.beforeLoss.toString(),
            report.afterLoss.toString(),report.modelSha256?:"none").joinToString("\t")
        val recent=if(log.exists())log.readLines().takeLast(31)else emptyList()
        val data=(recent+fields).joinToString("\n",postfix="\n")
        require(data.length<8192)
        log.writeText(data)
    }
    private fun hash(bytes:ByteArray):String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
