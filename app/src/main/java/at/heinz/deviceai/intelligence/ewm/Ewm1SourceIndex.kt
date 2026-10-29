// Product copy of the EWM2 research class (PR #224, code freeze e1669271): same logic; only the package,
// the removed EWM1_TRACE debug output and the removed research-only N0 baseline differ.
package at.heinz.deviceai.intelligence.ewm

import at.heinz.deviceai.intelligence.ewm.Ewm1Lexicon.fold
import at.heinz.deviceai.learning.ContinuumKnowledgeArchive
import org.json.JSONArray
import org.json.JSONObject

/**
 * EWM1-H5: public source excerpts (the knowledge archive) as labelled evidence for the language model.
 * Sources stay SOURCE_OBSERVATION: never owner facts, never instructions, always with origin, licence and age.
 */
class Ewm1SourceIndex(records: List<ContinuumKnowledgeArchive.SourceRecord>) {
    enum class SourceClass { ARTICLE, CALENDAR_DAY, YEAR, DISAMBIGUATION, VERY_SHORT }

    data class Source(val record: ContinuumKnowledgeArchive.SourceRecord, val sourceClass: SourceClass, val truncated: Boolean,
                      val twins: List<String>, val titleKey: String)

    data class Pack(val decision: String, val reason: String, val evidence: List<Source>) {
        /** Data block for the model; the excerpt is unverified, possibly cut off and dated by collection only. */
        fun render(): String = JSONObject().put("decision", decision).put("reason", reason)
            .put("sources", JSONArray(evidence.map { s ->
                JSONObject().put("id", s.record.id).put("title", s.record.title).put("url", s.record.url)
                    .put("licence", s.record.attribution).put("trust", "SOURCE_OBSERVATION_UNVERIFIED")
                    .put("collected_at_ms", s.record.collectedAt).put("source_date", s.record.sourceDate ?: JSONObject.NULL)
                    .put("class", s.sourceClass.name).put("truncated", s.truncated).put("text", s.record.text)
            })).toString()
    }

    val sources: List<Source>
    private val byKey: Map<String, List<Source>>

    init {
        val bySha = records.groupBy { it.sha256 }
        sources = records.map { r ->
            Source(r, classify(r), r.text.length >= 380 && !Regex("[.!?»“\")]\\s*$").containsMatchIn(r.text),
                bySha.getValue(r.sha256).filter { it.id != r.id }.map { it.id }, titleKey(r.title))
        }
        byKey = sources.groupBy { it.titleKey }
    }

    /** Question -> evidence. Only a title match counts as "about this topic"; a word somewhere in a text does not. */
    fun retrieve(question: String): Pack {
        val topic = topicOf(question) ?: return Pack("ABSTAIN", "NO_TOPIC", emptyList())
        val key = titleKey(topic)
        val exact = byKey[key].orEmpty()
        val candidates = exact.ifEmpty {
            sources.filter { s -> s.titleKey.length >= 4 && key.length >= 4 && sameTopic(key, s.titleKey) }
        }
        if (candidates.isEmpty()) return Pack("ABSTAIN", "NO_SOURCE_FOR_TOPIC", emptyList())
        val articles = candidates.filter { it.sourceClass == SourceClass.ARTICLE || it.sourceClass == SourceClass.VERY_SHORT }
        if (articles.isEmpty()) {
            val disambiguation = candidates.filter { it.sourceClass == SourceClass.DISAMBIGUATION }
            return if (disambiguation.isNotEmpty()) Pack("CLARIFY", "DISAMBIGUATION_PAGE", disambiguation.take(1))
            else Pack("QUERY", "LOW_VALUE_PAGE", candidates.take(1))
        }
        // Redirect twins are one source. Wikipedia convention: the title without "(…)" is the primary topic;
        // several different articles that all carry a qualifier need a question back.
        val distinct = articles.distinctBy { it.record.sha256 }.let { d ->
            d.filter { !it.record.title.trimEnd().endsWith(")") }.ifEmpty { d } }
        if (distinct.size > 1) return Pack("CLARIFY", "SEVERAL_ARTICLES", distinct.take(3))
        return Pack("QUERY", if (distinct.first().truncated) "SUPPORTED_TRUNCATED" else "SUPPORTED", listOf(distinct.first()))
    }

