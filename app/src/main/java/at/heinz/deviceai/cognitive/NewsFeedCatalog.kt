package at.heinz.deviceai.cognitive

import java.net.URI
import java.util.Locale

/**
 * Sections the owner can ask for by name („Nachrichten zu Wirtschaft“). [aliases] are matched on the owner's own
 * words, [related] are narrower owner words that stay a keyword filter but add the section's feed („Fußball“ →
 * the sport feed), [keywords] and [sections] are matched on feed text (title, teaser, category, link path).
 * Matching is local only: no topic word is ever sent to a server.
 */
internal enum class NewsTopic(
    val label: String,
    val aliases: Set<String>,
    val related: Set<String> = emptySet(),
    val keywords: Set<String>,
    val sections: Set<String>
) {
    AUSTRIA("Österreich",
        aliases = setOf("österreich", "oesterreich", "inland", "austria"),
        related = setOf("wien", "graz", "linz", "salzburg", "innsbruck", "tirol", "kärnten", "steiermark",
            "burgenland", "vorarlberg", "niederösterreich", "oberösterreich"),
        keywords = setOf("österreich", "österreichisch", "wien", "wiener", "graz", "linz", "salzburg", "innsbruck",
            "tirol", "kärnten", "steiermark", "burgenland", "vorarlberg", "niederösterreich", "oberösterreich",
            "nationalrat", "övp", "spö", "fpö", "neos", "austria", "austrian", "vienna"),
        sections = setOf("oesterreich", "österreich", "chronik")),
    GERMANY("Deutschland",
        aliases = setOf("deutschland", "germany"),
        related = setOf("berlin", "bundestag", "bayern"),
        keywords = setOf("deutschland", "deutsche", "deutschen", "berlin", "bundestag", "bundesregierung",
            "bundeskanzler", "cdu", "csu", "spd", "afd", "germany", "german"),
        sections = setOf("deutschland")),
    WORLD("Ausland",
        aliases = setOf("ausland", "welt", "weltpolitik", "international", "internationales", "außenpolitik",
            "aussenpolitik", "weltgeschehen"),
        keywords = emptySet(),
        sections = setOf("ausland", "welt", "world", "international")),
    POLITICS("Politik",
        aliases = setOf("politik", "innenpolitik"),
        related = setOf("wahl", "wahlen", "regierung"),
        keywords = setOf("regierung", "parlament", "wahl", "wahlen", "minister", "ministerin", "kanzler", "präsident",
            "koalition", "partei", "opposition", "gesetz", "abstimmung", "government", "election", "parliament",
            "president", "senate", "vote"),
        sections = setOf("politik", "innenpolitik", "politics")),
    ECONOMY("Wirtschaft",
        aliases = setOf("wirtschaft", "finanzen", "business", "konjunktur", "märkte", "maerkte"),
        related = setOf("börse", "boerse", "aktien", "inflation", "zinsen", "arbeitsmarkt"),
        keywords = setOf("wirtschaft", "inflation", "konjunktur", "börse", "aktie", "aktien", "dax", "atx", "bank",
            "banken", "zinsen", "ezb", "export", "unternehmen", "konzern", "arbeitsmarkt", "arbeitslosigkeit",
            "handel", "zoll", "zölle", "steuer", "steuern", "preise", "umsatz", "gewinn", "economy", "economic",
            "market", "markets", "stocks", "shares", "trade", "tariff", "tariffs", "earnings", "sales"),
        sections = setOf("wirtschaft", "business", "finanzen", "economy")),
    TECHNOLOGY("Technik",
        aliases = setOf("technik", "technologie", "tech", "it", "digital", "digitales", "netzwelt", "technology"),
        related = setOf("ki", "künstliche intelligenz", "smartphone", "smartphones", "computer", "software"),
        keywords = setOf("ki", "künstliche", "software", "smartphone", "chip", "chips", "cyber", "hacker", "digital",
            "roboter", "technologie", "ai", "tech", "technology", "nvidia", "apple", "google", "microsoft", "openai"),
        sections = setOf("it", "technik", "technology", "digital", "netzwelt", "tech")),
    SCIENCE("Wissenschaft",
        aliases = setOf("wissenschaft", "forschung", "wissen", "science"),
        related = setOf("weltraum", "raumfahrt"),
        keywords = setOf("forscher", "forschende", "forschung", "studie", "wissenschaft", "weltraum", "raumfahrt",
            "nasa", "esa", "universität", "science", "scientists", "study", "research", "space"),
        sections = setOf("wissenschaft", "wissen", "science")),
    SPORT("Sport",
        aliases = setOf("sport", "sports"),
        related = setOf("fußball", "fussball", "tennis", "formel 1", "ski", "skifahren", "bundesliga", "eishockey"),
        keywords = setOf("fußball", "bundesliga", "champions", "liga", "olympia", "tennis", "ski", "formel",
            "trainer", "football", "league", "cup"),
        sections = setOf("sport", "sports")),
    CULTURE("Kultur",
        aliases = setOf("kultur", "culture"),
        related = setOf("film", "kino", "musik", "literatur", "theater"),
        keywords = setOf("film", "kino", "musik", "konzert", "theater", "oper", "buch", "autor", "autorin", "museum",
            "kunst", "festival"),
        sections = setOf("kultur", "culture", "entertainment")),
    CLIMATE("Klima und Umwelt",
        aliases = setOf("klima", "umwelt", "klima und umwelt", "klimawandel"),
        keywords = setOf("klima", "umwelt", "klimawandel", "erderwärmung", "hochwasser", "dürre", "emissionen",
            "climate", "environment", "flood", "floods", "wildfire"),
        sections = setOf("umwelt", "klima", "climate", "environment")),
    HEALTH("Gesundheit",
        aliases = setOf("gesundheit", "medizin", "health"),
        keywords = setOf("gesundheit", "medizin", "krankheit", "virus", "impfung", "spital", "krankenhaus", "ärzte",
            "health", "disease", "vaccine"),
        sections = setOf("gesundheit", "health"));

    companion object {
        /** The section named exactly by [word] (owner wording, lower case), or null. */
        fun ofAlias(word: String): NewsTopic? = entries.firstOrNull { word in it.aliases }

        /** The section whose feed helps for the narrower owner word [word], or null. */
        fun ofRelated(word: String): NewsTopic? = entries.firstOrNull { word in it.related }
    }
}

