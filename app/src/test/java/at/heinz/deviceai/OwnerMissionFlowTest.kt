package at.heinz.deviceai

import at.heinz.deviceai.OwnerDocumentDeskHarness.Companion.ARGON
import at.heinz.deviceai.OwnerDocumentDeskHarness.Companion.SUMMARY_2
import at.heinz.deviceai.OwnerDocumentDeskHarness.Companion.SUMMARY_3
import at.heinz.deviceai.OwnerMissionHarness.Companion.ANNA
import at.heinz.deviceai.OwnerMissionHarness.Companion.BRIEFING
import at.heinz.deviceai.OwnerMissionHarness.Companion.DOWNLOAD_LISTING
import at.heinz.deviceai.OwnerMissionHarness.Companion.NEWS_PDF
import at.heinz.deviceai.OwnerMissionHarness.Companion.WORD_2
import at.heinz.deviceai.cognitive.InternetAccessMode
import at.heinz.deviceai.sending.SendChannel
import at.heinz.deviceai.sending.SendPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mission layer end to end on the host: the owner's sentence, the productive planner and director, and behind
 * them the productive lanes (document desk, work tools, send lane, task chains) with their own parsers, gates,
 * questions and wording. Stand-ins are only the phone and the model text (see [OwnerMissionHarness]).
 *
 * Device behaviour – the real storage, PDF reader, model, viewer, Gmail and the work window – is NOT_RUN here.
 */
class OwnerMissionFlowTest {
    private val argonOrder = "Öffne das PDF Argon4, fass Seite 2 zusammen, speichere es als Word und schick es per Mail an $ANNA"
    private val newsOrder = "Fass mir die Nachrichten von heute zusammen, speichere sie als PDF und schick sie per Mail an $ANNA"
    private val argonPlan = "Ich mache das in drei Schritten: 1. Das PDF „Argon4“ suchen und öffnen. 2. Seite 2 zusammenfassen und " +
        "als Word-Datei speichern. 3. Per E-Mail an $ANNA schicken."
    private val argonDone = "Fertig – alle drei Schritte sind erledigt. 1. „Argon4.pdf“ geöffnet (Ordner Download). 2. „$WORD_2“ " +
        "gespeichert (Interner Speicher › Download). 3. E-Mail an $ANNA im Mailprogramm vorbereitet, diese Datei hängt an – gesendet habe ich nichts."

    /** A chat with „Argon4.pdf“ in Downloads and a model that summarises the page it is given. */
    private fun argon(): OwnerMissionHarness = OwnerMissionHarness().apply {
        deskHost.put("Argon4.pdf", pages = ARGON)
        deskHost.model = { prompt -> OwnerWorkGeneration(if (prompt.contains("Seite drei")) SUMMARY_3 else SUMMARY_2, null, true) }
    }

    private fun news(mode: InternetAccessMode = InternetAccessMode.ALWAYS_ON): OwnerMissionHarness = OwnerMissionHarness(mode).apply {
        toolsHost.model = { OwnerWorkGeneration(BRIEFING, null, true) }
    }

    // ── The two owner flows ─────────────────────────────────────────────────────────────────────────────────

    @Test fun theArgonMissionOpensSummarisesSavesAndPreparesTheMailInOrder() {
        val h = argon()
        assertEquals("MISSION", h.owner(argonOrder))
        // The plan is said first, then every lane speaks for its own step, then the report.
        assertEquals(argonPlan, h.missionSaid.first())
        assertEquals(listOf("MISSION", "DESK", "DESK", "TOOLS", "MISSION"), h.said.map { it.substringBefore(':') })
        assertEquals("Argon4.pdf", h.deskHost.shown.single().name)
        assertEquals(listOf(WORD_2), h.downloads())
        val mail = h.toolsHost.mails.single()
        assertEquals(ANNA, mail.address)
        assertEquals(WORD_2, mail.attachment!!.displayName)
        assertTrue(mail.body.contains(SUMMARY_2) && mail.body.contains("Quelle: „Argon4.pdf“, Seite 2 von 4"))
        assertEquals(argonDone, h.lastSaid)
        // The desk's offer to open the file is not asked in between: the mission goes on instead of waiting for it.
        assertTrue(h.said[2], h.said[2].startsWith("DESK:Seite 2 von „Argon4.pdf“ – Zusammenfassung:") && h.said[2].endsWith(", 2 KB."))
        // The owner said one sentence: it is the only owner turn shown, and the model ran once.
        assertEquals(listOf(argonOrder), h.toolsHost.lines("USER:"))
        assertEquals(1, h.deskHost.prompts.size)
        assertEquals(listOf("1/3 Das PDF „Argon4“ suchen und öffnen:DONE", "2/3 Seite 2 zusammenfassen und als Word-Datei speichern:DONE",
            "3/3 Per E-Mail an $ANNA schicken:DONE"), h.steps)
        assertEquals(true, h.stageEnded)
        assertTrue("nothing was sent by the app", h.sendHost.device.opened.isEmpty() && h.sendHost.device.pressed.isEmpty())
    }

    @Test fun theNewsMissionBriefsSavesThePdfAndPreparesTheMailWithIt() {
        val h = news()
        assertEquals("MISSION", h.owner(newsOrder))
        assertEquals("Ich mache das in drei Schritten: 1. Die Nachrichten von heute zusammenfassen. 2. Als PDF speichern. " +
            "3. Per E-Mail an $ANNA schicken.", h.missionSaid.first())
        assertEquals(listOf(NEWS_PDF), h.downloads())
        val mail = h.toolsHost.mails.single()
        assertEquals(ANNA, mail.address)
        assertEquals(NEWS_PDF, mail.attachment!!.displayName)
        assertEquals("application/pdf", mail.attachment!!.mime)
        assertTrue(mail.body.contains("Die Nationalbank von Musterstadt") && mail.body.contains("Quellen"))
        assertTrue(h.lastSaid, h.lastSaid.startsWith("Fertig – alle drei Schritte sind erledigt. 1. Nachrichten zusammengefasst ("))
        assertTrue(h.lastSaid, h.lastSaid.endsWith("2. „$NEWS_PDF“ gespeichert (Download-Ordner). 3. E-Mail an $ANNA im Mailprogramm " +
            "vorbereitet, diese Datei hängt an – gesendet habe ich nichts."))
        assertEquals(listOf(newsOrder), h.toolsHost.lines("USER:"))
        // The feeds were read once, under the owner's standing web setting, as for the single order.
        assertEquals(1, h.toolsHost.consents.size)
    }

