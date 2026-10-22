package at.heinz.deviceai.learning.span

import java.security.MessageDigest
import java.util.Locale
import java.util.Random

/**
 * EXP-04/05/06 research benchmark for CSX1 abstention and evidence binding (JVM only, no Android).
 *
 * Deterministic generator with strict separation: entity ids (first letter), answer values
 * (disjoint pools) and sentence families (test-only families) differ between TRAIN, VALIDATION
 * and TEST. Every unanswerable case is built from explicit fact objects, so the generator can
 * prove structurally that its evidence holds no positive fact for the asked (entity, relation).
 * TEST is frozen by its SHA-256 ([TEST_SHA256]); thresholds may only be chosen on VALIDATION.
 *
 * Run: see docs/CONTINUUM-CSX1-ABSTENTION-OPUS-20261022.md
 */
object ContinuumSpanAbstentionBench {
    enum class Split { TRAIN, VALIDATION, TEST }
    enum class Kind { ANSWER, MULTI_FACT, CONTRAST, MULTI_ENTITY, SAME_CLAUSE_ENTITIES, TEMPORAL, UNIT_MULTIWORD,
        OTHER_RELATION, OTHER_ENTITY, NEGATED }

    data class Case(
        val id: String,
        val split: Split,
        val kind: Kind,
        val family: String,
        val relation: String,
        val example: ContinuumResidualSpanCore.Example,
        /** Tokens in the evidence that would be an entity confusion or a negation error. */
        val traps: Set<String>,
        /** True when the sentence family or question template never occurs in TRAIN. */
        val unseenForm: Boolean
    ) {
        val answerable: Boolean get() = example.answer != null
    }

    private data class Relation(
        val name: String,
        val type: String,
        val facts: List<String>,      // evidence templates, {T} type word, {E} entity, {V} value
        val heldOut: List<String>,    // TEST-only evidence families
        val questions: List<String>,  // last one is TEST-only
        val negated: String,
        val contrast: String?,
        val values: List<String>,
        /** VALIDATION-only forms (never in TRAIN or TEST): model selection for unseen forms without test peeking. */
        val valFamilies: List<String> = emptyList(),
        val valQuestion: String? = null
    )

    private val CITIES = listOf("Wien", "Graz", "Linz", "Salzburg", "Innsbruck", "Bregenz", "Klagenfurt", "Villach",
        "Wels", "Steyr", "Krems", "Dornbirn", "Feldkirch", "Leoben", "Baden", "Amstetten", "Kufstein", "Lienz",
        "Hallein", "Bludenz", "Tulln", "Ternitz", "Mödling", "Traun", "Spittal", "Wolfsberg", "Gmunden", "Zwettl",
        "Melk", "Eisenstadt", "Bruck", "Imst", "Schwaz", "Hall", "Telfs", "Wörgl")
    private val TOOLS = listOf("Zange", "Greifer", "Bohrer", "Hammer", "Schleifer", "Fräse", "Säge", "Feile",
        "Meißel", "Lötkolben", "Spachtel", "Hobel", "Raspel", "Ahle", "Pinzette", "Kelle", "Bürste", "Schaber",
        "Zwinge", "Stanze", "Lupe", "Klemme", "Winkel", "Pinsel")
    private val COLORS = listOf("rot", "blau", "grün", "gelb", "schwarz", "weiß", "orange", "violett", "grau",
        "braun", "türkis", "silbern", "golden", "rosa", "beige", "oliv", "purpur", "ocker")
    private val PEOPLE = listOf("Anna", "Bernd", "Clara", "David", "Elena", "Felix", "Greta", "Hannes", "Ida",
        "Jonas", "Katrin", "Lukas", "Mira", "Niklas", "Olga", "Paul", "Quirin", "Rosa", "Sven", "Tina", "Ulla",
        "Viktor", "Wanda", "Xaver")
    private val SYLLABLES = listOf("zor", "lib", "kan", "mex", "tur", "vok", "pel", "qua", "rin", "sut", "dax", "fen")

