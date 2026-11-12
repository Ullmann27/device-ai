package at.heinz.deviceai

import at.heinz.deviceai.cognitive.CertifiedLocationReflex
import at.heinz.deviceai.cognitive.GroundedWebRecall
import at.heinz.deviceai.cognitive.LocalConversationContext
import at.heinz.deviceai.cognitive.OwnerAppLookupIntent
import at.heinz.deviceai.cognitive.OwnerChatIntentRouter
import at.heinz.deviceai.cognitive.OwnerCorrectionIntent
import at.heinz.deviceai.cognitive.OwnerDeviceRequestSafety
import at.heinz.deviceai.cognitive.OwnerDocumentIntent
import at.heinz.deviceai.cognitive.OwnerInternalNavigationIntent
import at.heinz.deviceai.cognitive.OwnerPreviousBatteryIntent
import at.heinz.deviceai.cognitive.OwnerResearchCommand
import at.heinz.deviceai.cognitive.WebToolRequest
import at.heinz.deviceai.cognitive.WorkDecisionRecall
import at.heinz.deviceai.cognitive.WorkRecall
import at.heinz.deviceai.learning.ContinuumBrainCommand
import at.heinz.deviceai.learning.MentorAssociativeRecall
import at.heinz.deviceai.learning.MentorRecall
import at.heinz.deviceai.learning.MentorSemanticRecall
import at.heinz.deviceai.learning.NeuralDialogue
import at.heinz.deviceai.systemintelligence.SystemIntelligenceCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Code55 field bug D (Fold7, Code54, offline): „Meine Werkzeugkiste ist blau, steht im Keller und enthält zwölf
 * Schrauben.“ is a FACT STATEMENT about the owner's own object. The assistant answered that it had no tool to search
 * the toolbox. Every chat router that AgentActivity.sendCurrentInput consults before the model is checked here with
 * the real classes; the owner object-state memory (OwnerObjectStateLedger via LocalConversationContext) must hold
 * colour, place and count, answer the recall without a model turn and keep colour/place through a 12 → 17 → 21
 * correction chain. The owner has not confirmed a device result for the recall question yet (Fold7: NOT_RUN).
 */
class OwnerObjectStateFieldStatementTest {
    private val session = "11111111-2222-3333-4444-555555555555"
    private val statement = "Meine Werkzeugkiste ist blau, steht im Keller und enthält zwölf Schrauben."
    private val recall = "Welche Farbe hat meine Werkzeugkiste, wo steht sie und wie viele Schrauben sind darin?"
    private val firstCorrection = "Nicht zwölf, sondern siebzehn Schrauben."
    private val secondCorrection = "Meine Werkzeugkiste enthält jetzt einundzwanzig Schrauben."
    private val history = "Was hatte ich vorher gesagt?"

    /** The same pre-model routers, in the order of AgentActivity.sendCurrentInput; a non-null entry hijacks the turn. */
    private fun hijackingRoutes(text: String): Map<String, Any?> = linkedMapOf(
        "OwnerChatIntentRouter" to OwnerChatIntentRouter.route(text),
        "OwnerCorrectionIntent" to OwnerCorrectionIntent.parse(text),
        "ContinuumWorkerChatRouter" to ContinuumWorkerChatRouter.parse(text),
        "SystemIntelligence.cachePreview" to SystemIntelligenceCommands.isCachePreviewRequest(text).takeIf { it },
        "SystemIntelligence.diagnosis" to SystemIntelligenceCommands.isDiagnosisRequest(text).takeIf { it },
        "OwnerPreviousBatteryIntent" to OwnerPreviousBatteryIntent.isPreviousReadingRequest(text).takeIf { it },
        "WorkDecisionRecall" to WorkDecisionRecall.parse(text),
        "WorkRecall" to WorkRecall.parse(text),
        "OwnerDocumentIntent" to OwnerDocumentIntent.parse(text),
        "ContinuumBrainCommand" to ContinuumBrainCommand.parse(text),
        "GroqInvocationRouter" to GroqInvocationRouter.extractQuestion(text),
        "OwnerInternalNavigationIntent" to OwnerInternalNavigationIntent.resolve(text),
        "OwnerResearchCommand" to OwnerResearchCommand.parse(text),
        "OwnerAppLookupIntent" to OwnerAppLookupIntent.inspect(text),
        "NeuralDialogue" to NeuralDialogue.accepts(text).takeIf { it },
        "MentorSemanticRecall" to MentorSemanticRecall.topic(text),
        "MentorAssociativeRecall" to MentorAssociativeRecall.topic(text),
        "MentorRecall" to MentorRecall.topic(text),
        "CertifiedLocationReflex" to CertifiedLocationReflex.routeExplicit(text),
        "GroundedWebRecall" to GroundedWebRecall.topicOf(text),
        "WebToolRequest" to WebToolRequest.fromChat(text, false),
        "BootstrapBrain.requiresOwnerFacts" to BootstrapBrain.requiresOwnerFacts(text).takeIf { it }
    ).filterValues { it != null }

