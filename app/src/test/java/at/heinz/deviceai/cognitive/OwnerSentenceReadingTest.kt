package at.heinz.deviceai.cognitive

import at.heinz.deviceai.cognitive.OwnerSentenceReading.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner's sentence read clause by clause ([OwnerMissionPlanner.read]): the typed result the planner, the check of
 * a model plan and the honest partial answer share. The five sentences of the architecture review of 19.11.2026 and
 * the owner's goal sentence are pinned here with what the planner makes of them today.
 */
class OwnerSentenceReadingTest {
    private fun read(text: String) = checkNotNull(OwnerMissionPlanner.read(text, "Jarvis")) { text }

    private val goal = "Schau bitte meine E-Mails durch, fasse mir das Wichtigste zusammen, mach daraus ein PDF und schick meinem " +
        "Vater auf WhatsApp, dass ich mich später melde."
    private val two = "Öffne bitte WhatsApp und schreib meinem Vater guten Morgen."
    private val three = "Suche die letzte Mail von meinem Anwalt, fasse sie zusammen, erstelle daraus ein PDF und öffne es."
    private val four = "Schau, welche Termine ich diese Woche habe, und sag mir, ob ich etwas vergessen könnte."
    private val five = "Such die PDF-Datei Argon4, öffne sie, analysiere Seite zwei und fasse sie zusammen."

    // ── The six sentences ───────────────────────────────────────────────────────────────────────────────────

    /** Second phase (19.11.2026): mails, the summary of them and a message on WhatsApp are steps – the sentence is one sequence. */
    @Test fun theGoalSentenceIsOneSequenceOfFourStepsThatWorkOnEachOthersResults() {
        val reading = read(goal)
        assertEquals(Kind.PLAN, reading.kind)
        val plan = checkNotNull(reading.plan)
        assertEquals(listOf("MAILFIND:INBOX", "MAILSUM", "EXPORT:PDF", "SEND:WHATSAPP:meinem Vater"), plan.codes)
        // Clause by clause as the owner said it: „fasse mir das Wichtigste zusammen“ only refers; the plan binds it.
        assertEquals(listOf("MAILFIND:INBOX", "SUM_IT", "EXPORT:PDF", "SEND:WHATSAPP:meinem Vater"), reading.codes)
        assertEquals(listOf("Schau bitte meine E-Mails durch", "fasse mir das Wichtigste zusammen", "mach daraus ein PDF",
            "schick meinem Vater auf WhatsApp, dass ich mich später melde"), reading.clauses.map { it.text })
        assertEquals(listOf("Schau", "fasse", "mach", "schick"), reading.clauses.map { it.verb?.word })
        assertTrue(reading.open.isEmpty() && reading.rest.isEmpty())
        // Typed: the summary works on the mails of step 1, the PDF on the summary of step 2 – never on „the last answer
        // of the chat“. The message works on nothing: it carries the owner's words, and no result is attached.
        assertEquals(listOf(null, 0, 1, null), plan.steps.map { it.source })
        assertEquals(listOf(null, OwnerStepSlot.MAILS, OwnerStepSlot.DELIVERABLE, null), plan.steps.map { it.needs })
        // The message text is free text of the send order: it is the owner's wording and no order of its own.
        val send = reading.clauses.last()
        assertEquals("dass ich mich später melde", send.payloadText)
        assertEquals("schick meinem Vater auf WhatsApp, ", send.command)
        assertEquals("schick meinem Vater auf WhatsApp, dass ich mich später melde", plan.steps.last().order)
    }

