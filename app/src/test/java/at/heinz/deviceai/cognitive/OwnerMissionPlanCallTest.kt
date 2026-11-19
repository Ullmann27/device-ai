package at.heinz.deviceai.cognitive

import at.heinz.deviceai.localmodel.ClaudePromptFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The structured plan call (architecture review D, option iii): what is sent – the constant block and the owner's
 * sentence, nothing else – and that the typed JSON answer is data: every step is worded as a lane order and passes the
 * same checks as a text plan. A real answer of a cloud model is NOT_RUN here; the answers below are written by hand.
 */
class OwnerMissionPlanCallTest {
    private val goal = "Schau bitte meine E-Mails durch, fasse mir das Wichtigste zusammen, mach daraus ein PDF und schick meinem " +
        "Vater auf WhatsApp, dass ich mich später melde."

    private fun accepted(request: String, json: String): List<String> =
        (OwnerMissionPlanCall.check(request, json, "Jarvis") as OwnerMissionModelPlan.Result.Accepted).plan.codes

    private fun rejected(request: String, json: String): String =
        (OwnerMissionPlanCall.check(request, json, "Jarvis") as OwnerMissionModelPlan.Result.Rejected).reason

    private val goalPlan = """{"steps": [
        {"kind": "mail_search", "args": {}, "covers": "Schau bitte meine E-Mails durch"},
        {"kind": "mail_summary", "args": {}, "covers": "fasse mir das Wichtigste zusammen"},
        {"kind": "export", "args": {"format": "pdf"}, "covers": "mach daraus ein PDF"},
        {"kind": "send_whatsapp", "args": {}, "covers": "schick meinem Vater auf WhatsApp, dass ich mich später melde"}
    ], "unsupported": []}"""

    // ── What is sent ────────────────────────────────────────────────────────────────────────────────────────

    @Test fun theRequestIsTheConstantBlockAndTheOwnersSentenceAndNothingElse() {
        val prompt = OwnerMissionPlanCall.prompt(goal)
        assertEquals(OwnerMissionPlanCall.SYSTEM + goal, prompt)
        // For the cloud runtime: the constant block is a system block of its own behind the unchanged preface, and the
        // user message is the sentence alone. It is not the owner's chat turn: no ability catalogue, no server tool.
        val frame = ClaudePromptFrame.frame(prompt, isolatedWorker = false) { listOf("ABILITY CATALOGUE") }
        assertEquals(listOf(ClaudePromptFrame.RUNTIME_PREFACE, OwnerMissionPlanCall.SYSTEM), frame.systemBlocks)
        assertEquals(goal, frame.user)
        assertFalse(ClaudePromptFrame.isOwnerTurn(prompt))
        // The block is the same for every sentence: a byte-stable prefix.
        assertTrue(OwnerMissionPlanCall.prompt("Öffne Chrome und speichere das als PDF").startsWith(OwnerMissionPlanCall.SYSTEM))
        assertFalse(OwnerMissionPlanCall.SYSTEM.contains("{wish}") || OwnerMissionPlanCall.SYSTEM.contains("\$"))
    }

    @Test fun theDialoguePrefaceIsUntouched() {
        // The first sentences of the preface, pinned: a change here would move the cache prefix of every dialogue.
        assertTrue(ClaudePromptFrame.RUNTIME_PREFACE.startsWith("You are the language model of \"Continuum\", a personal assistant app on its owner's Android phone. "))
        assertTrue(ClaudePromptFrame.RUNTIME_PREFACE.contains("Request at most one tool call per turn."))
        assertFalse(ClaudePromptFrame.RUNTIME_PREFACE.contains("JSON"))
    }

    // ── The answer is data ──────────────────────────────────────────────────────────────────────────────────

    @Test fun aTypedPlanForTheGoalSentenceIsAcceptedAsTheSameStepsTheAppPlansItself() {
        assertEquals(listOf("MAILFIND:INBOX", "MAILSUM", "EXPORT:PDF", "SEND:WHATSAPP:meinem Vater"), accepted(goal, goalPlan))
        assertEquals(OwnerMissionPlanner.plan(goal, "Jarvis")?.codes, accepted(goal, goalPlan))
        // In a code fence, as models like to answer: the same plan.
        assertEquals(4, accepted(goal, "```json\n$goalPlan\n```").size)
    }

