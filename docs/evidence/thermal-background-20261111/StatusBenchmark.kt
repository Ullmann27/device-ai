package at.heinz.deviceai.learning
import java.io.File
import java.nio.file.Files
import java.lang.management.ManagementFactory
import java.util.IdentityHashMap
fun main() {
    val directory=Files.createTempDirectory("status-benchmark").toFile()
    try {
        val checkpoint=File(directory,"learning-core-v2/subword-v1.bin")
        checkpoint.parentFile.mkdirs()
        checkpoint.writeBytes(TrainableSubwordEncoder(101L).save())
        repeat(50) { ContinuumLearningCore(directory).status() }
        val clock=ManagementFactory.getThreadMXBean()
        val identities=IdentityHashMap<SubwordEvaluation,Boolean>()
        var checksum=0.0
        val times=List(5) {
            val cpu=clock.currentThreadCpuTime
            val wall=System.nanoTime()
            repeat(200) {
                val status=ContinuumLearningCore(directory).status()
                checksum+=status.intentHoldout.loss+status.semanticHoldout.loss
                identities[status.intentHoldout]=true
            }
            "{\"cpu_ns\":"+(clock.currentThreadCpuTime-cpu)+",\"wall_ns\":"+(System.nanoTime()-wall)+"}"
        }
        println("{\"status_reads\":1000,\"unique_intent_measurements\":"+identities.size+",\"checksum\":"+checksum+",\"batches\":["+times.joinToString(",")+"]}")
    } finally { directory.deleteRecursively() }
}
