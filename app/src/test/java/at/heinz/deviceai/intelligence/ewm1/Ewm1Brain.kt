package at.heinz.deviceai.intelligence.ewm1

import org.json.JSONObject

/**
 * EWM2: one evidence package from the owner's memory (EWM discourse) and public sources (H5 index).
 * Personal questions and the owner's own named things go to memory; general definition questions go to sources;
 * a source never becomes an owner fact and owner memory is never overridden by a source.
 */
class Ewm1Brain(facts: List<Pair<String, String>>, private val sources: Ewm1SourceIndex,
                b0Rank: (String, Set<String>) -> List<String>) {
    val workspace = Ewm1Workspace(facts, b0Rank)
    private var lastSourceTopic: String? = null

    data class Answer(val decision: String, val reason: String, val route: String,
                      val memory: Ewm1Pack?, val source: Ewm1SourceIndex.Pack?) {
        /** Memory fact ids, or "SOURCE:<record id>" for public sources. */
        val cited: List<String> get() = when (route) {
            "MEMORY" -> memory?.cited.orEmpty()
            "SOURCE" -> if (decision == "QUERY") source?.evidence.orEmpty().map { "SOURCE:" + it.record.id } else emptyList()
            else -> emptyList()
        }

        fun render(factText: (String) -> String): String = JSONObject().put("decision", decision).put("reason", reason)
            .put("route", route)
            .put("owner_memory", memory?.let { JSONObject(it.render(factText)) } ?: JSONObject.NULL)
            .put("public_sources", source?.let { JSONObject(it.render()) } ?: JSONObject.NULL)
            .put("rule", "Owner memory outranks public sources; sources are unverified observations, never owner facts.")
            .toString()
    }

    fun ask(question: String): Answer {
        val topic = Ewm1SourceIndex.topicOf(question)
        val personal = PERSONAL.containsMatchIn(question)
        if (topic != null && !personal && !ownerDefines(topic)) {
            val pack = sources.retrieve(question)
            if (pack.decision != "ABSTAIN") {
                lastSourceTopic = topic
                return Answer(pack.decision, pack.reason, "SOURCE", null, pack)
            }
        }
        // "Was ist eine Anhängerkupplung?" … "Hat mein Auto eine?": the bare pronoun takes up the source topic.
        val rewritten = lastSourceTopic?.takeIf { TRAILING_INDEFINITE.containsMatchIn(question) }
            ?.let { question.trimEnd('?', '.', '!', ' ') + " " + it + "?" } ?: question
        lastSourceTopic = null
        val mem = workspace.decide(rewritten)
        return Answer(mem.decision, mem.reason, if (mem.decision == "QUERY") "MEMORY" else "NONE", mem, null)
    }

    /** The owner's own named thing defined in memory ("Das Projekt Lichtblick ist eine Lern-App …") outranks an encyclopedia. */
    private fun ownerDefines(topic: String): Boolean {
        val key = Ewm1SourceIndex.titleKey(topic)
        return workspace.memory.facts.any { f ->
            val m = f.mentions.firstOrNull() ?: return@any false
            val label = Ewm1SourceIndex.titleKey(m.label)
            (label == key || label.endsWith(" $key") || m.key == key) && f.tokens.getOrNull(m.end + 1)?.lower in setOf("ist", "sind")
        }
    }

    companion object {
        private val PERSONAL = Regex("(?iu)\\b(mein|meine|meinen|meinem|meiner|meines|unser|unsere|unseren|unserem|unserer|" +
            "ich|mir|mich|wir|uns|sein|seine|seinen|seinem|seiner|ihr|ihre|ihren|ihrem|ihrer|dessen|deren|er|sie)\\b")
        private val TRAILING_INDEFINITE = Regex("(?iu)\\b(eine|einen|eins|einer|welche)\\s*[?.!]*\\s*$")
    }
}
