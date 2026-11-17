package at.heinz.deviceai.learning

/**
 * Synthetic multi-turn dialogues with mechanically known answers.
 * No owner facts, private chat text, Internet content or model-generated labels.
 * Stage IDs never overlap and the fixed audit uses a separate namespace.
 */
object ContinuumAutoDialogueCurriculum {
    const val VERSION = "auto-dialogue-v1"
    const val MAX_STAGES = 64
    const val TRAIN_COUNT = 16
    const val NEW_AUDIT_COUNT = 8

    data class Batch(
        val training: List<Cre1ResearchExample>,
        val audit: List<Cre1ResearchExample>
    )

    private val cities = listOf("Wien", "Graz", "Linz", "Salzburg", "Innsbruck", "Villach")
    private val colors = listOf("blau", "grün", "rot", "silber", "violett", "gelb")
    private val tools = listOf("Hammer", "Zange", "Lupe", "Bohrer", "Pinsel", "Lineal")
    private val energies = listOf("Solarstrom", "Windstrom", "Akkustrom", "Wasserkraft", "Netzstrom", "Brennstoffzelle")

    private fun example(id: String, variant: Int): Cre1ResearchExample {
        val n = variant / 4
        val actual = n % cities.size
        val question: String
        val options: List<String>
        when (variant % 4) {
            0 -> {
                val correct = cities[actual]
                question = "Person A: Im Datenblatt steht, dass Roboter " + id + " in " + correct +
                    " arbeitet. Person B: Verstanden. Person A: In welcher Stadt arbeitet er?"
                options = listOf(correct, cities[(actual + 1) % cities.size],
                    cities[(actual + 2) % cities.size]).map { "Roboter " + id + " arbeitet in " + it + "." }
            }
            1 -> {
                val correct = colors[actual]
                question = "Person A: Roboter " + id + " trägt die Farbe " + correct +
                    ". Person B: Das habe ich gelesen. Person A: Welche Farbe hat dieser Roboter?"
                options = listOf(correct, colors[(actual + 1) % colors.size],
                    colors[(actual + 2) % colors.size]).map { "Roboter " + id + " ist " + it + "." }
            }
            2 -> {
                val correct = tools[actual]
                question = "Person A: Bei Roboter " + id + " ist als Werkzeug " + correct +
                    " eingetragen. Person B: Notiert. Person A: Was benutzt er?"
                options = listOf(correct, tools[(actual + 1) % tools.size],
                    tools[(actual + 2) % tools.size]).map { "Roboter " + id + " benutzt " + it + "." }
            }
            else -> {
                val correct = energies[actual]
                question = "Person A: Roboter " + id + " wird mit " + correct +
                    " betrieben. Person B: Ich habe die Angabe. Person A: Welche Energie nutzt er?"
                options = listOf(correct, energies[(actual + 1) % energies.size],
                    energies[(actual + 2) % energies.size]).map { "Roboter " + id + " nutzt " + it + "." }
            }
        }
        return Cre1ResearchExample(question, options)
    }

    fun batch(stage: Int): Batch {
        require(stage in 0 until MAX_STAGES)
        val start = stage * 64
        val train = (0 until TRAIN_COUNT).map { index ->
            example("S" + (start + index).toString().padStart(4, '0'), index)
        }
        val newAudit = (0 until NEW_AUDIT_COUNT).map { index ->
            example("S" + (start + 32 + index).toString().padStart(4, '0'), index + 16)
        }
        val fixedAudit = (0 until 8).map { index ->
            example("F" + index.toString().padStart(3, '0'), index + 24)
        }
        val audit = newAudit + fixedAudit
        require(train.size == TRAIN_COUNT && audit.size == NEW_AUDIT_COUNT + fixedAudit.size)
        require((train + audit).map { it.query }.distinct().size == train.size + audit.size)
        val trainCandidates = train.flatMap { it.candidates }.toSet()
        require(audit.none { item -> item.candidates.any(trainCandidates::contains) })
        return Batch(train, audit)
    }
}
