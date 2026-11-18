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
        listOf("Öffne das PDF Argon4", "Fass Seite 2 zusammen und speichere es als Word", "Fass mir die Nachrichten von heute zusammen",
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
}