    /** „Öffne WhatsApp und schreib …“: two orders, one step – the send lane opens the chat itself. */
    @Test fun openWhatsAppAndWriteIsOneSendStep() {
        val reading = read(two)
        assertEquals(Kind.PLAN, reading.kind)
        assertEquals(listOf("SEND:WHATSAPP:meinem Vater"), reading.codes)
        assertEquals(listOf("Öffne bitte WhatsApp und schreib meinem Vater guten Morgen"), reading.clauses.map { it.text })
        val step = reading.plan!!.steps.single() as OwnerMissionStep.SendChat
        assertTrue(step.channelFromOpening)
        assertEquals("schreib meinem Vater guten Morgen", step.order)
        // The computer lane no longer ends the turn with „Der zusätzliche App-Schritt wird noch nicht sicher verstanden“.
        assertEquals(OwnerComputerIntentParser.Result.NotComputerCommand, OwnerComputerIntentParser.parse(two))
        // Another app is opened as a step; writing there without a channel the send lane has is still no step.
        val facebook = read("Öffne Facebook und schreib Elena einen Geburtstagsgruß")
        assertEquals(Kind.PARTIAL, facebook.kind)
        assertEquals(listOf("APP:FACEBOOK", "LATER:SEND"), facebook.codes)
        assertEquals(listOf("APP:FACEBOOK"), facebook.rest.map { it.code })
        // A search inside another app stays the computer lane's whole order.
        assertNull(read("Öffne WhatsApp und suche Anna").plan)
    }

    @Test fun theLawyersLastMailIsSearchedSummarisedSavedAndTheSavedFileOpened() {
        val reading = read(three)
        assertEquals(Kind.PLAN, reading.kind)
        assertEquals(listOf("MAILFIND:LATEST:Anwalt", "SUM_IT", "EXPORT:PDF", "OPEN_IT"), reading.codes)
        val plan = reading.plan!!
        assertEquals(listOf("MAILFIND:LATEST:Anwalt", "MAILSUM", "EXPORT:PDF", "OPEN_SAVED"), plan.codes)
        assertEquals("die letzte Mail von meinem Anwalt suchen", plan.steps.first().label)
        assertEquals(listOf(null, 0, 1, 2), plan.steps.map { it.source })
        assertEquals(OwnerStepSlot.SAVED_FILE, plan.steps.last().needs)
    }

    @Test fun theWeekQuestionIsOneOrderOfTheBriefingLane() {
        val reading = read(four)
        assertEquals(Kind.NONE, reading.kind)
        assertEquals("BRIEFING", reading.whole)
        assertNull(OwnerMissionPlanner.plan(four, "Jarvis"))
        assertEquals("AGENDA_CHECK:THIS_WEEK", at.heinz.deviceai.briefing.ContinuumBriefingCommands.parse(four, "Jarvis")?.code)
    }

    @Test fun findOpenAndSummariseOnePageIsACompletePlanWithoutASave() {
        val reading = read(five)
        assertEquals(Kind.PLAN, reading.kind)
        val plan = checkNotNull(reading.plan)
        assertEquals(listOf("OPEN:Argon4", "PAGESUM:2"), plan.codes)
        assertEquals(listOf("Öffne das PDF Argon4", "analysiere Seite zwei und fasse sie zusammen"), plan.steps.map { it.order })
        assertEquals(listOf(null, 0), plan.steps.map { it.source })
        assertEquals("Ich mache das in zwei Schritten: 1. Das PDF „Argon4“ suchen und öffnen. 2. Seite 2 zusammenfassen.", plan.announcement())
        // No step saves anything.
        assertTrue(plan.steps.none { it is OwnerMissionStep.PageSave || it is OwnerMissionStep.Export })
        assertEquals(listOf("FIND:Argon4", "OPEN_IT", "PAGESUM:2"), reading.codes)
        assertTrue(reading.open.isEmpty())
    }

    // ── The typed result ────────────────────────────────────────────────────────────────────────────────────

