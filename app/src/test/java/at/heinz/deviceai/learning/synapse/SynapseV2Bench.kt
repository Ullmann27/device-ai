package at.heinz.deviceai.learning.synapse

import java.security.MessageDigest
import java.util.Locale
import java.util.Random

/**
 * SYNAPSE v2 benchmark (test sources only, synthetic German technical facts).
 *
 * Fresh material, never used by CSX1 (frozen TEST 974e4ba3…) or by the PR #71 DEV probe:
 * - new relations: Standort, Energiequelle, Sensor, Material, Betreiber, Linie (bridge for 2-hop chains);
 * - new entity format `XY-123` (letters and digit ranges disjoint per split) and bridges `LN-123`;
 * - values disjoint per split; VALIDATION and TEST each have their own sentence families and question forms.
 *
 * TRAIN: model fitting. VALIDATION: model selection and abstention thresholds only. TEST: frozen by [TEST_SHA256],
 * evaluated once at the end of the study. Every TEST form carries an honest overlap tag:
 * MORPH = shares a word stem with a TRAIN form of the same relation, LEXICAL = genuinely new wording,
 * ORDER = new word order, SEEN = TRAIN form with new entities and values.
 */
object SynapseV2Bench {
    enum class Split { TRAIN, VALIDATION, TEST }
    enum class Form { SEEN, NEW_QUESTION, NEW_EVIDENCE, NEW_BOTH }
    enum class Category(val answerable: Boolean) {
        ANSWER(true), CONTRAST(true), UNRELATED_NEGATION(true), MULTI_ENTITY(true), SAME_CLAUSE_ENTITIES(true),
        SHARED_VALUE(true), TEMPORAL(true), HOP2(true), HOP2_DISTRACTOR(true),
        OTHER_RELATION(false), OTHER_ENTITY(false), UNKNOWN_RELATION(false), NEGATED(false), NEGATED_UNSEEN_CUE(false),
        HOP2_BROKEN(false), HOP2_CONTRADICTION(false), HOP2_NEGATED_BRIDGE(false)
    }

    data class Case(
        val id: String, val split: Split, val category: Category, val relation: String, val form: Form,
        /** SEEN, MORPH, LEXICAL, ORDER, ORDER+LEXICAL, ORDER+MORPH, ELLIPSIS, UNSEEN_CUE, … */
        val overlap: String,
        val evidence: String, val question: String,
        /** Gold answer (exact token of the evidence) or null when the evidence does not support an answer. */
        val answer: String?,
        val hops: Int,
        /** Gold bridge entity of a two-hop chain. */
        val bridge: String?
    )

    private data class Form2(val text: String, val tag: String)
    private fun f(text: String, tag: String = "SEEN") = Form2(text, tag)

    private class Relation(
        val name: String,
        val train: List<String>, val validation: List<String>, val test: List<Form2>,
        val questionsTrain: List<String>, val questionsValidation: List<String>, val questionsTest: List<Form2>,
        val negated: String, val negatedUnseen: String, val contrast: String,
        /** Attribute inherited through a production line (two-hop chains). */
        val inheritable: Boolean
    )

