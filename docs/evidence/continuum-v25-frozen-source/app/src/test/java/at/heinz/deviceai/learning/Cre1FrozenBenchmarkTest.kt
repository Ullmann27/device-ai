package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test

class Cre1FrozenBenchmarkTest {
    private fun fact(text:String)=LearningExperienceFactory.create(
        LearningExperienceType.USER_FACT,LearningProvenance.USER_CONFIRMED,
        LearningTrustClass.OWNER_CONFIRMED,LearningStatus.CONFIRMED,
        text,now=1L
    )

    @Test fun realEncodersProvideReproducibleReadOnlyEvidenceAudit() {
        // Authored SYNTHETIC examples exist in memory only; no owner ledger.
        val leo=fact("Leo wohnt in Graz.")
        val lena=fact("Lena wohnt in Wien.")
        val pool=listOf(leo,lena)
        val input=listOf(
            Cre1FrozenQuestion("q1","Wo wohnt Leo?",leo.id,pool),
            Cre1FrozenQuestion("q2","Wo wohnt Lena?",lena.id,pool),
            Cre1FrozenQuestion("q3","Welche Farbe hat ein Quantenfahrrad?",null,pool)
        )
        val swb=TrainableSubwordEncoder().save()
        val cre=Cre1InferenceEncoder.createResearch(73).save()
        val one=Cre1FrozenBenchmark.run(swb,cre,input,.35)
        val two=Cre1FrozenBenchmark.run(swb,cre,input.reversed(),.35)
        assertEquals(one.manifest.auditSha256,two.manifest.auditSha256)
        assertEquals(one.manifest.swb1Correct,two.manifest.swb1Correct)
        assertEquals(one.manifest.hybridCorrect,two.manifest.hybridCorrect)
        assertEquals(3,one.manifest.sampleCount)
        assertFalse("Three untrained toy cases do not authorize production",one.qualified)
        assertArrayEquals(swb,TrainableSubwordEncoder.restore(swb).save())
        assertArrayEquals(cre,Cre1InferenceEncoder.restore(cre).save())
    }

    @Test fun revokedFactsAreNotResurrectedIntoAuditCandidatePool() {
        val fact=fact("Rudi hat ein schwarzes Fahrrad.").copy(status=LearningStatus.REVOKED)
        val swb=TrainableSubwordEncoder().save()
        val cre=Cre1InferenceEncoder.createResearch(73).save()
        val result=Cre1FrozenBenchmark.run(swb,cre,listOf(
            Cre1FrozenQuestion("r1","Was hat Rudi?",fact.id,listOf(fact))
        ),.35)
        assertEquals(0,result.manifest.swb1Correct)
        assertEquals(0,result.manifest.hybridCorrect)
        assertFalse(result.qualified)
    }

    @Test fun cancellationAndCorruptWeightsCannotProducePartialPromotion() {
        val memory=fact("Lea wohnt in Linz.")
        val case=Cre1FrozenQuestion("case","Wo wohnt Lea?",memory.id,listOf(memory))
        val swb=TrainableSubwordEncoder().save()
        val cre=Cre1InferenceEncoder.createResearch(73).save()
        assertThrows(IllegalStateException::class.java) {
            Cre1FrozenBenchmark.run(swb,cre,listOf(case),.35,cancelled={true})
        }
        assertThrows(IllegalArgumentException::class.java) {
            Cre1FrozenBenchmark.run(swb,byteArrayOf(1,2,3),listOf(case),.35)
        }
    }
}