/**
 * One public feed of an established outlet. [linkHosts] are the outlet's own article hosts: an item whose link
 * points anywhere else is dropped, so feed text can never put a foreign link into the sources window.
 * [homeTopic]: what the outlet's own „Inland“ section means (ORF → Österreich, tagesschau → Deutschland).
 */
internal data class NewsFeed(
    val id: String,
    val outlet: String,
    val url: String,
    val language: String,
    val linkHosts: Set<String>,
    val topics: Set<NewsTopic> = emptySet(),
    val homeTopic: NewsTopic? = null,
    val general: Boolean = false
) {
    val host: String = URI(url).host.lowercase(Locale.ROOT)

    /** The publishing house: the general and the section feed of one outlet are the same publisher. */
    val publisher: String = linkHosts.minOrNull().orEmpty().ifEmpty { host }

    fun ownsLink(link: String): Boolean {
        val uri = runCatching { URI(link) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.userInfo != null) return false
        val linkHost = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return false
        return linkHosts.any { linkHost == it || linkHost.endsWith(".$it") }
    }
}

/**
 * The fixed source list of the news briefing: public RSS/Atom feeds of established outlets, HTTPS, no API key, no
 * account, no paid service. The general feeds are read for every request; a section feed only when the owner asks
 * for that section.
 *
 * Checked on the build host on 18.11.2026 (GET with the app's request headers; HTTP 200, no redirect):
 *
 * | id | format | content type |
 * |---|---|---|
 * | orf, orf-oesterreich, orf-science, orf-sport | RSS 1.0/RDF, dc:date, dc:subject, no teasers | application/xml |
 * | tagesschau, tagesschau-wirtschaft | RSS 2.0, pubDate, teasers | application/xml |
 * | dw | RSS 2.0, pubDate (GMT), category, teasers | application/xml |
 * | bbc | RSS 2.0, CDATA, pubDate (GMT), English | text/xml |
 * | heise | Atom, published/updated, summary | application/atom+xml |
 *
 * A feed that moves or changes its format is reported to the owner as „nicht erreicht“; nothing is substituted.
 */
internal object NewsFeedCatalog {
    val feeds: List<NewsFeed> = listOf(
        NewsFeed("orf", "ORF", "https://rss.orf.at/news.xml", "de", setOf("orf.at"),
            homeTopic = NewsTopic.AUSTRIA, general = true),
        NewsFeed("tagesschau", "tagesschau", "https://www.tagesschau.de/index~rss2.xml", "de",
            setOf("tagesschau.de"), homeTopic = NewsTopic.GERMANY, general = true),
        NewsFeed("dw", "Deutsche Welle", "https://rss.dw.com/xml/rss-de-all", "de", setOf("dw.com"), general = true),
        NewsFeed("bbc", "BBC News", "https://feeds.bbci.co.uk/news/world/rss.xml", "en",
            setOf("bbc.co.uk", "bbc.com"), topics = setOf(NewsTopic.WORLD), general = true),
        NewsFeed("orf-oesterreich", "ORF Österreich", "https://rss.orf.at/oesterreich.xml", "de", setOf("orf.at"),
            topics = setOf(NewsTopic.AUSTRIA), homeTopic = NewsTopic.AUSTRIA),
        NewsFeed("tagesschau-wirtschaft", "tagesschau Wirtschaft", "https://www.tagesschau.de/wirtschaft/index~rss2.xml",
            "de", setOf("tagesschau.de"), topics = setOf(NewsTopic.ECONOMY), homeTopic = NewsTopic.GERMANY),
        NewsFeed("heise", "heise online", "https://www.heise.de/rss/heise-atom.xml", "de", setOf("heise.de"),
            topics = setOf(NewsTopic.TECHNOLOGY)),
        NewsFeed("orf-science", "ORF Science", "https://rss.orf.at/science.xml", "de", setOf("orf.at"),
            topics = setOf(NewsTopic.SCIENCE), homeTopic = NewsTopic.AUSTRIA),
        NewsFeed("orf-sport", "ORF Sport", "https://rss.orf.at/sport.xml", "de", setOf("orf.at"),
            topics = setOf(NewsTopic.SPORT), homeTopic = NewsTopic.AUSTRIA)
    )

    /** Exact feed hosts; the only hosts the news reader may contact (redirects included). */
    val hosts: Set<String> = feeds.map { it.host }.toSet()

    val generalOutlets: List<String> get() = feeds.filter { it.general }.map { it.outlet }

    /** General feeds plus the section feeds of the requested [topics]. */
    fun feedsFor(topics: Collection<NewsTopic>): List<NewsFeed> =
        feeds.filter { feed -> feed.general || feed.topics.any { it in topics } }
}
