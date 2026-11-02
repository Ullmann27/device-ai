package at.heinz.deviceai.intelligence.maxultra

import at.heinz.deviceai.intelligence.maxultra.Ctx1DialogueContext.Ask
import at.heinz.deviceai.intelligence.maxultra.Ctx1DialogueContext.Clarify
import at.heinz.deviceai.intelligence.maxultra.Ctx1DialogueContext.G
import at.heinz.deviceai.intelligence.maxultra.Ctx1DialogueContext.Unknown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Ctx1DialogueContextTest {
    private val facts = listOf(
        "Max ist mein Bruder.", "Max ist 34 Jahre alt.", "Max wohnt in Graz.",
        "Paul ist mein Onkel.", "Paul ist 61 Jahre alt.", "Paul wohnt in Linz.",
        "Anna ist meine Schwester.", "Anna ist 29 Jahre alt.", "Anna wohnt in Wien.",
        "Unsere Katze heißt Mimi.", "Mimi ist 4 Jahre alt.",
        "Unser Meerschweinchen heißt Flocki.", "Mein Auto ist 7 Jahre alt.",
        "Am Montag habe ich einen Termin beim Zahnarzt.", "Am Mittwoch habe ich Training im Verein.")

    private fun ask(d: Ctx1DialogueContext.Decision) = (d as Ask)

    @Test fun registryDerivesGenderFromDeterminersAndDiminutives() {
        val byName = Ctx1DialogueContext.buildRegistry(facts).associateBy { it.surface }
        assertEquals(setOf(G.M), byName.getValue("Max").genders)
        assertEquals(setOf(G.F), byName.getValue("Anna").genders)
        assertEquals(setOf(G.F), byName.getValue("Mimi").genders)
        assertEquals(setOf("Mimi", "Katze"), byName.getValue("Mimi").aliases)
        assertEquals(setOf(G.N), byName.getValue("Flocki").genders)
        assertEquals(setOf(G.M, G.N), byName.getValue("Auto").genders)
        assertEquals(6, byName.size)
    }

    @Test fun aPronounGoesToTheMostRecentMatchingMentionAndAsksOnlyAboutItsFacts() {
        val c = Ctx1DialogueContext(facts)
        ask(c.decide("Erzähl mir von Max."))
        ask(c.decide("Was weißt du über Anna?"))
        val q = ask(c.decide("Wie alt ist er?"))
        assertEquals("Wie alt ist Max?", q.query)
        assertEquals(setOf("Max ist mein Bruder.", "Max ist 34 Jahre alt.", "Max wohnt in Graz."), q.scope)
        assertEquals("Wo wohnt Anna?", ask(c.decide("Wo wohnt sie?")).query)
    }

    @Test fun twoCandidatesFromTheSameTurnOrNoneAtAllNeedAQuestionBack() {
        val c = Ctx1DialogueContext(facts)
        assertEquals(Clarify, c.decide("Wie alt ist er?"))
        c.decide("Erzähl mir von Max und Paul.")
        assertEquals(Clarify, c.decide("Wo wohnt er?"))
        assertEquals("Wie alt ist Anna?", ask(c.decide("Wie alt ist Anna?")).query)
        assertEquals("Wo wohnt Anna?", ask(c.decide("Wo wohnt sie?")).query)
    }

    @Test fun aShortFollowUpRetargetsTheLastQuestion() {
        val c = Ctx1DialogueContext(facts)
        ask(c.decide("Wie alt ist Max?"))
        assertEquals("Wie alt ist Paul?", ask(c.decide("Und Paul?")).query)
        assertEquals("Wie alt ist Anna?", ask(c.decide("Nein, ich meinte Anna.")).query)
        assertEquals("Wie alt ist Max?", ask(c.decide("Nicht Anna, sondern Max.")).query)
        assertEquals("Wohnt Max ?", ask(c.decide("Wohnt er nicht in Linz?")).query)
        val days = Ctx1DialogueContext(facts)
        ask(days.decide("Was habe ich am Montag vor?"))
        val wednesday = ask(days.decide("Und am Mittwoch?"))
        assertEquals("Was habe ich am Mittwoch vor?", wednesday.query)
        assertEquals(null, wednesday.scope)
    }

    @Test fun aNameNobodyStoredIsUnknownAndSoIsAPronounThatMeansIt() {
        val c = Ctx1DialogueContext(facts)
        assertEquals(Unknown, c.decide("Wie alt ist Gustav?"))
        assertEquals(Unknown, c.decide("Und wo wohnt er?"))
        // a common noun after a determiner and a place after a preposition are no unknown persons
        val d = Ctx1DialogueContext(facts)
        d.decide("Erzähl mir von Anna.")
        assertEquals("In welcher Stadt wohnt Anna?", ask(d.decide("In welcher Stadt wohnt sie?")).query)
        assertTrue(d.decide("Wohnt sie nicht in Graz?") is Ask)
    }

    @Test fun aCitationAfterAResolvedEntityNeverAddsAnotherMention() {
        val c = Ctx1DialogueContext(facts)
        c.decide("Erzähl mir von Anna.")
        c.observe(listOf("Mimi ist 4 Jahre alt."))   // a wrong citation must not make "sie" ambiguous
        assertEquals("Wie alt ist Anna?", ask(c.decide("Wie alt ist sie?")).query)
        val open = Ctx1DialogueContext(facts)
        open.decide("Wer ist meine Schwester?")
        open.observe(listOf("Anna ist meine Schwester."))
        assertEquals("Wo wohnt Anna?", ask(open.decide("Wo wohnt sie?")).query)
    }

    @Test fun typoRepairUsesOnlyTheReferentsWordsAndOnlyOneUnambiguousSlip() {
        val scope = setOf("Max ist 34 Jahre alt.", "Max wohnt in Graz.")
        assertEquals("Wie alt ist Max?", Ctx1DialogueContext.repairTypo("Wie akt ist Max?", scope))
        assertEquals("Wo wohnt Max?", Ctx1DialogueContext.repairTypo("Wo wohtn Max?", scope))
        assertEquals("Wie alt ist Max?", Ctx1DialogueContext.repairTypo("Wie alt ist Max?", scope))
        assertEquals("Wie xyz ist Max?", Ctx1DialogueContext.repairTypo("Wie xyz ist Max?", scope))
        // two words of the scope are one slip away: no guess
        assertEquals("Was ist rit?", Ctx1DialogueContext.repairTypo("Was ist rit?", setOf("Es ist rot.", "Es ist rut.")))
        assertEquals("Wohnt Max ?", Ctx1DialogueContext.cleanNegation("Wohnt Max nicht in Linz?"))
    }

    @Test fun corpusIsDeterministicGoldIsStoredAndTestVocabularyIsUnseen() {
        val a = Ctx1Corpus.split(Ctx1Corpus.Split.DEV, 7L, 2)
        assertEquals(Ctx1Corpus.canonical(a), Ctx1Corpus.canonical(Ctx1Corpus.split(Ctx1Corpus.Split.DEV, 7L, 2)))
        assertNotEquals(Ctx1Corpus.canonical(a), Ctx1Corpus.canonical(Ctx1Corpus.split(Ctx1Corpus.Split.DEV, 8L, 2)))
        assertEquals(Ctx1Corpus.FAMILIES.size * 2, a.size)
        for (d in a) for (t in d.turns) (t.gold as? Ctx1Corpus.Memory)?.let { assertTrue(d.id, d.facts.containsAll(it.facts)) }
        val known = Ctx1Corpus.split(Ctx1Corpus.Split.TRAIN, 1L, 3).flatMap { it.facts }.flatMap { Ctx1DialogueContext.tokens(it) }.toSet()
        val testNames = Ctx1DialogueContext.buildRegistry(Ctx1Corpus.split(Ctx1Corpus.Split.TEST, 3L, 1).flatMap { it.facts }).map { it.surface }
        assertTrue(testNames.isNotEmpty() && testNames.none { it in known })
    }

    @Test fun classificationCountsAnyClaimWhereNothingOrAQuestionIsRightAsWrong() {
        val gold = Ctx1Corpus.Memory(setOf("Max ist 34 Jahre alt."))
        assertEquals(Ctx1Experiment.Outcome.CORRECT, Ctx1Experiment.classify(gold, listOf("Max ist 34 Jahre alt.", "Max wohnt in Graz."), false))
        assertEquals(Ctx1Experiment.Outcome.WRONG, Ctx1Experiment.classify(gold, listOf("Paul ist 61 Jahre alt."), false))
        assertEquals(Ctx1Experiment.Outcome.MISSED, Ctx1Experiment.classify(gold, emptyList(), false))
        assertEquals(Ctx1Experiment.Outcome.UNNEEDED_CLARIFY, Ctx1Experiment.classify(gold, emptyList(), true))
        assertEquals(Ctx1Experiment.Outcome.WRONG, Ctx1Experiment.classify(Ctx1Corpus.Clarify, listOf("Max ist 34 Jahre alt."), false))
        assertEquals(Ctx1Experiment.Outcome.CLARIFIED, Ctx1Experiment.classify(Ctx1Corpus.Clarify, emptyList(), true))
        assertEquals(Ctx1Experiment.Outcome.WRONG, Ctx1Experiment.classify(Ctx1Corpus.Unknown, listOf("Max ist 34 Jahre alt."), false))
        assertEquals(Ctx1Experiment.Outcome.ABSTAINED, Ctx1Experiment.classify(Ctx1Corpus.Unknown, emptyList(), false))
    }
}