    private val RELATIONS = listOf(
        Relation("location",
            listOf("{T} {E} steht in {V}.", "{T} {E} befindet sich in {V}.", "Der Standort von {T} {E} ist {V}."),
            listOf("{T} {E} ist in {V} untergebracht."),
            listOf(f("In {V} arbeitet {T} {E}.", "ORDER"), f("{T} {E} wurde in {V} aufgestellt.", "LEXICAL"),
                f("{V} ist der Einsatzort von {T} {E}.", "ORDER+LEXICAL"), f("Standort von {T} {E}: {V}.", "MORPH")),
            listOf("Wo steht {E}?", "Wo befindet sich {E}?"), listOf("An welchem Ort ist {E}?"),
            listOf(f("Wo ist {E} aufgestellt?", "MORPH"), f("An welchem Standort arbeitet {E}?", "MORPH")),
            "{T} {E} steht nicht in {V}.", "{T} {E} stand nie in {V}.", "{T} {E} steht nicht in {V2}, sondern in {V}.",
            inheritable = true),
        Relation("power",
            listOf("{T} {E} nutzt {V} als Energiequelle.", "{T} {E} wird mit {V} versorgt.", "Die Energiequelle von {T} {E} ist {V}."),
            listOf("{T} {E} läuft mit {V}."),
            listOf(f("{V} versorgt {T} {E} mit Strom.", "ORDER+MORPH"), f("{T} {E} bezieht Energie aus {V}.", "MORPH"),
                f("Als Energiespender von {T} {E} dient {V}.", "ORDER+LEXICAL")),
            listOf("Welche Energiequelle nutzt {E}?", "Womit wird {E} versorgt?"), listOf("Welche Stromquelle hat {E}?"),
            listOf(f("Woher bezieht {E} Energie?", "MORPH"), f("Welcher Energiespender treibt {E} an?", "LEXICAL")),
            "{T} {E} nutzt nicht {V} als Energiequelle.", "{T} {E} nutzt keinesfalls {V} als Energiequelle.",
            "{T} {E} nutzt nicht {V2}, sondern {V} als Energiequelle.", inheritable = true),
        Relation("sensor",
            listOf("{T} {E} misst mit {V}.", "Der Sensor von {T} {E} ist {V}.", "{T} {E} ist mit {V} ausgestattet."),
            listOf("{T} {E} erfasst Daten per {V}."),
            listOf(f("Mittels {V} tastet {T} {E} die Umgebung ab.", "ORDER+LEXICAL"), f("{T} {E} verfügt über {V} als Sensorik.", "MORPH"),
                f("{V} dient {T} {E} als Messfühler.", "ORDER+LEXICAL")),
            listOf("Welchen Sensor hat {E}?", "Womit misst {E}?"), listOf("Mit welchem Sensor arbeitet {E}?"),
            listOf(f("Welche Sensorik besitzt {E}?", "MORPH"), f("Womit tastet {E} die Umgebung ab?", "LEXICAL")),
            "{T} {E} misst nicht mit {V}.", "{T} {E} misst niemals mit {V}.", "{T} {E} misst nicht mit {V2}, sondern mit {V}.",
            inheritable = false),
        Relation("material",
            listOf("{T} {E} besteht aus {V}.", "Das Gehäuse von {T} {E} ist aus {V}.", "Das Material von {T} {E} ist {V}."),
            listOf("{T} {E} ist aus {V} gebaut."),
            listOf(f("Aus {V} wurde {T} {E} gefertigt.", "ORDER+LEXICAL"), f("{T} {E} hat ein Gehäuse aus {V}.", "MORPH"),
                f("Als Werkstoff von {T} {E} dient {V}.", "LEXICAL")),
            listOf("Aus welchem Material ist {E}?", "Woraus besteht {E}?"), listOf("Woraus ist das Gehäuse von {E}?"),
            listOf(f("Aus welchem Werkstoff ist {E} gefertigt?", "LEXICAL"), f("Welches Gehäusematerial hat {E}?", "MORPH")),
            "{T} {E} besteht nicht aus {V}.", "{T} {E} besteht keineswegs aus {V}.", "{T} {E} besteht nicht aus {V2}, sondern aus {V}.",
            inheritable = false),
        Relation("operator",
            listOf("{T} {E} wird von {V} betrieben.", "Der Betreiber von {T} {E} ist {V}.", "{V} betreibt {T} {E}."),
            listOf("{T} {E} gehört der Firma {V}."),
            listOf(f("{T} {E} läuft im Auftrag von {V}.", "LEXICAL"), f("Betrieben wird {T} {E} von {V}.", "ORDER+MORPH"),
                f("{V} ist Eigentümer von {T} {E}.", "ORDER+LEXICAL")),
            listOf("Wer betreibt {E}?", "Wer ist der Betreiber von {E}?"), listOf("Welcher Firma gehört {E}?"),
            listOf(f("In wessen Auftrag läuft {E}?", "LEXICAL"), f("Von wem wird {E} betrieben?", "MORPH")),
            "{T} {E} wird nicht von {V} betrieben.", "{T} {E} wird nie von {V} betrieben.",
            "{T} {E} wird nicht von {V2}, sondern von {V} betrieben.", inheritable = true),
        Relation("line",
            listOf("{T} {E} gehört zu Linie {V}.", "{T} {E} ist Teil von Linie {V}."),
            listOf("{T} {E} arbeitet in Linie {V}."),
            listOf(f("Linie {V} umfasst {T} {E}.", "ORDER+LEXICAL"), f("{T} {E} ist der Linie {V} zugeordnet.", "LEXICAL")),
            listOf("Zu welcher Linie gehört {E}?", "Welcher Linie gehört {E} an?"), listOf("In welcher Linie arbeitet {E}?"),
            listOf(f("Welcher Linie ist {E} zugeordnet?", "MORPH"), f("In welchem Fertigungsstrang arbeitet {E}?", "LEXICAL")),
            "{T} {E} gehört nicht zu Linie {V}.", "{T} {E} gehört keinesfalls zu Linie {V}.",
            "{T} {E} gehört nicht zu Linie {V2}, sondern zu Linie {V}.", inheritable = false)
    )

