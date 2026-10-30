package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryTimelineTest {
    @Test fun aSinceFactSupersedesAnUntilFactAndTheCitationIsReplaced() {
        val facts = listOf("a" to "Ute wohnte bis 2022 in Enns.", "b" to "Ute wohnt seit 2023 in Tulln.")
        assertEquals(listOf(MemoryTimeline.Link("b", "a", MemoryTimeline.Relation.SUPERSEDES)), MemoryTimeline.links(facts))
        assertEquals(MemoryTimeline.Verdict("REPLACE", listOf("b"), "OUTDATED_CITATION"), MemoryTimeline.check(listOf("a"), facts))
        assertEquals("UNCHANGED", MemoryTimeline.check(listOf("b"), facts).decision)
    }

    @Test fun anExplicitCorrectionReplacesTheOldValue() {
        val facts = listOf("a" to "Der Termin von Kurt ist am 7. Juni.", "b" to "Korrektur: Der Termin von Kurt ist nicht am 7. Juni, sondern am 9. Juni.")
        assertEquals("b", MemoryTimeline.check(listOf("a"), facts).factIds.first())
    }

    @Test fun unmarkedDifferentValuesAskBack() {
        val facts = listOf("a" to "Kurt fährt einen grünen Bus.", "b" to "Kurt fährt einen gelben Bus.")
        assertEquals("CLARIFY", MemoryTimeline.check(listOf("a"), facts).decision)
    }

    @Test fun differentAttributesDifferentPeopleAndRepetitionsAreNotLinked() {
        assertTrue(MemoryTimeline.links(listOf("a" to "Ute wohnt in Enns.", "b" to "Ute arbeitet in Tulln.")).isEmpty())
        assertTrue(MemoryTimeline.links(listOf("a" to "Der Termin von Kurt ist am 7. Juni.", "b" to "Der Termin von Ida ist am 9. Juni.")).isEmpty())
        assertTrue(MemoryTimeline.links(listOf("a" to "Ute wohnt in Enns.", "b" to "Ute wohnt in Enns.")).isEmpty())
        assertEquals("UNCHANGED", MemoryTimeline.check(emptyList(), listOf("a" to "Ute wohnt in Enns.")).decision)
    }

    @Test fun aSiblingQuestionIsRewrittenOnlyForExactlyOneCandidate() {
        val one = listOf("a" to "Ute ist die Schwester von Kurt.", "b" to "Kurt wohnt in Enns.")
        assertEquals("Wo wohnt Kurt?", MemoryQuestionResolver.resolve("Wo wohnt der Bruder von Ute?", one))
        val two = one + ("c" to "Ute ist die Schwester von Hans.")
        assertNull(MemoryQuestionResolver.resolve("Wo wohnt der Bruder von Ute?", two))
        assertNull(MemoryQuestionResolver.resolve("Wo wohnt Ute?", one))
    }
}
