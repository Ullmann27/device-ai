package at.heinz.deviceai.learning
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

object QualityLex3CrashProbe {
    private fun record(i:Int)=LearningExperienceFactory.create(LearningExperienceType.USER_FACT,
        LearningProvenance.SYNTHETIC_FIXTURE,LearningTrustClass.OWNER_CONFIRMED,
        LearningStatus.CONFIRMED,"Independent crash fixture $i",now=i.toLong())
    @JvmStatic fun main(args:Array<String>) {
        if(args.isNotEmpty()) {
            val dir=File(args[0]);val previous=dir.resolve("experiences-v2.bin").readBytes()
            val before=FileLearningExperienceStore(dir).all()
            Lex3LearningExperienceStore(dir).save(before+record(args[1].toInt()),previous,beforeCommit = { Runtime.getRuntime().halt(73) })
            error("halt did not occur")
        }
        val dir=Files.createTempDirectory("quality-lex3-real-crash-").toFile()
        try {
            val store=FileLearningExperienceStore(dir)
            check(store.appendMissing((0..255).map(::record))==256)
            val legacy=dir.resolve("experiences-v2.bin").readBytes()
            fun crash(next:Int) {
                val child=ProcessBuilder(System.getProperty("java.home")+"/bin/java","-Xmx128m","-cp",
                    System.getProperty("java.class.path"),QualityLex3CrashProbe::class.java.name,dir.absolutePath,next.toString())
                    .redirectErrorStream(true).start()
                check(child.waitFor(20,TimeUnit.SECONDS)) { "child did not stop" }
                val output=child.inputStream.bufferedReader().readText()
                check(child.exitValue()==73) { "unexpected child ${child.exitValue()}: $output" }
            }
            crash(256)
            check(dir.resolve("experiences-v2.bin").readBytes().contentEquals(legacy))
            check(FileLearningExperienceStore(dir).all()==(0..255).map(::record))
            check(store.append(record(256)))
            check(dir.resolve("experiences-v3/legacy-v2.bin").readBytes().contentEquals(legacy))
            check(store.updateStatus(record(0).id,LearningStatus.REVOKED,500L))
            val committed=store.all()
            val manifest=dir.resolve("experiences-v2.bin").readBytes()
            crash(257)
            check(dir.resolve("experiences-v2.bin").readBytes().contentEquals(manifest))
            check(FileLearningExperienceStore(dir).all()==committed)
            check(store.append(record(257)))
            check(store.all().size==258 && store.stats().revoked==1)
            check(!store.append(record(0)))
            check(FileLearningExperienceStore(dir).all().first().status==LearningStatus.REVOKED)
            println("PASS: two real process halts before atomic commit preserve legacy, committed revocation and retry; 258 records")
            println("LIMIT: Linux filesystem/process test, not Android power-loss or device durability")
        } finally { dir.deleteRecursively() }
    }
}
