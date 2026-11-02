package at.heinz.deviceai.intelligence.maxultra

import java.util.Random

/**
 * Experiment A (TTT1), research only; pre-registered in docs/research/TTT1-WORKING-MEMORY-PREREG-20261023.md.
 * Reference resolution in German dialogs by a bounded context (K), an explicit entity store (S) and the store plus
 * 16x16 fast weights (S+FW). Turns arrive already analysed (mentions, pronoun gender, predicate type): this measures
 * the memory, not language understanding. Everything lives in RAM for one session only.
 */
object Ttt1 {
    enum class Gender(val nominative: String, val accusative: String, val article: String) {
        MASC("er", "ihn", "der"), FEM("sie", "sie", "die"), NEUT("es", "es", "das")
    }
    enum class Kind { DEVICE, BUILDING }
    data class Entity(val id: String, val kind: Kind, val noun: String, val gender: Gender, val name: String)

    const val ASK = "RÜCKFRAGE"

    /**
     * One user turn. `mentions`: entities named explicitly; `pronoun`: gender of a pronoun to resolve; `predicate`:
     * kind the predicate requires (null = any); `typeNoun`: "Und die Kamera?"; `gold`: entity id or ASK for scored turns.
     */
    data class Turn(val text: String, val mentions: List<Entity> = emptyList(), val pronoun: Gender? = null,
                    val predicate: Kind? = null, val typeNoun: String? = null, val reset: Boolean = false,
                    val gold: String? = null)

    data class Dialog(val id: String, val variant: String, val turns: List<Turn>)

    interface Memory {
        val name: String
        fun reset()
        /** Resolve a reference turn (pronoun or type noun) to an entity id or ASK; null for other turns. */
        fun resolve(t: Turn): String?
        /** Record what the turn referred to (explicit mentions, or the resolved entity). */
        fun observe(t: Turn, resolved: String?)
        /** Bytes of model state held now (counted fields, not JVM object overhead). */
        fun bytes(): Int
    }

    /** K: the last three turns; the most recent entity of the pronoun's gender, else ASK. */
    class BoundedContext(private val window: Int = 3) : Memory {
        override val name = "K"
        private val turns = ArrayDeque<List<Entity>>()
        private val known = HashMap<String, Entity>()
        override fun reset() { turns.clear(); known.clear() }
        override fun resolve(t: Turn): String? {
            val recent = turns.reversed().flatMap { it.reversed() }
            return when {
                t.pronoun != null -> recent.firstOrNull { it.gender == t.pronoun }?.id ?: ASK
                t.typeNoun != null -> recent.firstOrNull { it.noun == t.typeNoun }?.id ?: ASK
                else -> null
            }
        }
        override fun observe(t: Turn, resolved: String?) {
            t.mentions.forEach { known[it.id] = it }
            val entities = t.mentions.ifEmpty { listOfNotNull(resolved?.let(known::get)) }
            turns.addLast(entities)
            while (turns.size > window) turns.removeFirst()
        }
        override fun bytes() = turns.sumOf { it.size } * 12
    }

    /** S: up to 16 entities with gender, kind, noun and last turn; ties in the latest turn and no match -> ASK. */
    open class EntityStore(private val capacity: Int = 16) : Memory {
        override val name = "S"
        protected data class Entry(val entity: Entity, var lastTurn: Int)
        protected val entries = LinkedHashMap<String, Entry>()
        protected var turn = 0
        override fun reset() { entries.clear(); turn = 0 }
        protected fun candidates(t: Turn): List<Entry> = when {
            t.pronoun != null -> entries.values.filter { it.entity.gender == t.pronoun && (t.predicate == null || it.entity.kind == t.predicate) }
            t.typeNoun != null -> entries.values.filter { it.entity.noun == t.typeNoun }
            else -> emptyList()
        }.sortedByDescending { it.lastTurn }

        override fun resolve(t: Turn): String? {
            if (t.pronoun == null && t.typeNoun == null) return null
            val c = candidates(t)
            if (c.isEmpty()) return ASK
            if (t.typeNoun != null) return if (c.size == 1) c[0].entity.id else ASK
            return if (c.size > 1 && c[0].lastTurn == c[1].lastTurn) ASK else c[0].entity.id
        }

        override fun observe(t: Turn, resolved: String?) {
            turn++
            for (e in t.mentions) entries.getOrPut(e.id) { Entry(e, turn) }.lastTurn = turn
            if (t.mentions.isEmpty() && resolved != null && resolved != ASK) entries[resolved]?.lastTurn = turn
            while (entries.size > capacity) entries.remove(entries.values.minBy { it.lastTurn }.entity.id)
        }
        override fun bytes() = entries.size * 28
    }