    @Test fun newsAndSaveInOneSentenceFetchesTheNewsFirstInAFreshChatAndSavesTheBriefingAfterwards() {
        val sentence = "Fass mir die Nachrichten von heute zusammen und speichere sie als PDF"
        val h = news()
        assertEquals("In a fresh chat there is nothing to save yet: the sentence is carried out as it is said", "MISSION", h.owner(sentence))
        assertEquals("Ich mache das in zwei Schritten: 1. Die Nachrichten von heute zusammenfassen. 2. Als PDF speichern.", h.missionSaid.first())
        assertEquals(listOf(NEWS_PDF), h.downloads())
        assertTrue(h.lastSaid, h.lastSaid.startsWith("Fertig – beide Schritte sind erledigt. 1. Nachrichten zusammengefasst ("))
        assertEquals(1, h.toolsHost.consents.size)
        // With that briefing in the chat the same sentence is the export lane's order on it, as before: no second fetch.
        assertEquals("TOOLS", h.owner(sentence))
        assertEquals(1, h.toolsHost.consents.size)
        assertEquals(2, h.downloads().size)
    }

    @Test fun theFirstStepWaitsUntilThePlanHasBeenSpoken() {
        val h = argon()
        h.speakingPlan = true
        assertEquals("MISSION", h.owner(argonOrder))
        assertEquals("Only the plan has been said; no lane has started", listOf(argonPlan), h.said.map { it.substringAfter(':') })
        assertTrue(h.deskHost.shown.isEmpty() && h.downloads().isEmpty())
        assertTrue(h.mission.active)
        h.announcementSpoken()
        assertEquals("Argon4.pdf", h.deskHost.shown.single().name)
        assertEquals(listOf(WORD_2), h.downloads())
        assertEquals(argonDone, h.lastSaid)
    }

    @Test fun stopWhileThePlanIsSpokenStartsNoStep() {
        val h = argon()
        h.speakingPlan = true
        assertEquals("MISSION", h.owner(argonOrder))
        assertTrue(h.interrupt("Stopp"))
        h.announcementSpoken()
        assertTrue("Nothing was opened, nothing was written", h.deskHost.shown.isEmpty() && h.downloads().isEmpty())
        assertTrue(!h.mission.active)
    }

    @Test fun withTheStandingGmailGrantTheSendLaneVerifiesTheDraftWithItsAttachmentAndSendsOnce() {
        val h = news()
        h.sendHost.device.gmailWorks()
        h.sendHost.grant(SendChannel.GMAIL)
        assertEquals("MISSION", h.owner(newsOrder))
        assertTrue("the generic hand-over is not used as well", h.toolsHost.mails.isEmpty())
        val plan = h.sendHost.device.opened.single() as SendPlan.Mail
        assertEquals(ANNA, plan.address)
        assertEquals(NEWS_PDF, plan.attachment!!.displayName)
        assertEquals(1, h.sendHost.device.pressed.size)
        assertTrue(h.lastSaid, h.lastSaid.endsWith("3. E-Mail an $ANNA über Gmail gesendet, diese Datei hängt an."))
    }

    @Test fun textThatIsNotAFileStillWaitsForTheSendLanesOwnConfirmation() {
        val h = news()
        h.sendHost.device.gmailWorks()
        h.sendHost.grant(SendChannel.GMAIL)
        // No save step: the mail would carry generated text, so the send lane shows it once and asks.
        assertEquals("MISSION", h.owner("Fass mir die Nachrichten von heute zusammen und schick sie per Mail an $ANNA"))
        assertTrue(h.lastSaid, h.lastSaid.contains("Soll ich das so senden?"))
        assertTrue(h.sendHost.device.opened.isEmpty())
        assertTrue(h.mission.active)
        assertEquals("SEND", h.owner("Ja"))
        assertEquals(1, h.sendHost.device.pressed.size)
        assertTrue(h.lastSaid, h.lastSaid.startsWith("Fertig – beide Schritte sind erledigt."))
        assertTrue(h.lastSaid, h.lastSaid.endsWith("2. E-Mail an $ANNA über Gmail gesendet, ohne Anhang."))
    }

    @Test fun theNewestPdfIsFoundByTheChainAndOpenedByTheDesk() {
        val h = argon()
        h.deskHost.put("Angebot.pdf", pages = listOf("Angebot für Musterstadt."))
        h.device["list_files"] = DOWNLOAD_LISTING
        assertEquals("MISSION", h.owner("Such das neueste PDF in Downloads und öffne es"))
        assertEquals("Ich mache das in zwei Schritten: 1. Die neueste Datei suchen (Ordner Downloads lesen). 2. Die gefundene Datei öffnen.",
            h.missionSaid.first())
        assertEquals(listOf("list_files"), h.chainCalls)
        assertEquals("Angebot.pdf", h.deskHost.shown.single().name)
        assertEquals("Fertig – beide Schritte sind erledigt. 1. „Angebot.pdf“ gefunden. 2. „Angebot.pdf“ geöffnet (Ordner Download).", h.lastSaid)
    }

    @Test fun withoutTheDeviceBridgeTheSearchFailsAndNothingIsOpened() {
        val h = argon()
        h.bridgeReady = false
        assertEquals("MISSION", h.owner("Such das neueste PDF in Downloads und öffne es"))
        assertTrue(h.deskHost.shown.isEmpty())
        assertEquals(0, h.deskHost.scans)
        assertEquals("Die Auftragsfolge endet bei Schritt 1 von 2. Erledigt war noch nichts. Nicht geklappt: 1. Die neueste Datei suchen " +
            "(Ordner Downloads lesen) – Der Gerätezugriff für die Dateisuche ist nicht bereit; es wurde nichts gesucht. Nicht begonnen: " +
            "2. Die gefundene Datei öffnen.", h.lastSaid)
        assertFalse(h.mission.active)
    }

    // ── Pause and resume ────────────────────────────────────────────────────────────────────────────────────

