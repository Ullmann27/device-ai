package at.heinz.deviceai.intelligence.maxultra

import java.security.MessageDigest
import kotlin.random.Random

/**
 * CTX1 corpus (S1/S2), pre-registered in docs/research/CTX1-DIALOGUE-CONTEXT-PREREG-20261024.md: German multi-turn
 * dialogs about stored owner facts, with a gold answer per turn. Owner facts keep their fixed phrasings in every
 * split (they are stored statements); TEST uses unseen names, relation nouns, species, objects, cities, jobs, hobbies,
 * foods and unseen phrasings for every user turn.
 */
object Ctx1Corpus {
    enum class Gender { M, F, N }
    enum class Split { TRAIN, DEV, TEST }

    /** Gold of one turn: a memory answer (any of [facts] is right as the first citation), a question back, or nothing known. */
    sealed interface Gold
    data class Memory(val facts: Set<String>) : Gold
    object Clarify : Gold { override fun toString() = "CLARIFY" }
    object Unknown : Gold { override fun toString() = "NONE" }

    data class Turn(val text: String, val gold: Gold)
    data class Dialog(val id: String, val family: String, val facts: List<String>, val turns: List<Turn>)

    val FAMILIES = listOf("REF_SIMPLE", "REF_TYPO", "REF_AUSTRIAN", "ELLIPSIS", "CORRECTION", "NEGATION",
        "SWITCH", "RETURN", "AMBIGUOUS", "NEW_SESSION", "UNKNOWN", "TIME")
    val DAYS = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")

    private class Lexicon(
        val namesM: List<String>, val namesF: List<String>, val unknownM: List<String>, val unknownF: List<String>,
        val relM: List<String>, val relF: List<String>,
        val species: List<Pair<String, Gender>>, val petNames: List<String>, val objects: List<Pair<String, Gender>>,
        val cities: List<String>, val jobs: List<Pair<String, String>>, val hobbies: List<String>,
        val foods: List<String>, val colors: List<String>, val appointments: List<String>,
        /** predicate -> question templates; {X} is the referring expression */
        val questions: Map<String, List<String>>, val austrian: Map<String, List<String>>,
        val intro: List<String>, val introPair: List<String>, val ellipsis: List<String>,
        val correction: List<String>, val negation: List<String>, val negatedCity: List<String>,
        val dayQuestion: List<String>, val dayEllipsis: List<String>,
    )

    private val KNOWN = Lexicon(
        namesM = listOf("Max", "Paul", "Lukas", "Felix", "Jonas", "Tobias", "Simon", "David", "Florian", "Stefan", "Michael", "Andreas"),
        namesF = listOf("Anna", "Lena", "Laura", "Julia", "Sarah", "Lisa", "Katharina", "Sophie", "Hannah", "Marie", "Elena", "Clara"),
        unknownM = listOf("Gustav", "Herbert", "Otto"), unknownF = listOf("Gerda", "Ilse", "Frieda"),
        relM = listOf("Bruder", "Onkel", "Kollege", "Nachbar", "Freund", "Sohn", "Cousin"),
        relF = listOf("Schwester", "Tante", "Kollegin", "Nachbarin", "Freundin", "Tochter", "Cousine"),
        species = listOf("Hund" to Gender.M, "Katze" to Gender.F, "Pferd" to Gender.N, "Hamster" to Gender.M,
            "Schildkröte" to Gender.F, "Kaninchen" to Gender.N),
        petNames = listOf("Bello", "Rex", "Mimi", "Luna", "Fritz", "Susi"),
        objects = listOf("Auto" to Gender.N, "Fahrrad" to Gender.N, "Laptop" to Gender.M, "Waschmaschine" to Gender.F,
            "Handy" to Gender.N, "Fernseher" to Gender.M),
        cities = listOf("Graz", "Linz", "Salzburg", "Innsbruck", "Klagenfurt", "Bregenz", "Eisenstadt", "Wien"),
        jobs = listOf("Tischler" to "Tischlerin", "Lehrer" to "Lehrerin", "Koch" to "Köchin", "Ingenieur" to "Ingenieurin",
            "Verkäufer" to "Verkäuferin", "Programmierer" to "Programmiererin"),
        hobbies = listOf("Schach", "Fußball", "Gitarre", "Tennis", "Klavier", "Karten"),
        foods = listOf("Karotten", "Fisch", "Heu", "Salat", "Körner", "Äpfel"),
        colors = listOf("Rot", "Blau", "Grün", "Schwarz", "Weiß", "Gelb"),
        appointments = listOf("einen Termin beim Zahnarzt", "Training im Verein", "einen Kurs in der Volkshochschule",
            "ein Treffen mit dem Elternverein", "Dienst im Krankenhaus"),
        questions = mapOf(
            "age" to listOf("Wie alt ist {X}?"), "city" to listOf("Wo wohnt {X}?"),
            "job" to listOf("Als was arbeitet {X}?"), "hobby" to listOf("Was spielt {X} gern?"),
            "food" to listOf("Was frisst {X} am liebsten?"), "color" to listOf("Welche Farbe hat {X}?")),
        austrian = mapOf("age" to listOf("Wie alt is {X} denn?"), "city" to listOf("Wo wohnt'n {X}?"),
            "job" to listOf("Als was arbeit {X}?")),
        intro = listOf("Erzähl mir von {X}.", "Was weißt du über {X}?"),
        introPair = listOf("Erzähl mir von {X} und {Y}."),
        ellipsis = listOf("Und {X}?"),
        correction = listOf("Nein, ich meinte {X}."),
        negation = listOf("Nicht {X}, sondern {Y}."),
        negatedCity = listOf("Wohnt {X} nicht in {C}?"),
        dayQuestion = listOf("Was habe ich am {D} vor?"),
        dayEllipsis = listOf("Und am {D}?"),
    )

