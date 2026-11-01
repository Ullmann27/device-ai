package at.heinz.deviceai.learning.span

import org.junit.Assert.*
import org.junit.Test

class RoleAwareShadowTest {
    private fun snapshot(text: String) = LocationEvidenceSnapshot.freeze(listOf(
        EvidenceBoundNaturalLocationProbe.Evidence("test", 1, EvidenceBoundNaturalLocationProbe.digest(text), text)))

    @Test fun orderedAdapterKeepsUnknownEntityRelationObjectAndExactPositions() {
        val a = snapshot("Ylva ist in Perg. Zeno ist in Berg.")
        val b = snapshot("Ylva ist in Berg. Zeno ist in Perg.")
        assertNull(EntityRelationLocationShadowAdapter.encode("Wo ist Ylva?", a))
        val left = EntityRelationLocationShadowAdapter.encode("Wo ist Ylva?", a, enabled=true)!!
        val right = EntityRelationLocationShadowAdapter.encode("Wo ist Ylva?", b, enabled=true)!!
        assertEquals("Ylva", left.entity)
        assertEquals("ist", left.relation)
        assertEquals("Perg", left.location.text)
        assertEquals("Berg", right.location.text)
        assertNotEquals(left.orderedSha256, right.orderedSha256)
        assertTrue(left.entityStart < left.relationStart && left.relationStart < left.location.start)
        assertEquals("Ylva", a.sources.single().text.substring(left.entityStart, left.entityEndExclusive))
        assertNull(EntityRelationLocationShadowAdapter.encode("Wo ist Ylva?", snapshot("Ylva wohnt in Perg."), enabled=true))
    }

    @Test fun truncatedRankerAnswerFallsBackToIndependentlyVerifiedCompleteRuleSpan() {
        val s = snapshot("Ylva ist in Bad Ischl.")
        val found = EvidenceBoundNaturalLocationProbe.answer("Wo ist Ylva?", s.sources)
            as EvidenceBoundNaturalLocationProbe.Decision.Found
        val choice = LocationSpecialistShadow.choose("Wo ist Ylva?", s,
            found.copy(text="Bad", endExclusive=found.start+3))
        assertEquals(LocationSpecialistShadow.Route.RULE_VERIFIED, choice.route)
        assertEquals("Bad Ischl", (choice.decision as EvidenceBoundNaturalLocationProbe.Decision.Found).text)
        assertEquals(LocationSpecialistShadow.Route.CONSENSUS,
            LocationSpecialistShadow.choose("Wo ist Ylva?", s, found).route)
    }

    @Test fun neitherSpecialistOverridesMissingConflictingOrRevisedEvidence() {
        val s = snapshot("Ylva ist in Graz.")
        val old = EvidenceBoundNaturalLocationProbe.answer("Wo ist Ylva?",s.sources)
            as EvidenceBoundNaturalLocationProbe.Decision.Found
        for (text in listOf("Zeno ist in Graz.","Ylva ist in Graz. Sie ist in Wien.","Ylva ist in Wien.")) {
            val changed = snapshot(text)
            val choice = LocationSpecialistShadow.choose("Wo ist Ylva?",changed,old)
            if (text == "Ylva ist in Wien.") {
                assertEquals("Wien",(choice.decision as EvidenceBoundNaturalLocationProbe.Decision.Found).text)
                assertEquals(LocationSpecialistShadow.Route.RULE_VERIFIED,choice.route)
            } else assertTrue(choice.decision is EvidenceBoundNaturalLocationProbe.Decision.Abstained)
        }
    }

    @Test fun revisionOnlyChangeAndSecondSourceKeepExactLocalProvenance() {
        val first=EvidenceBoundNaturalLocationProbe.Evidence("a",1,
            EvidenceBoundNaturalLocationProbe.digest("Zeno ist in Berg."),"Zeno ist in Berg.")
        val second=EvidenceBoundNaturalLocationProbe.Evidence("b",1,
            EvidenceBoundNaturalLocationProbe.digest("Ylva ist in Perg."),"Ylva ist in Perg.")
        val original=LocationEvidenceSnapshot.freeze(listOf(first,second))
        val old=EvidenceBoundNaturalLocationProbe.answer("Wo ist Ylva?",original.sources)
            as EvidenceBoundNaturalLocationProbe.Decision.Found
        val revised=LocationEvidenceSnapshot.freeze(listOf(first,second.copy(revision=2)))
        val encoding=EntityRelationLocationShadowAdapter.encode("Wo ist Ylva?",revised,enabled=true)!!
        assertEquals("b",encoding.location.sourceId)
        assertEquals(0,encoding.entityStart)
        assertEquals("Ylva",second.text.substring(encoding.entityStart,encoding.entityEndExclusive))
        val choice=LocationSpecialistShadow.choose("Wo ist Ylva?",revised,old)
        assertEquals(LocationSpecialistShadow.Route.RULE_VERIFIED,choice.route)
        assertEquals(2,(choice.decision as EvidenceBoundNaturalLocationProbe.Decision.Found).revision)
    }
}
