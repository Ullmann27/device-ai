package at.heinz.deviceai.learning

import java.util.Locale

data class NeuralDialogueReport(
    val before: NeuralEvaluation,
    val after: NeuralEvaluation,
    val holdout: NeuralEvaluation,
    val holdoutBefore: NeuralEvaluation,
    val training: NeuralTraining
)

/** Experimental dialogue classification, NOT language generation or a factual authority.
 * Corpus is explicit, local and bounded. No chat logs/web text are implicitly learned.
 * Training is transactional: cancelled/partial training is never published.
 */
class NeuralDialogue {
    private var model = TinyNeuralNetwork(32, 42)
    private var lessons: List<Pair<String, Int>> = emptyList()
    private fun words(text: String): List<String> {
        require(text.length in 1..320)
        return text.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+"))
            .filter(String::isNotEmpty).take(48)
    }
    private fun features(text: String): List<Double> {
        val values = DoubleArray(32)
        words(text).forEach { values[(it.hashCode() and Int.MAX_VALUE) % values.size] = 1.0 }
        return values.toList()
    }
    private fun examples(data: List<Pair<String, Int>>) = data.map { NeuralExample(features(it.first), it.second) }

    @Synchronized fun trainDemo(
        approved: Boolean, cancelled: () -> Boolean = { false },
        onProgress: (NeuralLearningProgress) -> Unit = {}
    ): NeuralDialogueReport {
        require(approved)
        // Isolated reference experiment. Existing custom lessons require an explicit reset first.
        require(lessons.isEmpty()) { "Experiment zuerst ausdrücklich zurücksetzen; eigene Beispiele bleiben erhalten." }
        val candidate = TinyNeuralNetwork(32, 42)
        val train = examples(DEMO_TRAINING)
        val before = candidate.evaluate(train)
        val holdoutBefore = candidate.evaluate(examples(DEMO_HOLDOUT))
        val result = candidate.train(train, 4000, cancelled = cancelled, onProgress = onProgress)
        val after = candidate.evaluate(train)
        val holdout = candidate.evaluate(examples(DEMO_HOLDOUT))
        if (!result.stoppedEarly && !cancelled()) { model = candidate; lessons = DEMO_TRAINING.toList() }
        return NeuralDialogueReport(before, after, holdout, holdoutBefore, result)
    }
    @Synchronized fun teach(
        text: String, label: Int, approved: Boolean, cancelled: () -> Boolean = { false },
        onProgress: (NeuralLearningProgress) -> Unit = {}
    ): NeuralTraining {
        require(approved && label in 0..1 && words(text).isNotEmpty())
        require(lessons.size < 64 || lessons.any { it.first == text })
        // An explicit corrected label replaces the old label, never trains both accidentally.
        val nextLessons = lessons.filterNot { it.first == text } + (text to label)
        val candidate = TinyNeuralNetwork.restore(model.save())
        val result = candidate.train(examples(nextLessons), 800, cancelled = cancelled, onProgress = onProgress)
        if (!result.stoppedEarly && !cancelled()) { model = candidate; lessons = nextLessons }
        return result
    }
    @Synchronized fun reply(text: String): String {
        val inputWords = words(text)
        if (lessons.isEmpty()) return "Neuronales Experiment noch nicht trainiert. Öffne das Lernlabor oder schreibe „Neuron Demo“."
        val known = lessons.flatMap { words(it.first) }.toSet()
        if (inputWords.isEmpty() || inputWords.count { it in known } * 2 < inputWords.size)
            return "Neuronales Experiment: Wortschatz unbekannt; keine belastbare Zuordnung."
        val score = model.predict(features(text))
        if (lessons.map { it.second }.distinct().size < 2 || score in 0.2..0.8)
            return "Neuronales Experiment: unsicher. Bitte weitere Beispiele für Gruß und Frage beibringen."
        val label = if (score < 0.5) "Gruß" else "Frage"
        val response = if (label == "Gruß") "Hallo! Ich bin da." else "Ich erkenne möglicherweise eine Frage. Was möchtest du mir beibringen?"
        return "$response\nNeuronales Experiment: $label, Modellscore ${String.format(Locale.ROOT, "%.3f", if (score < 0.5) 1-score else score)}. " +
            "Die Zuordnung wurde neuronal gelernt; dieser Antworttext ist vorgegeben. Noch keine freie Sprachgenerierung."
    }
    @Synchronized fun status(): String = "Neuronales Gesprächsexperiment: ${lessons.size}/64 Beispiele, ${model.updates} Gewichtsaktualisierungen. " +
        "32 Eingaben, 8 versteckte Neuronen, 1 Ausgabe. Kein Sprachmodell.\n" +
        "Neuron Demo · Neuron: Hallo · Neuron lerne: Servus => Gruß · Neuron zurücksetzen"
    @Synchronized fun reset() { model = TinyNeuralNetwork(32, 42); lessons = emptyList() }
    @Synchronized fun save(): ByteArray = LearningSnapshot.encode { out ->
        out.writeInt(0x44444331)
        val state = model.save()
        out.writeInt(state.size); out.write(state)
        out.writeInt(lessons.size)
        lessons.forEach { (text, label) -> out.writeUTF(text); out.writeInt(label) }
    }
    companion object {
        // Authored synthetic examples; no private user data, downloads, or imported weights.
        val DEMO_TRAINING = listOf(
            "hallo" to 0, "servus" to 0, "hey" to 0, "guten morgen" to 0,
            "guten tag" to 0, "hallo device" to 0, "servus device" to 0, "hey device" to 0,
            "was kannst du" to 1, "wie geht es dir" to 1, "wer bist du" to 1, "was weißt du" to 1,
            "wie lernst du" to 1, "was bedeutet das" to 1, "wer kann helfen" to 1, "wie funktioniert das" to 1
        )
        val DEMO_HOLDOUT = listOf(
            "hallo guten tag" to 0, "servus guten morgen" to 0, "hey guten tag" to 0, "hallo servus" to 0,
            "was lernst du" to 1, "wie kannst du helfen" to 1, "wer weiß das" to 1, "wie geht das" to 1
        )
        fun accepts(text: String): Boolean {
            val key = text.trim().lowercase(Locale.ROOT)
            return key == "neuron" || key.startsWith("neuron:") || key.startsWith("neuron ")
        }
        fun restore(bytes: ByteArray): NeuralDialogue = LearningSnapshot.decode(bytes) { input ->
            require(input.readInt() == 0x44444331)
            val length = input.readInt().also { require(it in 36..4096 && it <= input.available()) }
            val saved = ByteArray(length).also(input::readFully)
            val model = TinyNeuralNetwork.restore(saved)
            require(model.inputSize == 32)
            val count = input.readInt().also { require(it in 0..64) }
            val lab = NeuralDialogue()
            val lessons = (0 until count).map {
                val text = input.readUTF()
                val label = input.readInt()
                require(label in 0..1 && lab.words(text).isNotEmpty())
                text to label
            }
            require(lessons.map { it.first }.distinct().size == lessons.size)
            require((count == 0) == (model.updates == 0))
            lab.model = model; lab.lessons = lessons
            lab
        }
    }
}