    private val UNKNOWN_RELATION_QUESTIONS = listOf("Welche Farbe hat {E}?", "Wie schwer ist {E}?", "Wann wurde {E} gebaut?")
    private val TYPES = listOf("Roboter", "Gerät", "Anlage", "Maschine")

    private val PLACE_PREFIX = listOf("Ahl", "Bern", "Dorn", "Eich", "Fal", "Garm", "Hol", "Kirch", "Lind", "Mar", "Ober",
        "Ros", "Stein", "Tann", "Wald", "Wester")
    private val PLACE_SUFFIX = mapOf(Split.TRAIN to listOf("feld", "ingen", "bach"), Split.VALIDATION to listOf("hausen", "dorf"),
        Split.TEST to listOf("stedt", "rode", "heim"))
    private val VALUES: Map<String, Map<Split, List<String>>> = mapOf(
        "location" to Split.entries.associateWith { s -> PLACE_SUFFIX.getValue(s).flatMap { suf -> PLACE_PREFIX.map { it + suf } } },
        "power" to mapOf(
            Split.TRAIN to listOf("Akku", "Netzteil", "Solarpanel", "Brennstoffzelle", "Dieselgenerator", "Kondensator", "Batterie", "Stromschiene"),
            Split.VALIDATION to listOf("Bleiakku", "Windrad", "Gasturbine", "Schwungrad", "Nickelzelle"),
            Split.TEST to listOf("Lithiumakku", "Wasserstofftank", "Superkondensator", "Thermogenerator", "Pumpspeicher", "Druckluftspeicher")),
        "sensor" to mapOf(
            Split.TRAIN to listOf("Lidar", "Radar", "Sonar", "Kamera", "Gyroskop", "Thermometer", "Laserscanner", "Tachometer"),
            Split.VALIDATION to listOf("Hygrometer", "Barometer", "Magnetometer", "Lichtschranke", "Kompass"),
            Split.TEST to listOf("Infrarotkamera", "Drucksensor", "Ultraschallsensor", "Neigungssensor", "Spektrometer", "Dosimeter")),
        "material" to mapOf(
            Split.TRAIN to listOf("Titan", "Aluminium", "Kupfer", "Keramik", "Carbon", "Edelstahl", "Gusseisen", "Holz"),
            Split.VALIDATION to listOf("Messing", "Bronze", "Zink", "Glasfaser", "Chrom"),
            Split.TEST to listOf("Magnesium", "Wolfram", "Kevlar", "Polyamid", "Graphit", "Nickel")),
        "operator" to mapOf(
            Split.TRAIN to listOf("Varnex", "Quelltor", "Brimsoft", "Taluna", "Orvida", "Meltrax", "Kordana", "Sylvent"),
            Split.VALIDATION to listOf("Ostrino", "Pelkor", "Sundra", "Faventa", "Gralum"),
            Split.TEST to listOf("Kalvero", "Drunmar", "Ylsana", "Torvik", "Wenmora", "Zastrel"))
    )
    private val ENTITY_LETTERS = mapOf(Split.TRAIN to "BCDFG", Split.VALIDATION to "HJK", Split.TEST to "MPQRSTVWXZ")
    private val ENTITY_DIGITS = mapOf(Split.TRAIN to 100..499, Split.VALIDATION to 500..699, Split.TEST to 700..999)
    private val LINE_DIGITS = mapOf(Split.TRAIN to 100..399, Split.VALIDATION to 400..599, Split.TEST to 600..999)