    companion object {
        private val FRAMES = listOf(
            Regex("(?iu)^\\s*(?:kannst|könntest)\\s+du\\s+mir\\s+(?:etwas|was|mehr)\\s+(?:über|zu)\\s+(.+?)\\s+(?:sagen|erzählen|erklären)\\s*[?.!]*\\s*$"),
            Regex("(?iu)^\\s*(?:was|wer)\\s+(?:weißt|kennst)\\s+du\\s+(?:über|von|zu)\\s+(.+?)\\s*[?.!]*\\s*$"),
            Regex("(?iu)^\\s*erkläre?\\s+(?:mir\\s+)?(?:bitte\\s+)?(.+?)\\s*[?.!]*\\s*$"),
            Regex("(?iu)^\\s*erzähl(?:e)?\\s+(?:mir\\s+)?(?:etwas\\s+|was\\s+)?(?:über|von)\\s+(.+?)\\s*[?.!]*\\s*$"),
            Regex("(?iu)^\\s*was\\s+bedeutet\\s+(.+?)\\s*[?.!]*\\s*$"),
            Regex("(?iu)^\\s*was\\s+(?:macht|tut)\\s+(?:ein|eine|der|die|das)\\s+(.+?)\\s*[?.!]*\\s*$"),
            Regex("(?iu)^\\s*(?:was|wer)\\s+(?:ist|sind|war|waren)\\s+(.+?)\\s*[?.!]*\\s*$")
        )
        private val TRAILING = Regex("(?iu)\\s+(?:eigentlich|denn|genau|nochmal|noch einmal|bitte)$")
        private val LEADING = Regex("(?iu)^(?:der|die|das|den|dem|des|ein|eine|einen|einem|eines|einer)\\s+")

        fun topicOf(question: String): String? {
            if (question.length !in 4..240) return null
            for (f in FRAMES) f.find(question)?.let { m ->
                val t = m.groupValues[1].replace(TRAILING, "").replace(LEADING, "").trim().trim('„', '“', '"', '\'')
                return t.takeIf { it.length in 2..120 && it.split(Regex("\\s+")).size <= 8 }
            }
            return null
        }

        /** Title without disambiguating parenthesis, case- and umlaut-folded, whitespace-normalised. */
        fun titleKey(title: String): String =
            fold(title.replace(Regex("\\s*\\([^)]*\\)\\s*$"), "")).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

        /** Same topic: inflection ("Adiabat"/"Adiabate") or plural, never a mere shared word. */
        private fun sameTopic(a: String, b: String): Boolean {
            if (a == b) return true
            val wa = a.split(' '); val wb = b.split(' ')
            if (wa.size != wb.size) return false
            return wa.zip(wb).all { (x, y) -> x == y || (x.length >= 4 && y.length >= 4 && Ewm1Lexicon.stem(x) == Ewm1Lexicon.stem(y)) }
        }

        fun classify(r: ContinuumKnowledgeArchive.SourceRecord): SourceClass {
            val t = r.title; val head = r.text.take(200)
            return when {
                Regex("\\d{1,2}\\. (Januar|Jänner|Februar|März|April|Mai|Juni|Juli|August|September|Oktober|November|Dezember)").matches(t) ->
                    SourceClass.CALENDAR_DAY
                Regex("\\d{1,4}( v\\. Chr\\.)?").matches(t) -> SourceClass.YEAR
                Regex("steht für|bezeichnet:|Begriffsklärung|mehrere Bedeutungen").containsMatchIn(head) || t.contains("(Begriffsklärung)") ->
                    SourceClass.DISAMBIGUATION
                r.text.length < 120 -> SourceClass.VERY_SHORT
                else -> SourceClass.ARTICLE
            }
        }
    }
}