    @Test fun aChoiceOfTheDeskPausesTheMissionAndTheAnswerLetsItContinueByItself() {
        val h = argon()
        h.deskHost.put("Argon4 Datenblatt.pdf", folder = "Documents", pages = listOf("Datenblatt."))
        h.deskHost.put("Argon4 Handbuch.pdf", folder = "Documents", pages = listOf("Handbuch."))
        h.deskHost.library.removeAll { it.name == "Argon4.pdf" }
        assertEquals("MISSION", h.owner("Öffne das PDF Argon4, fass Seite 1 zusammen, speichere es als Word und schick es per Mail an $ANNA"))
        assertTrue(h.lastSaid, h.lastSaid.contains("Welches soll ich öffnen?"))
        assertTrue(h.mission.active)
        assertTrue(h.downloads().isEmpty() && h.toolsHost.mails.isEmpty())
        assertEquals(listOf("1/3 Das PDF „Argon4“ suchen und öffnen:WAITING"), h.steps)
        // The answer goes to the desk; when the PDF is open the mission goes on with steps 2 and 3.
        val second = h.deskHost.hitsShown.last()[1]
        assertEquals("DESK", h.owner("das zweite"))
        assertEquals(second, h.deskHost.shown.single().name)
        assertEquals(1, h.downloads().size)
        assertEquals(h.downloads().single(), h.toolsHost.mails.single().attachment!!.displayName)
        assertTrue(h.lastSaid, h.lastSaid.startsWith("Fertig – alle drei Schritte sind erledigt. 1. „$second“ geöffnet"))
    }

    @Test fun aMissingStorageGrantPausesTheMissionUntilTheOwnerHasGrantedIt() {
        val h = argon()
        h.deskHost.libraryAccess = OwnerLibraryAccess.MISSING
        assertEquals("MISSION", h.owner(argonOrder))
        assertEquals(OwnerDocumentDesk.GRANT_MISSING, h.lastSaid)
        assertTrue(h.mission.active)
        assertEquals("DESK", h.owner("Ja"))
        assertEquals(1, h.deskHost.settingsOpened)
        assertTrue(h.downloads().isEmpty())
        // The owner comes back with the grant: the desk's search continues, and with it the mission.
        h.deskHost.libraryAccess = OwnerLibraryAccess.GRANTED
        h.desk.onResumed()
        h.settle()
        assertEquals(listOf(WORD_2), h.downloads())
        assertEquals(WORD_2, h.toolsHost.mails.single().attachment!!.displayName)
        assertEquals(argonDone, h.lastSaid)
    }

    @Test fun aNoToTheGrantEndsTheMissionWithNothingDone() {
        val h = argon()
        h.deskHost.libraryAccess = OwnerLibraryAccess.MISSING
        h.owner(argonOrder)
        assertEquals("DESK", h.owner("Nein"))
        assertFalse(h.mission.active)
        assertEquals("Die Auftragsfolge ist gestoppt. Erledigt war noch nichts. Abgebrochen: 1. Das PDF „Argon4“ suchen und öffnen. " +
            "Nicht begonnen: 2. Seite 2 zusammenfassen und als Word-Datei speichern. 3. Per E-Mail an $ANNA schicken.", h.lastSaid)
        assertEquals(0, h.deskHost.scans)
        assertTrue(h.downloads().isEmpty() && h.toolsHost.mails.isEmpty())
    }

    @Test fun anUnrelatedTurnLeavesTheMissionPausedAndSaysSoOnce() {
        val h = argon()
        h.deskHost.put("Argon4 Handbuch.pdf", folder = "Documents", pages = ARGON)
        h.deskHost.library.removeAll { it.name == "Argon4.pdf" }
        h.deskHost.put("Argon4 Datenblatt.pdf", folder = "Documents", pages = ARGON)
        h.owner(argonOrder)
        assertEquals("OTHER", h.owner("Wie spät ist es?"))
        val notice = "Die Auftragsfolge wartet noch bei Schritt 1 von 3 (Das PDF „Argon4“ suchen und öffnen). Mit „weiter“ mache ich " +
            "dort weiter, mit „abbrechen“ beende ich sie."
        assertEquals(notice, h.lastSaid)
        assertEquals("OTHER", h.owner("Und wie wird das Wetter?"))
        assertEquals(1, h.missionSaid.count { it == notice })
        assertTrue(h.mission.active)
        // „weiter“ hands step 1 to the desk again; it asks its question again and the answer continues the mission.
        assertEquals("MISSION", h.owner("weiter"))
        assertTrue(h.lastSaid, h.lastSaid.contains("Welches soll ich öffnen?"))
        val first = h.deskHost.hitsShown.last().first()
        assertEquals("DESK", h.owner("das erste"))
        assertEquals(first, h.deskHost.shown.single().name)
        assertTrue(h.lastSaid, h.lastSaid.startsWith("Fertig – alle drei Schritte sind erledigt."))
        assertEquals(1, h.toolsHost.mails.size)
    }

    @Test fun aNewOrderWhileTheMissionWaitsIsNotItsAnswerAndItsFailureIsNotTheMissions() {
        val h = argon()
        h.deskHost.put("Argon4 Handbuch.pdf", folder = "Documents", pages = ARGON)
        h.owner(argonOrder.replace("Argon4,", "Argon,"))
        assertTrue(h.lastSaid, h.lastSaid.contains("Welches soll ich öffnen?"))
        // Another order for the same lane: it fails on its own; the mission keeps waiting and says so.
        assertEquals("DESK", h.owner("Öffne das PDF Xenon5"))
        assertTrue(h.mission.active)
        assertEquals("Die Auftragsfolge wartet noch bei Schritt 1 von 3 (Das PDF „Argon“ suchen und öffnen). Mit „weiter“ mache ich " +
            "dort weiter, mit „abbrechen“ beende ich sie.", h.lastSaid)
        assertTrue(h.downloads().isEmpty() && h.toolsHost.mails.isEmpty())
        assertEquals("MISSION", h.owner("weiter"))
        // The answer may name the hit as a whole order; it is still the answer.
        assertEquals("DESK", h.owner("Öffne das PDF Argon4 Handbuch"))
        assertEquals("Argon4 Handbuch.pdf", h.deskHost.shown.single().name)
        assertEquals(1, h.toolsHost.mails.size)
        assertTrue(h.lastSaid, h.lastSaid.startsWith("Fertig – alle drei Schritte sind erledigt. 1. „Argon4 Handbuch.pdf“ geöffnet"))
    }

