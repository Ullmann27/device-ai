package at.heinz.deviceai

import at.heinz.deviceai.OwnerDocumentDeskHarness.Companion.ARGON
import at.heinz.deviceai.OwnerDocumentDeskHarness.Companion.SUMMARY_3
import at.heinz.deviceai.OwnerMissionHarness.Companion.ANNA
import at.heinz.deviceai.OwnerMissionHarness.Companion.BRIEFING
import at.heinz.deviceai.cognitive.OwnerInputRepair
import at.heinz.deviceai.cognitive.OwnerMissionModelPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner goal 19.11.2026: „… dass Opus oder Claude hier wirklich jedes Werkzeug bedienen kann“ – and his two field
 * cases from the Fold7, end to end on the host with the productive planner, director and lanes (see
 * [OwnerMissionHarness]). Each field case runs twice: with the on-device model and with the cloud brain. Both must
 * reach the same fixed flow of the app – neither brain is asked, and no Shizuku tool is involved.
 *
 * Then the cloud brain's own contribution: a free sentence no lane parser took is mapped to ONE lane order, checked
 * like a plan, and run by the lane – at once when it only reads or writes the owner's own file, after his „Ja“ when
 * something leaves the device.
 *
 * The real model answers, the device and the real cloud brain are NOT_RUN here; the model text is a stand-in.
 */
class OwnerCloudBrainAbilityFlowTest {
    private val draft = "Kernaussagen: Argon ist ein Edelgas, das in den vier Versuchsreihen unter Druck gemessen wurde.\n\n" +
        "Offene Punkte: Die vierte Messreihe ist unvollständig und sollte wiederholt werden."
    private val fieldA = "Erstelle.bitte eine pdf und zeig sie mir.dann"
    private val fieldB = "ich schaue meinen Standort Ordner ich suche eine PDF die heißt a und 4 Experimente"

    private fun harness(cloud: Boolean) = OwnerMissionHarness().apply {
        cloudBrain = cloud
        // The Activity's default: model plans are on with the cloud brain and off with the model on the device.
        modelPlansOn = OwnerMissionModelPlan.switchedOn(saved = null, cloudBrain = cloud)
        toolsHost.cloud = cloud
        deskHost.cloud = cloud
    }

    /** A chat in which the summary draft of the opened PDF „Argon4 Experimente.pdf“ was just shown. */
    private fun afterDraft(cloud: Boolean) = harness(cloud).apply {
        toolsHost.foreignTurn("Fass das Dokument zusammen", draft)
        tools.noteDocumentDraft("Argon4 Experimente.pdf", draft, complete = true, cloud = cloud)
        // The work window then offers the next steps: the draft is no longer the last line of the chat.
        toolsHost.log += "SAY:Dein Entwurf steht bereit. Du kannst ihn bearbeiten oder als Datei speichern."
    }

    private fun text(bytes: ByteArray) = String(bytes, Charsets.UTF_8)

    // ── field case a: „Erstelle.bitte eine pdf und zeig sie mir.dann“ ───────────────────────────────────