    private fun relations(split: Split, rnd: Random): List<Relation> {
        fun part(pool: List<String>): List<String> {
            val third = pool.size / 3
            return when (split) {
                Split.TRAIN -> pool.subList(0, third)
                Split.VALIDATION -> pool.subList(third, 2 * third)
                Split.TEST -> pool.subList(2 * third, pool.size)
            }
        }
        val codeLetters = when (split) { Split.TRAIN -> "ABCDEFGH"; Split.VALIDATION -> "JKLM"; Split.TEST -> "PQRSTUVW" }
        val codes = (1..12).map { "" + codeLetters[rnd.nextInt(codeLetters.length)] + codeLetters[rnd.nextInt(codeLetters.length)] +
            "-" + (100 + rnd.nextInt(900)) }.distinct()
        val ports = (1..12).map { (when (split) { Split.TRAIN -> 2000; Split.VALIDATION -> 5000; Split.TEST -> 7000 } + rnd.nextInt(2000)).toString() }.distinct()
        val libs = (1..10).map { SYLLABLES[rnd.nextInt(SYLLABLES.size)] + SYLLABLES[rnd.nextInt(SYLLABLES.size)] +
            when (split) { Split.TRAIN -> "a"; Split.VALIDATION -> "o"; Split.TEST -> "u" } }.distinct()
        return listOf(
            Relation("location", "Einheit", listOf("{T} {E} steht in {V}.", "{T} {E} befindet sich in {V}."),
                listOf("In {V} steht {T} {E}.", "{T} {E} wurde nach {V} verlegt."),
                listOf("Wo steht {E}?", "In welcher Stadt befindet sich {E}?", "In welcher Stadt ist {E} untergebracht?"),
                "{T} {E} steht nicht in {V}.", "{T} {E} steht nicht in {A}, sondern in {V}.", part(CITIES)),
            Relation("tool", "Roboter", listOf("{T} {E} nutzt eine {V}.", "{T} {E} arbeitet mit einem {V}."),
                listOf("Für {T} {E} ist ein {V} vorgesehen."),
                listOf("Welches Werkzeug nutzt {E}?", "Mit welchem Werkzeug arbeitet {E}?", "Welches Werkzeug braucht {E}?"),
                "{T} {E} nutzt keine {V}.", "{T} {E} nutzt nicht {A}, sondern {V}.", part(TOOLS)),
            Relation("module", "Gerät", listOf("{T} {E} verwendet Modul {V}.", "{T} {E} läuft mit dem Modul {V}."),
                listOf("Modul {V} ist in {T} {E} verbaut."),
                listOf("Welches Modul verwendet {E}?", "Mit welchem Modul läuft {E}?", "Welches Modul steckt in {E}?"),
                "{T} {E} verwendet kein Modul {V}.", null, codes),
            Relation("color", "Station", listOf("Die Hülle von {E} ist {V}.", "{T} {E} hat eine {V} Hülle."),
                listOf("{T} {E} ist {V} lackiert."),
                listOf("Welche Farbe hat {E}?", "In welcher Farbe ist die Hülle von {E}?", "Welchen Farbton hat {E}?"),
                "Die Hülle von {E} ist nicht {V}.", null, part(COLORS)),
            Relation("port", "Dienst", listOf("{T} {E} lauscht auf Port {V}.", "{T} {E} öffnet Port {V}."),
                listOf("Port {V} gehört zu {T} {E}."),
                listOf("Auf welchem Port lauscht {E}?", "Welchen Port öffnet {E}?", "Welcher Port gehört zu {E}?"),
                "{T} {E} öffnet nicht Port {V}.", null, ports),
            Relation("library", "Paket", listOf("{T} {E} benötigt die Bibliothek {V}.", "{T} {E} bindet die Bibliothek {V} ein."),
                listOf("Die Bibliothek {V} wird von {T} {E} geladen."),
                listOf("Welche Bibliothek benötigt {E}?", "Welche Bibliothek bindet {E} ein?", "Welche Bibliothek lädt {E}?"),
                "{T} {E} benötigt keine Bibliothek {V}.", null, libs),
            Relation("developer", "Modell", listOf("{T} {E} wurde von {V} entwickelt.", "{V} hat {T} {E} entwickelt."),
                listOf("Die Entwicklung von {T} {E} leitete {V}."),
                listOf("Wer hat {E} entwickelt?", "Von wem wurde {E} entwickelt?", "Wer ist der Entwickler von {E}?"),
                "{T} {E} wurde nicht von {V} entwickelt.", null, part(PEOPLE))
        )
    }