    @Test fun abbrechenEndsAWaitingMissionAndTheNextTurnIsNoLongerItsAnswer() {
        val h = argon()
        h.deskHost.put("Argon4 Handbuch.pdf", folder = "Documents", pages = ARGON)
        h.owner(argonOrder.replace("Argon4,", "Argon,"))
        assertTrue(h.mission.active)
        assertEquals("MISSION", h.owner("abbrechen"))
        assertFalse(h.mission.active)
        assertTrue(h.lastSaid, h.lastSaid.startsWith("Die Auftragsfolge ist gestoppt. Erledigt war noch nichts. Abgebrochen: 1. "))
        assertTrue(h.downloads().isEmpty() && h.toolsHost.mails.isEmpty())
        assertEquals("OTHER", h.owner("weiter"))
    }

    @Test fun aMissionThatWaitedTooLongIsGivenUp() {
        val h = argon()
        h.deskHost.libraryAccess = OwnerLibraryAccess.MISSING
        h.owner(argonOrder)
        h.now += OwnerMissionDirector.PAUSE_MILLIS + 1
        assertFalse(h.mission.active)
        assertEquals("OTHER", h.owner("weiter"))
    }

    // ── Corrections ─────────────────────────────────────────────────────────────────────────────────────────

    @Test fun aCorrectionOfAStepThatHasNotRunChangesThePlan() {
        val h = argon()
        h.deskHost.put("Argon4 Datenblatt.pdf", folder = "Documents", pages = ARGON)
        h.owner(argonOrder.replace("Argon4,", "Argon,"))
        assertTrue(h.lastSaid, h.lastSaid.contains("Welches soll ich öffnen?"))
        val first = h.deskHost.hitsShown.last().first().removeSuffix(".pdf")
        assertEquals("MISSION", h.owner("Ich meinte Seite 3"))
        assertEquals("Verstanden – Seite 3 statt Seite 2. Das gilt für Schritt 2; er ist noch nicht gelaufen.", h.lastSaid)
        assertEquals("MISSION", h.owner("an bernd@example.org, nicht an $ANNA"))
        assertEquals("Verstanden – an bernd@example.org statt an $ANNA. Das gilt für Schritt 3; er ist noch nicht gelaufen.", h.lastSaid)
        assertEquals("DESK", h.owner("das erste"))
        assertEquals(listOf("Zusammenfassung $first Seite 3 18.11.2026.docx"), h.downloads())
        assertEquals("bernd@example.org", h.toolsHost.mails.single().address)
        assertTrue(h.toolsHost.mails.single().body.contains(SUMMARY_3))
        assertTrue(h.lastSaid, h.lastSaid.startsWith("Fertig – mit deiner Korrektur sind alle drei Schritte erledigt."))
    }

    @Test fun aCorrectionWhileThePageIsBeingSummarisedRestartsThatStepAndTheMailCarriesTheCorrectedFile() {
        val h = argon()
        h.deskHost.duringGeneration = { assertTrue(h.interrupt("Ich meinte Seite 3")) }
        assertEquals("MISSION", h.owner(argonOrder))
        // Page 2 was stopped before anything was written; page 3 is what was saved and mailed.
        val word3 = "Zusammenfassung Argon4 Seite 3 18.11.2026.docx"
        assertEquals(listOf(word3), h.downloads())
        assertEquals(word3, h.toolsHost.mails.single().attachment!!.displayName)
        assertTrue(h.toolsHost.mails.single().body.contains(SUMMARY_3))
        assertTrue(h.said.any { it.startsWith("DESK:Verstanden – Seite 3 statt Seite 2.") })
        assertEquals("Fertig – mit deiner Korrektur sind alle drei Schritte erledigt. 1. „Argon4.pdf“ geöffnet (Ordner Download). " +
            "2. „$word3“ gespeichert (Interner Speicher › Download). 3. E-Mail an $ANNA im Mailprogramm vorbereitet, diese Datei hängt an – " +
            "gesendet habe ich nichts.", h.lastSaid)
    }

    @Test fun aFormatCorrectionAfterTheMissionRedoesTheSaveAndAsksBeforeItRepeatsTheMail() {
        val h = argon()
        h.owner(argonOrder)
        assertEquals("MISSION", h.owner("Nicht als Word, als PDF"))
        val pdf = "Zusammenfassung Argon4 Seite 2 18.11.2026.pdf"
        // The save is done again in the other format with the summary that was already accepted …
        assertEquals(listOf(WORD_2, pdf), h.downloads())
        assertEquals(1, h.deskHost.prompts.size)
        // … and the mail that already exists is not repeated without a yes.
        assertEquals(1, h.toolsHost.mails.size)
        assertEquals("Schritt 3 war schon erledigt: E-Mail an $ANNA im Mailprogramm vorbereitet, Anhang „$WORD_2“ – gesendet habe ich " +
            "nichts. Soll ich ihn mit dem neuen Ergebnis noch einmal ausführen?", h.lastSaid)
        assertEquals("MISSION", h.owner("Ja"))
        assertEquals(2, h.toolsHost.mails.size)
        assertEquals(pdf, h.toolsHost.mails.last().attachment!!.displayName)
        assertEquals("Fertig – mit deiner Korrektur sind alle drei Schritte erledigt. 1. „Argon4.pdf“ geöffnet (Ordner Download). " +
            "2. „$pdf“ gespeichert (Interner Speicher › Download). 3. E-Mail an $ANNA im Mailprogramm vorbereitet, diese Datei hängt an – " +
            "gesendet habe ich nichts. Von vorher gibt es weiterhin: „$WORD_2“ gespeichert (Interner Speicher › Download); E-Mail an " +
            "$ANNA im Mailprogramm vorbereitet, Anhang „$WORD_2“ – gesendet habe ich nichts (das lässt sich nicht zurücknehmen).", h.lastSaid)
    }

    @Test fun aNewRecipientWhileTheMissionAsksAboutTheMailIsTheAnswer() {
        val h = argon()
        h.owner(argonOrder)
        h.owner("Ich meinte als PDF")
        assertTrue(h.lastSaid, h.lastSaid.endsWith("Soll ich ihn mit dem neuen Ergebnis noch einmal ausführen?"))
        assertEquals("MISSION", h.owner("An bernd@example.org, nicht an $ANNA"))
        assertEquals(listOf(ANNA, "bernd@example.org"), h.toolsHost.mails.map { it.address })
        assertEquals("Zusammenfassung Argon4 Seite 2 18.11.2026.pdf", h.toolsHost.mails.last().attachment!!.displayName)
        assertTrue(h.said.any { it == "MISSION:Verstanden – an bernd@example.org statt an $ANNA. Ich mache Schritt 3 damit." })
        assertTrue(h.lastSaid, h.lastSaid.startsWith("Fertig – mit deiner Korrektur sind alle drei Schritte erledigt."))
    }