    private val UNSEEN = Lexicon(
        namesM = listOf("Bernhard", "Gregor", "Konrad", "Valentin", "Ferdinand", "Leopold", "Matthias", "Severin", "Clemens", "Raphael"),
        namesF = listOf("Theresa", "Magdalena", "Veronika", "Johanna", "Rosalie", "Valerie", "Helene", "Antonia", "Barbara", "Franziska"),
        unknownM = listOf("Ignaz", "Wendelin", "Kilian"), unknownF = listOf("Walburga", "Notburga", "Hedwig"),
        relM = listOf("Schwager", "Neffe", "Taufpate", "Chef", "Mitbewohner", "Enkel", "Schulfreund"),
        relF = listOf("Schwägerin", "Nichte", "Taufpatin", "Chefin", "Mitbewohnerin", "Enkelin", "Schulfreundin"),
        species = listOf("Wellensittich" to Gender.M, "Ziege" to Gender.F, "Meerschweinchen" to Gender.N, "Papagei" to Gender.M,
            "Ratte" to Gender.F, "Pony" to Gender.N),
        petNames = listOf("Strolchi", "Flocki", "Moritz", "Kiki", "Brutus", "Nala"),
        objects = listOf("Motorrad" to Gender.N, "Kaffeemaschine" to Gender.F, "Rasenmäher" to Gender.M, "Tablet" to Gender.N,
            "Nähmaschine" to Gender.F, "Kühlschrank" to Gender.M),
        cities = listOf("Villach", "Wels", "Steyr", "Leoben", "Krems", "Dornbirn", "Amstetten", "Baden"),
        jobs = listOf("Bäcker" to "Bäckerin", "Gärtner" to "Gärtnerin", "Elektriker" to "Elektrikerin",
            "Apotheker" to "Apothekerin", "Friseur" to "Friseurin", "Tischler" to "Tischlerin"),
        hobbies = listOf("Volleyball", "Akkordeon", "Badminton", "Schlagzeug", "Golf", "Tarock"),
        foods = listOf("Löwenzahn", "Nüsse", "Gras", "Gurken", "Hirse", "Bananen"),
        colors = listOf("Silber", "Orange", "Violett", "Braun", "Grau", "Türkis"),
        appointments = listOf("eine Besprechung im Büro", "Probe im Musikverein", "einen Arzttermin in Wien",
            "Kinderdienst im Kindergarten", "einen Ausflug mit den Kollegen"),
        questions = mapOf(
            "age" to listOf("Weißt du, wie alt {X} ist?", "Wie alt ist {X} eigentlich?"),
            "city" to listOf("In welcher Stadt wohnt {X}?", "Wo wohnt {X} jetzt?"),
            "job" to listOf("Was arbeitet {X} eigentlich?", "Als was arbeitet {X} denn?"),
            "hobby" to listOf("Was spielt {X} denn gern?", "Was spielt {X} am liebsten?"),
            "food" to listOf("Was frisst {X} denn gern?", "Was frisst {X} eigentlich am liebsten?"),
            "color" to listOf("Welche Farbe hat {X} eigentlich?", "Was für eine Farbe hat {X}?")),
        austrian = mapOf("age" to listOf("Wia alt is {X} eigentlich?"), "city" to listOf("Wo wohnt'n {X} jetzt?"),
            "job" to listOf("Wos arbeit {X} eigentlich?")),
        intro = listOf("Sag mir etwas über {X}.", "Kennst du {X}?"),
        introPair = listOf("Sag mir etwas über {X} und {Y}."),
        ellipsis = listOf("Und was ist mit {X}?", "Und bei {X}?"),
        correction = listOf("Ich mein eigentlich {X}.", "Falsch, gemeint war {X}."),
        negation = listOf("Nein, nicht {X}, ich meine {Y}."),
        negatedCity = listOf("Wohnt {X} denn nicht in {C}?"),
        dayQuestion = listOf("Was steht am {D} an?"),
        dayEllipsis = listOf("Und wie schaut's am {D} aus?"),
    )

