package at.heinz.deviceai.intelligence.maxultra

import at.heinz.deviceai.intelligence.maxultra.Lct1.Answer
import at.heinz.deviceai.intelligence.maxultra.Lct1.Entity
import at.heinz.deviceai.intelligence.maxultra.Lct1.Fact
import at.heinz.deviceai.intelligence.maxultra.Lct1.Kind
import at.heinz.deviceai.intelligence.maxultra.Lct1.KnowledgeBase
import at.heinz.deviceai.intelligence.maxultra.Lct1.Polarity
import at.heinz.deviceai.intelligence.maxultra.Lct1.Query
import at.heinz.deviceai.intelligence.maxultra.Lct1.QuestionType
import at.heinz.deviceai.intelligence.maxultra.Lct1.Relation
import at.heinz.deviceai.intelligence.maxultra.Lct1.Solver
import at.heinz.deviceai.intelligence.maxultra.Lct1.SourceStatus
import at.heinz.deviceai.intelligence.maxultra.Lct1.Type
import java.io.File
import java.time.LocalDate
import java.util.Random

/**
 * Experiment B (LCT1) data and evaluation, pre-registered in docs/research/LCT1-REASONING-PREREG-20261023.md.
 * DEV is for development; TEST is evaluated exactly once with frozen code. Research only.
 * Run: java ... at.heinz.deviceai.intelligence.maxultra.Lct1Experiment dev|test <report.json> <commit>
 */
object Lct1Experiment {
    val AS_OF: LocalDate = LocalDate.of(2026, 10, 23)
    private val MAYOR_FROM = LocalDate.of(2023, 1, 1)
    private val CEO_FROM = LocalDate.of(2022, 6, 1)
    private val LONG_AGO = LocalDate.of(2015, 1, 1)
    private val EXPIRED = LocalDate.of(2025, 7, 1)
    private val FUTURE = LocalDate.of(2027, 1, 1)
    const val DEV_SEED = 20261023L
    const val TEST_SEED = 20261024L
    const val DEV_PER_VARIANT = 6
    const val TEST_PER_VARIANT = 25
    const val DISTRACTOR_CHAINS = 200
    val LATENT_SEEDS = listOf(1L, 2L, 3L, 4L, 5L)
    val VOLTAGES = listOf(110L, 120L, 220L, 230L, 240L, 400L)

    private val TRUSTED = mapOf(
        "kataster" to (1..5).map { "src-kataster-$it" }, "register" to (1..4).map { "src-register-$it" },
        "netz" to (1..3).map { "src-netz-$it" }, "gemeinde" to (1..4).map { "src-gemeinde-$it" })
    private val REVOKED = listOf("src-archiv-1", "src-archiv-2")
    private const val UNKNOWN_SOURCE = "src-fremd-1"
    val REGISTER: Map<String, SourceStatus> =
        TRUSTED.values.flatten().associateWith { SourceStatus.TRUSTED } + REVOKED.associateWith { SourceStatus.REVOKED }

    private fun family(r: Relation) = when (r) {
        Relation.IN_BUILDING, Relation.IN_CITY -> "kataster"
        Relation.GRID_VOLTAGE -> "netz"
        Relation.MAYOR -> "gemeinde"
        else -> "register"
    }

    enum class Variant(val group: String) {
        HOP2_CITY("hop"), HOP2_MAYOR("hop"), HOP3_REGION("hop"), HOP3_VOLTAGE("hop"), HOP3_CEO("hop"),
        CONTRA_STEP("contradiction"),
        AMBIG_MENTION("ambiguity"), AMBIG_INTERMEDIATE_GAP("ambiguity"), AMBIG_INTERMEDIATE_OK("ambiguity"),
        WRONG_REL_CITY("wrong-relation"), WRONG_REL_CAPITAL("wrong-relation"),
        OUTDATED_WITH_CURRENT("time"), OUTDATED_ONLY("time"), FUTURE_ONLY("time"),
        SRC_REVOKED_ONLY("source"), SRC_TAMPERED("source"), SRC_UNKNOWN("source"), SRC_REVOKED_CONFLICT("source"),
        MISSING_LINK("missing-link"),
        NUM_CHECK_TRUE("number-negation"), NUM_CHECK_CHANGED("number-negation"), NEG_QUESTION("number-negation"),
        NEG_FACT_CONFLICT("number-negation"), NEG_FACT_ONLY("number-negation")
    }

    data class Case(val id: String, val variant: Variant, val query: Query, val gold: Answer, val factIds: List<String>)

    class Dataset(val split: String, val kb: KnowledgeBase, val cases: List<Case>, val names: Set<String>) {
        val sha256: String by lazy {
            sha256(buildString {
                kb.entities.sortedBy { it.id }.forEach { append("E|${it.id}|${it.type}|${it.name}\n") }
                kb.facts.sortedBy { it.id }.forEach {
                    append("F|${it.id}|${it.source}|${it.subject}|${it.relation}|${it.objectId}|${it.quantity}|")
                    append("${it.polarity}|${it.validFrom}|${it.validTo}|${it.seal}\n")
                }
                cases.forEach { append("C|${it.id}|${it.variant}|${it.query}|${it.gold}|${it.factIds}\n") }
            })
        }
    }

