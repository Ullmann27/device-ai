package at.heinz.deviceai.learning.span

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class NaturalLocationLabComparisonTest {
    private fun checkpoint() = listOf("src/main/assets/", "app/src/main/assets/")
        .map { File(it + NaturalLocationLabComparison.MODEL_ASSET) }.firstOrNull { it.isFile }?.readBytes()
        ?: requireNotNull(javaClass.classLoader?.getResourceAsStream(NaturalLocationLabComparison.MODEL_ASSET)).use { it.readBytes() }

    @Test fun checkpointReaderIsExactAndBounded() {
        val bytes = checkpoint()
        assertArrayEquals(bytes, NaturalLocationLabComparison.readCheckpoint(bytes.inputStream()))
        assertTrue(runCatching { NaturalLocationLabComparison.readCheckpoint(bytes.dropLast(1).toByteArray().inputStream()) }.isFailure)
        assertTrue(runCatching { NaturalLocationLabComparison.readCheckpoint((bytes + byteArrayOf(0)).inputStream()) }.isFailure)
    }

    @Test fun packagedFrozenModelAnswersSwappedPersonsWithExactVerifiedSpans() {
        val bytes = checkpoint()
        for ((source, expected) in listOf("Eva ist in Bern. Otto ist in Kiel." to "Bern",
            "Otto ist in Bern. Eva ist in Kiel." to "Kiel")) {
            val result = NaturalLocationLabComparison.compare(source, "Wo ist Eva?", bytes)
            assertEquals(expected, (result.rule as EvidenceBoundNaturalLocationProbe.Decision.Found).text)
            assertEquals(expected, (result.learned as EvidenceBoundNaturalLocationProbe.Decision.Found).text)
            assertEquals("c28c44c21e6b79aea5d13254760a0a782714e662294b366826d9d8c940697e92", result.modelSha256)
            assertTrue(NaturalLocationLabComparison.render(result).contains("Keine CDX1-Reparatur"))
        }
    }

    @Test fun neitherRuleNorLearnedResultCanOverrideMissingNegationOrConflict() {
        for (source in listOf("Otto ist in Kiel.", "Eva ist nicht in Bern.", "Eva ist in Bern. Eva ist in Kiel.")) {
            val result = NaturalLocationLabComparison.compare(source, "Wo ist Eva?", checkpoint())
            assertTrue(result.rule is EvidenceBoundNaturalLocationProbe.Decision.Abstained)
            assertTrue(result.learned is EvidenceBoundNaturalLocationProbe.Decision.Abstained)
        }
    }
}
