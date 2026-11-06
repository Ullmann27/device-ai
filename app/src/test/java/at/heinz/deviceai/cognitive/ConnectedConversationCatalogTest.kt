package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner brief 06.11.2026 (Priorität 2) – connected conversations: several statements in one message, indirect
 * references, contradictions, temporal change, consequences. Measured per layer, host only:
 *  1. intent layer – no rule may hijack a reasoning question (RED: [WorkDecisionRecall] answered the owner's example
 *     sentence from the journal because it contains „wir … entschieden … ?“);
 *  2. context layer – the owner's earlier statements the question depends on are inside the ONE task the model
 *     receives ([LocalFollowUpTaskFrame]); RED: „die zweite Möglichkeit“, „unseren Plan“, „… jetzt?“ and
 *     „Was bedeutet das für …?“ were not framed („Was bedeutet …“ even counted as a word definition);
 *  3. model answer – NOT_RUN on the host (no LiteRT model); the quality of the answer is unverified here;
 *  4. tools – none; the frame never re-sends mutating or secret instructions (covered in the P0 catalog).
 */
class ConnectedConversationCatalogTest {
    private val ownerExample = "Wir hatten uns gestern für die zweite Möglichkeit entschieden, aber die Voraussetzung hat " +
        "sich heute geändert. Was bedeutet das für unseren Plan?"
    private val options = listOf("Für die Bewerbung gibt es zwei Möglichkeiten: per E-Mail oder über das Portal.",
        "Das Portal braucht ein Bürgerkonto, die E-Mail nicht.")

    private fun framed(prior: List<String>, now: String) = LocalFollowUpTaskFrame.compose(now, now, prior)

    /** Layer 1: the owner's sentence is a reasoning question for the model, not a journal lookup or a document command. */
    @Test fun theOwnersExampleIsNotHijackedByAnyRule() {
        assertNull("WorkDecisionRecall must leave reasoning questions to the model", WorkDecisionRecall.parse(ownerExample))
        assertNull(WorkRecall.parse(ownerExample))
        assertNull(OwnerCorrectionIntent.parse(ownerExample))
        assertNull(OwnerDocumentIntent.parse(ownerExample))
        // The journal question itself keeps working when it IS the question.
        assertEquals(WorkDecisionRecall.Ask.Decisions(WorkRecall.Period.YESTERDAY, false),
            WorkDecisionRecall.parse("Was haben wir gestern entschieden?"))
        // A statement about a decision followed by an unrelated question is not a journal lookup either.
        assertNull(WorkDecisionRecall.parse("Wir haben uns gestern entschieden. Wie spät ist es?"))
    }

    /** Layer 1/2: as a first message the example is self-contained – answered, never refused for missing history. */
    @Test fun theOwnersExampleStandsAloneWithoutHistory() {
        assertFalse(LocalConversationFollowUpPolicy.requiresPreviousOwnerTurn(ownerExample))
        assertEquals(ownerExample, framed(emptyList(), ownerExample))
        assertEquals(ownerExample, LocalFollowUpTaskFrame.compose(ownerExample, ownerExample, null))
    }

    /** Layer 2: with history, the options the owner enumerated earlier travel inside the task. */
    @Test fun indirectReferencesBringTheEnumeratedOptionsIntoTheTask() {
        val task = framed(options, ownerExample)
        assertTrue(task, task.startsWith(LocalFollowUpTaskFrame.HEADER))
        options.forEach { assertTrue("„$it“ reaches the model", task.contains(it)) }
        assertTrue(task.trimEnd().endsWith(ownerExample))
        // A bare consequence question after the statements is a follow-up too – and needs history when it comes first.
        val consequence = "Was bedeutet das für unseren Plan?"
        assertTrue(LocalConversationFollowUpPolicy.requiresPreviousOwnerTurn(consequence))
        assertTrue(framed(options, consequence).contains(options[0]))
        // … while a real word definition still stands alone.
        assertFalse(LocalConversationFollowUpPolicy.requiresPreviousOwnerTurn("Was bedeutet Voraussetzung?"))
        assertEquals("Was bedeutet Photosynthese?", framed(options, "Was bedeutet Photosynthese?"))
    }