enum class NeuralCommandKind { STATUS, DEMO, PREDICT, TEACH, RESET }
data class NeuralCommand(val kind: NeuralCommandKind, val text: String = "", val label: Int = 0)
object NeuralCommandParser {
    fun parse(input: String): NeuralCommand? {
        if (input.length > 400) return null
        val text = input.trim()
        val low = text.lowercase(Locale.ROOT)
        return when {
            low == "neuron" || low == "neuron status" -> NeuralCommand(NeuralCommandKind.STATUS)
            low == "neuron demo" -> NeuralCommand(NeuralCommandKind.DEMO)
            low == "neuron zurücksetzen" -> NeuralCommand(NeuralCommandKind.RESET)
            low.startsWith("neuron:") -> text.substringAfter(':').trim().takeIf { it.length in 1..320 }
                ?.let { NeuralCommand(NeuralCommandKind.PREDICT, it) }
            low.startsWith("neuron lerne:") -> {
                val parts = text.substringAfter(':').split("=>").map(String::trim)
                if (parts.size != 2 || parts[0].length !in 3..320) null else {
                    val label = when (parts[1].lowercase(Locale.ROOT)) { "gruß", "gruss" -> 0; "frage" -> 1; else -> -1 }
                    if (label < 0) null else NeuralCommand(NeuralCommandKind.TEACH, parts[0], label)
                }
            }
            else -> null
        }
    }
}
