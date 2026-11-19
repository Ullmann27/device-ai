package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The check of a model-proposed mission plan: the model's text is data, every line must be an order a lane parser
 * takes, and nothing in it may be something the owner did not say. Whether an accepted plan runs is the owner's yes
 * (OwnerMissionFlowTest); here only what is accepted at all.
 */
class OwnerMissionModelPlanTest {
    private val goal = "Mach mir aus Seite 2 vom PDF Argon4 eine Word-Datei, die dann per Mail an anna@example.org geht"
    private val good = "1. Öffne das PDF Argon4\n2. Fass Seite 2 zusammen und speichere es als Word\n3. Schick das per Mail an anna@example.org"

    private fun check(text: String?, request: String = goal) = OwnerMissionModelPlan.check(request, text, "Jarvis")
    private fun rejected(text: String?, request: String = goal): String =
        (check(text, request) as? OwnerMissionModelPlan.Result.Rejected)?.reason ?: "ACCEPTED"

    @Test fun onlyACommandAboutSeveralLaneAbilitiesIsEverAskedOfTheModel() {
        assertTrue(OwnerMissionModelPlan.wants(goal, "Jarvis"))
        assertTrue(OwnerMissionModelPlan.wants("Mach aus den Nachrichten von heute ein PDF für meinen Chef per Mail", "Jarvis"))
        listOf("Öffne das PDF Argon4 bitte gleich für mich",                                   // one ability
            "Wie mache ich aus Seite 2 vom PDF Argon4 eine Word-Datei für eine Mail?",          // a question
            "Mach aus Seite 2 vom PDF Argon4 eine Word-Datei, aber schick sie nicht per Mail",   // rules an action out
            "Schreib „Mach aus dem PDF eine Word-Datei und schick sie per Mail“ in die Notiz",   // a quote
            "Notiz: aus dem PDF Argon4 eine Word-Datei machen und per Mail schicken",            // dictation
            "Wie spät ist es und wie wird das Wetter morgen in Wien am Nachmittag?"
        ).forEach { assertFalse(it, OwnerMissionModelPlan.wants(it, "Jarvis")) }
    }

    @Test fun thePromptOffersOnlyTheLaneFormsAndStaysSmall() {
        val prompt = OwnerMissionModelPlan.prompt(goal)
        assertTrue(prompt.endsWith("Auftrag: $goal"))
        assertTrue(prompt.contains("Öffne das PDF <Name>") && prompt.contains("KEIN PLAN"))
        assertTrue(prompt.length < 1_400)
        // Every form of the prompt, filled in, is an order the planner recognises.
        listOf("Öffne das PDF Argon4", "Fass Seite 2 zusammen und speichere es als Word", "Fass Seite 2 zusammen", "Fass mir die Nachrichten von heute zusammen",
            "Speichere das als PDF", "Speichere das als Word-Datei", "Schick das per Mail an anna@example.org",
            "Such das neueste PDF in Downloads", "Öffne es").forEach { assertTrue(it, OwnerMissionPlanner.recognise(it, "Jarvis") != null) }
    }

    @Test fun aPlanOfLaneOrdersWithOnlyWhatTheOwnerSaidIsAccepted() {
        val accepted = check(good) as OwnerMissionModelPlan.Result.Accepted
        assertEquals(listOf("OPEN:Argon4", "PAGE:2:DOCX", "MAIL:anna@example.org"), accepted.plan.codes)
        assertEquals(goal, accepted.plan.request)
        // Other list marks and a missing format (the desk then asks) are fine.
        assertEquals("ACCEPTED", rejected("- Öffne das PDF Argon4\n- Fass Seite 2 zusammen und speichere es\n- Schick das per Mail an anna@example.org"))
        assertEquals("ACCEPTED", rejected("1) Fass mir die Nachrichten von heute zusammen\n2) Speichere das als PDF\n3) Schick das per Mail an meinen Chef",
            "Mach aus den Nachrichten von heute ein PDF für meinen Chef per Mail"))
    }