    /** One referable thing of a dialog world with its stored facts per predicate. */
    class Entity(val name: String, val gender: Gender, val kind: String, val det: String?, val facts: LinkedHashMap<String, String>) {
        /** Referring expression in a question: the name, or the possessive noun phrase for an object. */
        val mention get() = if (kind == "object") "$det $name" else name
        /** Dative after "von": objects need "meinem/meiner". */
        val dativeMention get() = if (kind == "object") (if (gender == Gender.F) "meiner $name" else "meinem $name") else name
        val pronoun get() = when (gender) { Gender.M -> "er"; Gender.F -> "sie"; Gender.N -> "es" }
    }

    private class World(val people: List<Entity>, val pet: Entity, val obj: Entity, val days: List<Pair<String, String>>) {
        val all get() = people + pet + obj
        fun facts(): List<String> = all.flatMap { it.facts.values } + days.map { it.second }
    }

    private class Builder(private val lex: Lexicon, private val rnd: Random) {
        private fun <T> pick(xs: List<T>) = xs[rnd.nextInt(xs.size)]
        private fun <T> distinct(xs: List<T>, n: Int): List<T> = xs.shuffled(rnd).take(n)

        fun world(genders: List<Gender>, withDays: Boolean = false): World {
            val namesM = distinct(lex.namesM, 3).toMutableList(); val namesF = distinct(lex.namesF, 3).toMutableList()
            val ages = distinct((8..79).toList(), 6).toMutableList()
            val cities = distinct(lex.cities, 3).toMutableList(); val jobs = distinct(lex.jobs, 3).toMutableList()
            val hobbies = distinct(lex.hobbies, 3).toMutableList()
            val people = genders.map { g ->
                val name = if (g == Gender.F) namesF.removeAt(0) else namesM.removeAt(0)
                val rel = pick(if (g == Gender.F) lex.relF else lex.relM)
                val job = jobs.removeAt(0).let { if (g == Gender.F) it.second else it.first }
                Entity(name, g, "person", null, linkedMapOf(
                    "relation" to "$name ist ${if (g == Gender.F) "meine" else "mein"} $rel.",
                    "age" to "$name ist ${ages.removeAt(0)} Jahre alt.",
                    "city" to "$name wohnt in ${cities.removeAt(0)}.",
                    "job" to "$name arbeitet als $job.",
                    "hobby" to "$name spielt gern ${hobbies.removeAt(0)}."))
            }
            val (species, sg) = pick(lex.species)
            val petName = pick(lex.petNames)
            val pet = Entity(petName, sg, "pet", null, linkedMapOf(
                "species" to "${if (sg == Gender.F) "Unsere" else "Unser"} $species heißt $petName.",
                "age" to "$petName ist ${ages.removeAt(0)} Jahre alt.",
                "food" to "$petName frisst am liebsten ${pick(lex.foods)}."))
            val (noun, og) = pick(lex.objects)
            val det = if (og == Gender.F) "meine" else "mein"
            val obj = Entity(noun, og, "object", det, linkedMapOf(
                "age" to "${det.replaceFirstChar { it.uppercase() }} $noun ist ${ages.removeAt(0)} Jahre alt.",
                "color" to "${det.replaceFirstChar { it.uppercase() }} $noun hat die Farbe ${pick(lex.colors)}."))
            val days = if (!withDays) emptyList() else distinct(DAYS.take(5), 3).zip(distinct(lex.appointments, 3))
                .map { (d, a) -> d to "Am $d habe ich $a." }
            return World(people, pet, obj, days)
        }

