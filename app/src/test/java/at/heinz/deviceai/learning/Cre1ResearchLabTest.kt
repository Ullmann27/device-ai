package at.heinz.deviceai.learning

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class Cre1ResearchLabTest {
    private fun trainSamples()=listOf(
        Cre1ResearchExample("Wo wohnt Lara?",listOf(
            "Lara lebt in Graz.", "Lara hat am 5. Mai Geburtstag.","Noah wohnt in Wien.")),
        Cre1ResearchExample("Wann hat Leo Geburtstag?",listOf(
            "Leo wurde am 3. Juni geboren.","Leo arbeitet als Koch.","Anna hat einen Hund."))
    )
    private fun auditSamples()=listOf(
        Cre1ResearchExample("Welche Sprache spricht Amir?",listOf(
            "Amir beherrscht Türkisch.", "Amir besitzt ein rotes Fahrrad.")),
        Cre1ResearchExample("Was isst Nina gern?",listOf(
            "Nina mag Kartoffelgratin.", "Nina fährt einen Honda Civic."))
    )
    @Test fun interruptionNeverPublishesWeightsAndRejectsOverlappingAudit() {
        val dir=Files.createTempDirectory("cre1-research").toFile()
        try{
            val lab=Cre1ResearchLab(dir)
            assertFalse(lab.status().available)
            assertThrows(IllegalArgumentException::class.java){
                lab.train(trainSamples(),trainSamples(),steps=2)
            }
            val report=lab.train(trainSamples(),auditSamples(),steps=20,cancelled={true})
            assertFalse(report.accepted)
            assertEquals(0,report.steps)
            assertTrue(report.stoppedEarly)
            assertFalse(lab.status().available)
            assertFalse(dir.resolve("cre1-research-v1.cre1").exists())
            assertTrue(dir.resolve("cre1-research-audit.tsv").readText().contains("INTERRUPTED"))
        }finally{dir.deleteRecursively()}
    }

    @Test fun evaluatedCandidateIsEitherAtomicAndValidOrRolledBack() {
        val dir=Files.createTempDirectory("cre1-research").toFile()
        try{
            val lab=Cre1ResearchLab(dir)
            val before=lab.status()
            val report=lab.train(trainSamples(),auditSamples(),steps=5)
            assertEquals(5,report.steps)
            assertFalse(report.stoppedEarly)
            assertTrue(report.beforeLoss.isFinite())
            assertTrue(report.afterLoss.isFinite())
            val after=lab.status()
            assertEquals(report.accepted,after.available)
            assertNotEquals(before.available,report.accepted && !before.available)
            if(report.accepted){
                assertNotNull(after.modelSha256)
                val raw=dir.resolve("cre1-research-v1.cre1").readBytes()
                assertEquals(5,Cre1InferenceEncoder.restore(raw).steps)
                val damaged=raw.copyOf()
                damaged[100]=(damaged[100].toInt() xor 1).toByte()
                assertThrows(IllegalArgumentException::class.java) {
                    Cre1InferenceEncoder.restore(damaged)
                }
            }
            assertEquals(1,dir.resolve("cre1-research-audit.tsv").readLines().size)
            assertFalse(dir.listFiles()!!.any { it.name.startsWith("cre1-pending-") })
        }finally{dir.deleteRecursively()}
    }
}
