package at.heinz.deviceai.research.exp20.m5

import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Random
import kotlin.math.abs

/**
 * M5 / WP1. Synthetic DEV/VAL only. A SEALED test set MUST be supplied by an independent author.
 * Each group has a symmetric 2 x 2 x 2 candidate cube and one query-selected correct record.
 * No method is entitled to gold roles, group IDs or candidate IDs when scoring texts.
 */
object M5Data {
    const val SEED = 2026103005L
    const val GROUPS_PER_KIND_PER_SPLIT = 64
    const val HEADER = "group\tsplit\ttype\trole\tcandidate_id\ttext"

    enum class Split { DEV, VAL }
    enum class Kind { L, S }

    data class Row(
        val group: String,
        val split: Split,
        val kind: Kind,
        val role: String,
        val candidateId: String,
        val text: String
    )

    // None of these paraphrase pairs comes from the P3 text bank.
    private val people = listOf(
        "Frau Albrecht", "Herr Bertram", "Frau Cerny", "Herr Dörner", "Frau Eder", "Herr Falk",
        "Frau Gasser", "Herr Heller", "Frau Ilic", "Herr Jäger", "Frau Kern", "Herr Leitner",
        "Frau Novak", "Herr Oswald", "Frau Pichler", "Herr Quirin", "Frau Reiter", "Herr Steiner",
        "Frau Tomic", "Herr Urban", "Frau Vogel", "Herr Weber", "Frau Xaver", "Herr Zeller"
    )
    private val tasks = listOf(
        "kontrolliert die Messgeräte", "ordnet die Werkzeugkisten", "bereitet die Ersatzteile vor",
        "prüft die Sicherheitsprotokolle", "sortiert die Bestelllisten", "reinigt die Arbeitsflächen",
        "inventarisiert die Geräte", "aktualisiert die Einsatzplanung"
    )
    private val places = listOf(
        "in der Druckerei" to "an dem Ort, an dem Unterlagen vervielfältigt werden",
        "in der Werkhalle" to "zwischen den großen Montagemaschinen",
        "im Archivraum" to "bei den abgelegten Aktenordnern",
        "in der Teeküche" to "dort, wo die Mitarbeitenden Getränke zubereiten",
        "in der Empfangshalle" to "am zentralen Zugang des Gebäudes",
        "im Prüfraum" to "wo die Messungen kontrolliert werden",
        "im Besprechungszimmer" to "dort, wo das Team seine Sitzungen abhält",
        "in der Versandzone" to "an der Stelle, an der die Lieferungen hinausgehen"
    )
    private val days = listOf(
        "am Dienstag" to "am zweiten Arbeitstag der Woche",
        "am Donnerstag" to "am Tag unmittelbar vor dem Freitag"
    )

    private fun randomIndex(r: Random, except: Int, bound: Int): Int {
        require(bound > 1)
        val sample = r.nextInt(bound - 1)
        return if (sample >= except) sample + 1 else sample
    }

    fun generate(): List<Row> {
        val rows = ArrayList<Row>(Split.values().size * Kind.values().size * GROUPS_PER_KIND_PER_SPLIT * 9)
        for (split in Split.values()) for (kind in Kind.values()) for (g in 0 until GROUPS_PER_KIND_PER_SPLIT) {
            val group = "M5-${split.name}-${kind.name}-${g.toString().padStart(3, '0')}"
            val salt = split.ordinal.toLong() * 1_000_003L + kind.ordinal * 100_003L + g * 997L
            val r = Random(SEED xor salt)
            // DEV and VAL use disjoint names: no verbatim memory can cross the split boundary.
            val personPool = if (split == Split.DEV) people.take(12) else people.drop(12)
            val person0 = r.nextInt(personPool.size)
            val person1 = randomIndex(r, person0, personPool.size)
            val place0 = r.nextInt(places.size)
            val place1 = randomIndex(r, place0, places.size)
            val day0 = r.nextInt(2)
            val task = tasks[r.nextInt(tasks.size)]
            val names = listOf(personPool[person0], personPool[person1])
            val locs = listOf(places[place0], places[place1])
            val ds = listOf(days[day0], days[1 - day0])
            // Every factor position is the target exactly eight times for each (split,kind).
            val target = g % 8
            fun person(i: Int) = names[i / 4]
            fun day(i: Int) = ds[(i / 2) % 2]
            fun place(i: Int) = locs[i % 2]
            val query = if (kind == Kind.L) {
                "Welchen Auftrag bearbeitet ${person(target)} ${day(target).first} ${place(target).first}?"
            } else {
                "Woran arbeitet ${person(target)} ${day(target).second} ${place(target).second}?"
            }
            rows += Row(group, split, kind, "QUERY", "-", query)
            val order = (0 until 8).toMutableList()
            for (i in order.lastIndex downTo 1) {
                val j = r.nextInt(i + 1)
                val tmp = order[i]; order[i] = order[j]; order[j] = tmp
            }
            for ((position, factor) in order.withIndex()) {
                val memory = "${person(factor)} $task ${day(factor).first} ${place(factor).first}."
                rows += Row(group, split, kind, if (factor == target) "TRUE" else "CANDIDATE", "C$position", memory)
            }
        }
        validate(rows)
        return rows
    }