    @Test fun aCorrectionToWhatTheDeskAlreadySavedRedoesNothing() {
        val h = OwnerMissionHarness().apply {
            deskHost.put("Argon4.pdf", pages = ARGON.take(3))
            deskHost.model = { OwnerWorkGeneration(SUMMARY_3, null, true) }
        }
        h.owner("Öffne das PDF Argon4, fass die letzte Seite zusammen, speichere sie als Word und schick sie per Mail an $ANNA")
        val word3 = "Zusammenfassung Argon4 Seite 3 18.11.2026.docx"
        assertEquals(listOf(word3), h.downloads())
        val reports = h.missionSaid.size
        // The last page is page 3: the desk says that this is what it saved, and neither file nor mail is repeated.
        assertEquals("MISSION", h.owner("Ich meinte Seite 3"))
        assertTrue(h.lastSaid, h.lastSaid.startsWith("Genau das habe ich gerade gespeichert: „$word3“"))
        assertEquals(listOf(word3), h.downloads())
        assertEquals(1, h.toolsHost.mails.size)
        assertEquals(1, h.deskHost.prompts.size)
        assertEquals(reports + 1, h.missionSaid.size)
        assertFalse(h.mission.active)
    }

    @Test fun aNoToRepeatingTheMailLeavesItAsItWas() {
        val h = argon()
        h.owner(argonOrder)
        h.owner("Ich meinte als PDF")
        assertEquals("MISSION", h.owner("Nein"))
        assertEquals(1, h.toolsHost.mails.size)
        assertTrue(h.lastSaid, h.lastSaid.startsWith("Fertig bis auf das, was du nicht wiederholt haben wolltest."))
        assertTrue(h.lastSaid, h.lastSaid.contains("Nicht wiederholt: 3. Per E-Mail an $ANNA schicken."))
    }

    @Test fun aRecipientCorrectionAfterTheMissionPreparesTheMailForTheOtherRecipient() {
        val h = argon()
        h.owner(argonOrder)
        assertEquals("MISSION", h.owner("An bernd@example.org, nicht an $ANNA"))
        assertEquals(listOf(ANNA, "bernd@example.org"), h.toolsHost.mails.map { it.address })
        assertEquals(WORD_2, h.toolsHost.mails.last().attachment!!.displayName)
        assertEquals(listOf(WORD_2), h.downloads())
        assertTrue(h.lastSaid, h.lastSaid.contains("3. E-Mail an bernd@example.org im Mailprogramm vorbereitet, diese Datei hängt an"))
        assertTrue(h.lastSaid, h.lastSaid.endsWith("Von vorher gibt es weiterhin: E-Mail an $ANNA im Mailprogramm vorbereitet, Anhang " +
            "„$WORD_2“ – gesendet habe ich nichts (das lässt sich nicht zurücknehmen)."))
    }

    @Test fun anotherNamedPdfAfterTheMissionRedoesItFromTheFirstStep() {
        val h = argon()
        h.deskHost.put("Xenon5.pdf", pages = ARGON)
        h.owner(argonOrder)
        assertEquals("MISSION", h.owner("Nein, ich meinte das PDF Xenon5"))
        assertEquals(listOf("Argon4.pdf", "Xenon5.pdf"), h.deskHost.shown.map { it.name })
        val xenon = "Zusammenfassung Xenon5 Seite 2 18.11.2026.docx"
        assertEquals(listOf(WORD_2, xenon), h.downloads())
        // The mail with the first file already exists: it is repeated only after a yes.
        assertEquals(1, h.toolsHost.mails.size)
        assertTrue(h.lastSaid, h.lastSaid.startsWith("Schritt 3 war schon erledigt: E-Mail an $ANNA im Mailprogramm vorbereitet, Anhang „$WORD_2“"))
        assertEquals("MISSION", h.owner("Ja"))
        assertEquals(xenon, h.toolsHost.mails.last().attachment!!.displayName)
        assertTrue(h.lastSaid, h.lastSaid.startsWith("Fertig – mit deiner Korrektur sind alle drei Schritte erledigt. 1. „Xenon5.pdf“ geöffnet " +
            "(Ordner Download). 2. „$xenon“ gespeichert"))
        assertTrue(h.lastSaid, h.lastSaid.contains("Von vorher gibt es weiterhin: „Argon4.pdf“ geöffnet (Ordner Download); „$WORD_2“ gespeichert"))
    }

    @Test fun anotherNamedPdfWhileALaterStepRunsEndsThatStepAndStartsOver() {
        val h = argon()
        h.deskHost.put("Xenon5.pdf", pages = ARGON)
        h.deskHost.duringGeneration = { assertTrue(h.interrupt("Nein, ich meinte das PDF Xenon5")) }
        h.owner(argonOrder)
        val xenon = "Zusammenfassung Xenon5 Seite 2 18.11.2026.docx"
        // Nothing of the first PDF was saved or mailed; everything was done with the corrected one.
        assertEquals(listOf(xenon), h.downloads())
        assertEquals(xenon, h.toolsHost.mails.single().attachment!!.displayName)
        assertTrue(h.said.any { it == "MISSION:Verstanden – das PDF „Xenon5“ statt „Argon4“. Gerade läuft Schritt 2; ich breche ihn ab und " +
            "mache Schritt 1 mit der Änderung neu." })
        assertTrue(h.lastSaid, h.lastSaid.startsWith("Fertig – mit deiner Korrektur sind alle drei Schritte erledigt. 1. „Xenon5.pdf“ geöffnet"))
    }

    @Test fun aCorrectionThatChangesNothingIsAnsweredAndNothingIsRedone() {
        val h = argon()
        h.owner(argonOrder)
        assertEquals("MISSION", h.owner("Ich meinte Seite 2"))
        assertEquals("Genau so habe ich Schritt 2 gemacht: Seite 2 zusammenfassen und als Word-Datei speichern. Was soll ich anders machen?", h.lastSaid)
        assertEquals(listOf(WORD_2), h.downloads())
        assertEquals(1, h.toolsHost.mails.size)
    }

