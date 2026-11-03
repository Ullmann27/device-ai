package at.heinz.deviceai.learning

import java.io.File

object MemorySyncProbe {
    private fun record(index:Int)=LearningExperienceFactory.create(
        LearningExperienceType.USER_FACT,LearningProvenance.SYNTHETIC_FIXTURE,
        LearningTrustClass.OWNER_CONFIRMED,LearningStatus.CONFIRMED,"Sync fixture $index",now=index.toLong())
    @JvmStatic fun main(args:Array<String>) {
        val store=FileLearningExperienceStore(File(args[0]))
        if(args[1]=="init") {
            println("init=${store.appendMissing((0..256).map(::record))}")
            return
        }
        val first=runCatching { store.updateStatus(record(0).id,LearningStatus.REVOKED,1000) }
        println("first=${first.fold({ "OK $it" },{ "FAIL ${it.javaClass.simpleName}: ${it.message}" })}")
        println("retry=${store.updateStatus(record(0).id,LearningStatus.REVOKED,1000)}")
        println("revoked=${store.stats().revoked}")
        check(first.isFailure) { "Fault did not occur" }
    }
}
