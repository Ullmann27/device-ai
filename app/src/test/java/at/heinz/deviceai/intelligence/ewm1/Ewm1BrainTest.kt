package at.heinz.deviceai.intelligence.ewm1

import at.heinz.deviceai.learning.ContinuumKnowledgeArchive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** EWM2 router on own synthetic facts and sources (none from DEV5, DEV5b or TEST5); B0 is a stub that keeps memory order. */
class Ewm1BrainTest {
    private var n = 0
    private fun rec(title: String, text: String) = ContinuumKnowledgeArchive.SourceRecord(
        id = "%064x".format(++n), kind = ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT, title = title,
        url = "https://de.wikipedia.org/wiki/T$n", attribution = "Synthetisch", collectedAt = 1_760_000_000_000L,
        sourceDate = null, sha256 = "s$n", text = text)

    private fun brain(facts: List<String>, vararg sources: Pair<String, String>) =
        Ewm1Brain(facts.mapIndexed { i, f -> "f$i" to f }, Ewm1SourceIndex(sources.map { rec(it.first, it.second) })) { _, s -> s.toList() }

    private fun Ewm1Brain.text(a: Ewm1Brain.Answer) = a.cited.map { if (it.startsWith("SOURCE:")) it else workspace.memory.byId.getValue(it).text }

    private val facts = listOf("Jonas wohnt in Gmunden.", "Jonas spielt Posaune.", "Mein Neffe heißt Jonas.", "Das Projekt Nordlicht ist eine Wanderkarte.")

    @Test fun generalQuestionsGoToSourcesAndPersonalOnesToMemory() {
        val b = brain(facts, "Gmunden" to "Gmunden ist eine Stadt am Traunsee.")
        val general = b.ask("Was ist Gmunden?")
        assertEquals("SOURCE", general.route)
        assertTrue(general.cited.single().startsWith("SOURCE:"))
        assertEquals(listOf("Jonas wohnt in Gmunden."), b.text(b.ask("Wo wohnt mein Neffe?")).take(1))
        // the frame's "mir" does not make a general question personal
        assertEquals("SOURCE", b.ask("Kannst du mir etwas über Gmunden sagen?").route)
        assertEquals("SOURCE", b.ask("Erzähl mir etwas über Gmunden.").route)
    }

    @Test fun theOwnersPersonOrOwnNamedThingOutranksANamesakeSource() {
        val b = brain(facts, "Jonas" to "Jonas ist ein biblischer Prophet.", "Nordlicht" to "Ein Nordlicht ist eine Leuchterscheinung.")
        val who = b.ask("Wer ist Jonas?")
        assertEquals("MEMORY", who.route)
        assertEquals("Mein Neffe heißt Jonas.", b.text(who).first())
        assertEquals("Das Projekt Nordlicht ist eine Wanderkarte.", b.text(b.ask("Was ist Nordlicht?")).first())
        // kin outranks the namesake in every frame, not only in "wer"-questions
        val what = b.ask("Was ist Jonas?")
        assertTrue(what.route != "SOURCE" && what.memory != null)
    }

    @Test fun aDefinitionQuestionWithoutSourceIsNotAnsweredByAMereMention() {
        val b = brain(facts)
        val a = b.ask("Was ist eine Posaune?")
        assertEquals("ABSTAIN", a.decision)
        assertTrue(a.cited.isEmpty())
        // an owner attribute in the same frame still reaches memory
        val attr = b.ask("Was ist Jonas' Lieblingsessen?")
        assertTrue(attr.memory != null && attr.decision == "ABSTAIN" && !attr.reason.startsWith("NO_SOURCE"))
        assertEquals("MEMORY", b.ask("Was spielt Jonas?").route)
    }

    @Test fun aTrailingIndefiniteTakesUpTheLastNoun() {
        val b = brain(listOf("Meine Tante Rosa hat eine Nähmaschine.", "Wir haben einen Rasenmäher im Schuppen."),
            "Nähmaschine" to "Eine Nähmaschine ist ein Gerät zum Nähen.")
        assertEquals("SOURCE", b.ask("Was ist eine Nähmaschine?").route)
        assertEquals(listOf("Meine Tante Rosa hat eine Nähmaschine."), b.text(b.ask("Hat Rosa eine?")))
        assertEquals(listOf("Wir haben einen Rasenmäher im Schuppen."), b.text(b.ask("Wo steht unser Rasenmäher?")))
        assertEquals("ABSTAIN", b.ask("Hat Rosa auch einen?").decision)
    }

    @Test fun aSourceNeverBecomesAnOwnerFact() {
        val b = brain(facts, "Posaune" to "Die Posaune ist ein Blechblasinstrument.")
        val json = b.ask("Was ist eine Posaune?").render { b.workspace.memory.byId.getValue(it).text }
        assertTrue(json.contains("\"owner_memory\":null") && json.contains("SOURCE_OBSERVATION_UNVERIFIED"))
    }
}
