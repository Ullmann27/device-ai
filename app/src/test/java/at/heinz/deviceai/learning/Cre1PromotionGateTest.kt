package at.heinz.deviceai.learning

import java.nio.file.Files
import java.util.Properties
import org.junit.Assert.*
import org.junit.Test

class Cre1PromotionGateTest {
    private val sha="a".repeat(64)
    private val audit="b".repeat(64)
    private fun manifest(modelSha:String=sha,hybrid:Int=170,negatives:Int=0,
        p:Double=.01)=Cre1PromotionManifest(modelSha,40,audit,200,60,150,
        hybrid,0,0,negatives,p,.35)

    private fun hit(id:String,text:String,score:Double)=ContinuumMemoryHit(
        id,text,"Bestätigte Nutzererinnerung",null,score,0.0)

    @Test fun fakeTrainingOrWeakHoldoutNeverQualifies() {
        assertTrue(manifest().qualified(sha,40))
        assertFalse(manifest(modelSha="c".repeat(64)).qualified(sha,40))
        assertFalse(manifest(hybrid=151).qualified(sha,40))
        assertFalse(manifest(negatives=1).qualified(sha,40))
        assertFalse(manifest(p=.06).qualified(sha,40))
        assertFalse(manifest().qualified(sha,1))
        assertFalse(manifest().copy(auditSha256="0".repeat(64)).qualified(sha,40))
    }

    @Test fun cannotAddUnknownFactsAndCannotRerankDisagreeingNumbersOrPlaces() {
        val baseline=listOf(hit("a","Leo wohnt in Wien 2026.",.60),
            hit("b","Leo wohnt in Graz 2027.",.59))
        val scores=mapOf("a" to -1.0,"b" to 1.0)
        assertEquals(baseline,Cre1PromotionGate.rerank(
            "Wo wohnt Leo?",baseline,scores,manifest(),sha,40))
        val places=listOf(hit("a","Leo wohnt in Wien.",.60),
            hit("b","Leo wohnt in Graz.",.59))
        assertEquals(places,Cre1PromotionGate.rerank(
            "Wo wohnt Leo?",places,scores,manifest(),sha,40))
        assertEquals(places,Cre1PromotionGate.rerank("Wo wohnt Leo?",places,
            mapOf("a" to -.1,"intruder" to 1.0),manifest(),sha,40))
        assertEquals(places,Cre1PromotionGate.rerank("Wo wohnt Leo?",places,
            mapOf("a" to Double.NaN,"b" to 1.0),manifest(),sha,40))
    }

    @Test fun permitsOnlyBenchmarkedReorderingWithinAnIdenticalSafeEvidenceSet() {
        val swb1=listOf(hit("a","Leo arbeitet heute in Wien.",.60),
            hit("b","In Wien arbeitet Leo heute.",.59))
        val changed=Cre1PromotionGate.rerank("Wo arbeitet Leo?",swb1,
            mapOf("a" to -1.0,"b" to 1.0),manifest(),sha,40)
        assertEquals(listOf("b","a"),changed.map { it.id })
        assertEquals(swb1.toSet(),changed.toSet())
        assertEquals(swb1,Cre1PromotionGate.rerank("Wo arbeitet Leo?",swb1,
            mapOf("a" to -1.0,"b" to 1.0),manifest(hybrid=152),sha,40))
    }

    @Test fun absentMalformedAndHashMismatchedManifestsFailClosed() {
        val dir=Files.createTempDirectory("cre1-promotion").toFile()
        try {
            val file=dir.resolve("cre1-promotion.properties")
            assertNull(Cre1PromotionGate.readQualified(file,sha,40))
            file.writeText("schema=1\nmodelSha256=bogus\n")
            assertNull(Cre1PromotionGate.readQualified(file,sha,40))
            val data=manifest()
            val props=Properties()
            mapOf(
                "schema" to "1","modelSha256" to data.modelSha256,
                "modelSteps" to data.modelSteps.toString(),
                "auditSha256" to data.auditSha256,
                "sampleCount" to data.sampleCount.toString(),
                "negativeCount" to data.negativeCount.toString(),
                "swb1Correct" to data.swb1Correct.toString(),
                "hybridCorrect" to data.hybridCorrect.toString(),
                "baselineFalseAttributions" to data.baselineFalseAttributions.toString(),
                "hybridFalseAttributions" to data.hybridFalseAttributions.toString(),
                "negativeFalseAnswers" to data.negativeFalseAnswers.toString(),
                "pairedPValue" to data.pairedPValue.toString(),
                "fusionWeight" to data.fusionWeight.toString()
            ).forEach { (k,v)->props.setProperty(k,v) }
            file.outputStream().use { props.store(it,"test fixtures only") }
            assertNotNull(Cre1PromotionGate.readQualified(file,sha,40))
            assertNull(Cre1PromotionGate.readQualified(file,"c".repeat(64),40))
            assertNull(Cre1PromotionGate.readQualified(file,sha,41))
            file.appendText("unexpectedField=1\n")
            assertNull(Cre1PromotionGate.readQualified(file,sha,40))
        } finally { dir.deleteRecursively() }
    }

    @Test fun untrustedRerankerCannotForgeOrPersistNewMemory() {
        val dir=Files.createTempDirectory("cre1-reorder-contract").toFile()
        try {
            val core=ContinuumLearningCore(dir)
            core.confirmOwnerFact("Leo arbeitet heute in Wien.")
            val old=core.replyFromMemory("Wo arbeitet Leo?")
            val modelHash=core.modelHash()
            val forged=core.replyWithEvidence("Wo arbeitet Leo?") { _,hits ->
                hits+hit("forged","Leo wohnt in Graz.",.99)
            }.first
            assertEquals(old.memoryIds,forged.memoryIds)
            assertFalse(forged.answer.contains("Graz"))
            assertEquals(modelHash,core.modelHash())
            assertTrue(core.activeOwnerFacts().none { it.content.contains("Graz") })
        } finally { dir.deleteRecursively() }
    }
}
