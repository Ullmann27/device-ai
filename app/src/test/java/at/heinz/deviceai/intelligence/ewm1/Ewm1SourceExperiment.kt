package at.heinz.deviceai.intelligence.ewm1

import at.heinz.deviceai.learning.ContinuumArchiveAnswerPolicy
import at.heinz.deviceai.learning.ContinuumKnowledgeArchive
import at.heinz.deviceai.learning.ContinuumMemoryDialogue
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.nio.file.Files
import org.json.JSONObject

/**
 * EWM1-H5 runner on a local knowledge-archive ZIP (never committed). The archive is loaded only through the
 * unchanged production import; questions are generated at run time; only aggregate counts are written.
 */
object Ewm1SourceExperiment {
    const val GEN16_ARCHIVE_SHA256 = "92e95ac2708d192bd2596cd649db7491fc66ab59481d8861ef906ac2d91c24dc"
    val UNKNOWN_TOPICS = listOf("Quantenchromodynamik", "Photosynthese", "Mount Everest", "Rotkehlchen", "Kaffeeröstung",
        "Zugspitze", "Schachgroßmeister", "Tiefseegraben", "Vulkanismus", "Bienenwachs", "Glasbläserei", "Heißluftballon",
        "Kernspintomographie", "Leuchtturm", "Mondfinsternis", "Nordlicht", "Origami", "Pinguin", "Quecksilber", "Regenwald",
        "Seidenstraße", "Tsunami", "Uhrmacherei", "Wanderfalke", "Xylophon", "Yak", "Zitronensäure", "Biathlon", "Eisvogel",
        "Fjord", "Geysir", "Hängebrücke", "Igel", "Jazz", "Kompost", "Lavendel", "Murmeltier", "Nougat", "Ozonschicht", "Polarstern")
    private val FORMS = listOf("Was ist %s?", "Was weißt du über %s?", "Erkläre mir bitte %s.", "Kannst du mir etwas über %s sagen?", "was ist %s?")

    fun load(zip: File): List<ContinuumKnowledgeArchive.SourceRecord> {
        val dir = Files.createTempDirectory("ewm1-archive-").toFile()
        try {
            val archive = ContinuumKnowledgeArchive(dir)
            zip.inputStream().use { archive.importZip(it) }
            return File(dir, "continuum-knowledge-v1").listFiles { f -> f.name.endsWith(".rec") }!!.sortedBy { it.name }.map { f ->
                DataInputStream(ByteArrayInputStream(f.readBytes())).use { s ->
                    s.readInt(); s.readInt()
                    val id = s.readUTF(); val kind = ContinuumKnowledgeArchive.Kind.valueOf(s.readUTF()); val title = s.readUTF()
                    val url = s.readUTF(); val attr = s.readUTF(); val at = s.readLong(); val date = if (s.readBoolean()) s.readUTF() else null
                    ContinuumKnowledgeArchive.SourceRecord(id, kind, title, url, attr, at, date, s.readUTF(), s.readUTF())
                }
            }
        } finally { dir.deleteRecursively() }
    }

    @JvmStatic fun main(args: Array<String>) {
        require(args.size >= 2 && args[1] in setOf("DEV", "TEST")) { "usage: <archive.zip> <DEV|TEST> [<new-report.json> <frozen-commit>]" }
        val zip = File(args[0]); val split = args[1]
        val sha = ContinuumMemoryDialogue.hash(zip.readBytes())
        if (split == "TEST") {
            require(sha == GEN16_ARCHIVE_SHA256) { "TEST runs only on the preregistered archive" }
            require(args.size == 4 && Regex("[a-f0-9]{40}").matches(args[3]) && !File(args[2]).exists())
        }
        val records = load(zip)
        val index = Ewm1SourceIndex(records)
        val dir = Files.createTempDirectory("ewm1-a0-").toFile()
        val counts = linkedMapOf("A0" to linkedMapOf<String, Int>(), "S1" to linkedMapOf())
        val nanos = ArrayList<Long>()
        val verbose = System.getenv("EWM1_VERBOSE") == "1" && split == "DEV"
        try {
            val archive = ContinuumKnowledgeArchive(dir)
            zip.inputStream().use { archive.importZip(it) }
            fun inSplit(id: String) = (id[0] in '0'..'7') == (split == "DEV")
            val targets = index.sources.filter { inSplit(it.record.id) &&
                (it.sourceClass == Ewm1SourceIndex.SourceClass.ARTICLE || it.sourceClass == Ewm1SourceIndex.SourceClass.VERY_SHORT) }
            val questions = ArrayList<Triple<String, Set<String>, String>>()
            for (t in targets) {
                val title = t.record.title.replace(Regex("\\s*\\([^)]*\\)\\s*$"), "")
                FORMS.forEachIndexed { i, f -> questions += Triple(f.format(if (i == 4) title.lowercase() else title), setOf(t.record.id) + t.twins, "F${i + 1}") }
            }
            val present = records.map { Ewm1SourceIndex.titleKey(it.title) }.toSet()
            UNKNOWN_TOPICS.forEachIndexed { i, topic ->
                if ((i % 2 == 0) == (split == "DEV") && Ewm1SourceIndex.titleKey(topic) !in present &&
                    records.none { it.text.contains(topic, ignoreCase = true) }) questions += Triple("Was ist $topic?", emptySet(), "UNKNOWN")
            }
            for ((q, gold, form) in questions) {
                val a0 = runCatching { ContinuumArchiveAnswerPolicy.select(q, archive.search(q, limit = 20)) }.getOrNull()?.source?.id
                val start = System.nanoTime(); val pack = index.retrieve(q); nanos += System.nanoTime() - start
                val s1 = if (pack.decision == "QUERY") pack.evidence.first().record.id else null
                for ((sys, cited) in listOf("A0" to a0, "S1" to s1)) {
                    val outcome = if (gold.isEmpty()) (if (cited == null) "ABSTAINED" else "FALSE_CITE")
                        else when { cited == null -> "MISSED"; cited in gold -> "ANSWERED"; else -> "WRONG" }
                    counts.getValue(sys).merge(outcome, 1, Int::plus); counts.getValue(sys).merge("$form:$outcome", 1, Int::plus)
                }
                if (verbose && s1 !in gold && gold.isNotEmpty()) println("MISS[$form] ${pack.decision}/${pack.reason} :: $q")
            }
            val sorted = nanos.sorted()
            val result = JSONObject().put("experiment", "EWM1-H5").put("split", split).put("archiveSha256", sha)
                .put("commit", if (args.size == 4) args[3] else JSONObject.NULL).put("records", records.size)
                .put("targets", targets.size).put("questions", questions.size).put("systems", JSONObject(counts as Map<*, *>))
                .put("s1MillisMedian", sorted[sorted.size / 2] / 1e6).put("s1MillisP95", sorted[(sorted.size * 95) / 100] / 1e6)
            if (args.size >= 3) { val out = File(args[2]); check(out.createNewFile()); out.writeText(result.toString(2) + "\n") }
            println(result.toString())
        } finally { dir.deleteRecursively() }
    }
}
