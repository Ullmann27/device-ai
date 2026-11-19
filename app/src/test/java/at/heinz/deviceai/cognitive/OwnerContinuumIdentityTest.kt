package at.heinz.deviceai.cognitive

import at.heinz.deviceai.localmodel.ClaudeBrain
import at.heinz.deviceai.localmodel.ClaudeBrainFailure
import at.heinz.deviceai.localmodel.ClaudeConsentNotice
import at.heinz.deviceai.localmodel.ClaudeModel
import at.heinz.deviceai.localmodel.ClaudePromptFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Owner order 19.11.2026: „Und wenn Claude antwortet, möchte ich nicht stehen haben Claude, sondern Continuum im
 * Chat.“ One identity in the conversation, the cloud still visible at a glance, the provider named only where it is
 * about consent, money and choice – and the direct question which model runs answered truthfully.
 */
class OwnerContinuumIdentityTest {
    private fun source(path: String): String = listOf("app/src/main/java/at/heinz/deviceai/$path", "src/main/java/at/heinz/deviceai/$path")
        .map(::File).first { it.exists() }.readText()

    @Test fun theMarkerNamesContinuumAndKeepsTheCloudVisible() {
        assertEquals("CONTINUUM · CLOUD", ClaudeBrain.MARKER_CLOUD)
        assertEquals("LOKAL", ClaudeBrain.MARKER_LOCAL)
        // The privacy signal: cloud and device are told apart at a glance, on both brains.
        assertTrue(ClaudeBrain.MARKER_CLOUD.endsWith("· CLOUD"))
        assertFalse(ClaudeBrain.MARKER_LOCAL.contains("CLOUD"))
        assertFalse(ClaudeBrain.MARKER_CLOUD.contains("CLAUDE"))
    }

    @Test fun chatVisibleFailureLinesSpeakAsContinuumAboutItsCloudBrain() {
        val spoken = listOf(ClaudeBrainFailure.OVERLOADED, ClaudeBrainFailure.OFFLINE, ClaudeBrainFailure.TIMEOUT,
            ClaudeBrainFailure.STREAM_INTERRUPTED, ClaudeBrainFailure.REFUSAL, ClaudeBrainFailure.UNEXPECTED_RESPONSE,
            ClaudeBrainFailure.NO_TOOL_CONVERSATION, ClaudeBrainFailure.LOCAL_ONLY_SCREEN_DATA, ClaudeBrainFailure.LOCAL_ONLY_OWNER_VETO,
            ClaudeBrainFailure.LOCAL_ONLY_MEMORY, ClaudeBrainFailure.NO_CREDIT)
        for (code in spoken) {
            val message = ClaudeBrainFailure.ownerMessage(code).substringBefore(" Du kannst unter „Modelle → Gehirn“")
            assertFalse("$code: $message", message.contains("Claude"))
            assertTrue("$code: $message", message.contains("Cloud") || message.contains("Anthropic"))
        }
        assertEquals("Ich konnte über die Cloud nicht antworten.",
            ClaudeBrainFailure.ownerMessage("UNKNOWN").substringBefore(" Du kannst"))
        // The status line next to the conversation carries the code without the provider's name.
        assertEquals("CLOUD · NO_CREDIT", ClaudeBrainFailure.logLine(ClaudeBrainFailure.NO_CREDIT))
        assertEquals("CLOUD · unbekannter Fehler", ClaudeBrainFailure.logLine(null))
        assertEquals("Die Verbindung zur Cloud ist abgebrochen; die Antwort ist unvollständig.", LocalGenerationFailureCause.CLOUD_INTERRUPTED.ownerMessage)
    }