    private class Generator(val split: Split, seed: Long) {
        val rnd = Random(seed)
        val used = HashSet<String>()
        val out = ArrayList<Case>()
        fun entity(): String {
            val letters = ENTITY_LETTERS.getValue(split); val digits = ENTITY_DIGITS.getValue(split)
            while (true) {
                val id = "" + letters[rnd.nextInt(letters.length)] + letters[rnd.nextInt(letters.length)] + "-" +
                    (digits.first + rnd.nextInt(digits.last - digits.first + 1))
                if (used.add(id)) return id
            }
        }
        fun line(): String {
            val digits = LINE_DIGITS.getValue(split)
            while (true) { val id = "LN-" + (digits.first + rnd.nextInt(digits.last - digits.first + 1)); if (used.add(id)) return id }
        }
        fun type() = TYPES[rnd.nextInt(TYPES.size)]
        fun value(rel: Relation, avoid: Set<String> = emptySet()): String {
            if (rel.name == "line") return line()
            val pool = VALUES.getValue(rel.name).getValue(split).filter { it !in avoid }
            return pool[rnd.nextInt(pool.size)]
        }
        fun add(category: Category, rel: String, form: Form, overlap: String, evidence: String, question: String,
                answer: String?, hops: Int = 1, bridge: String? = null) {
            if (answer != null) require(Regex("(?<![\\p{L}\\p{N}-])" + Regex.escape(answer) + "(?![\\p{L}\\p{N}-])").containsMatchIn(evidence)) {
                "gold '$answer' missing in '$evidence'" }
            out += Case(split.name.lowercase(Locale.ROOT) + "-" + out.size, split, category, rel, form, overlap,
                evidence, question, answer, hops, bridge)
        }
    }

    private fun fill(template: String, type: String, entity: String, value: String, value2: String = "") =
        template.replace("{T}", type).replace("{E}", entity).replace("{V2}", value2).replace("{V}", value)

    /** Second clause after a comma: function words lose their sentence-initial capital, nouns and names keep it. */
    private fun continueClause(sentence: String): String {
        val first = sentence.substringBefore(' ')
        return if (first in setOf("Der", "Die", "Das", "In", "Aus", "Als", "Mittels", "Betrieben")) first.lowercase(Locale.ROOT) +
            sentence.substring(first.length) else sentence
    }

    /** All TEST-only and VALIDATION-only templates (evidence and questions), for the leakage gate. */
    fun heldOutTemplates(split: Split): List<String> = RELATIONS.flatMap { r ->
        when (split) {
            Split.TEST -> r.test.map { it.text } + r.questionsTest.map { it.text } + listOf(r.negatedUnseen)
            Split.VALIDATION -> r.validation + r.questionsValidation
            Split.TRAIN -> emptyList()
        }
    } + if (split == Split.TEST) UNKNOWN_RELATION_QUESTIONS + listOf("Bis 2024 stand {T} {E} in {V1}, inzwischen steht {T} {E} in {V}.") else emptyList()

    /** Template text normalized exactly like [shapes]: every slot and the word "Linie" become `<X>`. */
    fun templateShape(template: String): String = template.replace("{T}", "<X>").replace("{E}", "<X>")
        .replace("{V2}", "<X>").replace("{V1}", "<X>").replace("{V}", "<X>").replace("Linie", "<X>")
    private fun ask(template: String, entity: String) = template.replace("{E}", entity)

