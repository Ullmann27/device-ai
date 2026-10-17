package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test

class Cre1PromotionAuditTest {
    private val hash="a".repeat(64)

    private fun cases():List<Cre1AuditOutcome> =
        (0 until 160).map { i ->
            val correct="gold-$i"
            val distractor="other-$i"
            Cre1AuditOutcome("positive-$i",correct,setOf(correct,distractor),
                if(i<18)distractor else correct,correct)
        }+(0 until 60).map { i ->
            Cre1AuditOutcome("negative-$i",null,emptySet(),null,null)
        }

    @Test fun exactMcNemarHandlesDiscordanceAndTiesWithoutNormalApproximation() {
        assertEquals(1.0,Cre1PromotionAudit.exactTwoSidedMcNemar(0,0),0.0)
        assertEquals(1.0,Cre1PromotionAudit.exactTwoSidedMcNemar(3,2),1e-12)
        assertEquals(2.0/1024.0,Cre1PromotionAudit.exactTwoSidedMcNemar(10,0),1e-12)
        assertTrue(Cre1PromotionAudit.exactTwoSidedMcNemar(300,0)<1e-80)
    }

    @Test fun correctlyProducesBoundManifestOnlyFromPairedIndividualCases() {
        val items=cases()
        val result=Cre1PromotionAudit.evaluate(hash,40,items,.35)
        assertEquals(18,result.correctedByHybrid)
        assertEquals(0,result.damagedByHybrid)
        assertEquals(220,result.manifest.sampleCount)
        assertEquals(60,result.manifest.negativeCount)
        assertEquals(202,result.manifest.swb1Correct)
        assertEquals(220,result.manifest.hybridCorrect)
        assertEquals(18,result.manifest.baselineFalseAttributions)
        assertEquals(0,result.manifest.hybridFalseAttributions)
        assertEquals(0,result.manifest.negativeFalseAnswers)
        assertTrue(result.qualified)
        assertTrue(result.manifest.qualified(hash,40))
        assertEquals(Cre1PromotionAudit.fingerprint(items.reversed()),result.manifest.auditSha256)
        assertNotEquals(result.manifest.auditSha256,
            Cre1PromotionAudit.fingerprint(items.drop(1)))
    }

    @Test fun positiveAbsenceCannotBeHiddenByPretendNeuralAbstentionOrInvention() {
        assertThrows(IllegalArgumentException::class.java) {
            Cre1AuditOutcome("wrong",null,setOf("a"),"a",null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Cre1AuditOutcome("wrong",null,emptySet(),null,"a")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Cre1AuditOutcome("wrong",null,setOf("a"),"a","invented")
        }
        val unknown=Cre1AuditOutcome("miss","absent",setOf("a","b"),"a","b")
        assertEquals("absent",unknown.expectedId)
        val result=Cre1PromotionAudit.evaluate(hash,40,cases()+unknown,.35)
        assertFalse(result.qualified)
    }

    @Test fun aSingleFalseAttributionOrUnprovenImprovementCannotPromote() {
        val neg=Cre1AuditOutcome("negative-0",null,setOf("false-hit"),"false-hit","false-hit")
        val changed=cases().map { if(it.caseId==neg.caseId)neg else it }
        val result=Cre1PromotionAudit.evaluate(hash,40,changed,.35)
        assertEquals(1,result.manifest.negativeFalseAnswers)
        assertEquals(1,result.manifest.hybridFalseAttributions)
        assertFalse(result.qualified)
        val noImprovement=cases().map { row ->
            row.copy(cre1SelectedId=row.swb1SelectedId)
        }
        assertFalse(Cre1PromotionAudit.evaluate(hash,40,noImprovement,.35).qualified)
    }

    @Test fun rejectsDuplicateAndInvalidCaseIdsOrOutOfRangeFusion() {
        val examples=cases()
        assertThrows(IllegalArgumentException::class.java) {
            Cre1PromotionAudit.evaluate(hash,40,examples+examples[0],.35)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Cre1PromotionAudit.evaluate(hash,40,examples,Double.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Cre1PromotionAudit.exactTwoSidedMcNemar(-1,4)
        }
    }
}