    @Test fun everyClauseCarriesItsRangeItsVerbItsFreeTextAndWhatRecognisedIt() {
        val sentence = "Fass mir die Nachrichten von heute zusammen und schreib Anna auf WhatsApp, dass ich nicht komme"
        val reading = read(sentence)
        val (news, send) = reading.clauses
        assertEquals("Fass mir die Nachrichten von heute zusammen", reading.said.substring(news.range.first, news.range.last + 1))
        assertEquals(OwnerVerbLexicon.Family.SUMMARISE, news.verb?.family)
        assertNull(news.payload)
        assertTrue(news.reading is OwnerClauseReading.Step && news.step is OwnerMissionStep.News)
        assertEquals("schreib Anna auf WhatsApp, dass ich nicht komme", reading.said.substring(send.range.first, send.range.last + 1))
        assertEquals(OwnerVerbLexicon.Family.WRITE, send.verb?.family)
        assertEquals("dass ich nicht komme", send.payloadText)
        assertEquals("Anna", (send.step as OwnerMissionStep.SendChat).recipient)
        // „nicht“ stands in the message, not in a command: the sentence is a sequence, and the message is a step of it.
        assertFalse(reading.vetoed)
        assertEquals(Kind.PLAN, reading.kind)
        assertEquals(listOf("NEWS", "SEND:WHATSAPP:Anna"), OwnerMissionPlanner.plan(sentence, "Jarvis")?.codes)
        // An order that is no step keeps the honest partial answer: a call.
        val call = read("Fass mir die Nachrichten von heute zusammen und ruf Anna an")
        assertEquals(Kind.PARTIAL, call.kind)
        assertEquals(listOf("NEWS"), call.rest.map { it.code })
        assertEquals("Verstanden – ganz kann ich das noch nicht erledigen. Einen Anruf kann ich in so einer Auftragsfolge noch nicht " +
            "vorbereiten. Soll ich den Rest machen: Die Nachrichten von heute zusammenfassen?", OwnerMissionPartial.text(call.open, call.rest))
    }

    @Test fun aNegationAConditionOrAnAlternativeRulesOutOnlyInACommand() {
        for (sentence in listOf(
            "Öffne das PDF Argon4 nicht und schreib Anna auf WhatsApp, dass ich komme",
            "Öffne das PDF Argon4 oder schreib Anna auf WhatsApp, dass ich es nicht finde",
            "Wenn Anna schreibt, öffne das PDF Argon4 und schick ihr eine Nachricht auf WhatsApp",
            "Öffne das PDF Argon4 ohne Anhang und schreib Anna auf WhatsApp, dass es da ist",
            "Öffne kein PDF und schreib Anna auf WhatsApp, dass ich komme")) {
            val reading = read(sentence)
            assertTrue(sentence, reading.vetoed)
            assertEquals(sentence, Kind.NONE, reading.kind)
        }
        // The same words inside the free text of a message – after „dass“, after a colon, inside quotes – rule nothing out.
        for (sentence in listOf(
            "Öffne das PDF Argon4 und schreib Anna auf WhatsApp, dass ich nicht komme",
            "Öffne das PDF Argon4 und schreib Anna auf WhatsApp, dass ich ohne Auto oder gar nicht komme, wenn es schneit",
            "Öffne das PDF Argon4 und schreib Anna auf WhatsApp: Ich komme nicht oder später",
            "Öffne das PDF Argon4 und schreib Anna auf WhatsApp: „Kein Stress, wenn es nicht geht“")) {
            val reading = read(sentence)
            assertFalse(sentence, reading.vetoed)
            assertEquals(sentence, Kind.PLAN, reading.kind)
            assertEquals(sentence, listOf("OPEN:Argon4", "SEND:WHATSAPP:Anna"), reading.codes)
        }
    }