        fun q(pred: String, x: String) = pick(lex.questions.getValue(pred)).replace("{X}", x)
        fun austrian(pred: String, x: String) = pick(lex.austrian.getValue(pred)).replace("{X}", x)
        fun intro(e: Entity) = pick(lex.intro).let { t -> t.replace("{X}", if (t.contains("von {X}")) e.dativeMention else e.mention) }
        fun introPair(a: Entity, b: Entity) = pick(lex.introPair).replace("{X}", a.name).replace("{Y}", b.name)
        fun ellipsis(e: Entity) = pick(lex.ellipsis).replace("{X}", e.mention)
        fun correction(e: Entity) = pick(lex.correction).replace("{X}", e.mention)
        fun negation(a: Entity, b: Entity) = pick(lex.negation).replace("{X}", a.mention).replace("{Y}", b.mention)
        fun negatedCity(x: String, city: String) = pick(lex.negatedCity).replace("{X}", x).replace("{C}", city)
        fun dayQ(d: String) = pick(lex.dayQuestion).replace("{D}", d)
        fun dayEllipsis(d: String) = pick(lex.dayEllipsis).replace("{D}", d)
        fun unknownName(g: Gender) = pick(if (g == Gender.F) lex.unknownF else lex.unknownM)
        fun gender() = if (rnd.nextBoolean()) Gender.M else Gender.F
        fun personPred() = pick(listOf("age", "city", "job", "hobby"))
        fun nextInt(n: Int) = rnd.nextInt(n)

        /** One keyboard-neighbour substitution inside the predicate word (not its first letter). */
        fun typo(question: String, word: String): String {
            val rows = listOf("qwertzuiopü", "asdfghjklöä", "yxcvbnm")
            val i = 1 + rnd.nextInt(word.length - 1)
            val c = word[i].lowercaseChar()
            val row = rows.firstOrNull { c in it } ?: return question
            val j = row.indexOf(c)
            val n = if (j == 0) row[1] else if (j == row.length - 1) row[j - 1] else row[j + if (rnd.nextBoolean()) 1 else -1]
            return question.replaceFirst(word, word.substring(0, i) + n + word.substring(i + 1))
        }
    }

    private val PRED_WORD = mapOf("age" to "alt", "city" to "wohnt", "job" to "arbeitet", "hobby" to "spielt",
        "food" to "frisst", "color" to "Farbe")

    private fun mem(vararg facts: String) = Memory(facts.toSet())

