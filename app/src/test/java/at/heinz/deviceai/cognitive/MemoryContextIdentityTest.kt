package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MemoryContextIdentityTest {
    @Test fun aFilteredHistoricalAliasCannotHideAnAmbiguousCurrentIdentity() {
        for (historicalState in listOf(ValidationState.STALE, ValidationState.CONTRADICTED)) {
            val current = ContextIntegrityFixture.memory("disputed", "Maren wohnt in Graz.")
            val historical = current.copy(validationState = historicalState, content = "Maren wohnt nicht in Graz.")
            val safe = current.copy(id = "safe", content = "Maren verwendet das Stichwort Bernstein.")
            for (input in listOf(listOf(current, historical, safe), listOf(historical, safe, current))) {
                val compiled = AiContextCompiler(AiContextPolicy(), { 100L }).compile(
                    ContextIntegrityFixture.input("Maren", input))
                assertEquals(listOf("safe"), compiled.includedMemoryIds)
                assertFalse(compiled.text.contains("wohnt in Graz"))
            }
        }
    }

    @Test fun aPrivateHistoricalConflictCannotSurviveAsAPublicCurrentRecord() {
        val current = ContextIntegrityFixture.memory("privacy-dispute", "Maren wohnt in Graz.")
            .copy(privacyClass = PrivacyClass.PUBLIC)
        val hidden = current.copy(validationState = ValidationState.STALE, privacyClass = PrivacyClass.SENSITIVE)
        val result = AiContextCompiler(AiContextPolicy(allowedPrivacy = setOf(PrivacyClass.PUBLIC)), { 100L })
            .compile(ContextIntegrityFixture.input("Maren", listOf(current, hidden)))
        assertEquals(emptyList<String>(), result.includedMemoryIds)
    }

    @Test fun uniqueHistoricalRecordsDoNotConsumeCurrentContextSlots() {
        val current = ContextIntegrityFixture.memory("current", "Maren ist das Stichwort.")
        val old = current.copy(id = "old", validationState = ValidationState.STALE)
        val contradicted = current.copy(id = "contradicted", validationState = ValidationState.CONTRADICTED)
        val compiler = AiContextCompiler(AiContextPolicy(memoryLimit = 1), { 100L })
        val clean = compiler.compile(ContextIntegrityFixture.input("Maren", listOf(current)))
        val mixed = compiler.compile(ContextIntegrityFixture.input("Maren", listOf(old, contradicted, current)))
        assertEquals(clean, mixed)
    }
}
