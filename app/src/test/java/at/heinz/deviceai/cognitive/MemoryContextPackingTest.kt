package at.heinz.deviceai.cognitive

import at.heinz.deviceai.learning.RecallHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryContextPackingTest {
    private fun compiler(budget: Int) =
        AiContextCompiler(AiContextPolicy(maxBytes = budget, includeToolCatalog = false), nowMillis = { 10L })

    private fun memory(id: String, content: String, at: Long = 1L) = MemoryRecord(id, MemoryType.SEMANTIC, content,
        ProvenanceType.USER_PROVIDED, 0.9, at, at, privacyClass = PrivacyClass.DEVICE_LOCAL_ONLY)

    @Test fun anOversizedGroupNoLongerRemovesEveryOtherMemory() {
        val big = memory("big", "zircon zircon zircon " + "Notiz ".repeat(400))
        val small = memory("small", "Der zircon Ring liegt in der Schublade.")
        val result = compiler(2_048).compile(ContextIntegrityFixture.input("Wo ist der zircon Ring?", listOf(big, small)))
        assertEquals(listOf("small"), result.includedMemoryIds)
        assertTrue(result.truncated)
        assertTrue(result.text.contains("Der zircon Ring liegt in der Schublade."))
        assertFalse(result.text.contains("Notiz Notiz"))
        assertTrue(result.text.toByteArray(Charsets.UTF_8).size <= 2_048)
    }

    @Test fun anOutdatedRecordIsRenderedAfterItsCurrentSuccessorWithMarkers() {
        val result = compiler(12_000).compile(ContextIntegrityFixture.input("Enns", listOf(
            memory("old", "Ute wohnte bis 2022 in Enns.", at = 1L), memory("new", "Ute wohnt seit 2023 in Tulln.", at = 2L))))
        assertEquals(listOf("new", "old"), result.includedMemoryIds)
        val text = result.text
        assertTrue(text.contains("current=true supersedes=old"))
        assertTrue(text.contains("current=false superseded_by=new"))
        assertTrue(text.indexOf("Tulln") < text.indexOf("Enns."))
        assertTrue(text.contains("Records marked superseded_by are outdated"))
    }

    @Test fun contradictionsAreRenderedTogetherWithAnAskBackInstruction() {
        val result = compiler(12_000).compile(ContextIntegrityFixture.input("Welchen Bus fährt Kurt?", listOf(
            memory("g", "Kurt fährt einen grünen Bus."), memory("y", "Kurt fährt einen gelben Bus."))))
        assertEquals(setOf("g", "y"), result.includedMemoryIds.toSet())
        assertTrue(result.text.contains("conflicts_with=y (ask back, do not choose)"))
        assertTrue(result.text.contains("conflicts_with=g (ask back, do not choose)"))
        assertTrue(result.text.contains("ask the owner instead of choosing one"))
    }

    @Test fun aGroupIsNeverSplitToFitTheBudget() {
        val records = listOf(memory("g", "Kurt fährt einen grünen Bus. " + "Detail ".repeat(60)),
            memory("y", "Kurt fährt einen gelben Bus. " + "Detail ".repeat(60)))
        val full = compiler(12_000).compile(ContextIntegrityFixture.input("Welchen Bus fährt Kurt?", records))
        assertEquals(setOf("g", "y"), full.includedMemoryIds.toSet())
        val withoutMemory = compiler(12_000).compile(ContextIntegrityFixture.input("Welchen Bus fährt Kurt?"))
        // room for exactly one of the two records, not both
        val tight = withoutMemory.text.toByteArray(Charsets.UTF_8).size + 700
        val cut = compiler(tight).compile(ContextIntegrityFixture.input("Welchen Bus fährt Kurt?", records))
        assertTrue(cut.includedMemoryIds.isEmpty())
        assertTrue(cut.truncated)
        assertFalse(cut.text.contains("Kurt fährt"))
    }

    @Test fun repeatedStatementsAreSentOnceAndTheObserverSeesOnlyWhatWasSent() {
        val observed = mutableListOf<List<RecallHit>>()
        val compiler = compiler(12_000).withRecallObserver { _, hits, _ -> observed += hits }
        val result = compiler.compile(ContextIntegrityFixture.input("Wo wohnt Ute?", listOf(
            memory("a", "Ute wohnt in Enns."), memory("b", "Ute wohnt in Enns."))))
        assertEquals(1, result.includedMemoryIds.size)
        assertEquals(1, Regex("Ute wohnt in Enns\\.").findAll(result.text).count())
        assertTrue(result.text.contains("same_statement_also_in="))
        assertEquals(listOf(result.includedMemoryIds), observed.map { hits -> hits.map { it.entryId } })
    }

    @Test fun aCorrectionWithoutParsableSubjectStillTravelsWithItsStatementWhileOtherMemoryFits() {
        val memories = listOf(memory("positive", "Maren ist in Graz."),
            memory("negative", "Maren: " + "Zusatz. ".repeat(140) + "Maren ist nicht in Graz."),
            memory("ring", "Der zircon Ring liegt in der Schublade."))
        val result = compiler(1_400).compile(ContextIntegrityFixture.input("Wo ist Maren und der zircon Ring?", memories))
        assertEquals(listOf("ring"), result.includedMemoryIds)
        assertFalse(result.text.contains("Maren ist in Graz."))
        assertTrue(result.truncated)
        assertTrue(result.text.toByteArray(Charsets.UTF_8).size <= 1_400)
    }
}