    @Test fun fieldCaseA_theSentenceWithKeyboardDotsSavesTheDraftAsPdfAndOpensItWithEitherBrain() {
        for (cloud in listOf(false, true)) {
            val h = afterDraft(cloud)
            h.modelPlan = { error("no model is asked: the app's fixed flow takes this sentence") }
            // What the Activity does first with every typed turn.
            val said = OwnerInputRepair.strayDots(fieldA)
            assertEquals("Erstelle bitte eine pdf und zeig sie mir dann", said)
            assertEquals("cloud=$cloud", "MISSION", h.owner(said))
            assertEquals("Ich mache das in zwei Schritten: 1. Als PDF speichern. 2. Die gespeicherte Datei öffnen.", h.missionSaid.first())
            // The PDF exists, holds the draft, and names the PDF it summarises – in its name and in its source line.
            val name = h.downloads().single()
            assertEquals("Zusammenfassung Argon4 Experimente 18.11.2026.pdf", name)
            val pdf = text(h.toolsHost.downloads.getValue(name))
            assertTrue(pdf, pdf.contains("Zusammenfassung Argon4 Experimente"))
            assertTrue(pdf, pdf.contains("Kernaussagen: Argon ist ein Edelgas") && pdf.contains("Offene Punkte: Die vierte Messreihe"))
            assertTrue(pdf, pdf.contains("Quelle: „Argon4 Experimente.pdf“ · Entwurf, zusammengefasst " +
                (if (cloud) "vom Cloud-Gehirn" else "vom Sprachmodell auf dem Gerät") + " · gespeichert am 18.11.2026 · vor der Verwendung prüfen."))
            assertFalse("the window's offer is not what gets saved", pdf.contains("Dein Entwurf steht bereit"))
            // … and then it is opened.
            assertEquals(name, h.toolsHost.opened.single().displayName)
            assertTrue(h.lastSaid, h.lastSaid.startsWith("Fertig"))
            assertTrue(h.said.none { it.contains("kann ich nicht", ignoreCase = true) })
            assertTrue("neither brain was asked", h.planPrompts.isEmpty() && h.toolsHost.prompts.isEmpty())
            assertTrue("no device tool was needed", h.chainCalls.isEmpty())
        }
    }

    @Test fun fieldCaseA_theSingleOrderAloneSavesTheDraftAndSaysWhatIsInTheFile() {
        for (order in listOf("Erstelle bitte eine pdf", "Mach mir eine PDF", "Erstell mir ein PDF davon")) {
            val h = afterDraft(cloud = true)
            assertEquals(order, "TOOLS", h.owner(order))
            assertEquals(order, "Zusammenfassung Argon4 Experimente 18.11.2026.pdf", h.downloads().single())
            assertTrue(h.lastSaid, h.lastSaid.contains("Darin steht der Entwurf der Zusammenfassung aus diesem Gespräch; das PDF steht als Quelle dabei."))
        }
        // The pointing form right after the draft means the draft as well; Word works the same way.
        val h = afterDraft(cloud = false)
        assertEquals("TOOLS", h.owner("Speichere das als Word-Datei"))
        assertEquals("Zusammenfassung Argon4 Experimente 18.11.2026.docx", h.downloads().single())
    }

    @Test fun fieldCaseA_anEditedDraftIsSavedAsEdited() {
        val h = afterDraft(cloud = false)
        val edited = "Kernaussagen: Argon ist ein Edelgas. Die vierte Messreihe wurde am Montag wiederholt und ist jetzt vollständig."
        h.tools.reviseDocumentDraft(edited)
        assertEquals("TOOLS", h.owner("Erstelle eine PDF"))
        val pdf = text(h.toolsHost.downloads.values.single())
        assertTrue(pdf.contains("wurde am Montag wiederholt"))
        assertFalse(pdf.contains("sollte wiederholt werden"))
    }

    @Test fun fieldCaseA_aDraftFromTheWorkJournalNamesNoAuthorItCannotKnow() {
        val h = harness(cloud = true)
        h.toolsHost.foreignTurn("Zeig mir den Entwurf von gestern", draft)
        h.tools.noteDocumentDraft("Argon4 Experimente.pdf", draft, complete = false)
        assertEquals("TOOLS", h.owner("Mach mir bitte eine PDF"))
        val pdf = text(h.toolsHost.downloads.values.single())
        assertTrue(pdf, pdf.contains("Quelle: „Argon4 Experimente.pdf“ · Entwurf einer Zusammenfassung · gespeichert am 18.11.2026 · " +
            "vor der Verwendung prüfen. Grundlage ist ein Textauszug des Dokuments; der Rest ist nicht enthalten."))
        assertFalse(pdf.contains("Cloud-Gehirn") || pdf.contains("Sprachmodell auf dem Gerät"))
    }

