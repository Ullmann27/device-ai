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
    private var lastNoun: String? = null

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
        // "Kannst du mir etwas über Kreta sagen?": the frame's "mir" is not personal, only the topic counts
        val personal = PERSONAL.containsMatchIn(topic ?: question)
        if (topic != null && !personal && !ownerDefines(topic) && !ownerEntity(topic, question)) {
            val pack = sources.retrieve(question)
            if (pack.decision != "ABSTAIN") {
                lastNoun = topic
                return Answer(pack.decision, pack.reason, "SOURCE", null, pack)
            }
            // "Was ist ein Cello?": memory that merely mentions a cello does not define one.
            if (simple(topic)) {
                lastNoun = topic
                return Answer("ABSTAIN", "NO_SOURCE:$topic", "NONE", null, null)
            }
        }
        // "Was ist eine Anhängerkupplung?" … "Hat mein Auto eine?": the bare pronoun takes up the last noun of the dialogue.
        val rewritten = lastNoun?.takeIf { TRAILING_INDEFINITE.containsMatchIn(question) }
            ?.let { question.trimEnd('?', '.', '!', ' ') + " " + it + "?" } ?: question
        val mem = workspace.decide(rewritten)
        lastNoun = Ewm1Tokens.of(rewritten).lastOrNull { Ewm1Fact.isNominal(it) && !it.sentenceInitial &&
            fold(it.lower) !in memory.names.map(::fold) }?.text ?: lastNoun
        return Answer(mem.decision, mem.reason, if (mem.decision == "QUERY") "MEMORY" else "NONE", mem, null)
    }

    private val memory get() = workspace.memory

    /** The owner's own person or pet ("Wer ist Anna?" with "Meine Schwester heißt Anna.") outranks a namesake in the sources. */
    private fun ownerEntity(topic: String, question: String): Boolean {
        val key = fold(Ewm1SourceIndex.titleKey(topic))
        val named = memory.names.any { fold(it) == key }
        val kin = memory.aliases.any { (role, targets) -> Ewm1Fact.isRole(role) && targets.keys.any { fold(it) == key } }
        return kin || named && WHO.containsMatchIn(question)
    }

    /** One plain noun phrase ("ein Cello", "Opel Corsa"), not an owner attribute ("Bellos Lieblingsspielzeug", "Beruf von Klara"). */
    private fun simple(topic: String): Boolean {
        val words = topic.split(Regex("\\s+")).map { fold(it.lowercase()) }
        val keys = memory.allKeys.map(::fold).toSet()
        val nominal = topic.split(Regex("\\s+")).all { w -> w.first().isUpperCase() || w.first().isDigit() }
        // "Bellos …", "Jonas' …": a genitive of a memory entity makes it the owner's attribute
        return nominal && words.size <= 3 && words.none { it in LINKS || it.trimEnd('\'', '’') in keys && it.last() in "'’" ||
            it.length >= 4 && it.endsWith("s") && it.dropLast(1) in keys }
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
        private val WHO = Regex("(?iu)^\\s*(wer\\b|was\\s+(weißt|kennst)\\s+du\\s+über)")
        private val LINKS = setOf("von", "vom", "der", "des", "für", "im", "in", "am", "beim", "zum", "zur", "mit", "und")
        private fun fold(s: String) = Ewm1Lexicon.fold(s)
    }
}
