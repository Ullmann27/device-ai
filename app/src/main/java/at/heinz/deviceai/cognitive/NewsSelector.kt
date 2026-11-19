package at.heinz.deviceai.cognitive

import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** What reading one feed produced: its items, or the reason it could not be read. */
internal data class NewsFeedResult(val feed: NewsFeed, val items: List<NewsFeedItem>, val failure: String? = null)

/** Outlet, headline and link of one report – what the sources window shows for every story that was used. */
internal data class NewsSourceRef(val outlet: String, val title: String, val link: String)

/** One story of the briefing. [alsoReportedBy]: the same story at other outlets (merged, not repeated). */
internal data class NewsStory(
    val title: String,
    val teaser: String,
    val outlet: String,
    val link: String,
    val language: String,
    val publishedAtMillis: Long?,
    val alsoReportedBy: List<NewsSourceRef> = emptyList()
) {
    val sources: List<NewsSourceRef> get() = listOf(NewsSourceRef(outlet, title, link)) + alsoReportedBy
}

internal data class NewsSelection(
    val stories: List<NewsStory>,
    /** Outlets whose feed was read and contained items. */
    val readOutlets: List<String>,
    /** Outlets whose feed failed, with the reason. */
    val failedOutlets: List<Pair<String, String>>,
    /** Items dated today (owner's local date) across all read feeds, before topic filter and merging. */
    val datedToday: Int,
    /** Before noon, a thin day is filled with reports since 18:00 of the previous day. */
    val includesLastEvening: Boolean,
    /** At least one selected story comes from a feed without dates (taken in feed order). */
    val includesUndated: Boolean
)

/**
 * Chooses the stories of a briefing from the parsed feeds. Deterministic and pure: today's items by the owner's
 * local date (feeds without any date are tolerated in feed order), the same story across outlets is merged, a
 * story several outlets report ranks first, and every story keeps outlet, headline and link of each report.
 */
internal object NewsSelector {
    const val GENERAL_STORIES = 5
    const val TOPIC_STORIES = 6
    private const val UNDATED_PER_FEED = 6
    private const val MAX_ALSO = 2

    fun select(results: List<NewsFeedResult>, request: OwnerNewsCommand.Request, now: Instant, zone: ZoneId,
               maxStories: Int = if (request.unspecific) GENERAL_STORIES else TOPIC_STORIES): NewsSelection {
        val local = now.atZone(zone)
        val today = local.toLocalDate()
        val eveningStart = today.minusDays(1).atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val latest = now.toEpochMilli() + 6 * 3_600_000L // a date further ahead is not a publication time
        val todays = ArrayList<Candidate>()
        val evening = ArrayList<Candidate>()
        var datedToday = 0
        results.forEach { result ->
            val owned = result.items.filter { it.title.isNotBlank() && result.feed.ownsLink(it.link) }
            val dated = owned.any { it.publishedAtMillis != null }
            var undated = 0
            owned.forEachIndexed { position, item ->
                val at = item.publishedAtMillis?.takeIf { it <= latest }
                val candidate = Candidate(result.feed, position, item, at)
                when {
                    at == null -> if (!dated && undated++ < UNDATED_PER_FEED) todays += candidate
                    at >= dayStart -> { datedToday++; todays += candidate }
                    at >= eveningStart -> evening += candidate
                }
            }
        }
        fun wanted(candidate: Candidate) = request.topics.isEmpty() || request.topics.any { matches(candidate, it) }
        var picked = rank(todays.filter(::wanted), now, request.unspecific, maxStories)
        var fromEvening = false
        if (picked.size < maxStories && local.hour < 12) {
            val widened = rank((todays + evening).filter(::wanted), now, request.unspecific, maxStories)
            fromEvening = widened.any { cluster -> cluster.members.any { it in evening } }
            picked = widened
        }
        return NewsSelection(
            stories = picked.map(::story),
            readOutlets = results.filter { it.failure == null && it.items.isNotEmpty() }.map { it.feed.outlet }.distinct(),
            failedOutlets = results.filter { it.failure != null || it.items.isEmpty() }
                .map { it.feed.outlet to (it.failure ?: "keine lesbaren Einträge") }.distinctBy { it.first },
            datedToday = datedToday,
            includesLastEvening = fromEvening,
            includesUndated = picked.any { cluster -> cluster.primary.publishedAt == null }
        )
    }