    @Test fun fieldCaseA_withNothingToSaveTheAppAsksWhatShouldGoInInsteadOfSayingItCannot() {
        for (cloud in listOf(false, true)) {
            val h = harness(cloud)
            assertEquals("TOOLS", h.owner("Erstelle bitte eine PDF"))
            assertTrue(h.lastSaid, h.lastSaid.startsWith("Gern – was soll in der PDF stehen?"))
            assertTrue(h.downloads().isEmpty())
            assertTrue(h.said.none { it.contains("kann ich nicht", ignoreCase = true) })
            // As a sequence the first step asks the same, and the opening never starts.
            val sequence = harness(cloud)
            assertEquals("MISSION", sequence.owner("Erstelle bitte eine PDF und zeig sie mir dann"))
            assertTrue(sequence.said.any { it.contains("was soll in der PDF stehen?") })
            assertTrue(sequence.toolsHost.opened.isEmpty() && sequence.downloads().isEmpty())
        }
    }

    // ── field case b: „… ich suche eine PDF die heißt a und 4 Experimente“ ─────────────────────────────

    private fun withArgonExperiments(cloud: Boolean) = harness(cloud).apply {
        deskHost.put("Argon4 Experimente.pdf", pages = ARGON)
        deskHost.put("Rechnung 2026.pdf")
        deskHost.put("Urlaub.pdf", folder = "Documents")
        modelPlan = { error("no model is asked: the document desk takes this sentence") }
    }

    @Test fun fieldCaseB_theSpokenSentenceIsTheDesksOwnSearchAndOffersTheSimilarNameWithEitherBrain() {
        for (cloud in listOf(false, true)) {
            val h = withArgonExperiments(cloud)
            assertEquals("cloud=$cloud", "DESK", h.owner(fieldB))
            // The name is searched as he said it. It is no hit – the similar file is offered, never opened by itself.
            assertTrue(h.lastSaid, h.lastSaid.startsWith("Ein PDF namens „a und 4 Experimente“ habe ich nicht gefunden."))
            assertTrue(h.lastSaid, h.lastSaid.contains("Ähnlich heißt: „Argon4 Experimente.pdf“ – Ordner Download"))
            assertTrue(h.lastSaid, h.lastSaid.endsWith("Meinst du das?"))
            assertEquals(listOf("Argon4 Experimente.pdf"), h.deskHost.hitsShown.last())
            assertTrue(h.deskHost.shown.isEmpty())
            // In the app itself: no Shizuku file search, no model, no plan.
            assertTrue(h.chainCalls.isEmpty() && h.planPrompts.isEmpty() && h.deskHost.prompts.isEmpty())
            assertEquals("DESK", h.owner("Ja"))
            assertEquals("Argon4 Experimente.pdf", h.deskHost.shown.single().name)
        }
    }

    @Test fun fieldCaseB_theCleanSentenceFindsTheFileByItsName() {
        for (cloud in listOf(false, true)) {
            val h = withArgonExperiments(cloud)
            assertEquals("DESK", h.owner("Ich suche eine PDF, die heißt Argon4 Experimente"))
            assertEquals(listOf("Argon4 Experimente.pdf"), h.deskHost.hitsShown.last())
            assertTrue(h.lastSaid, h.lastSaid.contains("Argon4 Experimente.pdf"))
            assertTrue("a search shows the hit; it opens nothing by itself", h.deskHost.shown.isEmpty())
            assertTrue(h.chainCalls.isEmpty() && h.planPrompts.isEmpty())
            assertEquals("DESK", h.owner("Ja"))
            assertEquals("Argon4 Experimente.pdf", h.deskHost.shown.single().name)
        }
    }

    @Test fun fieldCaseB_withoutASimilarFileNothingIsInvented() {
        val h = harness(cloud = true).apply { deskHost.put("Rechnung 2026.pdf") }
        assertEquals("DESK", h.owner(fieldB))
        assertTrue(h.lastSaid, h.lastSaid.startsWith("Ein PDF namens „a und 4 Experimente“ habe ich nicht gefunden."))
        assertFalse(h.lastSaid.contains("Ähnlich"))
        assertTrue(h.deskHost.shown.isEmpty())
    }

    // ── the cloud brain maps a free sentence to ONE order of a fixed flow ───────────────────────────────

    private val openFree = "Kannst du mir bitte mal das Argon-Dokument aufmachen"

    private fun withArgon(cloud: Boolean) = harness(cloud).apply {
        deskHost.put("Argon.pdf", pages = ARGON)
        deskHost.model = { OwnerWorkGeneration(SUMMARY_3, null, true) }
    }

