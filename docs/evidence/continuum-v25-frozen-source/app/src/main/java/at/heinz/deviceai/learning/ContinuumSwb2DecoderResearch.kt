package at.heinz.deviceai.learning

import java.io.File
import java.security.MessageDigest

/** Research-only opt-in SWB2 encoder -> CDX1 causal decoder bridge.
 * A separate checkpoint prevents accidental mixing with the SWB1 model;
 * nothing is wired to production chat, memory writes, or ToolGate.
 */
class ContinuumSwb2DecoderResearch(private val folder:File) {
    private val checkpoint=File(folder,"swb2-research-context.cdx")
    data class Status(val available:Boolean,val updates:Int,val checksum:String?)
    @Synchronized fun status():Status {
        val m=loadOrNull()?:return Status(false,0,null)
        return Status(true,m.updates,sha(m.save()))
    }
    @Synchronized fun trainExperiment(
        train:DecoderCorpus,audit:DecoderCorpus,encoderSnapshot:ByteArray,
        steps:Int=500,cancelled:()->Boolean={false}
    ):DecoderExperimentResult {
        require(train.split==DecoderSplit.TRAIN && audit.split==DecoderSplit.AUDIT)
        require(train.texts.none { it in audit.texts }) { "Audit overlap" }
        val encoder=Swb2ExperimentalEncoder.restore(encoderSnapshot)
        val condition=encoder.encode("research context")
        val baseline=loadOrNull()?:TinyContextDecoder()
        val candidate=TinyContextDecoder.restore(baseline.save())
        val before=baseline.evaluate(audit,condition)
        val report=candidate.train(train,steps=steps,conditioning=condition,
            maxMillis=5000,cancelled=cancelled)
        val after=candidate.evaluate(audit,condition)
        val accepted=!report.interrupted && !cancelled() &&
            report.lossAfter<report.lossBefore && after<=before
        if(accepted)candidate.saveTo(checkpoint)
        return DecoderExperimentResult(accepted,report.steps,report.lossBefore,
            report.lossAfter,before,after,sha((if(accepted)candidate else baseline).save()))
    }
    @Synchronized fun experimentalContinuation(
        prompt:String,encoderSnapshot:ByteArray,maxBytes:Int=48
    ):DecoderGeneration? {
        require(maxBytes in 1..48)
        val decoder=loadOrNull()?:return null
        val condition=Swb2ExperimentalEncoder.restore(encoderSnapshot).encode(prompt.take(320))
        return decoder.generate(prompt,maxBytes,condition)
    }
    private fun loadOrNull():TinyContextDecoder? {
        if(!checkpoint.exists())return null
        return runCatching{TinyContextDecoder.restoreFrom(checkpoint)}.getOrNull()
    }
    private fun sha(bytes:ByteArray):String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString(""){"%02x".format(it)}
}