    fun validate(rows: List<Row>) {
        require(rows.isNotEmpty())
        val groups = rows.groupBy { it.group }
        for ((id, group) in groups) {
            require(group.size == 9) { "$id: must contain query + eight memories" }
            require(group.all { it.group == id && it.split == group[0].split && it.kind == group[0].kind })
            require(group.count { it.role == "QUERY" } == 1)
            require(group.count { it.role == "TRUE" } == 1)
            require(group.count { it.role == "CANDIDATE" } == 7)
            val memories = group.filter { it.role != "QUERY" }
            require(memories.map { it.candidateId }.toSet() == (0..7).map { "C$it" }.toSet())
            require(group.single { it.role == "QUERY" }.candidateId == "-")
            require(memories.map { it.text }.toSet().size == 8) { "$id: duplicate candidate" }
            require(group.all { it.text.isNotBlank() && '\t' !in it.text && '\n' !in it.text && '\r' !in it.text })
        }
        val devMemories = rows.filter { it.split == Split.DEV && it.role != "QUERY" }.map { it.text }.toSet()
        val valMemories = rows.filter { it.split == Split.VAL && it.role != "QUERY" }.map { it.text }.toSet()
        require(devMemories.intersect(valMemories).isEmpty()) { "DEV/VAL contains duplicate memory text" }
        for (split in Split.values()) for (kind in Kind.values()) {
            val filtered = groups.values.filter { it[0].split == split && it[0].kind == kind }
            require(filtered.size == GROUPS_PER_KIND_PER_SPLIT)
            val positions = IntArray(8)
            for (group in filtered) {
                val memoryOrder = group.filter { it.role != "QUERY" }
                positions[memoryOrder.indexOfFirst { it.role == "TRUE" }]++
            }
            // Order is independently shuffled; every slot must occur (not necessarily perfectly balanced).
            require(positions.all { it > 0 }) { "$split/$kind: target placement exposes label" }
        }
    }

    fun write(rows: List<Row>, file: File) {
        validate(rows)
        file.parentFile?.mkdirs()
        val body = buildString {
            append(HEADER).append('\n')
            rows.forEach { append(it.group).append('\t').append(it.split.name).append('\t').append(it.kind.name)
                .append('\t').append(it.role).append('\t').append(it.candidateId).append('\t').append(it.text).append('\n') }
        }
        file.writeText(body, StandardCharsets.UTF_8)
    }

    fun read(file: File): List<Row> {
        val lines = file.readLines(StandardCharsets.UTF_8)
        require(lines.firstOrNull() == HEADER) { "invalid M5 TSV header" }
        val rows = lines.drop(1).filter { it.isNotEmpty() }.map { line ->
            val f = line.split('\t', limit = 6)
            require(f.size == 6) { "invalid M5 TSV field count" }
            Row(f[0], Split.valueOf(f[1]), Kind.valueOf(f[2]), f[3], f[4], f[5])
        }
        validate(rows)
        return rows
    }

    fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private val tokenPattern = Regex("[\\p{L}\\p{N}]+")
    private fun tokens(s: String): Set<String> = tokenPattern.findAll(s.lowercase()).map { it.value }.toSet()
    private fun jaccard(a: String, b: String): Double {
        val x = tokens(a); val y = tokens(b)
        return (x intersect y).size.toDouble() / (x union y).size.coerceAtLeast(1)
    }

    /** QUERY BLIND lexical sanity check. The preregistered CRE1-blind gate must be run separately. */
    fun blindLexicalWins(rows: List<Row>, split: Split): Pair<Int, Int> {
        val groups = rows.groupBy { it.group }.values.filter { it[0].split == split }
        var wins = 0
        for (group in groups) {
            val memories = group.filter { it.role != "QUERY" }
            val score = memories.map { candidate -> memories.sumOf { other -> jaccard(candidate.text, other.text) } }
            val trueIndex = memories.indexOfFirst { it.role == "TRUE" }
            if (score.indices.all { it == trueIndex || score[it] < score[trueIndex] - 1e-12 }) wins++
        }
        return wins to groups.size
    }

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 2 && args[0] in listOf("generate", "validate")) {
            "usage: M5Data generate|validate <DEV_VAL.tsv>; no SEALED generation"
        }
        val file = File(args[1])
        val rows = if (args[0] == "generate") generate().also { write(it, file) } else read(file)
        val dev = blindLexicalWins(rows, Split.DEV)
        val valResult = blindLexicalWins(rows, Split.VAL)
        println("M5 WP1 ${args[0]}: rows=${rows.size} groups=${rows.size / 9} sha256=${sha256(file)}")
        println("QUERY_BLIND_LEX DEV=${dev.first}/${dev.second} VAL=${valResult.first}/${valResult.second}")
        // This is only a cheap pre-check; it does NOT validate the preregistered CRE1 blind gate.
        check(valResult.first.toDouble() / valResult.second <= 0.175) { "VAL query-blind lexical gate failed" }
        println("CRE1_QUERY_BLIND_GATE=NOT_RUN; SEALED=NOT_GENERATED; DEVICE=NOT_RUN")
    }
}
