package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class OwnerObjectScopeCorrectionTest {
    private val session = "object-correction"
    private val statement = "Meine Werkzeugkiste ist blau, steht im Keller und enthält zwölf Schrauben."
    private val question = "Welche Farbe hat meine Werkzeugkiste, wo steht sie und wie viele Schrauben sind darin?"

    private fun context() = LocalConversationContext().apply { record(session, statement, "Verstanden.") }

    @Test fun namedAttributeCorrectionPreservesOtherAttributesAndOriginalEvidence() {
        val c = context()
        val invalidations = mutableListOf<String>()
        c.onTurnsInvalidated = { invalidations += it }
        val correction = "Nein, ich meinte die Werkzeugkiste ist rot."
        assertNull(c.inspectRequest(session, correction))
        val preview = c.objectStateBlock(session, correction)
        assertTrue(preview, preview.contains("Farbe rot"))
        assertTrue(preview, preview.contains("Ort im Keller"))
        assertTrue(preview, preview.contains("Anzahl Schrauben 12"))
        assertTrue(c.objectStateAnswer(session, question)!!.contains("Farbe: blau"))
        c.record(session, correction, "Rot, verstanden.")
        val answer = c.objectStateAnswer(session, question)!!
        assertTrue(answer, answer.contains("Farbe: rot"))
        assertTrue(answer, answer.contains("Ort: im Keller"))
        assertTrue(answer, answer.contains("Anzahl Schrauben: 12"))
        assertEquals(listOf(statement, correction), c.sessionEvidence(session))
        assertTrue(invalidations.isEmpty())
    }

    @Test fun spokenCountCorrectionUsesExistingUniqueObject() {
        val c = context()
        val correction = "Ich meinte nicht zwölf, sondern siebzehn Schrauben."
        assertNull(c.inspectRequest(session, correction))
        c.recordUnanswered(session, correction)
        val answer = c.objectStateAnswer(session, question)!!
        assertTrue(answer, answer.contains("Anzahl Schrauben: 17"))
        assertTrue(answer, answer.contains("Farbe: blau"))
        assertTrue(answer, answer.contains("Ort: im Keller"))
    }

    @Test fun correctionWorksAfterTheOriginalTurnLeftThePromptWindow() {
        val c = context()
        repeat(5) { c.record(session, "Erkläre Thema Nummer $it.", "Antwort.") }
        val correction = "Gemeint war die Werkzeugkiste steht in der Garage."
        assertNull(c.inspectRequest(session, correction))
        c.record(session, correction, "Verstanden.")
        val answer = c.objectStateAnswer(session, question)!!
        assertTrue(answer, answer.contains("Ort: in der Garage"))
        assertTrue(answer, answer.contains("Farbe: blau"))
        assertTrue(answer, answer.contains("Anzahl Schrauben: 12"))
    }

    @Test fun exactStoredCorrectionRestoresWithoutInventingOwnerText() {
        val correction = "Nein, ich meinte die Werkzeugkiste ist rot."
        val c = LocalConversationContext()
        c.restore(session, LocalChatTranscriptStore.ContextTurn(statement, "Verstanden."))
        c.restore(session, LocalChatTranscriptStore.ContextTurn(correction, "Verstanden."))
        assertTrue(c.objectStateAnswer(session, question)!!.contains("Farbe: rot"))
        assertEquals(listOf(statement, correction), c.sessionEvidence(session))
    }

    @Test fun realTopicCorrectionStillInvalidatesObjectContext() {
        val c = context()
        assertEquals(ContextDirectiveKind.SCOPE_UPDATED,
            c.inspectRequest(session, "Ich meinte den Mond.")!!.kind)
        assertNull(c.objectStateAnswer(session, question))
    }

    @Test fun ambiguousObjectCorrectionStillRequiresClarification() {
        for (correction in listOf("Ich meinte die andere Werkzeugkiste.", "Ich meinte sie.")) {
            val c = context()
            assertEquals(correction, ContextDirectiveKind.CLARIFY_SCOPE,
                c.inspectRequest(session, correction)!!.kind)
            assertNull(c.objectStateAnswer(session, question))
        }
    }

    @Test fun ambiguousPronounNeverSelectsOneOfTwoObjects() {
        val c = context()
        c.record(session, "Meine Tasche ist grün, steht im Flur und enthält drei Ringe.", "Verstanden.")
        assertEquals(ContextDirectiveKind.CLARIFY_SCOPE,
            c.inspectRequest(session, "Ich meinte sie ist rot.")!!.kind)
        assertNull(c.objectStateAnswer(session, question))
    }

    @Test fun anotherSessionCannotSupplyTheCorrectionObject() {
        val c = context()
        assertNotNull(c.inspectRequest("other-session", "Ich meinte die Werkzeugkiste ist rot."))
        assertNull(c.objectStateAnswer("other-session", question))
    }

    @Test fun quotedQuestionAndConditionalBodiesDoNotChangeObjectState() {
        for (text in listOf(
            "Ich meinte: Ist die Werkzeugkiste rot?",
            "Ich meinte die Werkzeugkiste wäre rot.",
            "Ich meinte die Werkzeugkiste ist vielleicht rot.",
            "Ich meinte die Werkzeugkiste ist rot, wenn ich sie streiche.",
            "Ich meinte die Aussage „Die Werkzeugkiste ist rot“.",
            "Ich meinte öffne die rote Werkzeugkiste."
        )) {
            val ledger = OwnerObjectStateLedger().apply { observeOwnerTurn(statement) }
            ledger.observeOwnerTurn(text)
            val answer = ledger.answer(question)
            assertTrue(text + "\n" + answer, answer == null || !answer.contains("Farbe: rot"))
        }
    }

    @Test fun laterRetractionNeverBecomesAnObjectCorrection() {
        for (suffix in listOf("was nicht stimmt", "das war falsch", "aber ich weiß es nicht",
            "ich weiß das genau", "wie ich weiß", "denke ich")) {
            val text = "Ich meinte die Werkzeugkiste ist rot, $suffix."
            val ledger = OwnerObjectStateLedger().apply { observeOwnerTurn(statement) }
            assertFalse(text, ledger.isObjectCorrection(text))
            ledger.observeOwnerTurn(text)
            val answer = ledger.answer(question)
            assertTrue(text + "\n" + answer, answer == null || !answer.contains("Farbe: rot"))
            assertNotNull(text, context().inspectRequest(session, text))
        }
    }

    @Test fun literalWhiteRemainsAValidColorCorrection() {
        val c = context()
        val text = "Ich meinte die Werkzeugkiste ist weiß."
        assertNull(c.inspectRequest(session, text))
        c.record(session, text, "Verstanden.")
        assertTrue(c.objectStateAnswer(session, question)!!.contains("Farbe: weiß"))
    }

    @Test fun qualifiedCorrectionsRemainOutsideTheObjectLedger() {
        for (suffix in listOf("sofern ich sie streiche", "vorausgesetzt ich streiche sie",
            "es sei denn ich behalte die alte Farbe")) {
            val text = "Ich meinte die Werkzeugkiste ist rot, $suffix."
            val ledger = OwnerObjectStateLedger().apply { observeOwnerTurn(statement) }
            assertFalse(text, ledger.isObjectCorrection(text))
            ledger.observeOwnerTurn(text)
            val answer = ledger.answer(question)
            assertTrue(text + "\n" + answer, answer == null || !answer.contains("Farbe: rot"))
        }
    }

    @Test fun fold7OwnerMemoryExampleAndPronounQuestionKeepOneObject() {
        val c = LocalConversationContext()
        val introduction = "Merke dir für dieses Gespräch: Die blaue Werkzeugkiste steht in der Garage und enthält fünf Schraubenschlüssel."
        val question = "Welche Farbe hat sie und wo steht sie?"
        c.record(session, introduction, "Ich habe mir notiert: Die blaue Werkzeugkiste steht in der Garage und enthält fünf Schraubenschlüssel.")
        val answer = c.objectStateAnswer(session, question)
        assertNotNull("An explicitly session-scoped owner statement must be observable without model inference", answer)
        assertTrue(answer, answer!!.contains("Farbe: blau"))
        assertTrue(answer, answer.contains("Ort: in der Garage"))
    }

    @Test fun fold7CorrectionWithoutIchMeinteKeepsLocationAndCount() {
        val c = LocalConversationContext()
        c.record(session, "Merke dir für dieses Gespräch: Die blaue Werkzeugkiste steht in der Garage und enthält fünf Schraubenschlüssel.", "Verstanden.")
        val correction = "Nein, die Werkzeugkiste ist rot. Der Standort und die Anzahl bleiben gleich."
        assertNull(c.inspectRequest(session, correction))
        c.record(session, correction, "Die Werkzeugkiste ist nun rot.")
        val answer = c.objectStateAnswer(session, "Welche Farbe hat die Werkzeugkiste, wo steht sie und wie viele Schraubenschlüssel sind darin?")
        assertNotNull(answer)
        assertTrue(answer, answer!!.contains("Farbe: rot"))
        assertTrue(answer, answer.contains("Ort: in der Garage"))
        assertTrue(answer, answer.contains("Anzahl Schraubenschlüssel: 5"))
    }

}