    @Test fun afterAnyOtherTurnTheFinishedMissionIsNoLongerCorrected() {
        val h = argon()
        h.owner(argonOrder)
        assertEquals("OTHER", h.owner("Danke dir"))
        // Now the correction belongs to no mission; the recipient sentence is nobody's order.
        assertEquals("OTHER", h.owner("An bernd@example.org, nicht an $ANNA"))
        assertEquals(1, h.toolsHost.mails.size)
    }

    // ── Failure and stop ────────────────────────────────────────────────────────────────────────────────────

    @Test fun aFailedStepEndsTheMissionAndSaysWhatWasDoneWhatFailedAndWhatWasNotStarted() {
        val h = argon()
        assertEquals("MISSION", h.owner(argonOrder.replace("Seite 2", "Seite 9")))
        assertEquals("Die Auftragsfolge endet bei Schritt 2 von 3. Erledigt: 1. „Argon4.pdf“ geöffnet (Ordner Download). Nicht geklappt: " +
            "2. Seite 9 zusammenfassen und als Word-Datei speichern – „Argon4.pdf“ hat nur 4 Seiten – eine Seite 9 gibt es nicht. " +
            "Nicht begonnen: 3. Per E-Mail an $ANNA schicken.", h.lastSaid)
        assertTrue(h.downloads().isEmpty() && h.toolsHost.mails.isEmpty())
        assertFalse(h.mission.active)
        assertEquals(false, h.stageEnded)
        assertEquals("2/3 Seite 9 zusammenfassen und als Word-Datei speichern:FAILED", h.steps.last())
    }

    @Test fun aFileThatDoesNotExistEndsTheMissionAtTheFirstStep() {
        val h = OwnerMissionHarness()
        assertEquals("MISSION", h.owner(argonOrder))
        assertTrue(h.lastSaid, h.lastSaid.startsWith("Die Auftragsfolge endet bei Schritt 1 von 3. Erledigt war noch nichts. Nicht geklappt: " +
            "1. Das PDF „Argon4“ suchen und öffnen – Ein PDF namens „Argon4“ habe ich nicht gefunden."))
        assertTrue(h.lastSaid, h.lastSaid.endsWith("Nicht begonnen: 2. Seite 2 zusammenfassen und als Word-Datei speichern. 3. Per E-Mail an $ANNA schicken."))
        assertEquals(0, h.deskHost.prompts.size)
    }

    @Test fun aSaveThatFailsIsReportedAndNothingIsMailed() {
        val h = news()
        h.toolsHost.saveProblem = "Kein Speicherplatz"
        assertEquals("MISSION", h.owner(newsOrder))
        assertTrue(h.lastSaid, h.lastSaid.contains("Nicht geklappt: 2. Als PDF speichern – Das Speichern hat nicht geklappt: Kein Speicherplatz."))
        assertTrue(h.lastSaid, h.lastSaid.endsWith("Nicht begonnen: 3. Per E-Mail an $ANNA schicken."))
        assertTrue(h.downloads().isEmpty() && h.toolsHost.mails.isEmpty())
    }

    @Test fun aDeclinedWebConsentEndsTheNewsMissionAndNothingIsSavedOrMailed() {
        val h = news(InternetAccessMode.ASK)
        h.toolsHost.ownerApproves = false
        assertEquals("MISSION", h.owner(newsOrder))
        assertTrue(h.toolsHost.fetched.isEmpty())
        assertEquals("Die Auftragsfolge endet bei Schritt 1 von 3. Erledigt war noch nichts. Nicht geklappt: 1. Die Nachrichten von heute " +
            "zusammenfassen – Der Internetzugriff wurde für die Nachrichten nicht freigegeben. Nicht begonnen: 2. Als PDF speichern. " +
            "3. Per E-Mail an $ANNA schicken.", h.lastSaid)
        assertTrue(h.downloads().isEmpty() && h.toolsHost.mails.isEmpty())
    }

    @Test fun withTheOneTimeWebConsentTheNewsMissionRunsAfterTheOwnersTap() {
        val h = news(InternetAccessMode.ASK)
        assertEquals("MISSION", h.owner(newsOrder))
        assertEquals(1, h.toolsHost.consents.size)
        assertEquals(listOf(NEWS_PDF), h.downloads())
        assertEquals(NEWS_PDF, h.toolsHost.mails.single().attachment!!.displayName)
    }

    @Test fun aLongReasonAndALongSummaryStayTheLanesOwnAndTheReportStaysShort() {
        // A long summary is the desk's message; the mission's report names the file, not the text.
        val long = (1..40).joinToString(" ") { "Satz $it der Zusammenfassung beschreibt den Abruf der Erinnerungen genauer." }
        val h = argon()
        h.deskHost.model = { OwnerWorkGeneration(long, null, true) }
        h.owner(argonOrder)
        assertTrue(h.said.any { it.startsWith("DESK:Seite 2 von „Argon4.pdf“") && it.length > 2_000 })
        assertEquals(argonDone, h.lastSaid)
        // A long failure reason is said in full by its lane once; the report keeps its first sentence.
        val failing = news()
        failing.toolsHost.saveProblem = "Der Datenträger meldet einen Fehler. " + "Weitere Einzelheiten folgen hier. ".repeat(60)
        failing.owner(newsOrder)
        assertTrue(failing.lastSaid, failing.lastSaid.contains("Nicht geklappt: 2. Als PDF speichern – Das Speichern hat nicht geklappt: " +
            "Der Datenträger meldet einen Fehler. Nicht begonnen: 3. Per E-Mail an $ANNA schicken."))
        assertTrue(failing.lastSaid.length < 600)
    }

    @Test fun withInternetOffTheNewsMissionEndsAtTheFirstStepWithoutAnyRequest() {
        val h = news(InternetAccessMode.OFF)
        assertEquals("MISSION", h.owner(newsOrder))
        assertTrue(h.toolsHost.fetched.isEmpty())
        assertTrue(h.lastSaid, h.lastSaid.startsWith("Die Auftragsfolge endet bei Schritt 1 von 3. Erledigt war noch nichts. Nicht geklappt: " +
            "1. Die Nachrichten von heute zusammenfassen – Internetzugang ist AUS, deshalb habe ich keine Nachrichten abgerufen."))
        assertTrue(h.downloads().isEmpty() && h.toolsHost.mails.isEmpty())
    }