    /** Syllable names; unique within a split and never one of `exclude` (DEV names stay out of TEST). */
    class Names(seed: Long, private val exclude: Set<String>) {
        private val rnd = Random(seed)
        val used = LinkedHashSet<String>()
        private val pre = listOf("Kalt", "Wald", "Berg", "Rosen", "Linden", "Eichen", "Sonnen", "Stein", "Hoch", "Tief",
            "Nord", "Süd", "Ost", "West", "Neu", "Alt", "Grün", "Weiß", "Schwarz", "Roth", "Buch", "Erlen", "Birken",
            "Tannen", "Felsen", "Wiesen", "Mühl", "Brunn", "Hasel", "Ulmen", "Espen", "Kirch", "Schön", "Lang", "Breit",
            "Klein", "Groß", "Hain", "Moos", "Au")
        private val mid = listOf("", "en", "er", "ing", "ach", "eck", "hof", "stein", "wald", "berg", "au", "feld", "bach",
            "burg", "brück", "hagen", "horn", "kirch", "lohe", "mark", "roda", "see", "tal", "wiesen", "zell")
        private val suf = listOf("brunn", "dorf", "feld", "hausen", "heim", "stadt", "berg", "tal", "au", "bach", "burg",
            "hofen", "ingen", "kirchen", "leben", "rode", "stätt", "weiler", "wang", "zell", "furt", "hain", "see",
            "walde", "stedt", "born", "beck", "büttel", "ried", "grund")
        private val rivers = listOf("", " an der Lauter", " am Inn", " an der Enz", " am Main", " an der Ilm",
            " an der Saale", " am Rhein")
        private val first = listOf("Anna", "Ben", "Clara", "David", "Eva", "Felix", "Greta", "Hannes", "Ida", "Jonas",
            "Klara", "Lukas", "Mia", "Noah", "Olga", "Paul", "Rosa", "Simon", "Theresa", "Udo", "Vera", "Walter", "Xenia",
            "Yannik", "Zoe", "Anton", "Berta", "Carl", "Doris", "Emil", "Frieda", "Georg", "Helga", "Ingo", "Jana",
            "Karl", "Lena", "Max", "Nina", "Otto", "Petra", "Ralf", "Sophie", "Tim", "Ulla", "Viktor", "Wilma", "Elias",
            "Hanna", "Leon", "Marie", "Finn", "Lea", "Moritz", "Emma", "Jakob", "Lina", "Oskar", "Ella", "Franz")
        private val last = listOf("Huber", "Gruber", "Bauer", "Wagner", "Müller", "Pichler", "Steiner", "Moser", "Mayer",
            "Hofer", "Leitner", "Berger", "Fuchs", "Eder", "Fischer", "Schmid", "Winkler", "Weber", "Schwarz", "Maier",
            "Schneider", "Reiter", "Mayr", "Schmidt", "Wimmer", "Egger", "Brunner", "Lang", "Baumgartner", "Auer",
            "Binder", "Lechner", "Wolf", "Wallner", "Aigner", "Ebner", "Koller", "Lehner", "Haas", "Schuster",
            "Holzer", "Strasser", "Wieser", "Haider", "Mair", "Hauser", "Böhm", "Wurm", "Graf", "Kaiser", "Zach",
            "Unger", "Hahn", "Riegler", "Seidl", "Schober", "Frank", "Kern", "Neubauer", "Hofmann", "Koch", "Richter",
            "Klein", "Wolff", "Schröder", "Neumann", "Schwab", "Zimmermann", "Krüger", "Hartmann", "Lange", "Werner",
            "Krause", "Meier", "Lehmann", "Köhler", "Herrmann", "Walter", "König", "Busch")
        private fun pick(list: List<String>) = list[rnd.nextInt(list.size)]
        private fun letter() = 'A' + rnd.nextInt(26)
        private fun draw(make: () -> String): String {
            repeat(1_000_000) { val n = make(); if (n !in used && n !in exclude) { used += n; return n } }
            error("name space exhausted")
        }
        fun of(type: Type): String = when (type) {
            Type.DEVICE -> draw { pick(listOf("Zähler", "Sensor", "Wechselrichter", "Router", "Pumpe", "Kamera",
                "Thermostat", "Ladesäule")) + " " + letter() + letter() + "-" + (10000 + rnd.nextInt(90000)) }
            Type.BUILDING -> draw { pick(listOf("Halle", "Werkhalle", "Lager", "Bürohaus", "Technikraum", "Umspannwerk",
                "Pumpenhaus", "Rechenzentrum")) + " " + pick(pre) + pick(listOf("weg", "straße", "gasse", "platz",
                "allee", "ring")) + " " + (1 + rnd.nextInt(199)) }
            Type.CITY -> draw { pick(pre) + pick(mid) + pick(suf) + pick(rivers) }
            Type.REGION -> draw { pick(listOf("Kreis ", "Bezirk ", "Gau ")) + pick(pre) + pick(mid) + pick(suf) }
            Type.COMPANY -> draw { pick(pre) + pick(mid) + pick(listOf("werk", "tec", "energie", "bau", "logistik",
                "netz", "daten", "technik")) + " " + pick(listOf("GmbH", "AG", "KG", "eG")) }
            Type.PERSON -> draw { pick(first) + " " + letter() + ". " + pick(last) }
        }
    }