    /** Evidence forms and question forms available to a split, with their honesty tags. */
    private fun evidenceForms(rel: Relation, split: Split): List<Pair<Form2, Boolean>> = when (split) {
        Split.TRAIN -> rel.train.map { f(it) to false }
        Split.VALIDATION -> rel.train.map { f(it) to false } + rel.validation.map { f(it, "VALIDATION_ONLY") to true }
        Split.TEST -> rel.train.map { f(it) to false } + rel.test.map { it to true }
    }
    private fun questionForms(rel: Relation, split: Split): List<Pair<Form2, Boolean>> = when (split) {
        Split.TRAIN -> rel.questionsTrain.map { f(it) to false }
        Split.VALIDATION -> rel.questionsTrain.map { f(it) to false } + rel.questionsValidation.map { f(it, "VALIDATION_ONLY") to true }
        Split.TEST -> rel.questionsTrain.map { f(it) to false } + rel.questionsTest.map { it to true }
    }
    private fun form(newEvidence: Boolean, newQuestion: Boolean) = when {
        newEvidence && newQuestion -> Form.NEW_BOTH
        newEvidence -> Form.NEW_EVIDENCE
        newQuestion -> Form.NEW_QUESTION
        else -> Form.SEEN
    }
    private fun tag(e: Pair<Form2, Boolean>, q: Pair<Form2, Boolean>) = when {
        e.second && q.second -> e.first.tag + "|" + q.first.tag
        e.second -> e.first.tag
        q.second -> q.first.tag
        else -> "SEEN"
    }