    @Test fun stopWhileSearchingEndsTheMissionBeforeAnythingWasOpened() {
        val h = argon()
        h.deskHost.duringScan = { assertTrue(h.interrupt("Stopp")) }
        h.owner(argonOrder)
        assertTrue(h.deskHost.shown.isEmpty())
        assertEquals("Die Auftragsfolge ist gestoppt. Erledigt war noch nichts. Abgebrochen: 1. Das PDF „Argon4“ suchen und öffnen. " +
            "Nicht begonnen: 2. Seite 2 zusammenfassen und als Word-Datei speichern. 3. Per E-Mail an $ANNA schicken.", h.lastSaid)
        assertFalse(h.mission.active)
    }

    @Test fun stopWhileSummarisingSavesNothingAndStartsNoMail() {
        val h = argon()
        h.deskHost.duringGeneration = { assertTrue(h.interrupt("Stopp")) }
        h.owner(argonOrder)
        assertTrue(h.downloads().isEmpty() && h.toolsHost.mails.isEmpty())
        assertTrue(h.said.any { it == "DESK:Gestoppt. Seite 2 wurde nicht fertig; es wurde nichts gespeichert." })
        assertEquals("Die Auftragsfolge ist gestoppt. Erledigt: 1. „Argon4.pdf“ geöffnet (Ordner Download). Abgebrochen: 2. Seite 2 " +
            "zusammenfassen und als Word-Datei speichern. Nicht begonnen: 3. Per E-Mail an $ANNA schicken.", h.lastSaid)
    }

    @Test fun aStopThatArrivesAfterTheFileWasWrittenReportsTheFileAndStartsNoMail() {
        val h = argon()
        h.deskHost.beforeSave = { assertTrue(h.interrupt("Stopp")) }
        h.owner(argonOrder)
        assertEquals(listOf(WORD_2), h.downloads())
        assertTrue(h.toolsHost.mails.isEmpty())
        assertEquals("Die Auftragsfolge ist gestoppt. Erledigt: 1. „Argon4.pdf“ geöffnet (Ordner Download). 2. „$WORD_2“ gespeichert " +
            "(Interner Speicher › Download). Nicht begonnen: 3. Per E-Mail an $ANNA schicken.", h.lastSaid)
    }

    @Test fun theStopControlDuringTheNewsDropsTheBriefingAndTheRemainingSteps() {
        val h = news()
        // The STOP control arrives while the briefing is generated: the Activity drops the result of that job.
        h.toolsHost.cancelDuringGeneration = true
        h.toolsHost.model = { h.mission.stopRequested(); OwnerWorkGeneration(BRIEFING, null, true) }
        h.owner(newsOrder)
        assertTrue(h.downloads().isEmpty() && h.toolsHost.mails.isEmpty())
        assertEquals("Die Auftragsfolge ist gestoppt. Erledigt war noch nichts. Abgebrochen: 1. Die Nachrichten von heute zusammenfassen. " +
            "Nicht begonnen: 2. Als PDF speichern. 3. Per E-Mail an $ANNA schicken.", h.lastSaid)
    }

    // ── Verification between steps, bounds, follow-ups ──────────────────────────────────────────────────────

    @Test fun aStepIsNotStartedOnAResultThatIsNoLongerWhatTheChatIsAbout() {
        val h = argon()
        h.toolsHost.model = { OwnerWorkGeneration(BRIEFING, null, true) }
        h.owner(argonOrder)
        h.owner("Ich meinte als PDF")
        assertTrue(h.lastSaid, h.lastSaid.endsWith("Soll ich ihn mit dem neuen Ergebnis noch einmal ausführen?"))
        // Before he answers, the owner asks for something else that becomes the chat's result.
        assertEquals("TOOLS", h.owner("Fass mir die Nachrichten von heute zusammen"))
        assertEquals("MISSION", h.owner("weiter"))
        assertEquals(1, h.toolsHost.mails.size)
        assertTrue(h.lastSaid, h.lastSaid.contains("Nicht geklappt: 3. Per E-Mail an $ANNA schicken – Das Ergebnis aus Schritt 2 ist nicht " +
            "mehr das, worum es in diesem Gespräch geht."))
    }

    @Test fun aLaneThatIsOccupiedHoldsTheMissionUntilTheOwnerSaysWeiter() {
        val h = argon()
        h.owner("Öffne das PDF Argon4")
        // Something else occupies the single work lane when the mission wants to start its first step.
        h.deskHost.busyElsewhere = true
        assertEquals("MISSION", h.owner("Fass Seite 2 zusammen, speichere es als Word und schick es per Mail an $ANNA"))
        assertEquals("Gerade läuft noch etwas anderes. Die Auftragsfolge wartet bei Schritt 1 von 2 (Seite 2 zusammenfassen und als " +
            "Word-Datei speichern). Sag „weiter“, wenn ich dort weitermachen soll.", h.lastSaid)
        assertTrue(h.downloads().isEmpty())
        h.deskHost.busyElsewhere = false
        assertEquals("MISSION", h.owner("weiter"))
        assertEquals(listOf(WORD_2), h.downloads())
        assertEquals(WORD_2, h.toolsHost.mails.single().attachment!!.displayName)
    }

    @Test fun theTimeLimitEndsTheMissionBetweenTwoSteps() {
        val h = argon()
        h.deskHost.onAdopt = { _, _ -> h.now += OwnerMissionDirector.MAX_TOTAL_MILLIS + 1 }
        h.owner(argonOrder)
        assertTrue(h.toolsHost.mails.isEmpty())
        assertEquals("Die Auftragsfolge endet hier: Ihre Grenze für Zeit und Wiederholungen ist erreicht. Erledigt: 1. „Argon4.pdf“ " +
            "geöffnet (Ordner Download). 2. „$WORD_2“ gespeichert (Interner Speicher › Download). Nicht begonnen: 3. Per E-Mail an " +
            "$ANNA schicken.", h.lastSaid)
    }

    @Test fun resumingAgainAndAgainRunsIntoTheBoundInsteadOfALoop() {
        val h = argon()
        h.deskHost.libraryAccess = OwnerLibraryAccess.MISSING
        h.owner(argonOrder)
        repeat(OwnerMissionDirector.MAX_RUNS + 2) { if (h.mission.active) h.owner("weiter") }
        assertFalse(h.mission.active)
        assertTrue(h.lastSaid, h.lastSaid.startsWith("Die Auftragsfolge endet hier: Ihre Grenze für Zeit und Wiederholungen ist erreicht."))
        assertEquals(0, h.deskHost.scans)
    }