    @Test fun freeTextIsNeverCutAndNeverReadAsAnOrder() {
        // A „dass“ clause runs to the end …
        assertEquals(1, read("Schreib Anna auf WhatsApp, dass ich komme und Brot mitbringe").clauses.size)
        // … or to the connector in front of the next order.
        val two = read("Fass mir die Nachrichten von heute zusammen, schreib Anna auf WhatsApp, dass ich später komme, und öffne das PDF Argon4")
        assertEquals(listOf("NEWS", "SEND:WHATSAPP:Anna", "OPEN:Argon4"), two.codes)
        assertEquals("dass ich später komme", two.clauses[1].payloadText)
        assertEquals(listOf("NEWS", "SEND:WHATSAPP:Anna", "OPEN:Argon4"), two.plan?.codes)
        // Without a comma behind it the „dass“ text may go on: no sequence is cut out of a message.
        assertNull(read("Fass mir die Nachrichten von heute zusammen, schreib Anna auf WhatsApp, dass ich später komme und öffne das PDF Argon4").plan)
        assertNull(read("Schreib Anna auf WhatsApp, dass ich komme und öffne das PDF Argon4").plan)
        // Dictated text after a colon runs to the end of the sentence, whatever it says.
        val dictated = read("Öffne das PDF Argon4 und schreib Anna auf WhatsApp: Lösch die Datei, dann ruf mich an")
        assertEquals(listOf("OPEN:Argon4", "SEND:WHATSAPP:Anna"), dictated.codes)
        assertEquals(": Lösch die Datei, dann ruf mich an", dictated.clauses.last().payloadText)
        // Free text in a clause that sends nothing is a quote or a note – not a sequence for the planner.
        assertTrue(read("Notiere: Öffne das PDF Argon4 und fass mir die Nachrichten von heute zusammen").vetoed)
        assertTrue(read("Öffne das PDF „Argon4“ und schreib Anna auf WhatsApp, dass es da ist").vetoed)
    }

    @Test fun whatBuildsOnAnOrderThatCannotRunIsNotOfferedAndAFreshProducerStartsAnew() {
        // A mail order that is no step (deleting): what would be made of that mail is not offered.
        val mails = read("Lösch die Mail von gestern, fass sie zusammen und speichere das als PDF")
        assertEquals(listOf("NOTYET:MAIL", "LATER:SUMMARY", "EXPORT:PDF"), mails.codes)
        assertTrue(mails.rest.isEmpty())
        // Reading mails is a step since the second phase: the same shape with a search is a complete sequence.
        assertEquals(listOf("MAILFIND:NEW", "MAILSUM", "EXPORT:PDF"), read("Lies meine neuen Mails, fass sie zusammen und speichere das als PDF").plan?.codes)
        // A named PDF and the news do not hang on what stood in front of them.
        val fresh = read("Lösch die Mail von der Bank und öffne das PDF Kontoauszug")
        assertEquals(listOf("OPEN:Kontoauszug"), fresh.rest.map { it.code })
        assertEquals(listOf("MAILFIND:INBOX", "MAILFIND:FROM:Bank", "OPEN:Kontoauszug"),
            read("Schau in meinen Posteingang, such die Mail von der Bank und öffne das PDF Kontoauszug").plan?.codes)
        val between = read("Öffne das PDF Argon4, druck es aus und fass mir die Nachrichten von heute zusammen")
        assertEquals(listOf("OPEN:Argon4", "UNKNOWN", "NEWS"), between.codes)
        assertEquals(listOf("OPEN:Argon4", "NEWS"), between.rest.map { it.code })
        assertEquals("Verstanden – ganz kann ich das noch nicht erledigen. Für „druck es aus“ habe ich in einer Auftragsfolge noch keinen festen Ablauf. " +
            "Soll ich den Rest so machen: 1. Das PDF „Argon4“ suchen und öffnen. 2. Die Nachrichten von heute zusammenfassen?",
            OwnerMissionPartial.text(between.open, between.rest))
        // A message in the owner's own words produces nothing a later „sie“ could mean: the save still means the news.
        val through = read("Fass mir die Nachrichten von heute zusammen, schreib Anna auf WhatsApp, dass ich komme, und speichere sie als PDF")
        assertEquals(listOf("NEWS", "SEND:WHATSAPP:Anna", "EXPORT:PDF"), through.plan?.codes)
        assertEquals(0, through.plan?.steps?.last()?.source)
        // The chat's own result can still be saved in front of a message.
        assertEquals(listOf("EXPORT:PDF", "SEND:WHATSAPP:meinem Chef"),
            read("Speichere das als PDF und schick meinem Chef auf WhatsApp, dass es im Download-Ordner liegt").plan?.codes)
        // A call produces nothing either, and it is no step: the save behind it is still offered with the news.
        assertEquals(listOf("NEWS", "EXPORT:PDF"), read("Fass mir die Nachrichten von heute zusammen, ruf Anna an und speichere sie als PDF").rest.map { it.code })
    }

