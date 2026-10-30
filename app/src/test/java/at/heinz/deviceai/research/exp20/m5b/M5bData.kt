package at.heinz.deviceai.research.exp20.m5b

import java.io.File
import kotlin.random.Random

/**
 * EXP-20 M5b: semantic retrieval with DISJOINT paraphrase families per split (preregistered in
 * docs/research/exp20/EXP-20-M5B-PREREGISTRATION-20261031.md). TRAIN families live here; the VAL families are written
 * AFTER this file and the normalizer were committed (see M5bValFamilies.kt). SEALED must come from another author.
 * Each group: one query, eight candidates forming a 2 persons x 2 days x 2 places factorial with one shared task;
 * the target factor position is exactly balanced and the listing order is permuted independently.
 */
object M5bData {
    enum class Split { TRAIN, VAL }
    enum class Kind { L, S }
    data class Row(val group: String, val split: Split, val kind: Kind, val role: String, val cand: String, val text: String)
    /** Paraphrases per canonical day / place index; `near` keeps TRAIN's construction type, `far` uses a new one. */
    class Families(val days: List<List<String>>, val places: List<List<String>>)

    const val HEADER = "group\tsplit\tkind\trole\tcandidate\ttext"
    const val GROUPS = 64
    const val SEED = 2026103105L

    val days = listOf("am Montag", "am Dienstag", "am Mittwoch", "am Donnerstag", "am Freitag")
    val places = listOf("in der Bibliothek", "in der Kantine", "in der Garage", "im Labor", "in der Poststelle", "im Lager",
        "in der Werkstatt", "im Serverraum")
    private val tasks = listOf("überprüft die Lieferscheine", "wartet die Klimaanlage", "ordnet die Ersatzschlüssel",
        "testet die Notbeleuchtung", "zählt die Kartons", "erneuert die Etiketten")
    private val people = listOf(
        "Frau Amann", "Herr Brandstätter", "Frau Czerny", "Herr Danner", "Frau Egli", "Herr Fasching", "Frau Grill",
        "Herr Hartl", "Frau Iser", "Herr Jank", "Frau Kogler", "Herr Lackner",
        "Frau Maurer", "Herr Nagl", "Frau Oberhuber", "Herr Pfeifer", "Frau Ranftl", "Herr Schober", "Frau Thaler",
        "Herr Unger", "Frau Wallner", "Herr Ziegler", "Frau Auer", "Herr Bichler")

    /** TRAIN paraphrase families (two per slot). Fixed before any VAL wording existed. */
    val train = Families(
        days = listOf(
            listOf("am ersten Arbeitstag der Woche", "einen Tag vor dem Dienstag"),
            listOf("am zweiten Arbeitstag der Woche", "einen Tag vor dem Mittwoch"),
            listOf("am dritten Arbeitstag der Woche", "einen Tag vor dem Donnerstag"),
            listOf("am vierten Arbeitstag der Woche", "einen Tag vor dem Freitag"),
            listOf("am fünften Arbeitstag der Woche", "einen Tag vor dem Samstag")),
        places = listOf(
            listOf("wo man Bücher ausleihen kann", "zwischen den Regalen voller Bücher"),
            listOf("wo mittags das Essen ausgegeben wird", "wo die Belegschaft zu Mittag isst"),
            listOf("wo die Firmenautos abgestellt werden", "beim Stellplatz der Dienstwagen"),
            listOf("wo die chemischen Proben untersucht werden", "bei den Reagenzgläsern"),
            listOf("wo die Briefe sortiert werden", "wo eingehende Sendungen verteilt werden"),
            listOf("wo die Vorräte gestapelt sind", "zwischen den Paletten mit Vorräten"),
            listOf("wo Maschinen repariert werden", "an der Werkbank mit dem Schraubstock"),
            listOf("wo die Rechner der Firma laufen", "bei den gekühlten Computerschränken")))

    fun generate(split: Split, families: Families): List<Row> {
        val rows = ArrayList<Row>()
        val pool = if (split == Split.TRAIN) people.take(12) else people.drop(12)
        for (kind in Kind.values()) for (g in 0 until GROUPS) {
            val id = "M5B-${split.name}-${kind.name}-${g.toString().padStart(3, '0')}"
            val r = Random(SEED * 31 + split.ordinal * 100_003L + kind.ordinal * 10_007L + g)
            val ps = pool.shuffled(r).take(2); val ds = days.indices.shuffled(r).take(2); val ls = places.indices.shuffled(r).take(2)
            val task = tasks[r.nextInt(tasks.size)]
            fun mem(f: Int) = "${ps[f / 4]} $task ${days[ds[(f / 2) % 2]]} ${places[ls[f % 2]]}."
            val target = (g + split.ordinal + kind.ordinal) % 8          // each factor position is the target 8x per (split, kind)
            val td = ds[(target / 2) % 2]; val tl = ls[target % 2]
            // the paraphrase choice is fixed by the group index: S groups alternate over both family members
            val dayText = if (kind == Kind.L) days[td] else families.days[td][g % families.days[td].size]
            val placeText = if (kind == Kind.L) places[tl] else families.places[tl][(g / 2) % families.places[tl].size]
            rows += Row(id, split, kind, "QUERY", "-", "Was erledigt ${ps[target / 4]} $dayText $placeText?")
            // listing position of the target is balanced independently of its factor position (8x each per split/kind)
            val order = (0 until 8).filter { it != target }.shuffled(r).toMutableList().also { it.add((g / 8 + split.ordinal * 3 + kind.ordinal * 5) % 8, target) }
            for ((pos, f) in order.withIndex()) rows += Row(id, split, kind, if (f == target) "TRUE" else "CANDIDATE", "C$pos", mem(f))
        }
        validate(rows)
        return rows
    }

    fun validate(rows: List<Row>) {
        for ((_, g) in rows.groupBy { it.group }) {
            require(g.size == 9 && g.count { it.role == "QUERY" } == 1 && g.count { it.role == "TRUE" } == 1)
            require(g.filter { it.role != "QUERY" }.map { it.text }.distinct().size == 8)
        }
        for ((key, gs) in rows.groupBy { it.split to it.kind }) {
            val pos = gs.filter { it.role == "TRUE" }.groupingBy { it.cand }.eachCount()
            require(pos.size == 8 && pos.values.all { it == GROUPS / 8 }) { "$key: target position imbalance $pos" }
        }
    }

    fun write(rows: List<Row>, f: File) { f.parentFile?.mkdirs(); f.writeText(HEADER + "\n" + rows.joinToString("\n") { "${it.group}\t${it.split}\t${it.kind}\t${it.role}\t${it.cand}\t${it.text}" } + "\n") }
    fun read(f: File) = f.readLines().drop(1).filter { it.isNotEmpty() }.map { val c = it.split('\t', limit = 6); Row(c[0], Split.valueOf(c[1]), Kind.valueOf(c[2]), c[3], c[4], c[5]) }
}