    private class Chain(val device: String, val building: String, val city: String, val region: String,
                        val company: String, val hq: String, val mayor: String, val ceo: String, val voltage: Long,
                        val f: Map<String, Fact>)

    /** Builds one split. Case entities are fresh; only the ambiguity variants reuse a name on purpose. */
    class Builder(val names: Names, private val rnd: Random, private val prefix: String) {
        val entities = ArrayList<Entity>()
        private val nameOf = HashMap<String, String>()
        val facts = LinkedHashMap<String, Fact>()
        private var nextEntity = 0
        private var nextFact = 0

        fun entity(type: Type, name: String = names.of(type)): String {
            val id = "$prefix-e$nextEntity"; nextEntity++
            entities += Entity(id, type, name); nameOf[id] = name
            return id
        }
        fun name(id: String) = nameOf.getValue(id)
        private fun trusted(r: Relation) = TRUSTED.getValue(family(r)).let { it[rnd.nextInt(it.size)] }
        private fun newId() = "$prefix-f$nextFact".also { nextFact++ }
        fun fact(subject: String, relation: Relation, objectId: String? = null, quantity: Long? = null,
                 polarity: Polarity = Polarity.ASSERTED, from: LocalDate? = null, to: LocalDate? = null,
                 source: String = trusted(relation)): Fact =
            Fact.of(newId(), source, subject, relation, objectId, quantity, polarity, from, to).also { facts[it.id] = it }
        fun remove(f: Fact) { checkNotNull(facts.remove(f.id)) }
        /** The same statement as `old` under a new id with other source/validity/polarity. */
        fun restate(old: Fact, source: String = trusted(old.relation), from: LocalDate? = old.validFrom,
                    to: LocalDate? = old.validTo, polarity: Polarity = old.polarity): Fact {
            remove(old)
            return fact(old.subject, old.relation, old.objectId, old.quantity, polarity, from, to, source)
        }
        fun otherVoltage(v: Long): Long = VOLTAGES.filter { it != v }.let { it[rnd.nextInt(it.size)] }
        fun revoked() = REVOKED[rnd.nextInt(REVOKED.size)]

        private fun chain(deviceName: String? = null, cityName: String? = null): Chain {
            val d = if (deviceName == null) entity(Type.DEVICE) else entity(Type.DEVICE, deviceName)
            val b = entity(Type.BUILDING)
            val c = if (cityName == null) entity(Type.CITY) else entity(Type.CITY, cityName)
            val r = entity(Type.REGION)
            val k = entity(Type.COMPANY)
            val h = entity(Type.CITY)
            val m = entity(Type.PERSON)
            val ceo = entity(Type.PERSON)
            val capital = entity(Type.CITY)
            val largest = entity(Type.CITY)
            val v = VOLTAGES[rnd.nextInt(VOLTAGES.size)]
            val f = linkedMapOf(
                "device" to fact(d, Relation.IN_BUILDING, b), "city" to fact(b, Relation.IN_CITY, c),
                "region" to fact(c, Relation.IN_REGION, r), "voltage" to fact(r, Relation.GRID_VOLTAGE, quantity = v),
                "mayor" to fact(c, Relation.MAYOR, m, from = MAYOR_FROM), "operator" to fact(b, Relation.OPERATED_BY, k),
                "hq" to fact(k, Relation.HQ_CITY, h), "ceo" to fact(k, Relation.CEO, ceo, from = CEO_FROM),
                "capital" to fact(r, Relation.CAPITAL, capital), "largest" to fact(r, Relation.LARGEST_CITY, largest))
            return Chain(d, b, c, r, k, h, m, ceo, v, f)
        }