    private fun dialog(b: Builder, family: String, id: String): Dialog {
        fun personWorld(vararg g: Gender) = b.world(g.toList() + List(3 - g.size) { b.gender() })
        return when (family) {
            "REF_SIMPLE" -> {
                val w = personWorld(b.gender()); val p = w.people[0]
                val (p1, p2) = listOf("age", "city", "job", "hobby").shuffled(Random(id.hashCode())).take(2)
                Dialog(id, family, w.facts(), listOf(Turn(b.intro(p), Memory(p.facts.values.toSet())),
                    Turn(b.q(p1, p.pronoun), mem(p.facts.getValue(p1))), Turn(b.q(p2, p.pronoun), mem(p.facts.getValue(p2)))))
            }
            "REF_TYPO" -> {
                val w = personWorld(b.gender()); val p = w.people[0]; val pred = b.personPred()
                val q = b.q(pred, p.pronoun)
                Dialog(id, family, w.facts(), listOf(Turn(b.intro(p), Memory(p.facts.values.toSet())),
                    Turn(b.typo(q, PRED_WORD.getValue(pred)), mem(p.facts.getValue(pred)))))
            }
            "REF_AUSTRIAN" -> {
                val w = personWorld(b.gender()); val p = w.people[0]; val pred = listOf("age", "city", "job")[b.nextInt(3)]
                Dialog(id, family, w.facts(), listOf(Turn(b.intro(p), Memory(p.facts.values.toSet())),
                    Turn(b.austrian(pred, p.pronoun), mem(p.facts.getValue(pred)))))
            }
            "ELLIPSIS" -> {
                val w = personWorld(); val (a, c) = w.people.take(2); val pred = b.personPred()
                Dialog(id, family, w.facts(), listOf(Turn(b.q(pred, a.mention), mem(a.facts.getValue(pred))),
                    Turn(b.ellipsis(c), mem(c.facts.getValue(pred)))))
            }
            "CORRECTION" -> {
                val w = personWorld(); val (a, c) = w.people.take(2); val pred = b.personPred()
                Dialog(id, family, w.facts(), listOf(Turn(b.q(pred, a.mention), mem(a.facts.getValue(pred))),
                    Turn(b.correction(c), mem(c.facts.getValue(pred)))))
            }
            "NEGATION" -> {
                val w = personWorld(); val (a, c) = w.people.take(2)
                val wrongCity = a.facts.getValue("city").substringAfter("wohnt in ").removeSuffix(".")
                Dialog(id, family, w.facts(), listOf(Turn(b.q("age", a.mention), mem(a.facts.getValue("age"))),
                    Turn(b.negation(a, c), mem(c.facts.getValue("age"))),
                    Turn(b.negatedCity(c.pronoun, wrongCity), mem(c.facts.getValue("city")))))
            }
            "SWITCH" -> {
                val w = personWorld(b.gender()); val p = w.people[0]
                val target = if (b.nextInt(2) == 0) w.pet else w.obj
                val pred = if (target.kind == "pet") "food" else "color"
                Dialog(id, family, w.facts(), listOf(Turn(b.intro(p), Memory(p.facts.values.toSet())),
                    Turn(b.intro(target), Memory(target.facts.values.toSet())),
                    Turn(b.q(pred, target.pronoun), mem(target.facts.getValue(pred)))))
            }
            "RETURN" -> {
                // the more recent topic is feminine, so "er" can only mean the person mentioned before it
                var w = personWorld(Gender.M)
                while (w.pet.gender != Gender.F && w.obj.gender != Gender.F) w = personWorld(Gender.M)
                val p = w.people[0]; val middle = if (w.pet.gender == Gender.F) w.pet else w.obj
                val pred = b.personPred()
                Dialog(id, family, w.facts(), listOf(Turn(b.intro(p), Memory(p.facts.values.toSet())),
                    Turn(b.intro(middle), Memory(middle.facts.values.toSet())),
                    Turn(b.q(pred, p.pronoun), mem(p.facts.getValue(pred)))))
            }
            "AMBIGUOUS" -> {
                val g = b.gender(); val w = personWorld(g, g); val (a, c) = w.people.take(2)
                Dialog(id, family, w.facts(), listOf(Turn(b.introPair(a, c), Memory((a.facts.values + c.facts.values).toSet())),
                    Turn(b.q(b.personPred(), a.pronoun), Clarify)))
            }
            "NEW_SESSION" -> {
                val w = personWorld(); Dialog(id, family, w.facts(), listOf(Turn(b.q(b.personPred(), w.people[b.nextInt(3)].pronoun), Clarify)))
            }
            "UNKNOWN" -> {
                val w = personWorld(); val g = b.gender(); val name = b.unknownName(g)
                val (p1, p2) = listOf("age", "city", "job", "hobby").shuffled(Random(id.hashCode())).take(2)
                Dialog(id, family, w.facts(), listOf(Turn(b.q(p1, name), Unknown),
                    Turn(b.q(p2, if (g == Gender.F) "sie" else "er"), Unknown)))
            }
            "TIME" -> {
                val w = b.world(List(3) { b.gender() }, withDays = true); val (d1, d2) = w.days.take(2)
                Dialog(id, family, w.facts(), listOf(Turn(b.dayQ(d1.first), mem(d1.second)), Turn(b.dayEllipsis(d2.first), mem(d2.second))))
            }
            else -> error("unknown family $family")
        }
    }

    fun split(split: Split, seed: Long, perFamily: Int): List<Dialog> {
        val b = Builder(if (split == Split.TEST) UNSEEN else KNOWN, Random(seed))
        return FAMILIES.flatMap { f -> (0 until perFamily).map { i -> dialog(b, f, "${split.name.lowercase()}-${f.lowercase()}-$i") } }
    }

    /** Canonical text of a corpus; its SHA-256 seals TEST before the first run. */
    fun canonical(dialogs: List<Dialog>): String = buildString {
        for (d in dialogs) {
            append("D\t").append(d.id).append('\t').append(d.family).append('\n')
            d.facts.forEach { append("F\t").append(it).append('\n') }
            d.turns.forEach { t ->
                append("T\t").append(t.text).append('\t')
                when (val g = t.gold) {
                    is Memory -> append("M\t").append(g.facts.sorted().joinToString("|"))
                    else -> append(g.toString())
                }
                append('\n')
            }
        }
    }

    fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
