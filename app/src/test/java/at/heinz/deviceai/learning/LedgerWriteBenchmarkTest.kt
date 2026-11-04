package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

/** M2.8 measurement: latency of one durable append (incl. read+validate) at 100 / 1000 / 4000 records. */
class LedgerWriteBenchmarkTest {
    private fun fact(i:Int)=LearningExperienceFactory.create(LearningExperienceType.USER_FACT,
        LearningProvenance.USER_CONFIRMED,LearningTrustClass.OWNER_CONFIRMED,LearningStatus.CONFIRMED,
        "Bestätigter Fakt Nummer $i über den Alltag in Steyr und Linz.",now=i.toLong())

    @Test fun appendLatencyAtThreeSizes() {
        for(size in listOf(100,1000,4000)) {
            val dir=Files.createTempDirectory("lex-bench").toFile()
            try {
                val store=FileLearningExperienceStore(dir)
                store.appendMissing((0 until size).map(::fact))
                val times=(0 until 40).map { k -> val t=System.nanoTime();store.append(fact(size+k));(System.nanoTime()-t)/1e6 }.drop(5).sorted()
                println("M2-METRIC write size=$size p50=%.2f p95=%.2f bytes=%d".format(times[times.size/2],
                    times[(times.size*0.95).toInt()-1],java.io.File(dir,"experiences-v2.bin").length()))
                assertEquals(size+40,FileLearningExperienceStore(dir).all().size)
            } finally { dir.deleteRecursively() }
        }
    }
}