    private val VALIDATION_FORMS = mapOf(
        "location" to (listOf("{V} ist der Standort von {T} {E}.", "Standort von {T} {E} ist {V}.") to "Wo ist {E} stationiert?"),
        "tool" to (listOf("Eine {V} gehört zur Ausstattung von {T} {E}.") to "Welches Werkzeug setzt {E} ein?"),
        "module" to (listOf("In {T} {E} steckt das Modul {V}.") to "Welches Modul besitzt {E}?"),
        "color" to (listOf("Die Farbe von {T} {E} ist {V}.") to "Wie ist {E} gefärbt?"),
        "port" to (listOf("Über Port {V} erreicht man {T} {E}.") to "Über welchen Port erreicht man {E}?"),
        "library" to (listOf("{T} {E} hängt von der Bibliothek {V} ab.") to "Von welcher Bibliothek hängt {E} ab?"),
        "developer" to (listOf("Entwickelt wurde {T} {E} von {V}.") to "Wer entwickelte {E}?")
    )

    private fun fill(t: String, r: Relation, e: String, v: String, a: String? = null) =
        t.replace("{T}", r.type).replace("{E}", e).replace("{V}", v).replace("{A}", a ?: "")

    private fun entity(split: Split, rnd: Random, used: MutableSet<String>): String {
        val letters = when (split) { Split.TRAIN -> "ABCDEFGH"; Split.VALIDATION -> "JKLM"; Split.TEST -> "PQRSTUVW" }
        while (true) {
            val id = "" + letters[rnd.nextInt(letters.length)] + letters[rnd.nextInt(letters.length)] + (10 + rnd.nextInt(90))
            if (used.add(id)) return id
        }
    }

