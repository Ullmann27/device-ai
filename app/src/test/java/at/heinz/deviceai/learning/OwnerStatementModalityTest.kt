package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** M2 cognitive memory: what kind of statement did the owner make? Only an assertion may become a confirmed fact. */
class OwnerStatementModalityTest {
    private fun assertKind(expected: StatementModality, vararg texts: String) = texts.forEach {
        assertEquals(it, expected, OwnerStatementModality.classify(it))
    }

    @Test fun plainOwnerAssertionsStayAssertions() = assertKind(StatementModality.ASSERTED,
        "Mein Hund heißt Bello.", "Ich wohne in Graz.", "Meine Tochter Klara ist 29.",
        "Ich arbeite bei der Voestalpine.", "Ich bin nach Linz gezogen.",
        "Korrektur: Ich wohne nicht in Graz, sondern in Linz.", "Mein Projekt heißt „Astrolux“.",
        "Emma wohnt seit 2024 in Wien.", "Ich wohne jetzt in Salzburg.")

    @Test fun conditionalAndCounterfactualStatementsAreHypothetical() = assertKind(StatementModality.HYPOTHETICAL,
        "Wenn ich im Lotto gewinne, kaufe ich ein Haus in Kitzbühel.",
        "Falls ich nach Berlin ziehe, nehme ich den Hund mit.",
        "Angenommen, ich wohne in Wien.", "Ich würde gern in Paris wohnen.",
        "Hätte ich mehr Zeit, wäre ich Pilot.", "Stell dir vor, ich heiße Maximilian.")

    @Test fun hedgedStatementsAreUncertain() = assertKind(StatementModality.UNCERTAIN,
        "Vielleicht ziehe ich nach Linz.", "Ich glaube, mein Auto ist blau.",
        "Eventuell arbeite ich ab März bei Siemens.", "Meine Schwester wohnt wahrscheinlich in Graz.",
        "Ich bin mir nicht sicher, ob Klara 29 ist.")

    @Test fun plansAndWishesAreIntentionsNotStates() = assertKind(StatementModality.INTENTION,
        "Ich überlege, ein rotes Auto zu kaufen.", "Ich plane, nach Graz zu ziehen.",
        "Ich möchte in Wien wohnen.", "Nächstes Jahr ziehe ich nach Innsbruck.", "Ich werde Pilot.")

    @Test fun questionsAreNeverFacts() = assertKind(StatementModality.QUESTION,
        "Wohne ich in Graz?", "Ob mein Hund Bello heißt", "Wie alt ist Klara?")

    @Test fun reportedAndQuotedSpeechBelongsToSomeoneElse() = assertKind(StatementModality.REPORTED,
        "Anna sagt, ich wohne in Salzburg.", "Laut Peter heißt mein Hund Rex.",
        "Angeblich arbeite ich bei Google.", "„Ich wohne in Rom“, schrieb Lisa.")

    @Test fun negatedStatementsAreBarriersNotPositiveValues() {
        assertKind(StatementModality.NEGATED, "Meine Tochter Klara ist nicht 29.", "Anna wohnt nicht in Graz.",
            "Ich wohne nicht mehr in Wien.")
        // A negation may be kept as a confirmed remove-only barrier; it never supplies a positive value.
        assertTrue(OwnerStatementModality.mayBecomeConfirmedFact(StatementModality.NEGATED))
    }

    @Test fun onlyAssertionsAndBarriersMayBecomeConfirmed() {
        assertTrue(OwnerStatementModality.mayBecomeConfirmedFact(StatementModality.ASSERTED))
        for (m in listOf(StatementModality.HYPOTHETICAL, StatementModality.UNCERTAIN, StatementModality.INTENTION,
                StatementModality.QUESTION, StatementModality.REPORTED))
            assertFalse(m.name, OwnerStatementModality.mayBecomeConfirmedFact(m))
    }
}
