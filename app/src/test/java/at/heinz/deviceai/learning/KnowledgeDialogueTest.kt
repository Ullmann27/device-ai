package at.heinz.deviceai.learning

import at.heinz.deviceai.BootstrapBrain
import at.heinz.deviceai.BootstrapKind
import org.junit.Assert.*
import org.junit.Test

class KnowledgeDialogueTest {
    @Test fun derivesNovelRelationFromTwoExplicitMemoriesWithoutToolCalls() {
        val memories = listOf("Fakt: Nova | ist | Mobilgerät", "Regel: Mobilgerät | besitzt | Betriebssystem")
        val answer = BootstrapBrain.decide("Prüfe Wissen: Nova | besitzt | Betriebssystem", memories)
        assertEquals(BootstrapKind.REPLY, answer.kind)
        assertNull(answer.tool)
        assertTrue(answer.message, answer.message.contains("ABGELEITET"))
        assertTrue(answer.message.contains(memories[0]))
        assertTrue(answer.message.contains(memories[1]))
    }
}

class KnowledgeReasoningTest {
    private fun ask(facts: List<String>, text: String, checks: Int = 4096) =
        StructuredKnowledge(checks).query(facts, KnowledgeSyntax.literal(text)!!)

    @Test fun generalizesAcrossUnseenNamesAndMultipleHops() {
        repeat(12) { i ->
            val facts = listOf("Fakt: Objekt $i | ist | Klasse $i", "Regel: Klasse $i | ist | Oberklasse $i",
                "Regel: Oberklasse $i | benötigt | Ressource $i")
            val answer = ask(facts, "Objekt $i | benötigt | Ressource $i")
            assertEquals(KnowledgeStatus.SUPPORTED, answer.status)
            assertEquals(3, answer.evidence.size)
            assertEquals(2, answer.steps.size)
        }
    }
    @Test fun contradictionsDoNotBecomeFactsAndCannotPoisonDescendants() {
        val facts = listOf("Fakt: n | ist | a", "Fakt: n | nicht ist | a", "Regel: a | hat | b")
        assertEquals(KnowledgeStatus.CONFLICT, ask(facts, "n | ist | a").status)
        val answer = ask(facts, "n | hat | b")
        assertEquals(KnowledgeStatus.UNKNOWN, answer.status)
        assertEquals(3, answer.evidence.size)
        assertEquals(KnowledgeStatus.UNKNOWN, ask(facts, "fremd | hat | b").status)
    }
    @Test fun negativeEvidenceIsExplicitAndCyclesAreBounded() {
        val facts = listOf("Fakt: n | ist | a", "Regel: a | ist | b", "Regel: b | ist | a",
            "Regel: b | nicht hat | flügel")
        assertEquals(KnowledgeStatus.REFUTED, ask(facts, "n | hat | flügel").status)
        assertEquals(KnowledgeStatus.SUPPORTED, ask(facts, "n | nicht hat | flügel").status)
        assertEquals(KnowledgeStatus.LIMIT, ask(facts, "n | hat | flügel", 1).status)
    }
    @Test fun revisionsInvalidateDerivedKnowledgeAndPreserveOtherFacts() {
        val facts = listOf("Fakt: n | ist | a", "Regel: a | hat | b", "Ich mag Blau")
        val revised = KnowledgeRevision.replace(facts, facts[0], "Fakt: n | ist | c")!!
        assertEquals(KnowledgeStatus.UNKNOWN, ask(revised, "n | hat | b").status)
        assertTrue("Ich mag Blau" in revised)
        val revoked = KnowledgeRevision.replace(facts, facts[1], null)!!
        assertEquals(KnowledgeStatus.UNKNOWN, ask(revoked, "n | hat | b").status)
        assertNull(KnowledgeRevision.replace(facts, "missing", null))
    }
    @Test fun malformedQueriesWithDeviceKeywordsNeverRequestTools() {
        listOf("Prüfe Wissen: Akku", "Prüfe Wissen: Downloads | hat | Datei", "Prüfe Wissen: " + "x".repeat(2200))
            .forEach { assertNull(BootstrapBrain.decide(it, emptyList()).tool) }
        assertEquals(BootstrapKind.REPLY, BootstrapBrain.decide("Merke dir: Regel: fehlerhaft", emptyList()).kind)
    }
}