    /**
     * @param bindingCurriculum adds in-clause two-entity and temporal TRAIN examples for the module and port
     * relations only (VALIDATION analogues use tool, the frozen TEST uses location). Off = cycle-1 TRAIN.
     */
    fun generate(split: Split, seed: Long = 20261022L + split.ordinal * 1000L, perRelation: Int = 0,
                 bindingCurriculum: Boolean = false, multiword: Boolean = false): List<Case> {
        val rnd = Random(seed)
        val rels = relations(split, rnd).map { r ->
            if (split != Split.VALIDATION) r else VALIDATION_FORMS.getValue(r.name).let { (f, q) -> r.copy(valFamilies = f, valQuestion = q) }
        }
        val per = if (perRelation > 0) perRelation else when (split) { Split.TRAIN -> 8; Split.VALIDATION -> 5; Split.TEST -> 8 }
        val used = HashSet<String>()
        val out = ArrayList<Case>()
        var n = 0
        fun add(kind: Kind, family: String, rel: Relation, ev: String, q: String, answer: String?, traps: Set<String> = emptySet(), unseen: Boolean = false) {
            val ex = ContinuumResidualSpanCore.Example(ev, q, answer)
            out += Case(split.name.lowercase(Locale.ROOT) + "-" + (n++), split, kind, family, rel.name, ex, traps, unseen)
        }
        val trainQuestions = 2
        for (rel in rels) {
            repeat(per) { k ->
                val e = entity(split, rnd, used)
                val v = rel.values[k % rel.values.size]
                val families = when (split) { Split.TEST -> rel.facts + rel.heldOut; Split.VALIDATION -> rel.facts + rel.valFamilies; else -> rel.facts }
                val questions = if (split == Split.VALIDATION) rel.questions.take(trainQuestions) + rel.valQuestion!! else rel.questions
                val famIndex = k % families.size
                val unseenFamily = famIndex >= rel.facts.size
                val qIndex = if (split == Split.TRAIN) k % trainQuestions else k % questions.size
                val q = fill(questions[qIndex], rel, e, v)
                val unseen = unseenFamily || qIndex >= trainQuestions
                val sentence = fill(families[famIndex], rel, e, v)
                add(Kind.ANSWER, "F" + famIndex + "Q" + qIndex, rel, sentence, q, v, unseen = unseen)
                // hard negative 1: same evidence, question about another relation of the same entity
                val other = rels[(rels.indexOf(rel) + 1 + k % (rels.size - 1)) % rels.size]
                add(Kind.OTHER_RELATION, "F" + famIndex, rel, sentence, fill(other.questions[qIndex % trainQuestions.coerceAtLeast(1)], other, e, v), null,
                    traps = setOf(v), unseen = unseenFamily)
                // hard negative 2: same relation, but the question names another entity
                val stranger = entity(split, rnd, used)
                add(Kind.OTHER_ENTITY, "F" + famIndex, rel, sentence, fill(questions[qIndex], rel, stranger, v), null,
                    traps = setOf(v), unseen = unseen)
                // hard negative 3: the only value is explicitly negated
                if (k % 2 == 0) add(Kind.NEGATED, "N", rel, fill(rel.negated, rel, e, v), q, null, traps = setOf(v))
                val v2 = rel.values[(k + 1) % rel.values.size]
                if (rel.contrast != null && k % 2 == 1)
                    add(Kind.CONTRAST, "C", rel, fill(rel.contrast, rel, e, v, v2), q, v, traps = setOf(v2))
                // two entities in separate clauses: bind the value to the asked entity
                if (k % 3 == 0) {
                    val e2 = entity(split, rnd, used)
                    val asked = fill(rel.facts[0], rel, e, v); val distractor = fill(rel.facts[0], rel, e2, v2)
                    add(Kind.MULTI_ENTITY, "M" + famIndex, rel, if (k % 2 == 0) "$distractor $asked" else "$asked $distractor",
                        q, v, traps = setOf(v2))
                }
                // two facts of one entity: pick the asked relation
                if (k % 3 == 1) {
                    val ov = other.values[k % other.values.size]
                    add(Kind.MULTI_FACT, "MF", rel, fill(other.facts[0], other, e, ov).replace(other.type, rel.type) + " " + sentence,
                        q, v, traps = setOf(ov))
                }
            }
        }
        fun inClause(rel: Relation, count: Int, unseen: Boolean) = repeat(count) { k ->
            val e1 = entity(split, rnd, used); val e2 = entity(split, rnd, used)
            val a = rel.values[k % rel.values.size]; val b = rel.values[(k + 1) % rel.values.size]
            val first = fill(rel.facts[0], rel, e1, a).removeSuffix("."); val second = fill(rel.facts[0], rel, e2, b).removeSuffix(".")
            val q = fill(rel.questions[k % 2], rel, e2, b)
            add(Kind.SAME_CLAUSE_ENTITIES, "S", rel, if (k % 2 == 0) "$first, $second." else "$second, $first.", q, b, traps = setOf(a), unseen = unseen)
        }
        fun temporal(rel: Relation, now: String, before: String, ask: String, count: Int, unseen: Boolean) = repeat(count) { k ->
            val e = entity(split, rnd, used)
            val a = rel.values[k % rel.values.size]; val b = rel.values[(k + 1) % rel.values.size]
            val ev = if (k % 2 == 0) before.replace("{X}", a).replace("{E}", e).replace("{T}", rel.type) + ", " +
                now.replace("{X}", b).replace("{E}", e).replace("{T}", rel.type) + "."
            else now.replace("{X}", b).replace("{E}", e).replace("{T}", rel.type) + ", " +
                before.replace("{X}", a).replace("{E}", e).replace("{T}", rel.type) + "."
            add(Kind.TEMPORAL, "T", rel, ev.replaceFirstChar { it.uppercase() }, ask.replace("{E}", e), b, traps = setOf(a), unseen = unseen)
        }
        if (split == Split.TRAIN && bindingCurriculum) {
            inClause(rels.first { it.name == "module" }, 6, false)
            inClause(rels.first { it.name == "port" }, 6, false)
            temporal(rels.first { it.name == "module" }, "heute verwendet {T} {E} Modul {X}", "früher verwendete {T} {E} Modul {X}",
                "Welches Modul verwendet {E} heute?", 6, false)
        }
        if (multiword && split != Split.TEST) {
            // Multi-token answers (number + unit, two-part names). Forms differ from the frozen TEST form
            // ("wiegt N Kilogramm" / "Wie schwer"); VALIDATION again uses its own forms and unit.
            val train = split == Split.TRAIN
            val firsts = if (train) listOf("Anna", "Bernd", "Clara", "David", "Elena", "Felix") else listOf("Greta", "Hannes", "Ida", "Jonas")
            val lasts = if (train) listOf("Huber", "Gruber", "Wagner", "Pichler", "Moser", "Mayer") else listOf("Steiner", "Hofer", "Leitner", "Berger")
            val unitRel = Relation(if (train) "weight" else "capacity", if (train) "Gerät" else "Behälter", emptyList(), emptyList(),
                emptyList(), "", null, emptyList())
            val nameRel = Relation("author", "Paket", emptyList(), emptyList(), emptyList(), "", null, emptyList())
            repeat(if (train) 10 else 6) { k ->
                val e = entity(split, rnd, used)
                val n = ((if (train) 100 else 300) + rnd.nextInt(100)).toString()
                val (ev, q, unit) = if (train) {
                    if (k % 2 == 0) Triple("Gerät $e hat ein Gewicht von $n Kilogramm.", "Welches Gewicht hat $e?", "$n Kilogramm")
                    else Triple("Roboter $e ist $n Meter lang.", "Wie lang ist $e?", "$n Meter")
                } else Triple("Behälter $e fasst $n Liter.", "Wie viel fasst $e?", "$n Liter")
                add(Kind.UNIT_MULTIWORD, "MW", unitRel, ev, q, unit, traps = setOf(n), unseen = !train)
                add(Kind.OTHER_RELATION, "MW", unitRel, ev, "Welche Farbe hat $e?", null, traps = setOf(n, unit), unseen = !train)
                if (k % 2 == 0) {
                    val neg = if (train) "Gerät $e hat kein Gewicht von $n Kilogramm." else "Behälter $e fasst keine $n Liter."
                    add(Kind.NEGATED, "MWN", unitRel, neg, q, null, traps = setOf(n, unit), unseen = !train)
                }
                val e2 = entity(split, rnd, used)
                val name = firsts[k % firsts.size] + " " + lasts[(k + 1) % lasts.size]
                val form = if (train) "Paket $e2 stammt von $name." else "Die Autorin von Paket $e2 ist $name."
                add(Kind.UNIT_MULTIWORD, "MWN2", nameRel, form, if (train) "Von wem stammt $e2?" else "Wer ist die Autorin von $e2?",
                    name, traps = setOf(name.substringBefore(' '), name.substringAfter(' ')), unseen = !train)
            }
        }
        if (split == Split.VALIDATION) {
            val tool = rels.first { it.name == "tool" }
            inClause(tool, 6, true)
            temporal(tool, "heute nutzt {T} {E} eine {X}", "früher nutzte {T} {E} eine {X}", "Welches Werkzeug nutzt {E} heute?", 6, true)
        }
        if (split == Split.TEST) {
            // diagnostic forms that no training example shows
            val loc = rels.first { it.name == "location" }
            repeat(6) { k ->
                val e1 = entity(split, rnd, used); val e2 = entity(split, rnd, used)
                val a = loc.values[k % loc.values.size]; val b = loc.values[(k + 2) % loc.values.size]
                // the asked value alternates between first and last position: no position shortcut
                if (k % 2 == 0) add(Kind.SAME_CLAUSE_ENTITIES, "S", loc, "Einheit $e1 steht in $a, Einheit $e2 steht in $b.", "Wo steht $e2?", b,
                    traps = setOf(a), unseen = true)
                else add(Kind.SAME_CLAUSE_ENTITIES, "S", loc, "Einheit $e2 steht in $b, Einheit $e1 steht in $a.", "Wo steht $e2?", b,
                    traps = setOf(a), unseen = true)
                if (k % 2 == 0) add(Kind.TEMPORAL, "T", loc, "Einheit $e1 stand früher in $a, heute steht Einheit $e1 in $b.", "Wo steht $e1 heute?", b,
                    traps = setOf(a), unseen = true)
                else add(Kind.TEMPORAL, "T", loc, "Einheit $e1 steht heute in $b, früher stand Einheit $e1 in $a.", "Wo steht $e1 heute?", b,
                    traps = setOf(a), unseen = true)
                val kg = (12 + 7 * k).toString()
                add(Kind.UNIT_MULTIWORD, "U", loc, "Maschine $e1 wiegt $kg Kilogramm.", "Wie schwer ist $e1?", "$kg Kilogramm", unseen = true)
            }
        }
        validate(out)
        return out
    }