    private fun dialogue(vararg ownerTurns: String) = LocalConversationContext().apply {
        ownerTurns.forEach { record(session, it, "Verstanden.") }
    }

    @Test fun theFactStatementIsNeitherAToolSearchAppLookupNorABlockedRequest() {
        for (text in listOf(statement, firstCorrection, secondCorrection)) {
            assertEquals("pre-model routers must not take: $text", emptyMap<String, Any?>(), hijackingRoutes(text))
            assertFalse("not blocked: $text", OwnerDeviceRequestSafety.inspect(text).blocked)
            val decision = BootstrapBrain.decide(text, emptyList())
            assertEquals("ordinary dialogue, not a device tool: $text", BootstrapKind.REPLY, decision.kind)
            assertNull(text, decision.tool)
            assertTrue("reaches the local dialogue path: $text", decision.allowNeuralRecall)
            assertNull(text, LocalConversationContext().inspectRequest(session, text))
        }
    }

    @Test fun recallAndHistoryQuestionsAreNotHijackedBeforeTheObjectStateAnswer() {
        for (text in listOf(recall, history)) {
            assertEquals("pre-model routers must not take: $text", emptyMap<String, Any?>(), hijackingRoutes(text))
            assertFalse(text, OwnerDeviceRequestSafety.inspect(text).blocked)
            assertNull(text, LocalConversationContext().inspectRequest(session, text))
        }
    }

    @Test fun theStatementIsCapturedInTheExistingObjectStateMemory() {
        val context = LocalConversationContext()
        // The model prompt of the statement turn is built before the turn is recorded: it must already carry the state.
        val turnBlock = context.also { it.record(session, "Hallo", "Hallo!") }.objectStateBlock(session, statement)
        assertTrue(turnBlock, turnBlock.contains("Werkzeugkiste: Farbe blau · Ort im Keller · Anzahl Schrauben 12"))
        assertTrue("a plain statement needs no tool or search: $turnBlock",
            turnBlock.contains("kein Werkzeug") && turnBlock.contains("keine Suche"))
        context.record(session, statement, "Verstanden.")
        val block = context.objectStateBlock(session, "Wo steht meine Werkzeugkiste?")
        assertTrue(block, block.contains("Werkzeugkiste: Farbe blau · Ort im Keller · Anzahl Schrauben 12"))
    }

    @Test fun theRecallIsAnsweredFromMemoryWithoutAModelTurn() {
        val answer = dialogue(statement).objectStateAnswer(session, recall)
        assertNotNull("deterministic recall of the owner's own statement", answer)
        answer!!
        assertTrue(answer, answer.startsWith("Laut deinen Angaben in diesem Gespräch – Werkzeugkiste"))
        assertTrue(answer, answer.contains("Farbe: blau"))
        assertTrue(answer, answer.contains("Ort: im Keller"))
        assertTrue(answer, answer.contains("Anzahl Schrauben: 12"))
        assertFalse(answer, answer.contains("Zugriff"))
        assertFalse(answer, answer.contains("Werkzeug,"))
    }

