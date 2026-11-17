package at.heinz.deviceai.learning

import java.io.File

/** Opt-in JVM research entry; fixed authored texts only, no app memories or chat hook.
 * Run with the compiled learning classes and Kotlin stdlib on the Java classpath.
 */
object ContextDecoderResearchMain {
    @JvmStatic fun main(args:Array<String>) {
        require(args.size==1) { "Usage: ContextDecoderResearchMain <experimental-output-directory>" }
        val directory=File(args[0]);check(directory.exists()||directory.mkdirs())
        val file=File(directory,"context-decoder.cdx")
        val training=DecoderCorpus(listOf("hallo welt","hallo heinz","hallo dan","lernen macht freude","lernen braucht zeit"),DecoderSplit.TRAIN)
        val audit=DecoderCorpus(listOf("hallo lerner","lernen macht sinn"),DecoderSplit.AUDIT)
        val baseline=if(file.exists())TinyContextDecoder.restoreFrom(file) else TinyContextDecoder(91)
        if(!file.exists())baseline.saveTo(file)
        val candidate=TinyContextDecoder.restore(baseline.save())
        val before=candidate.evaluate(audit)
        val report=candidate.train(training,steps=6000,maxMillis=5000)
        val after=candidate.evaluate(audit)
        val accepted=!report.interrupted && report.lossAfter<report.lossBefore && after<=before
        if(accepted)candidate.saveTo(file)
        val active=TinyContextDecoder.restoreFrom(file)
        val hash=java.security.MessageDigest.getInstance("SHA-256").digest(active.save()).joinToString("") { "%02x".format(it) }
        val json="{\"experiment\":\"CDX1_AUTHORED_SMALL_CORPUS\",\"accepted\":"+accepted+
            ",\"steps\":"+report.steps+",\"trainingBefore\":"+report.lossBefore+",\"trainingAfter\":"+report.lossAfter+
            ",\"auditBefore\":"+before+",\"auditAfter\":"+after+",\"activeUpdates\":"+active.updates+
            ",\"parameters\":"+active.parameterCount+",\"activeHash\":\""+hash+"\"}"
        File(directory,"decoder-report.json").writeText(json+"\n")
        println(json)
        println("Experimental continuation: "+active.generate("hallo ",32).text)
    }
}