    /** Structural leakage and gold checks; a broken generator must fail loudly, not score. */
    fun validate(cases: List<Case>) {
        val tokenRe = Regex("[\\p{L}\\p{N}]+(?:-[\\p{L}\\p{N}]+)*")
        for (c in cases) {
            val tokens = tokenRe.findAll(c.example.evidence).map { it.value }.toList()
            val answer = c.example.answer
            if (answer != null && c.kind != Kind.UNIT_MULTIWORD) require(tokens.count { it == answer } == 1) { "gold not unique in ${c.id}" }
            if (answer != null && c.kind == Kind.UNIT_MULTIWORD) require(c.example.evidence.split(answer).size == 2) { "multiword gold not unique in ${c.id}" }
            if (answer == null) {
                val q = tokenRe.findAll(c.example.question).map { it.value }.filter { it.any(Char::isDigit) && it.any(Char::isLetter) }.toList()
                require(q.size == 1) { "question entity missing in ${c.id}" }
                when (c.kind) {
                    Kind.OTHER_ENTITY -> require(q.single() !in tokens) { "OTHER_ENTITY leaks the asked entity in ${c.id}" }
                    Kind.NEGATED -> require(tokens.any { it.lowercase(Locale.ROOT) in setOf("nicht", "kein", "keine") }) { "negation missing ${c.id}" }
                    Kind.OTHER_RELATION -> require(true)
                    else -> error("unexpected unanswerable kind ${c.kind}")
                }
            }
        }
        val ids = cases.map { it.id }
        require(ids.toSet().size == ids.size)
    }

