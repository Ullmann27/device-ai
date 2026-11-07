package at.heinz.deviceai.learning

import kotlin.math.tanh

/**
 * Resonanz: experimenteller 16-dimensionaler Konditionierungsvektor.
 *
 * Der bestehende CDX1-Decoder wurde auf reinen SWB1-Eingaben trainiert.
 * Deshalb NICHT für bestehende CDX1-Checkpoints einsetzen, bevor ein
 * getrenntes Training mit unabhängigem Audit einen Gewinn bestätigt.
 *
 * Schreibt keine Modelle, führt keine Geräteaktionen aus.
 */
object ContinuumBimodalConditioner {
    fun build16dCondition(
        swb1: TrainableSubwordEncoder,
        cre1: Cre1InferenceEncoder,
        evidence: String,
        question: String
    ): DoubleArray {
        require(evidence.isNotBlank() && question.isNotBlank())
        require(evidence.length <= 160 && question.length <= 160)
        val lexical = swb1.representation(evidence + "\n" + question)
        val query = cre1.embed(question, "q")
        val memory = cre1.embed(evidence, "m")
        return combine(lexical, query, memory)
    }

    /** Pure deterministic kernel for offline tests and cross-runtime parity. */
    fun combine(
        lexical: DoubleArray,
        query: DoubleArray,
        memory: DoubleArray
    ): DoubleArray {
        require(lexical.size == 16 && query.size == 32 && memory.size == 32)
        require(lexical.all { it.isFinite() && it in -1.0..1.0 })
        require(query.all { it.isFinite() && it in -1.0..1.0 })
        require(memory.all { it.isFinite() && it in -1.0..1.0 })

        return DoubleArray(16) { channel ->
            if (channel < 8) {
                tanh((lexical[2 * channel] + lexical[2 * channel + 1]) * 0.5)
            } else {
                val group = channel - 8
                var correlation = 0.0
                for (part in 0 until 4) {
                    val index = group * 4 + part
                    correlation += query[index] * memory[index]
                }
                tanh(2.5 * correlation)
            }
        }.also { result ->
            check(result.all { it.isFinite() && it in -1.0..1.0 })
        }
    }
}
