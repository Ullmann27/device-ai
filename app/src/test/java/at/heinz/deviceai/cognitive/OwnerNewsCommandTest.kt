package at.heinz.deviceai.cognitive

import at.heinz.deviceai.BootstrapBrain
import at.heinz.deviceai.routines.OwnerRoutineCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class OwnerNewsCommandTest {
    private fun topics(utterance: String): List<String>? = OwnerNewsCommand.parse(utterance)?.topics?.map { it.label }

    @Test fun ownerPhrasingsStartTheGeneralBriefing() {
        listOf(
            "Fass mir die Nachrichten von heute zusammen",
            "Fasse mir alle Nachrichten von heute zusammen aus dem Internet",
            "Jarvis, fass mir bitte die globalen Nachrichten von heute zusammen.",
            "Was gibt's Neues in der Welt?",
            "Was gibt es Neues auf der Welt",
            "Nachrichten bitte",
            "Was ist heute passiert?",
            "Was ist heute in der Welt passiert?",
            "Schlagzeilen",
            "Die Schlagzeilen von heute bitte",
            "Gib mir einen Überblick über die Nachrichten",
            "Kannst du mir die wichtigsten Nachrichten von heute zusammenfassen?",
            "Was steht heute in den Nachrichten?",
            "Was sind die Schlagzeilen des Tages",
            "Weltnachrichten",
            "Guten Morgen, was gibt's Neues in der Welt",
            "Erzähl mir was Neues aus der Welt",
            "Ich möchte die Nachrichten hören",
            "Lies mir die Nachrichten von heute vor",
            "News"
        ).forEach { assertEquals(it, emptyList<String>(), topics(it)) }
    }

    @Test fun topicsAreRecognisedAsSectionOrOwnWords() {
        assertEquals(listOf("Wirtschaft"), topics("Nachrichten zu Wirtschaft"))
        assertEquals(listOf("Österreich"), topics("Nachrichten aus Österreich bitte"))
        assertEquals(listOf("Technik"), topics("Was gibt's Neues in der Technik?"))
        assertEquals(listOf("Sport"), topics("Sportnachrichten"))
        assertEquals(listOf("Wirtschaft"), topics("Fass mir die Wirtschaftsnachrichten von heute zusammen"))
        assertEquals(listOf("Ukraine"), topics("Nachrichten zur Ukraine"))
        assertEquals(listOf("Ukraine"), topics("Gib mir die Nachrichten über die Ukraine von heute"))
        assertEquals(listOf("Wirtschaft", "Sport"), topics("Nachrichten zu Wirtschaft und Sport"))
        assertEquals(listOf("Ausland"), topics("Auslandsnachrichten bitte"))
        val football = OwnerNewsCommand.parse("Nachrichten zum Thema Fußball")!!.topics.single()
        assertEquals(NewsTopic.SPORT, football.section)
        assertEquals("fußball", football.words)
        val free = OwnerNewsCommand.parse("Was gibt es Neues zu Donald Trump?")!!.topics.single()
        assertNull(free.section)
        assertEquals("Donald Trump", free.label)
    }

    @Test fun messagesNotificationsAndUnclearTurnsAreNeverNews() {
        listOf(
            "Nachricht an Anna schreiben",
            "Schreib Anna eine Nachricht",
            "Neue Nachricht",
            "Neue Nachrichten",
            "Gibt es neue Nachrichten?",
            "Lies mir meine Nachrichten vor",
            "Lies mir die Nachrichten vor",
            "Lies mir die WhatsApp-Nachrichten vor",
            "Zeig mir meine ungelesenen Nachrichten",
            "Hast du Nachrichten für mich?",
            "Die Nachrichten von Anna",
            "Schick die Nachrichten an Anna",
            "Nachrichten von gestern",
            "Kurznachrichten",
            "Sprachnachrichten abhören",
            "Benachrichtigungen vorlesen",
            "Welche Meldungen gibt es?",
            "Keine Nachrichten bitte",
            "Ich will keine Nachrichten hören",
            "Was gibt's Neues?",
            "Was ist passiert?",
            "Was ist heute los?",
            "Was ist mit dir los?",
            "Die Nachrichten waren heute schlimm",
            "Fass mir die Nachrichten zusammen ohne Internet",
            "Nachrichten von https://example.org",
            "Öffne die Nachrichten App",
            "Fass mir die in Word zusammen und speichere sie als PDF",
            "Wie spät ist es?",
            "Hallo"
        ).forEach { assertNull(it, OwnerNewsCommand.parse(it)) }
    }

    /** The news hook sits after these lanes in AgentActivity; none of them may take a news order away. */
    @Test fun earlierOwnerLanesDoNotClaimNewsOrders() {
        listOf("Fass mir die Nachrichten von heute zusammen", "Was gibt's Neues in der Welt?", "Nachrichten bitte",
            "Was ist heute passiert?", "Schlagzeilen", "Nachrichten zu Wirtschaft", "Nachrichten zur Ukraine").forEach {
            assertEquals(it, "NONE", EvalProbe.computer(it))
            assertNull(it, OwnerCorrectionIntent.parse(it))
            assertNull(it, OwnerRoutineCommands.parse(it))
            assertNull(it, OwnerChatIntentRouter.route(it))
            assertNull(it, ContinuousConversationMode.command(it))
            assertNotNull(it, OwnerNewsCommand.parse(it))
        }
    }

    /** Orders that belong to other lanes stay there. */
    @Test fun newsParserLeavesOtherLanesAlone() {
        assertNotNull(OwnerDeviceClockReply.answer("Welcher Tag ist heute?", Instant.parse("2026-11-18T09:00:00Z"),
            ZoneId.of("Europe/Vienna")))
        listOf("Welcher Tag ist heute?", "Wie viel Speicher habe ich?", "Öffne WhatsApp",
            "Schreibe eine WhatsApp Nachricht an Anna: Bin gleich da", "Lies diese PDF und erstelle mir eine Zusammenfassung",
            "Recherchiere aktuelle Nachrichten zu Photovoltaik", "Prüfe aktuelle Nachrichten zu Wien",
            "Such nach Nachrichten über Linz").forEach { assertNull(it, OwnerNewsCommand.parse(it)) }
        assertTrue(OwnerResearchCommand.parse("Prüfe aktuelle Nachrichten zu Wien") is OwnerResearchCommand.Outcome.Research)
        assertEquals("get_storage_info", BootstrapBrain.decide("Wie viel Speicher habe ich?", emptyList()).tool)
    }

    @Test fun followUpToTheClosingQuestion() {
        val words = setOf("inflation", "spritpreisdeckel", "ruanda")
        fun follow(text: String, immediate: Boolean = true) = OwnerNewsCommand.parseFollowUp(text, null, immediate, words)
        assertEquals(listOf("Wirtschaft"), (follow("Wirtschaft") as OwnerNewsCommand.FollowUp.Topics).request.topics.map { it.label })
        assertEquals(listOf("Sport"), (follow("Ja, Sport bitte") as OwnerNewsCommand.FollowUp.Topics).request.topics.map { it.label })
        assertEquals(listOf("Österreich"), (follow("Mehr zu Österreich", immediate = false) as OwnerNewsCommand.FollowUp.Topics)
            .request.topics.map { it.label })
        assertEquals(listOf("Ukraine"), (follow("Erzähl mir mehr über die Ukraine", immediate = false)
            as OwnerNewsCommand.FollowUp.Topics).request.topics.map { it.label })
        assertEquals(listOf("Inflation"), (follow("Inflation") as OwnerNewsCommand.FollowUp.Topics).request.topics.map { it.label })
        assertEquals(OwnerNewsCommand.FollowUp.Story(2), follow("Mehr zur zweiten Meldung", immediate = false))
        assertEquals(OwnerNewsCommand.FollowUp.Story(1), follow("Die erste"))
        assertEquals(OwnerNewsCommand.FollowUp.Story(-1), follow("Die letzte Meldung bitte"))
        assertEquals(OwnerNewsCommand.FollowUp.Story(3), follow("Nummer 3"))
        assertEquals(OwnerNewsCommand.FollowUp.AskWhich, follow("Ja bitte"))
        assertEquals(OwnerNewsCommand.FollowUp.Decline, follow("Nein danke, das reicht"))
        assertEquals(OwnerNewsCommand.FollowUp.Decline, follow("Passt, danke"))
    }

    @Test fun followUpNeverSwallowsAnUnrelatedTurn() {
        val words = setOf("inflation")
        fun follow(text: String, immediate: Boolean) = OwnerNewsCommand.parseFollowUp(text, null, immediate, words)
        listOf("Wie spät ist es?", "Öffne WhatsApp", "Schreib Anna eine Nachricht", "Was ist mit dir los?", "Pizza",
            "Danke, und wie wird das Wetter?", "Speichere das als PDF", "Wie viel Speicher habe ich?").forEach {
            assertNull(it, follow(it, true))
            assertNull(it, follow(it, false))
        }
        // Later in the chat only an unmistakable „mehr zu …“ counts.
        listOf("Wirtschaft", "Ja", "Nein", "Die erste", "Was ist mit Sport?").forEach { assertNull(it, follow(it, false)) }
    }
}