    /**
     * S+FW: the store, plus fast weights that learn previous -> current referent within the session. They decide ties
     * the store would ask about and overrule recency when another admissible candidate is at least `margin` more
     * similar to the prediction. Gender and kind filters stay.
     */
    class StoreWithFastWeights(private val seed: Long, private val margin: Double = 0.2) : EntityStore() {
        override val name = "S+FW"
        private var guide = Lct1.FastWeightGuide(seed)
        private val vectors = HashMap<String, DoubleArray>()
        private var previous: String? = null
        private fun vector(id: String) = vectors.getOrPut(id) {
            val r = Random(seed * 1_000_003L + id.hashCode())
            Lct1.FastWeightGuide.normalize(DoubleArray(Lct1.FastWeightGuide.DIM) { r.nextGaussian() })
        }
        override fun reset() { super.reset(); guide = Lct1.FastWeightGuide(seed); vectors.clear(); previous = null }

        override fun resolve(t: Turn): String? {
            val plain = super.resolve(t) ?: return null
            val before = previous ?: return plain
            if (t.pronoun == null) return plain
            val c = candidates(t)
            if (c.size < 2) return plain
            val prediction = guide.predict(vector(before))
            val score = { e: Entry -> Lct1.FastWeightGuide.cosine(prediction, vector(e.entity.id)) }
            if (plain == ASK) return c.filter { it.lastTurn == c[0].lastTurn }.maxBy(score).entity.id
            val chosen = c.first { it.entity.id == plain }
            val rival = c.filter { it !== chosen }.maxBy(score)
            return if (score(rival) >= score(chosen) + margin) rival.entity.id else plain
        }

        override fun observe(t: Turn, resolved: String?) {
            super.observe(t, resolved)
            val current = t.mentions.singleOrNull()?.id ?: resolved?.takeIf { it != ASK && t.mentions.isEmpty() } ?: return
            previous?.let { guide.learn(vector(it), vector(current)) }
            previous = current
        }
        override fun bytes() = super.bytes() + Lct1.FastWeightGuide.DIM * Lct1.FastWeightGuide.DIM * 8 +
            vectors.size * Lct1.FastWeightGuide.DIM * 8
    }

    /** Deterministic German test dialogs; names never repeat within a split and avoid `exclude`. */
    class Generator(seed: Long, private val exclude: Set<String>) {
        private val rnd = Random(seed)
        val used = LinkedHashSet<String>()
        private var next = 0
        private val nouns = mapOf(
            Kind.DEVICE to listOf("Zähler" to Gender.MASC, "Sensor" to Gender.MASC, "Router" to Gender.MASC,
                "Wechselrichter" to Gender.MASC, "Kompressor" to Gender.MASC, "Pumpe" to Gender.FEM, "Kamera" to Gender.FEM,
                "Ladesäule" to Gender.FEM, "Heizung" to Gender.FEM, "Waage" to Gender.FEM, "Thermometer" to Gender.NEUT,
                "Messgerät" to Gender.NEUT, "Ventil" to Gender.NEUT, "Modem" to Gender.NEUT, "Netzteil" to Gender.NEUT),
            Kind.BUILDING to listOf("Technikraum" to Gender.MASC, "Keller" to Gender.MASC, "Turm" to Gender.MASC,
                "Halle" to Gender.FEM, "Werkhalle" to Gender.FEM, "Garage" to Gender.FEM, "Lager" to Gender.NEUT,
                "Bürohaus" to Gender.NEUT, "Rechenzentrum" to Gender.NEUT, "Umspannwerk" to Gender.NEUT))

        fun entity(kind: Kind, gender: Gender? = null, noun: String? = null): Entity {
            val options = nouns.getValue(kind).filter { (n, g) -> (gender == null || g == gender) && (noun == null || n == noun) }
            val (n, g) = options[rnd.nextInt(options.size)]
            var name: String
            do {
                name = "$n " + ('A' + rnd.nextInt(26)) + ('A' + rnd.nextInt(26)) + "-" + (100 + rnd.nextInt(900))
            } while (name in used || name in exclude)
            used += name
            return Entity("x${next++}", kind, n, g, name)
        }

        private fun named(e: Entity) = "${e.gender.article} ${e.name}"
        private fun mention(e: Entity) = Turn(
            (if (e.kind == Kind.DEVICE) "Wo steht ${named(e)}?" else "Wer betreibt ${named(e)}?").replaceFirstChar { it.uppercase() },
            mentions = listOf(e))
        /** A pronoun turn about `e` (gold = e unless overridden); the predicate fits e's kind or is neutral. */
        private fun pronoun(e: Entity, gold: String = e.id, kindSpecific: Boolean = rnd.nextBoolean()): Turn {
            val g = e.gender
            return if (!kindSpecific) Turn("Wie alt ist ${g.nominative}?", pronoun = g, gold = gold)
            else if (e.kind == Kind.DEVICE) Turn("Welche Seriennummer hat ${g.nominative}?", pronoun = g, predicate = Kind.DEVICE, gold = gold)
            else Turn("Wer betreibt ${g.accusative}?", pronoun = g, predicate = Kind.BUILDING, gold = gold)
        }
        private fun anyKind() = if (rnd.nextBoolean()) Kind.DEVICE else Kind.BUILDING
        private fun otherGender(g: Gender) = Gender.entries.filter { it != g }.let { it[rnd.nextInt(it.size)] }