    @Test fun aReadingOrderIsMappedCheckedAndRunByItsLaneWithoutAFurtherQuestion() {
        val h = withArgon(cloud = true)
        h.modelPlan = { "1. Öffne das PDF Argon" }
        assertEquals("MISSION", h.owner(openFree))
        // One short request with the owner's sentence and the closed forms – nothing else of the chat.
        val prompt = h.planPrompts.single()
        assertTrue(prompt.endsWith("Auftrag: $openFree"))
        assertTrue(prompt.contains("Such das PDF <Name>") && prompt.contains("KEIN PLAN"))
        // The desk ran its own order: its search, its wording, and the PDF is open.
        assertEquals("Argon.pdf", h.deskHost.shown.single().name)
        assertTrue(h.activities.any { it == "AUFTRAG · zugeordnet: „Öffne das PDF Argon“ · nichts hinzugefügt" })
        assertTrue("no question was asked in between", h.missionSaid.isEmpty())
        // The owner's sentence is shown once; the mapped order is not shown as a second turn of his.
        assertEquals(listOf("USER:$openFree"), h.toolsHost.log.filter { it.startsWith("USER:") })
        assertTrue(h.ordinaryTurns.isEmpty())
    }

    @Test fun withTheOnDeviceModelASingleSentenceIsNotMapped() {
        val h = withArgon(cloud = false)
        h.modelPlansOn = true
        h.modelPlan = { "1. Öffne das PDF Argon" }
        assertEquals("OTHER", h.owner(openFree))
        assertTrue(h.planPrompts.isEmpty() && h.deskHost.shown.isEmpty())
    }

    @Test fun anOrderThatLeavesTheDeviceIsAskedFirstAndRunsOnlyAfterJa() {
        fun briefed() = harness(cloud = true).apply {
            toolsHost.model = { OwnerWorkGeneration(BRIEFING, null, true) }
            assertEquals("TOOLS", owner("Fass mir die Nachrichten von heute zusammen"))
            modelPlan = { "1. Schick das per Mail an $ANNA" }
        }
        val sentence = "Ich möchte die Zusammenfassung bitte per Mail an $ANNA haben"
        val yes = briefed()
        val route = yes.owner(sentence)
        if (route == "MISSION") {
            assertEquals("Ich verstehe das so: Per E-Mail an $ANNA schicken. Soll ich das so machen?", yes.missionSaid.last())
            assertTrue("nothing is handed over before his Ja", yes.toolsHost.mails.isEmpty())
            assertEquals("MISSION", yes.owner("Ja"))
        } else assertEquals("the mail flow took the sentence by itself", "TOOLS", route)
        val mail = yes.toolsHost.mails.single()
        assertEquals(ANNA, mail.address)
        assertTrue("the briefing he spoke about is what is handed over", mail.body.contains("Die Nationalbank von Musterstadt"))

        val no = briefed()
        if (no.owner(sentence) == "MISSION") {
            assertEquals("MISSION", no.owner("Nein"))
            assertEquals("In Ordnung, ich mache nichts davon.", no.missionSaid.last())
            assertTrue(no.toolsHost.mails.isEmpty())
        }
    }

    @Test fun aMappedOrderMayNameNothingTheOwnerDidNotSay() {
        for ((answer, why) in listOf(
            "1. Öffne das PDF Xenon" to "nennt den Dateinamen",
            "1. Lösche das PDF Argon" to "kein Auftrag, den eine Lenkung kennt",
            "1. Öffne das PDF Argon aus dem Download-Ordner" to "einen Ordner",
            "1. Öffne das PDF Argon\n2. Schick das per Mail an $ANNA" to "",
            "Gerne! Ich öffne das PDF Argon für dich." to "Listenformat",
            "KEIN PLAN" to "kein",
            "" to "kein Plan erhalten")) {
            val h = withArgon(cloud = true)
            h.modelPlan = { answer }
            assertEquals(answer, "MISSION", h.owner(openFree))
            assertTrue(answer, h.deskHost.shown.isEmpty() && h.toolsHost.mails.isEmpty() && h.downloads().isEmpty())
            assertTrue(answer, h.activities.any { it.startsWith("AUFTRAGSFOLGE · Modellplan verworfen") && it.contains(why) })
            // The sentence then takes its ordinary turn; nothing of the model's text is said or run.
            assertEquals(answer, listOf(openFree), h.ordinaryTurns)
            assertTrue(answer, h.missionSaid.isEmpty())
        }
    }