    @Test fun theSavedResultStaysTheSubjectForFollowUpsAfterTheMission() {
        // The offer to open the saved file ended when the mission went on to the mail: „Danke“ answers nothing.
        val h = argon()
        h.owner(argonOrder)
        assertEquals("OTHER", h.owner("Danke"))
        assertTrue(h.toolsHost.opened.isEmpty())
        // Right after the mission „das“ of a single order still is the page summary it saved.
        val again = argon()
        again.owner(argonOrder)
        assertEquals("TOOLS", again.owner("Speichere das als PDF"))
        assertEquals(listOf(WORD_2, "Zusammenfassung Argon4 Seite 2 18.11.2026.pdf"), again.downloads())
        assertEquals("TOOLS", again.owner("Schick das per Mail an bernd@example.org"))
        assertEquals("bernd@example.org", again.toolsHost.mails.last().address)
    }

    @Test fun aMissionThatEndsWithASaveOffersToOpenTheFileOnce() {
        val h = argon()
        assertEquals("MISSION", h.owner("Öffne das PDF Argon4 und fass Seite 2 zusammen und speichere es als Word"))
        assertEquals(1, h.said.count { it.contains("öffnen?") })
        assertEquals("Fertig – beide Schritte sind erledigt. 1. „Argon4.pdf“ geöffnet (Ordner Download). 2. „$WORD_2“ gespeichert " +
            "(Interner Speicher › Download). Soll ich die Datei öffnen?", h.lastSaid)
        assertEquals("TOOLS", h.owner("Ja"))
        assertEquals(WORD_2, h.toolsHost.opened.single().displayName)
    }

    @Test fun aNewMissionReplacesOneThatIsStillWaiting() {
        val h = argon()
        h.deskHost.libraryAccess = OwnerLibraryAccess.MISSING
        h.owner(argonOrder)
        h.toolsHost.model = { OwnerWorkGeneration(BRIEFING, null, true) }
        assertEquals("MISSION", h.owner(newsOrder))
        assertTrue(h.missionSaid.any { it.startsWith("Die frühere Auftragsfolge beende ich damit. Ich mache das in drei Schritten:") })
        assertEquals(listOf(NEWS_PDF), h.downloads())
    }

    @Test fun singleOrdersKeepTheirLanesExactlyAsBefore() {
        val h = argon()
        assertEquals("DESK", h.owner("Öffne das PDF Argon4"))
        assertEquals("DESK", h.owner("Fass Seite 2 zusammen und speichere es als Word"))
        assertEquals("TOOLS", h.owner("Schick das per Mail an $ANNA"))
        assertTrue(h.missionSaid.isEmpty())
        assertEquals(listOf(WORD_2), h.downloads())
        assertEquals(WORD_2, h.toolsHost.mails.single().attachment!!.displayName)
        assertNull(h.stageEnded)
    }

    // ── Model-proposed plans ────────────────────────────────────────────────────────────────────────────────

    private val freeGoal = "Mach mir aus Seite 2 vom PDF Argon4 eine Word-Datei, die dann per Mail an $ANNA geht"
    private val goodPlan = "1. Öffne das PDF Argon4\n2. Fass Seite 2 zusammen und speichere es als Word\n3. Schick das per Mail an $ANNA"

    @Test fun withTheSwitchOffNoPlanIsAskedOfTheModel() {
        val h = argon()
        h.modelPlan = { goodPlan }
        assertEquals("OTHER", h.owner(freeGoal))
        assertTrue(h.planPrompts.isEmpty() && h.downloads().isEmpty())
    }

    @Test fun aModelPlanIsOnlySpokenAndRunsAfterTheOwnersYes() {
        val h = argon()
        h.modelPlansOn = true
        h.modelPlan = { goodPlan }
        assertEquals("MISSION", h.owner(freeGoal))
        assertEquals("Ich würde das in drei Schritten machen: 1. Das PDF „Argon4“ suchen und öffnen. 2. Seite 2 zusammenfassen und als " +
            "Word-Datei speichern. 3. Per E-Mail an $ANNA schicken. Soll ich das so machen?", h.lastSaid)
        // Nothing has run: no search, no file, no mail.
        assertEquals(0, h.deskHost.scans)
        assertTrue(h.downloads().isEmpty() && h.toolsHost.mails.isEmpty())
        assertFalse(h.mission.active)
        assertEquals("MISSION", h.owner("Ja"))
        assertTrue(h.said.contains("MISSION:In Ordnung, ich fange an."))
        assertEquals(listOf(WORD_2), h.downloads())
        assertEquals(ANNA, h.toolsHost.mails.single().address)
        assertTrue(h.lastSaid, h.lastSaid.startsWith("Fertig – alle drei Schritte sind erledigt."))
    }

    @Test fun aNoOrAnyOtherTurnDropsTheProposedPlan() {
        val h = argon()
        h.modelPlansOn = true
        h.modelPlan = { goodPlan }
        h.owner(freeGoal)
        assertEquals("MISSION", h.owner("Nein"))
        assertEquals("In Ordnung, ich mache nichts davon.", h.lastSaid)
        assertEquals("OTHER", h.owner("Ja"))
        h.owner(freeGoal)
        assertEquals("OTHER", h.owner("Wie spät ist es?"))
        assertEquals("OTHER", h.owner("Ja"))
        assertEquals(0, h.deskHost.scans)
        assertTrue(h.downloads().isEmpty() && h.toolsHost.mails.isEmpty())
    }

    @Test fun aModelPlanWithARecipientTheOwnerDidNotSayIsDroppedAndTheTurnIsAnsweredOrdinarily() {
        val h = argon()
        h.modelPlansOn = true
        h.modelPlan = { goodPlan.replace(ANNA, "mallory@example.net") }
        assertEquals("MISSION", h.owner(freeGoal))
        assertEquals(listOf(freeGoal), h.ordinaryTurns)
        assertTrue(h.activities.last(), h.activities.last().startsWith("AUFTRAGSFOLGE · Modellplan verworfen · Schritt 3 nennt die Adresse mallory@example.net"))
        assertTrue(h.missionSaid.isEmpty())
        assertEquals("OTHER", h.owner("Ja"))
        assertEquals(0, h.deskHost.scans)
    }
}