    /** Layer 2: temporal change and contradiction – both statements reach the model in order; later replaces earlier. */
    @Test fun temporalChangeAndContradictionKeepBothStatementsInOrder() {
        val prior = listOf("Der Abgabetermin ist am Montag.", "Der Abgabetermin wurde auf Mittwoch verschoben.")
        val task = framed(prior, "Wann ist der Abgabetermin jetzt?")
        assertTrue(task, task.startsWith(LocalFollowUpTaskFrame.HEADER))
        assertTrue(task.indexOf(prior[0]) in 0 until task.indexOf(prior[1]))
        assertTrue("the frame tells the model that later statements replace earlier ones", task.contains("Spätere Angaben ersetzen frühere"))
        // Layer 1: the deterministic timeline does not guess weekdays.
        assertNull(OwnerStatementTimeline.project(prior, "Wann ist der Abgabetermin jetzt?"))
        // The same question as a first message is a normal question, not a refusal.
        assertFalse(LocalConversationFollowUpPolicy.requiresPreviousOwnerTurn("Wann ist der Abgabetermin jetzt?"))
    }

    /** Layer 2: a logical consequence drawn from two earlier statements. */
    @Test fun logicalConsequenceGetsBothPremises() {
        val prior = listOf("Ohne geladenes Modell kann JARVIS keinen Entwurf erstellen.", "Das Modell ist gerade nicht geladen.")
        val task = framed(prior, "Kann er den Entwurf jetzt erstellen?")
        prior.forEach { assertTrue(task, task.contains(it)) }
    }

    /** Layer 2/3: ambiguity – both candidates are handed over and the frame asks the model to ask back, not to guess. */
    @Test fun ambiguityHandsOverBothCandidatesAndAsksForAClarifyingQuestion() {
        val prior = listOf("Anna kommt am Dienstag.", "Lisa kommt am Donnerstag.")
        val task = framed(prior, "Wann kommt sie?")
        prior.forEach { assertTrue(task, task.contains(it)) }
        assertTrue("the frame instructs a clarifying question on ambiguity: $task", task.contains("Rückfrage"))
        assertNull(OwnerStatementTimeline.project(prior, "Wann kommt sie?"))
    }

    /** Layer 3 is not measurable here: the host has no LiteRT model. This test only records that fact. */
    @Test fun modelAnswerQualityIsMarkedUnverifiedOnTheHost() {
        assertEquals("NOT_RUN (kein LiteRT-Modell auf dem Host; Antwortqualität ungeprüft)", LAYER_3_STATUS)
    }

    @Test fun inlineHerePointsForwardRatherThanDemandingEarlierHistory() {
        val inline = listOf(
            "Was bedeutet das hier: „Carpe diem“?",
            "Was bedeutet das hier: \"Carpe diem\"?",
            "Was heißt das hier auf Englisch: Guten Morgen?",
            "Was bedeutet das hier konkret: „Carpe diem“?"
        )
        inline.forEach { text ->
            assertFalse("An inline quote is self-contained: $text",
                LocalConversationFollowUpPolicy.requiresPreviousOwnerTurn(text))
            assertFalse("No history is needed for: $text",
                LocalConversationFollowUpPolicy.benefitsFromPreviousOwnerTurn(text))
            assertEquals(text, framed(emptyList(), text))
        }
        listOf(
            "Was bedeutet das hier?",
            "Was bedeutet das hier eigentlich?",
            "Was bedeutet das hier für unseren Plan?",
            "Was bedeutet das hier jetzt?",
            "Was heißt das hier konkret?",
            "Was folgt das hier dann?",
            "Was bedeutet das hier also?",
            "Was bedeutet das hier eigentlich für uns?",
            "Was bedeutet das hier, was du gesagt hast?"
        ).forEach { text ->
            assertTrue("Unresolved demonstrative needs context: $text",
                LocalConversationFollowUpPolicy.requiresPreviousOwnerTurn(text))
            assertTrue("Context rendering needs previous owner turn: $text",
                LocalConversationFollowUpPolicy.benefitsFromPreviousOwnerTurn(text))
        }
    }

    private companion object {
        const val LAYER_3_STATUS = "NOT_RUN (kein LiteRT-Modell auf dem Host; Antwortqualität ungeprüft)"
    }
}
