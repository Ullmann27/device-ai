package at.heinz.deviceai.learning

import java.util.Locale

data class NeuralDialogueReport(
    val before: NeuralEvaluation,
    val after: NeuralEvaluation,
    val holdout: NeuralEvaluation,
    val holdoutBefore: NeuralEvaluation,
    val training: NeuralTraining,
    val unfamiliarBefore: NeuralEvaluation,
    val unfamiliarAfter: NeuralEvaluation
)

data class NeuralPracticeReport(
    val attemptedSteps: Int,
    val completedSteps: Int,
    val accepted: Boolean,
    val reason: String,
    val trainingLossBefore: Double,
    val trainingLossAfter: Double,
    val heldoutBefore: NeuralEvaluation,
    val heldoutAfter: NeuralEvaluation,
    val unfamiliarBefore: NeuralEvaluation,
    val unfamiliarAfter: NeuralEvaluation
)

/** Measured, transactional outcome of one human-approved labeled lesson.
 * Prior examples exclude an explicitly corrected label for the same phrase.
 */
data class NeuralTeachingAudit(
    val training: NeuralTraining,
    val accepted: Boolean,
    val retainedBefore: Int,
    val retainedAfter: Int,
    val retainedTotal: Int,
    val priorLoss: Double?,
    val afterLoss: Double?,
    val newExampleScoreBefore: Double,
    val newExampleScoreAfter: Double,
    val reason: String
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
        val unfamiliarBefore = candidate.evaluate(examples(UNFAMILIAR_HOLDOUT))
        val result = candidate.train(train, 4000, cancelled = cancelled, onProgress = onProgress)
        val after = candidate.evaluate(train)
        val holdout = candidate.evaluate(examples(DEMO_HOLDOUT))
        if (!result.stoppedEarly && !cancelled()) { model = candidate; lessons = DEMO_TRAINING.toList() }
        return NeuralDialogueReport(before, after, holdout, holdoutBefore, result,
            unfamiliarBefore, candidate.evaluate(examples(UNFAMILIAR_HOLDOUT)))
    }
    @Synchronized fun teach(
        text: String, label: Int, approved: Boolean, cancelled: () -> Boolean = { false },
        onProgress: (NeuralLearningProgress) -> Unit = {}
    ): NeuralTraining = teachWithAudit(text, label, approved, cancelled, onProgress).training

    /**
     * Rehearse all previously approved examples, then test whether the proposed weights
     * learn the new lesson *without reducing* accuracy on the other approved lessons.
     * A deliberately corrected label supersedes the old label for that exact sentence.
     * Rejected attempts NEVER change the model or the persisted example list.
     */
    @Synchronized fun teachWithAudit(
        text: String, label: Int, approved: Boolean, cancelled: () -> Boolean = { false },
        onProgress: (NeuralLearningProgress) -> Unit = {}
    ): NeuralTeachingAudit {
        require(approved && label in 0..1 && words(text).isNotEmpty())
        require(lessons.size < 64 || lessons.any { it.first == text })
        val previous = lessons.filterNot { it.first == text }
        val nextLessons = previous + (text to label)
        val priorExamples = if (previous.isNotEmpty()) examples(previous) else null
        val priorBefore = priorExamples?.let(model::evaluate)
        val incoming = features(text)
        val incomingBefore = model.predict(incoming)
        val candidate = TinyNeuralNetwork.restore(model.save())
        val training = candidate.train(
            examples(nextLessons), 800, cancelled = cancelled, onProgress = onProgress
        )
        val priorAfter = priorExamples?.let(candidate::evaluate)
        val incomingAfter = candidate.predict(incoming)
        // 0.55/0.45 is a modest classification confidence gate, not semantic certainty.
        val learnedNewExample = if (label == 1) incomingAfter >= 0.55 else incomingAfter <= 0.45
        val retained = priorBefore == null || (
            priorAfter!!.correct >= priorBefore.correct &&
            priorAfter.loss <= priorBefore.loss + 0.075
        )
        val accepted = !training.stoppedEarly && !cancelled() && learnedNewExample && retained
        val reason = when {
            training.stoppedEarly || cancelled() -> "Abgebrochen oder Zeitlimit; alter Stand erhalten"
            !learnedNewExample -> "Neuer Satz noch nicht zuverlässig als Gruß/Frage erkannt"
            !retained -> "Bestehende bestätigte Sprachbeispiele würden schlechter erkannt"
            else -> "Neuer Satz erkannt und bestätigte ältere Beispiele behalten"
        }
        if (accepted) { model = candidate; lessons = nextLessons }
        return NeuralTeachingAudit(
            training, accepted,
            priorBefore?.correct ?: 0, priorAfter?.correct ?: 0, previous.size,
            priorBefore?.loss, priorAfter?.loss,
            incomingBefore, incomingAfter, reason
        )
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
    @Synchronized fun lessonCount(): Int = lessons.size
    @Synchronized fun hasLesson(text: String): Boolean =
        lessons.any { (phrase, _) -> phrase.equals(text.trim(), ignoreCase = true) }

    /** Content fingerprint over explicitly approved examples only (not passwords, chat logs or URLs). */
    @Synchronized fun lessonFingerprint(): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        lessons.sortedWith(compareBy<Pair<String, Int>> { it.first }.thenBy { it.second }).forEach { (text, label) ->
            digest.update(label.toByte())
            digest.update(text.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Repeat a small bounded neural learning cycle using ONLY already approved lessons.
     * Candidate weights are discarded if tests regress or the cycle is cancelled.
     * This is not self-supervised web learning or a generative language model.
     */
    @Synchronized fun practice(
        steps: Int = 200,
        cancelled: () -> Boolean = { false },
        onProgress: (NeuralLearningProgress) -> Unit = {}
    ): NeuralPracticeReport {
        require(steps in 1..300)
        require(lessons.isNotEmpty()) { "Keine bestätigten neuronalen Beispiele vorhanden." }
        val candidate = TinyNeuralNetwork.restore(model.save())
        val trainingExamples = examples(lessons)
        val trainingBefore = candidate.evaluate(trainingExamples)
        val knownBefore = candidate.evaluate(examples(DEMO_HOLDOUT))
        val unfamiliarBefore = candidate.evaluate(examples(UNFAMILIAR_HOLDOUT))
        val result = candidate.train(
            trainingExamples, steps, maxMillis = 1_200,
            cancelled = cancelled, onProgress = onProgress
        )
        val trainingAfter = candidate.evaluate(trainingExamples)
        val knownAfter = candidate.evaluate(examples(DEMO_HOLDOUT))
        val unfamiliarAfter = candidate.evaluate(examples(UNFAMILIAR_HOLDOUT))
        val accepted = !result.stoppedEarly && !cancelled() &&
            trainingAfter.loss + 0.000001 < trainingBefore.loss &&
            knownAfter.correct >= knownBefore.correct &&
            knownAfter.loss <= knownBefore.loss + 0.02 &&
            unfamiliarAfter.correct >= unfamiliarBefore.correct &&
            unfamiliarAfter.loss <= unfamiliarBefore.loss + 0.02
        val reason = when {
            result.stoppedEarly || cancelled() -> "Abgebrochen oder Zeitlimit"
            !accepted -> "Keine sichere Verbesserung auf Trainings- und unabhängigen Prüfbeispielen"
            else -> "Trainingsverlust verbessert; unabhängige Tests nicht verschlechtert"
        }
        if (accepted) model = candidate
        return NeuralPracticeReport(
            steps, result.steps, accepted, reason,
            trainingBefore.loss, trainingAfter.loss,
            knownBefore, knownAfter, unfamiliarBefore, unfamiliarAfter
        )
    }

    /** OOD benchmark; never trains or modifies weights. Unknown word tokens by design. */
    @Synchronized fun unfamiliarEvaluation(): NeuralEvaluation =
        model.evaluate(examples(UNFAMILIAR_HOLDOUT))

    @Synchronized fun approvedExampleCount(): Int = lessons.size

    @Synchronized fun status(): String = "Neuronales Gesprächsexperiment: ${lessons.size}/64 Beispiele, ${model.updates} Gewichtsaktualisierungen. " +
        "32 Eingaben, 8 versteckte Neuronen, 1 Ausgabe. Kein Sprachmodell.\n" +
        "Neuron Demo · Neuron Sprachlabor · Neuron Vergleichslabor · Neuron üben · Neuron Härtetest · Neuron: Hallo · Neuron lerne: Servus => Gruß · Neuron zurücksetzen"
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
        // Frozen novel-vocabulary corpus. Deliberately no tokens from DEMO_TRAINING.
        val UNFAMILIAR_HOLDOUT = listOf(
            "moin" to 0, "grüß gott" to 0, "tachchen" to 0,
            "willkommen zurück" to 0, "schönen abend" to 0, "na ihr" to 0,
            "woher stammt silizium" to 1, "warum leuchten sterne" to 1,
            "welche zeit herrscht" to 1, "wann beginnt sommer" to 1,
            "wo liegt graz" to 1, "wieviel kostet brot" to 1
        )
        fun accepts(text: String): Boolean {
            val key = text.trim().lowercase(Locale.ROOT)
            return key == "neuron" || key.startsWith("neuron:") || key.startsWith("neuron ") ||
                key in setOf("neuronen experiment", "neuronenexperiment", "neuron experiment", "neuronen demo")
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

enum class NeuralCommandKind { STATUS, DEMO, PREDICT, TEACH, RESET, UNFAMILIAR_TEST, PRACTICE, STRUCTURE_LAB, COMPARE_LAB }
data class NeuralCommand(val kind: NeuralCommandKind, val text: String = "", val label: Int = 0)
object NeuralCommandParser {
    fun parse(input: String): NeuralCommand? {
        if (input.length > 400) return null
        val text = input.trim()
        val low = text.lowercase(Locale.ROOT)
        return when {
            low == "neuron" || low == "neuron status" -> NeuralCommand(NeuralCommandKind.STATUS)
            low in setOf("neuron demo", "neuronen experiment", "neuronenexperiment",
                "neuron experiment", "neuronen demo") -> NeuralCommand(NeuralCommandKind.DEMO)
            low == "neuron vergleichslabor" || low == "neuron vergleich" ->
                NeuralCommand(NeuralCommandKind.COMPARE_LAB)
            low == "neuron sprachlabor" || low == "neuron strukturtest" ->
                NeuralCommand(NeuralCommandKind.STRUCTURE_LAB)
            low == "neuron üben" || low == "neuron wiederholen" ->
                NeuralCommand(NeuralCommandKind.PRACTICE)
            low == "neuron härtetest" || low == "neuron generalisierung" ||
                low == "neuron ood" -> NeuralCommand(NeuralCommandKind.UNFAMILIAR_TEST)
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