    /** Lower-case words of the stories' headlines and teasers – what a bare follow-up word may refer to. */
    fun briefedWords(stories: List<NewsStory>): Set<String> =
        stories.flatMap { words(it.title + " " + it.teaser) }.filter { it.length >= 4 }.toSet()

    private class Candidate(val feed: NewsFeed, val position: Int, val item: NewsFeedItem, val publishedAt: Long?) {
        val words: Set<String> = words(item.title + " " + item.teaser + " " + item.categories.joinToString(" ")).toSet()
        val sections: Set<String> = (item.categories.map { it.lowercase(Locale.ROOT).trim() } + pathSections(item.link)).toSet()
        /** Content words of the headline plus its decimal figures („2,5“ and „2.5“ are the same figure). */
        val signature: Set<String> = (words(item.title).filter { significant(it) }.map(::stem) +
            decimal.findAll(item.title).map { it.value.replace(',', '.') }).toSet()
    }

    private class Cluster(first: Candidate) {
        val members = arrayListOf(first)
        val primary: Candidate get() = members.minWith(
            compareBy<Candidate>({ if (it.feed.language == "de") 0 else 1 }, { if (it.item.teaser.isNotBlank()) 0 else 1 },
                { it.position }))
        /** Independent publishers: the general and the section feed of one outlet count once. */
        val outlets: Int get() = members.map { it.feed.publisher }.distinct().size
    }

    private fun rank(candidates: List<Candidate>, now: Instant, diverse: Boolean, maxStories: Int): List<Cluster> {
        val clusters = ArrayList<Cluster>()
        candidates.sortedWith(compareBy({ it.position }, { it.feed.id })).forEach { candidate ->
            val home = clusters.firstOrNull { cluster -> cluster.members.any { sameStory(it, candidate) } }
            if (home == null) clusters += Cluster(candidate) else home.members += candidate
        }
        val nowMillis = now.toEpochMilli()
        fun score(cluster: Cluster): Double {
            val best = cluster.members.minOf { it.position }
            val newest = cluster.members.mapNotNull { it.publishedAt }.maxOrNull()
            val recency = when {
                newest == null -> 0.2
                nowMillis - newest <= 2 * 3_600_000L -> 1.0
                nowMillis - newest <= 6 * 3_600_000L -> 0.6
                nowMillis - newest <= 12 * 3_600_000L -> 0.3
                else -> 0.0
            }
            val german = cluster.members.any { it.feed.language == "de" && it.item.teaser.isNotBlank() }
            return minOf(cluster.outlets, 3) * 4.0 + (10 - best).coerceAtLeast(0) * 0.35 +
                (if (german) 1.0 else 0.0) + (if (cluster.members.any { it.item.teaser.isNotBlank() }) 0.5 else 0.0) + recency
        }
        val ordered = clusters.map { it to score(it) }
            .sortedWith(compareByDescending<Pair<Cluster, Double>> { it.second }
                .thenBy { it.first.primary.position }.thenBy { it.first.primary.feed.id }).map { it.first }
        // No single outlet dominates the general briefing while other outlets have stories of their own.
        val perOutlet = if (diverse) 2 else 3
        val chosen = ArrayList<Cluster>()
        val counts = HashMap<String, Int>()
        ordered.forEach { cluster ->
            val outlet = cluster.primary.feed.publisher
            if (chosen.size < maxStories && (counts[outlet] ?: 0) < perOutlet) {
                chosen += cluster
                counts[outlet] = (counts[outlet] ?: 0) + 1
            }
        }
        ordered.forEach { if (chosen.size < maxStories && it !in chosen) chosen += it }
        return chosen.sortedBy { ordered.indexOf(it) }
    }

    private fun story(cluster: Cluster): NewsStory {
        val primary = cluster.primary
        val also = cluster.members.filter { it !== primary && it.feed.publisher != primary.feed.publisher }
            .sortedWith(compareBy({ if (it.feed.language == "de") 0 else 1 }, { it.position }, { it.feed.id }))
            .distinctBy { it.feed.publisher }.take(MAX_ALSO)
            .map { NewsSourceRef(it.feed.outlet, it.item.title, it.item.link) }
        return NewsStory(primary.item.title, primary.item.teaser, primary.feed.outlet, primary.item.link,
            primary.feed.language, primary.publishedAt, also)
    }

