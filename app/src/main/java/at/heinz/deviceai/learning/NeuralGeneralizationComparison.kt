package at.heinz.deviceai.learning

import java.util.Locale

/**
 * Read-only, independently seeded research comparison.
 * All datasets are intentionally small, authored synthetic fixtures, NOT evidence
 * of general German understanding. No production model or user record is updated.
 *
 * Factorial design: original vs broader example coverage, crossed with
 * coarse first-letter vs character-bigram features.
 * This separates more data from a changed feature representation.
 */
object NeuralGeneralizationComparison {
    enum class Encoder { FIRST_LETTER, CHAR_BIGRAM }

    data class Trial(
        val encoder: Encoder,
        val examples: Int,
        val train: NeuralEvaluation,
        val familiar: NeuralEvaluation,
        val unfamiliar: NeuralEvaluation,
        val counterexamples: NeuralEvaluation,
        val fresh: NeuralEvaluation,
        val steps: Int,
        val stoppedEarly: Boolean,
        val unfamiliarOverlapTokens: Int
    )

    private const val STEPS = 3000
    private const val FEATURE_COUNT = 32

    // New approved-by-developer synthetic fixtures; never sourced from user
    // conversations or web pages. No example sentence duplicates the test pools.
    val DIVERSE_TRAINING: List<Pair<String, Int>> = listOf(
        "liebe grüße an euch" to 0,
        "wünsche einen freundlichen tag" to 0,
        "frohen feierabend miteinander" to 0,
        "herzlichen empfang" to 0,
        "einen angenehmen morgengruß" to 0,
        "schön dich wiederzusehen" to 0,
        "willkommen mein freund" to 0,
        "wunderbaren sonntag euch" to 0,
        "servus an alle" to 0,
        "guten abend zusammen" to 0,
        "beste grüße" to 0,
        "hey leute schön euch zu sehen" to 0,
        "ist das eine gute idee" to 1,
        "hast du eine antwort" to 1,
        "kannst du zuhören" to 1,
        "dürfte das klappen" to 1,
        "wäre dies möglich" to 1,
        "magst du kaffee" to 1,
        "soll ich beginnen" to 1,
        "können wir reden" to 1,
        "möchtest du einen tipp" to 1,
        "gibt es einen plan" to 1,
        "brauchst du hilfe" to 1,
        "stimmt diese erklärung" to 1
    )

    // Newly authored control group with question forms, greetings and punctuation
    // variants; no exact training text occurs here. It becomes an exploratory
    // test after first use and MUST NOT be presented as external real-world proof.
    val FRESH_CONTROL: List<Pair<String, Int>> = listOf(
        "Sollte ich morgen fahren" to 1,
        "Würdest du das empfehlen" to 1,
        "Kann man dort parken" to 1,
        "Brauchst du noch etwas" to 1,
        "Was wäre anders" to 1,
        "Gibt es Alternativen" to 1,
        "Musst du jetzt gehen" to 1,
        "Dürfen wir starten" to 1,
        "Ist morgen Feiertag" to 1,
        "Weiß jemand Bescheid" to 1,
        "Herzlichste Grüße aus der Ferne" to 0,
        "Guten Start in die Woche" to 0,
        "Einen sonnigen Gruß" to 0,
        "Hi ihr Lieben" to 0,
        "Wünsche dir Glück" to 0,
        "Frohe Feiertage miteinander" to 0,
        "Morgen allerseits" to 0,
        "Beste Wünsche" to 0,
        "Hallo zurück" to 0,
        "Tach in die Runde" to 0
    )

    private fun tokens(text: String): List<String> =
        text.lowercase(Locale.GERMAN)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotEmpty() }.take(48)

    /** No rules for 'question' or 'greeting' labels live in this encoder. */
    fun features(text: String, encoder: Encoder): List<Double> {
        require(text.length in 1..320)
        if (encoder == Encoder.FIRST_LETTER) return NeuralTransferExperiment.features(text)
        val words = tokens(text)
        val values = DoubleArray(FEATURE_COUNT)
        val first = words.firstOrNull()
        if (first != null) {
            // First two characters distinguish e.g. wie/was/willkommen/wunderbar.
            val prefix = first.take(2)
            values[(prefix.hashCode() and Int.MAX_VALUE) % 12] = 1.0
            words.forEach { word ->
                val padded = "^" + word + "$"
                for (i in 0 until padded.length - 1) {
                    val pair = padded.substring(i, i + 2)
                    values[12 + ((pair.hashCode() and Int.MAX_VALUE) % 15)] = 1.0
                }
            }
        }
        values[27] = if (text.trimEnd().endsWith("?")) 1.0 else 0.0
        values[28] = if (words.size == 1) 1.0 else 0.0
        values[29] = if (words.size >= 3) 1.0 else 0.0
        values[30] = if (words.size >= 5) 1.0 else 0.0
        values[31] = if (first != null && first.length >= 7) 1.0 else 0.0
        return values.toList()
    }

    private fun lexicalOverlap(training: List<Pair<String, Int>>,
                               holdout: List<Pair<String, Int>>): Int {
        val known = training.flatMap { tokens(it.first) }.toSet()
        return holdout.flatMap { tokens(it.first) }.toSet().intersect(known).size
    }

    fun run(
        cancelled: () -> Boolean = { false },
        onProgress: (NeuralLearningProgress) -> Unit = {}
    ): List<Trial> {
        val stages = listOf(
            Encoder.FIRST_LETTER to false,
            Encoder.FIRST_LETTER to true,
            Encoder.CHAR_BIGRAM to false,
            Encoder.CHAR_BIGRAM to true
        )
        val results = mutableListOf<Trial>()
        stages.forEachIndexed { index, (encoder, diverse) ->
            if (cancelled()) return results
            val corpus = if (diverse) NeuralDialogue.DEMO_TRAINING + DIVERSE_TRAINING
                         else NeuralDialogue.DEMO_TRAINING
            fun examples(items: List<Pair<String, Int>>) =
                items.map { (s, label) -> NeuralExample(features(s, encoder), label) }
            val net = TinyNeuralNetwork(FEATURE_COUNT, seed = 42)
            val process = net.train(examples(corpus), STEPS, maxMillis = 3500,
                cancelled = cancelled, onProgress = { progress ->
                    onProgress(progress.copy(
                        step = index * STEPS + progress.step,
                        total = stages.size * STEPS
                    ))
                })
            results += Trial(
                encoder = encoder, examples = corpus.size,
                train = net.evaluate(examples(corpus)),
                familiar = net.evaluate(examples(NeuralDialogue.DEMO_HOLDOUT)),
                unfamiliar = net.evaluate(examples(NeuralDialogue.UNFAMILIAR_HOLDOUT)),
                counterexamples = net.evaluate(examples(NeuralTransferExperiment.CHALLENGE)),
                fresh = net.evaluate(examples(FRESH_CONTROL)),
                steps = process.steps, stoppedEarly = process.stoppedEarly,
                unfamiliarOverlapTokens = lexicalOverlap(
                    corpus, NeuralDialogue.UNFAMILIAR_HOLDOUT)
            )
            if (process.stoppedEarly) return results
        }
        return results
    }
}