    @Test fun aSentenceThatRulesOutTheCloudIsNeverSentForAPlan() {
        val h = withArgon(cloud = true)
        h.planAllowed = { false }
        h.modelPlan = { "1. Öffne das PDF Argon" }
        assertEquals("OTHER", h.owner(openFree))
        assertTrue(h.planPrompts.isEmpty() && h.deskHost.shown.isEmpty())
    }

    @Test fun aQuestionOrASentenceWithoutAThingOfTheFlowsCostsNoRequest() {
        val h = withArgon(cloud = true)
        h.modelPlan = { "1. Öffne das PDF Argon" }
        for (other in listOf("Was ist ein PDF?", "Erzähl mir einen Witz", "Erklär mir, was eine Word-Datei ist",
            "Speichere das auf keinen Fall als PDF", "Wie wird das Wetter morgen?"))
            assertEquals(other, "OTHER", h.owner(other))
        assertTrue(h.planPrompts.isEmpty())
    }

    @Test fun aSavingOrderMappedFromAFreeSentenceMeansWhatTheChatWasAboutWhenHeSaidIt() {
        val h = afterDraft(cloud = true)
        h.modelPlan = { "1. Speichere das als Word-Datei" }
        val route = h.owner("Mach aus der Zusammenfassung eine Word-Datei")
        assertTrue(route, route == "MISSION" || route == "TOOLS")
        // Whichever way it went: his own file with the draft of the PDF – not the line that was shown since.
        assertEquals("Zusammenfassung Argon4 Experimente 18.11.2026.docx", h.downloads().single())
        assertTrue(h.said.none { it.contains("Dein Entwurf steht bereit") && it.startsWith("TOOLS:") })
    }

    // ── plans of several steps with the cloud brain ───────────────────────────────────────────────────────

    @Test fun withTheCloudBrainAPlanOfSeveralStepsIsOnlySpokenAndRunsAfterJa() {
        val h = withArgon(cloud = true)
        h.modelPlan = { "1. Öffne das PDF Argon\n2. Fass Seite 3 zusammen und speichere es als Word" }
        val goal = "Mach bitte aus Seite 3 vom Argon-PDF eine Word-Datei"
        assertEquals("MISSION", h.owner(goal))
        assertEquals("Ich würde das in zwei Schritten machen: 1. Das PDF „Argon“ suchen und öffnen. 2. Seite 3 zusammenfassen und " +
            "als Word-Datei speichern. Soll ich das so machen?", h.missionSaid.single())
        assertTrue("nothing runs before his Ja", h.deskHost.shown.isEmpty() && h.downloads().isEmpty())
        assertEquals("MISSION", h.owner("Ja"))
        assertEquals("Argon.pdf", h.deskHost.shown.single().name)
        assertEquals(1, h.downloads().size)
        assertTrue(h.downloads().single().endsWith(".docx"))
    }

    @Test fun modelPlansAreOnByDefaultWithTheCloudBrainAndTheOwnersSwitchWinsEitherWay() {
        assertTrue(OwnerMissionModelPlan.switchedOn(saved = null, cloudBrain = true))
        assertFalse(OwnerMissionModelPlan.switchedOn(saved = null, cloudBrain = false))
        assertFalse(OwnerMissionModelPlan.switchedOn(saved = false, cloudBrain = true))
        assertTrue(OwnerMissionModelPlan.switchedOn(saved = true, cloudBrain = false))
        // Switched off: no plan request with either brain.
        val h = withArgon(cloud = true)
        h.modelPlansOn = false
        h.modelPlan = { "1. Öffne das PDF Argon" }
        assertEquals("OTHER", h.owner(openFree))
        assertTrue(h.planPrompts.isEmpty())
    }
}