    private fun sameStory(a: Candidate, b: Candidate): Boolean {
        if (a.item.link == b.item.link) return true
        val shared = a.signature.intersect(b.signature)
        if (shared.size < 2 || shared.none { it.length >= 5 }) return false
        // Across languages only names and figures can coincide at all: two of them are the same story.
        if (a.feed.language != b.feed.language) return true
        return shared.size.toDouble() / minOf(a.signature.size, b.signature.size) >= 0.5
    }

    private fun matches(candidate: Candidate, filter: OwnerNewsCommand.TopicFilter): Boolean {
        filter.words?.let { return wordsMatch(candidate, it) }
        val section = filter.section ?: return false
        if (section in candidate.feed.topics) return true
        if (candidate.sections.any { it in section.sections }) return true
        if (candidate.feed.homeTopic == section && candidate.sections.any { it == "inland" }) return true
        return candidate.words.any { word ->
            section.keywords.any { key ->
                // The word itself or a compound noun with it („Steuerreform“, „Stromsteuer“) – never a verb form
                // that merely starts the same way („gewinnt“, „handelt“).
                word == key || (key.length >= 5 && word.length >= key.length + 3 && (word.startsWith(key) || word.endsWith(key)))
            }
        }
    }

    /** Every content word of the owner's topic must occur: exact for short words, as a word part for longer ones. */
    private fun wordsMatch(candidate: Candidate, topic: String): Boolean {
        val wanted = words(topic).filter { it !in stopwords }.ifEmpty { words(topic) }
        if (wanted.isEmpty()) return false
        return wanted.all { raw ->
            val key = stem(raw)
            candidate.words.any { word ->
                when {
                    key.length <= 3 -> word == key
                    key.length == 4 -> word.startsWith(key)
                    else -> word.contains(key)
                }
            }
        }
    }

    private val decimal = Regex("\\d+[.,]\\d+")

    private fun words(text: String): List<String> =
        text.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 }

    private fun significant(word: String): Boolean =
        word !in stopwords && (word.length >= 4 || (word.length >= 2 && word.all(Char::isDigit)))

    /** Crude suffix cut so that „ukrainische“, „Ukraine“ and „Ukrainian“ share a stem; only for longer words. */
    private fun stem(word: String): String {
        if (word.length < 6) return word
        for (suffix in listOf("ischen", "ische", "isch", "ern", "en", "er", "es", "e", "n", "s"))
            if (word.endsWith(suffix) && word.length - suffix.length >= 4) return word.dropLast(suffix.length)
        return word
    }

    /** The first two path segments of the article link („/wirtschaft/konjunktur/…“): the outlet's own section. */
    private fun pathSections(link: String): List<String> = runCatching {
        URI(link).path.orEmpty().split('/').filter { it.isNotBlank() }.take(2)
            .map { it.lowercase(Locale.ROOT).substringBefore('.') }
    }.getOrDefault(emptyList())

    private val stopwords = setOf(
        "der", "die", "das", "den", "dem", "des", "ein", "eine", "einen", "einem", "einer", "und", "oder", "aber",
        "mit", "ohne", "von", "vom", "zum", "zur", "für", "fuer", "auf", "aus", "bei", "nach", "vor", "über", "ueber",
        "unter", "gegen", "durch", "wegen", "seit", "bis", "als", "wie", "was", "wer", "wird", "werden", "wurde",
        "wurden", "ist", "sind", "war", "waren", "hat", "haben", "hatte", "soll", "sollen", "will", "wollen", "kann",
        "können", "muss", "müssen", "sich", "nicht", "kein", "keine", "mehr", "weniger", "neue", "neuer", "neues",
        "neuen", "laut", "sagt", "sagen", "erstmals", "wieder", "weiter", "deutlich", "heute", "gestern", "morgen",
        "jahr", "jahre", "jahren", "prozent", "million", "millionen", "milliarde", "milliarden", "euro", "dollar",
        "steigt", "sinkt", "fällt", "bleibt", "kommt", "geht", "gibt", "macht", "zwei", "drei", "vier", "fünf",
        "the", "and", "for", "with", "from", "that", "this", "says", "said", "over", "after", "before", "into",
        "amid", "what", "will", "have", "has", "had", "are", "was", "were", "been", "its", "their", "about", "more",
        "than", "year", "years", "percent", "million", "billion", "news", "live", "video", "first", "new")
}
