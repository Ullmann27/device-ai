package at.heinz.deviceai.learning

import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln

data class OnlinePrediction(val label: String, val score: Double)
data class OnlineEvaluation(val correct: Int, val total: Int, val abstained: Int)

/** Incremental multinomial NB with Laplace smoothing. Scores are NOT calibrated probabilities.
 * Bounded vocabulary, six labels, 256 approved examples. Suggestions cannot issue tool calls.
 */
class OnlineNaiveBayes {
    private val documents = linkedMapOf<String, Int>()
    private val counts = linkedMapOf<String, MutableMap<String, Int>>()
    private val vocabulary = linkedSetOf<String>()
    private fun tokens(text: String): List<String> {
        require(text.length <= 320)
        return text.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length in 2..32 }.take(32)
    }
    @Synchronized fun learn(text: String, label: String, approved: Boolean) {
        require(approved && label.matches(Regex("[a-z][a-z0-9_]{0,47}")))
        val words = tokens(text)
        require(words.isNotEmpty() && documents.values.sum() < 256)
        require(label in documents || documents.size < 6)
        require((vocabulary + words).size <= 256)
        vocabulary.addAll(words)
        documents[label] = (documents[label] ?: 0) + 1
        val target = counts.getOrPut(label) { linkedMapOf() }
        words.forEach { target[it] = (target[it] ?: 0) + 1 }
    }
    @Synchronized fun predict(text: String): OnlinePrediction? {
        val all = tokens(text)
        val words = all.filter { it in vocabulary }
        if (documents.size < 2 || words.isEmpty() || words.size * 2 < all.size) return null
        val totalDocs = documents.values.sum()
        val scores = documents.map { (label, n) ->
            val bag = counts.getValue(label)
            val total = bag.values.sum() + vocabulary.size
            label to (ln(n.toDouble() / totalDocs) + words.sumOf { ln(((bag[it] ?: 0) + 1.0) / total) })
        }.sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first })
        val best = scores.first()
        val score = 1.0 / scores.sumOf { exp(it.second - best.second) }
        return if (score + 1e-12 < 0.60) null else OnlinePrediction(best.first, score)
    }
    @Synchronized fun evaluate(test: List<Pair<String, String>>): OnlineEvaluation {
        require(test.size in 1..128)
        val predictions = test.map { predict(it.first) }
        return OnlineEvaluation(test.indices.count { predictions[it]?.label == test[it].second },
            test.size, predictions.count { it == null })
    }
    @Synchronized fun reset() { documents.clear(); counts.clear(); vocabulary.clear() }
    @Synchronized fun save(): ByteArray = LearningSnapshot.encode { out ->
        out.writeInt(0x444e4231)
        out.writeInt(documents.size)
        documents.toSortedMap().forEach { (label, docs) ->
            out.writeUTF(label); out.writeInt(docs)
            val bag = counts.getValue(label)
            out.writeInt(bag.size)
            bag.toSortedMap().forEach { (word, n) -> out.writeUTF(word); out.writeInt(n) }
        }
    }
    companion object {
        fun restore(bytes: ByteArray): OnlineNaiveBayes = LearningSnapshot.decode(bytes) { input ->
            require(input.readInt() == 0x444e4231)
            val model = OnlineNaiveBayes()
            val labels = input.readInt().also { require(it in 0..6) }
            repeat(labels) {
                val label = input.readUTF()
                require(label.matches(Regex("[a-z][a-z0-9_]{0,47}")) && label !in model.documents)
                val docs = input.readInt().also { require(it in 1..256) }
                val size = input.readInt().also { require(it in 1..256) }
                val bag = linkedMapOf<String, Int>()
                repeat(size) {
                    val word = input.readUTF()
                    require(word.length in 2..32 && model.tokens(word) == listOf(word) && word !in bag)
                    val n = input.readInt().also { require(it in 1..8192) }
                    bag[word] = n
                }
                require(bag.values.sum() in docs..docs * 32)
                model.documents[label] = docs
                model.counts[label] = bag
                model.vocabulary.addAll(bag.keys)
            }
            require(model.documents.values.sum() <= 256 && model.vocabulary.size <= 256)
            model
        }
    }
}
