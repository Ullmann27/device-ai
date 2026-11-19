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

    @Test fun theGoalSentenceIsFourOrdersOfWhichNoneCanRunYetAndNothingIsOffered() {
        val reading = read(goal)
        assertEquals(Kind.PARTIAL, reading.kind)
        assertNull(reading.plan)
        assertEquals(listOf("NOTYET:MAIL", "LATER:SUMMARY", "EXPORT:PDF", "LATER:SEND"), reading.codes)
        assertEquals(listOf("Schau bitte meine E-Mails durch", "fasse mir das Wichtigste zusammen", "mach daraus ein PDF",
            "schick meinem Vater auf WhatsApp, dass ich mich später melde"), reading.clauses.map { it.text })
        assertEquals(listOf("Schau", "fasse", "mach", "schick"), reading.clauses.map { it.verb?.word })
        // The summary and the PDF would be made of mails nobody can read: they are not offered – no PDF of the last
        // chat answer, which is what used to be saved and reported as done.
        assertEquals(listOf(false, true, true, false), reading.clauses.map { it.dependsOnOpen })
        assertTrue(reading.rest.isEmpty())
        assertEquals(4, reading.open.size)
        // The message text is free text of the send order: it is the owner's wording and no order of its own.
        val send = reading.clauses.last()
        assertEquals("dass ich mich später melde", send.payloadText)
        assertEquals("schick meinem Vater auf WhatsApp, ", send.command)
        val said = OwnerMissionPartial.text(reading.open, reading.rest)
        assertEquals("Verstanden – ganz kann ich das noch nicht erledigen. Deine E-Mails kann ich noch nicht lesen. Was darauf aufbaut, " +
            "lasse ich deshalb auch: zusammenfassen und als PDF speichern. Eine Nachricht kann ich in so einer Auftragsfolge noch " +
            "nicht mitschicken. Ich habe nichts davon ausgeführt.", said)
        assertFalse(said.contains("einzeln", ignoreCase = true))
        assertFalse(said.contains("Erledigt"))
    }

    @Test fun openAnAppAndWriteAreTwoOrdersThatAreNoStepsYet() {
        val reading = read(two)
        assertEquals(Kind.PARTIAL, reading.kind)
        assertEquals(listOf("LATER:APP", "LATER:SEND"), reading.codes)
        assertEquals(listOf("Öffne bitte WhatsApp", "schreib meinem Vater guten Morgen"), reading.clauses.map { it.text })
        assertTrue(reading.rest.isEmpty())
        // The computer lane no longer ends the turn with „Der zusätzliche App-Schritt wird noch nicht sicher verstanden“.
        assertEquals(OwnerComputerIntentParser.Result.NotComputerCommand, OwnerComputerIntentParser.parse(two))
        assertEquals("Verstanden – ganz kann ich das noch nicht erledigen. Eine andere App öffnen oder darin suchen kann ich in so " +
            "einer Auftragsfolge noch nicht. Eine Nachricht kann ich in so einer Auftragsfolge noch nicht mitschicken. Ich habe " +
            "nichts davon ausgeführt.", OwnerMissionPartial.text(reading.open, reading.rest))
    }

    @Test fun theLawyersMailCannotBeReadSoNothingIsMadeOfIt() {
        val reading = read(three)
        assertEquals(Kind.PARTIAL, reading.kind)
        assertEquals(listOf("NOTYET:MAIL", "LATER:SUMMARY", "EXPORT:PDF", "OPEN_IT"), reading.codes)
        assertEquals(listOf(false, true, true, true), reading.clauses.map { it.dependsOnOpen })
        assertTrue(reading.rest.isEmpty())
        assertEquals("Verstanden – ganz kann ich das noch nicht erledigen. Deine E-Mails kann ich noch nicht lesen. Was darauf aufbaut, " +
            "lasse ich deshalb auch: zusammenfassen, als PDF speichern und öffnen. Ich habe nichts davon ausgeführt.",
            OwnerMissionPartial.text(reading.open, reading.rest))
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
        assertEquals(OwnerClauseReading.Later(OwnerClauseAbility.SEND, transparent = true), send.reading)
        // „nicht“ stands in the message, not in a command: the sentence is read, and the rest is offered.
        assertFalse(reading.vetoed)
        assertEquals(Kind.PARTIAL, reading.kind)
        assertEquals(listOf("NEWS"), reading.rest.map { it.code })
        assertEquals("Verstanden – ganz kann ich das noch nicht erledigen. Eine Nachricht kann ich in so einer Auftragsfolge noch nicht " +
            "mitschicken. Soll ich den Rest machen: Die Nachrichten von heute zusammenfassen?", OwnerMissionPartial.text(reading.open, reading.rest))
        // A complete plan is still something else: the send clause is no step yet.
        assertNull(OwnerMissionPlanner.plan(sentence, "Jarvis"))
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
            assertEquals(sentence, Kind.PARTIAL, reading.kind)
            assertEquals(sentence, listOf("OPEN:Argon4", "LATER:SEND"), reading.codes)
            assertEquals(sentence, listOf("OPEN:Argon4"), reading.rest.map { it.code })
        }
    }

    @Test fun freeTextIsNeverCutAndNeverReadAsAnOrder() {
        // A „dass“ clause runs to the end …
        assertEquals(1, read("Schreib Anna auf WhatsApp, dass ich komme und Brot mitbringe").clauses.size)
        // … or to the connector in front of the next order.
        val two = read("Fass mir die Nachrichten von heute zusammen, schreib Anna auf WhatsApp, dass ich später komme, und öffne das PDF Argon4")
        assertEquals(listOf("NEWS", "LATER:SEND", "OPEN:Argon4"), two.codes)
        assertEquals("dass ich später komme", two.clauses[1].payloadText)
        assertEquals(listOf("NEWS", "OPEN:Argon4"), two.rest.map { it.code })
        // Dictated text after a colon runs to the end of the sentence, whatever it says.
        val dictated = read("Öffne das PDF Argon4 und schreib Anna auf WhatsApp: Lösch die Datei, dann ruf mich an")
        assertEquals(listOf("OPEN:Argon4", "LATER:SEND"), dictated.codes)
        assertEquals(": Lösch die Datei, dann ruf mich an", dictated.clauses.last().payloadText)
        // Free text in a clause that sends nothing is a quote or a note – not a sequence for the planner.
        assertTrue(read("Notiere: Öffne das PDF Argon4 und fass mir die Nachrichten von heute zusammen").vetoed)
        assertTrue(read("Öffne das PDF „Argon4“ und schreib Anna auf WhatsApp, dass es da ist").vetoed)
    }

    @Test fun whatBuildsOnAnOrderThatCannotRunIsNotOfferedAndAFreshProducerStartsAnew() {
        val mails = read("Lies meine neuen Mails, fass sie zusammen und speichere das als PDF")
        assertEquals(listOf("NOTYET:MAIL", "LATER:SUMMARY", "EXPORT:PDF"), mails.codes)
        assertTrue(mails.rest.isEmpty())
        // A named PDF and the news do not hang on what stood in front of them.
        val fresh = read("Schau in meinen Posteingang, such die Mail von der Bank und öffne das PDF Kontoauszug")
        assertEquals(listOf("OPEN:Kontoauszug"), fresh.rest.map { it.code })
        val between = read("Öffne das PDF Argon4, druck es aus und fass mir die Nachrichten von heute zusammen")
        assertEquals(listOf("OPEN:Argon4", "UNKNOWN", "NEWS"), between.codes)
        assertEquals(listOf("OPEN:Argon4", "NEWS"), between.rest.map { it.code })
        assertEquals("Verstanden – ganz kann ich das noch nicht erledigen. Für „druck es aus“ habe ich in einer Auftragsfolge noch keinen festen Ablauf. " +
            "Soll ich den Rest so machen: 1. Das PDF „Argon4“ suchen und öffnen. 2. Die Nachrichten von heute zusammenfassen?",
            OwnerMissionPartial.text(between.open, between.rest))
        // A message in the owner's own words produces nothing a later „sie“ could mean: the save still means the news.
        val through = read("Fass mir die Nachrichten von heute zusammen, schreib Anna auf WhatsApp, dass ich komme, und speichere sie als PDF")
        assertEquals(listOf("NEWS", "EXPORT:PDF"), through.rest.map { it.code })
        // The chat's own result can still be saved when nothing in front of it is open.
        assertEquals(listOf("EXPORT:PDF"), read("Speichere das als PDF und schick meinem Chef auf WhatsApp, dass es im Download-Ordner liegt")
            .rest.map { it.code })
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