    @Test fun anythingElseRejectsTheWholePlan() {
        assertEquals("kein Plan erhalten", rejected(null))
        assertEquals("kein Plan erhalten", rejected("  \n "))
        assertEquals("das Modell sieht keinen Plan", rejected("KEIN PLAN"))
        assertEquals("der Modellplan ist zu lang", rejected(good + "\n" + "x".repeat(OwnerMissionModelPlan.MAX_CHARS)))
        assertEquals("der Modellplan hat 1 Zeilen", rejected("1. Öffne das PDF Argon4"))
        assertEquals("der Modellplan hat 7 Zeilen", rejected((1..7).joinToString("\n") { "$it. Speichere das als PDF" }))
        assertEquals("Zeile 1 hält das Listenformat nicht ein", rejected("Gern, hier ist der Plan:\n1. Öffne das PDF Argon4\n2. Speichere das als PDF"))
        assertEquals("Zeile 2 ist kein Auftrag, den eine Lenkung kennt", rejected("1. Öffne das PDF Argon4\n2. Lösche das PDF Argon4"))
        assertEquals("Zeile 2 ist kein Auftrag, den eine Lenkung kennt", rejected("1. Öffne das PDF Argon4\n2. Schreib Anna auf WhatsApp, dass es da ist"))
        assertEquals("Zeile 2 ist kein klarer Auftrag", rejected("1. Öffne das PDF Argon4\n2. Fass Seite 2 zusammen und speichere es nicht als Word"))
        assertEquals("die Schritte passen nicht aufeinander", rejected("1. Öffne das PDF Argon4\n2. Speichere das als PDF"))
    }

    @Test fun nothingTheOwnerDidNotSayMayAppearInThePlan() {
        assertEquals("Schritt 1 nennt den Dateinamen „Xenon5“, das du nicht gesagt hast", rejected(good.replace("Argon4", "Xenon5")))
        assertEquals("Schritt 3 nennt die Adresse mallory@example.net, das du nicht gesagt hast", rejected(good.replace("anna@example.org", "mallory@example.net")))
        assertEquals("Schritt 3 nennt den Empfänger „Bernd“, das du nicht gesagt hast", rejected(good.replace("anna@example.org", "Bernd")))
        assertEquals("Schritt 2 nennt eine Seite, das du nicht gesagt hast", rejected(good.replace("Seite 2", "Seite 4")))
        assertEquals("Schritt 2 nennt ein Format, das du nicht gesagt hast", rejected(good.replace("als Word", "als PDF")))
        assertEquals("Schritt 2 nennt den Dateinamen „Geheim“, das du nicht gesagt hast",
            rejected(good.replace("als Word", "als Word unter dem Namen Geheim")))
        assertEquals("Schritt 1 nennt einen Ordner, das du nicht gesagt hast", rejected(good.replace("Argon4\n", "Argon4 aus dem Download-Ordner\n")))
        // A mail the owner never mentioned, news he did not ask for.
        val noMail = "Mach aus Seite 2 vom PDF Argon4 eine Word-Datei und speichere sie mir bitte gleich ab"
        assertEquals("Schritt 3 nennt eine E-Mail, das du nicht gesagt hast", rejected(good, noMail))
        assertEquals("Schritt 1 nennt die Nachrichten, das du nicht gesagt hast",
            rejected("1. Fass mir die Nachrichten von heute zusammen\n2. Schick das per Mail an anna@example.org"))
    }

    // ── Review findings B1 and B7 (19.11.2026): a plan neither drops work nor adds it ───────────────────────

    private val goalSentence = "Schau bitte meine E-Mails durch, fasse mir das Wichtigste zusammen, mach daraus ein PDF und schick " +
        "meinem Vater auf WhatsApp, dass ich mich später melde."
    private val lawyer = "Suche die letzte Mail von meinem Anwalt, fasse sie zusammen, erstelle daraus ein PDF und öffne es."
    private val pageTwo = "Such die PDF-Datei Argon4, öffne sie, analysiere Seite zwei und fasse sie zusammen."

