package at.heinz.deviceai

import at.heinz.deviceai.cognitive.InternetAccessMode
import at.heinz.deviceai.cognitive.NewsBriefing
import at.heinz.deviceai.cognitive.NewsFeedCatalog
import at.heinz.deviceai.cognitive.NewsTestFeeds
import at.heinz.deviceai.cognitive.OwnerDeliverable
import at.heinz.deviceai.cognitive.OwnerNewsCommand
import at.heinz.deviceai.cognitive.WebToolRequest
import at.heinz.deviceai.localmodel.LocalModelThermalGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The news briefing end to end on the host: owner sentence → intent → web consent → feeds through the ToolGate →
 * selection → local-model task → accepted answer or headline fallback → reply, sources, steps, deliverable.
 */
class OwnerWorkToolsNewsFlowTest {
    private val briefing = "Die Nationalbank von Musterstadt hat den Leitzins auf 2,5 Prozent gesenkt, das berichten die " +
        "tagesschau und der ORF. Außerdem müssen in Testland neue Häuser ab 2028 eine Solaranlage haben. Währenddessen " +
        "warnt ein Bericht vor häufigeren Dürren in Beispielia, wie die Deutsche Welle meldet."
    private val order = "Fass mir die Nachrichten von heute zusammen"
    private val general = NewsFeedCatalog.feeds.filter { it.general }.map { it.url }.toSet()

    private fun tools(host: OwnerWorkToolsHarness) = OwnerWorkTools(host, now = { NewsTestFeeds.NOW }, zone = { NewsTestFeeds.ZONE })
    private fun answering(text: String) = OwnerWorkToolsHarness(model = { OwnerWorkGeneration(text, null, true) })

    @Test fun theOwnerSentenceBecomesASpokenStyleBriefingWithSourcesAndRealSteps() {
        val host = answering(briefing)
        val tools = tools(host)
        assertTrue(tools.handle("Jarvis, $order"))
        assertEquals(listOf("Jarvis, $order"), host.lines("USER:"))
        // One grant for the fixed public feeds; the owner's words are not part of it.
        val (call, explanation) = host.consents.single()
        assertEquals(WebToolRequest.Search(OwnerNewsCommand.GRANT_LABEL), call)
        assertTrue(explanation!!.contains("ORF, tagesschau, Deutsche Welle, BBC News"))
        assertEquals(general, host.fetched.toSet())
        assertEquals(listOf(
            "news-fetch:Quellen abrufen:ACTIVE:RESEARCH", "news-fetch:Quellen abrufen:DONE",
            "news-select:Meldungen auswählen:ACTIVE", "news-select:Meldungen auswählen:DONE",
            "news-summary:Zusammenfassen:ACTIVE:THINK", "news-summary:Zusammenfassen:DONE"), host.lines("STEP:"))
        // The model saw only the selected reports, framed as data.
        val prompt = host.prompts.single()
        assertTrue(prompt.contains(NewsBriefing.OPEN) && prompt.endsWith(NewsBriefing.CLOSE))
        assertFalse(prompt.contains("Jarvis, $order"))
        assertFalse(prompt.contains("Gestern"))
        val said = host.lines("SAY:").single()
        assertTrue(said.startsWith(briefing))
        assertTrue(said.endsWith("oder zu einer der Meldungen nachlesen?"))
        assertFalse(said.contains("https://"))
        assertFalse(said.contains("Hier ist eine Zusammenfassung"))
        // Every used story is traceable: outlet, headline and link reach the sources window.
        assertEquals(8, host.sources.size)
        assertTrue(host.sources.all { it.outlet.isNotBlank() && it.title.isNotBlank() && it.link.startsWith("https://") })
        assertEquals(setOf("tagesschau", "ORF", "BBC News", "Deutsche Welle"), host.sources.map { it.outlet }.toSet())
        val deliverable = tools.deliverable!!
        assertEquals(OwnerDeliverable.Kind.NEWS_BRIEFING, deliverable.kind)
        assertEquals("Nachrichten von heute", deliverable.title)
        assertEquals("Mittwoch, 18. November 2026", deliverable.dateLabel)
        assertEquals(host.sources, deliverable.sources)
        assertTrue(host.digests.single().modelSynthesized)
        assertTrue(host.lines("ACTIVITY:").last().contains("5 Meldungen · 8 Quellen · lokale Zusammenfassung"))
    }

    @Test fun aTopicOrderReadsTheSectionFeedAndBriefsOnlyThatTopic() {
        val host = answering("Der FC Muster hat das Testland-Derby 3:1 gewonnen, meldet die Deutsche Welle. Außerdem wurde " +
            "laut ORF Sport das Skirennen in Beispielberg wegen Neuschnees abgesagt.")
        assertTrue(tools(host).handle("Nachrichten zu Sport"))
        assertEquals(general + "https://rss.orf.at/sport.xml", host.fetched.toSet())
        assertTrue(host.prompts.single().contains("zum Thema „Sport“"))
        assertEquals(setOf("Deutsche Welle", "ORF Sport"), host.sources.map { it.outlet }.toSet())
        assertTrue(host.lines("SAY:").single().endsWith("Soll ich zu einer dieser Meldungen nachlesen oder ein anderes Thema ansehen?"))
        assertEquals("Nachrichten von heute – Sport", host.digests.single().question)
    }