    fun generate(split: Split, seed: Long = 20261022L + 7919L * split.ordinal, perRelation: Int = 0): List<Case> {
        val g = Generator(split, seed)
        val per = if (perRelation > 0) perRelation else when (split) { Split.TRAIN -> 24; Split.VALIDATION -> 10; Split.TEST -> 16 }
        val chainRelations = RELATIONS.filter { it.inheritable }
        val lineRelation = RELATIONS.first { it.name == "line" }
        for (rel in RELATIONS) {
            val eForms = evidenceForms(rel, split); val qForms = questionForms(rel, split)
            repeat(per) { k ->
                val e = g.entity(); val type = g.type(); val v = g.value(rel)
                // balanced form coverage: cycle evidence forms and question forms independently
                val ef = eForms[k % eForms.size]; val qf = qForms[(k / eForms.size + k) % qForms.size]
                val sentence = fill(ef.first.text, type, e, v)
                val q = ask(qf.first.text, e)
                g.add(Category.ANSWER, rel.name, form(ef.second, qf.second), tag(ef, qf), sentence, q, v)
                // hard negative: another relation asked about the same entity (never inheritable without a bridge)
                val other = RELATIONS[(RELATIONS.indexOf(rel) + 1 + k % (RELATIONS.size - 1)) % RELATIONS.size]
                val oq = questionForms(other, split)[k % questionForms(other, split).size]
                g.add(Category.OTHER_RELATION, rel.name, form(ef.second, oq.second), tag(ef, oq), sentence, ask(oq.first.text, e), null)
                // structural negative: the asked entity is absent
                if (k % 3 == 0) g.add(Category.OTHER_ENTITY, rel.name, form(ef.second, qf.second), tag(ef, qf), sentence, ask(qf.first.text, g.entity()), null)
                // explicit negation (seen cue) and, on TEST only, unseen negation cues
                if (k % 2 == 0) g.add(Category.NEGATED, rel.name, form(false, qf.second), qf.first.tag.takeIf { qf.second } ?: "SEEN",
                    fill(rel.negated, type, e, v), q, null)
                if (split == Split.TEST && k % 2 == 1) g.add(Category.NEGATED_UNSEEN_CUE, rel.name, Form.NEW_EVIDENCE, "UNSEEN_CUE",
                    fill(rel.negatedUnseen, type, e, v), q, null)
                // contrast: "nicht A, sondern B"
                if (k % 3 == 1) { val v2 = g.value(rel, setOf(v)); g.add(Category.CONTRAST, rel.name, form(false, qf.second), tag(f("") to false, qf),
                    fill(rel.contrast, type, e, v, v2), q, v) }
                // a negation about another entity must not block the asked fact
                if (k % 4 == 2) { val e2 = g.entity(); val v2 = g.value(rel, setOf(v))
                    g.add(Category.UNRELATED_NEGATION, rel.name, form(ef.second, qf.second), tag(ef, qf),
                        fill(rel.negated, g.type(), e2, v2) + " " + sentence, q, v) }
                // two entities in separate sentences; and in one clause; and sharing one value
                if (k % 3 == 2) { val e2 = g.entity(); val v2 = g.value(rel, setOf(v))
                    val distractor = fill(ef.first.text, g.type(), e2, v2)
                    g.add(Category.MULTI_ENTITY, rel.name, form(ef.second, qf.second), tag(ef, qf),
                        if (k % 2 == 0) "$distractor $sentence" else "$sentence $distractor", q, v) }
                if (k % 4 == 1) { val e2 = g.entity(); val v2 = g.value(rel, setOf(v))
                    val first = fill(ef.first.text, g.type(), e2, v2).removeSuffix(".")
                    g.add(Category.SAME_CLAUSE_ENTITIES, rel.name, form(ef.second, qf.second), tag(ef, qf),
                        "$first, ${continueClause(sentence)}", q, v) }
                if (k % 4 == 3) { val e2 = g.entity()
                    val shared = fill(ef.first.text, g.type(), e2, v)
                    g.add(Category.SHARED_VALUE, rel.name, form(ef.second, qf.second), tag(ef, qf),
                        if (k % 2 == 0) "$shared $sentence" else "$sentence $shared", q, v) }
            }
            // questions about relations the benchmark never states (TEST only, all must abstain)
            if (split == Split.TEST) repeat(per / 4) { k ->
                val e = g.entity(); val ef = eForms[k % eForms.size]
                g.add(Category.UNKNOWN_RELATION, rel.name, Form.NEW_QUESTION, "UNKNOWN_RELATION",
                    fill(ef.first.text, g.type(), e, g.value(rel)), ask(UNKNOWN_RELATION_QUESTIONS[k % UNKNOWN_RELATION_QUESTIONS.size], e), null)
            }
        }
        // temporal qualifiers (location): seen cues "früher/heute"; TEST adds unseen cues "Bis 2024 … inzwischen"
        val location = RELATIONS.first { it.name == "location" }
        repeat(per) { k ->
            val e = g.entity(); val type = g.type(); val now = g.value(location); val before = g.value(location, setOf(now))
            val unseen = split == Split.TEST && k % 2 == 1
            val evidence = when {
                unseen -> "Bis 2024 stand $type $e in $before, inzwischen steht $type $e in $now."
                k % 2 == 0 -> "Früher stand $type $e in $before, heute steht $type $e in $now."
                else -> "Heute steht $type $e in $now, früher stand $type $e in $before."
            }
            val askNow = k % 4 < 2
            g.add(Category.TEMPORAL, "location", if (unseen) Form.NEW_EVIDENCE else Form.SEEN, if (unseen) "UNSEEN_CUE" else "SEEN",
                evidence, if (askNow) "Wo steht $e heute?" else "Wo stand $e früher?", if (askNow) now else before)
        }
        // two-hop chains through production lines (only inheritable attributes)
        for (rel in chainRelations) {
            val tails = evidenceForms(rel, split); val bridges = evidenceForms(lineRelation, split); val qForms = questionForms(rel, split)
            repeat(per) { k ->
                val e = g.entity(); val type = g.type(); val b = g.line(); val v = g.value(rel)
                val bf = bridges[k % bridges.size]; val tf = tails[(k / bridges.size + k) % tails.size]; val qf = qForms[k % qForms.size]
                val bridge = fill(bf.first.text, type, e, b)
                val tail = fill(tf.first.text, "Linie", b, v)
                val newForm = bf.second || tf.second
                val overlap = listOfNotNull(bf.first.tag.takeIf { bf.second }, tf.first.tag.takeIf { tf.second }, qf.first.tag.takeIf { qf.second })
                    .joinToString("|").ifEmpty { "SEEN" }
                val q = ask(qf.first.text, e)
                val fm = form(newForm, qf.second)
                g.add(Category.HOP2, rel.name, fm, overlap, if (k % 3 == 2) "$tail $bridge" else "$bridge $tail", q, v, 2, b)
                when (k % 4) {
                    0 -> { val e2 = g.entity(); val b2 = g.line(); val v2 = g.value(rel, setOf(v))
                        g.add(Category.HOP2_DISTRACTOR, rel.name, fm, overlap,
                            "${fill(bf.first.text, g.type(), e2, b2)} $bridge ${fill(tf.first.text, "Linie", b2, v2)} $tail", q, v, 2, b) }
                    1 -> { val b2 = g.line()
                        g.add(Category.HOP2_BROKEN, rel.name, fm, overlap, "$bridge ${fill(tf.first.text, "Linie", b2, v)}", q, null, 2) }
                    2 -> { val v2 = g.value(rel, setOf(v)); val tf2 = tails[(k + 1) % tails.size]
                        g.add(Category.HOP2_CONTRADICTION, rel.name, fm, overlap, "$bridge $tail ${fill(tf2.first.text, "Linie", b, v2)}", q, null, 2) }
                    3 -> g.add(Category.HOP2_NEGATED_BRIDGE, rel.name, fm, overlap, "${fill(lineRelation.negated, type, e, b)} $tail", q, null, 2)
                }
            }
        }
        return g.out
    }

