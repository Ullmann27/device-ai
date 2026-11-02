package at.heinz.deviceai.intelligence.maxultra

import kotlin.random.Random

/**
 * CTX2 corpus (robustness of CTX1 to the owner's own phrasings), pre-registered in
 * docs/research/CTX2-FACT-PHRASING-PREREG-20261024.md: the CTX1 dialogs, but every owner fact that introduces a person,
 * a pet or an object is stored in another phrasing, some merged with an attribute ("Max ist mein Bruder und wohnt in
 * Graz."). Gold sets follow the rewritten facts. TRAIN/DEV use the KNOWN phrasings, TEST only the UNSEEN ones.
 */
object Ctx2Corpus {
    private val RELATION = Regex("^(\\p{Lu}[\\p{L}-]+) ist (mein|meine) (\\p{Lu}[\\p{L}-]+)\\.$")
    private val SPECIES = Regex("^(Unser|Unsere) (\\p{Lu}[\\p{L}-]+) heißt (\\p{Lu}[\\p{L}-]+)\\.$")
    private val OBJECT = Regex("^(Mein|Meine) (\\p{Lu}[\\p{L}-]+) (ist .+ Jahre alt|hat die Farbe .+)\\.$")

    private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

    /** Article of the noun in the accusative / nominative, from the gender the possessive shows (unser/mein = m or n). */
    private fun petGender(det: String, species: String, known: Map<String, Ctx1Corpus.Gender>) =
        known[species] ?: if (det.endsWith("e")) Ctx1Corpus.Gender.F else Ctx1Corpus.Gender.M

    private class Styles(val unseen: Boolean, val rnd: Random, val genders: Map<String, Ctx1Corpus.Gender>) {
        /** Rewrites one dialog's facts; returns old fact -> new fact (two old facts may map to one merged fact). */
        fun restyle(facts: List<String>): Map<String, String> {
            val out = LinkedHashMap<String, String>()
            for (f in facts) {
                RELATION.find(f)?.let { m ->
                    val (name, det, rel) = m.destructured
                    val g = if (det == "meine") "f" else "m"
                    val city = facts.firstOrNull { it.startsWith("$name wohnt in ") }
                    val age = facts.firstOrNull { it.startsWith("$name ist ") && it.endsWith(" Jahre alt.") }
                    val style = rnd.nextInt(3)
                    if (!unseen) when (style) {
                        0 -> out[f] = "${cap(det)} $rel heißt $name."
                        1 -> if (city != null) {
                            val merged = "$name ist $det $rel und ${city.removePrefix("$name ").removeSuffix(".")}."
                            out[f] = merged; out[city] = merged
                        } else out[f] = "${cap(det)} $rel heißt $name."
                        else -> out[f] = "Ich habe ${if (g == "f") "eine" else "einen"} $rel, ${if (g == "f") "sie" else "er"} heißt $name."
                    } else when (style) {
                        0 -> if (city != null) {
                            val merged = "$name, $det $rel, ${city.removePrefix("$name ")}"
                            out[f] = merged; out[city] = merged
                        } else out[f] = "Das ist $name, $det $rel."
                        1 -> if (age != null) {
                            val merged = "${cap(det)} $rel $name ${age.removePrefix("$name ")}"
                            out[f] = merged; out[age] = merged
                        } else out[f] = "Das ist $name, $det $rel."
                        else -> out[f] = "Das ist $name, $det $rel."
                    }
                }
                SPECIES.find(f)?.let { m ->
                    val (det, species, name) = m.destructured
                    val g = petGender(det, species, genders)
                    val article = when (g) { Ctx1Corpus.Gender.F -> "eine"; Ctx1Corpus.Gender.N -> "ein"; Ctx1Corpus.Gender.M -> "einen" }
                    out[f] = if (!unseen) {
                        if (rnd.nextBoolean()) "Wir haben $article $species namens $name." else "$name ist ${det.lowercase()} $species."
                    } else {
                        val nominative = if (g == Ctx1Corpus.Gender.F) "eine" else "ein"
                        if (rnd.nextBoolean()) "Bei uns lebt $nominative $species namens $name." else "$name ist das Haustier der Familie, $nominative $species."
                    }
                }
                OBJECT.find(f)?.let { m ->
                    val (det, noun, rest) = m.destructured
                    val g = genders[noun] ?: if (det == "Meine") Ctx1Corpus.Gender.F else Ctx1Corpus.Gender.N
                    val definite = when (g) { Ctx1Corpus.Gender.F -> "Die"; Ctx1Corpus.Gender.N -> "Das"; Ctx1Corpus.Gender.M -> "Der" }
                    out[f] = if (!unseen) "$definite $noun $rest." else "${if (g == Ctx1Corpus.Gender.F) "Unsere" else "Unser"} $noun $rest."
                }
            }
            return out
        }
    }

    /** Grammatical gender of the species and object nouns of both lexicons (the generator knows them; the routine does not). */
    private val GENDERS = mapOf(
        "Hund" to Ctx1Corpus.Gender.M, "Katze" to Ctx1Corpus.Gender.F, "Pferd" to Ctx1Corpus.Gender.N, "Hamster" to Ctx1Corpus.Gender.M,
        "Schildkröte" to Ctx1Corpus.Gender.F, "Kaninchen" to Ctx1Corpus.Gender.N, "Wellensittich" to Ctx1Corpus.Gender.M,
        "Ziege" to Ctx1Corpus.Gender.F, "Meerschweinchen" to Ctx1Corpus.Gender.N, "Papagei" to Ctx1Corpus.Gender.M,
        "Ratte" to Ctx1Corpus.Gender.F, "Pony" to Ctx1Corpus.Gender.N,
        "Auto" to Ctx1Corpus.Gender.N, "Fahrrad" to Ctx1Corpus.Gender.N, "Laptop" to Ctx1Corpus.Gender.M,
        "Waschmaschine" to Ctx1Corpus.Gender.F, "Handy" to Ctx1Corpus.Gender.N, "Fernseher" to Ctx1Corpus.Gender.M,
        "Motorrad" to Ctx1Corpus.Gender.N, "Kaffeemaschine" to Ctx1Corpus.Gender.F, "Rasenmäher" to Ctx1Corpus.Gender.M,
        "Tablet" to Ctx1Corpus.Gender.N, "Nähmaschine" to Ctx1Corpus.Gender.F, "Kühlschrank" to Ctx1Corpus.Gender.M)

    fun split(split: Ctx1Corpus.Split, seed: Long, perFamily: Int): List<Ctx1Corpus.Dialog> {
        val base = Ctx1Corpus.split(if (split == Ctx1Corpus.Split.TEST) Ctx1Corpus.Split.TEST else Ctx1Corpus.Split.TRAIN, seed, perFamily)
        val styles = Styles(split == Ctx1Corpus.Split.TEST, Random(seed * 31 + 7), GENDERS)
        return base.map { d ->
            val map = styles.restyle(d.facts)
            val facts = d.facts.map { map[it] ?: it }.distinct()
            val turns = d.turns.map { t ->
                val g = t.gold
                Ctx1Corpus.Turn(t.text, if (g is Ctx1Corpus.Memory) Ctx1Corpus.Memory(g.facts.map { map[it] ?: it }.toSet()) else g)
            }
            Ctx1Corpus.Dialog("ctx2-${split.name.lowercase()}-" + d.id.substringAfter('-'), d.family, facts, turns)
        }
    }
}
