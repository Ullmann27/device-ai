package at.heinz.deviceai

import at.heinz.deviceai.learning.context.*
import org.junit.Assert.*
import org.junit.Test

class Ctx3RoleResolutionTest {
    private fun entities(vararg pairs: Pair<String, String>): List<Ctx3Entity> =
        Ctx3EntityIndex.fromFacts(linkedMapOf(*pairs))

    @Test fun confirmedFamilyFactsCreateCanonicalRoleMetadata() {
        val e = entities(
            "s1" to "Meine Tochter Alina ist 7.",
            "s2" to "Meine Tochter Zoey ist 6.",
            "s3" to "Anna ist meine Schwester."
        )

        val alina = e.single { it.label == "Alina" }
        val zoey = e.single { it.label == "Zoey" }
        val anna = e.single { it.label == "Anna" }

        assertEquals(Ctx3Gender.FEMININE, alina.gender)
        assertEquals(setOf(Ctx3OwnerRole.DAUGHTER), alina.ownerRoles)
        assertEquals(setOf("sie"), alina.pronouns)
        assertEquals(setOf("s1"), alina.sourceIds)

        assertEquals(setOf(Ctx3OwnerRole.DAUGHTER), zoey.ownerRoles)
        assertEquals(setOf(Ctx3OwnerRole.SISTER), anna.ownerRoles)
    }

    @Test fun feminineFocusCanResolveUniqueSisterThenPronounKeepsNewCenter() {
        val e = entities(
            "s1" to "Meine Tochter Alina ist 7.",
            "s2" to "Meine Tochter Zoey ist 6."
        )
        val c = Ctx3Context()

        val first = c.decide("Alina", e, "v1") as Ctx3Decision.Query
        assertEquals(setOf("s1"), first.sourceIds)

        val sister = c.decide("Ihre Schwester ist jünger.", e, "v1") as Ctx3Decision.Query
        assertEquals(setOf("s2"), sister.sourceIds)
        assertEquals("ROLE_POSSESSIVE_BINDING", sister.resolution)
        assertTrue(sister.query.contains("Zoey"))

        val pronoun = c.decide("Wie alt ist sie?", e, "v1") as Ctx3Decision.Query
        assertEquals(setOf("s2"), pronoun.sourceIds)
        assertEquals("PRONOUN_BINDING", pronoun.resolution)
        assertTrue(pronoun.query.contains("Zoey"))
    }

    @Test fun multiplePossibleSistersFailClosedInsteadOfGuessing() {
        val e = entities(
            "s1" to "Meine Tochter Alina ist 9.",
            "s2" to "Meine Tochter Zoey ist 7.",
            "s3" to "Meine Tochter Klara ist 5."
        )
        val c = Ctx3Context()
        c.decide("Alina", e, "v1")

        val decision = c.decide("Ihre Schwester ist jünger.", e, "v1")
        assertTrue(decision is Ctx3Decision.Clarify)
        assertEquals("NO_UNIQUE_ROLE_REFERENT", (decision as Ctx3Decision.Clarify).reason)
    }

    @Test fun directOwnerRoleCanBindWithoutInventingAssistantFamily() {
        val e = entities("s1" to "Anna ist meine Schwester.")
        val c = Ctx3Context()

        val mine = c.decide("Wie alt ist meine Schwester?", e, "v1") as Ctx3Decision.Query
        assertEquals(setOf("s1"), mine.sourceIds)
        assertEquals("ROLE_POSSESSIVE_BINDING", mine.resolution)

        val yours = Ctx3Context().decide("Wie alt ist deine Schwester?", e, "v1")
        assertTrue(yours is Ctx3Decision.Clarify)
    }
}
