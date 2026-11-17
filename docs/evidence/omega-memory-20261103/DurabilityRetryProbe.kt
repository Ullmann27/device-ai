package at.heinz.deviceai.learning

import java.io.File
import java.io.IOException

/** Linux syscall-fault regression companion; every record is synthetic, in a disposable directory. */
object DurabilityRetryProbe {
    private fun record(index:Int)=LearningExperienceFactory.create(LearningExperienceType.USER_FACT,
        LearningProvenance.SYNTHETIC_FIXTURE,LearningTrustClass.OWNER_CONFIRMED,
        LearningStatus.CONFIRMED,"Durability retry fixture $index",now=index.toLong())
    @JvmStatic fun main(args:Array<String>) {
        val store=FileLearningExperienceStore(File(args[0]))
        if(args[1]=="init") { check(store.appendMissing((0..256).map(::record))==257);return }
        val first=runCatching { store.updateStatus(record(0).id,LearningStatus.REVOKED,1000) }
        check(first.exceptionOrNull() is IOException) { "Expected injected fsync IOException" }
        println("first=IOException")
        val retry=when(args[1]) {
            "status" -> store.updateStatus(record(0).id,LearningStatus.REVOKED,1000).also { check(it) }
            "append" -> store.append(record(0)).also { check(!it) }
            "batch" -> store.appendMissing(listOf(record(0))).also { check(it==0) }
            "matching" -> store.updateMatching(LearningExperienceType.USER_FACT,record(0).content,
                LearningStatus.REVOKED,1000).also { check(it==0) }
            else -> error("Unknown probe mode")
        }
        check(store.stats().revoked==1 && store.all().size==257)
        println("mode=${args[1]} retry=$retry revoked=1 records=257")
    }
}