        /** Complete chains that are never asked about; cities, regions and companies come from shared pools. */
        fun distractors(count: Int) {
            val cities = List(maxOf(50, count)) { entity(Type.CITY) }
            val regions = List(maxOf(10, count / 10)) { entity(Type.REGION) }
            val companies = List(maxOf(20, count / 5)) { entity(Type.COMPANY) }
            fun <T> pickFrom(list: List<T>) = list[rnd.nextInt(list.size)]
            for (r in regions) {
                fact(r, Relation.GRID_VOLTAGE, quantity = pickFrom(VOLTAGES))
                fact(r, Relation.CAPITAL, pickFrom(cities)); fact(r, Relation.LARGEST_CITY, pickFrom(cities))
            }
            for (c in cities) { fact(c, Relation.IN_REGION, pickFrom(regions)); fact(c, Relation.MAYOR, entity(Type.PERSON), from = MAYOR_FROM) }
            for (k in companies) { fact(k, Relation.HQ_CITY, pickFrom(cities)); fact(k, Relation.CEO, entity(Type.PERSON), from = CEO_FROM) }
            repeat(count) {
                val d = entity(Type.DEVICE)
                val b = entity(Type.BUILDING)
                fact(d, Relation.IN_BUILDING, b); fact(b, Relation.IN_CITY, pickFrom(cities)); fact(b, Relation.OPERATED_BY, pickFrom(companies))
            }
        }

        fun knowledgeBase() = KnowledgeBase(entities.toList(), facts.values.toList(), REGISTER)

        private fun value(v: String, vararg steps: Fact) = Answer(Kind.VALUE, v, steps.map { it.id })
        private fun verdict(yes: Boolean, v: String, vararg steps: Fact) = Answer(if (yes) Kind.YES else Kind.NO, v, steps.map { it.id })
        private val abstain = Answer.abstain("GOLD")
        private val keys = mapOf(
            QuestionType.CITY_OF_DEVICE to listOf("device", "city"), QuestionType.MAYOR_AT_BUILDING to listOf("city", "mayor"),
            QuestionType.REGION_OF_DEVICE to listOf("device", "city", "region"),
            QuestionType.VOLTAGE_AT_BUILDING to listOf("city", "region", "voltage"),
            QuestionType.CEO_FOR_DEVICE to listOf("device", "operator", "ceo"),
            QuestionType.CAPITAL_FOR_BUILDING to listOf("city", "region", "capital"))
        private fun ask(type: QuestionType, c: Chain) =
            Query(type, name(if (type.pattern.first().from == Type.DEVICE) c.device else c.building), AS_OF)

