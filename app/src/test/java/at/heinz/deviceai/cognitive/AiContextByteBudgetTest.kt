package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiContextByteBudgetTest {
    private val noTools = AiContextPolicy(maxBytes = 65_536, includeToolCatalog = false)

    private fun compile(input: AiContextInput, policy: AiContextPolicy = noTools): CompiledAiContext =
        AiContextCompiler(policy, nowMillis = { 100L }).compile(input)

    @Test
    fun requiredUnicodeSectionsFitExactlyAndFailOneByteBelow() {
        val input = ContextIntegrityFixture.input("Maren: " + "Å中🧠".repeat(120) + "\uD800")
            .copy(activeTaskSummary = "\uDC00Keine Freigabe.\uD800")
        val complete = compile(input)
        val bytes = complete.text.toByteArray(Charsets.UTF_8).size

        assertEquals(complete, compile(input, noTools.copy(maxBytes = bytes)))
        assertTrue(complete.text.contains("\uD800\n\n## ACTIVE TASK\n\uDC00"))
        val failure = assertThrows(IllegalStateException::class.java) {
            compile(input, noTools.copy(maxBytes = bytes - 1))
        }
        assertEquals("MANDATORY_CONTEXT_EXCEEDS_BUDGET", failure.message)
    }

    @Test
    fun optionalMemoryIsIncludedWithItsIdOrOmittedAsAWholeAtTheByteBoundary() {
        val memory = ContextIntegrityFixture.memory("unicode", "Maren: " + "Å中🧠".repeat(90) + " nicht Graz.\uD800")
        val input = ContextIntegrityFixture.input("Wo ist Maren?", listOf(memory))
        val complete = compile(input)
        val bytes = complete.text.toByteArray(Charsets.UTF_8).size

        assertEquals(listOf(memory.id), complete.includedMemoryIds)
        assertEquals(complete, compile(input, noTools.copy(maxBytes = bytes)))
        val omitted = compile(input, noTools.copy(maxBytes = bytes - 1))
        assertTrue(omitted.includedMemoryIds.isEmpty())
        assertFalse(omitted.text.contains(memory.content))
        assertTrue(omitted.truncated)
    }

    @Test
    fun omittedMemoryDoesNotSpendBytesNeededByLaterResearchAndSkills() {
        val memory = ContextIntegrityFixture.memory("large", "Maren: " + "Zusatz ".repeat(1_000))
        val input = ContextIntegrityFixture.input(
            "Wo ist Maren?",
            listOf(memory),
            dossier = ContextIntegrityFixture.dossier("Maren ist nicht in Graz.")
        ).copy(availableSkillNames = listOf("follow-up"))
        val policy = noTools.copy(maxBytes = 2_000)
        val result = compile(input, policy)
        val withoutMemory = compile(input.copy(memories = emptyList()), policy)

        assertEquals(withoutMemory.text, result.text)
        assertEquals(withoutMemory.includedResearchSourceUrls, result.includedResearchSourceUrls)
        assertEquals(1, result.includedResearchSourceUrls.size)
        assertTrue(result.includedMemoryIds.isEmpty())
        assertTrue(result.text.contains("## AVAILABLE SKILLS\n- follow-up"))
        assertTrue(result.truncated)
        assertFalse(withoutMemory.truncated)
    }

    @Test
    fun blankSectionsDoNotChangeTheBudgetAndCounterIsLocalToEachCompile() {
        val input = ContextIntegrityFixture.input("Wo ist Maren?")
        val compiler = AiContextCompiler(noTools.copy(maxBytes = 1_024), nowMillis = { 100L })
        val expected = compiler.compile(input)

        repeat(3) {
            assertEquals(expected, compiler.compile(input.copy(activeTaskSummary = " \n\t")))
            assertThrows(IllegalStateException::class.java) {
                compiler.compile(input.copy(userRequest = "Maren: " + "x".repeat(2_000)))
            }
            assertEquals(expected, compiler.compile(input))
        }
    }

    @Test
    fun redactedTextDeterminesTheBudgetAndKeepsResearchDelimiters() {
        val input = ContextIntegrityFixture.input(
            "Maren token=" + "synthetic_secret".repeat(100) + "\nNicht senden.",
            dossier = ContextIntegrityFixture.dossier("Maren: api_key=synthetic_secret\nNicht Graz.")
                .copy(synthesis = ResearchSynthesisOutput("authorization=synthetic_secret"))
        ).copy(activeTaskSummary = "Bearer synthetic_bearer_value\n" + "ä".repeat(350))
        val complete = compile(input)
        val bytes = complete.text.toByteArray(Charsets.UTF_8).size
        val result = compile(input, noTools.copy(maxBytes = bytes))

        assertEquals(complete, result)
        assertFalse(result.truncated)
        assertFalse(result.text.contains("synthetic_secret"))
        assertFalse(result.text.contains("synthetic_bearer_value"))
        assertTrue(result.text.contains("token=[REDACTED]"))
        assertTrue(result.text.contains("Bearer [REDACTED]"))
        assertEquals(2, Regex("<<<UNTRUSTED_EVIDENCE>>>").findAll(result.text).count())
        assertEquals(2, Regex("<<<END_UNTRUSTED_EVIDENCE>>>").findAll(result.text).count())
    }

    @Test
    fun requiredContradictionsStillFailClosedBeforeOptionalSections() {
        val input = ContextIntegrityFixture.input(
            "Wo ist Maren?",
            contradictions = listOf("Maren: " + "nicht in Graz. ".repeat(100))
        ).copy(availableSkillNames = listOf("follow-up"))

        val failure = assertThrows(IllegalStateException::class.java) {
            compile(input, noTools.copy(maxBytes = 1_024))
        }
        assertEquals("MANDATORY_CONTEXT_EXCEEDS_BUDGET", failure.message)
    }

    @Test
    fun reservedOwnerHistoryDisplacesOptionalMemoryBeforeTheCurrentRequest() {
        val request = "Wo steht die Kiste von Maren jetzt?"
        val memory = ContextIntegrityFixture.memory("large", "Maren: " + "Zusatz. ".repeat(140))
        val input = ContextIntegrityFixture.input(request, listOf(memory))
        val mandatory = compile(input.copy(memories = emptyList()))
        val complete = compile(input)
        assertEquals(listOf(memory.id), complete.includedMemoryIds)
        val policy = noTools.copy(maxBytes = complete.text.toByteArray(Charsets.UTF_8).size)
        val reserve = policy.maxBytes - mandatory.text.toByteArray(Charsets.UTF_8).size
        val result = AiContextCompiler(policy, nowMillis = { 100L }).compile(input, reservedBytes = reserve)

        assertEquals(mandatory.text, result.text)
        assertTrue(result.text.contains(request))
        assertTrue(result.includedMemoryIds.isEmpty())
        assertTrue(result.truncated)
        assertEquals(policy.maxBytes, result.text.toByteArray(Charsets.UTF_8).size + reserve)
    }

    @Test
    fun reservationCannotClipRequiredCorrectionsOrGrowTheBudget() {
        val input = ContextIntegrityFixture.input("Wo ist Maren?",
            contradictions = listOf("Maren ist nicht in Graz."))
        val complete = compile(input)
        val policy = noTools.copy(maxBytes = complete.text.toByteArray(Charsets.UTF_8).size + 100)
        val compiler = AiContextCompiler(policy)
        assertEquals(complete.text, compiler.compile(input, reservedBytes = 100).text)
        assertEquals("MANDATORY_CONTEXT_EXCEEDS_BUDGET", assertThrows(IllegalStateException::class.java) {
            compiler.compile(input, reservedBytes = 101)
        }.message)
        assertEquals("MANDATORY_CONTEXT_EXCEEDS_BUDGET", assertThrows(IllegalStateException::class.java) {
            compiler.compile(input, reservedBytes = Int.MAX_VALUE)
        }.message)
        assertThrows(IllegalArgumentException::class.java) { compiler.compile(input, reservedBytes = -1) }
    }
}
