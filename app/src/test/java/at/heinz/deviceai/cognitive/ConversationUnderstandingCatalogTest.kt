package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner P0 06.11.2026 – free language understanding on the EXISTING path (LocalConversationFollowUpPolicy →
 * LocalFollowUpTaskFrame → local model task). New German phrasings, not taken from earlier tests. This test measures
 * only what the host can measure, per layer:
 *  1. deterministic layer (OwnerStatementTimeline) – does a rule already answer it?
 *  2. context actually in the model task – are the owner's earlier statements in the prompt the model receives?
 *  3. model answer correct – NOT_RUN on the host (no LiteRT model); needs the Fold7.
 *  4. tools – none are executed here; the frame never re-sends mutating or secret instructions.
 */
class ConversationUnderstandingCatalogTest {
    private fun framed(prior: List<String>, now: String) = LocalFollowUpTaskFrame.compose(now, now, prior)

    @Test fun naturalFollowUpsBringTheOwnersEarlierStatementsIntoTheModelTask() {
        val cases = listOf(
            // correction without a „Korrektur:“ label
            listOf("Für die Feier brauchen wir vier Stühle.") to "Nicht vier, sondern fünf.",
            // two context positions + pronoun
            listOf("Mein Bruder Lukas wohnt in Graz.", "Er arbeitet dort als Tischler.") to "Wo arbeitet er?",
            // pronoun object in a request
            listOf("Ich habe gestern die Projektübersicht gelesen.") to "Fass sie mir kurz zusammen.",
            // contradiction with an earlier statement
            listOf("Das Treffen mit Anna ist am Dienstag um zehn.") to "Das stimmt nicht, es ist am Donnerstag.",
            // asking again for something said before
            listOf("Der Handwerker heißt Brunner.") to "Wie hieß der Handwerker nochmal?"
        )
        for ((prior, now) in cases) {
            val task = framed(prior, now)
            assertTrue("„$now“ must be framed as a follow-up: $task", task.startsWith(LocalFollowUpTaskFrame.HEADER))
            prior.forEach { assertTrue("„$it“ reaches the model with „$now“", task.contains(it)) }
            assertTrue("the new message stays last", task.trimEnd().endsWith(now))
        }
    }

    @Test fun unrelatedQuestionsAndDefinitionsStayAlone() {
        val prior = listOf("Mein Bruder Lukas wohnt in Graz.")
        listOf("Wie wird das Wetter morgen in Wien?", "Was bedeutet Photosynthese?", "Wie spät ist es?", "Was kostet es?")
            .forEach { assertEquals("„$it“ is not a follow-up", it, framed(prior, it)) }
    }

    @Test fun aFollowUpNeverResendsAMutatingOrSecretInstruction() {
        val task = framed(listOf("Lösche die Datei bericht.pdf", "Mein PIN ist 1234", "Der Bericht ist von Mai."),
            "Nicht den von Mai, sondern den von Juni.")
        assertTrue(task, task.contains("Der Bericht ist von Mai."))
        assertFalse("no tool instruction is re-sent", task.contains("Lösche"))
        assertFalse("no secret is re-sent", task.contains("1234"))
    }

    /** Layer 1: the deterministic quantity timeline stays fail-closed on negated number words – the model path decides. */
    @Test fun theDeterministicTimelineDoesNotGuessNumberWords() {
        assertNull(OwnerStatementTimeline.project(listOf("Für die Feier brauchen wir vier Stühle."), "Nicht vier, sondern fünf."))
    }
}