        fun case(variant: Variant, i: Int, id: String): Case {
            val before = facts.keys.toHashSet()
            val even = i % 2 == 0
            val c = chain()
            val f = c.f
            val (query, gold) = when (variant) {
                Variant.HOP2_CITY -> ask(QuestionType.CITY_OF_DEVICE, c) to value(c.city, f.getValue("device"), f.getValue("city"))
                Variant.HOP2_MAYOR -> ask(QuestionType.MAYOR_AT_BUILDING, c) to value(c.mayor, f.getValue("city"), f.getValue("mayor"))
                Variant.HOP3_REGION -> ask(QuestionType.REGION_OF_DEVICE, c) to
                    value(c.region, f.getValue("device"), f.getValue("city"), f.getValue("region"))
                Variant.HOP3_VOLTAGE -> ask(QuestionType.VOLTAGE_AT_BUILDING, c) to
                    value("${c.voltage} V", f.getValue("city"), f.getValue("region"), f.getValue("voltage"))
                Variant.HOP3_CEO -> ask(QuestionType.CEO_FOR_DEVICE, c) to
                    value(c.ceo, f.getValue("device"), f.getValue("operator"), f.getValue("ceo"))
                Variant.CONTRA_STEP -> {
                    val type = if (even) QuestionType.REGION_OF_DEVICE else QuestionType.VOLTAGE_AT_BUILDING
                    val step = f.getValue(keys.getValue(type)[rnd.nextInt(3)])
                    if (step.quantity != null) fact(step.subject, step.relation, quantity = otherVoltage(step.quantity))
                    else fact(step.subject, step.relation, entity(step.relation.to!!))
                    ask(type, c) to abstain
                }
                Variant.AMBIG_MENTION -> {
                    chain(deviceName = name(c.device))
                    ask(if (even) QuestionType.CITY_OF_DEVICE else QuestionType.REGION_OF_DEVICE, c) to abstain
                }
                Variant.AMBIG_INTERMEDIATE_GAP -> {
                    chain(cityName = name(c.city)); remove(f.getValue("region"))
                    ask(QuestionType.REGION_OF_DEVICE, c) to abstain
                }
                Variant.AMBIG_INTERMEDIATE_OK -> {
                    chain(cityName = name(c.city))
                    ask(QuestionType.REGION_OF_DEVICE, c) to value(c.region, f.getValue("device"), f.getValue("city"), f.getValue("region"))
                }
                Variant.WRONG_REL_CITY -> { remove(f.getValue("city")); ask(QuestionType.CITY_OF_DEVICE, c) to abstain }
                Variant.WRONG_REL_CAPITAL -> { remove(f.getValue("capital")); ask(QuestionType.CAPITAL_FOR_BUILDING, c) to abstain }
                Variant.OUTDATED_WITH_CURRENT -> if (even) {
                    fact(c.city, Relation.MAYOR, entity(Type.PERSON), from = LONG_AGO, to = MAYOR_FROM)
                    ask(QuestionType.MAYOR_AT_BUILDING, c) to value(c.mayor, f.getValue("city"), f.getValue("mayor"))
                } else {
                    fact(c.company, Relation.CEO, entity(Type.PERSON), from = LONG_AGO, to = CEO_FROM)
                    ask(QuestionType.CEO_FOR_DEVICE, c) to value(c.ceo, f.getValue("device"), f.getValue("operator"), f.getValue("ceo"))
                }
                Variant.OUTDATED_ONLY, Variant.FUTURE_ONLY -> {
                    val old = f.getValue(if (even) "mayor" else "ceo")
                    if (variant == Variant.OUTDATED_ONLY) restate(old, from = LONG_AGO, to = EXPIRED) else restate(old, from = FUTURE, to = null)
                    ask(if (even) QuestionType.MAYOR_AT_BUILDING else QuestionType.CEO_FOR_DEVICE, c) to abstain
                }
                Variant.SRC_REVOKED_ONLY, Variant.SRC_UNKNOWN -> {
                    restate(f.getValue(if (even) "voltage" else "region"),
                        source = if (variant == Variant.SRC_UNKNOWN) UNKNOWN_SOURCE else revoked())
                    ask(if (even) QuestionType.VOLTAGE_AT_BUILDING else QuestionType.REGION_OF_DEVICE, c) to abstain
                }
                Variant.SRC_TAMPERED -> {
                    val old = f.getValue(if (even) "voltage" else "region")
                    val forged = if (even) old.copy(quantity = otherVoltage(c.voltage)) else old.copy(objectId = entity(Type.REGION))
                    facts[old.id] = forged
                    ask(if (even) QuestionType.VOLTAGE_AT_BUILDING else QuestionType.REGION_OF_DEVICE, c) to abstain
                }
                Variant.SRC_REVOKED_CONFLICT -> if (even) {
                    fact(c.region, Relation.GRID_VOLTAGE, quantity = otherVoltage(c.voltage), source = revoked())
                    ask(QuestionType.VOLTAGE_AT_BUILDING, c) to value("${c.voltage} V", f.getValue("city"), f.getValue("region"), f.getValue("voltage"))
                } else {
                    fact(c.city, Relation.IN_REGION, entity(Type.REGION), source = revoked())
                    ask(QuestionType.REGION_OF_DEVICE, c) to value(c.region, f.getValue("device"), f.getValue("city"), f.getValue("region"))
                }
                Variant.MISSING_LINK -> {
                    val type = keys.keys.toList()[i % keys.size]
                    remove(f.getValue(keys.getValue(type)[rnd.nextInt(type.pattern.size)]))
                    ask(type, c) to abstain
                }
                Variant.NUM_CHECK_TRUE, Variant.NUM_CHECK_CHANGED -> {
                    val claim = if (variant == Variant.NUM_CHECK_TRUE) c.voltage else otherVoltage(c.voltage)
                    ask(QuestionType.CHECK_VOLTAGE_AT_BUILDING, c).copy(claimedQuantity = claim) to
                        verdict(variant == Variant.NUM_CHECK_TRUE, "${c.voltage} V", f.getValue("city"), f.getValue("region"), f.getValue("voltage"))
                }
                Variant.NEG_QUESTION -> ask(QuestionType.CHECK_CITY_OF_DEVICE, c)
                    .copy(claimedName = name(if (even) c.city else c.hq), negated = true) to
                    verdict(!even, c.city, f.getValue("device"), f.getValue("city"))
                Variant.NEG_FACT_CONFLICT -> {
                    fact(c.building, Relation.IN_CITY, c.city, polarity = Polarity.NEGATED)
                    ask(QuestionType.CITY_OF_DEVICE, c) to abstain
                }
                Variant.NEG_FACT_ONLY -> {
                    restate(f.getValue("city"), polarity = Polarity.NEGATED)
                    ask(QuestionType.CITY_OF_DEVICE, c) to abstain
                }
            }
            return Case(id, variant, query, gold, facts.keys.filter { it !in before })
        }
    }

    /** One split: all variants, then the distractor chains. The builder can add more distractors for scaling. */
    fun generate(split: String, seed: Long, perVariant: Int, exclude: Set<String>): Pair<Dataset, Builder> {
        val builder = Builder(Names(seed xor 0x5EEDL, exclude), Random(seed), split)
        val cases = Variant.entries.flatMap { v -> (0 until perVariant).map { i -> builder.case(v, i, "$split-${v.name.lowercase()}-$i") } }
        builder.distractors(DISTRACTOR_CHAINS)
        return Dataset(split, builder.knowledgeBase(), cases, builder.names.used.toSet()) to builder
    }