    @Test fun aMessageIsNeverWordedByTheModel() {
        // Another text, another recipient, or the model's own wording of the order: rejected.
        val changed = goalPlan.replace("schick meinem Vater auf WhatsApp, dass ich mich später melde", "schick meinem Vater auf WhatsApp, dass ich heute nicht komme")
        assertEquals("Schritt 4 deckt etwas ab, das nicht in deinem Satz steht", rejected(goal, changed))
        val other = goalPlan.replace("schick meinem Vater auf WhatsApp, dass ich mich später melde", "schick meinem Chef auf WhatsApp, dass ich mich später melde")
        assertEquals("Schritt 4 deckt etwas ab, das nicht in deinem Satz steht", rejected(goal, other))
        val withArgs = goalPlan.replace("\"kind\": \"send_whatsapp\", \"args\": {}", "\"kind\": \"send_whatsapp\", \"args\": {\"text\": \"Hallo\"}")
        assertEquals("Schritt 4 darf keine eigenen Angaben haben", rejected(goal, withArgs))
        // A message the owner did not order at all.
        val added = """{"steps": [{"kind": "open_pdf", "args": {"name": "Argon4", "open": true}, "covers": "Öffne das PDF Argon4"},
            {"kind": "send_whatsapp", "args": {}, "covers": "Öffne das PDF Argon4"}], "unsupported": []}"""
        assertTrue(OwnerMissionPlanCall.check("Öffne das PDF Argon4 und fass Seite 2 zusammen", added, "Jarvis") is OwnerMissionModelPlan.Result.Rejected)
    }

    @Test fun nothingIsAddedInventedOrLeftOut() {
        val request = "Öffne das PDF Argon4 und fass Seite 2 zusammen"
        val plan = """{"steps": [{"kind": "open_pdf", "args": {"name": "Argon4", "open": true}, "covers": "Öffne das PDF Argon4"},
            {"kind": "page_summary", "args": {"page": 2}, "covers": "fass Seite 2 zusammen"}], "unsupported": []}"""
        assertEquals(listOf("OPEN:Argon4", "PAGESUM:2"), accepted(request, plan))
        // Another file, another page, a save nobody ordered, a research nobody ordered.
        assertTrue(rejected(request, plan.replace("\"Argon4\"", "\"Xenon5\"")).contains("Dateinamen"))
        assertTrue(rejected(request, plan.replace("\"page\": 2", "\"page\": 7")).contains("eine Seite"))
        val saving = plan.replace("{\"kind\": \"page_summary\", \"args\": {\"page\": 2}", "{\"kind\": \"page_save\", \"args\": {\"page\": 2, \"format\": \"word\"}")
        assertTrue(OwnerMissionPlanCall.check(request, saving, "Jarvis") is OwnerMissionModelPlan.Result.Rejected)
        val researching = """{"steps": [{"kind": "open_pdf", "args": {"name": "Argon4", "open": true}, "covers": "Öffne das PDF Argon4"},
            {"kind": "research", "args": {"topic": "Argon4"}, "covers": "fass Seite 2 zusammen"}], "unsupported": []}"""
        assertTrue(rejected(request, researching).contains("recherchiert im Internet"))
        // One order of the sentence without a step: nothing runs by itself.
        val half = """{"steps": [{"kind": "mail_search", "args": {}, "covers": "Schau bitte meine E-Mails durch"},
            {"kind": "mail_summary", "args": {}, "covers": "fasse mir das Wichtigste zusammen"}], "unsupported": []}"""
        assertTrue(OwnerMissionPlanCall.check(goal, half, "Jarvis") is OwnerMissionModelPlan.Result.Uncovered)
    }

    @Test fun aPartTheModelCouldNotMapMeansNothingRunsByItself() {
        val request = "Öffne das PDF Argon4, druck es aus und fass mir die Nachrichten von heute zusammen"
        val plan = """{"steps": [{"kind": "open_pdf", "args": {"name": "Argon4", "open": true}, "covers": "Öffne das PDF Argon4"},
            {"kind": "news", "args": {}, "covers": "fass mir die Nachrichten von heute zusammen"}], "unsupported": ["druck es aus"]}"""
        val checked = OwnerMissionPlanCall.check(request, plan, "Jarvis")
        assertTrue(checked.toString(), checked is OwnerMissionModelPlan.Result.Uncovered)
        // A part in „unsupported“ that the owner never said is invented as well.
        assertEquals("„unsupported“ nennt etwas, das du nicht gesagt hast", rejected(request, plan.replace("druck es aus", "lösche alle Dateien")))
    }