    @Test fun withoutALoadedModelTheHeadlinesAreGivenAndNoGenerationStarts() {
        val host = OwnerWorkToolsHarness(modelLoaded = false)
        assertTrue(tools(host).handle(order))
        assertTrue(host.prompts.isEmpty())
        val said = host.lines("SAY:").single()
        assertTrue(said.startsWith("Das lokale Modell ist gerade nicht geladen. Du bekommst deshalb die Schlagzeilen von heute"))
        assertTrue(said.contains("tagesschau: Leitzins sinkt: Nationalbank Musterstadt senkt auf 2,5 Prozent."))
        assertEquals("news-summary:Schlagzeilen zusammenstellen:DONE", host.lines("STEP:").last())
        assertFalse(host.digests.single().modelSynthesized)
        assertTrue(host.lines("ACTIVITY:").last().endsWith("Schlagzeilen ohne Modell"))
    }

    @Test fun aSummaryOfTheCloudBrainIsNeverCalledLocal() {
        val host = answering(briefing).apply { cloud = true }
        val tools = tools(host)
        assertTrue(tools.handle(order))
        assertTrue(host.lines("SAY:").single().startsWith(briefing))
        assertEquals("Zusammenfassung des Cloud-Gehirns aus den unten genannten Meldungen", tools.deliverable!!.note)
        assertTrue(host.lines("ACTIVITY:").last().endsWith("8 Quellen · Zusammenfassung des Cloud-Gehirns"))
        val notReady = OwnerWorkToolsHarness(modelLoaded = false).apply { cloud = true }
        assertTrue(tools(notReady).handle(order))
        assertTrue(notReady.lines("SAY:").single().startsWith("Das Cloud-Gehirn ist gerade nicht bereit. Du bekommst deshalb"))
        val failed = OwnerWorkToolsHarness(model = { OwnerWorkGeneration("", "CLAUDE_NETWORK", false) }).apply { cloud = true }
        assertTrue(tools(failed).handle(order))
        assertTrue(failed.lines("SAY:").single().startsWith("Das Cloud-Gehirn hat gerade keine Zusammenfassung geliefert."))
        val meta = answering("Um Nachrichten zusammenfassen zu können, müsstest du eine Funktion entwickeln.").apply { cloud = true }
        assertTrue(tools(meta).handle(order))
        assertTrue(meta.lines("SAY:").single().startsWith("Die Zusammenfassung des Cloud-Gehirns habe ich verworfen"))
        (listOf(host, notReady, failed, meta)).forEach { harness ->
            assertFalse(harness.log.toString(), harness.log.any { it.contains("lokale", ignoreCase = true) })
        }
    }

    @Test fun heatFailureOrAnUngroundedAnswerFallBackToHeadlinesWithTheRealReason() {
        val hot = OwnerWorkToolsHarness(model = { OwnerWorkGeneration("", LocalModelThermalGuard.PAUSED_THERMAL, false) })
        assertTrue(tools(hot).handle(order))
        assertTrue(hot.lines("SAY:").single().startsWith("Das Gerät ist gerade zu warm"))
        val failed = OwnerWorkToolsHarness(model = { OwnerWorkGeneration("halber Satz", "BOT_DEADLINE_EXCEEDED", false) })
        assertTrue(tools(failed).handle(order))
        assertTrue(failed.lines("SAY:").single().startsWith("Das lokale Modell hat gerade keine Zusammenfassung geliefert."))
        // The field failure: a meta answer instead of news. It is never shown as a briefing.
        val meta = answering("Um Nachrichten oder Webseiten zusammenfassen zu können, müsstest du eine Funktion entwickeln, " +
            "die auf diese Inhalte zugreift und sie verarbeitet.")
        assertTrue(tools(meta).handle(order))
        val said = meta.lines("SAY:").single()
        assertTrue(said.startsWith("Die Zusammenfassung des lokalen Modells habe ich verworfen"))
        assertFalse(said.contains("Funktion entwickeln"))
        val invented = answering(briefing.replace("2,5 Prozent", "4,75 Prozent"))
        assertTrue(tools(invented).handle(order))
        assertFalse(invented.lines("SAY:").single().contains("4,75"))
    }