    fun splits(): Pair<Dataset, Pair<Dataset, Builder>> {
        val dev = generate("dev", DEV_SEED, DEV_PER_VARIANT, emptySet()).first
        return dev to generate("test", TEST_SEED, TEST_PER_VARIANT, dev.names)
    }

    enum class Outcome { CORRECT, WRONG, MISSED }

    fun score(case: Case, a: Answer, kb: KnowledgeBase): Outcome {
        val g = case.gold
        if (g.kind == Kind.ABSTAIN) return if (a.kind == Kind.ABSTAIN) Outcome.CORRECT else Outcome.WRONG
        if (a.kind == Kind.ABSTAIN) return Outcome.MISSED
        val same = a.kind == g.kind && a.derivation == g.derivation && kb.display(a.value) == kb.display(g.value)
        return if (same) Outcome.CORRECT else Outcome.WRONG
    }

    class Tally(val solver: String) {
        val perVariant = linkedMapOf<Variant, IntArray>()
        fun add(v: Variant, o: Outcome) { perVariant.getOrPut(v) { IntArray(3) }[o.ordinal]++ }
        fun total(o: Outcome) = perVariant.values.sumOf { it[o.ordinal] }
        val answerable get() = total(Outcome.MISSED) + perVariant.entries.sumOf { (v, n) -> if (goldAnswers(v)) n[0] + n[1] else 0 }
        val coverage get() = perVariant.entries.sumOf { (v, n) -> if (goldAnswers(v)) n[0] else 0 }.toDouble() / answerable
    }

    private val answering = setOf(Variant.HOP2_CITY, Variant.HOP2_MAYOR, Variant.HOP3_REGION, Variant.HOP3_VOLTAGE,
        Variant.HOP3_CEO, Variant.AMBIG_INTERMEDIATE_OK, Variant.OUTDATED_WITH_CURRENT, Variant.SRC_REVOKED_CONFLICT,
        Variant.NUM_CHECK_TRUE, Variant.NUM_CHECK_CHANGED, Variant.NEG_QUESTION)
    fun goldAnswers(v: Variant) = v in answering

    fun evaluate(solver: Solver, ds: Dataset): Pair<Tally, List<Answer>> {
        val tally = Tally(solver.name)
        val answers = ds.cases.map { case -> solver.answer(case.query).also { tally.add(case.variant, score(case, it, ds.kb)) } }
        return tally to answers
    }

    fun trainedGuide(seed: Long, dev: Dataset): Lct1.FastWeightGuide =
        Lct1.FastWeightGuide(seed).apply { train(dev.cases.filter { it.gold.kind != Kind.ABSTAIN }.map { it.query.type }) }

    /** Up to five mutations of a correct answer; the verifier must reject every one. */
    fun mutations(q: Query, a: Answer, kb: KnowledgeBase, rnd: Random): List<Pair<String, Pair<Query, Answer>>> {
        val byId = kb.facts.associateBy { it.id }
        val out = ArrayList<Pair<String, Pair<Query, Answer>>>()
        val fin = byId.getValue(a.derivation.last())
        out += "VALUE" to (q to when {
            a.kind != Kind.VALUE -> a.copy(kind = if (a.kind == Kind.YES) Kind.NO else Kind.YES)
            fin.objectId == null -> a.copy(value = "${fin.quantity!! + 10} ${fin.relation.unit}")
            else -> {
                val type = kb.entityById.getValue(fin.objectId).type
                val others = kb.entities.filter { it.type == type && it.id != fin.objectId }
                a.copy(value = others[rnd.nextInt(others.size)].id)
            }
        })
        val i = rnd.nextInt(a.derivation.size)
        val step = byId.getValue(a.derivation[i])
        val foreign = kb.facts.filter { it.relation == step.relation && it.subject != step.subject }
        out += "FOREIGN_FACT" to (q to a.copy(derivation = a.derivation.toMutableList().also { it[i] = foreign[rnd.nextInt(foreign.size)].id }))
        out += "DROP_STEP" to (q to a.copy(derivation = a.derivation.dropLast(1)))
        if (a.derivation.size >= 2) out += "SWAP" to (q to a.copy(derivation = a.derivation.reversed()))
        val timed = a.derivation.map(byId::getValue).firstOrNull { it.validFrom != null || it.validTo != null }
        if (timed != null) out += "OTHER_DATE" to (q.copy(asOf = timed.validFrom?.minusDays(1) ?: timed.validTo!!) to a)
        return out
    }