    fun sha256(cases: List<Case>): String {
        val md = MessageDigest.getInstance("SHA-256")
        for (c in cases) md.update((listOf(c.id, c.split, c.category, c.relation, c.form, c.overlap, c.evidence, c.question,
            c.answer ?: "<ABSTAIN>", c.hops, c.bridge ?: "-").joinToString("\u0001") + "\n").toByteArray(Charsets.UTF_8))
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Frozen before any model was evaluated on TEST; changing the generator must be a visible, reviewed change. */
    const val TEST_SHA256 = "c565a541c0225de1bd256ddcfa3b18fb87e9235886cc33d265eaeff923512832"

    // ---------------------------------------------------------------------------------------------
    // Leakage report
    // ---------------------------------------------------------------------------------------------
    private val ID = Regex("\\b(?:[A-Z]{2}|LN)-\\d{3}\\b")
    private val WORD = Regex("[\\p{L}\\p{N}]+(?:-[\\p{L}\\p{N}]+)*")
    fun entities(cases: List<Case>): Set<String> = cases.flatMap { c -> ID.findAll(c.evidence + " " + c.question).map { it.value } }.toSet()
    fun answers(cases: List<Case>): Set<String> = cases.mapNotNull { it.answer }.toSet()
    /** Sentences and questions with every entity, value, type word and "Linie" replaced by `<X>`: the sentence family. */
    fun shapes(cases: List<Case>): Set<String> = cases.flatMap { c ->
        (c.evidence.split(Regex("(?<=[.])\\s+")) + c.question).map { s ->
            var t = ID.replace(s, "<X>")
            for (v in VALUES.values.flatMap { m -> m.values.flatten() }) t = t.replace(Regex("(?<![\\p{L}])" + Regex.escape(v) + "(?![\\p{L}])"), "<X>")
            for (ty in TYPES + "Linie") t = t.replace(Regex("\\b$ty\\b"), "<X>")
            t
        }
    }.toSet()
    fun trigramOverlap(train: List<Case>, other: List<Case>): Double {
        fun grams(cs: List<Case>) = cs.flatMap { c -> WORD.findAll(c.evidence.lowercase(Locale.ROOT)).map { it.value }.toList().windowed(3) }.toSet()
        val a = grams(train); val b = grams(other)
        return if (b.isEmpty()) 0.0 else (b intersect a).size.toDouble() / b.size
    }
}