        fun dialog(variant: String, id: String): Dialog = Dialog(id, variant, when (variant) {
            "CONTINUE" -> { val e = entity(anyKind()); listOf(mention(e), pronoun(e), pronoun(e), pronoun(e)) }
            "SWITCH" -> {
                val a = entity(anyKind()); val b = entity(anyKind(), otherGender(a.gender))
                listOf(mention(a), pronoun(a), Turn("Und ${named(b)}?", mentions = listOf(b)), pronoun(b))
            }
            "RETURN" -> {
                val a = entity(anyKind()); val b = entity(anyKind(), otherGender(a.gender))
                listOf(mention(a), mention(b), pronoun(a, kindSpecific = false))
            }
            "LONG_DISTANCE" -> {
                val a = entity(anyKind()); val b = entity(anyKind(), otherGender(a.gender))
                listOf(mention(a), mention(b), pronoun(b), pronoun(b), pronoun(b), pronoun(a, kindSpecific = false))
            }
            "TYPE_CONSTRAINT" -> {
                val g = Gender.entries[rnd.nextInt(3)]
                val building = entity(Kind.BUILDING, g); val device = entity(Kind.DEVICE, g)
                listOf(mention(building), mention(device),
                    Turn("Wer betreibt ${g.accusative}?", pronoun = g, predicate = Kind.BUILDING, gold = building.id))
            }
            "AMBIGUOUS" -> {
                val c = entity(anyKind()); val kind = anyKind(); val g = otherGender(c.gender)
                val a = entity(kind, g); val b = entity(kind, g)
                listOf(mention(c), pronoun(c),
                    Turn("Vergleiche ${named(a)} mit ${if (g == Gender.FEM) "der" else "dem"} ${b.name}.", mentions = listOf(a, b)),
                    Turn("Wie alt ist ${g.nominative}?", pronoun = g, gold = ASK))
            }
            "TYPE_DESCRIPTION" -> {
                val camera = entity(Kind.DEVICE, noun = "Kamera"); val router = entity(Kind.DEVICE, noun = "Router")
                if (rnd.nextBoolean()) listOf(mention(camera), mention(router), Turn("Und die Kamera?", typeNoun = "Kamera", gold = camera.id))
                else {
                    val second = entity(Kind.DEVICE, noun = "Kamera")
                    listOf(mention(camera), mention(router), mention(second), Turn("Und die Kamera?", typeNoun = "Kamera", gold = ASK))
                }
            }
            "NEW_SESSION" -> {
                val e = entity(anyKind())
                listOf(mention(e), pronoun(e), Turn("Neues Gespräch.", reset = true),
                    Turn("Wie alt ist ${e.gender.nominative}?", pronoun = e.gender, gold = ASK))
            }
            "PATTERN" -> {
                val kind = anyKind(); val a = entity(kind); val b = entity(kind, a.gender)
                listOf(mention(a), mention(b), mention(a), mention(b), pronoun(b, kindSpecific = false))
            }
            else -> error("unknown variant $variant")
        })
    }

    val VARIANTS = listOf("CONTINUE", "SWITCH", "RETURN", "LONG_DISTANCE", "TYPE_CONSTRAINT", "AMBIGUOUS",
        "TYPE_DESCRIPTION", "NEW_SESSION", "PATTERN")

    enum class Outcome { CORRECT, WRONG, UNNECESSARY_ASK }

    fun score(gold: String, answer: String): Outcome = when {
        answer == gold -> Outcome.CORRECT
        answer == ASK -> Outcome.UNNECESSARY_ASK
        else -> Outcome.WRONG
    }

    /** Runs one dialog as one session; returns (variant, outcome) per scored turn and the peak state bytes. */
    fun run(memory: Memory, dialog: Dialog, nanos: MutableList<Long>? = null): Pair<List<Outcome>, Int> {
        memory.reset()
        val outcomes = ArrayList<Outcome>()
        var peak = 0
        for (t in dialog.turns) {
            if (t.reset) { memory.reset(); continue }
            val start = System.nanoTime()
            val resolved = memory.resolve(t)
            memory.observe(t, resolved)
            nanos?.add(System.nanoTime() - start)
            peak = maxOf(peak, memory.bytes())
            if (t.gold != null) outcomes += score(t.gold, resolved ?: ASK)
        }
        return outcomes to peak
    }
}
