package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class AiContextIntegrityTest {
    private fun compile(input: AiContextInput, budget: Int = 6000) =
        AiContextCompiler(AiContextPolicy(maxBytes = budget), { 100L }).compile(input)

    @Test fun lateNegationSurvivesAsPartOfTheWholeMemory() {
        val text = "Maren ist in Graz. " + "Zusatz. ".repeat(150) + "Maren ist nicht in Graz."
        val result = compile(ContextIntegrityFixture.input("Wo ist Maren?",
            listOf(ContextIntegrityFixture.memory("late", text))))
        assertTrue(result.includedMemoryIds.contains("late"))
        assertTrue("A declared memory must include its late negation", result.text.contains(text))
    }

    @Test fun overflowOmitsTheWholeSelectedMemoryGroupIncludingItsIds() {
        val memories = listOf(ContextIntegrityFixture.memory("positive", "Maren ist in Graz."),
            ContextIntegrityFixture.memory("negative", "Maren: " + "Zusatz. ".repeat(140) + "Maren ist nicht in Graz."))
        val result = compile(ContextIntegrityFixture.input("Wo ist Maren?", memories), 1100)
        assertTrue("Do not retain the positive while dropping the correction", result.includedMemoryIds.isEmpty())
        assertFalse(result.text.contains("Maren ist in Graz."))
        assertTrue(result.truncated)
        assertTrue(result.text.toByteArray(Charsets.UTF_8).size <= 1100)
    }

    @Test fun requiredRequestCannotBeSilentlyCutBeforeItsNegation() {
        val request = "Prüfe " + "Details ".repeat(180) + "aber sende nichts."
        assertThrows(IllegalStateException::class.java) { compile(ContextIntegrityFixture.input(request), 1024) }
    }

    @Test fun allContradictionsPrecedeOptionalMemoryAndCannotBePartiallyLost() {
        val warning = "Maren ist nicht in Graz."
        val result = compile(ContextIntegrityFixture.input("Wo ist Maren?",
            listOf(ContextIntegrityFixture.memory("large", "Maren ist in Graz. " + "Zusatz ".repeat(500))),
            listOf(warning)), 1300)
        assertTrue(result.text.contains(warning))
        assertTrue(result.includedMemoryIds.isEmpty())
        val contradictions = List(33) { "Widerruf $it: Maren ist nicht in Graz." }
        val all = compile(ContextIntegrityFixture.input("Wo ist Maren?", contradictions = contradictions), 6000)
        assertTrue(all.text.contains(contradictions.last()))
    }

    @Test fun researchEvidenceKeepsItsNegativeSuffixAndBalancedUntrustedFrame() {
        val snippet = "Maren ist in Graz. " + "Zusatz. ".repeat(110) + "Maren ist nicht in Graz."
        val input = ContextIntegrityFixture.input("Wo ist Maren?", dossier = ContextIntegrityFixture.dossier(snippet))
        val result = compile(input)
        assertTrue(result.text.contains(snippet))
        assertEquals(1, result.includedResearchSourceUrls.size)
        val small = compile(input, 1100)
        assertTrue(small.includedResearchSourceUrls.isEmpty())
        assertFalse(small.text.contains("Maren ist in Graz."))
        assertEquals(Regex("<<<UNTRUSTED_EVIDENCE>>>").findAll(small.text).count(),
            Regex("<<<END_UNTRUSTED_EVIDENCE>>>").findAll(small.text).count())
    }

    @Test fun finalModelCharacterCapCannotUndoAtomicCompilation() {
        val compiled = compile(ContextIntegrityFixture.input("Wo ist Maren?",
            listOf(ContextIntegrityFixture.memory("short", "Maren ist in Graz."))))
        val complete = ChatReasoningRouter.composePrompt(compiled.text, null, "\nEnde", 8000)
        compiled.requireCompleteIn(complete)
        val clipped = ChatReasoningRouter.composePrompt(compiled.text, null, "\nEnde", 700)
        assertFalse(clipped.contains(compiled.text)) // reproduces the second, downstream truncation boundary
        assertThrows(IllegalStateException::class.java) { compiled.requireCompleteIn(clipped) }
    }

    @Test fun completeSmallMemoryIdsMatchVisibleBodiesAndContradictionOverflowBlocks() {
        val memory = ContextIntegrityFixture.memory("small", "Maren ist in Graz.")
        val compiled = compile(ContextIntegrityFixture.input("Wo ist Maren?", listOf(memory)))
        assertEquals(listOf("small"), compiled.includedMemoryIds)
        assertTrue(compiled.text.contains(memory.content))
        assertThrows(IllegalStateException::class.java) {
            compile(ContextIntegrityFixture.input("Wo ist Maren?", contradictions = List(40) { "Maren: " + "nein ".repeat(30) }), 1100)
        }
    }

    @Test fun secretRedactionAndWholeUnicodeMemoryRespectTheByteLimit() {
        val text = "Maren ist in Ålesund. token=privatevalue " + "🧠".repeat(60) + " nicht Graz."
        val result = compile(ContextIntegrityFixture.input("Wo ist Maren?",
            listOf(ContextIntegrityFixture.memory("unicode", text))), 1800)
        assertTrue(result.text.contains("nicht Graz."))
        assertFalse(result.text.contains("privatevalue"))
        assertTrue(result.text.toByteArray(Charsets.UTF_8).size <= 1800)
        assertTrue(Charsets.UTF_8.newEncoder().canEncode(result.text))
    }
    @Test fun secretRedactionCannotConsumeTheResearchClosingBoundary() {
        val dossier=ContextIntegrityFixture.dossier("Maren: token=synthetic_secret")
            .copy(synthesis=ResearchSynthesisOutput("credential=another_synthetic_secret"))
        val result=compile(ContextIntegrityFixture.input("Wo ist Maren?",dossier=dossier))
        assertEquals(1,result.includedResearchSourceUrls.size)
        assertFalse(result.text.contains("synthetic_secret"))
        assertEquals(2,Regex("<<<UNTRUSTED_EVIDENCE>>>").findAll(result.text).count())
        assertEquals(2,Regex("<<<END_UNTRUSTED_EVIDENCE>>>").findAll(result.text).count())
    }

}