    @Test fun anythingThatIsNotExactlyThePlanObjectIsRejectedAndNeverRepaired() {
        assertEquals("kein Plan erhalten", rejected(goal, "  "))
        assertEquals("der Modellplan ist kein gültiges JSON", rejected(goal, "{\"steps\": ["))
        assertEquals("der Modellplan enthält ein unbekanntes Feld", rejected(goal, goalPlan.replace("\"unsupported\": []", "\"unsupported\": [], \"run\": true")))
        assertEquals("Schritt 1 hat keine erlaubte Art", rejected(goal, goalPlan.replace("\"mail_search\"", "\"delete_all_mails\"")))
        assertEquals("Schritt 1 enthält ein unbekanntes Feld", rejected(goal, goalPlan.replace("\"kind\": \"mail_search\"", "\"tool\": \"shell\", \"kind\": \"mail_search\"")))
        assertEquals("Schritt 3 hat keine gültigen Angaben", rejected(goal, goalPlan.replace("\"format\": \"pdf\"", "\"format\": \"exe\"")))
        assertEquals("das Modell sieht keinen Plan", rejected(goal, "{\"steps\": [], \"unsupported\": [\"$goal\"]}"))
        assertEquals("der Modellplan ist zu lang", rejected(goal, "{\"steps\": [], \"pad\": \"" + "x".repeat(OwnerMissionPlanCall.MAX_CHARS) + "\"}"))
        // Text around the object is not a plan object: it is read as the line format, which it is not either.
        assertTrue(OwnerMissionPlanCall.check(goal, "Hier ist der Plan: $goalPlan", "Jarvis") is OwnerMissionModelPlan.Result.Rejected)
    }

    @Test fun anAnswerInTheNumberedLineFormatIsCheckedByTheSameCode() {
        val request = "Mach aus Seite 2 vom PDF Argon4 eine Word-Datei und die soll dann per Mail an anna@example.org"
        val lines = "1. Öffne das PDF Argon4\n2. Fass Seite 2 zusammen und speichere es als Word\n3. Schick das per Mail an anna@example.org"
        val typed = OwnerMissionPlanCall.check(request, lines, "Jarvis")
        val text = OwnerMissionModelPlan.check(request, lines, "Jarvis")
        assertEquals(text::class, typed::class)
        assertEquals((text as? OwnerMissionModelPlan.Result.Accepted)?.plan?.codes, (typed as? OwnerMissionModelPlan.Result.Accepted)?.plan?.codes)
        assertTrue(text is OwnerMissionModelPlan.Result.Accepted)
    }

    // ── When the call is made ───────────────────────────────────────────────────────────────────────────────

    @Test fun theCallIsOnlyWantedForOrdersNoLaneKnewByTheirWords() {
        // An order no lane knows behind one it knows: a model might map it.
        assertTrue(OwnerMissionPlanCall.wants(OwnerMissionPlanner.read("Öffne das PDF Argon4, druck es aus und fass mir die Nachrichten von heute zusammen", "Jarvis")))
        // A complete plan needs no model; one order is no sequence; a negation rules the sentence out.
        assertFalse(OwnerMissionPlanCall.wants(OwnerMissionPlanner.read(goal, "Jarvis")))
        assertFalse(OwnerMissionPlanCall.wants(OwnerMissionPlanner.read("Öffne das PDF Argon4", "Jarvis")))
        assertFalse(OwnerMissionPlanCall.wants(OwnerMissionPlanner.read("Öffne das PDF Argon4 nicht und druck es aus", "Jarvis")))
        // An order the app cannot carry out as a step at all (a call) is said plainly – no request is spent on it.
        assertFalse(OwnerMissionPlanCall.wants(OwnerMissionPlanner.read("Ruf Anna an und öffne danach das PDF Argon4", "Jarvis")))
        assertFalse(OwnerMissionPlanCall.wants(null))
    }
}
