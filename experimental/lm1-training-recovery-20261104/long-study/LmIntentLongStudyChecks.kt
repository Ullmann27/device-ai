package at.heinz.deviceai.learning.lm

import java.io.File
import kotlin.math.abs
import kotlin.math.ln

/** Numerical and boundary checks for the new masked objective; no owner model is modified. */
object LmIntentLongStudyChecks {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 1)
        val tok = LmBpeTokenizer.deserialize(File(args[0]).readBytes())
        val a = LmIntentLongStudy.supervised(tok, "Auftrag: Test.\nAktion:", "0", 256)
        val b = LmIntentLongStudy.supervised(tok, "Auftrag: Eine andere Prüfung.\nAktion:", "3", 256)
        for ((s, label) in listOf(a to "0", b to "3")) {
            check(s.ids.all { it >= 0 })
            check(s.targets.count { it >= 0 } == 2)
            check(s.targets.take(s.answerIndex).all { it == -1 })
            check(s.targets[s.answerIndex] == tok.encode(label).single())
            check(s.targets.last() == tok.eos && s.ids.last() == tok.encode(label).single())
        }
        println("CHECK\tname=mask_alignment_and_complete_eos\tpass=true")
        val model = LmModel(LmParams.init(LmConfig(LmArch.MINGRU, tok.vocabSize, 8, 1, context = 256), 731L))
        val batch = listOf(a, b)
        val reference = batch.sumOf { s ->
            val prefix = s.ids.copyOfRange(0, s.answerIndex + 1)
            -ln(model.nextDistribution(prefix)[s.targets[s.answerIndex]].toDouble()) -
                ln(model.nextDistribution(s.ids)[tok.eos].toDouble())
        } / 4
        val before = model.params.mats.mapValues { it.value.data.copyOf() }
        val actual = LmIntentLongStudy.MaskedTrainer(model).use { it.step(batch, 0) }
        check(abs(actual.first - reference) < 0.00001) { "masked batch loss ${actual.first} != independent probability loss $reference" }
        check(actual.second.isFinite() && actual.second > 0)
        check(model.params.allFinite())
        check(model.params.mats.any { (name, m) -> !m.data.contentEquals(before.getValue(name)) })
        println("CHECK\tname=actual_masked_update_matches_probability_loss\tpass=true\tloss=${actual.first}\tgradientNorm=${actual.second}")
        val logits = Mat(5, 4, FloatArray(20) { it * .03f })
        val grad = Mat(5, 4); val tape = Tape()
        val loss = tape.crossEntropy(tape.param(logits, grad), intArrayOf(-1, -1, -1, 2, 1), 2)
        tape.backward(loss)
        check(grad.data.take(12).all { it == 0f })
        for (row in 3..4) check(abs((0..3).sumOf { grad[row, it].toDouble() }) < 1e-6)
        val index = 15; val original = logits.data[index]; val epsilon = .001f
        fun value(v: Float): Double {
            logits.data[index] = v
            val t = Tape(false)
            return t.crossEntropy(t.constant(logits), intArrayOf(-1, -1, -1, 2, 1), 2).value.data[0].toDouble()
        }
        val finiteDifference = (value(original + epsilon) - value(original - epsilon)) / (2 * epsilon)
        logits.data[index] = original
        check(abs(grad.data[index] - finiteDifference) < 0.0001)
        println("CHECK\tname=masked_gradient_finite_difference\tpass=true")
        fun result(text: String, tokens: Int, stop: LmRuntime.Stop) = LmRuntime.Result(text, tokens, 4, false, stop, 0)
        check(LmIntentLongStudy.exactAnswer(result("2", 1, LmRuntime.Stop.EOS), "2"))
        check(!LmIntentLongStudy.exactAnswer(result("2", 1, LmRuntime.Stop.MAX_TOKENS), "2"))
        check(!LmIntentLongStudy.exactAnswer(result("", 0, LmRuntime.Stop.EOS), "2"))
        check(!LmIntentLongStudy.exactAnswer(result(" 2", 2, LmRuntime.Stop.EOS), "2"))
        check(!LmIntentLongStudy.exactAnswer(result("3", 1, LmRuntime.Stop.EOS), "2"))
        println("CHECK\tname=strict_greedy_answer_boundary\tpass=true")
        check(runCatching { LmIntentLongStudy.supervised(tok, "x".repeat(200), "0", 8) }.isFailure)
        println("CHECK\tname=no_silent_context_truncation\tpass=true")
        println("CHECKS_RESULT\tpassed=5\tfailed=0\townerModelRead=false")
    }
}