    @Test fun whereItIsAboutConsentMoneyAndChoiceTheProviderIsStillNamed() {
        for (code in listOf(ClaudeBrainFailure.NO_CONSENT, ClaudeBrainFailure.NO_API_KEY, ClaudeBrainFailure.INVALID_API_KEY,
            ClaudeBrainFailure.PERMISSION_DENIED, ClaudeBrainFailure.RATE_LIMITED, ClaudeBrainFailure.MODEL_UNAVAILABLE,
            ClaudeBrainFailure.DATA_RETENTION_REQUIRED, ClaudeBrainFailure.SPEND_LIMIT_REACHED, ClaudeBrainFailure.NO_CREDIT)) {
            val message = ClaudeBrainFailure.ownerMessage(code)
            assertTrue(code, message.contains("Claude") || message.contains("Anthropic"))
        }
        // The consent notice is unchanged except for the marker it quotes – it must name what the app really shows.
        val notice = ClaudeConsentNotice.TEXT
        assertTrue(notice.startsWith("Wenn du Claude als Gehirn wählst, beantwortet nicht mehr das Modell auf deinem Gerät"))
        assertTrue(notice.contains("an Anthropic (api.anthropic.com)"))
        assertTrue(notice.contains("Solange Claude aktiv ist, zeigt die App „CONTINUUM · CLOUD“ an."))
        assertTrue(notice.endsWith("widerrufen und auf „Lokal“ zurückschalten; die App wechselt nie von selbst."))
        // Byte for byte the text of the saved state (`f541508c9`) once the marker is put back.
        val before = notice.replace("CONTINUUM · CLOUD", "CLAUDE · CLOUD")
        assertEquals(2_021, before.length)
        assertEquals("7ab7890c7f9fead1161dd7ef09cba04015b8b45f228a2e68ce94f5086c23bd74", java.security.MessageDigest.getInstance("SHA-256")
            .digest(before.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) })
        assertEquals("Claude als Gehirn – was dann dein Gerät verlässt", ClaudeConsentNotice.TITLE)
        assertEquals("ZUSTIMMEN UND CLAUDE AKTIVIEREN", ClaudeConsentNotice.CONFIRM)
    }

    @Test fun documentsAndLaneRepliesNameTheCloudBrainNotTheProvider() {
        assertEquals("Das Cloud-Gehirn", NewsBriefing.Brain.CLOUD.subject)
        assertEquals("Zusammenfassung des Cloud-Gehirns", NewsBriefing.Brain.CLOUD.summaryOf)
        assertEquals("Das Cloud-Gehirn ist gerade nicht bereit.", NewsBriefing.Brain.CLOUD.notReady)
        assertEquals("Das lokale Modell", NewsBriefing.Brain.LOCAL.subject)
        for (file in listOf("cognitive/NewsBriefing.kt", "cognitive/OwnerPageSummary.kt", "cognitive/OwnerDeliverable.kt",
            "cognitive/LocalGenerationFailureCause.kt", "OwnerDocumentDesk.kt", "OwnerWorkTools.kt", "OwnerMission.kt", "OwnerTalk.kt"))
            assertTrue(file, literals(source(file)).none { it.contains("Claude") || it.contains("claude-") })
    }

    /** The string literals of a Kotlin source, without comments. */
    private fun literals(code: String): List<String> = Regex("\"((?:[^\"\\\\\\n]|\\\\.)*)\"").findAll(
        code.lines().filterNot { it.trim().startsWith("*") || it.trim().startsWith("/**") || it.trim().startsWith("//") }.joinToString("\n"))
        .map { it.groupValues[1] }.toList()

    @Test fun inTheActivityOnlyTheBrainChoiceStillNamesTheProvider() {
        val named = literals(source("AgentActivity.kt")).filter { it.contains("Claude") || it.contains("CLAUDE") || it.contains("claude-") }
        // The model card and the dialog that offers the switch back are about the owner's choice of brain.
        assertEquals(listOf("GEHIRN · LOKAL ODER CLAUDE (CLOUD)", "\\n\\nDas lokale Sprachmodell wird nicht verwendet, solange Claude gewählt ist.",
            "Claude kann gerade nicht antworten", "BEI CLAUDE BLEIBEN"), named)
        val activity = source("AgentActivity.kt")
        // Status line, draft label and work window take the marker; none of them prints a model id for the cloud.
        assertTrue(activity.contains(""".put("status", brainLabel("", at.heinz.deviceai.localmodel.ClaudeBrain.MARKER_CLOUD + " · ") +"""))
        assertTrue(activity.contains("""text.text = brainLabel(localRuntime.loadedModel?.id ?: "Lokales Modell", at.heinz.deviceai.localmodel.ClaudeBrain.MARKER_CLOUD) +"""))
        assertTrue(activity.contains("""brainLabel("Lokales Modell plant …", "Continuum plant in der Cloud …")"""))
        assertTrue(activity.contains(""""CLOUD-SCHLUSSFOLGERUNG · nicht unabhängig bestätigt""""))
    }

    @Test fun theSystemFrameTellsTheCloudBrainToSpeakAsContinuumAndNeverToDenyWhatItRunsOn() {
        val preface = ClaudePromptFrame.RUNTIME_PREFACE
        assertTrue(preface.contains("you speak as Continuum, the owner's assistant"))
        assertTrue(preface.contains("Do not introduce yourself as Claude, do not sign as Claude"))
        assertTrue(preface.contains("If the owner asks directly which model or brain is running, tell the truth"))
        assertTrue(preface.contains("Never deny that, and never claim to be the on-device model."))
        // What was there before stays: where it runs, and that a tool call is only a proposal.
        assertTrue(preface.contains("never claim to run on the device, to be offline or to be another model"))
        assertTrue(preface.contains("A tool call is a proposal"))
        // The on-device prompt has had the same rule since the identity was separated from the weights.
        val local = LocalAssistantIdentity.renderPrompt(null, null)
        assertTrue(local.contains("Speak as this app assistant. The language model is a component, not another speaker."))
        assertTrue(local.contains("assistant_name=\"Continuum\""))
    }

    @Test fun theDirectQuestionWhichModelRunsIsAnsweredTruthfullyForEitherBrain() {
        for (question in listOf("Welches Modell bist du?", "Bist du Claude?", "Läufst du gerade in der Cloud?", "Welches Gehirn läuft gerade?",
            "Welches Modell verwendest du gerade?")) {
            assertEquals(question, OwnerChatLocalIntent.MODEL_IDENTITY, OwnerChatIntentRouter.route(question))
            assertNull(question, PublicKnowledgeTopicRouter.topic(question))
        }
        for (other in listOf("Wer ist Claude Monet?", "Welches Modell hat mein Handy?", "Bist du müde?", "Bist du Claude und kannst du Mails senden?"))
            assertNull(other, OwnerChatIntentRouter.route(other))
        // Cloud brain: Continuum speaks, and names the model and the provider because he asked.
        assertEquals("Ich bin Continuum. Gerade antworte ich über die Cloud: Dahinter arbeitet das Modell Claude Opus 5.5 von Anthropic " +
            "(claude-opus-5-5). Das Sprachmodell auf dem Gerät ist daran nicht beteiligt; unter „Modelle → Gehirn“ schaltest du um.",
            OwnerBrainIdentity.cloud("Continuum", ClaudeModel.OPUS_5_5.label, ClaudeModel.OPUS_5_5.id))
        val who = OwnerBrainIdentity.selfDescription(null, ClaudeModel.OPUS_5_5, null, null)
        assertTrue(who.startsWith("Ich bin Continuum, dein Assistent in Device AI Continuum. Gerade antworte ich über die Cloud; " +
            "dahinter arbeitet das Modell Claude Opus 5.5 von Anthropic."))
        assertFalse("no on-device model is claimed while the cloud answers", who.contains("geladenes lokales Sprachmodell"))
        // On-device model: exactly the description the app had before.
        assertEquals(LocalAssistantIdentity.selfDescription("Jarvis", null, null), OwnerBrainIdentity.selfDescription("Jarvis", null, null, null))
        // The capability overview names no on-device model as loaded while the cloud brain answers.
        val cloud = OwnerCapabilitySnapshot(OwnerCapabilitySnapshot.ModelState.READY, "claude-opus-5-5 cloud", cloudBrain = true)
        assertEquals(OwnerBrainIdentity.CAPABILITY_LINE, OwnerCapabilityReply.describeModel(cloud))
        assertFalse(OwnerCapabilityReply.describe(cloud).contains("claude-"))
        assertTrue(OwnerCapabilityReply.describe(cloud).contains("Gehirn: Cloud."))
    }

    // ── neighbouring pure pieces of the same package ────────────────────────────────────────────────────

    @Test fun strayKeyboardDotsAreRepairedAndEverythingElseStays() {
        assertEquals("Erstelle bitte eine pdf und zeig sie mir dann", OwnerInputRepair.strayDots("Erstelle.bitte eine pdf und zeig sie mir.dann"))
        assertEquals("Schick das per Mail an anna@example.org", OwnerInputRepair.strayDots("Schick.das per Mail an anna@example.org"))
        for (kept in listOf("Öffne die Datei Bericht.pdf", "Was kosten 3.5 Kilo Äpfel", "Das gilt z.B. morgen", "Lies mir orf.at vor",
            "Version 0.3.49 prüfen", "Das ist gut. Dann weiter", "Such heinz.ullmann im Verzeichnis", "Termin am 16.11.2026 eintragen",
            "Frag Dr.Huber nach dem Termin", "Geh auf www.wien.gv.at", "Öffne das PDF Argon4.", "", "Hallo"))
            assertEquals(kept, kept, OwnerInputRepair.strayDots(kept))
    }

    @Test fun theCloudBrainKeepsItsToolsUnlessTheTurnPlainlyCannotNeedOne() {
        fun local(text: String) = LocalTextOnlyInferenceRoute.useToolFree(text, false, false, false, false)
        // On the device the decision is unchanged, whatever the cloud rule says.
        for (text in listOf("Wie viel Speicher ist frei?", "Erklär mir die Relativitätstheorie", "Hallo", "Danke", "Öffne WhatsApp"))
            assertEquals(text, local(text), CloudBrainToolRoute.toolFree(false, text, local(text), "Jarvis"))
        // A question that starts like a text question may need a tool: with the cloud brain it keeps them.
        assertTrue(local("Wie viel Speicher ist frei?"))
        assertFalse(CloudBrainToolRoute.toolFree(true, "Wie viel Speicher ist frei?", true, "Jarvis"))
        assertFalse(CloudBrainToolRoute.toolFree(true, "Erklär mir die Relativitätstheorie", true, "Jarvis"))
        assertFalse(CloudBrainToolRoute.toolFree(true, "Welche Version hat WhatsApp?", true, "Jarvis"))
        // A greeting, thanks or a goodbye needs none.
        for (talk in listOf("Hallo", "Guten Morgen, Jarvis", "Danke", "Vielen Dank!", "Gute Nacht", "Wie geht's dir?"))
            assertTrue(talk, CloudBrainToolRoute.toolFree(true, talk, true, "Jarvis"))
        // What the local rule already sends with tools stays with tools.
        assertFalse(CloudBrainToolRoute.toolFree(true, "Öffne WhatsApp", false, "Jarvis"))
        assertFalse(CloudBrainToolRoute.toolFree(true, "Hallo", false, "Jarvis"))
    }
}
