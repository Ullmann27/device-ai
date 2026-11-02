package at.heinz.deviceai.learning

import java.util.Locale

/**
 * Research-only, small structural feature encoder. Character-level shape hints allow
 * the SAME neuron to recognize some previously unseen words, but do not understand
 * their meanings. Features contain no pre-trained parameters or model weights.
 *
 * Explicitly isolated: this experiment never changes NeuralDialogue's saved weights,
 * training examples, model version, device rights or the owner's memories.
 */
object NeuralTransferExperiment {
    private const val SIZE = 32

    data class Result(
        val trained: NeuralEvaluation,
        val known: NeuralEvaluation,
        val unseenBefore: NeuralEvaluation,
        val unseenAfter: NeuralEvaluation,
        val challengeBefore: NeuralEvaluation,
        val challengeAfter: NeuralEvaluation,
        val training: NeuralTraining
    )

    // Separate test: yes/no questions often do NOT start with 'w', while some
    // greetings DO. This prevents the misleading claim that an initial-letter
    // shortcut is general German language understanding.
    val CHALLENGE = listOf(
        "Ist diese Aussage wahr" to 1,
        "Könntest du bitte helfen" to 1,
        "Darf ich fragen" to 1,
        "Kann das funktionieren" to 1,
        "Hast du Hunger" to 1,
        "Willkommen zu Hause" to 0,
        "Wunderschönen Nachmittag" to 0,
        "Mahlzeit zusammen" to 0,
        "Schön dich zu sehen" to 0,
        "Grüß dich" to 0
    )

    /**
     * Hash first-character shape into 24 slots, word identities into only five
     * additional slots and simple punctuation/token-count patterns into three.
     * Unlike the original whole-word-only vector, unseen words can share
     * structural signals. This is intentionally a falsifiable experiment.
     */
    fun features(text: String): List<Double> {
        require(text.length in 1..320)
        val words = text.lowercase(Locale.GERMAN)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotEmpty() }.take(48)
        val values = DoubleArray(SIZE)
        val first = words.firstOrNull()
        if (first != null) {
            values[(first.first().code * 7 and Int.MAX_VALUE) % 24] = 1.0
            words.forEach { word ->
                values[24 + (word.hashCode() and Int.MAX_VALUE) % 5] = 1.0
            }
        }
        values[29] = if (text.trimEnd().endsWith("?")) 1.0 else 0.0
        values[30] = if (words.size == 1) 1.0 else 0.0
        values[31] = if (words.size >= 4) 1.0 else 0.0
        return values.toList()
    }

    fun run(
        cancelled: () -> Boolean = { false },
        progress: (NeuralLearningProgress) -> Unit = {}
    ): Result {
        val candidate = TinyNeuralNetwork(SIZE, seed = 42)
        fun examples(items: List<Pair<String, Int>>) = items.map { (phrase, label) ->
            NeuralExample(features(phrase), label)
        }
        val train = examples(NeuralDialogue.DEMO_TRAINING)
        val known = examples(NeuralDialogue.DEMO_HOLDOUT)
        val unseen = examples(NeuralDialogue.UNFAMILIAR_HOLDOUT)
        val challenge = examples(CHALLENGE)
        val unfamiliarBefore = candidate.evaluate(unseen)
        val challengeBefore = candidate.evaluate(challenge)
        val result = candidate.train(train, steps = 4000, maxMillis = 2800,
            cancelled = cancelled, onProgress = progress)
        return Result(
            candidate.evaluate(train), candidate.evaluate(known),
            unfamiliarBefore, candidate.evaluate(unseen),
            challengeBefore, candidate.evaluate(challenge), result
        )
    }
}