    @Test fun aSentenceOneLaneTakesWholeTalkAndASingleOrderKeepTheirRoute() {
        for ((sentence, lane) in listOf(
            "Schreib Anna auf WhatsApp, dass ich später komme und Brot mitbringe" to "SEND",
            "Öffne WhatsApp und suche Anna" to "COMPUTER",
            "Fass Seite 2 zusammen und speichere es als Word" to "DESK",
            "Such das PDF Argon4 und öffne es" to "DESK",
            "Fass mir die Nachrichten von heute zusammen" to "WORK",
            four to "BRIEFING")) {
            val reading = read(sentence)
            assertEquals(sentence, lane, reading.whole)
            assertEquals(sentence, Kind.NONE, reading.kind)
        }
        for (sentence in listOf("Erzähl mir einen Witz und sag mir, wie es dir geht", "Erklär mir Photosynthese und schreib ein Gedicht darüber",
            "Öffne das PDF Argon4", "Wie spät ist es?", "Öffne das PDF Xenon5 und speichere es als PDF"))
            assertEquals(sentence, Kind.NONE, read(sentence).kind)
        // A complete plan is a plan, whatever else could be said about its clauses.
        assertEquals(Kind.PLAN, read("Öffne das PDF Argon4, fass Seite 2 zusammen, speichere es als Word und schick es per Mail an anna@example.org").kind)
    }

    /**
     * „… und zeige mir jeden Arbeitsschritt“ behind a document order is how the document route shows its work
     * ([OwnerDocumentIntent] takes it off the sentence itself and opens Android's picker). The planner used to read it
     * as an order nobody knows and answered „ganz kann ich das noch nicht erledigen“ instead of opening the picker
     * (ContinuumPdfSceneAcceptanceUiTest, 19.11.2026).
     */
    @Test fun thePresentationTailOfADocumentOrderIsNoSecondOrder() {
        for (sentence in listOf(
            "öffne meine PDF Argon 4 Forschungsexperiment und zeige mir jeden Arbeitsschritt",
            "Öffne diese PDF und zeig mir alle Arbeitsschritte.",
            "Lies diese PDF, zeige mir jeden einzelnen Arbeitsschritt bitte")) {
            val reading = read(sentence)
            assertNotNull(sentence, OwnerDocumentIntent.parse(sentence, "Jarvis"))
            assertEquals(sentence, "DOCUMENT", reading.whole)
            assertEquals(sentence, Kind.NONE, reading.kind)
        }
        // Only that tail, and only behind an order the document route takes: any other second order is still read
        // as an order, so nothing is dropped without a word.
        val printed = read("Öffne das PDF Argon4, druck es aus und zeige mir jeden Arbeitsschritt")
        assertNull(printed.whole)
        assertEquals(Kind.PARTIAL, printed.kind)
        val news = read("Fass mir die Nachrichten von heute zusammen und zeige mir jeden Arbeitsschritt")
        assertFalse("DOCUMENT" == news.whole)
        assertTrue(OwnerDocumentIntent.isPresentationOnly("zeige mir jeden Arbeitsschritt"))
        assertFalse(OwnerDocumentIntent.isPresentationOnly("zeige mir jeden Arbeitsschritt und lösche die Datei"))
        assertFalse(OwnerDocumentIntent.isPresentationOnly("zeige mir die Datei"))
    }

