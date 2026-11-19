package at.heinz.deviceai.cognitive

import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId

/** The synthetic feeds of docs/evaluation/continuum-eval-v1/regression-news.feeds.json for host tests. */
internal object NewsTestFeeds {
    private val root: File = listOf("docs/evaluation/continuum-eval-v1", "../docs/evaluation/continuum-eval-v1")
        .map(::File).first { it.isDirectory }

    val raw: Map<String, String> = JSONObject(File(root, "regression-news.feeds.json").readText(Charsets.UTF_8)).let { json ->
        json.keys().asSequence().filter { !it.startsWith("_") }.associateWith(json::getString)
    }

    /** Wednesday 18.11.2026, 10:30 in Vienna. */
    val NOW: Instant = Instant.parse("2026-11-18T09:30:00Z")
    val ZONE: ZoneId = ZoneId.of("Europe/Vienna")

    fun feed(id: String): NewsFeed = NewsFeedCatalog.feeds.first { it.id == id }

    fun results(vararg ids: String): List<NewsFeedResult> =
        ids.map { NewsFeedResult(feed(it), NewsFeedParser.parse(raw.getValue(it))) }

    val general: List<NewsFeedResult> get() = results("orf", "tagesschau", "dw", "bbc")
}