    fun sha256(cases: List<Case>): String {
        val md = MessageDigest.getInstance("SHA-256")
        for (c in cases) md.update((listOf(c.id, c.split, c.kind, c.family, c.relation, c.example.evidence,
            c.example.question, c.example.answer ?: "<ABSTAIN>", c.unseenForm).joinToString("\u0001") + "\n").toByteArray(Charsets.UTF_8))
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Frozen before any tuning on this branch; changing the generator must be a visible, reviewed change. */
    const val TEST_SHA256 = "974e4ba3c405005a7773c479282f4abc03e2a23530def4da69471a27799b0ba8"

    // ---------------------------------------------------------------------------------------------
    // Metrics
    // ---------------------------------------------------------------------------------------------
    data class Metrics(
        val answerable: Int, val correct: Int, val wrongSpan: Int, val falseAbstain: Int,
        val unanswerable: Int, val correctAbstain: Int, val unsupported: Int,
        val trapErrors: Int, val trapCases: Int
    ) {
        fun line(): String = "answerable $correct/$answerable correct, wrong span $wrongSpan, false abstain $falseAbstain | " +
            "unanswerable $correctAbstain/$unanswerable abstained, unsupported answers $unsupported | traps $trapErrors/$trapCases"
    }

    fun metrics(cases: List<Case>, predict: (Case) -> String?): Metrics {
        var correct = 0; var wrong = 0; var falseAbstain = 0; var abstain = 0; var unsupported = 0; var traps = 0; var trapCases = 0
        for (c in cases) {
            val p = predict(c)
            if (c.traps.isNotEmpty()) { trapCases++; if (p != null && p in c.traps) traps++ }
            if (c.answerable) when {
                p == c.example.answer -> correct++
                p == null -> falseAbstain++
                else -> wrong++
            } else if (p == null) abstain++ else unsupported++
        }
        return Metrics(cases.count { it.answerable }, correct, wrong, falseAbstain,
            cases.count { !it.answerable }, abstain, unsupported, traps, trapCases)
    }

    fun report(name: String, cases: List<Case>, predict: (Case) -> String?): String = buildString {
        appendLine("== $name")
        appendLine("  ALL      " + metrics(cases, predict).line())
        appendLine("  SEEN     " + metrics(cases.filter { !it.unseenForm }, predict).line())
        appendLine("  UNSEEN   " + metrics(cases.filter { it.unseenForm }, predict).line())
        for (k in Kind.entries) {
            val sub = cases.filter { it.kind == k }
            if (sub.isNotEmpty()) appendLine("  " + k.name.padEnd(21) + metrics(sub, predict).line())
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Deterministic baselines (hand rules, labelled as such)
    // ---------------------------------------------------------------------------------------------
    private val TOKEN = Regex("[\\p{L}\\p{N}]+(?:-[\\p{L}\\p{N}]+)*")
    private val STOP = setOf("der", "die", "das", "den", "dem", "des", "ein", "eine", "einer", "einen", "einem", "und",
        "oder", "als", "in", "im", "am", "an", "auf", "mit", "für", "von", "ist", "war", "hat", "zu", "nach", "sich", "wird")

    private fun questionEntity(q: String) = TOKEN.findAll(q).map { it.value }.firstOrNull { it.any(Char::isDigit) && it.any(Char::isLetter) }

    /** R1: surface heuristic – last content token of the clause that names the asked entity. */
    fun surfaceBaseline(c: Case): String? {
        val e = questionEntity(c.example.question) ?: return null
        val clause = c.example.evidence.split(Regex("(?<=[.;\\n])")).firstOrNull { e in TOKEN.findAll(it).map { m -> m.value } } ?: return null
        val qWords = TOKEN.findAll(c.example.question).map { it.value.lowercase(Locale.ROOT) }.toSet()
        return TOKEN.findAll(clause).map { it.value }.filter {
            it != e && it.lowercase(Locale.ROOT) !in STOP && it.lowercase(Locale.ROOT) !in qWords && it.length >= 2
        }.lastOrNull()
    }

    /**
     * R2: lexicon rule learned only from TRAIN: question relation = training relation with the most shared
     * question words; answer only if the entity clause contains a training verb of that relation and no negation.
     */
    class LexiconBaseline(train: List<Case>) {
        private val questionWords = HashMap<String, MutableSet<String>>()
        private val evidenceWords = HashMap<String, MutableSet<String>>()
        init {
            for (c in train.filter { it.kind == Kind.ANSWER }) {
                val e = questionEntity(c.example.question)
                questionWords.getOrPut(c.relation) { HashSet() } += TOKEN.findAll(c.example.question).map { it.value.lowercase(Locale.ROOT) }.filter { it != e?.lowercase(Locale.ROOT) }
                evidenceWords.getOrPut(c.relation) { HashSet() } += TOKEN.findAll(c.example.evidence).map { it.value.lowercase(Locale.ROOT) }
                    .filter { it !in STOP && it != e?.lowercase(Locale.ROOT) && it != c.example.answer?.lowercase(Locale.ROOT) }
            }
        }
        fun predict(c: Case): String? {
            val e = questionEntity(c.example.question) ?: return null
            val q = TOKEN.findAll(c.example.question).map { it.value.lowercase(Locale.ROOT) }.toSet()
            val rel = questionWords.maxByOrNull { (_, w) -> w.count { it in q } }?.key ?: return null
            val clause = c.example.evidence.split(Regex("(?<=[.;\\n])")).firstOrNull { e in TOKEN.findAll(it).map { m -> m.value } } ?: return null
            val words = TOKEN.findAll(clause).map { it.value }.toList()
            val lower = words.map { it.lowercase(Locale.ROOT) }
            if (lower.none { it in evidenceWords.getValue(rel) && it !in q }) return null
            if (lower.any { it in setOf("nicht", "kein", "keine") } && "sondern" !in lower) return null
            val tail = if ("sondern" in lower) words.drop(lower.indexOf("sondern") + 1) else words
            return tail.filter { val l = it.lowercase(Locale.ROOT)
                it != e && l !in STOP && l !in q && l !in evidenceWords.getValue(rel) && it.length >= 2 }.lastOrNull()
        }
    }
}