    /** B0 must give what the real EvidenceBoundedThoughtSearch gives on every CITY_OF_DEVICE case. */
    fun ebtsAgreement(ds: Dataset, b0: Solver): Pair<Int, Int> {
        val byId = ds.kb.facts.associateBy { it.id }
        val mapped = mapOf(Relation.IN_BUILDING to ReviewedKnowledgeEdge.Relation.LOCATED_IN,
            Relation.IN_CITY to ReviewedKnowledgeEdge.Relation.CITY)
        var agree = 0
        val cases = ds.cases.filter { it.query.type == QuestionType.CITY_OF_DEVICE }
        for (case in cases) {
            val edges = case.factIds.map(byId::getValue)
                .filter { it.sealValid() && it.polarity == Polarity.ASSERTED && it.relation in mapped }
                .map {
                    val s = ds.kb.entityById.getValue(it.subject).name
                    val t = ds.kb.entityById.getValue(it.objectId!!).name
                    val r = mapped.getValue(it.relation)
                    ReviewedKnowledgeEdge(it.id, s, r, t, ReviewedKnowledgeEdge.pin(it.id, s, r, t), ownerReviewed = true)
                }
            val real = runCatching {
                EvidenceBoundedThoughtSearch(edges).solve(
                    EvidenceBoundedThoughtSearch.Query(case.query.mention, EvidenceBoundedThoughtSearch.Question.CITY))
            }.getOrNull()
            val ours = b0.answer(case.query)
            val same = if (real == null) ours.kind == Kind.ABSTAIN
                else ours.kind == Kind.VALUE && ours.value == real.value && ours.derivation == real.edgeIds
            if (same) agree++
        }
        return agree to cases.size
    }

    @Volatile private var sink = 0

    /** Median and p95 (µs) of per-query medians over five timed runs, after three warm-up passes. */
    fun latency(solver: Solver, queries: List<Query>): Pair<Double, Double> {
        repeat(3) { queries.forEach { sink += solver.answer(it).kind.ordinal } }
        val per = queries.map { q ->
            LongArray(5) { val t = System.nanoTime(); sink += solver.answer(q).kind.ordinal; System.nanoTime() - t }.sorted()[2]
        }.sorted()
        return per[per.size / 2] / 1000.0 to per[(per.size * 95 + 99) / 100 - 1] / 1000.0
    }

    private fun table(tallies: List<Tally>): String = buildString {
        append("variant".padEnd(24)); tallies.forEach { append(it.solver.padStart(14)) }; append("\n")
        for (v in Variant.entries) {
            append((v.name + if (goldAnswers(v)) " (A)" else " (E)").padEnd(24))
            tallies.forEach { t -> val n = t.perVariant[v] ?: IntArray(3); append("${n[0]}/${n[1]}/${n[2]}".padStart(14)) }
            append("\n")
        }
        append("WRONG total".padEnd(24)); tallies.forEach { append(it.total(Outcome.WRONG).toString().padStart(14)) }; append("\n")
        append("coverage".padEnd(24)); tallies.forEach { append("%.3f".format(it.coverage).padStart(14)) }; append("\n")
    }

    private fun json(value: Any?): String = when (value) {
        null -> "null"
        is String -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        is Number, is Boolean -> value.toString()
        is Map<*, *> -> value.entries.joinToString(",", "{", "}") { json(it.key.toString()) + ":" + json(it.value) }
        is Iterable<*> -> value.joinToString(",", "[", "]") { json(it) }
        else -> json(value.toString())
    }