    @Test fun theCorrectionChainKeepsColourAndPlace() {
        val context = dialogue(statement, firstCorrection)
        val afterFirst = context.objectStateAnswer(session, recall)!!
        assertTrue(afterFirst, afterFirst.contains("Anzahl Schrauben: 17"))
        assertTrue(afterFirst, afterFirst.contains("Farbe: blau"))
        assertTrue(afterFirst, afterFirst.contains("Ort: im Keller"))
        context.record(session, secondCorrection, "Verstanden.")
        val afterSecond = context.objectStateAnswer(session, recall)!!
        assertTrue(afterSecond, afterSecond.contains("Farbe: blau"))
        assertTrue(afterSecond, afterSecond.contains("Ort: im Keller"))
        assertTrue(afterSecond, afterSecond.contains("Anzahl Schrauben: 21"))
        assertTrue(afterSecond, afterSecond.contains("Anzahl Schrauben 12 → 17 → 21"))
        assertFalse("colour and place were never corrected: $afterSecond", afterSecond.contains("Farbe blau →"))
        assertFalse(afterSecond, afterSecond.contains("im Keller →"))
    }

    @Test fun whatDidISayBeforeUsesTheCorrectionHistory() {
        val answer = dialogue(statement, firstCorrection, secondCorrection).objectStateAnswer(session, history)
        assertNotNull("„Was hatte ich vorher gesagt?“ is answered from the owner's own statements", answer)
        answer!!
        assertTrue(answer, answer.contains("Anzahl Schrauben: 12 → 17 → 21"))
        assertTrue(answer, answer.contains("aktuell 21"))
        assertTrue(answer, answer.contains("Farbe: blau"))
        assertTrue(answer, answer.contains("Ort: im Keller"))
        assertFalse(answer, answer.contains("keine frühere Angabe"))
        // „vorher“ for one attribute is the value before the current one, like the existing „zuvor“.
        val previous = dialogue(statement, firstCorrection, secondCorrection)
            .objectStateAnswer(session, "Wie viele Schrauben waren es vorher?")!!
        assertTrue(previous, previous.contains("Anzahl Schrauben: 17 (vor der aktuellen Angabe 21)"))
    }

    @Test fun theStatementSurvivesTheFourTurnDialogueWindow() {
        val context = dialogue(statement, "Wie spät ist es?", "Erzähl mir einen kurzen Witz.",
            "Was ist die Hauptstadt von Frankreich?", "Erkläre mir kurz die Photosynthese.")
        assertFalse(context.sessionEvidence(session)!!.contains(statement))
        assertTrue(context.objectStateAnswer(session, recall)!!.contains("Anzahl Schrauben: 12"))
    }

    @Test fun neighbouringSubjectFormsStayConservative() {
        // Another person's object, a reported statement and a hypothesis never become owner object state.
        for (text in listOf(
            "Mein Nachbar hat eine rote Werkzeugkiste mit neun Schrauben im Garten.",
            "Meine Schwester hat ihre Werkzeugkiste rot gestrichen.",
            "Wenn meine Werkzeugkiste rot wäre, hätte sie neun Schrauben."
        )) {
            val context = dialogue(statement, text)
            val answer = context.objectStateAnswer(session, recall)!!
            assertTrue("$text → $answer", answer.contains("Farbe: blau"))
            assertTrue("$text → $answer", answer.contains("Anzahl Schrauben: 12"))
            assertTrue("$text → $answer", answer.contains("Ort: im Keller"))
        }
        // An unsupported change of the stated object makes the state unclear instead of resurrecting it.
        assertNull(dialogue(statement, "Meine Werkzeugkiste ist leer.").objectStateAnswer(session, recall))
        // The owner's own object stated with a colour adjective in front of the noun.
        val adjective = dialogue("Meine graue Box steht im Büro und enthält vier Kabel.")
            .objectStateAnswer(session, "Welche Farbe hat die Box, wo steht sie und wie viele Kabel sind darin?")!!
        assertTrue(adjective, adjective.contains("Farbe: grau"))
        assertTrue(adjective, adjective.contains("Ort: im Büro"))
        assertTrue(adjective, adjective.contains("Anzahl Kabel: 4"))
    }
}