    private fun uncovered(request: String, text: String, single: Boolean = true) =
        OwnerMissionModelPlan.check(request, text, "Jarvis", allowSingle = single) as? OwnerMissionModelPlan.Result.Uncovered

    /** Review sentence 1: „1. Speichere das als PDF“ passed the check and ran at once on the last chat answer. */
    @Test fun aPlanForOneOfFourOrdersIsNeverAcceptedAndOffersNothingThatBuildsOnTheRest() {
        val checked = checkNotNull(uncovered(goalSentence, "1. Speichere das als PDF"))
        assertEquals(listOf("NOTYET:MAIL", "LATER:SUMMARY", "EXPORT:PDF", "LATER:SEND"), checked.open.map { it.reading.code })
        // The PDF would be made of mails nobody read: it is not offered, and nothing runs.
        assertTrue(checked.rest.isEmpty())
        assertTrue(checked.open.single { it.reading.code == "EXPORT:PDF" }.dependsOnOpen)
        val said = OwnerMissionPartial.text(checked.open, checked.rest)
        assertTrue(said, said.startsWith("Verstanden – ganz kann ich das noch nicht erledigen. Deine E-Mails kann ich noch nicht lesen."))
        assertTrue(said, said.endsWith("Ich habe nichts davon ausgeführt."))
        assertFalse(said, said.contains("Erledigt"))
        // Not as a plan of several lines either: a mail to his father is not what he said.
        assertEquals("Schritt 2 nennt eine E-Mail, das du nicht gesagt hast",
            rejected("1. Speichere das als PDF\n2. Schick das per Mail an meinen Vater", goalSentence))
    }

    /** Review sentence 3: „Speichere das als PDF / Öffne es“ was announced and saved the last chat answer after „Ja“. */
    @Test fun aPlanThatSkipsTheSearchAndTheSummaryIsNeverAccepted() {
        val checked = checkNotNull(uncovered(lawyer, "1. Speichere das als PDF\n2. Öffne es", single = false))
        assertEquals(listOf("NOTYET:MAIL", "LATER:SUMMARY", "EXPORT:PDF", "OPEN_IT"), checked.open.map { it.reading.code })
        assertTrue(checked.rest.isEmpty())
        assertEquals("Verstanden – ganz kann ich das noch nicht erledigen. Deine E-Mails kann ich noch nicht lesen. Was darauf aufbaut, " +
            "lasse ich deshalb auch: zusammenfassen, als PDF speichern und öffnen. Ich habe nichts davon ausgeführt.",
            OwnerMissionPartial.text(checked.open, checked.rest))
    }

    /** Review sentence 5: the only allowed page form carried a save, so a file nobody ordered was written. */
    @Test fun aPlanCannotAddASaveTheOwnerDidNotOrder() {
        assertEquals("Schritt 2 speichert eine Datei, was du nicht bestellt hast",
            rejected("1. Öffne das PDF Argon4\n2. Fass Seite 2 zusammen und speichere es", pageTwo))
        assertEquals("Schritt 2 nennt ein Format, das du nicht gesagt hast",
            rejected("1. Öffne das PDF Argon4\n2. Fass Seite 2 zusammen und speichere es als Word", pageTwo))
        // „die PDF-Datei“ names the file he is looking for – it is no order to make a PDF.
        assertEquals("Schritt 2 nennt ein Format, das du nicht gesagt hast",
            rejected("1. Öffne das PDF Argon4\n2. Fass Seite 2 zusammen und speichere es als PDF", pageTwo))
        // What he did say is a plan: find and open, then the summary of page two – shown, not saved.
        val accepted = OwnerMissionModelPlan.check(pageTwo, "1. Öffne das PDF Argon4\n2. Fass Seite 2 zusammen", "Jarvis") as OwnerMissionModelPlan.Result.Accepted
        assertEquals(listOf("OPEN:Argon4", "PAGESUM:2"), accepted.plan.codes)
        // With a verb of saving in the sentence the same line is his order.
        assertEquals("ACCEPTED", rejected("1. Öffne das PDF Argon4\n2. Fass Seite 2 zusammen und speichere es",
            "Such die PDF-Datei Argon4, öffne sie, fasse Seite zwei zusammen und speichere das Ergebnis"))
    }

