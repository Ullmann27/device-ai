package at.heinz.deviceai

import at.heinz.deviceai.cognitive.NewsTestFeeds
import at.heinz.deviceai.cognitive.OwnerChatLine
import at.heinz.deviceai.cognitive.OwnerDeliverable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * B2 of the architecture review (19.11.2026): „das“ means a result – a model answer or a lane's text result –, never
 * a refusal of the app, a lane's report or the content of others the app read out. Before, any last assistant line of
 * 40 characters or more became „Continuum Notiz“ on „Speichere das als PDF“ and could be mailed as an attachment.
 */
class OwnerWorkToolsSubjectTest {
    private fun tools(): Pair<OwnerWorkToolsHarness, OwnerWorkTools> {
        val host = OwnerWorkToolsHarness()
        return host to OwnerWorkTools(host, now = { NewsTestFeeds.NOW }, zone = { NewsTestFeeds.ZONE })
    }

    private val nothing = "In diesem Gespräch gibt es noch nichts, das ich speichern könnte."

    @Test fun onlyModelAnswersAndLaneResultsAreWorthKeepingByTheirText() {
        val day = LocalDate.of(2026, 11, 19)
        val long = "Die Photosynthese wandelt Lichtenergie in chemische Energie um, in den Chloroplasten der Pflanzen."
        assertTrue(OwnerDeliverable.ofReply(OwnerChatLine(long, OwnerChatLine.Kind.MODEL_ANSWER), day, 0L) != null)
        assertTrue(OwnerDeliverable.ofReply(OwnerChatLine(long, OwnerChatLine.Kind.LANE_DELIVERABLE), day, 0L) != null)
        listOf(OwnerChatLine.Kind.SYSTEM, OwnerChatLine.Kind.LANE_REPORT, OwnerChatLine.Kind.FOREIGN).forEach {
            assertNull(it.name, OwnerDeliverable.ofReply(OwnerChatLine(long, it), day, 0L))
        }
    }

    @Test fun aRefusalOfTheAppIsNeverSavedAsTheResult() {
        // The failing input of the review: the computer lane's refusal, then „Erstelle eine PDF“.
        val (host, tools) = tools()
        host.systemTurn("Öffne bitte WhatsApp und schreib meinem Vater guten Morgen",
            "Der zusätzliche App-Schritt wird noch nicht sicher verstanden.")
        assertTrue(tools.handle("Speichere das als PDF"))
        assertTrue(host.downloads.isEmpty())
        assertTrue(host.lines("REPLY:").last().startsWith(nothing))
        assertTrue(tools.handle("Erstelle bitte eine PDF"))
        assertTrue(host.downloads.isEmpty())
        assertTrue(host.lines("REPLY:").last().startsWith("Gern – was soll in der PDF stehen?"))
    }

    @Test fun aReadOutNotificationIsNeitherSavedNorMailed() {
        val (host, tools) = tools()
        host.readOut("Anna Beispiel schreibt auf WhatsApp: Der Schlüssel liegt unter der Matte beim Nachbarn.")
        assertTrue(tools.handle("Speichere das als PDF"))
        assertTrue(host.downloads.isEmpty())
        assertTrue(tools.handle("Schick das per Mail an anna@example.org"))
        assertTrue("no mail is prepared with the notification", host.mails.isEmpty())
        assertTrue(host.lines("REPLY:").last().startsWith("In diesem Gespräch gibt es noch nichts, das ich verschicken könnte."))
    }

    @Test fun aLanesReportIsNotItsResultButTheModelAnswerStillIs() {
        val (host, tools) = tools()
        host.log += "SAY:Die Auftragsfolge endet bei Schritt 2 von 3. Erledigt: 1. „Argon4.pdf“ geöffnet. Nicht geklappt: 2. Seite 9 gibt es nicht."
        assertTrue(tools.handle("Speichere das als PDF"))
        assertTrue(host.downloads.isEmpty())
        host.foreignTurn("Erklär mir die Photosynthese", "Die Photosynthese wandelt Lichtenergie in chemische Energie um. " +
            "Pflanzen bilden dabei aus Wasser und Kohlendioxid Zucker und geben Sauerstoff ab.")
        assertTrue(tools.handle("Speichere das als PDF"))
        assertEquals(listOf("Continuum Notiz 18.11.2026.pdf"), host.downloads.keys.toList())
    }
}
