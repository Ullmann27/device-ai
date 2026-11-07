package at.heinz.deviceai.learning

import java.io.File

object MemoryBoundaryProbe {
    @JvmStatic fun main(args:Array<String>) {
        val dir=File(args[0]).apply { mkdirs() }
        fun record(index:Int,chars:Int)=LearningExperienceFactory.create(
            LearningExperienceType.USER_FACT,LearningProvenance.SYNTHETIC_FIXTURE,
            LearningTrustClass.OWNER_CONFIRMED,LearningStatus.CONFIRMED,
            index.toString().padStart(4,'0')+"界".repeat(chars),
            "界".repeat(160),"界".repeat(500),index.toLong())
        val count=4096
        val baseline=record(0,1900)
        val oneBytes=LearningExperienceCodec.encode(listOf(baseline)).size-40
        val baselineBytes=count.toLong()*oneBytes+count/32*40
        val target=Lex3LearningExperienceStore.MAX_TOTAL_BYTES-1024
        var extraChars=((target-baselineBytes)/3).toInt()
        val records=(0 until count).map { index ->
            val extra=minOf(extraChars,144)
            extraChars-=extra
            record(index,1900+extra)
        }
        check(extraChars==0)
        val segmentBytes=records.chunked(32).sumOf { LearningExperienceCodec.encode(it).size.toLong() }
        println("LEX3 bytes=$segmentBytes limit=${Lex3LearningExperienceStore.MAX_TOTAL_BYTES} records=${records.size}")
        val store=FileLearningExperienceStore(dir)
        println("admitted=${store.appendMissing(records)} reopened=${FileLearningExperienceStore(dir).all().size}")
        val exportResult=runCatching { PersonalLearningTransfer.export("at.heinz.deviceai.continuumlocal",store) }
        println("export=${exportResult.fold({ "OK bytes=${it.size}" },{ "FAIL ${it.javaClass.simpleName}: ${it.message}" })}")
        check(exportResult.isFailure) { "Expected boundary mismatch did not reproduce" }
    }
}