    private fun tallyJson(t: Tally) = linkedMapOf("correct" to t.total(Outcome.CORRECT), "wrong" to t.total(Outcome.WRONG),
        "missed" to t.total(Outcome.MISSED), "answerable" to t.answerable, "coverage" to t.coverage,
        "perVariant" to t.perVariant.mapKeys { it.key.name }.mapValues { (_, n) -> listOf(n[0], n[1], n[2]) })

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 3 && args[0] in setOf("dev", "test")) { "usage: dev|test <report.json> <commit>" }
        val (dev, testPair) = splits()
        val (test, builder) = testPair
        println("DEV ${dev.cases.size} cases, ${dev.kb.facts.size} facts, sha256 ${dev.sha256}")
        println("TEST ${test.cases.size} cases, ${test.kb.facts.size} facts, sha256 ${test.sha256}")
        check(dev.names.intersect(test.names).isEmpty()) { "DEV and TEST names overlap" }
        val ds = if (args[0] == "dev") dev else test
        val l = Lct1.Reasoner(ds.kb)
        val verifier = Lct1.Verifier(ds.kb)
        val core = listOf(l, Lct1.VerifiedReasoner(l, verifier), Lct1.EbtsAlgorithm(ds.kb))
        val tallies = core.map { evaluate(it, ds).first }
        val latent = LATENT_SEEDS.associateWith { seed ->
            val guide = trainedGuide(seed, dev)
            listOf(Lct1.LatentRelationReasoner(ds.kb, guide, l), Lct1.LatentBeamReasoner(guide, l)).map { evaluate(it, ds) }
        }
        println(table(tallies + latent.getValue(1L).map { it.first }))
        val trainedOn = dev.cases.filter { it.gold.kind != Kind.ABSTAIN }.map { it.query.type }
        val transitionAccuracy = LATENT_SEEDS.associateWith { trainedGuide(it, dev).transitionAccuracy(trainedOn) }
        for ((seed, results) in latent) println("seed $seed: transition accuracy ${"%.3f".format(transitionAccuracy[seed])}, " +
            results.joinToString { "${it.first.solver} wrong=${it.first.total(Outcome.WRONG)} coverage=${"%.3f".format(it.first.coverage)}" })

        // verifier: false rejections of correct L answers, rejection of mutations, catch rate on latent wrong answers
        val rnd = Random(ds.split.hashCode().toLong())
        var correctAnswers = 0; var falseRejections = 0
        val mutated = linkedMapOf<String, IntArray>()
        val (_, lAnswers) = evaluate(l, ds)
        for ((case, a) in ds.cases.zip(lAnswers)) {
            if (a.kind == Kind.ABSTAIN || score(case, a, ds.kb) != Outcome.CORRECT) continue
            correctAnswers++
            if (!verifier.confirms(case.query, a)) falseRejections++
            for ((kind, pair) in mutations(case.query, a, ds.kb, rnd)) {
                val n = mutated.getOrPut(kind) { IntArray(2) }; n[0]++
                if (!verifier.confirms(pair.first, pair.second)) n[1]++
            }
        }
        val caught = latent.mapValues { (_, results) -> results.associate { (tally, answers) ->
            val wrong = ds.cases.zip(answers).filter { (case, a) -> a.kind != Kind.ABSTAIN && score(case, a, ds.kb) == Outcome.WRONG }
            tally.solver to listOf(wrong.size, wrong.count { (case, a) -> !verifier.confirms(case.query, a) })
        } }
        println("verifier: correct L answers $correctAnswers, false rejections $falseRejections, mutations rejected " +
            mutated.entries.joinToString { "${it.key} ${it.value[1]}/${it.value[0]}" })
        println("verifier on latent wrong answers (wrong, caught): $caught")
        val (agree, cityCases) = ebtsAgreement(ds, core[2])
        println("B0 vs real EvidenceBoundedThoughtSearch on CITY_OF_DEVICE: $agree/$cityCases agree")
        if (args[0] == "dev") return

        val scales = linkedMapOf<String, Any>()
        val queries = test.cases.map { it.query }
        val guide = trainedGuide(1L, dev)
        for ((label, extra) in listOf("1x" to 0, "10x" to 1800, "50x" to 8000)) {
            if (extra > 0) builder.distractors(extra)
            val kb = if (extra == 0) test.kb else builder.knowledgeBase()
            System.gc()
            val heapBefore = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
            val base = Lct1.Reasoner(kb)
            val solvers = listOf(base, Lct1.VerifiedReasoner(base, Lct1.Verifier(kb)), Lct1.EbtsAlgorithm(kb),
                Lct1.LatentRelationReasoner(kb, guide, base), Lct1.LatentBeamReasoner(guide, base))
            val timing = solvers.associate { s -> s.name to latency(s, queries).let { (m, p) -> mapOf("medianMicros" to m, "p95Micros" to p) } }
            val heapAfter = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
            scales[label] = mapOf("facts" to kb.facts.size, "entities" to kb.entities.size, "latency" to timing,
                "approxHeapBytesForSolvers" to (heapAfter - heapBefore))
            println("$label: ${kb.facts.size} facts " + timing.entries.joinToString { "${it.key} ${it.value}" })
        }
        val report = linkedMapOf(
            "experiment" to "B-LCT1", "preregistration" to "docs/research/LCT1-REASONING-PREREG-20261023.md",
            "commit" to args[2], "asOf" to AS_OF.toString(), "jvm" to java.lang.System.getProperty("java.version"),
            "devSha256" to dev.sha256, "testSha256" to test.sha256, "devCases" to dev.cases.size, "testCases" to test.cases.size,
            "testFacts" to test.kb.facts.size,
            "systems" to tallies.associate { it.solver to tallyJson(it) },
            "latent" to latent.mapKeys { "seed${it.key}" }.mapValues { (_, r) -> r.associate { it.first.solver to tallyJson(it.first) } },
            "verifier" to mapOf("correctLAnswers" to correctAnswers, "falseRejections" to falseRejections,
                "mutations" to mutated.mapValues { listOf(it.value[0], it.value[1]) }, "latentWrongCaught" to caught.mapKeys { "seed${it.key}" }),
            "ebtsFidelity" to mapOf("agree" to agree, "cases" to cityCases),
            "latentGuide" to mapOf("weightNorm" to guide.weightNorm(), "updates" to guide.updates, "parameters" to 256,
                "transitionAccuracy" to transitionAccuracy.mapKeys { "seed${it.key}" }),
            "scales" to scales)
        File(args[1]).writeText(json(report) + "\n")
        println("report written: ${args[1]}")
    }
}