    @Test fun internetOffOrDeclinedMeansNoRequestAndAnHonestReply() {
        val off = OwnerWorkToolsHarness(mode = InternetAccessMode.OFF)
        assertTrue(tools(off).handle(order))
        assertEquals(listOf(OwnerNewsCommand.offlineReply()), off.spoken)
        assertTrue(off.consents.isEmpty() && off.fetched.isEmpty() && off.prompts.isEmpty())
        val declined = OwnerWorkToolsHarness(mode = InternetAccessMode.ASK, ownerApproves = false)
        assertTrue(tools(declined).handle(order))
        assertEquals(listOf("Nachrichten nicht abgerufen: Der Internetzugriff wurde für diese Anfrage nicht freigegeben."), declined.spoken)
        assertTrue(declined.fetched.isEmpty() && declined.prompts.isEmpty() && declined.sources.isEmpty())
        // ASK with the owner's approval runs exactly once.
        val approved = OwnerWorkToolsHarness(mode = InternetAccessMode.ASK, model = { OwnerWorkGeneration(briefing, null, true) })
        assertTrue(tools(approved).handle(order))
        assertTrue(approved.lines("SAY:").single().startsWith(briefing))
    }

    @Test fun unreachableSourcesAndEmptyTopicsAreSaidPlainly() {
        val offline = answering(briefing).apply { served.clear() }
        assertTrue(tools(offline).handle(order))
        val reply = offline.lines("REPLY:").single()
        assertTrue(reply.startsWith("Ich konnte gerade keine Nachrichtenquelle erreichen (ORF: keine Verbindung"))
        assertTrue(reply.contains("ich erfinde keine Nachrichten"))
        assertTrue(offline.prompts.isEmpty() && offline.sources.isEmpty())
        assertEquals("news-fetch:Quellen abrufen:FAILED", offline.lines("STEP:").last())
        assertNull(tools(offline).deliverable)

        val partial = answering(briefing).apply { served.remove("https://rss.dw.com/xml/rss-de-all") }
        assertTrue(tools(partial).handle(order))
        // The model answer names Deutsche Welle, which was not read: it is rejected, the headlines say what is missing.
        assertTrue(partial.lines("SAY:").single().contains("Nicht erreicht habe ich diesmal: Deutsche Welle."))

        val topic = answering(briefing)
        assertTrue(tools(topic).handle("Nachrichten zur Ukraine"))
        assertTrue(topic.lines("REPLY:").single().startsWith("Zu „Ukraine“ habe ich heute in meinen Quellen"))
        assertTrue(topic.prompts.isEmpty())
    }

    @Test fun aStoppedJobPublishesNothing() {
        val host = answering(briefing).apply { cancelDuringGeneration = true }
        val tools = tools(host)
        assertTrue(tools.handle(order))
        assertTrue(host.lines("SAY:").isEmpty() && host.sources.isEmpty())
        assertNull(tools.deliverable)
        assertTrue(host.lines("ACTIVITY:").last().contains("Ergebnis verworfen"))
    }

    @Test fun theClosingQuestionCanBeAnsweredWithATopicAStoryOrNo() {
        val host = answering(briefing)
        val tools = tools(host)
        assertTrue(tools.handle(order))
        // „Wirtschaft“ as the very next turn is a topic order.
        assertTrue(tools.handle("Wirtschaft"))
        assertTrue(host.fetched.contains("https://www.tagesschau.de/wirtschaft/index~rss2.xml"))
        assertEquals(2, host.consents.size)
        // „Mehr zur ersten Meldung“ reads that article through the existing single-source research.
        val economyLead = host.sources.drop(8).first().link
        assertTrue(tools.handle("Mehr zur ersten Meldung"))
        assertEquals(listOf<WebToolRequest>(WebToolRequest.Read(economyLead)), host.researched)
        assertFalse(tools.handle("Und die neunte Meldung?"))
        assertTrue(tools.handle("Mehr zu Nummer 6"))
        assertTrue(host.lines("REPLY:").last().startsWith("Ich habe dir"))
        // Unrelated turns are left to the other lanes.
        listOf("Wie spät ist es?", "Öffne WhatsApp", "Schreib Anna eine Nachricht").forEach { assertFalse(it, tools.handle(it)) }
    }

    @Test fun yesAsksWhichAndNoEndsIt() {
        val host = answering(briefing)
        val tools = tools(host)
        assertTrue(tools.handle(order))
        assertTrue(tools.handle("Ja bitte"))
        assertEquals(NewsBriefing.askWhichTopic(), host.lines("REPLY:").last())
        assertTrue(tools.handle("Sport"))
        assertEquals(2, host.consents.size)
        assertTrue(tools.handle("Nein danke"))
        assertEquals(NewsBriefing.declined(), host.lines("REPLY:").last())
        // Without a briefing in this chat a bare word is nothing for the work tools.
        val fresh = answering(briefing)
        assertFalse(tools(fresh).handle("Wirtschaft"))
        assertFalse(tools(fresh).handle("Ja bitte"))
        assertTrue(fresh.log.isEmpty())
        // Another chat does not inherit the open question.
        host.sessionId = "chat-2"
        assertFalse(tools.handle("Wirtschaft"))
        assertNull(tools.deliverable)
    }
}
