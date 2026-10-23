package at.heinz.deviceai.intelligence.maxultra

import at.heinz.deviceai.intelligence.maxultra.Ctx1DialogueContext.Ask
import at.heinz.deviceai.intelligence.maxultra.Ctx1DialogueContext.G
import at.heinz.deviceai.intelligence.maxultra.Ctx1DialogueContext.Unknown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** CTX2: the generalized routine (B2) against the owner's own phrasings; CTX1 (B1) stays exactly as measured. */
class Ctx2FactPhrasingTest {
    private val facts = listOf(
        "Mein Bruder heißt Max.", "Max ist 34 Jahre alt.",
        "Ich habe eine Kollegin, sie heißt Anna.", "Anna wohnt in Graz.",
        "Wir haben einen Hund namens Bello.", "Bello frisst am liebsten Fisch.",
        "Mimi ist unsere Katze.", "Das Auto hat die Farbe Rot.",
        "Theresa, meine Nichte, wohnt in Wels.")

    @Test fun ctx1MissesStoredPeopleItsPatternsDoNotKnowButB2FindsThem() {
        val b1 = Ctx1DialogueContext(facts)
        assertEquals("CTX1 registry knows none of these phrasings", 0, b1.registry.size)
        assertEquals(Unknown, b1.decide("Wie alt ist Max?"))
        val b2 = Ctx1DialogueContext(facts, generalized = true)
        val q = b2.decide("Wie alt ist Max?") as Ask
        assertEquals(setOf("Mein Bruder heißt Max.", "Max ist 34 Jahre alt."), q.scope)
    }

    @Test fun b2ReadsGenderFromPossessivesArticlesAndPronounsOfTheKnownPhrasings() {
        val byName = Ctx1DialogueContext.buildRegistry(facts, generalized = true).associateBy { it.surface }
        assertEquals(setOf(G.M), byName.getValue("Max").genders)
        assertEquals(setOf(G.F), byName.getValue("Anna").genders)
        assertEquals(setOf(G.M), byName.getValue("Bello").genders)
        assertEquals(setOf("Bello", "Hund"), byName.getValue("Bello").aliases)
        assertEquals(setOf(G.F), byName.getValue("Mimi").genders)
        assertEquals(setOf(G.N), byName.getValue("Auto").genders)
        // a fact subject in an unknown phrasing still counts, with unknown gender
        assertEquals(setOf(G.M, G.F), byName.getValue("Theresa").genders)
        assertTrue(byName.keys.none { it in setOf("Mein", "Ich", "Wir", "Das", "Bruder", "Kollegin") })
    }

    @Test fun b2ResolvesPronounsOverTheNewPhrasings() {
        val c = Ctx1DialogueContext(facts, generalized = true)
        c.decide("Erzähl mir von Anna.")
        assertEquals("Wo wohnt Anna?", (c.decide("Wo wohnt sie?") as Ask).query)
        c.decide("Was frisst der Hund?")
        assertEquals("Was frisst Bello am liebsten?", (c.decide("Was frisst er am liebsten?") as Ask).query)
    }

    @Test fun b2CallsOnlyANameNoMemoryContainsUnknown() {
        val c = Ctx1DialogueContext(facts, generalized = true)
        assertEquals(Unknown, c.decide("Wie alt ist Gustav?"))
        // stored, but in a phrasing no rule reads: known (the core answers it), never "unknown"
        val stored = Ctx1DialogueContext(listOf("Zu Besuch kommt oft Gerda, die Nachbarin."), generalized = true)
        assertTrue(stored.decide("Wer ist Gerda?") is Ask)
    }

    @Test fun ctx2CorpusRewritesTheIntroducingFactsAndKeepsGoldStored() {
        val dialogs = Ctx2Corpus.split(Ctx1Corpus.Split.DEV, 5L, 2)
        assertEquals(Ctx1Corpus.FAMILIES.size * 2, dialogs.size)
        val ctx1 = Ctx1Corpus.split(Ctx1Corpus.Split.TRAIN, 5L, 2)
        assertEquals(ctx1.map { it.turns.map { t -> t.text } }, dialogs.map { it.turns.map { t -> t.text } })
        val relation = Regex("^\\p{Lu}[\\p{L}-]+ ist (mein|meine) \\p{Lu}[\\p{L}-]+\\.$")
        assertTrue(dialogs.flatMap { it.facts }.none { relation.matches(it) })
        for (d in dialogs) for (t in d.turns) (t.gold as? Ctx1Corpus.Memory)?.let { assertTrue(d.id, d.facts.containsAll(it.facts)) }
        assertEquals(Ctx1Corpus.canonical(dialogs), Ctx1Corpus.canonical(Ctx2Corpus.split(Ctx1Corpus.Split.DEV, 5L, 2)))
    }
}
