package at.heinz.deviceai.learning

import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class Cre1InferenceEncoderTest {
    @Test fun rejectsMalformedSnapshots() {
        assertThrows(IllegalArgumentException::class.java) {
            Cre1InferenceEncoder.restore(byteArrayOf(0x43, 0x52, 0x45, 0x31))
        }
        assertThrows(IllegalArgumentException::class.java) { Cre1InferenceEncoder.tokens("") }
        assertThrows(IllegalArgumentException::class.java) { Cre1InferenceEncoder.tokens("x".repeat(2049)) }
        val weights = Cre1InferenceEncoder.features("Geburtsdatum von Anna")
        assertTrue(weights.size > 2)
        assertEquals(1.0, weights.sumOf { it.weight }, 1e-11)
        assertEquals(weights.size, weights.map { it.id }.distinct().size)
    }

    @Test fun trainedNodeCheckpointMatchesKotlinFeaturesAndScores() {
        val dirName = System.getenv("CONTINUUM_CRE1_PARITY_DIR")
        assumeTrue("CRE1 fixture supplied by CI", !dirName.isNullOrBlank())
        val dir = File(dirName!!)
        val original = File(dir, "node-cre1.cre1").readBytes()
        assertEquals(Cre1InferenceEncoder.SNAPSHOT_LENGTH, original.size)
        val model = Cre1InferenceEncoder.restore(original)
        assertEquals(42L, model.seed)
        assertTrue(model.steps > 0)
        val rows = File(dir, "node-scores.tsv").readLines().filter(String::isNotEmpty)
        assertEquals(7, rows.size)
        val computed = rows.map { line ->
            val fields = line.split('\t')
            assertEquals(7, fields.size)
            val query = fields[0]
            val memory = fields[1]
            val queryVec = model.embed(query, "q")
            val memoryVec = model.embed(memory, "m")
            val actual = doubleArrayOf(model.score(query, memory), queryVec[0],
                queryVec[31], memoryVec[0], memoryVec[31])
            for (i in actual.indices) {
                assertEquals("CRE1 value " + i + " for " + query, fields[i + 2].toDouble(), actual[i], 1e-8)
            }
            listOf(query, memory).plus(actual.map { it.toString() }).joinToString("\t")
        }
        File(dir, "kotlin-scores.tsv").writeText(computed.joinToString("\n", postfix = "\n"))
        val source = File(dir, "node-features.tsv").readLines().filter(String::isNotEmpty)
        assertEquals(rows.size, source.size)
        for (line in source) {
            val cells = line.split('\t')
            assertEquals(3, cells.size)
            assertEquals(cells[1], Cre1InferenceEncoder.tokens(cells[0]).joinToString(","))
            val expected = cells[2].split(',').map {
                val parts = it.split(':')
                parts[0].toInt() to parts[1].toDouble()
            }
            val actual = Cre1InferenceEncoder.features(cells[0])
            assertEquals(expected.map { it.first }, actual.map { it.id })
            for (i in actual.indices) {
                assertEquals("Feature " + actual[i].id, expected[i].second, actual[i].weight, 1e-10)
            }
        }
        val damaged = original.copyOf()
        damaged[damaged.size / 2] = (damaged[damaged.size / 2].toInt() xor 1).toByte()
        assertThrows(IllegalArgumentException::class.java) { Cre1InferenceEncoder.restore(damaged) }
    }
}
