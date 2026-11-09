package at.heinz.deviceai.learning.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityRoleResolutionEngineTest {
    private val owner=EntityRoleResolutionEngine.Entity(
        "owner","OWNER","Heinz",EntityRoleResolutionEngine.Gender.MASCULINE)
    private val assistant=EntityRoleResolutionEngine.Entity(
        "assistant","ASSISTANT","Continuum",EntityRoleResolutionEngine.Gender.NEUTER)
    private val alina=EntityRoleResolutionEngine.Entity(
        "alina","OWNER.CHILD[0]","Alina",EntityRoleResolutionEngine.Gender.FEMININE)
    private val zoey=EntityRoleResolutionEngine.Entity(
        "zoey","OWNER.CHILD[1]","Zoey",EntityRoleResolutionEngine.Gender.FEMININE)
    private val entities=listOf(owner,assistant,alina,zoey).associateBy { it.id }

    @Test fun feminineCenterResolvesHerSisterOnlyThroughEvidenceEdge() {
        val frame=EntityRoleResolutionEngine.advanceFrame(
            "alina",listOf("owner" to EntityRoleResolutionEngine.Role.POSSESSOR))
        val edges=listOf(EntityRoleResolutionEngine.RelationEdge(
            "alina","SISTER","zoey",setOf("memory-sister-edge")))
        val result=EntityRoleResolutionEngine.resolvePossessiveRelation(
            "ihre","Schwester",frame,"owner","assistant",entities,edges)
        assertTrue(result is EntityRoleResolutionEngine.Resolution.Resolved)
        result as EntityRoleResolutionEngine.Resolution.Resolved
        assertEquals("zoey",result.entity.id)
        assertEquals(setOf("memory-sister-edge"),result.sourceIds)
    }

    @Test fun germanRoleNameWithoutEdgeNeverInventsRelation() {
        val frame = EntityRoleResolutionEngine.advanceFrame("alina", emptyList())
        val missing = EntityRoleResolutionEngine.resolvePossessiveRelation(
            "ihre", "Schwester", frame, "owner", "assistant", entities,
            listOf(EntityRoleResolutionEngine.RelationEdge(
                "alina", "CHILD", "zoey", setOf("unrelated-edge")))
        )
        assertTrue(missing is EntityRoleResolutionEngine.Resolution.NoEvidence)
    }

    @Test fun daughterLabelCannotResolveMasculineChildWithoutGenderEvidence() {
        val son = EntityRoleResolutionEngine.Entity(
            "son", "OWNER.CHILD[3]", "Sohn", EntityRoleResolutionEngine.Gender.MASCULINE
        )
        val result = EntityRoleResolutionEngine.resolvePossessiveRelation(
            "meine", "Tochter", EntityRoleResolutionEngine.CenteringFrame(null, null, emptyList()),
            "owner", "assistant", entities + (son.id to son),
            listOf(EntityRoleResolutionEngine.RelationEdge(
                "owner", "CHILD", "son", setOf("verified-child-only")))
        )
        assertTrue(result is EntityRoleResolutionEngine.Resolution.NoEvidence)
    }

    @Test fun nextSheResolvesToNewSubjectAfterCenteringShift() {
        val afterSister=EntityRoleResolutionEngine.advanceFrame(
            "zoey",listOf("alina" to EntityRoleResolutionEngine.Role.POSSESSOR))
        val result=EntityRoleResolutionEngine.resolvePersonalPronoun("sie",afterSister,entities)
        assertTrue(result is EntityRoleResolutionEngine.Resolution.Resolved)
        assertEquals("zoey",(result as EntityRoleResolutionEngine.Resolution.Resolved).entity.id)
    }

    @Test fun absentKinshipEdgeDoesNotInventFamilyRelation() {
        val frame=EntityRoleResolutionEngine.advanceFrame("alina",emptyList())
        assertTrue(EntityRoleResolutionEngine.resolvePossessiveRelation(
            "ihre","Schwester",frame,"owner","assistant",entities,emptyList()
        ) is EntityRoleResolutionEngine.Resolution.NoEvidence)
    }

    @Test fun multipleRelationTargetsAreAmbiguousInsteadOfRegistryOrder() {
        val extra=EntityRoleResolutionEngine.Entity(
            "third","OWNER.CHILD[2]","Dritte",EntityRoleResolutionEngine.Gender.FEMININE)
        val registry=entities + (extra.id to extra)
        val edges=listOf(
            EntityRoleResolutionEngine.RelationEdge("alina","SISTER","zoey",setOf("e1")),
            EntityRoleResolutionEngine.RelationEdge("alina","SISTER","third",setOf("e2"))
        )
        val frame=EntityRoleResolutionEngine.advanceFrame("alina",emptyList())
        val result=EntityRoleResolutionEngine.resolvePossessiveRelation(
            "ihre","Schwester",frame,"owner","assistant",registry,edges)
        assertTrue(result is EntityRoleResolutionEngine.Resolution.Ambiguous)
    }

    @Test fun ownerAndAssistantPossessivesUseExplicitAnchors() {
        val ownerEdge=EntityRoleResolutionEngine.RelationEdge(
            "owner","CHILD","alina",setOf("owner-child"))
        val mine=EntityRoleResolutionEngine.resolvePossessiveRelation(
            "meine","Tochter",EntityRoleResolutionEngine.CenteringFrame(null,null,emptyList()),
            "owner","assistant",entities,listOf(ownerEdge))
        assertEquals("alina",(mine as EntityRoleResolutionEngine.Resolution.Resolved).entity.id)
        val yours=EntityRoleResolutionEngine.resolvePossessiveRelation(
            "deine","Tochter",EntityRoleResolutionEngine.CenteringFrame(null,null,emptyList()),
            "owner","assistant",entities,listOf(ownerEdge))
        assertTrue(yours is EntityRoleResolutionEngine.Resolution.NoEvidence)
    }

    @Test fun childTermsRequireTheKnownTargetGender() {
        val son=EntityRoleResolutionEngine.Entity(
            "synthetic-son","OWNER.CHILD[2]","Testsohn",EntityRoleResolutionEngine.Gender.MASCULINE)
        val registry=entities + (son.id to son)
        val edges=listOf(
            EntityRoleResolutionEngine.RelationEdge("owner","CHILD","alina",setOf("daughter-evidence")),
            EntityRoleResolutionEngine.RelationEdge("owner","CHILD",son.id,setOf("son-evidence")))
        val frame=EntityRoleResolutionEngine.CenteringFrame(null,null,emptyList())
        fun resolve(relation:String)=EntityRoleResolutionEngine.resolvePossessiveRelation(
            "mein",relation,frame,"owner","assistant",registry,edges)
        val daughter=resolve("Tochter") as EntityRoleResolutionEngine.Resolution.Resolved
        assertEquals("alina",daughter.entity.id)
        assertEquals(setOf("daughter-evidence"),daughter.sourceIds)
        assertEquals(son.id,(resolve("Sohn") as EntityRoleResolutionEngine.Resolution.Resolved).entity.id)
        assertTrue(resolve("Kind") is EntityRoleResolutionEngine.Resolution.Ambiguous)
        assertTrue(resolve("CHILD") is EntityRoleResolutionEngine.Resolution.Ambiguous)
    }

    @Test fun unknownGenderChildCannotBeDeclaredDaughterOrSon() {
        val child=EntityRoleResolutionEngine.Entity(
            "synthetic-child","OWNER.CHILD[2]","Testkind",EntityRoleResolutionEngine.Gender.UNKNOWN)
        val edge=EntityRoleResolutionEngine.RelationEdge("owner","CHILD",child.id,setOf("child-evidence"))
        for (relation in listOf("Tochter","Sohn")) {
            assertTrue(EntityRoleResolutionEngine.resolvePossessiveRelation(
                "mein",relation,EntityRoleResolutionEngine.CenteringFrame(null,null,emptyList()),
                "owner","assistant",entities + (child.id to child),listOf(edge))
                is EntityRoleResolutionEngine.Resolution.NoEvidence)
        }
    }

    @Test fun sharedOwnerChildEdgesDoNotInventASisterEdge() {
        val edges=listOf(
            EntityRoleResolutionEngine.RelationEdge("owner","CHILD","alina",setOf("first-child")),
            EntityRoleResolutionEngine.RelationEdge("owner","CHILD","zoey",setOf("second-child")))
        assertTrue(EntityRoleResolutionEngine.resolvePossessiveRelation(
            "ihre","Schwester",EntityRoleResolutionEngine.advanceFrame("alina",emptyList()),
            "owner","assistant",entities,edges) is EntityRoleResolutionEngine.Resolution.NoEvidence)
    }

}