    @Test fun everyKindOfStepNeedsItsWordInTheOwnersSentence() {
        val wish = "Ich brauche das PDF Argon4 und dazu die Nachrichten von heute"
        // Nothing in the sentence asks to open anything.
        assertEquals("Schritt 1 öffnet ein PDF, was du nicht bestellt hast",
            rejected("1. Öffne das PDF Argon4\n2. Fass mir die Nachrichten von heute zusammen", wish))
        assertEquals("ACCEPTED", rejected("1. Such das PDF Argon4\n2. Fass mir die Nachrichten von heute zusammen", wish))
        // A model never decides to clean the storage: only the owner's own clause can be that order.
        assertEquals("Schritt 2 räumt den Speicher auf oder prüft ihn, was du nicht bestellt hast",
            rejected("1. Öffne das PDF Argon4\n2. Bereinige die sicheren temporären Dateien", "Öffne das PDF Argon4 und zeig mir dann die Datei"))
        // „… als PDF“ orders a file although no verb of saving is said (TLKC-MAP-007 stays what it was).
        assertEquals("ACCEPTED", rejected("1. Fass mir die Nachrichten von heute zusammen\n2. Speichere das als PDF\n3. Schick das per Mail an Anna",
            "Die Nachrichten sollen bitte als PDF an Anna per Mail gehen"))
        // The PDF a page step reads and the search whose hit is opened need no verb of their own.
        assertEquals("ACCEPTED", rejected("1. Öffne das PDF Argon\n2. Fass Seite 3 zusammen und speichere es als Word",
            "Mach bitte aus Seite 3 vom Argon-PDF eine Word-Datei"))
        // A single page summary without a save stays the answer in the chat.
        assertEquals("eine einzelne Seite ohne Speichern beantworte ich im Gespräch",
            (OwnerMissionModelPlan.check("Kannst du mir Seite 2 bitte kurz zusammenfassen", "1. Fass Seite 2 zusammen", "Jarvis",
                allowSingle = true) as OwnerMissionModelPlan.Result.Rejected).reason)
    }

    @Test fun oneOrderRunsWithoutAQuestionOnlyWhenTheSentenceIsOneOrder() {
        fun single(request: String, line: String) = OwnerMissionModelPlan.check(request, line, "Jarvis", allowSingle = true)
        // One order: it runs.
        val one = single("Kannst du mir bitte mal das Argon-Dokument aufmachen", "1. Öffne das PDF Argon") as OwnerMissionModelPlan.Result.Accepted
        assertTrue(OwnerMissionModelPlan.runsWithoutQuestion(one.plan, "Jarvis"))
        // A second clause with an order verb: the one line does not cover it – nothing runs, the rest is only offered.
        val two = single("Öffne das PDF Argon4 und erkläre es mir", "1. Öffne das PDF Argon4") as OwnerMissionModelPlan.Result.Uncovered
        assertEquals(listOf("LATER:SUMMARY"), two.open.map { it.reading.code })
        assertEquals(listOf("OPEN:Argon4"), two.rest.map { it.code })
        val unknown = single("Mach mir bitte das Argon-Dokument auf und druck es dann aus", "1. Öffne das PDF Argon") as OwnerMissionModelPlan.Result.Uncovered
        assertEquals(listOf("druck es dann aus"), unknown.open.map { it.text })
        assertEquals(listOf("OPEN:Argon"), unknown.rest.map { it.code })
        // And whatever a check lets through: a plan of one step for a sentence with two orders is asked first.
        val step = checkNotNull(OwnerMissionPlanner.recognise("Öffne das PDF Argon4", "Jarvis"))
        assertFalse(OwnerMissionModelPlan.runsWithoutQuestion(OwnerMissionPlan("Öffne das PDF Argon4 und druck es aus", listOf(step)), "Jarvis"))
        assertTrue(OwnerMissionModelPlan.runsWithoutQuestion(OwnerMissionPlan("Öffne das PDF Argon4", listOf(step)), "Jarvis"))
    }
}