    /**
     * The partial answer is for a sentence that would otherwise end in a free model turn or a dead end – never for one
     * a lane later in the route answers itself. „Öffne Signal und schreibe Lisa eine Nachricht“ is the app lane's:
     * Signal is no app of the computer lane and no channel of the send lane, and the app lane asks which message for
     * whom (OwnerAppPlanningContextUiTest.planningClarificationKeepsTheCompleteOriginalOnce, 19.11.2026).
     */
    @Test fun aSentenceTheAppLaneAnswersItselfKeepsItsRoute() {
        for (sentence in listOf("Öffne Signal und schreibe Lisa eine Nachricht.", "Starte Threema und schick Lisa eine Nachricht")) {
            val reading = read(sentence)
            assertEquals(sentence, "APP", reading.whole)
            assertEquals(sentence, Kind.NONE, reading.kind)
            assertNull(sentence, reading.plan)
            assertFalse(sentence, OwnerComputerIntentParser.handsOn(sentence))
        }
        // A sentence the computer lane handed on used to be that lane's refusal, a dead end: it is read here.
        for (sentence in listOf("Öffne Facebook und schreib Elena einen Geburtstagsgruß", "Suche in WhatsApp nach Anna und schreib ihr Hallo",
            "Öffne Word und bearbeite den Vertrag")) {
            assertTrue(sentence, OwnerComputerIntentParser.handsOn(sentence))
            assertFalse(sentence, read(sentence).whole == "APP")
        }
        assertEquals(Kind.PARTIAL, read("Öffne Facebook und schreib Elena einen Geburtstagsgruß").kind)
        // The app lane's hand-over to the model is no answer of its own: such a sentence may still be read here.
        assertNull(read("Öffne Spotify und fass mir die Nachrichten von heute zusammen").whole)
        // An order of the computer lane itself is not „handed on“.
        for (sentence in listOf("Öffne WhatsApp", "Öffne WhatsApp und suche Anna", "Wie spät ist es?"))
            assertFalse(sentence, OwnerComputerIntentParser.handsOn(sentence))
    }

    /**
     * „Schreibe Lisa eine Nachricht“ names no app, so the send lane does not take it and the computer lane would ask
     * „In welcher App …?“. Every answer of the computer lane used to be read as „another app“: the owner who wanted a
     * message written was told about searching in another app, and the message was not mentioned at all.
     */
    @Test fun aMessageOrderOfTheComputerLaneIsAnOrderToWriteNotAnotherApp() {
        val reading = read("Fass mir die Nachrichten von heute zusammen und schreibe Lisa eine Nachricht")
        assertEquals(Kind.PARTIAL, reading.kind)
        assertEquals(listOf("NEWS", "LATER:SEND"), reading.codes)
        val said = OwnerMissionPartial.text(reading.open, reading.rest)
        assertTrue(said, said.contains(OwnerClauseAbility.SEND.cannot))
        assertFalse(said, said.contains(OwnerClauseAbility.APP.cannot))
        for (order in listOf("Schreibe Lisa eine Nachricht", "bitte sende eine Nachricht an Lisa", "Bereite eine Nachricht an Anna vor"))
            assertTrue(order, OwnerComputerIntentParser.isMessageOrder(order))
        for (order in listOf("Öffne WhatsApp", "Suche in WhatsApp nach Anna", "Öffne WhatsApp und suche Anna"))
            assertFalse(order, OwnerComputerIntentParser.isMessageOrder(order))
    }

    @Test fun inputOutsideTheBoundsIsNotRead() {
        assertNull(OwnerMissionPlanner.read("", "Jarvis"))
        assertNull(OwnerMissionPlanner.read("Öffne das PDF Argon4,\u0000 schreib Anna", "Jarvis"))
        assertNull(OwnerMissionPlanner.read("Öffne das PDF Argon4 und " + "x".repeat(OwnerMissionPlanner.MAX_CHARS), "Jarvis"))
        assertNotNull(OwnerMissionPlanner.read("Öffne Argon", "Jarvis"))
        // Too many clauses: read as one, and never a sequence.
        val many = read("Öffne das PDF Argon4 und " + "ruf Anna an und ".repeat(14) + "öffne das PDF Xenon5")
        assertTrue(many.vetoed)
        assertEquals(Kind.NONE, many.kind)
    }
}
