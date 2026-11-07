package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Independent policy/alias cases frozen before reviewing the candidate fix. */
class ContradictionBoundaryHoldoutTest {
    private fun record(id: String, content: String, privacy: PrivacyClass) =
        ContextIntegrityFixture.memory(id, content).copy(
            validationState = ValidationState.CONTRADICTED,
            privacyClass = privacy
        )

    private fun compileProjection(
        memories: List<MemoryRecord>,
        privacy: Set<PrivacyClass>,
        maxBytes: Int = 6_000
    ): CompiledAiContext = AiContextCompiler(
        AiContextPolicy(maxBytes = maxBytes, allowedPrivacy = privacy, includeToolCatalog = false),
        { 100L }
    ).compile(ContextIntegrityFixture.input(
        "Orion", memories,
        contradictions = memories
            .filter { memory -> memory.validationState.name == "CONTRADICTED" }
            .map { memory -> memory.content }
    ))

    @Test
    fun explicitSensitiveAllowanceKeepsTheCompleteContradiction() {
        val memory = record("sensitive-allowed", "Orion ist nicht in Graz, sondern in Linz.",
            PrivacyClass.SENSITIVE)
        val compiled = compileProjection(listOf(memory), setOf(PrivacyClass.PUBLIC, PrivacyClass.SENSITIVE))
        assertTrue(compiled.text.contains(memory.content))
        assertTrue(compiled.includedMemoryIds.isEmpty())
    }

    @Test
    fun publicOnlyPolicyAlsoExcludesPrivateContradictionAliases() {
        val memory = record("private-forbidden", "Orion: SYNTHETIC_PRIVATE_CORRECTION_812",
            PrivacyClass.PRIVATE)
        val compiled = compileProjection(listOf(memory), setOf(PrivacyClass.PUBLIC))
        assertFalse(compiled.text.contains("SYNTHETIC_PRIVATE_CORRECTION_812"))
        assertTrue(compiled.includedMemoryIds.isEmpty())
    }

    @Test
    fun aStringSharedByAllowedAndForbiddenRecordsCannotChooseTheAllowedAlias() {
        val text = "Orion: SYNTHETIC_AMBIGUOUS_CORRECTION_924"
        val public = record("public-copy", text, PrivacyClass.PUBLIC)
        val sensitive = record("sensitive-copy", text, PrivacyClass.SENSITIVE)
        for (records in listOf(listOf(public, sensitive), listOf(sensitive, public))) {
            val compiled = compileProjection(records, setOf(PrivacyClass.PUBLIC))
            assertFalse("String-only provenance cannot select an allowed alias by collection order",
                compiled.text.contains("SYNTHETIC_AMBIGUOUS_CORRECTION_924"))
        }
    }

    @Test
    fun conflictingIdCannotReenterThroughItsOtherwiseAllowedContradictionContent() {
        val visible = record("conflicted", "Orion: SYNTHETIC_CONFLICTED_VISIBLE_635", PrivacyClass.PUBLIC)
        val hidden = record("conflicted", "SYNTHETIC_DIFFERENT_PRIVATE_636", PrivacyClass.SENSITIVE)
            .copy(validationState = ValidationState.UNVERIFIED)
        for (records in listOf(listOf(visible, hidden), listOf(hidden, visible))) {
            val compiled = compileProjection(records, setOf(PrivacyClass.PUBLIC))
            assertFalse(compiled.text.contains("SYNTHETIC_CONFLICTED_VISIBLE_635"))
            assertFalse(compiled.text.contains("SYNTHETIC_DIFFERENT_PRIVATE_636"))
            assertTrue(compiled.includedMemoryIds.isEmpty())
        }
    }

    @Test
    fun oversizedForbiddenContradictionDoesNotConsumeMandatoryContextBudget() {
        val memory = record("large-forbidden", "Orion " + "SYNTHETIC_PRIVATE_SEGMENT_416 ".repeat(300),
            PrivacyClass.SENSITIVE)
        val compiled = compileProjection(listOf(memory), setOf(PrivacyClass.PUBLIC), maxBytes = 1_600)
        assertFalse(compiled.text.contains("SYNTHETIC_PRIVATE_SEGMENT_416"))
        assertTrue(compiled.text.toByteArray(Charsets.UTF_8).size <= 1_600)
    }

    @Test
    fun standaloneCompleteCorrectionsStayRequiredAndAtomic() {
        val corrections = List(33) { "Korrektur $it: Orion ist nicht in Graz, sondern in Linz." }
        val input = ContextIntegrityFixture.input("Orion", contradictions = corrections)
        val compiler = AiContextCompiler(AiContextPolicy(maxBytes = 6_000, includeToolCatalog = false), { 100L })
        val compiled = compiler.compile(input)
        corrections.forEach { assertTrue(compiled.text.contains(it)) }
        assertTrue(compiled.includedMemoryIds.isEmpty())
        val error = assertThrows(IllegalStateException::class.java) {
            AiContextCompiler(AiContextPolicy(maxBytes = 1_024, includeToolCatalog = false), { 100L })
                .compile(input)
        }
        assertEquals("MANDATORY_CONTEXT_EXCEEDS_BUDGET", error.message)
    }
}
