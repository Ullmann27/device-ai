package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NewsSelectorTest {
    private fun request(text: String) = OwnerNewsCommand.parse(text)!!
    private fun select(text: String, results: List<NewsFeedResult> = NewsTestFeeds.general, now: Instant = NewsTestFeeds.NOW) =
        NewsSelector.select(results, request(text), now, NewsTestFeeds.ZONE)

    @Test fun generalBriefingTakesTodaysStoriesAndMergesTheSameStoryAcrossOutlets() {
        val selection = select("Nachrichten von heute")
        assertEquals(NewsSelector.GENERAL_STORIES, selection.stories.size)
        // Three outlets report the rate cut: it leads, is told once, and keeps every outlet with headline and link.
        val lead = selection.stories.first()
        assertEquals("tagesschau", lead.outlet)
        assertTrue(lead.title.startsWith("Leitzins sinkt"))
        assertEquals(listOf("tagesschau", "ORF", "BBC News"), lead.sources.map { it.outlet })
        assertEquals("https://orf.at/stories/9000002/", lead.sources[1].link)
        assertEquals("Musterstadt central bank cuts key rate to 2.5%", lead.sources[2].title)
        val solar = selection.stories[1]
        assertTrue(solar.title.startsWith("Solarpflicht"))
        assertEquals(listOf("ORF"), solar.alsoReportedBy.map { it.outlet })
        assertEquals(1, selection.stories.count { it.title.contains("Leitzins") || it.title.contains("central bank") })
        // Yesterday, older items and the entry with a foreign link are not in today's briefing.
        val titles = selection.stories.flatMap { story -> story.sources.map { it.title } }
        listOf("Gestern", "Rückblick", "Brückensperre", "Fremder Link").forEach { old -> assertTrue(old, titles.none { it.contains(old) }) }
        assertTrue(selection.stories.flatMap { it.sources }.none { it.link.contains("evil.example") })
        assertEquals(listOf("ORF", "tagesschau", "Deutsche Welle", "BBC News"), selection.readOutlets)
        assertTrue(selection.failedOutlets.isEmpty())
        assertFalse(selection.includesLastEvening)
        assertFalse(selection.includesUndated)
        assertTrue(selection.stories.groupingBy { it.outlet }.eachCount().values.all { it <= 2 })
    }

    @Test fun topicRequestsKeepOnlyThatTopic() {
        val economy = select("Nachrichten zu Wirtschaft")
        assertEquals(1, economy.stories.size)
        assertEquals(3, economy.stories.single().sources.size)
        // Before noon a thin topic is filled with last evening's reports of the same topic, and says so.
        val austria = select("Nachrichten aus Österreich")
        assertEquals(setOf("Testland beschließt Solarpflicht für Neubauten", "Hochwasserwarnung für den Musterfluss aufgehoben",
            "Brückensperre in Beispielstadt nach Unwetter"), austria.stories.map { it.title }.toSet())
        assertTrue(austria.includesLastEvening)
        assertTrue(austria.stories.all { it.outlet == "ORF" })
        val sport = select("Sportnachrichten", NewsTestFeeds.general + NewsTestFeeds.results("orf-sport")).stories.map { it.outlet }
        assertEquals(setOf("ORF Sport", "Deutsche Welle"), sport.toSet())
        assertEquals(listOf("Marsmission Beispiel-7 erreicht die Umlaufbahn"), select("Nachrichten zur Marsmission").stories.map { it.title })
        assertEquals(listOf("Fußball: FC Muster gewinnt das Testland-Derby 3:1"), select("Nachrichten zum Thema Fußball").stories.map { it.title })
        val technology = select("Nachrichten zu Technik", NewsTestFeeds.general + NewsTestFeeds.results("heise"))
        assertEquals(setOf("heise online"), technology.stories.map { it.outlet }.toSet())
        assertEquals(listOf("Storm Example batters northern Sampleland coast"), select("Nachrichten zu Sampleland").stories.map { it.title })
    }

    @Test fun nothingOnTheTopicIsAnEmptySelectionNotAGuess() {
        val selection = select("Nachrichten zur Ukraine")
        assertTrue(selection.stories.isEmpty())
        assertEquals(4, selection.readOutlets.size)
    }

    @Test fun failedAndEmptyFeedsAreReportedByName() {
        val results = NewsTestFeeds.results("orf") + NewsFeedResult(NewsTestFeeds.feed("tagesschau"), emptyList(), "Zeitüberschreitung") +
            NewsFeedResult(NewsTestFeeds.feed("dw"), emptyList())
        val selection = select("Schlagzeilen", results)
        assertEquals(listOf("ORF"), selection.readOutlets)
        assertEquals(listOf("tagesschau" to "Zeitüberschreitung", "Deutsche Welle" to "keine lesbaren Einträge"), selection.failedOutlets)
        // Three ORF reports of today; before noon the thin day is filled with last evening's report.
        assertEquals(4, selection.stories.size)
        assertTrue(selection.includesLastEvening)
    }

    @Test fun earlyInTheMorningAThinDayIsFilledWithLastEvening() {
        // 06:00 in Vienna: nothing of the fixture is published yet today except what came before 06:00 local.
        val early = Instant.parse("2026-11-18T05:00:00Z")
        val selection = select("Nachrichten bitte", NewsTestFeeds.results("orf"), early)
        assertTrue(selection.includesLastEvening)
        assertTrue(selection.stories.any { it.title.startsWith("Brückensperre") })
        assertTrue(selection.stories.none { it.title.startsWith("Gestern Mittag") })
        // In the afternoon the day is never widened.
        val afternoon = Instant.parse("2026-11-18T14:00:00Z")
        assertFalse(select("Nachrichten bitte", NewsTestFeeds.results("orf"), afternoon).includesLastEvening)
    }

    @Test fun aFeedWithoutAnyDateIsToleratedInFeedOrder() {
        val subjects = listOf("Brücke", "Schule", "Bahnhof", "Rathaus", "Hafen", "Museum", "Stadion", "Markt", "Tunnel")
        val undated = subjects.mapIndexed { index, subject -> "<item><title>$subject in Beispielort wird saniert</title>" +
            "<link>https://orf.at/stories/77$index/</link></item>" }.joinToString("")
        val results = listOf(NewsFeedResult(NewsTestFeeds.feed("orf"), NewsFeedParser.parse("<rss><channel>$undated</channel></rss>")))
        val selection = select("Schlagzeilen", results)
        assertTrue(selection.includesUndated)
        assertEquals(0, selection.datedToday)
        assertTrue(selection.stories.isNotEmpty() && selection.stories.all { it.publishedAtMillis == null })
        // Only the first entries of an undated feed count as current.
        assertTrue(selection.stories.none { it.title.startsWith("Stadion") || it.title.startsWith("Tunnel") })
    }

    /**
     * Optional evidence run on real feed files fetched on the host (see NewsFeedParserTest): prints what the selector
     * and the headline briefing make of them. CONTINUUM_NEWS_REAL_NOW = ISO instant of the fetch.
     */
    @Test fun selectsFromRealFeedFilesWhenProvided() {
        val directory = System.getenv("CONTINUUM_NEWS_REAL_FEEDS")?.let { java.io.File(it) }
        org.junit.Assume.assumeTrue(directory?.isDirectory == true)
        val now = Instant.parse(System.getenv("CONTINUUM_NEWS_REAL_NOW") ?: "2026-11-18T08:40:00Z")
        fun results(topics: List<NewsTopic>) = NewsFeedCatalog.feedsFor(topics).mapNotNull { feed ->
            java.io.File(directory, feed.id + ".xml").takeIf { it.isFile }?.let {
                NewsFeedResult(feed, NewsFeedParser.parse(it.readText(Charsets.UTF_8).take(100_000)))
            }
        }
        listOf("Nachrichten von heute", "Nachrichten zu Wirtschaft", "Nachrichten aus Österreich", "Nachrichten zu Technik",
            "Sportnachrichten", "Nachrichten zur Ukraine").forEach { order ->
            val request = request(order)
            val selection = NewsSelector.select(results(request.sections), request, now, NewsTestFeeds.ZONE)
            println("REAL-SELECTION „$order“: today=${selection.datedToday} stories=${selection.stories.size} evening=${selection.includesLastEvening}")
            selection.stories.forEach { story ->
                println("  - ${story.outlet}: ${story.title}" + story.alsoReportedBy.joinToString("") { " | auch ${it.outlet}: ${it.title}" })
            }
            if (selection.stories.isNotEmpty()) {
                val prompt = NewsBriefing.prompt(selection.stories, request, now.atZone(NewsTestFeeds.ZONE).toLocalDate(), NewsBriefing.MAX_PROMPT_BYTES)
                println("  prompt bytes=${prompt?.text?.toByteArray(Charsets.UTF_8)?.size} stories=${prompt?.stories?.size}")
            }
            assertTrue(order, selection.stories.all { story -> story.sources.all { it.link.startsWith("https://") } })
        }
    }

    @Test fun differentStoriesThatShareGenericWordsAreNotMerged() {
        fun feedOf(vararg titles: String) = "<rss><channel>" + titles.mapIndexed { index, title ->
            "<item><title>$title</title><link>https://orf.at/stories/55$index/</link>" +
                "<pubDate>Wed, 18 Nov 2026 09:0$index:00 +0100</pubDate></item>" }.joinToString("") + "</channel></rss>"
        val results = listOf(NewsFeedResult(NewsTestFeeds.feed("orf"), NewsFeedParser.parse(feedOf(
            "Arbeitslosigkeit steigt auf 7 Prozent", "Inflation steigt auf 3 Prozent", "Neue Regeln für E-Scooter in Musterstadt"))))
        assertEquals(3, select("Schlagzeilen", results).stories.size)
    }
}
