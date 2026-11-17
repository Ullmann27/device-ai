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
    @Test fun interruptedRoundPreservesExistingCheckpointByteForByte() {
        val dir=Files.createTempDirectory("cre1-interrupt-existing").toFile()
        try {
            val original=Cre1InferenceEncoder.createResearch(73).save()
            val checkpoint=dir.resolve("cre1-research-v1.cre1")
            checkpoint.writeBytes(original)
            val lab=Cre1ResearchLab(dir)
            val report=lab.train(trainSamples(),auditSamples(),steps=20,
                cancelled={true})
            assertTrue(report.stoppedEarly)
            assertFalse(report.accepted)
            assertArrayEquals(original,checkpoint.readBytes())
            assertEquals(0,lab.status().steps)
        }finally{dir.deleteRecursively()}
    }

    @Test fun corruptedCre1ResearchCheckpointNeverSilentlyResetsToFreshWeights() {
        val dir=Files.createTempDirectory("cre1-tamper").toFile()
        try {
            val checkpoint=dir.resolve("cre1-research-v1.cre1")
            val corrupted=Cre1InferenceEncoder.createResearch(73).save()
            corrupted[256]=(corrupted[256].toInt() xor 1).toByte()
            checkpoint.writeBytes(corrupted)
            val lab=Cre1ResearchLab(dir)
            assertThrows(IllegalArgumentException::class.java) { lab.status() }
            assertThrows(IllegalArgumentException::class.java) {
                lab.train(trainSamples(),auditSamples(),steps=2)
            }
            assertArrayEquals(corrupted,checkpoint.readBytes())
        }finally{dir.deleteRecursively()}
    }

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

    @Test fun continuumLearningCoreConnectsCre1WithoutModifyingProductiveSwb1() {
        val folder=Files.createTempDirectory("cre1-core").toFile()
        try {
            val core=ContinuumLearningCore(folder)
            val baseline=core.modelHash()
            assertFalse(core.cre1ResearchStatus().available)
            val result=core.trainExperimentalCre1(
                trainSamples(),auditSamples(),steps=3,cancelled={false})
            assertEquals(3,result.steps)
            assertEquals(result.accepted,core.cre1ResearchStatus().available)
            assertEquals(baseline,core.modelHash())
            assertEquals(1,folder.resolve(
                "learning-core-v2/cre1-research/cre1-research-audit.tsv"
            ).readLines().size)
        }finally{folder.deleteRecursively()}
    }

    @Test fun evaluatedCandidateIsEitherAtomicAndValidOrRolledBack() {
        val dir=Files.createTempDirectory("cre1-research").toFile()
        try{
            val lab=Cre1ResearchLab(dir)
            val before=lab.status()
            val report=lab.train(trainSamples(),auditSamples(),steps=5)
            assertEquals(5,report.steps)
            assertFalse(report.stoppedEarly)
            assertTrue(report.trainingBeforeLoss.isFinite())
            assertTrue(report.trainingAfterLoss.isFinite())
            assertTrue(report.beforeLoss.isFinite())
            assertTrue(report.afterLoss.isFinite())
            if(report.accepted) {
                assertTrue(report.trainingAfterLoss < report.trainingBeforeLoss)
                assertTrue(report.afterLoss < report.beforeLoss)
            }
            val after=lab.status()
            assertEquals(report.accepted,after.available)
            assertFalse(before.available)
            assertEquals(report.accepted,after.available)
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
